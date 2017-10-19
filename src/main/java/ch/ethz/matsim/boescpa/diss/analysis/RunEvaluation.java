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
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class RunEvaluation {
	private static final String DEL = ";";

	private final String pathToRunFolders;
	private final String simClass;
	private final double scaleFactor;
	private final BufferedWriter writer;
	private final DecimalFormat df;

	private RunEvaluation(String pathToRunFolders, String simClass, double scaleFactor) throws IOException {
		this.pathToRunFolders = pathToRunFolders;
		this.simClass = simClass;
		this.scaleFactor = scaleFactor;
		this.writer = IOUtils.getBufferedWriter(pathToRunFolders + File.separator
				+ "results_summary.csv");
		String header = "runID"
				+ header_simType
				+ header_analysisResults
				+ header_VKM
				+ header_Accessibility;
		System.out.print(header + "\n");
		this.writer.write(header);
		this.writer.newLine();
		this.writer.flush();

		this.df = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
		this.df.setMaximumFractionDigits(340);
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
			output = output.concat(getAccessibilities(pathToFolder, runId));
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

	private static final String header_Accessibility = DEL + "access_total_pt" + DEL + "access_total_av" +
			DEL + "access_total_car" + DEL + "access_peak_pt" + DEL + "access_peak_av" + DEL + "access_peak_car" +
			DEL + "access_offpeak_pt" + DEL + "access_offpeak_av" + DEL + "access_offpeak_car";

	private String getAccessibilities(String pathToRunFolder, String runId) throws IOException {
		BufferedReader reader = IOUtils.getBufferedReader(pathToRunFolder + File.separator
				+ runId + ".output_events.xml.gz_analysisResultsTargetFunction.csv");
		for (int i = 0; i < 40; i++) reader.readLine();
		String line = reader.readLine();
		int numberOfValsFound = 0;
		Map<String, String> valsFound = new HashMap<>();
		while(numberOfValsFound < 9 || line != null) {
			String[] lineElements = line.split("; ");
			switch (lineElements[0]) {
				case "pt":
					if (numberOfValsFound < 3) {
						valsFound.put("pt_total", lineElements[1]);
					} else if (numberOfValsFound < 6) {
						valsFound.put("pt_peak", lineElements[1]);
					} else {
						valsFound.put("pt_offpeak", lineElements[1]);
					}
					numberOfValsFound++;
					break;
				case "av":
					if (numberOfValsFound < 3) {
						valsFound.put("av_total", lineElements[1]);
					} else if (numberOfValsFound < 6) {
						valsFound.put("av_peak", lineElements[1]);
					} else {
						valsFound.put("av_offpeak", lineElements[1]);
					}
					numberOfValsFound++;
					break;
				case "car":
					if (numberOfValsFound < 3) {
						valsFound.put("car_total", lineElements[1]);
					} else if (numberOfValsFound < 6) {
						valsFound.put("car_peak", lineElements[1]);
					} else {
						valsFound.put("car_offpeak", lineElements[1]);
					}
					numberOfValsFound++;
					break;
			}
			line = reader.readLine();
		}
		String output = DEL + valsFound.get("pt_total");
		output = output + DEL + valsFound.get("av_total");
		output = output + DEL + valsFound.get("car_total");
		output = output + DEL + valsFound.get("pt_peak");
		output = output + DEL + valsFound.get("av_peak");
		output = output + DEL + valsFound.get("car_peak");
		output = output + DEL + valsFound.get("pt_offpeak");
		output = output + DEL + valsFound.get("av_offpeak");
		output = output + DEL + valsFound.get("car_offpeak");
		return output;
	}

	private static final String header_VKM = DEL + "totalVKM_av" + DEL + "totalVKM_av-car" + DEL + "totalVKM_all";

	private String getVKT(String pathToRunFolder, String runId) throws IOException {
		Map<String, Double> evalResult = evaluateFile(pathToRunFolder + File.separator +
				runId + ".vehicle_km.csv");
		String output = "";
		// total vkm av
		double totalVKM = 0;
		for (String mode : evalResult.keySet()) {
			if (mode.contains("av")) totalVKM += evalResult.get(mode);
		}
		output = output + DEL + df.format(totalVKM*scaleFactor);
		// total vkm car and av
		for (String mode : evalResult.keySet()) {
			if (mode.equals("car")) totalVKM += evalResult.get(mode);
		}
		output = output + DEL + df.format(totalVKM*scaleFactor);
		// total vkm
		totalVKM = 0;
		for (String mode : evalResult.keySet()) {
			totalVKM += evalResult.get(mode);
		}
		output = output + DEL + df.format(totalVKM*scaleFactor);
		return output;
	}

	private Map<String, Double> evaluateFile(String pathToFile) throws IOException {
		BufferedReader reader = IOUtils.getBufferedReader(pathToFile);
		// identify modes
		String[] header = reader.readLine().split(DEL); // header
		Map<Integer, List<Double>> zwischenresultate = new HashMap<>();
		for (int i = 1; i < header.length; i++) {
			zwischenresultate.put(i, new ArrayList<>());
		}
		String line = reader.readLine();
		while (line != null) { // we only average the last 25 iterations
			String[] lineVals = line.split(DEL);
			if (lineVals[0].equals("276")) break;
			line = reader.readLine();
		}
		while (line != null) {
			String[] lineVals = line.split(DEL);
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
			DEL + "modeShare_PT" + DEL + "modeShare_AV" + DEL + "modeShare_CAR" + DEL + "modeShare_SM" + DEL +
					"avTravDist_PT" + DEL + "avTravDist_AV" + DEL + "avTravDist_CAR" + DEL + "avTravDist_SM" + DEL +
					"avSpeed_PT" + DEL + "avSpeed_AV" + DEL + "avSpeed_CAR" + DEL + "avSpeed_SM";

	private String getAnalysisResults(String pathToRunFolder, String runId) throws IOException {
		BufferedReader reader = IOUtils.getBufferedReader(pathToRunFolder + File.separator
				+ runId + ".output_events.xml.gz_analysisResultsTargetFunction.csv");
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
			modeShares = modeShares.concat(DEL + df.format(numberOfTrips[i]/totalNumberOfTrips));
			travDists = travDists.concat(DEL + df.format(avDist[i]));
			if (avDur[i] > 0) {
				speeds = speeds.concat(DEL + df.format(avDist[i] / (avDur[i] / 60)));
			} else {
				speeds = speeds.concat(DEL + "0");
			}
		}
		return modeShares + travDists + speeds;
	}

	private static final String header_simType = DEL + "simClass" + DEL +
			"aPTprice" + DEL + "aMITprice" + DEL + "emptyRides" + DEL + "votMIT" + DEL +
			"serviceTypeAV" + DEL + "AVprice" + DEL + "AVbaseFare" + DEL + "votAV" + DEL + "levelOfServiceAV";

	private String getSimType(String runId) {
		String output = DEL + runId.replace("-", DEL);
		switch (simClass) {
			case "combination":
				return DEL + simClass + DEL + output;
			case "nonAV":
				return DEL + simClass + DEL + output + DEL + ";;;;;";
			case "oligo":
				if (output.contains("onlyAVoligo")) {
					return DEL + simClass + ";;;;;;;;;";
				} else {
					return DEL + simClass + DEL + output + DEL + ";;;;;";
				}
			case "onlyAV":
				return DEL + simClass + DEL + ";;;" + output;
			default:
				return "";
		}
	}
}
