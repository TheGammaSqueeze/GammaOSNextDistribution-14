/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.devicelockcontroller.storage;

import static com.android.devicelockcontroller.storage.ISetupParametersService.Stub.asInterface;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.devicelockcontroller.DeviceLockControllerApplication;
import com.android.devicelockcontroller.common.DeviceLockConstants.ProvisioningType;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.List;
import java.util.concurrent.Executors;

/**
 * Class used to access Setup Parameters from any users.
 * Storage is hosted by user 0 and is accessed indirectly using a service.
 */
public final class SetupParametersClient extends DlcClient
        implements SetupParametersClientInterface {
    private static final Object sInstanceLock = new Object();

    @SuppressLint("StaticFieldLeak") // Only holds application context.
    @GuardedBy("sInstanceLock")
    private static SetupParametersClient sClient;

    private SetupParametersClient(@NonNull Context context,
            ListeningExecutorService executorService) {
        super(context, new ComponentName(context, SetupParametersService.class), executorService);
    }

    /**
     * Get the SetupParametersClient singleton instance.
     */
    public static SetupParametersClient getInstance() {
        return getInstance(DeviceLockControllerApplication.getAppContext(),
                /* executorService= */ null);
    }

    /**
     * Get the SetupParametersClient singleton instance.
     */
    @VisibleForTesting
    public static SetupParametersClient getInstance(Context appContext,
            @Nullable ListeningExecutorService executorService) {
        synchronized (sInstanceLock) {
            if (sClient == null) {
                sClient = new SetupParametersClient(
                        appContext,
                        executorService == null
                                ? MoreExecutors.listeningDecorator(Executors.newCachedThreadPool())
                                : executorService);
            }
            return sClient;
        }
    }

    /**
     * Reset the Client singleton instance
     */
    @VisibleForTesting
    public static void reset() {
        synchronized (sInstanceLock) {
            if (sClient != null) {
                sClient.tearDown();
                sClient = null;
            }
        }
    }

    /**
     * Override setup parameters if there exists any; otherwise create new parameters.
     * Note that this API can only be called in debuggable build for debugging purpose.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Void> overridePrefs(Bundle bundle) {
        return call(() -> {
            asInterface(getService()).overridePrefs(bundle);
            return null;
        });
    }

    /**
     * Dump current values of SetupParameters to logcat.
     * Note that this API can only be called in debuggable build for debugging purpose.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Void> dump() {
        return call(() -> {
            asInterface(getService()).dump();
            return null;
        });
    }

    /**
     * Clear any existing setup parameters.
     * Note that this API can only be called in debuggable build for debugging purpose.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Void> clear() {
        return call(() -> {
            asInterface(getService()).clear();
            return null;
        });
    }

    /**
     * Parse setup parameters from the extras bundle.
     *
     * @param bundle Bundle with provisioning parameters.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Void> createPrefs(Bundle bundle) {
        return call(() -> {
            asInterface(getService()).createPrefs(bundle);
            return null;
        });
    }

    /**
     * Get the name of the package implementing the kiosk app.
     *
     * @return kiosk app package name.
     */
    @Override
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<String> getKioskPackage() {
        return call(() -> asInterface(getService()).getKioskPackage());
    }

    /**
     * Check if the configuration disables outgoing calls.
     *
     * @return True if outgoign calls are disabled.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Boolean> getOutgoingCallsDisabled() {
        return call(() -> asInterface(getService()).getOutgoingCallsDisabled());
    }

    /**
     * Get package allowlist provisioned by the server.
     *
     * @return List of allowed packages.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<List<String>> getKioskAllowlist() {
        return call(() -> asInterface(getService()).getKioskAllowlist());
    }

    /**
     * Check if notifications are enabled in lock task mode.
     *
     * @return True if notification are enabled.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Boolean> isNotificationsInLockTaskModeEnabled() {
        return call(() -> asInterface(getService()).isNotificationsInLockTaskModeEnabled());
    }

    /**
     * Get the provisioning type of this configuration.
     *
     * @return The type of provisioning which could be one of {@link ProvisioningType}.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<@ProvisioningType Integer> getProvisioningType() {
        return call(() -> asInterface(getService()).getProvisioningType());
    }

    /**
     * Check if provision is mandatory.
     *
     * @return True if the provision should be mandatory.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Boolean> isProvisionMandatory() {
        return call(() -> asInterface(getService()).isProvisionMandatory());
    }

    /**
     * Get the name of the provider of the kiosk app.
     *
     * @return the name of the provider.
     */
    @Nullable
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<String> getKioskAppProviderName() {
        return call(() -> asInterface(getService()).getKioskAppProviderName());
    }

    /**
     * Check if installing from unknown sources should be disallowed on this device after
     * provision
     *
     * @return True if installing from unknown sources is disallowed.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Boolean> isInstallingFromUnknownSourcesDisallowed() {
        return call(() -> asInterface(getService()).isInstallingFromUnknownSourcesDisallowed());
    }

    /**
     * Get the Terms and Conditions URL of the partner for enrolling in a Device Lock program.
     *
     * @return The URL to the terms and conditions.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<String> getTermsAndConditionsUrl() {
        return call(() -> asInterface(getService()).getTermsAndConditionsUrl());
    }

    /**
     * The URL to the support page the user can use to get help.
     *
     * @return The URL to the support page.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<String> getSupportUrl() {
        return call(() -> asInterface(getService()).getSupportUrl());
    }
}
