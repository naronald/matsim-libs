/* *********************************************************************** *
 * project: org.matsim.*
 * MyMobsimFactory.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2010 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package playground.christoph.withinday2;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.framework.MobsimFactory;
import org.matsim.core.mobsim.framework.Mobsim;
import org.matsim.core.mobsim.framework.listeners.FixedOrderSimulationListener;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.mobsim.qsim.interfaces.Netsim;
import org.matsim.core.mobsim.qsim.multimodalsimengine.router.util.TravelTimeFactoryWrapper;
import org.matsim.core.population.PopulationFactoryImpl;
import org.matsim.core.population.routes.ModeRouteFactory;
import org.matsim.core.replanning.modules.AbstractMultithreadedModule;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.DijkstraFactory;
import org.matsim.core.router.util.TravelTime;
import org.matsim.withinday.mobsim.ReplanningManager;
import org.matsim.withinday.mobsim.WithinDayQSimFactory;
import org.matsim.withinday.replanning.modules.ReplanningModule;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayDuringActivityReplannerFactory;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayDuringLegReplannerFactory;

/**
 * @author nagel
 *
 */
public class MyMobsimFactory implements MobsimFactory {
	private static final Logger log = Logger.getLogger(MyMobsimFactory.class);
	private TravelDisutilityFactory travCostCalc;
	private TravelTime travTimeCalc;
	private ReplanningManager replanningManager;
	
	MyMobsimFactory( TravelDisutilityFactory travelCostCalculator, TravelTime travelTimeCalculator ) {
		this.travCostCalc = travelCostCalculator ;
		this.travTimeCalc = travelTimeCalculator ;
	}

	@Override
	public Mobsim createMobsim(Scenario sc, EventsManager eventsManager) {
		int numReplanningThreads = 1;

		replanningManager = new ReplanningManager();
		replanningManager.initializeReplanningModules(numReplanningThreads);

		QSim mobsim = new WithinDayQSimFactory(replanningManager).createMobsim(sc, eventsManager);
		
		// Use a FixedOrderQueueSimulationListener to bundle the Listeners and
		// ensure that they are started in the needed order.
		FixedOrderSimulationListener fosl = new FixedOrderSimulationListener();
		fosl.addSimulationBeforeSimStepListener(replanningManager);
		mobsim.addQueueSimulationListeners(fosl);
		// (essentially, can just imagine the replanningManager as a regular MobsimListener)
		
		log.info("Initialize Replanning Routers");
		initReplanningRouter(sc, mobsim);

		//just activitate replanning during an activity
		replanningManager.doDuringActivityReplanning(true);
		replanningManager.doInitialReplanning(false);
		replanningManager.doDuringLegReplanning(true);

		return mobsim ;
	}

	/*
	 * New Routers for the Replanning are used instead of using the controler's.
	 * By doing this every person can use a personalised Router.
	 */
	private void initReplanningRouter(Scenario sc, Netsim mobsim ) {

		ModeRouteFactory routeFactory = ((PopulationFactoryImpl) mobsim.getScenario().getPopulation().getFactory()).getModeRouteFactory();
		TravelTimeFactoryWrapper wrapper = new TravelTimeFactoryWrapper(this.travTimeCalc);
		AbstractMultithreadedModule routerModule =
			new ReplanningModule(sc.getConfig(), sc.getNetwork(), this.travCostCalc, wrapper, new DijkstraFactory(), routeFactory);
		// (ReplanningModule is a wrapper that either returns PlansCalcRoute or MultiModalPlansCalcRoute)
		// this pretends being a general Plan Algorithm, but I wonder if it can reasonably be anything else but a router?



		// replanning while at activity:

		WithinDayDuringActivityReplannerFactory duringActivityReplannerFactory = new OldPeopleReplannerFactory(sc, replanningManager, routerModule, 1.0);
		// defines a "doReplanning" method which contains the core of the work
		// as a piece, it re-routes a _future_ leg.

		duringActivityReplannerFactory.addIdentifier(new OldPeopleIdentifierFactory(mobsim).createIdentifier());
		// which persons to replan

		this.replanningManager.addDuringActivityReplannerFactory(duringActivityReplannerFactory);
		// I think this just adds the stuff to the threads mechanics (can't say why it is not enough to use the multithreaded
		// module).  kai, oct'10



		// replanning while on leg:

		WithinDayDuringLegReplannerFactory duringLegReplannerFactory = new YoungPeopleReplannerFactory(sc, replanningManager, routerModule, 1.0);
		// defines a "doReplanning" method which contains the core of the work
		// it replaces the next activity
		// in order to get there, it re-routes the current route

		duringLegReplannerFactory.addIdentifier(new YoungPeopleIdentifierFactory(mobsim).createIdentifier());
		// persons identifier added to replanner

		this.replanningManager.addDuringLegReplannerFactory(duringLegReplannerFactory);
		// I think this just adds the stuff to the threads mechanics (can't say why it is not enough to use the multithreaded
		// module).  kai, oct'10
	}



}
