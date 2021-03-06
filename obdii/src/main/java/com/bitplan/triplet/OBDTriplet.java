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
package com.bitplan.triplet;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.bitplan.can4eve.CANInfo;
import com.bitplan.can4eve.CANValue;
import com.bitplan.can4eve.CANValue.CANRawValue;
import com.bitplan.can4eve.CANValue.DoubleValue;
import com.bitplan.can4eve.CANValue.IntegerValue;
import com.bitplan.can4eve.CANValue.StringValue;
import com.bitplan.can4eve.ErrorHandler;
import com.bitplan.can4eve.Pid;
import com.bitplan.can4eve.Vehicle;
import com.bitplan.can4eve.VehicleGroup;
import com.bitplan.can4eve.gui.javafx.CANProperty;
import com.bitplan.csv.CSVUtil;
import com.bitplan.elm327.Connection;
import com.bitplan.obdii.CANValueDisplay;
import com.bitplan.obdii.JFXTripletDisplay;
import com.bitplan.obdii.OBDHandler;
import com.bitplan.obdii.PIDResponse;
import com.bitplan.obdii.elm327.ELM327;
import com.bitplan.triplet.ShifterPosition.ShiftPosition;

import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;

/**
 * Handles OBD II communication for the Triplet cars Mitsubishi i-Miev, Peugeot
 * Ion and Citroen C-Zero
 * 
 * @author wf
 *
 */
public class OBDTriplet extends OBDHandler {

  // car parameters
  public VINValue VIN;
  VINValue VIN2;
  ClimateValue climateValue;
  StringValue ventDirection;

  ShifterPositionValue shifterPositionValue;
  /**
   * construct me
   */
  public OBDTriplet(VehicleGroup vehicleGroup) {
    super(vehicleGroup);
    postConstruct();
  }

  /**
   * construct me from a serial Device
   * 
   * @param vehicleGroup
   * @param file
   * @param baudRate
   */
  public OBDTriplet(VehicleGroup vehicleGroup, String device, int baudRate) {
    super(vehicleGroup, device, baudRate);
    postConstruct();
  }

  /**
   * construct me
   * 
   * @param vehicleGroup
   * @param file
   */
  public OBDTriplet(VehicleGroup vehicleGroup, File file) {
    super(vehicleGroup, file);
    postConstruct();
  }

  /**
   * construct me
   * 
   * @param vehicleGroup
   * @param socket
   * @param debug
   * @throws IOException
   */
  public OBDTriplet(VehicleGroup vehicleGroup, Socket socket, boolean debug)
      throws Exception {
    super(vehicleGroup, socket, debug);
    postConstruct();
  }

  /**
   * construct me
   * 
   * @param vehicleGroup
   * @param socket
   * @throws IOException
   */
  public OBDTriplet(VehicleGroup vehicleGroup, Socket socket) throws Exception {
    this(vehicleGroup, socket, debug);
  }

  /**
   * create me with a preconfigured elm (e.g. simulator)
   * 
   * @param vehicleGroup
   * @param elm
   */
  public OBDTriplet(VehicleGroup vehicleGroup, ELM327 elm) {
    super(vehicleGroup, elm);
    postConstruct();
  }

  /**
   * initialize the CanValues
   */
  public void initCanValues(String... canInfoNames) {
    super.initCanValues(canInfoNames);
    VIN = new VINValue(getCanInfo("VIN"));
    VIN2 = new VINValue(getCanInfo("VIN"));
    climateValue = new ClimateValue(getCanInfo("Climate"));
    ventDirection = new StringValue(getCanInfo("VentDirection"));
    shifterPositionValue = new ShifterPositionValue(
        getCanInfo("ShifterPosition"));
  }

