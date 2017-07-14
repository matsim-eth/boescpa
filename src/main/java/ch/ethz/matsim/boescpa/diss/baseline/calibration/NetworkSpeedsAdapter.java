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

package ch.ethz.matsim.boescpa.diss.baseline.calibration;

import ch.ethz.matsim.boescpa.lib.tools.NetworkUtils;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class NetworkSpeedsAdapter {

	public static void main(final String[] args) {
		Network network = NetworkUtils.readNetwork(args[0]);
		double speedReduction = Double.parseDouble(args[1])/100; // reduction in percentage

		for (Link link : network.getLinks().values()) {
			if (link.getAllowedModes().contains("car"))
				link.setFreespeed(link.getFreespeed()*(1 - speedReduction));
		}

		new NetworkWriter(network).write("network_"+args[1]+"PrctReducedSpeed.xml.gz");
	}

}
