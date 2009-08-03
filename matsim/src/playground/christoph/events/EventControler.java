/* *********************************************************************** *
 * project: org.matsim.*
 * EventControler.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
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

package playground.christoph.events;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import org.matsim.core.api.experimental.ScenarioImpl;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.CharyparNagelScoringConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.mobsim.queuesim.listener.QueueSimulationListener;
import org.matsim.core.population.PersonImpl;
import org.matsim.core.router.Dijkstra;
import org.matsim.core.router.PlansCalcRoute;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeCost;
import org.matsim.core.router.costcalculators.TravelTimeDistanceCostCalculator;
import org.matsim.core.router.util.AStarLandmarksFactory;
import org.matsim.core.router.util.DijkstraFactory;
import org.matsim.core.router.util.TravelCost;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.core.utils.geometry.transformations.AtlantisToWGS84;
import org.matsim.core.utils.geometry.transformations.CH1903LV03toWGS84;
import org.matsim.core.utils.geometry.transformations.GK4toWGS84;
import org.matsim.population.algorithms.PlanAlgorithm;

import playground.christoph.analysis.wardrop.ActTimesCollector;
import playground.christoph.analysis.wardrop.Wardrop;
import playground.christoph.events.algorithms.ParallelActEndReplanner;
import playground.christoph.events.algorithms.ParallelInitialReplanner;
import playground.christoph.events.algorithms.ParallelLeaveLinkReplanner;
import playground.christoph.events.algorithms.ParallelReplanner;
import playground.christoph.knowledge.KMLPersonWriter;
import playground.christoph.knowledge.container.MapKnowledgeDB;
import playground.christoph.knowledge.container.dbtools.KnowledgeDBStorageHandler;
import playground.christoph.knowledge.nodeselection.ParallelCreateKnownNodesMap;
import playground.christoph.knowledge.nodeselection.SelectNodes;
import playground.christoph.knowledge.nodeselection.SelectNodesCircular;
import playground.christoph.knowledge.nodeselection.SelectNodesDijkstra;
import playground.christoph.knowledge.nodeselection.SelectionReaderMatsim;
import playground.christoph.knowledge.nodeselection.SelectionWriter;
import playground.christoph.mobsim.MyQueueSimEngine;
import playground.christoph.mobsim.ReplanningQueueSimulation;
import playground.christoph.router.CompassRoute;
import playground.christoph.router.DijkstraWrapper;
import playground.christoph.router.KnowledgePlansCalcRoute;
import playground.christoph.router.RandomCompassRoute;
import playground.christoph.router.RandomRoute;
import playground.christoph.router.TabuRoute;
import playground.christoph.router.costcalculators.KnowledgeTravelCostCalculator;
import playground.christoph.router.costcalculators.KnowledgeTravelCostWrapper;
import playground.christoph.router.costcalculators.KnowledgeTravelTimeCalculator;
import playground.christoph.router.costcalculators.KnowledgeTravelTimeWrapper;
import playground.christoph.router.costcalculators.OnlyTimeDependentTravelCostCalculator;
import playground.christoph.scoring.OnlyTimeDependentScoringFunctionFactory;
import playground.scnadine.choiceSetGeneration.algorithms.FreeSpeedTravelTimeCalculator;


/**
 * The Controler is responsible for complete simulation runs, including
 * the initialization of all required data, running the iterations and
 * the replanning, analyses, etc.
 *
 * @author Christoph Dobler
 */

//mysimulations/kt-zurich/config10pct_factor_0.075_replanning.xml
//mysimulations/kt-zurich/config10pct_factor_0.05.xml
//mysimulations/kt-zurich/config10pct_factor_0.075.xml
//mysimulations/kt-zurich/config10pct_factor_0.10.xml

//mysimulations/census2000_dilZh30km_miv_transitincl_10pct/config.xml
//mysimulations/berlin/config.xml
/* Example for the new entries in a config.xml file to read / write selected nodes from / to files.
<module name="selection">
	<param name="readSelection" value="true"/>
	<param name="inputSelectionFile" value="mysimulations/berlin/selection.xml.gz" />
	<param name="writeSelection" value="true"/>
	<param name="outputSelectionFile" value="./output/berlin/selection.xml.gz" />
	<param name="dtdFile" value="./src/playground/christoph/knowledge/nodeselection/Selection.dtd" />
</module>
*/
public class EventControler extends Controler{

