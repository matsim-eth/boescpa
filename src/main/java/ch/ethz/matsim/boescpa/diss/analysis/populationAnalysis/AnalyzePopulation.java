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
import ch.ethz.matsim.boescpa.lib.tools.utils.PopulationUtils;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedWriter;
import java.io.IOException;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class AnalyzePopulation {

	public static void main(final String[] args) throws IOException {
		String pathToPopulation = args[0];
		String pathToConfig = args[1];
		String path2HomesSHP = args[2];

		Config config = ConfigUtils.loadConfig(pathToConfig);
		Population population = PopulationUtils.readPopulation(pathToPopulation);
		CoordAnalyzer coordAnalyzer = Utils.getCoordAnalyzer(path2HomesSHP);
		double marginalUtilityOfMoney = config.planCalcScore().getMarginalUtilityOfMoney();

		ExpectedMaximumUtility emu = new ExpectedMaximumUtility(population, marginalUtilityOfMoney, coordAnalyzer);
		AvgExecScore aes = new AvgExecScore(population, coordAnalyzer);
		String out = aes.createResults() + emu.createResults();

		BufferedWriter writer = IOUtils.getBufferedWriter(pathToPopulation.concat("_analysis.txt"));
		writer.write(out);
		writer.close();
	}

}
