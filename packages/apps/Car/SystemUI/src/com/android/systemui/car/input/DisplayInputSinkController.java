/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.car.input;

import static android.car.CarOccupantZoneManager.DISPLAY_TYPE_MAIN;
import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.hardware.power.CarPowerManager;
import android.car.settings.CarSettings;
import android.content.Context;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.ArraySet;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import android.view.Display;
import android.view.MotionEvent;
import android.widget.Toast;

import androidx.annotation.MainThread;
import androidx.annotation.VisibleForTesting;

import com.android.systemui.CoreStartable;
import com.android.systemui.R;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;

import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import javax.inject.Inject;

/**
 * Controls {@link DisplayInputSink}. It can be used for the display input lock or display input
 * monitor.
 * <ul>
 * <li>For the display input lock, it observes for when the setting is changed and starts/stops
 * display input lock window accordingly.
 * <li>For the display input monitor, when the display turns off, it adds the spy window
 * on the display to generate the user activity notification for the wake up.*
 * </ul>
 */
@SysUISingleton
public final class DisplayInputSinkController implements CoreStartable {
    private static final String TAG = "DisplayInputLock";
    // 4 displays would be enough for most systems.
    private static final int INITIAL_INPUT_SINK_CAPACITY = 4;
    static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    private static final Uri DISPLAY_INPUT_LOCK_URI =
            Settings.Global.getUriFor(CarSettings.Global.DISPLAY_INPUT_LOCK);

    private final Context mContext;

    private final CarServiceProvider mCarServiceProvider;
    private final Handler mHandler;
    private final DisplayManager mDisplayManager;
    private final ContentObserver mSettingsObserver;

    // Map of input sinks per display that are currently on going. (key: displayId)
    private final SparseArray<DisplayInputSink> mDisplayInputSinks;

    // Map of input locks that are currently on going. (key: displayId)
    private final ArraySet<Integer> mDisplayInputLockedDisplays;

    // A set of display unique ids from the display input lock setting.
    private final ArraySet<String> mDisplayInputLockSetting;

    // Map of the available passenger displays. (key: displayId)
    private final SparseArray<Display> mPassengerDisplays;

    private CarOccupantZoneManager mOccupantZoneManager;
    private CarPowerManager mCarPowerManager;

    @VisibleForTesting
    final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
        @Override
        @MainThread
        public void onDisplayAdded(int displayId) {
            mayUpdatePassengerDisplayOnAdded(displayId);
            refreshDisplayInputSink(displayId, "onDisplayAdded");
        }

        @Override
        @MainThread
        public void onDisplayRemoved(int displayId) {
            if (!mPassengerDisplays.contains(displayId)) return;
            mayStopDisplayInputLock(mDisplayManager.getDisplay(displayId));
            mayStopDisplayInputMonitor(displayId);
            mPassengerDisplays.remove(displayId);
        }

