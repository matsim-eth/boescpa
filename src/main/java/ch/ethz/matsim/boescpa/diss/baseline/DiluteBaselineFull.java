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
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.FacilitiesConfigGroup;
import org.matsim.core.config.groups.HouseholdsConfigGroup;
import org.matsim.core.config.groups.NetworkConfigGroup;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.*;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.PreProcessDijkstra;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.misc.Counter;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;
import org.matsim.households.*;
import org.matsim.pt.config.TransitConfigGroup;
import org.matsim.utils.objectattributes.ObjectAttributesUtils;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Dilutes a scenario to the provided area (SHP-file) - diluting in the traditional IVT sense. This means:
 * 	- all agents who at least pass through the area  are kept (this includes any agent with at least one activity in the area)
 * 	- the full original network and all original facilities are kept
 * 	- the full original pt service is kept
 *
 * @author boescpa
 */
public class DiluteBaselineFull {

	private final static String COMMUTER = "outAct";

	private final Map<Coord, Boolean> coordCache = new HashMap<>();
	private final CoordAnalyzer coordAnalyzer;
	private final String outputPath;
	private final Network network;
	private final ActivityFacilities facilities;

	private DiluteBaselineFull(Config config, String pathToSHPOfCutArea, String pathToOutputFolder) {
		// load network
		this.network = NetworkUtils.readNetwork(config.network().getInputFile());
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
		DiluteBaselineFull diluter = new DiluteBaselineFull(config, pathToSHPFile, pathToOutputFolder);

		// cut demand:
		Population inputPopulation = diluter.loadAndRouteInputPopulation(config.plans());
		Population filteredPopulation = diluter.filterPopulation(inputPopulation);
		diluter.filterHouseholds(config.households(), filteredPopulation);

		// copy facilities and supply
		try {
			diluter.copyFacilities(config.facilities());
			diluter.copyTransitSchedule(config.transit());
			diluter.copyVehicles(config.transit());
			diluter.copyNetwork(config.network());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void copyNetwork(NetworkConfigGroup networkConfigGroup) throws IOException {
		Files.copy(Paths.get(networkConfigGroup.getInputFile()),
				Paths.get(outputPath + "network.xml.gz"));
	}

	private void copyVehicles(TransitConfigGroup transitConfigGroup) throws IOException {
		Files.copy(Paths.get(transitConfigGroup.getVehiclesFile()),
				Paths.get(outputPath + "vehicles.xml.gz"));
	}

	private void copyTransitSchedule(TransitConfigGroup transitConfigGroup) throws IOException {
			Files.copy(Paths.get(transitConfigGroup.getTransitScheduleFile()),
					Paths.get(outputPath + "schedule.xml.gz"));
	}

	private void copyFacilities(FacilitiesConfigGroup facilitiesConfigGroup) throws IOException {
		Files.copy(Paths.get(facilitiesConfigGroup.getInputFile()),
				Paths.get(outputPath + "facilities.xml.gz"));
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

	private Population filterPopulation(Population inputPopulation) {
		// load population
		Population filteredPopulation = PopulationUtils.getEmptyPopulation();
		// filter population
		Counter counter = new Counter(" person # ");
		boolean actInArea, actNotInArea;
		for (Person person : inputPopulation.getPersons().values()) {
			counter.incCounter();
			if (person.getSelectedPlan() != null) {
				actInArea = false; actNotInArea = false;
				for (PlanElement pe : person.getSelectedPlan().getPlanElements()) {
					if (pe instanceof Activity) {
						Activity act = (Activity) pe;
						if (inArea(act.getCoord())) {
							actInArea = true;
						} else {
							actNotInArea = true;
						}
					}
				}
				if (actInArea || checkForRouteIntersection(person.getSelectedPlan())) {
					filteredPopulation.addPerson(person);
					// copy attribute
					for (String attribute : ObjectAttributesUtils.getAllAttributeNames(
							inputPopulation.getPersonAttributes(), person.getId().toString())) {
						filteredPopulation.getPersonAttributes().putAttribute(person.getId().toString(), attribute,
								inputPopulation.getPersonAttributes().getAttribute(person.getId().toString(), attribute));
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
		new PopulationWriter(filteredPopulation).write(
				outputPath + "population.xml.gz");
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
					if (inArea(this.network.getLinks().get(linkId).getCoord())) {
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
		preprocessDijkstra.run(network);
		LeastCostPathCalculator leastCostPathCalculator =
				new Dijkstra(network, travelDisutility, travelTime, preprocessDijkstra);
		RoutingModule routingModule =
				new NetworkRoutingModule("car", population.getFactory(), network, leastCostPathCalculator);
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
							facilities.getFacilities().get(trip.getOriginActivity().getFacilityId());
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
