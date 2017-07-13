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
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;

import java.util.Map;
import java.util.TreeMap;

import static ch.ethz.matsim.boescpa.analysis.scenarioAnalyzer.ScenarioAnalyzer.DEL;
import static ch.ethz.matsim.boescpa.analysis.scenarioAnalyzer.ScenarioAnalyzer.NL;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class VehicleKilometerCounter implements LinkLeaveEventHandler {

	private final TargetFunctionEvaluator targetFunctionEvaluator;
	private final Network network;

	private Map<String, Double> vehicleKilometersDriven;

	private final String ptBus = "pt-bus", ptOther = "pt-other", avTaxi = "av-taxi", avPool = "av-pool",
			car = "car", freight = "freight";

	public VehicleKilometerCounter(TargetFunctionEvaluator targetFunctionEvaluator, Network network) {
		this.targetFunctionEvaluator = targetFunctionEvaluator;
		this.network = network;
		reset(0);
	}

	@Override
	public void reset(int i) {
		this.vehicleKilometersDriven = new TreeMap<>();
		this.vehicleKilometersDriven.put(ptBus, 0.);
		this.vehicleKilometersDriven.put(ptOther, 0.);
		this.vehicleKilometersDriven.put(avTaxi, 0.);
		this.vehicleKilometersDriven.put(avPool, 0.);
		this.vehicleKilometersDriven.put(car, 0.);
		this.vehicleKilometersDriven.put(freight, 0.);
	}

	public String createResults(int scaleFactor) {
		String vehicleKilometers = "Vehicle Kilometers:" + NL;
		for (String vehicleType : this.vehicleKilometersDriven.keySet()) {
			int adjustedScaleFactor = vehicleType.contains("pt") ? 1 : scaleFactor;
			vehicleKilometers += vehicleType + DEL
					+ (long)(this.vehicleKilometersDriven.get(vehicleType)/1000*adjustedScaleFactor) + NL;
		}
		return vehicleKilometers;
	}

	@Override
	public void handleEvent(LinkLeaveEvent linkLeaveEvent) {
		String vehicleId = linkLeaveEvent.getVehicleId().toString();
		Id<Link> linkId = linkLeaveEvent.getLinkId();
		if (vehicleId.contains("av")) { // its an av-vehicle
			if (vehicleId.contains("taxi")) {
				addVKM(avTaxi, linkId);
			} else if (vehicleId.contains("pool")) {
				addVKM(avPool, linkId);
			} else {
				throw new RuntimeException("Undefined AV-mode: " + vehicleId);
			}
		} else if (vehicleId.contains("freight")) { // its a truck
			addVKM(freight, linkId);
		} else if (!vehicleId.contains("_") || vehicleId.contains("cb")) { // its a car
			if (targetFunctionEvaluator.isPersonToConsider(Id.createPersonId(vehicleId))) {
				addVKM(car, linkId);
			}
		} else { // its a pt-vehicle
			if (vehicleId.contains("NFB") || vehicleId.contains("BUS")) {
				addVKM(ptBus, linkId);
			} else {
				addVKM(ptOther, linkId);
			}
		}
	}

	private void addVKM(String type, Id<Link> linkId) {
		double vehicleKilometer = this.vehicleKilometersDriven.get(type);
		vehicleKilometer += this.network.getLinks().get(linkId).getLength();
		this.vehicleKilometersDriven.put(type, vehicleKilometer);
	}
}
