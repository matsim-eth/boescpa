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

package ch.ethz.matsim.boescpa.diss.analysis.eventHandlers.targetFunctionUtils;

import ch.ethz.matsim.boescpa.lib.tools.coordUtils.CoordAnalyzer;
import ch.ethz.matsim.boescpa.lib.tools.utils.FacilityUtils;
import ch.ethz.matsim.boescpa.lib.tools.utils.PopulationUtils;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;

import static java.util.Objects.isNull;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class AccessibilityClusters {

	private static final double GRID_CELL_SIZE = 100;
	private static final double MIN_REL_SIZE = 5;

	private final ActivityFacilities facilities;
	private final CoordAnalyzer coordAnalyzer;
	private final Population population;
	private Map<String, Set<ActivityFacility>> clusters;
	private Map<Id<ActivityFacility>, String> facilityClusters;
	private Map<Tuple<String, String>, Integer> relations;
	private Map<String, Map<String, Integer>> clusterRelations;

	private int facilityCount = 0;
	private int relationCount = 0;

	//todo-boescpa: Möglichkeit für mehr und kleinere Cluster: Nimm an, dass Symmetrische TT-OD-Matrix -> fasse jeweils from-to und to-from zusammen als eine Relationship -> doppelt so viele Beobachtungen pro Relationship.

	public static void main(final String[] args) throws IOException {
		String path2Facilities = args[0];
		String path2Pop = args[1];
		String path2SHP = args[2];
		String outputPath = args[3];

		AccessibilityClusters accessibilityClusters = new AccessibilityClusters(path2Facilities, path2Pop, path2SHP);
		accessibilityClusters.testClass();

		accessibilityClusters.optimizeClusters();
		accessibilityClusters.testClass();

		accessibilityClusters.outputAccessibilityClusters(outputPath);
		accessibilityClusters.outputFacilityAttributes(outputPath + "/facilities.xml.gz");
	}

	private void outputAccessibilityClusters(String outputPath) throws IOException {
		BufferedWriter writer = IOUtils.getBufferedWriter(outputPath + "/accessibilityClusters.csv");
		String header = "facilityId;clusterId;facilityCoordX;facilityCoordY";
		writer.write(header); writer.newLine();
		for (Id<ActivityFacility> facilityId : facilityClusters.keySet()) {
			writer.write(facilityId.toString() + ";" + facilityClusters.get(facilityId) + ";" +
							facilities.getFacilities().get(facilityId).getCoord().getX() + ";" +
							facilities.getFacilities().get(facilityId).getCoord().getY());
			writer.newLine();
		}
		writer.close();
	}

	private void testClass() {
		System.out.println("\nNumber of initial clusters: " + clusters.keySet().size());
		System.out.println("Average number of facilities per cluster: " +
				clusters.values().stream().mapToDouble(Set::size).average().orElse(0.));
		System.out.println("Total number of facilities in area: " + facilityCount);

		System.out.println("\nNumber of relations: " + relations.keySet().size());
		System.out.println("Average size of relation: " +
				relations.values().stream().mapToDouble(a->a).average().orElse(0.));
		System.out.println("Min size of relation: " +
				relations.values().stream().mapToDouble(a->a).min().orElse(-1));
		System.out.println("Number of cluster relations: " +
						clusterRelations.values().stream().mapToDouble(Map::size).sum());
		System.out.println("Total number of relations: " + relationCount);
	}

	private AccessibilityClusters(String path2Facilities, String path2Pop, String path2SHP) {
		this.coordAnalyzer = CoordAnalyzer.getCoordAnalyzer(path2SHP);
		this.facilities = FacilityUtils.readFacilities(path2Facilities);
		initializeClusters();
		this.population = PopulationUtils.readPopulation(path2Pop);
		initializeRelations();
	}

	private void initializeRelations() {
		this.relations = new LinkedHashMap<>();
		for (String fromCluster : clusters.keySet()) {
			for (String toCluster : clusters.keySet()) {
				relations.put(new Tuple<>(fromCluster, toCluster), 0);
			}
		}
		this.clusterRelations = new LinkedHashMap<>();
		for (String cluster : clusters.keySet()) {
			clusterRelations.put(cluster, new LinkedHashMap<>());
		}
		for (Person person : population.getPersons().values()) {
			Plan p = person.getSelectedPlan();
			Activity fromAct = null;
			for (PlanElement pe : p.getPlanElements()) {
				if (pe instanceof Activity) {
					if (!isNull(fromAct)) {
						addTrip(fromAct, (Activity)pe);
					}
					fromAct = (Activity)pe;
				}
			}
		}
	}

	private void addTrip(Activity fromAct, Activity toAct) {
		String fromCluster = facilityClusters.get(fromAct.getFacilityId());
		String toCluster = facilityClusters.get(toAct.getFacilityId());
		if (!isNull(fromCluster) && !isNull(toCluster)) {
			relationCount++;
			Tuple<String, String> relation = new Tuple<>(fromCluster, toCluster);
			int numberOfRelations = relations.get(relation);
			relations.put(relation, ++numberOfRelations);
			Map<String, Integer> fromClusterSet = clusterRelations.get(fromCluster);
			fromClusterSet.put(toCluster, numberOfRelations);
			clusterRelations.put(fromCluster, fromClusterSet);
		}
	}

	private void initializeClusters() {
		this.clusters = new LinkedHashMap<>();
		this.facilityClusters = new LinkedHashMap<>();
		for (ActivityFacility facility : facilities.getFacilities().values()) {
			if (!coordAnalyzer.isFacilityAffected(facility)) continue;
			facilityCount++;
			String facilityClusterKey = getClusterKey(facility);
			this.facilityClusters.put(facility.getId(), facilityClusterKey);
			Set<ActivityFacility> clusterZ = this.clusters.getOrDefault(facilityClusterKey, new LinkedHashSet<>());
			clusterZ.add(facility);
			clusters.put(facilityClusterKey, clusterZ);
		}
	}

	static private String getClusterKey(ActivityFacility facility) {
		return String.valueOf(GRID_CELL_SIZE * Math.floor(facility.getCoord().getX()/ GRID_CELL_SIZE)) + "_" +
				String.valueOf(GRID_CELL_SIZE * Math.floor(facility.getCoord().getY()/ GRID_CELL_SIZE));
	}

	//****************************************************************************************************************

	private void optimizeClusters() {
		double minRel = relations.values().stream().mapToDouble(a -> a).min().orElse(-1);
		while (minRel < MIN_REL_SIZE) {
			if (minRel < 0) throw new RuntimeException("Minimum relation size could not be calculated. Please revise.");
			String smallestCluster = identifySmallestCluster(clusterRelations.keySet());
			String mergingNeighbour = findMergingNeighbour(smallestCluster);
			if (isNull(smallestCluster) || isNull(mergingNeighbour))
				throw new RuntimeException("No cluster (" + smallestCluster + ") or neighbour (" + mergingNeighbour + ") for merging.");
			mergeClusters(smallestCluster, mergingNeighbour);
			minRel = relations.values().stream().mapToDouble(a->a).min().orElse(-1);
			System.out.print(minRel + " - " + clusters.size() + "; ");
		}
	}

	private String identifySmallestCluster(Set<String> clusters) {
		double minClusterSize = Double.MAX_VALUE;
		String minCluster = null;
		for (String cluster : clusters) {
			if (clusterRelations.get(cluster).values().stream().mapToDouble(a->a).sum() < minClusterSize) {
				minCluster = cluster;
				minClusterSize = clusterRelations.get(cluster).values().stream().mapToDouble(a->a).sum();
			}
		}
		return minCluster;
	}

	private String findMergingNeighbour(String smallestCluster) {
		Set<String> neighbours = new LinkedHashSet<>();
		double minClusterDist = Double.MAX_VALUE;
		double scX = Double.parseDouble(smallestCluster.split("_")[0]);
		double scY = Double.parseDouble(smallestCluster.split("_")[1]);
		for (String cluster : clusters.keySet()) {
			if (cluster.equals(smallestCluster)) continue;
			double cX = Double.parseDouble(cluster.split("_")[0]);
			double cY = Double.parseDouble(cluster.split("_")[1]);
			double clusterDist = Math.sqrt(Math.pow(scX - cX,2) + Math.pow(scY - cY,2));
			if (clusterDist < minClusterDist) {
				neighbours.clear();
				neighbours.add(cluster);
				minClusterDist = clusterDist;
			} else if (clusterDist == minClusterDist) {
				neighbours.add(cluster);
			}
		}
		return identifySmallestCluster(neighbours);
	}

	private void mergeClusters(String smallestCluster, String mergingNeighbour) {
		String newClusterKey = getNewClusterKey(smallestCluster, mergingNeighbour);
		updateClusters(smallestCluster, mergingNeighbour, newClusterKey);
		updateFacilityClusters(smallestCluster, mergingNeighbour, newClusterKey);
		updateRelations(smallestCluster, mergingNeighbour, newClusterKey);
		updateClusterRelations(smallestCluster, mergingNeighbour, newClusterKey);
	}

	private void updateClusterRelations(String smallestCluster, String mergingNeighbour, String newClusterKey) {
		//private Map<String, Map<String, Integer>> clusterRelations;
		Map<String, Integer> newCluster = new LinkedHashMap<>();
		for (String cluster : clusterRelations.keySet()) {
			Map<String, Integer> oldCluster = clusterRelations.get(cluster);
			if (cluster.equals(smallestCluster) || cluster.equals(mergingNeighbour)) {
				for (String toCluster : oldCluster.keySet()) {
					int newVal = newCluster.getOrDefault(toCluster, 0);
					newVal += oldCluster.get(toCluster);
					newCluster.put(toCluster, newVal);
				}
			} else {
				int newVal = oldCluster.getOrDefault(smallestCluster, 0) + oldCluster.getOrDefault(mergingNeighbour, 0);
				if (newVal > 0) oldCluster.put(newClusterKey, newVal);
				oldCluster.remove(smallestCluster);
				oldCluster.remove(mergingNeighbour);
			}
		}
		clusterRelations.remove(smallestCluster);
		clusterRelations.remove(mergingNeighbour);
		clusterRelations.put(newClusterKey, newCluster);
	}

	private void updateRelations(String smallestCluster, String mergingNeighbour, String newClusterKey) {
		//private Map<Tuple<String, String>, Integer> relations;
		Map<Tuple<String, String>, Integer> relationsToPut = new LinkedHashMap<>();
		Set<Tuple<String, String>> relationsToRemove = new LinkedHashSet<>();
		for (Tuple<String, String> rel : relations.keySet()) {
			// first equals one of them, but second does not equal any of the two
			if ((rel.getFirst().equals(smallestCluster) || rel.getFirst().equals(mergingNeighbour))
					&& (!rel.getSecond().equals(smallestCluster) && !rel.getSecond().equals(mergingNeighbour))) {
				int anzRels = relationsToPut.getOrDefault(new Tuple<>(newClusterKey, rel.getSecond()), 0);
				anzRels += relations.get(rel);
				relationsToPut.put(new Tuple<>(newClusterKey, rel.getSecond()), anzRels);
				relationsToRemove.add(rel);
			}
			// first equals one of them, and second equals one of them
			else if ((rel.getFirst().equals(smallestCluster) || rel.getFirst().equals(mergingNeighbour))
					&& (rel.getSecond().equals(smallestCluster) || rel.getSecond().equals(mergingNeighbour))) {
				int anzRels = relationsToPut.getOrDefault(new Tuple<>(newClusterKey, newClusterKey), 0);
				anzRels += relations.get(rel);
				relationsToPut.put(new Tuple<>(newClusterKey, newClusterKey), anzRels);
				relationsToRemove.add(rel);
			}
			// first does not equal any of the two, but second equals one of them
			else if ((!rel.getFirst().equals(smallestCluster) && !rel.getFirst().equals(mergingNeighbour))
					&& (rel.getSecond().equals(smallestCluster) || rel.getSecond().equals(mergingNeighbour))) {
				int anzRels = relationsToPut.getOrDefault(new Tuple<>(rel.getFirst(), newClusterKey), 0);
				anzRels += relations.get(rel);
				relationsToPut.put(new Tuple<>(rel.getFirst(), newClusterKey), anzRels);
				relationsToRemove.add(rel);
			}
		}
		for (Tuple<String, String> relationToRemove : relationsToRemove) {
			relations.remove(relationToRemove);
		}
		for (Tuple<String, String> relationToPut : relationsToPut.keySet()) {
			relations.put(relationToPut, relationsToPut.get(relationToPut));
		}
	}

	private void updateFacilityClusters(String smallestCluster, String mergingNeighbour, String newClusterKey) {
		//private Map<Id<ActivityFacility>, String> facilityClusters;
		Set<Id<ActivityFacility>> facilitiesToChange = new LinkedHashSet<>();
		for (Id<ActivityFacility> facilityId : facilityClusters.keySet()) {
			if (facilityClusters.get(facilityId).equals(smallestCluster) ||
					facilityClusters.get(facilityId).equals(mergingNeighbour)) {
				facilitiesToChange.add(facilityId);
			}
		}
		for (Id<ActivityFacility> facilityId : facilitiesToChange) {
			facilityClusters.put(facilityId, newClusterKey);
		}
	}

	private void updateClusters(String smallestCluster, String mergingNeighbour, String newClusterKey) {
		//private Map<String, Set<ActivityFacility>> clusters;
		Set<ActivityFacility> newSet = clusters.remove(smallestCluster);
		newSet.addAll(clusters.remove(mergingNeighbour));
		clusters.put(newClusterKey, newSet);
	}

	private String getNewClusterKey(String smallestCluster, String mergingNeighbour) {
		double scX = Double.parseDouble(smallestCluster.split("_")[0]);
		double scY = Double.parseDouble(smallestCluster.split("_")[1]);
		double sizeSC = clusterRelations.get(smallestCluster).values().stream().mapToDouble(a->a).sum();
		double mnX = Double.parseDouble(mergingNeighbour.split("_")[0]);
		double mnY = Double.parseDouble(mergingNeighbour.split("_")[1]);
		double sizeMN = clusterRelations.get(mergingNeighbour).values().stream().mapToDouble(a->a).sum();
		double newX = scX + 0.5*(mnX - scX);
		double newY = scY + 0.5*(mnY - scY);
		if (sizeSC > 0 || sizeMN > 0) {
			newX = scX + ((mnX - scX) * (sizeMN / (sizeSC + sizeMN)));
			newY = scY + ((mnY - scY) * (sizeMN / (sizeSC + sizeMN)));
		}
		return String.valueOf(newX) + "_" + String.valueOf(newY);
	}

	// ***************************************************************************************************************

	private void outputFacilityAttributes(String path2Facilities) {
		for (Id<ActivityFacility> facilityId : facilityClusters.keySet()) {
			facilities.getFacilityAttributes().putAttribute(facilityId.toString(),
					"accessibility_cluster", facilityClusters.get(facilityId));
		}
		ObjectAttributesXmlWriter attributesWriter = new ObjectAttributesXmlWriter(facilities.getFacilityAttributes());
		attributesWriter.writeFile(path2Facilities.substring(0, path2Facilities.indexOf(".xml")) + "_accessibilityClusters.xml.gz");
	}
}
