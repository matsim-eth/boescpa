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

package ch.ethz.matsim.boescpa.various.vbzKostendeckungsgrade;

import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;

/**
 * WHAT IS IT FOR?
 *
 * @author boescpa
 */
public class DatenaufbereitungVBZKoDeGra {
	final static String DEL = ";";

	public static void main(final String[] main) {
		BufferedReader reader = IOUtils.getBufferedReader(main[0]);
		BufferedWriter writer = IOUtils.getBufferedWriter(main[1]);
		try {
			// header
			String[] header = reader.readLine().split(" ");
			writer.write(header[0] + DEL + header[1] + DEL + header [3]); writer.newLine();
			// body
			String line = reader.readLine();
			while (line != null) {
				writer.write(processLine(line));
				writer.newLine();
				line = reader.readLine();
			}
			reader.close();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static String processLine(String line) {
		String cleanedLine;
		String[] lineElements = line.split(" ");
		cleanedLine = lineElements[0];
		cleanedLine += DEL + lineElements[1];
		for (int i = 2; i < lineElements.length; i++) {
			if (lineElements[i].contains("%")) {
				cleanedLine += DEL + lineElements[i];
				break;
			}
		}
		return cleanedLine;
	}
}
