/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.car.hardware.CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;
import static com.android.car.internal.common.CommonConstants.EMPTY_INT_ARRAY;
import static com.android.car.internal.property.CarPropertyHelper.SYNC_OP_LIMIT_TRY_AGAIN;
import static com.android.car.internal.property.CarPropertyHelper.propertyIdsToString;

import static java.lang.Integer.toHexString;
import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.VehiclePropertyIds;
import android.car.builtin.os.TraceHelper;
import android.car.builtin.util.Slogf;
import android.car.feature.FeatureFlags;
import android.car.feature.FeatureFlagsImpl;
import android.car.feature.Flags;
import android.car.hardware.CarHvacFanDirection;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.AreaIdConfig;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.CruiseControlType;
import android.car.hardware.property.ErrorState;
import android.car.hardware.property.EvStoppingMode;
import android.car.hardware.property.ICarProperty;
import android.car.hardware.property.ICarPropertyEventListener;
import android.car.hardware.property.WindshieldWipersSwitch;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.os.Trace;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;
import android.util.proto.ProtoOutputStream;

import com.android.car.hal.PropertyHalService;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.property.AsyncPropertyServiceRequest;
import com.android.car.internal.property.AsyncPropertyServiceRequestList;
import com.android.car.internal.property.CarPropertyConfigList;
import com.android.car.internal.property.CarPropertyErrorCodes;
import com.android.car.internal.property.CarPropertyHelper;
import com.android.car.internal.property.CarSubscription;
import com.android.car.internal.property.GetPropertyConfigListResult;
import com.android.car.internal.property.IAsyncPropertyResultCallback;
import com.android.car.internal.property.InputSanitizationUtils;
import com.android.car.internal.property.SubscriptionManager;
import com.android.car.internal.util.ArrayUtils;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.internal.util.IntArray;
import com.android.car.property.CarPropertyServiceClient;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;
import com.android.modules.expresslog.Histogram;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * This class implements the binder interface for ICarProperty.aidl to make it easier to create
 * multiple managers that deal with Vehicle Properties. The property Ids in this class are IDs in
 * manager level.
 */
