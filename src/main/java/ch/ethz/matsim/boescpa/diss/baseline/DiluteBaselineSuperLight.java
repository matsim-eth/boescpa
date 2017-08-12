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

package ch.ethz.matsim.boescpa.diss.baseline;

import ch.ethz.matsim.boescpa.lib.tools.FacilityUtils;
import ch.ethz.matsim.boescpa.lib.tools.NetworkUtils;
import ch.ethz.matsim.boescpa.lib.tools.PopulationUtils;
import ch.ethz.matsim.boescpa.lib.tools.SHPFileUtils;
import ch.ethz.matsim.boescpa.lib.tools.coordUtils.CoordAnalyzer;
import com.vividsolutions.jts.geom.Geometry;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.*;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.population.routes.LinkNetworkRouteImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.*;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.PreProcessDijkstra;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.misc.Counter;
import org.matsim.facilities.*;
import org.matsim.households.*;
import org.matsim.pt.config.TransitConfigGroup;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.utils.objectattributes.ObjectAttributesUtils;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;
import org.matsim.vehicles.*;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.util.*;

/**
 * Dilutes a scenario to the provided area (SHP-file) - diluting in the new IVT sense. This means:
 * 	- all agents who at least pass through the area  are kept (this includes any agent with at least one activity in the area)
 * 	- only the full network in the area plus all arterial links are kept
 * 	- only the full facilities in the area plus all facilities used by agents are kept
 * 	- the full original pt service from the area plus all rail pt are kept
 *
 * This resembles heavily and is based on the ch.ethz.matsim.ivt_baseline.preparation.ZHCutter
 *
 * @author boescpa
 */
public class DiluteBaselineSuperLight {

	private final static String COMMUTER = "outAct";
	private final static String OUTAREA_PT = "outArea_pt";
	private final static String OUTAREA_CAR = "outArea_car";

	private final Map<Coord, Boolean> coordCache = new HashMap<>();
	private final CoordAnalyzer coordAnalyzer;
	private final String outputPath;
	private final Network carNetwork;
	private final ActivityFacilities facilities;

	private DiluteBaselineSuperLight(Config config, String pathToSHPOfCutArea, String pathToOutputFolder) {
		// load network
		this.carNetwork = getCarNetwork(NetworkUtils.readNetwork(config.network().getInputFile()));
		this.facilities = FacilityUtils.readFacilities(config.facilities().getInputFile());
		// Output path:
		this.outputPath = pathToOutputFolder + File.separator;
		// Set up cut area from SHP-file.
		Set<SimpleFeature> features = new HashSet<>();
		SHPFileUtils util = new SHPFileUtils();
		features.addAll(ShapeFileReader.getAllFeatures(pathToSHPOfCutArea));
		Geometry area = util.mergeGeometries(features);
		this.coordAnalyzer = new CoordAnalyzer(area);
	}


	public static void main(final String[] args) {
		final String pathToConfig = args[0];
		final String pathToOutputFolder = args[1];
		final String pathToSHPFile = args[2];

		Config config = ConfigUtils.loadConfig(pathToConfig);
		DiluteBaselineSuperLight diluter = new DiluteBaselineSuperLight(config, pathToSHPFile, pathToOutputFolder);

		// cut demand:
		Population filteredPopulation = diluter.filterPopulation(config.plans(), config.facilities());
		diluter.filterHouseholds(config.households(), filteredPopulation);
		ActivityFacilities filteredFacilities = diluter.filterFacilities(config.facilities(), filteredPopulation);

		// cut supply:
		TransitSchedule filteredSchedule = diluter.filterTransitSchedule(config.transit());
		diluter.filterVehicles(config.transit(), filteredSchedule);
		Network onlyCarNetwork = diluter.filterNetwork(config.network(), filteredSchedule, filteredPopulation);

		// repair scenario:
		diluter.reconnectLostFacilities(filteredFacilities, onlyCarNetwork);
		diluter.resetAndCorrectPop(filteredPopulation, config.plansCalcRoute());
	}

