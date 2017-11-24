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

package ch.ethz.matsim.boescpa.diss.analysis.eventHandlers.targetFunctionUtils.accessibility;

import ch.ethz.matsim.boescpa.lib.tools.coordUtils.CoordAnalyzer;
import ch.ethz.matsim.boescpa.lib.tools.utils.FacilityUtils;
import ch.ethz.matsim.boescpa.lib.tools.utils.NetworkUtils;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class AccessibilityCalculator {
	private final static double GridCellSize = 300; // meters
	private final static String[] OpportunityActivities = {"work","home","leisure","shop","education"};
	private final static String[] AnalyzedModes = {"car","pt","av","sm"};
	private final static double[] ModeDeterrenceBetas = {0.2613, 0.2613, 0.2613, 0.2613};
	// source betas:
	/*
	Axhausen, K.W., T. Bischof, R. Fuhrer, R. Neuenschwander, G. Sarlas, und P. Walker (2015):
	Gesamtwirtschaftliche Effekte des öffentlichen Verkehrs mit besonderer Berücksichtigung der Verdichtungs-
	und Agglomerationseffekte, Schlussbericht, SBB Fonds für Forschung, Bern und Zürich.
	********************************************************************************************************
	Personal communication G. Sarlas, 24.11.2017: Best would be to estimate new betas. Next best:
		- Beta for car (0.2613) was estimated based on travel times
		- Beta for pt (0.0344) was estimated based on generalized costs
			=> Use car-beta for all modes if accessibility calculation based on travel times,
				use pt-beta for all modes if accessibility calculation based on generalized costs.
	 */
	// todo-boescpa: Estimate parameters for mode based on observed travel times as part of accessibility calculation
	private final static double[] DayTimes = {7.5*3600, 10*3600, 3*3600}; // peak, offpeak, night

	private final Network network;
	private final Map<String, Double> opportunities;
	private final CoordAnalyzer coordAnalyzer;
	private final AccessiblityRouter router;

	private AccessibilityCalculator(Network network, ActivityFacilities activityFacilities, String pathToSHP,
									AccessiblityRouter accessiblityRouter) {
		this.network = network;
		this.coordAnalyzer = CoordAnalyzer.getCoordAnalyzer(pathToSHP);
		this.opportunities = calculateOpportunities(activityFacilities);
		this.router = accessiblityRouter;
	}

	public static void main(final String[] args) throws IOException {
		String path2Config = args[0];
		String path2Events = args[1];
		String path2Network = args[2];
		String path2Facilities = args[3];
		String path2Schedule = args[4];
		String path2SHP = args[5];
		String outputPath = args[6];

		Config config = ConfigUtils.loadConfig(path2Config);
		config.network().setInputFile(path2Network);
		config.facilities().setInputFile(path2Facilities);
		config.transit().setTransitScheduleFile(path2Schedule);

		Network network = NetworkUtils.readNetwork(path2Network);
		ActivityFacilities facilities = FacilityUtils.readFacilities(path2Facilities);
		AccessiblityRouter router = new AccessiblityRouter(config, path2Events, path2SHP);
		AccessibilityCalculator calculator = new AccessibilityCalculator(network, facilities, path2SHP, router);

		Map<String,Map<String, Map<String, Double>>> totalAccessibilities = calculator.calculateTotalAccessibilities();
		Map<String, Map<String, Double>> averageAccessibilities =
				calculator.calculateOpportunityWeightedAverageAccessibilities(totalAccessibilities);
		calculator.writeAverageAccessibilities(averageAccessibilities, outputPath);
	}

	private void writeAverageAccessibilities(Map<String, Map<String, Double>> averageAccessibilities, String outputPath) throws IOException {
		BufferedWriter writer = IOUtils.getBufferedWriter(outputPath);
		writer.write("daytime;mode;averageAccessibility");
		writer.newLine();
		for (String daytime : averageAccessibilities.keySet()) {
			for (String mode : AnalyzedModes) {
				writer.write(daytime + ";" + mode + ";" + String.valueOf(averageAccessibilities.get(daytime).get(mode)));
				writer.newLine();
			}
		}
		writer.flush();
		writer.close();
	}

	// meaning of the strings:
	//  1. daytime (peak, offpeak, night)
	//  2. mode (AnalyzedModes)
	private Map<String, Map<String, Double>> calculateOpportunityWeightedAverageAccessibilities(
			Map<String,Map<String, Map<String, Double>>> totalAccessibilities) {
		Map<String, Map<String, Double>> averageAccessibilities = new HashMap<>();
		for (String daytime : totalAccessibilities.keySet()) {
			for (String mode : AnalyzedModes) {
				double totalAccessibility = 0;
				double numberOfOpportunities = 0;
				for (String zone : opportunities.keySet()) {
					totalAccessibility += totalAccessibilities.get(daytime).get(mode).get(zone) * opportunities.get(zone);
					numberOfOpportunities += opportunities.get(zone);
				}
				Map<String, Double> modeAccessibilities = averageAccessibilities.getOrDefault(daytime, new HashMap<>());
				modeAccessibilities.put(mode, totalAccessibility/numberOfOpportunities);
				averageAccessibilities.put(daytime, modeAccessibilities);
			}
		}
		return averageAccessibilities;
	}

	// meaning of the strings:
	//  1. daytime (peak, offpeak, night)
	//  2. mode (AnalyzedModes)
	//  3. zone
	private Map<String,Map<String, Map<String, Double>>> calculateTotalAccessibilities() {
		Map<String, Map<String, Map<String, Double>>> accessibilitiesTotal = createAccessibilitiesTree();
		double zoneCounter = 0;
		double zoneCounterTrigger = 0;
		for (String fromZone : opportunities.keySet()) {
			zoneCounter++;
			if ((zoneCounter/opportunities.keySet().size()) > zoneCounterTrigger) {
				System.out.println("Zones calculated: " + (int)(100*zoneCounterTrigger) + "%");
				zoneCounterTrigger += 0.1;
			}
			for (String toZone : opportunities.keySet()) {
				if (fromZone.equals(toZone)) continue;
				for (double dayTime : DayTimes) {
					Map<String, Double> travelTimes = router.getCoordToCoordTravelTime(
							getCentroidCoord(fromZone), getCentroidCoord(toZone), dayTime);
					for (int i = 0; i < AnalyzedModes.length; i++) {
						String mode = AnalyzedModes[i];
						// get correct accessibilities container:
						Map<String, Double> accessibilities;
						if ((7*3600 <= dayTime && dayTime < 8*3600) || (17*3600 <= dayTime && dayTime < 18*3600)) {
							accessibilities = accessibilitiesTotal.get("peak").get(mode);
						} else if (8*3600 <= dayTime && dayTime < 17*3600) {
							accessibilities = accessibilitiesTotal.get("offpeak").get(mode);
						} else {
							accessibilities = accessibilitiesTotal.get("night").get(mode);
						}
						// get correct travel time and mode beta:
						double modeBeta = ModeDeterrenceBetas[i];
						double travelTime = travelTimes.get(mode);
						// calculate accessibilities:
							// We get the accessibility already stored for this origin from other od-pairs and
							// if nothing is stored (a.k.a. we look at this origin for the first time,
							// we take its self-accessibility, which is all its own opportunities.
						double accessibility = accessibilities.getOrDefault(fromZone, opportunities.get(fromZone));
							// We calculate the accessibility and add it to the total accessibility of this origin.
						// todo-boescpa: Wechsel von travel time zu generalisierten Kosten, d.h. "modeConstant + travelTime * VOT_mode + travelDistance * CostPerKM_mode"
						accessibility += opportunities.get(toZone) * Math.exp(-modeBeta * (travelTime/60));
						accessibilities.put(fromZone, accessibility);
					}
				}
			}
		}
		return accessibilitiesTotal;
	}

	private Map<String, Map<String, Map<String, Double>>> createAccessibilitiesTree() {
		Map<String, Map<String, Map<String, Double>>> accessibilitiesTree = new HashMap<>();
		accessibilitiesTree.put("peak", new HashMap<>());
		accessibilitiesTree.put("offpeak", new HashMap<>());
		accessibilitiesTree.put("night", new HashMap<>());
		for (String daytime : accessibilitiesTree.keySet()) {
			for (String mode : AnalyzedModes) {
				accessibilitiesTree.get(daytime).put(mode, new HashMap<>());
			}
		}
		return accessibilitiesTree;
	}


	/**
	 * Calculates opportunities for each grid-cell based on opportunities specified in config.
	 */
	private Map<String, Double> calculateOpportunities(ActivityFacilities facilities) {
		Map<String, Double> opportunities = new HashMap<>();
		for (ActivityFacility facility : facilities.getFacilities().values()) {
			if (!coordAnalyzer.isFacilityAffected(facility)) continue;
			String gridCell = getGridCell(facility.getLinkId());
			double opportunity = opportunities.getOrDefault(gridCell, 0.);
			for (String opportunityType : OpportunityActivities) {
				if (facility.getActivityOptions().containsKey(opportunityType)) {
					opportunity += facility.getActivityOptions().get(opportunityType).getCapacity();
				}
			}
			opportunities.put(gridCell, opportunity);
		}
		return opportunities;
	}

	private String getGridCell(Id<Link> linkId) {
		Node fromNode = this.network.getLinks().get(linkId).getFromNode();
		// floor x-coord to next grid cell size
		long x = (long)(fromNode.getCoord().getX()/GridCellSize);
		// floor y-coord to next grid cell size
		long y = (long)(fromNode.getCoord().getY()/GridCellSize);
		return x + "_" + y;
	}

	private Coord getCentroidCoord(String gridCell) {
		String[] stringCoords = gridCell.split("_");
		double x = (Double.parseDouble(stringCoords[0]) + 0.5) * GridCellSize;
		double y = (Double.parseDouble(stringCoords[1]) + 0.5) * GridCellSize;
		return new Coord(x, y);
	}

}
