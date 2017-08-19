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

import org.matsim.core.api.internal.MatsimParameters;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class StaticAVConfig extends	ReflectiveConfigGroup {
	final static String NAME = "staticAV";

	final static String STATS_INTERVAL = "statsInterval";
	final static String BOARDING_TIME = "boardingDuration";
	final static String UNBOARDING_TIME = "unboardingDuration";
	final static String OPERATOR = "operator";
	final static String BOUNDING_BOX = "boundingBox";

	final private Set<AVOperatorConfig> operators = new HashSet<>();
	private double boardingTime;
	private double unboardingTime;
	private int statsInterval = 5*60;
	private double[] boundingBox = null;

	public StaticAVConfig() {
		super(NAME);
	}

	public Set<AVOperatorConfig> getOperatorConfigs() {
		return operators;
	}

	@Override
	public void addParameterSet(final ConfigGroup set) {
		switch (set.getName()) {
			case OPERATOR:
				addOperator((StaticAVConfig.AVOperatorConfig) set);
				break;
			default:
				throw new IllegalArgumentException(set.getName());
		}
	}

	@Override
	public ConfigGroup createParameterSet(final String type) {
		switch (type) {
			case OPERATOR:
				return new AVOperatorConfig();
			default:
				throw new IllegalArgumentException(type);
		}
	}

	@Override
	protected void checkParameterSet(final ConfigGroup module) {
		switch (module.getName()) {
			case OPERATOR:
				break;
			default:
				throw new IllegalArgumentException( module.getName() );
		}
	}

	private void addOperator(StaticAVConfig.AVOperatorConfig operatorConfig) {
		operators.add(operatorConfig);
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

	@StringSetter(STATS_INTERVAL)
	public void setStatsInterval(String statsInterval) {
		this.statsInterval = Integer.parseInt(statsInterval);
	}

	@StringGetter(STATS_INTERVAL)
	public int getStatsInterval() {
		return statsInterval;
	}

	@StringSetter(BOUNDING_BOX)
	public void setBoundingBox(String boundingBox) {
		// minx, miny, maxx, maxy
		this.boundingBox = Arrays.stream(boundingBox.split(","))
				.mapToDouble(Double::parseDouble)
				.toArray();
	}

	@StringGetter(BOUNDING_BOX)
	public double[] getBoundingBox() {
		return boundingBox;
	}

	//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

	public static class AVOperatorConfig extends ReflectiveConfigGroup implements MatsimParameters {
		final static String NAME = "operator";

		final static String OPERATOR_ID = "id";
		final static String AV_ASSIGNMENT = "avAssignment";
		final static String LEVEL_OF_SERVICE = "levelOfService";
		final static String WAITING_TIME_UNMET = "waitingTimeUnmet";

		private String operatorId;
		private double levelOfService = 5*60.0;
		private String avAssignment = "closestWithinRange";
		private double waitingTimeUnmet = 2*60.0;

		public AVOperatorConfig() {
			super(NAME);
		}

		@StringGetter(OPERATOR_ID)
		public String getOperatorId() {
			return operatorId;
		}

		@StringSetter(OPERATOR_ID)
		public void getOperatorId(String operatorId) {
			this.operatorId = operatorId;
		}

		@StringSetter(LEVEL_OF_SERVICE)
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
		public AVAssignment getAVAssignment() {
			// as at the moment we have only one, we set this one...
			// later (after ride-sharing implementation) decide based on string...
			return new AVAssignment();
		}

		@StringSetter(WAITING_TIME_UNMET)
		public void setWaitingTimeUnmet(String waitingTimeUnmet) {
			this.waitingTimeUnmet = Double.parseDouble(waitingTimeUnmet);
		}

		@StringGetter(WAITING_TIME_UNMET)
		public double getWaitingTimeUnmet() {
			return waitingTimeUnmet;
		}
	}
}
