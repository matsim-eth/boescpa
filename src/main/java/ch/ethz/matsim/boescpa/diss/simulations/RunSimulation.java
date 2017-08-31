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

package ch.ethz.matsim.boescpa.diss.simulations;

import ch.ethz.matsim.boescpa.diss.analysis.InSimAnalyzerHomeInSHPTargetFunction;
import ch.ethz.matsim.boescpa.diss.baseline.replanning.BlackListedTimeAllocationMutatorConfigGroup;
import ch.ethz.matsim.boescpa.diss.baseline.replanning.BlackListedTimeAllocationMutatorStrategyModule;
import ch.ethz.matsim.boescpa.diss.baseline.scoring.IVTBaselineScoringModule;
import ch.ethz.matsim.boescpa.diss.baseline.scoring.IndividualVOTConfig;
import com.google.inject.name.Names;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class RunSimulation {

	static public void main(String[] args) {
		String configFile = args[0];
		String pathToSHP = args[1];

		// Configuration
		Config config = ConfigUtils.loadConfig(configFile,
				new BlackListedTimeAllocationMutatorConfigGroup(),
				new IndividualVOTConfig());
		Scenario scenario = ScenarioUtils.loadScenario(config);

		// Controller setup
		Controler controler = new Controler(scenario);
		controler.addOverridingModule(new BlackListedTimeAllocationMutatorStrategyModule());
		controler.addOverridingModule(new IVTBaselineScoringModule());
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				bind(String.class)
						.annotatedWith(Names.named("pathToSHP"))
						.toInstance(pathToSHP);
				this.addControlerListenerBinding().to(InSimAnalyzerHomeInSHPTargetFunction.class);
			}
		});

		// Run
		controler.run();
	}

}
