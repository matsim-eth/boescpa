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
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static ch.ethz.matsim.boescpa.diss.simulations.runSetupCreation.NoAVSetupCreator.getNameString;
import static ch.ethz.matsim.boescpa.diss.simulations.runSetupCreation.OnlyAVSetupCreator.aRSConfigFileNames;
import static ch.ethz.matsim.boescpa.diss.simulations.runSetupCreation.OnlyAVSetupCreator.aTaxiConfigFileNames;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class CombinationSetupCreator {
	private final String inputAVConfigPath;
	private String outputPath;
	private String eulerCommands = "module load new" + "\n" + "module load java";

	public CombinationSetupCreator(String inputConfig, String outputPath) {
		this.inputAVConfigPath = inputConfig;
		this.outputPath = outputPath + File.separator;
	}

	public static void main(final String[] args) {
		final String inputConfigPath = args[0];
		final String outputPath = args[1];
		CombinationSetupCreator configCreator = new CombinationSetupCreator(inputConfigPath, outputPath);
		configCreator.createConfigs();
		configCreator.writeShell();
	}

	private void createConfigs() {
		for (double vot : new double[]{-14.43, 0.75*-14.43}) {
			for (double levelOfService : new double[]{3*60.0}) {
				for (String aTaxiConfig : aTaxiConfigFileNames) {
					List<Tuple<String, Config>> tempConfigs = createNonAVConfigs(vot, levelOfService, aTaxiConfig);
					for (Tuple<String, Config> tempConfig : tempConfigs) {
						createEulerCommand(tempConfig);
					}
				}
			}
		}
		for (double vot : new double[]{-14.43}) {
			for (double levelOfService : new double[]{3*60.0, 5*60.0}) {
				for (String aRSConfig : aRSConfigFileNames) {
					List<Tuple<String, Config>> tempConfigs = createNonAVConfigs(vot, levelOfService, aRSConfig);
					for (Tuple<String, Config> tempConfig : tempConfigs) {
						createEulerCommand(tempConfig);
					}
				}
			}
		}
	}

	private List<Tuple<String, Config>> createNonAVConfigs(double avVot, double avLevelOfService, String avConfig) {
		Map<String, PlanCalcScoreConfigGroup.ModeParams> modeParams =
				ConfigUtils.loadConfig(inputAVConfigPath).planCalcScore().getModes();
		List<Tuple<String, Config>> tempConfigs = new LinkedList<>();
		for (double aPTprice : new double[]{0.50, 0.00}) {
			for (double aMITprice : new double[]{1.00}) {
				for (String emptyRides : new String[]{"0.0", "0.5", "1.5"}) {
					for (String votMIT : new String[]{"car", "pt_plus"}) {
						switch (votMIT) {
							case "car":
								tempConfigs.addAll(createNewConfigs(aPTprice, aMITprice, emptyRides,
										modeParams.get("car").getMarginalUtilityOfTraveling(), votMIT,
										avVot, avLevelOfService, avConfig));
								break;
							case "pt_plus":
								tempConfigs.addAll(createNewConfigs(aPTprice, aMITprice, emptyRides,
										modeParams.get("pt").getMarginalUtilityOfTraveling()*0.75, votMIT,
										avVot, avLevelOfService, avConfig));
								break;
						}
					}
				}
			}
		}
		return tempConfigs;
	}

	private void createEulerCommand(Tuple<String, Config> tempConfig) {
		new ConfigWriter(tempConfig.getSecond()).write(
				outputPath + tempConfig.getFirst());
		eulerCommands += "\n" + "bsub -n 8 -W ";
		eulerCommands += "24:00 ";
		eulerCommands += "-R \"rusage[mem=2560]\" "
				+ "java -Xmx20g -server -cp ../../resources/boescpa-0.1.0/boescpa-0.1.0.jar ";
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

	private List<Tuple<String, Config>> createNewConfigs(double aPTprice, double aMITprice, String emptyRides,
														 double votMIT, String votMITName, double avVot,
														 double avLevelOfService, String avConfig) {
		List<Tuple<String, Config>> newConfigs = new LinkedList<>();
		Config config = ConfigUtils.loadConfig(inputAVConfigPath);
		// make basic policy implementations
		config.planCalcScore().getModes().get("pt").setMonetaryDistanceRate(
				config.planCalcScore().getModes().get("pt").getMonetaryDistanceRate()*aPTprice);
		config.planCalcScore().getModes().get("car").setMonetaryDistanceRate(
				config.planCalcScore().getModes().get("car").getMonetaryDistanceRate()*aMITprice);
		config.planCalcScore().getModes().get("car").setMarginalUtilityOfTraveling(votMIT);
		// add av stuff
		config.planCalcScore().getModes().get("av").setMarginalUtilityOfTraveling(avVot);
		config.getModules().get(GrowingFleetDispatcherConfig.NAME).getParams()
				.put("levelOfService", String.valueOf(avLevelOfService));
		// create AV-stuff
		AVConfigGroup avConfigGroup = new AVConfigGroup();
		avConfigGroup.setConfigPath("../../resources/configs/" + avConfig + ".xml");
		avConfigGroup.setParallelRouters(8L);
		config.addModule(avConfigGroup);
		// add empty rides
		if (!emptyRides.equals("0.0")) {
			String inbase = config.plans().getInputFile().substring(0,config.plans().getInputFile().lastIndexOf(".xml"));
			config.plans().setInputFile(inbase + "_" + emptyRides + "emptyTripsPerAgent.xml.gz");
			config.plans().setInputPersonAttributeFile(inbase + "_attributes_" + emptyRides + "emptyTripsPerAgent.xml.gz");
		}
		// other customizations for each run
		String runString = getNameString(aPTprice, aMITprice, votMITName, emptyRides) + "-"
				+ OnlyAVSetupCreator.getNameString(avVot, avLevelOfService, avConfig);
		config.controler().setRunId(runString);
		config.controler().setOutputDirectory("/cluster/work/ivt_vpl/pboesch/diss/combination/output_" + runString);
		config.qsim().setTimeStepSize(5);
		newConfigs.add(new Tuple<>("config_-_" + runString + ".xml", config));
		return newConfigs;
	}
}
