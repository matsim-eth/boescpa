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

import ch.ethz.matsim.boescpa.lib.tools.coordUtils.CoordAnalyzer;
import ch.ethz.matsim.boescpa.lib.tools.utils.NetworkUtils;
import ch.ethz.matsim.boescpa.lib.tools.utils.SHPFileUtils;
import com.vividsolutions.jts.geom.Geometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.io.IOUtils;
import org.opengis.feature.simple.SimpleFeature;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class CountCutter {

	private final CoordAnalyzer coordAnalyzer;
	private final Network network;

	private final Map<Id<Link>, String> inputCounts = new LinkedHashMap<>();
	private final Map<Id<Link>, String> outputCounts = new LinkedHashMap<>();

	private CountCutter(String pathToNetwork, String pathToSHPOfCutArea) {
		this.network = NetworkUtils.readNetwork(pathToNetwork);
		// Set up cut area from SHP-file.
		Set<SimpleFeature> features = new HashSet<>();
		SHPFileUtils util = new SHPFileUtils();
		features.addAll(ShapeFileReader.getAllFeatures(pathToSHPOfCutArea));
		Geometry area = util.mergeGeometries(features);
		this.coordAnalyzer = new CoordAnalyzer(area);
	}

	public static void main(final String[] args) throws IOException {
		CountCutter countCutter = new CountCutter(args[0], args[1]);
		countCutter.readCounts(args[2]);
		countCutter.filterCounts();
		countCutter.writeCounts(args[3]);
	}

	private void writeCounts(String outputCountPath) throws IOException {
		if (outputCounts.size() > 0) {
			BufferedWriter writer = IOUtils.getBufferedWriter(outputCountPath);
			for (String line : outputCounts.values()) {
				writer.write(line);
				writer.newLine();
			}
			writer.close();
		} else {
			System.out.println("No output counts found.");
		}
	}

	private void filterCounts() {
		for (Id<Link> linkId : inputCounts.keySet()) {
			if (network.getLinks().containsKey(linkId)
					&& coordAnalyzer.isLinkAffected(network.getLinks().get(linkId))) {
				outputCounts.put(linkId, inputCounts.get(linkId));
			}
		}
	}

	private void readCounts(String inputCountPath) throws IOException {
		BufferedReader reader = IOUtils.getBufferedReader(inputCountPath);
		reader.readLine(); // header
		String line = reader.readLine();
		while (line != null) {
			if (line.length() > 0) {
				String[] lineElements = line.split(";");
				inputCounts.put(Id.createLinkId(lineElements[2]), line);
			}
			line = reader.readLine();
		}
		reader.close();
	}

}
