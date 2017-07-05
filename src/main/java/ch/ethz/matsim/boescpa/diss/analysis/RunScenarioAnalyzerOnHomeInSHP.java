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

package ch.ethz.matsim.boescpa.diss.analysis;

import ch.ethz.matsim.boescpa.analysis.scenarioAnalyzer.ScenarioAnalyzer;
import ch.ethz.matsim.boescpa.analysis.spatialCutters.NoCutter;
import ch.ethz.matsim.boescpa.diss.analysis.eventHandlers.*;
import ch.ethz.matsim.boescpa.lib.tools.NetworkUtils;
import ch.ethz.matsim.boescpa.lib.tools.PopulationUtils;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;

/**
 * Analyzes events for different statistics. Thereby considers only agents with Home in area specified by SHP-file.
 *
 * @author boescpa
 */
public class RunScenarioAnalyzerOnHomeInSHP {

	public static void main(String[] args) {
		Network network = NetworkUtils.readNetwork(args[0]);
		Population population = PopulationUtils.readPopulation(args[1]);
		String path2EventFile = args[2];
		String path2HomesSHP = args[3];
		int scaleFactor = Integer.parseInt(args[4]);

		try {
			// Analyze the events:
			ScenarioAnalyzerEventHandlerHomeInSHP[] handlers = {
					new AgentCounter(path2HomesSHP, population, network),
					new TripAnalyzer(path2HomesSHP, population, network),
					new TripActivityCrosscorrelator(path2HomesSHP, population, network),
					new MFDCreator(path2HomesSHP, population, network)
			};
			ScenarioAnalyzer scenarioAnalyzer = new ScenarioAnalyzer(path2EventFile, scaleFactor, handlers);
			scenarioAnalyzer.analyzeScenario();

			// Return the results:
			scenarioAnalyzer.createResults(path2EventFile + "_analysisResults.csv", new NoCutter());

		} catch (Exception e){
			e.printStackTrace();
		}
	}

}
