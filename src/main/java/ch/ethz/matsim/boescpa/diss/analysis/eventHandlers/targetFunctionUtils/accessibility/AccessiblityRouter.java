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
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkUtils;
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
public class AccessiblityRouter {

	private final PTTravelTimeCalculator ptTravelTimeCalculator;
	private final CarTravelTimeCalculator carTravelTimeCalculator;
	private final AVTravelTimeCalculator avTravelTimeCalculator;
	private final SMTravelTimeCalculator smTravelTimeCalculator;

	public AccessiblityRouter(Scenario scenario, EventsManager eventsManager, String path2CommunitiesSHP) {
		this.carTravelTimeCalculator = new CarTravelTimeCalculator(scenario);
		this.avTravelTimeCalculator = new AVTravelTimeCalculator(scenario, path2CommunitiesSHP, carTravelTimeCalculator);
		this.ptTravelTimeCalculator = new PTTravelTimeCalculator(scenario);
		this.smTravelTimeCalculator = new SMTravelTimeCalculator(scenario);
		eventsManager.addHandler(this.carTravelTimeCalculator);
		eventsManager.addHandler(this.avTravelTimeCalculator);
		eventsManager.addHandler(this.smTravelTimeCalculator);
	}

	AccessiblityRouter(Config config, String path2Events, String path2CommunitiesSHP) {
		Scenario scenario = ScenarioUtils.createScenario(config);

		// Load required scenario elements
		new MatsimNetworkReader(scenario.getNetwork()).readFile(scenario.getConfig().network().getInputFile());
		new TransitScheduleReader(scenario).readFile(scenario.getConfig().transit().getTransitScheduleFile());

		this.carTravelTimeCalculator = new CarTravelTimeCalculator(scenario);
		this.avTravelTimeCalculator = new AVTravelTimeCalculator(scenario, path2CommunitiesSHP, carTravelTimeCalculator);
		this.ptTravelTimeCalculator = new PTTravelTimeCalculator(scenario);
		this.smTravelTimeCalculator = new SMTravelTimeCalculator(scenario);

		loadEvents(path2Events);
	}

	private void loadEvents(String path2Events) {
		// Load events to adapt travel times
		EventsManager eventsManager = EventsUtils.createEventsManager();
		eventsManager.addHandler(this.carTravelTimeCalculator);
		eventsManager.addHandler(this.avTravelTimeCalculator);
		eventsManager.addHandler(this.smTravelTimeCalculator);
		MatsimEventsReader reader = new MatsimEventsReader(eventsManager);
		reader.readFile(path2Events);
	}

	public Map<String, Double> getLinkToLinkTravelTime(Id<Link> fromLinkId, Id<Link> toLinkId, double time) {
		Map<String, Double> linkToLinkTravelTime = new LinkedHashMap<>(3);
		linkToLinkTravelTime.put("car", this.carTravelTimeCalculator.getLinkToLinkTravelTime(fromLinkId, toLinkId, time));
		linkToLinkTravelTime.put("pt", this.ptTravelTimeCalculator.getLinkToLinkTravelTime(fromLinkId, toLinkId, time));
		linkToLinkTravelTime.put("av", linkToLinkTravelTime.get("car") > 0 ?
				this.avTravelTimeCalculator.getOverhead(fromLinkId, time) + linkToLinkTravelTime.get("car"): 0);
		linkToLinkTravelTime.put("sm", this.smTravelTimeCalculator.getLinkToLinkTravelTime(fromLinkId, toLinkId, time));
		return linkToLinkTravelTime;
	}

	public Map<String, Double> getCoordToCoordTravelTime(Coord fromCoord, Coord toCoord, double time) {
		Map<String, Double> coordToCoordTravelTimes = new LinkedHashMap<>(3);
		coordToCoordTravelTimes.put("car", this.carTravelTimeCalculator.getCoordToCoordTravelTime(fromCoord, toCoord, time));
		coordToCoordTravelTimes.put("pt", this.ptTravelTimeCalculator.getCoordToCoordTravelTime(fromCoord, toCoord, time));
		coordToCoordTravelTimes.put("av", coordToCoordTravelTimes.get("car") > 0 ?
				this.avTravelTimeCalculator.getOverhead(fromCoord, time) + coordToCoordTravelTimes.get("car"): 0);
		coordToCoordTravelTimes.put("sm", this.smTravelTimeCalculator.getCoordToCoordTravelTime(fromCoord, toCoord, time));
		return coordToCoordTravelTimes;
	}

