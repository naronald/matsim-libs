/* *********************************************************************** *
 * project: org.matsim.*
 * MarginalCostControler.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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

package playground.gregor.sims.socialcost;

import org.matsim.core.controler.Controler;
import org.matsim.core.trafficmonitoring.PessimisticTravelTimeAggregator;
import org.matsim.core.trafficmonitoring.TravelTimeAggregatorFactory;
import org.matsim.core.trafficmonitoring.TravelTimeDataHashMap;

public class MarginalCostControler extends Controler{



	public MarginalCostControler(final String[] args) {
		super(args);
	}

	@Override
	protected void setUp() {
		super.setUp();
		
		
		TravelTimeAggregatorFactory factory = new TravelTimeAggregatorFactory();
		factory.setTravelTimeDataPrototype(TravelTimeDataHashMap.class);
		factory.setTravelTimeAggregatorPrototype(PessimisticTravelTimeAggregator.class);
		SocialCostCalculator sc = new SocialCostCalculatorSingleLink(this.network,this.config.travelTimeCalculator().getTraveltimeBinSize());
		
		this.events.addHandler(sc);
		this.travelCostCalculator = new MarginalTravelCostCalculatorII(this.travelTimeCalculator,sc);
		this.strategyManager = loadStrategyManager();
		this.addControlerListener(sc);
	}

	public static void main(final String[] args) {
		final Controler controler = new MarginalCostControler(args);
		controler.run();
		System.exit(0);
	}
}