  /**
   * things to do / initialize after I a was constructed
   */
  public void postConstruct() {
    initCanValues("ACAmps", "ACVolts", "Accelerator", "BatteryCapacity",
        "BlinkerLeft", "BlinkerRight", "BreakPedal", "BreakPressed",
        "CellCount", "CellTemperature", "CellVoltage", "ChargerTemp", "DCAmps",
        "DCVolts", "DoorOpen", "HeadLight", "HighBeam", "Key", "MotorTemp",
        "Odometer", "ParkingLight", "Range", "RPM", "RPMSpeed", "SOC", "Speed",
        "SteeringWheelPosition", "SteeringWheelMovement", "TripRounds",
        "TripOdo");
    // add all available PIDs to the available raw values
    for (Pid pid : getVehicleGroup().getPids()) {
      // FIXME - do we keep the convention for raw values?
      CANInfo pidInfo = pid.getFirstInfo();
      if (debug) {
        // LOGGER.log(Level.INFO,"rawValue "+pidInfo.getPid().getPid()+"
        // added");
      }
      getCanRawValues().put(pid.getPid(), new CANRawValue(pidInfo));
    }
    VIN.activate();
    // properties
    msecsRunningProperty = new SimpleLongProperty();
    vehicleStateProperty = new SimpleObjectProperty<Vehicle.State>();
  }

