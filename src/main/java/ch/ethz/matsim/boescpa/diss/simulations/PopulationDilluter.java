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

import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.io.PopulationWriter;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class PopulationDilluter {

	public static void main(String[] args) {
		String pathToInputPopulation = args[0];
		double shareOfPopToKeep = Double.parseDouble(args[1]);

		String pathToOutputPopulation = pathToInputPopulation.substring(0, pathToInputPopulation.indexOf(".xml")) + "_" + args[1] + ".xml.gz";

		Random random = new Random(2345);
		for (int i = 0; i < 1000; i++) random.nextDouble();

		Population population = ch.ethz.matsim.boescpa.lib.tools.utils.PopulationUtils.readPopulation(pathToInputPopulation);

		long numberOfAgentsInPop = Math.round(population.getPersons().size() * (1-shareOfPopToKeep));
		Set<Person> toRemove = new HashSet<>();
		while(true) {
			for (Person person : population.getPersons().values()) {
				if (!(random.nextDouble() < shareOfPopToKeep)) {
					toRemove.add(person);
					if (toRemove.size() >= numberOfAgentsInPop) break;
				}
			}
			if (toRemove.size() >= numberOfAgentsInPop) break;
		}
		for (Person person : toRemove) {
			population.removePerson(person.getId());
		}

		PopulationWriter writer = new PopulationWriter(population);
		writer.write(pathToOutputPopulation);
	}
}
