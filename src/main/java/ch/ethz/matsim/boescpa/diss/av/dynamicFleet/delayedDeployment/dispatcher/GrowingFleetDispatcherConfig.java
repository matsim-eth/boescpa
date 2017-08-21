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

package ch.ethz.matsim.boescpa.diss.av.dynamicFleet.delayedDeployment.dispatcher;

import org.matsim.core.config.ReflectiveConfigGroup;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class GrowingFleetDispatcherConfig extends ReflectiveConfigGroup {
	public final static String NAME = "GrowingFleetDispatcher";

	final static String LEVEL_OF_SERVICE = "levelOfService";

	private double levelOfService = 5*60;

	public GrowingFleetDispatcherConfig() {
		super(NAME);
	}

	@StringGetter(LEVEL_OF_SERVICE)
	public double getLevelOfService() {
		return levelOfService;
	}

	@StringSetter(LEVEL_OF_SERVICE)
	public void setLevelOfService(String levelOfService) {
		this.levelOfService = Double.parseDouble(levelOfService);
	}
}