  /**
   * callback for responses
   * 
   * @param response
   *          - the reponse to handle
   */
  public void handleResponse(PIDResponse pr) {
    if (debug)
      LOGGER.log(Level.INFO, "triplet handling PID Response " + pr.pidId + " ("
          + pr.pid.getName() + ")");
    Date timeStamp = pr.getResponse().getTime();
    String pidName = pr.pid.getName();
    switch (pidName) {
    case "Accelerator":
      cpm.setValue(pidName, pr.d[2] / 250.0 * 100, timeStamp);
      break;
    case "AmpsVolts":
      cpm.setValue("DCAmps", ((pr.d[2] * 256 + pr.d[3]) - 128 * 256) / 100.0,
          timeStamp);
      cpm.setValue("DCVolts", (pr.d[4] * 256 + pr.d[5]) / 10.0, timeStamp);
      break;
    case "ACAmpsVolts":
      cpm.setValue("ACVolts", pr.d[1] * 1.0, timeStamp);
      cpm.setValue("ACAmps", pr.d[6] / 10.0, timeStamp);
      break;
    case "BatteryCapacity":
      int bindex = pr.d[0];
      if (bindex == 0x24) {
        double ah = (pr.d[3] * 256 + pr.d[4]) / 10.0;
        // LOGGER.log(Level.INFO,String.format("Battery capacity is: %4.1f Ah",
        // ah));
        cpm.setValue("BatteryCapacity", ah, timeStamp);
      }
      break;
    case "BreakPedal":
      // TODO 6F FF FF FF FF FF
      cpm.setValue(pidName, ((pr.d[2] * 256 + pr.d[3]) - 24576.0) / 640 * 100.0,
          timeStamp);
      break;
    case "BreakPressed":
      cpm.setValue(pidName, pr.d[4] == 2, timeStamp);
      break;
    case "ChargerTemp":
      cpm.setValue(pidName, pr.d[3] - 40, timeStamp);
      break;
    case "CellInfo1":
    case "CellInfo2":
    case "CellInfo3":
    case "CellInfo4":
      Pid cellInfo1 = getVehicleGroup().getPidByName("CellInfo1");
      int pidindex = pr.pidHex - PIDResponse.hex2decimal(cellInfo1.getPid());
      // cell monitoring unit index
      int cmu_id = pr.d[0]; // 1-12
      double temp1 = pr.d[1] - 50;
      double temp2 = pr.d[2] - 50;
      double temp3 = pr.d[3] - 50;
      double voltage1 = (pr.d[4] * 256 + pr.d[5]) / 100.0;
      double voltage2 = (pr.d[6] * 256 + pr.d[7]) / 100.0;
      int index = pidindex * 2 + (cmu_id - 1) * 8;
      // ignore voltages for cmu id 6 and 12 on 6E3 and 6E4
      boolean voltageIgnore = (pidindex == 2 || pidindex == 3)
          && (cmu_id == 6 || cmu_id == 12);
      DoubleValue cellVoltage = this.getValue("CellVoltage");
      if (index < cellVoltage.canInfo.getMaxIndex()) {
        if (!voltageIgnore) {
          cellVoltage.setValue(index, voltage1, timeStamp);
          cellVoltage.setValue(index + 1, voltage2, timeStamp);
        }
      }
      DoubleValue cellTemperature = this.getValue("CellTemperature");
      if (index < cellTemperature.canInfo.getMaxIndex()) {
        switch (pr.pid.getName()) {
        case "CellInfo1":
          cellTemperature.setValue(index, temp2, timeStamp);
          cellTemperature.setValue(index + 1, temp3, timeStamp);
          break;
        case "CellInfo2":
          cellTemperature.setValue(index, temp1, timeStamp);
          if (cmu_id != 6 && cmu_id != 12)
            cellTemperature.setValue(index + 1, temp2, timeStamp);
          break;
        case "CellInfo3":
          if (cmu_id != 6 && cmu_id != 12) {
            cellTemperature.setValue(index, temp1, timeStamp);
            cellTemperature.setValue(index + 1, temp2, timeStamp);
          }
        default:
          // ignore
        }
      }
      break;
    case "Climate":
      Climate climate = new Climate();
      climate.setClimate(pr.d[0], pr.d[1]);
      /**
       * http://myimiev.com/forum/viewtopic.php?p=31226 PID 3A4 byte 0, bits
       * 0-3: heating level (7 is off, under 7 is cooling, over 7 is heating)
       * byte 0, bit 7: AC on (ventilation dial pressed) byte 0, bit 5: MAX
       * heating (heating dial pressed) byte 0, bit 6: air recirculation
       * (ventilation direction dial pressed)
       * 
       * byte 1, bits 0-3: ventilation level (if AUTO is chosen, the
       * automatically calculated level is returned) byte 1, bits 4-7:
       * ventilation direction (1-2 face, 3 legs+face, 4 -5legs, 6
       * legs+windshield 7-9 windshield)
       */
      int ventDirVal = (pr.d[1] & 0xf0) >> 4;
      String ventDir = "?";
      switch (ventDirVal) {
      case 1:
      case 2:
        ventDir = "face";
        break;
      case 3:
      case 4:
        ventDir = "legs+face";
        break;
      case 5:
      case 6:
        ventDir = "legs";
        break;
      case 7:
      case 8:
        ventDir = "legs+windshield";
        break;
      case 9:
        ventDir = "windshield";
        break;
      }
      ventDirection.setValue(String.format("%s(%d)", ventDir, ventDirVal),
          timeStamp);
      climateValue.setValue(climate, timeStamp);
      break;
    case "Key":
      int keyVal = pr.d[0];
      cpm.setValue(pidName, keyVal == 4, timeStamp);
      break;
    case "Lights":
      int lightNum = pr.d[0] * 256 + pr.d[1];
      int ilightNum = pr.d[2];
      cpm.setValue("DoorOpen", (ilightNum & 0x01) != 0, timeStamp);
      cpm.setValue("BlinkerRight", (lightNum & 0x01) != 0, timeStamp);
      cpm.setValue("BlinkerLeft", (lightNum & 0x02) != 0, timeStamp);
      cpm.setValue("HighBeam", (lightNum & 0x04) != 0, timeStamp);
      cpm.setValue("HeadLight", (lightNum & 0x20) != 0, timeStamp);
      cpm.setValue("ParkingLight", (lightNum & 0x40) != 0, timeStamp);
      break;
    case "MotorTemp_RPM":
      cpm.setValue("MotorTemp", pr.d[3] - 40, timeStamp);
      // fetch teh rounds per minute
      int rpmValue = (pr.d[6] * 256 + pr.d[7]) - 10000;
      // if we have a previous value we can start integrating
      IntegerValue rpm = getValue("RPM");
      if (rpm.getValueItem().isAvailable()) {
        DoubleValue tripRounds = this.getValue("TripRounds");
        // calc numerical integral - how many rounds total on this trip?
        tripRounds.integrate(rpm.getValueItem().getValue(),
            rpm.getValueItem().getTimeStamp(), Math.abs(rpmValue), timeStamp,
            1 / 60000.0);
        // calc distance based on rounds
        cpm.setValue("TripOdo",
            tripRounds.getValueItem().getValue() * mmPerRound / 1000000.0,
            timeStamp);
      }
      cpm.setValue("RPM", rpmValue, timeStamp);
      IntegerValue speed = this.getValue("Speed");
      if (speed.getValueItem().isAvailable()) {
        // m per round
        // speed.getValueItem().getValue() * 1000.0 / 60
        // / rpm.getValueItem().getValue()
        double rpmSpeed = rpm.getValue() * this.mmPerRound * 60 / 1000000.0;
        cpm.setValue("RPMSpeed", rpmSpeed, timeStamp);
      }
      break;
    case "Odometer_Speed":
      cpm.setValue("Odometer", pr.d[2] * 65536 + pr.d[3] * 256 + pr.d[4],
          timeStamp);
      Integer speedNum = pr.d[1];
      if (speedNum == 255)
        speedNum = null;
      cpm.setValue("Speed", speedNum, timeStamp);
      break;
    case "Range": // 0x346
      Integer rangeNum = pr.d[7];
      if (rangeNum == 255)
        rangeNum = null;
      cpm.setValue("Range", rangeNum, timeStamp);
      break;
    case "Steering_Wheel":
      cpm.setValue("SteeringWheelPosition",
          (pr.d[0] * 256 + pr.d[1] - 4096) / 2.0, timeStamp);
      cpm.setValue("SteeringWheelMovement",
          (pr.d[2] * 256 + pr.d[3] - 4096) / 2.0, timeStamp);
      break;
    case "ShifterPosition":
      ShifterPosition newShifterPosition = new ShifterPosition(pr.d[0]);
      shifterPositionValue.setValue(newShifterPosition, timeStamp);
      if (newShifterPosition.shiftPosition == ShiftPosition.P) {
        this.vehicleStateProperty.set(Vehicle.State.Parking);
        // are we charging?
        DoubleValue acvolts = getValue("ACVolts");
        if (acvolts.getValueItem().isAvailable() && acvolts.getValue() > 50) {
          // AC charging
          this.vehicleStateProperty.set(Vehicle.State.Charging);
        }
        // DC charging
        // FIXME is 1 amp the minimum?
        DoubleValue dcamps = getValue("DCAmps");
        if (dcamps.getValueItem().isAvailable() && dcamps.getValue() > 1.0) {
          this.vehicleStateProperty.set(Vehicle.State.Charging);
        }
      } else {
        this.vehicleStateProperty.set(Vehicle.State.Moving);
      }
      break;

    case "SOC":
      // state of charging in %
      double soc = ((pr.d[1]) - 10) / 2.0;
      // FIXME - workaround for binding timing issue
      soc = soc - Math.random() * 0.001;
      cpm.setValue("SOC", soc, timeStamp);
      break;

    case "VIN":
      int indexVal = pr.d[0];
      String partVal = pr.getString(1);
      VIN.set(indexVal, partVal, timeStamp);
      if (VIN.getValueItem().isAvailable()) {
        cpm.setValue("CellCount", VIN.getCellCount(), timeStamp);
      }
      break;

    case "VIN2":
      int v2indexVal = pr.d[0];
      String v2partVal = pr.getString();
      VIN2.set(v2indexVal, v2partVal, timeStamp);
      break;
    default:
      // ignore - this case is handled by the raw values below
      break;
    } // switch
    CANRawValue canRawValue = this.getCanRawValues().get(pr.pid.getPid());
    canRawValue.setRawValue(pr.getRawString(), timeStamp);
  }

