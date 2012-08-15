/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
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
package playground.droeder.southAfrica.replanning;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.router.TransitRouterConfig;
import org.matsim.pt.router.TransitRouterImpl;
import org.matsim.pt.router.TransitRouterNetworkTravelTimeAndDisutility;
import org.matsim.pt.transitSchedule.TransitScheduleFactoryImpl;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

/**
 * @author droeder
 *
 */
public class PtSubModeDependendRouter implements TransitRouter{
	private static final Logger log = Logger
			.getLogger(PtSubModeDependendRouter.class);
	
	private TransitRouterNetworkTravelTimeAndDisutility travelTime;
	private TransitRouterConfig config;
	private TransitRouterNetworkTravelTimeAndDisutility travelDisutility;
	private Object transitNetwork;
	private TransitRouter completeRouter;
	private HashMap<String, TransitRouter> modeRouter = null;

	public PtSubModeDependendRouter(Scenario sc, boolean routeOnSameMode){
		this.config = new TransitRouterConfig(sc.getConfig());
		TransitRouterNetworkTravelTimeAndDisutility transitRouterNetworkTravelTimeAndDisutility = new TransitRouterNetworkTravelTimeAndDisutility(this.config);
		this.travelTime = transitRouterNetworkTravelTimeAndDisutility;
		this.travelDisutility = transitRouterNetworkTravelTimeAndDisutility;
		this.completeRouter = new TransitRouterImpl(this.config, sc.getTransitSchedule()); 
		this.initTransitRouter(sc, routeOnSameMode);
	}
	
	
	/**
	 * @param sc
	 * @param routeOnSameSubMode
	 */
	private void initTransitRouter(Scenario sc, boolean routeOnSameSubMode) {
		if(routeOnSameSubMode) return; //create additional router only if necessary
		log.info("separating lines by mode from transitSchedule...");
		Map<String, TransitSchedule> temp = new HashMap<String, TransitSchedule>();
		for(String s: sc.getConfig().transit().getTransitModes()){
			temp.put(s, new TransitScheduleFactoryImpl().createTransitSchedule());
		}
		
		String mode = null;
		for(TransitLine line : sc.getTransitSchedule().getTransitLines().values()){
			for(TransitRoute route: line.getRoutes().values()){
				if(mode == null){
					mode = route.getTransportMode();
				}else{
					if(mode != route.getTransportMode()){
						throw new IllegalArgumentException("one line must not operate on different transport-modes...");
					}
				}
			}
			if(temp.containsKey(mode)){
				temp.get(mode).addTransitLine(line);
				for(TransitRoute route: line.getRoutes().values()){
					for(TransitRouteStop stop: route.getStops()){
						if(!temp.get(mode).getFacilities().containsKey(stop.getStopFacility().getId())){
							temp.get(mode).getFacilities().put(stop.getStopFacility().getId(), stop.getStopFacility());
						}
					}
				}
			}else{
				log.error("no mode specified for routes of transitline " + line.getId());
			}
			mode = null;
		}
		log.info("finished...");
		log.info("creating mode-dependend transitRouter for: " + temp.keySet().toString());
		this.modeRouter = new HashMap<String, TransitRouter>();
		for(Entry<String, TransitSchedule> e: temp.entrySet()){
			this.modeRouter.put(e.getKey(), new TransitRouterImpl(this.config, e.getValue()));
		}
		log.info("finished");
	}



	@Override
	public List<Leg> calcRoute(Coord fromCoord, Coord toCoord, double departureTime, Person person) {
		throw new UnsupportedOperationException("use class own's calcRoute!");
	}

}
