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

package ch.ethz.matsim.boescpa.diss.analysis.populationAnalysis;

import ch.ethz.matsim.boescpa.lib.tools.coordUtils.CoordAnalyzer;
import ch.ethz.matsim.boescpa.lib.tools.utils.SHPFileUtils;
import com.vividsolutions.jts.geom.Geometry;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.opengis.feature.simple.SimpleFeature;

import java.util.HashSet;
import java.util.Set;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
class Utils {

	static CoordAnalyzer getCoordAnalyzer(String path2HomesSHP) {
		Set<SimpleFeature> features = new HashSet<>();
		SHPFileUtils util = new SHPFileUtils();
		features.addAll(ShapeFileReader.getAllFeatures(path2HomesSHP));
		Geometry area = util.mergeGeometries(features);
		return new CoordAnalyzer(area);
	}

	static boolean hasHomeInArea(Person person, CoordAnalyzer coordAnalyzer) {
		Activity homeAct = (Activity) person.getSelectedPlan().getPlanElements().get(0);
		return coordAnalyzer.isCoordAffected(homeAct.getCoord());
	}

}
