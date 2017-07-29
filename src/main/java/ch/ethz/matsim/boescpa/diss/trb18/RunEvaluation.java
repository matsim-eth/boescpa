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

import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static ch.ethz.matsim.boescpa.diss.trb18.ConfigCreator.getNameString;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class RunEvaluation {
	private static final String SEP = ";";

	private final String pathToRunFolders;
	private final BufferedWriter writer;

	private RunEvaluation(String pathToRunFolders) {
		this.pathToRunFolders = pathToRunFolders;
		this.writer = IOUtils.getBufferedWriter(pathToRunFolders + File.separator
				+ "summaryTRB18.csv");
		String header = "runID" + SEP + "aPTprice" + SEP + "aMITprice" + SEP + "votMIT" + SEP + "avType"
				+ SEP + "avgAccessibility" + SEP + "totVKM" + SEP + "avMonProfitable" + SEP + "MScar"
				+ SEP + "MSpt" + SEP + "MSav";
		System.out.print(header + "\n");
		try {
			this.writer.write(header);
			this.writer.newLine();
			this.writer.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(final String[] args) {
		String pathToRunFolders = args[0];

		RunEvaluation runEvaluation = new RunEvaluation(pathToRunFolders);
		runEvaluation.readTestFiles();
		//runEvaluation.readAllFiles();
	}

	private void readTestFiles() {
		try {
			readFilesAndCreateOutput(1,1,"car","none");
			readFilesAndCreateOutput(0,1,"car", "mon_rs");
			this.writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void readAllFiles() {
		try {
			for (double aPTprice : new double[]{1.00, 0.50, 0.00}) {
				for (double aMITprice : new double[]{1.00, 1.25}) {
					for (String votMIT : new String[]{"car", "pt", "pt_plus"}) {
						for (String avType : new String[]{"none", "mon_tax", "mon_rs", "oligo"}) {
							readFilesAndCreateOutput(aPTprice, aMITprice, votMIT, avType);
						}
					}
				}
			}
			this.writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void readFilesAndCreateOutput(double aPTprice, double aMITprice, String votMIT, String avType) throws IOException {
		String name = getNameString(aPTprice, aMITprice, votMIT, avType);
		String filePaths = this.pathToRunFolders + File.separator + "output_" + name +
				File.separator + name + ".";
		String pathNumPassengers = filePaths + "passenger_km.csv";
		Map<String, Double> modeSplit = getModeSplit(pathNumPassengers);
		String pathAccessibilities = filePaths + "accessibilities_total.csv";
		double avgAccessibility = getAvgAccessibility(pathAccessibilities, modeSplit);
		String pathVKM = filePaths + "vehicle_km.csv";
		double totVKM = getTotalVKM(pathVKM);
		String pathPassengerKM = filePaths + "passenger_km.csv";
		int avMonProfitable = -1;
		if (avType.equals("mon_tax")) avMonProfitable =
				getProfitability(pathVKM,pathPassengerKM, 0.46, 0.36);
		if (avType.equals("mon_rs")) avMonProfitable =
				getProfitability(pathVKM, pathPassengerKM, 0.32, 0.61);
		String categorization =
				getCategorizationString(aPTprice, aMITprice, votMIT, avType);
		// write output
		String outputString = categorization + SEP + avgAccessibility + SEP + totVKM
				+ SEP + avMonProfitable + SEP + modeSplit.get("car")
				+ SEP + modeSplit.get("pt") + SEP + modeSplit.get("av");
		this.writer.write(outputString);
		this.writer.newLine();
		this.writer.flush();
		System.out.print(outputString + "\n");
	}

	private Map<String, Double> getModeSplit(String pathPassengerKM) throws IOException {
		Map<String, Double> evalResult = evaluateFile(pathPassengerKM);
		// calculate weighted averages
		double avgCarVal = evalResult.get("car");
		double total = avgCarVal;
		double avgPTVal = evalResult.get("pt");
		total += avgPTVal;
		double avgAVVal = evalResult.get("av");
		total += avgAVVal;
		// calculate modal split
		Map<String, Double> modalSplit = new HashMap<>();
		modalSplit.put("car", avgCarVal / total);
		modalSplit.put("pt", avgPTVal / total);
		modalSplit.put("av", avgAVVal / total);
		return modalSplit;
	}

	private int getProfitability(String pathVKM, String pathPassengerKM,
								 double pricePerPassengerKM, double costPerVehicleKM) throws IOException {
		double passengerKMmonAV = evaluateFile(pathPassengerKM).get("av");
		double vehicleKMmonAV = evaluateFile(pathVKM).get("av");
		// profitability
		double income = passengerKMmonAV*pricePerPassengerKM;
		double costs = vehicleKMmonAV * costPerVehicleKM;
		return income > costs ? 1 : 0;
	}

	private double getTotalVKM(String pathVKM) throws IOException {
		Map<String, Double> evalResult = evaluateFile(pathVKM);
		return evalResult.get("car") + evalResult.get("pt") + evalResult.get("av");
	}

	private double getAvgAccessibility(String pathAccessibilities, Map<String, Double> modeSplit) throws IOException {
		BufferedReader reader = IOUtils.getBufferedReader(pathAccessibilities);
		reader.readLine(); // header
		// read values
		List<Double> carVals = new ArrayList<>();
		List<Double> ptVals = new ArrayList<>();
		List<Double> avVals = new ArrayList<>();
		String line = reader.readLine();
		while (line != null) {
			String[] lineVals = line.split(SEP);
			if (lineVals[1].trim().equals("car")) {
				carVals.add(Double.parseDouble(lineVals[2]));
			} else if (lineVals[1].trim().equals("pt")) {
				ptVals.add(Double.parseDouble(lineVals[2]));
			} else {
				avVals.add(Double.parseDouble(lineVals[2]));
			}
			line = reader.readLine();
		}
		// weighted averages
		double avgCarVal = weightedAverage(carVals);
		double avgPTVal = weightedAverage(ptVals);
		double avgAVVal = weightedAverage(avVals);
		// modesplit weighted averages
		return avgCarVal * modeSplit.get("car") + avgPTVal * modeSplit.get("pt") + avgAVVal * modeSplit.get("av");
	}

	private String getCategorizationString(double aPTprice, double aMITprice, String votMIT, String avType) {
		return getNameString(aPTprice, aMITprice, votMIT, avType)
				+ SEP + aPTprice + SEP + aMITprice + SEP + votMIT + SEP + avType;
	}

	private Map<String, Double> evaluateFile(String pathToFile) throws IOException {
		BufferedReader reader = IOUtils.getBufferedReader(pathToFile);
		// identify modes
		String[] header = reader.readLine().split(SEP); // header
		int car = 0;
		int[] pt = {0,0};
		List<Integer> av = new ArrayList<>();
		for (int i = 1; i < header.length; i++) {
			if (header[i].trim().equals("car")) {
				car = i;
			} else if (header[i].contains("pt")) {
				if (pt[0] == 0) pt[0] = i; else pt[1] = i;
			} else if (header[i].contains("av")) {
				av.add(i);
			}
		}
		// read values
		List<Double> carVals = new ArrayList<>();
		List<Double> ptVals = new ArrayList<>();
		List<Double> avVals = new ArrayList<>();
		String line = reader.readLine();
		while (line != null) {
			String[] lineVals = line.split(SEP);
			// car
			carVals.add(Double.parseDouble(lineVals[car]));
			// pt
			double ptVal = Double.parseDouble(lineVals[pt[0]]);
			ptVal += Double.parseDouble(lineVals[pt[1]]);
			ptVals.add(ptVal);
			// av
			double avVal = 0;
			for (int i : av) {
				avVal += Double.parseDouble(lineVals[i]);
			}
			avVals.add(avVal);
			line = reader.readLine();
		}
		// calculate weighted averages
		Map<String, Double> evalResult = new HashMap<>();
		evalResult.put("car", weightedAverage(carVals));
		evalResult.put("pt", weightedAverage(ptVals));
		evalResult.put("av", weightedAverage(avVals));
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

}
