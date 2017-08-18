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

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Route;

import java.util.*;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class StaticAVSim {
	private final AVRouter router;
	private final AVAssignment avAssignment;

	private final double levelOfService;
	private final double boardingTime;
	private final double unboardingTime;
	private final double waitingTimeUnmet;

	private final List<PersonDepartureEvent> pendingRequests;
	private final List<PersonArrivalEvent> pendingArrivals;
	private final Map<Id<Person>, AutonomousVehicle> vehiclesInUse;
	private final Map<AutonomousVehicle, Double> vehicleBlockedUntil;
	private final List<AutonomousVehicle> availableVehicles;

	private Stats stats;

	public StaticAVSim(AVRouter router, AVAssignment avAssignment,
					   double levelOfService, double boardingTime, double unboardingTime,
					   double waitingTimeUnmet) {
		this.router = router;
		this.avAssignment = avAssignment;

		this.levelOfService = levelOfService;
		this.boardingTime = boardingTime;
		this.unboardingTime = unboardingTime;
		this.waitingTimeUnmet = waitingTimeUnmet;

		this.pendingRequests = new ArrayList<>();
		this.pendingArrivals = new ArrayList<>();
		this.vehiclesInUse = new HashMap<>();
		this.vehicleBlockedUntil = new LinkedHashMap<>();
		this.availableVehicles = new LinkedList<>();
		this.reset();
	}

	public void reset() {
		this.pendingRequests.clear();
		this.pendingArrivals.clear();
		this.vehiclesInUse.clear();
		this.vehicleBlockedUntil.clear();
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
					levelOfService - (simulationTime - request.getTime()),
					availableVehicles);
			if (assignedVehicle != null) {
				// We have a vehicle and it's getting on the way.
				handleMetRequests(request, assignedVehicle, simulationTime);
			} else {
				// There is currently no suitable vehicle available.
				if (simulationTime - request.getTime() >= levelOfService) {
					handleUnmetRequests(request);
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
		Route accessRoute = this.router.getRoute(assignedVehicle.getPosition(), request.getLinkId(),
				request.getTime());
		double accessDistance = accessRoute.getDistance();
		double waitingTimeForAssignment = simulationTime - request.getTime();
		double responseTime = waitingTimeForAssignment + accessRoute.getTravelTime();
		// 3. Agent boards vehicle:
		double waitingTimeForAgents = levelOfService - responseTime > 0 ?
				levelOfService - responseTime : 0;
		assignedVehicle.setTravelTime(boardingTime);
		// 4. Vehicle is on its way...
		assignedVehicle.setDepartureTime(request.getTime());
		pendingRequests.remove(request);
		// All this took time...:
		assignedVehicle.setBlockTime(responseTime + waitingTimeForAgents + boardingTime);
		// ***************************************************************************
		recordDepartureStats(request.getTime(), assignedVehicle, waitingTimeForAssignment, responseTime,
				waitingTimeForAgents, accessDistance);
	}

	private void handleUnmetRequests(PersonDepartureEvent request) {
		// 1. Create the vehicle at the place of the agent:
		AutonomousVehicle assignedVehicle = new AutonomousVehicle(request.getLinkId());
		vehiclesInUse.put(request.getPersonId(), assignedVehicle);
		double accessDistance = 0;
		double waitingTimeForAssignment = 0;
		double responseTime = 0;
		// 2. Agent boards vehicle:
		double waitingTimeForAgents = this.waitingTimeUnmet;
		assignedVehicle.setTravelTime(boardingTime);
		// 3. Vehicle is on its way...
		assignedVehicle.setDepartureTime(request.getTime());
		pendingRequests.remove(request);
		// All this took time...:
		assignedVehicle.setBlockTime(responseTime + waitingTimeForAgents + boardingTime);
		// ***************************************************************************
		recordDepartureStats(request.getTime(), assignedVehicle, waitingTimeForAssignment, responseTime,
				waitingTimeForAgents, accessDistance);
	}

	void handleArrival(PersonArrivalEvent arrival) {
		pendingArrivals.add(arrival);
	}

	void handlePendingArrivals() {
		List<PersonArrivalEvent> handledArrivals = new ArrayList<>();
		for (PersonArrivalEvent arrival : pendingArrivals) {
			if (vehiclesInUse.containsKey(arrival.getPersonId())) {
				// 1. Move vehicle:
				AutonomousVehicle vehicle = vehiclesInUse.remove(arrival.getPersonId());
				double travelTime = vehicle.getTravelTime();
				travelTime += arrival.getTime() + vehicle.getDepartureTime();
				vehicle.setPosition(arrival.getLinkId());
				// 2. Agents unboards vehicle and thus "frees" the vehicle:
				travelTime += unboardingTime;
				//		While agent already arrived, he's still blocking the vehicle for the additional
				// 		time it took the vehicle to serve this agent. In reality, the agent would arrive
				// 		a few seconds to minutes (the time it took the vehicle to get to him) later, but
				// 		we are accepting this approximation here.
				double totalBlockTime = vehicle.getBlockTime() // time until departure vehicle
						+ (arrival.getTime() - vehicle.getDepartureTime()) // travel time with agent
						+ unboardingTime; // unboarding agent
				vehicleBlockedUntil.put(vehicle, arrival.getTime() + totalBlockTime);
				// 3. Mark arrival as handled:
				handledArrivals.add(arrival);
				// ***************************************************************************
				recordArrivalStats(arrival.getTime(), vehicle, travelTime);
			}
		}
		pendingArrivals.removeAll(handledArrivals);
	}

	void finishAllTrips() {
		for (int i = pendingRequests.size() - 1; i > -1; i--) {
			PersonDepartureEvent request = pendingRequests.get(i);
			handleUnmetRequests(request);
		}
		handlePendingArrivals();
	}

	// ******************************************************************************************
	// ******************************************************************************************

	private void recordDepartureStats(double timeOfRequest, AutonomousVehicle assignedVehicle,
									  double waitingTimeForAssignment, double responseTime,
									  double waitingTimeForAgents, double accessDistance) {
		StatRequest statRequest = new StatRequest();
		statRequest.setAssignmentTime(waitingTimeForAssignment);
		statRequest.setResponseTime(responseTime);
		statRequest.setStartTime(timeOfRequest);
		assignedVehicle.setStatRequest(new StatRequest());
		assignedVehicle.incNumberOfServices();
		assignedVehicle.incAccessTime(responseTime - waitingTimeForAssignment);
		assignedVehicle.incAccessDistance(accessDistance);
		assignedVehicle.incWaitingTime(waitingTimeForAgents);
		stats.incWaitingTime(responseTime > levelOfService ? responseTime - levelOfService : 0);
		stats.incWaitingTimeForAssignment(waitingTimeForAssignment);
		stats.incResponseTime(responseTime);
		if (responseTime < 60) {
			stats.incQuickMetDemand();
		}
	}

	private void recordArrivalStats(double timeOfArrival, AutonomousVehicle vehicle, double travelTime) {
		StatRequest statRequest = vehicle.getStatRequest();
		statRequest.setDuration(travelTime);
		//statRequest.setDistance();
		stats.addRequest(statRequest);
		stats.incTravelTime(timeOfArrival - vehicle.getDepartureTime());
		//stats.incTravelDistance();
		vehicle.incServiceTime(travelTime);
		//vehicle.incServiceDistance();
	}

	public void writeResults(String iterationFilename) {
		stats.printResults(iterationFilename);
	}
}
