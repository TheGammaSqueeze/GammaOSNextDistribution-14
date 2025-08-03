/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.car.builtin.view.DisplayHelper.INVALID_PORT;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STOPPING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;
import static android.view.Display.STATE_ON;

import static com.android.car.CarServiceUtils.getHandlerThread;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;
import static com.android.car.internal.common.CommonConstants.EMPTY_INT_ARRAY;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.car.Car;
import android.car.CarInfoManager;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.DisplayTypeEnum;
import android.car.CarOccupantZoneManager.OccupantTypeEnum;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.ICarOccupantZone;
import android.car.ICarOccupantZoneCallback;
import android.car.PlatformVersion;
import android.car.VehicleAreaSeat;
import android.car.builtin.util.Slogf;
import android.car.builtin.view.DisplayHelper;
import android.car.input.CarInputManager;
import android.car.media.CarAudioManager;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.UserLifecycleEventFilter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.proto.ProtoOutputStream;
import android.view.Display;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.internal.util.IntArray;
import com.android.car.occupantzone.CarOccupantZoneDumpProto;
import com.android.car.occupantzone.CarOccupantZoneDumpProto.DisplayConfigProto;
import com.android.car.occupantzone.CarOccupantZoneDumpProto.DisplayPortConfigsProto;
import com.android.car.occupantzone.CarOccupantZoneDumpProto.DisplayPortConfigsProto.DisplayConfigPortProto;
import com.android.car.occupantzone.CarOccupantZoneDumpProto.DisplayUniqueIdConfigsProto;
import com.android.car.occupantzone.CarOccupantZoneDumpProto.DisplayUniqueIdConfigsProto.DisplayConfigUniqueIdProto;
import com.android.car.user.CarUserService;
import com.android.car.user.ExperimentalCarUserService;
import com.android.car.user.ExperimentalCarUserService.ZoneUserBindingHelper;
import com.android.car.user.UserHandleHelper;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Service to implement CarOccupantZoneManager API.
 */
