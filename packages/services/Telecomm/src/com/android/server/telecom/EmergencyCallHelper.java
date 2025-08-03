/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.telecom;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.telecom.Log;
import android.telecom.PhoneAccountHandle;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Helps with emergency calls by:
 * 1. granting temporary location permission to the system dialer service during emergency calls
 * 2. keeping track of the time of the last emergency call
 */
@VisibleForTesting
public class EmergencyCallHelper {
    private final Context mContext;
    private final DefaultDialerCache mDefaultDialerCache;
    private final Timeouts.Adapter mTimeoutsAdapter;
    private UserHandle mLocationPermissionGrantedToUser;
    private PhoneAccountHandle mLastOutgoingEmergencyCallPAH;

    //stores the original state of permissions that dialer had
    private boolean mHadFineLocation = false;
    private boolean mHadBackgroundLocation = false;

    //stores whether we successfully granted the runtime permission
    //This is stored so we don't unnecessarily revoke if the grant had failed with an exception.
    //Else we will get an exception
    private boolean mFineLocationGranted= false;
    private boolean mBackgroundLocationGranted = false;

    private long mLastEmergencyCallTimestampMillis;
    private long mLastOutgoingEmergencyCallTimestampMillis;

    @VisibleForTesting
    public EmergencyCallHelper(
            Context context,
            DefaultDialerCache defaultDialerCache,
            Timeouts.Adapter timeoutsAdapter) {
        mContext = context;
        mDefaultDialerCache = defaultDialerCache;
        mTimeoutsAdapter = timeoutsAdapter;
    }

    @VisibleForTesting
    public void maybeGrantTemporaryLocationPermission(Call call, UserHandle userHandle) {
        if (shouldGrantTemporaryLocationPermission(call)) {
            grantLocationPermission(userHandle);
        }
        if (call != null && call.isEmergencyCall()) {
            recordEmergencyCall(call);
        }
    }

    @VisibleForTesting
    public void maybeRevokeTemporaryLocationPermission() {
        if (wasGrantedTemporaryLocationPermission()) {
            revokeLocationPermission();
        }
    }

    long getLastEmergencyCallTimeMillis() {
        return mLastEmergencyCallTimestampMillis;
    }

    @VisibleForTesting
    public void setLastOutgoingEmergencyCallTimestampMillis(long timestampMillis) {
        mLastOutgoingEmergencyCallTimestampMillis = timestampMillis;
    }

    @VisibleForTesting
    public void setLastOutgoingEmergencyCallPAH(PhoneAccountHandle accountHandle) {
        mLastOutgoingEmergencyCallPAH = accountHandle;
    }

    @VisibleForTesting
    public boolean isLastOutgoingEmergencyCallPAH(PhoneAccountHandle currentCallHandle) {
        boolean ecbmActive = mLastOutgoingEmergencyCallPAH != null
                && isInEmergencyCallbackWindow(mLastOutgoingEmergencyCallTimestampMillis)
                && currentCallHandle != null
                && currentCallHandle.equals(mLastOutgoingEmergencyCallPAH);
        if (ecbmActive) {
            Log.i(this, "ECBM is enabled for %s. The last recorded call timestamp was at %s",
                    currentCallHandle, mLastOutgoingEmergencyCallTimestampMillis);
        }

        return ecbmActive;
    }

    boolean isInEmergencyCallbackWindow(long lastEmergencyCallTimestampMillis) {
        return System.currentTimeMillis() - lastEmergencyCallTimestampMillis
                < mTimeoutsAdapter.getEmergencyCallbackWindowMillis(mContext.getContentResolver());
    }

    private void recordEmergencyCall(Call call) {
        mLastEmergencyCallTimestampMillis = System.currentTimeMillis();
        if (!call.isIncoming()) {
            // ECBM is applicable to MO emergency calls
            mLastOutgoingEmergencyCallTimestampMillis = mLastEmergencyCallTimestampMillis;
            mLastOutgoingEmergencyCallPAH = call.getTargetPhoneAccount();
        }
    }

