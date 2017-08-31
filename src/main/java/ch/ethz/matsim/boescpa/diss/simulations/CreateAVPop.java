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

import ch.ethz.matsim.boescpa.lib.tools.utils.PopulationUtils;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class CreateAVPop {

	public static void main(final String[] args) {
		String pathToInputPopulation = args[0];

		String pathToInputAttributes = pathToInputPopulation.substring(0, pathToInputPopulation.indexOf(".xml")) + "_attributes.xml.gz";
		String pathToOutputPopulation = pathToInputPopulation.substring(0, pathToInputPopulation.indexOf(".xml")) + "_onlyAV.xml.gz";

		Population population = PopulationUtils.readPopulation(pathToInputPopulation);
		new ObjectAttributesXmlReader(population.getPersonAttributes()).readFile(pathToInputAttributes);
		for (Person p : population.getPersons().values()) {
			if (population.getPersonAttributes().getAttribute(p.getId().toString(),"subpopulation") == null) {
				for (PlanElement pe : p.getSelectedPlan().getPlanElements()) {
					if (pe instanceof Leg && ((Leg) pe).getMode().equals("car")) {
						((Leg) pe).setMode("av");
					}
				}
			}
		}
		org.matsim.core.population.io.PopulationWriter writer = new PopulationWriter(population);
		writer.write(pathToOutputPopulation);
	}

}
