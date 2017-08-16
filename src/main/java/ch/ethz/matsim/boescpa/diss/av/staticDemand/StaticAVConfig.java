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

package ch.ethz.matsim.boescpa.diss.av.staticDemand;

import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;

import java.util.HashSet;
import java.util.Set;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class StaticAVConfig extends	ReflectiveConfigGroup {
	final static String NAME = "staticAV";

	final static String BOARDING_TIME = "boardingTime";
	final static String UNBOARDING_TIME = "unboardingTime";
	final static String OPERATORS = "operators";

	final private Set<AVOperatorConfig> operators = new HashSet<>();
	private double boardingTime;
	private double unboardingTime;

	public StaticAVConfig() {
		super(NAME);
	}

	@StringGetter(OPERATORS)
	public Set<AVOperatorConfig> getOperatorConfigs() {
		return operators;
	}

	@StringSetter(OPERATORS)
	public AVOperatorConfig createOperatorConfig(String id) {
		AVOperatorConfig oc = new AVOperatorConfig(id);
		operators.add(oc);
		return oc;
	}

	@StringSetter(BOARDING_TIME)
	public void setBoardingTime(String boardingTime) {
		this.boardingTime = Double.parseDouble(boardingTime);
	}

	@StringGetter(BOARDING_TIME)
	public double getBoardingTime() {
		return boardingTime;
	}

	@StringSetter(UNBOARDING_TIME)
	public void setUnboardingTime(String unboardingTime) {
		this.unboardingTime = Double.parseDouble(unboardingTime);
	}

	@StringGetter(UNBOARDING_TIME)
	public double getUnboardingTime() {
		return unboardingTime;
	}

	//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

	public class AVOperatorConfig extends ReflectiveConfigGroup {
		final static String NAME = "operator";

		final static String OPERATOR_ID = "operatorID";
		final static String AV_ASSIGNMENT = "avAssignment";
		final static String LEVEL_OF_SERVICE = "levelOfService";

		private final String operatorId;
		private double levelOfService = 5*60.0;
		private String avAssignment = "closestWithinRange";

		public AVOperatorConfig(String operatorId) {
			super(NAME);
			this.operatorId = operatorId;
		}

		@StringGetter(OPERATOR_ID)
		public String getOperatorId() {
			return operatorId;
		}

		@StringSetter(AV_ASSIGNMENT)
		public void setLevelofservice(String levelofservice) {
			this.levelOfService = Double.parseDouble(levelofservice);
		}

		@StringGetter(LEVEL_OF_SERVICE)
		public double getLevelOfService() {
			return levelOfService;
		}

		@StringSetter(AV_ASSIGNMENT)
		public void setAVAssignment(String avAssignment) {
			this.avAssignment = avAssignment;
		}

		@StringGetter(AV_ASSIGNMENT)
		public AVAssignment getAVAssignment(TravelTimeCalculator travelTimeCalculator) {
			// as at the moment we have only one, we set this one...
			// later (after ride-sharing implementation) decide based on string...
			return new AVAssignment(travelTimeCalculator);
		}
	}
}
