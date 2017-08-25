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
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
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

	public GrowingFleetTravelTimeEstimator(PlansCalcRouteConfigGroup plansCalcRouteConfigGroup,
										   String teleportMode, double travelTimeThreshold) {
		this.detourFactor = plansCalcRouteConfigGroup.getBeelineDistanceFactors().get(teleportMode);
		this.teleportSpeed = plansCalcRouteConfigGroup.getTeleportedModeSpeeds().get(teleportMode);
		this.travelTimeThreshold = travelTimeThreshold;
	}

	@Override
	public double estimateTravelTime(Link fromLink, Link toLink, double startTime) {
		double travelDistance = CoordUtils.calcEuclideanDistance(fromLink.getCoord(), toLink.getCoord());
		return travelDistance * detourFactor / teleportSpeed;
	}

	@Override
	public double getTravelTimeThreshold() {
		return travelTimeThreshold;
	}
}
