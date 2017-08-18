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

import ch.ethz.matsim.boescpa.av.staticDemand.Stats;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;

/**
 * WHAT IS IT FOR?
 * WHAT DOES IT?
 *
 * @author boescpa
 */
public class AutonomousVehicle {

	private Id<Link> position;
    private double travelTime;
    private double departureTime;
	private StatRequest statRequest;
	private double blockTime;

	AutonomousVehicle(Id<Link> initialPosition) {
        position = initialPosition;
    }

    Id<Link> getPosition() {
        return position;
    }

	double getTravelTime() {
		return travelTime;
	}

	double getDepartureTime() {
		return departureTime;
	}

	StatRequest getStatRequest() {
		return statRequest;
	}

	public double getBlockTime() {
		return blockTime;
	}

	public void setPosition(Id<Link> position) {
		this.position = position;
	}

	void setStatRequest(StatRequest statRequest) {
		this.statRequest = statRequest;
	}

	void setDepartureTime(double departureTime) {
		this.departureTime = departureTime;
	}

	void setTravelTime(double travelTime) {
		this.travelTime = travelTime;
	}

	public void setBlockTime(double blockTime) {
		this.blockTime = blockTime;
	}

    // ------ Stats ------

    private int numberOfServices = 0;
    private double totalAccessTime = 0;
    private double totalAccessDistance = 0;
    private double totalServiceTime = 0;
    private double totalServiceDistance = 0;
    private double totalWaitingTimeForAgents = 0;

    void incNumberOfServices() {
        numberOfServices++;
    }

    void incAccessTime(double accessTime) {
        if (accessTime >= 0) {
            totalAccessTime += accessTime;
        } else {
            throw new IllegalArgumentException("Negative access time!");
        }
    }

    void incAccessDistance(double accessDistance) {
        if (accessDistance >= 0) {
            totalAccessDistance += accessDistance;
        } else {
            throw new IllegalArgumentException("Negative access distance!");
        }
    }

    void incServiceTime(double serviceTime) {
        if (serviceTime >= 0) {
            totalServiceTime += serviceTime;
        } else {
            throw new IllegalArgumentException("Negative service time!");
        }
    }

    void incServiceDistance(double serviceDistance) {
        if (serviceDistance >= 0) {
            totalServiceDistance += serviceDistance;
        } else {
            throw new IllegalArgumentException("Negative service distance!");
        }
    }

    void incWaitingTime(double waitingTimeForAgent) {
        if (waitingTimeForAgent >= 0) {
            totalWaitingTimeForAgents += waitingTimeForAgent;
        } else {
            throw new IllegalArgumentException("Negative waiting time for agent!");
        }
    }

    public static String getStatsDescr() {
        return "numberOfServices"
                + Stats.delimiter + "totalAccessDistance"
                + Stats.delimiter + "totalServiceDistance"
                + Stats.delimiter + "totalAccessTime"
                + Stats.delimiter + "totalWaitingTimeForAgents"
                + Stats.delimiter + "totalServiceTime"
                + Stats.delimiter + "totalActiveTime";
    }

    public String getStats() {
        return numberOfServices
                + Stats.delimiter + totalAccessDistance
                + Stats.delimiter + totalServiceDistance
                + Stats.delimiter + totalAccessTime
                + Stats.delimiter + totalWaitingTimeForAgents
                + Stats.delimiter + totalServiceTime
                + Stats.delimiter + (totalAccessTime + totalServiceTime + totalWaitingTimeForAgents);
    }
}
