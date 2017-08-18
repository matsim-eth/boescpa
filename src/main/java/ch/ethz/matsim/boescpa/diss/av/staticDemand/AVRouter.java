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

import ch.ethz.matsim.boescpa.lib.tools.NetworkUtils;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.*;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.pt.router.FakeFacility;

import java.util.List;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
class AVRouter {
	/*private TripRouter tripRouter;
	private Network network;

	@Inject
	AVRouter(TripRouter tripRouter, Network network) {
		this.tripRouter = tripRouter;
		this.network = network;
	}

	Route getPath(String mode, Id<Link> fromLink, Id<Link> toLink, double departureTime) {
		Coord fromCoord = this.network.getLinks().get(fromLink).getCoord();
		Coord toCoord = this.network.getLinks().get(toLink).getCoord();
		List<? extends PlanElement> route = tripRouter.calcRoute(
				mode, new FakeFacility(fromCoord), new FakeFacility(toCoord), departureTime, null);
		return ((Leg)route.get(0)).getRoute();
	}

	double getTravelTime(String mode, Id<Link> fromLink, Id<Link> toLink, double departureTime) {
		return getPath(mode, fromLink, toLink, departureTime).getTravelTime();
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

	LeastCostPathCalculator.Path getPath(Id<Link> fromLink, Id<Link> toLink, double departureTime) {
		Node fromNode = this.routingNetwork.getLinks().get(fromLink).getToNode();
		Node toNode = this.routingNetwork.getLinks().get(toLink).getToNode();
		return leastCostPathCalculator.calcLeastCostPath(fromNode, toNode, departureTime,
				null, null);
	}

	double getTravelTime(Id<Link> fromLink, Id<Link> toLink, double departureTime) {
		return getPath(fromLink, toLink, departureTime).travelTime;
	}*/

	private final Network routingNetwork;
	private LeastCostPathCalculator leastCostPathCalculator = null;

	AVRouter(Network network, TravelTime travelTime) {
		this.routingNetwork = network;
		TravelDisutility travelDisutility = new OnlyTimeDependentTravelDisutility(travelTime);
		LeastCostPathCalculatorFactory factory = new FastAStarLandmarksFactory(this.routingNetwork,
				travelDisutility);
		this.leastCostPathCalculator = factory.createPathCalculator(this.routingNetwork,
				travelDisutility, travelTime);
	}

	LeastCostPathCalculator.Path getPath(Id<Link> fromLink, Id<Link> toLink, double departureTime) {
		Node fromNode = this.routingNetwork.getLinks().get(fromLink).getToNode();
		Node toNode = this.routingNetwork.getLinks().get(toLink).getToNode();
		return leastCostPathCalculator.calcLeastCostPath(fromNode, toNode, departureTime,
				null, null);
	}

	double getTravelTime(Id<Link> fromLink, Id<Link> toLink, double departureTime) {
		return getPath(fromLink, toLink, departureTime).travelTime;
	}

	public class Factory {
		@Inject @Named("car") TravelTime travelTime;
		@Inject Network network;

		public AVRouter createRouter() {
			return new AVRouter(network, travelTime);
		}
	}
}
