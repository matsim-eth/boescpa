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

package ch.ethz.matsim.boescpa.diss.trb18;

import ch.ethz.matsim.av.framework.AVConfigGroup;
import ch.ethz.matsim.av.framework.AVModule;
import ch.ethz.matsim.av.framework.AVQSimProvider;
import ch.ethz.matsim.boescpa.diss.analysis.InSimAnalyzerHomeInSHPTargetFunction;
import ch.ethz.matsim.boescpa.diss.baseline.replanning.BlackListedTimeAllocationMutatorConfigGroup;
import ch.ethz.matsim.boescpa.diss.baseline.replanning.BlackListedTimeAllocationMutatorStrategyModule;
import ch.ethz.matsim.boescpa.diss.baseline.scoring.IVTBaselineScoringModule;
import com.google.inject.name.Names;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.trafficmonitoring.VrpTravelTimeModules;
import org.matsim.contrib.dynagent.run.DynQSimModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;

import java.net.MalformedURLException;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class RunForTRB {

	public static void main(String[] args) throws MalformedURLException {
		String configFile = args[0];
		String pathToSHP = args[1];

		// Configuration
		DvrpConfigGroup dvrpConfigGroup = new DvrpConfigGroup();
		dvrpConfigGroup.setTravelTimeEstimationAlpha(0.05);
		Config config = ConfigUtils.loadConfig(configFile,
				new AVConfigGroup(), dvrpConfigGroup, // AV-modules
				new BlackListedTimeAllocationMutatorConfigGroup()); // IVT-Modules
		Scenario scenario = ScenarioUtils.loadScenario(config);

		// Controller setup
		Controler controler = new Controler(scenario);
		//	Add AV modules
		if (config.getModules().get("av").getParams().get("config") != null) {
			controler.addOverridingModule(VrpTravelTimeModules.createTravelTimeEstimatorModule());
			controler.addOverridingModule(new DynQSimModule<>(AVQSimProvider.class));
			controler.addOverridingModule(new AVModule());
		}
		//	Add IVT modules
		controler.addOverridingModule(new BlackListedTimeAllocationMutatorStrategyModule());
		controler.addOverridingModule(new IVTBaselineScoringModule());
		//	Add target function evaluation:
		// Add PT-Counts creator:
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
