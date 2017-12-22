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

import ch.ethz.matsim.boescpa.lib.tools.coordUtils.CoordAnalyzer;
import ch.ethz.matsim.boescpa.lib.tools.utils.NetworkUtils;
import ch.ethz.matsim.boescpa.lib.tools.utils.PopulationUtils;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.events.handler.*;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class SavFleetStats {
	private final Network network;
	private final int LoS;
	private final CoordAnalyzer coordAnalyzer;
	private final Population population;
	private final MyHandler handler;

	public SavFleetStats(Network network, Population population, int LoS, String path2SHP) {
		this(network, population, LoS, path2SHP != null ? CoordAnalyzer.getCoordAnalyzer(path2SHP) : null);
	}

	public SavFleetStats(Network network, Population population, int LoS, CoordAnalyzer coordAnalyzer) {
		this.network = network;
		this.population = population;
		this.LoS = LoS;
		this.coordAnalyzer = coordAnalyzer;
		this.handler = new MyHandler();
	}

	public static void main(final String[] args) throws IOException {
		String path2Events = args[0];
		String path2Network = args[1];
		String path2Population = args[2];
		int LoS = args[3].equals("oligo") ? 1 : (path2Events.contains("180") ? 180 : 300);
		double scalingFactor = Integer.parseInt(args[4]);
		String path2SHP = args.length > 5 ? args[5] : null;

		SavFleetStats misc2 = new SavFleetStats(NetworkUtils.readNetwork(path2Network),
				PopulationUtils.readPopulation(path2Population), LoS, path2SHP);
		misc2.readEvents(path2Events);
		misc2.writeOutput(scalingFactor);
	}

	public void writeOutput(double scalingFactor) throws IOException {
		BufferedWriter writer = IOUtils.getBufferedWriter("./savUsageStats.csv");
		writer.write("\nTotal waiting time agents; " + handler.totalWaitingTime
				+ "; " + scalingFactor*handler.totalWaitingTime);
		writer.newLine();
		writer.write("Average waiting time; " + handler.totalWaitingTime/handler.numberOfPassengers
				+ "; " + handler.totalWaitingTime/handler.numberOfPassengers);
		writer.newLine();
		writer.write("Max waiting time; " + handler.maxWaitingTime
				+ "; " + handler.maxWaitingTime);
		writer.newLine();
		writer.write("Min waiting time; " + handler.minWaitingTime
				+ "; " + handler.minWaitingTime);
		writer.newLine();
		writer.write("Number of waiting times bigger LoS; " + handler.anzWaitingTimeBigger180
				+ "; " + scalingFactor*handler.anzWaitingTimeBigger180);
		writer.newLine();
		Percentile p = new Percentile();
		p.setData(handler.waitingTimes.stream().mapToDouble(d -> d).toArray());
		writer.write("Waiting Time 25th percentile; " + p.evaluate(25.0) + ";" + p.evaluate(25.0));
		writer.newLine();
		writer.write("Waiting Time 50th percentile; " + p.evaluate(50.0) + ";" + p.evaluate(50.0));
		writer.newLine();
		writer.write("Waiting Time 75th percentile; " + p.evaluate(75.0) + ";" + p.evaluate(75.0));
		writer.newLine();
		double upperIQR = p.evaluate(75.0) + (1.5*(p.evaluate(75.0) - p.evaluate(25.0)));
		long upperOutliers = handler.waitingTimes.stream().filter(d -> d > upperIQR).count();
		writer.write("Number of upper outliers; " + upperOutliers + ";" + scalingFactor*upperOutliers);

		writer.write("\nTotal pick-up drive time; " + handler.totalPickTime
				+ "; " + scalingFactor*handler.totalPickTime);
		writer.newLine();
		//writer.write("Average pick-up drive time per passenger (only aTaxi); " + handler.totalPickTime/handler.numberOfPassengers);
		//writer.newLine();
		writer.write("Average pick-up drive time per SAV and day; " + handler.totalPickTime/handler.avVehicles.size()
				+ "; " + handler.totalPickTime/handler.avVehicles.size());
		writer.newLine();
		//writer.write("Average pick dist per passenger (only aTaxi); " + handler.totalPickDist/handler.numberOfPassengers);
		//writer.newLine();
		writer.write("Max pick dist to passenger (aTaxi) or first passenger of pool (aRS); " + handler.maxPickDist
				+ "; " + handler.maxPickDist);
		writer.newLine();
		writer.write("Min pick dist to passenger (aTaxi) or first passenger of pool (aRS); " + handler.minPickDist
				+ "; " + handler.minPickDist);
		writer.newLine();
		writer.write("Average pick dist per SAV and day; " + handler.totalPickDist/handler.avVehicles.size()
				+ "; " + handler.totalPickDist/handler.avVehicles.size());
		writer.newLine();
		writer.write("Average crow pick dist to passenger (aTaxi) or first passenger of pool (aRS); "
				+ handler.totalCrowPickDist/handler.numberOfPassengers
				+ "; " + handler.totalCrowPickDist/handler.numberOfPassengers);
		writer.newLine();
		writer.write("Max crow pick dist to passenger (aTaxi) or first passenger of pool (aRS); " + handler.maxCrowPickDist
				+ "; " + handler.maxCrowPickDist);
		writer.newLine();
		writer.write("Min crow pick dist to passenger (aTaxi) or first passenger of pool (aRS); " + handler.minCrowPickDist
				+ "; " + handler.minCrowPickDist);
		writer.newLine();
		writer.write("Number of crow pick dist bigger LoS; " + handler.anzCrowDistBigger180
				+ "; " + scalingFactor*handler.anzCrowDistBigger180);
		writer.newLine();

		writer.write("\nTotal boarding time; " + handler.totalBoardingTime
				+ "; " + scalingFactor*handler.totalBoardingTime);
		writer.newLine();
		writer.write("Average boarding time; " + handler.totalBoardingTime/handler.numberOfPassengers
				+ "; " + handler.totalBoardingTime/handler.numberOfPassengers);
		writer.newLine();
		writer.write("Max boarding time; " + handler.maxBoardingTime
				+ "; " + handler.maxBoardingTime);
		writer.newLine();
		writer.write("Min boarding time; " + handler.minBoardingTime
				+ "; " + handler.minBoardingTime);
		writer.newLine();
		writer.write("Average boarding time per SAV and day; " + handler.totalBoardingTime/handler.avVehicles.size()
				+ "; " + handler.totalBoardingTime/handler.avVehicles.size());
		writer.newLine();

		writer.write("\nTotal driving time; " + handler.totalDrivingTime
				+ "; " + scalingFactor*handler.totalDrivingTime);
		writer.newLine();
		writer.write("Average driving time per passenger; " + handler.totalDrivingTimePassengers/handler.numberOfPassengers
				+ "; " + handler.totalDrivingTimePassengers/handler.numberOfPassengers);
		writer.newLine();
		writer.write("Average driving time per SAV and day; " + handler.totalDrivingTime/handler.avVehicles.size()
				+ "; " + handler.totalDrivingTime/handler.avVehicles.size());
		writer.newLine();
		writer.write("Total driving distance; " + handler.totalDrivingDist
				+ "; " + scalingFactor*handler.totalDrivingDist);
		writer.newLine();
		writer.write("Average driving distance per SAV and day; " + handler.totalDrivingDist/handler.avVehicles.size()
				+ "; " + handler.totalDrivingDist/handler.avVehicles.size());
		writer.newLine();
		writer.write("Average driving speed AV when transporting a passenger; "
				+ handler.totalDrivingDist/handler.totalDrivingTime
				+ "; " + handler.totalDrivingDist/handler.totalDrivingTime);
		writer.newLine();

		writer.write("\nTotal dropoff time; " + handler.totalDropOffTime
				+ "; " + scalingFactor*handler.totalDropOffTime);
		writer.newLine();
		writer.write("Average dropoff time per passenger; " + handler.totalDropOffTime/handler.numberOfPassengers
				+ "; " + handler.totalDropOffTime/handler.numberOfPassengers);
		writer.newLine();
		writer.write("Average dropoff time per SAV and day; " + handler.totalDropOffTime/handler.avVehicles.size()
				+ "; " + handler.totalDropOffTime/handler.avVehicles.size());
		writer.newLine();

		writer.write("\nTotal number of passengers; " + handler.numberOfPassengers
				+ "; " + scalingFactor*handler.numberOfPassengers);
		writer.newLine();
		writer.write("Number of SAVs; " + handler.avVehicles.size()
				+ "; " + scalingFactor*handler.avVehicles.size());
		writer.newLine();
		writer.write("Number of passengers per SAV; " + handler.numberOfPassengers/handler.avVehicles.size()
				+ "; " + handler.numberOfPassengers/handler.avVehicles.size());
		writer.newLine();
		writer.close();

		writer = IOUtils.getBufferedWriter("./waitingTimeDistribution.csv");
		writer.write("hour of day; average waiting time; anzPickups");
		for (int i = 0; i < 30; i++) {
			writer.write("\n" + i + "; " + handler.waitingTimeDistribution.get(i).getSecond()/handler.waitingTimeDistribution.get(i).getFirst() + "; " + handler.waitingTimeDistribution.get(i).getFirst());
		}
		writer.close();
	}

	private void readEvents(String path2Events) {
		EventsManager eventsManager= EventsUtils.createEventsManager();
		eventsManager.addHandler(handler);
		// Read the events file:
		MatsimEventsReader reader = new MatsimEventsReader(eventsManager);
		reader.readFile(path2Events);
		//if (handler.linkLeaves > 500) System.out.println(handler.lastTime + ": " + handler.linkLeaves);
	}

	public void addHandler(EventsManager em) {
		em.addHandler(handler);
	}

	private class MyHandler implements LinkLeaveEventHandler, PersonDepartureEventHandler, PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler, ActivityEndEventHandler, ActivityStartEventHandler {

		// Record AV wait times:
		private final Map<Id<Person>, Double> personDepartureTime = new LinkedHashMap<>();
		private final Map<String, Double> personBoarded = new LinkedHashMap<>();
		private final Set<String> avVehicles = new HashSet<>();
		private final Map<Integer, Tuple<Integer, Double>> waitingTimeDistribution = new LinkedHashMap<>();
		private final List<Double> waitingTimes = new ArrayList<>();
		private final Map<String, Double> vehiclePickUpDist = new LinkedHashMap<>();
		private final Map<String, Double> vehiclePickUpTime = new LinkedHashMap<>();
		private final Map<String, String> vehicleCrowDist = new LinkedHashMap<>();
		private final Map<String, Double> vehicleDriveDist = new LinkedHashMap<>();
		private final Map<String, Double> vehicleDriveTime = new LinkedHashMap<>();
		private final Map<Id<Person>, Double> personDrivingTime = new LinkedHashMap<>();
		double totalWaitingTime = 0;
		double totalPickTime = 0;
		double totalBoardingTime = 0;
		double totalDrivingTime = 0;
		double totalDrivingTimePassengers = 0;
		double totalDropOffTime = 0;
		double totalPickDist = 0;
		double totalCrowPickDist = 0;
		double totalDrivingDist = 0;
		double numberOfPassengers = 0;
		double maxWaitingTime = Double.MIN_VALUE;
		double minWaitingTime = Double.MAX_VALUE;
		double maxBoardingTime = Double.MIN_VALUE;
		double minBoardingTime = Double.MAX_VALUE;
		double maxPickDist = Double.MIN_VALUE;
		double minPickDist = Double.MAX_VALUE;
		double maxCrowPickDist = Double.MIN_VALUE;
		double minCrowPickDist = Double.MAX_VALUE;
		double anzWaitingTimeBigger180 = 0;
		double waitingOvertimeIgnoreCounter = 283;
		double anzCrowDistBigger180 = 0;

		MyHandler() {
			for (int i = 0; i < 30; i++) {
				waitingTimeDistribution.put(i, new Tuple<>(0, 0d));
			}
		}

		// First a person departs with AV and has to wait until boarding...
		@Override
		public void handleEvent(PersonDepartureEvent personDepartureEvent) {
			if (personDepartureEvent.getLegMode().equals("av")) {
				this.personDepartureTime.put(personDepartureEvent.getPersonId(), personDepartureEvent.getTime());
			}
		}

		// ... then the person boards which takes some time...
		@Override
		public void handleEvent(PersonEntersVehicleEvent personEntersVehicleEvent) {
			if (personDepartureTime.containsKey(personEntersVehicleEvent.getPersonId())
					&& personHomeInArea(personEntersVehicleEvent.getPersonId())) {
				double waitingTime = personEntersVehicleEvent.getTime() -
						personDepartureTime.get(personEntersVehicleEvent.getPersonId());
				totalWaitingTime += waitingTime;
				waitingTimes.add(waitingTime);
				maxWaitingTime = waitingTime > maxWaitingTime ? waitingTime : maxWaitingTime;
				minWaitingTime = waitingTime < minWaitingTime ? waitingTime : minWaitingTime;
				if (waitingTime > LoS) {
					anzWaitingTimeBigger180++;
					waitingOvertimeIgnoreCounter--;
				}

				if (waitingTime <= LoS) {// || waitingOvertimeIgnoreCounter < 0) {
					int hourOfDay = (int)Math.floor(personEntersVehicleEvent.getTime()/3600);
					Tuple<Integer, Double> numberTime = waitingTimeDistribution.get(hourOfDay);
					waitingTimeDistribution.put(hourOfDay,
							new Tuple<>(numberTime.getFirst() + 1, numberTime.getSecond() + waitingTime));
				}

				personDepartureTime.remove(personEntersVehicleEvent.getPersonId());
				personDrivingTime.put(personEntersVehicleEvent.getPersonId(), personEntersVehicleEvent.getTime());
				personBoarded.put(personEntersVehicleEvent.getVehicleId().toString(), personEntersVehicleEvent.getTime());
				avVehicles.add(personEntersVehicleEvent.getVehicleId().toString());

				if (vehiclePickUpDist.containsKey(personEntersVehicleEvent.getVehicleId().toString())) {
					double pickUpDist = vehiclePickUpDist.remove(personEntersVehicleEvent.getVehicleId().toString());
					totalPickDist += pickUpDist;
					maxPickDist = pickUpDist > maxPickDist ? pickUpDist : maxPickDist;
					minPickDist = pickUpDist < minPickDist ? pickUpDist : minPickDist;
					totalPickTime += personEntersVehicleEvent.getTime() -
							vehiclePickUpTime.remove(personEntersVehicleEvent.getVehicleId().toString());
				}
			}
		}

		@Override
		public void handleEvent(PersonLeavesVehicleEvent personLeavesVehicleEvent) {
			if (personDrivingTime.containsKey(personLeavesVehicleEvent.getPersonId())
					&& personHomeInArea(personLeavesVehicleEvent.getPersonId())) {
				totalDrivingTimePassengers += personLeavesVehicleEvent.getTime()
						- personDrivingTime.remove(personLeavesVehicleEvent.getPersonId());
			}
		}

		// ... and when the boarding is finished, the AV takes off.
		@Override
		public void handleEvent(ActivityEndEvent activityEndEvent) {
			if (activityEndEvent.getActType().equals("AVPickup") &&
					personBoarded.containsKey(activityEndEvent.getPersonId().toString())) {
				double boardingTime = activityEndEvent.getTime() -
						personBoarded.get(activityEndEvent.getPersonId().toString());
				totalBoardingTime += boardingTime;
				maxBoardingTime = boardingTime > maxBoardingTime ? boardingTime : maxBoardingTime;
				minBoardingTime = boardingTime < minBoardingTime ? boardingTime : minBoardingTime;
				personBoarded.remove(activityEndEvent.getPersonId().toString());
				numberOfPassengers++;
				if (vehicleCrowDist.containsKey(activityEndEvent.getPersonId().toString())) {
					double crowPickDist = CoordUtils.calcEuclideanDistance(
							network.getLinks().get(Id.createLinkId(vehicleCrowDist.remove(activityEndEvent.getPersonId().toString()))).getCoord(),
							network.getLinks().get(activityEndEvent.getLinkId()).getCoord());
					totalCrowPickDist += crowPickDist;
					maxCrowPickDist = crowPickDist > maxCrowPickDist ? crowPickDist : maxCrowPickDist;
					minCrowPickDist = crowPickDist < minCrowPickDist ? crowPickDist : minCrowPickDist;
					if ((crowPickDist * 1.3 / 13.8888888889) > LoS) {
						//System.out.println(activityEndEvent.getTime() +
						//		" - " + activityEndEvent.getPersonId() +
						//		" - " + crowPickDist);
						anzCrowDistBigger180++;
					}
				}
				vehicleDriveTime.put(activityEndEvent.getPersonId().toString(), activityEndEvent.getTime());
				vehicleDriveDist.put(activityEndEvent.getPersonId().toString(), 0d);
			} else if (activityEndEvent.getActType().equals("AVStay")) {
				vehiclePickUpDist.put(activityEndEvent.getPersonId().toString(), 0d);
				vehiclePickUpTime.put(activityEndEvent.getPersonId().toString(), activityEndEvent.getTime());
				vehicleCrowDist.put(activityEndEvent.getPersonId().toString(), activityEndEvent.getLinkId().toString());
			} else if (activityEndEvent.getActType().equals("AVDropoff") &&
					vehicleDriveTime.containsKey(activityEndEvent.getPersonId().toString())) {
				totalDropOffTime += 10;
			}
		}

		@Override
		public void handleEvent(ActivityStartEvent activityStartEvent) {
			if (activityStartEvent.getActType().equals("AVStay")
					&& vehicleDriveTime.containsKey(activityStartEvent.getPersonId().toString())) {
				totalDrivingTime += activityStartEvent.getTime() - 10
						- vehicleDriveTime.remove(activityStartEvent.getPersonId().toString());
				totalDrivingDist += vehicleDriveDist.remove(activityStartEvent.getPersonId().toString());
			}

		}

		double lastTime = 0;
		double linkLeaves = 0;
		@Override
		public void handleEvent(LinkLeaveEvent linkLeaveEvent) {
			if (linkLeaveEvent.getVehicleId().toString().contains("av")) {
				if (vehiclePickUpDist.containsKey(linkLeaveEvent.getVehicleId().toString())) {
					vehiclePickUpDist.put(linkLeaveEvent.getVehicleId().toString(),
							vehiclePickUpDist.get(linkLeaveEvent.getVehicleId().toString()) +
							network.getLinks().get(linkLeaveEvent.getLinkId()).getLength());
				}
				if (vehicleDriveDist.containsKey(linkLeaveEvent.getVehicleId().toString())) {
					vehicleDriveDist.put(linkLeaveEvent.getVehicleId().toString(),
							vehicleDriveDist.get(linkLeaveEvent.getVehicleId().toString()) +
									network.getLinks().get(linkLeaveEvent.getLinkId()).getLength());
				}
				if (linkLeaveEvent.getTime() != lastTime) {
					//if (linkLeaves > 500) System.out.println(lastTime + ": " + linkLeaves);
					lastTime = linkLeaveEvent.getTime();
					linkLeaves = 0;
				}
				linkLeaves++;
			}
		}

		@Override
		public void reset(int i) {}
	}

	private boolean personHomeInArea(Id<Person> personId) {
		if (coordAnalyzer == null) return true;
		Person person = this.population.getPersons().get(personId);
		for (PlanElement planElement : person.getSelectedPlan().getPlanElements()) {
			if (planElement instanceof Activity) {
				Activity activity = (Activity) planElement;
				if (activity.getType().equals("home_1")) {
					Coord homeCoord = activity.getCoord();
					return coordAnalyzer.isCoordAffected(homeCoord);
				}
			}
		}
		return false;
	}
}
