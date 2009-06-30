/* *********************************************************************** *
 * project: org.matsim.*
 * BasicScenarioImpl.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
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

package org.matsim.api.basic.v01;

import org.matsim.api.basic.v01.network.BasicLink;
import org.matsim.api.basic.v01.network.BasicNetwork;
import org.matsim.api.basic.v01.network.BasicNode;
import org.matsim.api.basic.v01.population.BasicPerson;
import org.matsim.api.basic.v01.population.BasicPopulation;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.config.Config;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.network.NetworkLayer;
import org.matsim.core.population.PopulationImpl;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.households.basic.BasicHousehold;
import org.matsim.households.basic.BasicHouseholds;
import org.matsim.vehicles.BasicVehiclesImpl;

public class BasicScenarioImpl implements BasicScenario {

	private final Config config;
	private final BasicNetwork<?, ?> network;
	private final BasicPopulation<?> population;
	private BasicHouseholds<BasicHousehold> households;
	private BasicVehiclesImpl vehicles;
	
	public BasicScenarioImpl() {
		this(new Config());
		this.config.addCoreModules();
	}
	
	public BasicScenarioImpl(Config config) {
		this.config = config;

		this.network = new NetworkLayer();  
		// TODO should be changed to a basic implementation
		// I think that the full implementation is ok.  But should become a "normal" 
		// implementation (not a "Layer"). kai, jun09
		
		//never use the next line in new matsim code
		Gbl.getWorld().setNetworkLayer((NetworkLayer)this.network);

		this.population = new PopulationImpl();
	}

	public BasicNetwork<? extends BasicNode, ? extends BasicLink> getNetwork() {
		return this.network;
	}

	public BasicPopulation<? extends BasicPerson> getPopulation() {
		return this.population;
	}

	public Config getConfig() {
		return this.config;
	}

	public Id createId(String string) {
		return new IdImpl(string);
	}

	public Coord createCoord(double x, double y) {
		return new CoordImpl(x, y);
	}

}
