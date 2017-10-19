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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class RunEvaluation {
	private static final String SEP = ";";

	private final String pathToRunFolders;
	private final String simClass;
	private final double scaleFactor;
	private final BufferedWriter writer;

	private RunEvaluation(String pathToRunFolders, String simClass, double scaleFactor) throws IOException {
		this.pathToRunFolders = pathToRunFolders;
		this.simClass = simClass;
		this.scaleFactor = scaleFactor;
		this.writer = IOUtils.getBufferedWriter(pathToRunFolders + File.separator
				+ "results_summary.csv");
		String header = "runID"
				+ header_simType
				+ header_analysisResults
				+ header_VKM;
		System.out.print(header + "\n");
		this.writer.write(header);
		this.writer.newLine();
		this.writer.flush();
	}

	public static void main(final String[] args) throws IOException {
		String pathToRunFolders = args[0];
		String simClass = args[1];
		double scaleFactor = Double.parseDouble(args[2]);
		RunEvaluation runEvaluation = new RunEvaluation(pathToRunFolders, simClass, scaleFactor);
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
			output = output.concat(getVKT(pathToFolder, runId));
			//output = output.concat(getAccessibilities(pathToFolder, runId));
			//output = output.concat(getProfitabilities(pathToFolder, runId));
			//output = output.concat(getWelfares(pathToFolder, runId));
			// ****************************
			// add more information here...
			// ****************************
			return output;
		} else {
			return null;
		}
	}

	private static final String header_VKM = SEP + "totalVKM_av" + SEP + "totalVKM_av-car" + SEP + "totalVKM_all";

	private String getVKT(String pathToRunFolder, String runId) throws IOException {
		Map<String, Double> evalResult = evaluateFile(pathToRunFolder + File.separator +
				runId + ".vehicle_km.csv");
		String output = "";
		// total vkm av
		double totalVKM = 0;
		for (String mode : evalResult.keySet()) {
			if (mode.contains("av")) totalVKM += evalResult.get(mode);
		}
		output = output + SEP + String.valueOf(totalVKM*scaleFactor);
		// total vkm car and av
		for (String mode : evalResult.keySet()) {
			if (mode.equals("car")) totalVKM += evalResult.get(mode);
		}
		output = output + SEP + String.valueOf(totalVKM*scaleFactor);
		// total vkm
		totalVKM = 0;
		for (String mode : evalResult.keySet()) {
			totalVKM += evalResult.get(mode);
		}
		output = output + SEP + String.valueOf(totalVKM*scaleFactor);
		return output;
	}

	private Map<String, Double> evaluateFile(String pathToFile) throws IOException {
		BufferedReader reader = IOUtils.getBufferedReader(pathToFile);
		// identify modes
		String[] header = reader.readLine().split(SEP); // header
		Map<Integer, List<Double>> zwischenresultate = new HashMap<>();
		for (int i = 1; i < header.length; i++) {
			zwischenresultate.put(i, new ArrayList<>());
		}
		String line = reader.readLine();
		while (line != null) {
			String[] lineVals = line.split(SEP);
			for (int i = 1; i < header.length; i++) {
				zwischenresultate.get(i).add(Double.parseDouble(lineVals[i]));
			}
			line = reader.readLine();
		}
		Map<String, Double> evalResult = new HashMap<>();
		for (int i = 1; i < header.length; i++) {
			evalResult.put(header[i].trim(), weightedAverage(zwischenresultate.get(i)));
		}
		return evalResult;
	}

	private double weightedAverage(List<Double> vals) {
		if (vals.size() == 0) return 0;
		long i = 1, intSum = 0;
		double valSum = 0;
		for (double val : vals) {
			intSum += i;
			valSum += val*i;
			i++;
		}
		return valSum/intSum;
	}

	private static final String header_analysisResults =
			SEP + "modeShare_PT" + SEP + "modeShare_AV" + SEP + "modeShare_CAR" + SEP + "modeShare_SM" + SEP +
					"avTravDist_PT" + SEP + "avTravDist_AV" + SEP + "avTravDist_CAR" + SEP + "avTravDist_SM" + SEP +
					"avSpeed_PT" + SEP + "avSpeed_AV" + SEP + "avSpeed_CAR" + SEP + "avSpeed_SM";

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
			modeShares = modeShares.concat(SEP + String.valueOf(numberOfTrips[i]/totalNumberOfTrips));
			travDists = travDists.concat(SEP + String.valueOf(avDist[i]));
			if (avDur[i] > 0) {
				speeds = speeds.concat(SEP + String.valueOf(avDist[i] / (avDur[i] / 60)));
			} else {
				speeds = speeds.concat(SEP + "0");
			}
		}
		return modeShares + travDists + speeds;
	}

	private static final String header_simType = SEP + "simClass" + SEP +
			"aPTprice" + SEP + "aMITprice" + SEP + "emptyRides" + SEP + "votMIT" + SEP +
			"serviceTypeAV" + SEP + "AVprice" + SEP + "AVbaseFare" + SEP + "votAV" + SEP + "levelOfServiceAV";

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