    private boolean shouldGrantTemporaryLocationPermission(Call call) {
        if (!mContext.getResources().getBoolean(R.bool.grant_location_permission_enabled)) {
            Log.i(this, "ShouldGrantTemporaryLocationPermission, disabled by config");
            return false;
        }
        if (call == null) {
            Log.i(this, "ShouldGrantTemporaryLocationPermission, no call");
            return false;
        }
        if (!call.isEmergencyCall() && !isInEmergencyCallbackWindow(
                getLastEmergencyCallTimeMillis())) {
            Log.i(this, "ShouldGrantTemporaryLocationPermission, not emergency");
            return false;
        }
        Log.i(this, "ShouldGrantTemporaryLocationPermission, returning true");
        return true;
    }

    private void grantLocationPermission(UserHandle userHandle) {
        String systemDialerPackage = mDefaultDialerCache.getSystemDialerApplication();
        Log.i(this, "Attempting to grant temporary location permission to " + systemDialerPackage
            + ", user: " + userHandle);

        boolean hadBackgroundLocation = hasBackgroundLocationPermission();
        boolean hadFineLocation = hasFineLocationPermission();
        if (hadBackgroundLocation && hadFineLocation) {
            Log.i(this, "Skipping location grant because the system dialer already"
                + " holds sufficient permissions");
            return;
        }
        mHadFineLocation = hadFineLocation;
        mHadBackgroundLocation = hadBackgroundLocation;

        if (!hadFineLocation) {
            try {
                mContext.getPackageManager().grantRuntimePermission(systemDialerPackage,
                    Manifest.permission.ACCESS_FINE_LOCATION, userHandle);
                recordFineLocationPermissionGrant(userHandle);
            } catch (Exception e) {
                Log.i(this, "Failed to grant ACCESS_FINE_LOCATION");
            }
        }
        if (!hadBackgroundLocation) {
            try {
                mContext.getPackageManager().grantRuntimePermission(systemDialerPackage,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION, userHandle);
                recordBackgroundLocationPermissionGrant(userHandle);
            } catch (Exception e) {
                Log.i(this, "Failed to grant ACCESS_BACKGROUND_LOCATION");
            }
        }
    }

    private void revokeLocationPermission() {
        String systemDialerPackage = mDefaultDialerCache.getSystemDialerApplication();
        Log.i(this, "Revoking temporary location permission from " + systemDialerPackage
            + ", user: " + mLocationPermissionGrantedToUser);
        UserHandle userHandle = mLocationPermissionGrantedToUser;

        try {
            if (!mHadFineLocation && mFineLocationGranted) {
                mContext.getPackageManager().revokeRuntimePermission(systemDialerPackage,
                    Manifest.permission.ACCESS_FINE_LOCATION, userHandle);
            }
        } catch (Exception e) {
            Log.e(this, e, "Failed to revoke location permission from " + systemDialerPackage
                + ", user: " + userHandle);
        }

        try {
            if (!mHadBackgroundLocation && mBackgroundLocationGranted) {
                mContext.getPackageManager().revokeRuntimePermission(systemDialerPackage,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION, userHandle);
            }
        } catch (Exception e) {
            Log.e(this, e, "Failed to revoke location permission from " + systemDialerPackage
                + ", user: " + userHandle);
        }

        clearPermissionGrant();
    }

    private boolean hasBackgroundLocationPermission() {
        return mContext.getPackageManager().checkPermission(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                mDefaultDialerCache.getSystemDialerApplication())
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasFineLocationPermission() {
        return mContext.getPackageManager().checkPermission(
                Manifest.permission.ACCESS_FINE_LOCATION,
                mDefaultDialerCache.getSystemDialerApplication())
                == PackageManager.PERMISSION_GRANTED;
    }

    private void recordBackgroundLocationPermissionGrant(UserHandle userHandle) {
        mLocationPermissionGrantedToUser = userHandle;
        mBackgroundLocationGranted = true;
    }

    private void recordFineLocationPermissionGrant(UserHandle userHandle) {
        mLocationPermissionGrantedToUser = userHandle;
        mFineLocationGranted = true;
    }

    private boolean wasGrantedTemporaryLocationPermission() {
        return mLocationPermissionGrantedToUser != null;
    }

    private void clearPermissionGrant() {
        mLocationPermissionGrantedToUser = null;
        mHadBackgroundLocation = false;
        mHadFineLocation = false;
        mBackgroundLocationGranted = false;
        mFineLocationGranted = false;
    }
}
