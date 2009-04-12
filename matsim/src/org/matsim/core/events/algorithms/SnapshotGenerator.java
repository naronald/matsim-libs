/* *********************************************************************** *
 * project: org.matsim.*
 * SnapshotGenerator.java
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

package org.matsim.core.events.algorithms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;
import org.matsim.api.basic.v01.Id;
import org.matsim.api.basic.v01.network.BasicLink;
import org.matsim.api.basic.v01.network.BasicNetwork;
import org.matsim.core.api.network.Link;
import org.matsim.core.api.network.Network;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.events.AgentArrivalEvent;
import org.matsim.core.events.AgentDepartureEvent;
import org.matsim.core.events.AgentStuckEvent;
import org.matsim.core.events.AgentWait2LinkEvent;
import org.matsim.core.events.LinkEnterEvent;
import org.matsim.core.events.LinkLeaveEvent;
import org.matsim.core.events.PersonEvent;
import org.matsim.core.events.handler.AgentArrivalEventHandler;
import org.matsim.core.events.handler.AgentDepartureEventHandler;
import org.matsim.core.events.handler.AgentStuckEventHandler;
import org.matsim.core.events.handler.AgentWait2LinkEventHandler;
import org.matsim.core.events.handler.LinkEnterEventHandler;
import org.matsim.core.events.handler.LinkLeaveEventHandler;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.vis.netvis.DisplayNetStateWriter;
import org.matsim.vis.netvis.DrawableAgentI;
import org.matsim.vis.netvis.VisConfig;
import org.matsim.vis.snapshots.writers.PositionInfo;
import org.matsim.vis.snapshots.writers.SnapshotWriter;

public class SnapshotGenerator implements AgentDepartureEventHandler, AgentArrivalEventHandler, LinkEnterEventHandler,
		LinkLeaveEventHandler, AgentWait2LinkEventHandler, AgentStuckEventHandler {

	private final Network network;
	private int lastSnapshotIndex = -1;
	private final double snapshotPeriod;
	protected final HashMap<Id, EventLink> eventLinks;
	private final HashMap<Id, EventAgent> eventAgents;
	private final List<SnapshotWriter> snapshotWriters = new ArrayList<SnapshotWriter>();
	private final double capCorrectionFactor;
	private final String snapshotStyle;

	public SnapshotGenerator(final Network network, final double snapshotPeriod) {
		this.network = network;
		this.eventLinks = new HashMap<Id, EventLink>((int)(network.getLinks().size()*1.1), 0.95f);
		this.eventAgents = new HashMap<Id, EventAgent>(1000, 0.95f);
		this.snapshotPeriod = snapshotPeriod;
		this.capCorrectionFactor = Gbl.getConfig().simulation().getFlowCapFactor() / network.getCapacityPeriod();
		this.snapshotStyle = Gbl.getConfig().simulation().getSnapshotStyle();
		reset(-1);
	}

	public void addSnapshotWriter(final SnapshotWriter writer) {
		this.snapshotWriters.add(writer);
	}

	public boolean removeSnapshotWriter(final SnapshotWriter writer) {
		return this.snapshotWriters.remove(writer);
	}

	public void handleEvent(final AgentDepartureEvent event) {
		testForSnapshot(event.getTime());
		this.eventLinks.get(event.getLinkId()).departure(getEventAgent(event));
	}

	public void handleEvent(final AgentArrivalEvent event) {
		testForSnapshot(event.getTime());
		this.eventLinks.get(event.getLinkId()).arrival(getEventAgent(event));
	}

	public void handleEvent(final LinkEnterEvent event) {
		testForSnapshot(event.getTime());
		this.eventLinks.get(event.getLinkId()).enter(getEventAgent(event));
	}

	public void handleEvent(final LinkLeaveEvent event) {
		testForSnapshot(event.getTime());
		this.eventLinks.get(event.getLinkId()).leave(getEventAgent(event));
	}

	public void handleEvent(final AgentWait2LinkEvent event) {
		testForSnapshot(event.getTime());
		this.eventLinks.get(event.getLinkId()).wait2link(getEventAgent(event));
	}

	public void handleEvent(final AgentStuckEvent event) {
		testForSnapshot(event.getTime());
		this.eventLinks.get(event.getLinkId()).stuck(getEventAgent(event));
	}

	public void reset(final int iteration) {
		this.eventLinks.clear();
		for (Link link : this.network.getLinks().values()) {
			this.eventLinks.put(link.getId(), new EventLink(link, this.capCorrectionFactor, this.network.getEffectiveCellSize()));
		}
		this.eventAgents.clear();
		this.lastSnapshotIndex = -1;
	}

	private EventAgent getEventAgent(final PersonEvent event) {
		EventAgent agent = this.eventAgents.get(event.getPersonId());
		if (agent == null) {
			agent = new EventAgent(event.getPersonId().toString(), event.getTime());
			this.eventAgents.put(event.getPersonId(), agent);
		}
		agent.time = event.getTime();
		return agent;
	}

	private void testForSnapshot(final double time) {
		int snapshotIndex = (int) (time / this.snapshotPeriod);
		if (this.lastSnapshotIndex == -1) {
			this.lastSnapshotIndex = snapshotIndex;
		}
		while (snapshotIndex > this.lastSnapshotIndex) {
			this.lastSnapshotIndex++;
			double snapshotTime = this.lastSnapshotIndex * this.snapshotPeriod;
			doSnapshot(snapshotTime);
		}
	}

	private void doSnapshot(final double time) {
		System.out.println("create snapshot at " + Time.writeTime(time));
		if (!this.snapshotWriters.isEmpty()) {
			Collection<PositionInfo> positions = getVehiclePositions(time);
			for (SnapshotWriter writer : this.snapshotWriters) {
				writer.beginSnapshot(time);
				for (PositionInfo position : positions) {
					writer.addAgent(position);
				}
				writer.endSnapshot();
			}
		}
	}

	private Collection<PositionInfo> getVehiclePositions(final double time) {
		Collection<PositionInfo> positions = new ArrayList<PositionInfo>();
		if ("queue".equals(this.snapshotStyle)) {
			for (EventLink link : this.eventLinks.values()) {
				link.getVehiclePositionsQueue(positions, time);
			}
		} else if ("equiDist".equals(this.snapshotStyle)) {
			for (EventLink link : this.eventLinks.values()) {
				link.getVehiclePositionsEquil(positions, time);
			}
		} else {
			Logger.getLogger(this.getClass()).warn("The snapshotStyle \"" + this.snapshotStyle + "\" is not supported.");
		}
		return positions;
	}

	public void finish() {
		for (SnapshotWriter writer : this.snapshotWriters) {
			writer.finish();
		}
	}

	private static class EventLink {
		private final Link link;
		protected final List<EventAgent> drivingQueue;
		private final List<EventAgent> parkingQueue;
		private final List<EventAgent> waitingQueue;
		protected final List<EventAgent> buffer;

		private final double euklideanDist;
		private final double freespeedTravelTime;
		protected final double spaceCap;
		private final double timeCap;
		private final double inverseTimeCap;

		private final double ratioLengthToEuklideanDist; // ratio of link.length / euklideanDist
		private final double effectiveCellSize;

		protected EventLink(final Link link2, final double capCorrectionFactor, final double effectiveCellSize) {
			this.link = link2;
			this.drivingQueue = new ArrayList<EventAgent>();
			this.parkingQueue = new ArrayList<EventAgent>();
			this.waitingQueue = new ArrayList<EventAgent>();
			this.buffer = new ArrayList<EventAgent>();
			this.euklideanDist = CoordUtils.calcDistance(link2.getFromNode().getCoord(), link2.getToNode().getCoord());
			this.ratioLengthToEuklideanDist = this.link.getLength() / this.euklideanDist;
			this.freespeedTravelTime = this.link.getLength() / this.link.getFreespeed(Time.UNDEFINED_TIME);
			this.timeCap = this.link.getCapacity(org.matsim.core.utils.misc.Time.UNDEFINED_TIME) * capCorrectionFactor;
			this.inverseTimeCap = 1.0 / this.timeCap;
			this.effectiveCellSize = effectiveCellSize;
			this.spaceCap = (this.link.getLength() * this.link.getNumberOfLanes(org.matsim.core.utils.misc.Time.UNDEFINED_TIME)) / this.effectiveCellSize * Gbl.getConfig().simulation().getStorageCapFactor();
		}

		protected void enter(final EventAgent agent) {
			if (agent.currentLink != null) {
				agent.currentLink.stuck(agent); // use stuck to remove it from wherever it is
			}
			agent.currentLink = this;
			this.drivingQueue.add(agent);
		}

		protected void leave(final EventAgent agent) {
			this.drivingQueue.remove(agent);
			this.buffer.remove(agent);
			agent.currentLink = null;
		}

		protected void arrival(final EventAgent agent) {
			this.buffer.remove(agent);
			this.drivingQueue.remove(agent);
			this.parkingQueue.add(agent);
		}

		protected void departure(final EventAgent agent) {
			agent.currentLink = this;
			this.parkingQueue.remove(agent);
			this.waitingQueue.add(agent);
		}

		protected void wait2link(final EventAgent agent) {
			this.waitingQueue.remove(agent);
			this.buffer.add(agent);
		}

		protected void stuck(final EventAgent agent) {
			// vehicles can be anywhere when they get stuck
			this.drivingQueue.remove(agent);
			this.parkingQueue.remove(agent);
			this.waitingQueue.remove(agent);
			this.buffer.remove(agent);
			agent.currentLink = null;
		}

		/**
		 * Calculates the positions of all vehicles on this link according to the queue-logic: Vehicles are placed on the link
		 * according to the ratio between the free-travel time and the time the vehicles are already on the link. If they could
		 * have left the link already (based on the time), the vehicles start to build a traffic-jam (queue) at the end of the link.
		 *
		 * @param positions A collection where the calculated positions can be stored.
		 * @param time The current timestep
		 */
		protected void getVehiclePositionsQueue(final Collection<PositionInfo> positions, final double time) {
			double queueEnd = this.link.getLength(); // the length of the queue jammed vehicles build at the end of the link
			double storageCapFactor = Gbl.getConfig().simulation().getStorageCapFactor();
			double vehLen = Math.min(	// the length of a vehicle in visualization
					this.euklideanDist / this.spaceCap, // all vehicles must have place on the link
					this.effectiveCellSize / storageCapFactor); // a vehicle should not be larger than it's actual size

			// put all cars in the buffer one after the other
			for (EventAgent agent : this.buffer) {

				int lane = 1 + (agent.intId % this.link.getLanesAsInt(org.matsim.core.utils.misc.Time.UNDEFINED_TIME));

				int cmp = (int) (agent.time + this.freespeedTravelTime + this.inverseTimeCap + 2.0);
				double speed = (time > cmp) ? 0.0 : this.link.getFreespeed(time);
				agent.speed = speed;

				PositionInfo position = new PositionInfo(agent.id,
						this.link, queueEnd/* + NetworkLayer.CELL_LENGTH*/,
						lane, speed, PositionInfo.VehicleState.Driving,null);
				agent.linkPosition = queueEnd * this.ratioLengthToEuklideanDist;
				positions.add(position);
				queueEnd -= vehLen;
			}

			/* place other driving cars according the following rule:
			 * - calculate the time how long the vehicle is on the link already
			 * - calculate the position where the vehicle should be if it could drive with freespeed
			 * - if the position is already within the congestion queue, add it to the queue with slow speed
			 * - if the position is not within the queue, just place the car with free speed at that place
			 */
			double lastDistance = Integer.MAX_VALUE;
			for (EventAgent agent : this.drivingQueue) {
				double travelTime = time - agent.time;
				double distanceOnLink = (this.freespeedTravelTime == 0.0 ? 0.0 : ((travelTime / this.freespeedTravelTime) * this.euklideanDist));
				if (distanceOnLink > queueEnd) { // vehicle is already in queue
					distanceOnLink = queueEnd;
					queueEnd -= vehLen;
				}
				if (distanceOnLink >= lastDistance) {
					/* we have a queue, so it should not be possible that one vehicles overtakes another.
					 * additionally, if two vehicles entered at the same time, they would be drawn on top of each other.
					 * we don't allow this, so in this case we put one after the other. Theoretically, this could lead to
					 * vehicles placed at negative distance when a lot of vehicles all enter at the same time on an empty
					 * link. not sure what to do about this yet... just setting them to 0 currently.
					 */
					distanceOnLink = lastDistance - vehLen;
					if (distanceOnLink < 0) distanceOnLink = 0.0;
				}
				int cmp = (int) (agent.time + this.freespeedTravelTime + this.inverseTimeCap + 2.0);
				double speed = (time > cmp) ? 0.0 : this.link.getFreespeed(time);
				agent.speed = speed;
				int lane = 1 + (agent.intId % this.link.getLanesAsInt(org.matsim.core.utils.misc.Time.UNDEFINED_TIME));
				PositionInfo position = new PositionInfo(agent.id,
						this.link, distanceOnLink/* + NetworkLayer.CELL_LENGTH*/,
						lane, speed, PositionInfo.VehicleState.Driving,null);
				positions.add(position);
				agent.linkPosition = distanceOnLink * this.ratioLengthToEuklideanDist;
				lastDistance = distanceOnLink;
			}

			/* Put the vehicles from the waiting list in positions.
			 * Their actual position doesn't matter, so they are just placed
			 * to the coordinates of the from node */
			int lane = this.link.getLanesAsInt(org.matsim.core.utils.misc.Time.UNDEFINED_TIME) + 1; // place them next to the link
			for (EventAgent agent : this.waitingQueue) {
				PositionInfo position = new PositionInfo(agent.id,
						this.link, this.effectiveCellSize, lane, 0.0, PositionInfo.VehicleState.Parking,null);
				positions.add(position);
			}

			/* put the vehicles from the parking list in positions
			 * their actual position doesn't matter, so they are just placed
			 * to the coordinates of the from node */
			lane = this.link.getLanesAsInt(org.matsim.core.utils.misc.Time.UNDEFINED_TIME) + 2; // place them next to the link
			for (EventAgent agent : this.parkingQueue) {
				PositionInfo position = new PositionInfo(agent.id,
						this.link, this.effectiveCellSize, lane, 0.0, PositionInfo.VehicleState.Parking,null);
				positions.add(position);
			}
		}

		/**
		 * Calculates the positions of all vehicles on this link so that there is always the same distance between following cars.
		 * A single vehicle will be placed at the middle (0.5) of the link, two cars will be placed at positions 0.25 and 0.75,
		 * three cars at positions 0.16, 0.50, 0.83, and so on.
		 *
		 * @param positions A collection where the calculated positions can be stored.
		 * @param time The current timestep
		 */
		protected void getVehiclePositionsEquil(final Collection<PositionInfo> positions, final double time) {
			int cnt = this.buffer.size() + this.drivingQueue.size();
			int nLanes = this.link.getLanesAsInt(org.matsim.core.utils.misc.Time.UNDEFINED_TIME);
			if (cnt > 0) {
				double cellSize = this.link.getLength() / cnt;
				double distFromFromNode = this.link.getLength() - cellSize / 2.0;
				double freespeed = this.link.getFreespeed(time);

				// the cars in the buffer
				for (EventAgent agent : this.buffer) {
					agent.lane = 1 + agent.intId % nLanes;
					agent.linkPosition = distFromFromNode;
					int cmp = (int) (agent.time + this.freespeedTravelTime + this.inverseTimeCap + 2.0);
					if (time > cmp) {
						agent.speed = 0.0;
					} else {
						agent.speed = freespeed;
					}
					PositionInfo position = new PositionInfo(agent.id, this.link, distFromFromNode, agent.lane, agent.speed, PositionInfo.VehicleState.Driving, null);
					positions.add(position);
					distFromFromNode -= cellSize;
				}

				// the cars in the drivingQueue
				for (EventAgent agent : this.drivingQueue) {
					agent.lane = 1 + agent.intId % nLanes;
					agent.linkPosition = distFromFromNode;
					int cmp = (int) (agent.time + this.freespeedTravelTime + this.inverseTimeCap + 2.0);
					if (time > cmp) {
						agent.speed = 0.0;
					} else {
						agent.speed = freespeed;
					}
					PositionInfo position = new PositionInfo(agent.id, this.link, distFromFromNode, agent.lane, agent.speed, PositionInfo.VehicleState.Driving, null);
					positions.add(position);
					distFromFromNode -= cellSize;
				}
			}

			// the cars in the waitingQueue
			// the actual position doesn't matter, so they're just placed next to the link at the end
			cnt = this.waitingQueue.size();
			if (cnt > 0) {
				int lane = nLanes + 2;
				double cellSize = Math.min(this.effectiveCellSize, this.link.getLength() / cnt);
				double distFromFromNode = this.link.getLength() - cellSize / 2.0;
				for (EventAgent agent : this.waitingQueue) {
					agent.lane = lane;
					agent.linkPosition = distFromFromNode;
					agent.speed = 0.0;
					PositionInfo position = new PositionInfo(agent.id, this.link, distFromFromNode, agent.lane, agent.speed, PositionInfo.VehicleState.Parking, null);
					positions.add(position);
					distFromFromNode -= cellSize;
				}
			}

			// the cars in the parkingQueue
			// the actual position  doesn't matter, so they're distributed next to the link
			cnt = this.parkingQueue.size();
			if (cnt > 0) {
				int lane = nLanes + 4;
				double cellSize = this.link.getLength() / cnt;
				double distFromFromNode = this.link.getLength() - cellSize / 2.0;
				for (EventAgent agent : this.parkingQueue) {
					agent.lane = lane;
					agent.linkPosition = distFromFromNode;
					agent.speed = 0.0;
					PositionInfo position = new PositionInfo(agent.id, this.link, distFromFromNode, agent.lane, agent.speed, PositionInfo.VehicleState.Parking, null);
					positions.add(position);
					distFromFromNode -= cellSize;
				}
			}
		}
	}

	private static class EventAgent implements Comparable<EventAgent>, DrawableAgentI {
		protected final IdImpl id;
		protected final int intId;
		protected double time;
		protected EventLink currentLink = null;
		protected double speed = 0.0;
		protected int lane = 1;
		protected double linkPosition = 0.0;

		protected EventAgent(final String id, final double time) {
			this.id = new IdImpl(id);
			this.time = time;
			this.intId = id.hashCode();
		}

		public int compareTo(final EventAgent o) {
			return this.id.compareTo(o.id);
		}

		@Override
		public boolean equals(final Object o) {
			if (o instanceof EventAgent) {
				return this.id.equals(((EventAgent) o).id);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return this.id.hashCode();
		}

		/* implementation of DrawableAgentI */

		public int getLane() {
			return this.lane;
		}

		public double getPosInLink_m() {
			return this.linkPosition;
		}
	}

	private class NetStateWriter extends DisplayNetStateWriter implements SnapshotWriter {

		public NetStateWriter(final BasicNetwork network, final String networkFileName,
				final VisConfig visConfig, final String filePrefix, final int timeStepLength_s, final int bufferSize) {
			super(network, networkFileName, visConfig, filePrefix, timeStepLength_s, bufferSize);
		}

		/* implementation of SnapshotWriter */
		public void addAgent(final PositionInfo position) {
		}

		public void beginSnapshot(final double time) {
			try {
				dump((int)time);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public void endSnapshot() {
		}

		public void finish() {
			try {
				close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		/* methods for DisplayNetStateWriter */

		@Override
		protected String getLinkDisplLabel(final BasicLink link) {
			return link.getId().toString();
		}

		@Override
		protected double getLinkDisplValue(final BasicLink link, final int index) {
			EventLink mylink = SnapshotGenerator.this.eventLinks.get(link.getId());
			return (mylink.buffer.size() + mylink.drivingQueue.size()) / mylink.spaceCap;
		}

		@Override
		protected Collection<? extends DrawableAgentI> getAgentsOnLink(final BasicLink link) {
			EventLink mylink = SnapshotGenerator.this.eventLinks.get(link.getId());
			Collection<EventAgent> agents = new ArrayList<EventAgent>(mylink.buffer.size() + mylink.drivingQueue.size());
			agents.addAll(mylink.buffer);
			agents.addAll(mylink.drivingQueue);
			return agents;
		}
	}

	public void addNetStateWriter(final String networkFileName, final VisConfig visConfig,
			final String filePrefix, final int timeStepLength_s, final int bufferSize) {
		NetStateWriter netStateWriter = new NetStateWriter(this.network, networkFileName, visConfig, filePrefix, timeStepLength_s, bufferSize);
		netStateWriter.open();
		this.addSnapshotWriter(netStateWriter);
	}

}
