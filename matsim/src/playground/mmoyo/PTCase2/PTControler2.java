package playground.mmoyo.PTCase2;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

import org.matsim.interfaces.basic.v01.BasicNode;
import org.matsim.interfaces.basic.v01.Coord;
import org.matsim.interfaces.core.v01.Node;
import org.matsim.router.util.LeastCostPathCalculator.Path;
import org.matsim.utils.geometry.CoordImpl;

import playground.mmoyo.Validators.NetValidator;
import playground.mmoyo.input.PTNodeFactory;
import playground.mmoyo.input.PTNodeReader;
import playground.mmoyo.input.PTLinkFactory;

public class PTControler2 {
    private static String path = "../shared-svn/studies/schweiz-ivtch/pt-experimental/"; 
    //private static String path = "C://Users/manuel/Desktop/TU/scenarios/Zuerich/";
    //private static String path = "C://Users/manuel/Desktop/TU/scenarios/5x5";
    
	private static final String CONFIG= "../shared-svn/studies/schweiz-ivtch/pt-experimental/config.xml";
	private static final String ZURICHPTN= path + "network.xml";
	private static final String ZURICHPTTIMETABLE= path + "PTTimetable.xml";
	private static final String INPTNETFILE= path + "inptnetfile.xml";
	private static final String ZURICHPTPLANS= path + "plans.xml";
	private static final String OUTPUTPLANS= path + "output_plans.xml";
	