	private void resetAndCorrectPop(Population filteredPopulation,
									PlansCalcRouteConfigGroup plansCalcRouteConfigGroup) {
		double maxWalkDist = 20*60*plansCalcRouteConfigGroup.getTeleportedModeSpeeds().get("walk")/
				plansCalcRouteConfigGroup.getBeelineDistanceFactors().get("walk");
		double maxBikeDist = 20*60*plansCalcRouteConfigGroup.getTeleportedModeSpeeds().get("bike")/
				plansCalcRouteConfigGroup.getBeelineDistanceFactors().get("bike");

		for (Person person : filteredPopulation.getPersons().values()) {
			// reset all legs so that they are newly initialized when the new sim starts
			for (PlanElement pe : person.getSelectedPlan().getPlanElements()) {
				if (pe instanceof Leg) {
					((Leg)pe).setRoute(null);
				}
			}
			// reset all activity coordinates to the chosen facilities coord...
			for (PlanElement pe : person.getSelectedPlan().getPlanElements()) {
				if (pe instanceof Activity) {
					((Activity)pe).setCoord(
							facilities.getFacilities().get(((Activity)pe).getFacilityId()).getCoord());
				}
			}
			// for every agent, but especially for outAct, which is not allowed to change mode,
			// change initial mode choice to "reasonable" modes
			// - for non-outAct, it should only change the initial setting, but as they are allowed
			// 		mode-choice, it should be fine...
			// - for freight it should not change anything (because they have only car and we don't
			// 		touch car here))
			Leg lastLeg = null;
			ActivityFacility lastFacility = null;
			for (PlanElement pe : person.getSelectedPlan().getPlanElements()) {
				if (pe instanceof Activity) {
					ActivityFacility thisFacility = this.facilities.getFacilities().get(
							((Activity)pe).getFacilityId());
					if (lastFacility != null && lastLeg != null && !lastLeg.getMode().equals("car")) {
						double distance = CoordUtils.calcEuclideanDistance(
								thisFacility.getCoord(), lastFacility.getCoord());
						if (lastLeg.getMode().equals("walk") && (distance > maxWalkDist)) {
							lastLeg.setMode("bike");
						}
						if (lastLeg.getMode().equals("bike") && (distance > maxBikeDist)) {
							String subpop = (String)filteredPopulation.getPersonAttributes().getAttribute(
									person.getId().toString(), "subpopulation");
							if (subpop != null && subpop.equals("outAct")) {
								lastLeg.setMode(OUTAREA_PT);
							} else {
								lastLeg.setMode("pt");
							}
						}
					}
					lastFacility = thisFacility;
				}
				if (pe instanceof Leg) {
					lastLeg = (Leg)pe;
				}
			}
		}

		new PopulationWriter(filteredPopulation).write(outputPath + "population.xml.gz");
	}

	private void reconnectLostFacilities(ActivityFacilities filteredFacilities, Network onlyCarNetwork) {
		// reconnect all lost facilities
		for (ActivityFacility facility : filteredFacilities.getFacilities().values()) {
			if (!onlyCarNetwork.getLinks().keySet().contains(facility.getLinkId())) {
				ActivityFacilityImpl facilityImpl = (ActivityFacilityImpl) facility;
				Link nearestRightEntryLink = org.matsim.core.network.NetworkUtils.getNearestRightEntryLink(
						onlyCarNetwork, facility.getCoord());
				facilityImpl.setLinkId(nearestRightEntryLink.getId());
			}
		}
		// write facilities
		new FacilitiesWriter(filteredFacilities).write(outputPath + "facilities.xml.gz");
	}

