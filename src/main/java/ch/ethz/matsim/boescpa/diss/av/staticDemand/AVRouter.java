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

package ch.ethz.matsim.boescpa.diss.av.staticDemand;

import com.google.inject.Inject;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.*;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.facilities.Facility;

import java.util.List;
import java.util.Map;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
class AVRouter {

	/*double getTravelTime(Id<Link> fromLink, Id<Link> toLink, double departureTime) {
		return 1.0;
	}*/

	/*private final Network routingNetwork;
	private final LeastCostPathCalculator leastCostPathCalculator;

	@Inject
	AVRouter(Network network) {
		TravelTime travelTime = new FreeSpeedTravelTime();
		this.routingNetwork = network;
		TravelDisutility travelDisutility = new OnlyTimeDependentTravelDisutility(travelTime);
		LeastCostPathCalculatorFactory factory = new FastAStarLandmarksFactory(this.routingNetwork,
				travelDisutility);
		this.leastCostPathCalculator = factory.createPathCalculator(this.routingNetwork,
				travelDisutility, travelTime);
	}

	LeastCostPathCalculator.Path getRoute(Id<Link> fromLink, Id<Link> toLink, double departureTime) {
		Node fromNode = this.routingNetwork.getLinks().get(fromLink).getToNode();
		Node toNode = this.routingNetwork.getLinks().get(toLink).getToNode();
		return leastCostPathCalculator.calcLeastCostPath(fromNode, toNode, departureTime,
				null, null);
	}

	double getTravelTime(Id<Link> fromLink, Id<Link> toLink, double departureTime) {
		return getRoute(fromLink, toLink, departureTime).travelTime;
	}*/

	private final RoutingModule routingModule;

	@Inject
	AVRouter(TripRouter tripRouter) {
		this.routingModule = tripRouter.getRoutingModule("car");
	}

	Route getRoute(Id<Link> fromLink, Id<Link> toLink, double departureTime) {
		List<? extends PlanElement> result = routingModule.calcRoute(new FakeFacility(fromLink),
				new FakeFacility(toLink), departureTime, null);
		return ((Leg)result.get(0)).getRoute();
	}

	double getTravelTime(Id<Link> fromLink, Id<Link> toLink, double departureTime) {
		return getRoute(fromLink, toLink, departureTime).getTravelTime();
	}

	public class FakeFacility implements Facility {
		private final Id<Link> linkId;

		FakeFacility(Id<Link> linkId) {
			this.linkId = linkId;
		}

		@Override
		public Id<Link> getLinkId() {
			return linkId;
		}

		@Override
		public Coord getCoord() {
			return null;
		}

		@Override
		public Map<String, Object> getCustomAttributes() {
			return null;
		}

		@Override
		public Id getId() {
			return null;
		}
	}
}
