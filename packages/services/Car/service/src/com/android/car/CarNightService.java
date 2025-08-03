/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.car;

import static android.car.settings.CarSettings.Global.FORCED_DAY_NIGHT_MODE;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.IntDef;
import android.app.UiModeManager;
import android.car.builtin.util.Slogf;
import android.car.feature.Flags;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarPropertyEventListener;
import android.content.Context;
import android.database.ContentObserver;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.proto.ProtoOutputStream;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/**
 * Class used to handle events used to set vehicle in night mode.
 */
public class CarNightService implements CarServiceBase {

    public static final boolean DBG = false;

    @IntDef({FORCED_SENSOR_MODE, FORCED_DAY_MODE, FORCED_NIGHT_MODE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DayNightSensorMode {}

    public static final int FORCED_SENSOR_MODE = 0;
    public static final int FORCED_DAY_MODE = 1;
    public static final int FORCED_NIGHT_MODE = 2;

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private int mNightSetting = UiModeManager.MODE_NIGHT_YES;
    @GuardedBy("mLock")
    private int mForcedMode = FORCED_SENSOR_MODE;
    @GuardedBy("mLock")
    private long mLastSensorEventTime = -1;
    private final Context mContext;
    @GuardedBy("mLock")
    private final UiModeManager mUiModeManager;
    private final CarPropertyService mCarPropertyService;

    private final ICarPropertyEventListener mICarPropertyEventListener =
            new ICarPropertyEventListener.Stub() {
                @Override
                public void onEvent(List<CarPropertyEvent> events) throws RemoteException {
                    synchronized (mLock) {
                        for (CarPropertyEvent event : events) {
                            onNightModeCarPropertyEventLocked(event);
                        }
                    }
                }
            };

    private final ContentObserver mForcedNightModeObserver =
            new ContentObserver(null /* handler */) {
                @Override
                public void onChange(boolean selfChange) {
                    synchronized (mLock) {
                        forceDayNightModeInternal(getForcedDayNightModeSetting());
                    }
                }
            };

    /**
     * Acts on {@link CarPropertyEvent} events marked with {@link
     * CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE} and marked with {@link
     * VehicleProperty.NIGHT_MODE} by setting the vehicle in night mode.
     * <p>
     * This method does nothing if the event parameter is {@code null}.
     *
     * @param event the car property event to be handled
     */
    @GuardedBy("mLock")
    private void onNightModeCarPropertyEventLocked(CarPropertyEvent event) {
        if (event == null) {
            return;
        }
        if (event.getEventType() == CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE) {
            // Only handle onChange events
            CarPropertyValue value = event.getCarPropertyValue();
            if (value.getPropertyId() == VehicleProperty.NIGHT_MODE
                    && value.getTimestamp() > mLastSensorEventTime) {
                mLastSensorEventTime = value.getTimestamp();
                boolean nightMode = (Boolean) value.getValue();
                Slogf.i(CarLog.TAG_SENSOR, "Set dayNight Mode as "
                        + nightMode + " at timestamp: " + mLastSensorEventTime);
                setNightModeLocked(nightMode);
            }
        }
    }

    @GuardedBy("mLock")
    private void setNightModeLocked(boolean nightMode) {
        if (nightMode) {
            mNightSetting = UiModeManager.MODE_NIGHT_YES;
            if (DBG) Slogf.d(CarLog.TAG_SENSOR, "CAR dayNight handleSensorEvent NIGHT");
        } else {
            mNightSetting = UiModeManager.MODE_NIGHT_NO;
            if (DBG) Slogf.d(CarLog.TAG_SENSOR, "CAR dayNight handleSensorEvent DAY");
        }
        if (mUiModeManager != null && (mForcedMode == FORCED_SENSOR_MODE)) {
            mUiModeManager.setNightMode(mNightSetting);
            if (DBG) Slogf.d(CarLog.TAG_SENSOR, "CAR dayNight handleSensorEvent APPLIED");
        } else {
            if (DBG) Slogf.d(CarLog.TAG_SENSOR, "CAR dayNight handleSensorEvent IGNORED");
        }
    }

    @DayNightSensorMode
    private int getForcedDayNightModeSetting() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                FORCED_DAY_NIGHT_MODE, FORCED_SENSOR_MODE);
    }