	private Network filterNetwork(NetworkConfigGroup networkConfigGroup, TransitSchedule filteredSchedule,
								  Population filteredPopulation) {
		// load network
		Network inputNetwork = NetworkUtils.readNetwork(networkConfigGroup.getInputFile());
		Network filteredNetwork = org.matsim.core.network.NetworkUtils.createNetwork();
		Set<Id<Link>> scheduleLinks = getScheduleLinks(filteredSchedule);
		Set<Id<Link>> popLinks = getPopLinks(filteredPopulation);
		// filter only-car-network in area
		Network onlyCarNetworkArea = org.matsim.core.network.NetworkUtils.createNetwork();
		for (Link link : this.carNetwork.getLinks().values()) {
			if (inArea(link.getFromNode().getCoord()) || inArea(link.getToNode().getCoord())
					|| popLinks.contains(link.getId())) {
				addLink(onlyCarNetworkArea, link);
			}
		}
		new NetworkCleaner().run(onlyCarNetworkArea);
		// filter network
		for (Link link : inputNetwork.getLinks().values()) {
			if (scheduleLinks.contains(link.getId())
					|| onlyCarNetworkArea.getLinks().keySet().contains(link.getId())) {
				addLink(filteredNetwork, link);
			}
		}
		// write network
		new NetworkWriter(filteredNetwork).write(outputPath + "network.xml.gz");
		return onlyCarNetworkArea;
	}

	private Network getCarNetwork(Network network) {
		Network onlyCarNetwork = org.matsim.core.network.NetworkUtils.createNetwork();
		for (Link link : network.getLinks().values()) {
			if (link.getAllowedModes().contains("car")) {
				addLink(onlyCarNetwork, link);
			}
		}
		new NetworkCleaner().run(onlyCarNetwork);
		return onlyCarNetwork;
	}

	private Set<Id<Link>> getPopLinks(Population filteredPopulation) {
		Set<Id<Link>> popLinks = new HashSet<>();
		for (Person person : filteredPopulation.getPersons().values()) {
			for (PlanElement pe : person.getSelectedPlan().getPlanElements()) {
				if (pe instanceof Leg && ((Leg)pe).getMode().equals("car")) {
					Leg leg = (Leg)pe;
					popLinks.add(leg.getRoute().getStartLinkId());
					popLinks.add(leg.getRoute().getEndLinkId());
					if (leg.getRoute() instanceof LinkNetworkRouteImpl) {
						popLinks.addAll(((NetworkRoute)leg.getRoute()).getLinkIds());
					}
				}
			}
		}
		return popLinks;
	}

	private void addLink(Network network, Link link) {
		if (!network.getNodes().containsKey(link.getFromNode().getId())) {
			Node node = network.getFactory().createNode(link.getFromNode().getId(), link.getFromNode().getCoord());
			network.addNode(node);
		}
		if (!network.getNodes().containsKey(link.getToNode().getId())) {
			Node node = network.getFactory().createNode(link.getToNode().getId(), link.getToNode().getCoord());
			network.addNode(node);
		}
		network.addLink(link);
		link.setFromNode(network.getNodes().get(link.getFromNode().getId()));
		link.setToNode(network.getNodes().get(link.getToNode().getId()));
	}

	private Set<Id<Link>> getScheduleLinks(TransitSchedule filteredSchedule) {
		Set<Id<Link>> scheduleLinks = new HashSet<>();
		for (TransitLine transitLine : filteredSchedule.getTransitLines().values()) {
			for (TransitRoute transitRoute : transitLine.getRoutes().values()) {
				scheduleLinks.addAll(transitRoute.getRoute().getLinkIds());
				for (TransitRouteStop transitStop : transitRoute.getStops()) {
					scheduleLinks.add(transitStop.getStopFacility().getLinkId());
				}
			}
		}
		return scheduleLinks;
	}

