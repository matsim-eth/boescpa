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

package ch.ethz.matsim.boescpa.diss.simulations;

import ch.ethz.matsim.boescpa.lib.tools.utils.FacilityUtils;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class AddEmptyRidesToPop {
	private final static double PROB_TRIP = 0.1;

	public static void main(String[] args) {
		String pathToInputPopulation = args[0];
		String pathToFacilities = args[1];
		double numberOfEmptyTripsPerAgentInPop = Double.parseDouble(args[2]);

		String pathToOutputPopulation = pathToInputPopulation.substring(0, pathToInputPopulation.indexOf(".xml")) + "_" + args[2] + "emptyTripsPerAgent.xml.gz";
		String pathToInputAttributes = pathToInputPopulation.substring(0, pathToInputPopulation.indexOf(".xml")) + "_attributes.xml.gz";
		String pathToOutputAttributes = pathToInputPopulation.substring(0, pathToInputPopulation.indexOf(".xml")) + "_attributes_" + args[2] + "emptyTripsPerAgent.xml.gz";

		Random random = new Random(1234);
		for (int i = 0; i < 1000; i++) random.nextDouble();

		Population population = ch.ethz.matsim.boescpa.lib.tools.utils.PopulationUtils.readPopulation(pathToInputPopulation);
		new ObjectAttributesXmlReader(population.getPersonAttributes()).readFile(pathToInputAttributes);
		ActivityFacilities facilities = FacilityUtils.readFacilities(pathToFacilities);

		long numberOfEmptyTrips = Math.round(population.getPersons().size() * numberOfEmptyTripsPerAgentInPop);
		Set<Person> newEmptTrips = new HashSet<>();
		while(true) {
			Tuple<Id<Link>, Id<Link>> locations = null;
			for (Person person : population.getPersons().values()) {
				Activity lastAct = null;
				for (PlanElement element : person.getSelectedPlan().getPlanElements()) {
					if (element instanceof Activity && lastAct != null && random.nextDouble() < PROB_TRIP) {
						if (locations == null) {
							locations = new Tuple<>(
									facilities.getFacilities().get(lastAct.getFacilityId()).getLinkId(),
									facilities.getFacilities().get(((Activity)element).getFacilityId()).getLinkId());
						} else {
							Tuple<Double, Double> travelTimes =
									new Tuple<>(lastAct.getEndTime(), ((Activity)element).getStartTime());
							newEmptTrips.add(createNewEmptyTrip(population.getFactory(), locations, travelTimes,
											newEmptTrips.size()));
							locations = null;
							if (newEmptTrips.size() >= numberOfEmptyTrips) break;
						}
					}
					if (newEmptTrips.size() >= numberOfEmptyTrips) break;
					if (element instanceof Activity) lastAct = (Activity)element;
				}
				if (newEmptTrips.size() >= numberOfEmptyTrips) break;
			}
			if (newEmptTrips.size() >= numberOfEmptyTrips) break;
		}
		for (Person emptyTrip : newEmptTrips) {
			population.addPerson(emptyTrip);
			population.getPersonAttributes().putAttribute(emptyTrip.getId().toString(),
					"subpopulation", "freight"); // we assume the same "behaviour" as for freight
		}

		PopulationWriter writer = new PopulationWriter(population);
		writer.write(pathToOutputPopulation);
		ObjectAttributesXmlWriter attributesWriter = new ObjectAttributesXmlWriter(population.getPersonAttributes());
		attributesWriter.writeFile(pathToOutputAttributes);
	}

	private static Person createNewEmptyTrip(PopulationFactory factory, Tuple<Id<Link>, Id<Link>> locations,
											 Tuple<Double, Double> travelTimes, int number) {
		// new empty trip
		Person emptyTrip = factory.createPerson(Id.createPersonId("empty_trip_" + number));
		Plan plan = factory.createPlan();
		emptyTrip.addPlan(plan);
		// startActivity
		Activity startActivity = factory.createActivityFromLinkId("empty", locations.getFirst());
		startActivity.setEndTime(travelTimes.getFirst());
		plan.addActivity(startActivity);
		// leg
		Leg newLeg = factory.createLeg("car");
		plan.addLeg(newLeg);
		// endActivity
		Activity endActivity = factory.createActivityFromLinkId("empty", locations.getSecond());
		endActivity.setStartTime(travelTimes.getSecond());
		plan.addActivity(endActivity);
		return emptyTrip;
	}
}
