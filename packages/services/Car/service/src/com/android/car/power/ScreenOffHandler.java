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

package com.android.car.power;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DEBUGGING_CODE;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.IntDef;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.ICarOccupantZoneCallback;
import android.car.builtin.os.HandlerHelper;
import android.car.builtin.util.Slogf;
import android.car.builtin.view.DisplayHelper;
import android.car.settings.CarSettings;
import android.content.Context;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.proto.ProtoOutputStream;
import android.view.Display;

import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.CarOccupantZoneService;
import com.android.car.R;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.power.CarPowerDumpProto.ScreenOffHandlerProto;
import com.android.car.power.CarPowerDumpProto.ScreenOffHandlerProto.DisplayPowerInfoProto;
import com.android.car.systeminterface.SystemInterface;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.List;

class ScreenOffHandler {
    private static final String TAG = CarLog.tagFor(ScreenOffHandler.class);

    // Minimum and maximum timeout in milliseconds when there is no user.
    private static final int MIN_NO_USER_SCREEN_OFF_TIMEOUT_MS = 15 * 1000; // 15 seconds
    private static final int MAX_NO_USER_SCREEN_OFF_TIMEOUT_MS = 10 * 60 * 1000; // 10 minutes

    private static final String DISPLAY_POWER_MODE_SETTING =
            CarSettings.Global.DISPLAY_POWER_MODE;
    private static final Uri DISPLAY_POWER_MODE_URI =
            Settings.Global.getUriFor(DISPLAY_POWER_MODE_SETTING);