	private void filterVehicles(TransitConfigGroup transitConfigGroup, TransitSchedule filteredSchedule) {
		// load vehicles
		Vehicles inputVehicles = VehicleUtils.createVehiclesContainer();
		new VehicleReaderV1(inputVehicles).readFile(transitConfigGroup.getVehiclesFile());
		Vehicles filteredVehicles = VehicleUtils.createVehiclesContainer();
		// filter vehicles
		for (TransitLine line : filteredSchedule.getTransitLines().values()) {
			for (TransitRoute route : line.getRoutes().values()) {
				for (Departure departure : route.getDepartures().values()) {
					Vehicle vehicleToKeep = inputVehicles.getVehicles().get(departure.getVehicleId());
					if (!filteredVehicles.getVehicleTypes().containsValue(vehicleToKeep.getType())) {
						filteredVehicles.addVehicleType(vehicleToKeep.getType());
					}
					filteredVehicles.addVehicle(vehicleToKeep);
				}
			}
		}
		// write vehicles
		new VehicleWriterV1(filteredVehicles).writeFile(outputPath + "vehicles.xml.gz");
	}

	private TransitSchedule filterTransitSchedule(TransitConfigGroup transitConfigGroup) {
		// load schedule
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new TransitScheduleReader(scenario).readFile(transitConfigGroup.getTransitScheduleFile());
		TransitSchedule inputSchedule = scenario.getTransitSchedule();
		TransitSchedule filteredSchedule =
				ScenarioUtils.createScenario(ConfigUtils.createConfig()).getTransitSchedule();
		// filter schedule (all lines with at least two stops in area,
		// 		because these can be used for transitions within area)
		for (TransitLine transitLine : inputSchedule.getTransitLines().values()) {
			for (TransitRoute transitRoute : transitLine.getRoutes().values()) {
				int numbOfStopsInArea = 0;
				for (TransitRouteStop transitStop : transitRoute.getStops()) {
					if (inArea(transitStop.getStopFacility().getCoord())) {
						numbOfStopsInArea++;
					}
					if (numbOfStopsInArea > 1) {
						Id<TransitLine> newLineId = addLine(filteredSchedule, transitLine);
						filteredSchedule.getTransitLines().get(newLineId).addRoute(transitRoute);
						addStopFacilities(filteredSchedule, transitRoute);
						break;
					}
				}
			}
		}
		// write schedule
		new TransitScheduleWriter(filteredSchedule).writeFile(outputPath + "schedule.xml.gz");
		return filteredSchedule;
	}

	private void addStopFacilities(TransitSchedule schedule, TransitRoute transitRoute) {
		for (TransitRouteStop newStop : transitRoute.getStops()) {
			if (!schedule.getFacilities().containsKey(newStop.getStopFacility().getId())) {
				schedule.addStopFacility(newStop.getStopFacility());
			}
		}
	}

	private Id<TransitLine> addLine(TransitSchedule schedule, TransitLine transitLine) {
		Id<TransitLine> newLineId = Id.create(transitLine.getId().toString(), TransitLine.class);
		if (!schedule.getTransitLines().containsKey(newLineId)) {
			TransitLine newLine = schedule.getFactory().createTransitLine(newLineId);
			schedule.addTransitLine(newLine);
			newLine.setName(transitLine.getName());
		}
		return newLineId;
	}

	private ActivityFacilities filterFacilities(FacilitiesConfigGroup facilities, Population filteredPopulation) {
		// load facilities
		ActivityFacilities inputFacilities =
				FacilityUtils.readFacilities(facilities.getInputFile());
		ActivityFacilities filteredFacilities = new ActivityFacilitiesImpl();
		Set<Id<ActivityFacility>> popFacilities = getPopFacilities(filteredPopulation);
		// filter facilities
		Counter counter = new Counter(" facility # ");
		for (ActivityFacility facility : inputFacilities.getFacilities().values()) {
			counter.incCounter();
			if (popFacilities.contains(facility.getId()) || inArea(facility.getCoord())) {
				filteredFacilities.addActivityFacility(facility);
			}
		}
		counter.printCounter();
		// deal with remote_home activities
		dealWithRemoteHomeActs(filteredFacilities);
		// remove open-close-time for facilities which shouldn't have it
		removeOpenCloseTimes(filteredFacilities);
		// return facilities
		return filteredFacilities;
	}

