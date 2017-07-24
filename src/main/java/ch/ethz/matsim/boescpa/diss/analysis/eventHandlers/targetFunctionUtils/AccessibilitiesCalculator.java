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
import org.matsim.core.utils.collections.Tuple;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;

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
			accessibilityString += NL + mode + DEL + avgAccMode + NL;
			for (String gridCell : accessibilities.get(mode).keySet()) {
				accessibilityString += gridCell + DEL + accessibilities.get(mode).get(gridCell) + DEL;
			}
		}
		return accessibilityString + NL;
	}

	private Map<String, Map<String, Double>> calculateAccessibilities(int scaleFactor,
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
}