	protected ReplanningQueueSimulation sim;
//	protected TravelTimeDistanceCostCalculator travelCostCalculator;
//	protected KnowledgeTravelTimeCalculator travelTimeCalculator;
	protected ArrayList<PlanAlgorithm> replanners;
	protected ArrayList<SelectNodes> nodeSelectors;
	
	protected ActTimesCollector actTimesCollector;
	protected boolean calcWardrop = false;
	
	// for Batch Runs
	public double pNoReplanning = 1.0;
	public double pInitialReplanning = 0.0;
	public double pActEndReplanning = 0.0;
	public double pLeaveLinkReplanning = 0.0;
	
	protected int noReplanningCounter = 0;
	protected int initialReplanningCounter = 0;
	protected int actEndReplanningCounter = 0;
	protected int leaveLinkReplanningCounter = 0;
	
	protected KnowledgeTravelTimeCalculator knowledgeTravelTime;
	
	protected LinkVehiclesCounter linkVehiclesCounter;
	
	private static final Logger log = Logger.getLogger(EventControler.class);
	
	/**
	 * Initializes a new instance of Controler with the given arguments.
	 *
	 * @param args The arguments to initialize the controler with. <code>args[0]</code> is expected to
	 * 		contain the path to a configuration file, <code>args[1]</code>, if set, is expected to contain
	 * 		the path to a local copy of the DTD file used in the configuration file.
	 */
	public EventControler(String[] args)
	{
		super(args);
//		config.global().setNumberOfThreads(2);
		
		// Use a Scoring Function, that only scores the travel times!
		this.setScoringFunctionFactory(new OnlyTimeDependentScoringFunctionFactory());

		knowledgeTravelTime = new KnowledgeTravelTimeCalculator();
		OnlyTimeDependentTravelCostCalculator travelCost = new OnlyTimeDependentTravelCostCalculator(knowledgeTravelTime);

//		KnowledgeTravelCostWrapper travelCostWrapper = new KnowledgeTravelCostWrapper(travelCost);

		this.setTravelCostCalculator(travelCost);	
		
	}
	
	
	// only for Batch Runs
	public EventControler(Config config)
	{
		super(config);
				
		// Use a Scoring Function, that only scores the travel times!
		this.setScoringFunctionFactory(new OnlyTimeDependentScoringFunctionFactory());

		knowledgeTravelTime = new KnowledgeTravelTimeCalculator();
		OnlyTimeDependentTravelCostCalculator travelCost = new OnlyTimeDependentTravelCostCalculator(knowledgeTravelTime);
//		KnowledgeTravelCostWrapper travelCostWrapper = new KnowledgeTravelCostWrapper(travelCost);

		this.setTravelCostCalculator(travelCost);
	}
	
	
	/*
	 * New Routers for the Replanning are used instead of using the controler's. 
	 * By doing this every person can use a personalised Router.
	 */
	protected void initReplanningRouter()
	{
		replanners = new ArrayList<PlanAlgorithm>();

		KnowledgeTravelTimeCalculator travelTimeCalculator = new KnowledgeTravelTimeCalculator(sim.getMyQueueNetwork());
		TravelTimeDistanceCostCalculator travelCostCalculator = new TravelTimeDistanceCostCalculator(travelTimeCalculator, new CharyparNagelScoringConfigGroup());
	
		// Dijkstra
		//replanners.add(new PlansCalcRouteConfigGroup(), new PlansCalcRouteDijkstra(network, travelCostCalculator, travelTimeCalculator));

		//AStarLandmarks
		//PreProcessLandmarks landmarks = new PreProcessLandmarks(new FreespeedTravelTimeCost(new CharyparNagelScoringConfigGroup()));
		//landmarks.run(network);
		AStarLandmarksFactory factory = new AStarLandmarksFactory(network, new FreespeedTravelTimeCost(new CharyparNagelScoringConfigGroup()));
		replanners.add(new PlansCalcRoute(new PlansCalcRouteConfigGroup(), network, travelCostCalculator, travelTimeCalculator, factory));
		
		// BasicReplanners (Random, Tabu, Compass, ...)
		// each replanner can handle an arbitrary number of persons
		KnowledgePlansCalcRoute randomRouter = new KnowledgePlansCalcRoute(network, new RandomRoute(), new RandomRoute());
		randomRouter.setMyQueueNetwork(sim.getMyQueueNetwork());
		replanners.add(randomRouter);

		
		KnowledgePlansCalcRoute tabuRouter = new KnowledgePlansCalcRoute(network, new TabuRoute(), new TabuRoute());
		tabuRouter.setMyQueueNetwork(sim.getMyQueueNetwork());
		replanners.add(tabuRouter);
		
		KnowledgePlansCalcRoute compassRouter = new KnowledgePlansCalcRoute(network, new CompassRoute(), new CompassRoute());
		compassRouter.setMyQueueNetwork(sim.getMyQueueNetwork());
		replanners.add(compassRouter);
		
		KnowledgePlansCalcRoute randomCompassRouter = new KnowledgePlansCalcRoute(network, new RandomCompassRoute(), new RandomCompassRoute());
		randomCompassRouter.setMyQueueNetwork(sim.getMyQueueNetwork());
		replanners.add(randomCompassRouter);
		
		
		// Dijkstra for Replanning
//		KnowledgeTravelTimeCalculator travelTime = new KnowledgeTravelTimeCalculator();
//		KnowledgeTravelCostCalculator travelCost = new KnowledgeTravelCostCalculator(travelTime);
		
		// Use a Wrapper - by doing this, already available MATSim CostCalculators can be used
//		TravelTimeDistanceCostCalculator travelCost = new TravelTimeDistanceCostCalculator(travelTime);
//		KnowledgeTravelCostWrapper travelCostWrapper = new KnowledgeTravelCostWrapper(travelCost);

//		TravelTime travelTime = new FreeSpeedTravelTimeCalculator();
		// Use a Wrapper - by doing this, already available MATSim CostCalculators can be used
		KnowledgeTravelTimeCalculator travelTime = new KnowledgeTravelTimeCalculator();
		KnowledgeTravelTimeWrapper travelTimeWrapper = new KnowledgeTravelTimeWrapper(travelTime);
		OnlyTimeDependentTravelCostCalculator travelCost = new OnlyTimeDependentTravelCostCalculator(travelTimeWrapper);
		KnowledgeTravelCostWrapper travelCostWrapper = new KnowledgeTravelCostWrapper(travelCost);
	
		travelTimeWrapper.checkNodeKnowledge(false);
		travelCostWrapper.checkNodeKnowledge(false);
		
		// Use the Wrapper with the same CostCalculator as the MobSim uses
		//KnowledgeTravelCostWrapper travelCostWrapper = new KnowledgeTravelCostWrapper(this.getTravelCostCalculator());

		// Don't use Knowledge for CostCalculations
		Dijkstra dijkstra = new Dijkstra(network, travelCostWrapper, travelTimeWrapper);
		DijkstraWrapper dijkstraWrapper = new DijkstraWrapper(dijkstra, travelCostWrapper, travelTimeWrapper);

//		Dijkstra dijkstra = new Dijkstra(network, travelCost, travelTime);
//		DijkstraWrapper dijkstraWrapper = new DijkstraWrapper(dijkstra, travelCost, travelTime);
		KnowledgePlansCalcRoute dijkstraRouter = new KnowledgePlansCalcRoute(network, dijkstraWrapper, dijkstraWrapper);
		dijkstraRouter.setMyQueueNetwork(sim.getMyQueueNetwork());
		
		replanners.add(dijkstraRouter);

		
		TravelTime travelTime2 = new FreespeedTravelTimeCost();
		TravelCost travelCost2 = new OnlyTimeDependentTravelCostCalculator(travelTime2);
		PlansCalcRoute dijkstraRouter2 = new PlansCalcRoute(new PlansCalcRouteConfigGroup(), network, travelCost2, travelTime2, new DijkstraFactory());
		replanners.add(dijkstraRouter2);
	}
	
