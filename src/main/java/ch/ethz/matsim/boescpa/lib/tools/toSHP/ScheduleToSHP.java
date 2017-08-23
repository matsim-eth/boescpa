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

package ch.ethz.matsim.boescpa.lib.tools.toSHP;

import ch.ethz.matsim.boescpa.lib.tools.utils.NetworkUtils;
import ch.ethz.matsim.boescpa.lib.tools.utils.TransitScheduleUtils;
import com.vividsolutions.jts.geom.Coordinate;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.PointFeatureFactory;
import org.matsim.core.utils.gis.PolylineFeatureFactory;
import org.matsim.core.utils.gis.ShapeFileWriter;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class ScheduleToSHP {

	public static void main(String[] args) throws Exception {
		final String pathToNetwork = args[0];
		final String pathToSchedule = args[1];
		final String pathToOutputFolder = args[2] + File.separator;
		final String coordinateSystem = args.length > 3 ? args[3] : "EPSG:2056"; // EPSG-Code for Swiss CH1903_LV03+

		// load inputs
		Network network = NetworkUtils.readNetwork(pathToNetwork);
		TransitSchedule schedule = TransitScheduleUtils.readSchedule(pathToSchedule);

		// transform transit lines (based on first route of line)
		CoordinateReferenceSystem crs = MGC.getCRS(coordinateSystem);
		Collection<SimpleFeature> features = new ArrayList<>();
		PolylineFeatureFactory lineFactory = new PolylineFeatureFactory.Builder().
				setCrs(crs).
				setName("transitLine").
				addAttribute("ID", String.class).
				addAttribute("name", String.class).
				addAttribute("mode", String.class).
				create();
		for (TransitLine transitLine : schedule.getTransitLines().values()) {
			List<Coordinate> linePath = new LinkedList<>();
			TransitRoute route = getLongestRoute(transitLine);
			for (Id<Link> linkId : route.getRoute().getLinkIds()) {
				if (linePath.isEmpty()) {
					Coord nodeCoord = network.getLinks().get(linkId).getFromNode().getCoord();
					Coordinate firstFromNoteCoordinate = new Coordinate(nodeCoord.getX(), nodeCoord.getY());
					linePath.add(firstFromNoteCoordinate);
				}
				Coord linkMidCoord = network.getLinks().get(linkId).getCoord();
				Coordinate linkMidCoordinate = new Coordinate(linkMidCoord.getX(), linkMidCoord.getY());
				linePath.add(linkMidCoordinate);
				Coord linkEndCoord = network.getLinks().get(linkId).getToNode().getCoord();
				Coordinate linkEndCoordinate = new Coordinate(linkEndCoord.getX(), linkEndCoord.getY());
				linePath.add(linkEndCoordinate);
			}
			SimpleFeature ft = lineFactory.createPolyline(
					linePath.toArray(new Coordinate[linePath.size()]),
					new Object[]{transitLine.getId().toString(),
							transitLine.getName(),
							route.getTransportMode()
					}, null);
			features.add(ft);
		}
		ShapeFileWriter.writeGeometries(features, pathToOutputFolder + "transit_lines.shp");

		// transform transit stops (based on first route of line)
		features = new ArrayList<>();
		PointFeatureFactory nodeFactory = new PointFeatureFactory.Builder().
				setCrs(crs).
				setName("transitStops").
				addAttribute("ID", String.class).
				addAttribute("name", String.class).
				create();
		for (TransitLine transitLine : schedule.getTransitLines().values()) {
			TransitRoute route = getLongestRoute(transitLine);
			for (TransitRouteStop stop : route.getStops()) {
				SimpleFeature ft = nodeFactory.createPoint(
						stop.getStopFacility().getCoord(),
						new Object[]{stop.getStopFacility().getId().toString(),
								stop.getStopFacility().getName(),
						}, null);
				features.add(ft);
			}
		}
		ShapeFileWriter.writeGeometries(features, pathToOutputFolder + "transit_stops.shp");
	}

	private static TransitRoute getLongestRoute(TransitLine transitLine) {
		TransitRoute longestRoute = null;
		int longestRouteLength = 0;
		for (TransitRoute route : transitLine.getRoutes().values()) {
			int length = route.getRoute().getLinkIds().size();
			if (length > longestRouteLength) {
				longestRoute = route;
				longestRouteLength = length;
			}
		}
		return longestRoute;
	}
}