public final class CarOccupantZoneService extends ICarOccupantZone.Stub
        implements CarServiceBase {

    private static final String TAG = CarLog.tagFor(CarOccupantZoneService.class);
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    private static final String HANDLER_THREAD_NAME = "CarOccupantZoneService";

    private static final int[] EMPTY_INPUT_SUPPORT_TYPES = EMPTY_INT_ARRAY;

    private final Object mLock = new Object();
    private final Context mContext;
    private final DisplayManager mDisplayManager;
    private final UserManager mUserManager;
    private CarUserService mCarUserService;

    private final boolean mEnableProfileUserAssignmentForMultiDisplay;

    /**
     * Stores android user id of profile users for the current user.
     */
    @GuardedBy("mLock")
    private final ArraySet<Integer> mProfileUsers = new ArraySet<>();

    /** key: zone id */
    @GuardedBy("mLock")
    private final SparseArray<OccupantZoneInfo> mOccupantsConfig = new SparseArray<>();

    /**
     * The config of a display identified by occupant zone id and display type.
     */
    public static final class DisplayConfig {
        public final int displayType;
        public final int occupantZoneId;
        public final int[] inputTypes;

        DisplayConfig(int displayType, int occupantZoneId, IntArray inputTypes) {
            this.displayType = displayType;
            this.occupantZoneId = occupantZoneId;
            if (inputTypes == null && Car.getPlatformVersion().isAtLeast(
                    PlatformVersion.VERSION_CODES.UPSIDE_DOWN_CAKE_0)) {
                Slogf.w(TAG, "No input type was defined for displayType:%d "
                        + " and occupantZoneId:%d", displayType, occupantZoneId);
            }
            this.inputTypes = inputTypes == null ? EMPTY_INPUT_SUPPORT_TYPES : inputTypes.toArray();
        }

        @Override
        public String toString() {
            // do not include type as this is only used for dump
            StringBuilder b = new StringBuilder(64);
            b.append("{displayType=");
            b.append(Integer.toHexString(displayType));
            b.append(" occupantZoneId=");
            b.append(occupantZoneId);
            b.append(" inputTypes=");
            b.append(Arrays.toString(inputTypes));
            b.append("}");
            return b.toString();
        }
    }

    /** key: display port address */
    @GuardedBy("mLock")
    private final SparseArray<DisplayConfig> mDisplayPortConfigs = new SparseArray<>();

    /** key: displayUniqueId */
    @GuardedBy("mLock")
    private final ArrayMap<String, DisplayConfig> mDisplayUniqueIdConfigs = new ArrayMap<>();

    /** key: audio zone id */
    @GuardedBy("mLock")
    private final SparseIntArray mAudioZoneIdToOccupantZoneIdMapping = new SparseIntArray();

    @VisibleForTesting
    static class DisplayInfo {
        public final Display display;
        public final int displayType;

        DisplayInfo(Display display, int displayType) {
            this.display = display;
            this.displayType = displayType;
        }

        @Override
        public String toString() {
            // do not include type as this is only used for dump
            StringBuilder b = new StringBuilder(64);
            b.append("{displayId=");
            b.append(display.getDisplayId());
            b.append(" displayType=");
            b.append(displayType);
            b.append("}");
            return b.toString();
        }
    }

    @VisibleForTesting
    static class OccupantConfig {
        public int userId = CarOccupantZoneManager.INVALID_USER_ID;
        public final ArrayList<DisplayInfo> displayInfos = new ArrayList<>();
        public int audioZoneId = CarAudioManager.INVALID_AUDIO_ZONE;

        @Override
        public String toString() {
            // do not include type as this is only used for dump
            StringBuilder b = new StringBuilder(128);
            b.append("{userId=");
            b.append(userId);
            b.append(" displays=");
            for (int i = 0; i < displayInfos.size(); i++) {
                b.append(displayInfos.get(i).toString());
            }
            b.append(" audioZoneId=");
            if (audioZoneId != CarAudioManager.INVALID_AUDIO_ZONE) {
                b.append(audioZoneId);
            } else {
                b.append("none");
            }
            b.append("}");
            return b.toString();
        }
    }

    /** key : zoneId */
    @GuardedBy("mLock")
    private final SparseArray<OccupantConfig> mActiveOccupantConfigs = new SparseArray<>();

    @GuardedBy("mLock")
    private int mDriverZoneId = OccupantZoneInfo.INVALID_ZONE_ID;

    @VisibleForTesting
    final UserLifecycleListener mUserLifecycleListener = event -> {
        if (DBG) Slogf.d(TAG, "onEvent(%s)", event);
        handleUserChange();
    };

    final ExperimentalCarUserService.PassengerCallback mPassengerCallback =
            new ExperimentalCarUserService.PassengerCallback() {
                @Override
                public void onPassengerStarted(@UserIdInt int passengerId, int zoneId) {
                    handlePassengerStarted();
                }

                @Override
                public void onPassengerStopped(@UserIdInt int passengerId) {
                    handlePassengerStopped();
                }
            };

    @VisibleForTesting
    final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                    handleDisplayChange();
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                    handleDisplayChange();
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    // nothing to do
                }
            };

    private final RemoteCallbackList<ICarOccupantZoneCallback> mClientCallbacks =
            new RemoteCallbackList<>();

    @GuardedBy("mLock")
    private int mDriverSeat = VehicleAreaSeat.SEAT_UNKNOWN;
    private final UserHandleHelper mUserHandleHelper;

    final Handler mHandler = new Handler(getHandlerThread(HANDLER_THREAD_NAME).getLooper());

    public CarOccupantZoneService(Context context) {
        this(context, context.getSystemService(DisplayManager.class),
                context.getSystemService(UserManager.class),
                context.getResources().getBoolean(
                        R.bool.enableProfileUserAssignmentForMultiDisplay)
                        && context.getPackageManager().hasSystemFeature(
                                PackageManager.FEATURE_MANAGED_USERS),
                new UserHandleHelper(context, context.getSystemService(UserManager.class)));
    }

    @VisibleForTesting
    public CarOccupantZoneService(Context context, DisplayManager displayManager,
            UserManager userManager, boolean enableProfileUserAssignmentForMultiDisplay,
            UserHandleHelper userHandleHelper) {
        mContext = context;
        mDisplayManager = displayManager;
        mUserManager = userManager;
        mEnableProfileUserAssignmentForMultiDisplay = enableProfileUserAssignmentForMultiDisplay;
        mUserHandleHelper = userHandleHelper;
    }

    @Override
    public void init() {
        // This does not require connection as binder will be passed directly.
        Car car = new Car(mContext, /* service= */null, /* handler= */ null);
        CarInfoManager infoManager = new CarInfoManager(car, CarLocalServices.getService(
                CarPropertyService.class));
        int driverSeat = infoManager.getDriverSeat();
        synchronized (mLock) {
            mDriverSeat = driverSeat;
            parseOccupantZoneConfigsLocked();
            parseDisplayConfigsLocked();
            handleActiveDisplaysLocked();
            handleAudioZoneChangesLocked();
            handleUserChangesLocked();
        }
        mCarUserService = CarLocalServices.getService(CarUserService.class);
        UserLifecycleEventFilter userEventFilter = new UserLifecycleEventFilter.Builder()
                .addEventType(USER_LIFECYCLE_EVENT_TYPE_SWITCHING).addEventType(
                        USER_LIFECYCLE_EVENT_TYPE_STOPPING).build();
        mCarUserService.addUserLifecycleListener(userEventFilter, mUserLifecycleListener);
        ExperimentalCarUserService experimentalUserService =
                CarLocalServices.getService(ExperimentalCarUserService.class);
        if (experimentalUserService != null) {
            experimentalUserService.addPassengerCallback(mPassengerCallback);
        }
        mDisplayManager.registerDisplayListener(mDisplayListener,
                new Handler(Looper.getMainLooper()));
        ZoneUserBindingHelper helper = new ZoneUserBindingHelper() {
            @Override
            @NonNull
            public List<OccupantZoneInfo> getOccupantZones(@OccupantTypeEnum int occupantType) {
                List<OccupantZoneInfo> zones = new ArrayList<OccupantZoneInfo>();
                for (OccupantZoneInfo ozi : getAllOccupantZones()) {
                    if (ozi.occupantType == occupantType) {
                        zones.add(ozi);
                    }
                }
                return zones;
            }

            @Override
            public boolean assignUserToOccupantZone(@UserIdInt int userId, int zoneId) {
                // Check if the user is already assigned to the other zone.
                synchronized (mLock) {
                    int userZoneId = getZoneIdForUserIdLocked(userId);
                    if (userZoneId != OccupantZoneInfo.INVALID_ZONE_ID
                            && mActiveOccupantConfigs.keyAt(userZoneId) != zoneId) {
                        Slogf.w(TAG, "Cannot assign user to two different zones simultaneously");
                        return false;
                    }
                    OccupantConfig zoneConfig = mActiveOccupantConfigs.get(zoneId);
                    if (zoneConfig == null) {
                        Slogf.w(TAG, "cannot find the zone(%d)", zoneId);
                        return false;
                    }
                    if (zoneConfig.userId != CarOccupantZoneManager.INVALID_USER_ID
                            && zoneConfig.userId != userId) {
                        Slogf.w(TAG, "other user already occupies the zone(%d)", zoneId);
                        return false;
                    }
                    zoneConfig.userId = userId;
                    return true;
                }
            }

            @Override
            public boolean unassignUserFromOccupantZone(@UserIdInt int userId) {
                synchronized (mLock) {
                    for (int i = 0; i < mActiveOccupantConfigs.size(); ++i) {
                        OccupantConfig config = mActiveOccupantConfigs.valueAt(i);
                        if (config.userId == userId) {
                            config.userId = CarOccupantZoneManager.INVALID_USER_ID;
                            break;
                        }
                    }
                    return true;
                }
            }

            @Override
            public boolean isPassengerDisplayAvailable() {
                for (OccupantZoneInfo ozi : getAllOccupantZones()) {
                    if (getDisplayForOccupant(ozi.zoneId,
                            CarOccupantZoneManager.DISPLAY_TYPE_MAIN) != Display.INVALID_DISPLAY
                            && ozi.occupantType != CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER) {
                        return true;
                    }
                }
                return false;
            }
        };
        if (experimentalUserService != null) {
            experimentalUserService.setZoneUserBindingHelper(helper);
        }

        CarServiceHelperWrapper.getInstance().runOnConnection(() -> doSyncWithCarServiceHelper(
                /* updateDisplay= */ true, /* updateUser= */ true));
    }

    @Override
    public void release() {
        mDisplayManager.unregisterDisplayListener(mDisplayListener);
        mCarUserService.removeUserLifecycleListener(mUserLifecycleListener);
        ExperimentalCarUserService experimentalUserService =
                CarLocalServices.getService(ExperimentalCarUserService.class);
        if (experimentalUserService != null) {
            experimentalUserService.removePassengerCallback(mPassengerCallback);
        }
        synchronized (mLock) {
            mOccupantsConfig.clear();
            mDisplayPortConfigs.clear();
            mDisplayUniqueIdConfigs.clear();
            mAudioZoneIdToOccupantZoneIdMapping.clear();
            mActiveOccupantConfigs.clear();
        }
    }

    /** Return cloned mOccupantsConfig for testing */
    @VisibleForTesting
    @NonNull
    public SparseArray<OccupantZoneInfo> getOccupantsConfig() {
        synchronized (mLock) {
            return mOccupantsConfig.clone();
        }
    }

    /** Return cloned mDisplayPortConfigs for testing */
    @VisibleForTesting
    @NonNull
    public SparseArray<DisplayConfig> getDisplayPortConfigs() {
        synchronized (mLock) {
            return mDisplayPortConfigs.clone();
        }
    }

    /** Return cloned mDisplayUniqueIdConfigs for testing */
    @VisibleForTesting
    @NonNull
    ArrayMap<String, DisplayConfig> getDisplayUniqueIdConfigs() {
        synchronized (mLock) {
            return new ArrayMap<>(mDisplayUniqueIdConfigs);
        }
    }

    /** Return cloned mAudioConfigs for testing */
    @VisibleForTesting
    @NonNull
    SparseIntArray getAudioConfigs() {
        synchronized (mLock) {
            return mAudioZoneIdToOccupantZoneIdMapping.clone();
        }
    }

    /** Return cloned mActiveOccupantConfigs for testing */
    @VisibleForTesting
    @NonNull
    public SparseArray<OccupantConfig> getActiveOccupantConfigs() {
        synchronized (mLock) {
            return mActiveOccupantConfigs.clone();
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("*OccupantZoneService*");
        synchronized (mLock) {
            writer.println("**mOccupantsConfig**");
            for (int i = 0; i < mOccupantsConfig.size(); ++i) {
                writer.println(" zoneId=" + mOccupantsConfig.keyAt(i)
                        + " info=" + mOccupantsConfig.valueAt(i));
            }
            writer.println("**mDisplayConfigs**");
            for (int i = 0; i < mDisplayPortConfigs.size(); ++i) {
                writer.println(" port=" + mDisplayPortConfigs.keyAt(i)
                        + " config=" + mDisplayPortConfigs.valueAt(i));
            }
            for (int i = 0; i < mDisplayUniqueIdConfigs.size(); ++i) {
                writer.println(" uniqueId=" + mDisplayUniqueIdConfigs.keyAt(i)
                        + " config=" + mDisplayUniqueIdConfigs.valueAt(i));
            }
            writer.println("**mAudioZoneIdToOccupantZoneIdMapping**");
            for (int index = 0; index < mAudioZoneIdToOccupantZoneIdMapping.size(); index++) {
                int audioZoneId = mAudioZoneIdToOccupantZoneIdMapping.keyAt(index);
                writer.println(" audioZoneId=" + Integer.toHexString(audioZoneId)
                        + " zoneId=" + mAudioZoneIdToOccupantZoneIdMapping.valueAt(index));
            }
            writer.println("**mActiveOccupantConfigs**");
            for (int i = 0; i < mActiveOccupantConfigs.size(); ++i) {
                writer.println(" zoneId=" + mActiveOccupantConfigs.keyAt(i)
                        + " config=" + mActiveOccupantConfigs.valueAt(i));
            }
            writer.println("mEnableProfileUserAssignmentForMultiDisplay:"
                    + mEnableProfileUserAssignmentForMultiDisplay);
            writer.println("hasDriverZone: " + hasDriverZone());
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {
        synchronized (mLock) {
            for (int i = 0; i < mDisplayPortConfigs.size(); i++) {
                long displayPortConfigsToken = proto.start(
                        CarOccupantZoneDumpProto.DISPLAY_PORT_CONFIGS);
                long displayConfigPortToken = proto.start(
                        DisplayPortConfigsProto.DISPLAY_CONFIG_PORT);
                int port = mDisplayPortConfigs.keyAt(i);
                proto.write(DisplayConfigPortProto.PORT, port);
                long displayConfigToken = proto.start(DisplayConfigPortProto.DISPLAY_CONFIG);
                DisplayConfig displayConfig = mDisplayPortConfigs.valueAt(i);
                proto.write(DisplayConfigProto.DISPLAY_TYPE, displayConfig.displayType);
                proto.write(DisplayConfigProto.OCCUPANT_ZONE_ID, displayConfig.occupantZoneId);
                for (int j = 0; j < displayConfig.inputTypes.length; j++) {
                    proto.write(DisplayConfigProto.INPUT_TYPES, displayConfig.inputTypes[j]);
                }
                proto.end(displayConfigToken);
                proto.end(displayConfigPortToken);
                proto.end(displayPortConfigsToken);
            }

            for (int i = 0; i < mDisplayUniqueIdConfigs.size(); i++) {
                long displayUniqueIdConfigsToken = proto.start(
                        CarOccupantZoneDumpProto.DISPLAY_UNIQUE_ID_CONFIGS);
                long displayConfigUniqueIdToken = proto.start(
                        DisplayUniqueIdConfigsProto.DISPLAY_CONFIG_UNIQUE_ID);
                String uniqueId = mDisplayUniqueIdConfigs.keyAt(i);
                proto.write(DisplayConfigUniqueIdProto.UNIQUE_ID, uniqueId);
                long displayConfigToken = proto.start(DisplayConfigPortProto.DISPLAY_CONFIG);
                DisplayConfig displayConfig = mDisplayUniqueIdConfigs.valueAt(i);
                proto.write(DisplayConfigProto.DISPLAY_TYPE, displayConfig.displayType);
                proto.write(DisplayConfigProto.OCCUPANT_ZONE_ID, displayConfig.occupantZoneId);
                for (int j = 0; j < displayConfig.inputTypes.length; j++) {
                    proto.write(DisplayConfigProto.INPUT_TYPES, displayConfig.inputTypes[j]);
                }
                proto.end(displayConfigToken);
                proto.end(displayConfigUniqueIdToken);
                proto.end(displayUniqueIdConfigsToken);
            }
        }
    }

    @Override
    public List<OccupantZoneInfo> getAllOccupantZones() {
        synchronized (mLock) {
            List<OccupantZoneInfo> infos = new ArrayList<>();
            for (int i = 0; i < mActiveOccupantConfigs.size(); ++i) {
                int zoneId = mActiveOccupantConfigs.keyAt(i);
                // no need for deep copy as OccupantZoneInfo itself is static.
                infos.add(mOccupantsConfig.get(zoneId));
            }
            return infos;
        }
    }

    @Override
    public int[] getAllDisplaysForOccupantZone(int occupantZoneId) {
        synchronized (mLock) {
            OccupantConfig config = mActiveOccupantConfigs.get(occupantZoneId);
            if (config == null) {
                return EMPTY_INT_ARRAY;
            }
            int[] displayIds = new int[config.displayInfos.size()];
            for (int i = 0; i < config.displayInfos.size(); i++) {
                displayIds[i] = config.displayInfos.get(i).display.getDisplayId();
            }
            return displayIds;
        }
    }

    /**
     * Checks if all displays for a given OccupantZone are on.
     *
     * @param occupantZoneId indicates which OccupantZone's displays to check
     * @return whether all displays for a given OccupantZone are on
     */
    public boolean areDisplaysOnForOccupantZone(int occupantZoneId) {
        synchronized (mLock) {
            OccupantConfig config = mActiveOccupantConfigs.get(occupantZoneId);
            if (config == null) {
                return false;
            }
            for (int i = 0; i < config.displayInfos.size(); i++) {
                if (config.displayInfos.get(i).display.getState() != STATE_ON) {
                    return false;
                }
            }

            return true;
        }
    }

    @Override
    public int getDisplayForOccupant(int occupantZoneId, int displayType) {
        synchronized (mLock) {
            OccupantConfig config = mActiveOccupantConfigs.get(occupantZoneId);
            if (config == null) {
                return Display.INVALID_DISPLAY;
            }
            for (int i = 0; i < config.displayInfos.size(); i++) {
                if (displayType == config.displayInfos.get(i).displayType) {
                    return config.displayInfos.get(i).display.getDisplayId();
                }
            }
        }
        return Display.INVALID_DISPLAY;
    }

    public IntArray getAllDisplayIdsForDriver(int displayType) {
        synchronized (mLock) {
            OccupantConfig config = mActiveOccupantConfigs.get(mDriverZoneId);
            if (config == null) {
                return new IntArray(0);
            }
            IntArray displayIds = new IntArray(config.displayInfos.size());
            for (int i = 0; i < config.displayInfos.size(); i++) {
                DisplayInfo displayInfo = config.displayInfos.get(i);
                if (displayInfo.displayType == displayType) {
                    displayIds.add(displayInfo.display.getDisplayId());
                }
            }
            return displayIds;
        }
    }

    @Override
    public int getDisplayIdForDriver(@DisplayTypeEnum int displayType) {
        enforcePermission(Car.ACCESS_PRIVATE_DISPLAY_ID);
        synchronized (mLock) {
            int driverUserId = getDriverUserId();
            DisplayInfo displayInfo = findDisplayForDriverLocked(driverUserId, displayType);
            if (displayInfo == null) {
                return Display.INVALID_DISPLAY;
            }
            return displayInfo.display.getDisplayId();
        }
    }

    @GuardedBy("mLock")
    @Nullable
    private DisplayInfo findDisplayForDriverLocked(@UserIdInt int driverUserId,
            @DisplayTypeEnum int displayType) {
        for (OccupantZoneInfo zoneInfo : getAllOccupantZones()) {
            if (zoneInfo.occupantType == CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER) {
                OccupantConfig config = mActiveOccupantConfigs.get(zoneInfo.zoneId);
                if (config == null) {
                    //No active display for zone, just continue...
                    continue;
                }

                if (config.userId == driverUserId) {
                    for (DisplayInfo displayInfo : config.displayInfos) {
                        if (displayInfo.displayType == displayType) {
                            return displayInfo;
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public int getAudioZoneIdForOccupant(int occupantZoneId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        synchronized (mLock) {
            OccupantConfig config = mActiveOccupantConfigs.get(occupantZoneId);
            if (config != null) {
                return config.audioZoneId;
            }
            // check if the occupant id exist at all
            if (!mOccupantsConfig.contains(occupantZoneId)) {
                return CarAudioManager.INVALID_AUDIO_ZONE;
            }
            // Exist but not active
            return getAudioZoneIdForOccupantLocked(occupantZoneId);
        }
    }

    @GuardedBy("mLock")
    private int getAudioZoneIdForOccupantLocked(int occupantZoneId) {
        for (int index = 0; index < mAudioZoneIdToOccupantZoneIdMapping.size(); index++) {
            int audioZoneId = mAudioZoneIdToOccupantZoneIdMapping.keyAt(index);
            if (occupantZoneId == mAudioZoneIdToOccupantZoneIdMapping.get(audioZoneId)) {
                return audioZoneId;
            }
        }
        return CarAudioManager.INVALID_AUDIO_ZONE;
    }

    @Override
    public OccupantZoneInfo getOccupantForAudioZoneId(int audioZoneId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        synchronized (mLock) {
            int occupantZoneId = mAudioZoneIdToOccupantZoneIdMapping.get(audioZoneId,
                    OccupantZoneInfo.INVALID_ZONE_ID);
            if (occupantZoneId == OccupantZoneInfo.INVALID_ZONE_ID) {
                return null;
            }
            // To support headless zones return the occupant configuration.
            return mOccupantsConfig.get(occupantZoneId);
        }
    }

    /**
     * Finds the DisplayConfig for a logical display id.
     */
    @Nullable
    public DisplayConfig findDisplayConfigForDisplayId(int displayId) {
        synchronized (mLock) {
            return findDisplayConfigForDisplayIdLocked(displayId);
        }
    }

    /**
     * Finds the DisplayConfig for a physical display port.
     */
    @Nullable
    public DisplayConfig findDisplayConfigForPort(int portAddress) {
        synchronized (mLock) {
            return findDisplayConfigForPortLocked(portAddress);
        }
    }

    @GuardedBy("mLock")
    @Nullable
    private DisplayConfig findDisplayConfigForDisplayIdLocked(int displayId) {
        Display display = mDisplayManager.getDisplay(displayId);
        if (display == null) {
            return null;
        }
        return findDisplayConfigForDisplayLocked(display);
    }

    @GuardedBy("mLock")
    @Nullable
    private DisplayConfig findDisplayConfigForDisplayLocked(Display display) {
        int portAddress = DisplayHelper.getPhysicalPort(display);
        if (portAddress != INVALID_PORT) {
            DisplayConfig config = mDisplayPortConfigs.get(portAddress);
            if (config != null) {
                return config;
            }
        }
        return mDisplayUniqueIdConfigs.get(DisplayHelper.getUniqueId(display));
    }

    @GuardedBy("mLock")
    @Nullable
    private DisplayConfig findDisplayConfigForPortLocked(int portAddress) {
        return portAddress != INVALID_PORT ? mDisplayPortConfigs.get(portAddress) : null;
    }

    @Override
    public int getDisplayType(int displayId) {
        synchronized (mLock) {
            DisplayConfig config = findDisplayConfigForDisplayIdLocked(displayId);
            if (config != null) {
                return config.displayType;
            }
        }
        return CarOccupantZoneManager.DISPLAY_TYPE_UNKNOWN;
    }

    @Override
    public int getUserForOccupant(int occupantZoneId) {
        synchronized (mLock) {
            OccupantConfig config = mActiveOccupantConfigs.get(occupantZoneId);
            if (config == null) {
                return CarOccupantZoneManager.INVALID_USER_ID;
            }
            return config.userId;
        }
    }

    @Override
    public int getOccupantZoneIdForUserId(@UserIdInt int userId) {
        synchronized (mLock) {
            for (int i = 0; i < mActiveOccupantConfigs.size(); ++i) {
                OccupantConfig config = mActiveOccupantConfigs.valueAt(i);
                if (config.userId == userId) {
                    return mActiveOccupantConfigs.keyAt(i);
                }
            }
            Slogf.w(TAG, "Could not find occupantZoneId for userId%d returning invalid "
                    + "occupant zone id %d", userId, OccupantZoneInfo.INVALID_ZONE_ID);
            return OccupantZoneInfo.INVALID_ZONE_ID;
        }
    }

    @Override
    public OccupantZoneInfo getOccupantZoneForDisplayId(int displayId) {
        synchronized (mLock) {
            DisplayConfig displayConfig = findDisplayConfigForDisplayIdLocked(displayId);
            if (displayConfig == null) {
                Slogf.w(TAG, "getOccupantZoneForDisplayId: Could not find DisplayConfig for "
                        + "display Id %d", displayId);
                return null;
            }

            int occupantZoneId = displayConfig.occupantZoneId;
            if (occupantZoneId == OccupantZoneInfo.INVALID_ZONE_ID) {
                Slogf.w(TAG, "getOccupantZoneForDisplayId: Got invalid occupant zone id from "
                        + "DisplayConfig: %s", displayConfig);
                return null;
            }

            return mOccupantsConfig.get(occupantZoneId);
        }
    }

    /**
     * returns the current driver user id.
     */
    public @UserIdInt int getDriverUserId() {
        return getCurrentUser();
    }

    /**
     * Sets the mapping for audio zone id to occupant zone id.
     *
     * @param audioZoneIdToOccupantZoneMapping map for audio zone id, where key is the audio zone id
     *                                         and value is the occupant zone id
     */
    public void setAudioZoneIdsForOccupantZoneIds(
            @NonNull SparseIntArray audioZoneIdToOccupantZoneMapping) {
        Objects.requireNonNull(audioZoneIdToOccupantZoneMapping,
                "audioZoneIdToOccupantZoneMapping can not be null");
        synchronized (mLock) {
            validateOccupantZoneIdsLocked(audioZoneIdToOccupantZoneMapping);
            mAudioZoneIdToOccupantZoneIdMapping.clear();
            for (int index = 0; index < audioZoneIdToOccupantZoneMapping.size(); index++) {
                int audioZoneId = audioZoneIdToOccupantZoneMapping.keyAt(index);
                mAudioZoneIdToOccupantZoneIdMapping.put(audioZoneId,
                        audioZoneIdToOccupantZoneMapping.get(audioZoneId));
            }
            //If there are any active displays for the zone send change event
            handleAudioZoneChangesLocked();
        }
        sendConfigChangeEvent(CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_AUDIO);
    }

    @GuardedBy("mLock")
    private void validateOccupantZoneIdsLocked(SparseIntArray audioZoneIdToOccupantZoneMapping) {
        for (int i = 0; i < audioZoneIdToOccupantZoneMapping.size(); i++) {
            int occupantZoneId =
                    audioZoneIdToOccupantZoneMapping.get(audioZoneIdToOccupantZoneMapping.keyAt(i));
            if (!mOccupantsConfig.contains(occupantZoneId)) {
                throw new IllegalArgumentException("occupantZoneId " + occupantZoneId
                        + " does not exist.");
            }
        }
    }

    @Override
    public void registerCallback(ICarOccupantZoneCallback callback) {
        mClientCallbacks.register(callback);
    }

    @Override
    public void unregisterCallback(ICarOccupantZoneCallback callback) {
        mClientCallbacks.unregister(callback);
    }

    @Override
    public boolean assignProfileUserToOccupantZone(int occupantZoneId, @UserIdInt int userId) {
        CarServiceUtils.assertAnyPermission(mContext, android.Manifest.permission.MANAGE_USERS,
                Car.PERMISSION_MANAGE_OCCUPANT_ZONE);

        if (!mEnableProfileUserAssignmentForMultiDisplay) {
            throw new IllegalStateException("feature not enabled");
        }

        UserHandle user = null;
        synchronized (mLock) {
            if (occupantZoneId == mDriverZoneId) {
                throw new IllegalArgumentException("Driver zone cannot have profile user");
            }
            updateEnabledProfilesLocked(getCurrentUser());

            if (!mProfileUsers.contains(userId)
                    && userId != CarOccupantZoneManager.INVALID_USER_ID) {
                // current user can change while this call is happening, so return false rather
                // than throwing exception
                Slogf.w(TAG, "Invalid profile user id: %d", userId);
                return false;
            }
            if (userId != CarOccupantZoneManager.INVALID_USER_ID) {
                user = UserHandle.of(userId);
            }
        }

        long token = Binder.clearCallingIdentity();
        try {
            if (userId == CarOccupantZoneManager.INVALID_USER_ID) {
                return unassignOccupantZoneUnchecked(occupantZoneId)
                        == CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_OK;
            } else {
                return assignVisibleUserToOccupantZoneUnchecked(occupantZoneId, user)
                        == CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_OK;
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public int assignVisibleUserToOccupantZone(int occupantZoneId, UserHandle user) {
        CarServiceUtils.assertAnyPermission(mContext, android.Manifest.permission.MANAGE_USERS,
                Car.PERMISSION_MANAGE_OCCUPANT_ZONE);
        Preconditions.checkNotNull(user);
        long token = Binder.clearCallingIdentity();
        try {
            return assignVisibleUserToOccupantZoneUnchecked(occupantZoneId, user);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Precondition: permission check should be done and binder caller identity should be cleared.
     */
    private int assignVisibleUserToOccupantZoneUnchecked(int occupantZoneId,
            @NonNull UserHandle user) {
        int userId;
        if (user.equals(UserHandle.CURRENT)) {
            userId = getCurrentUser();
        } else {
            userId = user.getIdentifier();
        }

        if (!mCarUserService.isUserVisible(userId)) {
            Slogf.w(TAG, "Non-visible user %d cannot be allocated to zone %d", userId,
                    occupantZoneId);
            return CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_FAIL_NON_VISIBLE_USER;
        }

        synchronized (mLock) {
            int userZoneId = getZoneIdForUserIdLocked(userId);
            if (userZoneId != OccupantZoneInfo.INVALID_ZONE_ID
                    && mActiveOccupantConfigs.keyAt(userZoneId) != occupantZoneId) {
                Slogf.w(TAG, "Cannot assign visible user %d to two different zones simultaneously,"
                                + " user is already assigned to %d",
                        userId, userZoneId);
                return CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_FAIL_ALREADY_ASSIGNED;
            }
            OccupantConfig config = mActiveOccupantConfigs.get(occupantZoneId);
            if (config == null) {
                Slogf.w(TAG, "Invalid zone:%d", occupantZoneId);
                throw new IllegalArgumentException("Invalid occupantZoneId:" + occupantZoneId);
            }
            if (config.userId == userId && userId != CarOccupantZoneManager.INVALID_USER_ID) {
                Slogf.w(TAG, "assignVisibleUserToOccupantZone zone:%d already set to user:%d",
                        occupantZoneId, userId);
                return CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_OK;
            }
            if (DBG) Slogf.d(TAG, "Assigned user %d to zone %d", userId, occupantZoneId);
            config.userId = userId;
        }

        sendConfigChangeEvent(CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER);
        return CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_OK;
    }

    @GuardedBy("mLock")
    private int getZoneIdForUserIdLocked(@UserIdInt int userId) {
        for (int i = 0; i < mActiveOccupantConfigs.size(); i++) {
            OccupantConfig config = mActiveOccupantConfigs.valueAt(i);
            if (config.userId == userId) {
                return mActiveOccupantConfigs.keyAt(i);
            }
        }
        return OccupantZoneInfo.INVALID_ZONE_ID;
    }

    @Override
    public int unassignOccupantZone(int occupantZoneId) {
        CarServiceUtils.assertAnyPermission(mContext, android.Manifest.permission.MANAGE_USERS,
                Car.PERMISSION_MANAGE_OCCUPANT_ZONE);

        long token = Binder.clearCallingIdentity();
        try {
            return unassignOccupantZoneUnchecked(occupantZoneId);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Precondition: permission check should be done and binder caller identity should be cleared.
     */
    private int unassignOccupantZoneUnchecked(int occupantZoneId) {
        synchronized (mLock) {
            OccupantConfig config = mActiveOccupantConfigs.get(occupantZoneId);
            if (config == null) {
                Slogf.w(TAG, "Invalid zone:%d", occupantZoneId);
                throw new IllegalArgumentException("Invalid occupantZoneId:" + occupantZoneId);
            }
            if (config.userId == CarOccupantZoneManager.INVALID_USER_ID) {
                // already unassigned
                Slogf.w(TAG, "unassignOccupantZone for already unassigned zone:%d",
                        occupantZoneId);
                return CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_OK;
            }
            OccupantZoneInfo info = mOccupantsConfig.get(occupantZoneId);
            if (info.occupantType == CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER) {
                Slogf.w(TAG, "Cannot unassign driver zone");
                return CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_FAIL_DRIVER_ZONE;
            }
            if (DBG) Slogf.d(TAG, "Unassigned zone:%d", occupantZoneId);
            config.userId = CarOccupantZoneManager.INVALID_USER_ID;
        }
        sendConfigChangeEvent(CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER);

        return CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_OK;
    }

    @Override
    public OccupantZoneInfo getMyOccupantZone() {
        int uid = Binder.getCallingUid();
        // UserHandle.getUserId(uid) can do this in one step but it is hidden API.
        UserHandle user = UserHandle.getUserHandleForUid(uid);
        int userId = user.getIdentifier();
        synchronized (mLock) {
            for (int i = 0; i < mActiveOccupantConfigs.size(); i++) {
                OccupantConfig config = mActiveOccupantConfigs.valueAt(i);
                if (config.userId == userId) {
                    int zoneId = mActiveOccupantConfigs.keyAt(i);
                    return mOccupantsConfig.get(zoneId);
                }
            }
        }
        Slogf.w(TAG, "getMyOccupantZone: No assigned zone for uid:%d", uid);
        return null;
    }

    @Override
    public OccupantZoneInfo getOccupantZoneForUser(UserHandle user) {
        Objects.requireNonNull(user, "User cannot be null");
        if (user.getIdentifier() == CarOccupantZoneManager.INVALID_USER_ID) {
            return null;
        }
        int occupantZoneId = getOccupantZoneIdForUserId(user.getIdentifier());
        if (DBG) Slogf.d(TAG, "occupantZoneId that was gotten was %d", occupantZoneId);
        synchronized (mLock) {
            return mOccupantsConfig.get(occupantZoneId);
        }
    }

    @Override
    public OccupantZoneInfo getOccupantZone(@OccupantTypeEnum int occupantType,
            @VehicleAreaSeat.Enum int seat) {
        synchronized (mLock) {
            for (int i = 0; i < mActiveOccupantConfigs.size(); i++) {
                int zoneId = mActiveOccupantConfigs.keyAt(i);
                OccupantZoneInfo info = mOccupantsConfig.get(zoneId);
                if (occupantType == CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER
                        || occupantType == CarOccupantZoneManager.OCCUPANT_TYPE_FRONT_PASSENGER) {
                    if (occupantType == info.occupantType) {
                        return info;
                    }
                } else {
                    if (occupantType == info.occupantType && seat == info.seat) {
                        return info;
                    }
                }
            }
            return null;
        }
    }

    /**
     * Gets the occupant zone id for the seat
     *
     * @param seat The vehicle area seat to be used
     *
     * @return The occupant zone id for the given seat
     */
    public int getOccupantZoneIdForSeat(@VehicleAreaSeat.Enum int seat) {
        synchronized (mLock) {
            return getOccupantZoneIdForSeatLocked(seat);
        }
    }

    @GuardedBy("mLock")
    private int getOccupantZoneIdForSeatLocked(@VehicleAreaSeat.Enum int seat) {
        for (int i = 0; i < mActiveOccupantConfigs.size(); i++) {
            int zoneId = mActiveOccupantConfigs.keyAt(i);
            OccupantZoneInfo info = mOccupantsConfig.get(zoneId);
            if (seat == info.seat) {
                return zoneId;
            }
        }
        return OccupantZoneInfo.INVALID_ZONE_ID;
    }

    @Override
    public boolean hasDriverZone() {
        synchronized (mLock) {
            return mDriverZoneId != OccupantZoneInfo.INVALID_ZONE_ID;
        }
    }

    @Override
    public boolean hasPassengerZones() {
        synchronized (mLock) {
            // There can be only one driver zone. So if there is driver, there should be at least
            // two zones to have passenger. If there is no driver zone, having a zone is enough to
            // have passenger zone.
            boolean hasDriver = mDriverZoneId != OccupantZoneInfo.INVALID_ZONE_ID;
            return mActiveOccupantConfigs.size() > (hasDriver ? 1 : 0);
        }
    }

    @Override
    @UserIdInt
    public int getUserForDisplayId(int displayId) {
        synchronized (mLock) {
            for (int i = 0; i < mActiveOccupantConfigs.size(); i++) {
                OccupantConfig config = mActiveOccupantConfigs.valueAt(i);
                for (int j = 0; j < config.displayInfos.size(); j++) {
                    if (config.displayInfos.get(j).display.getDisplayId() == displayId) {
                        return config.userId;
                    }
                }
            }
        }
        Slogf.w(TAG, "Could not find OccupantZone for display Id %d", displayId);
        return CarOccupantZoneManager.INVALID_USER_ID;
    }

    /** Returns number of passenger zones in the device. */
    public int getNumberOfPassengerZones() {
        synchronized (mLock) {
            boolean hasDriver = mDriverZoneId != OccupantZoneInfo.INVALID_ZONE_ID;
            return mActiveOccupantConfigs.size() - (hasDriver ? 1 : 0);
        }
    }

    private void doSyncWithCarServiceHelper(boolean updateDisplay, boolean updateUser) {
        int[] passengerDisplays = null;
        ArrayMap<Integer, IntArray> allowlists = null;
        synchronized (mLock) {
            if (updateDisplay) {
                passengerDisplays = getAllActivePassengerDisplaysLocked();
            }
            if (updateUser) {
                allowlists = createDisplayAllowlistsLocked();
            }
        }
        if (updateDisplay) {
            updatePassengerDisplays(passengerDisplays);
        }
        if (updateUser) {
            updateUserAssignmentForDisplays(allowlists);
        }
    }

    @GuardedBy("mLock")
    private int[] getAllActivePassengerDisplaysLocked() {
        IntArray displays = new IntArray();
        for (int j = 0; j < mActiveOccupantConfigs.size(); ++j) {
            int zoneId = mActiveOccupantConfigs.keyAt(j);
            if (zoneId == mDriverZoneId) {
                continue;
            }
            OccupantConfig config = mActiveOccupantConfigs.valueAt(j);
            for (int i = 0; i < config.displayInfos.size(); i++) {
                displays.add(config.displayInfos.get(i).display.getDisplayId());
            }
        }
        return displays.toArray();
    }

    private void updatePassengerDisplays(int[] passengerDisplayIds) {
        if (passengerDisplayIds == null) {
            return;
        }
        CarServiceHelperWrapper.getInstance().setPassengerDisplays(passengerDisplayIds);
    }

    @GuardedBy("mLock")
    private ArrayMap<Integer, IntArray> createDisplayAllowlistsLocked() {
        ArrayMap<Integer, IntArray> allowlists = new ArrayMap<>();
        for (int j = 0; j < mActiveOccupantConfigs.size(); ++j) {
            int zoneId = mActiveOccupantConfigs.keyAt(j);
            if (zoneId == mDriverZoneId) {
                continue;
            }
            OccupantConfig config = mActiveOccupantConfigs.valueAt(j);
            if (config.displayInfos.isEmpty()) {
                continue;
            }
            // Do not allow any user if it is unassigned.
            if (config.userId == CarOccupantZoneManager.INVALID_USER_ID) {
                continue;
            }
            IntArray displays = allowlists.get(config.userId);
            if (displays == null) {
                displays = new IntArray();
                allowlists.put(config.userId, displays);
            }
            for (int i = 0; i < config.displayInfos.size(); i++) {
                displays.add(config.displayInfos.get(i).display.getDisplayId());
            }
        }
        return allowlists;
    }

    private void updateUserAssignmentForDisplays(ArrayMap<Integer, IntArray> allowlists) {
        if (allowlists == null || allowlists.isEmpty()) {
            return;
        }
        for (int i = 0; i < allowlists.size(); i++) {
            int userId = allowlists.keyAt(i);
            CarServiceHelperWrapper.getInstance().setDisplayAllowlistForUser(userId,
                    allowlists.valueAt(i).toArray());
        }
    }

    private void throwFormatErrorInOccupantZones(String msg) {
        throw new RuntimeException("Format error in config_occupant_zones resource:" + msg);
    }

    /** Returns the driver seat. */
    int getDriverSeat() {
        synchronized (mLock) {
            return mDriverSeat;
        }
    }

    @GuardedBy("mLock")
    private void parseOccupantZoneConfigsLocked() {
        final Resources res = mContext.getResources();
        // examples:
        // <item>occupantZoneId=0,occupantType=DRIVER,seatRow=1,seatSide=driver</item>
        // <item>occupantZoneId=1,occupantType=FRONT_PASSENGER,seatRow=1,
        // searSide=oppositeDriver</item>
        boolean hasDriver = false;
        int driverSeat = getDriverSeat();
        int driverSeatSide = VehicleAreaSeat.SIDE_LEFT; // default LHD : Left Hand Drive
        if (driverSeat == VehicleAreaSeat.SEAT_ROW_1_RIGHT) {
            driverSeatSide = VehicleAreaSeat.SIDE_RIGHT;
        }
        int maxZoneId = OccupantZoneInfo.INVALID_ZONE_ID;
        for (String config : res.getStringArray(R.array.config_occupant_zones)) {
            int zoneId = OccupantZoneInfo.INVALID_ZONE_ID;
            int type = CarOccupantZoneManager.OCCUPANT_TYPE_INVALID;
            int seatRow = 0; // invalid row
            int seatSide = VehicleAreaSeat.SIDE_LEFT;
            String[] entries = config.split(",");
            for (String entry : entries) {
                String[] keyValuePair = entry.split("=");
                if (keyValuePair.length != 2) {
                    throwFormatErrorInOccupantZones("No key/value pair:" + entry);
                }
                switch (keyValuePair[0]) {
                    case "occupantZoneId":
                        zoneId = Integer.parseInt(keyValuePair[1]);
                        break;
                    case "occupantType":
                        switch (keyValuePair[1]) {
                            case "DRIVER":
                                type = CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER;
                                break;
                            case "FRONT_PASSENGER":
                                type = CarOccupantZoneManager.OCCUPANT_TYPE_FRONT_PASSENGER;
                                break;
                            case "REAR_PASSENGER":
                                type = CarOccupantZoneManager.OCCUPANT_TYPE_REAR_PASSENGER;
                                break;
                            default:
                                throwFormatErrorInOccupantZones("Unrecognized type:" + entry);
                                break;
                        }
                        break;
                    case "seatRow":
                        seatRow = Integer.parseInt(keyValuePair[1]);
                        break;
                    case "seatSide":
                        switch (keyValuePair[1]) {
                            case "driver":
                                seatSide = driverSeatSide;
                                break;
                            case "oppositeDriver":
                                seatSide = -driverSeatSide;
                                break;
                            case "left":
                                seatSide = VehicleAreaSeat.SIDE_LEFT;
                                break;
                            case "center":
                                seatSide = VehicleAreaSeat.SIDE_CENTER;
                                break;
                            case "right":
                                seatSide = VehicleAreaSeat.SIDE_RIGHT;
                                break;
                            default:
                                throwFormatErrorInOccupantZones("Unrecognized seatSide:" + entry);
                                break;
                        }
                        break;
                    default:
                        throwFormatErrorInOccupantZones("Unrecognized key:" + entry);
                        break;
                }
            }
            if (zoneId == OccupantZoneInfo.INVALID_ZONE_ID) {
                throwFormatErrorInOccupantZones("Missing zone id:" + config);
            }
            if (zoneId > maxZoneId) {
                maxZoneId = zoneId;
            }
            if (type == CarOccupantZoneManager.OCCUPANT_TYPE_INVALID) {
                throwFormatErrorInOccupantZones("Missing type:" + config);
            }
            if (type == CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER) {
                if (hasDriver) {
                    throwFormatErrorInOccupantZones("Multiple driver:" + config);
                } else {
                    hasDriver = true;
                    mDriverZoneId = zoneId;
                }
            }
            int seat = VehicleAreaSeat.fromRowAndSide(seatRow, seatSide);
            if (seat == VehicleAreaSeat.SEAT_UNKNOWN) {
                throwFormatErrorInOccupantZones("Invalid seat:" + config);
            }
            OccupantZoneInfo info = new OccupantZoneInfo(zoneId, type, seat);
            if (mOccupantsConfig.contains(zoneId)) {
                throwFormatErrorInOccupantZones("Duplicate zone id:" + config);
            }
            mOccupantsConfig.put(zoneId, info);
        }
        // No zones defined. Then populate driver zone.
        if (maxZoneId == OccupantZoneInfo.INVALID_ZONE_ID) {
            maxZoneId++;
            mDriverZoneId = maxZoneId;
            Slogf.w(TAG, "No zones defined, add one as driver:%d", mDriverZoneId);
            OccupantZoneInfo info = new OccupantZoneInfo(mDriverZoneId,
                    CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER, getDriverSeat());
            mOccupantsConfig.put(mDriverZoneId, info);
        }
    }

    private void throwFormatErrorInDisplayMapping(String msg) {
        throw new RuntimeException(
                "Format error in config_occupant_display_mapping resource:" + msg);
    }

    @GuardedBy("mLock")
    private void parseDisplayConfigsLocked() {
        final Resources res = mContext.getResources();
        final SparseArray<IntArray> inputTypesPerDisplay = new SparseArray<>();
        // examples:
        // <item>displayPort=0,displayType=MAIN,occupantZoneId=0,inputTypes=DPAD_KEYS|
        //            NAVIGATE_KEYS|ROTARY_NAVIGATION</item>
        // <item>displayPort=1,displayType=INSTRUMENT_CLUSTER,occupantZoneId=0,
        //              inputTypes=DPAD_KEYS</item>
        for (String config : res.getStringArray(R.array.config_occupant_display_mapping)) {
            int port = INVALID_PORT;
            String uniqueId = null;
            int type = CarOccupantZoneManager.DISPLAY_TYPE_UNKNOWN;
            int zoneId = OccupantZoneInfo.INVALID_ZONE_ID;
            String[] entries = config.split(",");
            for (String entry : entries) {
                String[] keyValuePair = entry.split("=");
                if (keyValuePair.length != 2) {
                    throwFormatErrorInDisplayMapping("No key/value pair:" + entry);
                }
                switch (keyValuePair[0]) {
                    case "displayPort":
                        port = Integer.parseInt(keyValuePair[1]);
                        break;
                    case "displayUniqueId":
                        uniqueId = keyValuePair[1];
                        break;
                    case "displayType":
                        switch (keyValuePair[1]) {
                            case "MAIN":
                                type = CarOccupantZoneManager.DISPLAY_TYPE_MAIN;
                                break;
                            case "INSTRUMENT_CLUSTER":
                                type = CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER;
                                break;
                            case "HUD":
                                type = CarOccupantZoneManager.DISPLAY_TYPE_HUD;
                                break;
                            case "INPUT":
                                type = CarOccupantZoneManager.DISPLAY_TYPE_INPUT;
                                break;
                            case "AUXILIARY":
                                type = CarOccupantZoneManager.DISPLAY_TYPE_AUXILIARY;
                                break;
                            default:
                                throwFormatErrorInDisplayMapping(
                                        "Unrecognized display type:" + entry);
                                break;
                        }
                        inputTypesPerDisplay.set(type, new IntArray());
                        break;
                    case "occupantZoneId":
                        zoneId = Integer.parseInt(keyValuePair[1]);
                        break;
                    case "inputTypes":
                        String[] inputStrings = keyValuePair[1].split("\\|");
                        for (int i = 0; i < inputStrings.length; i++) {
                            switch (inputStrings[i].trim()) {
                                case "ROTARY_NAVIGATION":
                                    inputTypesPerDisplay.get(type).add(
                                            CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION);
                                    break;
                                case "ROTARY_VOLUME":
                                    inputTypesPerDisplay.get(type).add(
                                            CarInputManager.INPUT_TYPE_ROTARY_VOLUME);
                                    break;
                                case "DPAD_KEYS":
                                    inputTypesPerDisplay.get(type).add(
                                            CarInputManager.INPUT_TYPE_DPAD_KEYS);
                                    break;
                                case "NAVIGATE_KEYS":
                                    inputTypesPerDisplay.get(type).add(
                                            CarInputManager.INPUT_TYPE_NAVIGATE_KEYS);
                                    break;
                                case "SYSTEM_NAVIGATE_KEYS":
                                    inputTypesPerDisplay.get(type).add(
                                            CarInputManager.INPUT_TYPE_SYSTEM_NAVIGATE_KEYS);
                                    break;
                                case "CUSTOM_INPUT_EVENT":
                                    inputTypesPerDisplay.get(type).add(
                                            CarInputManager.INPUT_TYPE_CUSTOM_INPUT_EVENT);
                                    break;
                                case "TOUCH_SCREEN":
                                    inputTypesPerDisplay.get(type).add(
                                            CarInputManager.INPUT_TYPE_TOUCH_SCREEN);
                                    break;
                                case "NONE":
                                    inputTypesPerDisplay.get(type).add(
                                            CarInputManager.INPUT_TYPE_NONE);
                                    break;
                                default:
                                    throw new IllegalArgumentException("Invalid input type: "
                                            + inputStrings[i]);
                            }
                        }
                        break;
                    default:
                        throwFormatErrorInDisplayMapping("Unrecognized key:" + entry);
                        break;
                }
            }

            // Now check validity
            checkInputTypeNoneLocked(inputTypesPerDisplay);
            if (port == INVALID_PORT && uniqueId == null) {
                throwFormatErrorInDisplayMapping(
                        "Missing or invalid displayPort and displayUniqueId:" + config);
            }
            if (type == CarOccupantZoneManager.DISPLAY_TYPE_UNKNOWN) {
                throwFormatErrorInDisplayMapping("Missing or invalid displayType:" + config);
            }
            if (zoneId == OccupantZoneInfo.INVALID_ZONE_ID) {
                throwFormatErrorInDisplayMapping("Missing or invalid occupantZoneId:" + config);
            }
            if (!mOccupantsConfig.contains(zoneId)) {
                throwFormatErrorInDisplayMapping(
                        "Missing or invalid occupantZoneId:" + config);
            }
            final DisplayConfig displayConfig = new DisplayConfig(type, zoneId,
                    inputTypesPerDisplay.get(type));
            if (port != INVALID_PORT) {
                if (mDisplayPortConfigs.contains(port)) {
                    throwFormatErrorInDisplayMapping("Duplicate displayPort:" + config);
                }
                mDisplayPortConfigs.put(port, displayConfig);
            } else {
                if (mDisplayUniqueIdConfigs.containsKey(uniqueId)) {
                    throwFormatErrorInDisplayMapping("Duplicate displayUniqueId:" + config);
                }
                mDisplayUniqueIdConfigs.put(uniqueId, displayConfig);
            }
        }

        Display defaultDisplay = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        if (findDisplayConfigForDisplayLocked(defaultDisplay) == null) {
            int zoneForDefaultDisplay = mDriverZoneId;
            if (zoneForDefaultDisplay == OccupantZoneInfo.INVALID_ZONE_ID) {
                // No driver zone but we still need to allocate the default display to the 1st zone,
                // zone id 0.
                zoneForDefaultDisplay = 0;
            }
            Slogf.w(TAG, "No default display configuration, will assign to zone:"
                    + zoneForDefaultDisplay);
            mDisplayUniqueIdConfigs.put(DisplayHelper.getUniqueId(defaultDisplay),
                    new DisplayConfig(CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                            zoneForDefaultDisplay, inputTypesPerDisplay.get(
                                    CarOccupantZoneManager.DISPLAY_TYPE_MAIN)));
        }
    }

    @GuardedBy("mLock")
    private void checkInputTypeNoneLocked(SparseArray<IntArray> inputTypesPerDisplay) {
        for (int i = 0; i < inputTypesPerDisplay.size(); ++i) {
            IntArray inputTypes = inputTypesPerDisplay.valueAt(i);
            for (int j = 0; j < inputTypes.size(); ++j) {
                if (inputTypes.get(j) == CarInputManager.INPUT_TYPE_NONE && inputTypes.size() > 1) {
                    throw new IllegalArgumentException("Display {" + inputTypesPerDisplay.keyAt(i)
                            + "} has input type NONE defined along with other input types");
                }
            }
        }
    }

    @GuardedBy("mLock")
    private void addDisplayInfoToOccupantZoneLocked(int zoneId, DisplayInfo info) {
        OccupantConfig occupantConfig = mActiveOccupantConfigs.get(zoneId);
        if (occupantConfig == null) {
            occupantConfig = new OccupantConfig();
            mActiveOccupantConfigs.put(zoneId, occupantConfig);
        }
        occupantConfig.displayInfos.add(info);
    }

    @GuardedBy("mLock")
    private void handleActiveDisplaysLocked() {
        // Clear display info for each zone in preparation for re-populating display info.
        for (int i = 0; i < mActiveOccupantConfigs.size(); i++) {
            OccupantConfig occupantConfig = mActiveOccupantConfigs.valueAt(i);
            occupantConfig.displayInfos.clear();
        }

        boolean hasDefaultDisplayConfig = false;
        boolean hasDriverZone = hasDriverZone();
        for (Display display : mDisplayManager.getDisplays()) {
            DisplayConfig displayConfig = findDisplayConfigForDisplayLocked(display);
            if (displayConfig == null) {
                Slogf.w(TAG, "Display id:%d does not have configurations",
                        display.getDisplayId());
                continue;
            }
            if (hasDriverZone && display.getDisplayId() == Display.DEFAULT_DISPLAY) {
                if (displayConfig.occupantZoneId != mDriverZoneId) {
                    throw new IllegalStateException(
                            "Default display should be only assigned to driver zone");
                }
                hasDefaultDisplayConfig = true;
            }
            addDisplayInfoToOccupantZoneLocked(displayConfig.occupantZoneId,
                    new DisplayInfo(display, displayConfig.displayType));
        }

        // Remove OccupantConfig in zones without displays.
        for (int i = 0; i < mActiveOccupantConfigs.size(); i++) {
            OccupantConfig occupantConfig = mActiveOccupantConfigs.valueAt(i);
            if (occupantConfig.displayInfos.size() == 0) {
                if (DBG) {
                    Slogf.d(TAG, "handleActiveDisplaysLocked: removing zone %d due to no display",
                            mActiveOccupantConfigs.keyAt(i));
                }
                mActiveOccupantConfigs.removeAt(i);
            }
        }

        if (hasDriverZone && !hasDefaultDisplayConfig) {
            // This shouldn't happen, since we added the default display config in
            // parseDisplayConfigsLocked().
            throw new IllegalStateException("Default display not assigned");
        }
    }

    @VisibleForTesting
    int getCurrentUser() {
        return ActivityManager.getCurrentUser();
    }

    @GuardedBy("mLock")
    private void updateEnabledProfilesLocked(@UserIdInt int userId) {
        mProfileUsers.clear();
        List<UserHandle> profileUsers = mUserHandleHelper.getEnabledProfiles(userId);
        for (UserHandle profiles : profileUsers) {
            if (profiles.getIdentifier() != userId) {
                mProfileUsers.add(profiles.getIdentifier());
            }
        }
    }

    /** Returns {@code true} if user allocation has changed */
    @GuardedBy("mLock")
    private boolean handleUserChangesLocked() {
        int currentUserId = getCurrentUser();

        if (mEnableProfileUserAssignmentForMultiDisplay) {
            updateEnabledProfilesLocked(currentUserId);
        }

        boolean changed = false;
        for (int i = 0; i < mActiveOccupantConfigs.size(); ++i) {
            int zoneId = mActiveOccupantConfigs.keyAt(i);
            OccupantConfig config = mActiveOccupantConfigs.valueAt(i);
            OccupantZoneInfo info = mOccupantsConfig.get(zoneId);
            // Assign the current user to the driver zone if there is a driver zone.
            if (info.occupantType == CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER
                    && config.userId != currentUserId) {
                config.userId = currentUserId;
                changed = true;
                if (DBG) {
                    Slogf.d(TAG, "Changed driver, current user change to %d",
                            currentUserId);
                }
                continue;
            }
            // Do not touch if the zone is un-assigned.
            if (config.userId == CarOccupantZoneManager.INVALID_USER_ID) {
                continue;
            }
            // Now it will be non-driver valid user id.
            if (!mCarUserService.isUserVisible(config.userId)) {
                if (DBG) Slogf.d(TAG, "Unassigned no longer visible user:%d", config.userId);
                config.userId = CarOccupantZoneManager.INVALID_USER_ID;
                changed = true;
            }
        }

        return changed;
    }

    @GuardedBy("mLock")
    private void handleAudioZoneChangesLocked() {
        for (int index = 0; index < mAudioZoneIdToOccupantZoneIdMapping.size(); index++) {
            int audioZoneId = mAudioZoneIdToOccupantZoneIdMapping.keyAt(index);
            int occupantZoneId = mAudioZoneIdToOccupantZoneIdMapping.get(audioZoneId);
            OccupantConfig occupantConfig = mActiveOccupantConfigs.get(occupantZoneId);
            if (occupantConfig == null) {
                //no active display for zone just continue
                continue;
            }
            // Found an active configuration, add audio to it.
            occupantConfig.audioZoneId = audioZoneId;
        }
    }

    private void sendConfigChangeEvent(int changeFlags) {
        boolean updateDisplay = false;
        boolean updateUser = false;
        if ((changeFlags & CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_DISPLAY) != 0) {
            updateDisplay = true;
            updateUser = true;
        } else if ((changeFlags & CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER) != 0) {
            updateUser = true;
        }
        doSyncWithCarServiceHelper(updateDisplay, updateUser);

        // Schedule remote callback invocation with the handler attached to the same Looper to
        // ensure that only one broadcast can be active at one time.
        mHandler.post(() -> {
            int n = mClientCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                ICarOccupantZoneCallback callback = mClientCallbacks.getBroadcastItem(i);
                try {
                    callback.onOccupantZoneConfigChanged(changeFlags);
                } catch (RemoteException ignores) {
                    // ignore
                }
            }
            mClientCallbacks.finishBroadcast();
        });
    }

    private void handleUserChange() {
        boolean changed;
        synchronized (mLock) {
            changed = handleUserChangesLocked();
        }
        if (changed) {
            sendConfigChangeEvent(CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER);
        }
    }

    private void handlePassengerStarted() {
        sendConfigChangeEvent(CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER);
    }

    private void handlePassengerStopped() {
        sendConfigChangeEvent(CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER);
    }

    private void handleDisplayChange() {
        synchronized (mLock) {
            handleActiveDisplaysLocked();
            // Audio zones should be re-checked for changed display
            handleAudioZoneChangesLocked();
            // User should be re-checked for changed displays
            handleUserChangesLocked();
        }
        sendConfigChangeEvent(CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_DISPLAY);
    }

    private void enforcePermission(String permissionName) {
        if (mContext.checkCallingOrSelfPermission(permissionName)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("requires permission " + permissionName);
        }
    }

    /**
     * Returns the supported input types for the occupant zone info and display type passed as
     * the argument.
     *
     * @param occupantZoneId the occupant zone id of the supported input types to find
     * @param displayType    the display type of the supported input types to find
     * @return the supported input types for the occupant zone info and display type passed in as
     * the argument
     */
    public int[] getSupportedInputTypes(int occupantZoneId, int displayType) {
        checkOccupantZone(occupantZoneId, displayType);
        synchronized (mLock) {
            // Search input type in mDisplayPortConfigs
            for (int i = 0; i < mDisplayPortConfigs.size(); i++) {
                DisplayConfig config = mDisplayPortConfigs.valueAt(i);
                if (config.displayType == displayType && config.occupantZoneId == occupantZoneId) {
                    return config.inputTypes;
                }
            }
            // Search input type in mDisplayUniqueIdConfigs
            for (int i = 0; i < mDisplayUniqueIdConfigs.size(); i++) {
                DisplayConfig config = mDisplayUniqueIdConfigs.valueAt(i);
                if (config.displayType == displayType && config.occupantZoneId == occupantZoneId) {
                    return config.inputTypes;
                }
            }
        }
        return EMPTY_INPUT_SUPPORT_TYPES;
    }

    private void checkOccupantZone(int occupantZoneId, int displayType) {
        if (Display.INVALID_DISPLAY == getDisplayForOccupant(occupantZoneId, displayType)) {
            throw new IllegalArgumentException("No display is associated with OccupantZoneInfo "
                    + occupantZoneId);
        }
    }
}
