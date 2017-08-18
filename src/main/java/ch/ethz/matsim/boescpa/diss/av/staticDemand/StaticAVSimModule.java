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
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.name.Names;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.OutputDirectoryHierarchy;

import javax.inject.Named;
import java.util.HashSet;
import java.util.Set;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class StaticAVSimModule extends AbstractModule {
	private static final String instanceName = "staticAVSimListenerInstance";
	private static StaticAVSimEventListener staticAVSimEventListener;

	@Override
	public void install() {
		this.addEventHandlerBinding().to(Key.get(StaticAVSimEventListener.class,
				Names.named(instanceName)));
		this.addMobsimListenerBinding().to(Key.get(StaticAVSimEventListener.class,
				Names.named(instanceName)));
		this.addControlerListenerBinding().to(Key.get(StaticAVSimEventListener.class,
				Names.named(instanceName)));
		this.bind(AVRouter.class);
	}

	@Inject
	@Provides
	@Named(instanceName)
	public static StaticAVSimEventListener provideAVSim(Config config, AVRouter avRouter,
														OutputDirectoryHierarchy controlerIO) {
		if (staticAVSimEventListener == null) {
			staticAVSimEventListener = new StaticAVSimEventListener(config, controlerIO, avRouter);
		}
		return staticAVSimEventListener;
	}

	public static void addAVNetworkModes(Network network, StaticAVConfig staticAVConfig) {
		Set<String> modes = new HashSet<>();
		for (StaticAVConfig.AVOperatorConfig operatorConfig : staticAVConfig.getOperatorConfigs()){
			modes.add(operatorConfig.getOperatorId());
		}
		for (Link link : network.getLinks().values()) {
			if (link.getAllowedModes().contains("car")) {
				Set<String> allowedModes = new HashSet<>();
				allowedModes.addAll(link.getAllowedModes());
				allowedModes.addAll(modes);
				link.setAllowedModes(allowedModes);
			}
		}
	}
}
