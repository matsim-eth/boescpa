/*
 * *********************************************************************** *
 * project: org.matsim.*                                                   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
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
 * *********************************************************************** *
 */

package ch.ethz.matsim.boescpa.diss.av.dynamicFleet.dispatcher;

import ch.ethz.matsim.av.config.AVDispatcherConfig;
import ch.ethz.matsim.av.data.AVOperator;
import ch.ethz.matsim.av.data.AVVehicle;
import ch.ethz.matsim.av.dispatcher.AVDispatcher;
import ch.ethz.matsim.av.dispatcher.AVVehicleAssignmentEvent;
import ch.ethz.matsim.av.dispatcher.utils.SingleRideAppender;
import ch.ethz.matsim.av.framework.AVModule;
import ch.ethz.matsim.av.passenger.AVRequest;
import ch.ethz.matsim.av.plcpc.ParallelLeastCostPathCalculator;
import ch.ethz.matsim.av.schedule.AVStayTask;
import ch.ethz.matsim.av.schedule.AVTask;
import ch.ethz.matsim.boescpa.diss.av.dynamicFleet.vrpagent.VrpAgentSourceIndividualAgent;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.dvrp.data.Vehicle;
import org.matsim.contrib.dvrp.vrpagent.VrpAgentSource;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.core.utils.geometry.CoordUtils;

import java.util.*;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class GrowingFleetDispatcher implements AVDispatcher {
	// todo-boescpa: Move these to config...
	private final static double LEVEL_OF_SERVICE = 5*60;
	private final static double DETOUR_FACTOR = 1.44;
	private final static double TELEPORT_SPEED = 13.5;
	private final static String PREFIX = "taxi";

	private static int generatedNumberOfVehicles = 0;

	final private SingleRideAppender appender;
	final private AVOperator operator;
	final private EventsManager eventsManager;
	final private VrpAgentSourceIndividualAgent agentInjector;

	final private List<AVVehicle> availableVehicles = new LinkedList<>();
	final private List<AVRequest> pendingRequests = new LinkedList<>();

	final private QuadTree<AVVehicle> availableVehiclesTree;

	final private Map<AVVehicle, Link> vehicleLinks = new HashMap<>();

	public GrowingFleetDispatcher(AVOperator operator, EventsManager eventsManager, Network network,
								  SingleRideAppender appender) {
		this.appender = appender;
		this.operator = operator;
		this.eventsManager = eventsManager;
		this.agentInjector = VrpAgentSourceIndividualAgent.getInstance();

		// minx, miny, maxx, maxy
		double[] bounds = NetworkUtils.getBoundingBox(network.getNodes().values());
		availableVehiclesTree = new QuadTree<>(bounds[0], bounds[1], bounds[2], bounds[3]);
	}

	@Override
	public void addVehicle(AVVehicle vehicle) {
		eventsManager.processEvent(new AVVehicleAssignmentEvent(vehicle, 0));
		addVehicle(vehicle, vehicle.getStartLink());
		vehicle.setDispatcher(this);
	}

	@Override
	public void onRequestSubmitted(AVRequest request) {
		Link link = request.getFromLink();
		pendingRequests.add(request);
	}

	@Override
	public void onNextTaskStarted(AVVehicle vehicle) {
		AVTask task = (AVTask)vehicle.getSchedule().getCurrentTask();
		if (task.getAVTaskType() == AVTask.AVTaskType.STAY) {
			addVehicle(vehicle, ((AVStayTask) task).getLink());
		}
	}

	private void addVehicle(AVVehicle vehicle, Link link) {
		availableVehicles.add(vehicle);
		availableVehiclesTree.put(link.getCoord().getX(), link.getCoord().getY(), vehicle);
		vehicleLinks.put(vehicle, link);
	}

	@Override
	public void onNextTimestep(double now) {
		appender.update();
		if (pendingRequests.size() > 0) reoptimize(now);
	}

	private void reoptimize(double now) {
		for (int i = pendingRequests.size() - 1; i > -1; i--) {
			AVRequest request = pendingRequests.get(i);
			double remainingTime = LEVEL_OF_SERVICE - (now - request.getSubmissionTime());
			if (remainingTime > 0) {
				AVVehicle vehicle = findClosestVehicle(request.getFromLink(), remainingTime);
				if (vehicle != null) {
					// We have a vehicle and it's getting on the way.
					removeRequest(request);
					removeVehicle(vehicle);
					appender.schedule(request, vehicle, now);
				} // Else, there is currently no suitable vehicle available and we try again onNextTimestep;
			} else {
				// We never found a suitable vehicle within the expected level of service,
				// therefore we create a new one.
				AVVehicle vehicle = createNewVehicle(request.getFromLink(), now);
				removeRequest(request);
				appender.schedule(request, vehicle, now);
			}
		}
	}

	private AVVehicle createNewVehicle(Link fromLink, double now) {
		Link vehicleLink = fromLink.getFromNode().getInLinks().values().iterator().next();
		Id<Vehicle> id = Id.create("av_" + PREFIX + String.valueOf(++generatedNumberOfVehicles),
				Vehicle.class);
		AVVehicle avVehicle = new AVVehicle(id, vehicleLink, 4.0, 0.0, 108000.0);
		avVehicle.getSchedule().addTask(new AVStayTask(now, avVehicle.getServiceEndTime(), vehicleLink));
		avVehicle.setOpeartor(operator);
		eventsManager.processEvent(new AVVehicleAssignmentEvent(avVehicle, now));
		avVehicle.setDispatcher(this);
		agentInjector.insertIndividualAgentIntoMobsim(avVehicle);
		return avVehicle;
	}

	private AVVehicle findClosestVehicle(Link link, double remainingTime) {
		Coord coord = link.getCoord();
		AVVehicle closestVehicle = availableVehiclesTree.size() > 0 ?
				availableVehiclesTree.getClosest(coord.getX(), coord.getY()) : null;
		if (closestVehicle != null) {
			double travelDistance = CoordUtils.calcEuclideanDistance(
					coord, vehicleLinks.get(closestVehicle).getCoord());
			double travelTimeVehicle = travelDistance * DETOUR_FACTOR / TELEPORT_SPEED;
			if (travelTimeVehicle <= remainingTime) {
				return closestVehicle;
			}
		}
		return closestVehicle;
	}

	private void removeVehicle(AVVehicle vehicle) {
		availableVehicles.remove(vehicle);
		Coord coord = vehicleLinks.remove(vehicle).getCoord();
		availableVehiclesTree.remove(coord.getX(), coord.getY(), vehicle);
	}

	private void removeRequest(AVRequest request) {
		pendingRequests.remove(request);
	}

	static public class Factory implements AVDispatcherFactory {
		@Inject @Named(AVModule.AV_MODE)
		private Network network;

		@Inject private EventsManager eventsManager;

		@Inject @Named(AVModule.AV_MODE)
		private TravelTime travelTime;

		@Inject @Named(AVModule.AV_MODE)
		private ParallelLeastCostPathCalculator router;

		@Inject
		Map<Id<AVOperator>, AVOperator> operators;

		@Override
		public AVDispatcher createDispatcher(AVDispatcherConfig config) {
			return new GrowingFleetDispatcher(
					operators.get(config.getParent().getId()),
					eventsManager,
					network,
					new SingleRideAppender(config, router, travelTime)
			);
		}
	}
}
