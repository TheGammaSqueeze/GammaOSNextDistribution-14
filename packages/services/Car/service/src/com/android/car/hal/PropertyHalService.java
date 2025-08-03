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
package com.android.car.hal;

import static android.car.hardware.CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC;
import static android.car.hardware.property.CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_ACCESS_DENIED;
import static android.car.hardware.property.CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_INVALID_ARG;
import static android.car.hardware.property.CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_PROPERTY_NOT_AVAILABLE;
import static android.car.hardware.property.CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_TRY_AGAIN;
import static android.car.hardware.property.CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_UNKNOWN;
import static android.car.hardware.property.VehicleHalStatusCode.STATUS_ACCESS_DENIED;
import static android.car.hardware.property.VehicleHalStatusCode.STATUS_INTERNAL_ERROR;
import static android.car.hardware.property.VehicleHalStatusCode.STATUS_INVALID_ARG;
import static android.car.hardware.property.VehicleHalStatusCode.STATUS_NOT_AVAILABLE;
import static android.car.hardware.property.VehicleHalStatusCode.STATUS_NOT_AVAILABLE_DISABLED;
import static android.car.hardware.property.VehicleHalStatusCode.STATUS_NOT_AVAILABLE_POOR_VISIBILITY;
import static android.car.hardware.property.VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SAFETY;
import static android.car.hardware.property.VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_HIGH;
import static android.car.hardware.property.VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_LOW;
import static android.car.hardware.property.VehicleHalStatusCode.STATUS_TRY_AGAIN;

import static com.android.car.internal.common.CommonConstants.EMPTY_INT_ARRAY;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;
import static com.android.car.internal.property.CarPropertyErrorCodes.convertVhalStatusCodeToCarPropertyManagerErrorCodes;
import static com.android.car.internal.property.CarPropertyErrorCodes.STATUS_OK;
import static com.android.car.internal.property.CarPropertyHelper.isSystemProperty;
import static com.android.car.internal.property.GetSetValueResult.newGetValueResult;
import static com.android.car.internal.property.InputSanitizationUtils.sanitizeUpdateRateHz;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.car.VehiclePropertyIds;
import android.car.builtin.os.BuildHelper;
import android.car.builtin.util.Slogf;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.CarPropertyManager.CarSetPropertyErrorCode;
import android.car.hardware.property.VehicleHalStatusCode.VehicleHalStatusCodeInt;
import android.content.Context;
import android.hardware.automotive.vehicle.VehiclePropError;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.hardware.automotive.vehicle.VehiclePropertyStatus;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import com.android.car.CarLog;
import com.android.car.CarServiceUtils;
import com.android.car.VehicleStub;
import com.android.car.VehicleStub.AsyncGetSetRequest;
import com.android.car.VehicleStub.GetVehicleStubAsyncResult;
import com.android.car.VehicleStub.SetVehicleStubAsyncResult;
import com.android.car.VehicleStub.VehicleStubCallbackInterface;
import com.android.car.hal.VehicleHal.HalSubscribeOptions;
import com.android.car.hal.property.PropertyHalServiceConfigs;
import com.android.car.hal.property.PropertyPermissionInfo.PermissionCondition;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.LongPendingRequestPool;
import com.android.car.internal.LongPendingRequestPool.TimeoutCallback;
import com.android.car.internal.LongRequestIdWithTimeout;
import com.android.car.internal.property.AsyncPropertyServiceRequest;
import com.android.car.internal.property.CarPropertyErrorCodes;
import com.android.car.internal.property.CarPropertyHelper;
import com.android.car.internal.property.CarSubscription;
import com.android.car.internal.property.GetSetValueResult;
import com.android.car.internal.property.GetSetValueResultList;
import com.android.car.internal.property.IAsyncPropertyResultCallback;
import com.android.car.internal.property.SubscriptionManager;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.internal.util.PairSparseArray;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.expresslog.Histogram;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Common interface for HAL services that send Vehicle Properties back and forth via ICarProperty.
 * Services that communicate by passing vehicle properties back and forth via ICarProperty should
 * extend this class.
 */