	public ArrayList<PlanAlgorithm> getReplanningRouters()
	{
		return replanners;
	}

	/*
	 * Hands over the ArrayList to the ParallelReplanner
	 */
	protected void initParallelReplanningModules()
	{
		ParallelReplanner.init(replanners);
		ParallelReplanner.setNumberOfThreads(2);

		ParallelActEndReplanner.init();
		ParallelLeaveLinkReplanner.init();
	/*
		ParallelLeaveLinkReplanner.init(replanners);
		ParallelLeaveLinkReplanner.setNumberOfThreads(2);
		ParallelActEndReplanner.init(replanners);
		ParallelActEndReplanner.setNumberOfThreads(2);
		
		// more Modules to come...
	 */
	
	}
	
	/*
	 * Initializes the NodeSeletors that are used to create the Activity Spaces of the
	 * Persons of a Population.
	 */
	protected void initNodeSelectors()
	{
		nodeSelectors = new ArrayList<SelectNodes>();
		
		SelectNodesCircular snc = new SelectNodesCircular(this.network);
		snc.setDistance(5000);
		nodeSelectors.add(snc);
		
		SelectNodesDijkstra selectNodesDijkstra = new SelectNodesDijkstra(this.network);
		selectNodesDijkstra.setCostCalculator(new OnlyTimeDependentTravelCostCalculator(null));
		//selectNodesDijkstra.setCostCalculator(new OnlyDistanceDependentTravelCostCalculator(null));
		
		selectNodesDijkstra.setCostFactor(1.50);
		nodeSelectors.add(selectNodesDijkstra);
	}

