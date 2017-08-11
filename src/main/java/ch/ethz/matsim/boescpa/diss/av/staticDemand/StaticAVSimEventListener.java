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
import org.matsim.core.mobsim.framework.events.MobsimAfterSimStepEvent;
import org.matsim.core.mobsim.framework.listeners.MobsimAfterSimStepListener;

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
	public StaticAVSimEventListener() {

	}

	@Override
	public void handleEvent(PersonDepartureEvent personDepartureEvent) {
		if (personDepartureEvent.getLegMode().contains("av")) {
			avSims.get(personDepartureEvent.getLegMode()).handleDeparture(personDepartureEvent);
		}
	}

	@Override
	public void handleEvent(PersonArrivalEvent personArrivalEvent) {
		if (personArrivalEvent.getLegMode().contains("av")) {
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

	@Override
	public void reset(int i) {
		for (StaticAVSim avSim : avSims.values()) {
			avSim.reset();
		}
	}
}
