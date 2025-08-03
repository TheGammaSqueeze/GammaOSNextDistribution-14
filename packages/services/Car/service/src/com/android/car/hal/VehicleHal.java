/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.car.hal;

import static android.os.SystemClock.uptimeMillis;

import static com.android.car.hal.property.HalPropertyDebugUtils.toAccessString;
import static com.android.car.hal.property.HalPropertyDebugUtils.toAreaIdString;
import static com.android.car.hal.property.HalPropertyDebugUtils.toAreaTypeString;
import static com.android.car.hal.property.HalPropertyDebugUtils.toChangeModeString;
import static com.android.car.hal.property.HalPropertyDebugUtils.toGroupString;
import static com.android.car.hal.property.HalPropertyDebugUtils.toPropertyIdString;
import static com.android.car.hal.property.HalPropertyDebugUtils.toValueTypeString;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.CheckResult;
import android.annotation.Nullable;
import android.car.VehiclePropertyIds;
import android.car.builtin.os.TraceHelper;
import android.car.builtin.util.Slogf;
import android.car.feature.FeatureFlags;
import android.car.feature.FeatureFlagsImpl;
import android.content.Context;
import android.hardware.automotive.vehicle.StatusCode;
import android.hardware.automotive.vehicle.SubscribeOptions;
import android.hardware.automotive.vehicle.VehiclePropError;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.hardware.automotive.vehicle.VehiclePropertyAccess;
import android.hardware.automotive.vehicle.VehiclePropertyChangeMode;
import android.hardware.automotive.vehicle.VehiclePropertyStatus;
import android.hardware.automotive.vehicle.VehiclePropertyType;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.os.Trace;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import com.android.car.CarLog;
import com.android.car.CarServiceUtils;
import com.android.car.CarSystemService;
import com.android.car.VehicleStub;
import com.android.car.VehicleStub.SubscriptionClient;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.internal.util.Lists;
import com.android.car.internal.util.PairSparseArray;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Abstraction for vehicle HAL. This class handles interface with native HAL and does basic parsing
 * of received data (type check). Then each event is sent to corresponding {@link HalServiceBase}
 * implementation. It is the responsibility of {@link HalServiceBase} to convert data to
 * corresponding Car*Service for Car*Manager API.
 */
public class VehicleHal implements VehicleHalCallback, CarSystemService {
    private static final boolean DBG = Slogf.isLoggable(CarLog.TAG_HAL, Log.DEBUG);;
    private static final long TRACE_TAG = TraceHelper.TRACE_TAG_CAR_SERVICE;

    private static final int GLOBAL_AREA_ID = 0;

    /**
     * If call to vehicle HAL returns StatusCode.TRY_AGAIN, we will retry to invoke that method
     * again for this amount of milliseconds.
     */
    private static final int MAX_DURATION_FOR_RETRIABLE_RESULT_MS = 2000;

    private static final int SLEEP_BETWEEN_RETRIABLE_INVOKES_MS = 100;
    private static final float PRECISION_THRESHOLD = 0.001f;

    private final HandlerThread mHandlerThread;
    private final Handler mHandler;
    private final SubscriptionClient mSubscriptionClient;

    private final PowerHalService mPowerHal;
    private final PropertyHalService mPropertyHal;
    private final InputHalService mInputHal;
    private final VmsHalService mVmsHal;
    private final UserHalService mUserHal;
    private final DiagnosticHalService mDiagnosticHal;
    private final ClusterHalService mClusterHalService;
    private final EvsHalService mEvsHal;
    private final TimeHalService mTimeHalService;
    private final HalPropValueBuilder mPropValueBuilder;
    private final VehicleStub mVehicleStub;

    private final Object mLock = new Object();

    private FeatureFlags mFeatureFlags = new FeatureFlagsImpl();

    // Only changed for test.
    private int mMaxDurationForRetryMs = MAX_DURATION_FOR_RETRIABLE_RESULT_MS;
    // Only changed for test.
    private int mSleepBetweenRetryMs = SLEEP_BETWEEN_RETRIABLE_INVOKES_MS;

    /** Stores handler for each HAL property. Property events are sent to handler. */
    @GuardedBy("mLock")
    private final SparseArray<HalServiceBase> mPropertyHandlers = new SparseArray<>();
    /** This is for iterating all HalServices with fixed order. */
    @GuardedBy("mLock")
    private final List<HalServiceBase> mAllServices;
    @GuardedBy("mLock")
    private PairSparseArray<RateInfo> mRateInfoByPropIdAreaId = new PairSparseArray<>();
    @GuardedBy("mLock")
    private final SparseArray<HalPropConfig> mAllProperties = new SparseArray<>();
    @GuardedBy("mLock")
    private final PairSparseArray<Integer> mAccessByPropIdAreaId = new PairSparseArray<Integer>();

    @GuardedBy("mLock")
    private final SparseArray<VehiclePropertyEventInfo> mEventLog = new SparseArray<>();

    // Used by injectVHALEvent for testing purposes.  Delimiter for an array of data
    private static final String DATA_DELIMITER = ",";

    /** A structure to store update rate in hz and whether to enable VUR. */
    private static final class RateInfo {
        public float updateRateHz;
        public boolean enableVariableUpdateRate;
        public float resolution;

        RateInfo(float updateRateHz, boolean enableVariableUpdateRate, float resolution) {
            this.updateRateHz = updateRateHz;
            this.enableVariableUpdateRate = enableVariableUpdateRate;
            this.resolution = resolution;
        }
    }

    /* package */ static final class HalSubscribeOptions {
        private final int mHalPropId;
        private final int[] mAreaIds;
        private final float mUpdateRateHz;
        private final boolean mEnableVariableUpdateRate;
        private final float mResolution;

        HalSubscribeOptions(int halPropId, int[] areaIds, float updateRateHz) {
            this(halPropId, areaIds, updateRateHz, /* enableVariableUpdateRate= */ false,
                    /* resolution= */ 0.0f);
        }

        HalSubscribeOptions(int halPropId, int[] areaIds, float updateRateHz,
                boolean enableVariableUpdateRate) {
            this(halPropId, areaIds, updateRateHz, enableVariableUpdateRate,
                    /* resolution= */ 0.0f);
        }

        HalSubscribeOptions(int halPropId, int[] areaIds, float updateRateHz,
                            boolean enableVariableUpdateRate, float resolution) {
            mHalPropId = halPropId;
            mAreaIds = areaIds;
            mUpdateRateHz = updateRateHz;
            mEnableVariableUpdateRate = enableVariableUpdateRate;
            mResolution = resolution;
        }

        int getHalPropId() {
            return mHalPropId;
        }

        int[] getAreaId() {
            return mAreaIds;
        }

        float getUpdateRateHz() {
            return mUpdateRateHz;
        }

        boolean isVariableUpdateRateEnabled() {
            return mEnableVariableUpdateRate;
        }
        float getResolution() {
            return mResolution;
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }

            if (!(other instanceof VehicleHal.HalSubscribeOptions)) {
                return false;
            }

            VehicleHal.HalSubscribeOptions o = (VehicleHal.HalSubscribeOptions) other;

            return mHalPropId == o.getHalPropId() && mUpdateRateHz == o.getUpdateRateHz()
                    && Arrays.equals(mAreaIds, o.getAreaId())
                    && mEnableVariableUpdateRate == o.isVariableUpdateRateEnabled()
                    && mResolution == o.getResolution();
        }

        @Override
        public String toString() {
            return "HalSubscribeOptions{"
                    + "PropertyId: " + mHalPropId
                    + ", AreaId: " + Arrays.toString(mAreaIds)
                    + ", UpdateRateHz: " + mUpdateRateHz
                    + ", enableVariableUpdateRate: " + mEnableVariableUpdateRate
                    + ", Resolution: " + mResolution
                    + "}";
        }

