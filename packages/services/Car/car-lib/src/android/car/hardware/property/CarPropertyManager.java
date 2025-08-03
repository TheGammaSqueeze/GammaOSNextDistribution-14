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

package android.car.hardware.property;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;
import static com.android.car.internal.property.CarPropertyErrorCodes.STATUS_OK;
import static com.android.car.internal.property.CarPropertyErrorCodes.STATUS_TRY_AGAIN;
import static com.android.car.internal.property.CarPropertyHelper.SYNC_OP_LIMIT_TRY_AGAIN;

import static java.lang.Integer.toHexString;
import static java.util.Objects.requireNonNull;

import android.annotation.CallbackExecutor;
import android.annotation.FlaggedApi;
import android.annotation.FloatRange;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.car.Car;
import android.car.CarManagerBase;
import android.car.VehiclePropertyIds;
import android.car.feature.FeatureFlags;
import android.car.feature.FeatureFlagsImpl;
import android.car.feature.Flags;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.os.Binder;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.ICarBase;
import com.android.car.internal.SingleMessageHandler;
import com.android.car.internal.dep.Trace;
import com.android.car.internal.os.HandlerExecutor;
import com.android.car.internal.property.AsyncPropertyServiceRequest;
import com.android.car.internal.property.AsyncPropertyServiceRequestList;
import com.android.car.internal.property.CarPropertyErrorCodes;
import com.android.car.internal.property.CarPropertyEventCallbackController;
import com.android.car.internal.property.CarPropertyHelper;
import com.android.car.internal.property.CarSubscription;
import com.android.car.internal.property.GetPropertyConfigListResult;
import com.android.car.internal.property.GetSetValueResult;
import com.android.car.internal.property.GetSetValueResultList;
import com.android.car.internal.property.IAsyncPropertyResultCallback;
import com.android.car.internal.property.InputSanitizationUtils;
import com.android.car.internal.property.SubscriptionManager;
import com.android.car.internal.util.IntArray;
import com.android.car.internal.util.PairSparseArray;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides an application interface for interacting with the Vehicle specific properties.
 * For details about the individual properties, see the descriptions in {@link VehiclePropertyIds}
 */
public class CarPropertyManager extends CarManagerBase {
    private static final String TAG = "CarPropertyManager";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    private static final int MSG_GENERIC_EVENT = 0;
    private static final int SYNC_OP_RETRY_SLEEP_IN_MS = 10;
    private static final int SYNC_OP_RETRY_MAX_COUNT = 10;
    // The default update rate used when subscribePropertyEvents does not contain updateRateHz as
    // an argument.
    private static final float DEFAULT_UPDATE_RATE_HZ = 1f;

    /**
     * The default timeout in MS for {@link CarPropertyManager#getPropertiesAsync}.
     */
    public static final long ASYNC_GET_DEFAULT_TIMEOUT_MS = 10_000;

    private final SingleMessageHandler<CarPropertyEvent> mHandler;
    private final ICarProperty mService;
    private final int mAppTargetSdk;
    private final Executor mExecutor;
    private final AtomicInteger mRequestIdCounter = new AtomicInteger(0);
    @GuardedBy("mLock")
    private final SparseArray<AsyncPropertyRequestInfo<?, ?>> mRequestIdToAsyncRequestInfo =
            new SparseArray<>();
    private final AsyncPropertyResultCallback mAsyncPropertyResultCallback =
            new AsyncPropertyResultCallback();

    private final CarPropertyEventListenerToService mCarPropertyEventToService =
            new CarPropertyEventListenerToService(this);

    // This lock is shared with all CarPropertyEventCallbackController instances to prevent
    // potential deadlock.
    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final Map<CarPropertyEventCallback, CarPropertyEventCallbackController>
            mCpeCallbackToCpeCallbackController = new ArrayMap<>();
    @GuardedBy("mLock")
    private final SparseArray<ArraySet<CarPropertyEventCallbackController>>
            mPropIdToCpeCallbackControllerList = new SparseArray<>();

    private final GetPropertyResultCallback mGetPropertyResultCallback =
            new GetPropertyResultCallback();

    private final SetPropertyResultCallback mSetPropertyResultCallback =
            new SetPropertyResultCallback();
    @GuardedBy("mLock")
    private final SubscriptionManager<CarPropertyEventCallback> mSubscriptionManager =
            new SubscriptionManager<>();

    private FeatureFlags mFeatureFlags = new FeatureFlagsImpl();

    /**
     * Sets fake feature flag for unit testing.
     *
     * @hide
     */
    @VisibleForTesting
    public void setFeatureFlags(FeatureFlags fakeFeatureFlags) {
        mFeatureFlags = fakeFeatureFlags;
    }

    /**
     * Application registers {@link CarPropertyEventCallback} object to receive updates and changes
     * to subscribed Vehicle specific properties.
     */
    public interface CarPropertyEventCallback {
        /**
         * Called when a property is updated.
         *
         * <p>For an on-change property or a continuous property with Variable Update Rate enabled
         * (by default), this is called when a property's value or status changes.
         *
         * <p>For a continuous property with VUR disabled, this is called periodically based on
         * the update rate.
         *
         * <p>This will also be called once to deliver the initial value (or a value with
         * unavailable or error status) for every new subscription.
         *
         * @param value the new value of the property
         */
        void onChangeEvent(CarPropertyValue value);

        /**
         * Called when an error happens for a recent {@link CarPropertyManager#setProperty}.
         *
         * @param propertyId the property ID which has detected an error
         * @param areaId the area ID which has detected an error
         *
         * <p>Client is recommended to override
         * {@link CarPropertyEventCallback#onErrorEvent(int, int, int)} and override this as no-op.
         *
         * <p>For legacy clients, {@link CarPropertyEventCallback#onErrorEvent(int, int, int)}
         * should use the default implementation, which will internally call this.
         *
         * @see CarPropertyEventCallback#onErrorEvent(int, int, int)
         */
        void onErrorEvent(int propertyId, int areaId);

        /**
         * Called when an error happens for a recent {@link CarPropertyManager#setProperty}.
         *
         * <p>Note that {@link CarPropertyManager#setPropertiesAsync} will not trigger this. In the
         * case of failure, {@link CarPropertyManager.SetPropertyCallback#onFailure} will be called.
         *
         * <p>Clients which changed the property value in the areaId most recently will receive
         * this callback. If multiple clients set a property for the same area ID simultaneously,
         * which one takes precedence is undefined. Typically, the last set operation
         * (in the order that they are issued to car's ECU) overrides the previous set operations.
         * The delivered error reflects the error happened in the last set operation.
         *
         * <p>If clients override this, implementation does not have to call
         * {@link CarPropertyEventCallback#onErrorEvent(int, int)} inside this function.
         * {@link CarPropertyEventCallback#onErrorEvent(int, int)} should be overridden as no-op.
         *
         * @param propertyId the property ID which is detected an error
         * @param areaId the area ID which is detected an error
         * @param errorCode the error code is raised in the car
         */
        default void onErrorEvent(int propertyId, int areaId,
                @CarSetPropertyErrorCode int errorCode) {
            if (DBG) {
                Log.d(TAG, "onErrorEvent propertyId: " + VehiclePropertyIds.toString(propertyId)
                        + " areaId: 0x" + toHexString(areaId) + " ErrorCode: " + errorCode);
            }
            onErrorEvent(propertyId, areaId);
        }
    }

    /**
     * A callback {@link CarPropertyManager#getPropertiesAsync} when succeeded or failed.
     */
    public interface GetPropertyCallback {
        /**
         * Method called when {@link GetPropertyRequest} successfully gets a result.
         */
        void onSuccess(@NonNull GetPropertyResult<?> getPropertyResult);

        /**
         * Method called when {@link GetPropertyRequest} returns an error.
         */
        void onFailure(@NonNull PropertyAsyncError propertyAsyncError);
    }

    /**
     * A callback {@link CarPropertyManager#setPropertiesAsync} when succeeded or failed.
     */
    public interface SetPropertyCallback {
        /**
         * Method called when the {@link SetPropertyRequest} successfully set the value.
         *
         * <p>This means: the set value request is successfully sent to vehicle
         *
         * <p>and
         *
         * <p>either the current property value is already the target value, or we have received a
         * property update event indicating the value is updated to the target value.
         *
         * <p>If multiple clients set a property for the same area ID simultaneously with different
         * values, the order is undefined. One possible case is that both requests are sent to the
         * vehicle bus, which causes two property update events. As a result, the success callback
         * would be called for both clients, but in an undefined order. This means that even if
         * the success callback is called, it doesn't necessarily mean getting the property would
         * return the same value you just set. Another client might have changed the value after you
         * set it.
         *
         * <p>If only one requests is successfully processed by the vehicle bus, overwriting the
         * other request, then only one success callback would be called for one client. The other
         * client would get the failure callback with
         * {@link CarPropertyManager#STATUS_ERROR_TIMEOUT} error code.
         *
         * <p>If multiple clients set a property for the same area ID simultaneously with the same
         * value. The success callback for both clients would be called in an undefined order.
         */
        void onSuccess(@NonNull SetPropertyResult setPropertyResult);

        /**
         * Method called when {@link SetPropertyRequest} returns an error.
         */
        void onFailure(@NonNull PropertyAsyncError propertyAsyncError);
    }

    /**
     * An async get/set property request.
     */
    public interface AsyncPropertyRequest {
        /**
         * Returns the unique ID for this request.
         *
         * <p>Each request must have a unique request ID so the responses can be differentiated.
         */
        int getRequestId();

        /**
         * Returns the ID for the property of this request.
         *
         * <p>The ID must be one of the {@link VehiclePropertyIds} or vendor property IDs.
         */
        int getPropertyId();

        /**
         * Returns the area ID for the property of this request.
         */
        int getAreaId();
    }

    /**
     * A request for {@link CarPropertyManager#getPropertiesAsync(List, long, CancellationSignal,
     * Executor, GetPropertyCallback)}.
     */
    public static final class GetPropertyRequest implements AsyncPropertyRequest {
        private final int mRequestId;
        private final int mPropertyId;
        private final int mAreaId;

        /**
         * @see AsyncPropertyRequest#getRequestId
         */
        @Override
        public int getRequestId() {
            return mRequestId;
        }

        /**
         * @see AsyncPropertyRequest#getPropertyId
         */
        @Override
        public int getPropertyId() {
            return mPropertyId;
        }

        /**
         * @see AsyncPropertyRequest#getAreaId
         */
        @Override
        public int getAreaId() {
            return mAreaId;
        }

        /**
         * Internal use only. Users should use {@link #generateGetPropertyRequest(int, int)}
         * instead.
         */
        private GetPropertyRequest(int requestId, int propertyId, int areaId) {
            mRequestId = requestId;
            mPropertyId = propertyId;
            mAreaId = areaId;
        }

        /**
         * Prints out debug message.
         */
        @Override
        @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
        public String toString() {
            return new StringBuilder()
                    .append("GetPropertyRequest{request ID: ")
                    .append(mRequestId)
                    .append(", property ID: ")
                    .append(VehiclePropertyIds.toString(mPropertyId))
                    .append(", area ID: ")
                    .append(mAreaId)
                    .append("}").toString();
        }
    }

    /**
     * A request for {@link CarPropertyManager#setPropertiesAsync(List, long, CancellationSignal,
     * Executor, SetPropertyCallback)}.
     *
     * @param <T> the type for the property value, must be one of Object, Boolean, Float, Integer,
     *      Long, Float[], Integer[], Long[], String, byte[], Object[]
     */
    public static final class SetPropertyRequest<T> implements AsyncPropertyRequest {
        private final int mRequestId;
        private final int mPropertyId;
        private final int mAreaId;
        private float mUpdateRateHz = 0.f;
        // By default, the async set operation will wait for the property to be updated to the
        // target value before calling the success callback (or when the target value is the
        // same as the current value).
        private boolean mWaitForPropertyUpdate = true;

        /**
         * The value to set.
         */
        private final T mValue;

        /**
         * Sets the update rate in Hz for listening for property updates for continuous property.
         *
         * <p>If {@code waitForPropertyUpdate} is set to {@code true} (by default) and if the
         * property is set to a different value than its current value, the success callback will be
         * called when a property update event for the new value arrived. This option controls how
         * frequent the property update event should be reported for continuous property. This is
         * similar to {@code updateRateHz} in {@link CarPropertyManager#registerCallback}.
         *
         * <p>This is ignored for non-continuous properties.
         *
         * <p>This is ignored if {@code waitForPropertyUpdate} is set to {@code false}.
         */
        public void setUpdateRateHz(float updateRateHz) {
            mUpdateRateHz = updateRateHz;
        }

        /**
         * Sets whether to wait for the property update event before calling success callback.
         *
         * <p>This arguments controls under what conditions the operation is considered succeeded
         * and the success callback will be called.
         *
         * <p>If this is set to {@code true} (by default), the success callback will be called when
         * both of the following coniditions are met:
         *
         * <ul>
         * <li>the set operation is successfully delivered to vehicle bus.
         * <li>the {@code mPropertyId}+{@code mAreaId}'s value already equal to {@code mValue} or
         * is successfully updated to the {@code mValue} through the set operation.
         * </ul>
         *
         * <p>Even if the target value is the same as the current value, we will still send the set
         * operation to the vehicle bus. If caller wants to reduce unnecessary overhead, caller must
         * check existing values before issuing the requests.
         *
         * <p>If the first condition fails, the error callback will be called. If the second
         * condition fails, which means we don't see the property updated to the target value within
         * a specified timeout, the error callback will be called with {@link
         * #STATUS_ERROR_TIMEOUT}.
         *
         * <p>If this is set to {@code false}, the success callback will be called after the
         * set operation is successfully delivered to vehicle bus.
         *
         * <p>Under most cases, client should wait for the property update to verify that the set
         * operation actually succeeded.
         *
         * <p>For cases when the property is write-only (no way to get property update event) or
         * when the property represents some action, instead of an actual state, e.g. key stroke
         * where the property's current value is not meaningful, caller should set
         * {@code waitForPropertyUpdate} to {@code false}.
         *
         * <p>For {@code HVAC_TEMPERATURE_VALUE_SUGGESTION}, this must be set to {@code false}
         * because the updated property value will not be the same as the value to be set.
         *
         * <p>Note that even if this is set to {@code true}, it is only guaranteed that the property
         * value is the target value after the success callback is called if no other clients are
         * changing the property at the same time. It is always possible that another client changes
         * the property value after the property is updated to the target value, but before the
         * client success callback runs. We only guarantee that at some point during the period
         * after the client issues the request and before the success callback is called, the
         * property value was set to the target value.
         */
        public void setWaitForPropertyUpdate(boolean waitForPropertyUpdate) {
            mWaitForPropertyUpdate = waitForPropertyUpdate;
        }

        /**
         * @see AsyncPropertyRequest#getRequestId
         */
        @Override
        public int getRequestId() {
            return mRequestId;
        }

        /**
         * @see AsyncPropertyRequest#getPropertyId
         */
        @Override
        public int getPropertyId() {
            return mPropertyId;
        }

        /**
         * @see AsyncPropertyRequest#getAreaId
         */
        @Override
        public int getAreaId() {
            return mAreaId;
        }

        /**
         * Gets the property value to set.
         */
        public T getValue() {
            return mValue;
        }

        /**
         * Gets the update rate for listening for property updates.
         */
        public float getUpdateRateHz() {
            return mUpdateRateHz;
        }

        /**
         * Gets whether to wait for property update event before calling success callback.
         */
        public boolean isWaitForPropertyUpdate() {
            return mWaitForPropertyUpdate;
        }

        /**
         * Internal use only. Users should use {@link #generateSetPropertyRequest(int, int, T)}
         * instead.
         */
        private SetPropertyRequest(int requestId, int propertyId, int areaId, T value) {
            mRequestId = requestId;
            mPropertyId = propertyId;
            mAreaId = areaId;
            mValue = value;
        }

        /**
         * Prints out debug message.
         */
        @Override
        @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
        public String toString() {
            return new StringBuilder()
                    .append("SetPropertyRequest{request ID: ")
                    .append(mRequestId)
                    .append(", property ID: ")
                    .append(VehiclePropertyIds.toString(mPropertyId))
                    .append(", area ID: ")
                    .append(mAreaId)
                    .append(", value: ")
                    .append(mValue)
                    .append(", waitForPropertyUpdate: ")
                    .append(mWaitForPropertyUpdate)
                    .append(", mUpdateRateHz: ")
                    .append(mUpdateRateHz)
                    .append("}").toString();
        }
    }

    /**
     * An error result for {@link GetPropertyCallback} or {@link SetPropertyCallback}.
     */
    public static final class PropertyAsyncError {
        private final int mRequestId;
        private final int mPropertyId;
        private final int mAreaId;
        private final CarPropertyErrorCodes mCarPropertyErrorCodes;

        public int getRequestId() {
            return mRequestId;
        }

        public int getPropertyId() {
            return mPropertyId;
        }

        public int getAreaId() {
            return mAreaId;
        }

