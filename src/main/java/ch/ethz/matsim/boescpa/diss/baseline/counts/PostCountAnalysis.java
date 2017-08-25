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

package ch.ethz.matsim.boescpa.diss.baseline.counts;

import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class PostCountAnalysis {

	public static void main(final String[] args) {
		String pathToLinkCountsList = args[0];
		String pathToScenarioConfig = args[1];
		String pathToEventsFile = args[2];
		String pathToOutputFile = args[3];

		EventsManager events = EventsUtils.createEventsManager();
		StreetLinkHourlyCountsEventHandler countsEventHandler = new StreetLinkHourlyCountsEventHandler(
				pathToLinkCountsList, ConfigUtils.loadConfig(pathToScenarioConfig));
		countsEventHandler.reset(0);
		events.addHandler(countsEventHandler);
		new MatsimEventsReader(events).readFile(pathToEventsFile);
		countsEventHandler.write(pathToOutputFile);
	}

}
