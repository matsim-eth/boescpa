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

package ch.ethz.matsim.boescpa.diss.baseline.scoring;

import org.matsim.core.config.ReflectiveConfigGroup;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class IndividualVOTConfig extends ReflectiveConfigGroup {
	public final static String NAME = "IndividualVOTConfig";

	final static String VOT_ELASTICITY = "votElasticity";
	final static String REFERENCE_INCOME = "referenceHouseholdIncome";

	private double votElasticity = 0.0; // neutralizes the current implementation of individualVOT
	private double referenceHouseholdIncome = 5000.0;

	public IndividualVOTConfig() {
		super(NAME);
	}

	@StringGetter(VOT_ELASTICITY)
	public double getVotElasticity() {
		return votElasticity;
	}

	@StringSetter(VOT_ELASTICITY)
	public void setVotElasticity(String votElasticity) {
		this.votElasticity = Double.parseDouble(votElasticity);
	}

	@StringGetter(REFERENCE_INCOME)
	public double getReferenceHouseholdIncome() {
		return referenceHouseholdIncome;
	}

	@StringSetter(REFERENCE_INCOME)
	public void setReferenceHouseholdIncome(String referenceHouseholdIncome) {
		this.referenceHouseholdIncome = Double.parseDouble(referenceHouseholdIncome);
	}
}
