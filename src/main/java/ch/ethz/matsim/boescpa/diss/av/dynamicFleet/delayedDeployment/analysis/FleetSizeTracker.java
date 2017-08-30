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

package ch.ethz.matsim.boescpa.diss.av.dynamicFleet.delayedDeployment.analysis;

import ch.ethz.matsim.av.data.AVOperator;
import ch.ethz.matsim.av.dispatcher.AVVehicleAssignmentEvent;
import com.google.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.controler.listener.ShutdownListener;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.utils.charts.XYLineChart;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static ch.ethz.matsim.boescpa.analysis.scenarioAnalyzer.ScenarioAnalyzer.DEL;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class FleetSizeTracker implements BasicEventHandler, IterationEndsListener, ShutdownListener {

	private final BufferedWriter inSimFleetSizeWriter;
	private final HashMap<String, Map<Integer, Double>> history;
	private final String pathPNG;
	private final Map<Id<AVOperator>, AVOperator> operators;
	private int iteration;

	@Inject
	public FleetSizeTracker(Config config, OutputDirectoryHierarchy controlerIO,
							Map<Id<AVOperator>, AVOperator> operators, EventsManager eventsManager) {
		eventsManager.addHandler(this);
		this.operators = operators;
		String outputFilePath = controlerIO.getOutputPath() + File.separator + config.controler().getRunId() + ".";
		this.inSimFleetSizeWriter =
				IOUtils.getBufferedWriter(outputFilePath + "avFleetSizes.csv");
		String header = "it";
		this.history = new HashMap<>();
		for (Id<AVOperator> avOperatorId : operators.keySet()) {
			header += DEL + avOperatorId.toString();
			this.history.put(avOperatorId.toString(), new HashMap<>());
		}
		try {
			this.inSimFleetSizeWriter.write(header);
			this.inSimFleetSizeWriter.newLine();
			this.inSimFleetSizeWriter.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.pathPNG = outputFilePath + "avFleetSizes";
	}

	@Override
	public void notifyIterationEnds(IterationEndsEvent iterationEndsEvent) {
		// write results
		try {
			this.inSimFleetSizeWriter.write(getInSimResults());
			this.inSimFleetSizeWriter.newLine();
			this.inSimFleetSizeWriter.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		// plot graphs
		if (this.iteration > 0) {
			XYLineChart chart = new XYLineChart("AV Fleet Sizes", "iteration", "operator");
			for (String operator : this.history.keySet()) {
				Map<Integer, Double> modeHistory = this.history.get(operator);
				chart.addSeries(operator, modeHistory);
			}
			chart.saveAsPng(this.pathPNG + ".png", 800, 600);
		}
	}

	@Override
	public void notifyShutdown(ShutdownEvent shutdownEvent) {
		try {
			inSimFleetSizeWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void handleEvent(Event event) {
		if (event instanceof AVVehicleAssignmentEvent) {
			String operator = event.getAttributes().get("operator");
			double vehicleSize = history.get(operator).getOrDefault(this.iteration, 0.0);
			vehicleSize++;
			history.get(operator).put(this.iteration, vehicleSize);
		}
	}

	@Override
	public void reset(int i) {
		this.iteration = i;
	}

	private String getInSimResults() {
		String line = String.valueOf(this.iteration);
		for (Id<AVOperator> avOperatorId : operators.keySet()) {
			line += DEL + this.history.get(avOperatorId.toString()).get(this.iteration);
		}
		return line;
	}
}
