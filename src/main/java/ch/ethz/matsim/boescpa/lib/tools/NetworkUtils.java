/*
 * *********************************************************************** *
 * project: org.matsim.*                                                   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2014 by the members listed in the COPYING,        *
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

package ch.ethz.matsim.boescpa.lib.tools;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;

/**
 * Provides different useful static methods around networks...
 *
 * @author boescpa
 */
public class NetworkUtils {

	/**
	 * Directly loads and provides a network given a path to a network file.
	 *
	 * @param path2Network
	 * @return Loaded network
	 */
	public static Network readNetwork(String path2Network) {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new MatsimNetworkReader(scenario.getNetwork()).readFile(path2Network);
		return scenario.getNetwork();
	}

	public static Network getModeFilteredNetwork(Network network, String mode) {
		Network onlyModeNetwork = org.matsim.core.network.NetworkUtils.createNetwork();
		for (Link link : network.getLinks().values()) {
			if (link.getAllowedModes().contains(mode)) {
				addLink(onlyModeNetwork, link);
			}
		}
		return onlyModeNetwork;
	}

	private static void addLink(Network network, Link link) {
		if (!network.getNodes().containsKey(link.getFromNode().getId())) {
			Node node = network.getFactory().createNode(link.getFromNode().getId(), link.getFromNode().getCoord());
			network.addNode(node);
		}
		if (!network.getNodes().containsKey(link.getToNode().getId())) {
			Node node = network.getFactory().createNode(link.getToNode().getId(), link.getToNode().getCoord());
			network.addNode(node);
		}
		network.addLink(link);
		link.setFromNode(network.getNodes().get(link.getFromNode().getId()));
		link.setToNode(network.getNodes().get(link.getToNode().getId()));
	}
}