	private void removeOpenCloseTimes(ActivityFacilities filteredFacilities) {
		for (ActivityFacility facility : filteredFacilities.getFacilities().values()) {
			if (facility.getActivityOptions().keySet().contains("freight")) {
				// remove opening times
				((ActivityOptionImpl)facility.getActivityOptions().get("freight")).clearOpeningTimes();
				for (int i = 1; i < 16; i++) {
					((ActivityOptionImpl)facility.getActivityOptions().get("freight_" + i)).clearOpeningTimes();
				}
			}
		}
	}

	private void dealWithRemoteHomeActs(ActivityFacilities filteredFacilities) {
		for (ActivityFacility facility : filteredFacilities.getFacilities().values()) {
			if (facility.getActivityOptions().keySet().contains("remote_home")) {
				// add home-acts (if missing) and correct their capacity by remote_home
				double remoteHomeCap = facility.getActivityOptions().get("remote_home").getCapacity();
				if (!facility.getActivityOptions().keySet().contains("home")) {
					ActivityOption home = new ActivityOptionImpl("home");
					home.setCapacity(remoteHomeCap);
					facility.addActivityOption(home);
					for (int i = 1; i < 16; i++) {
						home = new ActivityOptionImpl("home_" + i);
						home.setCapacity(remoteHomeCap);
						facility.addActivityOption(home);
					}
				} else {
					double homeCap = facility.getActivityOptions().get("home").getCapacity() + remoteHomeCap;
					facility.getActivityOptions().get("home").setCapacity(homeCap);
					for (int i = 1; i < 16; i++) {
						facility.getActivityOptions().get("home_" + i).setCapacity(homeCap);
					}
				}
				// remove remote_home-acts
				facility.getActivityOptions().remove("remote_home");
				for (int i = 1; i < 16; i++) {
					facility.getActivityOptions().remove("remote_home_" + i);
				}
			}
		}
	}

	private Set<Id<ActivityFacility>> getPopFacilities(Population filteredPopulation) {
		Set<Id<ActivityFacility>> popFacilities = new HashSet<>();
		for (Person person : filteredPopulation.getPersons().values()) {
			for (PlanElement pe : person.getSelectedPlan().getPlanElements()) {
				if (pe instanceof Activity) {
					Activity act = (Activity) pe;
					popFacilities.add(act.getFacilityId());
				}
			}
		}
		return popFacilities;
	}

	private void filterHouseholds(HouseholdsConfigGroup householdsConfigGroup, Population filteredPopulation) {
		// load households
		Households inputHouseholds = new HouseholdsImpl();
		new HouseholdsReaderV10(inputHouseholds).readFile(householdsConfigGroup.getInputFile());
		new ObjectAttributesXmlReader(inputHouseholds.getHouseholdAttributes())
				.readFile(householdsConfigGroup.getInputHouseholdAttributesFile());
		Households filteredHouseholds = new HouseholdsImpl();
		// filter households
		Counter counter = new Counter(" household # ");
		for (Household household : inputHouseholds.getHouseholds().values()) {
			counter.incCounter();
			Set<Id<Person>> personIdsToRemove = new HashSet<>();
			for (Id<Person> personId : household.getMemberIds()) {
				if (!filteredPopulation.getPersons().keySet().contains(personId)) {
					personIdsToRemove.add(personId);
				}
			}
			for (Id<Person> personId : personIdsToRemove) {
				household.getMemberIds().remove(personId);
			}
			if (!household.getMemberIds().isEmpty()) {
				filteredHouseholds.getHouseholds().put(household.getId(), household);
				for (String attribute : ObjectAttributesUtils.getAllAttributeNames(
						inputHouseholds.getHouseholdAttributes(), household.getId().toString())) {
					filteredHouseholds.getHouseholdAttributes().putAttribute(household.getId().toString(),attribute,
							inputHouseholds.getHouseholdAttributes().getAttribute(household.getId().toString(), attribute));
				}
			}
		}
		counter.printCounter();
		// write households
		new HouseholdsWriterV10(filteredHouseholds).writeFile(
				outputPath + "households.xml.gz");
		new ObjectAttributesXmlWriter(filteredHouseholds.getHouseholdAttributes()).writeFile(
				outputPath + "households_attributes.xml.gz");
	}

