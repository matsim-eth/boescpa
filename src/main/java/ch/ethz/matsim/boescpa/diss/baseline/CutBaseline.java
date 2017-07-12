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
import org.matsim.core.config.groups.FacilitiesConfigGroup;
import org.matsim.core.config.groups.HouseholdsConfigGroup;
import org.matsim.core.config.groups.NetworkConfigGroup;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.scenario.ScenarioUtils;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Cuts a scenario to the provided area (SHP-file). This means:
 * 	- only those agents who spend their full day-plan in the area are kept
 * 	- only the network and facilities in the area are kept
 * 	- only the pt which at least passes the area is kept
 *
 * @author boescpa
 */
public class CutBaseline {

	private final Map<Coord, Boolean> coordCache = new HashMap<>();
	private final CoordAnalyzer coordAnalyzer;
	private final String outputPath;

	private CutBaseline(String pathToSHPOfCutArea, String pathToOutputFolder) {
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
		CutBaseline cutter = new CutBaseline(pathToSHPFile, pathToOutputFolder);

		// cut demand:
		Population filteredPopulation = cutter.filterPopulation(config.plans(), config.facilities());
		cutter.filterHouseholds(config.households(), filteredPopulation);
		ActivityFacilities filteredFacilities = cutter.filterFacilities(config.facilities(), filteredPopulation);

		// cut supply:
		TransitSchedule filteredSchedule = cutter.filterTransitSchedule(config.transit());
		cutter.filterVehicles(config.transit(), filteredSchedule);
		Network onlyCarNetwork = cutter.filterNetwork(config.network(), filteredSchedule);

		// repair scenario:
		cutter.reconnectLostFacilities(filteredFacilities, onlyCarNetwork);
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

	private Network filterNetwork(NetworkConfigGroup networkConfigGroup, TransitSchedule filteredSchedule) {
		// load network
		Network inputNetwork = NetworkUtils.readNetwork(networkConfigGroup.getInputFile());
		Network filteredNetwork = org.matsim.core.network.NetworkUtils.createNetwork();
		Set<Id<Link>> scheduleLinks = getScheduleLinks(filteredSchedule);
		// filter only-car-network
		Network onlyCarNetwork = org.matsim.core.network.NetworkUtils.createNetwork();
		for (Link link : inputNetwork.getLinks().values()) {
			if (scheduleLinks.contains(link.getId()) || inArea(link.getFromNode().getCoord()) || inArea(link.getToNode().getCoord())) {
				addLink(onlyCarNetwork, link);
			}
		}
		new NetworkCleaner().run(onlyCarNetwork);
		// filter network
		for (Link link : inputNetwork.getLinks().values()) {
			if (scheduleLinks.contains(link.getId()) || onlyCarNetwork.getLinks().keySet().contains(link.getId())) {
				addLink(filteredNetwork, link);
			}
		}
		// write network
		new NetworkWriter(filteredNetwork).write(outputPath + "network.xml.gz");
		return onlyCarNetwork;
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
		// filter schedule
		for (TransitLine transitLine : inputSchedule.getTransitLines().values()) {
			for (TransitRoute transitRoute : transitLine.getRoutes().values()) {
				for (TransitRouteStop transitStop : transitRoute.getStops()) {
					if (inArea(transitStop.getStopFacility().getCoord())) {
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
		// return facilities
		return filteredFacilities;
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
		ActivityFacilities activityFacilities =
				FacilityUtils.readFacilities(facilities.getInputFile());
		Population inputPopulation = PopulationUtils.readPopulation(plansConfigGroup.getInputFile());
		new ObjectAttributesXmlReader(inputPopulation.getPersonAttributes())
				.readFile(plansConfigGroup.getInputPersonAttributeFile());
		Population filteredPopulation = PopulationUtils.getEmptyPopulation();
		// filter population
		Counter counter = new Counter(" person # ");
		for (Person person : inputPopulation.getPersons().values()) {
			counter.incCounter();
			if (isFullyInArea(person, activityFacilities)) {
				filteredPopulation.addPerson(person);
				for (String attribute : ObjectAttributesUtils.getAllAttributeNames(
								inputPopulation.getPersonAttributes(), person.getId().toString())) {
					filteredPopulation.getPersonAttributes().putAttribute(person.getId().toString(), attribute,
							inputPopulation.getPersonAttributes().getAttribute(person.getId().toString(), attribute));
				}
			}
		}
		counter.printCounter();
		// write population
		new PopulationWriter(filteredPopulation).write(
				outputPath + "population.xml.gz");
		new ObjectAttributesXmlWriter(filteredPopulation.getPersonAttributes()).writeFile(
				outputPath + "population_attributes.xml.gz");
		return filteredPopulation;
	}

	private boolean isFullyInArea(Person person, ActivityFacilities activityFacilities) {
		if (person.getSelectedPlan() != null) {
			for (PlanElement pe : person.getSelectedPlan().getPlanElements()) {
				if (pe instanceof Activity) {
					Activity act = (Activity) pe;
					if (!inArea(activityFacilities.getFacilities().get(act.getFacilityId()).getCoord())) {
						return false;
					}
				}
			}
		}
		return true;
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
