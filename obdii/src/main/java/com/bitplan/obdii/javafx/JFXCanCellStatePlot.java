/**
 *
 * This file is part of the https://github.com/BITPlan/can4eve open source project
 *
 * Copyright 2017 BITPlan GmbH https://github.com/BITPlan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *  You may obtain a copy of the License at
 *
 *  http:www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bitplan.obdii.javafx;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.bitplan.can4eve.CANInfo;
import com.bitplan.can4eve.CANValue.DoubleValue;

import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

public class JFXCanCellStatePlot {
  protected static Logger LOGGER = Logger.getLogger("com.bitplan.obdii");
  public static boolean debug = false;

  String title;
  String xTitle;
  String yTitle;
  private DoubleValue cellValues;
  private Double rangeExtra;
  private Double tickUnit;

  /**
   * create a Plot for a History of CANValues
   * 
   * @param title
   * @param xTitle
   * @param yTitle
   * @param cellValues
   */
  public JFXCanCellStatePlot(String title, String xTitle, String yTitle,
      DoubleValue cellValues, Double rangeExtra, Double tickUnit) {
    this.title = title;
    this.xTitle = xTitle;
    this.yTitle = yTitle;
    this.cellValues = cellValues;
    this.rangeExtra = rangeExtra;
    this.tickUnit = tickUnit;
  }

  /**
   * get the BarChart for the cellStates (e.g. Temperature/Voltage)
   * 
   * @return - the barchart
   */
  public BarChart<String, Number> getBarChart() {
    if (cellValues == null)
      return null;
    // defining the axes
    final CategoryAxis xAxis = new CategoryAxis();
    NumberAxis yAxis;
    if ((cellValues.getMax() != null) && (cellValues.getMax() != null)) {
      yAxis = new NumberAxis(cellValues.getMin() - rangeExtra,
          cellValues.getMax() + rangeExtra, tickUnit);
    } else {
      yAxis= new NumberAxis();
    }
    yAxis.setLabel(yTitle);
    xAxis.setLabel(xTitle);
    // creating the chart
    final BarChart<String, Number> barChart = new BarChart<String, Number>(
        xAxis, yAxis);

    barChart.setTitle(title);
    barChart.setCategoryGap(0);
    barChart.setBarGap(1);
    final CANInfo canInfo = cellValues.canInfo;

    // defining a series
    XYChart.Series<String, Number> series = new XYChart.Series<String, Number>();
    series.setName(canInfo.getTitle());
    if (debug)
      LOGGER.log(Level.INFO, "plotting for " + canInfo.getMaxIndex()
          + " values of " + canInfo.getTitle());
    for (int i = 0; i < canInfo.getMaxIndex(); i++) {
      if (cellValues.getValueItems() != null)
        if (cellValues.getValueItems()[i].isAvailable()) {
          String cellnum = "" + (i + 1);
          series.getData().add(new XYChart.Data<String, Number>(cellnum,
              cellValues.getValueItems()[i].getValue()));
        }
    }
    barChart.getData().add(series);

    return barChart;
  }
}
