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

package ch.ethz.matsim.boescpa.diss.analysis;

import ch.ethz.matsim.boescpa.diss.analysis.eventHandlers.TargetFunctionEvaluator;
import com.google.inject.name.Named;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.controler.listener.ShutdownListener;

import javax.inject.Inject;
import java.io.File;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class InSimAnalyzerHomeInSHPTargetFunction implements IterationEndsListener, ShutdownListener {

	private final TargetFunctionEvaluator targetFunctionEvaluator;

	@Inject
	public InSimAnalyzerHomeInSHPTargetFunction(Config config, Scenario scenario,
												@Named("pathToSHP") final String pathToSHP,
												EventsManager events, OutputDirectoryHierarchy controlerIO) {
		this.targetFunctionEvaluator = new TargetFunctionEvaluator(pathToSHP, scenario.getPopulation(),
				scenario.getNetwork(), scenario.getActivityFacilities());
		events.addHandler(this.targetFunctionEvaluator);
		this.targetFunctionEvaluator.prepareForInSimResults(
				controlerIO.getOutputPath() + File.separator + config.controler().getRunId() + ".",
				config.counts().getCountsScaleFactor());
	}

	@Override
	public void notifyIterationEnds(IterationEndsEvent iterationEndsEvent) {
		this.targetFunctionEvaluator.createInSimResults();
	}

	@Override
	public void notifyShutdown(ShutdownEvent shutdownEvent) {
		this.targetFunctionEvaluator.closeFiles();
	}
}