        @Override
        @MainThread
        public void onDisplayChanged(int displayId) {
            refreshDisplayInputSink(displayId, "onDisplayChanged");
        }
    };

    private void refreshDisplayInputSink(int displayId, String caller) {
        int index = mPassengerDisplays.indexOfKey(displayId);
        if (index < 0) {
            if (DBG) Slog.d(TAG, caller + ": Not a passenger display#" + displayId);
            return;
        }
        decideDisplayInputSink(index);
    }

    @Inject
    public DisplayInputSinkController(Context context, @Main Handler handler,
            CarServiceProvider carServiceProvider) {
        this(context, handler, carServiceProvider,
                new SparseArray<DisplayInputSink>(INITIAL_INPUT_SINK_CAPACITY),
                new ArraySet<Integer>(INITIAL_INPUT_SINK_CAPACITY),
                new ArraySet<String>(INITIAL_INPUT_SINK_CAPACITY),
                new SparseArray<Display>(INITIAL_INPUT_SINK_CAPACITY));
    }

    @VisibleForTesting
    DisplayInputSinkController(Context context, @Main Handler handler,
            CarServiceProvider carServiceProvider,
            SparseArray<DisplayInputSink> displayInputSinks,
            ArraySet<Integer> displayInputLockedDisplays,
            ArraySet<String> displayInputLockSetting,
            SparseArray<Display> passengerDisplays) {
        mContext = context;
        mHandler = handler;
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        mSettingsObserver = new ContentObserver(mHandler) {
            @Override
            @MainThread
            public void onChange(boolean selfChange, Uri uri) {
                if (DBG) Slog.d(TAG, "onChange: self=" + selfChange + ", uri=" + uri);
                refreshDisplayInputLockSetting();
            }
        };
        mCarServiceProvider = carServiceProvider;

        mDisplayInputSinks = displayInputSinks;
        mDisplayInputLockedDisplays = displayInputLockedDisplays;
        mDisplayInputLockSetting = displayInputLockSetting;
        mPassengerDisplays = passengerDisplays;
    }

    @Override
    public void start() {
        if (UserHandle.myUserId() != UserHandle.USER_SYSTEM
                && UserManager.isHeadlessSystemUserMode()) {
            Slog.i(TAG, "Disable DisplayInputSinkController for non system user "
                    + UserHandle.myUserId());
            return;
        }

        mCarServiceProvider.addListener(mCarServiceOnConnectedListener);
        mContext.getContentResolver().registerContentObserver(DISPLAY_INPUT_LOCK_URI,
                /* notifyForDescendants= */ false, mSettingsObserver);
        mDisplayManager.registerDisplayListener(mDisplayListener, mHandler);
    }

    private final CarServiceProvider.CarServiceOnConnectedListener mCarServiceOnConnectedListener =
            new CarServiceProvider.CarServiceOnConnectedListener() {
                @Override
                public void onConnected(Car car) {
                    mOccupantZoneManager = car.getCarManager(CarOccupantZoneManager.class);
                    mCarPowerManager = car.getCarManager(CarPowerManager.class);
                    initPassengerDisplays();
                    refreshDisplayInputLockSetting();
                }
            };

    // Assumes that all main displays for passengers are static.
    private void initPassengerDisplays() {
        List<OccupantZoneInfo> allZones = mOccupantZoneManager.getAllOccupantZones();
        for (int i = allZones.size() - 1; i >= 0; --i) {
            OccupantZoneInfo zone = allZones.get(i);
            if (zone.occupantType == OCCUPANT_TYPE_DRIVER) continue;  // Skip a driver.
            Display display = mOccupantZoneManager.getDisplayForOccupant(zone, DISPLAY_TYPE_MAIN);
            if (display == null) {
                Slog.w(TAG, "Can't access the display of zone=" + zone);
                continue;
            }
            mPassengerDisplays.put(display.getDisplayId(), display);
        }
    }

    private void mayUpdatePassengerDisplayOnAdded(int displayId) {
        if (mPassengerDisplays.contains(displayId)) {
            // Display is already added to the passenger display list.
            return;
        }
        if (mOccupantZoneManager == null) {
            Slog.w(TAG, "CarService isn't connected yet");
            return;
        }
        OccupantZoneInfo zone = mOccupantZoneManager.getOccupantZoneForDisplayId(displayId);
        if (zone == null) {
            Slog.w(TAG, "Can't find the zone info for display#" + displayId);
            return;
        }
        if (zone.occupantType == OCCUPANT_TYPE_DRIVER) {
            // Skip a driver display
            return;
        }
        Display display = mOccupantZoneManager.getDisplayForOccupant(zone, DISPLAY_TYPE_MAIN);
        if (display == null) {
            Slog.w(TAG, "Can't access the display of zone=" + zone);
            return;
        }
        mPassengerDisplays.put(displayId, display);
    }

    // Start/stop display input locks from the current global setting.
    @VisibleForTesting
    void refreshDisplayInputLockSetting() {
        String settingValue = getDisplayInputLockSettingValue();
        parseDisplayInputLockSettingValue(CarSettings.Global.DISPLAY_INPUT_LOCK, settingValue);
        if (DBG) {
            Slog.d(TAG, "refreshDisplayInputLock: settingValue=" + settingValue);
        }
        for (int i = mPassengerDisplays.size() - 1; i >= 0; --i) {
            decideDisplayInputSink(i);
        }
    }

    private void decideDisplayInputSink(int index) {
        int displayId = mPassengerDisplays.keyAt(index);
        Display display = mPassengerDisplays.valueAt(index);
        if (mDisplayInputLockSetting.contains(display.getUniqueId())) {
            mayStopDisplayInputMonitor(displayId);
            mayStartDisplayInputLock(display);
        } else if (Display.isOffState(display.getState())) {
            mayStopDisplayInputLock(display);
            mayStartDisplayInputMonitor(display);
        } else {
            mayStopDisplayInputLock(display);
            mayStopDisplayInputMonitor(displayId);
        }
    }

    private String getDisplayInputLockSettingValue() {
        return Settings.Global.getString(mContext.getContentResolver(),
                CarSettings.Global.DISPLAY_INPUT_LOCK);
    }

    private void parseDisplayInputLockSettingValue(@NonNull String settingKey,
            @Nullable String value) {
        mDisplayInputLockSetting.clear();
        if (value == null || value.isEmpty()) {
            return;
        }

        String[] entries = value.split(",");
        int numEntries = entries.length;
        mDisplayInputLockSetting.ensureCapacity(numEntries);
        for (int i = 0; i < numEntries; i++) {
            String uniqueId = entries[i];
            if (findDisplayIdByUniqueId(uniqueId) == Display.INVALID_DISPLAY) {
                Slog.w(TAG, "Invalid display id: " + uniqueId);
                continue;
            }
            mDisplayInputLockSetting.add(uniqueId);
        }
    }

    private int findDisplayIdByUniqueId(@NonNull String displayUniqueId) {
        for (int i = mPassengerDisplays.size() - 1; i >= 0; --i) {
            Display display = mPassengerDisplays.valueAt(i);
            if (displayUniqueId.equals(display.getUniqueId())) {
                return display.getDisplayId();
            }
        }
        return Display.INVALID_DISPLAY;
    }

    private boolean isDisplayInputLockStarted(int displayId) {
        return mDisplayInputLockedDisplays.contains(displayId);
    }

    private boolean isDisplayInputMonitorStarted(int displayId) {
        return !isDisplayInputLockStarted(displayId) && mDisplayInputSinks.get(displayId) != null;
    }

    @VisibleForTesting
    void mayStartDisplayInputLock(@NonNull Display display) {
        int displayId = display.getDisplayId();
        if (isDisplayInputLockStarted(displayId)) {
            // Already started input lock for the given display.
            if (DBG) Slog.d(TAG, "Input lock is already started for display#" + displayId);
            return;
        }

        Slog.i(TAG, "Start input lock for display " + displayId);
        mDisplayInputLockedDisplays.add(displayId);
        Context displayContext = mContext.createDisplayContext(display);
        AtomicReference<Toast> toastRef = new AtomicReference<>(null);
        UnaryOperator<Toast> cancelToast = (toast) -> {
            toast.cancel();
            return toast;
        };
        UnaryOperator<Toast> createToast = (toast) -> Toast.makeText(displayContext,
                R.string.display_input_lock_text, Toast.LENGTH_SHORT);
        DisplayInputSink.OnInputEventListener callback = (event) -> {
            if (DBG) {
                Slog.d(TAG, "Received input events while input is locked for display#"
                        + event.getDisplayId());
            }
            if (mCarPowerManager != null) {
                mCarPowerManager.notifyUserActivity(event.getDisplayId());
            }
            Runnable r = () -> {
                // MotionEvents for clicks are ACTION_DOWN + ACTION_UP
                // Only capture one of those events so the Toast shows once per click
                if (event instanceof MotionEvent
                        && ((MotionEvent) event).getAction() == MotionEvent.ACTION_DOWN) {
                    if (toastRef.get() != null) {
                        toastRef.updateAndGet(cancelToast);
                    }
                    toastRef.updateAndGet(createToast).show();
                }
            };
            mHandler.post(r);
        };
        mDisplayInputSinks.put(displayId, new DisplayInputSink(display, callback));
        // Now that the display input lock is started, let's inform the user of it.
        mHandler.post(() -> Toast.makeText(displayContext, R.string.display_input_lock_started_text,
                Toast.LENGTH_SHORT).show());

    }

    private void mayStartDisplayInputMonitor(Display display) {
        int displayId = display.getDisplayId();
        if (isDisplayInputMonitorStarted(displayId)) {
            // Already started input monitor for the given display.
            if (DBG) Slog.d(TAG, "Input monitor is already started for display#" + displayId);
            return;
        }

        Slog.i(TAG, "Start input monitor for display#" + displayId);
        DisplayInputSink.OnInputEventListener callback = (event) -> {
            if (DBG) {
                Slog.d(TAG, "Received input events for monitored display#"
                        + event.getDisplayId());
            }
            if (mCarPowerManager != null) {
                mCarPowerManager.notifyUserActivity(event.getDisplayId());
            }
        };
        mDisplayInputSinks.put(displayId, new DisplayInputSink(display, callback));
    }

    @VisibleForTesting
    void mayStopDisplayInputLock(Display display) {
        int displayId = display.getDisplayId();
        if (!isDisplayInputLockStarted(displayId)) {
            if (DBG) Slog.d(TAG, "There is no input lock started for display#" + displayId);
            return;
        }
        Slog.i(TAG, "Stop input lock for display#" + displayId);
        mHandler.post(() -> Toast.makeText(mContext.createDisplayContext(display),
                R.string.display_input_lock_stopped_text, Toast.LENGTH_SHORT).show());
        removeDisplayInputSink(displayId);
        mDisplayInputLockedDisplays.remove(displayId);
    }

    private void mayStopDisplayInputMonitor(int displayId) {
        if (!isDisplayInputMonitorStarted(displayId)) {
            if (DBG) Slog.d(TAG, "There is no input monitor started for display#" + displayId);
            return;
        }
        Slog.i(TAG, "Stop input monitor for display#" + displayId);
        removeDisplayInputSink(displayId);
    }

    private void removeDisplayInputSink(int displayId) {
        int index = mDisplayInputSinks.indexOfKey(displayId);
        if (index < 0) {
            throw new IllegalStateException("Can't find the input sink for display#" + displayId);
        }
        DisplayInputSink inputLock = mDisplayInputSinks.valueAt(index);
        inputLock.release();
        mDisplayInputSinks.removeAt(index);
    }

    @Override
    public void dump(@NonNull PrintWriter pw, @NonNull String[] args) {
        pw.println("DisplayInputSinks:");
        int size = mDisplayInputSinks.size();
        for (int i = 0; i < size; i++) {
            DisplayInputSink inputSink = mDisplayInputSinks.valueAt(i);
            pw.printf("  %d: %s\n", i, inputSink.toString());
        }

        pw.println("DisplayInputLockedWindows:");
        size = mDisplayInputLockedDisplays.size();
        for (int i = 0; i < size; i++) {
            pw.printf("  %s\n", mDisplayInputLockedDisplays.valueAt(i).toString());
        }

        pw.printf("DisplayInputLockSetting: %s\n", mDisplayInputLockSetting);
        pw.print("PassegnerDisplays: [");
        for (int i = mPassengerDisplays.size() - 1; i >= 0; --i) {
            pw.print(mPassengerDisplays.keyAt(i));
            if (i > 0) pw.print(", ");
        }
        pw.println(']');
    }
}