  int dateUpdateCount = 0;
  int fpsUpdateCount = 0;
  Date latestUpdate;
  Date displayStart;
  long latestTotalUpdates;
  private Date latestHistoryUpdate;

  /**
   * show the values
   * 
   * @param display
   */
  public void showValues(final CANValueDisplay display) {
    Date now = new Date();
    String nowStr = isoDateFormatter.format(now);
    display.updateField("date", nowStr, ++dateUpdateCount);
    long totalUpdates = 0;
    if (displayStart != null) {
      long msecsRunning = now.getTime() - displayStart.getTime();
      if (msecsRunningProperty != null)
        this.msecsRunningProperty.setValue(msecsRunning);
    }
    for (CANValue<?> canValue : this.getCANValues()) {
      if (canValue.isDisplay()) {
        display.updateCanValueField(canValue);
        totalUpdates += canValue.getUpdateCount();
      }
    }
    if (latestUpdate == null) {
      latestUpdate = now;
      latestHistoryUpdate = now;
    } else {
      // TODO configure frequency
      if (now.getTime() - latestHistoryUpdate.getTime() >= 500
          && isWithHistory()) {
        latestHistoryUpdate = showHistory(display, now);
      }
      long msecs = now.getTime() - latestUpdate.getTime();
      // every second
      if (msecs >= 1000) {
        long updates = totalUpdates - latestTotalUpdates;
        double fps = 1000.0 * updates / msecs;
        display.updateField("#", totalUpdates, (int) totalUpdates);
        display.updateField("fps", fps, ++fpsUpdateCount);
        display.updateField("# of bufferOverruns", super.bufferOverruns,
            fpsUpdateCount);
        display.updateField("OBDII id", this.getElm327().getId(), 1);
        display.updateField("OBDII description",
            this.getElm327().getDescription(), 1);
        display.updateField("OBDII firmware", this.getElm327().getFirmwareId(),
            1);
        display.updateField("OBDII hardware", this.getElm327().getHardwareId(),
            1);
        latestTotalUpdates = totalUpdates;
        latestUpdate = now;
      }
    }
  }

