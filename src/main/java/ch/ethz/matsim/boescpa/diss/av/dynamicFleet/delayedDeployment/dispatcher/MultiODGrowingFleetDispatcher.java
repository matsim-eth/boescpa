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
import ch.ethz.matsim.av.dispatcher.multi_od_heuristic.AggregateRideAppender;
import ch.ethz.matsim.av.dispatcher.multi_od_heuristic.ParallelAggregateRideAppender;
import ch.ethz.matsim.av.dispatcher.multi_od_heuristic.TravelTimeEstimator;
import ch.ethz.matsim.av.dispatcher.multi_od_heuristic.aggregation.AggregatedRequest;
import ch.ethz.matsim.av.dispatcher.multi_od_heuristic.aggregation.AggregationEvent;
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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class MultiODGrowingFleetDispatcher implements AVDispatcher {
	private static Logger log = Logger.getLogger(MultiODGrowingFleetDispatcher.class);

	final private AggregateRideAppender appender;
	final private EventsManager eventsManager;
	final private TravelTimeEstimator estimator;

	final private List<AVRequest> pendingRequests = new LinkedList<>();

	final private QuadTree<AVVehicle> poolVehiclesTree;
	final private QuadTree<AVVehicle> availableVehiclesTree;
	final private Map<AVVehicle, Link> availableVehicleLinks = new HashMap<>();

	private boolean changesHappened = false;

	public MultiODGrowingFleetDispatcher(EventsManager eventsManager, Network network, AggregateRideAppender appender,
										 TravelTimeEstimator estimator) {
		this.eventsManager = eventsManager;
		this.appender = appender;
		this.estimator = estimator;

		// minx, miny, maxx, maxy
		double[] bounds = NetworkUtils.getBoundingBox(network.getNodes().values()); // minx, miny, maxx, maxy
		availableVehiclesTree = new QuadTree<>(bounds[0], bounds[1], bounds[2], bounds[3]);
		poolVehiclesTree = new QuadTree<>(bounds[0], bounds[1], bounds[2], bounds[3]);
	}

	@Override
	public void addVehicle(AVVehicle vehicle) {
		Link link = vehicle.getStartLink();
		poolVehiclesTree.put(link.getCoord().getX(), link.getCoord().getY(), vehicle);
		eventsManager.processEvent(new AVVehicleAssignmentEvent(vehicle, 0));
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
		if (changesHappened) {
			// first build aggregates of all pending requests
			List<AggregatedRequest> assignableRequests = aggregatePendingRequests();
			// then try to assign the aggregates
			for (AggregatedRequest request : assignableRequests) {
				double remainingTime = estimator.getTravelTimeThreshold()
						- (now - request.getMasterRequest().getSubmissionTime());
				if (remainingTime > 0) {
					AVVehicle vehicle = findClosestVehicle(request.getMasterRequest().getFromLink(), remainingTime);
					if (vehicle != null) {
						// We have a vehicle and it's getting on the way.
						removeVehicle(vehicle);
						appendAggregatedRequest(now, request, vehicle);
					} // Else, there is currently no suitable vehicle available and we try again onNextTimestep;
				}
			}
			// any aggregate that was not assigned is dropped and new aggregates are created of the remaining
			// and any new requests in the next iteration of reoptimize().
		}
		// we check all remaining requests for their remaining time and if one of them has none left,
		// we create a new vehicle for this request
		for (int i = pendingRequests.size() - 1; i > -1; i--) {
			AVRequest request = pendingRequests.get(i);
			double remainingTime = estimator.getTravelTimeThreshold() - (now - request.getSubmissionTime());
			// We never found a suitable vehicle within the expected level of service,
			// therefore we create a new one.
			if (remainingTime <= 0) {
				AVVehicle vehicle = getNewVehicle(request.getFromLink(), now);
				AggregatedRequest aggregatedRequest = new AggregatedRequest(request, estimator);
				appendAggregatedRequest(now, aggregatedRequest, vehicle);
			}
		}
		changesHappened = false;
	}

	private void appendAggregatedRequest(double now, AggregatedRequest request, AVVehicle vehicle) {
		pendingRequests.remove(request.getMasterRequest());
		for (AVRequest slaveRequest : request.getSlaveRequests()) {
			pendingRequests.remove(slaveRequest);
			eventsManager.processEvent(
					new AggregationEvent(request.getMasterRequest(), slaveRequest, now));
		}
		appender.schedule(request, vehicle, now);
	}

	private List<AggregatedRequest> aggregatePendingRequests() {
		List<AggregatedRequest> aggregatedRequests = new LinkedList<>();
		for (AVRequest request : pendingRequests) {
			AggregatedRequest aggregate = findAggregateRequest(aggregatedRequests, request);
			if (aggregate != null) {
				aggregate.addSlaveRequest(request);
			} else {
				aggregate = new AggregatedRequest(request, estimator);
				aggregatedRequests.add(aggregate);
			}
		}
		return aggregatedRequests;
	}

	private AggregatedRequest findAggregateRequest(List<AggregatedRequest> aggregatedRequests, AVRequest request) {
		AggregatedRequest bestAggregate = null;
		double bestCost = Double.POSITIVE_INFINITY;
		for (AggregatedRequest candidate : aggregatedRequests) {
			if (candidate == null) throw new IllegalStateException();
			Double cost = candidate.accept(request);
			if (cost != null && cost < bestCost) {
				bestCost = cost;
				bestAggregate = candidate;
			}
		}
		return bestAggregate;
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

	private void removeVehicle(AVVehicle vehicle) {
		Coord coord = availableVehicleLinks.remove(vehicle).getCoord();
		availableVehiclesTree.remove(coord.getX(), coord.getY(), vehicle);
	}

	static public class Factory implements AVDispatcher.AVDispatcherFactory {
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
			TravelTimeEstimator estimator =
					new GrowingFleetTravelTimeEstimator(fullConfig.plansCalcRoute(), "undefined",
							levelOfService);

			return new MultiODGrowingFleetDispatcher(
					eventsManager,
					network,
					new ParallelAggregateRideAppender(config, router, travelTime, estimator),
					estimator
			);
		}
	}

}
