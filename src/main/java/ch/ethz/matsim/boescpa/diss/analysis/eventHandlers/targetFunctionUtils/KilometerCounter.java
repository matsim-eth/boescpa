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
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.vehicles.Vehicle;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static ch.ethz.matsim.boescpa.analysis.scenarioAnalyzer.ScenarioAnalyzer.DEL;
import static ch.ethz.matsim.boescpa.analysis.scenarioAnalyzer.ScenarioAnalyzer.NL;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class KilometerCounter implements LinkLeaveEventHandler, PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler {

	private final TargetFunctionEvaluator targetFunctionEvaluator;
	private final Network network;

	private Map<String, Double> vehicleKilometersDriven;
	private Map<String, Double> passengerKilometersDriven;
	private Map<String, Long> numberOfPassengers;

	private Map<Id<Vehicle>, Map<Id<Person>, Double>> vehiclesToObserve;

	private final String ptBus = "pt_bus", ptOther = "pt_other", car = "car", freight = "freight";


	public KilometerCounter(TargetFunctionEvaluator targetFunctionEvaluator, Network network) {
		this.targetFunctionEvaluator = targetFunctionEvaluator;
		this.network = network;
		reset(0);
	}

	@Override
	public void reset(int i) {
		this.vehicleKilometersDriven = new TreeMap<>();
		this.passengerKilometersDriven = new TreeMap<>();
		this.numberOfPassengers = new TreeMap<>();
		this.vehiclesToObserve = new HashMap<>();
	}

	public String createResults(int scaleFactor) {
		String vehicleKilometers = getVehicleKilometerString(scaleFactor);
		String passengerKilometers = getPassengerKilometerString(scaleFactor);
		return vehicleKilometers + NL + passengerKilometers;
	}

	private String getPassengerKilometerString(int scaleFactor) {
		String passengerKilometers = "Passenger Kilometers and number of passengers:" + NL;
		for (String serviceType : this.passengerKilometersDriven.keySet()) {
			passengerKilometers += serviceType + DEL
					+ (long)(this.passengerKilometersDriven.get(serviceType)/1000*scaleFactor) + DEL
					+ this.numberOfPassengers.get(serviceType)*scaleFactor + NL;
		}
		return passengerKilometers;
	}

	private String getVehicleKilometerString(int scaleFactor) {
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
		addVKM(getServiceType(linkLeaveEvent.getVehicleId()), linkLeaveEvent.getLinkId());
		addPKM(linkLeaveEvent.getVehicleId(), linkLeaveEvent.getLinkId());
	}

	private void addPKM(Id<Vehicle> vehicleId, Id<Link> linkId) {
		if (vehiclesToObserve.containsKey(vehicleId)) {
			Map<Id<Person>, Double> vehiclePassengers = this.vehiclesToObserve.get(vehicleId);
			for(Id<Person> personId : vehiclePassengers.keySet()) {
				double passengerKilometers = vehiclePassengers.get(personId);
				passengerKilometers += this.network.getLinks().get(linkId).getLength();
				vehiclePassengers.put(personId, passengerKilometers);
			}
		}
	}

	private void addVKM(String type, Id<Link> linkId) {
		double vehicleKilometer = this.vehicleKilometersDriven.getOrDefault(type,0.);
		vehicleKilometer += this.network.getLinks().get(linkId).getLength();
		this.vehicleKilometersDriven.put(type, vehicleKilometer);
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent personEntersVehicleEvent) {
		if (!targetFunctionEvaluator.isPersonToConsider(personEntersVehicleEvent.getPersonId())) {
			return;
		}
		Map<Id<Person>, Double> vehiclePassengers = this.vehiclesToObserve.getOrDefault(
				personEntersVehicleEvent.getVehicleId(), new HashMap<>());
		vehiclePassengers.put(personEntersVehicleEvent.getPersonId(), 0.);
		this.vehiclesToObserve.put(personEntersVehicleEvent.getVehicleId(), vehiclePassengers);
		// count the passenger
		String serviceType = getServiceType(personEntersVehicleEvent.getVehicleId());
		long numberOfPassengers = this.numberOfPassengers.getOrDefault(serviceType, 0L);
		numberOfPassengers++;
		this.numberOfPassengers.put(serviceType, numberOfPassengers);
	}

	@Override
	public void handleEvent(PersonLeavesVehicleEvent personLeavesVehicleEvent) {
		if (!targetFunctionEvaluator.isPersonToConsider(personLeavesVehicleEvent.getPersonId())) {
			return;
		}
		Id<Vehicle> vehicleId = personLeavesVehicleEvent.getVehicleId();
		Id<Person> personId = personLeavesVehicleEvent.getPersonId();
		double personKilometers = this.vehiclesToObserve.get(vehicleId).get(personId);
		String serviceType = getServiceType(vehicleId);
		double totalPassengerKilometers = this.passengerKilometersDriven.getOrDefault(serviceType, 0.);
		totalPassengerKilometers += personKilometers;
		this.passengerKilometersDriven.put(serviceType, totalPassengerKilometers);
		this.vehiclesToObserve.get(vehicleId).remove(personId);
		if (this.vehiclesToObserve.get(vehicleId).isEmpty()) {
			this.vehiclesToObserve.remove(vehicleId);
		}
	}

	private String getServiceType(Id<Vehicle> vehicleIdOrig) {
		String vehicleId = vehicleIdOrig.toString();
		if (vehicleId.contains("av")) { // its an av-vehicle
			return vehicleId.substring(vehicleId.indexOf("_")+1, vehicleId.lastIndexOf("_"));
		} else if (vehicleId.contains("freight")) { // its a truck
			return freight;
		} else if (!vehicleId.contains("_") || vehicleId.contains("cb")) { // its a car
			if (targetFunctionEvaluator.isPersonToConsider(Id.createPersonId(vehicleId))) {
				return car;
			} else {
				return "car_notConsider";
			}
		} else { // its a pt-vehicle
			if (vehicleId.contains("NFB") || vehicleId.contains("BUS")) {
				return ptBus;
			} else {
				return ptOther;
			}
		}
	}

	public void prepareForInSimResults(String outputFilePath, double countsScaleFactor) {

	}

	public void createInSimResults() {

	}

	public void closeFiles() {

	}
}