	private Population filterPopulation(PlansConfigGroup plansConfigGroup, FacilitiesConfigGroup facilities) {
		// load population and facilities
		ActivityFacilities activityFacilities = FacilityUtils.readFacilities(facilities.getInputFile());
		Population inputPopulation = loadAndRouteInputPopulation(plansConfigGroup);
		Population filteredPopulation = PopulationUtils.getEmptyPopulation();
		// filter population
		Counter counter = new Counter(" person # ");
		boolean actInArea, actNotInArea, previousActInArea; Leg leg;
		for (Person person : inputPopulation.getPersons().values()) {
			counter.incCounter();
			if (person.getSelectedPlan() != null) {
				Activity firstAct = null; Activity lastAct = null;
				actInArea = false; actNotInArea = false; previousActInArea = false; leg = null;
				for (PlanElement pe : person.getSelectedPlan().getPlanElements()) {
					if (pe instanceof Activity) {
						Activity act = (Activity) pe;
						// remove all remote_home
						if (act.getType().contains("remote_home")) {
							act.setType(act.getType().substring(act.getType().indexOf("_")+1));
						}
						// make sure first and last act are of same type
						if (firstAct == null) {
							firstAct = act;
						} else {
							lastAct = act;
						}
						if (inArea(activityFacilities.getFacilities().get(act.getFacilityId()).getCoord())) {
							actInArea = true;
							if (!previousActInArea && leg != null && leg.getMode().equals("pt")) {
								leg.setMode(OUTAREA_PT);
							}
							previousActInArea = true;
						} else {
							actNotInArea = true;
							if (leg != null && leg.getMode().equals("pt")) {
								leg.setMode(OUTAREA_PT);
							} else if (!previousActInArea && leg != null && leg.getMode().equals("car")) {
								NetworkRoute route = (NetworkRoute) leg.getRoute();
								boolean routeIntersection = false;
								for (Id<Link> linkId : route.getLinkIds()) {
									if (inArea(this.carNetwork.getLinks().get(linkId).getCoord())) {
										routeIntersection = true;
										break;
									}
								}
								if (!routeIntersection) {
									leg.setRoute(null);
									leg.setMode(OUTAREA_CAR);
								}
							}
							previousActInArea = false;
						}
					} else {
						leg = (Leg) pe;
					}
				}
				// make sure first and last act are of same type
				if (lastAct != null && lastAct.getType().contains("home")) {
					lastAct.setType(firstAct.getType());
				}
				if (actInArea || checkForRouteIntersection(person.getSelectedPlan())) {
					filteredPopulation.addPerson(person);
					// copy attribute
					for (String attribute : ObjectAttributesUtils.getAllAttributeNames(
							inputPopulation.getPersonAttributes(), person.getId().toString())) {
						String newAttributeName = attribute.contains("remote_home") ?
								attribute.replace("remote_home", "home") : attribute;
						filteredPopulation.getPersonAttributes().putAttribute(
								person.getId().toString(), newAttributeName,
								inputPopulation.getPersonAttributes().getAttribute(
										person.getId().toString(), attribute));
					}
					// remove all tags except for "freight"
					if (filteredPopulation.getPersonAttributes().getAttribute(
							person.getId().toString(), "subpopulation") != null
							&& !filteredPopulation.getPersonAttributes().getAttribute(
							person.getId().toString(), "subpopulation").equals("freight")) {
						filteredPopulation.getPersonAttributes().removeAttribute(
								person.getId().toString(), "subpopulation");
					}
					// tag if commuting to / from outside of area of interest
					if (actNotInArea) {
						if (filteredPopulation.getPersonAttributes().getAttribute(
								person.getId().toString(), "subpopulation") == null) {
							filteredPopulation.getPersonAttributes().putAttribute(
									person.getId().toString(), "subpopulation", COMMUTER);
						}
					}
				}
			}
		}
		counter.printCounter();
		// write population
		new ObjectAttributesXmlWriter(filteredPopulation.getPersonAttributes()).writeFile(
				outputPath + "population_attributes.xml.gz");
		return filteredPopulation;
	}