public class CarPropertyService extends ICarProperty.Stub
        implements CarServiceBase, PropertyHalService.PropertyHalListener {
    private static final String TAG = CarLog.tagFor(CarPropertyService.class);
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);
    // Maximum count of sync get/set property operation allowed at once. The reason we limit this
    // is because each sync get/set property operation takes up one binder thread. If they take
    // all the binder thread, we do not have thread left for the result callback from VHAL. This
    // will cause all the pending sync operation to timeout because result cannot be delivered.
    private static final int SYNC_GET_SET_PROPERTY_OP_LIMIT = 16;
    private static final long TRACE_TAG = TraceHelper.TRACE_TAG_CAR_SERVICE;
    // A list of properties that must not set waitForPropertyUpdate to {@code true} for set async.
    private static final Set<Integer> NOT_ALLOWED_WAIT_FOR_UPDATE_PROPERTIES =
            new HashSet<>(Arrays.asList(
                VehiclePropertyIds.HVAC_TEMPERATURE_VALUE_SUGGESTION
            ));

    private static final Set<Integer> ERROR_STATES =
            new HashSet<Integer>(Arrays.asList(
                    ErrorState.OTHER_ERROR_STATE,
                    ErrorState.NOT_AVAILABLE_DISABLED,
                    ErrorState.NOT_AVAILABLE_SPEED_LOW,
                    ErrorState.NOT_AVAILABLE_SPEED_HIGH,
                    ErrorState.NOT_AVAILABLE_SAFETY
            ));
    private static final Set<Integer> CAR_HVAC_FAN_DIRECTION_UNWRITABLE_STATES =
            new HashSet<Integer>(Arrays.asList(
                    CarHvacFanDirection.UNKNOWN
            ));
    private static final Set<Integer> CRUISE_CONTROL_TYPE_UNWRITABLE_STATES =
            new HashSet<Integer>(Arrays.asList(
                    CruiseControlType.OTHER
            ));
    static {
        CRUISE_CONTROL_TYPE_UNWRITABLE_STATES.addAll(ERROR_STATES);
    }
    private static final Set<Integer> EV_STOPPING_MODE_UNWRITABLE_STATES =
            new HashSet<Integer>(Arrays.asList(
                    EvStoppingMode.STATE_OTHER
            ));
    private static final Set<Integer> WINDSHIELD_WIPERS_SWITCH_UNWRITABLE_STATES =
            new HashSet<Integer>(Arrays.asList(
                    WindshieldWipersSwitch.OTHER
            ));

    private static final SparseArray<Set<Integer>> PROPERTY_ID_TO_UNWRITABLE_STATES =
            new SparseArray<>();
    static {
        PROPERTY_ID_TO_UNWRITABLE_STATES.put(
                VehiclePropertyIds.CRUISE_CONTROL_TYPE,
                CRUISE_CONTROL_TYPE_UNWRITABLE_STATES);
        PROPERTY_ID_TO_UNWRITABLE_STATES.put(
                VehiclePropertyIds.EV_STOPPING_MODE,
                EV_STOPPING_MODE_UNWRITABLE_STATES);
        PROPERTY_ID_TO_UNWRITABLE_STATES.put(
                VehiclePropertyIds.HVAC_FAN_DIRECTION,
                CAR_HVAC_FAN_DIRECTION_UNWRITABLE_STATES);
        PROPERTY_ID_TO_UNWRITABLE_STATES.put(
                VehiclePropertyIds.WINDSHIELD_WIPERS_SWITCH,
                WINDSHIELD_WIPERS_SWITCH_UNWRITABLE_STATES);
    }

    private static final Histogram sConcurrentSyncOperationHistogram = new Histogram(
            "automotive_os.value_concurrent_sync_operations",
            new Histogram.UniformOptions(/* binCount= */ 17, /* minValue= */ 0,
                    /* exclusiveMaxValue= */ 17));

    private static final Histogram sGetPropertySyncLatencyHistogram = new Histogram(
            "automotive_os.value_sync_get_property_latency",
            new Histogram.ScaledRangeOptions(/* binCount= */ 20, /* minValue= */ 0,
                    /* firstBinWidth= */ 2, /* scaleFactor= */ 1.5f));

    private static final Histogram sSetPropertySyncLatencyHistogram = new Histogram(
            "automotive_os.value_sync_set_property_latency",
            new Histogram.ScaledRangeOptions(/* binCount= */ 20, /* minValue= */ 0,
                    /* firstBinWidth= */ 2, /* scaleFactor= */ 1.5f));

    private static final Histogram sSubscriptionUpdateRateHistogram = new Histogram(
            "automotive_os.value_subscription_update_rate",
            new Histogram.UniformOptions(/* binCount= */ 101, /* minValue= */ 0,
                    /* exclusiveMaxValue= */ 101));
    private static final Histogram sGetAsyncLatencyHistogram = new Histogram(
            "automotive_os.value_get_async_latency",
            new Histogram.UniformOptions(/* binCount= */ 20, /* minValue= */ 0,
                    /* exclusiveMaxValue= */ 1000));

    private static final Histogram sSetAsyncLatencyHistogram = new Histogram(
            "automotive_os.value_set_async_latency",
            new Histogram.UniformOptions(/* binCount= */ 20, /* minValue= */ 0,
                    /* exclusiveMaxValue= */ 1000));

    private final Context mContext;
    private final PropertyHalService mPropertyHalService;
    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final Map<IBinder, CarPropertyServiceClient> mClientMap = new ArrayMap<>();
    @GuardedBy("mLock")
    private final SubscriptionManager<CarPropertyServiceClient> mSubscriptionManager =
            new SubscriptionManager<>();
    @GuardedBy("mLock")
    private final SparseArray<SparseArray<CarPropertyServiceClient>> mSetOpClientByAreaIdByPropId =
            new SparseArray<>();
    private final HandlerThread mHandlerThread =
            CarServiceUtils.getHandlerThread(getClass().getSimpleName());
    private final Handler mHandler = new Handler(mHandlerThread.getLooper());
    // Use SparseArray instead of map to save memory.
    @GuardedBy("mLock")
    private SparseArray<CarPropertyConfig<?>> mPropertyIdToCarPropertyConfig = new SparseArray<>();
    @GuardedBy("mLock")
    private int mSyncGetSetPropertyOpCount;

    private FeatureFlags mFeatureFlags = new FeatureFlagsImpl();

    public CarPropertyService(Context context, PropertyHalService propertyHalService) {
        if (DBG) {
            Slogf.d(TAG, "CarPropertyService started!");
        }
        mPropertyHalService = propertyHalService;
        mContext = context;
    }

    /** Sets fake feature flag for unit testing. */
    @VisibleForTesting
    void setFeatureFlags(FeatureFlags fakeFeatureFlags) {
        mFeatureFlags = fakeFeatureFlags;
    }

    @Override
    public void init() {
        synchronized (mLock) {
            // Cache the configs list to avoid subsequent binder calls
            mPropertyIdToCarPropertyConfig = mPropertyHalService.getPropertyList();
            if (DBG) {
                Slogf.d(TAG, "cache CarPropertyConfigs " + mPropertyIdToCarPropertyConfig.size());
            }
        }
        mPropertyHalService.setPropertyHalListener(this);
    }

    @Override
    public void release() {
        synchronized (mLock) {
            mClientMap.clear();
            mSubscriptionManager.clear();
            mPropertyHalService.setPropertyHalListener(null);
            mSetOpClientByAreaIdByPropId.clear();
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("*CarPropertyService*");
        writer.increaseIndent();
        synchronized (mLock) {
            writer.println("There are " + mClientMap.size() + " clients that have registered"
                    + " listeners in CarPropertyService.");
            writer.println("Current sync operation count: " + mSyncGetSetPropertyOpCount);
            writer.println("Properties registered: ");
            writer.increaseIndent();
            mSubscriptionManager.dump(writer);
            writer.decreaseIndent();
            writer.println("Properties that have a listener registered for setProperty:");
            writer.increaseIndent();
            for (int i = 0; i < mSetOpClientByAreaIdByPropId.size(); i++) {
                int propId = mSetOpClientByAreaIdByPropId.keyAt(i);
                SparseArray areaIdToClient = mSetOpClientByAreaIdByPropId.valueAt(i);
                for (int j = 0; j < areaIdToClient.size(); j++) {
                    int areaId = areaIdToClient.keyAt(j);
                    writer.println("Client: " + areaIdToClient.valueAt(j).hashCode() + " propId: "
                            + VehiclePropertyIds.toString(propId)  + " areaId: 0x"
                            + toHexString(areaId));
                }
            }
            writer.decreaseIndent();
        }
        writer.decreaseIndent();
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {}

    /**
     * Subscribes to the property update events for the property ID.
     *
     * Used internally in car service.
     */
    public void registerListener(int propertyId, float updateRateHz,
            boolean enableVariableUpdateRate, float resolution,
            ICarPropertyEventListener carPropertyEventListener) {
        CarSubscription option = new CarSubscription();
        int[] areaIds = EMPTY_INT_ARRAY;
        CarPropertyConfig<?> carPropertyConfig = getCarPropertyConfig(propertyId);
        // carPropertyConfig nullity check will be done in registerListener
        if (carPropertyConfig != null) {
            areaIds = carPropertyConfig.getAreaIds();
        }
        option.propertyId = propertyId;
        option.updateRateHz = updateRateHz;
        option.areaIds = areaIds;
        option.enableVariableUpdateRate = enableVariableUpdateRate;
        option.resolution = resolution;
        registerListener(List.of(option), carPropertyEventListener);
    }

    /**
     * Subscribes to the property update events for the property ID at a resolution of 0.
     *
     * Used internally in car service.
     */
    public void registerListener(int propertyId, float updateRateHz,
            boolean enableVariableUpdateRate,
            ICarPropertyEventListener carPropertyEventListener) {
        registerListener(propertyId, updateRateHz, enableVariableUpdateRate, /* resolution */ 0.0f,
                carPropertyEventListener);
    }

    /**
     * Subscribes to the property update events for the property ID with VUR enabled for continuous
     * property and a resolution of 0.
     *
     * Used internally in car service.
     */
    public void registerListener(int propertyId, float updateRateHz,
            ICarPropertyEventListener carPropertyEventListener)
            throws IllegalArgumentException, ServiceSpecificException {
        CarPropertyConfig<?> carPropertyConfig = getCarPropertyConfig(propertyId);
        boolean enableVariableUpdateRate = false;
        // carPropertyConfig nullity check will be done in registerListener
        if (carPropertyConfig != null
                && carPropertyConfig.getChangeMode() == VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS) {
            enableVariableUpdateRate = true;
        }
        registerListener(propertyId, updateRateHz, enableVariableUpdateRate, /* resolution */ 0.0f,
                carPropertyEventListener);
    }

    /**
     * Validates the subscribe options and sanitize the update rate inside it.
     *
     * The update rate will be fit within the {@code minSampleRate} and {@code maxSampleRate}.
     *
     * @throws IllegalArgumentException if one of the options is not valid.
     */
    private List<CarSubscription> validateAndSanitizeSubscriptions(
                List<CarSubscription> carSubscriptions)
            throws IllegalArgumentException {
        List<CarSubscription> sanitizedSubscriptions = new ArrayList<>();
        for (int i = 0; i < carSubscriptions.size(); i++) {
            CarSubscription subscription = carSubscriptions.get(i);
            CarPropertyConfig<?> carPropertyConfig = validateRegisterParameterAndGetConfig(
                    subscription.propertyId, subscription.areaIds);
            subscription.updateRateHz = InputSanitizationUtils.sanitizeUpdateRateHz(
                    carPropertyConfig, subscription.updateRateHz);
            subscription.resolution = InputSanitizationUtils.sanitizeResolution(
                    mFeatureFlags, carPropertyConfig, subscription.resolution);
            sanitizedSubscriptions.addAll(InputSanitizationUtils.sanitizeEnableVariableUpdateRate(
                    mFeatureFlags, carPropertyConfig, subscription));
        }
        return sanitizedSubscriptions;
    }

    /**
     * Gets the {@code CarPropertyServiceClient} for the binder, create a new one if not exists.
     *
     * @param carPropertyEventListener The client callback.
     * @return the client for the binder, or null if the client is already dead.
     */
    @GuardedBy("mLock")
    private @Nullable CarPropertyServiceClient getOrCreateClientForBinderLocked(
            ICarPropertyEventListener carPropertyEventListener) {
        IBinder listenerBinder = carPropertyEventListener.asBinder();
        CarPropertyServiceClient client = mClientMap.get(listenerBinder);
        if (client != null) {
            return client;
        }
        client = new CarPropertyServiceClient(carPropertyEventListener,
                this::unregisterListenerBinderForProps);
        if (client.isDead()) {
            Slogf.w(TAG, "the ICarPropertyEventListener is already dead");
            return null;
        }
        mClientMap.put(listenerBinder, client);
        return client;
    }

    @Override
    public void registerListener(List<CarSubscription> carSubscriptions,
            ICarPropertyEventListener carPropertyEventListener)
            throws IllegalArgumentException, ServiceSpecificException {
        requireNonNull(carSubscriptions);
        requireNonNull(carPropertyEventListener);

        List<CarSubscription> sanitizedOptions =
                validateAndSanitizeSubscriptions(carSubscriptions);

        CarPropertyServiceClient finalClient;
        synchronized (mLock) {
            // We create the client first so that we will not subscribe if the binder is already
            // dead.
            CarPropertyServiceClient client = getOrCreateClientForBinderLocked(
                    carPropertyEventListener);
            if (client == null) {
                // The client is already dead.
                return;
            }

            for (int i = 0; i < sanitizedOptions.size(); i++) {
                CarSubscription option = sanitizedOptions.get(i);
                sSubscriptionUpdateRateHistogram.logSample(option.updateRateHz);
                if (DBG) {
                    Slogf.d(TAG, "registerListener after update rate sanitization, options: "
                            + sanitizedOptions.get(i));
                }
            }

            // Store the new subscritpion state in the staging area. This does not affect the
            // current state.
            mSubscriptionManager.stageNewOptions(client, sanitizedOptions);

            // Try to apply the staged changes.
            try {
                applyStagedChangesLocked();
            } catch (Exception e) {
                mSubscriptionManager.dropCommit();
                throw e;
            }

            // After subscribeProperty succeeded, adds the client to the
            // [propertyId -> subscribed clients list] map. Adds the property to the client's
            // [areaId -> update rate] map.
            mSubscriptionManager.commit();
            for (int i = 0; i < sanitizedOptions.size(); i++) {
                CarSubscription option = sanitizedOptions.get(i);
                // After {@code validateAndSanitizeSubscriptions}, update rate must be 0 for
                // on-change property and non-0 for continuous property.
                if (option.updateRateHz != 0) {
                    client.addContinuousProperty(
                            option.propertyId, option.areaIds, option.updateRateHz,
                            option.enableVariableUpdateRate, option.resolution);
                } else {
                    client.addOnChangeProperty(option.propertyId, option.areaIds);
                }
            }
            finalClient = client;
        }

        mHandler.post(() ->
                getAndDispatchPropertyInitValue(sanitizedOptions, finalClient));
    }

    /**
     * Register property listener for car service's internal usage.
     *
     * This function catches all exceptions and return {@code true} if succeed.
     */
    public boolean registerListenerSafe(int propertyId, float updateRateHz,
            boolean enableVariableUpdateRate,
            ICarPropertyEventListener iCarPropertyEventListener) {
        try {
            registerListener(propertyId, updateRateHz, enableVariableUpdateRate,
                    iCarPropertyEventListener);
            return true;
        } catch (Exception e) {
            Slogf.e(TAG, e, "registerListenerSafe() failed for property ID: %s updateRateHz: %f",
                    VehiclePropertyIds.toString(propertyId), updateRateHz);
            return false;
        }
    }

    /**
     * Register property listener for car service's internal usage with VUR enabled for continuous
     * property.
     *
     * This function catches all exceptions and return {@code true} if succeed.
     */
    public boolean registerListenerSafe(int propertyId, float updateRateHz,
            ICarPropertyEventListener iCarPropertyEventListener) {
        try {
            registerListener(propertyId, updateRateHz, iCarPropertyEventListener);
            return true;
        } catch (Exception e) {
            Slogf.e(TAG, e, "registerListenerSafe() failed for property ID: %s updateRateHz: %f",
                    VehiclePropertyIds.toString(propertyId), updateRateHz);
            return false;
        }
    }

    @GuardedBy("mLock")
    void applyStagedChangesLocked() throws ServiceSpecificException {
        List<CarSubscription> filteredSubscriptions = new ArrayList<>();
        List<Integer> propertyIdsToUnsubscribe = new ArrayList<>();
        mSubscriptionManager.diffBetweenCurrentAndStage(/* out */ filteredSubscriptions,
                /* out */ propertyIdsToUnsubscribe);

        if (DBG) {
            Slogf.d(TAG, "Subscriptions after filtering out options that are already"
                    + " subscribed at the same or a higher rate: " + filteredSubscriptions);
        }

        if (!filteredSubscriptions.isEmpty()) {
            try {
                mPropertyHalService.subscribeProperty(filteredSubscriptions);
            } catch (ServiceSpecificException e) {
                Slogf.e(TAG, "PropertyHalService.subscribeProperty failed", e);
                throw e;
            }
        }

        for (int i = 0; i < propertyIdsToUnsubscribe.size(); i++) {
            Slogf.d(TAG, "Property: %s is no longer subscribed",
                    propertyIdsToUnsubscribe.get(i));
            try {
                mPropertyHalService.unsubscribeProperty(propertyIdsToUnsubscribe.get(i));
            } catch (ServiceSpecificException e) {
                Slogf.e(TAG, "failed to call PropertyHalService.unsubscribeProperty", e);
                throw e;
            }
        }
    }

    private void getAndDispatchPropertyInitValue(List<CarSubscription> carSubscriptions,
            CarPropertyServiceClient client) {
        List<CarPropertyEvent> events = new ArrayList<>();
        for (int i = 0; i < carSubscriptions.size(); i++) {
            CarSubscription option = carSubscriptions.get(i);
            int propertyId = option.propertyId;
            int[] areaIds = option.areaIds;
            for (int areaId : areaIds) {
                CarPropertyValue carPropertyValue = null;
                try {
                    carPropertyValue = getProperty(propertyId, areaId);
                } catch (ServiceSpecificException e) {
                    Slogf.w(TAG, "Get initial carPropertyValue for registerCallback failed -"
                                    + " property ID: %s, area ID %s, exception: %s",
                            VehiclePropertyIds.toString(propertyId), Integer.toHexString(areaId),
                            e);
                    int errorCode = CarPropertyErrorCodes.getVhalSystemErrorCode(e.errorCode);
                    long timestampNanos = SystemClock.elapsedRealtimeNanos();
                    CarPropertyConfig<?> carPropertyConfig = getCarPropertyConfig(propertyId);
                    Object defaultValue = CarPropertyHelper.getDefaultValue(
                            carPropertyConfig.getPropertyType());
                    if (CarPropertyErrorCodes.isNotAvailableVehicleHalStatusCode(errorCode)) {
                        carPropertyValue = new CarPropertyValue<>(propertyId, areaId,
                                CarPropertyValue.STATUS_UNAVAILABLE, timestampNanos, defaultValue);
                    } else {
                        carPropertyValue = new CarPropertyValue<>(propertyId, areaId,
                                CarPropertyValue.STATUS_ERROR, timestampNanos, defaultValue);
                    }
                } catch (Exception e) {
                    // Do nothing.
                    Slogf.e(TAG, "Get initial carPropertyValue for registerCallback failed -"
                                    + " property ID: %s, area ID %s, exception: %s",
                            VehiclePropertyIds.toString(propertyId), Integer.toHexString(areaId),
                            e);
                }
                if (carPropertyValue != null) {
                    CarPropertyEvent event = new CarPropertyEvent(
                            CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE, carPropertyValue);
                    events.add(event);
                }
            }
        }

        if (events.isEmpty()) {
            return;
        }
        try {
            client.onEvent(events);
        } catch (RemoteException ex) {
            // If we cannot send a record, it's likely the connection snapped. Let the binder
            // death handle the situation.
            Slogf.e(TAG, "onEvent calling failed", ex);
        }
    }

    @Override
    public void unregisterListener(int propertyId,
            ICarPropertyEventListener iCarPropertyEventListener)
            throws IllegalArgumentException, ServiceSpecificException {
        requireNonNull(iCarPropertyEventListener);

        // We do not have to call validateRegisterParameterAndGetConfig since if the property was
        // previously subscribed, then the client already had the read permssion. If not, then we
        // would do nothing.
        // We also need to consider the case where the client has write-only permission and uses
        // setProperty before, we must remove the listener associated with property set error.
        assertConfigNotNullAndGetConfig(propertyId);

        if (DBG) {
            Slogf.d(TAG,
                    "unregisterListener property ID=" + VehiclePropertyIds.toString(propertyId));
        }

        IBinder listenerBinder = iCarPropertyEventListener.asBinder();
        unregisterListenerBinderForProps(List.of(propertyId), listenerBinder);
    }

    /**
     * Unregister property listener for car service's internal usage.
     */
    public boolean unregisterListenerSafe(int propertyId,
            ICarPropertyEventListener iCarPropertyEventListener) {
        try {
            unregisterListener(propertyId, iCarPropertyEventListener);
            return true;
        } catch (Exception e) {
            Slogf.e(TAG, e, "unregisterListenerSafe() failed for property ID: %s",
                    VehiclePropertyIds.toString(propertyId));
            return false;
        }
    }

    private void unregisterListenerBinderForProps(List<Integer> propertyIds, IBinder listenerBinder)
            throws ServiceSpecificException {
        synchronized (mLock) {
            CarPropertyServiceClient client = mClientMap.get(listenerBinder);
            if (client == null) {
                Slogf.e(TAG, "unregisterListener: Listener was not previously "
                        + "registered for any property");
                return;
            }

            ArraySet<Integer> validPropertyIds = new ArraySet<>();
            for (int i = 0; i < propertyIds.size(); i++) {
                int propertyId = propertyIds.get(i);
                if (mPropertyIdToCarPropertyConfig.get(propertyId) == null) {
                    // Do not attempt to unregister an invalid propertyId
                    Slogf.e(TAG, "unregisterListener: propertyId is not in config list: %s",
                            VehiclePropertyIds.toString(propertyId));
                    continue;
                }
                validPropertyIds.add(propertyId);
            }

            if (validPropertyIds.isEmpty()) {
                Slogf.e(TAG, "All properties are invalid: " + propertyIdsToString(propertyIds));
                return;
            }

            // Clear the onPropertySetError callback associated with this property.
            clearSetOperationRecorderLocked(validPropertyIds, client);

            mSubscriptionManager.stageUnregister(client, validPropertyIds);

            try {
                applyStagedChangesLocked();
            } catch (Exception e) {
                mSubscriptionManager.dropCommit();
                throw e;
            }

            mSubscriptionManager.commit();
            boolean allPropertiesRemoved = client.remove(validPropertyIds);
            if (allPropertiesRemoved) {
                mClientMap.remove(listenerBinder);
            }
        }
    }

    /**
     * Return the list of properties' configs that the caller may access.
     */
    @NonNull
    @Override
    public CarPropertyConfigList getPropertyList() {
        int[] allPropIds;
        // Avoid permission checking under lock.
        synchronized (mLock) {
            allPropIds = new int[mPropertyIdToCarPropertyConfig.size()];
            for (int i = 0; i < mPropertyIdToCarPropertyConfig.size(); i++) {
                allPropIds[i] = mPropertyIdToCarPropertyConfig.keyAt(i);
            }
        }
        return getPropertyConfigList(allPropIds).carPropertyConfigList;
    }

    /**
     * @param propIds Array of property Ids
     * @return the list of properties' configs that the caller may access.
     */
    @NonNull
    @Override
    public GetPropertyConfigListResult getPropertyConfigList(int[] propIds) {
        GetPropertyConfigListResult result = new GetPropertyConfigListResult();
        List<CarPropertyConfig> availableProp = new ArrayList<>();
        IntArray missingPermissionPropIds = new IntArray(availableProp.size());
        IntArray unsupportedPropIds = new IntArray(availableProp.size());

        synchronized (mLock) {
            for (int propId : propIds) {
                if (!mPropertyIdToCarPropertyConfig.contains(propId)) {
                    unsupportedPropIds.add(propId);
                    continue;
                }

                if (!mPropertyHalService.isReadable(mContext, propId)
                        && !mPropertyHalService.isWritable(mContext, propId)) {
                    missingPermissionPropIds.add(propId);
                    continue;
                }

                availableProp.add(mPropertyIdToCarPropertyConfig.get(propId));
            }
        }
        if (DBG) {
            Slogf.d(TAG, "getPropertyList returns " + availableProp.size() + " configs");
        }
        result.carPropertyConfigList = new CarPropertyConfigList(availableProp);
        result.missingPermissionPropIds = missingPermissionPropIds.toArray();
        result.unsupportedPropIds = unsupportedPropIds.toArray();
        return result;
    }

    @Nullable
    private <V> V runSyncOperationCheckLimit(Callable<V> c) {
        synchronized (mLock) {
            if (mSyncGetSetPropertyOpCount >= SYNC_GET_SET_PROPERTY_OP_LIMIT) {
                sConcurrentSyncOperationHistogram.logSample(mSyncGetSetPropertyOpCount);
                throw new ServiceSpecificException(SYNC_OP_LIMIT_TRY_AGAIN);
            }
            mSyncGetSetPropertyOpCount += 1;
            sConcurrentSyncOperationHistogram.logSample(mSyncGetSetPropertyOpCount);
            if (DBG) {
                Slogf.d(TAG, "mSyncGetSetPropertyOpCount: %d", mSyncGetSetPropertyOpCount);
            }
        }
        try {
            Trace.traceBegin(TRACE_TAG, "call sync operation");
            return c.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            Slogf.e(TAG, e, "catching unexpected exception for getProperty/setProperty");
            return null;
        } finally {
            Trace.traceEnd(TRACE_TAG);
            synchronized (mLock) {
                mSyncGetSetPropertyOpCount -= 1;
                if (DBG) {
                    Slogf.d(TAG, "mSyncGetSetPropertyOpCount: %d", mSyncGetSetPropertyOpCount);
                }
            }
        }
    }

    @Override
    public CarPropertyValue getProperty(int propertyId, int areaId)
            throws IllegalArgumentException, ServiceSpecificException {
        validateGetParameters(propertyId, areaId);
        Trace.traceBegin(TRACE_TAG, "CarPropertyValue#getProperty");
        long currentTimeMs = System.currentTimeMillis();
        try {
            return runSyncOperationCheckLimit(() -> {
                return mPropertyHalService.getProperty(propertyId, areaId);
            });
        } finally {
            if (DBG) {
                Slogf.d(TAG, "Latency of getPropertySync is: %f", (float) (System
                        .currentTimeMillis() - currentTimeMs));
            }
            sGetPropertySyncLatencyHistogram.logSample((float) (System.currentTimeMillis()
                    - currentTimeMs));
            Trace.traceEnd(TRACE_TAG);
        }
    }

    /**
     * Get property value for car service's internal usage.
     *
     * @return null if property is not implemented or there is an exception in the vehicle.
     */
    @Nullable
    public CarPropertyValue getPropertySafe(int propertyId, int areaId) {
        try {
            return getProperty(propertyId, areaId);
        } catch (Exception e) {
            Slogf.w(TAG, e, "getPropertySafe() failed for property id: %s area id: 0x%s",
                    VehiclePropertyIds.toString(propertyId), toHexString(areaId));
            return null;
        }
    }

    /**
     * Return read permission string for given property ID. The format of the return value of this
     * function has changed over time and thus should not be relied on.
     *
     * @param propId the property ID to query
     * @return the permission needed to read this property, {@code null} if the property ID is not
     * available
     */
    @Nullable
    @Override
    public String getReadPermission(int propId) {
        return mPropertyHalService.getReadPermission(propId);
    }

    /**
     * Return write permission string for given property ID. The format of the return value of this
     * function has changed over time and thus should not be relied on.
     *
     * @param propId the property ID to query
     * @return the permission needed to write this property, {@code null} if the property ID is not
     * available
     */
    @Nullable
    @Override
    public String getWritePermission(int propId) {
        return mPropertyHalService.getWritePermission(propId);
    }

    @Override
    public void setProperty(CarPropertyValue carPropertyValue,
            ICarPropertyEventListener iCarPropertyEventListener)
            throws IllegalArgumentException, ServiceSpecificException {
        requireNonNull(iCarPropertyEventListener);
        validateSetParameters(carPropertyValue);
        long currentTimeMs = System.currentTimeMillis();

        runSyncOperationCheckLimit(() -> {
            mPropertyHalService.setProperty(carPropertyValue);
            return null;
        });

        IBinder listenerBinder = iCarPropertyEventListener.asBinder();
        synchronized (mLock) {
            CarPropertyServiceClient client = mClientMap.get(listenerBinder);
            if (client == null) {
                client = new CarPropertyServiceClient(iCarPropertyEventListener,
                        this::unregisterListenerBinderForProps);
            }
            if (client.isDead()) {
                Slogf.w(TAG, "the ICarPropertyEventListener is already dead");
                return;
            }
            // Note that here we are not calling addContinuousProperty or addOnChangeProperty
            // for this client because we will not enable filtering in this client, so no need to
            // record these filtering information.
            mClientMap.put(listenerBinder, client);
            updateSetOperationRecorderLocked(carPropertyValue.getPropertyId(),
                    carPropertyValue.getAreaId(), client);
            if (DBG) {
                Slogf.d(TAG, "Latency of setPropertySync is: %f", (float) (System
                        .currentTimeMillis() - currentTimeMs));
            }
            sSetPropertySyncLatencyHistogram.logSample((float) (System.currentTimeMillis()
                    - currentTimeMs));
        }
    }

    // Updates recorder for set operation.
    @GuardedBy("mLock")
    private void updateSetOperationRecorderLocked(int propertyId, int areaId,
            CarPropertyServiceClient client) {
        if (mSetOpClientByAreaIdByPropId.get(propertyId) != null) {
            mSetOpClientByAreaIdByPropId.get(propertyId).put(areaId, client);
        } else {
            SparseArray<CarPropertyServiceClient> areaIdToClient = new SparseArray<>();
            areaIdToClient.put(areaId, client);
            mSetOpClientByAreaIdByPropId.put(propertyId, areaIdToClient);
        }
    }

    // Clears map when client unregister for property.
    @GuardedBy("mLock")
    private void clearSetOperationRecorderLocked(ArraySet<Integer> propertyIds,
            CarPropertyServiceClient client) {
        for (int i = 0; i < propertyIds.size(); i++) {
            int propertyId = propertyIds.valueAt(i);
            SparseArray<CarPropertyServiceClient> areaIdToClient = mSetOpClientByAreaIdByPropId.get(
                    propertyId);
            if (areaIdToClient == null) {
                continue;
            }
            List<Integer> areaIdsToRemove = new ArrayList<>();
            for (int j = 0; j < areaIdToClient.size(); j++) {
                if (client.equals(areaIdToClient.valueAt(j))) {
                    areaIdsToRemove.add(areaIdToClient.keyAt(j));
                }
            }
            for (int j = 0; j < areaIdsToRemove.size(); j++) {
                if (DBG) {
                    Slogf.d(TAG, "clear set operation client for property: %s, area ID: %d",
                            VehiclePropertyIds.toString(propertyId), areaIdsToRemove.get(j));
                }
                areaIdToClient.remove(areaIdsToRemove.get(j));
            }
            if (areaIdToClient.size() == 0) {
                mSetOpClientByAreaIdByPropId.remove(propertyId);
            }
        }
    }

    // Implement PropertyHalListener interface
    @Override
    public void onPropertyChange(List<CarPropertyEvent> events) {
        Map<CarPropertyServiceClient, List<CarPropertyEvent>> eventsToDispatch = new ArrayMap<>();
        synchronized (mLock) {
            for (int i = 0; i < events.size(); i++) {
                CarPropertyEvent event = events.get(i);
                int propId = event.getCarPropertyValue().getPropertyId();
                int areaId = event.getCarPropertyValue().getAreaId();
                Set<CarPropertyServiceClient> clients = mSubscriptionManager.getClients(
                        propId, areaId);
                if (clients == null) {
                    Slogf.e(TAG,
                            "onPropertyChange: no listener registered for propId=%s, areaId=%d",
                            VehiclePropertyIds.toString(propId), areaId);
                    continue;
                }

                for (CarPropertyServiceClient client : clients) {
                    List<CarPropertyEvent> eventsForClient = eventsToDispatch.get(client);
                    if (eventsForClient == null) {
                        eventsToDispatch.put(client, new ArrayList<CarPropertyEvent>());
                    }
                    eventsToDispatch.get(client).add(event);
                }
            }
        }

        // Parse the dispatch list to send events. We must call the callback outside the
        // scoped lock since the callback might call some function in CarPropertyService
        // which might cause deadlock.
        // In rare cases, if this specific client is unregistered after the lock but before
        // the callback, we would call callback on an unregistered client which should be ok because
        // 'onEvent' is an async oneway callback that might be delivered after unregistration
        // anyway.
        for (CarPropertyServiceClient client : eventsToDispatch.keySet()) {
            try {
                client.onEvent(eventsToDispatch.get(client));
            } catch (RemoteException ex) {
                // If we cannot send a record, it's likely the connection snapped. Let binder
                // death handle the situation.
                Slogf.e(TAG, "onEvent calling failed: " + ex);
            }
        }
    }

    @Override
    public void onPropertySetError(int property, int areaId, int errorCode) {
        CarPropertyServiceClient lastOperatedClient = null;
        synchronized (mLock) {
            if (mSetOpClientByAreaIdByPropId.get(property) != null
                    && mSetOpClientByAreaIdByPropId.get(property).get(areaId) != null) {
                lastOperatedClient = mSetOpClientByAreaIdByPropId.get(property).get(areaId);
            } else {
                Slogf.e(TAG, "Can not find the client changed propertyId: 0x"
                        + toHexString(property) + " in areaId: 0x" + toHexString(areaId));
            }

        }
        if (lastOperatedClient != null) {
            try {
                List<CarPropertyEvent> eventList = new ArrayList<>();
                eventList.add(
                        CarPropertyEvent.createErrorEventWithErrorCode(property, areaId,
                                errorCode));
                // We want all the error events to be delivered to this client with no filtering.
                lastOperatedClient.onFilteredEvents(eventList);
            } catch (RemoteException ex) {
                Slogf.e(TAG, "onFilteredEvents calling failed: " + ex);
            }
        }
    }

    private static void validateGetSetAsyncParameters(AsyncPropertyServiceRequestList requests,
            IAsyncPropertyResultCallback asyncPropertyResultCallback,
            long timeoutInMs) throws IllegalArgumentException {
        requireNonNull(requests);
        requireNonNull(asyncPropertyResultCallback);
        Preconditions.checkArgument(timeoutInMs > 0, "timeoutInMs must be a positive number");
    }

    /**
     * Gets CarPropertyValues asynchronously.
     */
    @Override
    public void getPropertiesAsync(
            AsyncPropertyServiceRequestList getPropertyServiceRequestsParcelable,
            IAsyncPropertyResultCallback asyncPropertyResultCallback, long timeoutInMs) {
        validateGetSetAsyncParameters(getPropertyServiceRequestsParcelable,
                asyncPropertyResultCallback, timeoutInMs);
        long currentTime = System.currentTimeMillis();
        List<AsyncPropertyServiceRequest> getPropertyServiceRequests =
                getPropertyServiceRequestsParcelable.getList();
        for (int i = 0; i < getPropertyServiceRequests.size(); i++) {
            validateGetParameters(getPropertyServiceRequests.get(i).getPropertyId(),
                    getPropertyServiceRequests.get(i).getAreaId());
        }
        mPropertyHalService.getCarPropertyValuesAsync(getPropertyServiceRequests,
                asyncPropertyResultCallback, timeoutInMs, currentTime);
        if (DBG) {
            Slogf.d(TAG, "Latency of getPropertyAsync is: %f", (float) (System
                    .currentTimeMillis() - currentTime));
        }
        sGetAsyncLatencyHistogram.logSample((float) (System.currentTimeMillis() - currentTime));
    }

    /**
     * Sets CarPropertyValues asynchronously.
     */
    @SuppressWarnings("FormatString")
    @Override
    public void setPropertiesAsync(AsyncPropertyServiceRequestList setPropertyServiceRequests,
            IAsyncPropertyResultCallback asyncPropertyResultCallback,
            long timeoutInMs) {
        validateGetSetAsyncParameters(setPropertyServiceRequests, asyncPropertyResultCallback,
                timeoutInMs);
        long currentTime = System.currentTimeMillis();
        List<AsyncPropertyServiceRequest> setPropertyServiceRequestList =
                setPropertyServiceRequests.getList();
        for (int i = 0; i < setPropertyServiceRequestList.size(); i++) {
            AsyncPropertyServiceRequest request = setPropertyServiceRequestList.get(i);
            CarPropertyValue carPropertyValueToSet = request.getCarPropertyValue();
            int propertyId = request.getPropertyId();
            int valuePropertyId = carPropertyValueToSet.getPropertyId();
            int areaId = request.getAreaId();
            int valueAreaId = carPropertyValueToSet.getAreaId();
            String propertyName = VehiclePropertyIds.toString(propertyId);
            if (valuePropertyId != propertyId) {
                throw new IllegalArgumentException(String.format(
                        "Property ID in request and CarPropertyValue mismatch: %s vs %s",
                        VehiclePropertyIds.toString(valuePropertyId), propertyName).toString());
            }
            if (valueAreaId != areaId) {
                throw new IllegalArgumentException(String.format(
                        "For property: %s, area ID in request and CarPropertyValue mismatch: %d vs"
                        + " %d", propertyName, valueAreaId, areaId).toString());
            }
            validateSetParameters(carPropertyValueToSet);
            if (request.isWaitForPropertyUpdate()) {
                if (NOT_ALLOWED_WAIT_FOR_UPDATE_PROPERTIES.contains(propertyId)) {
                    throw new IllegalArgumentException("Property: "
                            + propertyName + " must set waitForPropertyUpdate to false");
                }
                validateGetParameters(propertyId, areaId);
            }
        }
        mPropertyHalService.setCarPropertyValuesAsync(setPropertyServiceRequestList,
                asyncPropertyResultCallback, timeoutInMs, currentTime);
        if (DBG) {
            Slogf.d(TAG, "Latency of setPropertyAsync is: %f", (float) (System
                    .currentTimeMillis() - currentTime));
        }
        sSetAsyncLatencyHistogram.logSample((float) (System.currentTimeMillis() - currentTime));
    }

    @Override
    public int[] getSupportedNoReadPermPropIds(int[] propertyIds) {
        List<Integer> noReadPermPropertyIds = new ArrayList<>();
        for (int propertyId : propertyIds) {
            if (getCarPropertyConfig(propertyId) == null) {
                // Not supported
                continue;
            }
            if (!mPropertyHalService.isReadable(mContext, propertyId)) {
                noReadPermPropertyIds.add(propertyId);
            }
        }
        return ArrayUtils.convertToIntArray(noReadPermPropertyIds);
    }

    @Override
    public boolean isSupportedAndHasWritePermissionOnly(int propertyId) {
        return getCarPropertyConfig(propertyId) != null
                && mPropertyHalService.isWritable(mContext, propertyId)
                && !mPropertyHalService.isReadable(mContext, propertyId);
    }

    /**
     * Cancel on-going async requests.
     *
     * @param serviceRequestIds A list of async get/set property request IDs.
     */
    @Override
    public void cancelRequests(int[] serviceRequestIds) {
        mPropertyHalService.cancelRequests(serviceRequestIds);
    }

    private static void assertPropertyIsReadable(CarPropertyConfig<?> carPropertyConfig,
            int areaId) {
        int accessLevel = Flags.areaIdConfigAccess()
                ? carPropertyConfig.getAreaIdConfig(areaId).getAccess()
                : carPropertyConfig.getAccess();
        Preconditions.checkArgument(
                accessLevel == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ
                        || accessLevel == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                "Property: %s is not readable at areaId: %d",
                VehiclePropertyIds.toString(carPropertyConfig.getPropertyId()), areaId);
    }

    private static void assertAreaIdIsSupported(CarPropertyConfig<?> carPropertyConfig,
            int areaId) {
        Preconditions.checkArgument(ArrayUtils.contains(carPropertyConfig.getAreaIds(), areaId),
                "area ID: 0x" + toHexString(areaId) + " not supported for property ID: "
                        + VehiclePropertyIds.toString(carPropertyConfig.getPropertyId()));
    }

    @Nullable
    private CarPropertyConfig<?> getCarPropertyConfig(int propertyId) {
        CarPropertyConfig<?> carPropertyConfig;
        synchronized (mLock) {
            carPropertyConfig = mPropertyIdToCarPropertyConfig.get(propertyId);
        }
        return carPropertyConfig;
    }

    private void assertReadPermissionGranted(int propertyId) {
        if (!mPropertyHalService.isReadable(mContext, propertyId)) {
            throw new SecurityException(
                    "Platform does not have permission to read value for property ID: "
                            + VehiclePropertyIds.toString(propertyId));
        }
    }

    private CarPropertyConfig assertConfigNotNullAndGetConfig(int propertyId) {
        CarPropertyConfig<?> carPropertyConfig = getCarPropertyConfig(propertyId);
        Preconditions.checkArgument(carPropertyConfig != null,
                "property ID is not in carPropertyConfig list, and so it is not supported: %s",
                VehiclePropertyIds.toString(propertyId));
        return carPropertyConfig;
    }

    private void assertIfReadableAtAreaIds(CarPropertyConfig<?> carPropertyConfig, int[] areaIds) {
        for (int i = 0; i < areaIds.length; i++) {
            assertAreaIdIsSupported(carPropertyConfig, areaIds[i]);
            assertPropertyIsReadable(carPropertyConfig, areaIds[i]);
        }
        assertReadPermissionGranted(carPropertyConfig.getPropertyId());
    }

    private CarPropertyConfig validateRegisterParameterAndGetConfig(int propertyId,
            int[] areaIds) {
        CarPropertyConfig<?> carPropertyConfig = assertConfigNotNullAndGetConfig(propertyId);
        Preconditions.checkArgument(areaIds != null, "AreaIds must not be null");
        Preconditions.checkArgument(areaIds.length != 0, "AreaIds must not be empty");
        assertIfReadableAtAreaIds(carPropertyConfig, areaIds);
        return carPropertyConfig;
    }

    private void validateGetParameters(int propertyId, int areaId) {
        CarPropertyConfig<?> carPropertyConfig = assertConfigNotNullAndGetConfig(propertyId);
        assertAreaIdIsSupported(carPropertyConfig, areaId);
        assertPropertyIsReadable(carPropertyConfig, areaId);
        assertReadPermissionGranted(propertyId);
    }

    private void validateSetParameters(CarPropertyValue<?> carPropertyValue) {
        requireNonNull(carPropertyValue);
        int propertyId = carPropertyValue.getPropertyId();
        int areaId = carPropertyValue.getAreaId();
        Object valueToSet = carPropertyValue.getValue();
        CarPropertyConfig<?> carPropertyConfig = assertConfigNotNullAndGetConfig(propertyId);
        assertAreaIdIsSupported(carPropertyConfig, areaId);

        // Assert property is writable.
        int accessLevel = Flags.areaIdConfigAccess()
                ? carPropertyConfig.getAreaIdConfig(areaId).getAccess()
                : carPropertyConfig.getAccess();
        Preconditions.checkArgument(
                accessLevel == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE
                        || accessLevel == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                "Property: %s is not writable at areaId: %d",
                VehiclePropertyIds.toString(carPropertyConfig.getPropertyId()), areaId);

        // Assert write permission is granted.
        if (!mPropertyHalService.isWritable(mContext, propertyId)) {
            throw new SecurityException(
                    "Platform does not have permission to write value for property ID: "
                            + VehiclePropertyIds.toString(propertyId));
        }

        // Assert set value is valid for property.
        Preconditions.checkArgument(valueToSet != null,
                "setProperty: CarPropertyValue's must not be null - property ID: %s area ID: %s",
                VehiclePropertyIds.toString(carPropertyConfig.getPropertyId()),
                toHexString(areaId));
        Preconditions.checkArgument(
                valueToSet.getClass().equals(carPropertyConfig.getPropertyType()),
                "setProperty: CarPropertyValue's value's type does not match property's type. - "
                        + "property ID: %s area ID: %s",
                VehiclePropertyIds.toString(carPropertyConfig.getPropertyId()),
                toHexString(areaId));

        AreaIdConfig<?> areaIdConfig = carPropertyConfig.getAreaIdConfig(areaId);
        if (areaIdConfig.getMinValue() != null) {
            boolean isGreaterThanOrEqualToMinValue = false;
            if (carPropertyConfig.getPropertyType().equals(Integer.class)) {
                isGreaterThanOrEqualToMinValue =
                        (Integer) valueToSet >= (Integer) areaIdConfig.getMinValue();
            } else if (carPropertyConfig.getPropertyType().equals(Long.class)) {
                isGreaterThanOrEqualToMinValue =
                        (Long) valueToSet >= (Long) areaIdConfig.getMinValue();
            } else if (carPropertyConfig.getPropertyType().equals(Float.class)) {
                isGreaterThanOrEqualToMinValue =
                        (Float) valueToSet >= (Float) areaIdConfig.getMinValue();
            }
            Preconditions.checkArgument(isGreaterThanOrEqualToMinValue,
                    "setProperty: value to set must be greater than or equal to the area ID min "
                            + "value. - " + "property ID: %s area ID: 0x%s min value: %s",
                    VehiclePropertyIds.toString(carPropertyConfig.getPropertyId()),
                    toHexString(areaId), areaIdConfig.getMinValue());

        }

        if (areaIdConfig.getMaxValue() != null) {
            boolean isLessThanOrEqualToMaxValue = false;
            if (carPropertyConfig.getPropertyType().equals(Integer.class)) {
                isLessThanOrEqualToMaxValue =
                        (Integer) valueToSet <= (Integer) areaIdConfig.getMaxValue();
            } else if (carPropertyConfig.getPropertyType().equals(Long.class)) {
                isLessThanOrEqualToMaxValue =
                        (Long) valueToSet <= (Long) areaIdConfig.getMaxValue();
            } else if (carPropertyConfig.getPropertyType().equals(Float.class)) {
                isLessThanOrEqualToMaxValue =
                        (Float) valueToSet <= (Float) areaIdConfig.getMaxValue();
            }
            Preconditions.checkArgument(isLessThanOrEqualToMaxValue,
                    "setProperty: value to set must be less than or equal to the area ID max "
                            + "value. - " + "property ID: %s area ID: 0x%s min value: %s",
                    VehiclePropertyIds.toString(carPropertyConfig.getPropertyId()),
                    toHexString(areaId), areaIdConfig.getMaxValue());

        }

        if (!areaIdConfig.getSupportedEnumValues().isEmpty()) {
            Preconditions.checkArgument(areaIdConfig.getSupportedEnumValues().contains(valueToSet),
                    "setProperty: value to set must exist in set of supported enum values. - "
                            + "property ID: %s area ID: 0x%s supported enum values: %s",
                    VehiclePropertyIds.toString(carPropertyConfig.getPropertyId()),
                    toHexString(areaId), areaIdConfig.getSupportedEnumValues());
        }

        if (PROPERTY_ID_TO_UNWRITABLE_STATES.contains(carPropertyConfig.getPropertyId())) {
            Preconditions.checkArgument(!(PROPERTY_ID_TO_UNWRITABLE_STATES
                    .get(carPropertyConfig.getPropertyId()).contains(valueToSet)),
                    "setProperty: value to set: %s must not be an unwritable state value. - "
                            + "property ID: %s area ID: 0x%s unwritable states: %s",
                    valueToSet,
                    VehiclePropertyIds.toString(carPropertyConfig.getPropertyId()),
                    toHexString(areaId),
                    PROPERTY_ID_TO_UNWRITABLE_STATES.get(carPropertyConfig.getPropertyId()));
        }
    }
}
