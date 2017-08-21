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

package ch.ethz.matsim.boescpa.diss.av.dynamicFleet.delayedDeployment.dispatcher;

import ch.ethz.matsim.av.config.AVDispatcherConfig;
import ch.ethz.matsim.av.data.AVVehicle;
import ch.ethz.matsim.av.dispatcher.AVDispatcher;
import ch.ethz.matsim.av.dispatcher.AVVehicleAssignmentEvent;
import ch.ethz.matsim.av.dispatcher.utils.SingleRideAppender;
import ch.ethz.matsim.av.framework.AVModule;
import ch.ethz.matsim.av.passenger.AVRequest;
import ch.ethz.matsim.av.plcpc.ParallelLeastCostPathCalculator;
import ch.ethz.matsim.av.schedule.AVStayTask;
import ch.ethz.matsim.av.schedule.AVTask;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.core.utils.geometry.CoordUtils;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class GrowingFleetDispatcher implements AVDispatcher {
	private static Logger log = Logger.getLogger(GrowingFleetDispatcher.class);

	final private SingleRideAppender appender;
	final private EventsManager eventsManager;

	final private List<AVRequest> pendingRequests = new LinkedList<>();

	final private QuadTree<AVVehicle> poolVehiclesTree;
	final private QuadTree<AVVehicle> availableVehiclesTree;
	final private Map<AVVehicle, Link> availableVehicleLinks = new HashMap<>();

	private final double detourFactor;
	private final double teleportSpeed;
	private final double levelOfService;

	public GrowingFleetDispatcher(EventsManager eventsManager, Network network,
								  SingleRideAppender appender,
								  Config config) {
		this.appender = appender;
		this.eventsManager = eventsManager;

		// minx, miny, maxx, maxy
		double[] bounds = NetworkUtils.getBoundingBox(network.getNodes().values());
		poolVehiclesTree = new QuadTree<>(bounds[0], bounds[1], bounds[2], bounds[3]);
		availableVehiclesTree = new QuadTree<>(bounds[0], bounds[1], bounds[2], bounds[3]);

		detourFactor = config.plansCalcRoute().getBeelineDistanceFactors().get("undefined");
		teleportSpeed = config.plansCalcRoute().getTeleportedModeSpeeds().get("undefined");
		levelOfService = ((GrowingFleetDispatcherConfig)config.getModules().get(GrowingFleetDispatcherConfig.NAME)).getLevelOfService();
	}

	@Override
	public void addVehicle(AVVehicle vehicle) {
		Link link = vehicle.getStartLink();
		poolVehiclesTree.put(link.getCoord().getX(), link.getCoord().getY(), vehicle);
	}

	@Override
	public void onRequestSubmitted(AVRequest request) {
		pendingRequests.add(request);
	}

	@Override
	public void onNextTaskStarted(AVVehicle vehicle) {
		AVTask task = (AVTask)vehicle.getSchedule().getCurrentTask();
		if (task.getAVTaskType() == AVTask.AVTaskType.STAY) {
			Link link = ((AVStayTask) task).getLink();
			availableVehiclesTree.put(link.getCoord().getX(), link.getCoord().getY(), vehicle);
			availableVehicleLinks.put(vehicle, link);
		}
	}

	@Override
	public void onNextTimestep(double now) {
		appender.update();
		if (pendingRequests.size() > 0) reoptimize(now);
	}

	private void reoptimize(double now) {
		for (int i = pendingRequests.size() - 1; i > -1; i--) {
			AVRequest request = pendingRequests.get(i);
			double remainingTime = levelOfService - (now - request.getSubmissionTime());
			if (remainingTime > 0) {
				AVVehicle vehicle = findClosestVehicle(request.getFromLink(), remainingTime);
				if (vehicle != null) {
					// We have a vehicle and it's getting on the way.
					pendingRequests.remove(request);
					removeVehicle(vehicle);
					appender.schedule(request, vehicle, now);
				} // Else, there is currently no suitable vehicle available and we try again onNextTimestep;
			} else {
				// We never found a suitable vehicle within the expected level of service,
				// therefore we create a new one.
				AVVehicle vehicle = getNewVehicle(request.getFromLink(), now);
				pendingRequests.remove(request);
				appender.schedule(request, vehicle, now);
			}
		}
	}

	private AVVehicle getNewVehicle(Link fromLink, double now) {
		Coord coord = fromLink.getCoord();
		if (poolVehiclesTree.size() > 0) {
			AVVehicle poolVehicle = poolVehiclesTree.getClosest(coord.getX(), coord.getY());
			eventsManager.processEvent(new AVVehicleAssignmentEvent(poolVehicle, now));
			return poolVehicle;
		} else {
			log.error("Not enough pool vehicles. Sim-Time - " + now);
			AVVehicle vehicle = availableVehiclesTree.getClosest(coord.getX(), coord.getY());
			removeVehicle(vehicle);
			return vehicle;
		}
	}

	private AVVehicle findClosestVehicle(Link link, double remainingTime) {
		Coord coord = link.getCoord();
		AVVehicle closestVehicle = availableVehiclesTree.size() > 0 ?
				availableVehiclesTree.getClosest(coord.getX(), coord.getY()) : null;
		if (closestVehicle != null) {
			double travelDistance = CoordUtils.calcEuclideanDistance(
					coord, availableVehicleLinks.get(closestVehicle).getCoord());
			double travelTimeVehicle = travelDistance * detourFactor / teleportSpeed;
			if (travelTimeVehicle <= remainingTime) {
				return closestVehicle;
			}
		}
		return closestVehicle;
	}

	private void removeVehicle(AVVehicle vehicle) {
		Coord coord = availableVehicleLinks.remove(vehicle).getCoord();
		availableVehiclesTree.remove(coord.getX(), coord.getY(), vehicle);
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
		private Config fullConfig;

		@Override
		public AVDispatcher createDispatcher(AVDispatcherConfig config) {
			return new GrowingFleetDispatcher(
					eventsManager,
					network,
					new SingleRideAppender(config, router, travelTime),
					fullConfig
			);
		}
	}
}
