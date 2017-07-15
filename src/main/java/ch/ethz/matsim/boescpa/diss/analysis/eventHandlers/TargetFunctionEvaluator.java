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

package ch.ethz.matsim.boescpa.diss.analysis.eventHandlers;

import ch.ethz.matsim.boescpa.analysis.spatialCutters.SpatialCutter;
import ch.ethz.matsim.boescpa.diss.analysis.eventHandlers.targetFunctionUtils.AccessibilitiesCalculator;
import ch.ethz.matsim.boescpa.diss.analysis.eventHandlers.targetFunctionUtils.VehicleKilometerCounter;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.events.handler.*;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.facilities.ActivityFacilities;

import java.util.Map;
import java.util.TreeMap;

import static ch.ethz.matsim.boescpa.analysis.scenarioAnalyzer.ScenarioAnalyzer.DEL;
import static ch.ethz.matsim.boescpa.analysis.scenarioAnalyzer.ScenarioAnalyzer.NL;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class TargetFunctionEvaluator extends ScenarioAnalyzerEventHandlerHomeInSHP
		implements LinkLeaveEventHandler, PersonDepartureEventHandler, PersonArrivalEventHandler {

	private final VehicleKilometerCounter vehicleKilometerCounter;
	private final AccessibilitiesCalculator accessibilitiesCalculator;

	public TargetFunctionEvaluator(String path2HomesSHP, Population population, Network network,
								   ActivityFacilities facilities) {
		super(path2HomesSHP, population);
		this.vehicleKilometerCounter = new VehicleKilometerCounter(this, network);
		this.accessibilitiesCalculator = new AccessibilitiesCalculator(this, network, facilities);
		this.reset(0);
	}

	@Override
	public String createResults(SpatialCutter spatialEventCutter, int scaleFactor) {
		String vehicleKilometers = this.vehicleKilometerCounter.createResults(scaleFactor);
		String accessibilities = this.accessibilitiesCalculator.createResults(scaleFactor);
		return vehicleKilometers + NL + accessibilities;
	}

	@Override
	public void reset(int i) {
		vehicleKilometerCounter.reset(i);
		accessibilitiesCalculator.reset(i);
	}

	@Override
	public void handleEvent(LinkLeaveEvent linkLeaveEvent) {
		vehicleKilometerCounter.handleEvent(linkLeaveEvent);
	}

	@Override
	public void handleEvent(PersonArrivalEvent personArrivalEvent) {
		accessibilitiesCalculator.handleEvent(personArrivalEvent);
	}

	@Override
	public void handleEvent(PersonDepartureEvent personDepartureEvent) {
		accessibilitiesCalculator.handleEvent(personDepartureEvent);
	}
}