	@Override
	protected void runMobSim() 
	{		
		sim = new ReplanningQueueSimulation(this.network, this.population, this.events);
		
		sim.setControler(this);
		
		linkVehiclesCounter = new LinkVehiclesCounter();
		linkVehiclesCounter.setQueueNetwork(sim.getQueueNetwork());
		this.events.addHandler(linkVehiclesCounter);
		sim.getMyQueueNetwork().setLinkVehiclesCounter(linkVehiclesCounter);
		
		List<QueueSimulationListener> queueSimulationListeners = new ArrayList<QueueSimulationListener>();
		queueSimulationListeners.add(linkVehiclesCounter);
		sim.addQueueSimulationListeners(queueSimulationListeners);

		
		// set QueueNetwork in the Traveltime Calculator
		if (knowledgeTravelTime != null) knowledgeTravelTime.setMyQueueNetwork(sim.getMyQueueNetwork());

		log.info("Remove not selected Plans");
		clearPlans();
		
//		log.info("Read known Nodes Maps from a File");
//		readKnownNodesMap();
		
		log.info("Initialize Replanning Routers");
		initReplanningRouter();
		
		log.info("Initialize Parallel Replanning Modules");
		initParallelReplanningModules();
		
		log.info("Set Replanning flags");
		setReplanningFlags();
		
		log.info("Set Replanners for each Person");
		setReplanners();
		
		log.info("Initialize Node Selectors");
		initNodeSelectors();
		
		log.info("Set Node Selectors");
		setNodeSelectors();
		
		log.info("Initialize Knowledge Data Handler");
		initKnowledgeStorageHandler();
		
		log.info("Set Knowledge Data Handler");
		setKnowledgeStorageHandler();
		
		
//		KnowledgeDBStorageHandler knowledgeDBStorageHandler = new KnowledgeDBStorageHandler(population);
//		
//		knowledgeDBStorageHandler.run();
//		for(PersonImpl person : population.getPersons().values())
//		{
//			System.out.println("ping");
//			knowledgeDBStorageHandler.addPerson(person);
//			knowledgeDBStorageHandler.newPersons.notify();
//		}
//		try {
//			knowledgeDBStorageHandler.join();
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
//		log.info("Create known Nodes Maps");
//		createKnownNodes();

//		log.info("Write known Nodes Maps to a File");
//		writeKownNodesMap();
		
		/* 
		 * Could be done before or after the creation of the activity rooms -
		 * depending on the intention of the simulation.
		 * 
		 * If done before, the new created Route is the base for the activity rooms.
		 * 
		 * If done afterwards, existing routes are the base for the activity rooms and
		 * the replanners have to act within the borders of the already defined rooms.
		 * The existing plans could for example be the results of a relaxed solution of
		 * a standard MATSim simulation.
		 */
		log.info("do initial Replanning");
		doInitialReplanning();
		
		sim.run();
	}
	