    // Constants for display power mode
    /**
     * Display power mode is unknown. After initialization, needs to be
     * replaced with other mode as below.
     */
    @VisibleForTesting
    static final int DISPLAY_POWER_MODE_NONE = -1;
    /**
     * With this mode, screen keeps off.
     * And user cannot manually turn on the display.
     */
    @VisibleForTesting
    static final int DISPLAY_POWER_MODE_OFF = 0;
    /**
     * With this mode, two kinds of behavior is applied.
     * When user logged out, screen off timeout involves.
     * When user logged in, screen keeps on.
     * And user can manually turn off the display.
     */
    @VisibleForTesting
    static final int DISPLAY_POWER_MODE_ON = 1;
    /**
     * With this mode, screen keeps on.
     * And user can manually turn off the display.
     */
    @VisibleForTesting
    static final int DISPLAY_POWER_MODE_ALWAYS_ON = 2;
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "DISPLAY_POWER_MODE_", value = {
            DISPLAY_POWER_MODE_NONE,
            DISPLAY_POWER_MODE_OFF,
            DISPLAY_POWER_MODE_ON,
            DISPLAY_POWER_MODE_ALWAYS_ON,
    })
    @Target({ElementType.TYPE_USE})
    private @interface DisplayPowerMode {}

    private final Context mContext;
    private final SystemInterface mSystemInterface;
    private final CarOccupantZoneService mOccupantZoneService;
    private final SettingsObserver mSettingsObserver;
    private final EventHandler mEventHandler;
    private final ClockInterface mClock;

    private final boolean mIsAutoPowerSaving;
    private final int mNoUserScreenOffTimeoutMs;
    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final SparseArray<DisplayPowerInfo> mDisplayPowerInfos = new SparseArray<>();

    @GuardedBy("mLock")
    private boolean mBootCompleted;

    ScreenOffHandler(Context context, SystemInterface systemInterface, Looper looper) {
        this(context, systemInterface, looper, SystemClock::uptimeMillis);
    }

    @VisibleForTesting
    ScreenOffHandler(Context context, SystemInterface systemInterface, Looper looper,
            ClockInterface clock) {
        mContext = context;
        mEventHandler = new EventHandler(looper, this);
        mSystemInterface = systemInterface;
        mClock = clock;
        mSettingsObserver = new SettingsObserver(mEventHandler);
        mOccupantZoneService = CarLocalServices.getService(CarOccupantZoneService.class);
        mIsAutoPowerSaving = mContext.getResources().getBoolean(
                R.bool.config_enablePassengerDisplayPowerSaving);
        mNoUserScreenOffTimeoutMs = getNoUserScreenOffTimeout();
    }

    void init() {
        if (!mIsAutoPowerSaving) {
            return;
        }
        initializeDisplayPowerInfos();
        initializeDefaultSettings();
        mOccupantZoneService.registerCallback(mOccupantZoneCallback);
        mContext.getContentResolver().registerContentObserver(
                DISPLAY_POWER_MODE_URI, /* notifyForDescendants= */ false, mSettingsObserver);
        mSystemInterface.scheduleActionForBootCompleted(() -> {
            synchronized (mLock) {
                mBootCompleted = true;
                updateSettingsLocked();
                long eventTime = mClock.uptimeMillis();
                for (int i = 0; i < mDisplayPowerInfos.size(); i++) {
                    int displayId = mDisplayPowerInfos.keyAt(i);
                    updateUserActivityLocked(displayId, eventTime);
                }
            }
        }, Duration.ZERO);
    }

    void handleDisplayStateChange(int displayId, boolean on) {
        if (!mIsAutoPowerSaving) {
            return;
        }
        if (on) {
            synchronized (mLock) {
                updateUserActivityLocked(displayId, mClock.uptimeMillis());
            }
        }
    }

    void updateUserActivity(int displayId, long eventTime) {
        synchronized (mLock) {
            updateUserActivityLocked(displayId, eventTime);
        }
    }

    @GuardedBy("mLock")
    private void updateUserActivityLocked(int displayId, long eventTime) {
        if (!mIsAutoPowerSaving) {
            return;
        }
        if (eventTime > mClock.uptimeMillis()) {
            throw new IllegalArgumentException("event time must not be in the future");
        }
        DisplayPowerInfo info = mDisplayPowerInfos.get(displayId);
        if (info == null) {
            Slogf.w(TAG, "Display(id: %d) is not available", displayId);
            return;
        }
        info.setLastUserActivityTime(eventTime);
        updateDisplayPowerStateLocked(info);
    }

    @GuardedBy("mLock")
    private void handleSettingsChangedLocked() {
        updateSettingsLocked();
        updateAllDisplayPowerStateLocked();
    }

    boolean canTurnOnDisplay(int displayId) {
        if (!mIsAutoPowerSaving) {
            return true;
        }
        synchronized (mLock) {
            return canTurnOnDisplayLocked(displayId);
        }
    }

    @GuardedBy("mLock")
    private boolean canTurnOnDisplayLocked(int displayId) {
        DisplayPowerInfo info = mDisplayPowerInfos.get(displayId);
        if (info == null) {
            Slogf.w(TAG, "display(%d) power info is not ready yet.", displayId);
            return false;
        }
        if (info.getMode() == DISPLAY_POWER_MODE_OFF) {
            return false;
        }
        return true;
    }

    private void initializeDefaultSettings() {
        String setting = Settings.Global.getString(mContext.getContentResolver(),
                DISPLAY_POWER_MODE_SETTING);
        if (!TextUtils.isEmpty(setting)) {
            Slogf.d(TAG, "stored value of %s: %s", DISPLAY_POWER_MODE_SETTING, setting);
            return;
        }
        // At first boot, initialize default setting value
        StringBuilder sb = new StringBuilder();
        synchronized (mLock) {
            for (int i = 0; i < mDisplayPowerInfos.size(); i++) {
                int displayId = mDisplayPowerInfos.keyAt(i);
                DisplayPowerInfo info = mDisplayPowerInfos.valueAt(i);
                if (info == null) {
                    continue;
                }
                int displayPort = getDisplayPort(displayId);
                if (displayPort == DisplayHelper.INVALID_PORT) {
                    continue;
                }
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(displayPort);
                sb.append(':');
                if (info.isDriverDisplay()) {
                    // for driver display
                    info.setMode(DISPLAY_POWER_MODE_ALWAYS_ON);
                    sb.append(DISPLAY_POWER_MODE_ALWAYS_ON);
                } else {
                    // TODO(b/274050716): Restore passenger displays to ON.
                    // for passenger display
                    info.setMode(DISPLAY_POWER_MODE_ALWAYS_ON);
                    sb.append(DISPLAY_POWER_MODE_ALWAYS_ON);
                }
            }
        }
        Settings.Global.putString(
                mContext.getContentResolver(), DISPLAY_POWER_MODE_SETTING, sb.toString());
    }

    @GuardedBy("mLock")
    private void updateSettingsLocked() {
        String setting = Settings.Global.getString(mContext.getContentResolver(),
                DISPLAY_POWER_MODE_SETTING);
        SparseIntArray mapping = parseModeAssignmentSettingValue(setting);
        if (mapping == null) {
            Slogf.d(TAG, "Failed to parse [%s]", setting);
            initializeDefaultSettings();
            return;
        }
        for (int i = 0; i < mapping.size(); i++) {
            int displayId = mapping.keyAt(i);
            @DisplayPowerMode int mode = mapping.valueAt(i);
            DisplayPowerInfo info = mDisplayPowerInfos.get(displayId);
            if (info != null) {
                // Check if the mode in the corresponding display power info is the same as current
                // setting value.
                if (info.getMode() != mode) {
                    info.setMode(mode);
                    boolean on = mode != DISPLAY_POWER_MODE_OFF;
                    // Update last user activity time due to mode change by driver
                    info.setLastUserActivityTime(mClock.uptimeMillis());
                    mEventHandler.post(() -> {
                        handleSetDisplayState(displayId, on);
                    });
                }
            } else {
                Slogf.d(TAG, "No matching DisplayPowerInfo(display=%d)", displayId);
            }
        }
    }

    @GuardedBy("mLock")
    private void updateAllDisplayPowerStateLocked() {
        for (int i = 0; i < mDisplayPowerInfos.size(); i++) {
            updateDisplayPowerStateLocked(mDisplayPowerInfos.valueAt(i));
        }
    }

    @GuardedBy("mLock")
    private void updateDisplayPowerStateLocked(DisplayPowerInfo info) {
        int displayId = info.getDisplayId();
        mEventHandler.cancelUserActivityTimeout(displayId);

        if (!mBootCompleted
                || info == null
                || info.isDriverDisplay()
                || info.getUserId() != CarOccupantZoneManager.INVALID_USER_ID
                || info.getMode() == DISPLAY_POWER_MODE_ALWAYS_ON
                || !mSystemInterface.isDisplayEnabled(displayId)) {
            return;
        }

        checkUserActivityTimeout(info);
    }

    private void checkUserActivityTimeout(DisplayPowerInfo info) {
        long now = mClock.uptimeMillis();
        long nextTimeout = info.getLastUserActivityTime() + mNoUserScreenOffTimeoutMs;
        if (now < nextTimeout) {
            mEventHandler.handleUserActivityTimeout(info.getDisplayId(), nextTimeout);
        }
    }

    private void handleSetDisplayState(int displayId, boolean on) {
        if (on != mSystemInterface.isDisplayEnabled(displayId)) {
            mSystemInterface.setDisplayState(displayId, on);
        }
    }

    private final ICarOccupantZoneCallback mOccupantZoneCallback =
            new ICarOccupantZoneCallback.Stub() {
                @Override
                public void onOccupantZoneConfigChanged(int flags) {
                    if ((flags & (CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_DISPLAY
                            | CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER)) != 0) {
                        synchronized (mLock) {
                            handleOccupantZoneConfigChangeLocked(flags);
                            updateAllDisplayPowerStateLocked();
                        }
                    }
                }
            };

    private final class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            synchronized (mLock) {
                handleSettingsChangedLocked();
            }
        }
    }

    /**
     * Updates display power info if user occupancy is changed or if display is added or removed.
     */
    @GuardedBy("mLock")
    private void handleOccupantZoneConfigChangeLocked(int flags) {
        List<OccupantZoneInfo> occupantZoneInfos = mOccupantZoneService.getAllOccupantZones();
        for (int i = 0; i < occupantZoneInfos.size(); i++) {
            OccupantZoneInfo zoneInfo = occupantZoneInfos.get(i);
            int zoneId = zoneInfo.zoneId;
            int displayId = getMainTypeDisplayId(zoneId);
            if (displayId == Display.INVALID_DISPLAY) {
                Slogf.w(TAG, "No main display associated with occupant zone(id: %d)", zoneId);
                continue;
            }
            DisplayPowerInfo info = mDisplayPowerInfos.get(displayId);
            if ((flags & CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER) != 0
                    && info != null) {
                int userId = mOccupantZoneService.getUserForOccupant(zoneId);
                if (info.getUserId() != userId) {
                    if (userId == CarOccupantZoneManager.INVALID_USER_ID) {
                        // User logged out
                        info.setUserId(CarOccupantZoneManager.INVALID_USER_ID);
                        info.setLastUserActivityTime(mClock.uptimeMillis());
                    } else {
                        // User logged in
                        info.setUserId(userId);
                    }
                }
            }
            if ((flags & CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_DISPLAY) != 0
                    && info == null) {
                info = createDisplayPowerInfoLocked(displayId);
                if (info != null) {
                    // Display added
                    int userId = mOccupantZoneService.getUserForOccupant(zoneId);
                    info.setUserId(userId);
                }
            }
        }
        if ((flags & CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_DISPLAY) != 0) {
            for (int i = 0; i < mDisplayPowerInfos.size(); i++) {
                DisplayPowerInfo info = mDisplayPowerInfos.valueAt(i);
                if (info != null
                        && mOccupantZoneService.getDisplayType(info.getDisplayId())
                                == CarOccupantZoneManager.DISPLAY_TYPE_UNKNOWN) {
                    // Display removed
                    mDisplayPowerInfos.removeAt(i);
                }
            }
        }
    }

    private void initializeDisplayPowerInfos() {
        List<OccupantZoneInfo> occupantZoneInfos = mOccupantZoneService.getAllOccupantZones();
        synchronized (mLock) {
            for (int i = 0; i < occupantZoneInfos.size(); i++) {
                OccupantZoneInfo zoneInfo = occupantZoneInfos.get(i);
                int zoneId = zoneInfo.zoneId;
                int displayId = getMainTypeDisplayId(zoneId);
                if (displayId == Display.INVALID_DISPLAY) {
                    continue;
                }
                DisplayPowerInfo info = createDisplayPowerInfoLocked(displayId);
                int userId = mOccupantZoneService.getUserForOccupant(zoneId);
                info.setUserId(userId);
                if (zoneInfo.occupantType == CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER) {
                    info.setDriverDisplay(true);
                }
            }
        }
    }

    @GuardedBy("mLock")
    private DisplayPowerInfo createDisplayPowerInfoLocked(int displayId) {
        DisplayPowerInfo info = new DisplayPowerInfo(displayId);
        mDisplayPowerInfos.put(displayId, info);
        return info;
    }

    private int getMainTypeDisplayId(int zoneId) {
        return mOccupantZoneService.getDisplayForOccupant(zoneId,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
    }

    // value format: comma-separated displayPort:mode
    @VisibleForTesting
    SparseIntArray parseModeAssignmentSettingValue(String value) {
        SparseIntArray mapping = new SparseIntArray();
        try {
            String[] entries = value.split(",");
            for (int i = 0; i < entries.length; i++) {
                String entry = entries[i];
                String[] pair = entry.split(":");
                if (pair.length != 2) {
                    return null;
                }
                int displayPort = Integer.parseInt(pair[0], /* radix= */ 10);
                int displayId = getDisplayId(displayPort);
                if (displayId == Display.INVALID_DISPLAY) {
                    Slogf.w(TAG, "Invalid display port: %d", displayPort);
                    return null;
                }
                @DisplayPowerMode int mode = Integer.parseInt(pair[1], /* radix= */ 10);
                if (mapping.indexOfKey(displayId) >= 0) {
                    Slogf.w(TAG, "Multiple use of display id: %d", displayId);
                    return null;
                }
                if (mode < DISPLAY_POWER_MODE_OFF || mode > DISPLAY_POWER_MODE_ALWAYS_ON) {
                    Slogf.w(TAG, "Mode is out of range: %d(%s)",
                            mode, DisplayPowerInfo.displayPowerModeToString(mode));
                    return null;
                }
                mapping.append(displayId, mode);
            }
        } catch (Exception e) {
            Slogf.w(TAG, e, "Setting %s has invalid value: ", value);
            // Parsing error, ignore all.
            return null;
        }
        return mapping;
    }

    private int getDisplayId(int displayPort) {
        DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);
        for (Display display : displayManager.getDisplays()) {
            if (DisplayHelper.getPhysicalPort(display) == displayPort) {
                return display.getDisplayId();
            }
        }
        return Display.INVALID_DISPLAY;
    }

    private int getDisplayPort(int displayId) {
        DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);
        Display display = displayManager.getDisplay(displayId);
        if (display != null) {
            return DisplayHelper.getPhysicalPort(display);
        }
        return DisplayHelper.INVALID_PORT;
    }

    private static final class EventHandler extends Handler {
        private static final int MSG_USER_ACTIVITY_TIMEOUT = 0;

        private final WeakReference<ScreenOffHandler> mScreenOffHandler;

        private EventHandler(Looper looper, ScreenOffHandler screenOffHandler) {
            super(looper);
            mScreenOffHandler = new WeakReference<ScreenOffHandler>(screenOffHandler);
        }

        private void handleUserActivityTimeout(int displayId, long timeMs) {
            Message msg = obtainMessage(MSG_USER_ACTIVITY_TIMEOUT, displayId);
            msg.setAsynchronous(true);
            sendMessageAtTime(msg, timeMs);
        }

        private void cancelUserActivityTimeout(int displayId) {
            HandlerHelper.removeEqualMessages(this, MSG_USER_ACTIVITY_TIMEOUT, displayId);
        }

        @Override
        public void handleMessage(Message msg) {
            ScreenOffHandler screenOffHandler = mScreenOffHandler.get();
            if (screenOffHandler == null) {
                return;
            }
            switch (msg.what) {
                case MSG_USER_ACTIVITY_TIMEOUT:
                    screenOffHandler.handleSetDisplayState(/* displayId= */ (Integer) msg.obj,
                            /* on= */ false);
                    break;
                default:
                    Slogf.w(TAG, "Invalid message type: %d", msg.what);
                    break;
            }
        }
    }

    private int getNoUserScreenOffTimeout() {
        int timeout = mContext.getResources().getInteger(R.integer.config_noUserScreenOffTimeout);
        if (timeout < MIN_NO_USER_SCREEN_OFF_TIMEOUT_MS) {
            Slogf.w(TAG, "config_noUserScreenOffTimeout(%dms) is shorter than %dms and is reset to "
                    + "%dms", timeout, MIN_NO_USER_SCREEN_OFF_TIMEOUT_MS,
                    MIN_NO_USER_SCREEN_OFF_TIMEOUT_MS);
            timeout = MIN_NO_USER_SCREEN_OFF_TIMEOUT_MS;
        } else if (timeout > MAX_NO_USER_SCREEN_OFF_TIMEOUT_MS) {
            Slogf.w(TAG, "config_noUserScreenOffTimeout(%dms) is longer than %dms and is reset to "
                    + "%dms", timeout, MAX_NO_USER_SCREEN_OFF_TIMEOUT_MS,
                    MAX_NO_USER_SCREEN_OFF_TIMEOUT_MS);
            timeout = MAX_NO_USER_SCREEN_OFF_TIMEOUT_MS;
        }
        return timeout;
    }

    private static final class DisplayPowerInfo {
        private final int mDisplayId;

        private int mUserId;
        private @DisplayPowerMode int mMode;
        private boolean mIsDriverDisplay;
        private long mLastUserActivityTime;

        private DisplayPowerInfo(int displayId) {
            mDisplayId = displayId;
            mUserId = CarOccupantZoneManager.INVALID_USER_ID;
            mMode = DISPLAY_POWER_MODE_NONE;
            mIsDriverDisplay = false;
            mLastUserActivityTime = -1;
        }

        private int getDisplayId() {
            return mDisplayId;
        }

        private void setUserId(int userId) {
            mUserId = userId;
        }

        private int getUserId() {
            return mUserId;
        }

        private void setMode(@DisplayPowerMode int mode) {
            mMode = mode;
        }

        private @DisplayPowerMode int getMode() {
            return mMode;
        }

        private void setDriverDisplay(boolean isDriver) {
            mIsDriverDisplay = isDriver;
        }

        private boolean isDriverDisplay() {
            return mIsDriverDisplay;
        }

        private long getLastUserActivityTime() {
            return mLastUserActivityTime;
        }

        private void setLastUserActivityTime(long lastUserActivityTime) {
            mLastUserActivityTime = lastUserActivityTime;
        }

        @Override
        @ExcludeFromCodeCoverageGeneratedReport(reason = DEBUGGING_CODE)
        public String toString() {
            StringBuilder b = new StringBuilder(64);
            b.append("  DisplayPowerInfo{mDisplayId=");
            b.append(mDisplayId);
            b.append(" mUserId=");
            b.append(mUserId);
            b.append(" mMode=");
            b.append(displayPowerModeToString(mMode));
            b.append(" mIsDriverDisplay=");
            b.append(mIsDriverDisplay);
            b.append(" mLastUserActivityTime=");
            b.append(mLastUserActivityTime);
            b.append("}");
            return b.toString();
        }

        @ExcludeFromCodeCoverageGeneratedReport(reason = DEBUGGING_CODE)
        private static String displayPowerModeToString(@DisplayPowerMode int mode) {
            switch (mode) {
                case DISPLAY_POWER_MODE_NONE:
                    return "NONE";
                case DISPLAY_POWER_MODE_ON:
                    return "ON";
                case DISPLAY_POWER_MODE_OFF:
                    return "OFF";
                case DISPLAY_POWER_MODE_ALWAYS_ON:
                    return "ALWAYS_ON";
                default:
                    return "UNKNOWN";
            }
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dump(IndentingPrintWriter writer) {
        synchronized (mLock) {
            writer.println("ScreenOffHandler");
            writer.increaseIndent();
            writer.println("mIsAutoPowerSaving=" + mIsAutoPowerSaving);
            writer.println("mBootCompleted=" + mBootCompleted);
            writer.println("mNoUserScreenOffTimeoutMs=" + mNoUserScreenOffTimeoutMs);
            writer.decreaseIndent();
            for (int i = 0; i < mDisplayPowerInfos.size(); i++) {
                writer.println(mDisplayPowerInfos.valueAt(i));
            }
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dumpProto(ProtoOutputStream proto) {
        synchronized (mLock) {
            long screenOffHandlerToken = proto.start(CarPowerDumpProto.SCREEN_OFF_HANDLER);
            proto.write(ScreenOffHandlerProto.IS_AUTO_POWER_SAVING, mIsAutoPowerSaving);
            proto.write(ScreenOffHandlerProto.BOOT_COMPLETED, mBootCompleted);
            proto.write(
                    ScreenOffHandlerProto.NO_USER_SCREEN_OFF_TIMEOUT_MS, mNoUserScreenOffTimeoutMs);
            for (int i = 0; i < mDisplayPowerInfos.size(); i++) {
                long displayPowerInfosToken = proto.start(
                        ScreenOffHandlerProto.DISPLAY_POWER_INFOS);
                DisplayPowerInfo displayInfo = mDisplayPowerInfos.valueAt(i);
                proto.write(DisplayPowerInfoProto.DISPLAY_ID, displayInfo.getDisplayId());
                proto.write(DisplayPowerInfoProto.USER_ID, displayInfo.getUserId());
                proto.write(DisplayPowerInfoProto.MODE, displayInfo.getMode());
                proto.write(DisplayPowerInfoProto.IS_DRIVER_DISPLAY, displayInfo.isDriverDisplay());
                proto.write(DisplayPowerInfoProto.LAST_USER_ACTIVITY_TIME,
                        displayInfo.getLastUserActivityTime());
                proto.end(displayPowerInfosToken);
            }
            proto.end(screenOffHandlerToken);
        }
    }

    /** Functional interface for providing time. */
    @VisibleForTesting
    interface ClockInterface {
        /**
         * Returns current time in milliseconds since boot, not counting time spent in deep sleep.
         */
        long uptimeMillis();
    }
}
