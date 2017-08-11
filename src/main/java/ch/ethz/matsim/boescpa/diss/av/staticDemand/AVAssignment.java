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

import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;

import java.util.List;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
class AVAssignment {

	private final TravelTimeCalculator travelTimeCalculator;

	AVAssignment(TravelTimeCalculator travelTimeCalculator) {
		this.travelTimeCalculator = travelTimeCalculator;
	}

	AutonomousVehicle assignVehicleToRequest(PersonDepartureEvent request, double remainingTime,
											 List<AutonomousVehicle> availableVehicles) {
		AutonomousVehicle closestVehicle = getAbsoluteClosest(request, availableVehicles);
		if (closestVehicle != null) {
			double travelTimeVehicle = travelTimeCalculator.getLinkToLinkTravelTime(closestVehicle.getPosition(),
					request.getLinkId(), request.getTime());
			if (travelTimeVehicle <= remainingTime) {
				return closestVehicle;
			}
		}
		return closestVehicle;
	}

	private AutonomousVehicle getAbsoluteClosest(PersonDepartureEvent request,
												 List<AutonomousVehicle> availableAVs) {
		AutonomousVehicle closestVehicle = null;
		double minTravelTime = Double.MAX_VALUE;
		for (AutonomousVehicle currentVehicle : availableAVs) {
			double travelTime = travelTimeCalculator.getLinkToLinkTravelTime(currentVehicle.getPosition(),
					request.getLinkId(), request.getTime());
			if (travelTime < minTravelTime) {
				minTravelTime = travelTime;
				closestVehicle = currentVehicle;
			}
		}
		return closestVehicle;
	}
}
