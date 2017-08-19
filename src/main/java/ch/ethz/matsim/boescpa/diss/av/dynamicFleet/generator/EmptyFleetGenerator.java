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

package ch.ethz.matsim.boescpa.diss.av.dynamicFleet.generator;

import ch.ethz.matsim.av.config.AVGeneratorConfig;
import ch.ethz.matsim.av.data.AVVehicle;
import ch.ethz.matsim.av.generator.AVGenerator;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class EmptyFleetGenerator implements AVGenerator {

	@Override
	public boolean hasNext() {
		return false;
	}

	@Override
	public AVVehicle next() {
		return null;
	}

	static public class Factory implements AVGenerator.AVGeneratorFactory {
		@Override
		public AVGenerator createGenerator(AVGeneratorConfig generatorConfig) {
			return new EmptyFleetGenerator();
		}
	}
}
