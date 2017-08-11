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

/**
 * WHAT IS IT FOR?
 * WHAT DOES IT?
 *
 * @author boescpa
 */
public class StatRequest {

	private double assignmentTime = -1;
	private double responseTime = -1;
	private double startTime = -1;
	private double duration = -1;
	private double distance = -1;

	void setAssignmentTime(double assignmentTime) {
		this.assignmentTime = assignmentTime;
	}

	void setResponseTime(double responseTime) {
		this.responseTime = responseTime;
	}

	void setStartTime(double startTime) {
		this.startTime = startTime;
	}

	void setDuration(double duration) {
		this.duration = duration;
	}

	void setDistance(double distance) {
		this.distance = distance;
	}

	public static String getStatsDescr() {
		return "met"
				+ Stats.delimiter + "timeOfRequest"
				+ Stats.delimiter + "assignmentTime"
				+ Stats.delimiter + "responseTime"
				+ Stats.delimiter + "requestDuration"
				+ Stats.delimiter + "requestDistance";
	}

	public String getStats() {
		return Stats.delimiter + startTime
				+ Stats.delimiter + assignmentTime
				+ Stats.delimiter + responseTime
				+ Stats.delimiter + duration
				+ Stats.delimiter + distance;
	}
}
