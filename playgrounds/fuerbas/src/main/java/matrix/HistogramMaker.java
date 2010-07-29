package matrix;

import gnu.trove.TDoubleDoubleHashMap;
import gnu.trove.TDoubleObjectHashMap;
import gnu.trove.TDoubleObjectIterator;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectLongHashMap;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.ScenarioImpl;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.sna.math.Distribution;
import org.matsim.core.network.MatsimNetworkReader;

public class HistogramMaker {
	
	private static final Logger logger = Logger.getLogger(HistogramMaker.class);

	private static final String outputDir = "/Users/jillenberger/Work/work/socialnets/data/schweiz/network/";
	
	private static final String netFile = "/Users/jillenberger/Work/work/socialnets/data/schweiz/network/switzerland_matsim_cl_simple.xml";
	
	private static final String bcFile = "/Users/jillenberger/Work/work/socialnets/data/schweiz/network/BetweennessSchweiz.txt";
	
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		/*
		 * Load network
		 */
		logger.info("Loading network...");
		Scenario scenario = new ScenarioImpl();
		MatsimNetworkReader netReader = new MatsimNetworkReader(scenario);
		netReader.readFile(netFile);
		Network network = scenario.getNetwork();
		/*
		 * Create node list...
		 */
		logger.info("Indexing nodes...");
		List<Node> nodeList = new ArrayList<Node>(network.getNodes().size());
		for(Node node : network.getNodes().values()) {
			nodeList.add(node);
		}
		/*
		 * Load betweenness data
		 */
		Set<Link> linkSet = new HashSet<Link>(network.getLinks().size()/2);
		
		TObjectLongHashMap<Link> values = new TObjectLongHashMap<Link>();
		logger.info("Loading betweenness data...");
		BufferedReader reader = new BufferedReader(new FileReader(bcFile));
		String line;
		while((line = reader.readLine()) != null) {
			String tokens[] = line.split(" ");
			
			int fromNodeIdx = Integer.parseInt(tokens[1]);
			int toNodeIdx = Integer.parseInt(tokens[3]);
			
			Node fromNode = nodeList.get(fromNodeIdx);
			Node toNode = nodeList.get(toNodeIdx);
			
			Link link = null;
			for(Link outLink : fromNode.getOutLinks().values()) {
				if(outLink.getToNode() == toNode)
					link = outLink;
			}
			
			if(link == null) {
				logger.warn("Link not found!");
				System.exit(-1);
			}
			linkSet.add(link);
			
			line = reader.readLine();
			
			long value = Long.parseLong(line);
			if(value < 0) {
				logger.warn("Value < 0!");
				System.exit(-1);
			}
			values.put(link, value);
		}
		/*
		 * Categorize links according to their capacity.
		 */
		logger.info("Categorizing links....");
		TDoubleObjectHashMap<Set<Link>> linkCats = new TDoubleObjectHashMap<Set<Link>>();
		for(Link link : linkSet) {
			Set<Link> links = linkCats.get(link.getCapacity());
			if (links == null) {
				links = new HashSet<Link>();
				linkCats.put(link.getCapacity(), links);
			}
			links.add(link);
			
		}
		/*
		 * Create a histogram for each category
		 */
		logger.info("Creating histograms...");
		TDoubleObjectIterator<Set<Link>> it = linkCats.iterator();
		for(int i = 0; i < linkCats.size(); i++) {
			it.advance();
			Set<Link> links = it.value();
			
			TDoubleDoubleHashMap hist = new TDoubleDoubleHashMap();
			
			for(Link link : links) {
				hist.adjustOrPutValue(values.get(link), 1.0, 1.0);
			}
			
			Distribution.writeHistogram(hist, String.format("%1$s/linkbc.%2$s.txt", outputDir, Integer.valueOf((int) it.key())));
		}
	}

}