	public static void main(String[] args){
		PTOb pt= new PTOb(CONFIG, INPTNETFILE, ZURICHPTN, ZURICHPTTIMETABLE,ZURICHPTPLANS, OUTPUTPLANS); 
		
		int option =8;
		
		if (option>0){pt.readPTNet(ZURICHPTN);}
		switch (option){
			
			case -3:
				String filePath="C://Users/manuel/Desktop/TU/ZH_Files/bus/Basic_Bus_Network.xml";
	    	
				
				PTNodeReader ptNodeReader = new PTNodeReader();
	    		ptNodeReader.readFile (filePath);
	    		
	    		List<List<BasicNode>> nodeListList = ptNodeReader.getNodeLists();
	    		PTNodeFactory ptNodeFactory = new PTNodeFactory(pt.getPtNetworkLayer(), pt.getPtTimeTable());
	    		PTLinkFactory ptLinkFactory= new PTLinkFactory (pt.getPtNetworkLayer());

	    		//System.out.println(nodeListList.size());
	    		
	    		int iniNodes= pt.getPtNetworkLayer().getNodes().size();
	    		int iniLinks= pt.getPtNetworkLayer().getLinks().size();
	    		System.out.println("creating new nodes and links...");
	    		
	    		BasicNode[] basicNodeArr = new BasicNode[2];
	    		List<BasicNode> basicNodeList1 = new ArrayList<BasicNode>();
	    		List<BasicNode> basicNodeList2 = new ArrayList<BasicNode>();
	    		for(List<BasicNode> basicNodeList: nodeListList){
	    			for (BasicNode basicNode : basicNodeList){
	    				basicNodeArr = ptNodeFactory.CreatePTNodes(basicNode);
	    				basicNodeList1.add(basicNodeArr[0]);
	    				basicNodeList2.add(basicNodeArr[1]);
	    			}
	    			ptLinkFactory.createLinks(basicNodeList1);
	    			ptLinkFactory.createLinks(basicNodeList2);
	    		}
	    		System.out.println("Done.");
	    		
	    		int finNodes= pt.getPtNetworkLayer().getNodes().size() - iniNodes;
	    		int finLinks= pt.getPtNetworkLayer().getLinks().size() - iniLinks;
	    		
	    		System.out.println("created Nodes:" + finNodes);
	    		System.out.println("created Links:" + finLinks);
	    		
	    		System.out.println(pt.getPtNetworkLayer().toString());
	    		
	    		//--<y despues de crear los nuevos nodos y links hay que volver a crear los transfers and detached links

				
				
				
		
				break;
			case -2:
				pt.createPTNetWithTLinks(INPTNETFILE,ZURICHPTN);
	    		pt.readPTNet(ZURICHPTN);
	    		NetValidator netValidator = new NetValidator(pt.getPtNetworkLayer(), pt.getPtTimeTable());
	    		netValidator.printNegativeTransferCosts(28800);
				break;
			case -1:
				pt.createPTNetWithTLinks(INPTNETFILE,ZURICHPTN);
				pt.getPtNetworkFactory().CreateDetachedTransfers(pt.getPtNetworkLayer(), 300);
	    		pt.getPtNetworkFactory().writeNet(pt.getPtNetworkLayer(), ZURICHPTN);
	    		pt.readPTNet(ZURICHPTN);
   		
	    		PTActWriter ptActWriter1 = new PTActWriter(pt);
	    		ptActWriter1.writePTActsLegs();
	    		
				break;
			case 0: 
	    		pt.createPTNetWithTLinks(INPTNETFILE,ZURICHPTN);
	    		
	    		//Map<String, List<IdImpl>> intersecionMap = pt.getPtNetworkFactory().createIntersecionMap(pt.getPtTimeTable());
	    		//new StationValidator().validateStations(pt.getPtNetworkLayer(),intersecionMap);
	    		    		
	    		break;
	    	case 1:
	    		Coord coord1= new CoordImpl(708146,243607);
    			Coord coord2= new CoordImpl(709915,244793);
	    		Path path = pt.getPtRouter2().findRoute(coord1, coord2, 24060,400);
	    		//ptNetworkFactory.printLinks(ptNetworkLayer);
	    		pt.getPtRouter2().PrintRoute(path);
	    		break;
	    	case 2:
	    		Node ptNode = pt.getPtNetworkLayer().getNode("100048");
	    		Node ptNode2 = pt.getPtNetworkLayer().getNode("101456");
	    		pt.getPtRouter2().PrintRoute(pt.getPtRouter2().findRoute(ptNode, ptNode2, 391));
	    		break;
	    	
	    	case 3:
	    		long startTime = System.currentTimeMillis();
	    		PTActWriter ptActWriter = new PTActWriter(pt);
	    		ptActWriter.writePTActsLegs();
	    		System.out.println("Duration: " + (System.currentTimeMillis()-startTime));	
	    		
	    		ptActWriter.ptTravelTime.costValidator.printNegativeVaues();
	    		//System.out.println(ptActWriter.ptTravelTime.costValidator==null);
	    		break;
	 
	    	case 4:
	    		PTTester pttester= new PTTester(pt);
	    		pttester.countRoutes();
	    		break;
	    	case 5:
	    		//24 feb
	    		//playground.mmoyo.PTRouter.PTNProximity ptnProximity = new playground.mmoyo.PTRouter.PTNProximity(pt.getPtNetworkLayer());  

	    		Coord coord6= new CoordImpl(708146,243607);
	    		double distance= 4500;
	    		Collection <Node> nearNodes = pt.getPtNetworkLayer().getNearestNodes(coord6, distance);
	    		
	    		System.out.println ("Nodes near " + coord6.toString());
	    		for (Node nearNode : nearNodes){
	    			System.out.println (nearNode.getId());
	    		}
	    		break;
	    	case 6:
	      		//--> implement better createTransferLinks2
	    		//pt.getPtNetworkFactory().createTransferLinks2(pt.getPtNetworkLayer(),IntersecionMap );
	    		pt.getPtNetworkFactory().CreateDetachedTransfers(pt.getPtNetworkLayer(), 200);
	    		pt.getPtNetworkFactory().writeNet(pt.getPtNetworkLayer(), ZURICHPTN);
	    		break;
	    	case 7:
	    		Node nodeA= pt.getPtNetworkLayer().getNode("~101220");     
	    		Node nodeB=pt.getPtNetworkLayer().getNode("~8587652");
	    		System.out.println(nodeA==null);
	    		System.out.println(nodeB==null);
	    		boolean unidos= pt.getPtNetworkFactory().areConected(nodeA, nodeB);
	    		System.out.println(unidos);
	    		break;
	    	case 8:
	    		    		break;
		}//switch
	}//main

	
}//Class
