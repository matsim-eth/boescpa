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

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.core.utils.geometry.CoordUtils;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class AVAssignment {
	// todo-boescpa: Move these to config...
	private final static double DETOUR_FACTOR = 1.44;
	private final static double TELEPORT_SPEED = 13.5;


	private Network network;

	void setNetwork(Network network) {
		this.network = network;
	}

	AutonomousVehicle findClosestVehicle(QuadTree<AutonomousVehicle> availableVehiclesTree, Id<Link> linkId, double remainingTime) {
		Coord coord = network.getLinks().get(linkId).getCoord();
		AutonomousVehicle closestVehicle = availableVehiclesTree.size() > 0 ?
				availableVehiclesTree.getClosest(coord.getX(), coord.getY()) : null;
		if (closestVehicle != null) {
			double travelDistance = CoordUtils.calcEuclideanDistance(
					coord, network.getLinks().get(closestVehicle.getPosition()).getCoord());
			double travelTimeVehicle = travelDistance * DETOUR_FACTOR / TELEPORT_SPEED;
			if (travelTimeVehicle <= remainingTime) {
				return closestVehicle;
			}
		}
		return closestVehicle;
	}
}
