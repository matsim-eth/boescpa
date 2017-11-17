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

import ch.ethz.matsim.boescpa.lib.obj.BoxedHashMap;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.utils.collections.Tuple;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class AccessibilitiesCalculatorConfig extends ReflectiveConfigGroup {
	final static String ACCESSIBILITY_CALCULATOR = "accessibilityCalculator";

	final static String DELIMITER = ",";

	final static String OPPORTUNITY_ACTIVITIES = "opportunityActivities";
	final static String MODES_TO_EVALUATE = "modesToEvaluate";
	final static String MODES_TO_EVALUATE_DETERRENCE_FUNCTION_BETAS = "modesToEvaluateDeterrenceFunctionBetas";
	final static String MIN_OBSERVATIONS_TO_BE_CONSIDERED = "minObservationsForODPairToBeConsidered";
	final static String GRID_CELL_SIZE = "gridCellSize"; // in meters

	private String opportunityActivities = "work";
	private String modesToEvaluate = "car, pt, av";
	private String modesToEvaluateDeterrenceFunctionBetas = "0.2613, 0.0344, 0.0344";
	private int minObservationsToBeConsidered = 1;
	// source betas (assumed same for av as for pt):
	/*
	Axhausen, K.W., T. Bischof, R. Fuhrer, R. Neuenschwander, G. Sarlas, und P. Walker (2015):
	Gesamtwirtschaftliche Effekte des öffentlichen Verkehrs mit besonderer Berücksichtigung der Verdichtungs-
	und Agglomerationseffekte, Schlussbericht, SBB Fonds für Forschung, Bern und Zürich.
	 */
	private int gridCellSize = 100; // in meters
	// Peak hours for departures to count as peak trips.
	private Tuple<Double, Double> morningPeak = new Tuple<>(6.0*3600, 8.0*3600);
	private Tuple<Double, Double> eveningPeak = new Tuple<>(16.5*3600, 18.5*3600);

	public AccessibilitiesCalculatorConfig() {
		super(ACCESSIBILITY_CALCULATOR);
	}

	@StringSetter(OPPORTUNITY_ACTIVITIES)
	public void setOpportunityActivities(String opportunityActivities) {
		this.opportunityActivities = opportunityActivities;
	}

	@StringGetter(OPPORTUNITY_ACTIVITIES)
	public String[] getOpportunityActivities() {
		return opportunityActivities.replaceAll("\\s","").split(DELIMITER);
	}

	@StringSetter(MODES_TO_EVALUATE)
	public void setModesToEvaluate(String modesToEvaluate) {
		this.modesToEvaluate = modesToEvaluate;
	}

	@StringGetter(MODES_TO_EVALUATE)
	public List<String> getModesToEvaluate() {
		return Arrays.asList(modesToEvaluate.replaceAll("\\s","").split(DELIMITER));
	}

	@StringSetter(MODES_TO_EVALUATE_DETERRENCE_FUNCTION_BETAS)
	public void setModesToEvaluateDeterrenceFunctionBetas(String modesToEvaluateDeterrenceFunctionBetas) {
		this.modesToEvaluateDeterrenceFunctionBetas = modesToEvaluateDeterrenceFunctionBetas;
	}

	@StringGetter(MODES_TO_EVALUATE_DETERRENCE_FUNCTION_BETAS)
	public List<Double> getModesToEvaluateDeterrenceFunctionBetas() {
		String[] betasStrings = modesToEvaluateDeterrenceFunctionBetas.split(DELIMITER);
		List<Double> betas = new ArrayList<>(betasStrings.length);
		for (int i = 0; i < betasStrings.length; i++) {
			betas.add(Double.parseDouble(betasStrings[i].trim()));
		}
		return betas;
	}

	@StringSetter(MIN_OBSERVATIONS_TO_BE_CONSIDERED)
	public void setMinObservationsToBeConsidered(int minObservationsToBeConsidered) {
		this.minObservationsToBeConsidered = minObservationsToBeConsidered;
	}

	@StringGetter(MIN_OBSERVATIONS_TO_BE_CONSIDERED)
	public int getMinObservationsToBeConsidered() {
		return minObservationsToBeConsidered;
	}

	@StringSetter(GRID_CELL_SIZE)
	public void setGridCellSize(int sizeInMeters) {
		this.gridCellSize = sizeInMeters;
	}

	@StringGetter(GRID_CELL_SIZE)
	public int getGridCellSize() {
		return gridCellSize;
	}

	public Tuple<Double, Double> getMorningPeak() {
		return morningPeak;
	}

	public Tuple<Double, Double> getEveningPeak() {
		return eveningPeak;
	}
}
