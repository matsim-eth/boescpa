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
public class NoAVSetupCreator {
	private final String inputConfigPath;
	private String outputPath;
	private String eulerCommands = "module load new" + "\n" + "module load java";

	public NoAVSetupCreator(String inputConfig, String outputPath) {
		this.inputConfigPath = inputConfig;
		this.outputPath = outputPath + File.separator;
	}

	public static void main(final String[] args) {
		final String inputConfigPath = args[0];
		final String outputPath = args[1];
		NoAVSetupCreator configCreator = new NoAVSetupCreator(inputConfigPath, outputPath);
		configCreator.createConfigs();
		configCreator.writeShell();
	}

	private void createConfigs() {
		Config inputConfig = ConfigUtils.loadConfig(inputConfigPath);
		for (double aPTprice : new double[]{1.00, 0.75, 0.50, 0.00}) {
			for (double aMITprice : new double[]{1.00, 1.50}) {
				for (String emptyRides : new String[]{"0.0", "0.5", "1.0", "1.5"}) {
					for (String votMIT : new String[]{"car", "pt", "pt_plus"}) {
						List<Tuple<String, Config>> tempConfigs = null;
						switch (votMIT) {
							case "car":
								tempConfigs = createNewConfigs(aPTprice, aMITprice, emptyRides,
										inputConfig.planCalcScore().getModes()
												.get("car").getMarginalUtilityOfTraveling(), votMIT);
								break;
							case "pt":
								tempConfigs = createNewConfigs(aPTprice, aMITprice, emptyRides,
										inputConfig.planCalcScore().getModes()
												.get("pt").getMarginalUtilityOfTraveling(), votMIT);
								break;
							case "pt_plus":
								tempConfigs = createNewConfigs(aPTprice, aMITprice, emptyRides,
										inputConfig.planCalcScore().getModes()
												.get("pt").getMarginalUtilityOfTraveling()*0.75, votMIT);
								break;
						}
						for (Tuple<String, Config> tempConfig : tempConfigs) {
							new ConfigWriter(tempConfig.getSecond()).write(
									outputPath + tempConfig.getFirst());
							eulerCommands += "\n" + "bsub -n 8 -W ";
							eulerCommands += "24:00 ";
							eulerCommands += "-R \"rusage[mem=2560]\" "
									+ "java -Xmx20g -server -cp ../boescpa-0.1.0/boescpa-0.1.0.jar ";
							eulerCommands += "ch.ethz.matsim.boescpa.diss.simulations.RunSimulation ";
							eulerCommands += tempConfig.getFirst()
										+ " ../scenario/siedlungsraum_zug_shp/siedlungsraum_zug.shp";
						}
					}
				}
			}
		}
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
														 double votMIT, String votMITName) {
		List<Tuple<String, Config>> newConfigs = new LinkedList<>();
		Config config = ConfigUtils.loadConfig(inputConfigPath);
		// make basic policy implementations
		config.planCalcScore().getModes().get("pt").setMonetaryDistanceRate(
				config.planCalcScore().getModes().get("pt").getMonetaryDistanceRate()*aPTprice);
		config.planCalcScore().getModes().get("car").setMonetaryDistanceRate(
				config.planCalcScore().getModes().get("car").getMonetaryDistanceRate()*aMITprice);
		config.planCalcScore().getModes().get("car").setMarginalUtilityOfTraveling(votMIT);
		// add empty rides
		if (!emptyRides.equals("0.0")) {
			config.plans().setInputFile(
					"&INBASE;population_" + emptyRides + "emptyTripsPerAgent.xml.gz");
			config.plans().setInputPersonAttributeFile(
					"&INBASE;population_" + emptyRides + "emptyTripsPerAgent_attributes.xml.gz");
		}
		// other customizations for each run
		String runString = getNameString(aPTprice, aMITprice, votMITName, emptyRides);
		config.controler().setRunId(runString);
		config.controler().setOutputDirectory("/cluster/work/ivt_vpl/pboesch/diss/nonAV/output_" + runString);
		config.qsim().setTimeStepSize(5);
		newConfigs.add(new Tuple<>("config_-_" + runString + ".xml", config));
		return newConfigs;
	}

	public static String getNameString(double aPTprice, double aMITprice, String votMITName, String emptyRides) {
		return aPTprice + "-" + aMITprice + "-" + emptyRides + "-" + votMITName;
	}
}