        public @CarPropertyAsyncErrorCode int getErrorCode() {
            return getCarPropertyAsyncErrorCodeFromCarPropertyManagerErrorCode(
                    mCarPropertyErrorCodes.getCarPropertyManagerErrorCode());
        }

        /**
         * Gets the vendor error codes to allow for more detailed error codes.
         *
         * @return the vendor error code if it is set, otherwise 0. A vendor error code will have a
         * range from 0x0000 to 0xffff.
         *
         * @hide
         */
        @SystemApi
        public int getVendorErrorCode() {
            return mCarPropertyErrorCodes.getVendorErrorCode();
        }

        /**
         * Gets the detailed system error code.
         *
         * These must be a value defined in
         * {@link DetailedErrorCode}. The values in {@link DetailedErrorCode}
         * may be extended in the future to include additional error codes.
         *
         * @return the detailed error code if it is set, otherwise set to 0.
         */
        @FlaggedApi(Flags.FLAG_CAR_PROPERTY_DETAILED_ERROR_CODES)
        public int getDetailedErrorCode() {
            return getDetailedErrorCodeFromSystemErrorCode(
                    mCarPropertyErrorCodes.getSystemErrorCode());
        }

        /**
         * Creates a new error result for async property request.
         *
         * @param requestId the request ID
         * @param propertyId the property ID in the request
         * @param areaId the area ID for the property in the request
         * @param carPropertyErrorCodes the codes indicating the error
         */
        PropertyAsyncError(int requestId, int propertyId, int areaId,
                CarPropertyErrorCodes carPropertyErrorCodes) {
            mRequestId = requestId;
            mPropertyId = propertyId;
            mAreaId = areaId;
            mCarPropertyErrorCodes = carPropertyErrorCodes;
        }

        /**
         * Prints out debug message.
         */
        @Override
        @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
        public String toString() {
            return new StringBuilder()
                    .append("PropertyAsyncError{request ID: ")
                    .append(mRequestId)
                    .append(", property: ")
                    .append(VehiclePropertyIds.toString(mPropertyId))
                    .append(", areaId: ")
                    .append(mAreaId)
                    .append(", error codes: ")
                    .append(mCarPropertyErrorCodes)
                    .append("}").toString();
        }
    }

    /**
     * A successful result for {@link GetPropertyCallback}.
     *
     * @param <T> the type for the property value, must be one of Object, Boolean, Float, Integer,
     *      Long, Float[], Integer[], Long[], String, byte[], Object[]
     */
    public static final class GetPropertyResult<T> {
        private final int mRequestId;
        private final int mPropertyId;
        private final int mAreaId;
        private final long mTimestampNanos;
        private final T mValue;

        /**
         * Returns the unique ID for the {@link GetPropertyRequest} this result is for.
         */
        public int getRequestId() {
            return mRequestId;
        }

        /**
         * Returns the property ID for this result.
         */
        public int getPropertyId() {
            return mPropertyId;
        }

        /**
         * Returns the area ID for this result.
         */
        public int getAreaId() {
            return mAreaId;
        }

        /**
         * Returns the property's value.
         */
        @NonNull
        public T getValue() {
            return mValue;
        }

        /**
         * Returns the timestamp in nanoseconds at which the value for the vehicle property
         * happened. For a given vehicle property, each new timestamp should be monotonically
         * increasing using the same time base as {@link SystemClock#elapsedRealtimeNanos()}.
         *
         * <p>NOTE: Timestamp should be synchronized with other signals from the platform (e.g.
         * {@link android.location.Location} and {@link android.hardware.SensorEvent} instances).
         * Ideally, timestamp synchronization error should be below 1 millisecond.
         */
        public long getTimestampNanos() {
            return mTimestampNanos;
        }

        /**
         * Creates a new value result for async GetProperty request.
         *
         * @param requestId the request ID
         * @param propertyId the property ID in the request
         * @param areaId the area ID for the property in the request
         * @param timestampNanos the timestamp in nanoseconds when this property is updated
         * @param value the property's value
         */
        GetPropertyResult(int requestId, int propertyId, int areaId, long timestampNanos,
                 @NonNull T value) {
            mRequestId = requestId;
            mPropertyId = propertyId;
            mAreaId = areaId;
            mTimestampNanos = timestampNanos;
            mValue = value;
        }

        /**
         * Prints out debug message.
         */
        @Override
        @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
        public String toString() {
            return new StringBuilder()
                    .append("GetPropertyResult{type: ")
                    .append(mValue.getClass())
                    .append(", request ID: ")
                    .append(mRequestId)
                    .append(", property: ")
                    .append(VehiclePropertyIds.toString(mPropertyId))
                    .append(", areaId: ")
                    .append(mAreaId)
                    .append(", value: ")
                    .append(mValue)
                    .append(", timestamp: ")
                    .append(mTimestampNanos).append("ns")
                    .append("}").toString();
        }
    }

    /**
     * A successful result for {@link SetPropertyCallback}.
     */
    public static final class SetPropertyResult {
        private final int mRequestId;
        private final int mPropertyId;
        private final int mAreaId;
        private final long mUpdateTimestampNanos;

        /**
         * Gets the ID for the request this result is for.
         */
        public int getRequestId() {
            return mRequestId;
        }

        /**
         * Gets the property ID this result is for.
         */
        public int getPropertyId() {
            return mPropertyId;
        }

        /**
         * Gets the area ID this result is for.
         */
        public int getAreaId() {
            return mAreaId;
        }

        /**
         * Gets the timestamp in nanoseconds at which the property was updated to the desired value.
         *
         * <p>The timestamp will use the same time base as
         * {@link SystemClock#elapsedRealtimeNanos()}.
         *
         * <p>NOTE: If {@code waitForPropertyUpdate} is set to {@code false} for the request, then
         * this value will be the time when the async set request is successfully sent to the
         * vehicle bus, not when the property is updated since we have no way of knowing that.
         *
         * <p>NOTE: Timestamp should be synchronized with other signals from the platform (e.g.
         * {@link android.location.Location} and {@link android.hardware.SensorEvent} instances).
         * Ideally, timestamp synchronization error should be below 1 millisecond.
         */
        public long getUpdateTimestampNanos() {
            return mUpdateTimestampNanos;
        }

        SetPropertyResult(int requestId, int propertyId, int areaId, long updateTimestampNanos) {
            mRequestId = requestId;
            mPropertyId = propertyId;
            mAreaId = areaId;
            mUpdateTimestampNanos = updateTimestampNanos;
        }

        /**
         * Prints out debug message.
         */
        @Override
        @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
        public String toString() {
            return new StringBuilder()
                    .append("SetPropertyResult{request ID: ")
                    .append(mRequestId)
                    .append(", property: ")
                    .append(VehiclePropertyIds.toString(mPropertyId))
                    .append(", areaId: ")
                    .append(mAreaId)
                    .append(", updated timestamp: ")
                    .append(mUpdateTimestampNanos).append("ns")
                    .append("}").toString();
        }
    }

    /**
     * An abstract interface for converting async get/set result and calling client callbacks.
     */
    private interface PropertyResultCallback<CallbackType, ResultType> {
        ResultType build(int requestId, int propertyId, int areaId, long timestampNanos,
                @Nullable Object value);
        void onSuccess(CallbackType callback, ResultType result);
        void onFailure(CallbackType callback, PropertyAsyncError error);
    }

    /**
     * Class to hide implementation detail for get/set callbacks.
     */
    private static final class GetPropertyResultCallback implements
            PropertyResultCallback<GetPropertyCallback, GetPropertyResult> {
        public GetPropertyResult build(int requestId, int propertyId, int areaId,
                long timestampNanos, @Nullable Object value) {
            return new GetPropertyResult(requestId, propertyId, areaId, timestampNanos, value);
        }

        public void onSuccess(GetPropertyCallback callback, GetPropertyResult result) {
            if (DBG) {
                Log.d(TAG, "delivering success get property result: " + result);
            }
            callback.onSuccess(result);
        }

        public void onFailure(GetPropertyCallback callback, PropertyAsyncError error) {
            if (DBG) {
                Log.d(TAG, "delivering error get property result: " + error);
            }
            callback.onFailure(error);
        }
    }

    /**
     * Class to hide implementation detail for get/set callbacks.
     */
    private static final class SetPropertyResultCallback implements
            PropertyResultCallback<SetPropertyCallback, SetPropertyResult> {
        public  SetPropertyResult build(int requestId, int propertyId, int areaId,
                long timestampNanos, @Nullable Object value) {
            return new SetPropertyResult(requestId, propertyId, areaId, timestampNanos);
        }

        public void onSuccess(SetPropertyCallback callback, SetPropertyResult result) {
            if (DBG) {
                Log.d(TAG, "delivering success set property result: " + result);
            }
            callback.onSuccess(result);
        }

        public void onFailure(SetPropertyCallback callback, PropertyAsyncError error) {
            if (DBG) {
                Log.d(TAG, "delivering error set property result: " + error);
            }
            callback.onFailure(error);
        }
    }

    /**
     * A class for delivering {@link GetPropertyCallback} or {@link SetPropertyCallback} client
     * callback when {@code IAsyncPropertyResultCallback} returns results.
     */
    private class AsyncPropertyResultCallback extends IAsyncPropertyResultCallback.Stub {

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        public void onGetValueResults(GetSetValueResultList getValueResults) {
            this.<GetPropertyRequest, GetPropertyCallback, GetPropertyResult>onResults(
                    getValueResults.getList(), mGetPropertyResultCallback);
        }

        @Override
        public void onSetValueResults(GetSetValueResultList setValueResults) {
            this.<SetPropertyRequest<?>, SetPropertyCallback, SetPropertyResult>onResults(
                    setValueResults.getList(), mSetPropertyResultCallback);
        }

        @SuppressLint("WrongConstant")
        private <RequestType extends AsyncPropertyRequest, CallbackType, ResultType> void onResults(
                List<GetSetValueResult> results,
                PropertyResultCallback<CallbackType, ResultType> propertyResultCallback) {
            for (int i = 0; i < results.size(); i++) {
                GetSetValueResult result = results.get(i);
                int requestId = result.getRequestId();
                AsyncPropertyRequestInfo<RequestType, CallbackType> requestInfo;
                synchronized (mLock) {
                    requestInfo =
                            (AsyncPropertyRequestInfo<RequestType, CallbackType>)
                            mRequestIdToAsyncRequestInfo.get(requestId);
                    mRequestIdToAsyncRequestInfo.remove(requestId);
                }
                if (requestInfo == null) {
                    Log.w(TAG, "onResults: Request ID: " + requestId
                            + " might have been completed, cancelled or an exception might have "
                            + "been thrown");
                    continue;
                }
                Executor callbackExecutor = requestInfo.getCallbackExecutor();
                CallbackType clientCallback = requestInfo.getCallback();
                @CarPropertyAsyncErrorCode int errorCode =
                        result.getCarPropertyErrorCodes().getCarPropertyManagerErrorCode();
                int propertyId = requestInfo.getRequest().getPropertyId();
                String propertyName = VehiclePropertyIds.toString(propertyId);
                int areaId = requestInfo.getRequest().getAreaId();
                if (errorCode == STATUS_OK) {
                    CarPropertyValue<?> carPropertyValue = result.getCarPropertyValue();
                    long timestampNanos;
                    if (carPropertyValue != null) {
                        // This is a get result.
                        int valuePropertyId = carPropertyValue.getPropertyId();
                        if (propertyId  != valuePropertyId) {
                            Log.e(TAG, "onResults: Request ID: " + requestId + " received get "
                                    + "property value result, but has mismatch property ID, "
                                    + " expect: " + propertyName + ", got: "
                                    + VehiclePropertyIds.toString(valuePropertyId));
                        }
                        int valueAreaId = carPropertyValue.getAreaId();
                        if (areaId  != valueAreaId) {
                            Log.e(TAG, "onResults: Property: " + propertyName + " Request ID: "
                                    + requestId + " received get property value result, but has "
                                    + "mismatch area ID, expect: " + areaId + ", got: "
                                    + valueAreaId);
                        }
                        timestampNanos = carPropertyValue.getTimestamp();
                    } else {
                        // This is a set result.
                        timestampNanos = result.getUpdateTimestampNanos();
                    }

                    ResultType clientResult = propertyResultCallback.build(
                            requestId, propertyId, areaId, timestampNanos,
                            carPropertyValue == null ? null : carPropertyValue.getValue());
                    Binder.clearCallingIdentity();
                    callbackExecutor.execute(() -> propertyResultCallback.onSuccess(
                            clientCallback, clientResult));
                } else {
                    Binder.clearCallingIdentity();
                    callbackExecutor.execute(() -> propertyResultCallback.onFailure(clientCallback,
                            new PropertyAsyncError(requestId, propertyId, areaId,
                                    result.getCarPropertyErrorCodes())));
                }
            }
        }
    }

    /**
     * A class to store async get/set property request info.
     */
    private static final class AsyncPropertyRequestInfo<RequestType, CallbackType> {
        private final RequestType mRequest;
        private final Executor mCallbackExecutor;
        private final CallbackType mCallback;

        public RequestType getRequest() {
            return mRequest;
        }

        public Executor getCallbackExecutor() {
            return mCallbackExecutor;
        }

        public CallbackType getCallback() {
            return mCallback;
        }

        private AsyncPropertyRequestInfo(RequestType request, Executor callbackExecutor,
                CallbackType callback) {
            mRequest = request;
            mCallbackExecutor = callbackExecutor;
            mCallback = callback;
        }
    }

    /** Read ONCHANGE sensors. */
    public static final float SENSOR_RATE_ONCHANGE = 0f;
    /** Read sensors at the rate of  1 hertz */
    public static final float SENSOR_RATE_NORMAL = 1f;
    /** Read sensors at the rate of 5 hertz */
    public static final float SENSOR_RATE_UI = 5f;
    /** Read sensors at the rate of 10 hertz */
    public static final float SENSOR_RATE_FAST = 10f;
    /** Read sensors at the rate of 100 hertz */
    public static final float SENSOR_RATE_FASTEST = 100f;



    /**
     * Status to indicate that set operation failed. Try it again.
     */
    public static final int CAR_SET_PROPERTY_ERROR_CODE_TRY_AGAIN = 1;

    /**
     * Status to indicate that set operation failed because of an invalid argument.
     */
    public static final int CAR_SET_PROPERTY_ERROR_CODE_INVALID_ARG = 2;

    /**
     * Status to indicate that set operation failed because the property is not available.
     */
    public static final int CAR_SET_PROPERTY_ERROR_CODE_PROPERTY_NOT_AVAILABLE = 3;

    /**
     * Status to indicate that set operation failed because car denied access to the property.
     */
    public static final int CAR_SET_PROPERTY_ERROR_CODE_ACCESS_DENIED = 4;

    /**
     * Status to indicate that set operation failed because of a general error in cars.
     */
    public static final int CAR_SET_PROPERTY_ERROR_CODE_UNKNOWN = 5;

