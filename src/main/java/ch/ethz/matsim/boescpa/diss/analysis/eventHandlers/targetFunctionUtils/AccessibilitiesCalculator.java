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

package ch.ethz.matsim.boescpa.diss.analysis.eventHandlers.targetFunctionUtils;

import ch.ethz.matsim.boescpa.diss.analysis.eventHandlers.TargetFunctionEvaluator;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.utils.charts.XYLineChart;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;

import java.io.BufferedWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

import static ch.ethz.matsim.boescpa.analysis.scenarioAnalyzer.ScenarioAnalyzer.DEL;
import static ch.ethz.matsim.boescpa.analysis.scenarioAnalyzer.ScenarioAnalyzer.NL;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class AccessibilitiesCalculator implements PersonDepartureEventHandler, PersonArrivalEventHandler{
	private final TargetFunctionEvaluator targetFunctionEvaluator;
	private final Network network;
	private final Map<String, Double> opportunities;

	private final Map<String, Tuple<Double, String>> personsOnTheWay;
	private final Map<String, Map<Tuple<String, String>, List<Double>>> travelTimesPerZonePairForDifferentModes_Total;
	private final Map<String, Map<Tuple<String, String>, List<Double>>> travelTimesPerZonePairForDifferentModes_Peak;
	private final Map<String, Map<Tuple<String, String>, List<Double>>> travelTimesPerZonePairForDifferentModes_OffPeak;
	private final AccessibilitiesCalculatorConfig config;
	private int iteration;

	private final DecimalFormat df;

	public AccessibilitiesCalculator(TargetFunctionEvaluator targetFunctionEvaluator, Network network,
									 ActivityFacilities facilities) {
		this(new AccessibilitiesCalculatorConfig(), targetFunctionEvaluator, network, facilities);
	}

	public AccessibilitiesCalculator(AccessibilitiesCalculatorConfig config,
									 TargetFunctionEvaluator targetFunctionEvaluator, Network network,
									 ActivityFacilities facilities) {
		this.config = config;
		this.targetFunctionEvaluator = targetFunctionEvaluator;
		this.network = network;
		this.opportunities = calculateOpportunities(facilities);

		this.personsOnTheWay = new HashMap<>();
		this.travelTimesPerZonePairForDifferentModes_Total = new HashMap<>();
		this.travelTimesPerZonePairForDifferentModes_Peak = new HashMap<>();
		this.travelTimesPerZonePairForDifferentModes_OffPeak = new HashMap<>();

		this.reset(0);

		this.df = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
		this.df.setMaximumFractionDigits(340);
	}

	/**
	 * Calculates opportunities for each grid-cell based on opportunities specified in config.
	 */
	private Map<String, Double> calculateOpportunities(ActivityFacilities facilities) {
		Map<String, Double> opportunities = new HashMap<>();
		for (ActivityFacility facility : facilities.getFacilities().values()) {
			String gridCell = getGridCell(facility.getLinkId());
			double opportunity = opportunities.getOrDefault(gridCell, 0.);
			for (String opportunityMode : config.getOpportunityActivities()) {
				if (facility.getActivityOptions().containsKey(opportunityMode)) {
					opportunity += facility.getActivityOptions().get(opportunityMode).getCapacity();
				}
			}
			opportunities.put(gridCell, opportunity);
		}
		return opportunities;
	}

	public String createResults(int scaleFactor) {
		String results = "Total accessibilities:" + NL;
		Map<String, Map<String, Double>> totalAccessibilities = calculateAccessibilities(
				scaleFactor, this.travelTimesPerZonePairForDifferentModes_Total);
		results += getAccessibilityString(totalAccessibilities);
		results += NL + "Peak accessibilities:" + NL;
		Map<String, Map<String, Double>> peakAccessibilities = calculateAccessibilities(
				scaleFactor, this.travelTimesPerZonePairForDifferentModes_Peak);
		results += getAccessibilityString(peakAccessibilities);
		results += NL + "Offpeak accessibilities: " + NL;
		Map<String, Map<String, Double>> offpeakAccessibilities = calculateAccessibilities(
				scaleFactor, this.travelTimesPerZonePairForDifferentModes_OffPeak);
		results += getAccessibilityString(offpeakAccessibilities);
		return results;
	}

	private String getAccessibilityString(Map<String, Map<String, Double>> accessibilities) {
		String accessibilityString = "";
		for (String mode : accessibilities.keySet()) {
			double avgAccMode = accessibilities.get(mode).values().stream().mapToDouble(a -> a).filter(a -> a > 0).average().orElse(0.);
			accessibilityString += NL + mode + DEL + df.format(avgAccMode) + NL;
			for (String gridCell : accessibilities.get(mode).keySet()) {
				accessibilityString += gridCell + DEL + df.format(accessibilities.get(mode).get(gridCell)) + DEL;
			}
		}
		return accessibilityString + NL;
	}

	private Map<String, Map<String, Double>> calculateAccessibilities(double scaleFactor,
			Map<String, Map<Tuple<String, String>, List<Double>>> travelTimesPerZonePairForDifferentModes) {
		List<String> modesToEvaluate = config.getModesToEvaluate();
		List<Double> modesToEvaluateDeterrenceFunctionBetas = config.getModesToEvaluateDeterrenceFunctionBetas();
		Map<String, Map<String, Double>> accessibilities = new HashMap<>();
		for (String mode : travelTimesPerZonePairForDifferentModes.keySet()) {
			if (modesToEvaluate.contains(mode)) {
				double modeBeta = modesToEvaluateDeterrenceFunctionBetas.get(modesToEvaluate.indexOf(mode));
				Map<Tuple<String, String>, List<Double>> travelTimes =
						travelTimesPerZonePairForDifferentModes.get(mode);
				Map<String, Double> modeAccessibilities = new HashMap<>();
				for (Tuple<String, String> fromTo : travelTimes.keySet()) {
					if (travelTimes.get(fromTo).size() >= config.getMinObservationsToBeConsidered()) {
						double accessibility = modeAccessibilities.getOrDefault(fromTo.getFirst(), 0.);
						double avgTravelTime = travelTimes.get(fromTo)
								.stream().mapToDouble(a -> a).average().orElse(0.);
						accessibility += scaleFactor *
								opportunities.getOrDefault(fromTo.getSecond(), 0.) * Math.exp(-modeBeta * avgTravelTime);
						modeAccessibilities.put(fromTo.getFirst(), accessibility);
					}
				}
				accessibilities.put(mode, modeAccessibilities);
			}
		}
		return accessibilities;
	}

	public void reset(int i) {
		this.iteration = i;
		this.personsOnTheWay.clear();
		this.travelTimesPerZonePairForDifferentModes_Total.clear();
		this.travelTimesPerZonePairForDifferentModes_Peak.clear();
		this.travelTimesPerZonePairForDifferentModes_OffPeak.clear();
	}

	@Override
	public void handleEvent(PersonDepartureEvent personDepartureEvent) {
		if (targetFunctionEvaluator.isPersonToConsider(personDepartureEvent.getPersonId())) {
			String personId = personDepartureEvent.getPersonId().toString();
			if (this.personsOnTheWay.keySet().contains(personId)) {
				throw new RuntimeException("Person " + personId + " departed again but is already on the way!");
			}
			this.personsOnTheWay.put(personId, new Tuple<>(personDepartureEvent.getTime(),
					getGridCell(personDepartureEvent.getLinkId())));
		}
	}

	@Override
	public void handleEvent(PersonArrivalEvent personArrivalEvent) {
		String personId = personArrivalEvent.getPersonId().toString();
		if (targetFunctionEvaluator.isPersonToConsider(personArrivalEvent.getPersonId())) {
			if (!this.personsOnTheWay.keySet().contains(personId)) {
				throw new RuntimeException("Person " + personId + " arrived without ever having started!");
			}
			String from = this.personsOnTheWay.get(personId).getSecond();
			String to = getGridCell(personArrivalEvent.getLinkId());
			String mode = personArrivalEvent.getLegMode();
			double departureTime = this.personsOnTheWay.get(personId).getFirst();
			double travelTime = personArrivalEvent.getTime() - departureTime;
			addNewTravelTime(this.travelTimesPerZonePairForDifferentModes_Total, mode, from, to, travelTime);
			if ((config.getMorningPeak().getFirst() <= departureTime && departureTime <= config.getMorningPeak().getSecond())
					|| (config.getEveningPeak().getFirst() <= departureTime && departureTime <= config.getEveningPeak().getSecond())) {
				addNewTravelTime(this.travelTimesPerZonePairForDifferentModes_Peak, mode, from, to, travelTime);
			} else {
				addNewTravelTime(this.travelTimesPerZonePairForDifferentModes_OffPeak, mode, from, to, travelTime);
			}
			this.personsOnTheWay.remove(personId);
		}
	}

	private void addNewTravelTime(
			Map<String, Map<Tuple<String, String>, List<Double>>> travelTimesPerZonePairForDifferentModes,
								  String mode, String from, String to, double travelTime) {
		if (!travelTimesPerZonePairForDifferentModes.containsKey(mode)) {
			travelTimesPerZonePairForDifferentModes.put(mode, new HashMap<>());
		}
		Tuple<String, String> fromTo = new Tuple<>(from, to);
		if (!travelTimesPerZonePairForDifferentModes.get(mode).containsKey(fromTo)) {
			travelTimesPerZonePairForDifferentModes.get(mode).put(fromTo, new ArrayList<>());
		}
		travelTimesPerZonePairForDifferentModes.get(mode).get(fromTo).add(travelTime);
	}

	private String getGridCell(Id<Link> linkId) {
		Node fromNode = this.network.getLinks().get(linkId).getFromNode();
		// floor x-coord to next grid cell size
		long x = (long)(fromNode.getCoord().getX()/config.getGridCellSize());
		// floor y-coord to next grid cell size
		long y = (long)(fromNode.getCoord().getY()/config.getGridCellSize());
		return x + "_" + y;
	}

	// ****************************************************************************************
	// In Sim Outputs:

	private BufferedWriter inSimResultsWriter_Total;
	private BufferedWriter inSimResultsWriter_Peak;
	private BufferedWriter inSimResultsWriter_Offpeak;
	private Map<String, Map<Integer, Double>> totalHistory;
	private double countsScaleFactor;
	private String pathPNG;

	public void prepareForInSimResults(String outputFilePath, double countsScaleFactor) {
		this.countsScaleFactor = countsScaleFactor;
		String header = "it" + DEL + "mode" + DEL + "OD-pairs and accessibilities";
		this.inSimResultsWriter_Total =
				IOUtils.getBufferedWriter(outputFilePath + "accessibilities_total.csv");
		this.inSimResultsWriter_Peak =
				IOUtils.getBufferedWriter(outputFilePath + "accessibilities_peak.csv");
		this.inSimResultsWriter_Offpeak =
				IOUtils.getBufferedWriter(outputFilePath + "accessibilities_offpeak.csv");
		try {
			this.inSimResultsWriter_Total.write(header);
			this.inSimResultsWriter_Total.flush();
			this.inSimResultsWriter_Peak.write(header);
			this.inSimResultsWriter_Peak.flush();
			this.inSimResultsWriter_Offpeak.write(header);
			this.inSimResultsWriter_Offpeak.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		// prepare history map
		totalHistory = new HashMap<>();
		for (String mode : config.getModesToEvaluate()) {
			totalHistory.put(mode, new HashMap<>());
		}
		this.pathPNG = outputFilePath + "accessibilities_total";
	}

	public void createInSimResults() {
		Map<String, Map<String, Double>> totalAccessibilities = calculateAccessibilities(
				this.countsScaleFactor, this.travelTimesPerZonePairForDifferentModes_Total);
		updateHistory(totalAccessibilities);
		Map<String, Map<String, Double>> peakAccessibilities = calculateAccessibilities(
				this.countsScaleFactor, this.travelTimesPerZonePairForDifferentModes_Peak);
		Map<String, Map<String, Double>> offpeakAccessibilities = calculateAccessibilities(
				this.countsScaleFactor, this.travelTimesPerZonePairForDifferentModes_OffPeak);
		// write results
		try {
			inSimResultsWriter_Total.write(getInSimResults(totalAccessibilities));
			inSimResultsWriter_Total.flush();
			inSimResultsWriter_Peak.write(getInSimResults(peakAccessibilities));
			inSimResultsWriter_Peak.flush();
			inSimResultsWriter_Offpeak.write(getInSimResults(offpeakAccessibilities));
			inSimResultsWriter_Offpeak.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		// plot graphs
		if (this.iteration > 0) {
			XYLineChart chart = new XYLineChart("Average Accessibilities", "iteration", "mode");
			for (String mode : this.totalHistory.keySet()) {
				Map<Integer, Double> modeHistory = this.totalHistory.get(mode);
				chart.addSeries(mode, modeHistory);
			}
			chart.saveAsPng(this.pathPNG + ".png", 800, 600);
		}
	}

	private void updateHistory(Map<String, Map<String, Double>> totalAccessibilities) {
		for (String mode : config.getModesToEvaluate()) {
			double avgAccMode = 0.;
			if (totalAccessibilities.keySet().contains(mode)) {
				avgAccMode = totalAccessibilities.get(mode).values().stream().mapToDouble(a -> a).filter(a -> a > 0).average().orElse(0.);
			}
			this.totalHistory.get(mode).put(this.iteration, avgAccMode);
		}
	}

	private String getInSimResults(Map<String, Map<String, Double>> accessibilities) {
		String accessibilityString = "";
		for (String mode : accessibilities.keySet()) {
			accessibilityString += NL;
			double avgAccMode = accessibilities.get(mode).values().stream().mapToDouble(a -> a).filter(a -> a > 0).average().orElse(0.);
			accessibilityString += this.iteration + DEL + mode + DEL + df.format(avgAccMode) + DEL;
			for (String gridCell : accessibilities.get(mode).keySet()) {
				accessibilityString += gridCell + DEL + df.format(accessibilities.get(mode).get(gridCell)) + DEL;
			}
		}
		return accessibilityString;
	}

	public void closeFiles() {
		try {
			inSimResultsWriter_Total.close();
			inSimResultsWriter_Peak.close();
			inSimResultsWriter_Offpeak.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
