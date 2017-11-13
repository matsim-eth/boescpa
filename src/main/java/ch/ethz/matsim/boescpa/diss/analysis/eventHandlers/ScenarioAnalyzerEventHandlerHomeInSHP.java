/*
 * *********************************************************************** *
 * project: org.matsim.*                                                   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2014 by the members listed in the COPYING,        *
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

package ch.ethz.matsim.boescpa.diss.analysis.eventHandlers;

import ch.ethz.matsim.boescpa.analysis.scenarioAnalyzer.eventHandlers.ScenarioAnalyzerEventHandler;
import ch.ethz.matsim.boescpa.analysis.spatialCutters.SpatialCutter;
import ch.ethz.matsim.boescpa.lib.tools.utils.SHPFileUtils;
import ch.ethz.matsim.boescpa.lib.tools.coordUtils.CoordAnalyzer;
import com.vividsolutions.jts.geom.Geometry;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.opengis.feature.simple.SimpleFeature;

import java.util.HashSet;
import java.util.Set;

/**
 * Any new analysis to be done as part of the ScenarioAnalyzer-process has to implement this interface.
 *
 * @author boescpa
 */
public abstract class ScenarioAnalyzerEventHandlerHomeInSHP extends ScenarioAnalyzerEventHandler {

    protected static int ANALYSIS_END_TIME = 108000; // default 30h
	private static final boolean EXCLUDEPT = true;
	private static final boolean EXCLUDEFREIGHT = true;
	private static final boolean EXCLUDEAV = true;
	private final Population population;
	private final CoordAnalyzer coordAnalyzer;

	public ScenarioAnalyzerEventHandlerHomeInSHP(String pathToHomesSHP, Population population) {
		super();
		this.population = population;
		// load SHP with homes
		coordAnalyzer = CoordAnalyzer.getCoordAnalyzer(pathToHomesSHP);
	}

    public static void setAnalysisEndTime(int endTimeInSeconds) {
        ANALYSIS_END_TIME = endTimeInSeconds;
    }

	/**
	 * @param spatialEventCutter
	 * @return Results of the analysis in form of a (multiline) string.
	 */
	public abstract String createResults(SpatialCutter spatialEventCutter, int scaleFactor);

	@Override
	public boolean isPersonToConsider(Id<Person> personId) {
		return (!EXCLUDEPT || !personId.toString().contains(TransportMode.pt))
				&& (!EXCLUDEFREIGHT || !personId.toString().contains("freight"))
				&& (!EXCLUDEAV || !personId.toString().contains("av"))
				&& personHomeInArea(personId);
	}

	private boolean personHomeInArea(Id<Person> personId) {
		Person person = this.population.getPersons().get(personId);
		for (PlanElement planElement : person.getSelectedPlan().getPlanElements()) {
			if (planElement instanceof Activity) {
				Activity activity = (Activity) planElement;
				if (activity.getType().equals("home_1")) {
					Coord homeCoord = activity.getCoord();
					return coordAnalyzer.isCoordAffected(homeCoord);
				}
			}
		}
		return false;
	}
}