    /** @hide */
    @IntDef(prefix = {"CAR_SET_PROPERTY_ERROR_CODE_"}, value = {
            CAR_SET_PROPERTY_ERROR_CODE_TRY_AGAIN,
            CAR_SET_PROPERTY_ERROR_CODE_INVALID_ARG,
            CAR_SET_PROPERTY_ERROR_CODE_PROPERTY_NOT_AVAILABLE,
            CAR_SET_PROPERTY_ERROR_CODE_ACCESS_DENIED,
            CAR_SET_PROPERTY_ERROR_CODE_UNKNOWN,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface CarSetPropertyErrorCode {}

    /**
     * Error indicating that there is an error detected in cars.
     */
    public static final int STATUS_ERROR_INTERNAL_ERROR = 1;
    /**
     * Error indicating that the property is temporarily not available.
     */
    public static final int STATUS_ERROR_NOT_AVAILABLE = 2;
    /**
     * Error indicating the operation has timed-out.
     */
    public static final int STATUS_ERROR_TIMEOUT = 3;

    /** @hide */
    @IntDef(prefix = {"STATUS_"}, value = {
            STATUS_OK,
            STATUS_ERROR_INTERNAL_ERROR,
            STATUS_ERROR_NOT_AVAILABLE,
            STATUS_ERROR_TIMEOUT
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface CarPropertyAsyncErrorCode {}

    /**
     * Get an instance of the CarPropertyManager.
     *
     * Should not be obtained directly by clients, use {@link Car#getCarManager(String)} instead.
     *
     * @param car the Car instance
     * @param service the ICarProperty instance
     * @hide
     */
    public CarPropertyManager(ICarBase car, @NonNull ICarProperty service) {
        super(car);
        mService = service;
        mAppTargetSdk = getContext().getApplicationInfo().targetSdkVersion;

        Handler eventHandler = getEventHandler();
        if (eventHandler == null) {
            mHandler = null;
            mExecutor = null;
            return;
        }
        mExecutor = new HandlerExecutor(getEventHandler());
        mHandler = new SingleMessageHandler<>(eventHandler.getLooper(), MSG_GENERIC_EVENT) {
            @Override
            protected void handleEvent(CarPropertyEvent carPropertyEvent) {
                int propertyId = carPropertyEvent.getCarPropertyValue().getPropertyId();
                List<CarPropertyEventCallbackController> cpeCallbacks = new ArrayList<>();
                synchronized (mLock) {
                    ArraySet<CarPropertyEventCallbackController> cpeCallbackControllerSet =
                            mPropIdToCpeCallbackControllerList.get(propertyId);
                    if (cpeCallbackControllerSet == null) {
                        Log.w(TAG, "handleEvent: could not find any callbacks for propertyId="
                                + VehiclePropertyIds.toString(propertyId));
                        return;
                    }
                    for (int i = 0; i < cpeCallbackControllerSet.size(); i++) {
                        cpeCallbacks.add(cpeCallbackControllerSet.valueAt(i));
                    }
                }

                for (int i = 0; i < cpeCallbacks.size(); i++) {
                    cpeCallbacks.get(i).onEvent(carPropertyEvent);
                }
            }
        };
    }

    /**
     * Registers {@link CarPropertyEventCallback} to get property updates.
     * Multiple callbacks can be registered for a single property or the same callback can be used
     * for different properties. If the same callback is registered again for the same property,
     * it will be updated to new {@code updateRateHz}.
     *
     * <p>Rate could be one of the following:
     * <ul>
     *   <li>{@link CarPropertyManager#SENSOR_RATE_ONCHANGE}</li>
     *   <li>{@link CarPropertyManager#SENSOR_RATE_NORMAL}</li>
     *   <li>{@link CarPropertyManager#SENSOR_RATE_UI}</li>
     *   <li>{@link CarPropertyManager#SENSOR_RATE_FAST}</li>
     *   <li>{@link CarPropertyManager#SENSOR_RATE_FASTEST}</li>
     * </ul>
     *
     * <p>
     * <b>Note:</b>Rate has no effect if the property has one of the following change modes:
     * <ul>
     *   <li>{@link CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}</li>
     *   <li>{@link CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}</li>
     * </ul>
     *
     * <p>
     * <b>Note:</b> When this function is called, the callback will receive the current
     * values for all the areaIds for the property through property change events if they are
     * currently okay for reading. If they are not available for reading or in error state,
     * property change events with a unavailable or error status will be generated.
     *
     * <p>For properties that might be unavailable for reading because their power state
     * is off, property change events containing the property's initial value will be generated
     * once their power state is on.
     *
     * <p>If {@code updateRateHz} is higher than {@link CarPropertyConfig#getMaxSampleRate()}, it
     * will be registered with max sample {@code updateRateHz}.
     *
     * <p>If {@code updateRateHz} is lower than {@link CarPropertyConfig#getMinSampleRate()}, it
     * will be registered with min sample {@code updateRateHz}.
     *
     * <p>
     * <b>Note:</b>Caller must check the value of {@link CarPropertyValue#getStatus()} for property
     * change events and only use {@link CarPropertyValue#getValue()} when
     * {@link CarPropertyValue#getStatus()} is {@link CarPropertyValue#STATUS_AVAILABLE}. If not,
     * the {@link CarPropertyValue#getValue()} is meaningless.
     *
     * <p>
     * <b>Note:</b>A property change event may/may not happen when the property's status
     * changes. Caller should not depend on the change event to check property's status. For
     * properties that might be unavailable because they depend on certain power state, caller
     * should subscribe to the power state property (e.g.
     * {@link VehiclePropertyIds#HVAC_POWER_ON} for hvac power dependent properties) to decide this
     * property's availability.
     *
     * <p>
     * If the registration failed, this will return {@code false}. Caller must check the return
     * value to make sure the registration succeeded.
     *
     * <p>
     * If the property is not supported, this will return {@code false}.
     *
     * <p>
     * If the property is supported and the caller does not have read or write permission to it,
     * this will return {@code false}.
     *
     * <p>
     * If the caller has write permission but does not have read permission, this will throw
     * {@code SecurityException}.
     *
     * <p>Note that the callback will be executed on the event handler provided to the
     * {@link android.car.Car} or the main thread if none was provided.
     *
     * @param carPropertyEventCallback the CarPropertyEventCallback to be registered
     * @param propertyId               the property ID to subscribe
     * @param updateRateHz             how fast the property events are delivered in Hz
     * @return {@code true} if the listener is successfully registered.
     * @throws SecurityException if the property is supported and the caller has write permission,
     *                           but does not have read permission.
     */
    @SuppressWarnings("FormatString")
    public boolean registerCallback(@NonNull CarPropertyEventCallback carPropertyEventCallback,
            int propertyId, @FloatRange(from = 0.0, to = 100.0) float updateRateHz) {
        if (DBG) {
            Log.d(TAG, String.format("registerCallback, callback: %s propertyId: %s, "
                            + "updateRateHz: %f", carPropertyEventCallback,
                    VehiclePropertyIds.toString(propertyId), updateRateHz));
        }

        boolean hasWritePermissionOnly = false;
        try {
            hasWritePermissionOnly = mService.isSupportedAndHasWritePermissionOnly(propertyId);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
            return false;
        }

        if (hasWritePermissionOnly) {
            throw new SecurityException(
                    "Only has write permission, missing read permission for property: "
                    + CarPropertyHelper.propertyIdsToString(List.of(propertyId)));
        }

        // We require updateRateHz to be within 0 and 100, however, in the previous implementation,
        // we did not actually check this range. In order to prevent the existing behavior, and
        // to prevent Subscription.Builder.setUpdateRateHz to throw exception, we fit the
        // input within the expected range.
        if (updateRateHz > 100.0f) {
            updateRateHz = 100.0f;
        }
        if (updateRateHz < 0.0f) {
            updateRateHz = 0.0f;
        }
        CarSubscription subscribeOption = new CarSubscription();
        subscribeOption.propertyId = propertyId;
        subscribeOption.updateRateHz = updateRateHz;
        // Make sure areaIds is not null.
        subscribeOption.areaIds = new int[0];
        try {
            return subscribePropertyEventsInternal(List.of(subscribeOption),
                    /* callbackExecutor= */ null, carPropertyEventCallback);
        } catch (IllegalArgumentException | SecurityException e) {
            Log.w(TAG, "register: PropertyId=" + propertyId + ", exception=", e);
            return false;
        }
    }

    /**
     * Subscribes to property events for all areaIds for the property.
     *
     * <p>For continuous property, variable update rate is enabled. The update rate is 1Hz or
     * the max supported rate (if lower than 1hz).
     *
     * <p>Note that the callback will be executed on the event handler provided to the
     * {@link android.car.Car} or the main thread if none was provided.
     *
     * @param propertyId The ID for the property to subscribe to.
     * @param carPropertyEventCallback The callback to deliver property update/error events.
     * @return {@code true} if the listener is successfully registered
     * @throws SecurityException if the caller does not have read permission to one of the supported
     *                           properties.
     * @throws IllegalArgumentException if there are over-lapping areaIds or the executor is
     *                                  registered to another callback or one of the properties are
     *                                  not supported.
     *
     * @see #subscribePropertyEvents(int, int, float, boolean, CarPropertyEventCallback) for more
     * options.
     */
    @FlaggedApi(Flags.FLAG_VARIABLE_UPDATE_RATE)
    public boolean subscribePropertyEvents(int propertyId,
            @NonNull CarPropertyEventCallback carPropertyEventCallback) {
        return subscribePropertyEvents(List.of(
                new Subscription.Builder(propertyId).setUpdateRateHz(DEFAULT_UPDATE_RATE_HZ)
                .build()), /* callbackExecutor= */ null, carPropertyEventCallback);
    }

    /**
     * Subscribes to property events for all areaIds for the property.
     *
     * <p>For continuous property, variable update rate is enabled.
     *
     * <p>Note that the callback will be executed on the event handler provided to the
     * {@link android.car.Car} or the main thread if none was provided.
     *
     * @param propertyId The ID for the property to subscribe to.
     * @param updateRateHz Only meaningful for continuous property. The update rate in Hz. A common
     *      value is 1Hz. See {@link Subscription.Builder#setUpdateRateHz} for detail.
     * @param carPropertyEventCallback The callback to deliver property update/error events.
     * @return {@code true} if the listener is successfully registered
     * @throws SecurityException if the caller does not have read permission to one of the supported
     *                           properties.
     * @throws IllegalArgumentException if there are over-lapping areaIds or the executor is
     *                                  registered to another callback or one of the properties are
     *                                  not supported.
     *
     * @see #subscribePropertyEvents(int, int, float, boolean, CarPropertyEventCallback) for more
     * options.
     */
    @FlaggedApi(Flags.FLAG_VARIABLE_UPDATE_RATE)
    public boolean subscribePropertyEvents(int propertyId,
            @FloatRange(from = 0.0, to = 100.0) float updateRateHz,
            @NonNull CarPropertyEventCallback carPropertyEventCallback) {
        return subscribePropertyEvents(List.of(
                new Subscription.Builder(propertyId).setUpdateRateHz(updateRateHz).build()),
                /* callbackExecutor= */ null, carPropertyEventCallback);
    }


    /**
     * Subscribes to property events for the specific area ID for the property.
     *
     * <p>For continuous property, variable update rate is enabled. The update rate is 1Hz or
     * the max supported rate (if lower than 1hz).
     *
     * <p>Note that the callback will be executed on the event handler provided to the
     * {@link android.car.Car} or the main thread if none was provided.
     *
     * @param propertyId The ID for the property to subscribe to.
     * @param areaId The ID for the area to subscribe to.
     * @param carPropertyEventCallback The callback to deliver property update/error events.
     * @return {@code true} if the listener is successfully registered
     * @throws SecurityException if the caller does not have read permission to one of the supported
     *                           properties.
     * @throws IllegalArgumentException if there are over-lapping areaIds or the executor is
     *                                  registered to another callback or one of the properties are
     *                                  not supported.
     *
     * @see #subscribePropertyEvents(int, int, float, boolean, CarPropertyEventCallback) for more
     * options.
     */
    @FlaggedApi(Flags.FLAG_VARIABLE_UPDATE_RATE)
    public boolean subscribePropertyEvents(int propertyId, int areaId,
            @NonNull CarPropertyEventCallback carPropertyEventCallback) {
        return subscribePropertyEvents(List.of(
                new Subscription.Builder(propertyId).addAreaId(areaId).setUpdateRateHz(1f)
                        .build()),
                /* callbackExecutor= */ null, carPropertyEventCallback);
    }

    /**
     * Subscribes to property events for the specific area ID for the property.
     *
     * <p>For continuous property, variable update rate is enabled.
     *
     * <p>A property event is used to indicate a property's value/status changes (a.k.a
     * property update event) or used to indicate a previous {@link #setProperty} operation failed
     * (a.k.a property error event).
     *
     * <p>It is allowed to register multiple {@code carPropertyEventCallback} for a single
     * [PropertyId, areaId]. All the registered callbacks will be invoked.
     *
     * <p>It is only allowed to have one {@code updateRateHz} for a single
     * [propertyId, areaId, carPropertyEventCallback] combination. A new {@code updateRateHz} for
     * such combination will update the {@code updateRateHz}.
     *
     * <p>It is only allowed to have one {@code setVariableUpdateRateEnabled} setting for a single
     * [propertyId, areaId, carPropertyEventCallback] combination. A new setting will overwrite
     * the current setting for the combination.
     *
     * <p>The {@code carPropertyEventCallback} is executed on a single default event handler thread.
     *
     * <p>
     * <b>Note:</b> When this function is called, the callback will receive the current
     * values of the subscribed [propertyId, areaId]s through property change events if they are
     * currently okay for reading. If they are not available for reading or in error state,
     * property change events with a unavailable or error status will be generated.
     *
     * <p>Note that the callback will be executed on the event handler provided to the
     * {@link android.car.Car} or the main thread if none was provided.
     *
     * @param propertyId The ID for the property to subscribe to.
     * @param areaId The ID for the area to subscribe to.
     * @param updateRateHz Only meaningful for continuous property. The update rate in Hz. A common
     *      value is 1Hz. See {@link Subscription.Builder#setUpdateRateHz} for detail.
     * @param carPropertyEventCallback The callback to deliver property update/error events.
     * @return {@code true} if the listener is successfully registered
     * @throws SecurityException if the caller does not have read permission to one of the supported
     *                           properties.
     * @throws IllegalArgumentException if there are over-lapping areaIds or the executor is
     *                                  registered to another callback or one of the properties are
     *                                  not supported.
     *
     * @see #subscribePropertyEvents(List, Executor, CarPropertyEventCallback) for
     * more detailed explanation on property subscription and batched subscription usage.
     */
    @FlaggedApi(Flags.FLAG_VARIABLE_UPDATE_RATE)
    public boolean subscribePropertyEvents(int propertyId, int areaId,
            @FloatRange(from = 0.0, to = 100.0) float updateRateHz,
            @NonNull CarPropertyEventCallback carPropertyEventCallback) {
        Subscription subscription = new Subscription.Builder(propertyId).addAreaId(areaId)
                .setUpdateRateHz(updateRateHz).setVariableUpdateRateEnabled(false).build();
        return subscribePropertyEvents(List.of(subscription), /* callbackExecutor= */ null,
                carPropertyEventCallback);
    }

    /**
     * Subscribes to multiple [propertyId, areaId]s for property events.
     *
     * <p>
     * If caller don't need use different subscription options among different areaIds for
     * one property (e.g. 1 Hz update rate for front-left and 5 Hz update rate for front-right), it
     * is recommended to use one {@link Subscription} per property ID.
     *
     * <p>It is allowed to register multiple {@code carPropertyEventCallback} for a single
     * [PropertyId, areaId]. All the registered callbacks will be invoked.
     *
     * <p>It is only allowed to have one {@code updateRateHz} for a single
     * [propertyId, areaId, carPropertyEventCallback] combination. A new {@code updateRateHz} for
     * such combination will update the {@code updateRateHz}.
     *
     * <p>It is only allowed to have one {@code setVariableUpdateRateEnabled} setting for a single
     * [propertyId, areaId, carPropertyEventCallback] combination. A new setting will overwrite
     * the current setting for the combination.
     *
     * <p>
     * It is allowed to have the same PropertyId in different {@link Subscription}s
     * provided in one call. However, they must have non-overlapping AreaIds. A.k.a., one
     * [PropertyId, AreaId] must only be associated with one {@link Subscription} within one call.
     * Otherwise, {@link IllegalArgumentException} will be thrown.
     *
     * <p>
     * If the
     * {@code callbackExecutor} is {@code null}, the callback will be executed on the default event
     * handler thread. If no AreaIds are specified, then it will subscribe to all AreaIds for that
     * PropertyId.
     *
     * <p>
     * Only one executor can be registered to a callback. The callback must be unregistered before
     * trying to register another executor for the same callback. (A callback cannot have
     * multiple executors)
     *
     * <p>Only one executor can be registered to a callback. The callback must be unregistered
     * before trying to register another executor for the same callback. (E.G. A callback cannot
     * have multiple executors)
     *
     * <p>
     * <b>Note:</b>Rate has no effect if the property has one of the following change modes:
     * <ul>
     *   <li>{@link CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}</li>
     *   <li>{@link CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}</li>
     * </ul>
     *
     * <p>
     * If the property has the change mode:
     * {@link CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS}, {@code updateRateHz} in
     * {@code Subscription} specifies how frequent the property value has to be polled. If
     * {@code setVariableUpdateRateEnabled} is not called with {@code false} and variable update
     * rate is supported based on
     * {@link android.car.hardware.property.AreaIdConfig#isVariableUpdateRateSupported},
     * then the client will receive property update event only when the property's value changes
     * (a.k.a behaves the same as {@link CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}).
     * If {@code setVariableUpdateRateEnabled} is called with {@code false} or variable update rate
     * is not supported, then the client will receive all the property update events based on the
     * update rate even if the events contain the same property value.
     *
     * <p>See {@link Subscription.Builder#setVariableUpdateRateEnabled} for more detail.
     *
     * <p>
     * <b>Note:</b> When this function is called, the callback will receive the current
     * values of the subscribed [propertyId, areaId]s through property change events if they are
     * currently okay for reading. If they are not available for reading or in error state,
     * property change events with a unavailable or error status will be generated.
     *
     * <p>For properties that might be unavailable for reading because their power state is off,
     * PropertyId change events containing the PropertyId's initial value will be
     * generated once their power state is on.
     *
     * <p>If the update rate specified in the {@code subscriptions} for a given PropertyId is
     * higher than the PropertyId's maximum sample rate, the subscription will be registered at the
     * PropertyId's maximum sample rate specified by {@link CarPropertyConfig#getMaxSampleRate()}.

     * <p>If the update rate specified in the {@code subscriptions} for a given PropertyId is
     * lower than the PropertyId's minimum sample rate, the subscription will be registered at the
     * PropertyId's minimum sample rate specified by {@link CarPropertyConfig#getMinSampleRate()}.
     *
     * <p>
     * <b>Note:</b>Caller must check the value of {@link CarPropertyValue#getStatus()} for
     * PropertyId change events and only use {@link CarPropertyValue#getValue()} when
     * {@link CarPropertyValue#getStatus()} is {@link CarPropertyValue#STATUS_AVAILABLE}. If not,
     * the {@link CarPropertyValue#getValue()} is meaningless.
     *
     * <p>
     * <b>Note:</b>A PropertyId change event may/may not happen when the PropertyId's status
     * changes. Caller should not depend on the change event to check PropertyId's status. For
     * properties that might be unavailable because they depend on certain power state, caller
     * should subscribe to the power state PropertyId (e.g. {@link VehiclePropertyIds#HVAC_POWER_ON}
     * for hvac power dependent properties) to decide this PropertyId's availability.
     *
     * <p>
     * If one {@link CarPropertyEventCallback} is already registered using
     * {@link CarPropertyManager#registerCallback}, caller must make sure the executor is
     * null (using the default executor) for subscribePropertyEvents.
     *
     * @param subscriptions A list of subscriptions to add, which specifies PropertyId, AreaId, and
     *                      updateRateHz. Caller should typically use one Subscription for one
     *                      property ID.
     * @param callbackExecutor The executor in which the callback is done on. If this is null, the
     *                         callback will be executed on the event handler provided to the
     *                         {@link android.car.Car} or the main thread if none was provided.
     * @param carPropertyEventCallback The callback to deliver property update/error events.
     * @return {@code true} if the listener is successfully registered
     * @throws SecurityException if the caller does not have read permission to one of the supported
     *                           properties.
     * @throws IllegalArgumentException if there are over-lapping areaIds or the executor is
     *                                  registered to another callback or one of the properties are
     *                                  not supported.
     */
    @FlaggedApi(Flags.FLAG_BATCHED_SUBSCRIPTIONS)
    public boolean subscribePropertyEvents(@NonNull List<Subscription> subscriptions,
            @Nullable @CallbackExecutor Executor callbackExecutor,
            @NonNull CarPropertyEventCallback carPropertyEventCallback) {
        requireNonNull(subscriptions);
        List<CarSubscription> subscribeOptions = convertToCarSubscribeOptions(subscriptions);
        return subscribePropertyEventsInternal(subscribeOptions, callbackExecutor,
                carPropertyEventCallback);
    }

    /**
     * Converts the {@link Subscription} from client to internal {@link CarSubscription}.
     *
     * This is only called by APIs with FLAG_BATCHED_SUBSCRIPTIONS.
     */
    private List<CarSubscription> convertToCarSubscribeOptions(List<Subscription> subscriptions) {
        List<CarSubscription> carSubscribeOptions = new ArrayList<>();
        for (int i = 0; i < subscriptions.size(); i++) {
            Subscription clientOption = subscriptions.get(i);
            CarSubscription internalOption = new CarSubscription();
            internalOption.propertyId = clientOption.getPropertyId();
            internalOption.areaIds = clientOption.getAreaIds();
            internalOption.updateRateHz = clientOption.getUpdateRateHz();
            internalOption.enableVariableUpdateRate = clientOption.isVariableUpdateRateEnabled();
            internalOption.resolution = clientOption.getResolution();
            carSubscribeOptions.add(internalOption);
        }
        return carSubscribeOptions;
    }

    private boolean subscribePropertyEventsInternal(List<CarSubscription> subscribeOptions,
            @Nullable @CallbackExecutor Executor callbackExecutor,
            CarPropertyEventCallback carPropertyEventCallback) {
        requireNonNull(carPropertyEventCallback);
        validateAreaDisjointness(subscribeOptions);
        if (DBG) {
            Log.d(TAG, String.format("subscribePropertyEvents, callback: %s subscribeOptions: %s",
                             carPropertyEventCallback, subscribeOptions));
        }
        int[] noReadPermPropertyIds;
        try {
            noReadPermPropertyIds = getSupportedNoReadPermPropIds(subscribeOptions);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
            return false;
        }
        if (noReadPermPropertyIds.length != 0) {
            // propertyIdsToString does not work primitive array.
            List<Integer> noReadPermPropertyIdsCopy = new ArrayList<>();
            for (int i = 0; i < noReadPermPropertyIds.length; i++) {
                noReadPermPropertyIdsCopy.add(noReadPermPropertyIds[i]);
            }
            throw new SecurityException("Do not have read permissions for properties: "
                    + CarPropertyHelper.propertyIdsToString(noReadPermPropertyIdsCopy));
        }

        if (callbackExecutor == null) {
            callbackExecutor = mExecutor;
        }

        List<CarSubscription> sanitizedSubscribeOptions;
        try {
            sanitizedSubscribeOptions = sanitizeSubscribeOptions(subscribeOptions);
        } catch (IllegalStateException e) {
            Log.e(TAG, "failed to sanitize update rate", e);
            return false;
        }

        synchronized (mLock) {
            CarPropertyEventCallbackController cpeCallbackController =
                    mCpeCallbackToCpeCallbackController.get(carPropertyEventCallback);
            if (cpeCallbackController != null
                    && cpeCallbackController.getExecutor() != callbackExecutor) {
                throw new IllegalArgumentException("A different executor is already associated with"
                        + " this callback, please use the same executor.");
            }

            mSubscriptionManager.stageNewOptions(carPropertyEventCallback,
                    sanitizedSubscribeOptions);

            if (!applySubscriptionChangesLocked()) {
                Log.e(TAG, "Subscription failed: failed to apply subscription changes");
                return false;
            }

            // Must use sanitizedSubscribeOptions instead of subscribeOptions here since we need to
            // use sanitized update rate.
            for (int i = 0; i < sanitizedSubscribeOptions.size(); i++) {
                CarSubscription option = sanitizedSubscribeOptions.get(i);
                int propertyId = option.propertyId;
                float sanitizedUpdateRateHz = option.updateRateHz;
                int[] areaIds = option.areaIds;

                if (cpeCallbackController == null) {
                    cpeCallbackController =
                            new CarPropertyEventCallbackController(carPropertyEventCallback,
                                    callbackExecutor);
                    mCpeCallbackToCpeCallbackController.put(carPropertyEventCallback,
                            cpeCallbackController);
                }
                // After {@code sanitizeSubscribeOptions}, update rate must be 0
                // for on-change property and non-0 for continuous property.
                // There is an edge case where minSampleRate is 0 and client uses 0 as sample rate
                // for continuous property. In this case, it is really impossible to do VUR so treat
                // it as an on-change property is fine.
                if (sanitizedUpdateRateHz == 0) {
                    cpeCallbackController.addOnChangeProperty(propertyId, areaIds);
                } else {
                    cpeCallbackController.addContinuousProperty(propertyId, areaIds,
                            sanitizedUpdateRateHz, option.enableVariableUpdateRate,
                            option.resolution);
                }

                ArraySet<CarPropertyEventCallbackController> cpeCallbackControllerSet =
                        mPropIdToCpeCallbackControllerList.get(propertyId);
                if (cpeCallbackControllerSet == null) {
                    cpeCallbackControllerSet = new ArraySet<>();
                    mPropIdToCpeCallbackControllerList.put(propertyId, cpeCallbackControllerSet);
                }
                cpeCallbackControllerSet.add(cpeCallbackController);
            }
        }
        return true;
    }

    /**
     * Checks if any subscription have overlapping [propertyId, areaId] pairs.
     *
     * @param subscribeOptions The list of subscribe options to check.
     */
    private void validateAreaDisjointness(List<CarSubscription> subscribeOptions) {
        PairSparseArray<Object> propertyToAreaId = new PairSparseArray<>();
        Object placeHolder = new Object();
        for (int i = 0; i < subscribeOptions.size(); i++) {
            CarSubscription option = subscribeOptions.get(i);
            int propertyId = option.propertyId;
            int[] areaIds = option.areaIds;
            for (int areaId : areaIds) {
                if (propertyToAreaId.contains(propertyId, areaId)) {
                    throw new IllegalArgumentException("Subscribe options contain overlapping "
                            + "propertyId: " + VehiclePropertyIds.toString(propertyId) + " areaId: "
                            + areaId);
                }
                propertyToAreaId.append(propertyId, areaId, placeHolder);
            }
        }
    }

    private static class CarPropertyEventListenerToService extends ICarPropertyEventListener.Stub {
        private final WeakReference<CarPropertyManager> mCarPropertyManager;

        CarPropertyEventListenerToService(CarPropertyManager carPropertyManager) {
            mCarPropertyManager = new WeakReference<>(carPropertyManager);
        }

        @Override
        public void onEvent(List<CarPropertyEvent> carPropertyEvents) throws RemoteException {
            CarPropertyManager carPropertyManager = mCarPropertyManager.get();
            if (carPropertyManager != null) {
                carPropertyManager.handleEvents(carPropertyEvents);
            }
        }
    }

    private void handleEvents(List<CarPropertyEvent> carPropertyEvents) {
        if (mHandler != null) {
            mHandler.sendEvents(carPropertyEvents);
        }
    }

    /**
     * Update the property ID and area IDs subscription in {@link #mService}.
     *
     * @return {@code true} if the property has been successfully registered with the service.
     * @throws SecurityException if missing the appropriate property access permission.
     */
    @GuardedBy("mLock")
    private boolean applySubscriptionChangesLocked() {
        List<CarSubscription> updatedCarSubscriptions = new ArrayList<>();
        List<Integer> propertiesToUnsubscribe = new ArrayList<>();

        mSubscriptionManager.diffBetweenCurrentAndStage(updatedCarSubscriptions,
                propertiesToUnsubscribe);

        if (propertiesToUnsubscribe.isEmpty() && updatedCarSubscriptions.isEmpty()) {
            Log.d(TAG, "There is nothing to subscribe or unsubscribe to CarPropertyService");
            mSubscriptionManager.commit();
            return true;
        }

        if (DBG) {
            Log.d(TAG, "updatedCarSubscriptions to subscribe is: "
                    + updatedCarSubscriptions + " and the list of properties to unsubscribe is: "
                    + CarPropertyHelper.propertyIdsToString(propertiesToUnsubscribe));
        }

        try {
            if (!updatedCarSubscriptions.isEmpty()) {
                if (!registerLocked(updatedCarSubscriptions)) {
                    mSubscriptionManager.dropCommit();
                    return false;
                }
            }

            if (!propertiesToUnsubscribe.isEmpty()) {
                for (int i = 0; i < propertiesToUnsubscribe.size(); i++) {
                    if (!unregisterLocked(propertiesToUnsubscribe.get(i))) {
                        Log.w(TAG, "Failed to unsubscribe to: " + VehiclePropertyIds.toString(
                                propertiesToUnsubscribe.get(i)));
                        mSubscriptionManager.dropCommit();
                        return false;
                    }
                }
            }
        } catch (SecurityException e) {
            mSubscriptionManager.dropCommit();
            throw e;
        }

        mSubscriptionManager.commit();
        return true;
    }

    /**
     * Called when {@code propertyId} registration needs to be updated.
     *
     * @return {@code true} if registration was successful, otherwise {@code false}.
     * @throws SecurityException if missing the appropriate property access permission.
     */
    @GuardedBy("mLock")
    private boolean registerLocked(List<CarSubscription> options) {
        try {
            mService.registerListener(options, mCarPropertyEventToService);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
            return false;
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            Log.w(TAG, "registerLocked with options: " + options
                    + ", unexpected exception=", e);
            return false;
        }
        return true;
    }

    /**
     * Called when {@code propertyId} needs to be unregistered.
     *
     * @return {@code true} if unregistering was successful, otherwise {@code false}.
     * @throws SecurityException if missing the appropriate property access permission.
     */
    @GuardedBy("mLock")
    private boolean unregisterLocked(int propertyId) {
        try {
            mService.unregisterListener(propertyId, mCarPropertyEventToService);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
            return false;
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            Log.w(TAG, "unregisterLocked with property: "
                    + VehiclePropertyIds.toString(propertyId)
                    + ", unexpected exception=", e);
            return false;
        }
        return true;
    }

    /**
     * Stop getting property updates for the given {@link CarPropertyEventCallback}. If there are
     * multiple registrations for this {@link CarPropertyEventCallback}, all listening will be
     * stopped.
     *
     * @param carPropertyEventCallback A previously subscribed callback to unsubscribe.
     * @throws SecurityException if the caller does not have read permission to the properties
     *                           registered for this callback.
     */
    @FlaggedApi(Flags.FLAG_BATCHED_SUBSCRIPTIONS)
    public void unsubscribePropertyEvents(
            @NonNull CarPropertyEventCallback carPropertyEventCallback) {
        requireNonNull(carPropertyEventCallback);
        if (DBG) {
            Log.d(TAG, "unsubscribePropertyEvents, callback: " + carPropertyEventCallback);
        }
        int[] propertyIds;
        synchronized (mLock) {
            CarPropertyEventCallbackController cpeCallbackController =
                    mCpeCallbackToCpeCallbackController.get(carPropertyEventCallback);
            if (cpeCallbackController == null) {
                Log.w(TAG, "unsubscribePropertyEvents: callback was not previously registered.");
                return;
            }
            propertyIds = cpeCallbackController.getSubscribedProperties();
        }
        ArrayList<Integer> propertyIdsList = new ArrayList<>(propertyIds.length);
        for (int i = 0; i < propertyIds.length; i++) {
            propertyIdsList.add(propertyIds[i]);
        }
        unsubscribePropertyEventsInternal(propertyIdsList, carPropertyEventCallback);
    }

    /**
     * Stop getting property updates for the given {@link CarPropertyEventCallback}. If there are
     * multiple registrations for this {@link CarPropertyEventCallback}, all listening will be
     * stopped.
     *
     * @param carPropertyEventCallback A previously subscribed callback to unsubscribe.
     * @throws SecurityException if the caller does not have read permission to the properties
     *                           registered for this callback.
     */
    public void unregisterCallback(@NonNull CarPropertyEventCallback carPropertyEventCallback) {
        if (DBG) {
            Log.d(TAG, "unregisterCallback, callback: " + carPropertyEventCallback);
        }
        requireNonNull(carPropertyEventCallback);
        int[] propertyIds;
        synchronized (mLock) {
            CarPropertyEventCallbackController cpeCallbackController =
                    mCpeCallbackToCpeCallbackController.get(carPropertyEventCallback);
            if (cpeCallbackController == null) {
                Log.w(TAG, "unregisterCallback: callback was not previously registered.");
                return;
            }
            propertyIds = cpeCallbackController.getSubscribedProperties();
        }
        ArrayList<Integer> propertyIdsList = new ArrayList<>(propertyIds.length);
        for (int i = 0; i < propertyIds.length; i++) {
            propertyIdsList.add(propertyIds[i]);
        }
        unsubscribePropertyEventsInternal(propertyIdsList, carPropertyEventCallback);
    }

    /**
     * Stop getting update for {@code propertyId} to the given {@link CarPropertyEventCallback}. If
     * the same {@link CarPropertyEventCallback} is used for other properties, those subscriptions
     * will not be affected.
     *
     * @param propertyId The property ID to unsubscribe.
     * @param carPropertyEventCallback A previously subscribed callback to unsubscribe.
     * @throws SecurityException if the caller does not have read permission to the property.
     */
    @FlaggedApi(Flags.FLAG_BATCHED_SUBSCRIPTIONS)
    public void unsubscribePropertyEvents(int propertyId,
            @NonNull CarPropertyEventCallback carPropertyEventCallback) {
        requireNonNull(carPropertyEventCallback);
        unsubscribePropertyEventsInternal(List.of(propertyId), carPropertyEventCallback);
    }

    /**
     * Stop getting update for {@code propertyId} to the given {@link CarPropertyEventCallback}. If
     * the same {@link CarPropertyEventCallback} is used for other properties, those subscriptions
     * will not be affected.
     *
     * @param carPropertyEventCallback A previously subscribed callback to unsubscribe.
     * @param propertyId The property ID to unsubscribe.
     * @throws SecurityException if the caller does not have read permission to the property.
     */
    @SuppressWarnings("FormatString")
    public void unregisterCallback(@NonNull CarPropertyEventCallback carPropertyEventCallback,
            int propertyId) {
        if (DBG) {
            Log.d(TAG, String.format("unregisterCallback, callback: %s, property Id: %s",
                    carPropertyEventCallback, VehiclePropertyIds.toString(propertyId)));
        }
        requireNonNull(carPropertyEventCallback);
        unsubscribePropertyEventsInternal(List.of(propertyId), carPropertyEventCallback);
    }

    private void unsubscribePropertyEventsInternal(
            List<Integer> propertyIds, CarPropertyEventCallback carPropertyEventCallback) {
        synchronized (mLock) {
            CarPropertyEventCallbackController cpeCallbackController =
                    mCpeCallbackToCpeCallbackController.get(carPropertyEventCallback);
            if (cpeCallbackController == null) {
                return;
            }
            // Filter out User HAL property IDs so that getPropertyConfigsFromService will not
            // throw IllegalArgumentException.
            List<Integer> filteredPropertyIds = filterOutUserHalProperty(propertyIds);
            CarPropertyConfigs configs = getPropertyConfigsFromService(filteredPropertyIds);

            if (configs == null) {
                Log.e(TAG, "failed to get property config list from car service, do nothing");
                return;
            }
            for (int i = 0; i < filteredPropertyIds.size(); i++) {
                int propertyId = filteredPropertyIds.get(i);
                if (DBG) {
                    Log.d(TAG, String.format(
                            "unsubscribePropertyEvents, callback: %s, property Id: %s",
                            carPropertyEventCallback, VehiclePropertyIds.toString(propertyId)));
                }

                if (configs.isNotSupported(propertyId)) {
                    Log.e(TAG, "unsubscribePropertyEvents: not supported property: "
                            + VehiclePropertyIds.toString(propertyId));
                    continue;
                }
                if (configs.missingPermission(propertyId)) {
                    Log.e(TAG, "unsubscribePropertyEvents: missing read/write permission for "
                            + "property: " + VehiclePropertyIds.toString(propertyId));
                    continue;
                }
                ArraySet<CarPropertyEventCallbackController> cpeCallbackControllerSet =
                        mPropIdToCpeCallbackControllerList.get(propertyId);

                if (cpeCallbackControllerSet == null) {
                    Log.e(TAG,
                            "unsubscribePropertyEvents: callback was not previously registered.");
                    continue;
                } else if (!cpeCallbackControllerSet.contains(cpeCallbackController)) {
                    Log.e(TAG,
                            "unsubscribePropertyEvents: callback was not previously registered for"
                                    + " propertyId=" + VehiclePropertyIds.toString(propertyId));
                    continue;
                }

                mSubscriptionManager.stageUnregister(carPropertyEventCallback,
                        new ArraySet<>(Set.of(propertyId)));

                if (!applySubscriptionChangesLocked()) {
                    continue;
                }

                boolean allPropertiesRemoved = cpeCallbackController.remove(propertyId);
                if (allPropertiesRemoved) {
                    mCpeCallbackToCpeCallbackController.remove(carPropertyEventCallback);
                }

                cpeCallbackControllerSet.remove(cpeCallbackController);
                if (cpeCallbackControllerSet.isEmpty()) {
                    mPropIdToCpeCallbackControllerList.remove(propertyId);
                }
            }
        }
    }

    /**
     * @return the list of properties supported by this car that the application may access
     */
    @NonNull
    public List<CarPropertyConfig> getPropertyList() {
        if (DBG) {
            Log.d(TAG, "getPropertyList");
        }
        List<CarPropertyConfig> configs;
        try {
            configs = mService.getPropertyList().getConfigs();
        } catch (RemoteException e) {
            Log.e(TAG, "getPropertyList exception ", e);
            return handleRemoteExceptionFromCarService(e, new ArrayList<>());
        }
        if (DBG) {
            Log.d(TAG, "getPropertyList returns " + configs.size() + " configs");
            for (int i = 0; i < configs.size(); i++) {
                Log.v(TAG, i + ": " + configs.get(i));
            }
        }
        return configs;
    }

    /**
     * Checks the given property IDs and returns a list of property configs supported by the car.
     *
     * If some of the properties in the given ID list are not supported, they will not be returned.
     *
     * @param propertyIds the list of property IDs
     * @return the list of property configs
     */
    @NonNull
    public List<CarPropertyConfig> getPropertyList(@NonNull ArraySet<Integer> propertyIds) {
        if (DBG) {
            Log.d(TAG, "getPropertyList(" + CarPropertyHelper.propertyIdsToString(propertyIds)
                    + ")");
        }
        CarPropertyConfigs configs = getPropertyConfigsFromService(propertyIds);
        if (configs == null) {
            return new ArrayList<>();
        }
        if (configs.getMissingPermissionPropIds().length != 0) {
            Log.w(TAG, "Missing required permissions to access properties: "
                    + CarPropertyHelper.propertyIdsToString(configs.getMissingPermissionPropIds()));
        }
        if (configs.getUnsupportedPropIds().length != 0) {
            Log.w(TAG, "The following properties are not supported: "
                    + CarPropertyHelper.propertyIdsToString(configs.getUnsupportedPropIds()));
        }
        List<CarPropertyConfig> configList = configs.getConfigs();
        if (DBG) {
            Log.d(TAG, "getPropertyList(" + CarPropertyHelper.propertyIdsToString(propertyIds)
                    + ") returns " + configList.size() + " configs");
            for (int i = 0; i < configList.size(); i++) {
                Log.v(TAG, i + ": " + configList.get(i));
            }
        }
        return configList;
    }

    /**
     * Get {@link CarPropertyConfig} by property ID.
     *
     * @param propertyId the property ID
     * @return the {@link CarPropertyConfig} for the selected property, {@code null} if missing
     * the required permission to read/write the property or the property is not supported.
     */
    @Nullable
    public CarPropertyConfig<?> getCarPropertyConfig(int propertyId) {
        if (DBG) {
            Log.d(TAG, "getCarPropertyConfig(" + VehiclePropertyIds.toString(propertyId) + ")");
        }
        assertNotUserHalProperty(propertyId);
        if (!CarPropertyHelper.isSupported(propertyId)) {
            Log.w(TAG, "Property: " + VehiclePropertyIds.toString(propertyId)
                    + " is not supported");
            return null;
        }

        CarPropertyConfigs configs = getPropertyConfigsFromService(
                new ArraySet(Set.of(propertyId)));
        if (configs == null) {
            return null;
        }

        if (configs.missingPermission(propertyId)) {
            Log.w(TAG, "Missing required permissions to access property: "
                    + VehiclePropertyIds.toString(propertyId));
            return null;
        }
        if (configs.isNotSupported(propertyId)) {
            Log.w(TAG, "The property is not supported: "
                    + VehiclePropertyIds.toString(propertyId));
            return null;
        }

        CarPropertyConfig<?> config = configs.getConfigs().get(0);
        if (DBG) {
            Log.d(TAG, "getCarPropertyConfig(" + VehiclePropertyIds.toString(propertyId)
                    + ") returns " + config);
        }
        return config;
    }

    /**
     * Returns areaId contains the selected area for the property.
     *
     * @param propertyId the property ID
     * @param area the area enum such as Enums in {@link android.car.VehicleAreaSeat}
     * @throws IllegalArgumentException if the property is not supported in the vehicle for
     * the selected area or the caller does not have read or write permission to the property.
     * @return the {@code AreaId} containing the selected area for the property
     */
    public int getAreaId(int propertyId, int area) {
        assertNotUserHalProperty(propertyId);
        String propertyIdStr = VehiclePropertyIds.toString(propertyId);
        if (DBG) {
            Log.d(TAG, "getAreaId(propertyId = " + propertyIdStr + ", area = " + area + ")");
        }
        CarPropertyConfigs configs = getPropertyConfigsFromService(
                new ArraySet<>(Set.of(propertyId)));
        if (configs == null) {
            throw new IllegalArgumentException("Failed to getPropertyConfigList from car service");
        }
        if (configs.missingPermission(propertyId)) {
            throw new IllegalArgumentException("Missing required permissions to access property: "
                    + propertyIdStr);
        }
        if (configs.isNotSupported(propertyId)) {
            throw new IllegalArgumentException("The property is not supported: " + propertyIdStr);
        }
        CarPropertyConfig<?> propConfig = configs.getConfigs().get(0);
        // For the global property, areaId is 0
        if (propConfig.isGlobalProperty()) {
            if (DBG) {
                Log.d(TAG, "getAreaId returns the global area ID (0)");
            }
            return 0;
        }
        for (int areaId : propConfig.getAreaIds()) {
            if ((area & areaId) == area) {
                if (DBG) {
                    Log.d(TAG, "getAreaId returns " + areaId);
                }
                return areaId;
            }
        }

        throw new IllegalArgumentException("The propertyId: " + propertyIdStr
                + " is not available at the area: 0x" + toHexString(area));
    }

    /**
     * Return read permission string for given property ID. The format of the return value of this
     * function has changed over time and thus should not be relied on.
     *
     * @param propId the property ID to query
     * @return the permission needed to read this property, {@code null} if the property ID is not
     * available
     *
     * @hide
     */
    @Nullable
    public String getReadPermission(int propId) {
        assertNotUserHalProperty(propId);
        try {
            String permission = mService.getReadPermission(propId);
            if (DBG) {
                Log.d(TAG, "getReadPermission(propId =" + VehiclePropertyIds.toString(propId)
                        + ") returns " + permission);
            }
            return permission;
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, "");
        }
    }

    /**
     * Return write permission string for given property ID. The format of the return value of this
     * function has changed over time and thus should not be relied on.
     *
     * @param propId the property ID to query
     * @return the permission needed to write this property, {@code null} if the property ID is not
     * available.
     *
     * @hide
     */
    @Nullable
    public String getWritePermission(int propId) {
        assertNotUserHalProperty(propId);
        try {
            String permission = mService.getWritePermission(propId);
            if (DBG) {
                Log.d(TAG, "getWritePermission(propId = " + VehiclePropertyIds.toString(propId)
                        + ") returns " + permission);
            }
            return permission;
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, "");
        }
    }


    /**
     * Check whether a given property is available or disabled based on the car's current state.
     *
     * @param propertyId the property ID
     * @param areaId the area ID
     * @return {@code true} if {@link CarPropertyValue#STATUS_AVAILABLE}, {@code false} otherwise
     * (eg {@link CarPropertyValue#STATUS_UNAVAILABLE})
     */
    public boolean isPropertyAvailable(int propertyId, int areaId) {
        if (DBG) {
            Log.d(TAG, "isPropertyAvailable(propertyId = "
                    + VehiclePropertyIds.toString(propertyId) + ", areaId = " + areaId + ")");
        }
        assertNotUserHalProperty(propertyId);
        if (!CarPropertyHelper.isSupported(propertyId)) {
            if (DBG) {
                Log.d(TAG, "Property: " + VehiclePropertyIds.toString(propertyId)
                        + " is not supported");
            }
            return false;
        }

        try {
            CarPropertyValue propValue = runSyncOperation(() -> {
                if (DBG) {
                    Log.d(TAG, "calling getProperty to check property's availability");
                }
                return mService.getProperty(propertyId, areaId);
            });
            return (propValue != null
                    && propValue.getStatus() == CarPropertyValue.STATUS_AVAILABLE);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, false);
        } catch (ServiceSpecificException e) {
            Log.e(TAG, "unable to get property, error: " + e);
            return false;
        }
    }

    /**
     * Returns value of a bool property
     *
     * <p>This method may take couple seconds to complete, so it needs to be called from a
     * non-main thread.
     *
     * <p>Note: Client MUST NOT use one of the following as propertyId, otherwise the behavior is
     * undefined:
     *
     * <ul>
     * <li>{@code INITIAL_USER_INFO}
     * <li>{@code SWITCH_USER}
     * <li>{@code CREATE_USER}
     * <li>{@code REMOVE_USER}
     * <li>{@code USER_IDENTIFICATION_ASSOCIATION}
     * </ul>
     *
     * <p>Clients that declare a {@link android.content.pm.ApplicationInfo#targetSdkVersion} equal
     * or later than {@link Build.VERSION_CODES#UPSIDE_DOWN_CAKE} will receive the following
     * exceptions when request failed.
     * <ul>
     *     <li>{@link CarInternalErrorException} when there is an unexpected error detected in cars
     *     <li>{@link PropertyAccessDeniedSecurityException} when cars denied the access of the
     *     property
     *     <li>{@link PropertyNotAvailableAndRetryException} when the property is temporarily
     *     not available and likely that retrying will be successful
     *     <li>{@link PropertyNotAvailableException} when the property is not available and might be
     *     unavailable for a while.
     *     <li>{@link IllegalArgumentException} when the [propertyId, areaId] is not supported or
     *     when the property is of wrong type.
     * </ul>
     *
     * <p>Clients that declare a {@link android.content.pm.ApplicationInfo#targetSdkVersion} equal
     * or later than {@link Build.VERSION_CODES#R}, before
     * {@link Build.VERSION_CODES#UPSIDE_DOWN_CAKE} will
     * receive the following exceptions or {@code false} when request failed.
     * <ul>
     *     <li>{@link CarInternalErrorException} when there is an unexpected error detected in cars
     *     <li>{@link PropertyAccessDeniedSecurityException} when cars denied the access of the
     *     property
     *     <li>{@link PropertyNotAvailableAndRetryException} when the property is temporarily
     *     not available and likely that retrying will be successful
     *     <li>{@link PropertyNotAvailableException} when the property is not available and might be
     *     unavailable for a while.
     *     <li>{@link IllegalArgumentException} when the property is of wrong type.
     *     <li>{@code false} when the [propertyId, areaId] is not supported
     * </ul>
     *
     * <p>Clients that declare a {@link android.content.pm.ApplicationInfo#targetSdkVersion}
     * earlier than {@link Build.VERSION_CODES#R} will receive the following exceptions or
     * {@code false} when request failed.
     * <ul>
     *     <li>{@link IllegalStateException} when there is an error detected in cars, or when
     *         cars denied the access of the property, or when the property is not available and
     *         might be unavailable for a while, or when unexpected error happens.
     *     <li>{@link IllegalArgumentException} when the property is of wrong type.
     *     <li>{@code false} when the [propertyId, areaId] is not supported or when the property is
     *     temporarily not available.
     * </ul>
     *
     * <p>For pre-R client, the returned value might be {@code false} if the property is temporarily
     * not available. The client should try again in this case.
     *
     * <p>For pre-U client, when the [propertyId, areaId] is not supported, this will return
     * {@code false}.
     *
     * <p>For U and later client, when the [propertyId, areaId] is not supported, this is
     * guaranteed to throw {@code IllegalArgumentException}.
     *
     * @param propertyId the property ID to get
     * @param areaId the area ID of the property to get
     *
     * @throws CarInternalErrorException when there is an unexpected error detected in cars
     * @throws PropertyAccessDeniedSecurityException when cars denied the access of the
     * property
     * @throws PropertyNotAvailableAndRetryException when the property is temporarily
     * not available and likely that retrying will be successful
     * @throws PropertyNotAvailableException when the property is not available and might be
     * unavailable for a while.
     * @throws IllegalArgumentException when the [propertyId, areaId] is not supported for U and
     * later client, or when the property is of wrong type.
     *
     * @return the value of a bool property or {@code false}.
     */
    public boolean getBooleanProperty(int propertyId, int areaId) {
        CarPropertyValue<Boolean> carProp = getProperty(Boolean.class, propertyId, areaId);
        return handleNullAndPropertyStatus(carProp, areaId, false);
    }

    /**
     * Returns value of a float property
     *
     * <p>This method may take couple seconds to complete, so it needs to be called from a
     * non-main thread.
     *
     * <p>This method has the same exception behavior as {@link #getBooleanProperty(int, int)}.
     *
     * @param propertyId the property ID to get
     * @param areaId the area ID of the property to get
     *
     * @throws CarInternalErrorException when there is an unexpected error detected in cars
     * @throws PropertyAccessDeniedSecurityException when cars denied the access of the
     * property
     * @throws PropertyNotAvailableAndRetryException when the property is temporarily
     * not available and likely that retrying will be successful
     * @throws PropertyNotAvailableException when the property is not available and might be
     * unavailable for a while.
     * @throws IllegalArgumentException when the [propertyId, areaId] is not supported for U and
     * later client, or when the property is of wrong type.
     *
     * @return the value of a float property or {@code 0}.
     */
    public float getFloatProperty(int propertyId, int areaId) {
        CarPropertyValue<Float> carProp = getProperty(Float.class, propertyId, areaId);
        return handleNullAndPropertyStatus(carProp, areaId, 0f);
    }

    /**
     * Returns value of an integer property
     *
     * <p>This method may take couple seconds to complete, so it needs to be called form a
     * non-main thread.
     *
     * <p>This method has the same exception behavior as {@link #getBooleanProperty(int, int)}.
     *
     * @param propertyId the property ID to get
     * @param areaId the area ID of the property to get
     *
     * @throws CarInternalErrorException when there is an unexpected error detected in cars
     * @throws PropertyAccessDeniedSecurityException when cars denied the access of the
     * property
     * @throws PropertyNotAvailableAndRetryException when the property is temporarily
     * not available and likely that retrying will be successful
     * @throws PropertyNotAvailableException when the property is not available and might be
     * unavailable for a while.
     * @throws IllegalArgumentException when the [propertyId, areaId] is not supported for U and
     * later client, or when the property is of wrong type.
     *
     * @return the value of aa integer property or {@code 0}.
     */
    public int getIntProperty(int propertyId, int areaId) {
        CarPropertyValue<Integer> carProp = getProperty(Integer.class, propertyId, areaId);
        return handleNullAndPropertyStatus(carProp, areaId, 0);
    }

    /**
     * Returns value of an integer array property
     *
     * <p>This method may take couple seconds to complete, so it needs to be called from a
     * non-main thread.
     *
     * <p>This method has the same exception behavior as {@link #getBooleanProperty(int, int)}.
     *
     * @param propertyId the property ID to get
     * @param areaId the area ID of the property to get
     *
     * @throws CarInternalErrorException when there is an unexpected error detected in cars
     * @throws PropertyAccessDeniedSecurityException when cars denied the access of the
     * property
     * @throws PropertyNotAvailableAndRetryException when the property is temporarily
     * not available and likely that retrying will be successful
     * @throws PropertyNotAvailableException when the property is not available and might be
     * unavailable for a while.
     * @throws IllegalArgumentException when the [propertyId, areaId] is not supported for U and
     * later client, or when the property is of wrong type.
     *
     * @return the value of an integer array property or an empty array.
     */
    @NonNull
    public int[] getIntArrayProperty(int propertyId, int areaId) {
        CarPropertyValue<Integer[]> carProp = getProperty(Integer[].class, propertyId, areaId);
        Integer[] res = handleNullAndPropertyStatus(carProp, areaId, new Integer[0]);
        return toIntArray(res);
    }

    private static int[] toIntArray(Integer[] input) {
        int len = input.length;
        int[] arr = new int[len];
        for (int i = 0; i < len; i++) {
            arr[i] = input[i];
        }
        return arr;
    }

    private <T> T handleNullAndPropertyStatus(CarPropertyValue<T> propertyValue, int areaId,
            T defaultValue) {
        if (propertyValue == null) {
            return defaultValue;
        }

        // Keeps the same behavior as android R.
        if (mAppTargetSdk < Build.VERSION_CODES.S) {
            return propertyValue.getStatus() == CarPropertyValue.STATUS_AVAILABLE
                    ? propertyValue.getValue() : defaultValue;
        }

        // throws new exceptions in android S.
        switch (propertyValue.getStatus()) {
            case CarPropertyValue.STATUS_ERROR:
                throw new CarInternalErrorException(propertyValue.getPropertyId(), areaId);
            case CarPropertyValue.STATUS_UNAVAILABLE:
                throw new PropertyNotAvailableException(propertyValue.getPropertyId(),
                        areaId, /*vendorErrorCode=*/0);
            default:
                return propertyValue.getValue();
        }
    }

    @FunctionalInterface
    private interface RemoteCallable<V> {
        V call() throws RemoteException;
    }

    @Nullable
    private static <V> V runSyncOperation(RemoteCallable<V> c)
            throws RemoteException, ServiceSpecificException {
        int retryCount = 0;
        while (retryCount < SYNC_OP_RETRY_MAX_COUNT) {
            retryCount++;
            try {
                return c.call();
            } catch (ServiceSpecificException e) {
                if (e.errorCode != SYNC_OP_LIMIT_TRY_AGAIN) {
                    throw e;
                }
                // If car service don't have enough binder thread to handle this request. Sleep for
                // 10ms and try again.
                Log.d(TAG, "too many sync request, sleeping for " + SYNC_OP_RETRY_SLEEP_IN_MS
                        + " ms before retry");
                SystemClock.sleep(SYNC_OP_RETRY_SLEEP_IN_MS);
            } catch (RemoteException e) {
                throw e;
            }
        }
        throw new ServiceSpecificException(VehicleHalStatusCode.STATUS_INTERNAL_ERROR,
                "failed to call car service sync operations after " + retryCount + " retries");
    }

    /**
     * Return {@link CarPropertyValue}
     *
     * <p>This method may take couple seconds to complete, so it needs to be called from a
     * non-main thread.
     *
     * <p>Note: Client MUST NOT use one of the following as propertyId, otherwise the behavior is
     * undefined (might throw exception or might return null):
     *
     * <ul>
     * <li>{@code INITIAL_USER_INFO}
     * <li>{@code SWITCH_USER}
     * <li>{@code CREATE_USER}
     * <li>{@code REMOVE_USER}
     * <li>{@code USER_IDENTIFICATION_ASSOCIATION}
     * </ul>
     *
     * <p>Clients that declare a {@link android.content.pm.ApplicationInfo#targetSdkVersion} equal
     * or later than {@link Build.VERSION_CODES#UPSIDE_DOWN_CAKE} will receive the following
     * exceptions when request failed.
     * <ul>
     *     <li>{@link CarInternalErrorException} when there is an unexpected error detected in cars
     *     <li>{@link PropertyAccessDeniedSecurityException} when cars denied the access of the
     *     property
     *     <li>{@link PropertyNotAvailableAndRetryException} when the property is temporarily
     *     not available and likely that retrying will be successful
     *     <li>{@link PropertyNotAvailableException} when the property is not available and might be
     *     unavailable for a while.
     *     <li>{@link IllegalArgumentException} when the [propertyId, areaId] is not supported or
     *     when the specified class does not match the property type.
     * </ul>
     *
     * <p>Clients that declare a {@link android.content.pm.ApplicationInfo#targetSdkVersion} equal
     * or later than {@link Build.VERSION_CODES#R}, before
     * {@link Build.VERSION_CODES#UPSIDE_DOWN_CAKE} will
     * receive the following exceptions or {@code null} when request failed.
     * <ul>
     *     <li>{@link CarInternalErrorException} when there is an unexpected error detected in cars
     *     <li>{@link PropertyAccessDeniedSecurityException} when cars denied the access of the
     *     property
     *     <li>{@link PropertyNotAvailableAndRetryException} when the property is temporarily
     *     not available and likely that retrying will be successful
     *     <li>{@link PropertyNotAvailableException} when the property is not available and might be
     *     unavailable for a while.
     *     <li>{@link IllegalArgumentException} when the specified class does not match the property
     *     type.
     *     <li>{@code null} when the [propertyId, areaId] is not supported
     * </ul>
     *
     * <p>Clients that declare a {@link android.content.pm.ApplicationInfo#targetSdkVersion}
     * earlier than {@link Build.VERSION_CODES#R} will receive the following exceptions or
     * {@code null} when request failed.
     * <ul>
     *     <li>{@link IllegalStateException} when there is an error detected in cars, or when
     *         cars denied the access of the property, or when the property is not available and
     *         might be unavailable for a while, or when unexpected error happens.
     *     <li>{@link IllegalArgumentException} when the specified class does not match the
     *         property type.
     *     <li>{@code null} when the [propertyId, areaId] is not supported or when the property is
     *     temporarily not available.
     * </ul>
     *
     * <p>For pre-R client, the returned value might be null if the property is temporarily not
     * available. The client should try again in this case.
     *
     * <p>For pre-U client, when the [propertyId, areaId] is not supported, this will return
     * {@code null}.
     *
     * <p>For pre-U client, the returned {@link CarPropertyValue} might contain unavailable or
     * error status. Client must use {@link CarPropertyValue#getStatus} to check. If the returned
     * status is not {@link CarPropertyValue.STATUS_AVAILABLE}, then the value returned via
     * {@link CarPropertyValue#getValue} is undefined.
     *
     * <p>For U and later client, when the [propertyId, areaId] is not supported, this is
     * guaranteed to throw {@code IllegalArgumentException}. This method will never return
     * {@code null}.
     *
     * <p>For U and later client, if the property's status is
     * {@link CarPropertyValue.STATUS_UNAVAILABLE}, then {@link PropertyNotAvailableException} will
     * be thrown. If the property's status is {@link CarPropertyValue.STATUS_ERROR}, then
     * {@link CarInternalErrorException} will be thrown. If no exception is thrown, the returned
     * {@link CarPropertyValue#getStatus} is guaranteed to be
     * {@link CarPropertyValue.STATUS_AVAILABLE} so client do not need to check.
     *
     * @param clazz the class object for the CarPropertyValue
     * @param propertyId the property ID to get
     * @param areaId the area ID of the property to get
     *
     * @throws CarInternalErrorException when there is an unexpected error detected in cars
     * @throws PropertyAccessDeniedSecurityException when cars denied the access of the
     * property
     * @throws PropertyNotAvailableAndRetryException when the property is temporarily
     * not available and likely that retrying will be successful
     * @throws PropertyNotAvailableException when the property is not available and might be
     * unavailable for a while.
     * @throws IllegalArgumentException when the [propertyId, areaId] is not supported for U and
     * later client, or when the specified class does not match the property type.
     *
     * @return the value of a property or {@code null}.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <E> CarPropertyValue<E> getProperty(@NonNull Class<E> clazz, int propertyId,
            int areaId) {
        CarPropertyValue<E> carPropertyValue = getProperty(propertyId, areaId);
        if (carPropertyValue == null) {
            return null;
        }
        Class<?> actualClass = carPropertyValue.getValue().getClass();
        if (actualClass != clazz) {
            throw new IllegalArgumentException(
                    "Invalid property type. " + "Expected: " + clazz + ", but was: "
                            + actualClass);
        }
        return carPropertyValue;
    }

    /**
     * Query {@link CarPropertyValue} with property id and areaId.
     *
     * <p>This method may take couple seconds to complete, so it needs to be called from a
     * non-main thread.
     *
     * <p>Note: Client MUST NOT use one of the following as propertyId, otherwise the behavior is
     * undefined (might throw exception or might return null):
     *
     * <ul>
     * <li>{@code INITIAL_USER_INFO}
     * <li>{@code SWITCH_USER}
     * <li>{@code CREATE_USER}
     * <li>{@code REMOVE_USER}
     * <li>{@code USER_IDENTIFICATION_ASSOCIATION}
     * </ul>
     *
     * <p>Clients that declare a {@link android.content.pm.ApplicationInfo#targetSdkVersion} equal
     * or later than {@link Build.VERSION_CODES#UPSIDE_DOWN_CAKE} will receive the following
     * exceptions when request failed.
     * <ul>
     *     <li>{@link CarInternalErrorException} when there is an unexpected error detected in cars
     *     <li>{@link PropertyAccessDeniedSecurityException} when cars denied the access of the
     *     property
     *     <li>{@link PropertyNotAvailableAndRetryException} when the property is temporarily
     *     not available and likely that retrying will be successful
     *     <li>{@link PropertyNotAvailableException} when the property is not available and might be
     *     unavailable for a while.
     *     <li>{@link IllegalArgumentException} when the [propertyId, areaId] is not supported.
     * </ul>
     *
     * <p>Clients that declare a {@link android.content.pm.ApplicationInfo#targetSdkVersion} equal
     * or later than {@link Build.VERSION_CODES#R}, before
     * {@link Build.VERSION_CODES#UPSIDE_DOWN_CAKE} will
     * receive the following exceptions or {@code null} when request failed.
     * <ul>
     *     <li>{@link CarInternalErrorException} when there is an unexpected error detected in cars
     *     <li>{@link PropertyAccessDeniedSecurityException} when cars denied the access of the
     *     property
     *     <li>{@link PropertyNotAvailableAndRetryException} when the property is temporarily
     *     not available and likely that retrying will be successful
     *     <li>{@link PropertyNotAvailableException} when the property is not available and might be
     *     unavailable for a while.
     *     <li>{@code null} when the [propertyId, areaId] is not supported
     * </ul>
     *
     * <p>Clients that declare a {@link android.content.pm.ApplicationInfo#targetSdkVersion}
     * earlier than {@link Build.VERSION_CODES#R} will receive the following exceptions or
     * {@code null} when request failed.
     * <ul>
     *     <li>{@link IllegalStateException} when there is an error detected in cars, or when
     *         cars denied the access of the property, or when the property is not available and
     *         might be unavailable for a while, or when unexpected error happens.
     *     <li>{@code null} when the [propertyId, areaId] is not supported or when the property is
     *     temporarily not available.
     * </ul>
     *
     * <p>For pre-R client, the returned value might be null if the property is temporarily not
     * available. The client should try again in this case.
     *
     * <p>For pre-U client, when the [propertyId, areaId] is not supported, this will return
     * {@code null}.
     *
     * <p>For pre-U client, the returned {@link CarPropertyValue} might contain unavailable or
     * error status. Client must use {@link CarPropertyValue#getStatus} to check. If the returned
     * status is not {@link CarPropertyValue.STATUS_AVAILABLE}, then the value returned via
     * {@link CarPropertyValue#getValue} is undefined.
     *
     * <p>For U and later client, when the [propertyId, areaId] is not supported, this is
     * guaranteed to throw {@code IllegalArgumentException}. This method will never return
     * {@code null}.
     *
     * <p>For U and later client, if the property's status is
     * {@link CarPropertyValue.STATUS_UNAVAILABLE}, then {@link PropertyNotAvailableException} will
     * be thrown. If the property's status is {@link CarPropertyValue.STATUS_ERROR}, then
     * {@link CarInternalErrorException} will be thrown. If no exception is thrown, the returned
     * {@link CarPropertyValue#getStatus} is guaranteed to be
     * {@link CarPropertyValue.STATUS_AVAILABLE} so client do not need to check.
     *
     * @param propertyId the property ID to get
     * @param areaId the area ID of the property to get
     * @param <E> the class type of the property
     *
     * @throws CarInternalErrorException when there is an unexpected error detected in cars
     * @throws PropertyAccessDeniedSecurityException when cars denied the access of the
     * property
     * @throws PropertyNotAvailableAndRetryException when the property is temporarily
     * not available and likely that retrying will be successful
     * @throws PropertyNotAvailableException when the property is not available and might be
     * unavailable for a while.
     * @throws IllegalArgumentException when the [propertyId, areaId] is not supported for U and
     * later client.
     *
     * @return the value of a property
     */
    @Nullable
    public <E> CarPropertyValue<E> getProperty(int propertyId, int areaId) {
        if (DBG) {
            Log.d(TAG, "getProperty, propertyId: " + VehiclePropertyIds.toString(propertyId)
                    + ", areaId: 0x" + toHexString(areaId));
        }

        assertNotUserHalProperty(propertyId);

        try {
            assertPropertyIdIsSupported(propertyId);
        } catch (IllegalArgumentException e) {
            if (mAppTargetSdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                throw e;
            } else {
                // Return null for pre-U unsupported [propertyId, areaId].
                return null;
            }
        }

        Trace.beginSection("getProperty-" + propertyId + "/" + areaId);
        try {
            CarPropertyValue<E> carPropertyValue = (CarPropertyValue<E>) (runSyncOperation(() -> {
                return mService.getProperty(propertyId, areaId);
            }));
            if (carPropertyValue == null) {
                return null;
            }
            if (mAppTargetSdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (carPropertyValue.getStatus() == CarPropertyValue.STATUS_UNAVAILABLE) {
                    throw new ServiceSpecificException(VehicleHalStatusCode.STATUS_NOT_AVAILABLE,
                            "getProperty returned value with UNAVAILABLE status: "
                                    + carPropertyValue);
                } else if (carPropertyValue.getStatus() != CarPropertyValue.STATUS_AVAILABLE) {
                    throw new ServiceSpecificException(VehicleHalStatusCode.STATUS_INTERNAL_ERROR,
                            "getProperty returned value with error or unknown status: "
                                    + carPropertyValue);
                }
            }
            return carPropertyValue;
        } catch (IllegalArgumentException e) {
            if (mAppTargetSdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                throw e;
            } else {
                // Return null for pre-U unsupported [propertyId, areaId].
                return null;
            }
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, null);
        } catch (ServiceSpecificException e) {
            if (DBG) {
                Log.d(TAG, "getProperty received service specific exception, code: " + e.errorCode);
            }
            if (mAppTargetSdk < Build.VERSION_CODES.R) {
                if (e.errorCode == VehicleHalStatusCode.STATUS_TRY_AGAIN) {
                    return null;
                } else {
                    throw new IllegalStateException("Failed to get propertyId: "
                            + VehiclePropertyIds.toString(propertyId) + " areaId: 0x"
                            + toHexString(areaId), e);
                }
            }
            handleCarServiceSpecificException(e, propertyId, areaId);

            // Never reaches here.
            return null;
        } finally {
            Trace.endSection();
        }
    }