	/* Add three boolean variables to each Person.
	 * They are used to indicate, if the plans of this person should be
	 * replanned each time if an activity ends, each time a link is left,
	 * before the simulation starts or never during an iteration.
	 * 
	 * I don't like this way but, it is the best way I know at the moment...
	 * In my opinion these variables should be part of the PersonAgents within
	 * the QueueSimulation - but they can't be accessed from an EventHandler.
	 */
	protected void setReplanningFlags()
	{
		noReplanningCounter = 0;
		initialReplanningCounter = 0;
		actEndReplanningCounter = 0;
		leaveLinkReplanningCounter = 0;
		
		for(PersonImpl person : this.getPopulation().getPersons().values())
		{
			// get Person's Custom Attributes
			Map<String,Object> customAttributes = person.getCustomAttributes();
			
			double probability = MatsimRandom.getRandom().nextDouble();
			
			// No Replanning
			if (probability <= pNoReplanning)
			{
				noReplanningCounter++;
				customAttributes.put("initialReplanning", new Boolean(false));
				customAttributes.put("leaveLinkReplanning", new Boolean(false));
				customAttributes.put("endActivityReplanning", new Boolean(false));	
			}

			// Initial Replanning
			else if (probability > pNoReplanning && probability <= pNoReplanning + pInitialReplanning)
			{
				initialReplanningCounter++;
				customAttributes.put("initialReplanning", new Boolean(true));
				customAttributes.put("leaveLinkReplanning", new Boolean(false));
				customAttributes.put("endActivityReplanning", new Boolean(false));	
			}
			
			// Act End Replanning
			else if (probability > pNoReplanning + pInitialReplanning 
				  && probability <= pNoReplanning + pInitialReplanning + pActEndReplanning)
			{
				actEndReplanningCounter++;
				customAttributes.put("initialReplanning", new Boolean(false));
				customAttributes.put("leaveLinkReplanning", new Boolean(false));
				customAttributes.put("endActivityReplanning", new Boolean(true));	
			}			
			
			// Leave Link Replanning
			else
			{
				leaveLinkReplanningCounter++;
				customAttributes.put("initialReplanning", new Boolean(false));
				customAttributes.put("leaveLinkReplanning", new Boolean(true));
				customAttributes.put("endActivityReplanning", new Boolean(false));	
			}
			
			// (de)activate replanning
			if (actEndReplanningCounter == 0) MyQueueSimEngine.doActEndReplanning(false);
			else MyQueueSimEngine.doActEndReplanning(true);
			
			if (leaveLinkReplanningCounter == 0) MyQueueSimEngine.doLeaveLinkReplanning(false);
			else MyQueueSimEngine.doLeaveLinkReplanning(true);
			
		}

		log.info("No Replanning Probability: " + pNoReplanning);
		log.info("Initial Replanning Probability: " + pInitialReplanning);
		log.info("Act End Replanning Probability: " + pActEndReplanning);
		log.info("Leave Link Replanning Probability: " + pLeaveLinkReplanning);
		
		double numPersons = this.population.getPersons().size();
		log.info(noReplanningCounter + " persons don't replan their Plans (" + noReplanningCounter / numPersons * 100.0 + "%)");
		log.info(initialReplanningCounter + " persons replan their plans initially (" + initialReplanningCounter / numPersons * 100.0 + "%)");
		log.info(actEndReplanningCounter + " persons replan their plans after an activity (" + actEndReplanningCounter / numPersons * 100.0 + "%)");
		log.info(leaveLinkReplanningCounter + " persons replan their plans at each node (" + leaveLinkReplanningCounter / numPersons * 100.0 + "%)");
/*		
		int counter = 0;
		
		Iterator<Person> PersonIterator = this.getPopulation().getPersons().values().iterator();
		while (PersonIterator.hasNext())
		{		
			Person p = PersonIterator.next();
			
			// count persons - is decreased again, if a person is replanning
			noReplanningCounter++;
			
			counter++;
			if(counter < 1000000)
			{
				Map<String,Object> customAttributes = p.getCustomAttributes();
				customAttributes.put("initialReplanning", new Boolean(true));
				customAttributes.put("leaveLinkReplanning", new Boolean(true));
				customAttributes.put("endActivityReplanning", new Boolean(true));
				
				// (de)activate replanning
				MyQueueNetwork.doLeaveLinkReplanning(true);
				MyQueueNetwork.doActEndReplanning(true);
			}
			else
			{
				Map<String,Object> customAttributes = p.getCustomAttributes();
				customAttributes.put("initialReplanning", new Boolean(false));
				customAttributes.put("leaveLinkReplanning", new Boolean(false));
				customAttributes.put("endActivityReplanning", new Boolean(false));
				 
				// deactivate replanning
				MyQueueNetwork.doLeaveLinkReplanning(false);
				MyQueueNetwork.doActEndReplanning(false);
			}
		}
*/
	}