	public static void main(final String[] args) {
		String path2Config = args[0];
		String path2Events = args[1];
		String path2CommunitiesSHP = args[2];

		AccessiblityRouter accessiblitiesRouter = new AccessiblityRouter(
				ConfigUtils.loadConfig(path2Config), path2Events, path2CommunitiesSHP);

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

	private class SMTravelTimeCalculator implements PersonDepartureEventHandler {
		private final Network network;
		private final Double beelineDistanceFactor_bike;
		private final Double beelineDistanceFactor_walk;
		private final Double teleportedModeSpeed_bike;
		private final Double teleportedModeSpeed_walk;
		private double numberOfTrips_bike = 0.001; // to avoid division by zero
		private double numberOfTrips_walk = 0.001; // to avoid division by zero

		SMTravelTimeCalculator(Scenario scenario) {
			this.network = scenario.getNetwork();
			this.beelineDistanceFactor_bike = scenario.getConfig().plansCalcRoute().getBeelineDistanceFactors().get("bike");
			this.beelineDistanceFactor_walk = scenario.getConfig().plansCalcRoute().getBeelineDistanceFactors().get("walk");
			this.teleportedModeSpeed_bike = scenario.getConfig().plansCalcRoute().getTeleportedModeSpeeds().get("bike");
			this.teleportedModeSpeed_walk = scenario.getConfig().plansCalcRoute().getTeleportedModeSpeeds().get("walk");
		}

		private double getBeelineDistanceFactor() {
			return ((numberOfTrips_bike * beelineDistanceFactor_bike) + (numberOfTrips_walk * beelineDistanceFactor_walk)) /
					(numberOfTrips_bike + numberOfTrips_walk);
		}

		private double getTeleportedModeSpeed() {
			return ((numberOfTrips_bike * teleportedModeSpeed_bike) + (numberOfTrips_walk * teleportedModeSpeed_walk)) /
					(numberOfTrips_bike + numberOfTrips_walk);
		}

		double getLinkToLinkTravelTime(Id<Link> fromLinkId, Id<Link> toLinkId, double time) {
			return getCoordToCoordTravelTime(
					this.network.getLinks().get(fromLinkId).getFromNode().getCoord(),
					this.network.getLinks().get(toLinkId).getToNode().getCoord(), time);
		}

		double getCoordToCoordTravelTime(Coord fromCoord, Coord toCoord, double time) {
			double beelineDistance = NetworkUtils.getEuclideanDistance(fromCoord, toCoord);
			return (beelineDistance * getBeelineDistanceFactor()) / getTeleportedModeSpeed();
		}

		@Override
		public void handleEvent(PersonDepartureEvent personDepartureEvent) {
			switch (personDepartureEvent.getLegMode()) {
				case "bike": numberOfTrips_bike++; break;
				case "walk": numberOfTrips_walk++; break;
			}
		}

		@Override
		public void reset(int i) {}
	}

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

		double getOverhead(Coord fromCoord, double time) {
			String zone = getZone(fromCoord);
			return getOverhead(zone, time);
		}

		double getOverhead(Id<Link> fromLinkId, double time) {
			String zone = getZone(network.getLinks().get(fromLinkId).getCoord());
			return getOverhead(zone, time);
		}

		double getOverhead(String zone, double time) {
			String dayTime = getTime(time);
			double waitTimeAV;
			switch (dayTime) {
				case "peak": waitTimeAV = zone != null && averageWaitingTimeZonePeak.containsKey(zone) ?
						averageWaitingTimeZonePeak.get(zone).getFirst() : 0; break;
				case "offpeak": waitTimeAV = zone != null && averageWaitingTimeZoneOffPeak.containsKey(zone) ?
						averageWaitingTimeZoneOffPeak.get(zone).getFirst() : 0; break;
				case "night": waitTimeAV = zone != null && averageWaitingTimeZoneNight.containsKey(zone) ?
						averageWaitingTimeZoneNight.get(zone).getFirst() : 0; break;
				default: throw new RuntimeException("Undefined day time: " + dayTime);
			}
			return waitTimeAV;
		}

		double getCoordToCoordTravelTime(Coord fromCoord, Coord toCoord, double time) {
			double waitTimeAV = getOverhead(fromCoord, time);
			double driveTimeAV = carTravelTimeCalculator.getCoordToCoordTravelTime(fromCoord, toCoord, time);
			return driveTimeAV > 0 ? waitTimeAV + driveTimeAV : 0;
		}

		double getLinkToLinkTravelTime(Id<Link> fromLinkId, Id<Link> toLinkId, double time) {
			double waitTimeAV = getOverhead(fromLinkId, time);
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
					switch (dayTime) {
						case "peak": averageWaitingTime = this.averageWaitingTimeZonePeak.getOrDefault(zone, new Tuple<>(0., 0)); break;
						case "offpeak": averageWaitingTime = this.averageWaitingTimeZoneOffPeak.getOrDefault(zone, new Tuple<>(0., 0));break;
						case "night": averageWaitingTime = this.averageWaitingTimeZoneNight.getOrDefault(zone, new Tuple<>(0., 0)); break;
						default: throw new RuntimeException("Undefined day time: " + dayTime);
					}
					double totalWaitingTime = averageWaitingTime.getFirst() * averageWaitingTime.getSecond() + waitingTime;
					int totalObservations = averageWaitingTime.getSecond() + 1;
					switch (dayTime) {
						case "peak":
							this.averageWaitingTimeZonePeak.put(zone, new Tuple<>(totalWaitingTime / totalObservations, totalObservations));
							break;
						case "offpeak":
							this.averageWaitingTimeZoneOffPeak.put(zone, new Tuple<>(totalWaitingTime / totalObservations, totalObservations));
							break;
						case "night":
							this.averageWaitingTimeZoneNight.put(zone, new Tuple<>(totalWaitingTime / totalObservations, totalObservations));
							break;
						default: throw new RuntimeException("Undefined day time: " + dayTime);
					}
				} /*else {
					System.out.println("No zone found for link " + activityEndEvent.getLinkId());
				}*/
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

		double getCoordToCoordTravelTime(Coord fromCoord, Coord toCoord, double time) {
			LeastCostPathCalculator.Path path = leastCostPathCalculator.calcLeastCostPath(
					NetworkUtils.getNearestNode(this.network, fromCoord),
					NetworkUtils.getNearestNode(this.network, toCoord), time, null, null);
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

		double getCoordToCoordTravelTime(Coord fromCoord, Coord toCoord, double time) {
			List<Leg> transitRoute = transitRouter.calcRoute(
					new FakeFacility(fromCoord),
					new FakeFacility(toCoord), time, null);
			double travelTime = 0;
			for (Leg leg : transitRoute) {
				travelTime += leg.getTravelTime();
			}
			return travelTime;
		}
	}
}