    /**
     * Set value of car property by areaId.
     *
     * <p>If multiple clients set a property for the same area ID simultaneously, which one takes
     * precedence is undefined. Typically, the last set operation (in the order that they are issued
     * to the car's ECU) overrides the previous set operations.
     *
     * <p>This method may take couple seconds to complete, so it needs to be called form a
     * non-main thread.
     *
     * <p>Clients that declare a {@link android.content.pm.ApplicationInfo#targetSdkVersion} equal
     * or later than {@link Build.VERSION_CODES#R} will receive the following exceptions when
     * request failed.
     * <ul>
     *     <li>{@link CarInternalErrorException}
     *     <li>{@link PropertyAccessDeniedSecurityException}
     *     <li>{@link PropertyNotAvailableAndRetryException}
     *     <li>{@link PropertyNotAvailableException}
     *     <li>{@link IllegalArgumentException}
     * </ul>
     * <p>Clients that declare a {@link android.content.pm.ApplicationInfo#targetSdkVersion}
     * earlier than {@link Build.VERSION_CODES#R} will receive the following exceptions when request
     * failed.
     * <ul>
     *     <li>{@link RuntimeException} when the property is temporarily not available.
     *     <li>{@link IllegalStateException} when there is an error detected in cars, or when
     *         cars denied the access of the property, or when the property is not available and
     *         might be unavailable for a while, or when unexpected error happens.
     *     <li>{@link IllegalArgumentException} when the [propertyId, areaId] is not supported.
     * </ul>
     *
     * <p>Returning from this method does not necessary mean the set operation succeeded. In order
     * to determine whether the operation succeeded/failed, Client should use
     * {@link CarPropertyManager#registerCallback} to register for property updates for this
     * [propertyId, areaId] before the set operation. The operation succeeded when
     * {@link CarPropertyEventCallback#onChangeEvent} is called with the value to be set. The
     * operation failed when {@link CarPropertyEventCallback#onErrorEvent} is called for this
     * [propertyId, areaId]. Note that the registration must happen before the set operation
     * otherwise the callback might be invoked after the set operation, but before the registration.
     *
     *
     * <p>Note that if the value to set is the same as the current value, the set request will
     * still be sent to vehicle hardware, however, a new property change event will not be
     * generated for the set operation. If client want to prevent the set request to be sent,
     * client must use {@link getProperty} to check the current value before calling this.
     *
     * @param clazz the class object for the CarPropertyValue
     * @param propertyId the property ID to modify
     * @param areaId the area ID to apply the modification
     * @param val the value to set
     * @param <E> the class type of the given property, for example property that was
     * defined as {@code VEHICLE_VALUE_TYPE_INT32} in vehicle HAL could be accessed using
     * {@code Integer.class}.
     *
     * @throws CarInternalErrorException when there is an unexpected error detected in cars.
     * @throws PropertyAccessDeniedSecurityException when cars denied the access of the property.
     * @throws PropertyNotAvailableException when the property is not available and might be
     * unavailable for a while.
     * @throws PropertyNotAvailableAndRetryException when the property is temporarily not available
     * and likely that retrying will be successful.
     * @throws IllegalArgumentException when the [propertyId, areaId] is not supported.
     */
    public <E> void setProperty(@NonNull Class<E> clazz, int propertyId, int areaId,
            @NonNull E val) {
        if (DBG) {
            Log.d(TAG, "setProperty, propertyId: " + VehiclePropertyIds.toString(propertyId)
                    + ", areaId: 0x" + toHexString(areaId) + ", class: " + clazz + ", val: " + val);
        }

        assertNotUserHalProperty(propertyId);

        assertPropertyIdIsSupported(propertyId);

        Trace.beginSection("setProperty-" + propertyId + "/" + areaId);
        try {
            runSyncOperation(() -> {
                mService.setProperty(new CarPropertyValue<>(propertyId, areaId, val),
                        mCarPropertyEventToService);
                return null;
            });
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        } catch (ServiceSpecificException e) {
            if (DBG) {
                Log.d(TAG, "setProperty received service specific exception", e);
            }
            if (mAppTargetSdk < Build.VERSION_CODES.R) {
                if (e.errorCode == VehicleHalStatusCode.STATUS_TRY_AGAIN) {
                    throw new RuntimeException("Failed to set propertyId: "
                            + VehiclePropertyIds.toString(propertyId) + " areaId: 0x"
                            + toHexString(areaId), e);
                } else {
                    throw new IllegalStateException("Failed to set propertyId: "
                            + VehiclePropertyIds.toString(propertyId) + " areaId: 0x"
                            + toHexString(areaId), e);
                }
            }
            handleCarServiceSpecificException(e, propertyId, areaId);
        } finally {
            Trace.endSection();
        }
    }