	private boolean checkForRouteIntersection(Plan selectedPlan) {
		boolean routeIntersection = false;
		for (PlanElement pe : selectedPlan.getPlanElements()) {
			if (pe instanceof Leg && ((Leg) pe).getMode().equals("car")) {
				NetworkRoute route = (NetworkRoute) ((Leg) pe).getRoute();

				for (Id<Link> linkId : route.getLinkIds()) {
					if (inArea(this.carNetwork.getLinks().get(linkId).getCoord())) {
						routeIntersection = true;
						break;
					}
				}
			}
			if (routeIntersection) {
				break;
			}
		}
		return routeIntersection;
	}

	private Population loadAndRouteInputPopulation(PlansConfigGroup plansConfigGroup) {
		// load population
		Population inputPopulation = PopulationUtils.readPopulation(plansConfigGroup.getInputFile());
		new ObjectAttributesXmlReader(inputPopulation.getPersonAttributes())
				.readFile(plansConfigGroup.getInputPersonAttributeFile());
		// create initial car routes
		findInitialCarRoutes(inputPopulation);
		return inputPopulation;
	}

	private void findInitialCarRoutes(Population population) {
		TravelTime travelTime = new FreeSpeedTravelTime();
		TravelDisutility travelDisutility = new OnlyTimeDependentTravelDisutility(travelTime);
		PreProcessDijkstra preprocessDijkstra = new PreProcessDijkstra();
		preprocessDijkstra.run(carNetwork);
		LeastCostPathCalculator leastCostPathCalculator =
				new Dijkstra(carNetwork, travelDisutility, travelTime, preprocessDijkstra);
		RoutingModule routingModule =
				new NetworkRoutingModule("car", population.getFactory(), carNetwork, leastCostPathCalculator);
		MainModeIdentifier mainModeIdentifier = new MainModeIdentifierImpl();

		Counter counter = new Counter(" initial routing # ");
		for (Person person : population.getPersons().values()) {
			counter.incCounter();
			List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(
					person.getSelectedPlan(), routingModule.getStageActivityTypes());
			for (TripStructureUtils.Trip trip : trips) {
				if (mainModeIdentifier.identifyMainMode(trip.getTripElements()).equals("car")) {
					ActivityFacility origin =
							facilities.getFacilities().get(trip.getOriginActivity().getFacilityId());
					ActivityFacility destination =
							facilities.getFacilities().get(trip.getDestinationActivity().getFacilityId());
					List<Leg> legs = trip.getLegsOnly();
					if (legs.size() > 1) throw new IllegalStateException();
					List<? extends PlanElement> result =
							routingModule.calcRoute(origin, destination, legs.get(0).getDepartureTime(), person);
					legs.get(0).setRoute(((Leg)result.get(0)).getRoute());
				}
			}
		}
	}

	private boolean inArea(Coord coord) {
		if (coordCache.containsKey(coord)) {
			return coordCache.get(coord);
		} else {
			boolean coordIsInArea = coordAnalyzer.isCoordAffected(coord);
			coordCache.put(coord, coordIsInArea);
			return coordIsInArea;
		}
	}

}