	/*
	 * Assigns a replanner to every Person of the population.
	 * Same problem as above: should be part of the PersonAgents, but only
	 * Persons are available in the replanning modules.
	 * 
	 * At the moment: Replanning Modules are assigned hard coded.
	 * Later: Modules are assigned based on probabilities from config files. 
	 */
	protected void setReplanners()
	{
		Iterator<PersonImpl> PersonIterator = this.getPopulation().getPersons().values().iterator();
		while (PersonIterator.hasNext())
		{
			PersonImpl p = PersonIterator.next();
		
			Map<String,Object> customAttributes = p.getCustomAttributes();
//			customAttributes.put("Replanner", replanners.get(0));	// A*
//			customAttributes.put("Replanner", replanners.get(1));	// Random
//			customAttributes.put("Replanner", replanners.get(2));	// Tabu
//			customAttributes.put("Replanner", replanners.get(3));	// Compass
//			customAttributes.put("Replanner", replanners.get(4));	// RandomCompass
			customAttributes.put("Replanner", replanners.get(5));	// DijstraWrapper
//			customAttributes.put("Replanner", replanners.get(6));	// Dijstra
		}
	}
	
	/*
	 * Assigns nodeSelectors to every Person of the population, which are
	 * used to create an activity rooms for every Person. It is possible to
	 * assign more than one Selector to each Person.
	 * If non is selected the Person knows every Node of the network.
	 *
	 * At the moment: Selection Modules are assigned hard coded.
	 * Later: Modules are assigned based on probabilities from config files.
	 * 
	 * If no NodeSelectors is added (the ArrayList is initialized but empty)
	 * the person knows the entire Network (KnowledgeTools.knowsLink(...)
	 * always returns true).
	 */
	protected void setNodeSelectors()
	{		
		// Create NodeSelectorContainer
		Iterator<PersonImpl> PersonIterator = this.getPopulation().getPersons().values().iterator();
		while (PersonIterator.hasNext())
		{	
			PersonImpl p = PersonIterator.next();
		
			Map<String,Object> customAttributes = p.getCustomAttributes();
			
			ArrayList<SelectNodes> personNodeSelectors = new ArrayList<SelectNodes>();
			
			customAttributes.put("NodeSelectors", personNodeSelectors);
		}
		
		// Assign NodeSelectors
		int counter = 0;
		PersonIterator = this.getPopulation().getPersons().values().iterator();
		while (PersonIterator.hasNext())
		{	
			counter++;
			PersonImpl p = PersonIterator.next();
			
			Map<String,Object> customAttributes = p.getCustomAttributes();
			
			ArrayList<SelectNodes> personNodeSelectors = (ArrayList<SelectNodes>)customAttributes.get("NodeSelectors");
		
	//		personNodeSelectors.add(nodeSelectors.get(0));	// Circular NodeSelector
			personNodeSelectors.add(nodeSelectors.get(1));	// Dijkstra NodeSelector
			
//			if (counter >= 1000) break;
		}
	}
	
