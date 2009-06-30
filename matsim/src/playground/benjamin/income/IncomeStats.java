/* *********************************************************************** *
 * project: org.matsim.*
 * IncomeStats
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
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
 * *********************************************************************** */
package playground.benjamin.income;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.PriorityQueue;

import org.apache.log4j.Logger;
import org.matsim.core.utils.charts.XYLineChart;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.households.Households;
import org.matsim.households.basic.BasicHousehold;
import org.matsim.households.basic.BasicHouseholds;
import org.matsim.households.basic.BasicHouseholdsImpl;
import org.matsim.households.basic.BasicHouseholdsReaderV10;
import org.matsim.households.basic.HouseholdIncomeComparator;

import playground.dgrether.DgPaths;


/**
 * @author dgrether
 *
 */
public class IncomeStats {

	
	private static final Logger log = Logger.getLogger(IncomeStats.class);
	
	private BasicHouseholds<BasicHousehold> households;
	private double totalIncome;


	public IncomeStats(BasicHouseholds<BasicHousehold> hhs){
		this.households = hhs;
	}
	
	
	public IncomeStats(Households hhs) {
		this.households = (BasicHouseholds)hhs;
		this.totalIncome = calculateTotalIncome();
	}
	
	private double calculateTotalIncome(){
		double ti = 0.0;
		for (BasicHousehold hh : this.households.getHouseholds().values()){
			ti += hh.getIncome().getIncome();
		}
		return ti;
	}


	public void calculateStatistics(String outdir) {
		this.calculateLorenzCurve(outdir);
		this.writeIncomeTable(outdir);
	}
	
	
	
	
	private void writeIncomeTable(String outdir) {
		try {
			BufferedWriter writer = IOUtils.getBufferedWriter(outdir + "hhincomes.txt");
			writer.write("Id \t income \t incomeperiod");
			writer.newLine();
			for (BasicHousehold hh : this.households.getHouseholds().values()){
				writer.write(hh.getId() + "\t" + hh.getIncome().getIncome() + "\t" + hh.getIncome().getIncomePeriod());
				writer.newLine();
			}
			writer.flush();
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	private void calculateLorenzCurve(String outdir) {
		int stepSizePercent = 1;
		PriorityQueue<BasicHousehold> hhQueue = new PriorityQueue<BasicHousehold>(this.households.getHouseholds().size(), 
				new HouseholdIncomeComparator());
		hhQueue.addAll(this.households.getHouseholds().values());
    int hhsPerStepSize = this.households.getHouseholds().size() / 100 * stepSizePercent;
		int steps = 100 / stepSizePercent;
    double[] xValues = new double[steps];
		double[] yValues = new double[steps];
		double incomePerStep;
		for (int i = 0; i < steps; i++){
			xValues[i] = i;
			incomePerStep = 0.0;
			for (int j = 0; j < hhsPerStepSize; j++) {
				incomePerStep += hhQueue.poll().getIncome().getIncome();
			}
			yValues[i] = incomePerStep / hhsPerStepSize; 
		}
		
		XYLineChart chart = new XYLineChart("Lorenz", "number of hh percent", "percentage of income");
		ChartData data = new ChartData("Lorenz", "number of hh percent", "percentage of income");
		chart.addSeries("incomes", xValues, yValues);
		data.addSeries("incomes", xValues, yValues);
		ChartDataWriter writer = new ChartDataWriter(data);
		writer.writeFile(outdir + "lorenzValues.txt");
		chart.saveAsPng(outdir + "lorenz.png", 800, 600);
	}


	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String outdir = DgPaths.SHAREDSVN + "studies/bkick/oneRouteTwoModeIncomeTest/";
		String hhFile = outdir + "households.xml";
		BasicHouseholds<BasicHousehold> hhs = new BasicHouseholdsImpl();
		new BasicHouseholdsReaderV10(hhs).readFile(hhFile);
		IncomeStats istats = new IncomeStats(hhs);
		istats.calculateStatistics(outdir);
		log.info("stats written!");
	}

}
