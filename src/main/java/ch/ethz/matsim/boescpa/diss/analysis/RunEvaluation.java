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

package ch.ethz.matsim.boescpa.diss.analysis;

import org.matsim.core.utils.io.IOUtils;

import java.io.*;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class RunEvaluation {
	private static final String SEP = ";";

	private static final String header_simType = "simClass" + SEP +
			"aPTprice" + SEP + "aMITprice" + SEP + "emptyRides" + SEP + "votMIT" + SEP +
					"serviceTypeAV" + SEP + "AVprice" + SEP + "AVbaseFare" + SEP + "votAV" + SEP + "levelOfServiceAV";
	private static final String header_analysisResults =
			"modeShare_PT" + SEP + "modeShare_AV" + SEP + "modeShare_CAR" + SEP + "modeShare_SM" + SEP +
					"avTravDist_PT" + SEP + "avTravDist_AV" + SEP + "avTravDist_CAR" + SEP + "avTravDist_SM" + SEP +
					"avSpeed_PT" + SEP + "avSpeed_AV" + SEP + "avSpeed_CAR" + SEP + "avSpeed_SM";

	private final String pathToRunFolders;
	private final String simClass;
	private final BufferedWriter writer;

	private RunEvaluation(String pathToRunFolders, String simClass) throws IOException {
		this.pathToRunFolders = pathToRunFolders;
		this.simClass = simClass;
		this.writer = IOUtils.getBufferedWriter(pathToRunFolders + File.separator
				+ "results_summary.csv");
		String header = "runID"
				+ SEP + header_simType
				+ SEP + header_analysisResults;
		System.out.print(header + "\n");
		this.writer.write(header);
		this.writer.newLine();
		this.writer.flush();
	}

	public static void main(final String[] args) throws IOException {
		String pathToRunFolders = args[0];
		String simClass = args[1];
		RunEvaluation runEvaluation = new RunEvaluation(pathToRunFolders, simClass);
		runEvaluation.readFiles();
	}

	private void readFiles() throws IOException {
		File[] files = new File(this.pathToRunFolders).listFiles();
		if (files != null) {
			for (File file : files) {
				if (file.isDirectory()) {
					String outputString = analyzeFolder(file.getPath());
					if (outputString != null) {
						this.writer.write(outputString);
						this.writer.newLine();
						this.writer.flush();
						System.out.println(outputString);
					}
				}
			}
		}
		this.writer.close();
	}

	private String analyzeFolder(String pathToFolder) throws IOException {
		if (pathToFolder.contains("output_") || pathToFolder.contains("onlyAVoligo")) {
			String runId = pathToFolder.substring(pathToFolder.lastIndexOf("_") + 1);
			String output = runId;
			output = output.concat(getSimType(runId));
			output = output.concat(getAnalysisResults(pathToFolder, runId));
			// ****************************
			// add more information here...
			// ****************************
			return output;
		} else {
			return null;
		}
	}

	private String getAnalysisResults(String pathToRunFolder, String runId) throws IOException {
		BufferedReader reader = IOUtils.getBufferedReader(pathToRunFolder + File.separator
				+ runId + ".output_events.xml.gz_analysisResults.csv");
		for (int i = 0; i < 5; i++) reader.readLine();
		String line = reader.readLine();
		double totalNumberOfTrips = 0;
		double[] numberOfTrips = new double[4];
		double[] avDist = new double[4];
		double[] avDur = new double[4];
		while (!line.equals("")) {
			String[] lineElements = line.split("; ");
			switch (lineElements[0]) {
				case "pt":
					numberOfTrips[0] = Double.parseDouble(lineElements[1]);
					totalNumberOfTrips += numberOfTrips[0];
					avDist[0] = Double.parseDouble(lineElements[3]);
					avDur[0] = Double.parseDouble(lineElements[2]);
					break;
				case "av":
					numberOfTrips[1] = Double.parseDouble(lineElements[1]);
					totalNumberOfTrips += numberOfTrips[1];
					avDist[1] = Double.parseDouble(lineElements[3]);
					avDur[1] = Double.parseDouble(lineElements[2]);
					break;
				case "car":
					numberOfTrips[2] = Double.parseDouble(lineElements[1]);
					totalNumberOfTrips += numberOfTrips[2];
					avDist[2] = Double.parseDouble(lineElements[3]);
					avDur[2] = Double.parseDouble(lineElements[2]);
					break;
				case "slow_mode":
					numberOfTrips[3] = Double.parseDouble(lineElements[1]);
					totalNumberOfTrips += numberOfTrips[3];
					avDist[3] = Double.parseDouble(lineElements[3]);
					avDur[3] = Double.parseDouble(lineElements[2]);
					break;
				default:
			}
			line = reader.readLine();
		}
		String modeShares = "", travDists = "", speeds = "";
		for (int i = 0; i < 4; i++) {
			// mode share
			modeShares = modeShares.concat(String.valueOf(numberOfTrips[i]/totalNumberOfTrips) + SEP);
			travDists = travDists.concat(String.valueOf(avDist[i]) + SEP);
			if (avDur[i] > 0) {
				speeds = speeds.concat(String.valueOf(avDist[i] / (avDur[i] / 60)) + SEP);
			} else {
				speeds = speeds.concat("0" + SEP);
			}
		}
		return SEP.concat(modeShares).concat(travDists).concat(speeds);
	}

	private String getSimType(String runId) {
		String output = SEP + runId.replace("-", SEP);
		switch (simClass) {
			case "combination":
				return SEP + simClass + SEP + output;
			case "nonAV":
				return SEP + simClass + SEP + output + SEP + ";;;;;";
			case "oligo":
				if (output.contains("onlyAVoligo")) {
					return SEP + simClass + ";;;;;;;;;";
				} else {
					return SEP + simClass + SEP + output + SEP + ";;;;;";
				}
			case "onlyAV":
				return SEP + simClass + SEP + ";;;" + output;
			default:
				return "";
		}
	}
}
