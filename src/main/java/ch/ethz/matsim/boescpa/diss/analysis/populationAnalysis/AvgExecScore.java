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
import ch.ethz.matsim.boescpa.lib.tools.utils.SHPFileUtils;
import com.vividsolutions.jts.geom.Geometry;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.io.IOUtils;
import org.opengis.feature.simple.SimpleFeature;

import java.io.BufferedWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class AvgExecScore {

	private final Population population;
	private final CoordAnalyzer coordAnalyzer;
	private final DecimalFormat df;

	public AvgExecScore(Population population, CoordAnalyzer coordAnalyzer) {
		this.population = population;
		this.coordAnalyzer = coordAnalyzer;
		this.df = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
		this.df.setMaximumFractionDigits(340);
	}

	public AvgExecScore(Population population, String path2HomesSHP) {
		this(population, getCoordAnalyzer(path2HomesSHP));
	}

	public static void main(final String[] args) throws IOException {
		String pathToPopulation = args[0];
		String pathToHomesSHP = args[1];
		AvgExecScore avgExecScore = new AvgExecScore(PopulationUtils.readPopulation(pathToPopulation), pathToHomesSHP);
		BufferedWriter writer = IOUtils.getBufferedWriter(pathToPopulation.concat("_avgExec.txt"));
		writer.write(avgExecScore.createResults());
		writer.close();
	}

	private static CoordAnalyzer getCoordAnalyzer(String path2HomesSHP) {
		Set<SimpleFeature> features = new HashSet<>();
		SHPFileUtils util = new SHPFileUtils();
		features.addAll(ShapeFileReader.getAllFeatures(path2HomesSHP));
		Geometry area = util.mergeGeometries(features);
		return new CoordAnalyzer(area);
	}

	public String createResults() {
		double scoreSum = 0;
		double numberOfAgents = 0;
		for (Person person : population.getPersons().values()) {
			if (!person.getId().toString().contains("pt") && hasHomeInArea(person)) {
				double score = person.getSelectedPlan().getScore();
				if (score > -10000) {
					scoreSum += score;
					numberOfAgents++;
				}
			}
		}
		return "Average Exec Score: " + ScenarioAnalyzer.DEL + df.format(scoreSum/numberOfAgents) + ScenarioAnalyzer.NL;
	}

	private boolean hasHomeInArea(Person person) {
		Activity homeAct = (Activity) person.getSelectedPlan().getPlanElements().get(0);
		return coordAnalyzer.isCoordAffected(homeAct.getCoord());
	}

}
