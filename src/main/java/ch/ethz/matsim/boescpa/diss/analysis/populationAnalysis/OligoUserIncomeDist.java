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

package ch.ethz.matsim.boescpa.diss.analysis.populationAnalysis;

import ch.ethz.matsim.boescpa.lib.tools.coordUtils.CoordAnalyzer;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.households.Household;
import org.matsim.households.HouseholdsReaderV10;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class OligoUserIncomeDist {

	public static void main(final String[] args) throws IOException {
		final String pathToHouseholds = args[0];
		final String pathToPopulation = args[1];
		final String pathToSHP = args[2];
		final double scaleFactor = Double.parseDouble(args[3]);

		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new HouseholdsReaderV10(scenario.getHouseholds()).readFile(pathToHouseholds);
		new PopulationReader(scenario).readFile(pathToPopulation);
		CoordAnalyzer coordAnalyzer = Utils.getCoordAnalyzer(pathToSHP);

		Map<String, Double> numberOfUsers = new HashMap<>();
		Map<String, Double> totalHouseholdIncome = new HashMap<>();
		for (Household household : scenario.getHouseholds().getHouseholds().values()) {
			for (Id<Person> personId : household.getMemberIds()) {
				if (!personId.toString().contains("freight") &&
						scenario.getPopulation().getPersons().containsKey(personId)) {
					Person p = scenario.getPopulation().getPersons().get(personId);
					if (!Utils.hasHomeInArea(p, coordAnalyzer)) continue;
					for (PlanElement pe : p.getSelectedPlan().getPlanElements()) {
						if (pe instanceof Leg) {
							if (((Leg) pe).getMode().equals("av")) {
								String avType = ((Leg) pe).getRoute().getRouteDescription();
								double users = numberOfUsers.getOrDefault(avType, 0.);
								numberOfUsers.put(avType, ++users);
								double income = totalHouseholdIncome.getOrDefault(avType, 0.);
								totalHouseholdIncome.put(avType, income + household.getIncome().getIncome());
							}
						}
					}
				}
			}
		}

		for (String avType : numberOfUsers.keySet()) {
			System.out.println("AV type: " + avType
					+ "; average user household income: " + totalHouseholdIncome.get(avType)/numberOfUsers.get(avType));
		}

		BufferedWriter writer = IOUtils.getBufferedWriter(pathToPopulation.replace(".xml.gz", "_savOligoUsers.csv"));
		writer.write("AV type; averageUserHouseholdIncome"); writer.newLine();
		for (String avType : new String[]{"pool_l","pool_m","pool_h","taxi_l","taxi_m","taxi_h"}) {
			if (numberOfUsers.keySet().contains(avType)) {
				writer.write(avType + ";" + totalHouseholdIncome.get(avType) / numberOfUsers.get(avType));
			} else {
				writer.write(avType + "; 0.0");
			}
			writer.newLine();
		}
		writer.close();
	}

}