  /**
   * show the history
   * 
   * @param display
   * @param now
   * @return the now value
   */
  private Date showHistory(CANValueDisplay display, Date now) {
    if (display instanceof JFXTripletDisplay) {
      final JFXTripletDisplay tripletDisplay = (JFXTripletDisplay) display;
      tripletDisplay.updateHistory(cpm.get("SOC"), cpm.get("Range"),
          "SOC/RR over time", "time", "SOC/RR");
    }
    return now;
  }

  /**
   * make the canValues available
   */
  public void setUpCanValues() throws Exception {
    canValues = getCANValues();
  }

  /**
   * start the STM Monitoring
   * 
   * @param display
   *          - a display to use (if any)
   * @param canValues
   *          - the canValues to monitor
   * @param limit
   *          - the maximum number of frames to read
   * @throws Exception
   */
  public void pidMonitor(CANValueDisplay display, List<CANValue<?>> canValues,
      long frameLimit) throws Exception {
    ELM327 lelm = this.getElm327();
    Connection lcon = lelm.getCon();
    // VehicleGroup vg = this.getElm327().getVehicleGroup();
    // FIXME - china clone battery handling?
    // make available on button/menu?
    if (lelm.isSTN()) {
      for (CANValue<?> canValue : canValues) {
        if (canValue.isRead()) {
          Pid pid = canValue.canInfo.getPid();
          // handle ISO-TP based frames differently by direct reading
          if (pid.getIsoTp() != null)
            this.readPid(display, pid);
        }
      }
    }
    this.initOBD();
    if (lelm.isSTN()) {
      this.setSTMFilter(canValues);
      lcon.output("STM");
      setMonitoring(true);
      // regularly update the display
      this.startDisplay(display);
      for (long i = 0; i < frameLimit && isMonitoring(); i++) {
        lcon.getResponse(null);
      }
      this.stopDisplay();
    } else {
      if (debug)
        LOGGER.log(Level.INFO, "super slow China clone loop entered");
      // oh the China loop ... how ugly and slow ...
      int pidFrameLimit = 3;
      if (debug)
        LOGGER.log(Level.INFO,
            String.format("%3d PIDs to loop thru", canValues.size()));
      setMonitoring(true);
      for (long i = 0; i < frameLimit
          && isMonitoring(); i += pidFrameLimit * canValues.size()) {
        for (CANValue<?> canValue : canValues) {
          if (canValue.isRead()) {
            Pid pid = canValue.canInfo.getPid();
            // handle ISO-TP based frames differently by direct reading
            if (pid.getIsoTp() == null) {
              if (debug) {
                LOGGER.log(Level.INFO, String.format("pid %s (%s) ",
                    pid.getPid(), canValue.canInfo.getTitle()));
              }
              super.monitorPid(display, pid.getPid(), pidFrameLimit);
            } // if not ISO-TP
          } // is isREAD
        } // for canValues
      } // for frames
    } // non STN
    if (debug) {
      LOGGER.log(Level.INFO, "PidMonitoring finished");
    }
  }

