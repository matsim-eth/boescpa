/*
 * *********************************************************************** *
 * project: org.matsim.*                                                   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2015 by the members listed in the COPYING,        *
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

package ch.ethz.matsim.boescpa.diss.av.staticDemand;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * WHAT IS IT FOR?
 * WHAT DOES IT?
 *
 * @author boescpa
 */
public class Stats {
    private static Logger log = Logger.getLogger(Stats.class);

    public static final String delimiter = "; ";
	private final Map<Id<Person>, AutonomousVehicle> vehiclesInUse;
	private final List<AutonomousVehicle> availableVehicles;

	private List<StatRequest> statRequests = new ArrayList<>();
    private List<String> simStats = new ArrayList<>();
    private List<String> simResults = new ArrayList<>();

    private long totalDemand = 0;
    private long metDemand = 0;
	private long quickMetDemand = 0; // number
    private double responseTimeMetDemand = 0; // seconds
    private double maxResponseTimeMetDemand = 0; // seconds
    private double minTravelTimeMetDemand = 0; // minutes
    private double maxTravelTimeMetDemand = 0; // minutes
    private double totalTravelTimeMetDemand = 0; // minutes
    private double travelDistanceMetDemand = 0; // m

    private double totalWaitingTimeForAssignmentMetDemand = 0; // seconds
    private double maxWaitingTimeForAssignmentMetDemand = 0; // seconds
    private double totalWaitingTimeAgents = 0; // seconds
    private double maxWaitingTimeAgents = 0; // seconds


	public Stats(Map<Id<Person>,AutonomousVehicle> vehiclesInUse, List<AutonomousVehicle> availableVehicles) {
		this.vehiclesInUse = vehiclesInUse;
		this.availableVehicles = availableVehicles;
		writeRecordStatsHeader();
	}

	void incTotalDemand() {
        totalDemand++;
    }

    void incMetDemand() {
        metDemand++;
    }

	void incQuickMetDemand() {
		this.quickMetDemand++;
	}


    void incResponseTimeMetDemand(double responseTime) {
        if (responseTime >= 0) {
            responseTimeMetDemand += responseTime;
        } else {
            throw new IllegalArgumentException("Negative response time!");
        }

        if (responseTime > maxResponseTimeMetDemand) {
            maxResponseTimeMetDemand = responseTime;
        }
    }

    void incTravelTimeMetDemand(double travelTimeMetDemand) {
        double localTravelTime = travelTimeMetDemand / 60; // Conversion to minutes...

        if (localTravelTime >= 0) {
            totalTravelTimeMetDemand += localTravelTime;
        } else {
            throw new IllegalArgumentException("Negative travel time!");
        }

        if (localTravelTime < minTravelTimeMetDemand) {
            minTravelTimeMetDemand = localTravelTime;
        }
        if (localTravelTime > maxTravelTimeMetDemand) {
            maxTravelTimeMetDemand = localTravelTime;
        }
    }

    void incTravelDistanceMetDemand(double distance) {
        if (distance >= 0) {
            travelDistanceMetDemand += distance;
        } else {
            throw new IllegalArgumentException("Negative travel distance!");
        }
    }

    void incWaitingTimeForAssignmentMetDemand(double waitingTimeForAssignment) {
        if (waitingTimeForAssignment >= 0) {
            totalWaitingTimeForAssignmentMetDemand += waitingTimeForAssignment;
            if (waitingTimeForAssignment > maxWaitingTimeForAssignmentMetDemand) {
                maxWaitingTimeForAssignmentMetDemand = waitingTimeForAssignment;
            }
        } else {
            throw new IllegalArgumentException("Negative waiting time for assignment!");
        }
    }

    void incWaitingTime(double waitingTimeAgents) {
        if (waitingTimeAgents >= 0) {
            totalWaitingTimeAgents += waitingTimeAgents;
            if (waitingTimeAgents > maxWaitingTimeAgents) {
                maxWaitingTimeAgents = waitingTimeAgents;
            }
        } else {
            throw new IllegalArgumentException("Negative waiting times!");
        }
    }

    void addRequest(StatRequest request) {
        statRequests.add(request);
    }

    private void writeRecordStatsHeader() {
        simStats.add("time [min]"
                        + delimiter + "pendingRequests"
                        + delimiter + "vehiclesInUse"
                        + delimiter + "availableVehicles"
                        + delimiter + "quickServedRequests"
                        + delimiter + "servedRequests"
                        + delimiter + "lateServedRequests"
                        + delimiter + "unservedRequests"
        );
    }

    public void recordStats(int time, int pendingRequests, int vehiclesInUse, int availableVehicles,
							int quickServedRequests, int servedRequests, int lateServedRequests,
							int unservedRequests) {
        simStats.add(time / 60
                        + delimiter + pendingRequests
                        + delimiter + vehiclesInUse
                        + delimiter + availableVehicles
                        + delimiter + quickServedRequests
                        + delimiter + servedRequests
                        + delimiter + lateServedRequests
                        + delimiter + unservedRequests
        );

    }

