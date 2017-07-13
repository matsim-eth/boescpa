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
import ch.ethz.matsim.boescpa.diss.analysis.eventHandlers.targetFunctionUtils.VehicleKilometerCounter;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;

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
		implements LinkLeaveEventHandler {

	private final VehicleKilometerCounter vehicleKilometerCounter;

	public TargetFunctionEvaluator(String path2HomesSHP, Population population, Network network) {
		super(path2HomesSHP, population);
		this.vehicleKilometerCounter = new VehicleKilometerCounter(this, network);
		this.reset(0);
	}

	@Override
	public String createResults(SpatialCutter spatialEventCutter, int scaleFactor) {
		String vehicleKilometers = this.vehicleKilometerCounter.createResults(scaleFactor);
		String accessibilities = "Accessibilities" + NL + "TO BE DONE";
		return vehicleKilometers + NL + accessibilities;
	}

	@Override
	public void reset(int i) {
		vehicleKilometerCounter.reset(i);
	}

	@Override
	public void handleEvent(LinkLeaveEvent linkLeaveEvent) {
		vehicleKilometerCounter.handleEvent(linkLeaveEvent);
	}
}
