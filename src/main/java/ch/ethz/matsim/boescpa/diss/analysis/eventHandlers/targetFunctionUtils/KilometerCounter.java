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
import org.matsim.core.utils.charts.XYLineChart;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.vehicles.Vehicle;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;

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

	private final String ptBus = "pt_bus", ptOther = "pt_other", car = "car",
			carNotConsider = "car_notConsider", freight = "freight";
	private int iteration;


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
		this.iteration = i;
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
					+ (long)(this.passengerKilometersDriven.get(serviceType)*scaleFactor) + DEL
					+ this.numberOfPassengers.get(serviceType)*scaleFactor + NL;
		}
		return passengerKilometers;
	}

	private String getVehicleKilometerString(int scaleFactor) {
		String vehicleKilometers = "Vehicle Kilometers:" + NL;
		for (String vehicleType : this.vehicleKilometersDriven.keySet()) {
			int adjustedScaleFactor = vehicleType.contains("pt") ? 1 : scaleFactor;
			vehicleKilometers += vehicleType + DEL
					+ (long)(this.vehicleKilometersDriven.get(vehicleType)*adjustedScaleFactor) + NL;
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
				passengerKilometers += this.network.getLinks().get(linkId).getLength()/1000; // converstion to km
				vehiclePassengers.put(personId, passengerKilometers);
			}
		}
	}

	private void addVKM(String type, Id<Link> linkId) {
		double vehicleKilometer = this.vehicleKilometersDriven.getOrDefault(type,0.);
		vehicleKilometer += this.network.getLinks().get(linkId).getLength()/1000; // conversion to km
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
				return carNotConsider;
			}
		} else { // its a pt-vehicle
			if (vehicleId.contains("NFB") || vehicleId.contains("BUS")) {
				return ptBus;
			} else {
				return ptOther;
			}
		}
	}

	// ****************************************************************************************
	// In Sim Outputs:

	private double countsScaleFactor;
	private BufferedWriter passengerKilometersWriter;
	private BufferedWriter vehicleKilometersWriter;
	private BufferedWriter passengerAnzWriter;
	private Map<String, Map<Integer, Double>> vehicleKmHistory;
	private Map<String, Map<Integer, Double>> passengerKmHistory;
	private Map<String, Map<Integer, Double>> passengerAnzHistory;
	private String pathVehicleKmPNG;
	private String pathPassengerKmPNG;
	private String pathPasssengerAnzPNG;
	private List<String> modesOutputet;

	public void prepareForInSimResults(String outputFilePath, double countsScaleFactor) {
		this.countsScaleFactor = countsScaleFactor;
		// get writers
		this.passengerKilometersWriter =
				IOUtils.getBufferedWriter(outputFilePath + "passenger_km.csv");
		this.vehicleKilometersWriter =
				IOUtils.getBufferedWriter(outputFilePath + "vehicle_km.csv");
		this.passengerAnzWriter =
				IOUtils.getBufferedWriter(outputFilePath + "passenger_anz.csv");
		// prepare history map
		this.vehicleKmHistory = new HashMap<>();
		this.passengerKmHistory = new HashMap<>();
		this.passengerAnzHistory = new HashMap<>();
		this.pathVehicleKmPNG = outputFilePath + "vehicle_km";
		this.pathPassengerKmPNG = outputFilePath + "passenger_km";
		this.pathPasssengerAnzPNG = outputFilePath + "passenger_anz";
	}

	public void createInSimResults() {
		// every iteration update history
		updateHistory();
		// write stats (from iteration 10 on...)
		//	Reason for it.10: Assumption that by this time every vehicle type is used at least once...
		if (this.iteration > 1) { //9) {
			try {
				// headers and setup in 10th iteration:
				if (this.iteration == 2) { //10) {
					this.modesOutputet = new ArrayList<>();
					String header = "it";
					for (String vehicleType : this.vehicleKmHistory.keySet()) {
						header += DEL + vehicleType;
						this.modesOutputet.add(vehicleType);
					}
					writeHistorySoFar(header, this.vehicleKilometersWriter, this.vehicleKmHistory);
					writeHistorySoFar(header, this.passengerKilometersWriter, this.passengerKmHistory);
					writeHistorySoFar(header, this.passengerAnzWriter, this.passengerAnzHistory);
				// every other iteration afterwards:
				} else {
					writeOutput(this.vehicleKilometersWriter, this.vehicleKmHistory);
					writeOutput(this.passengerKilometersWriter, this.passengerKmHistory);
					writeOutput(this.passengerAnzWriter, this.passengerAnzHistory);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		// plot graphs (from iteration 1 on...)
		if (this.iteration > 0) {
			plotGraphs();
		}
	}

	private void writeOutput(BufferedWriter writer, Map<String, Map<Integer, Double>> history)
			throws IOException{
		writer.write(Integer.toString(this.iteration));
		for (String mode : this.modesOutputet) {
			writer.write(history.keySet().contains(mode) ?
					DEL + history.get(mode).get(this.iteration) : DEL + Integer.toString(0));
		}
		writer.newLine();
		writer.flush();
	}

	private void writeHistorySoFar(String header, BufferedWriter writer,
								   Map<String, Map<Integer, Double>> history) throws IOException {
		writer.write(header);
		writer.newLine();
		for (int i = 0; i <= this.iteration; i++) {
			writer.write(Integer.toString(i));
			for (String mode : this.modesOutputet) {
				writer.write(history.keySet().contains(mode) ?
						DEL + history.get(mode).get(i) : DEL + Integer.toString(0));
			}
			writer.newLine();
		}
		writer.flush();
	}

	private void plotGraphs() {
		plotGraph("Vehicle Kilometers", this.vehicleKmHistory, this.pathVehicleKmPNG);
		plotGraph("Passenger Kilometers", this.passengerKmHistory, this.pathPassengerKmPNG);
		plotGraph("Passenger Number", this.passengerAnzHistory, this.pathPasssengerAnzPNG);
	}

	private void plotGraph(String title, Map<String, Map<Integer, Double>> history, String pathPNG) {
		XYLineChart chart = new XYLineChart(title, "iteration", "mode");
		for (String mode : history.keySet()) {
			Map<Integer, Double> modeHistory = history.get(mode);
			chart.addSeries(mode, modeHistory);
		}
		chart.saveAsPng(pathPNG + ".png", 800, 600);
	}

	private void updateHistory() {
		// vehicle kilometers
		for (String vehicleType : this.vehicleKilometersDriven.keySet()) {
			// if never had this vehicle type before:
			if (!this.vehicleKmHistory.keySet().contains(vehicleType)) {
				this.vehicleKmHistory.put(vehicleType, getPreviousHistory());
			}
		}
		for (String vehicleType : this.vehicleKmHistory.keySet()) {
			double adjustedScaleFactor = vehicleType.contains("pt") ? 1. : this.countsScaleFactor;
			this.vehicleKmHistory.get(vehicleType).put(this.iteration,
					Math.floor(this.vehicleKilometersDriven.getOrDefault(vehicleType, 0.)*adjustedScaleFactor));
		}
		// passenger kilometers and number of passengers
		for (String vehicleType : this.passengerKilometersDriven.keySet()) {
			// if never had this vehicle type before:
			if (!this.passengerKmHistory.keySet().contains(vehicleType)) {
				this.passengerKmHistory.put(vehicleType, getPreviousHistory());
				this.passengerAnzHistory.put(vehicleType, getPreviousHistory());
			}
		}
		for (String vehicleType : this.passengerKmHistory.keySet()) {
			this.passengerKmHistory.get(vehicleType).put(this.iteration,
					Math.floor(this.passengerKilometersDriven.getOrDefault(vehicleType, 0.)*this.countsScaleFactor));
			this.passengerAnzHistory.get(vehicleType).put(this.iteration,
					this.numberOfPassengers.getOrDefault(vehicleType, 0L)*this.countsScaleFactor);
		}
	}

	private Map<Integer, Double> getPreviousHistory() {
		Map<Integer, Double> previousHistory = new HashMap<>();
		for (int i = 0; i < this.iteration; i++) {
			previousHistory.put(i, 0.);
		}
		return previousHistory;
	}

	public void closeFiles() {
		try {
			passengerKilometersWriter.close();
			vehicleKilometersWriter.close();
			passengerAnzWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
