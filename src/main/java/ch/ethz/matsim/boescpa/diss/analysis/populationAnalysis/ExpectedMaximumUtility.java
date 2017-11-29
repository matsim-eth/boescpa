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

import ch.ethz.matsim.boescpa.analysis.scenarioAnalyzer.ScenarioAnalyzer;
import ch.ethz.matsim.boescpa.lib.tools.coordUtils.CoordAnalyzer;
import ch.ethz.matsim.boescpa.lib.tools.utils.PopulationUtils;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Calculates the Expected Maximum Utility of a population as defined in:
 * Kaddoura, I., B. Kickhöfer, A. Neumann and A. Tirachini (2015) Optimal
 * public transport pricing - towards an agent-based marginal social cost
 * approach, Journal of Transport Economics and Policy, 49 (2) 200–218.
 *
 * @author boescpa
 */
public class ExpectedMaximumUtility {

	private final Population population;
	private final double absMarginalUtilityOfMoney;
	private final CoordAnalyzer coordAnalyzer;
	private final DecimalFormat df;

	public ExpectedMaximumUtility(Population population, double marginalUtilityOfMoney, CoordAnalyzer coordAnalyzer) {
		this.population = population;
		this.absMarginalUtilityOfMoney = Math.abs(marginalUtilityOfMoney);
		this.coordAnalyzer = coordAnalyzer;
		this.df = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
		this.df.setMaximumFractionDigits(340);
	}

	public ExpectedMaximumUtility(String pathToPopulation, double marginalUtilityOfMoney, String path2HomesSHP) {
		this(PopulationUtils.readPopulation(pathToPopulation), marginalUtilityOfMoney, Utils.getCoordAnalyzer(path2HomesSHP));
	}

	public static void main(final String[] args) throws IOException {
		String pathToPopulation = args[0];
		String pathToConfig = args[1];
		String pathToHomesSHP = args[2];
		ExpectedMaximumUtility emu = new ExpectedMaximumUtility(pathToPopulation,
				ConfigUtils.loadConfig(pathToConfig).planCalcScore().getMarginalUtilityOfMoney(), pathToHomesSHP);
		BufferedWriter writer = IOUtils.getBufferedWriter(pathToPopulation.concat("_EMU.txt"));
		writer.write(emu.createResults());
		writer.close();
	}

	public String createResults() {
		double emuSum = 0;
		for (Person person : population.getPersons().values()) {
			if (!person.getId().toString().contains("pt") && Utils.hasHomeInArea(person, coordAnalyzer)) {
				double plans95MaxScore = 0.95*person.getPlans().stream().mapToDouble(Plan::getScore).max().orElse(1);
				double expSumOfPlansScore = 0;
				for (Plan plan : person.getPlans()) {
					expSumOfPlansScore += Math.exp(plan.getScore()-plans95MaxScore);
				}
				if (expSumOfPlansScore > 0) {
					double logSumOfPlansScore = plans95MaxScore + Math.log(expSumOfPlansScore);
					emuSum += logSumOfPlansScore / absMarginalUtilityOfMoney;
				}
			}
		}
		return "Expected Maximum Utility population: " + ScenarioAnalyzer.DEL + df.format(emuSum) + ScenarioAnalyzer.NL;
	}

}
