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

package com.android.car.evs;

import static android.car.evs.CarEvsManager.ERROR_BUSY;
import static android.car.evs.CarEvsManager.ERROR_NONE;
import static android.car.evs.CarEvsManager.ERROR_UNAVAILABLE;
import static android.car.evs.CarEvsManager.SERVICE_STATE_ACTIVE;
import static android.car.evs.CarEvsManager.SERVICE_STATE_INACTIVE;
import static android.car.evs.CarEvsManager.SERVICE_STATE_REQUESTED;
import static android.car.evs.CarEvsManager.SERVICE_STATE_UNAVAILABLE;
import static android.car.evs.CarEvsManager.STREAM_EVENT_STREAM_STOPPED;

import static com.android.car.CarLog.TAG_EVS;
import static com.android.car.evs.StateMachine.REQUEST_PRIORITY_LOW;
import static com.android.car.evs.StateMachine.REQUEST_PRIORITY_NORMAL;
import static com.android.car.evs.StateMachine.REQUEST_PRIORITY_HIGH;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.Car;
import android.car.VehiclePropertyIds;
import android.car.builtin.content.pm.PackageManagerHelper;
import android.car.builtin.os.BuildHelper;
import android.car.builtin.util.Slogf;
import android.car.evs.CarEvsBufferDescriptor;
import android.car.evs.CarEvsManager;
import android.car.evs.CarEvsManager.CarEvsError;
import android.car.evs.CarEvsManager.CarEvsServiceState;
import android.car.evs.CarEvsManager.CarEvsServiceType;
import android.car.evs.CarEvsStatus;
import android.car.evs.ICarEvsStatusListener;
import android.car.evs.ICarEvsStreamCallback;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarPropertyEventListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.automotive.vehicle.VehicleGear;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseArray;
import android.util.proto.ProtoOutputStream;
import android.view.Display;

import com.android.car.CarPropertyService;
import com.android.car.CarServiceBase;
import com.android.car.CarServiceUtils;
import com.android.car.R;
import com.android.car.hal.EvsHalService;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.evs.CarEvsUtils;
import com.android.car.internal.evs.EvsHalWrapper;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A service that listens to the Extended View System across a HAL boundary and exposes the data to
 * system clients in Android via {@link android.car.evs.CarEvsManager}.
 *
 * Because of Fast Message Queue usages, android.hardware.automotive.evs@1.1 interfaces does not
 * support Java backend and, therefore, actual API calls are done in native methods.
 *
 *
 * CarEvsService consists of four states:
 *
 * UNAVAILABLE: CarEvsService is not connected to the Extended View System service.  In this
 * state, any service request will be declined.
 *
 * INACTIVE: CarEvsService has a valid, live connection the Extended View System service and
 * ready for any service requests.
 *
 * REQUESTED: CarEvsService received a service requeste from a privileged client and requested
 * the System UI to launch the camera viewing activity.
 *
 * ACTIVE: CarEvsService is actively streaming a video to the client.
 *
 * See CarEvsService.StateMachine class for more details.
 */