  /**
   * start the display
   * 
   * @param display
   */
  public void startDisplay(final CANValueDisplay display) {
    if (displayexecutor != null) {
      throw new IllegalStateException("display already started!");
    }
    // TODO make this more systematic
    if (display instanceof JFXTripletDisplay) {
      Map<String, ObservableValue<?>> canBindings = new HashMap<String, ObservableValue<?>>();
      // fixed bindings
      canBindings.put("msecs", this.msecsRunningProperty);
      canBindings.put("vehicleState", this.vehicleStateProperty);
      // property based bindings
      for (CANProperty<?, ?> canProperty : cpm.getCanProperties().values()) {
        String name = canProperty.getName();
        if (debug)
          LOGGER.log(Level.INFO, "binding " + name);
        canBindings.put(name, canProperty.getProperty());
        canBindings.put(name + "-max", canProperty.getMax());
        canBindings.put(name + "-avg", canProperty.getAvg());
      }
      ((JFXTripletDisplay) display).bind(canBindings);
    }
    displayexecutor = Executors.newSingleThreadScheduledExecutor();
    displayStart = new Date();
    displayTask = new Runnable() {
      public void run() {
        // Invoke method(s) to do the work
        try {
          showValues(display);
        } catch (Exception e) {
          ErrorHandler.handle(e);
        }
      }
    };
    // FIXME make configurable
    // update meter value every 200 milliseconds
    displayexecutor.scheduleAtFixedRate(displayTask, 0, 200,
        TimeUnit.MILLISECONDS);
  }

  /**
   * create a csv report according to
   * https://github.com/BITPlan/can4eve/issues/4 and put the result into the
   * given CSV file
   * 
   * @param display
   * @param reportFileName
   *          - the filename to use
   * @param frameLimit
   *          - the maximum number of frame to read
   * @throws Exception
   */
  public void report(CANValueDisplay display, String reportFileName,
      long frameLimit) throws Exception {
    File reportFile = new File(reportFileName);
    PrintWriter printWriter = new PrintWriter(reportFile);
    this.getElm327().identify();
    String isoDate = isoDateFormatter.format(new Date());
    printWriter.write(CSVUtil.csv("date", isoDate));
    String elmCSV = this.getElm327().asCSV();
    printWriter.write(elmCSV);
    printWriter.flush();
    // do we have an STM capable chip?
    if (this.getElm327().isSTN()) {
      this.pidMonitor(display, canValues, frameLimit);
      for (CANValue<?> canValue : cpm.getCANValues()) {
        if (debug)
          LOGGER.log(Level.INFO, "canValue:" + canValue.canInfo.getTitle());
        printWriter.write(canValue.asCSV());
      }
    } else {
      // slow version
      for (CANValue<?> canValue : cpm.getCANValues()) {
        CANInfo canInfo = canValue.canInfo;
        monitorPid(display, canInfo.getPid().getPid(),
            canInfo.getMaxIndex() + 5);
        printWriter.write(canValue.asCSV());
      }
    }
    printWriter.close();
  }

} // OBDTriplet
