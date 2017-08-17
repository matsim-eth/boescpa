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

package ch.ethz.matsim.boescpa.lib.tools.networkModification;

import ch.ethz.matsim.boescpa.lib.tools.NetworkUtils;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;

import java.util.HashSet;
import java.util.Set;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class AddNetworkMode {

	public static void main(final String[] args) {
		Network network = NetworkUtils.readNetwork(args[0]);
		final String newMode = args[1];

		for (Link link : network.getLinks().values()) {
			if (link.getAllowedModes().contains("car")) {
				Set<String> allowedModes = new HashSet<>();
				allowedModes.addAll(link.getAllowedModes());
				allowedModes.add(newMode);
				link.setAllowedModes(allowedModes);
			}
		}

		new NetworkWriter(network).write("network_incl_"+args[1]+".xml.gz");
	}

}
