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
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.core.config.Config;
import org.matsim.core.mobsim.framework.events.MobsimAfterSimStepEvent;
import org.matsim.core.mobsim.framework.listeners.MobsimAfterSimStepListener;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;

import java.util.HashMap;
import java.util.Map;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class StaticAVSimEventListener implements PersonDepartureEventHandler, PersonArrivalEventHandler,
		MobsimAfterSimStepListener {

	private final Map<String, StaticAVSim> avSims;

	@Inject
	public StaticAVSimEventListener(TravelTimeCalculator travelTimeCalculator, Config config) {
		StaticAVConfig avConfig = (StaticAVConfig)config.getModules().get(StaticAVConfig.NAME);
		this.avSims = new HashMap<>();

		double boardingTime = avConfig.getBoardingTime();
		double unboardingTime = avConfig.getUnboardingTime();

		for (StaticAVConfig.AVOperatorConfig operatorConfig : avConfig.getOperatorConfigs()) {
			double levelOfService = operatorConfig.getLevelOfService();
			AVAssignment avAssignment = operatorConfig.getAVAssignment(travelTimeCalculator);
			StaticAVSim avSim = new StaticAVSim(travelTimeCalculator, avAssignment, levelOfService,
					boardingTime, unboardingTime);
			this.avSims.put(operatorConfig.getOperatorId(), avSim);
		}
	}

	@Override
	public void handleEvent(PersonDepartureEvent personDepartureEvent) {
		if (avSims.keySet().contains(personDepartureEvent.getLegMode())) {
			avSims.get(personDepartureEvent.getLegMode()).handleDeparture(personDepartureEvent);
		}
	}

	@Override
	public void handleEvent(PersonArrivalEvent personArrivalEvent) {
		if (avSims.keySet().contains(personArrivalEvent.getLegMode())) {
			avSims.get(personArrivalEvent.getLegMode()).handleArrival(personArrivalEvent);
		}
	}

	@Override
	public void notifyMobsimAfterSimStep(MobsimAfterSimStepEvent mobsimAfterSimStepEvent) {
		for (StaticAVSim avSim : avSims.values()) {
			avSim.freeBlockedVehicles(mobsimAfterSimStepEvent.getSimulationTime());
			avSim.handlePendingRequests(mobsimAfterSimStepEvent.getSimulationTime());
		}
	}

	// todo-boescpa: Writeout am richtigen Ort zu den korrekten Iterationen noch einbauen (v.a. bevor reset gerufen wird, denn dies würde alle Werte deleten...).

	@Override
	public void reset(int i) {
		for (StaticAVSim avSim : avSims.values()) {
			avSim.reset();
		}
	}
}
