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

import ch.ethz.matsim.av.dispatcher.multi_od_heuristic.TravelTimeEstimator;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.costcalculators.RandomizingTimeDistanceTravelDisutilityFactory;
import org.matsim.core.router.util.FastAStarLandmarksFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.core.utils.geometry.CoordUtils;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class GrowingFleetTravelTimeEstimator implements TravelTimeEstimator {
	private final Double detourFactor;
	private final Double teleportSpeed;
	private final double travelTimeThreshold;
	//private final CarTravelTimeCalculator travelTimeCalculator;

	public GrowingFleetTravelTimeEstimator(Config fullConfig, Network network,
										   String teleportMode, double travelTimeThreshold) {
		this.detourFactor = fullConfig.plansCalcRoute().getBeelineDistanceFactors().get(teleportMode);
		this.teleportSpeed = fullConfig.plansCalcRoute().getTeleportedModeSpeeds().get(teleportMode);
		this.travelTimeThreshold = travelTimeThreshold;
		//this.travelTimeCalculator = new CarTravelTimeCalculator(fullConfig, network);
	}

	@Override
	public double estimateTravelTime(Link fromLink, Link toLink, double startTime) {
		double travelDistance = CoordUtils.calcEuclideanDistance(fromLink.getCoord(), toLink.getCoord());
		return travelDistance * detourFactor / teleportSpeed;
		//return this.travelTimeCalculator.getCoordToCoordTravelTime(fromLink.getCoord(), toLink.getCoord(), startTime);
	}

	@Override
	public double getTravelTimeThreshold() {
		return travelTimeThreshold;
	}

	/*
	private class CarTravelTimeCalculator extends TravelTimeCalculator {
		private final Network network;
		private final LeastCostPathCalculator leastCostPathCalculator;

		CarTravelTimeCalculator(Config fullConfig, Network network) {
			super(network, 900, 30*3600, fullConfig.travelTimeCalculator());
			this.network = network;
			this.leastCostPathCalculator = getEventBasedLeastCostPathCalculator(fullConfig);
		}

		double getCoordToCoordTravelTime(Coord fromCoord, Coord toCoord, double time) {
			LeastCostPathCalculator.Path path = leastCostPathCalculator.calcLeastCostPath(
					NetworkUtils.getNearestNode(this.network, fromCoord),
					NetworkUtils.getNearestNode(this.network, toCoord), time, null, null);
			return path.travelTime;
		}

		LeastCostPathCalculator getEventBasedLeastCostPathCalculator(Config fullConfig) {
			// Return event based leastCostPathCalculator
			TravelDisutility travelDisutility = new RandomizingTimeDistanceTravelDisutilityFactory(TransportMode.car, fullConfig.planCalcScore()).createTravelDisutility(this.getLinkTravelTimes());
			return new FastAStarLandmarksFactory(this.network, travelDisutility)
					.createPathCalculator(this.network, travelDisutility, this.getLinkTravelTimes());
		}
	}
	*/
}
