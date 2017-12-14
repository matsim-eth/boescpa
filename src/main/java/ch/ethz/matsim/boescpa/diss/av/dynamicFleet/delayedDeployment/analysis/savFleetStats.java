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

import ch.ethz.matsim.boescpa.lib.tools.utils.NetworkUtils;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.events.handler.*;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class savFleetStats {
	private final static int LoS = 300;
	private final Network network;

	public savFleetStats(Network network) {
		this.network = network;
	}

	public static void main(final String[] args) throws IOException {
		String path2Events = args[0];
		String path2Network = args[1];

		savFleetStats misc2 = new savFleetStats(NetworkUtils.readNetwork(path2Network));
		misc2.handleEvents(path2Events);
	}

	private void handleEvents(String path2Events) throws IOException {
		MyHandler handler = new MyHandler();
		EventsManager eventsManager= EventsUtils.createEventsManager();
		eventsManager.addHandler(handler);
		// Read the events file:
		MatsimEventsReader reader = new MatsimEventsReader(eventsManager);
		reader.readFile(path2Events);
		if (handler.linkLeaves > 500) System.out.println(handler.lastTime + ": " + handler.linkLeaves);

		BufferedWriter writer = IOUtils.getBufferedWriter("./savUsageStats.csv");
		writer.write("\nTotal waiting time agents; " + handler.totalWaitingTime);
		writer.newLine();
		writer.write("Average waiting time; " + handler.totalWaitingTime/handler.numberOfPassengers);
		writer.newLine();
		writer.write("Max waiting time; " + handler.maxWaitingTime);
		writer.newLine();
		writer.write("Min waiting time; " + handler.minWaitingTime);
		writer.newLine();
		writer.write("Number of waiting times bigger LoS; " + handler.anzWaitingTimeBigger180);
		writer.newLine();

		writer.write("\nTotal pick-up drive time; " + handler.totalPickTime);
		writer.newLine();
		//writer.write("Average pick-up drive time per passenger (only aTaxi); " + handler.totalPickTime/handler.numberOfPassengers);
		//writer.newLine();
		writer.write("Average pick-up drive time per SAV and day; " + handler.totalPickTime/handler.avVehicles.size());
		writer.newLine();
		//writer.write("Average pick dist per passenger (only aTaxi); " + handler.totalPickDist/handler.numberOfPassengers);
		//writer.newLine();
		writer.write("Max pick dist to passenger (aTaxi) or first passenger of pool (aRS); " + handler.maxPickDist);
		writer.newLine();
		writer.write("Min pick dist to passenger (aTaxi) or first passenger of pool (aRS); " + handler.minPickDist);
		writer.newLine();
		writer.write("Average pick dist per SAV and day; " + handler.totalPickDist/handler.avVehicles.size());
		writer.newLine();
		writer.write("Average crow pick dist to passenger (aTaxi) or first passenger of pool (aRS); "
				+ handler.totalCrowPickDist/handler.numberOfPassengers);
		writer.newLine();
		writer.write("Max crow pick dist to passenger (aTaxi) or first passenger of pool (aRS); " + handler.maxCrowPickDist);
		writer.newLine();
		writer.write("Min crow pick dist to passenger (aTaxi) or first passenger of pool (aRS); " + handler.minCrowPickDist);
		writer.newLine();
		writer.write("Anz crow pick dist bigger LoS; " + handler.anzCrowDistBigger180);
		writer.newLine();

		writer.write("\nTotal boarding time; " + handler.totalBoardingTime);
		writer.newLine();
		writer.write("Average boarding time; " + handler.totalBoardingTime/handler.numberOfPassengers);
		writer.newLine();
		writer.write("Max boarding time; " + handler.maxBoardingTime);
		writer.newLine();
		writer.write("Min boarding time; " + handler.minBoardingTime);
		writer.newLine();
		writer.write("Average boarding time per SAV and day; " + handler.totalBoardingTime/handler.avVehicles.size());
		writer.newLine();

		writer.write("\nTotal driving time; " + handler.totalDrivingTime);
		writer.newLine();
		writer.write("Average driving time per passenger; " + handler.totalDrivingTimePassengers/handler.numberOfPassengers);
		writer.newLine();
		writer.write("Average driving time per SAV and day; " + handler.totalDrivingTime/handler.avVehicles.size());
		writer.newLine();
		writer.write("Total driving distance; " + handler.totalDrivingDist);
		writer.newLine();
		writer.write("Average driving distance per SAV and day; " + handler.totalDrivingDist/handler.avVehicles.size());
		writer.newLine();
		writer.write("Average driving speed AV when transporting a passenger; "
				+ handler.totalDrivingDist/handler.totalDrivingTime);
		writer.newLine();

		writer.write("\nTotal dropoff time; " + handler.totalDropOffTime);
		writer.newLine();
		writer.write("Average dropoff time per passenger; " + handler.totalDropOffTime/handler.numberOfPassengers);
		writer.newLine();
		writer.write("Average dropoff time per SAV and day; " + handler.totalDropOffTime/handler.avVehicles.size());
		writer.newLine();

		writer.write("\nTotal number of passengers; " + handler.numberOfPassengers);
		writer.newLine();
		writer.write("Number of SAVs; " + handler.avVehicles.size());
		writer.newLine();
		writer.write("Number of passengers per SAV; " + handler.numberOfPassengers/handler.avVehicles.size());
		writer.newLine();
		writer.close();

		writer = IOUtils.getBufferedWriter("./waitingTimeDistribution.csv");
		writer.write("hour of day; average waiting time; anzPickups");
		for (int i = 0; i < 30; i++) {
			writer.write("\n" + i + "; " + handler.waitingTimeDistribution.get(i).getSecond()/handler.waitingTimeDistribution.get(i).getFirst() + "; " + handler.waitingTimeDistribution.get(i).getFirst());
		}
		writer.close();
	}

	private class MyHandler implements LinkLeaveEventHandler, PersonDepartureEventHandler, PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler, ActivityEndEventHandler, ActivityStartEventHandler {

		// Record AV wait times:
		private final Map<Id<Person>, Double> personDepartureTime = new LinkedHashMap<>();
		private final Map<String, Double> personBoarded = new LinkedHashMap<>();
		private final Set<String> avVehicles = new HashSet<>();
		private final Map<Integer, Tuple<Integer, Double>> waitingTimeDistribution = new LinkedHashMap<>();
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
			if (personDepartureTime.containsKey(personEntersVehicleEvent.getPersonId())) {
				double waitingTime = personEntersVehicleEvent.getTime() -
						personDepartureTime.get(personEntersVehicleEvent.getPersonId());
				totalWaitingTime += waitingTime;
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
			if (personDrivingTime.containsKey(personLeavesVehicleEvent.getPersonId())) {
				totalDrivingTimePassengers += personDrivingTime.remove(personLeavesVehicleEvent.getPersonId());
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
						System.out.println(activityEndEvent.getTime() +
								" - " + activityEndEvent.getPersonId() +
								" - " + crowPickDist);
						anzCrowDistBigger180++;
					}
				}
				vehicleDriveTime.put(activityEndEvent.getPersonId().toString(), activityEndEvent.getTime());
				vehicleDriveDist.put(activityEndEvent.getPersonId().toString(), 0d);
			} else if (activityEndEvent.getActType().equals("AVStay")) {
				vehiclePickUpDist.put(activityEndEvent.getPersonId().toString(), 0d);
				vehiclePickUpTime.put(activityEndEvent.getPersonId().toString(), activityEndEvent.getTime());
				vehicleCrowDist.put(activityEndEvent.getPersonId().toString(), activityEndEvent.getLinkId().toString());
			} else if (activityEndEvent.getActType().equals("AVDropoff")) {
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
					if (linkLeaves > 500) System.out.println(lastTime + ": " + linkLeaves);
					lastTime = linkLeaveEvent.getTime();
					linkLeaves = 0;
				}
				linkLeaves++;
			}
		}

		@Override
		public void reset(int i) {}
	}
}
