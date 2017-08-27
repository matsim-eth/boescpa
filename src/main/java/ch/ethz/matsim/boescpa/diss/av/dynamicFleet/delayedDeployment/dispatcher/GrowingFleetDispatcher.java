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
import ch.ethz.matsim.av.dispatcher.multi_od_heuristic.TravelTimeEstimator;
import ch.ethz.matsim.av.dispatcher.utils.SingleRideAppender;
import ch.ethz.matsim.av.passenger.AVRequest;
import ch.ethz.matsim.av.plcpc.ParallelLeastCostPathCalculator;
import ch.ethz.matsim.av.schedule.AVStayTask;
import ch.ethz.matsim.av.schedule.AVTask;
import ch.ethz.matsim.boescpa.diss.av.dynamicFleet.framework.IVTAVModule;
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
	final private TravelTimeEstimator estimator;

	final private List<AVRequest> pendingRequests = new LinkedList<>();

	final private QuadTree<AVVehicle> poolVehiclesTree;
	final private QuadTree<AVVehicle> availableVehiclesTree;
	final private Map<AVVehicle, Link> availableVehicleLinks = new HashMap<>();

	private boolean changesHappened = false;

	public GrowingFleetDispatcher(EventsManager eventsManager, Network network, TravelTimeEstimator estimator,
								  SingleRideAppender appender) {
		this.appender = appender;
		this.eventsManager = eventsManager;
		this.estimator = estimator;

		// minx, miny, maxx, maxy
		double[] bounds = NetworkUtils.getBoundingBox(network.getNodes().values());
		poolVehiclesTree = new QuadTree<>(bounds[0], bounds[1], bounds[2], bounds[3]);
		availableVehiclesTree = new QuadTree<>(bounds[0], bounds[1], bounds[2], bounds[3]);
	}

	@Override
	public void addVehicle(AVVehicle vehicle) {
		Link link = vehicle.getStartLink();
		poolVehiclesTree.put(link.getCoord().getX(), link.getCoord().getY(), vehicle);
	}

	@Override
	public void onRequestSubmitted(AVRequest request) {
		pendingRequests.add(request);
		changesHappened = true;
	}

	@Override
	public void onNextTaskStarted(AVVehicle vehicle) {
		AVTask task = (AVTask)vehicle.getSchedule().getCurrentTask();
		if (task.getAVTaskType() == AVTask.AVTaskType.STAY) {
			Link link = ((AVStayTask) task).getLink();
			availableVehiclesTree.put(link.getCoord().getX(), link.getCoord().getY(), vehicle);
			availableVehicleLinks.put(vehicle, link);
			changesHappened = true;
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
			double remainingTime = estimator.getTravelTimeThreshold() - (now - request.getSubmissionTime());
			if (remainingTime > 0) {
				if (changesHappened) {
					AVVehicle vehicle = findClosestVehicle(request.getFromLink(), remainingTime);
					if (vehicle != null) {
						// We have a vehicle and it's getting on the way.
						pendingRequests.remove(request);
						removeVehicle(vehicle);
						appender.schedule(request, vehicle, now);
					} // Else, there is currently no suitable vehicle available and we try again onNextTimestep;
				}
			} else {
				// We never found a suitable vehicle within the expected level of service,
				// therefore we create a new one.
				AVVehicle vehicle = getNewVehicle(request.getFromLink(), now);
				pendingRequests.remove(request);
				appender.schedule(request, vehicle, now);
			}
		}
		changesHappened = false;
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
		AVVehicle closestVehicle = availableVehiclesTree.size() > 0 ?
				availableVehiclesTree.getClosest(link.getCoord().getX(), link.getCoord().getY()) : null;
		if (closestVehicle != null) {
			double travelTimeVehicle =
					estimator.estimateTravelTime(link, availableVehicleLinks.get(closestVehicle), 0);
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
		@Inject @Named(IVTAVModule.AV_MODE)
		private Network network;

		@Inject private EventsManager eventsManager;

		@Inject @Named(IVTAVModule.AV_MODE)
		private TravelTime travelTime;

		@Inject @Named(IVTAVModule.AV_MODE)
		private ParallelLeastCostPathCalculator router;

		@Inject
		private Config fullConfig;

		@Override
		public AVDispatcher createDispatcher(AVDispatcherConfig config) {
			double levelOfService = ((GrowingFleetDispatcherConfig)fullConfig.getModules().get(GrowingFleetDispatcherConfig.NAME)).getLevelOfService();
			GrowingFleetTravelTimeEstimator estimator =
					new GrowingFleetTravelTimeEstimator(fullConfig.plansCalcRoute(), "undefined",
							levelOfService);

			return new GrowingFleetDispatcher(
					eventsManager,
					network,
					estimator,
					new SingleRideAppender(config, router, travelTime)
			);
		}
	}
}
