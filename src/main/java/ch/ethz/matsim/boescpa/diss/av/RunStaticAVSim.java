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

package ch.ethz.matsim.boescpa.diss.av;

import ch.ethz.matsim.boescpa.diss.av.staticDemand.StaticAVConfig;
import ch.ethz.matsim.boescpa.diss.av.staticDemand.StaticAVSimModule;
import ch.ethz.matsim.boescpa.diss.baseline.replanning.BlackListedTimeAllocationMutatorConfigGroup;
import ch.ethz.matsim.boescpa.diss.baseline.replanning.BlackListedTimeAllocationMutatorStrategyModule;
import ch.ethz.matsim.boescpa.diss.baseline.scoring.IVTBaselineScoringModule;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class RunStaticAVSim {

	public static void main(final String[] args) {
		String configFile = args[0];

		// Configuration
		Config config = ConfigUtils.loadConfig(configFile,
				new StaticAVConfig(), // AV-modules
				new BlackListedTimeAllocationMutatorConfigGroup()); // IVT-Modules
		Scenario scenario = ScenarioUtils.loadScenario(config);

		// Controller setup
		Controler controler = new Controler(scenario);
		//	Add AV module
		controler.addOverridingModule(new StaticAVSimModule());
		StaticAVSimModule.addAVNetworkModes(scenario.getNetwork(),
				(StaticAVConfig)config.getModules().get("staticAV"));
		//	Add IVT modules
		controler.addOverridingModule(new BlackListedTimeAllocationMutatorStrategyModule());
		controler.addOverridingModule(new IVTBaselineScoringModule());

		// Run
		controler.run();
	}

}
