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
package com.android.car.cluster;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.car.Car;
import android.car.CarAppFocusManager;
import android.car.builtin.util.Slogf;
import android.car.cluster.navigation.NavigationState.Maneuver;
import android.car.cluster.navigation.NavigationState.NavigationStateProto;
import android.car.cluster.navigation.NavigationState.Step;
import android.car.cluster.renderer.IInstrumentClusterNavigation;
import android.car.navigation.CarNavigationInstrumentCluster;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.util.Log;
import android.util.proto.ProtoOutputStream;

import com.android.car.AppFocusService;
import com.android.car.AppFocusService.FocusOwnershipCallback;
import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.CarServiceBase;
import com.android.car.CarServiceUtils;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Objects;

/**
 * Service responsible for Navigation focus management and {@code NavigationState} change.
 *
 * @hide
 */
public class ClusterNavigationService extends IInstrumentClusterNavigation.Stub
        implements CarServiceBase, FocusOwnershipCallback {

    @VisibleForTesting
    static final String TAG = CarLog.TAG_CLUSTER;

    private static final ContextOwner NO_OWNER = new ContextOwner(0, 0);
    private static final String NAV_STATE_PROTO_BUNDLE_KEY = "navstate2";

    private final Context mContext;
    private final AppFocusService mAppFocusService;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private ContextOwner mNavContextOwner = NO_OWNER;

    interface ClusterNavigationServiceCallback {
        void onNavigationStateChanged(Bundle bundle);

        CarNavigationInstrumentCluster getInstrumentClusterInfo();

        void notifyNavContextOwnerChanged(ContextOwner owner);
    }

    @GuardedBy("mLock")
    ClusterNavigationServiceCallback mClusterServiceCallback;

    @Override
    public void onNavigationStateChanged(Bundle bundle) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CAR_NAVIGATION_MANAGER);
        assertNavigationFocus();
        assertNavStateProtoValid(bundle);
        ClusterNavigationServiceCallback callback;
        synchronized (mLock) {
            callback = mClusterServiceCallback;
        }
        if (callback == null) return;
        callback.onNavigationStateChanged(bundle);
    }

    @Override
    public CarNavigationInstrumentCluster getInstrumentClusterInfo() {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CAR_NAVIGATION_MANAGER);
        ClusterNavigationServiceCallback callback;
        synchronized (mLock) {
            callback = mClusterServiceCallback;
        }
        if (callback == null) return null;
        return callback.getInstrumentClusterInfo();
    }

    public ClusterNavigationService(Context context, AppFocusService appFocusService) {
        mContext = context;
        mAppFocusService = appFocusService;
    }

    public void setClusterServiceCallback(
            ClusterNavigationServiceCallback clusterServiceCallback) {
        synchronized (mLock) {
            mClusterServiceCallback = clusterServiceCallback;
        }
    }

    @Override
    public void init() {
        if (Slogf.isLoggable(TAG, Log.DEBUG)) {
            Slogf.d(TAG, "initClusterNavigationService");
        }
        mAppFocusService.registerContextOwnerChangedCallback(this /* FocusOwnershipCallback */);
    }

    @Override
    public void release() {
        if (Slogf.isLoggable(TAG, Log.DEBUG)) {
            Slogf.d(TAG, "releaseClusterNavigationService");
        }
        setClusterServiceCallback(null);
        mAppFocusService.unregisterContextOwnerChangedCallback(this);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("**" + getClass().getSimpleName() + "**");
        synchronized (mLock) {
            writer.println("context owner: " + mNavContextOwner);
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {}

    @Override
    public void onFocusAcquired(int appType, int uid, int pid) {
        changeNavContextOwner(appType, uid, pid, true);
    }

    @Override
    public void onFocusAbandoned(int appType, int uid, int pid) {
        changeNavContextOwner(appType, uid, pid, false);
    }

    private void assertNavigationFocus() {
        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        synchronized (mLock) {
            if (uid == mNavContextOwner.uid && pid == mNavContextOwner.pid) {
                return;
            }
        }
        // Stored one failed. There can be a delay, so check with real one again.
        AppFocusService afs = CarLocalServices.getService(AppFocusService.class);
        if (afs != null && afs.isFocusOwner(uid, pid,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION)) {
            return;
        }
        throw new IllegalStateException("Client not owning APP_FOCUS_TYPE_NAVIGATION");
    }

    private void changeNavContextOwner(int appType, int uid, int pid, boolean acquire) {
        if (appType != CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION) {
            return;
        }
        ContextOwner requester = new ContextOwner(uid, pid);
        ContextOwner newOwner = acquire ? requester : NO_OWNER;
        ClusterNavigationServiceCallback callback;
        synchronized (mLock) {
            if ((acquire && Objects.equals(mNavContextOwner, requester))
                    || (!acquire && !Objects.equals(mNavContextOwner, requester))) {
                // Nothing to do here. Either the same owner is acquiring twice, or someone is
                // abandoning a focus they didn't have.
                Slogf.w(TAG, "Invalid nav context owner change (acquiring: " + acquire
                        + "), current owner: [" + mNavContextOwner
                        + "], requester: [" + requester + "]");
                return;
            }

            mNavContextOwner = newOwner;
            callback = mClusterServiceCallback;
        }
        if (callback == null) return;

        callback.notifyNavContextOwnerChanged(newOwner);
    }

    private void assertNavStateProtoValid(Bundle bundle) {
        byte[] protoBytes = bundle.getByteArray(NAV_STATE_PROTO_BUNDLE_KEY);
        if (protoBytes == null) {
            throw new IllegalArgumentException("Received navigation state byte array is null.");
        }
        try {
            NavigationStateProto navigationStateProto = NavigationStateProto.parseFrom(protoBytes);
            if (navigationStateProto.getStepsCount() == 0) {
                return;
            }
            for (Step step : navigationStateProto.getStepsList()) {
                Maneuver maneuver = step.getManeuver();
                if (!Maneuver.TypeV2.UNKNOWN_V2.equals(maneuver.getTypeV2())
                        && Maneuver.Type.UNKNOWN.equals(maneuver.getType())) {
                    throw new IllegalArgumentException(
                        "Maneuver#type must be populated if Maneuver#typeV2 is also populated.");
                }
            }
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Error parsing navigation state proto", e);
        }
    }

    static class ContextOwner {
        final int uid;
        final int pid;

        ContextOwner(int uid, int pid) {
            this.uid = uid;
            this.pid = pid;
        }

        @Override
        public String toString() {
            return "uid: " + uid + ", pid: " + pid;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ContextOwner that = (ContextOwner) o;
            return uid == that.uid && pid == that.pid;
        }

        @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
        @Override
        public int hashCode() {
            return Objects.hash(uid, pid);
        }
    }
}