public final class CarEvsService extends android.car.evs.ICarEvsService.Stub
        implements CarServiceBase {

    private static final boolean DBG = Slogf.isLoggable(TAG_EVS, Log.DEBUG);

    static final class EvsHalEvent {
        private long mTimestamp;
        private int mServiceType;
        private boolean mOn;

        public EvsHalEvent(long timestamp, @CarEvsServiceType int type, boolean on) {
            mTimestamp = timestamp;
            mServiceType = type;
            mOn = on;
        }

        public long getTimestamp() {
            return mTimestamp;
        }

        public @CarEvsServiceType int getServiceType() {
            return mServiceType;
        }

        public boolean isRequestingToStartActivity() {
            return mOn;
        }

        @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
        public String toString() {
            return "ServiceType=" + CarEvsUtils.convertToString(mServiceType) +
                    ", mOn=" + mOn + ", Timestamp=" + mTimestamp;
        }
    }

    private final Context mContext;
    private final Context mBuiltinContext;
    private final EvsHalService mEvsHalService;
    private final CarPropertyService mPropertyService;
    private final DisplayManager mDisplayManager;  // To monitor the default display's state
    private final Object mLock = new Object();
    private final ArraySet<IBinder> mSessionTokens = new ArraySet<>();

    // This handler is to monitor the client sends a video stream request within a given time
    // after a state transition to the REQUESTED state.
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final class StatusListenerList extends RemoteCallbackList<ICarEvsStatusListener> {
        private final WeakReference<CarEvsService> mService;

        StatusListenerList(CarEvsService evsService) {
            mService = new WeakReference<>(evsService);
        }

        /** Handle callback death */
        @Override
        public void onCallbackDied(ICarEvsStatusListener listener) {
            Slogf.w(TAG_EVS, "StatusListener has died: " + listener.asBinder());

            CarEvsService svc = mService.get();
            if (svc != null) {
                svc.handleClientDisconnected(listener);
            }
        }
    }

    private final StatusListenerList mStatusListeners = new StatusListenerList(this);

    /**
     * {@link CarPropertyEvent} listener registered with {@link CarPropertyService} to listen to
     * {@link VehicleProperty.GEAR_SELECTION} change notifications.
     */
    private final ICarPropertyEventListener mGearSelectionPropertyListener =
            new ICarPropertyEventListener.Stub() {
                @Override
                public void onEvent(List<CarPropertyEvent> events) throws RemoteException {
                    if (events.isEmpty()) {
                        return;
                    }

                    // Handle only the latest event
                    Slogf.i(TAG_EVS, "Handling GearSelection event");
                    handlePropertyEvent(events.get(events.size() - 1));
                }
            };

    private final DisplayListener mDisplayListener = new DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                // Nothing to do
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                // Nothing to do
            }

            @Override
            public void onDisplayChanged(int displayId) {
                if (displayId != Display.DEFAULT_DISPLAY) {
                    // We are interested only in the default display.
                    return;
                }

                Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
                if (mCurrentDisplayState == display.getState()) {
                    // We already handled this display state change.
                    Slogf.i(TAG_EVS, "We already handled a reported display status, %d",
                            display.getState());
                    return;
                }

                // TODO(b/292155786): Current implementation is optimized for the device with a
                //                    single display and therefore we may need to consider the
                //                    source of the display event and start/stop activities
                //                    accordingly.
                switch (display.getState()) {
                    case Display.STATE_ON:
                        // Requests each StateMachine to launch a registered activity if it's
                        // necessary.
                        for (int i = 0; i < mServiceInstances.size(); i++) {
                            if (mServiceInstances.valueAt(i)
                                    .requestStartActivityIfNecessary() == ERROR_NONE) {
                                continue;
                            }
                            Slogf.e(TAG_EVS, "Failed to start %s's activity.",
                                    CarEvsUtils.convertToString(mServiceInstances.keyAt(i)));
                        }
                        break;

                    case Display.STATE_OFF:
                        // Each StateMachine stores a valid session token that was used for
                        // recognizing a streaming callback from a launched activity.
                        // CarEvsService will request each StateMachine to stop those callbacks
                        // and let other callbacks continue running. Activities not launched by
                        // CarEvsService must handle display's state changes properly by
                        // themselves.
                        for (int i = 0; i < mServiceInstances.size(); i++) {
                            mServiceInstances.valueAt(i)
                                    .requestStopActivity(REQUEST_PRIORITY_HIGH);
                        }
                        break;

                    default:
                        // Nothing to do for all other state changes
                        break;
                }

                mCurrentDisplayState = display.getState();
            }
        };

    // Service instances per each type.
    private final SparseArray<StateMachine> mServiceInstances;

    // Associates callback objects with their service types.
    private final ArrayMap<IBinder, ArraySet<Integer>> mCallbackToServiceType =
            new ArrayMap<>();

    // The latest display state we have processed.
    private int mCurrentDisplayState = Display.STATE_OFF;

    // This boolean flag is true if CarEvsService uses GEAR_SELECTION VHAL property instead of
    // EVS_SERVICE_REQUEST.
    private boolean mUseGearSelection = true;

    // The last event EvsHalService reported.  This will be set to null when a related service
    // request is handled.
    //
    // To properly handle a HAL event that occurred before CarEvsService is ready, we initialize
    // mLastEvsHalEvent with a zero timestamp here.
    @GuardedBy("mLock")
    private EvsHalEvent mLastEvsHalEvent = new EvsHalEvent(/* timestamp= */ 0,
            CarEvsManager.SERVICE_TYPE_REARVIEW, /* on= */ false);

    /** Creates an Extended View System service instance given a {@link Context}. */
    public CarEvsService(Context context, Context builtinContext, EvsHalService halService,
            CarPropertyService propertyService) {
        mContext = context;
        mBuiltinContext = builtinContext;
        mPropertyService = propertyService;
        mEvsHalService = halService;

        // Reads the service configuration and initializes service instances.
        String[] rawConfigurationStrings = mContext.getResources()
                .getStringArray(R.array.config_carEvsService);
        if (rawConfigurationStrings != null && rawConfigurationStrings.length > 0) {
            mServiceInstances = new SparseArray<>(rawConfigurationStrings.length);
            for (String rawString : rawConfigurationStrings) {
                CarEvsServiceUtils.Parameters params = CarEvsServiceUtils.parse(rawString);

                StateMachine s = new StateMachine(context, builtinContext, this,
                        params.getActivityComponentName(), params.getType(), params.getCameraId());
                mServiceInstances.put(params.getType(), s);
            }

            if (mServiceInstances.size() < 1) {
                Slogf.e(TAG_EVS, "No valid configuration has been found. " +
                        "CarEvsService won't be available.");
                mDisplayManager = null;
                return;
            }
        } else {
            mServiceInstances = new SparseArray<>(/* capacity= */ 1);
            Slogf.i(TAG_EVS, "CarEvsService will be initialized only for the rearview service " +
                             "because no service configuration was available via " +
                             "config_carEvsService.");

            String activityName = mContext.getResources()
                    .getString(R.string.config_evsCameraActivity);
            ComponentName activityComponentName;
            if (!activityName.isEmpty()) {
                activityComponentName = ComponentName.unflattenFromString(activityName);
            } else {
                activityComponentName = null;
            }
            if (DBG) Slogf.d(TAG_EVS, "evsCameraActivity=" + activityName);

            String cameraId = context.getString(R.string.config_evsRearviewCameraId);
            StateMachine s = new StateMachine(context, builtinContext, this, activityComponentName,
                    CarEvsManager.SERVICE_TYPE_REARVIEW, cameraId);
            mServiceInstances.put(CarEvsManager.SERVICE_TYPE_REARVIEW, s);
        }

        mDisplayManager = context.getSystemService(DisplayManager.class);
        mDisplayManager.registerDisplayListener(mDisplayListener, mHandler);
    }

    @VisibleForTesting
    final class EvsTriggerListener implements EvsHalService.EvsHalEventListener {

        /** Implements EvsHalService.EvsHalEventListener to monitor VHAL properties. */
        @Override
        public void onEvent(@CarEvsServiceType int type, boolean on) {
            if (DBG) {
                Slogf.d(TAG_EVS,
                        "Received an event from EVS HAL: type = " + type + ", on = " + on);
            }

            StateMachine instance = mServiceInstances.get(type);
            if (instance == null) {
                Slogf.w(TAG_EVS, "CarEvsService is not configured for %s", type);
                return;
            }

            // Stores the last event.
            synchronized (mLock) {
                mLastEvsHalEvent = new EvsHalEvent(SystemClock.elapsedRealtimeNanos(), type, on);
            }

            if (on) {
                // Request a camera activity.
                if (instance.requestStartActivity(REQUEST_PRIORITY_HIGH) != ERROR_NONE) {
                    Slogf.e(TAG_EVS, "Fail to request a registered activity.");
                }
            } else {
                // Stop a video stream and close an activity.
                if (instance.requestStopActivity(REQUEST_PRIORITY_HIGH) != ERROR_NONE) {
                    Slogf.e(TAG_EVS, "Fail to stop a registered activity.");
                }
            }
        }
    }

    @VisibleForTesting
    final EvsTriggerListener mEvsTriggerListener = new EvsTriggerListener();

    @Override
    public void init() {
        if (DBG) {
            Slogf.d(TAG_EVS, "Initializing the service");
        }

        for (int i = mServiceInstances.size() - 1; i >= 0; i--) {
            StateMachine instance = mServiceInstances.valueAt(i);
            if (instance.init()) {
                continue;
            }

            Slogf.e(TAG_EVS, "Failed to initialize a service handle for %s.",
                    mServiceInstances.keyAt(i));
            mServiceInstances.removeAt(i);
        }

        if (mEvsHalService.isEvsServiceRequestSupported()) {
            try {
                mEvsHalService.setListener(mEvsTriggerListener);
                if (DBG) {
                    Slogf.d(TAG_EVS, "CarEvsService listens to EVS_SERVICE_REQUEST property.");
                }
                mUseGearSelection = false;
            } catch (IllegalStateException e) {
                Slogf.w(TAG_EVS, "Failed to set a EvsHalService listener. Try to use "
                        + "GEAR_SELECTION.");
            }
        }

        if (mUseGearSelection) {
            if (mPropertyService == null || mPropertyService.getPropertySafe(
                    VehiclePropertyIds.GEAR_SELECTION, /*areaId=*/ 0) == null) {
                Slogf.w(TAG_EVS,
                        "GEAR_SELECTION property is also not available. " +
                        "CarEvsService may not respond to the system events.");
                mUseGearSelection = false;
            } else {
                if (DBG) {
                    Slogf.d(TAG_EVS, "CarEvsService listens to GEAR_SELECTION property.");
                }

                if (!mPropertyService.registerListenerSafe(
                        VehiclePropertyIds.GEAR_SELECTION, /*updateRateHz=*/0,
                        mGearSelectionPropertyListener)) {
                    Slogf.w(TAG_EVS, "Failed to register a listener for GEAR_SELECTION property.");
                    mUseGearSelection = false;
                }
            }
        }

        StateMachine instance = mServiceInstances.get(CarEvsManager.SERVICE_TYPE_REARVIEW);
        if (instance == null) {
            Slogf.w(TAG_EVS, "The service is not initialized for the rearview service.");
            return;
        }

        instance.connectToHalServiceIfNecessary();
    }

    @Override
    public void release() {
        if (DBG) {
            Slogf.d(TAG_EVS, "Finalizing the service");
        }

        mDisplayManager.unregisterDisplayListener(mDisplayListener);

        if (mUseGearSelection && mPropertyService != null) {
            if (DBG) {
                Slogf.d(TAG_EVS, "Unregister a property listener in release()");
            }
            mPropertyService.unregisterListenerSafe(VehiclePropertyIds.GEAR_SELECTION,
                    mGearSelectionPropertyListener);
        }

        for (int i = 0; i < mServiceInstances.size(); i++) {
            StateMachine instance = mServiceInstances.valueAt(i);
            instance.release();
        }

        mStatusListeners.kill();
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("*CarEvsService*");

        writer.increaseIndent();
        for (int i = 0; i < mServiceInstances.size(); i++) {
            mServiceInstances.valueAt(i).dump(writer);
        }
        writer.decreaseIndent();
        writer.printf("\n");

        synchronized (mLock) {
            writer.printf("%d service listeners subscribed.\n",
                    mStatusListeners.getRegisteredCallbackCount());
            writer.printf("Last HAL event: %s\n", mLastEvsHalEvent);
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {}

    /**
     * Registers a {@link ICarEvsStatusListener} to listen requests to control the camera
     * previewing activity.
     *
     * <p>Requires {@link android.car.Car.PERMISSION_MONITOR_CAR_EVS_STATUS} permissions to
     * access.
     *
     * @param listener {@link ICarEvsStatusListener} listener to register.
     */
    @Override
    public void registerStatusListener(@NonNull ICarEvsStatusListener listener) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_MONITOR_CAR_EVS_STATUS);
        Objects.requireNonNull(listener);

        if (DBG) {
            Slogf.d(TAG_EVS, "Registering a new service listener");
        }
        mStatusListeners.register(listener);
    }

    /**
     * Unregister the given {@link ICarEvsStatusListener} listener from receiving events.
     *
     * <p>Requires {@link android.car.Car.PERMISSION_MONITOR_CAR_EVS_STATUS} permissions to
     * access.
     *
     * @param listener {@link ICarEvsStatusListener} listener to unregister.
     */
    @Override
    public void unregisterStatusListener(@NonNull ICarEvsStatusListener listener) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_MONITOR_CAR_EVS_STATUS);
        Objects.requireNonNull(listener);

        mStatusListeners.unregister(listener);
    }

    /**
     * Requests the system to start an activity to show the preview from a given EVS service type.
     *
     * <p>Requires {@link android.car.Car.PERMISSION_REQUEST_CAR_EVS_ACTIVITY} permissions to
     * access.
     *
     * @param type {@link android.car.evs.CarEvsManager#CarEvsServiceType}
     * @return {@link android.car.evs.CarEvsManager#CarEvsError}
     */
    @Override
    public @CarEvsError int startActivity(int type) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_REQUEST_CAR_EVS_ACTIVITY);

        if (type == CarEvsManager.SERVICE_TYPE_SURROUNDVIEW) {
            // TODO(b/179029031): Removes below when Surround View service is integrated.
            Slogf.e(TAG_EVS, "Surround view is not supported yet.");
            return ERROR_UNAVAILABLE;
        }

        StateMachine instance = mServiceInstances.get(type);
        if (instance == null) {
            return ERROR_UNAVAILABLE;
        }

        return instance.requestStartActivity(REQUEST_PRIORITY_NORMAL);
    }

    /**
     * Requests to stop a current previewing activity launched via {@link #startActivity}.
     *
     * <p>Requires {@link android.car.Car.PERMISSION_REQUEST_CAR_EVS_ACTIVITY} permissions to
     * access.
     */
    @Override
    public void stopActivity() {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_REQUEST_CAR_EVS_ACTIVITY);

        StateMachine instance = mServiceInstances.get(CarEvsManager.SERVICE_TYPE_REARVIEW);
        if (instance == null) {
            return;
        }

        instance.requestStopActivity(REQUEST_PRIORITY_NORMAL);
    }

    /**
     * Starts a video stream.
     *
     * <p>Requires {@link android.car.Car.PERMISSION_USE_CAR_EVS_CAMERA} permissions to access.
     *
     * @param type {@link android.car.evs.CarEvsManager#CarEvsServiceType}
     * @param token IBinder object as a session token.  If this is not null, CarEvsService handles a
     *              coming client as a privileged client.
     * @param callback {@link ICarEvsStreamCallback} listener to register.
     * @return {@link android.car.evs.CarEvsManager.CarEvsError}
     */
    @Override
    public @CarEvsError int startVideoStream(@CarEvsServiceType int type, @Nullable IBinder token,
            @NonNull ICarEvsStreamCallback callback) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_USE_CAR_EVS_CAMERA);
        Objects.requireNonNull(callback);

        StateMachine instance = mServiceInstances.get(type);
        if (instance == null) {
            Slogf.e(TAG_EVS, "CarEvsService is not configured for a service type %d.", type);
            return ERROR_UNAVAILABLE;
        }

        // Single client can subscribe to multiple services.
        // ArrayMap<IBinder, ArraySet<Integer>>
        // Remembers which service a given callback is subscribing to.
        ArraySet<Integer> types = mCallbackToServiceType.get(callback.asBinder());
        if (types == null) {
            mCallbackToServiceType.put(callback.asBinder(),
                    new ArraySet<>(Set.of(new Integer(type))));
        } else {
            types.add(type);
        }

        return instance.requestStartVideoStream(callback, token);
    }

    /**
     * Requests to stop a video stream from the current client.
     *
     * <p>Requires {@link android.car.Car.PERMISSION_USE_CAR_EVS_CAMERA} permissions to access.
     *
     * @param callback {@link ICarEvsStreamCallback} listener to unregister.
     */
    @Override
    public void stopVideoStream(@NonNull ICarEvsStreamCallback callback) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_USE_CAR_EVS_CAMERA);
        Objects.requireNonNull(callback);

        ArraySet<Integer> types = mCallbackToServiceType.get(callback.asBinder());
        if (types == null || types.isEmpty()) {
            Slogf.i(TAG_EVS, "Ignores a request to stop a video stream for unknown callback %s.",
                    callback);
            return;
        }

        for (int i = 0; i < types.size(); i++) {
            int type = types.valueAt(i);
            StateMachine instance = mServiceInstances.get(type);
            if (instance == null) {
                Slogf.w(TAG_EVS, "CarEvsService is not configured for a service type %d.", type);
                continue;
            }

            instance.requestStopVideoStream(callback);
        }
        mCallbackToServiceType.remove(callback.asBinder());
    }

    /**
     * Requests to stop a video stream from a given service type.
     *
     * <p>Requires {@link android.car.Car.PERMISSION_USE_CAR_EVS_CAMERA} permissions to access.
     *
     * @param type {@link CarEvsServiceType} to stop listening.
     * @param callback {@link ICarEvsStreamCallback} listener to unregister.
     */
    @Override
    public void stopVideoStreamFrom(@CarEvsServiceType int type,
            @NonNull ICarEvsStreamCallback callback) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_USE_CAR_EVS_CAMERA);
        Objects.requireNonNull(callback);

        ArraySet<Integer> types = mCallbackToServiceType.get(callback.asBinder());
        int idx = types.indexOf(type);
        if (idx < 0) {
            Slogf.i(TAG_EVS, "Ignores a request to stop a video stream that is not active.");
            return;
        }

        StateMachine instance = mServiceInstances.get(type);
        if (instance == null) {
            Slogf.w(TAG_EVS, "CarEvsService is not configured for a service type %d.", type);
            return;
        }

        instance.requestStopVideoStream(callback);
        types.remove(type);
        if (types.isEmpty()) {
            // No more active stream for this callback object.
            mCallbackToServiceType.remove(callback.asBinder());
        }
    }

    /**
     * Returns an used buffer to EVS service.
     *
     * <p>Requires {@link android.car.Car.PERMISSION_USE_CAR_EVS_CAMERA} permissions to access.
     *
     * @param buffer A consumed CarEvsBufferDescriptor object.  This would not be used and returned
     *               to the native EVS service.
     * @throws IllegalArgumentException if a passed buffer has an unregistered identifier.
     */
    @Override
    public void returnFrameBuffer(@NonNull CarEvsBufferDescriptor buffer) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_USE_CAR_EVS_CAMERA);
        Objects.requireNonNull(buffer);

        // 8 MSB tells the service type of this buffer.
        @CarEvsServiceType int type = CarEvsUtils.getTag(buffer.getId());
        mServiceInstances.get(type).doneWithFrame(buffer.getId());
    }

    /**
     * Returns a current status of CarEvsService's REARVIEW service type.
     *
     * <p>Requires {@link android.car.Car.PERMISSION_MONITOR_CAR_EVS_STATUS} permissions to
     * access.
     *
     * @return {@link android.car.evs.CarEvsStatus}
     */
    @Override
    @Nullable
    public CarEvsStatus getCurrentStatus(@CarEvsServiceType int type) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_MONITOR_CAR_EVS_STATUS);

        // This public API only returns current status of SERVICE_TYPE_REARVIEW. To get other
        // services' status, please register a status listener via
        // CarEvsService.registerStatusListener() API.
        StateMachine instance = mServiceInstances.get(type);
        if (instance == null) {
            return null;
        }

        return instance.getCurrentStatus();
    }

    /**
     * Returns a session token to be used to request the services.
     *
     * <p>Requires {@link android.car.Car.PERMISSION_CONTROL_CAR_EVS_ACTIVITY} permission to access.
     *
     * @return IBinder object as a session token.
     * @throws IllegalStateException if we fail to find System UI package.
     */
    @Override
    public IBinder generateSessionToken() {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CONTROL_CAR_EVS_ACTIVITY);

        // TODO(b/191940626): With the unlimited multi-client supports, a validity of a session
        //                    token does not make any different in handling streaming clients. This
        //                    needs to be modified with a logic to manage the maximum number of
        //                    streaming clients per service.
        String systemUiPackageName = PackageManagerHelper.getSystemUiPackageName(mContext);
        IBinder token = new Binder();
        try {
            int systemUiUid = PackageManagerHelper.getPackageUidAsUser(mContext.getPackageManager(),
                    systemUiPackageName, UserHandle.SYSTEM.getIdentifier());
            int callerUid = Binder.getCallingUid();
            if (systemUiUid == callerUid) {
                mSessionTokens.add(token);
            } else {
                throw new SecurityException("SystemUI only can generate SessionToken");
            }
        } catch (NameNotFoundException e) {
            throw new IllegalStateException(systemUiPackageName + " package not found", e);
        } finally {
            return token;
        }
    }

    /**
     * Returns whether or not a given service type is supported.
     *
     * <p>Requires {@link android.car.Car.PERMISSION_MONITOR_CAR_EVS_STATUS} permissions to
     * access.
     */
    @Override
    public boolean isSupported(@CarEvsServiceType int type) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_MONITOR_CAR_EVS_STATUS);

        StateMachine instance = mServiceInstances.get(type);
        if (instance == null) {
            return false;
        }

        return instance.isConnected();
    }

    /**
     * Sets a camera device for the rearview.
     *
     * <p>Requires {@link android.car.Car.PERMISSION_USE_CAR_EVS_CAMERA} permissions to access.
     *
     * @param id A string identifier of a target camera device.
     * @return This method return a false if this runs in a release build; otherwise, this returns
     *         true.
     */
    public boolean setRearviewCameraIdFromCommand(@NonNull String id) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_USE_CAR_EVS_CAMERA);
        Objects.requireNonNull(id);

        if (!BuildHelper.isDebuggableBuild()) {
            // This method is not allowed in the release build.
            Slogf.e(TAG_EVS, "It is not allowed to change a camera assigned to the rearview " +
                    "in the release build.");
            return false;
        }

        mServiceInstances.get(CarEvsManager.SERVICE_TYPE_REARVIEW).setCameraId(id);
        return true;
    }

    /**
     * Sets a camera device for a given service type.
     *
     * <p>Requires {@link android.car.Car.PERMISSION_USE_CAR_EVS_CAMERA} permissions to access.
     *
     * @param type A service type to assign a camera associated with a given string identifier.
     *             Please use '*' part of CarEvsManager.SERVICE_TYPE_* constants.
     * @param id A string identifier of a target camera device.
     * @return This method return true if it successfully programs a camera id for a given service
     *         type. Otherwise, this will return false.
     */
    public boolean setCameraIdFromCommand(@NonNull String type, @NonNull String id) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_USE_CAR_EVS_CAMERA);

        if (!BuildHelper.isDebuggableBuild()) {
            // This method is not allowed in the release build.
            Slogf.e(TAG_EVS, "It is not allowed to change a camera id assigned to the service " +
                    "in the release build.");
            return false;
        }

        @CarEvsServiceType int serviceType = CarEvsUtils.convertToServiceType(type);
        StateMachine instance = mServiceInstances.get(serviceType);
        if (instance == null) {
            Slogf.e(TAG_EVS, "Ignores a request to set a camera %s for unavailable service %s.",
                    id, type);
            return false;
        }

        instance.setCameraId(id);
        return true;
    }

    /**
     * Gets an identifier of a current camera device for the rearview.
     *
     * <p>Requires {@link android.car.Car.PERMISSION_MONITOR_CAR_EVS_STATUS} permissions to
     * access.
     *
     * @return A string identifier of current rearview camera device.
     */
    @NonNull
    public String getRearviewCameraIdFromCommand() {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_MONITOR_CAR_EVS_STATUS);
        return mServiceInstances.get(CarEvsManager.SERVICE_TYPE_REARVIEW).getCameraId();
    }

    /**
     * Gets a String identifier of a camera assigned to a given service type.
     *
     * <p>Requires {@link android.car.Car.PERMISSION_MONITOR_CAR_EVS_STATUS} permissions to
     * access.
     *
     * @param type A service type to get a camera identifier. Please use "*" part of
     *             CarEvsManager.SERVICE_TYPE_* constants.
     * @return A string identifier of a camera assigned to a given service type.
     */
    @Nullable
    public String getCameraIdFromCommand(@NonNull String type) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_MONITOR_CAR_EVS_STATUS);
        @CarEvsServiceType int serviceType = CarEvsUtils.convertToServiceType(type);
        StateMachine instance = mServiceInstances.get(serviceType);
        if (instance == null) {
            return null;
        }

        return instance.getCameraId();
    }

    /**
     * Enables a given service type with a specified camera device.
     *
     * <p>Requires {@link android.car.Car.PERMISSION_USE_CAR_EVS_CAMERA} permissions to access.
     *
     * @param typeString A service type to get a camera identifier. Please use "*" part of
     *             CarEvsManager.SERVICE_TYPE_* constants.
     * @param cameraId A string identifier of a target camera device. A camera associated with this
     *                 id must not be assigned to any service type.
     * @return false if a requested service type is already enabled or a specific camera id is
     *               already assigned to other service types.
     *         true otherwise.
     */
    public boolean enableServiceTypeFromCommand(@NonNull String typeString,
            @NonNull String cameraId) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_USE_CAR_EVS_CAMERA);

        @CarEvsServiceType int serviceType = CarEvsUtils.convertToServiceType(typeString);
        for (int i = 0; i < mServiceInstances.size(); i++) {
            int type = mServiceInstances.keyAt(i);
            StateMachine instance = mServiceInstances.valueAt(i);

            if (type == serviceType || cameraId.equals(instance.getCameraId())) {
                Slogf.e(TAG_EVS, "A requested service type is already provided by " +
                        " or a given camera id is used by %s.", instance);
                return false;
            }
        }

        StateMachine s = new StateMachine(mContext, mBuiltinContext, this, null,
                serviceType, cameraId);
        if (!s.init()) {
            Slogf.e(TAG_EVS, "Failed to initialize a requested service type.");
            return false;
        }
        s.connectToHalServiceIfNecessary();
        mServiceInstances.put(serviceType, s);
        return true;
    }

    /**
     * Checks whether or not a given service type is enabled.
     *
     * <p>Requires {@link android.car.Car.PERMISSION_MONITOR_CAR_EVS_STATUS} permissions to
     * access.
     *
     * @param type A service type to get a camera identifier. Please use "*" part of
     *             CarEvsManager.SERVICE_TYPE_* constants.
     * @return true if a given service type is available.
     *         false otherwise.
     */
    public boolean isServiceTypeEnabledFromCommand(@NonNull String type) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_MONITOR_CAR_EVS_STATUS);
        @CarEvsServiceType int serviceType = CarEvsUtils.convertToServiceType(type);
        return mServiceInstances.get(serviceType) != null;
    }

    /** Checks whether or not a given token is valid. */
    boolean isSessionToken(IBinder token) {
        return mSessionTokens.contains(token);
    }

    /** Invalidate a given token. */
    void invalidateSessionToken(IBinder token) {
        mSessionTokens.remove(token);
    }

    /** Package-private version of generateSessionToken() method. */
    @NonNull
    IBinder generateSessionTokenInternal() {
        IBinder token = new Binder();
        mSessionTokens.add(token);
        return token;
    }

    /**
     * Manually sets a stream callback.
     */
    @VisibleForTesting
    void addStreamCallback(@CarEvsServiceType int type, @Nullable ICarEvsStreamCallback callback) {
        StateMachine instance = mServiceInstances.get(type);
        if (instance == null || callback == null) {
            return;
        }

        instance.addStreamCallback(callback);

        ArraySet<Integer> types = mCallbackToServiceType.get(callback.asBinder());
        if (types == null) {
            mCallbackToServiceType.put(callback.asBinder(),
                    new ArraySet<>(Set.of(new Integer(type))));
        } else {
            types.add(type);
        }
    }

    /** Tells whether or not the latest EVS HAL event was requesting to start an activity. */
    boolean needToStartActivity() {
        synchronized (mLock) {
            return mLastEvsHalEvent != null && mLastEvsHalEvent.isRequestingToStartActivity();
        }
    }

    /**
     * Manually sets a current service state.
     */
    @VisibleForTesting
    void setServiceState(@CarEvsServiceType int type, @CarEvsServiceState int newState) {
        StateMachine instance = mServiceInstances.get(type);
        if (instance == null) {
            return;
        }

        instance.setState(newState);
    }

    /**
     * Manually chooses to use a gear selection property or not.
     */
    @VisibleForTesting
    void setToUseGearSelection(boolean useGearSelection) {
        mUseGearSelection = useGearSelection;
    }

    /**
     * Manually sets the last EVS HAL event.
     */
    @VisibleForTesting
    void setLastEvsHalEvent(long timestamp, @CarEvsServiceType int type, boolean on) {
        synchronized (mLock) {
            mLastEvsHalEvent = new EvsHalEvent(timestamp, type, on);
        }
    }

    /** Handles client disconnections; may request to stop a video stream. */
    private void handleClientDisconnected(ICarEvsStreamCallback callback) {
        // If the last stream client is disconnected before it stops a video stream, request to stop
        // current video stream.
        ArraySet<Integer> types = mCallbackToServiceType.get(callback.asBinder());
        if (types == null) {
            Slogf.d(TAG_EVS, "Ignores an incidental loss of unknown callback %s.",
                    callback.asBinder());
            return;
        }

        for (int i = 0; i < types.size(); i++) {
            StateMachine instance = mServiceInstances.get(types.valueAt(i));
            if (instance == null) {
                Slogf.i(TAG_EVS, "Ignores an incidental loss of a callback %s for service %d.",
                        callback.asBinder(), types.valueAt(i));
                return;
            }

            instance.handleClientDisconnected(callback);
        }
    }

    /** Notifies the service status gets changed */
    void broadcastStateTransition(int type, int state) {
        int idx = mStatusListeners.beginBroadcast();
        while (idx-- > 0) {
            ICarEvsStatusListener listener = mStatusListeners.getBroadcastItem(idx);
            try {
                listener.onStatusChanged(new CarEvsStatus(type, state));
            } catch (RemoteException e) {
                // Likely the binder death incident.
                Slogf.e(TAG_EVS, Log.getStackTraceString(e));
            }
        }
        mStatusListeners.finishBroadcast();
    }

    /** Stops a current service */
    void stopService() {
        stopService(/* callback= */ null);
    }

    private void stopService(ICarEvsStreamCallback callback) {
        ArraySet<Integer> types = mCallbackToServiceType.get(callback.asBinder());
        if (types == null || types.isEmpty()) {
            Slogf.d(TAG_EVS, "Ignores a request to stop a service for unknown callback %s.",
                    callback.asBinder());
            return;
        }

        for (int i = 0; i < types.size(); i++) {
            StateMachine instance = mServiceInstances.get(types.valueAt(i));
            if (instance == null) {
                Slogf.i(TAG_EVS, "Ignores a request to stop unsupported service %d.",
                        types.valueAt(i));
                return;
            }

            instance.requestStopVideoStream(callback);
        }
    }

    private void handlePropertyEvent(CarPropertyEvent event) {
        if (event.getEventType() != CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE) {
            // CarEvsService is interested only in the property change event.
            return;
        }

        CarPropertyValue value = event.getCarPropertyValue();
        if (value.getPropertyId() != VehiclePropertyIds.GEAR_SELECTION) {
            // CarEvsService is interested only in the GEAR_SELECTION property.
            return;
        }

        long timestamp = value.getTimestamp();
        boolean isReverseGear;
        synchronized (mLock) {
            if (timestamp != 0 && timestamp <= mLastEvsHalEvent.getTimestamp()) {
                if (DBG) {
                    Slogf.d(TAG_EVS,
                            "Ignoring GEAR_SELECTION change happened past, timestamp = " +
                            timestamp + ", last event was at " + mLastEvsHalEvent.getTimestamp());
                }
                return;
            }


            isReverseGear = (Integer) value.getValue() == VehicleGear.GEAR_REVERSE;
            mLastEvsHalEvent = new EvsHalEvent(timestamp, CarEvsManager.SERVICE_TYPE_REARVIEW,
                    isReverseGear);
        }

        StateMachine instance = mServiceInstances.get(CarEvsManager.SERVICE_TYPE_REARVIEW);
        if (instance == null) {
            Slogf.i(TAG_EVS,
                    "Ignore a GEAR_SELECTION event because the rearview service is not available.");
            return;
        }

        if (isReverseGear) {
            // Request to start the rearview activity when the gear is shifted into the reverse
            // position.
            if (instance.requestStartActivity(REQUEST_PRIORITY_HIGH) != ERROR_NONE) {
                Slogf.w(TAG_EVS, "Failed to request the rearview activity.");
            }
        } else {
            // Request to stop the rearview activity when the gear is shifted from the reverse
            // position to other positions.
            if (instance.requestStopActivity(REQUEST_PRIORITY_HIGH) != ERROR_NONE) {
                Slogf.i(TAG_EVS, "Failed to stop the rearview activity.");
            }
        }
    }

    /** Notify the client of a video stream loss */
    private static void notifyStreamStopped(@NonNull ICarEvsStreamCallback callback) {
        Objects.requireNonNull(callback);

        try {
            callback.onStreamEvent(CarEvsManager.STREAM_EVENT_STREAM_STOPPED);
        } catch (RemoteException e) {
            // Likely the binder death incident
            Slogf.w(TAG_EVS, Log.getStackTraceString(e));
        }
    }

    /** Handles a disconnection of a status monitoring client. */
    private void handleClientDisconnected(ICarEvsStatusListener listener) {
        mStatusListeners.unregister(listener);
        if (mStatusListeners.getRegisteredCallbackCount() == 0) {
            Slogf.d(TAG_EVS, "Last status listener has been disconnected.");
        }
    }
}
