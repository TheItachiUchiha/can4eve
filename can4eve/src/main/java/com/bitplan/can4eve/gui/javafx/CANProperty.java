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
package com.bitplan.can4eve.gui.javafx;

import java.util.Date;

import com.bitplan.can4eve.CANValue;
import com.bitplan.can4eve.CANValue.DoubleValue;
import com.bitplan.can4eve.CANValue.IntegerValue;

import javafx.application.Platform;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;

/**
 * generic JavaFX CANProperty
 * @author wf
 *
 * @param <CT>
 * @param <T>
 */
public class CANProperty<CT extends CANValue<T>,T> {
  CT canValue;
  private Property<T>property;
  private Property<T>max;
  private Property<T>avg;
  
  public CT getCanValue() {
    return canValue;
  }

  public Property<T> getProperty() {
    return property;
  }

  public void setProperty(Property<T> property) {
    this.property = property;
  }

  public Property<T> getMax() {
    return max;
  }

  public void setMax(Property<T> max) {
    this.max = max;
  }

  public Property<T> getAvg() {
    return avg;
  }

  public void setAvg(Property<T> avg) {
    this.avg = avg;
  }

  /**
   * construct me
   * @param canValue
   * @param property
   */
  public CANProperty(CT canValue, Property<T> property) {
    this.canValue=canValue;
    this.setProperty(property);
  }
  
  /**
   * construct a CANProperty
   * @param canValue
   * @param property
   */
  @SuppressWarnings({ "unchecked"})
  public CANProperty(DoubleValue canValue, SimpleDoubleProperty property) {
    this.canValue=(CT)canValue;
    this.setProperty((Property<T>)property);
    this.setAvg((Property<T>) new SimpleDoubleProperty());
    this.setMax((Property<T>) new SimpleDoubleProperty());
  }
  
  /**
   * construct me for an IntegerValue
   * @param canValue
   * @param property
   */
  @SuppressWarnings({ "unchecked"})
  public CANProperty(IntegerValue canValue,
      SimpleIntegerProperty property) {
    this.canValue=(CT)canValue;
    this.setProperty((Property<T>)property);
    this.setAvg((Property<T>) new SimpleIntegerProperty());
    this.setMax((Property<T>) new SimpleIntegerProperty());
  }
  
  /**
   * set the value for CANValue and property
   * @param value - the value to set
   * @param timeStamp
   */
  public void setValue(T value, Date timeStamp) {
    canValue.setValue(value, timeStamp);
    Platform.runLater(()->setValue(value));
  }
  
  /**
   * set the value (needs to be run on JavaFX thread!)
   * @param value
   */
  @SuppressWarnings({ "unchecked"})
  private void setValue(T value) {  
    getProperty().setValue(value);
    if (canValue instanceof DoubleValue) {
      DoubleValue dv=(DoubleValue) canValue;
      if (dv.getMax()!=null)
        getMax().setValue((T) dv.getMax());
      if (dv.getAvg()!=null)
        getAvg().setValue((T) dv.getAvg());
    }
    if (canValue instanceof IntegerValue) {
      IntegerValue iv=(IntegerValue) canValue;
      if (iv.getMax()!=null)
        getMax().setValue((T) iv.getMax());
      if (iv.getAvg()!=null)
        getAvg().setValue((T) iv.getAvg());
    }
  }

  /**
   * get the name of this property
   * @return
   */
  public String getName() {
    String name=canValue.canInfo.getName();
    return name;
  }
}