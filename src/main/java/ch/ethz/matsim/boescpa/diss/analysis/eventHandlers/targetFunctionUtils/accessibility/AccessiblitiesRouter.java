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

package ch.ethz.matsim.boescpa.diss.analysis.eventHandlers.targetFunctionUtils.accessibility;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.*;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.costcalculators.RandomizingTimeDistanceTravelDisutilityFactory;
import org.matsim.core.router.util.FastAStarLandmarksFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.pt.router.FakeFacility;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.router.TransitRouterConfig;
import org.matsim.pt.router.TransitRouterImplFactory;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import sun.rmi.transport.Transport;

import java.util.List;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class AccessiblitiesRouter {

	private final PTTravelTimeCalculator ptTravelTimeCalculator;
	private final CarTravelTimeCalculator carTravelTimeCalculator;

	public static void main(final String[] args) {
		AccessiblitiesRouter accessiblitiesRouter = new AccessiblitiesRouter(args);

		Id<Link> fromLink = Id.createLinkId(854574);
		Id<Link> toLink = Id.createLinkId(346880);

		System.out.println();
		for (int time = 1; time < 30; time++) {
			System.out.println("Car - " + time + ":00 - acrossTown: " +
					accessiblitiesRouter.carTravelTimeCalculator.getLinkToLinkTravelTime(fromLink, toLink, time*3600) / 60);
		}
		System.out.println();
		for (int time = 1; time < 30; time++) {
			System.out.println("PT - " + time + ":00 - acrossTown: " +
					accessiblitiesRouter.ptTravelTimeCalculator.getLinkToLinkTravelTime(fromLink, toLink, time*3600) / 60);
		}

		fromLink = Id.createLinkId(854574);
		toLink = Id.createLinkId(854574);
		System.out.println();
		for (int time = 1; time < 30; time++) {
			System.out.println("Car - " + time + ":00 - sameLink: " +
					accessiblitiesRouter.carTravelTimeCalculator.getLinkToLinkTravelTime(fromLink, toLink, time*3600) / 60);
		}
		System.out.println();
		for (int time = 1; time < 30; time++) {
			System.out.println("PT - " + time + ":00  - sameLink: " +
					accessiblitiesRouter.ptTravelTimeCalculator.getLinkToLinkTravelTime(fromLink, toLink, time*3600) / 60);
		}

		fromLink = Id.createLinkId(854574);
		toLink = Id.createLinkId(854573);
		System.out.println();
		for (int time = 1; time < 30; time++) {
			System.out.println("Car - " + time + ":00 - inverseLink: " +
					accessiblitiesRouter.carTravelTimeCalculator.getLinkToLinkTravelTime(fromLink, toLink, time*3600) / 60);
		}
		System.out.println();
		for (int time = 1; time < 30; time++) {
			System.out.println("PT - " + time + ":00  - inverseLink: " +
					accessiblitiesRouter.ptTravelTimeCalculator.getLinkToLinkTravelTime(fromLink, toLink, time*3600) / 60);
		}
	}

	private AccessiblitiesRouter(final String[] args) {
		String path2Config = args[0];
		String path2Events = args[1];

		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.loadConfig(path2Config));
		scenario.getConfig().travelTimeCalculator().setCalculateLinkToLinkTravelTimes(true);

		// Car travel times
		new MatsimNetworkReader(scenario.getNetwork()).readFile(scenario.getConfig().network().getInputFile());
		this.carTravelTimeCalculator = new CarTravelTimeCalculator(scenario, path2Events);
		//carTravelTimeCalculator.getLinkToLinkTravelTime()

		// PT travel times
		new TransitScheduleReader(scenario).readFile(scenario.getConfig().transit().getTransitScheduleFile());
		this.ptTravelTimeCalculator = new PTTravelTimeCalculator(scenario);
		//ptTravelTimeCalculator.getLinkToLinkTravelTime()
	}

	private class CarTravelTimeCalculator {
		private final Network network;
		private final LeastCostPathCalculator leastCostPathCalculator;

		CarTravelTimeCalculator(Scenario scenario, String path2Events) {
			this.network = scenario.getNetwork();
			this.leastCostPathCalculator = getEventBasedLeastCostPathCalculator(scenario, path2Events);
		}

		double getLinkToLinkTravelTime(Id<Link> fromLinkId, Id<Link> toLinkId, double time) {
			LeastCostPathCalculator.Path path = leastCostPathCalculator.calcLeastCostPath(
					this.network.getLinks().get(fromLinkId).getFromNode(),
					this.network.getLinks().get(toLinkId).getToNode(), time, null, null);
			return path.travelTime;
		}

		private LeastCostPathCalculator getEventBasedLeastCostPathCalculator(Scenario scenario, String path2Events) {
			// Create TravelTimeCalculator
			TravelTimeCalculator travelTimeCalculator = new TravelTimeCalculator(scenario.getNetwork(),
					900, 30*3600, scenario.getConfig().travelTimeCalculator());
			// Load events to adapt travel times
			EventsManager eventsManager = EventsUtils.createEventsManager();
			eventsManager.addHandler(travelTimeCalculator);
			MatsimEventsReader reader = new MatsimEventsReader(eventsManager);
			reader.readFile(path2Events);
			// Return event based leastCostPathCalculator
			TravelDisutility travelDisutility = new RandomizingTimeDistanceTravelDisutilityFactory(TransportMode.car, scenario.getConfig().planCalcScore()).createTravelDisutility(travelTimeCalculator.getLinkTravelTimes());
			return new FastAStarLandmarksFactory(this.network, travelDisutility)
					.createPathCalculator(this.network, travelDisutility, travelTimeCalculator.getLinkTravelTimes());
		}
	}

	private class PTTravelTimeCalculator {
		private final TransitRouter transitRouter;
		private final Network network;

		PTTravelTimeCalculator(Scenario scenario) {
			this.network = scenario.getNetwork();
			this.transitRouter = new TransitRouterImplFactory(scenario.getTransitSchedule(),
					new TransitRouterConfig(scenario.getConfig())).get();
		}

		double getLinkToLinkTravelTime(Id<Link> fromLinkId, Id<Link> toLinkId, double time) {
			List<Leg> transitRoute = transitRouter.calcRoute(
					new FakeFacility(network.getLinks().get(fromLinkId).getFromNode().getCoord()),
					new FakeFacility(network.getLinks().get(toLinkId).getToNode().getCoord()), time, null);
			double travelTime = 0;
			for (Leg leg : transitRoute) {
				travelTime += leg.getTravelTime();
			}
			return travelTime;
		}
	}

}