    /**
     * Modifies a property.  If the property modification doesn't occur, an error event shall be
     * generated and propagated back to the application.
     *
     * <p>This method may take couple seconds to complete, so it needs to be called from a
     * non-main thread.
     *
     * @param propertyId the property ID to modify
     * @param areaId the area ID to apply the modification
     * @param val the value to set
     */
    public void setBooleanProperty(int propertyId, int areaId, boolean val) {
        setProperty(Boolean.class, propertyId, areaId, val);
    }

    /**
     * Set float value of property
     *
     * <p>This method may take couple seconds to complete, so it needs to be called from a
     * non-main thread.
     *
     * @param propertyId the property ID to modify
     * @param areaId the area ID to apply the modification
     * @param val the value to set
     */
    public void setFloatProperty(int propertyId, int areaId, float val) {
        setProperty(Float.class, propertyId, areaId, val);
    }

    /**
     * Set int value of property
     *
     * <p>This method may take couple seconds to complete, so it needs to be called from a
     * non-main thread.
     *
     * @param propertyId the property ID to modify
     * @param areaId the area ID to apply the modification
     * @param val the value to set
     */
    public void setIntProperty(int propertyId, int areaId, int val) {
        setProperty(Integer.class, propertyId, areaId, val);
    }

    /**
     *  Handles {@code ServiceSpecificException} in {@code CarService} for R and later version.
     */
    private void handleCarServiceSpecificException(
            ServiceSpecificException e, int propertyId, int areaId) {
        // We are not passing the error message down, so log it here.
        Log.w(TAG, "received ServiceSpecificException: " + e);
        int errorCode = CarPropertyErrorCodes.getVhalSystemErrorCode(e.errorCode);
        int vendorErrorCode = CarPropertyErrorCodes.getVhalVendorErrorCode(e.errorCode);

        switch (errorCode) {
            case VehicleHalStatusCode.STATUS_NOT_AVAILABLE:
                throw new PropertyNotAvailableException(propertyId, areaId, vendorErrorCode);
            case VehicleHalStatusCode.STATUS_TRY_AGAIN:
                // Vendor error code is ignored for STATUS_TRY_AGAIN error
                throw new PropertyNotAvailableAndRetryException(propertyId, areaId);
            case VehicleHalStatusCode.STATUS_ACCESS_DENIED:
                // Vendor error code is ignored for STATUS_ACCESS_DENIED error
                throw new PropertyAccessDeniedSecurityException(propertyId, areaId);
            case VehicleHalStatusCode.STATUS_INTERNAL_ERROR:
                throw new CarInternalErrorException(propertyId, areaId, vendorErrorCode);
            case VehicleHalStatusCode.STATUS_NOT_AVAILABLE_DISABLED:
            case VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_LOW:
            case VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_HIGH:
            case VehicleHalStatusCode.STATUS_NOT_AVAILABLE_POOR_VISIBILITY:
            case VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SAFETY:
                throw new PropertyNotAvailableException(propertyId, areaId,
                        getPropertyNotAvailableErrorCodeFromStatusCode(errorCode), vendorErrorCode);
            default:
                Log.e(TAG, "Invalid errorCode: " + errorCode + " in CarService");
                throw new CarInternalErrorException(propertyId, areaId);
        }
    }

