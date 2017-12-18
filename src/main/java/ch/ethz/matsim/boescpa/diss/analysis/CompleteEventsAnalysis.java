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
import ch.ethz.matsim.boescpa.diss.analysis.eventHandlers.targetFunctionUtils.accessibility.AccessibilityCalculator;
import ch.ethz.matsim.boescpa.diss.analysis.eventHandlers.targetFunctionUtils.accessibility.AccessiblityRouter;
import ch.ethz.matsim.boescpa.diss.analysis.populationAnalysis.AvgExecScore;
import ch.ethz.matsim.boescpa.diss.analysis.populationAnalysis.ExpectedMaximumUtility;
import ch.ethz.matsim.boescpa.diss.av.dynamicFleet.delayedDeployment.analysis.SavFleetStats;
import ch.ethz.matsim.boescpa.lib.tools.coordUtils.CoordAnalyzer;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.MatsimFacilitiesReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Map;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class CompleteEventsAnalysis {

	public static void main(final String[] args) throws IOException {
		String pathToPopulation = args[0];
		String pathToNetwork = args[1];
		String pathToFacilities = args[2];
		String pathToSchedule = args[3];
		String pathToConfig = args[4];
		String pathToHomesSHP = args[5];
		String pathToEvents = args[6];
		double scalingFactor = Double.parseDouble(args[7]);
		boolean isAV = Boolean.parseBoolean(args[8]);
		int LoS = args[8].equals("oligo") ? 1 : (isAV ? (pathToEvents.contains("180") ? 180 : 300) : 0);

		CoordAnalyzer ca = CoordAnalyzer.getCoordAnalyzer(pathToHomesSHP);

		Config config = ConfigUtils.loadConfig(pathToConfig);
		config.plans().setInputFile(pathToPopulation);
		config.network().setInputFile(pathToNetwork);
		config.facilities().setInputFile(pathToFacilities);
		config.transit().setTransitScheduleFile(pathToSchedule);

		Scenario scenario = ScenarioUtils.createScenario(config);
		new PopulationReader(scenario).readFile(config.plans().getInputFile());
		new MatsimNetworkReader(scenario.getNetwork()).readFile(config.network().getInputFile());
		new MatsimFacilitiesReader(scenario).readFile(config.facilities().getInputFile());
		new TransitScheduleReader(scenario).readFile(config.transit().getTransitScheduleFile());

		// Population-based:
		// - Avg Exec Score
		writeAvgExecScore(pathToPopulation, scenario.getPopulation(), ca);
		// - EMU
		writeExpectedMaximumUtility(pathToPopulation, scenario.getPopulation(), ca,
				config.planCalcScore().getMarginalUtilityOfMoney());

		// Events reader:
		EventsManager em = EventsUtils.createEventsManager();
		// - Accessibility
		AccessiblityRouter router = new AccessiblityRouter(scenario, em, pathToHomesSHP);
		// - SAVstats
		SavFleetStats fleetStats = LoS > 0? new SavFleetStats(scenario.getNetwork(), scenario.getPopulation(), LoS, ca) : null;
		if (fleetStats != null) fleetStats.addHandler(em);
		// - Scenario analysis
		ScenarioAnalyzerEventHandlerHomeInSHP[] handlers = {
				new AgentCounter(pathToHomesSHP, scenario.getPopulation(), scenario.getNetwork()),
				new TripAnalyzer(pathToHomesSHP, scenario.getPopulation(), scenario.getNetwork()),
				new MFDCreator(pathToHomesSHP, scenario.getPopulation(), scenario.getNetwork()),
				new TargetFunctionEvaluator(pathToHomesSHP, scenario.getPopulation(), scenario.getNetwork(),
						scenario.getActivityFacilities())
		};
		ScenarioAnalyzer scenarioAnalyzer = new ScenarioAnalyzer(null, (int)scalingFactor, handlers);
		scenarioAnalyzer.addHandlers(em);
		// read Events
		MatsimEventsReader reader = new MatsimEventsReader(em);
		reader.readFile(pathToEvents);

		// Event-based analysis
		// - Accessibility
		writeAccessibility(scenario.getNetwork(), scenario.getActivityFacilities(), ca, router);
		// - SAVstats
		if (fleetStats != null) fleetStats.writeOutput(scalingFactor);
		// - Scenario analysis
		scenarioAnalyzer.createResults(pathToEvents + "_analysisResultsTargetFunction.csv", new NoCutter());
	}

	private static void writeAccessibility(Network network, ActivityFacilities facilities, CoordAnalyzer ca,
										   AccessiblityRouter router) throws IOException {
		AccessibilityCalculator calculator = new AccessibilityCalculator(network, facilities, ca, router);
		Map<String,Map<String, Map<String, Double>>> totalAccessibilities = calculator.calculateTotalAccessibilities();
		Map<String, Map<String, Double>> averageAccessibilities =
				calculator.calculateOpportunityWeightedAverageAccessibilities(totalAccessibilities);
		calculator.writeAverageAccessibilities(averageAccessibilities, "./accessibilities.csv");
	}

	private static void writeExpectedMaximumUtility(String pathToPopulation, Population pop, CoordAnalyzer ca,
													double marginalUtilityOfMoney) throws IOException {
		ExpectedMaximumUtility emu = new ExpectedMaximumUtility(pop,marginalUtilityOfMoney, ca);
		BufferedWriter writer = IOUtils.getBufferedWriter(pathToPopulation.concat("_EMU.txt"));
		writer.write(emu.createResults());
		writer.close();
	}

	private static void writeAvgExecScore(String pathToPop, Population pop, CoordAnalyzer ca) throws IOException {
		AvgExecScore avgExecScore = new AvgExecScore(pop, ca);
		BufferedWriter writer = IOUtils.getBufferedWriter(pathToPop.concat("_avgExec.txt"));
		writer.write(avgExecScore.createResults());
		writer.close();
	}
}
