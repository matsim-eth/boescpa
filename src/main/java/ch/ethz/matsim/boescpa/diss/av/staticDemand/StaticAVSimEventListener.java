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

import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.core.config.Config;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.mobsim.framework.events.MobsimAfterSimStepEvent;
import org.matsim.core.mobsim.framework.listeners.MobsimAfterSimStepListener;

import java.util.HashMap;
import java.util.Map;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class StaticAVSimEventListener implements PersonDepartureEventHandler, PersonArrivalEventHandler,
		MobsimAfterSimStepListener, IterationEndsListener {

	private final Map<String, StaticAVSim> avSims;
	private final OutputDirectoryHierarchy controlerIO;
	private final Config config;

	StaticAVSimEventListener(Config config, OutputDirectoryHierarchy controlerIO, AVRouter router) {
		StaticAVConfig avConfig = (StaticAVConfig)config.getModules().get(StaticAVConfig.NAME);
		this.avSims = new HashMap<>();
		this.controlerIO = controlerIO;
		this.config = config;

		double boardingTime = avConfig.getBoardingTime();
		double unboardingTime = avConfig.getUnboardingTime();

		for (StaticAVConfig.AVOperatorConfig operatorConfig : avConfig.getOperatorConfigs()) {
			double levelOfService = operatorConfig.getLevelOfService();
			double waitingTimeUnmet = operatorConfig.getWaitingTimeUnmet();
			AVAssignment avAssignment = operatorConfig.getAVAssignment();
			avAssignment.setTravelTimeCalculator(router);
			StaticAVSim avSim = new StaticAVSim(router, avAssignment, levelOfService,
					boardingTime, unboardingTime, waitingTimeUnmet);
			this.avSims.put(operatorConfig.getOperatorId(), avSim);
		}
	}

	@Override
	public void handleEvent(PersonDepartureEvent personDepartureEvent) {
		if (this.avSims.keySet().contains(personDepartureEvent.getLegMode())) {
			this.avSims.get(personDepartureEvent.getLegMode()).handleDeparture(personDepartureEvent);
		}
	}

	@Override
	public void handleEvent(PersonArrivalEvent personArrivalEvent) {
		if (this.avSims.keySet().contains(personArrivalEvent.getLegMode())) {
			this.avSims.get(personArrivalEvent.getLegMode()).handleArrival(personArrivalEvent);
		}
	}

	@Override
	public void notifyMobsimAfterSimStep(MobsimAfterSimStepEvent mobsimAfterSimStepEvent) {
		for (StaticAVSim avSim : this.avSims.values()) {
			avSim.handlePendingArrivals();
			avSim.freeBlockedVehicles(mobsimAfterSimStepEvent.getSimulationTime());
			avSim.handlePendingRequests(mobsimAfterSimStepEvent.getSimulationTime());
		}
	}

	@Override
	public void notifyIterationEnds(IterationEndsEvent iterationEndsEvent) {
		for (StaticAVSim avSim : this.avSims.values()) {
			avSim.finishAllTrips();
		}
		if (iterationEndsEvent.getIteration() % this.config.controler().getWriteSnapshotsInterval() == 0) {
			for (String avSimKey : this.avSims.keySet()) {
				this.avSims.get(avSimKey).writeResults(this.controlerIO.getIterationFilename(
						iterationEndsEvent.getIteration(), avSimKey + ".txt"));
			}
		}
	}

	@Override
	public void reset(int i) {
		for (StaticAVSim avSim : this.avSims.values()) {
			avSim.reset();
		}
	}
}
