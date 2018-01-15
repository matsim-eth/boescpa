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
import org.matsim.households.Households;
import org.matsim.households.HouseholdsReaderV10;

import java.io.BufferedWriter;
import java.io.IOException;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class SAVusage {

	public static void main (final String[] args) throws IOException {
		final String pathToHouseholds = args[0];
		final String pathToPopulation = args[1];
		final String pathToSHP = args[2];
		final double scaleFactor = Double.parseDouble(args[3]);

		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new HouseholdsReaderV10(scenario.getHouseholds()).readFile(pathToHouseholds);
		new PopulationReader(scenario).readFile(pathToPopulation);
		CoordAnalyzer coordAnalyzer = Utils.getCoordAnalyzer(pathToSHP);

		long numberOfAgents = 0L;
		long numberOfAgentsOnlyHome = 0L;
		long carUsingAgents = 0L;
		long numberOfHouseholds = 0L;
		long carUsingHouseholds = 0L;
		for (Household household : scenario.getHouseholds().getHouseholds().values()) {
			boolean isActiveHousehold = false;
			boolean isCarUsingHousehold = false;
			for (Id<Person> personId : household.getMemberIds()) {
				if (!personId.toString().contains("freight") &&
						scenario.getPopulation().getPersons().containsKey(personId)) {
					Person person = scenario.getPopulation().getPersons().get(personId);
					if (!Utils.hasHomeInArea(person, coordAnalyzer)) {
						continue;
					}
					numberOfAgents++;
					isActiveHousehold = true;
					Plan plan = person.getSelectedPlan();
					if (plan.getPlanElements().size() <= 1) numberOfAgentsOnlyHome++;
					for (PlanElement pe : plan.getPlanElements()) {
						if (pe instanceof Leg) {
							if (((Leg) pe).getMode().equals("car")) {
								carUsingAgents++;
								isCarUsingHousehold = true;
								break;
							}
						}
					}
				}
			}
			if (isActiveHousehold) numberOfHouseholds++;
			if (isCarUsingHousehold) carUsingHouseholds++;
		}

		//System.out.println("Agents: " + scaleFactor*numberOfAgents + "; AgentsOnlyHome: " + scaleFactor*numberOfAgentsOnlyHome + "; Households: " + scaleFactor*numberOfHouseholds);
		//System.out.println("Car users: " + scaleFactor*carUsingAgents + "; Car using households: " + scaleFactor*carUsingHouseholds);

		BufferedWriter writer = IOUtils.getBufferedWriter(pathToPopulation.replace(".xml.gz", "_savPopStats.csv"));
		writer.write("carUsers; " + scaleFactor*carUsingAgents);
		writer.newLine();
		writer.write("carUsingHouseholds; " + scaleFactor*carUsingHouseholds);
		writer.close();
	}

}