        @Override
        public int hashCode() {
            return Objects.hash(mHalPropId, Arrays.hashCode(mAreaIds), mUpdateRateHz,
                    mEnableVariableUpdateRate, mResolution);
        }
    }

    /**
     * Constructs a new {@link VehicleHal} object given the {@link Context} and {@link IVehicle}
     * both passed as parameters.
     */
    public VehicleHal(Context context, VehicleStub vehicle) {
        this(context, /* powerHal= */ null, /* propertyHal= */ null,
                /* inputHal= */ null, /* vmsHal= */ null, /* userHal= */ null,
                /* diagnosticHal= */ null, /* clusterHalService= */ null,
                /* timeHalService= */ null,
                CarServiceUtils.getHandlerThread(VehicleHal.class.getSimpleName()), vehicle);
    }

    /**
     * Constructs a new {@link VehicleHal} object given the services passed as parameters.
     * This method must be used by tests only.
     */
    @VisibleForTesting
    VehicleHal(Context context,
            PowerHalService powerHal,
            PropertyHalService propertyHal,
            InputHalService inputHal,
            VmsHalService vmsHal,
            UserHalService userHal,
            DiagnosticHalService diagnosticHal,
            ClusterHalService clusterHalService,
            TimeHalService timeHalService,
            HandlerThread handlerThread,
            VehicleStub vehicle) {
        // Must be initialized before HalService so that HalService could use this.
        mPropValueBuilder = vehicle.getHalPropValueBuilder();
        mHandlerThread = handlerThread;
        mHandler = new Handler(mHandlerThread.getLooper());
        mPowerHal = powerHal != null ? powerHal : new PowerHalService(context, this);
        mPropertyHal = propertyHal != null ? propertyHal : new PropertyHalService(this);
        mInputHal = inputHal != null ? inputHal : new InputHalService(this);
        mVmsHal = vmsHal != null ? vmsHal : new VmsHalService(context, this);
        mUserHal = userHal != null ? userHal :  new UserHalService(this);
        mDiagnosticHal = diagnosticHal != null ? diagnosticHal : new DiagnosticHalService(this);
        mClusterHalService = clusterHalService != null
                ? clusterHalService : new ClusterHalService(context, this);
        mEvsHal = new EvsHalService(this);
        mTimeHalService = timeHalService != null
                ? timeHalService : new TimeHalService(context, this);
        mAllServices = List.of(
                mPowerHal,
                mInputHal,
                mDiagnosticHal,
                mVmsHal,
                mUserHal,
                mClusterHalService,
                mEvsHal,
                mTimeHalService,
                // mPropertyHal must be the last so that on init/release it can be used for all
                // other HAL services properties.
                mPropertyHal);
        mVehicleStub = vehicle;
        mSubscriptionClient = vehicle.newSubscriptionClient(this);
    }

    /** Sets fake feature flag for unit testing. */
    @VisibleForTesting
    public void setFeatureFlags(FeatureFlags fakeFeatureFlags) {
        mFeatureFlags = fakeFeatureFlags;
    }

    @VisibleForTesting
    void setMaxDurationForRetryMs(int maxDurationForRetryMs) {
        mMaxDurationForRetryMs = maxDurationForRetryMs;
    }

    @VisibleForTesting
    void setSleepBetweenRetryMs(int sleepBetweenRetryMs) {
        mSleepBetweenRetryMs = sleepBetweenRetryMs;
    }

    @VisibleForTesting
    void fetchAllPropConfigs() {
        synchronized (mLock) {
            if (mAllProperties.size() != 0) { // already set
                Slogf.i(CarLog.TAG_HAL, "fetchAllPropConfigs already fetched");
                return;
            }
        }
        HalPropConfig[] configs;
        try {
            configs = getAllPropConfigs();
            if (configs == null || configs.length == 0) {
                Slogf.e(CarLog.TAG_HAL, "getAllPropConfigs returned empty configs");
                return;
            }
        } catch (RemoteException | ServiceSpecificException e) {
            throw new RuntimeException("Unable to retrieve vehicle property configuration", e);
        }

        synchronized (mLock) {
            // Create map of all properties
            for (HalPropConfig p : configs) {
                if (DBG) {
                    Slogf.d(CarLog.TAG_HAL, "Add config for prop: 0x%x config: %s", p.getPropId(),
                            p.toString());
                }
                mAllProperties.put(p.getPropId(), p);
                if (p.getAreaConfigs().length == 0) {
                    mAccessByPropIdAreaId.put(p.getPropId(), /* areaId */ 0, p.getAccess());
                } else {
                    for (HalAreaConfig areaConfig : p.getAreaConfigs()) {
                        mAccessByPropIdAreaId.put(p.getPropId(), areaConfig.getAreaId(),
                                areaConfig.getAccess());
                    }
                }
            }
        }
    }

    private void handleOnPropertyEvent(List<HalPropValue> propValues) {
        synchronized (mLock) {
            for (int i = 0; i < propValues.size(); i++) {
                HalPropValue v = propValues.get(i);
                int propId = v.getPropId();
                HalServiceBase service = mPropertyHandlers.get(propId);
                if (service == null) {
                    Slogf.e(CarLog.TAG_HAL, "handleOnPropertyEvent: HalService not found for %s",
                            v);
                    continue;
                }
                service.getDispatchList().add(v);
                mServicesToDispatch.add(service);
                VehiclePropertyEventInfo info = mEventLog.get(propId);
                if (info == null) {
                    info = new VehiclePropertyEventInfo(v);
                    mEventLog.put(propId, info);
                } else {
                    info.addNewEvent(v);
                }
            }
        }
        for (HalServiceBase s : mServicesToDispatch) {
            s.onHalEvents(s.getDispatchList());
            s.getDispatchList().clear();
        }
        mServicesToDispatch.clear();
    }

    private void handleOnPropertySetError(List<VehiclePropError> errors) {
        SparseArray<ArrayList<VehiclePropError>> errorsByPropId =
                new SparseArray<ArrayList<VehiclePropError>>();
        for (int i = 0; i < errors.size(); i++) {
            VehiclePropError error = errors.get(i);
            int errorCode = error.errorCode;
            int propId = error.propId;
            int areaId = error.areaId;
            Slogf.w(CarLog.TAG_HAL, "onPropertySetError, errorCode: %d, prop: 0x%x, area: 0x%x",
                    errorCode, propId, areaId);
            if (propId == VehicleProperty.INVALID) {
                continue;
            }

            ArrayList<VehiclePropError> propErrors;
            if (errorsByPropId.get(propId) == null) {
                propErrors = new ArrayList<VehiclePropError>();
                errorsByPropId.put(propId, propErrors);
            } else {
                propErrors = errorsByPropId.get(propId);
            }
            propErrors.add(error);
        }

        for (int i = 0; i < errorsByPropId.size(); i++) {
            int propId = errorsByPropId.keyAt(i);
            HalServiceBase service;
            synchronized (mLock) {
                service = mPropertyHandlers.get(propId);
            }
            if (service == null) {
                Slogf.e(CarLog.TAG_HAL,
                        "handleOnPropertySetError: HalService not found for prop: 0x%x", propId);
                continue;
            }

            ArrayList<VehiclePropError> propErrors = errorsByPropId.get(propId);
            service.onPropertySetError(propErrors);
        }
    }

    private static String errorMessage(String action, HalPropValue propValue, String errorMsg) {
        return String.format("Failed to %s value for: %s, error: %s", action,
                propValue, errorMsg);
    }

    private HalPropValue getValueWithRetry(HalPropValue value) {
        return getValueWithRetry(value, /* maxRetries= */ 0);
    }

    private HalPropValue getValueWithRetry(HalPropValue value, int maxRetries) {
        HalPropValue result;
        Trace.traceBegin(TRACE_TAG, "VehicleStub#getValueWithRetry");
        try {
            result = invokeRetriable((requestValue) -> {
                Trace.traceBegin(TRACE_TAG, "VehicleStub#get");
                try {
                    return mVehicleStub.get(requestValue);
                } finally {
                    Trace.traceEnd(TRACE_TAG);
                }
            }, "get", value, mMaxDurationForRetryMs, mSleepBetweenRetryMs, maxRetries);
        } finally {
            Trace.traceEnd(TRACE_TAG);
        }

        if (result == null) {
            // If VHAL returns null result, but the status is OKAY. We treat that as NOT_AVAILABLE.
            throw new ServiceSpecificException(StatusCode.NOT_AVAILABLE,
                    errorMessage("get", value, "VHAL returns null for property value"));
        }
        return result;
    }

    private void setValueWithRetry(HalPropValue value)  {
        invokeRetriable((requestValue) -> {
            Trace.traceBegin(TRACE_TAG, "VehicleStub#set");
            mVehicleStub.set(requestValue);
            Trace.traceEnd(TRACE_TAG);
            return null;
        }, "set", value, mMaxDurationForRetryMs, mSleepBetweenRetryMs, /* maxRetries= */ 0);
    }

    /**
     * Inits the vhal configurations.
     */
    @Override
    public void init() {
        // nothing to init as everything was done on priorityInit
    }

    /**
     * PriorityInit for the vhal configurations.
     */
    public void priorityInit() {
        fetchAllPropConfigs();

        // PropertyHalService will take most properties, so make it big enough.
        ArrayMap<HalServiceBase, ArrayList<HalPropConfig>> configsForAllServices;
        synchronized (mLock) {
            configsForAllServices = new ArrayMap<>(mAllServices.size());
            for (int i = 0; i < mAllServices.size(); i++) {
                ArrayList<HalPropConfig> configsForService = new ArrayList();
                HalServiceBase service = mAllServices.get(i);
                configsForAllServices.put(service, configsForService);
                int[] supportedProps = service.getAllSupportedProperties();
                if (supportedProps.length == 0) {
                    for (int j = 0; j < mAllProperties.size(); j++) {
                        Integer propId = mAllProperties.keyAt(j);
                        if (service.isSupportedProperty(propId)) {
                            HalPropConfig config = mAllProperties.get(propId);
                            mPropertyHandlers.append(propId, service);
                            configsForService.add(config);
                        }
                    }
                } else {
                    for (int prop : supportedProps) {
                        HalPropConfig config = mAllProperties.get(prop);
                        if (config == null) {
                            continue;
                        }
                        mPropertyHandlers.append(prop, service);
                        configsForService.add(config);
                    }
                }
            }
        }

        for (Map.Entry<HalServiceBase, ArrayList<HalPropConfig>> entry
                : configsForAllServices.entrySet()) {
            HalServiceBase service = entry.getKey();
            ArrayList<HalPropConfig> configsForService = entry.getValue();
            service.takeProperties(configsForService);
            service.init();
        }
    }

    /**
     * Releases all connected services (power management service, input service, etc).
     */
    @Override
    public void release() {
        ArraySet<Integer> subscribedProperties = new ArraySet<>();
        synchronized (mLock) {
            // release in reverse order from init
            for (int i = mAllServices.size() - 1; i >= 0; i--) {
                mAllServices.get(i).release();
            }
            for (int i = 0; i < mRateInfoByPropIdAreaId.size(); i++) {
                int propertyId = mRateInfoByPropIdAreaId.keyPairAt(i)[0];
                subscribedProperties.add(propertyId);
            }
            mRateInfoByPropIdAreaId.clear();
            mAllProperties.clear();
            mAccessByPropIdAreaId.clear();
        }
        for (int i = 0; i < subscribedProperties.size(); i++) {
            try {
                mSubscriptionClient.unsubscribe(subscribedProperties.valueAt(i));
            } catch (RemoteException | ServiceSpecificException e) {
                //  Ignore exceptions on shutdown path.
                Slogf.w(CarLog.TAG_HAL, "Failed to unsubscribe", e);
            }
        }
        // keep the looper thread as should be kept for the whole life cycle.
    }

    public DiagnosticHalService getDiagnosticHal() {
        return mDiagnosticHal;
    }

    public PowerHalService getPowerHal() {
        return mPowerHal;
    }

    public PropertyHalService getPropertyHal() {
        return mPropertyHal;
    }

    public InputHalService getInputHal() {
        return mInputHal;
    }

    public UserHalService getUserHal() {
        return mUserHal;
    }

    public VmsHalService getVmsHal() {
        return mVmsHal;
    }

    public ClusterHalService getClusterHal() {
        return mClusterHalService;
    }

    public EvsHalService getEvsHal() {
        return mEvsHal;
    }

    public TimeHalService getTimeHalService() {
        return mTimeHalService;
    }

    public HalPropValueBuilder getHalPropValueBuilder() {
        return mPropValueBuilder;
    }

    @GuardedBy("mLock")
    private void assertServiceOwnerLocked(HalServiceBase service, int property) {
        if (service != mPropertyHandlers.get(property)) {
            throw new IllegalArgumentException(String.format(
                    "Property 0x%x  is not owned by service: %s", property, service));
        }
    }

    /**
     * Subscribes given properties with sampling rate defaults to 0 and no special flags provided.
     *
     * @throws IllegalArgumentException thrown if property is not supported by VHAL
     * @throws ServiceSpecificException if VHAL returns error or lost connection with VHAL.
     * @see #subscribeProperty(HalServiceBase, int, float)
     */
    public void subscribeProperty(HalServiceBase service, int property)
            throws IllegalArgumentException, ServiceSpecificException {
        subscribeProperty(service, property, /* samplingRateHz= */ 0f);
    }

    /**
     * Similar to {@link #subscribeProperty(HalServiceBase, int)} except that all exceptions
     * are caught and are logged.
     */
    public void subscribePropertySafe(HalServiceBase service, int property) {
        try {
            subscribeProperty(service, property);
        } catch (IllegalArgumentException | ServiceSpecificException e) {
            Slogf.w(CarLog.TAG_HAL, "Failed to subscribe for property: "
                    + VehiclePropertyIds.toString(property), e);
        }
    }

    /**
     * Subscribe given property. Only Hal service owning the property can subscribe it.
     *
     * @param service HalService that owns this property
     * @param property property id (VehicleProperty)
     * @param samplingRateHz sampling rate in Hz for continuous properties
     * @throws IllegalArgumentException thrown if property is not supported by VHAL
     * @throws ServiceSpecificException if VHAL returns error or lost connection with VHAL.
     */
    public void subscribeProperty(HalServiceBase service, int property, float samplingRateHz)
            throws IllegalArgumentException, ServiceSpecificException {
        HalSubscribeOptions options = new HalSubscribeOptions(property, new int[0], samplingRateHz);
        subscribeProperty(service, List.of(options));
    }

    /**
     * Similar to {@link #subscribeProperty(HalServiceBase, int, float)} except that all exceptions
     * are caught and converted to logs.
     */
    public void subscribePropertySafe(HalServiceBase service, int property, float sampleRateHz) {
        try {
            subscribeProperty(service, property, sampleRateHz);
        } catch (IllegalArgumentException | ServiceSpecificException e) {
            Slogf.w(CarLog.TAG_HAL, e, "Failed to subscribe for property: %s, sample rate: %f hz",
                    VehiclePropertyIds.toString(property), sampleRateHz);
        }
    }

    /**
     * Subscribe given property. Only Hal service owning the property can subscribe it.
     *
     * @param service HalService that owns this property
     * @param halSubscribeOptions Information needed to subscribe to VHAL
     * @throws IllegalArgumentException thrown if property is not supported by VHAL
     * @throws ServiceSpecificException if VHAL returns error or lost connection with VHAL.
     */
    public void subscribeProperty(HalServiceBase service, List<HalSubscribeOptions>
            halSubscribeOptions) throws IllegalArgumentException, ServiceSpecificException {
        synchronized (mLock) {
            PairSparseArray<RateInfo> previousState = cloneState(mRateInfoByPropIdAreaId);
            SubscribeOptions[] subscribeOptions = createVhalSubscribeOptionsLocked(
                    service, halSubscribeOptions);
            if (subscribeOptions.length == 0) {
                if (DBG) {
                    Slogf.d(CarLog.TAG_HAL,
                            "Ignore the subscribeProperty request, SubscribeOptions is length 0");
                }
                return;
            }
            try {
                mSubscriptionClient.subscribe(subscribeOptions);
            } catch (RemoteException e) {
                mRateInfoByPropIdAreaId = previousState;
                Slogf.w(CarLog.TAG_HAL, "Failed to subscribe, connection to VHAL failed", e);
                // Convert RemoteException to ServiceSpecificException so that it could be passed
                // back to the client.
                throw new ServiceSpecificException(StatusCode.INTERNAL_ERROR,
                        "Failed to subscribe, connection to VHAL failed, error: " + e);
            } catch (ServiceSpecificException e) {
                mRateInfoByPropIdAreaId = previousState;
                Slogf.w(CarLog.TAG_HAL, "Failed to subscribe, received error from VHAL", e);
                throw e;
            }
        }
    }

    /**
     * Converts {@link HalSubscribeOptions} to {@link SubscribeOptions} which is the data structure
     * used by VHAL.
     */
    @GuardedBy("mLock")
    private SubscribeOptions[] createVhalSubscribeOptionsLocked(HalServiceBase service,
            List<HalSubscribeOptions> halSubscribeOptions) throws IllegalArgumentException {
        if (DBG) {
            Slogf.d(CarLog.TAG_HAL, "creating subscribeOptions from HalSubscribeOptions of size: "
                    + halSubscribeOptions.size());
        }
        List<SubscribeOptions> subscribeOptionsList = new ArrayList<>();
        for (int i = 0; i < halSubscribeOptions.size(); i++) {
            HalSubscribeOptions halSubscribeOption = halSubscribeOptions.get(i);
            int property = halSubscribeOption.getHalPropId();
            int[] areaIds = halSubscribeOption.getAreaId();
            float samplingRateHz = halSubscribeOption.getUpdateRateHz();
            boolean enableVariableUpdateRate = halSubscribeOption.isVariableUpdateRateEnabled();
            float resolution = halSubscribeOption.getResolution();

            HalPropConfig config;
            config = mAllProperties.get(property);

            if (config == null) {
                throw new IllegalArgumentException("subscribe error: "
                        + toPropertyIdString(property) + " is not supported");
            }

            if (enableVariableUpdateRate) {
                if (config.getChangeMode() != VehiclePropertyChangeMode.CONTINUOUS) {
                    // enableVur should be ignored if property is not continuous, but we set it to
                    // false to be safe.
                    enableVariableUpdateRate = false;
                    Slogf.w(CarLog.TAG_HAL, "VUR is always off for non-continuous property: "
                            + toPropertyIdString(property));
                }
                if (!mFeatureFlags.variableUpdateRate()) {
                    enableVariableUpdateRate = false;
                    Slogf.w(CarLog.TAG_HAL, "VUR feature is not enabled, VUR is always off");
                }
            }

            if (resolution != 0.0f) {
                if (config.getChangeMode() != VehiclePropertyChangeMode.CONTINUOUS) {
                    // resolution should be ignored if property is not continuous, but we set it to
                    // 0 to be safe.
                    resolution = 0.0f;
                    Slogf.w(CarLog.TAG_HAL, "resolution is always 0 for non-continuous property: "
                            + toPropertyIdString(property));
                }
                if (!mFeatureFlags.subscriptionWithResolution()) {
                    resolution = 0.0f;
                    Slogf.w(CarLog.TAG_HAL,
                            "Resolution feature is not enabled, resolution is always 0");
                }
            }

            if (isStaticProperty(config)) {
                Slogf.w(CarLog.TAG_HAL, "Ignore subscribing to static property: "
                        + toPropertyIdString(property));
                continue;
            }

            if (areaIds.length == 0) {
                if (!isPropertySubscribable(config)) {
                    throw new IllegalArgumentException("Property: " + toPropertyIdString(property)
                            + " is not subscribable");
                }
                areaIds = getAllAreaIdsFromPropertyId(config);
            } else {
                for (int j = 0; j < areaIds.length; j++) {
                    Integer access = mAccessByPropIdAreaId.get(config.getPropId(), areaIds[j]);
                    if (access == null) {
                        throw new IllegalArgumentException(
                                "Cannot subscribe to " + toPropertyIdString(property)
                                + " at areaId " + toAreaIdString(property, areaIds[j])
                                + " the property does not have the requested areaId");
                    }
                    if (!isPropIdAreaIdReadable(config, access.intValue())) {
                        throw new IllegalArgumentException(
                                "Cannot subscribe to " + toPropertyIdString(property)
                                + " at areaId " + toAreaIdString(property, areaIds[j])
                                + " the property's access mode does not contain READ");
                    }
                }
            }
            SubscribeOptions opts = new SubscribeOptions();
            opts.propId = property;
            opts.sampleRate = samplingRateHz;
            opts.enableVariableUpdateRate = enableVariableUpdateRate;
            opts.resolution = resolution;
            RateInfo rateInfo = new RateInfo(samplingRateHz, enableVariableUpdateRate, resolution);
            int[] filteredAreaIds = filterAreaIdsWithSameRateInfo(property, areaIds, rateInfo);
            opts.areaIds = filteredAreaIds;
            if (opts.areaIds.length == 0) {
                if (DBG) {
                    Slogf.d(CarLog.TAG_HAL, "property: " + VehiclePropertyIds.toString(property)
                            + " is already subscribed at rate: " + samplingRateHz + " hz");
                }
                continue;
            }
            assertServiceOwnerLocked(service, property);
            for (int j = 0; j < filteredAreaIds.length; j++) {
                if (DBG) {
                    Slogf.d(CarLog.TAG_HAL, "Update subscription rate for propertyId:"
                                    + " %s, areaId: %d, SampleRateHz: %f, enableVur: %b,"
                                    + " resolution: %f",
                            VehiclePropertyIds.toString(opts.propId), filteredAreaIds[j],
                            samplingRateHz, enableVariableUpdateRate, resolution);
                }
                mRateInfoByPropIdAreaId.put(property, filteredAreaIds[j], rateInfo);
            }
            subscribeOptionsList.add(opts);
        }
        return subscribeOptionsList.toArray(new SubscribeOptions[0]);
    }

    private int[] filterAreaIdsWithSameRateInfo(int property, int[] areaIds, RateInfo rateInfo) {
        List<Integer> areaIdList = new ArrayList<>();
        synchronized (mLock) {
            for (int i = 0; i < areaIds.length; i++) {
                RateInfo savedRateInfo = mRateInfoByPropIdAreaId.get(property, areaIds[i]);

                // Strict equality (==) is used here for comparing resolutions. This approach does
                // not introduce a margin of error through PRECISION_THRESHOLD, and thus can allow
                // clients to request the highest possible resolution without being limited by a
                // predefined threshold. This approach is assumed to be feasible under the
                // hypothesis that the floating point representation of numbers is consistent
                // across the system. That is, if two clients specify a resolution of 0.01f,
                // their internal representations will match, enabling an exact comparison despite
                // floating point inaccuracies. If this is inaccurate, we must introduce a margin
                // of error (ideally 1e-7 as floats can reliably represent up to 7 significant
                // figures, but can be higher if necessary), and update the documentation in {@link
                // android.car.hardware.property.Subscription.Builder#setResolution(float)}
                // appropriately.
                if (savedRateInfo != null
                        && (Math.abs(savedRateInfo.updateRateHz - rateInfo.updateRateHz)
                                < PRECISION_THRESHOLD)
                        && (savedRateInfo.enableVariableUpdateRate
                                == rateInfo.enableVariableUpdateRate)
                        && savedRateInfo.resolution == rateInfo.resolution) {
                    if (DBG) {
                        Slogf.d(CarLog.TAG_HAL, "Property: %s is already subscribed at rate: %f hz"
                                + ", enableVur: %b, resolution: %f",
                                toPropertyIdString(property), rateInfo.updateRateHz,
                                rateInfo.enableVariableUpdateRate, rateInfo.resolution);
                    }
                    continue;
                }
                areaIdList.add(areaIds[i]);
            }
        }
        return CarServiceUtils.toIntArray(areaIdList);
    }

    private int[] getAllAreaIdsFromPropertyId(HalPropConfig config) {
        HalAreaConfig[] allAreaConfigs = config.getAreaConfigs();
        if (allAreaConfigs.length == 0) {
            return new int[]{/* areaId= */ 0};
        }
        int[] areaId = new int[allAreaConfigs.length];
        for (int i = 0; i < allAreaConfigs.length; i++) {
            areaId[i] = allAreaConfigs[i].getAreaId();
        }
        return areaId;
    }

    /**
     * Like {@link unsubscribeProperty} except that exceptions are logged.
     */
    public void unsubscribePropertySafe(HalServiceBase service, int property) {
        try {
            unsubscribeProperty(service, property);
        } catch (ServiceSpecificException e) {
            Slogf.w(CarLog.TAG_SERVICE, "Failed to unsubscribe: "
                    + toPropertyIdString(property), e);
        }
    }

    /**
     * Unsubscribes from receiving notifications for the property and HAL services passed
     * as parameters.
     */
    public void unsubscribeProperty(HalServiceBase service, int property)
            throws ServiceSpecificException {
        if (DBG) {
            Slogf.d(CarLog.TAG_HAL, "unsubscribeProperty, service:" + service
                    + ", " + toPropertyIdString(property));
        }
        synchronized (mLock) {
            HalPropConfig config = mAllProperties.get(property);
            if (config == null) {
                Slogf.w(CarLog.TAG_HAL, "unsubscribeProperty " + toPropertyIdString(property)
                        + " does not exist");
                return;
            }
            if (isStaticProperty(config)) {
                Slogf.w(CarLog.TAG_HAL, "Unsubscribe to a static property: "
                        + toPropertyIdString(property) + ", do nothing");
                return;
            }
            assertServiceOwnerLocked(service, property);
            HalAreaConfig[] halAreaConfigs = config.getAreaConfigs();
            boolean isSubscribed = false;
            PairSparseArray<RateInfo> previousState = cloneState(mRateInfoByPropIdAreaId);
            if (halAreaConfigs.length == 0) {
                int index = mRateInfoByPropIdAreaId.indexOfKeyPair(property, 0);
                if (hasReadAccess(config.getAccess()) && index >= 0) {
                    mRateInfoByPropIdAreaId.removeAt(index);
                    isSubscribed = true;
                }
            } else {
                for (int i = 0; i < halAreaConfigs.length; i++) {
                    if (!isPropIdAreaIdReadable(config, halAreaConfigs[i].getAccess())) {
                        Slogf.w(CarLog.TAG_HAL,
                                "Cannot unsubscribe to " + toPropertyIdString(property)
                                + " at areaId " + toAreaIdString(property,
                                halAreaConfigs[i].getAreaId())
                                + " the property's access mode does not contain READ");
                        continue;
                    }
                    int index = mRateInfoByPropIdAreaId.indexOfKeyPair(property,
                            halAreaConfigs[i].getAreaId());
                    if (index >= 0) {
                        mRateInfoByPropIdAreaId.removeAt(index);
                        isSubscribed = true;
                    }
                }
            }
            if (!isSubscribed) {
                if (DBG) {
                    Slogf.d(CarLog.TAG_HAL, "Property " + toPropertyIdString(property)
                            + " was not subscribed, do nothing");
                }
                return;
            }
            try {
                mSubscriptionClient.unsubscribe(property);
            } catch (RemoteException e) {
                mRateInfoByPropIdAreaId = previousState;
                Slogf.w(CarLog.TAG_HAL, "Failed to unsubscribe, connection to VHAL failed", e);
                throw new ServiceSpecificException(StatusCode.INTERNAL_ERROR,
                        "Failed to unsubscribe, connection to VHAL failed, error: " + e);
            } catch (ServiceSpecificException e) {
                mRateInfoByPropIdAreaId = previousState;
                Slogf.w(CarLog.TAG_HAL, "Failed to unsubscribe, received error from VHAL", e);
                throw e;
            }
        }
    }

    /**
     * Indicates if the property passed as parameter is supported.
     */
    public boolean isPropertySupported(int propertyId) {
        synchronized (mLock) {
            return mAllProperties.contains(propertyId);
        }
    }

    /**
     * Gets given property with retries.
     *
     * <p>If getting the property fails after all retries, it will throw
     * {@code IllegalStateException}. If the property is not supported, it will simply return
     * {@code null}.
     */
    @Nullable
    public HalPropValue getIfSupportedOrFail(int propertyId, int maxRetries) {
        if (!isPropertySupported(propertyId)) {
            return null;
        }
        try {
            return getValueWithRetry(mPropValueBuilder.build(propertyId, GLOBAL_AREA_ID),
                    maxRetries);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * This works similar to {@link #getIfSupportedOrFail(int, int)} except that this can be called
     * before {@code init()} is called.
     *
     * <p>This call will check if requested vhal property is supported by querying directly to vhal
     * and can have worse performance. Use this only for accessing vhal properties before
     * {@code ICarImpl.init()} phase.
     */
    @Nullable
    public HalPropValue getIfSupportedOrFailForEarlyStage(int propertyId, int maxRetries) {
        fetchAllPropConfigs();
        return getIfSupportedOrFail(propertyId, maxRetries);
    }

    /**
     * Returns the property's {@link HalPropValue} for the property id passed as parameter and
     * not specified area.
     *
     * @throws IllegalArgumentException if argument is invalid
     * @throws ServiceSpecificException if VHAL returns error
     */
    public HalPropValue get(int propertyId)
            throws IllegalArgumentException, ServiceSpecificException {
        return get(propertyId, GLOBAL_AREA_ID);
    }

    /**
     * Returns the property's {@link HalPropValue} for the property id and area id passed as
     * parameters.
     *
     * @throws IllegalArgumentException if argument is invalid
     * @throws ServiceSpecificException if VHAL returns error
     */
    public HalPropValue get(int propertyId, int areaId)
            throws IllegalArgumentException, ServiceSpecificException {
        if (DBG) {
            Slogf.d(CarLog.TAG_HAL, "get, " + toPropertyIdString(propertyId)
                    + toAreaIdString(propertyId, areaId));
        }
        return getValueWithRetry(mPropValueBuilder.build(propertyId, areaId));
    }

    /**
     * Returns the property object value for the class and property id passed as parameter and
     * no area specified.
     *
     * @throws IllegalArgumentException if argument is invalid
     * @throws ServiceSpecificException if VHAL returns error
     */
    public <T> T get(Class clazz, int propertyId)
            throws IllegalArgumentException, ServiceSpecificException {
        return get(clazz, propertyId, GLOBAL_AREA_ID);
    }

    /**
     * Returns the property object value for the class, property id, and area id passed as
     * parameter.
     *
     * @throws IllegalArgumentException if argument is invalid
     * @throws ServiceSpecificException if VHAL returns error
     */
    public <T> T get(Class clazz, int propertyId, int areaId)
            throws IllegalArgumentException, ServiceSpecificException {
        return get(clazz, mPropValueBuilder.build(propertyId, areaId));
    }

    /**
     * Returns the property object value for the class and requested property value passed as
     * parameter.
     *
     * @throws IllegalArgumentException if argument is invalid
     * @throws ServiceSpecificException if VHAL returns error
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class clazz, HalPropValue requestedPropValue)
            throws IllegalArgumentException, ServiceSpecificException {
        HalPropValue propValue;
        propValue = getValueWithRetry(requestedPropValue);

        if (clazz == Long.class || clazz == long.class) {
            Long value = propValue.getInt64Value(0);
            return (T) value;
        } else if (clazz == Integer.class || clazz == int.class) {
            Integer value = propValue.getInt32Value(0);
            return (T) value;
        } else if (clazz == Boolean.class || clazz == boolean.class) {
            Boolean value = Boolean.valueOf(propValue.getInt32Value(0) == 1);
            return (T) value;
        } else if (clazz == Float.class || clazz == float.class) {
            Float value = propValue.getFloatValue(0);
            return (T) value;
        } else if (clazz == Long[].class) {
            int size = propValue.getInt64ValuesSize();
            Long[] longArray = new Long[size];
            for (int i = 0; i < size; i++) {
                longArray[i] = propValue.getInt64Value(i);
            }
            return (T) longArray;
        } else if (clazz == Integer[].class) {
            int size = propValue.getInt32ValuesSize();
            Integer[] intArray = new Integer[size];
            for (int i = 0; i < size; i++) {
                intArray[i] = propValue.getInt32Value(i);
            }
            return (T) intArray;
        } else if (clazz == Float[].class) {
            int size = propValue.getFloatValuesSize();
            Float[] floatArray = new Float[size];
            for (int i = 0; i < size; i++) {
                floatArray[i] = propValue.getFloatValue(i);
            }
            return (T) floatArray;
        } else if (clazz == long[].class) {
            int size = propValue.getInt64ValuesSize();
            long[] longArray = new long[size];
            for (int i = 0; i < size; i++) {
                longArray[i] = propValue.getInt64Value(i);
            }
            return (T) longArray;
        } else if (clazz == int[].class) {
            int size = propValue.getInt32ValuesSize();
            int[] intArray = new int[size];
            for (int i = 0; i < size; i++) {
                intArray[i] = propValue.getInt32Value(i);
            }
            return (T) intArray;
        } else if (clazz == float[].class) {
            int size = propValue.getFloatValuesSize();
            float[] floatArray = new float[size];
            for (int i = 0; i < size; i++) {
                floatArray[i] = propValue.getFloatValue(i);
            }
            return (T) floatArray;
        } else if (clazz == byte[].class) {
            return (T) propValue.getByteArray();
        } else if (clazz == String.class) {
            return (T) propValue.getStringValue();
        } else {
            throw new IllegalArgumentException("Unexpected type: " + clazz);
        }
    }

    /**
     * Returns the vehicle's {@link HalPropValue} for the requested property value passed
     * as parameter.
     *
     * @throws IllegalArgumentException if argument is invalid
     * @throws ServiceSpecificException if VHAL returns error
     */
    public HalPropValue get(HalPropValue requestedPropValue)
            throws IllegalArgumentException, ServiceSpecificException {
        return getValueWithRetry(requestedPropValue);
    }

    /**
     * Set property.
     *
     * @throws IllegalArgumentException if argument is invalid
     * @throws ServiceSpecificException if VHAL returns error
     */
    public void set(HalPropValue propValue)
            throws IllegalArgumentException, ServiceSpecificException {
        setValueWithRetry(propValue);
    }

    @CheckResult
    HalPropValueSetter set(int propId) {
        return set(propId, GLOBAL_AREA_ID);
    }

    @CheckResult
    HalPropValueSetter set(int propId, int areaId) {
        return new HalPropValueSetter(propId, areaId);
    }

    private static boolean hasReadAccess(int accessLevel) {
        return accessLevel == VehiclePropertyAccess.READ
                || accessLevel == VehiclePropertyAccess.READ_WRITE;
    }

    private static boolean isPropIdAreaIdReadable(HalPropConfig config, int areaIdAccess) {
        return (areaIdAccess == VehiclePropertyAccess.NONE)
                ? hasReadAccess(config.getAccess()) : hasReadAccess(areaIdAccess);
    }

    /**
     * Returns whether the property is readable and not static.
     */
    static boolean isPropertySubscribable(HalPropConfig config) {
        if (isStaticProperty(config)) {
            Slogf.w(CarLog.TAG_HAL, "Subscribe to a static property: "
                    + toPropertyIdString(config.getPropId()) + ", do nothing");
            return false;
        }
        if (config.getAreaConfigs().length == 0) {
            boolean hasReadAccess = hasReadAccess(config.getAccess());
            if (!hasReadAccess) {
                Slogf.w(CarLog.TAG_HAL, "Cannot subscribe to "
                        + toPropertyIdString(config.getPropId())
                        + " the property's access mode does not contain READ");
            }
            return hasReadAccess;
        }
        for (HalAreaConfig halAreaConfig : config.getAreaConfigs()) {
            if (!isPropIdAreaIdReadable(config, halAreaConfig.getAccess())) {
                Slogf.w(CarLog.TAG_HAL, "Cannot subscribe to "
                        + toPropertyIdString(config.getPropId()) + " at areaId "
                        + toAreaIdString(config.getPropId(), halAreaConfig.getAreaId())
                        + " the property's access mode does not contain READ");
                return false;
            }
        }
        return true;
    }

    /**
     * Sets a passed propertyId+areaId from the shell command.
     *
     * @param propertyId Property ID
     * @param areaId     Area ID
     * @param data       Comma-separated value.
     */
    public void setPropertyFromCommand(int propertyId, int areaId, String data,
            IndentingPrintWriter writer) throws IllegalArgumentException, ServiceSpecificException {
        long timestampNanos = SystemClock.elapsedRealtimeNanos();
        HalPropValue halPropValue = createPropValueForInjecting(mPropValueBuilder, propertyId,
                areaId, List.of(data.split(DATA_DELIMITER)), timestampNanos);
        if (halPropValue == null) {
            throw new IllegalArgumentException(
                    "Unsupported property type: propertyId=" + toPropertyIdString(propertyId)
                            + ", areaId=" + toAreaIdString(propertyId, areaId));
        }
        set(halPropValue);
    }

    private final ArraySet<HalServiceBase> mServicesToDispatch = new ArraySet<>();

    @Override
    public void onPropertyEvent(ArrayList<HalPropValue> propValues) {
        mHandler.post(() -> handleOnPropertyEvent(propValues));
    }

    @Override
    public void onPropertySetError(ArrayList<VehiclePropError> errors) {
        mHandler.post(() -> handleOnPropertySetError(errors));
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        synchronized (mLock) {
            writer.println("**dump HAL services**");
            for (int i = 0; i < mAllServices.size(); i++) {
                mAllServices.get(i).dump(writer);
            }
            // Dump all VHAL property configure.
            dumpPropertyConfigs(writer, -1);
            writer.printf("**All Events, now ns:%d**\n",
                    SystemClock.elapsedRealtimeNanos());
            for (int i = 0; i < mEventLog.size(); i++) {
                VehiclePropertyEventInfo info = mEventLog.valueAt(i);
                writer.printf("event count:%d, lastEvent: ", info.mEventCount);
                dumpPropValue(writer, info.mLastEvent);
            }
            writer.println("**Property handlers**");
            for (int i = 0; i < mPropertyHandlers.size(); i++) {
                int propId = mPropertyHandlers.keyAt(i);
                HalServiceBase service = mPropertyHandlers.valueAt(i);
                writer.printf("Property Id: %d // 0x%x name: %s, service: %s\n", propId, propId,
                        VehiclePropertyIds.toString(propId), service);
            }
        }
    }

     /**
     * Dumps or debug VHAL.
     */
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpVhal(ParcelFileDescriptor fd, List<String> options) throws RemoteException {
        mVehicleStub.dump(fd.getFileDescriptor(), options);
    }

    /**
     * Dumps the list of HALs.
     */
    public void dumpListHals(PrintWriter writer) {
        synchronized (mLock) {
            for (int i = 0; i < mAllServices.size(); i++) {
                writer.println(mAllServices.get(i).getClass().getName());
            }
        }
    }

    /**
     * Dumps the given HALs.
     */
    public void dumpSpecificHals(PrintWriter writer, String... halNames) {
        synchronized (mLock) {
            Map<String, HalServiceBase> byName = mAllServices.stream()
                    .collect(Collectors.toMap(s -> s.getClass().getSimpleName(), s -> s));
            for (String halName : halNames) {
                HalServiceBase service = byName.get(halName);
                if (service == null) {
                    writer.printf("No HAL named %s. Valid options are: %s\n",
                            halName, byName.keySet());
                    continue;
                }
                service.dump(writer);
            }
        }
    }

    /**
     * Dumps vehicle property values.
     *
     * @param propertyId property id, dump all properties' value if it is {@code -1}.
     * @param areaId areaId of the property, dump the property for all areaIds in the config
     *               if it is {@code -1}
     */
    public void dumpPropertyValueByCommand(PrintWriter writer, int propertyId, int areaId) {
        if (propertyId == -1) {
            writer.println("**All property values**");
            synchronized (mLock) {
                for (int i = 0; i < mAllProperties.size(); i++) {
                    HalPropConfig config = mAllProperties.valueAt(i);
                    dumpPropertyValueByConfig(writer, config);
                }
            }
        } else if (areaId == -1) {
            synchronized (mLock) {
                HalPropConfig config = mAllProperties.get(propertyId);
                if (config == null) {
                    writer.printf("Property: %s not supported by HAL\n",
                            toPropertyIdString(propertyId));
                    return;
                }
                dumpPropertyValueByConfig(writer, config);
            }
        } else {
            try {
                HalPropValue value = get(propertyId, areaId);
                dumpPropValue(writer, value);
            } catch (RuntimeException e) {
                writer.printf("Cannot get property value for property: %s in areaId: %s.\n",
                        toPropertyIdString(propertyId), toAreaIdString(propertyId, areaId));
            }
        }
    }

    /**
     * Gets all property configs from VHAL.
     */
    public HalPropConfig[] getAllPropConfigs() throws RemoteException, ServiceSpecificException {
        return mVehicleStub.getAllPropConfigs();
    }

    /**
     * Gets the property config for a property, returns {@code null} if not supported.
     */
    public @Nullable HalPropConfig getPropConfig(int propId) {
        synchronized (mLock) {
            return mAllProperties.get(propId);
        }
    }

    /**
     * Checks whether we are connected to AIDL VHAL: {@code true} or HIDL VHAL: {@code false}.
     */
    public boolean isAidlVhal() {
        return mVehicleStub.isAidlVhal();
    }

    /**
     * Checks if fake VHAL mode is enabled.
     *
     * @return {@code true} if car service is connected to FakeVehicleStub.
     */
    public boolean isFakeModeEnabled() {
        return mVehicleStub.isFakeModeEnabled();
    }

    private void dumpPropertyValueByConfig(PrintWriter writer, HalPropConfig config) {
        int propertyId = config.getPropId();
        HalAreaConfig[] areaConfigs = config.getAreaConfigs();
        if (areaConfigs == null || areaConfigs.length == 0) {
            try {
                HalPropValue value = get(config.getPropId());
                dumpPropValue(writer, value);
            } catch (RuntimeException e) {
                writer.printf("Can not get property value for property: %s, areaId: %s\n",
                        toPropertyIdString(propertyId), toAreaIdString(propertyId, /*areaId=*/0));
            }
        } else {
            for (HalAreaConfig areaConfig : areaConfigs) {
                int areaId = areaConfig.getAreaId();
                try {
                    HalPropValue value = get(propertyId, areaId);
                    dumpPropValue(writer, value);
                } catch (RuntimeException e) {
                    writer.printf(
                            "Can not get property value for property: %s in areaId: %s\n",
                            toPropertyIdString(propertyId), toAreaIdString(propertyId, areaId));
                }
            }
        }
    }

    /**
     * Dump VHAL property configs.
     * Dump all properties if {@code propertyId} is equal to {@code -1}.
     *
     * @param propertyId the property ID
     */
    public void dumpPropertyConfigs(PrintWriter writer, int propertyId) {
        HalPropConfig[] configs;
        synchronized (mLock) {
            configs = new HalPropConfig[mAllProperties.size()];
            for (int i = 0; i < mAllProperties.size(); i++) {
                configs[i] = mAllProperties.valueAt(i);
            }
        }

        if (propertyId == -1) {
            writer.println("**All properties**");
            for (HalPropConfig config : configs) {
                dumpPropertyConfigsHelp(writer, config);
            }
            return;
        }
        for (HalPropConfig config : configs) {
            if (config.getPropId() == propertyId) {
                dumpPropertyConfigsHelp(writer, config);
                return;
            }
        }
    }

    /** Dumps VehiclePropertyConfigs */
    private static void dumpPropertyConfigsHelp(PrintWriter writer, HalPropConfig config) {
        int propertyId = config.getPropId();
        writer.printf(
                "Property:%s, group:%s, areaType:%s, valueType:%s,\n    access:%s, changeMode:%s, "
                        + "configArray:%s, minSampleRateHz:%f, maxSampleRateHz:%f\n",
                toPropertyIdString(propertyId), toGroupString(propertyId),
                toAreaTypeString(propertyId), toValueTypeString(propertyId),
                toAccessString(config.getAccess()), toChangeModeString(config.getChangeMode()),
                Arrays.toString(config.getConfigArray()), config.getMinSampleRate(),
                config.getMaxSampleRate());
        if (config.getAreaConfigs() == null) {
            return;
        }
        for (HalAreaConfig area : config.getAreaConfigs()) {
            writer.printf("        areaId:%s, access:%s, f min:%f, f max:%f, i min:%d, i max:%d,"
                            + " i64 min:%d, i64 max:%d\n", toAreaIdString(propertyId,
                            area.getAreaId()), toAccessString(area.getAccess()),
                    area.getMinFloatValue(), area.getMaxFloatValue(), area.getMinInt32Value(),
                    area.getMaxInt32Value(), area.getMinInt64Value(), area.getMaxInt64Value());
        }
    }

    /**
     * Inject a VHAL event
     *
     * @param propertyId       the property ID as defined in the HAL
     * @param areaId           the area ID that this event services
     * @param value            the data value of the event
     * @param delayTimeSeconds add a certain duration to event timestamp
     */
    public void injectVhalEvent(int propertyId, int areaId, String value, int delayTimeSeconds)
            throws NumberFormatException {
        long timestampNanos = SystemClock.elapsedRealtimeNanos() + TimeUnit.SECONDS.toNanos(
                delayTimeSeconds);
        HalPropValue v = createPropValueForInjecting(mPropValueBuilder, propertyId, areaId,
                Arrays.asList(value.split(DATA_DELIMITER)), timestampNanos);
        if (v == null) {
            return;
        }
        mHandler.post(() -> handleOnPropertyEvent(Lists.newArrayList(v)));
    }

    /**
     * Injects continuous VHAL events.
     *
     * @param property the Vehicle property Id as defined in the HAL
     * @param zone the zone that this event services
     * @param value the data value of the event
     * @param sampleRate the sample rate for events in Hz
     * @param timeDurationInSec the duration for injecting events in seconds
     */
    public void injectContinuousVhalEvent(int property, int zone, String value,
            float sampleRate, long timeDurationInSec) {

        HalPropValue v = createPropValueForInjecting(mPropValueBuilder, property, zone,
                new ArrayList<>(Arrays.asList(value.split(DATA_DELIMITER))), 0);
        if (v == null) {
            return;
        }
        // rate in Hz
        if (sampleRate <= 0) {
            Slogf.e(CarLog.TAG_HAL, "Inject events at an invalid sample rate: " + sampleRate);
            return;
        }
        long period = (long) (1000 / sampleRate);
        long stopTime = timeDurationInSec * 1000 + SystemClock.elapsedRealtime();
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (stopTime < SystemClock.elapsedRealtime()) {
                    timer.cancel();
                    timer.purge();
                } else {
                    // Avoid the fake events be covered by real Event
                    long timestamp = SystemClock.elapsedRealtimeNanos()
                            + TimeUnit.SECONDS.toNanos(timeDurationInSec);
                    HalPropValue v = createPropValueForInjecting(mPropValueBuilder, property, zone,
                            new ArrayList<>(Arrays.asList(value.split(DATA_DELIMITER))), timestamp);
                    mHandler.post(() -> handleOnPropertyEvent(Lists.newArrayList(v)));
                }
            }
        }, /* delay= */0, period);
    }

    // Returns null if the property type is unsupported.
    @Nullable
    private static HalPropValue createPropValueForInjecting(HalPropValueBuilder builder,
            int propId, int zoneId, List<String> dataList, long timestamp) {
        int propertyType = propId & VehiclePropertyType.MASK;
        // Values can be comma separated list
        switch (propertyType) {
            case VehiclePropertyType.BOOLEAN:
                boolean boolValue = Boolean.parseBoolean(dataList.get(0));
                return builder.build(propId, zoneId, timestamp, VehiclePropertyStatus.AVAILABLE,
                        boolValue ? 1 : 0);
            case VehiclePropertyType.INT64:
            case VehiclePropertyType.INT64_VEC:
                long[] longValues = new long[dataList.size()];
                for (int i = 0; i < dataList.size(); i++) {
                    longValues[i] = Long.decode(dataList.get(i));
                }
                return builder.build(propId, zoneId, timestamp, VehiclePropertyStatus.AVAILABLE,
                        longValues);
            case VehiclePropertyType.INT32:
            case VehiclePropertyType.INT32_VEC:
                int[] intValues = new int[dataList.size()];
                for (int i = 0; i < dataList.size(); i++) {
                    intValues[i] = Integer.decode(dataList.get(i));
                }
                return builder.build(propId, zoneId, timestamp, VehiclePropertyStatus.AVAILABLE,
                        intValues);
            case VehiclePropertyType.FLOAT:
            case VehiclePropertyType.FLOAT_VEC:
                float[] floatValues = new float[dataList.size()];
                for (int i = 0; i < dataList.size(); i++) {
                    floatValues[i] = Float.parseFloat(dataList.get(i));
                }
                return builder.build(propId, zoneId, timestamp, VehiclePropertyStatus.AVAILABLE,
                        floatValues);
            default:
                Slogf.e(CarLog.TAG_HAL, "Property type unsupported:" + propertyType);
                return null;
        }
    }

    private static class VehiclePropertyEventInfo {
        private int mEventCount;
        private HalPropValue mLastEvent;

        private VehiclePropertyEventInfo(HalPropValue event) {
            mEventCount = 1;
            mLastEvent = event;
        }

        private void addNewEvent(HalPropValue event) {
            mEventCount++;
            mLastEvent = event;
        }
    }

    final class HalPropValueSetter {
        final int mPropId;
        final int mAreaId;

        private HalPropValueSetter(int propId, int areaId) {
            mPropId = propId;
            mAreaId = areaId;
        }

        /**
         * Set the property to the given value.
         *
         * @throws IllegalArgumentException if argument is invalid
         * @throws ServiceSpecificException if VHAL returns error
         */
        void to(boolean value) throws IllegalArgumentException, ServiceSpecificException {
            to(value ? 1 : 0);
        }

        /**
         * Set the property to the given value.
         *
         * @throws IllegalArgumentException if argument is invalid
         * @throws ServiceSpecificException if VHAL returns error
         */
        void to(int value) throws IllegalArgumentException, ServiceSpecificException {
            HalPropValue propValue = mPropValueBuilder.build(mPropId, mAreaId, value);
            submit(propValue);
        }

        /**
         * Set the property to the given values.
         *
         * @throws IllegalArgumentException if argument is invalid
         * @throws ServiceSpecificException if VHAL returns error
         */
        void to(int[] values) throws IllegalArgumentException, ServiceSpecificException {
            HalPropValue propValue = mPropValueBuilder.build(mPropId, mAreaId, values);
            submit(propValue);
        }

        /**
         * Set the property to the given values.
         *
         * @throws IllegalArgumentException if argument is invalid
         * @throws ServiceSpecificException if VHAL returns error
         */
        void to(Collection<Integer> values)
                throws IllegalArgumentException, ServiceSpecificException {
            int[] intValues = new int[values.size()];
            int i = 0;
            for (int value : values) {
                intValues[i] = value;
                i++;
            }
            HalPropValue propValue = mPropValueBuilder.build(mPropId, mAreaId, intValues);
            submit(propValue);
        }

        void submit(HalPropValue propValue)
                throws IllegalArgumentException, ServiceSpecificException {
            if (DBG) {
                Slogf.d(CarLog.TAG_HAL, "set - " + propValue);
            }
            setValueWithRetry(propValue);
        }
    }

    private static void dumpPropValue(PrintWriter writer, HalPropValue value) {
        writer.println(value);
    }

    interface RetriableAction {
        @Nullable HalPropValue run(HalPropValue requestValue)
                throws ServiceSpecificException, RemoteException;
    }

    private static HalPropValue invokeRetriable(RetriableAction action,
            String operation, HalPropValue requestValue, long maxDurationForRetryMs,
            long sleepBetweenRetryMs, int maxRetries)
            throws ServiceSpecificException, IllegalArgumentException {
        Retrier retrier = new Retrier(action, operation, requestValue, maxDurationForRetryMs,
                sleepBetweenRetryMs, maxRetries);
        HalPropValue result = retrier.invokeAction();
        if (DBG) {
            Slogf.d(CarLog.TAG_HAL,
                    "Invoked retriable action for %s - RequestValue: %s - ResultValue: %s, for "
                            + "retrier: %s",
                    operation, requestValue, result, retrier);
        }
        return result;
    }

    private PairSparseArray<RateInfo> cloneState(PairSparseArray<RateInfo> state) {
        PairSparseArray<RateInfo> cloned = new PairSparseArray<>();
        for (int i = 0; i < state.size(); i++) {
            int[] keyPair = state.keyPairAt(i);
            cloned.put(keyPair[0], keyPair[1], state.valueAt(i));
        }
        return cloned;
    }

    private static boolean isStaticProperty(HalPropConfig config) {
        return config.getChangeMode() == VehiclePropertyChangeMode.STATIC;
    }

    private static final class Retrier {
        private final RetriableAction mAction;
        private final String mOperation;
        private final HalPropValue mRequestValue;
        private final long mMaxDurationForRetryMs;
        private final long mSleepBetweenRetryMs;
        private final int mMaxRetries;
        private final long mStartTime;
        private int mRetryCount = 0;

        Retrier(RetriableAction action,
                String operation, HalPropValue requestValue, long maxDurationForRetryMs,
                long sleepBetweenRetryMs, int maxRetries) {
            mAction = action;
            mOperation = operation;
            mRequestValue = requestValue;
            mMaxDurationForRetryMs = maxDurationForRetryMs;
            mSleepBetweenRetryMs = sleepBetweenRetryMs;
            mMaxRetries = maxRetries;
            mStartTime = uptimeMillis();
        }

        HalPropValue invokeAction()
                throws ServiceSpecificException, IllegalArgumentException {
            mRetryCount++;

            try {
                return mAction.run(mRequestValue);
            } catch (ServiceSpecificException e) {
                switch (e.errorCode) {
                    case StatusCode.INVALID_ARG:
                        throw new IllegalArgumentException(errorMessage(mOperation, mRequestValue,
                            e.toString()));
                    case StatusCode.TRY_AGAIN:
                        return sleepAndTryAgain(e);
                    default:
                        throw e;
                }
            } catch (RemoteException e) {
                return sleepAndTryAgain(e);
            }
        }

        public String toString() {
            return "Retrier{"
                    + ", Operation=" + mOperation
                    + ", RequestValue=" + mRequestValue
                    + ", MaxDurationForRetryMs=" + mMaxDurationForRetryMs
                    + ", SleepBetweenRetriesMs=" + mSleepBetweenRetryMs
                    + ", MaxRetries=" + mMaxDurationForRetryMs
                    + ", StartTime=" + mStartTime
                    + "}";
        }

        private HalPropValue sleepAndTryAgain(Exception e)
                throws ServiceSpecificException, IllegalArgumentException {
            Slogf.d(CarLog.TAG_HAL, "trying the request: "
                    + toPropertyIdString(mRequestValue.getPropId()) + ", "
                    + toAreaIdString(mRequestValue.getPropId(), mRequestValue.getAreaId())
                    + " again...");
            try {
                Thread.sleep(mSleepBetweenRetryMs);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                Slogf.w(CarLog.TAG_HAL, "Thread was interrupted while waiting for vehicle HAL.",
                        interruptedException);
                throw new ServiceSpecificException(StatusCode.INTERNAL_ERROR,
                        errorMessage(mOperation, mRequestValue, interruptedException.toString()));
            }

            if (mMaxRetries != 0) {
                // If mMaxRetries is specified, check the retry count.
                if (mMaxRetries == mRetryCount) {
                    throw new ServiceSpecificException(StatusCode.TRY_AGAIN,
                            errorMessage(mOperation, mRequestValue,
                                    "cannot get property after " + mRetryCount + " retires, "
                                    + "last exception: " + e));
                }
            } else if ((uptimeMillis() - mStartTime) >= mMaxDurationForRetryMs) {
                // Otherwise, check whether we have reached timeout.
                throw new ServiceSpecificException(StatusCode.TRY_AGAIN,
                        errorMessage(mOperation, mRequestValue,
                                "cannot get property within " + mMaxDurationForRetryMs
                                + "ms, last exception: " + e));
            }
            return invokeAction();
        }
    }


    /**
     * Queries HalPropValue with list of GetVehicleHalRequest objects.
     *
     * <p>This method gets the HalPropValue using async methods.
     */
    public void getAsync(List<VehicleStub.AsyncGetSetRequest> getVehicleStubAsyncRequests,
            VehicleStub.VehicleStubCallbackInterface getVehicleStubAsyncCallback) {
        mVehicleStub.getAsync(getVehicleStubAsyncRequests, getVehicleStubAsyncCallback);
    }

    /**
     * Sets vehicle property value asynchronously.
     */
    public void setAsync(List<VehicleStub.AsyncGetSetRequest> setVehicleStubAsyncRequests,
            VehicleStub.VehicleStubCallbackInterface setVehicleStubAsyncCallback) {
        mVehicleStub.setAsync(setVehicleStubAsyncRequests, setVehicleStubAsyncCallback);
    }

    /**
     * Cancels all the on-going async requests with the given request IDs.
     */
    public void cancelRequests(List<Integer> vehicleStubRequestIds) {
        mVehicleStub.cancelRequests(vehicleStubRequestIds);
    }
}