	/*
	 * Read Maps of Nodes that each Agents "knows" from a file that is specified in config.xml.
	 */
	protected void readKnownNodesMap()
	{
		// reading Selection from file
		boolean readSelection = Boolean.valueOf(this.config.getModule("selection").getValue("readSelection"));
		if (readSelection)
		{
			String path = this.config.getModule("selection").getValue("inputSelectionFile");
			log.info("Path: " + path);

			// reading single File
			new SelectionReaderMatsim(this.network, this.population,(((ScenarioImpl)this.getScenarioData()).getKnowledges())).readFile(path);
			
			//reading multiple Files automatically
			//new SelectionReaderMatsim(this.network, this.population).readMultiFile(path);
			
			log.info("Read input selection file!");
		}
	}
	
	/*
	 * Write Maps of Nodes that each Agents "knows" to a file that is specified in config.xml.
	 */
	protected void writeKownNodesMap()
	{
		// writing Selection to file
		boolean writeSelection = Boolean.valueOf(this.config.getModule("selection").getValue("writeSelection"));
		if (writeSelection)
		{
			String outPutFile = this.config.getModule("selection").getValue("outputSelectionFile");
			String dtdFile = "./src/playground/christoph/knowledge/nodeselection/Selection.dtd";
			
			// write single File
			new SelectionWriter(this.population, outPutFile, dtdFile, "1.0", "dummy").write();
			
			// write multiple Files automatically
			//new SelectionWriter(this.population, outPutFile, dtdFile, "1.0", "dummy").write(10000);

			
			//new SelectionWriter(this.population, getOutputFilename("selection.xml.gz"), "1.0", "dummy").write();	
						
			log.info("Path: " + outPutFile);
		}
	}
		
	protected void initKnowledgeStorageHandler()
	{
		MapKnowledgeDB mapKnowledgeDB = new MapKnowledgeDB();
		mapKnowledgeDB.createTable();
		//mapKnowledgeDB.clearTable();
	}
		
	protected void setKnowledgeStorageHandler()
	{
		for(PersonImpl person : this.getPopulation().getPersons().values())
		{
			Map<String, Object> customAttributes = person.getCustomAttributes();
/*			
			CellNetworkMapping cellNetworkMapping = new CellNetworkMapping(network);
			cellNetworkMapping.createMapping();
			
			NodeKnowledge nodeKnowledge = new CellKnowledge(cellNetworkMapping);
*/			
			customAttributes.put("NodeKnowledgeStorageType", MapKnowledgeDB.class.getName());
			
			MapKnowledgeDB mapKnowledgeDB = new MapKnowledgeDB();
			mapKnowledgeDB.setPerson(person);
			mapKnowledgeDB.setNetwork(network);
			
			customAttributes.put("NodeKnowledge", mapKnowledgeDB);
		}
	}
	
	
	/*
	 * Creates the Maps of Nodes that each Agents "knows".
	 */
	protected void createKnownNodes()
	{
		// create Known Nodes Maps on multiple Threads
		ParallelCreateKnownNodesMap.run(this.population, this.network, nodeSelectors, 4);
		
//		writePersonKML(this.population.getPerson("100139"));
		
		// non multi-core calculation
//		CreateKnownNodesMap.collectAllSelectedNodes(this.population, this.network);

	}	// setNodes()

	
	protected void doInitialReplanning()
	{
		ArrayList<PersonImpl> personsToReplan = new ArrayList<PersonImpl>();
		
		for (PersonImpl person : this.getPopulation().getPersons().values())
		{
			boolean replanning = (Boolean)person.getCustomAttributes().get("initialReplanning");
			
			if (replanning)
			{
				personsToReplan.add(person);
			}
		}
		
		double time = 0.0;
		// Remove Knowledge after replanning to save memory.
		ParallelInitialReplanner.setRemoveKnowledge(true);
		// Run Replanner.
		ParallelInitialReplanner.run(personsToReplan, time);

		// Number of Routes that could not be created...
		log.info(RandomRoute.getErrorCounter() + " Routes could not be created by RandomRoute.");
		log.info(TabuRoute.getErrorCounter() + " Routes could not be created by TabuRoute.");
		log.info(CompassRoute.getErrorCounter() + " Routes could not be created by CompassRoute.");
		log.info(RandomCompassRoute.getErrorCounter() + " Routes could not be created by RandomCompassRoute.");
		log.info(DijkstraWrapper.getErrorCounter() + " Routes could not be created by DijkstraWrapper.");
		
/*
		for (Person person : this.getPopulation().getPersons().values())
		{			
			boolean replanning = (Boolean)person.getCustomAttributes().get("initialReplanning");
			
			if (replanning)
			{
				KnowledgePlansCalcRoute replanner = (KnowledgePlansCalcRoute)replanners.get(1);
				replanner.setPerson(person);
				replanner.run(person.getSelectedPlan());
			}
		}
*/	
	} //doInitialReplanning
	
