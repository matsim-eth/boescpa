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

package ch.ethz.matsim.boescpa.diss.baseline.deprecated;

import ch.ethz.matsim.boescpa.diss.baseline.replanning.BlackListedTimeAllocationMutatorConfigGroup;
import ch.ethz.matsim.boescpa.diss.baseline.replanning.BlackListedTimeAllocationMutatorStrategyModule;
import ch.ethz.matsim.boescpa.diss.baseline.scoring.IVTBaselineScoringModule;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.locationchoice.DestinationChoiceConfigGroup;
import org.matsim.contrib.locationchoice.bestresponse.DestinationChoiceBestResponseContext;
import org.matsim.contrib.locationchoice.bestresponse.DestinationChoiceInitializer;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.MatsimServices;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.UncheckedIOException;

import java.io.File;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class RunBaselineLC {

	static public void main(String[] args) {
		String configFile = args[0];

		// Configuration
		Config config = ConfigUtils.loadConfig(configFile,
				new BlackListedTimeAllocationMutatorConfigGroup(),
				//new IVTCalibrationConfigGroup(),
				new DestinationChoiceConfigGroup()
		);

		// This is currently needed for location choice: initializing
		// the location choice writes K-values files to the output directory, which:
		// - fails if the directory does not exist
		// - makes the controler crash latter if the unsafe setOverwriteFiles( true )
		// is not called.
		// This ensures that we get safety with location choice working as expected,
		// before we sort this out and definitely kick out setOverwriteFiles.
		createEmptyDirectoryOrFailIfExists(config.controler().getOutputDirectory());

		Scenario scenario = ScenarioUtils.loadScenario(config);

		// Controller setup
		Controler controler = new Controler(scenario);

		controler.getConfig().controler().setOverwriteFileSetting(
				OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);

		initializeLocationChoice(controler);

		controler.addOverridingModule(new BlackListedTimeAllocationMutatorStrategyModule());
		controler.addOverridingModule(new IVTBaselineScoringModule());
		//controler.addOverridingModule(new IVTBaselineCalibrationModule());

		// Run
		controler.run();
	}

	private static void initializeLocationChoice(MatsimServices controler) {
		Scenario scenario = controler.getScenario();
		DestinationChoiceBestResponseContext lcContext =
				new DestinationChoiceBestResponseContext(scenario);
		lcContext.init();
		controler.addControlerListener(new DestinationChoiceInitializer(lcContext));
	}

	private static void createEmptyDirectoryOrFailIfExists(String directory) {
		File file = new File( directory +"/" );
		if ( file.exists() && file.list().length > 0 ) {
			throw new UncheckedIOException( "Directory "+directory+" exists and is not empty!" );
		}
		file.mkdirs();
	}

}