public class PropertyHalService extends HalServiceBase {
    private static final String TAG = CarLog.tagFor(PropertyHalService.class);
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);
    private static final int ASYNC_RETRY_SLEEP_IN_MS = 100;

    // Async get request from user.
    private static final int GET = 0;
    // Async set request from user.
    private static final int SET = 1;
    // Async get request for getting initial value when user issues async set property request.
    // The reason we need to get initial value is that if the value to be set is the same as
    // the current value, there might not be a property update event generated. In this case,
    // it should be considered a success. If we get the initial value successfully and the
    // initial value is the same as the target value, we treat the async set as success.
    private static final int GET_INITIAL_VALUE_FOR_SET = 2;
    private static final float UPDATE_RATE_ERROR = -1f;

    // A fake pending request ID for car property service.
    private static final int CAR_PROP_SVC_REQUEST_ID = -1;

    // Only changed in testing.
    private PropertyHalServiceConfigs mPropertyHalServiceConfigs =
            PropertyHalServiceConfigs.getInstance();

    @GuardedBy("mLock")
    private final PairSparseArray<CarPropertyValue> mStaticPropertyIdAreaIdCache =
            new PairSparseArray<>();

    private static final Histogram sGetAsyncEndToEndLatencyHistogram = new Histogram(
            "automotive_os.value_get_async_end_to_end_latency",
            new Histogram.ScaledRangeOptions(/* binCount= */ 20, /* minValue= */ 0,
                    /* firstBinWidth= */ 2, /* scaleFactor= */ 1.5f));

    private static final Histogram sSetAsyncEndToEndLatencyHistogram = new Histogram(
            "automotive_os.value_set_async_end_to_end_latency",
            new Histogram.ScaledRangeOptions(/* binCount= */ 20, /* minValue= */ 0,
                    /* firstBinWidth= */ 2, /* scaleFactor= */ 1.5f));

    // Different type of async get/set property requests.
    @IntDef({GET, SET, GET_INITIAL_VALUE_FOR_SET})
    @Retention(RetentionPolicy.SOURCE)
    private @interface AsyncRequestType {}

    private static final class GetSetValueResultWrapper {
        private GetSetValueResult mGetSetValueResult;
        private long mAsyncRequestStartTime;
        private final int mRetryCount;

        private GetSetValueResultWrapper(GetSetValueResult getSetValueResult,
                long asyncRequestStartTime, int retryCount) {
            mGetSetValueResult = getSetValueResult;
            mAsyncRequestStartTime = asyncRequestStartTime;
            mRetryCount = retryCount;
        }

        private GetSetValueResult getGetSetValueResult() {
            return mGetSetValueResult;
        }

        private long getAsyncRequestStartTime() {
            return mAsyncRequestStartTime;
        }

        private int getRetryCount() {
            return mRetryCount;
        }
    }

    private static final class AsyncPropRequestInfo implements LongRequestIdWithTimeout {
        private final AsyncPropertyServiceRequest mPropMgrRequest;
        // The uptimeMillis when this request time out.
        private final long mTimeoutUptimeMs;
        private final @AsyncRequestType int mRequestType;
        private final VehicleStubCallback mVehicleStubCallback;
        private final long mAsyncRequestStartTime;
        private boolean mSetRequestSent;
        private long mUpdateTimestampNanos;
        private boolean mValueUpdated;
        private int mServiceRequestId;
        private float mUpdateRateHz;
        private int mRetryCount;
        // The associated async set request for get_initial_value request.
        private @Nullable AsyncPropRequestInfo mAssocSetValueRequestInfo;
        // The associated get initial value request for async set request.
        private @Nullable AsyncPropRequestInfo mAssocGetInitValueRequestInfo;

        AsyncPropRequestInfo(@AsyncRequestType int requestType,
                AsyncPropertyServiceRequest propMgrRequest,
                long timeoutUptimeMs, VehicleStubCallback vehicleStubCallback,
                long asyncRequestStartTime) {
            mPropMgrRequest = propMgrRequest;
            mTimeoutUptimeMs = timeoutUptimeMs;
            mRequestType = requestType;
            mVehicleStubCallback = vehicleStubCallback;
            mAsyncRequestStartTime = asyncRequestStartTime;
        }

        private @AsyncRequestType int getRequestType() {
            return mRequestType;
        }

        private int getManagerRequestId() {
            return mPropMgrRequest.getRequestId();
        }


        private String getPropertyName() {
            return VehiclePropertyIds.toString(getPropertyId());
        }

        int getPropertyId() {
            return mPropMgrRequest.getPropertyId();
        }

        int getAreaId() {
            return mPropMgrRequest.getAreaId();
        }

        public long getUpdateTimestampNanos() {
            return mUpdateTimestampNanos;
        }

        AsyncPropertyServiceRequest getPropSvcRequest() {
            return mPropMgrRequest;
        }

        GetSetValueResult toErrorResult(CarPropertyErrorCodes errorCodes) {
            return GetSetValueResult.newErrorResult(getManagerRequestId(), errorCodes);
        }

        GetSetValueResult toGetValueResult(CarPropertyValue value) {
            return newGetValueResult(getManagerRequestId(), value);
        }

        GetSetValueResult toSetValueResult(long updateTimestampNanos) {
            return GetSetValueResult.newSetValueResult(getManagerRequestId(),
                    updateTimestampNanos);
        }

        void setSetRequestSent() {
            mSetRequestSent = true;
        }

        void setValueUpdated(long updateTimestampNanos) {
            mValueUpdated = true;
            mUpdateTimestampNanos = updateTimestampNanos;
        }

        boolean isWaitForPropertyUpdate() {
            return mPropMgrRequest.isWaitForPropertyUpdate();
        }

        boolean success() {
            // If the set request is sent and either we don't wait for property update or the
            // property update happened (which includes the initial value is already the target
            // value)
            return mSetRequestSent && (!isWaitForPropertyUpdate() || mValueUpdated);
        }

        void setAssocSetValueRequestInfo(AsyncPropRequestInfo requestInfo) {
            mAssocSetValueRequestInfo = requestInfo;
        }

        @Nullable AsyncPropRequestInfo getAssocSetValueRequestInfo() {
            return mAssocSetValueRequestInfo;
        }

        void setAssocGetInitValueRequestInfo(AsyncPropRequestInfo requestInfo) {
            mAssocGetInitValueRequestInfo = requestInfo;
        }

        @Nullable AsyncPropRequestInfo getAssocGetInitValueRequestInfo() {
            return mAssocGetInitValueRequestInfo;
        }

        void setServiceRequestId(int serviceRequestId) {
            mServiceRequestId = serviceRequestId;
        }

        int getServiceRequestId() {
            return mServiceRequestId;
        }

        VehicleStubCallback getVehicleStubCallback() {
            return mVehicleStubCallback;
        }

        float getUpdateRateHz() {
            return mUpdateRateHz;
        }

        void incrementRetryCount() {
            mRetryCount++;
        }

        int getRetryCount() {
            return mRetryCount;
        }

        /**
         * Parses the updateRateHz from client and sanitize it.
         */
        void parseClientUpdateRateHz(CarPropertyConfig carPropertyConfig) {
            float clientUpdateRateHz = mPropMgrRequest.getUpdateRateHz();
            if (clientUpdateRateHz == 0.0f) {
                // If client does not specify a update rate for async set, subscribe at the max
                // update rate so that we can get the property update as soon as possible.
                clientUpdateRateHz = carPropertyConfig.getMaxSampleRate();
            }
            mUpdateRateHz = sanitizeUpdateRateHz(carPropertyConfig, clientUpdateRateHz);
        }

        @Override
        public long getTimeoutUptimeMs() {
            return mTimeoutUptimeMs;
        }

        @Override
        public long getRequestId() {
            return getServiceRequestId();
        }

        public long getAsyncRequestStartTime() {
            return mAsyncRequestStartTime;
        }

        @Override
        @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
        public String toString() {
            return new StringBuilder()
                    .append("AsyncPropRequestInfo{type: ")
                    .append(requestTypeToString(mRequestType))
                    .append(", mgrRequestId: ")
                    .append(getManagerRequestId())
                    .append(", property: ")
                    .append(getPropertyName())
                    .append(", areaId: ")
                    .append(getAreaId())
                    .append(", timeout at uptime: ")
                    .append(getTimeoutUptimeMs()).append("ms")
                    .append(", serviceRequestId: ")
                    .append(getServiceRequestId())
                    .append(", update rate: ")
                    .append(getUpdateRateHz()).append("hz")
                    .append(", value updated for set: ")
                    .append(mValueUpdated)
                    .append(", request sent for set: ")
                    .append(mSetRequestSent)
                    .append("}").toString();
        }
    };

    // The request ID passed by CarPropertyService (ManagerRequestId) is directly passed from
    // CarPropertyManager. Multiple CarPropertyManagers use the same car service instance, thus,
    // the ManagerRequestId is not unique. We have to create another unique ID called
    // ServiceRequestId and pass it to underlying layer (VehicleHal and VehicleStub).
    // Internally, we will map ManagerRequestId to ServiceRequestId.
    private final AtomicInteger mServiceRequestIdCounter = new AtomicInteger(0);
    private final VehicleHal mVehicleHal;
    private final HalPropValueBuilder mPropValueBuilder;
    private final HandlerThread mHandlerThread =
            CarServiceUtils.getHandlerThread(getClass().getSimpleName());
    private final Handler mHandler = new Handler(mHandlerThread.getLooper());
    private final TimeoutCallback mTimeoutCallback = new AsyncRequestTimeoutCallback();

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final Map<IBinder, VehicleStubCallback>
            mResultBinderToVehicleStubCallback = new ArrayMap<>();
    @GuardedBy("mLock")
    private final SparseArray<HalPropConfig> mHalPropIdToPropConfig =
            new SparseArray<>();
    @GuardedBy("mLock")
    private final SparseArray<Pair<String, String>> mMgrPropIdToPermissions = new SparseArray<>();
    // A pending request pool to store all pending async get/set property request info.
    // Service request ID is int, not long, but we only have one version of PendingRequestPool.
    @GuardedBy("mLock")
    private final LongPendingRequestPool<AsyncPropRequestInfo> mPendingAsyncRequests =
            new LongPendingRequestPool<>(mHandler.getLooper(), mTimeoutCallback);
    @GuardedBy("mLock")
    private PropertyHalListener mPropertyHalListener;
    // A map to store pending async set request info that are currently waiting for property update
    // events.
    @GuardedBy("mLock")
    private final SparseArray<List<AsyncPropRequestInfo>> mHalPropIdToWaitingUpdateRequestInfo =
            new SparseArray<>();

    // CarPropertyService subscribes to properties through PropertyHalService. Meanwhile,
    // PropertyHalService internally also subscribes to some property for async set operations.
    // We need to merge both of these subscription rate to VHAL.
    //
    // This manager uses async property request ID as key to store subscription caused by async
    // set operations. It uses CAR_PROP_SVC_REQUEST_ID as a fake key to store subscription caused
    // by car property service.
    //
    // For example, if we internally subscribed to [propA, areaA] at 10hz, client requests at 20hz,
    // then we need to tell VHAL to update the rate to 20hz. If we internally subscribed at 20hz,
    // client requests at 10hz, then we should do nothing, however, if we internally unsubscribe,
    // then the [propA, areaA] should be subscribed at 10hz.
    @GuardedBy("mLock")
    private final SubscriptionManager<Integer> mSubManager = new SubscriptionManager<>();

    private class AsyncRequestTimeoutCallback implements TimeoutCallback {
        @Override
        public void onRequestsTimeout(List<Long> serviceRequestIds) {
            ArrayMap<VehicleStubCallback, List<Integer>> callbackToRequestIds = new ArrayMap<>();
            synchronized (mLock) {
                // Get the callback for the pending requests.
                for (int i = 0; i < serviceRequestIds.size(); i++) {
                    // Service ID is always a valid int.
                    int serviceRequestId = serviceRequestIds.get(i).intValue();
                    AsyncPropRequestInfo requestInfo =
                            getPendingAsyncPropRequestInfoLocked(serviceRequestId);
                    if (requestInfo == null) {
                        Slogf.w(TAG, "The pending request: %d finished before timeout handler",
                                serviceRequestId);
                        continue;
                    }
                    VehicleStubCallback callback = requestInfo.getVehicleStubCallback();
                    if (callbackToRequestIds.get(callback) == null) {
                        callbackToRequestIds.put(callback, new ArrayList<>());
                    }
                    callbackToRequestIds.get(callback).add(serviceRequestId);
                }
            }
            for (int i = 0; i < callbackToRequestIds.size(); i++) {
                callbackToRequestIds.keyAt(i).onRequestsTimeout(callbackToRequestIds.valueAt(i));
            }
        }
    }

    private class VehicleStubCallback extends VehicleStubCallbackInterface {
        private final IAsyncPropertyResultCallback mAsyncPropertyResultCallback;
        private final IBinder mClientBinder;

        VehicleStubCallback(
                IAsyncPropertyResultCallback asyncPropertyResultCallback) {
            mAsyncPropertyResultCallback = asyncPropertyResultCallback;
            mClientBinder = asyncPropertyResultCallback.asBinder();
        }

        private static List<GetSetValueResult> logAndReturnResults(Histogram histogram,
                List<GetSetValueResultWrapper> getSetValueResultWrapperList,
                @AsyncRequestType int asyncRequestType) {
            List<GetSetValueResult> getSetValueResults = new ArrayList<>();
            float systemCurrentTimeMillis = (float) System.currentTimeMillis();
            for (int i = 0; i < getSetValueResultWrapperList.size(); i++) {
                GetSetValueResultWrapper getSetValueResultWrapper =
                        getSetValueResultWrapperList.get(i);
                GetSetValueResult getSetValueResult = getSetValueResultWrapper
                        .getGetSetValueResult();
                histogram.logSample(systemCurrentTimeMillis
                                - getSetValueResultWrapper.getAsyncRequestStartTime());
                getSetValueResults.add(getSetValueResult);
                if (DBG) {
                    Slogf.d(TAG, "E2E latency for %sPropertiesAsync for requestId: %d is %d",
                            requestTypeToString(asyncRequestType), getSetValueResult.getRequestId(),
                            getSetValueResultWrapper.getAsyncRequestStartTime());
                }
                if (getSetValueResultWrapper.getRetryCount() != 0) {
                    Slogf.i(TAG, "Async %s request finished after retry, requestID: %d,"
                                    + " CarPropertyValue: %s , retry count: %d",
                            requestTypeToString(asyncRequestType),
                            getSetValueResult.getRequestId(),
                            getSetValueResult.getCarPropertyValue(),
                            getSetValueResultWrapper.getRetryCount());
                }
            }
            return getSetValueResults;
        }

        private void sendGetValueResults(List<GetSetValueResultWrapper> results) {
            if (results.isEmpty()) {
                return;
            }
            List<GetSetValueResult> getSetValueResults = logAndReturnResults(
                    sGetAsyncEndToEndLatencyHistogram, results, GET);
            try {
                mAsyncPropertyResultCallback.onGetValueResults(
                        new GetSetValueResultList(getSetValueResults));
            } catch (RemoteException e) {
                Slogf.w(TAG, "sendGetValueResults: Client might have died already", e);
            }
        }

        void sendSetValueResults(List<GetSetValueResultWrapper> results) {
            if (results.isEmpty()) {
                return;
            }
            List<GetSetValueResult> getSetValueResults = logAndReturnResults(
                    sSetAsyncEndToEndLatencyHistogram, results, SET);
            try {
                mAsyncPropertyResultCallback.onSetValueResults(
                        new GetSetValueResultList(getSetValueResults));
            } catch (RemoteException e) {
                Slogf.w(TAG, "sendSetValueResults: Client might have died already", e);
            }
        }

        private void retryIfNotExpired(List<AsyncPropRequestInfo> retryRequests) {
            List<AsyncGetSetRequest> vehicleStubAsyncGetRequests = new ArrayList<>();
            List<GetSetValueResultWrapper> timeoutGetResults = new ArrayList<>();
            List<AsyncGetSetRequest> vehicleStubAsyncSetRequests = new ArrayList<>();
            List<GetSetValueResultWrapper> timeoutSetResults = new ArrayList<>();
            List<AsyncPropRequestInfo> pendingRetryRequests = new ArrayList<>();
            synchronized (mLock) {
                // Get the current time after obtaining lock since it might take some time to get
                // the lock.
                long currentUptimeMs = SystemClock.uptimeMillis();
                for (int i = 0; i < retryRequests.size(); i++) {
                    AsyncPropRequestInfo requestInfo = retryRequests.get(i);
                    requestInfo.incrementRetryCount();
                    long timeoutUptimeMs = requestInfo.getTimeoutUptimeMs();
                    if (timeoutUptimeMs <= currentUptimeMs) {
                        // The request already expired.
                        generateTimeoutResult(requestInfo, timeoutGetResults, timeoutSetResults);
                        continue;
                    }

                    // Generate a new service request ID and async request object for the retry.
                    AsyncGetSetRequest vehicleStubAsyncRequest =
                            generateVehicleStubAsyncRequestLocked(requestInfo);
                    pendingRetryRequests.add(requestInfo);

                    switch (requestInfo.getRequestType()) {
                        case GET: // fallthrough
                        case GET_INITIAL_VALUE_FOR_SET:
                            vehicleStubAsyncGetRequests.add(vehicleStubAsyncRequest);
                            break;
                        case SET:
                            vehicleStubAsyncSetRequests.add(vehicleStubAsyncRequest);
                            break;
                    }
                }

                // We already marked all the input requests as finished. Now for the new retry
                // requests, we need to put them back into the pending request pool.
                mPendingAsyncRequests.addPendingRequests(pendingRetryRequests);
            }

            sendGetValueResults(timeoutGetResults);
            if (!vehicleStubAsyncGetRequests.isEmpty()) {
                mVehicleHal.getAsync(vehicleStubAsyncGetRequests, this);
            }
            sendSetValueResults(timeoutSetResults);
            if (!vehicleStubAsyncSetRequests.isEmpty()) {
                mVehicleHal.setAsync(vehicleStubAsyncSetRequests, this);
            }
        }

        IBinder getClientBinder() {
            return mClientBinder;
        }

        // This is a wrapper for death recipient that will unlink itself upon binder death.
        private final class DeathRecipientWrapper implements DeathRecipient {
            private DeathRecipient mInnerRecipient;

            DeathRecipientWrapper(DeathRecipient innerRecipient) {
                mInnerRecipient = innerRecipient;
            }

            @Override
            public void binderDied() {
                mInnerRecipient.binderDied();
                mClientBinder.unlinkToDeath(this, /* flags= */ 0);
            }
        }

        @Override
        public void linkToDeath(DeathRecipient recipient) throws RemoteException {
            mClientBinder.linkToDeath(new DeathRecipientWrapper(recipient),
                    /* flags= */ 0);
        }

        // Parses an async getProperty result and convert it to an okay/error result.
        private GetSetValueResult parseGetAsyncResults(
                GetVehicleStubAsyncResult getVehicleStubAsyncResult,
                AsyncPropRequestInfo clientRequestInfo) {
            int carPropMgrErrorCode = getVehicleStubAsyncResult.getErrorCode();
            if (carPropMgrErrorCode != STATUS_OK) {
                // All other error results will be delivered back through callback.
                return clientRequestInfo.toErrorResult(
                        getVehicleStubAsyncResult.getCarPropertyErrorCodes());
            }

            // For okay status, convert the property value to the type the client expects.
            int mgrPropId = clientRequestInfo.getPropertyId();
            int halPropId = managerToHalPropId(mgrPropId);
            HalPropConfig halPropConfig;
            synchronized (mLock) {
                halPropConfig = mHalPropIdToPropConfig.get(halPropId);
            }
            if (halPropConfig == null) {
                Slogf.e(TAG, "No configuration found for property: %s, must not happen",
                        clientRequestInfo.getPropertyName());
                return clientRequestInfo.toErrorResult(
                        new CarPropertyErrorCodes(
                                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR,
                                /* vendorErrorCode= */ 0,
                                /* systemErrorCode= */ 0));
            }
            HalPropValue halPropValue = getVehicleStubAsyncResult.getHalPropValue();
            if (halPropValue.getStatus() == VehiclePropertyStatus.UNAVAILABLE) {
                return clientRequestInfo.toErrorResult(
                        new CarPropertyErrorCodes(
                                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE,
                                /* vendorErrorCode= */ 0,
                                /* systemErrorCode= */ 0));
            }
            if (halPropValue.getStatus() != VehiclePropertyStatus.AVAILABLE) {
                return clientRequestInfo.toErrorResult(
                        new CarPropertyErrorCodes(
                                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR,
                                /* vendorErrorCode= */ 0,
                                /* systemErrorCode= */ 0));
            }

            try {
                return clientRequestInfo.toGetValueResult(
                        halPropValue.toCarPropertyValue(mgrPropId, halPropConfig));
            } catch (IllegalStateException e) {
                Slogf.e(TAG, e,
                        "Cannot convert halPropValue to carPropertyValue, property: %s, areaId: %d",
                        halPropIdToName(halPropValue.getPropId()), halPropValue.getAreaId());
                return clientRequestInfo.toErrorResult(
                        new CarPropertyErrorCodes(
                                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR,
                                /* vendorErrorCode= */ 0,
                                /* systemErrorCode= */ 0));
            }
        }

        @Override
        public void onGetAsyncResults(
                List<GetVehicleStubAsyncResult> getVehicleStubAsyncResults) {
            List<GetSetValueResultWrapper> getValueResults = new ArrayList<>();
            // If we receive get value result for initial value request and the result is the
            // same as the target value, we might finish the associated async set value request.
            // So we need potential set value results here.
            List<GetSetValueResultWrapper> setValueResults = new ArrayList<>();
            List<AsyncPropRequestInfo> retryRequests = new ArrayList<>();
            synchronized (mLock) {
                for (int i = 0; i < getVehicleStubAsyncResults.size(); i++) {
                    GetVehicleStubAsyncResult getVehicleStubAsyncResult =
                            getVehicleStubAsyncResults.get(i);
                    int serviceRequestId = getVehicleStubAsyncResult.getServiceRequestId();
                    AsyncPropRequestInfo clientRequestInfo =
                            getAndRemovePendingAsyncPropRequestInfoLocked(serviceRequestId);
                    if (clientRequestInfo == null) {
                        Slogf.w(TAG, "async request for ID: %d not found, ignore the result",
                                serviceRequestId);
                        continue;
                    }

                    int carPropMgrErrorCode = getVehicleStubAsyncResult.getErrorCode();
                    if (carPropMgrErrorCode == CarPropertyErrorCodes.STATUS_TRY_AGAIN) {
                        // The request might need to be retried.
                        if (DBG) {
                            Slogf.d(TAG, "request: %s try again", clientRequestInfo);
                        }
                        retryRequests.add(clientRequestInfo);
                        continue;
                    }

                    GetSetValueResult result = parseGetAsyncResults(getVehicleStubAsyncResult,
                            clientRequestInfo);
                    CarPropertyValue carPropertyValue = result.getCarPropertyValue();
                    if (clientRequestInfo.getRequestType() != GET_INITIAL_VALUE_FOR_SET) {
                        getValueResults.add(new GetSetValueResultWrapper(result,
                                clientRequestInfo.getAsyncRequestStartTime(),
                                clientRequestInfo.getRetryCount()));
                        if (carPropertyValue != null) {
                            int propertyId = carPropertyValue.getPropertyId();
                            int areaId = carPropertyValue.getAreaId();
                            if (isStaticAndSystemProperty(propertyId)) {
                                mStaticPropertyIdAreaIdCache.put(propertyId, areaId,
                                        carPropertyValue);
                            }
                        }
                        continue;
                    }

                    if (DBG) {
                        Slogf.d(TAG, "handling init value result for request: %s",
                                clientRequestInfo);
                    }
                    // Handle GET_INITIAL_VALUE_FOR_SET result.
                    int errorCode = result.getCarPropertyErrorCodes()
                            .getCarPropertyManagerErrorCode();
                    if (errorCode != STATUS_OK) {
                        Slogf.w(TAG, "the init value get request: %s failed, ignore the result, "
                                + "error: %d", clientRequestInfo, errorCode);
                        continue;
                    }
                    // If the initial value result is the target value and the async set
                    // request returned, we finish the pending async set result.
                    AsyncPropRequestInfo assocSetValueRequestInfo =
                            clientRequestInfo.getAssocSetValueRequestInfo();
                    if (assocSetValueRequestInfo == null) {
                        Slogf.e(TAG, "received get initial value result, but no associated set "
                                + "value request is defined");
                        continue;
                    }
                    GetSetValueResult maybeSetResult = maybeFinishPendingSetValueRequestLocked(
                            assocSetValueRequestInfo, carPropertyValue);
                    if (maybeSetResult != null) {
                        if (DBG) {
                            Slogf.d(TAG, "The initial value is the same as target value for "
                                    + "request: %s, sending success set result",
                                    assocSetValueRequestInfo);
                        }
                        setValueResults.add(new GetSetValueResultWrapper(maybeSetResult,
                                assocSetValueRequestInfo.getAsyncRequestStartTime(),
                                assocSetValueRequestInfo.getRetryCount()));
                        removePendingAsyncPropRequestInfoLocked(assocSetValueRequestInfo);
                    }
                }
                updateSubscriptionRateForAsyncSetRequestLocked();
            }

            sendGetValueResults(getValueResults);
            sendSetValueResults(setValueResults);

            if (!retryRequests.isEmpty()) {
                mHandler.postDelayed(() -> {
                    retryIfNotExpired(retryRequests);
                }, ASYNC_RETRY_SLEEP_IN_MS);
            }
        }

        @Override
        public void onSetAsyncResults(
                List<SetVehicleStubAsyncResult> setVehicleStubAsyncResults) {
            List<GetSetValueResultWrapper> setValueResults = new ArrayList<>();
            List<AsyncPropRequestInfo> retryRequests = new ArrayList<>();
            synchronized (mLock) {
                for (int i = 0; i < setVehicleStubAsyncResults.size(); i++) {
                    SetVehicleStubAsyncResult setVehicleStubAsyncResult =
                            setVehicleStubAsyncResults.get(i);
                    int serviceRequestId = setVehicleStubAsyncResult.getServiceRequestId();
                    AsyncPropRequestInfo clientRequestInfo =
                            getPendingAsyncPropRequestInfoLocked(serviceRequestId);
                    if (clientRequestInfo == null) {
                        Slogf.w(TAG, "async request for ID:  %d not found, ignore the result",
                                serviceRequestId);
                        continue;
                    }
                    int carPropMgrErrorCode = setVehicleStubAsyncResult.getErrorCode();

                    if (carPropMgrErrorCode == CarPropertyErrorCodes.STATUS_TRY_AGAIN) {
                        // The request might need to be retried.
                        retryRequests.add(clientRequestInfo);
                        removePendingAsyncPropRequestInfoLocked(clientRequestInfo);
                        continue;
                    }

                    if (carPropMgrErrorCode != STATUS_OK) {
                        // All other error results will be delivered back through callback.
                        setValueResults.add(new GetSetValueResultWrapper(clientRequestInfo
                                .toErrorResult(
                                        setVehicleStubAsyncResult.getCarPropertyErrorCodes()),
                                clientRequestInfo.getAsyncRequestStartTime(),
                                clientRequestInfo.getRetryCount()));
                        removePendingAsyncPropRequestInfoLocked(clientRequestInfo);
                        sSetAsyncEndToEndLatencyHistogram
                                .logSample((float) System.currentTimeMillis()
                                - clientRequestInfo.getAsyncRequestStartTime());
                        continue;
                    }

                    clientRequestInfo.setSetRequestSent();
                    if (clientRequestInfo.success()) {
                        // If we have already received event for the target value or the initial
                        // value is already the target value. Mark the request as complete.
                        removePendingAsyncPropRequestInfoLocked(clientRequestInfo);
                        // If we don't wait for property update event, then we don't know when
                        // the property is updated to the target value. We set it to the
                        // current timestamp.
                        long updateTimestampNanos = clientRequestInfo.isWaitForPropertyUpdate()
                                ? clientRequestInfo.getUpdateTimestampNanos() :
                                SystemClock.elapsedRealtimeNanos();
                        setValueResults.add(new GetSetValueResultWrapper(clientRequestInfo
                                .toSetValueResult(updateTimestampNanos),
                                clientRequestInfo.getAsyncRequestStartTime(),
                                clientRequestInfo.getRetryCount()));
                    }
                }
                updateSubscriptionRateForAsyncSetRequestLocked();
            }

            sendSetValueResults(setValueResults);

            if (!retryRequests.isEmpty()) {
                mHandler.postDelayed(() -> {
                    retryIfNotExpired(retryRequests);
                }, ASYNC_RETRY_SLEEP_IN_MS);
            }
        }

        private void generateTimeoutResult(AsyncPropRequestInfo requestInfo,
                List<GetSetValueResultWrapper> timeoutGetResults,
                List<GetSetValueResultWrapper> timeoutSetResults) {
            GetSetValueResult timeoutResult =  requestInfo.toErrorResult(
                    new CarPropertyErrorCodes(
                            CarPropertyManager.STATUS_ERROR_TIMEOUT,
                            /* vendorErrorCode= */ 0,
                            /* systemErrorCode= */ 0));
            Slogf.w(TAG, "the %s request for request ID: %d time out, request time: %d ms, current"
                    + " time: %d ms", requestTypeToString(requestInfo.getRequestType()),
                    requestInfo.getRequestId(), requestInfo.getAsyncRequestStartTime(),
                    System.currentTimeMillis());

            switch (requestInfo.getRequestType()) {
                case GET:
                    timeoutGetResults.add(new GetSetValueResultWrapper(timeoutResult,
                            requestInfo.getAsyncRequestStartTime(), requestInfo.getRetryCount()));
                    break;
                case GET_INITIAL_VALUE_FOR_SET:
                    // Do not send the timeout requests back to the user because the original
                    // request is not originated from the user.
                    Slogf.e(TAG, "the initial value request: %s timeout", requestInfo);
                    break;
                case SET:
                    timeoutSetResults.add(new GetSetValueResultWrapper(timeoutResult,
                            requestInfo.getAsyncRequestStartTime(), requestInfo.getRetryCount()));
                    break;
            }
        }

        @Override
        public void onRequestsTimeout(List<Integer> serviceRequestIds) {
            List<GetSetValueResultWrapper> timeoutGetResults = new ArrayList<>();
            List<GetSetValueResultWrapper> timeoutSetResults = new ArrayList<>();
            synchronized (mLock) {
                for (int i = 0; i < serviceRequestIds.size(); i++) {
                    int serviceRequestId = serviceRequestIds.get(i);
                    AsyncPropRequestInfo requestInfo =
                            getAndRemovePendingAsyncPropRequestInfoLocked(serviceRequestId);
                    if (requestInfo == null) {
                        Slogf.w(TAG, "Service request ID %d time out but no "
                                + "pending request is found. The request may have already been "
                                + "cancelled or finished", serviceRequestId);
                        continue;
                    }
                    if (DBG) {
                        Slogf.d(TAG, "Request: %s time out", requestInfo);
                    }
                    generateTimeoutResult(requestInfo, timeoutGetResults, timeoutSetResults);
                }
                updateSubscriptionRateForAsyncSetRequestLocked();
            }
            sendGetValueResults(timeoutGetResults);
            sendSetValueResults(timeoutSetResults);
        }
    }

    /**
     * Maybe finish the pending set value request depending on the updated value.
     *
     * Check whether the updated property value is the same as the target value for pending
     * set value requests. If so, finish those requests.
     *
     * @return A success set value result for the finished request or {@code null}.
     */
    @GuardedBy("mLock")
    @Nullable
    private GetSetValueResult maybeFinishPendingSetValueRequestLocked(
            AsyncPropRequestInfo pendingSetValueRequest, CarPropertyValue updatedValue) {
        Object targetValue = pendingSetValueRequest.getPropSvcRequest()
                .getCarPropertyValue().getValue();
        Object currentValue = updatedValue.getValue();
        if (!targetValue.equals(currentValue)) {
            if (DBG) {
                Slogf.d(TAG, "Async set value request: %s receive different updated value: %s"
                        + " than target value: %s", pendingSetValueRequest, currentValue,
                        targetValue);
            }
            return null;
        }
        long updateTimestampNanos = updatedValue.getTimestamp();
        pendingSetValueRequest.setValueUpdated(updateTimestampNanos);
        if (!pendingSetValueRequest.success()) {
            return null;
        }

        return pendingSetValueRequest.toSetValueResult(updateTimestampNanos);
    }

    /**
     * Generates a {@link AsyncGetSetRequest} according to a {@link AsyncPropRequestInfo}.
     *
     * <p>Generates a new PropertyHalService Request ID. Associate the ID with the request and
     * returns a {@link AsyncGetSetRequest} that could be sent to {@link VehicleStub}.
     */
    @GuardedBy("mLock")
    private AsyncGetSetRequest generateVehicleStubAsyncRequestLocked(
            AsyncPropRequestInfo asyncPropRequestInfo) {
        int serviceRequestId = mServiceRequestIdCounter.getAndIncrement();
        asyncPropRequestInfo.setServiceRequestId(serviceRequestId);

        HalPropValue halPropValue;
        CarPropertyValue requestCarPropertyValue = asyncPropRequestInfo.getPropSvcRequest()
                .getCarPropertyValue();
        if (requestCarPropertyValue != null) {
            // If this is a set request, the car property value stores the value to be set.
            halPropValue = carPropertyValueToHalPropValueLocked(requestCarPropertyValue);
        } else {
            // Otherwise this is a get request, we only need the property ID and area ID.
            int halPropertyId = managerToHalPropId(asyncPropRequestInfo.getPropertyId());
            int areaId = asyncPropRequestInfo.getAreaId();
            halPropValue = mPropValueBuilder.build(halPropertyId, areaId);
        }
        return new AsyncGetSetRequest(serviceRequestId, halPropValue,
                asyncPropRequestInfo.getTimeoutUptimeMs());
    }

    @GuardedBy("mLock")
    @Nullable private AsyncPropRequestInfo getPendingAsyncPropRequestInfoLocked(
            int serviceRequestId) {
        AsyncPropRequestInfo requestInfo =
                mPendingAsyncRequests.getRequestIfFound(serviceRequestId);
        if (requestInfo == null) {
            Slogf.w(TAG, "the request for propertyHalService request "
                    + "ID: %d already timed out or already completed", serviceRequestId);
        }
        return requestInfo;
    }

    @GuardedBy("mLock")
    @Nullable private AsyncPropRequestInfo getAndRemovePendingAsyncPropRequestInfoLocked(
            int serviceRequestId) {
        AsyncPropRequestInfo requestInfo = getPendingAsyncPropRequestInfoLocked(serviceRequestId);
        if (requestInfo == null) {
            Slogf.w(TAG, "the request for propertyHalService request "
                    + "ID: %d already timed out or already completed", serviceRequestId);
            return null;
        }
        removePendingAsyncPropRequestInfoLocked(requestInfo);
        return requestInfo;
    }

    /**
     * Remove the pending async request from the pool.
     *
     * If the request to remove is an async set request, also remove it from the
     * {@code mHalPropIdToWaitingUpdateRequestInfo} map. This will cause the subscription rate to
     * be updated for the specific property because we no longer need to monitor this property
     * any more internally.
     *
     * The {@code updatedAreaIdsByHalPropIds} will store the affected area Ids and property IDs if
     * their subscription rate need to be recalculated.
     */
    @GuardedBy("mLock")
    private void removePendingAsyncPropRequestInfoLocked(AsyncPropRequestInfo pendingRequest) {
        int serviceRequestId = pendingRequest.getServiceRequestId();
        mPendingAsyncRequests.removeRequest(serviceRequestId);
        if (pendingRequest.getRequestType() == SET) {
            cleanupPendingAsyncSetRequestLocked(pendingRequest);
        }
    }

    @GuardedBy("mLock")
    private void cleanupPendingAsyncSetRequestLocked(AsyncPropRequestInfo pendingRequest) {
        int halPropId = managerToHalPropId(pendingRequest.getPropertyId());
        if (!pendingRequest.isWaitForPropertyUpdate()) {
            return;
        }
        if (pendingRequest.getAssocGetInitValueRequestInfo() == null) {
            Slogf.e(TAG, "The pending async set value request: %s"
                    + " does not have an associated get initial value request, must not happen",
                    pendingRequest);
            return;
        }
        // If we are removing an async set property request, then we should remove its associated
        // get initial value request as well if it has not been finished.
        AsyncPropRequestInfo assocGetInitValueRequestInfo =
                pendingRequest.getAssocGetInitValueRequestInfo();
        int assocInitValueRequestId = assocGetInitValueRequestInfo.getServiceRequestId();
        assocGetInitValueRequestInfo = mPendingAsyncRequests.getRequestIfFound(
                assocInitValueRequestId);
        if (assocGetInitValueRequestInfo != null) {
            mPendingAsyncRequests.removeRequest(assocInitValueRequestId);
            // Use a separate runnable to do this outside lock.
            mHandler.post(() -> mVehicleHal.cancelRequests(List.of(assocInitValueRequestId)));
        }
        if (!mHalPropIdToWaitingUpdateRequestInfo.contains(halPropId)) {
            return;
        }
        if (!mHalPropIdToWaitingUpdateRequestInfo.get(halPropId).remove(pendingRequest)) {
            return;
        }
        if (mHalPropIdToWaitingUpdateRequestInfo.get(halPropId).isEmpty()) {
            mHalPropIdToWaitingUpdateRequestInfo.remove(halPropId);
        }
        // We no longer need to subscribe to the property.
        mSubManager.stageUnregister(pendingRequest.getServiceRequestId(),
                new ArraySet<Integer>(Set.of(halPropId)));
    }

    /**
     * PropertyHalListener used to send events to CarPropertyService
     */
    public interface PropertyHalListener {
        /**
         * This event is sent whenever the property value is updated
         */
        void onPropertyChange(List<CarPropertyEvent> events);

        /**
         * This event is sent when the set property call fails
         */
        void onPropertySetError(int property, int area,
                @CarSetPropertyErrorCode int errorCode);

    }

    public PropertyHalService(VehicleHal vehicleHal) {
        mVehicleHal = vehicleHal;
        if (DBG) {
            Slogf.d(TAG, "started PropertyHalService");
        }
        mPropValueBuilder = vehicleHal.getHalPropValueBuilder();
    }

    /**
     * Set the listener for the HAL service
     */
    public void setPropertyHalListener(PropertyHalListener propertyHalListener) {
        synchronized (mLock) {
            mPropertyHalListener = propertyHalListener;
        }
    }

    /**
     * Used for resetting the configs state during unit testing. The real implementation uses a
     * static instance of configs so one test will affect the state of another.
     */
    @VisibleForTesting
    void setPropertyHalServiceConfigs(PropertyHalServiceConfigs configs) {
        mPropertyHalServiceConfigs = configs;
    }

    /**
     * @return SparseArray<CarPropertyConfig> List of configs available.
     */
    public SparseArray<CarPropertyConfig<?>> getPropertyList() {
        if (DBG) {
            Slogf.d(TAG, "getPropertyList");
        }
        synchronized (mLock) {
            SparseArray<CarPropertyConfig<?>> mgrPropIdToCarPropConfig = new SparseArray<>();
            for (int i = 0; i < mHalPropIdToPropConfig.size(); i++) {
                HalPropConfig halPropConfig = mHalPropIdToPropConfig.valueAt(i);
                int mgrPropId = halToManagerPropId(halPropConfig.getPropId());
                CarPropertyConfig<?> carPropertyConfig = halPropConfig.toCarPropertyConfig(
                        mgrPropId);
                mgrPropIdToCarPropConfig.put(mgrPropId, carPropertyConfig);
            }
            return mgrPropIdToCarPropConfig;
        }
    }

    /**
     * Returns property value.
     *
     * @param mgrPropId property id in {@link VehiclePropertyIds}
     * @throws IllegalArgumentException if argument is not valid.
     * @throws ServiceSpecificException if there is an exception in HAL or the property status is
     *                                  not available.
     */
    public CarPropertyValue getProperty(int mgrPropId, int areaId)
            throws IllegalArgumentException, ServiceSpecificException {
        int halPropId = managerToHalPropId(mgrPropId);
        // CarPropertyManager catches and rethrows exception, no need to handle here.
        HalPropValue halPropValue;
        HalPropConfig halPropConfig;
        synchronized (mLock) {
            halPropConfig = mHalPropIdToPropConfig.get(halPropId);
            if (isStaticAndSystemProperty(mgrPropId)) {
                CarPropertyValue carPropertyValue = mStaticPropertyIdAreaIdCache.get(mgrPropId,
                        areaId);
                if (carPropertyValue != null) {
                    if (DBG) {
                        Slogf.d(TAG, "Get Sync Property: %s retrieved from cache",
                                VehiclePropertyIds.toString(mgrPropId));
                    }
                    return carPropertyValue;
                }
            }
        }
        halPropValue = mVehicleHal.get(halPropId, areaId);
        try {
            CarPropertyValue result = halPropValue.toCarPropertyValue(mgrPropId, halPropConfig);
            if (!isStaticAndSystemProperty(mgrPropId)) {
                return result;
            }
            synchronized (mLock) {
                mStaticPropertyIdAreaIdCache.put(mgrPropId, areaId, result);
                return result;
            }
        } catch (IllegalStateException e) {
            throw new ServiceSpecificException(STATUS_INTERNAL_ERROR,
                    "Cannot convert halPropValue to carPropertyValue, property: "
                    + VehiclePropertyIds.toString(mgrPropId) + " areaId: " + areaId
                    + ", exception: " + e);
        }
    }

    /**
     * Get the read permission string for the property. The format of the return value of this
     * function has changed over time and thus should not be relied on.
     */
    @Nullable
    public String getReadPermission(int mgrPropId) {
        PermissionCondition readPermission =
                mPropertyHalServiceConfigs.getReadPermission(managerToHalPropId(mgrPropId));
        if (readPermission == null) {
            Slogf.w(TAG, "readPermission is null for mgrPropId: "
                    + VehiclePropertyIds.toString(mgrPropId));
            return null;
        }
        return readPermission.toString();
    }

    /**
     * Get the write permission string for the property. The format of the return value of this
     * function has changed over time and thus should not be relied on.
     */
    @Nullable
    public String getWritePermission(int mgrPropId) {
        PermissionCondition writePermission =
                mPropertyHalServiceConfigs.getWritePermission(managerToHalPropId(mgrPropId));
        if (writePermission == null) {
            Slogf.w(TAG, "writePermission is null for mgrPropId: "
                    + VehiclePropertyIds.toString(mgrPropId));
            return null;
        }
        return writePermission.toString();
    }

    /**
     * Checks whether the property is readable.
     */
    public boolean isReadable(Context context, int mgrPropId) {
        return mPropertyHalServiceConfigs.isReadable(context, managerToHalPropId(mgrPropId));
    }

    /**
     * Checks whether the property is writable.
     */
    public boolean isWritable(Context context, int mgrPropId) {
        return mPropertyHalServiceConfigs.isWritable(context, managerToHalPropId(mgrPropId));
    }

    /**
     * Set the property value.
     *
     * @throws IllegalArgumentException if argument is invalid.
     * @throws ServiceSpecificException if there is an exception in HAL.
     */
    public void setProperty(CarPropertyValue carPropertyValue)
            throws IllegalArgumentException, ServiceSpecificException {
        HalPropValue valueToSet;
        synchronized (mLock) {
            valueToSet = carPropertyValueToHalPropValueLocked(carPropertyValue);
        }

        // CarPropertyManager catches and rethrows exception, no need to handle here.
        mVehicleHal.set(valueToSet);
    }

    /**
     * Subscribe to this property at the specified updateRateHz and areaId. The list of
     * carSubscriptions should never be empty since it is checked at CarPropertyService.
     *
     * @throws ServiceSpecificException If VHAL returns error.
     */
    public void subscribeProperty(List<CarSubscription> carSubscriptions)
            throws ServiceSpecificException {
        synchronized (mLock) {
            Set<Integer> halPropIdsToSubscribe = new ArraySet<>();
            // Even though this involves binder call, this must be done inside the lock so that
            // the state in {@code mSubManager} is consistent with the state in VHAL.
            for (int i = 0; i < carSubscriptions.size(); i++) {
                CarSubscription carSubscription = carSubscriptions.get(i);
                int mgrPropId = carSubscription.propertyId;
                int[] areaIds = carSubscription.areaIds;
                float updateRateHz = carSubscription.updateRateHz;
                if (DBG) {
                    Slogf.d(TAG, "subscribeProperty propertyId: %s, updateRateHz=%f",
                            VehiclePropertyIds.toString(mgrPropId), updateRateHz);
                }
                int halPropId = managerToHalPropId(mgrPropId);
                // Note that we use halPropId instead of mgrPropId in mSubManager.
                mSubManager.stageNewOptions(CAR_PROP_SVC_REQUEST_ID, List.of(newCarSubscription(
                        halPropId, areaIds, updateRateHz, carSubscription.enableVariableUpdateRate,
                        carSubscription.resolution)));
            }
            try {
                updateSubscriptionRateLocked();
            } catch (ServiceSpecificException e) {
                Slogf.e(TAG, "Failed to update subscription rate for subscribe", e);
                throw e;
            }
        }
    }

    /**
     * Unsubscribe the property and turn off update events for it.
     *
     * @throws ServiceSpecificException If VHAL returns error.
     */
    public void unsubscribeProperty(int mgrPropId) throws ServiceSpecificException {
        if (DBG) {
            Slogf.d(TAG, "unsubscribeProperty mgrPropId=%s",
                    VehiclePropertyIds.toString(mgrPropId));
        }
        int halPropId = managerToHalPropId(mgrPropId);
        synchronized (mLock) {
            // Even though this involves binder call, this must be done inside the lock so that
            // the state in {@code mSubManager} is consistent with the state in VHAL.
            mSubManager.stageUnregister(CAR_PROP_SVC_REQUEST_ID, new ArraySet<Integer>(Set.of(
                    halPropId)));
            try {
                updateSubscriptionRateLocked();
            } catch (ServiceSpecificException e) {
                Slogf.e(TAG, "Failed to update subscription rate for unsubscribe, "
                        + "restoring previous state", e);
                throw e;
            }
        }
    }

    @GuardedBy("mLock")
    private int[] getAllAreaIdsLocked(int mgrPropId) {
        int[] areaIds = EMPTY_INT_ARRAY;
        HalPropConfig halPropConfig = mHalPropIdToPropConfig.get(managerToHalPropId(mgrPropId));
        if (halPropConfig == null) {
            if (DBG) {
                Slogf.d(TAG, "Unable to get any areaIds from %s",
                        VehiclePropertyIds.toString(mgrPropId));
            }
            return areaIds;
        }
        areaIds = halPropConfig.toCarPropertyConfig(mgrPropId).getAreaIds();
        return areaIds;
    }

    @Override
    public void init() {
        if (DBG) {
            Slogf.d(TAG, "init()");
        }
    }

    @Override
    public void release() {
        if (DBG) {
            Slogf.d(TAG, "release()");
        }
        synchronized (mLock) {
            ArraySet<Integer> halPropIds = mSubManager.getCurrentSubscribedPropIds();
            for (int i = 0; i < halPropIds.size(); i++) {
                int halPropId = halPropIds.valueAt(i);
                mVehicleHal.unsubscribePropertySafe(this, halPropId);
            }
            mSubManager.clear();
            mHalPropIdToPropConfig.clear();
            mPropertyHalListener = null;
        }
        mHandlerThread.quitSafely();
    }

    @Override
    public boolean isSupportedProperty(int halPropId) {
        return mPropertyHalServiceConfigs.isSupportedProperty(halPropId)
                && CarPropertyHelper.isSupported(halToManagerPropId(halPropId));
    }

    @Override
    public int[] getAllSupportedProperties() {
        return EMPTY_INT_ARRAY;
    }

    // The method is called in HAL init(). Avoid handling complex things in here.
    @Override
    public void takeProperties(Collection<HalPropConfig> halPropConfigs) {
        for (HalPropConfig halPropConfig : halPropConfigs) {
            int halPropId = halPropConfig.getPropId();
            if (isSupportedProperty(halPropId)) {
                synchronized (mLock) {
                    mHalPropIdToPropConfig.put(halPropId, halPropConfig);
                }
                if (DBG) {
                    Slogf.d(TAG, "takeSupportedProperties: %s", halPropIdToName(halPropId));
                }
            } else {
                if (DBG) {
                    Slogf.d(TAG, "takeProperties: Property: %s is not supported, ignore",
                            halPropIdToName(halPropId));
                }
            }
        }
        if (DBG) {
            Slogf.d(TAG, "takeSupportedProperties() took %d properties", halPropConfigs.size());
        }
        // If vehicle hal support to select permission for vendor properties.
        HalPropConfig customizePermission = mVehicleHal.getPropConfig(
                VehicleProperty.SUPPORT_CUSTOMIZE_VENDOR_PERMISSION);
        if (customizePermission != null) {
            mPropertyHalServiceConfigs.customizeVendorPermission(
                    customizePermission.getConfigArray());
        } else {
            if (DBG) {
                Slogf.d(TAG, "No custom vendor permission defined in VHAL");
            }
        }
    }

    private static void storeResultForRequest(GetSetValueResult result,
            AsyncPropRequestInfo request,
            Map<VehicleStubCallback, List<GetSetValueResultWrapper>> callbackToResults) {
        VehicleStubCallback clientCallback = request.getVehicleStubCallback();
        if (callbackToResults.get(clientCallback) == null) {
            callbackToResults.put(clientCallback, new ArrayList<>());
        }
        callbackToResults.get(clientCallback).add(new GetSetValueResultWrapper(result,
                request.getAsyncRequestStartTime(), request.getRetryCount()));
    }

    /**
     * Check whether there is pending async set value request for the property.
     *
     * If there are pending async set value request, check whether the updated property value is
     * the target value. If so, store the success set value result into callbackToSetValueResults.
     */
    @GuardedBy("mLock")
    private void checkPendingWaitForUpdateRequestsLocked(int halPropId,
            CarPropertyValue<?> updatedValue,
            Map<VehicleStubCallback, List<GetSetValueResultWrapper>> callbackToSetValueResults) {
        List<AsyncPropRequestInfo> pendingSetRequests = mHalPropIdToWaitingUpdateRequestInfo.get(
                halPropId);
        if (pendingSetRequests == null) {
            return;
        }
        List<AsyncPropRequestInfo> finishedPendingSetRequests = new ArrayList<>();
        for (AsyncPropRequestInfo pendingSetRequest : pendingSetRequests) {
            GetSetValueResult maybeSetResult = maybeFinishPendingSetValueRequestLocked(
                    pendingSetRequest, updatedValue);
            if (pendingSetRequest.getAreaId() != updatedValue.getAreaId()) {
                continue;
            }
            // Don't remove the finished pending request info during the loop since it will
            // modify pendingSetRequests array.
            if (maybeSetResult == null) {
                if (DBG) {
                    Slogf.d(TAG, "received property update event for request: %s, but the value is "
                            + "different than target value", pendingSetRequest);
                }
                continue;
            }
            if (DBG) {
                Slogf.d(TAG, "received property update to target value event for request: %s"
                        + ", sending success async set value result", pendingSetRequest);
            }
            storeResultForRequest(maybeSetResult, pendingSetRequest, callbackToSetValueResults);
            finishedPendingSetRequests.add(pendingSetRequest);
        }

        for (AsyncPropRequestInfo finishedRequest : finishedPendingSetRequests) {
            // Pending set value request is now succeeded. Remove all record to the pending request.
            removePendingAsyncPropRequestInfoLocked(finishedRequest);
        }
    }

    private static ArrayList<HalSubscribeOptions> toHalSubscribeOptions(
            ArrayList<CarSubscription> carSubscriptions) {
        ArrayList<HalSubscribeOptions> halOptions = new ArrayList<>();
        for (int i = 0; i < carSubscriptions.size(); i++) {
            CarSubscription carOption = carSubscriptions.get(i);
            halOptions.add(new HalSubscribeOptions(carOption.propertyId, carOption.areaIds,
                    carOption.updateRateHz, carOption.enableVariableUpdateRate,
                    carOption.resolution));
        }
        return halOptions;
    }

    /**
     * Apply the staged subscription rate in {@code mSubManager} to VHAL.
     *
     * Use {@code subscribeProperty} to update its subscription rate or {@code unsubscribeProperty}
     * if it is no longer subscribed.
     *
     * This functions involves binder call to VHAL, but we intentionally keep this inside the
     * lock because we need to keep the subscription status consistent. If we do not use lock
     * here, the following situation might happen:
     *
     * <ol>
     * <li>Lock is obtained by thread 1.
     * <li>mSubManager is updated by one thread to state 1.
     * <li>New update rate (local variable) is calculated based on state 1.
     * <li>Lock is released by thread 1.
     * <li>Lock is obtained by thread 2.
     * <li>mSubManager is updated by thread 2 to state 2.
     * <li>New update rate (local variable) is calculated based on state 2.
     * <li>Lock is released by thread 2.
     * <li>Thread 2 calls subscribeProperty to VHAL based on state 2.
     * <li>Thread 1 calls subscribeProperty to VHAL based on state 1.
     * <li>Now internally, the state is in state 2, but from VHAL side, it is in state 1.
     */
    @GuardedBy("mLock")
    private void updateSubscriptionRateLocked() throws ServiceSpecificException {
        ArrayList<CarSubscription> diffSubscribeOptions = new ArrayList<>();
        List<Integer> propIdsToUnsubscribe = new ArrayList<>();
        mSubManager.diffBetweenCurrentAndStage(diffSubscribeOptions, propIdsToUnsubscribe);
        try {
            if (!diffSubscribeOptions.isEmpty()) {
                if (DBG) {
                    Slogf.d(TAG, "subscribeProperty, options: %s", diffSubscribeOptions);
                }
                // This may throw ServiceSpecificException.
                mVehicleHal.subscribeProperty(this, toHalSubscribeOptions(diffSubscribeOptions));
            }
            for (int halPropId : propIdsToUnsubscribe) {
                if (DBG) {
                    Slogf.d(TAG, "unsubscribeProperty for property ID: %s",
                            halPropIdToName(halPropId));
                }
                // This may throw ServiceSpecificException.
                mVehicleHal.unsubscribeProperty(this, halPropId);
            }
            mSubManager.commit();
        } catch (IllegalArgumentException e) {
            Slogf.e(TAG, "Failed to subscribe/unsubscribe, property is not supported, this should "
                    + "not happen, caller must make sure the property is supported", e);
            mSubManager.dropCommit();
            return;
        } catch (Exception e) {
            mSubManager.dropCommit();
            throw e;
        }
    }

    @GuardedBy("mLock")
    private void updateSubscriptionRateForAsyncSetRequestLocked() {
        try {
            updateSubscriptionRateLocked();
        } catch (ServiceSpecificException e) {
            Slogf.e(TAG, "failed to update subscription rate after we finish async set request", e);
            return;
        }
    }

    @Override
    public void onHalEvents(List<HalPropValue> halPropValues) {

        List<CarPropertyEvent> eventsToDispatch = new ArrayList<>();

        // A map to store potential succeeded set value results which is caused by the values
        // updated to the target values.
        Map<VehicleStubCallback, List<GetSetValueResultWrapper>> callbackToSetValueResults =
                new ArrayMap<>();

        synchronized (mLock) {
            for (HalPropValue halPropValue : halPropValues) {
                if (halPropValue == null) {
                    continue;
                }
                int halPropId = halPropValue.getPropId();
                HalPropConfig halPropConfig = mHalPropIdToPropConfig.get(halPropId);
                if (halPropConfig == null) {
                    Slogf.w(TAG, "onHalEvents - received HalPropValue for unsupported property: %s",
                            halPropIdToName(halPropId));
                    continue;
                }
                // Check payload if it is an userdebug build.
                if (BuildHelper.isDebuggableBuild()
                        && !mPropertyHalServiceConfigs.checkPayload(halPropValue)) {
                    Slogf.w(TAG,
                            "Drop event for property: %s because it is failed "
                                    + "in payload checking.", halPropValue);
                    continue;
                }
                int mgrPropId = halToManagerPropId(halPropId);
                if (DBG && halPropValue.getStatus() != VehiclePropertyStatus.AVAILABLE) {
                    Slogf.d(TAG, "Received event %s with status that is not AVAILABLE",
                            halPropValue);
                }
                try {
                    CarPropertyValue<?> carPropertyValue = halPropValue.toCarPropertyValue(
                            mgrPropId, halPropConfig);
                    CarPropertyEvent carPropertyEvent = new CarPropertyEvent(
                            CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE, carPropertyValue);
                    eventsToDispatch.add(carPropertyEvent);

                    checkPendingWaitForUpdateRequestsLocked(halPropId, carPropertyValue,
                            callbackToSetValueResults);
                } catch (IllegalStateException e) {
                    Slogf.w(TAG, "Drop event %s that does not have valid value", halPropValue);
                    continue;
                }
            }
            updateSubscriptionRateForAsyncSetRequestLocked();
        }

        PropertyHalListener propertyHalListener;
        synchronized (mLock) {
            propertyHalListener = mPropertyHalListener;
        }
        if (propertyHalListener != null) {
            propertyHalListener.onPropertyChange(eventsToDispatch);
        }

        for (VehicleStubCallback callback : callbackToSetValueResults.keySet()) {
            callback.sendSetValueResults(callbackToSetValueResults.get(callback));
        }
    }

    private static @CarSetPropertyErrorCode int convertStatusCodeToCarSetPropertyErrorCode(
            @VehicleHalStatusCodeInt int vhalStatusCode) {
        switch (vhalStatusCode) {
            case STATUS_TRY_AGAIN:
                return CAR_SET_PROPERTY_ERROR_CODE_TRY_AGAIN;
            case STATUS_INVALID_ARG:
                return CAR_SET_PROPERTY_ERROR_CODE_INVALID_ARG;
            case STATUS_NOT_AVAILABLE: // fallthrough
            case STATUS_NOT_AVAILABLE_DISABLED: // fallthrough
            case STATUS_NOT_AVAILABLE_SPEED_LOW: // fallthrough
            case STATUS_NOT_AVAILABLE_SPEED_HIGH: // fallthrough
            case STATUS_NOT_AVAILABLE_POOR_VISIBILITY: // fallthrough
            case STATUS_NOT_AVAILABLE_SAFETY:
                return CAR_SET_PROPERTY_ERROR_CODE_PROPERTY_NOT_AVAILABLE;
            case STATUS_ACCESS_DENIED:
                return CAR_SET_PROPERTY_ERROR_CODE_ACCESS_DENIED;
            default:
                return CAR_SET_PROPERTY_ERROR_CODE_UNKNOWN;
        }
    }

    @Override
    public void onPropertySetError(ArrayList<VehiclePropError> vehiclePropErrors) {
        PropertyHalListener propertyHalListener;
        synchronized (mLock) {
            propertyHalListener = mPropertyHalListener;
        }
        if (propertyHalListener != null) {
            for (int i = 0; i < vehiclePropErrors.size(); i++) {
                VehiclePropError vehiclePropError = vehiclePropErrors.get(i);
                int mgrPropId = halToManagerPropId(vehiclePropError.propId);
                int vhalErrorCode = CarPropertyErrorCodes.getVhalSystemErrorCode(
                        vehiclePropError.errorCode);
                Slogf.w(TAG,
                        "onPropertySetError for property: %s, area ID: %d, vhal error code: %d",
                        VehiclePropertyIds.toString(mgrPropId), vehiclePropError.areaId,
                        vhalErrorCode);
                @CarSetPropertyErrorCode int carPropErrorCode =
                        convertStatusCodeToCarSetPropertyErrorCode(vhalErrorCode);
                propertyHalListener.onPropertySetError(mgrPropId, vehiclePropError.areaId,
                        carPropErrorCode);
            }
        }
        Map<VehicleStubCallback, List<GetSetValueResultWrapper>> callbackToSetValueResults =
                new ArrayMap<>();
        synchronized (mLock) {
            for (int i = 0; i < vehiclePropErrors.size(); i++) {
                VehiclePropError vehiclePropError = vehiclePropErrors.get(i);
                // Fail all pending async set requests that are currently waiting for property
                // update which has the same property ID and same area ID.
                int halPropId = vehiclePropError.propId;
                List<AsyncPropRequestInfo> pendingSetRequests =
                        mHalPropIdToWaitingUpdateRequestInfo.get(halPropId);
                if (pendingSetRequests == null) {
                    continue;
                }
                for (int j = 0; j < pendingSetRequests.size(); j++) {
                    AsyncPropRequestInfo pendingRequest = pendingSetRequests.get(j);
                    if (pendingRequest.getAreaId() != vehiclePropError.areaId) {
                        continue;
                    }
                    removePendingAsyncPropRequestInfoLocked(pendingRequest);
                    CarPropertyErrorCodes carPropertyErrorCodes =
                            convertVhalStatusCodeToCarPropertyManagerErrorCodes(
                                    vehiclePropError.errorCode);
                    GetSetValueResult errorResult = pendingRequest.toErrorResult(
                            carPropertyErrorCodes);
                    Slogf.w(TAG, "Pending async set request received property set error with "
                            + "error: %d, vendor error code: %d, fail the pending request: %s",
                            carPropertyErrorCodes.getCarPropertyManagerErrorCode(),
                            carPropertyErrorCodes.getVendorErrorCode(),
                            pendingRequest);
                    storeResultForRequest(errorResult, pendingRequest, callbackToSetValueResults);
                }
            }
            updateSubscriptionRateForAsyncSetRequestLocked();
        }
        for (VehicleStubCallback callback : callbackToSetValueResults.keySet()) {
            callback.sendSetValueResults(callbackToSetValueResults.get(callback));
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(PrintWriter writer) {
        writer.println(TAG);
        writer.println("  Properties available:");
        synchronized (mLock) {
            for (int i = 0; i < mHalPropIdToPropConfig.size(); i++) {
                HalPropConfig halPropConfig = mHalPropIdToPropConfig.valueAt(i);
                writer.println("    " + halPropConfig);
            }
            mSubManager.dump(new IndentingPrintWriter(writer));
        }
    }

    private List<AsyncGetSetRequest> prepareVehicleStubRequests(@AsyncRequestType int requestType,
            List<AsyncPropertyServiceRequest> serviceRequests,
            long timeoutInMs,
            VehicleStubCallback vehicleStubCallback,
            @Nullable List<AsyncPropRequestInfo> assocSetValueRequestInfo,
            @Nullable List<AsyncPropRequestInfo> outRequestInfo, long asyncRequestStartTime) {
        // TODO(b/242326085): Change local variables into memory pool to reduce memory
        //  allocation/release cycle
        List<AsyncGetSetRequest> vehicleStubRequests = new ArrayList<>();
        List<AsyncPropRequestInfo> pendingRequestInfo = new ArrayList<>();
        List<GetSetValueResultWrapper> staticGetValueResults = new ArrayList<>();
        long nowUptimeMs = SystemClock.uptimeMillis();
        synchronized (mLock) {
            for (int i = 0; i < serviceRequests.size(); i++) {
                AsyncPropertyServiceRequest serviceRequest = serviceRequests.get(i);
                int propertyId = serviceRequest.getPropertyId();
                int areaId = serviceRequest.getAreaId();
                if (requestType == GET
                        && mStaticPropertyIdAreaIdCache.get(propertyId, areaId) != null) {
                    if (DBG) {
                        Slogf.d(TAG, "Get Async property: %s retrieved from cache",
                                VehiclePropertyIds.toString(propertyId));
                    }
                    staticGetValueResults.add(new GetSetValueResultWrapper(newGetValueResult(
                            serviceRequest.getRequestId(), mStaticPropertyIdAreaIdCache.get(
                                    propertyId, areaId)), asyncRequestStartTime,
                            /* retryCount= */ 0));
                    continue;
                }
                AsyncPropRequestInfo pendingRequest = new AsyncPropRequestInfo(requestType,
                        serviceRequest, nowUptimeMs + timeoutInMs, vehicleStubCallback,
                        asyncRequestStartTime);
                if (assocSetValueRequestInfo != null) {
                    // Link the async set value request and the get init value request together.
                    pendingRequest.setAssocSetValueRequestInfo(assocSetValueRequestInfo.get(i));
                    assocSetValueRequestInfo.get(i).setAssocGetInitValueRequestInfo(pendingRequest);
                }
                AsyncGetSetRequest vehicleStubRequest = generateVehicleStubAsyncRequestLocked(
                        pendingRequest);
                vehicleStubRequests.add(vehicleStubRequest);
                pendingRequestInfo.add(pendingRequest);
                if (outRequestInfo != null) {
                    outRequestInfo.add(pendingRequest);
                }
            }
            mPendingAsyncRequests.addPendingRequests(pendingRequestInfo);
        }
        if (!staticGetValueResults.isEmpty()) {
            vehicleStubCallback.sendGetValueResults(staticGetValueResults);
        }
        return vehicleStubRequests;
    }

    VehicleStubCallback createVehicleStubCallback(
            IAsyncPropertyResultCallback asyncPropertyResultCallback) {
        IBinder asyncPropertyResultBinder = asyncPropertyResultCallback.asBinder();
        VehicleStubCallback callback;
        synchronized (mLock) {
            if (mResultBinderToVehicleStubCallback.get(asyncPropertyResultBinder) == null) {
                callback = new VehicleStubCallback(asyncPropertyResultCallback);
                try {
                    callback.linkToDeath(() -> onBinderDied(asyncPropertyResultBinder));
                } catch (RemoteException e) {
                    throw new IllegalStateException("Linking to binder death recipient failed, "
                            + "the client might already died", e);
                }
                mResultBinderToVehicleStubCallback.put(asyncPropertyResultBinder, callback);
            } else {
                callback = mResultBinderToVehicleStubCallback.get(asyncPropertyResultBinder);
            }
        }
        return callback;
    }

    private void sendVehicleStubRequests(@AsyncRequestType int requestType,
            List<AsyncGetSetRequest> vehicleStubRequests, VehicleStubCallback callback) {
        switch (requestType) {
            case GET: // fallthrough
            case GET_INITIAL_VALUE_FOR_SET:
                mVehicleHal.getAsync(vehicleStubRequests, callback);
                break;
            case SET:
                mVehicleHal.setAsync(vehicleStubRequests, callback);
                break;
        }
    }

    /**
     * Queries CarPropertyValue with list of AsyncPropertyServiceRequest objects.
     *
     * <p>This method gets the CarPropertyValue using async methods. </p>
     */
    public void getCarPropertyValuesAsync(
            List<AsyncPropertyServiceRequest> serviceRequests,
            IAsyncPropertyResultCallback asyncPropertyResultCallback,
            long timeoutInMs, long asyncRequestStartTime) {
        VehicleStubCallback vehicleStubCallback = createVehicleStubCallback(
                asyncPropertyResultCallback);
        List<AsyncGetSetRequest> vehicleStubRequests = prepareVehicleStubRequests(
                GET, serviceRequests, timeoutInMs, vehicleStubCallback,
                /* assocSetValueRequestInfo= */ null, /* outRequestInfo= */ null,
                asyncRequestStartTime);
        if (vehicleStubRequests.isEmpty()) {
            return;
        }
        sendVehicleStubRequests(GET, vehicleStubRequests, vehicleStubCallback);
    }

    private <T> List<T> filterWaitForUpdateRequests(List<T> requests,
            Function<T, Boolean> isWaitForPropertyUpdate) {
        List<T> waitForUpdateSetRequests = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            if (isWaitForPropertyUpdate.apply(requests.get(i))) {
                waitForUpdateSetRequests.add(requests.get(i));
            }
        }
        return waitForUpdateSetRequests;
    }

    private static CarSubscription newCarSubscription(int propertyId, int[] areaIds,
                                                      float updateRateHz, boolean enableVur) {
        return newCarSubscription(propertyId, areaIds, updateRateHz, enableVur,
                /*resolution*/ 0.0f);
    }

    private static CarSubscription newCarSubscription(int propertyId, int[] areaIds,
            float updateRateHz, boolean enableVur, float resolution) {
        CarSubscription option = new CarSubscription();
        option.propertyId = propertyId;
        option.areaIds = areaIds;
        option.updateRateHz = updateRateHz;
        option.enableVariableUpdateRate = enableVur;
        option.resolution = resolution;
        return option;
    }

    /**
     * For every pending async set request that needs to wait for property update, generates an
     * async get initial value request and subscribes to the property update event.
     */
    private void sendGetInitialValueAndSubscribeUpdateEvent(
            List<AsyncPropertyServiceRequest> serviceRequests,
            VehicleStubCallback vehicleStubCallback, long timeoutInMs,
            List<AsyncPropRequestInfo> waitForUpdateSetRequestInfo, long asyncRequestStartTime) {
        // Stores a list of async GET_INITIAL_VALUE request to be sent.
        List<AsyncGetSetRequest> getInitValueRequests = prepareVehicleStubRequests(
                GET_INITIAL_VALUE_FOR_SET, serviceRequests, timeoutInMs,
                vehicleStubCallback, /* assocSetValueRequestInfo= */ waitForUpdateSetRequestInfo,
                /* outRequestInfo= */ null, asyncRequestStartTime);

        // Subscribe to the property's change events before setting the property.
        synchronized (mLock) {
            for (AsyncPropRequestInfo setRequestInfo : waitForUpdateSetRequestInfo) {
                int halPropId = managerToHalPropId(setRequestInfo.getPropertyId());
                // We already checked in {@code carPropertyValueToHalPropValueLocked} inside
                // {@code prepareVehicleStubRequests}, this is guaranteed not to be null.
                HalPropConfig halPropConfig = mHalPropIdToPropConfig.get(halPropId);

                setRequestInfo.parseClientUpdateRateHz(halPropConfig.toCarPropertyConfig(
                        setRequestInfo.getPropertyId()));

                if (mHalPropIdToWaitingUpdateRequestInfo.get(halPropId) == null) {
                    mHalPropIdToWaitingUpdateRequestInfo.put(halPropId, new ArrayList<>());
                }
                mHalPropIdToWaitingUpdateRequestInfo.get(halPropId).add(setRequestInfo);
                // Internally subscribe to the propId, areaId for property update events.
                // We use the pending async service request ID as client key.
                // Enable VUR for continuous since we only want to know when the value is updated.
                boolean enableVur = (halPropConfig.getChangeMode()
                        == CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS);
                mSubManager.stageNewOptions(setRequestInfo.getServiceRequestId(),
                        // Note that we use halPropId instead of mgrPropId in mSubManager.
                        List.of(newCarSubscription(halPropId,
                                new int[]{setRequestInfo.getAreaId()},
                                setRequestInfo.getUpdateRateHz(), enableVur)));
            }
            try {
                updateSubscriptionRateLocked();
            } catch (ServiceSpecificException e) {
                Slogf.e(TAG, "failed to update subscription rate after we start a new async set "
                        + "request, the request will likely time-out", e);
            }
        }

        sendVehicleStubRequests(GET_INITIAL_VALUE_FOR_SET, getInitValueRequests,
                vehicleStubCallback);
    }

    /**
     * Sets car property values asynchronously.
     */
    public void setCarPropertyValuesAsync(
            List<AsyncPropertyServiceRequest> serviceRequests,
            IAsyncPropertyResultCallback asyncPropertyResultCallback,
            long timeoutInMs, long asyncRequestStartTime) {
        List<AsyncPropRequestInfo> pendingSetRequestInfo = new ArrayList<>();
        VehicleStubCallback vehicleStubCallback = createVehicleStubCallback(
                asyncPropertyResultCallback);
        List<AsyncGetSetRequest> setValueRequests = prepareVehicleStubRequests(
                SET, serviceRequests, timeoutInMs, vehicleStubCallback,
                 /* assocSetValueRequestInfo= */ null, /* outRequestInfo= */ pendingSetRequestInfo,
                asyncRequestStartTime);
        List<AsyncPropRequestInfo> waitForUpdateSetRequestInfo = filterWaitForUpdateRequests(
                pendingSetRequestInfo, (request) -> request.isWaitForPropertyUpdate());

        if (waitForUpdateSetRequestInfo.size() != 0) {
            List<AsyncPropertyServiceRequest> waitForUpdateServiceRequests =
                    filterWaitForUpdateRequests(serviceRequests,
                            (request) -> request.isWaitForPropertyUpdate());
            sendGetInitialValueAndSubscribeUpdateEvent(waitForUpdateServiceRequests,
                    vehicleStubCallback, timeoutInMs, waitForUpdateSetRequestInfo,
                    asyncRequestStartTime);
        }

        sendVehicleStubRequests(SET, setValueRequests, vehicleStubCallback);
    }

    /**
     * Maps managerRequestIds to serviceRequestIds and remove them from the pending request map.
     */
    public void cancelRequests(int[] managerRequestIds) {
        List<Integer> serviceRequestIdsToCancel = new ArrayList<>();
        Set<Integer> managerRequestIdsSet = CarServiceUtils.toIntArraySet(managerRequestIds);
        synchronized (mLock) {
            for (int i = 0; i < mPendingAsyncRequests.size(); i++) {
                // For GET_INITIAL_VALUE request, they have the same manager request ID as their
                // associated async set request. While cancelling the async set request, they will
                // be cancelled as well, see {@link cleanupPendingAsyncSetRequestLocked}, so no need
                // to cancel them here.
                AsyncPropRequestInfo propRequestInfo = mPendingAsyncRequests.valueAt(i);
                if (managerRequestIdsSet.contains(propRequestInfo.getManagerRequestId())
                        && propRequestInfo.getRequestType() != GET_INITIAL_VALUE_FOR_SET) {
                    serviceRequestIdsToCancel.add((int) mPendingAsyncRequests.keyAt(i));
                }
            }
            cancelRequestsByServiceRequestIdsLocked(serviceRequestIdsToCancel);
        }
        if (!serviceRequestIdsToCancel.isEmpty()) {
            mVehicleHal.cancelRequests(serviceRequestIdsToCancel);
        }
    }

    private void onBinderDied(IBinder binder) {
        List<Integer> serviceRequestIdsToCancel = new ArrayList<>();
        synchronized (mLock) {
            mResultBinderToVehicleStubCallback.remove(binder);
            for (int i = 0; i < mPendingAsyncRequests.size(); i++) {
                AsyncPropRequestInfo clientRequestInfo = mPendingAsyncRequests.valueAt(i);
                if (clientRequestInfo.getVehicleStubCallback().getClientBinder() != binder) {
                    continue;
                }
                serviceRequestIdsToCancel.add((int) mPendingAsyncRequests.keyAt(i));
            }
            cancelRequestsByServiceRequestIdsLocked(serviceRequestIdsToCancel);
        }
        if (!serviceRequestIdsToCancel.isEmpty()) {
            mVehicleHal.cancelRequests(serviceRequestIdsToCancel);
        }
    }

    @GuardedBy("mLock")
    private void cancelRequestsByServiceRequestIdsLocked(List<Integer> serviceRequestIdsToCancel) {
        if (serviceRequestIdsToCancel.isEmpty()) {
            return;
        }
        for (int i = 0; i < serviceRequestIdsToCancel.size(); i++) {
            Slogf.w(TAG, "the request for propertyHalService request ID: %d is cancelled",
                    serviceRequestIdsToCancel.get(i));
            getAndRemovePendingAsyncPropRequestInfoLocked(serviceRequestIdsToCancel.get(i));
        }
        try {
            updateSubscriptionRateLocked();
        } catch (ServiceSpecificException e) {
            Slogf.e(TAG, " failed to update subscription rate when an async set request is "
                    + "cancelled", e);
        }
    }

    @GuardedBy("mLock")
    private HalPropValue carPropertyValueToHalPropValueLocked(CarPropertyValue carPropertyValue) {
        int mgrPropId = carPropertyValue.getPropertyId();
        int halPropId = managerToHalPropId(mgrPropId);
        HalPropConfig halPropConfig = mHalPropIdToPropConfig.get(halPropId);
        if (halPropConfig == null) {
            throw new IllegalArgumentException("Property ID: " + mgrPropId + " is not supported");
        }
        return mPropValueBuilder.build(carPropertyValue, halPropId, halPropConfig);
    }

    /**
     * Get the pending async requests size.
     *
     * For test only.
     */
    public int countPendingAsyncRequests() {
        synchronized (mLock) {
            return mPendingAsyncRequests.size();
        }
    }

    /**
     * Get the size of the map from hal prop ID to pending async set value requests.
     *
     * For test only.
     */
    public int countHalPropIdToWaitForUpdateRequests() {
        synchronized (mLock) {
            return mHalPropIdToWaitingUpdateRequestInfo.size();
        }
    }

    private static String requestTypeToString(@AsyncRequestType int requestType) {
        switch (requestType) {
            case GET:
                return "GET";
            case SET:
                return "SET";
            case GET_INITIAL_VALUE_FOR_SET:
                return "GET_INITIAL_VALUE_FOR_SET";
            default:
                return "UNKNOWN";
        }
    }

    @GuardedBy("mLock")
    private boolean isStaticAndSystemProperty(int propertyId) {
        return mHalPropIdToPropConfig.get(managerToHalPropId(propertyId))
                .getChangeMode() == VEHICLE_PROPERTY_CHANGE_MODE_STATIC
                && isSystemProperty(propertyId);
    }

    private int managerToHalPropId(int mgrPropId) {
        return mPropertyHalServiceConfigs.managerToHalPropId(mgrPropId);
    }

    private int halToManagerPropId(int mgrPropId) {
        return mPropertyHalServiceConfigs.halToManagerPropId(mgrPropId);
    }

    private String halPropIdToName(int halPropId) {
        return mPropertyHalServiceConfigs.halPropIdToName(halPropId);
    }
}
