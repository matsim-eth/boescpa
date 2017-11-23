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

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.costcalculators.RandomizingTimeDistanceTravelDisutilityFactory;
import org.matsim.core.router.util.FastAStarLandmarksFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.pt.router.FakeFacility;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.router.TransitRouterConfig;
import org.matsim.pt.router.TransitRouterImplFactory;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.opengis.feature.simple.SimpleFeature;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class AccessiblitiesRouter {

	private final PTTravelTimeCalculator ptTravelTimeCalculator;
	private final CarTravelTimeCalculator carTravelTimeCalculator;
	private final AVTravelTimeCalculator avTravelTimeCalculator;

	public AccessiblitiesRouter(final String[] args) {
		String path2Config = args[0];
		String path2Events = args[1];
		String path2CommunitiesSHP = args[2];

		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.loadConfig(path2Config));
		scenario.getConfig().travelTimeCalculator().setCalculateLinkToLinkTravelTimes(true);

		// Car travel times
		new MatsimNetworkReader(scenario.getNetwork()).readFile(scenario.getConfig().network().getInputFile());
		this.carTravelTimeCalculator = new CarTravelTimeCalculator(scenario);
		//carTravelTimeCalculator.getLinkToLinkTravelTime()

		// AV travel times
		this.avTravelTimeCalculator = new AVTravelTimeCalculator(scenario, path2CommunitiesSHP, carTravelTimeCalculator);

		// PT travel times
		new TransitScheduleReader(scenario).readFile(scenario.getConfig().transit().getTransitScheduleFile());
		this.ptTravelTimeCalculator = new PTTravelTimeCalculator(scenario);
		//ptTravelTimeCalculator.getLinkToLinkTravelTime()

		loadEvents(path2Events);
	}

	private void loadEvents(String path2Events) {
		// Load events to adapt travel times
		EventsManager eventsManager = EventsUtils.createEventsManager();
		eventsManager.addHandler(this.carTravelTimeCalculator);
		eventsManager.addHandler(this.avTravelTimeCalculator);
		MatsimEventsReader reader = new MatsimEventsReader(eventsManager);
		reader.readFile(path2Events);
	}

	public Map<String, Double> getLinkToLinkTravelTime(Id<Link> fromLinkId, Id<Link> toLinkId, double time) {
		Map<String, Double> linkToLinkTravelTime = new LinkedHashMap<>(3);
		linkToLinkTravelTime.put("car", this.carTravelTimeCalculator.getLinkToLinkTravelTime(fromLinkId, toLinkId, time));
		linkToLinkTravelTime.put("pt", this.ptTravelTimeCalculator.getLinkToLinkTravelTime(fromLinkId, toLinkId, time));
		linkToLinkTravelTime.put("av", this.avTravelTimeCalculator.getLinkToLinkTravelTime(fromLinkId, toLinkId, time));
		return linkToLinkTravelTime;
	}

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
		System.out.println();
		for (int time = 1; time < 30; time++) {
			System.out.println("AV - " + time + ":00 - acrossTown: " +
					accessiblitiesRouter.avTravelTimeCalculator.getLinkToLinkTravelTime(fromLink, toLink, time*3600) / 60);
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
		System.out.println();
		for (int time = 1; time < 30; time++) {
			System.out.println("AV - " + time + ":00  - sameLink: " +
					accessiblitiesRouter.avTravelTimeCalculator.getLinkToLinkTravelTime(fromLink, toLink, time*3600) / 60);
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
		System.out.println();
		for (int time = 1; time < 30; time++) {
			System.out.println("AV - " + time + ":00  - inverseLink: " +
					accessiblitiesRouter.avTravelTimeCalculator.getLinkToLinkTravelTime(fromLink, toLink, time*3600) / 60);
		}
	}

	//###############################################################################################################

	private class AVTravelTimeCalculator implements PersonDepartureEventHandler, PersonEntersVehicleEventHandler, ActivityEndEventHandler {
		private final Map<String, Geometry> zones;
		private final GeometryFactory factory;
		private final Network network;
		private final Map<String, Tuple<Double, Integer>> averageWaitingTimeZonePeak;
		private final Map<String, Tuple<Double, Integer>> averageWaitingTimeZoneOffPeak;
		private final Map<String, Tuple<Double, Integer>> averageWaitingTimeZoneNight;
		private final CarTravelTimeCalculator carTravelTimeCalculator;

		AVTravelTimeCalculator(Scenario scenario, String path2SHP, CarTravelTimeCalculator carTravelTimeCalculator) {
			this.averageWaitingTimeZonePeak = new LinkedHashMap<>();
			this.averageWaitingTimeZoneOffPeak = new LinkedHashMap<>();
			this.averageWaitingTimeZoneNight = new LinkedHashMap<>();
			this.zones = getZonesSHP(path2SHP);
			this.factory = new GeometryFactory();
			this.network = scenario.getNetwork();
			this.carTravelTimeCalculator = carTravelTimeCalculator;
		}

		public double getLinkToLinkTravelTime(Id<Link> fromLinkId, Id<Link> toLinkId, double time) {
			String zone = getZone(network.getLinks().get(fromLinkId).getCoord());
			String dayTime = getTime(time);
			double waitTimeAV;
			if (dayTime.equals("peak")) {
				waitTimeAV = zone != null && averageWaitingTimeZonePeak.containsKey(zone) ?
						averageWaitingTimeZonePeak.get(zone).getFirst() : 0;
			} else if (dayTime.equals("offpeak")) {
				waitTimeAV = zone != null && averageWaitingTimeZoneOffPeak.containsKey(zone) ?
						averageWaitingTimeZoneOffPeak.get(zone).getFirst() : 0;
			} else {
				waitTimeAV = zone != null && averageWaitingTimeZoneNight.containsKey(zone) ?
						averageWaitingTimeZoneNight.get(zone).getFirst() : 0;
			}
			double driveTimeAV = carTravelTimeCalculator.getLinkToLinkTravelTime(fromLinkId, toLinkId, time);
			return driveTimeAV > 0 ? waitTimeAV + driveTimeAV : 0;
		}

		// #########################################################################################################

		// Record AV wait times per community:
		private final Map<Id<Person>, Double> personDepartureTime = new LinkedHashMap<>();
		private final Map<String, Id<Person>> personBoarded = new LinkedHashMap<>();

		@Override
		public void reset(int i) {}

		// First a persone departs with AV and has to wait until boarding...
		@Override
		public void handleEvent(PersonDepartureEvent personDepartureEvent) {
			if (personDepartureEvent.getLegMode().equals("av")) {
				this.personDepartureTime.put(personDepartureEvent.getPersonId(), personDepartureEvent.getTime());
			}
		}

		// ... then the person boards which takes some time...
		@Override
		public void handleEvent(PersonEntersVehicleEvent personEntersVehicleEvent) {
			if (personDepartureTime.containsKey(personEntersVehicleEvent.getPersonId())) {
				personBoarded.put(personEntersVehicleEvent.getVehicleId().toString(), personEntersVehicleEvent.getPersonId());
			}
		}

		// ... and when the boarding is finished, the AV takes off.
		@Override
		public void handleEvent(ActivityEndEvent activityEndEvent) {
			if (activityEndEvent.getActType().equals("AVPickup") &&
					personBoarded.containsKey(activityEndEvent.getPersonId().toString())) {
				String zone = getZone(network.getLinks().get(activityEndEvent.getLinkId()).getCoord());
				if (zone != null) {
					double waitingTime = activityEndEvent.getTime() -
							personDepartureTime.get(personBoarded.get(activityEndEvent.getPersonId().toString()));
					String dayTime = getTime(personDepartureTime.get(personBoarded.get(activityEndEvent.getPersonId().toString())));
					Tuple<Double, Integer> averageWaitingTime;
					if (dayTime.equals("peak")) {
						averageWaitingTime = this.averageWaitingTimeZonePeak.getOrDefault(zone, new Tuple<>(0., 0));
					} else if (dayTime.equals("offpeak")) {
						averageWaitingTime = this.averageWaitingTimeZoneOffPeak.getOrDefault(zone, new Tuple<>(0., 0));
					} else {
						averageWaitingTime = this.averageWaitingTimeZoneNight.getOrDefault(zone, new Tuple<>(0., 0));
					}
					double totalWaitingTime = averageWaitingTime.getFirst() * averageWaitingTime.getSecond() + waitingTime;
					int totalObservations = averageWaitingTime.getSecond() + 1;
					if (dayTime.equals("peak")) {
						this.averageWaitingTimeZonePeak.put(zone, new Tuple<>(totalWaitingTime / totalObservations, totalObservations));
					} else if (dayTime.equals("offpeak")) {
						this.averageWaitingTimeZoneOffPeak.put(zone, new Tuple<>(totalWaitingTime / totalObservations, totalObservations));
					} else {
						this.averageWaitingTimeZoneNight.put(zone, new Tuple<>(totalWaitingTime / totalObservations, totalObservations));
					}
				} else {
					System.out.println("No zone found for link " + activityEndEvent.getLinkId());
				}
				personDepartureTime.remove(personBoarded.remove(activityEndEvent.getPersonId().toString()));
			}
		}

		// #########################################################################################################

		private Map<String, Geometry> getZonesSHP(String path2CommunitiesSHP) {
			Map<String, Geometry> zones = new LinkedHashMap<>();
			for (SimpleFeature feature : ShapeFileReader.getAllFeatures(path2CommunitiesSHP)) {
				zones.put(feature.getID(), (Geometry) ((Geometry) feature.getDefaultGeometry()).clone());
			}
			return zones;
		}

		private String getZone(Coord coord) {
			for (String zone : zones.keySet()) {
				Point point = factory.createPoint(new Coordinate(coord.getX(), coord.getY()));
				if (zones.get(zone).contains(point)) {
					return zone;
				}
			}
			return null;
		}

		private String getTime(double time) {
			if ((7*3600 <= time && time < 8*3600) || (17*3600 <= time && time < 18*3600)) {
				return "peak";
			} else if (8*3600 <= time && time < 17*3600) {
				return "offpeak";
			} else {
				return "night";
			}
		}
	}

	private class CarTravelTimeCalculator extends TravelTimeCalculator {
		private final Network network;
		private final LeastCostPathCalculator leastCostPathCalculator;

		CarTravelTimeCalculator(Scenario scenario) {
			super(scenario.getNetwork(), 900, 30*3600, scenario.getConfig().travelTimeCalculator());
			this.network = scenario.getNetwork();
			this.leastCostPathCalculator = getEventBasedLeastCostPathCalculator(scenario);
		}

		@Override
		public double getLinkToLinkTravelTime(Id<Link> fromLinkId, Id<Link> toLinkId, double time) {
			LeastCostPathCalculator.Path path = leastCostPathCalculator.calcLeastCostPath(
					this.network.getLinks().get(fromLinkId).getFromNode(),
					this.network.getLinks().get(toLinkId).getToNode(), time, null, null);
			return path.travelTime;
		}

		LeastCostPathCalculator getEventBasedLeastCostPathCalculator(Scenario scenario) {
			// Return event based leastCostPathCalculator
			TravelDisutility travelDisutility = new RandomizingTimeDistanceTravelDisutilityFactory(TransportMode.car, scenario.getConfig().planCalcScore()).createTravelDisutility(this.getLinkTravelTimes());
			return new FastAStarLandmarksFactory(this.network, travelDisutility)
					.createPathCalculator(this.network, travelDisutility, this.getLinkTravelTimes());
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
