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

package ch.ethz.matsim.boescpa.diss.av.staticDemand;

import com.google.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;

import java.util.*;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class StaticAVSim {

	private final TravelTimeCalculator travelTimeCalculator;
	private final AVAssignment avAssignment;

	private final double LEVEL_OF_SERVICE;
	private final double BOARDING_TIME;
	private final double UNBOARDING_TIME;

	private final List<PersonDepartureEvent> pendingRequests;
	private final Map<Id<Person>, AutonomousVehicle> vehiclesInUse;
	private final Map<AutonomousVehicle, Double> vehicleBlockedUntil;
	private final List<AutonomousVehicle> availableVehicles;
	private Stats stats;

	@Inject
	public StaticAVSim(TravelTimeCalculator travelTimeCalculator) {
		this.travelTimeCalculator = travelTimeCalculator;
		this.avAssignment = new AVAssignment(travelTimeCalculator);
		this.pendingRequests = new ArrayList<>();
		this.vehiclesInUse = new HashMap<>();
		this.vehicleBlockedUntil = new LinkedHashMap<>();
		this.availableVehicles = new LinkedList<>();
		this.reset();
	}

	public void reset() {
		this.pendingRequests.clear();
		this.vehiclesInUse.clear();
		this.availableVehicles.clear();
		this.stats = new Stats(this.vehiclesInUse, this.availableVehicles, this.vehicleBlockedUntil);
	}

	void handleDeparture(PersonDepartureEvent request) {
		pendingRequests.add(request);
		stats.incTotalDemand();
	}

	void freeBlockedVehicles(double simulationTime) {
		List<AutonomousVehicle> vehiclesToFree = new ArrayList<>();
		for (AutonomousVehicle vehicle : vehicleBlockedUntil.keySet()) {
			if (vehicleBlockedUntil.get(vehicle) <= simulationTime) {
				vehiclesToFree.add(vehicle);
			}
		}
		for (AutonomousVehicle vehicle : vehiclesToFree) {
			vehicleBlockedUntil.remove(vehicle);
			availableVehicles.add(vehicle);
		}
	}

	void handlePendingRequests(double simulationTime) {
		for (int i = pendingRequests.size() - 1; i > -1; i--) {
			PersonDepartureEvent request = pendingRequests.get(i);
			AutonomousVehicle assignedVehicle = avAssignment.assignVehicleToRequest(request,
					LEVEL_OF_SERVICE - (simulationTime - request.getTime()),
					availableVehicles);
			if (assignedVehicle != null) {
				// We have a vehicle and it's getting on the way.
				handleMetRequests(request, assignedVehicle, simulationTime);
			} else {
				// There is currently no suitable vehicle available.
				if (simulationTime - request.getTime() >= LEVEL_OF_SERVICE) {
					handleUnmetRequests(request, simulationTime);
				} // Else we leave the request unhandled and try again in a second.
			}
		}
	}

	private void handleMetRequests(PersonDepartureEvent request, AutonomousVehicle assignedVehicle,
								   double simulationTime) {
		// 1. Get the vehicle:
		availableVehicles.remove(assignedVehicle);
		vehiclesInUse.put(request.getPersonId(), assignedVehicle);
		// 2. Move vehicle to agent:
		double travelTime = travelTimeCalculator.getLinkToLinkTravelTime(
				assignedVehicle.getPosition(), request.getLinkId(), simulationTime);
		double waitingTimeForAssignment = simulationTime - request.getTime();
		double responseTime = waitingTimeForAssignment + travelTime;
		// 3. Agent boards vehicle:
		double waitingTimeForAgents = LEVEL_OF_SERVICE - responseTime > 0 ? LEVEL_OF_SERVICE - responseTime : 0;
		travelTime += waitingTimeForAgents;
		travelTime += BOARDING_TIME;
		// 4. Vehicle is on its way...
		assignedVehicle.setTravelTime(travelTime);
		assignedVehicle.setDepartureTime(simulationTime);
		pendingRequests.remove(request);
		// ***************************************************************************
		recordDepartureStats(simulationTime, assignedVehicle, waitingTimeForAssignment, responseTime,
				waitingTimeForAgents);
	}

	private void handleUnmetRequests(PersonDepartureEvent request, double simulationTime) {
		// 1. Create the vehicle at the place of the agent:
		AutonomousVehicle assignedVehicle = new AutonomousVehicle(request.getLinkId());
		vehiclesInUse.put(request.getPersonId(), assignedVehicle);
		double travelTime = 0;
		double waitingTimeForAssignment = 0;
		double responseTime = waitingTimeForAssignment + travelTime;
		// 3. Agent boards vehicle:
		double waitingTimeForAgents = LEVEL_OF_SERVICE / 2; // represents the expected average
		travelTime += waitingTimeForAgents;
		travelTime += BOARDING_TIME;
		// 4. Vehicle is on its way...
		assignedVehicle.setTravelTime(travelTime);
		assignedVehicle.setDepartureTime(simulationTime);
		pendingRequests.remove(request);
		// ***************************************************************************
		recordDepartureStats(simulationTime, assignedVehicle, waitingTimeForAssignment, responseTime,
				waitingTimeForAgents);
	}

	public void handleArrival(PersonArrivalEvent arrival) {
		// 1. Move vehicle:
		AutonomousVehicle vehicle = vehiclesInUse.remove(arrival.getPersonId());
		double travelTime = vehicle.getTravelTime();
		travelTime += arrival.getTime() + vehicle.getDepartureTime();
		vehicle.setPosition(arrival.getLinkId());
		// 2. Agents unboards vehicle and thus "frees" the vehicle:
		travelTime += UNBOARDING_TIME;
		//		While agent already arrived, he's still blocking the vehicle for the additional time it
		//		took the vehicle to serve this agent. In reality, the agent would arrive a few seconds to
		//		minutes (the time it took the vehicle to get to him) later, but we are accepting this
		//		approximation here.
		vehicleBlockedUntil.put(vehicle,
				arrival.getTime() + (travelTime - (arrival.getTime() - vehicle.getDepartureTime())));
		// ***************************************************************************
		recordArrivalStats(arrival, vehicle, travelTime);
	}

	// ******************************************************************************************
	// ******************************************************************************************

	private void recordDepartureStats(double simulationTime, AutonomousVehicle assignedVehicle,
									  double waitingTimeForAssignment, double responseTime,
									  double waitingTimeForAgents) {
		StatRequest statRequest = new StatRequest();
		statRequest.setAssignmentTime(waitingTimeForAssignment);
		statRequest.setResponseTime(responseTime);
		statRequest.setStartTime(simulationTime);
		assignedVehicle.setStatRequest(new StatRequest());
		assignedVehicle.incNumberOfServices();
		assignedVehicle.incAccessTime(responseTime - waitingTimeForAssignment);
		//assignedVehicle.incAccessDistance();
		assignedVehicle.incWaitingTime(waitingTimeForAgents);
		stats.incWaitingTime(responseTime > LEVEL_OF_SERVICE ? responseTime - LEVEL_OF_SERVICE : 0);
		stats.incWaitingTimeForAssignmentMetDemand(waitingTimeForAssignment);
		stats.incResponseTimeMetDemand(responseTime);
		if (responseTime < 60) {
			stats.incQuickMetDemand();
		} else {
			stats.incMetDemand();
		}
	}

	private void recordArrivalStats(PersonArrivalEvent arrival, AutonomousVehicle vehicle, double travelTime) {
		StatRequest statRequest = vehicle.getStatRequest();
		statRequest.setDuration(arrival.getTime()-vehicle.getDepartureTime());
		//statRequest.setDistance();
		stats.addRequest(statRequest);
		stats.incTravelTimeMetDemand(arrival.getTime() - vehicle.getDepartureTime());
		//stats.incTravelDistanceMetDemand();
		vehicle.incServiceTime(travelTime);
		//vehicle.incServiceDistance();
	}
}