    /**
     * Sets {@link UiModeManager} to night mode according to the {@link DayNightSensorMode} passed
     * as parameter.
     *
     * @param mode the sensor mode used to set vehicle in night mode
     * @return the current night mode, or {@code -1} on error
     */
    public int forceDayNightMode(@DayNightSensorMode int mode) {
        synchronized (mLock) {
            return forceDayNightModeInternal(mode);
        }
    }

    @GuardedBy("mLock")
    private int forceDayNightModeInternal(@DayNightSensorMode int mode) {
        if (mUiModeManager == null) {
            return -1;
        }
        int resultMode;
        switch (mode) {
            case FORCED_SENSOR_MODE:
                resultMode = mNightSetting;
                mForcedMode = FORCED_SENSOR_MODE;
                break;
            case FORCED_DAY_MODE:
                resultMode = UiModeManager.MODE_NIGHT_NO;
                mForcedMode = FORCED_DAY_MODE;
                break;
            case FORCED_NIGHT_MODE:
                resultMode = UiModeManager.MODE_NIGHT_YES;
                mForcedMode = FORCED_NIGHT_MODE;
                break;
            default:
                Slogf.e(CarLog.TAG_SENSOR, "Unknown forced day/night mode " + mode);
                return -1;
        }
        mUiModeManager.setNightMode(resultMode);
        return mUiModeManager.getNightMode();
    }

    CarNightService(Context context, CarPropertyService propertyService) {
        mContext = context;
        mCarPropertyService = propertyService;
        mUiModeManager = (UiModeManager) mContext.getSystemService(Context.UI_MODE_SERVICE);
        if (mUiModeManager == null) {
            Slogf.w(CarLog.TAG_SENSOR, "Failed to get UI_MODE_SERVICE");
        }
    }

    @Override
    public void init() {
        if (DBG) {
            Slogf.d(CarLog.TAG_SENSOR, "CAR dayNight init.");
        }
        synchronized (mLock) {
            if (Flags.carNightGlobalSetting()) {
                mContext.getContentResolver().registerContentObserver(
                        Settings.Global.getUriFor(FORCED_DAY_NIGHT_MODE),
                        false /* notifyForDescendants */,
                        mForcedNightModeObserver);
                mForcedMode = getForcedDayNightModeSetting();
                if (mForcedMode != FORCED_SENSOR_MODE) {
                    // Only update the mode if it's not currently sensor mode. If it is sensor mode,
                    // it will be updated by the setNightModeLocked call below.
                    forceDayNightModeInternal(mForcedMode);
                }
            }

            mCarPropertyService.registerListenerSafe(VehicleProperty.NIGHT_MODE, 0,
                    mICarPropertyEventListener);
            CarPropertyValue propertyValue = mCarPropertyService.getPropertySafe(
                    VehicleProperty.NIGHT_MODE, 0);
            if (propertyValue != null && propertyValue.getTimestamp() != 0) {
                mLastSensorEventTime = propertyValue.getTimestamp();
                setNightModeLocked((Boolean) propertyValue.getValue());
            } else {
                Slogf.w(CarLog.TAG_SENSOR, "Failed to get value of NIGHT_MODE");
                setNightModeLocked(true);
            }
        }
    }

    @Override
    public void release() {
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        synchronized (mLock) {
            writer.println("*DAY NIGHT POLICY*");
            writer.println(
                    "Mode:" + ((mNightSetting == UiModeManager.MODE_NIGHT_YES) ? "night" : "day"));
            writer.println("Forced Mode? " + (mForcedMode == FORCED_SENSOR_MODE
                    ? "false, timestamp of dayNight sensor is: " + mLastSensorEventTime
                    : (mForcedMode == FORCED_DAY_MODE ? "day" : "night")));
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {}
}
