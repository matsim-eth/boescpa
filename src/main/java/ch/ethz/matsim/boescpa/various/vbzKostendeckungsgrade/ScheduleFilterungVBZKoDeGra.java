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

package ch.ethz.matsim.boescpa.various.vbzKostendeckungsgrade;


import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.pt.transitSchedule.api.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class ScheduleFilterungVBZKoDeGra {

	public static void main(final String[] args) {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new TransitScheduleReader(scenario).readFile(args[0]);
		TransitSchedule inputSchedule = scenario.getTransitSchedule();
		TransitSchedule outputSchedule =
				ScenarioUtils.createScenario(ConfigUtils.createConfig()).getTransitSchedule();

		BufferedReader reader = IOUtils.getBufferedReader(args[1]);
		BufferedWriter writerNotFound = IOUtils.getBufferedWriter(args[2]);
		try {
			reader.readLine(); // header
			String line = reader.readLine();
			while (line != null) {
				Id<TransitLine> ptLineId = getTransitLineId(inputSchedule, line);
				if (ptLineId != null) {
					if (!outputSchedule.getTransitLines().keySet().contains(ptLineId)) {
						TransitLine transitLine = inputSchedule.getTransitLines().get(ptLineId);
						outputSchedule.addTransitLine(transitLine);
						for (TransitRoute route : transitLine.getRoutes().values()) {
							for (TransitRouteStop stop : route.getStops()) {
								if (!outputSchedule.getFacilities().values()
										.contains(stop.getStopFacility())) {
									outputSchedule.addStopFacility(stop.getStopFacility());
								}
							}
						}
					}
				} else {
					System.out.println("Not found: " + line);
					writerNotFound.write(line); writerNotFound.newLine();
				}
				line = reader.readLine();
			}
			reader.close();
			writerNotFound.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		new TransitScheduleWriter(outputSchedule).writeFile(args[3]);
	}

	static Id<TransitLine> getTransitLineId(TransitSchedule inputSchedule, String line) {
		String[] lineElements = line.split(DatenaufbereitungVBZKoDeGra.DEL);
		String ptLine = lineElements[0] + "_line" + lineElements[1].replace("S", "");
		String ptLineTrain = lineElements[0] + "_" + lineElements[1] + "_";
		Id<TransitLine> ptLineId = null;
		for (Id<TransitLine> lineId : inputSchedule.getTransitLines().keySet()) {
			if (lineId.toString().contains(ptLine)
					|| lineId.toString().contains(ptLineTrain)) ptLineId = lineId;
		}
		return ptLineId;
	}

}