    /**
     * Convert {@link VehicleHalStatusCode} into public {@link PropertyNotAvailableErrorCode}
     * equivalents.
     *
     * @throws IllegalArgumentException if an invalid status code is passed in.
     * @hide
     */
    private static int getPropertyNotAvailableErrorCodeFromStatusCode(int statusCode) {
        switch (statusCode) {
            case VehicleHalStatusCode.STATUS_NOT_AVAILABLE:
                return PropertyNotAvailableErrorCode.NOT_AVAILABLE;
            case VehicleHalStatusCode.STATUS_NOT_AVAILABLE_DISABLED:
                return PropertyNotAvailableErrorCode.NOT_AVAILABLE_DISABLED;
            case VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_LOW:
                return PropertyNotAvailableErrorCode.NOT_AVAILABLE_SPEED_LOW;
            case VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_HIGH:
                return PropertyNotAvailableErrorCode.NOT_AVAILABLE_SPEED_HIGH;
            case VehicleHalStatusCode.STATUS_NOT_AVAILABLE_POOR_VISIBILITY:
                return PropertyNotAvailableErrorCode.NOT_AVAILABLE_POOR_VISIBILITY;
            case VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SAFETY:
                return PropertyNotAvailableErrorCode.NOT_AVAILABLE_SAFETY;
            default:
                throw new IllegalArgumentException("Invalid status code: " + statusCode);
        }
    }

