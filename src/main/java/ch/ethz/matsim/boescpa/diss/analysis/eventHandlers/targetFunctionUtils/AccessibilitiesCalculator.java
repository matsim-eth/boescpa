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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class AccessibilitiesCalculator implements PersonDepartureEventHandler, PersonArrivalEventHandler{
	private final static int GRID_CELL_SIZE = 100; // in meters
	private final static int MIN_NUMBER_OF_OBSERVATIONS_TO_BE_CONSIDERED = 10;
	// Peak hours for departures to count as peak trips.
	private final static Tuple<Double, Double> MORNING_PEAK = new Tuple<>(6.0*3600, 8.0*3600);
	private final static Tuple<Double, Double> EVENING_PEAK = new Tuple<>(16.5*3600, 18.5*3600);

	private final TargetFunctionEvaluator targetFunctionEvaluator;
	private final Network network;
	private final ActivityFacilities facilities;

	private final Map<String, Tuple<Double, String>> personsOnTheWay;
	private final Map<String, Map<String, List<Double>>> travelTimesPerZonePairForDifferentModes_Total;
	private final Map<String, Map<String, List<Double>>> travelTimesPerZonePairForDifferentModes_Peak;
	private final Map<String, Map<String, List<Double>>> travelTimesPerZonePairForDifferentModes_OffPeak;

	public AccessibilitiesCalculator(TargetFunctionEvaluator targetFunctionEvaluator, Network network,
									 ActivityFacilities facilities) {
		this.targetFunctionEvaluator = targetFunctionEvaluator;
		this.network = network;
		this.facilities = facilities;

		this.personsOnTheWay = new HashMap<>();
		this.travelTimesPerZonePairForDifferentModes_Total = new HashMap<>();
		this.travelTimesPerZonePairForDifferentModes_Peak = new HashMap<>();
		this.travelTimesPerZonePairForDifferentModes_OffPeak = new HashMap<>();

		this.reset(0);
	}

	public String createResults(int scaleFactor) {
		return null;
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
		if (!this.personsOnTheWay.keySet().contains(personId)) {
			throw new RuntimeException("Person " + personId + " arrived without ever having started!");
		}
		String fromTo = this.personsOnTheWay.get(personId).getSecond() + "_-_"
				+ getGridCell(personArrivalEvent.getLinkId());
		String mode = personArrivalEvent.getLegMode();
		double departureTime = this.personsOnTheWay.get(personId).getFirst();
		double travelTime = personArrivalEvent.getTime() - departureTime;
		addNewTravelTime(this.travelTimesPerZonePairForDifferentModes_Total, mode, fromTo, travelTime);
		if ((MORNING_PEAK.getFirst() <= departureTime && departureTime <= MORNING_PEAK.getSecond())
			|| (EVENING_PEAK.getFirst() <= departureTime && departureTime <= EVENING_PEAK.getSecond())) {
			addNewTravelTime(this.travelTimesPerZonePairForDifferentModes_Peak, mode, fromTo, travelTime);
		} else {
			addNewTravelTime(this.travelTimesPerZonePairForDifferentModes_OffPeak, mode, fromTo, travelTime);
		}
		this.personsOnTheWay.remove(personId);
	}

	private void addNewTravelTime(
			Map<String, Map<String, List<Double>>> travelTimesPerZonePairForDifferentModes,
								  String mode, String fromTo, double travelTime) {
		if (!travelTimesPerZonePairForDifferentModes.containsKey(mode)) {
			travelTimesPerZonePairForDifferentModes.put(mode, new HashMap<>());
		}
		if (!travelTimesPerZonePairForDifferentModes.get(mode).containsKey(fromTo)) {
			travelTimesPerZonePairForDifferentModes.get(mode).put(fromTo, new ArrayList<>());
		}
		travelTimesPerZonePairForDifferentModes.get(mode).get(fromTo).add(travelTime);
	}

	private String getGridCell(Id<Link> linkId) {
		Node fromNode = this.network.getLinks().get(linkId).getFromNode();
		long x = (long)(fromNode.getCoord().getX()/ GRID_CELL_SIZE); // floor x-coord to next grid cell size
		long y = (long)(fromNode.getCoord().getY()/ GRID_CELL_SIZE); // floor y-coord to next grid cell size
		return x + "_" + y;
	}
}
