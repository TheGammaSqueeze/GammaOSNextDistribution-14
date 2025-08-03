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

package com.android.ondevicepersonalization.services;

import android.adservices.ondevicepersonalization.CallerMetadata;
import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.aidl.IExecuteCallback;
import android.adservices.ondevicepersonalization.aidl.IOnDevicePersonalizationManagingService;
import android.adservices.ondevicepersonalization.aidl.IRegisterMeasurementEventCallback;
import android.adservices.ondevicepersonalization.aidl.IRequestSurfacePackageCallback;
import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Trace;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.enrollment.PartnerEnrollmentChecker;
import com.android.ondevicepersonalization.services.serviceflow.ServiceFlowOrchestrator;
import com.android.ondevicepersonalization.services.serviceflow.ServiceFlowType;
import com.android.ondevicepersonalization.services.util.DeviceUtils;

import java.util.Objects;

/** Implementation of OnDevicePersonalizationManagingService */
public class OnDevicePersonalizationManagingServiceDelegate
        extends IOnDevicePersonalizationManagingService.Stub {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "OnDevicePersonalizationManagingServiceDelegate";
    private static final ServiceFlowOrchestrator sSfo = ServiceFlowOrchestrator.getInstance();
    @NonNull private final Context mContext;

    public OnDevicePersonalizationManagingServiceDelegate(@NonNull Context context) {
        mContext = Objects.requireNonNull(context);
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public void execute(
            @NonNull String callingPackageName,
            @NonNull ComponentName handler,
            @NonNull Bundle wrappedParams,
            @NonNull CallerMetadata metadata,
            @NonNull IExecuteCallback callback) {
        if (getGlobalKillSwitch()) {
            throw new IllegalStateException("Service skipped as the global kill switch is on.");
        }

        if (!DeviceUtils.isOdpSupported(mContext)) {
            throw new IllegalStateException("Device not supported.");
        }

        Trace.beginSection("OdpManagingServiceDelegate#Execute");
        Objects.requireNonNull(callingPackageName);
        Objects.requireNonNull(handler);
        Objects.requireNonNull(handler.getPackageName());
        Objects.requireNonNull(handler.getClassName());
        Objects.requireNonNull(wrappedParams);
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(callback);
        if (callingPackageName.isEmpty()) {
            throw new IllegalArgumentException("missing app package name");
        }
        if (handler.getPackageName().isEmpty()) {
            throw new IllegalArgumentException("missing service package name");
        }
        if (handler.getClassName().isEmpty()) {
            throw new IllegalArgumentException("missing service class name");
        }

        final int uid = Binder.getCallingUid();
        enforceCallingPackageBelongsToUid(callingPackageName, uid);
        enforceEnrollment(callingPackageName, handler);

        sSfo.schedule(ServiceFlowType.APP_REQUEST_FLOW,
                callingPackageName, handler, wrappedParams,
                callback, mContext, metadata.getStartTimeMillis());
        Trace.endSection();
    }

    @Override
    public void requestSurfacePackage(
            @NonNull String slotResultToken,
            @NonNull IBinder hostToken,
            int displayId,
            int width,
            int height,
            @NonNull CallerMetadata metadata,
            @NonNull IRequestSurfacePackageCallback callback) {
        if (getGlobalKillSwitch()) {
            throw new IllegalStateException("Service skipped as the global kill switch is on.");
        }

        if (!DeviceUtils.isOdpSupported(mContext)) {
            throw new IllegalStateException("Device not supported.");
        }

        Trace.beginSection("OdpManagingServiceDelegate#RequestSurfacePackage");
        Objects.requireNonNull(slotResultToken);
        Objects.requireNonNull(hostToken);
        Objects.requireNonNull(callback);
        if (width <= 0) {
            throw new IllegalArgumentException("width must be > 0");
        }

        if (height <= 0) {
            throw new IllegalArgumentException("height must be > 0");
        }

        if (displayId < 0) {
            throw new IllegalArgumentException("displayId must be >= 0");
        }

        sSfo.schedule(ServiceFlowType.RENDER_FLOW,
                slotResultToken, hostToken, displayId,
                width, height, callback,
                mContext, metadata.getStartTimeMillis());
        Trace.endSection();
    }

    // TODO(b/301732670): Move to a new service.
    @Override
    public void registerMeasurementEvent(
            @NonNull int measurementEventType,
            @NonNull Bundle params,
            @NonNull CallerMetadata metadata,
            @NonNull IRegisterMeasurementEventCallback callback
    ) {
        if (getGlobalKillSwitch()) {
            throw new IllegalStateException("Service skipped as the global kill switch is on.");
        }

        if (!DeviceUtils.isOdpSupported(mContext)) {
            throw new IllegalStateException("Device not supported.");
        }

        Trace.beginSection("OdpManagingServiceDelegate#RegisterMeasurementEvent");
        if (measurementEventType
                != Constants.MEASUREMENT_EVENT_TYPE_WEB_TRIGGER) {
            throw new IllegalStateException("invalid measurementEventType");
        }
        Objects.requireNonNull(params);
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(callback);

        sSfo.schedule(ServiceFlowType.WEB_TRIGGER_FLOW,
                params, mContext,
                callback, metadata.getStartTimeMillis());
        Trace.endSection();
    }

    private boolean getGlobalKillSwitch() {
        long origId = Binder.clearCallingIdentity();
        boolean globalKillSwitch = FlagsFactory.getFlags().getGlobalKillSwitch();
        FlagsFactory.getFlags().setStableFlags();
        Binder.restoreCallingIdentity(origId);
        return globalKillSwitch;
    }

    private void enforceCallingPackageBelongsToUid(@NonNull String packageName, int uid) {
        int packageUid;
        PackageManager pm = mContext.getPackageManager();
        try {
            packageUid = pm.getPackageUid(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new SecurityException(packageName + " not found");
        }
        if (packageUid != uid) {
            throw new SecurityException(packageName + " does not belong to uid " + uid);
        }
        //TODO(b/242792629): Handle requests from the SDK sandbox.
    }

    private void enforceEnrollment(@NonNull String callingPackageName,
                                   @NonNull ComponentName service) {
        long origId = Binder.clearCallingIdentity();

        try {
            if (!PartnerEnrollmentChecker.isCallerAppEnrolled(callingPackageName)) {
                sLogger.d("caller app %s not enrolled to call ODP.", callingPackageName);
                throw new IllegalStateException(
                        "Service skipped as the caller app is not enrolled to call ODP.");
            }
            if (!PartnerEnrollmentChecker.isIsolatedServiceEnrolled(service.getPackageName())) {
                sLogger.d("isolated service %s not enrolled to access ODP.",
                        service.getPackageName());
                throw new IllegalStateException(
                        "Service skipped as the isolated service is not enrolled to access ODP.");
            }
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }
}
