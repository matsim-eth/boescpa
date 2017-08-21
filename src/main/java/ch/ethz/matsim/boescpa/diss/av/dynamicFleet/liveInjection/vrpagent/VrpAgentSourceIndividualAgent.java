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

package ch.ethz.matsim.boescpa.diss.av.dynamicFleet.liveInjection.vrpagent;

import ch.ethz.matsim.av.data.AVVehicle;
import ch.ethz.matsim.av.schedule.AVOptimizer;
import ch.ethz.matsim.av.vrpagent.AVActionCreator;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.dvrp.data.Vehicle;
import org.matsim.contrib.dvrp.vrpagent.VrpAgentLogic;
import org.matsim.contrib.dynagent.DynAgent;
import org.matsim.core.mobsim.framework.AgentSource;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.mobsim.qsim.qnetsimengine.QVehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.VehiclesFactory;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class VrpAgentSourceIndividualAgent implements AgentSource {
	private final AVActionCreator nextActionCreator;
	private final AVOptimizer optimizer;
	private final QSim qSim;
	private final VehiclesFactory vehicleFactory;
	private VehicleType vehicleType;

	private static VrpAgentSourceIndividualAgent myInstance = null;

	private VrpAgentSourceIndividualAgent(AVActionCreator nextActionCreator, AVOptimizer optimizer,
										  QSim qSim, VehicleType vehicleType) {
		this.nextActionCreator = nextActionCreator;
		this.optimizer = optimizer;
		this.qSim = qSim;
		this.vehicleFactory = VehicleUtils.getFactory();
		this.vehicleType = vehicleType;
	}

	public static VrpAgentSourceIndividualAgent createInstance(AVActionCreator nextActionCreator, AVOptimizer optimizer, QSim qSim, VehicleType vehicleType) {
		myInstance = new VrpAgentSourceIndividualAgent(nextActionCreator, optimizer, qSim, vehicleType);
		return myInstance;
	}

	public static VrpAgentSourceIndividualAgent getInstance() {
		return myInstance;
	}

	public void insertIndividualAgentIntoMobsim(AVVehicle avVehicle) {
		Id<Vehicle> id = avVehicle.getId();
		Id<Link> startLinkId = avVehicle.getStartLink().getId();

		VrpAgentLogic vrpAgentLogic = new VrpAgentLogic(optimizer, nextActionCreator, avVehicle);
		DynAgent vrpAgent = new DynAgent(Id.createPersonId(id), startLinkId, qSim.getEventsManager(),
				vrpAgentLogic);
		QVehicle mobsimVehicle = new QVehicle(
				vehicleFactory.createVehicle(Id.create(id, org.matsim.vehicles.Vehicle.class), vehicleType));
		vrpAgent.setVehicle(mobsimVehicle);
		mobsimVehicle.setDriver(vrpAgent);

		qSim.addParkedVehicle(mobsimVehicle, startLinkId);
		qSim.insertAgentIntoMobsim(vrpAgent);
	}

	@Override
	public void insertAgentsIntoMobsim() {

	}
}
