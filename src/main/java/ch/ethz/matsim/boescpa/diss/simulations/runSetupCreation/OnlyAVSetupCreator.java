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

package ch.ethz.matsim.boescpa.diss.simulations.runSetupCreation;

import ch.ethz.matsim.av.framework.AVConfigGroup;
import ch.ethz.matsim.boescpa.diss.av.dynamicFleet.delayedDeployment.dispatcher.GrowingFleetDispatcherConfig;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class OnlyAVSetupCreator {
	private final String inputAVConfigPath;
	private String outputPath;
	private String eulerCommands = "module load new" + "\n" + "module load java";

	final static String[] aTaxiConfigFileNames = new String[]{
			"aTaxi-0.43-0.00",
			"aTaxi-0.50-0.00",
			"aTaxi-1.00-0.00",
			"aTaxi-0.43-1.50",
			"aTaxi-0.50-1.50",
			"aTaxi-1.00-1.50",
	};

	final static String[] aRSConfigFileNames = new String[]{
			"aRS-0.31-0.00",
			"aRS-0.50-0.00",
			"aRS-1.00-0.00",
			"aRS-0.31-1.50",
			"aRS-0.50-1.50",
			"aRS-1.00-1.50",
	};

	public OnlyAVSetupCreator(String inputAVConfigPath, String outputPath) {
		this.inputAVConfigPath = inputAVConfigPath;
		this.outputPath = outputPath + File.separator;
	}

	public static void main(final String[] args) {
		final String inputAVConfigPath = args[0];
		final String outputPath = args[1];
		OnlyAVSetupCreator configCreator = new OnlyAVSetupCreator(inputAVConfigPath, outputPath);
		configCreator.createConfigs();
		configCreator.writeShell();
	}

	private void createConfigs() {
		for (double vot : new double[]{-14.43, 0.75*-14.43}) {
			for (double levelOfService : new double[]{3*60.0}) {
				for (String aTaxiConfig : aTaxiConfigFileNames) {
					List<Tuple<String, Config>> tempConfigs = createNewConfigs(vot, levelOfService, aTaxiConfig);
					for (Tuple<String, Config> tempConfig : tempConfigs) {
						setEulerCommand(tempConfig);
					}
				}
			}
		}
		for (double vot : new double[]{-14.43}) {
			for (double levelOfService : new double[]{3*60.0, 5*60.0}) {
				for (String aRSConfig : aRSConfigFileNames) {
					List<Tuple<String, Config>> tempConfigs = createNewConfigs(vot, levelOfService, aRSConfig);
					for (Tuple<String, Config> tempConfig : tempConfigs) {
						setEulerCommand(tempConfig);
					}
				}
			}
		}
	}

	private void setEulerCommand(Tuple<String, Config> tempConfig) {
		new ConfigWriter(tempConfig.getSecond()).write(
				outputPath + tempConfig.getFirst());
		eulerCommands += "\n" + "bsub -n 10 -W ";
		eulerCommands += "24:00 ";
		eulerCommands += "-R \"rusage[mem=2560]\" "
				+ "java -Xmx25g -server -cp ../../resources/boescpa-0.1.0/boescpa-0.1.0.jar ";
		eulerCommands += "ch.ethz.matsim.boescpa.diss.simulations.RunSimulationAV ";
		eulerCommands += tempConfig.getFirst()
					+ " ../../resources/siedlungsraum_zug_shp/siedlungsraum_zug.shp";
	}

	private void writeShell() {
		BufferedWriter writer = IOUtils.getBufferedWriter(this.outputPath + "run.sh");
		try {
			writer.write(eulerCommands);
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private List<Tuple<String, Config>> createNewConfigs(double vot, double levelOfService, String avConfig) {
		List<Tuple<String, Config>> newConfigs = new LinkedList<>();
		Config config = ConfigUtils.loadConfig(inputAVConfigPath);
		// make basic policy implementations
		config.planCalcScore().getModes().get("av").setMarginalUtilityOfTraveling(vot);
		config.getModules().get(GrowingFleetDispatcherConfig.NAME).getParams()
				.put("levelOfService", String.valueOf(levelOfService));
		// create AV-stuff
		AVConfigGroup avConfigGroup = new AVConfigGroup();
		avConfigGroup.setConfigPath("../../resources/configs/" + avConfig + ".xml");
		avConfigGroup.setParallelRouters(8L);
		config.addModule(avConfigGroup);

		// other customizations for each run
		String runString = getNameString(vot, levelOfService, avConfig);
		config.controler().setRunId(runString);
		config.controler().setOutputDirectory("/cluster/scratch/pboesch/diss/onlyAV/output_" + runString);
		config.qsim().setTimeStepSize(5);
		newConfigs.add(new Tuple<>("config_-_" + runString + ".xml", config));
		return newConfigs;
	}

	public static String getNameString(double vot, double levelOfService, String avConfig) {
		return avConfig + vot + "-" + levelOfService;
	}
}
