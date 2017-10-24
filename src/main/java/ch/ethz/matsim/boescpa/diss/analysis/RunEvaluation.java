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
				+ header_PassengerKM
				+ header_AnzPassengers
				+ header_Accessibility
				//+ header_Profitability
				//+ header_Welfares
				+ header_TimeUsage
				+ header_AVFleetSizes;
				//+ header_AVStats;
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

	private String analyzeFolder(String pathToRunFolder) throws IOException {
		if (pathToRunFolder.contains("output_")) {
			String runId = pathToRunFolder.substring(pathToRunFolder.lastIndexOf("output_") + 7);
			String output = runId;
			output = output.concat(getSimType(runId));
			output = output.concat(getAnalysisResults(pathToRunFolder, runId));
			output = output.concat(getVKT(pathToRunFolder, runId));
			output = output.concat(getPassengerKM(pathToRunFolder, runId));
			output = output.concat(getAnzPassengers(pathToRunFolder, runId));
			output = output.concat(getAccessibilities(pathToRunFolder, runId));
			//output = output.concat(getProfitabilities(pathToRunFolder, runId));
			//output = output.concat(getWelfares(pathToRunFolder, runId));
			output = output.concat(getTimeUsages(pathToRunFolder, runId));
			output = output.concat(getAVFleetSizes(pathToRunFolder, runId));
			//output = output.concat(getAVStats(pathToRunFolder, runId));
			// ****************************
			// add more information here...
			// ****************************
			return output;
		} else {
			return null;
		}
	}

	/*private static final String header_AVStats = "";

	private String getAVStats(String pathToRunFolder, String runId) {
		//int fleetSize = getFleetSize(pathToRunFolder + File.separator + runId + )
		return "";
	}*/

	private static final String header_AVFleetSizes = DEL + "FleetSize_aTaxi" +
			DEL + "FleetSize_aRS" + DEL + "FleetSize_aTaxi_l" + DEL + "FleetSize_aTaxi_m" + DEL + "FleetSize_aTaxi_h" +
			DEL + "FleetSize_aRS_l" + DEL + "FleetSize_aRS_m" + DEL + "FleetSize_aRS_h";

	private String getAVFleetSizes(String pathToRunFolder, String runId) throws IOException {
		Map<String, Double> evalResult = !simClass.equals("nonAV") ?
				evaluateFile(pathToRunFolder + File.separator + runId + ".avFleetSizes.csv") :
				new HashMap<>();
		String out = evalResult.keySet().contains("aTaxi") ? DEL + df.format(evalResult.get("aTaxi")*scaleFactor) : DEL + 0;
		out = evalResult.keySet().contains("aRS") ? out + DEL + df.format(evalResult.get("aRS")*scaleFactor) : out + DEL + 0;
		out = evalResult.keySet().contains("taxi_l") ? out + DEL + df.format(evalResult.get("taxi_l")*scaleFactor) : out + DEL + 0;
		out = evalResult.keySet().contains("taxi_m") ? out + DEL + df.format(evalResult.get("taxi_m")*scaleFactor) : out + DEL + 0;
		out = evalResult.keySet().contains("taxi_h") ? out + DEL + df.format(evalResult.get("taxi_h")*scaleFactor) : out + DEL + 0;
		out = evalResult.keySet().contains("pool_l") ? out + DEL + df.format(evalResult.get("pool_l")*scaleFactor) : out + DEL + 0;
		out = evalResult.keySet().contains("pool_m") ? out + DEL + df.format(evalResult.get("pool_m")*scaleFactor) : out + DEL + 0;
		out = evalResult.keySet().contains("pool_h") ? out + DEL + df.format(evalResult.get("pool_h")*scaleFactor) : out + DEL + 0;
		return out;
	}

	private static final String header_TimeUsage = DEL + "time_pt" + DEL + "time_av" + DEL + "time_car" + DEL + "time_slowmodes" +
			DEL + "time_outarea" + DEL + "time_work" + DEL + "time_home" + DEL + "time_shop" + DEL + "time_leisure" +
			DEL + "time_escort"  + DEL + "time_education";

	private String getTimeUsages(String pathToRunFolder, String runId) throws IOException {
		BufferedReader reader = IOUtils.getBufferedReader(pathToRunFolder + File.separator
				+ runId + ".output_events.xml.gz_analysisResultsTargetFunction.csv");
		double numberOfAgents = -1;
		Map<String, Double> timesSpent = new HashMap<>();
		String line = reader.readLine();
		boolean record = false;
		while (line != null) {
			String[] lineElements = line.split("; ");
			if (lineElements.length <= 1) {
				record = false;
			} else if (lineElements[0].length() > 5 && lineElements[0].substring(0, 6).equals("Number")) {
				numberOfAgents = Double.parseDouble(lineElements[1]);
			} else if (record) {
				double totalTimeSpent = Double.parseDouble(lineElements[1]) * Double.parseDouble(lineElements[2]);
				timesSpent.put(lineElements[0], totalTimeSpent/numberOfAgents);
			} else if (lineElements[0].equals("Mode") || lineElements[0].equals("Activity")) {
				record = true;
			}
			line = reader.readLine();
		}
		// minutes spent on traveling
		String out = timesSpent.keySet().contains("pt") ? DEL + df.format(timesSpent.get("pt")) : DEL + 0;
		out = timesSpent.keySet().contains("av") ? out + DEL + df.format(timesSpent.get("av")) : out + DEL + 0;
		out = timesSpent.keySet().contains("car") ? out + DEL + df.format(timesSpent.get("car")) : out + DEL + 0;
		out = timesSpent.keySet().contains("slow_mode") ? out + DEL + df.format(timesSpent.get("slow_mode")) : out + DEL + 0;
		double outArea = timesSpent.keySet().contains("outArea_pt") ? timesSpent.get("outArea_pt") : 0;
		outArea += timesSpent.keySet().contains("outArea_car") ? timesSpent.get("outArea_car") : 0;
		out = out + DEL + df.format(outArea);
		// minutes spent on activities
		//	remote work and work are taken together as "work"
		double workTime = timesSpent.keySet().contains("re") ? timesSpent.get("re")*60 : 0;
		workTime += timesSpent.keySet().contains("wo") ? timesSpent.get("wo")*60 : 0;
		out = out + DEL + df.format(workTime);
		//	30 hours to 24 hours is done by subtracting from home 6 hours ("average" swiss assumed to spend hours 24 to 30 at home)
		out = timesSpent.keySet().contains("ho") ? out + DEL + df.format((timesSpent.get("ho")-6)*60) : out + DEL + 0;
		out = timesSpent.keySet().contains("sh") ? out + DEL + df.format(timesSpent.get("sh")*60) : out + DEL + 0;
		out = timesSpent.keySet().contains("le") ? out + DEL + df.format(timesSpent.get("le")*60) : out + DEL + 0;
		out = timesSpent.keySet().contains("es") ? out + DEL + df.format(timesSpent.get("es")*60) : out + DEL + 0;
		out = timesSpent.keySet().contains("ed") ? out + DEL + df.format(timesSpent.get("ed")*60) : out + DEL + 0;
		return out;
	}

	private static final String header_Accessibility = DEL + "access_total_pt" + DEL + "access_total_av" +
			DEL + "access_total_car" + DEL + "access_peak_pt" + DEL + "access_peak_av" + DEL + "access_peak_car" +
			DEL + "access_offpeak_pt" + DEL + "access_offpeak_av" + DEL + "access_offpeak_car";

	private String getAccessibilities(String pathToRunFolder, String runId) throws IOException {
		BufferedReader reader = IOUtils.getBufferedReader(pathToRunFolder + File.separator
				+ runId + ".output_events.xml.gz_analysisResultsTargetFunction.csv");
		String line = reader.readLine();
		while (!line.contains("accessibilities")) line = reader.readLine();
		reader.readLine(); line = reader.readLine();
		int numberOfEmptyLines = 0;
		Map<String, String> valsFound = new HashMap<>();
		while(numberOfEmptyLines < 5 && line != null) {
			if (line.equals("")) {
				numberOfEmptyLines++;
				line = reader.readLine();
				continue;
			}
			String[] lineElements = line.split("; ");
			switch (lineElements[0].trim()) {
				case "pt":
					if (numberOfEmptyLines < 1) {
						valsFound.put("pt_total", lineElements[1]);
					} else if (numberOfEmptyLines < 3) {
						valsFound.put("pt_peak", lineElements[1]);
					} else {
						valsFound.put("pt_offpeak", lineElements[1]);
					}
					break;
				case "av":
					if (numberOfEmptyLines < 1) {
						valsFound.put("av_total", lineElements[1]);
					} else if (numberOfEmptyLines < 3) {
						valsFound.put("av_peak", lineElements[1]);
					} else {
						valsFound.put("av_offpeak", lineElements[1]);
					}
					break;
				case "car":
					if (numberOfEmptyLines < 1) {
						valsFound.put("car_total", lineElements[1]);
					} else if (numberOfEmptyLines < 3) {
						valsFound.put("car_peak", lineElements[1]);
					} else {
						valsFound.put("car_offpeak", lineElements[1]);
					}
					break;
			}
			line = reader.readLine();
		}
		String out = valsFound.containsKey("pt_total") ? DEL + valsFound.get("pt_total") : DEL + 0;
		out = valsFound.containsKey("av_total") ? out + DEL + valsFound.get("av_total") : out + DEL + 0;
		out = valsFound.containsKey("car_total") ? out + DEL + valsFound.get("car_total") : out + DEL + 0;
		out = valsFound.containsKey("pt_peak") ? out + DEL + valsFound.get("pt_peak") : out + DEL + 0;
		out = valsFound.containsKey("av_peak") ? out + DEL + valsFound.get("av_peak") : out + DEL + 0;
		out = valsFound.containsKey("car_peak") ? out + DEL + valsFound.get("car_peak") : out + DEL + 0;
		out = valsFound.containsKey("pt_offpeak") ? out + DEL + valsFound.get("pt_offpeak") : out + DEL + 0;
		out = valsFound.containsKey("av_offpeak") ? out + DEL + valsFound.get("av_offpeak") : out + DEL + 0;
		out = valsFound.containsKey("car_offpeak") ? out + DEL + valsFound.get("car_offpeak") : out + DEL + 0;
		return out;
	}

	private static final String header_AnzPassengers = DEL + "AnzPass_ptBus" + DEL + "AnzPass_ptOther" + DEL + "AnzPass_aTaxi" +
			DEL + "AnzPass_aRS" + DEL + "AnzPass_aTaxi_l" + DEL + "AnzPass_aTaxi_m" + DEL + "AnzPass_aTaxi_h" +
			DEL + "AnzPass_aRS_l" + DEL + "AnzPass_aRS_m" + DEL + "AnzPass_aRS_h";

	private String getAnzPassengers(String pathToRunFolder, String runId) throws IOException {
		Map<String, Double> evalResult = evaluateFile(pathToRunFolder + File.separator +
				runId + ".passenger_anz.csv");
		String out = evalResult.keySet().contains("pt_bus") ? DEL + df.format(evalResult.get("pt_bus")*scaleFactor) : DEL + 0;
		out = evalResult.keySet().contains("pt_other") ? out + DEL + df.format(evalResult.get("pt_other")*scaleFactor) : out + DEL + 0;
		out = evalResult.keySet().contains("av_aTaxi") ? out + DEL + df.format(evalResult.get("av_aTaxi")*scaleFactor) : out + DEL + 0;
		out = evalResult.keySet().contains("av_aRS") ? out + DEL + df.format(evalResult.get("av_aRS")*scaleFactor) : out + DEL + 0;
		out = evalResult.keySet().contains("av_taxi_l") ? out + DEL + df.format(evalResult.get("av_taxi_l")*scaleFactor) : out + DEL + 0;
		out = evalResult.keySet().contains("av_taxi_m") ? out + DEL + df.format(evalResult.get("av_taxi_m")*scaleFactor) : out + DEL + 0;
		out = evalResult.keySet().contains("av_taxi_h") ? out + DEL + df.format(evalResult.get("av_taxi_h")*scaleFactor) : out + DEL + 0;
		out = evalResult.keySet().contains("av_pool_l") ? out + DEL + df.format(evalResult.get("av_pool_l")*scaleFactor) : out + DEL + 0;
		out = evalResult.keySet().contains("av_pool_m") ? out + DEL + df.format(evalResult.get("av_pool_m")*scaleFactor) : out + DEL + 0;
		out = evalResult.keySet().contains("av_pool_h") ? out + DEL + df.format(evalResult.get("av_pool_h")*scaleFactor) : out + DEL + 0;
		return out;
	}

	private static final String header_PassengerKM = DEL + "PassKM_ptBus" + DEL + "PassKM_ptOther" + DEL + "PassKM_aTaxi" +
			DEL + "PassKM_aRS" + DEL + "PassKM_aTaxi_l" + DEL + "PassKM_aTaxi_m" + DEL + "PassKM_aTaxi_h" +
			DEL + "PassKM_aRS_l" + DEL + "PassKM_aRS_m" + DEL + "PassKM_aRS_h";

	private String getPassengerKM(String pathToRunFolder, String runId) throws IOException {
		Map<String, Double> evalResult = evaluateFile(pathToRunFolder + File.separator +
				runId + ".passenger_km.csv");
		String out = evalResult.keySet().contains("pt_bus") ? DEL + df.format(evalResult.get("pt_bus")*scaleFactor) : DEL + 0;
		out = evalResult.keySet().contains("pt_other") ? out + DEL + df.format(evalResult.get("pt_other")*scaleFactor) : out + DEL + 0;
		out = evalResult.keySet().contains("av_aTaxi") ? out + DEL + df.format(evalResult.get("av_aTaxi")*scaleFactor) : out + DEL + 0;
		out = evalResult.keySet().contains("av_aRS") ? out + DEL + df.format(evalResult.get("av_aRS")*scaleFactor) : out + DEL + 0;
		out = evalResult.keySet().contains("av_taxi_l") ? out + DEL + df.format(evalResult.get("av_taxi_l")*scaleFactor) : out + DEL + 0;
		out = evalResult.keySet().contains("av_taxi_m") ? out + DEL + df.format(evalResult.get("av_taxi_m")*scaleFactor) : out + DEL + 0;
		out = evalResult.keySet().contains("av_taxi_h") ? out + DEL + df.format(evalResult.get("av_taxi_h")*scaleFactor) : out + DEL + 0;
		out = evalResult.keySet().contains("av_pool_l") ? out + DEL + df.format(evalResult.get("av_pool_l")*scaleFactor) : out + DEL + 0;
		out = evalResult.keySet().contains("av_pool_m") ? out + DEL + df.format(evalResult.get("av_pool_m")*scaleFactor) : out + DEL + 0;
		out = evalResult.keySet().contains("av_pool_h") ? out + DEL + df.format(evalResult.get("av_pool_h")*scaleFactor) : out + DEL + 0;
		return out;
	}

	private static final String header_VKM = DEL + "totalVKM_av" + DEL + "totalVKM_avANDcar" + DEL + "totalVKM_all";

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
					"avTripDist_PT" + DEL + "avTripDist_AV" + DEL + "avTripDist_CAR" + DEL + "avTripDist_SM" + DEL +
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
		String output = runId.replace("-", DEL);
		switch (simClass) {
			case "combination":
				return DEL + simClass + DEL + output;
			case "nonAV":
				return DEL + simClass + DEL + output + DEL + ";;;;";
			case "oligo":
				if (output.contains("onlyAVoligo")) {
					return DEL + simClass + ";;;;;;;;;";
				} else {
					return DEL + simClass + DEL + output + DEL + ";;;;";
				}
			case "onlyAV":
				return DEL + simClass + DEL + ";;;;" + output;
			default:
				return "";
		}
	}
}
