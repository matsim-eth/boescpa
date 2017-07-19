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
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.utils.collections.Tuple;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class ConfigCreator {
	private final String inputConfigPath;
	private final String outputPath;

	public ConfigCreator(String inputConfig, String outputPath) {
		this.inputConfigPath = inputConfig;
		this.outputPath = outputPath + File.separator;
	}

	public static void main(final String[] args) {
		final String inputConfigPath = args[1];
		final String outputPath = args[2];
		ConfigCreator configCreator = new ConfigCreator(inputConfigPath, outputPath);
		configCreator.createConfigs();
	}

	private void createConfigs() {
		Config inputConfig = ConfigUtils.loadConfig(inputConfigPath);
		for (double aPTprice : new double[]{1.00, 0.50, 0.00}) {
			List<Tuple<String, Config>> tempConfigs = null;
			for (double aMITprice : new double[]{1.00, 1.25}) {
				for (String votMIT : new String[]{"none", "pt", "plus"}) {
					switch (votMIT) {
						case "none":
							tempConfigs = createNewConfigs(aPTprice, aMITprice,
									inputConfig.planCalcScore().getModes()
											.get("car").getMarginalUtilityOfTraveling());
							break;
						case "pt":
							tempConfigs = createNewConfigs(aPTprice, aMITprice,
									inputConfig.planCalcScore().getModes()
											.get("pt").getMarginalUtilityOfTraveling());
							break;
						case "plus":
							tempConfigs = createNewConfigs(aPTprice, aMITprice,
									inputConfig.planCalcScore().getModes()
											.get("pt").getMarginalUtilityOfTraveling()*0.75);
							break;
					}
				}
			}
			for (Tuple<String, Config> tempConfig : tempConfigs) {
				new ConfigWriter(tempConfig.getSecond()).write(outputPath + tempConfig.getFirst());
			}
		}
	}

	private List<Tuple<String, Config>> createNewConfigs(double aPTprice, double aMITprice, double votMIT) {
		List<Tuple<String, Config>> newConfigs = new LinkedList<>();
		for (String avType : new String[]{
				"none"}) { //, "mon_pub_tax", "mon_pub_rs", "mon_priv_tax", "mon_priv_rs", "oligo"}) {
			createAVConfig(avType);
			Config config = ConfigUtils.loadConfig(inputConfigPath);
			config.planCalcScore().getModes().get("pt").setMonetaryDistanceRate(
					config.planCalcScore().getModes().get("pt").getMonetaryDistanceRate()*aPTprice);
			config.planCalcScore().getModes().get("car").setMonetaryDistanceRate(
					config.planCalcScore().getModes().get("car").getMonetaryDistanceRate()*aMITprice);
			config.planCalcScore().getModes().get("car").setMarginalUtilityOfTraveling(votMIT);
			AVConfigGroup avConfigGroup = new AVConfigGroup();
			avConfigGroup.setConfigPath("av.xml");
			config.addModule(avConfigGroup);
			String runString = getNameString(aPTprice, aMITprice, votMIT, avType);
			config.controler().setRunId(runString);
			newConfigs.add(new Tuple<>("config_-_" + runString + ".xml", config));
		}
		return newConfigs;
	}

	private String getNameString(double aPTprice, double aMITprice, double votMIT, String avType) {
		return aPTprice + "_" + aMITprice + "_" + votMIT + "_" + avType;
	}

	private void createAVConfig(String avType) {
		String outputPath = this.outputPath + "av.xml";
		switch (avType) {
			case "none":
				// do nothing
				break;
			case "mon_pub_tax":
				// TO IMPLEMENT...
				break;
			case "usw":
				// TO IMPLEMENT...
				break;
		}

	}


}