    public void printResults(String pathToOutput) {
        composeResults();
        log.info("");
        for (String result : simResults) {
            log.info(result);
        }
        log.info("");
        writeResultsToFile(pathToOutput);
    }

    private void composeResults() {
        simResults.add("RESULTS:");
        simResults.add(" - Total demand: " + totalDemand);
        simResults.add(" - Total number of AVs: " + (this.vehiclesInUse.size() + this.availableVehicles.size()));
        // Met demand:
        simResults.add("   ...........");
        simResults.add(" - Quick met demand: " + quickMetDemand);
        simResults.add(" - Met demand: " + metDemand);
        simResults.add(" - Average waiting time for assignment met demand: " + 0.01 * (Math.round(100 * (totalWaitingTimeForAssignmentMetDemand / metDemand / 60))) + " min");
        simResults.add(" - Max waiting time for assignment met demand: " + 0.01 * (Math.round(100 * (maxWaitingTimeForAssignmentMetDemand / 60))) + " min");
		simResults.add(" - Average waiting time agent for vehicle: " + 0.01 * (Math.round(100 * (totalWaitingTimeAgents / metDemand / 60))) + " min");
		simResults.add(" - Max waiting time agent for vehicle: " + 0.01 * (Math.round(100 * (maxWaitingTimeAgents / 60))) + " min");
        simResults.add(" - Average response time met demand: " + 0.01 * (Math.round(100 * (responseTimeMetDemand / metDemand / 60))) + " min");
        simResults.add(" - Max response time met demand: " + 0.01 * (Math.round(100 * (maxResponseTimeMetDemand / 60))) + " min");
        simResults.add(" - Average travel time met demand: " + 0.01 * (Math.round(100 * (totalTravelTimeMetDemand / metDemand))) + " min");
        simResults.add(" - Min travel time met demand: " + 0.01 * (Math.round(100 * (minTravelTimeMetDemand))) + " min");
        simResults.add(" - Max travel time met demand: " + 0.01 * (Math.round(100 * (maxTravelTimeMetDemand))) + " min");
        simResults.add(" - Average travel distance met demand: " + (travelDistanceMetDemand / metDemand / 1000) + " km");
    }

    private void writeResultsToFile(String pathToOutput) {
        final String outputFileStats = pathToOutput.substring(0, pathToOutput.lastIndexOf("."))
                + "_Stats" + pathToOutput.substring(pathToOutput.lastIndexOf("."));
        final String outputFileResults = pathToOutput.substring(0, pathToOutput.lastIndexOf("."))
                + "_Results.txt";
        final String outputFileVehicles = pathToOutput.substring(0, pathToOutput.lastIndexOf("."))
                + "_Vehicles" + pathToOutput.substring(pathToOutput.lastIndexOf("."));
        final String outputFileRequests = pathToOutput.substring(0, pathToOutput.lastIndexOf("."))
                + "_Requests" + pathToOutput.substring(pathToOutput.lastIndexOf("."));

        try {
            final BufferedWriter outStats = IOUtils.getBufferedWriter(outputFileStats);
            final BufferedWriter outResults = IOUtils.getBufferedWriter(outputFileResults);
            final BufferedWriter outVehicles = IOUtils.getBufferedWriter(outputFileVehicles);
            final BufferedWriter outRequests = IOUtils.getBufferedWriter(outputFileRequests);
            log.info("Writing stats file...");
            for (String line : simStats) {
                outStats.write(line);
                outStats.newLine();
            }
            outStats.close();
            log.info("Writing stats file...done.");
            log.info("Writing results file...");
            for (String line : simResults) {
                outResults.write(line);
                outResults.newLine();
            }
            outResults.close();
            log.info("Writing results file...done.");
            log.info("Writing vehicle file...");
            outVehicles.write(AutonomousVehicle.getStatsDescr());
            outVehicles.newLine();
            for (AutonomousVehicle vehicle : this.availableVehicles) {
                outVehicles.write(vehicle.getStats());
                outVehicles.newLine();
            }
			for (AutonomousVehicle vehicle : this.vehiclesInUse.values()) {
				outVehicles.write(vehicle.getStats());
				outVehicles.newLine();
			}
            outVehicles.close();
            log.info("Writing vehicle file...done.");
            log.info("Writing request file...");
            outRequests.write(StatRequest.getStatsDescr());
            outRequests.newLine();
            for (StatRequest request : statRequests) {
                outRequests.write(request.getStats());
                outRequests.newLine();
            }
            outRequests.close();
            log.info("Writing request file...done.");
        } catch (IOException e) {
            log.info("Given trip-file-path not valid. Print trips not successfully executed.");
        }
    }
}
