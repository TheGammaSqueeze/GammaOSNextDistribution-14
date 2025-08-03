/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.platform.helpers;

import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_IDLING;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_MOVING;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_PARKED;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.car.Car;
import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.CarDrivingStateManager;
import android.content.Context;

import androidx.test.InstrumentationRegistry;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VehicleHardKeysHelperImpl extends AbstractStandardAppHelper
        implements IAutoVehicleHardKeysHelper {

    private static final String ENABLE_DRIVING_MODE =
            "cmd car_service " + "inject-vhal-event 0x11400400 8";
    private static final String ENABLE_PARKING_MODE =
            "cmd car_service " + "inject-vhal-event 0x11400400 4";
    private static final String SET_SPEED =
            "cmd car_service " + "inject-vhal-event 0x11600207 %s -t 2000";

    private static final int WAIT_TIME_SEC = 2;

    private Car mCar;
    private Context mContext;
    private CarDrivingStateManager mCarDrivingStateManager;
    private final UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    public VehicleHardKeysHelperImpl(Instrumentation instr) {
        super(instr);
        mContext = InstrumentationRegistry.getContext();
        mUiAutomation.adoptShellPermissionIdentity("android.car.permission.CAR_DRIVING_STATE");
        mCar = Car.createCar(mContext);
        mCarDrivingStateManager =
                (CarDrivingStateManager) mCar.getCarManager(Car.CAR_DRIVING_STATE_SERVICE);
    }

    /** {@inheritDoc} */
    @Override
    public void dismissInitialDialogs() {
        // Nothing to dismiss
    }

    /** {@inheritDoc} */
    @Override
    public String getPackage() {
        return "";
    }

    /** {@inheritDoc} */
    @Override
    public void open() {
        // Noting to open
    }

    /** {@inheritDoc} */
    @Override
    public String getLauncherName() {
        throw new RuntimeException("Not Supported");
    }

    /** {@inheritDoc} */
    public void pressRecieveCallKey() { // NOTYPO
        getSpectatioUiUtil().executeShellCommand("input keyevent KEYCODE_CALL");
    }

    /** {@inheritDoc} */
    public void pressEndCallKey() {
        getSpectatioUiUtil().executeShellCommand("input keyevent KEYCODE_ENDCALL");
    }

    /** {@inheritDoc} */
    public void pressMediaNextTrackKey() {
        getSpectatioUiUtil().executeShellCommand("input keyevent KEYCODE_MEDIA_NEXT");
    }

    /** {@inheritDoc} */
    public void pressMediaPreviousTrackKey() {
        getSpectatioUiUtil().executeShellCommand("input keyevent KEYCODE_MEDIA_PREVIOUS");
    }

    /** {@inheritDoc} */
    public void tuneVolumeUpKey() {
        getSpectatioUiUtil().executeShellCommand("input keyevent KEYCODE_VOLUME_UP");
    }

    /** {@inheritDoc} */
    public void tuneVolumeDownKey() {
        getSpectatioUiUtil().executeShellCommand("input keyevent KEYCODE_VOLUME_DOWN");
    }

    /** {@inheritDoc} */
    public void pressBrightnessUpKey() {
        getSpectatioUiUtil().executeShellCommand("input keyevent KEYCODE_BRIGHTNESS_UP");
    }

    /** {@inheritDoc} */
    public void pressBrightnessDownKey() {
        getSpectatioUiUtil().executeShellCommand("input keyevent KEYCODE_BRIGHTNESS_DOWN");
    }

    /** {@inheritDoc} */
    public void pressAssistantKey() {
        throw new RuntimeException("Not Supported");
    }

    /** {@inheritDoc} */
    public void tuneMuteKey() {
        getSpectatioUiUtil().executeShellCommand("input keyevent KEYCODE_VOLUME_MUTE");
    }

    /** {@inheritDoc} */
    public void pressScreenOffKey() {
        throw new RuntimeException("Not Supported");
    }

    /** {@inheritDoc} */
    public void tuneKnobKey() {
        throw new RuntimeException("Not Supported");
    }

    /** {@inheritDoc} */
    public void pressKnobButtonKey() {
        throw new RuntimeException("Not Supported");
    }

    /** {@inheritDoc} */
    public void tuneVolumeKnobKey() {
        throw new RuntimeException("Not Supported");
    }

    /** {@inheritDoc} */
    public void pressVolumeKnobButtonKey() {
        throw new RuntimeException("Not Supported");
    }

    /** {@inheritDoc} */
    public int getCurrentVolumeLevel(VolumeType type) {
        String volumeType = "";
        switch (type) {
            case Media:
                volumeType = "MUSIC";
                break;
            case Ring:
                volumeType = "RING";
                break;
            case Notification:
                volumeType = "NOTIFICATION";
                break;
            case Navigation:
                volumeType = "NAVIGATION";
                break;
        }
        String cmd = "dumpsys car_service";
        String res = getSpectatioUiUtil().executeShellCommand(cmd);
        Pattern p =
                Pattern.compile(
                        "(?s).+CarVolumeGroup.+?"
                                + volumeType
                                + ".+?Gain values.+?"
                                + "([0-9]+)\\s+([0-9]+)\\s+([0-9]+)\\s+([0-9]+).+");
        Matcher m = p.matcher(res);
        if (m.matches()) {
            /*
                Four volumes can be obtained from the match:
                1: min, 2: max, 3: default, 4: current
            */
            return Integer.parseInt(m.group(4));
        } else {
            throw new RuntimeException("Cannot find current volume");
        }
    }

    /** {@inheritDoc} */
    public DrivingState getDrivingState() {
        CarDrivingStateEvent event = mCarDrivingStateManager.getCurrentCarDrivingState();
        switch (event.eventValue) {
            case DRIVING_STATE_IDLING:
                return DrivingState.IDLING;
            case DRIVING_STATE_MOVING:
                return DrivingState.MOVING;
            case DRIVING_STATE_PARKED:
                return DrivingState.PARKED;
        }
        return DrivingState.UNKNOWN;
    }

    /** {@inheritDoc} */
    public void setDrivingState(DrivingState state) {
        if (state == DrivingState.UNKNOWN) {
            throw new RuntimeException("State not supported");
        } else if (state == DrivingState.PARKED) {
            getSpectatioUiUtil().executeShellCommand(ENABLE_PARKING_MODE);
        } else {
            getSpectatioUiUtil().executeShellCommand(ENABLE_DRIVING_MODE);
        }
        getSpectatioUiUtil().waitNSeconds(WAIT_TIME_SEC);
    }

    /** {@inheritDoc} */
    public void setSpeed(int speed) {
        if (getDrivingState() == DrivingState.PARKED) {
            throw new RuntimeException("Car is in parking mode.");
        } else {
            getSpectatioUiUtil().executeShellCommand(String.format(SET_SPEED, speed));
        }
        getSpectatioUiUtil().waitNSeconds(WAIT_TIME_SEC);
    }
}