    /**
     * Convert {@link VehicleHalStatusCode} system error code into its public
     * {@link DetailedErrorCode} equivalent.
     *
     * @return the detailed error code if available, otherwise set to 0.
     * @throws IllegalArgumentException if an invalid error code is passed in.
     */
    private static int getDetailedErrorCodeFromSystemErrorCode(int systemErrorCode) {
        if (Flags.carPropertyDetailedErrorCodes()) {
            switch (systemErrorCode) {
                case VehicleHalStatusCode.STATUS_OK: // Fallthrough
                case VehicleHalStatusCode.STATUS_TRY_AGAIN: // Fallthrough
                case VehicleHalStatusCode.STATUS_INVALID_ARG: // Fallthrough
                case VehicleHalStatusCode.STATUS_NOT_AVAILABLE: // Fallthrough
                case VehicleHalStatusCode.STATUS_ACCESS_DENIED: // Fallthrough
                case VehicleHalStatusCode.STATUS_INTERNAL_ERROR: // Fallthrough
                    return DetailedErrorCode.NO_DETAILED_ERROR_CODE;
                case VehicleHalStatusCode.STATUS_NOT_AVAILABLE_DISABLED:
                    return DetailedErrorCode.NOT_AVAILABLE_DISABLED;
                case VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_LOW:
                    return DetailedErrorCode.NOT_AVAILABLE_SPEED_LOW;
                case VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_HIGH:
                    return DetailedErrorCode.NOT_AVAILABLE_SPEED_HIGH;
                case VehicleHalStatusCode.STATUS_NOT_AVAILABLE_POOR_VISIBILITY:
                    return DetailedErrorCode.NOT_AVAILABLE_POOR_VISIBILITY;
                case VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SAFETY:
                    return DetailedErrorCode.NOT_AVAILABLE_SAFETY;
                default:
                    throw new IllegalArgumentException("Invalid error code: " + systemErrorCode);
            }
        }

        return 0;
    }

    /**
     * Convert {@link CarPropMgrErrorCode} error code in {@link CarPropertyErrorCodes} into the
     * {@link CarPropertyAsyncErrorCode} equivalent.
     *
     * @return the async error code
     * @throws IllegalArgumentException if an invalid error code is passed in.
     */
    private static int getCarPropertyAsyncErrorCodeFromCarPropertyManagerErrorCode(int errorCode) {
        switch (errorCode) {
            case STATUS_OK: // Fallthrough
            case STATUS_ERROR_INTERNAL_ERROR: // Fallthrough
            case STATUS_ERROR_NOT_AVAILABLE: // Fallthrough
            case STATUS_ERROR_TIMEOUT: // Fallthrough
                return errorCode;
            case STATUS_TRY_AGAIN: // Fallthrough
            default:
                throw new IllegalArgumentException("Invalid error code: " + errorCode);
        }
    }

    private void clearRequestIdToAsyncRequestInfo(
            List<? extends AsyncPropertyRequest> asyncPropertyRequests) {
        if (DBG) {
            Log.d(TAG, "clear pending async requests: " + asyncPropertyRequests);
        }
        synchronized (mLock) {
            for (int i = 0; i < asyncPropertyRequests.size(); i++) {
                mRequestIdToAsyncRequestInfo.remove(asyncPropertyRequests.get(i).getRequestId());
            }
        }
    }

    /**
     * Set an {@code onCancelListener} for the cancellation signal.
     *
     * <p>When the signal is cancelled, car service will remove the stored state for the specified
     * pending request IDs and ignore all the future results.
     */
    private void setOnCancelListener(CancellationSignal cancellationSignal,
            List<Integer> requestIds) {
        cancellationSignal.setOnCancelListener(() -> {
            int[] requestIdsArray = new int[requestIds.size()];
            synchronized (mLock) {
                for (int i = 0; i < requestIds.size(); i++) {
                    int requestId = requestIds.get(i);
                    requestIdsArray[i] = requestId;
                    mRequestIdToAsyncRequestInfo.remove(requestId);
                }
            }
            try {
                mService.cancelRequests(requestIdsArray);
            } catch (RemoteException e) {
                handleRemoteExceptionFromCarService(e);
            }
        });
    }

    /** @hide */
    @Override
    public void onCarDisconnected() {
        synchronized (mLock) {
            mCpeCallbackToCpeCallbackController.clear();
            mPropIdToCpeCallbackControllerList.clear();
            mSubscriptionManager.clear();
        }
    }

    /**
     * Generate unique get request ID and return to the client.
     *
     * @param propertyId the property ID
     * @param areaId the area ID
     * @return the GetPropertyRequest object
     */
    @NonNull
    @SuppressWarnings("FormatString")
    public GetPropertyRequest generateGetPropertyRequest(int propertyId, int areaId) {
        int requestIdCounter = mRequestIdCounter.getAndIncrement();
        if (DBG) {
            Log.d(TAG, String.format("generateGetPropertyRequest, requestId: %d, propertyId: %s, "
                    + "areaId: %d", requestIdCounter, VehiclePropertyIds.toString(propertyId),
                    areaId));
        }
        return new GetPropertyRequest(requestIdCounter, propertyId, areaId);
    }

    /**
     * Generate unique set request ID and return to the client.
     *
     * @param <T> the type for the property value, must be one of Object, Boolean, Float, Integer,
     *      Long, Float[], Integer[], Long[], String, byte[], Object[]
     * @param propertyId the property ID
     * @param areaId the area ID
     * @param value the value to set
     * @return the {@link SetPropertyRequest} object
     */
    @NonNull
    @SuppressWarnings("FormatString")
    public <T> SetPropertyRequest<T> generateSetPropertyRequest(int propertyId, int areaId,
            @NonNull T value) {
        requireNonNull(value);
        int requestIdCounter = mRequestIdCounter.getAndIncrement();
        if (DBG) {
            Log.d(TAG, String.format("generateSetPropertyRequest, requestId: %d, propertyId: %s, "
                    + "areaId: %d, value: %s", requestIdCounter,
                    VehiclePropertyIds.toString(propertyId), areaId, value));
        }
        return new SetPropertyRequest(requestIdCounter, propertyId, areaId, value);
    }

    private void checkAsyncArguments(Object requests, Object callback, long timeoutInMs) {
        requireNonNull(requests);
        requireNonNull(callback);
        if (timeoutInMs <= 0) {
            throw new IllegalArgumentException("timeoutInMs must be a positive number");
        }
    }

    /**
     * Query a list of {@link CarPropertyValue} with property ID and area ID asynchronously.
     *
     * <p>This function would return immediately before the results are ready. For each request,
     * the corresponding result would either be delivered through one
     * {@code resultCallback.onSuccess} call if the request succeeded or through one
     * {@code errorCallback.onFailure} call if failed. It is guaranteed that the total times the
     * callback functions are called is equal to the number of requests if this function does not
     * throw an exception. It is guaranteed that none of the callback functions are called if an
     * exception is thrown. If the {@code callbackExecutor} is {@code null}, the callback will be
     * executed on the default event handler thread. If the callback is doing heavy work, it is
     * recommended that the {@code callbackExecutor} is provided.
     *
     * <p>If the operation is cancelled, it is guaranteed that no more callbacks will be called.
     *
     * <p>For one request, if the property's status is not available,
     * {@code errorCallback.onFailure} will be called once with {@link #STATUS_ERROR_NOT_AVAILABLE}.
     *
     * <p>For one request, if the property's status is error,
     * {@code errorCallback.onFailure} will be called once with {@link
     * #STATUS_ERROR_INTERNAL_ERROR}.
     *
     * @param getPropertyRequests a list of properties to get
     * @param timeoutInMs the timeout for the operation, in milliseconds
     * @param cancellationSignal a signal that could be used to cancel the on-going operation
     * @param callbackExecutor the executor to execute the callback with
     * @param getPropertyCallback the callback function to deliver the result
     * @throws SecurityException if missing permission to read one of the specific properties.
     * @throws IllegalArgumentException if one of the properties to read is not supported.
     */
    public void getPropertiesAsync(
            @NonNull List<GetPropertyRequest> getPropertyRequests,
            long timeoutInMs,
            @Nullable CancellationSignal cancellationSignal,
            @Nullable @CallbackExecutor Executor callbackExecutor,
            @NonNull GetPropertyCallback getPropertyCallback) {
        if (DBG) {
            Log.d(TAG, "getPropertiesAsync, requests: " + getPropertyRequests + ", timeoutInMs: "
                    + timeoutInMs + ", callback: " + getPropertyCallback);
        }

        checkAsyncArguments(getPropertyRequests, getPropertyCallback, timeoutInMs);
        if (callbackExecutor == null) {
            callbackExecutor = new HandlerExecutor(getEventHandler());
        }

        List<AsyncPropertyServiceRequest> getPropertyServiceRequests = new ArrayList<>(
                getPropertyRequests.size());
        for (int i = 0; i < getPropertyRequests.size(); i++) {
            GetPropertyRequest getPropertyRequest = getPropertyRequests.get(i);
            int propertyId = getPropertyRequest.getPropertyId();
            assertPropertyIdIsSupported(propertyId);

            getPropertyServiceRequests.add(AsyncPropertyServiceRequest.newGetAsyncRequest(
                    getPropertyRequest));
        }

        List<Integer> requestIds = storePendingRequestInfo(getPropertyRequests, callbackExecutor,
                getPropertyCallback);

        try {
            if (DBG) {
                Log.d(TAG, "calling CarPropertyService.getPropertiesAsync");
            }
            mService.getPropertiesAsync(new AsyncPropertyServiceRequestList(
                    getPropertyServiceRequests), mAsyncPropertyResultCallback, timeoutInMs);
            if (DBG) {
                Log.d(TAG, "CarPropertyService.getPropertiesAsync succeed");
            }
        } catch (RemoteException e) {
            clearRequestIdToAsyncRequestInfo(getPropertyRequests);
            handleRemoteExceptionFromCarService(e);
        } catch (IllegalArgumentException | SecurityException e) {
            clearRequestIdToAsyncRequestInfo(getPropertyRequests);
            throw e;
        }
        if (cancellationSignal != null) {
            setOnCancelListener(cancellationSignal, requestIds);
        }
    }