	// removes all plans, that are currently not selectedS
	protected void clearPlans()
	{
		for (PersonImpl person : this.getPopulation().getPersons().values())
		{
			person.removeUnselectedPlans();
		}
	}
	
	protected void writePersonKML(PersonImpl person)
	{
		KMLPersonWriter test = new KMLPersonWriter(network, person);
		
		// set CoordinateTransformation
		String coordSys = this.config.global().getCoordinateSystem();
		if(coordSys.equalsIgnoreCase("GK4")) test.setCoordinateTransformation(new GK4toWGS84());
		if(coordSys.equalsIgnoreCase("Atlantis")) test.setCoordinateTransformation(new AtlantisToWGS84());
		if(coordSys.equalsIgnoreCase("CH1903_LV03")) test.setCoordinateTransformation(new CH1903LV03toWGS84());
		
		// waiting for an implementation...
		//new WGS84toCH1903LV03();
		
		String outputDirectory = this.config.controler().getOutputDirectory();
		test.setOutputDirectory(outputDirectory);
		
		test.writeFile();
	}

	@Override
	protected void setUp()
	{
		super.setUp();
	
		if (calcWardrop)
		{
			actTimesCollector = new ActTimesCollector();
			
			actTimesCollector.setNetwork(this.network);
			actTimesCollector.setPopulation(this.population);
			
	//		actTimesCollector.setStartTime(startTime);
	//		actTimesCollector.setEndTime(endTime);
	
			events.addHandler(actTimesCollector);
		}
		
	}
	
	@Override
	protected void shutdown(final boolean unexpected)
	{
		if (calcWardrop)
		{
			log.info("");
			Wardrop wardrop = new Wardrop(network, population);
			
			//wardrop.setActTimesCollector(this.actTimesCollector);
/*			
			// with length Correction
			wardrop.setUseLengthCorrection(true);
			wardrop.fillMatrixViaActTimesCollectorObject(this.actTimesCollector);		
			wardrop.getResults();
			log.info("");
*/	
			// without length Correction
			wardrop.setUseLengthCorrection(false);
			wardrop.fillMatrixViaActTimesCollectorObject(this.actTimesCollector);		
			wardrop.getResults();
			log.info("");
		}
		
		super.shutdown(unexpected);
	}
	

	public void setConfig(Config config)
	{
		this.config.config();
	}
	
	/* ===================================================================
	 * main
	 * =================================================================== */

	public static void main(final String[] args) {
		if ((args == null) || (args.length == 0)) {
			System.out.println("No argument given!");
			System.out.println("Usage: Controler config-file [dtd-file]");
			System.out.println();
		} else {
			final EventControler controler = new EventControler(args);			
			controler.setOverwriteFiles(true);
			controler.run();
		}
		System.exit(0);
	}

	
}