    /**
     * Query a list of {@link CarPropertyValue} with property Id and area Id asynchronously.
     *
     * Same as {@link CarPropertyManager#getPropertiesAsync(List, long, CancellationSignal,
     * Executor, GetPropertyCallback)} with default timeout 10s.
     */
    public void getPropertiesAsync(
            @NonNull List<GetPropertyRequest> getPropertyRequests,
            @Nullable CancellationSignal cancellationSignal,
            @Nullable @CallbackExecutor Executor callbackExecutor,
            @NonNull GetPropertyCallback getPropertyCallback) {
        getPropertiesAsync(getPropertyRequests, ASYNC_GET_DEFAULT_TIMEOUT_MS, cancellationSignal,
                callbackExecutor, getPropertyCallback);
    }

    /**
     * Sets a list of car property values asynchronously.
     *
     * <p>This function would return immediately before the results are ready. For each request,
     * the corresponding result would either be delivered through one
     * {@code resultCallback.onSuccess} call if the request succeeded or through one
     * {@code errorCallback.onFailure} call if failed. It is guaranteed that the total times the
     * callback functions are called is equal to the number of requests if this function does not
     * throw an exception. It is guaranteed that none of the callback functions are called if an
     * exception is thrown. If the {@code callbackExecutor} is {@code null}, the callback will be
     * executed on the default event handler thread. If the callback is doing heavy work, it is
     * recommended that the {@code callbackExecutor} is provided.
     *
     * <p>If the operation is cancelled, it is guaranteed that no more callbacks will be called.
     *
     * <p>If multiple clients set a property for the same area ID simultaneously, which one takes
     * precedence is undefined. Typically, the last set operation (in the order that they are issued
     * to the car's ECU) overrides the previous set operations.
     *
     * <p>When the success callback will be called depends on whether {@code waitForPropertyUpdate}
     * for each request is set. If this is set to {@code true} (by default), the success callback
     * will be called when the set operation is successfully delivered to vehicle bus AND either
     * target value is the same as the current or when the property is updated to the target value.
     *
     * <p>When {@code waitForPropertyUpdate} is set to {@code false}, the success callback will be
     * called as long as the set operation is successfully delivered to vehicle bus.
     *
     * <p>Under most cases, client should wait for the property update to verify that the set
     * operation actually succeeded.
     *
     * <p>For cases when the property is write-only (no way to get property update event) or when
     * the property represents some action, instead of an actual state, e.g. key stroke where the
     * property's current value is not meaningful, caller must set {@code waitForPropertyUpdate}
     * to {@code false}.
     *
     * <p>For {@code HVAC_TEMPERATURE_VALUE_SUGGESTION}, this must be set to {@code false}
     * because the updated property value will not be the same as the value to be set.
     *
     * @param setPropertyRequests a list of properties to set
     * @param timeoutInMs the timeout for the operation, in milliseconds
     * @param cancellationSignal a signal that could be used to cancel the on-going operation
     * @param callbackExecutor the executor to execute the callback with
     * @param setPropertyCallback the callback function to deliver the result
     * @throws SecurityException if missing permission to write one of the specific properties.
     * @throws IllegalArgumentException if one of the properties to set is not supported.
     * @throws IllegalArgumentException if one of the properties is not readable and does not set
     *   {@code waitForPropertyUpdate} to {@code false}.
     * @throws IllegalArgumentException if one of the properties is
     *   {@code HVAC_TEMPERATURE_VALUE_SUGGESTION} and does not set {@code waitForPropertyUpdate}
     *   to {@code false}.
     */
    public void setPropertiesAsync(
            @NonNull List<SetPropertyRequest<?>> setPropertyRequests,
            long timeoutInMs,
            @Nullable CancellationSignal cancellationSignal,
            @Nullable @CallbackExecutor Executor callbackExecutor,
            @NonNull SetPropertyCallback setPropertyCallback) {
        if (DBG) {
            Log.d(TAG, "setPropertiesAsync, requests: " + setPropertyRequests + ", timeoutInMs: "
                    + timeoutInMs + ", callback: " + setPropertyCallback);
        }

        checkAsyncArguments(setPropertyRequests, setPropertyCallback, timeoutInMs);
        if (callbackExecutor == null) {
            callbackExecutor = new HandlerExecutor(getEventHandler());
        }

        List<AsyncPropertyServiceRequest> setPropertyServiceRequests = new ArrayList<>(
                setPropertyRequests.size());
        for (int i = 0; i < setPropertyRequests.size(); i++) {
            SetPropertyRequest setPropertyRequest = setPropertyRequests.get(i);
            int propertyId = setPropertyRequest.getPropertyId();
            requireNonNull(setPropertyRequest.getValue());
            assertPropertyIdIsSupported(propertyId);

            setPropertyServiceRequests.add(AsyncPropertyServiceRequest.newSetAsyncRequest(
                    setPropertyRequest));
        }

        List<Integer> requestIds = storePendingRequestInfo(setPropertyRequests, callbackExecutor,
                setPropertyCallback);

        try {
            if (DBG) {
                Log.d(TAG, "calling CarPropertyService.setPropertiesAsync");
            }
            mService.setPropertiesAsync(new AsyncPropertyServiceRequestList(
                    setPropertyServiceRequests), mAsyncPropertyResultCallback, timeoutInMs);
            if (DBG) {
                Log.d(TAG, "CarPropertyService.setPropertiesAsync succeed");
            }
        } catch (RemoteException e) {
            clearRequestIdToAsyncRequestInfo(setPropertyRequests);
            handleRemoteExceptionFromCarService(e);
        } catch (IllegalArgumentException | SecurityException e) {
            clearRequestIdToAsyncRequestInfo(setPropertyRequests);
            throw e;
        }
        if (cancellationSignal != null) {
            setOnCancelListener(cancellationSignal, requestIds);
        }
    }

    /**
     * Sets a list of car property values asynchronously.
     *
     * Same as {@link CarPropertyManager#setPropertiesAsync(List, long, CancellationSignal,
     * Executor, SetPropertyCallback)} with default timeout 10s.
     */
    public void setPropertiesAsync(
            @NonNull List<SetPropertyRequest<?>> setPropertyRequests,
            @Nullable CancellationSignal cancellationSignal,
            @Nullable @CallbackExecutor Executor callbackExecutor,
            @NonNull SetPropertyCallback setPropertyCallback) {
        setPropertiesAsync(setPropertyRequests, ASYNC_GET_DEFAULT_TIMEOUT_MS, cancellationSignal,
                callbackExecutor, setPropertyCallback);
    }

    private void assertPropertyIdIsSupported(int propertyId) {
        if (!CarPropertyHelper.isSupported(propertyId)) {
            throw new IllegalArgumentException("The property: "
                    + VehiclePropertyIds.toString(propertyId) + " is unsupported");
        }
    }

    private <RequestType extends AsyncPropertyRequest, CallbackType> List<Integer>
            storePendingRequestInfo(
                    List<RequestType> requests, Executor callbackExecutor, CallbackType callback) {
        if (DBG) {
            Log.d(TAG, "store pending async requests: " + requests);
        }
        List<Integer> requestIds = new ArrayList<>();
        SparseArray<AsyncPropertyRequestInfo<?, ?>> requestInfoToAdd = new SparseArray<>();
        synchronized (mLock) {
            for (int i = 0; i < requests.size(); i++) {
                RequestType request = requests.get(i);
                AsyncPropertyRequestInfo<RequestType, CallbackType> requestInfo =
                        new AsyncPropertyRequestInfo(request, callbackExecutor, callback);
                int requestId = request.getRequestId();
                requestIds.add(requestId);
                if (mRequestIdToAsyncRequestInfo.contains(requestId)
                        || requestInfoToAdd.contains(requestId)) {
                    throw new IllegalArgumentException(
                            "Request ID: " + requestId + " already exists");
                }
                requestInfoToAdd.put(requestId, requestInfo);
            }
            for (int i = 0; i < requestInfoToAdd.size(); i++) {
                mRequestIdToAsyncRequestInfo.put(requestInfoToAdd.keyAt(i),
                        requestInfoToAdd.valueAt(i));
            }
        }
        return requestIds;
    }

    private List<CarSubscription> sanitizeSubscribeOptions(List<CarSubscription> subscribeOptions)
            throws IllegalArgumentException, IllegalStateException {
        ArraySet<Integer> propertyIds = new ArraySet<>();
        for (int i = 0; i < subscribeOptions.size(); i++) {
            propertyIds.add(subscribeOptions.get(i).propertyId);
        }
        CarPropertyConfigs configs = getPropertyConfigsFromService(propertyIds);
        if (configs == null) {
            throw new IllegalStateException("Failed to get property config list from car service");
        }

        List<CarSubscription> output = new ArrayList<>();
        for (int i = 0; i < subscribeOptions.size(); i++) {
            CarSubscription subscribeOption = subscribeOptions.get(i);
            int propertyId = subscribeOption.propertyId;

            if (configs.isNotSupported(propertyId)) {
                String errorMessage = "propertyId is not in carPropertyConfig list: "
                        + VehiclePropertyIds.toString(propertyId);
                Log.e(TAG, "sanitizeUpdateRate: " + errorMessage);
                throw new IllegalArgumentException(errorMessage);
            }
            if (configs.missingPermission(propertyId)) {
                // This should not happen since we already checked whether the caller has read
                // permission via getSupportedNoReadPermPropIds. If the caller does not have
                // read or write permission, {@code SecurityException} should be thrown before this.
                String errorMessage = "missing required read/write permission for: "
                        + VehiclePropertyIds.toString(propertyId);
                Log.wtf(TAG, "sanitizeUpdateRate: " + errorMessage);
                throw new SecurityException(errorMessage);
            }

            CarPropertyConfig<?> carPropertyConfig = configs.getConfig(propertyId);
            CarSubscription carSubscription = new CarSubscription();
            carSubscription.propertyId = propertyId;
            carSubscription.areaIds = subscribeOption.areaIds;
            if (carSubscription.areaIds.length == 0) {
                // Subscribe to all areaIds if not specified.
                carSubscription.areaIds = carPropertyConfig.getAreaIds();
            }
            carSubscription.enableVariableUpdateRate =
                    subscribeOption.enableVariableUpdateRate;
            carSubscription.updateRateHz = InputSanitizationUtils.sanitizeUpdateRateHz(
                    carPropertyConfig, subscribeOption.updateRateHz);
            float resolution = mFeatureFlags.subscriptionWithResolution()
                    ? subscribeOption.resolution : 0.0f;
            carSubscription.resolution = InputSanitizationUtils.sanitizeResolution(mFeatureFlags,
                    carPropertyConfig, resolution);
            output.addAll(InputSanitizationUtils.sanitizeEnableVariableUpdateRate(
                    mFeatureFlags, carPropertyConfig, carSubscription));
        }
        return output;
    }

    /**
     * Checks if the given property ID is one of the user HAL property.
     *
     * <p>Properties related to user management should only be manipulated by
     * {@code UserHalService} and should not be used by directly by the client.
     *
     * <p>This check is no longer necessary for clients after U, but this logic exists before U so
     * we still need this to keep backward compatibility for clients before U.
     *
     * @param propId property to be checked
     *
     * @throws IllegalArgumentException if the property is not supported.
     */
    private void assertNotUserHalProperty(int propId) {
        if (mAppTargetSdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // After Android U, we treat this the same as other unsupported property IDs and this
            // special logic is no longer required.
            return;
        }
        switch (propId) {
            case VehiclePropertyIds.INITIAL_USER_INFO:
            case VehiclePropertyIds.SWITCH_USER:
            case VehiclePropertyIds.CREATE_USER:
            case VehiclePropertyIds.REMOVE_USER:
            case VehiclePropertyIds.USER_IDENTIFICATION_ASSOCIATION:
                throw new IllegalArgumentException("Unsupported property: "
                        + VehiclePropertyIds.toString(propId) + " (" + propId + ")");
        }
    }

    private List<Integer> filterOutUserHalProperty(List<Integer> propertyIds) {
        if (mAppTargetSdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // After Android U, we treat this the same as other unsupported property IDs and this
            // special logic is no longer required.
            return propertyIds;
        }
        List<Integer> filteredPropertyIds = new ArrayList<>();
        for (int i = 0; i < propertyIds.size(); i++) {
            switch (propertyIds.get(i)) {
                case VehiclePropertyIds.INITIAL_USER_INFO:
                case VehiclePropertyIds.SWITCH_USER:
                case VehiclePropertyIds.CREATE_USER:
                case VehiclePropertyIds.REMOVE_USER:
                case VehiclePropertyIds.USER_IDENTIFICATION_ASSOCIATION:
                    continue;
            }
            filteredPropertyIds.add(propertyIds.get(i));
        }
        return filteredPropertyIds;
    }

    private int[] getSupportedNoReadPermPropIds(List<CarSubscription> subscribeOptions)
            throws RemoteException {
        ArraySet<Integer> propertyIds = new ArraySet<>();
        for (int i = 0; i < subscribeOptions.size(); i++) {
            propertyIds.add(subscribeOptions.get(i).propertyId);
        }
        int[] propertyIdsArray = new int[propertyIds.size()];
        for (int i = 0; i < propertyIds.size(); i++) {
            propertyIdsArray[i] = propertyIds.valueAt(i);
        }

        return mService.getSupportedNoReadPermPropIds(propertyIdsArray);
    }

    // Wraps the result returned from {@code ICarProperty.getPropertyConfigList}.
    private static final class CarPropertyConfigs {
        private final SparseArray<CarPropertyConfig<?>> mCarPropertyConfigById =
                new SparseArray<>();
        private final ArraySet<Integer> mMissingPermissionPropIds = new ArraySet<>();
        private final ArraySet<Integer> mUnsupportedPropIds = new ArraySet<>();
        private final GetPropertyConfigListResult mResult;

        // The unsupportedPropIds are the property Ids we filtered out before we send out
        // the request to car service to get the configs.
        CarPropertyConfigs(GetPropertyConfigListResult result, IntArray unsupportedPropIds) {
            mResult = result;
            List<CarPropertyConfig> configs = result.carPropertyConfigList.getConfigs();
            for (int i = 0; i < configs.size(); i++) {
                mCarPropertyConfigById.put(configs.get(i).getPropertyId(), configs.get(i));
            }
            for (int i = 0; i < result.missingPermissionPropIds.length; i++) {
                mMissingPermissionPropIds.add(result.missingPermissionPropIds[i]);
            }
            for (int i = 0; i < result.unsupportedPropIds.length; i++) {
                mUnsupportedPropIds.add(result.unsupportedPropIds[i]);
            }
            for (int i = 0; i < unsupportedPropIds.size(); i++) {
                mUnsupportedPropIds.add(unsupportedPropIds.get(i));
            }
        }

        // For the propertyIds provided to {@code getPropertyConfigsFromService}, this must not
        // return null if both {@code isNotSupported} and {@code missingPermission} is false.
        @Nullable
        CarPropertyConfig<?> getConfig(int propertyId) {
            return mCarPropertyConfigById.get(propertyId);
        }

        // Returns whether the property is not supported.
        boolean isNotSupported(int propertyId) {
            return mUnsupportedPropIds.contains(propertyId);
        }

        // Returns whether the caller does not have read and does not have write access to this
        // property, hence the caller cannot get the property's config.
        boolean missingPermission(int propertyId) {
            return mMissingPermissionPropIds.contains(propertyId);
        }

        List<CarPropertyConfig> getConfigs() {
            return mResult.carPropertyConfigList.getConfigs();
        }

        int[] getMissingPermissionPropIds() {
            return mResult.missingPermissionPropIds;
        }

        int[] getUnsupportedPropIds() {
            return mResult.unsupportedPropIds;
        }
    }

    @Nullable
    private CarPropertyConfigs getPropertyConfigsFromService(Iterable<Integer> propertyIds) {
        IntArray filteredPropertyIds = new IntArray();
        IntArray unsupportedPropertyIds = new IntArray();
        for (int propertyId : propertyIds) {
            assertNotUserHalProperty(propertyId);
            if (!CarPropertyHelper.isSupported(propertyId)) {
                unsupportedPropertyIds.add(propertyId);
                continue;
            }
            filteredPropertyIds.add(propertyId);
        }
        GetPropertyConfigListResult result;
        try {
            result = mService.getPropertyConfigList(filteredPropertyIds.toArray());
        } catch (RemoteException e) {
            Log.e(TAG, "CarPropertyService.getPropertyConfigList exception ", e);
            return handleRemoteExceptionFromCarService(e, null);
        }
        return new CarPropertyConfigs(result, unsupportedPropertyIds);
    }

}
