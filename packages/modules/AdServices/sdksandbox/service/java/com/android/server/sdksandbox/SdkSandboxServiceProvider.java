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

package com.android.server.sdksandbox;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;

import com.android.sdksandbox.ISdkSandboxService;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Interface to get hold of SdkSandbox service
 *
 * @hide
 */
public interface SdkSandboxServiceProvider {

    /** @hide */
    @IntDef(value = {NON_EXISTENT, CREATE_PENDING, CREATED})
    @Retention(RetentionPolicy.SOURCE)
    @interface SandboxStatus {}

    // Represents the state of the sandbox process when it has either not yet been created or is
    // dead.
    int NON_EXISTENT = 1;

    // Indicates that the sandbox is either in the middle of being created (after a call to bind
    // was performed) or being restarted.
    int CREATE_PENDING = 2;

    // Indicates that the sandbox process is up and running.
    int CREATED = 3;

    /** Fixed suffix which get appended to app process name to create its sandbox process name. */
    String SANDBOX_PROCESS_NAME_SUFFIX = "_sdk_sandbox";

    /**
     * Fixed suffix appended to instrumented app process name to create its sandbox process name.
     */
    String SANDBOX_INSTR_PROCESS_NAME_SUFFIX = "_sdk_sandbox_instr";

    /**
     * Bind to and establish a connection with SdkSandbox service.
     *
     * @param callingInfo represents the calling app.
     * @param serviceConnection receives information when service is started and stopped.
     */
    void bindService(CallingInfo callingInfo, ServiceConnection serviceConnection);

    /**
     * Unbind the SdkSandbox service associated with the app.
     *
     * @param callingInfo represents the app for which the sandbox should be unbound.
     */
    void unbindService(CallingInfo callingInfo);

    /**
     * Kills the sandbox for the given app.
     *
     * @param callingInfo app for which the sandbox kill is being requested.
     */
    void stopSandboxService(CallingInfo callingInfo) throws PackageManager.NameNotFoundException;

    /**
     * Return {@link ISdkSandboxService} connected for {@code callingInfo} or otherwise {@code
     * null}.
     */
    @Nullable
    ISdkSandboxService getSdkSandboxServiceForApp(CallingInfo callingInfo);

    /**
     * Informs the provider when the sandbox service has connected.
     *
     * @param callingInfo represents the app for which the sandbox service has connected.
     * @param service the binder object used to communicate with the sandbox service.
     */
    void onServiceConnected(CallingInfo callingInfo, @NonNull ISdkSandboxService service);

    /**
     * Informs the provider when the sandbox service has disconnected.
     *
     * @param callingInfo represents the app for which the sandbox service has disconnected.
     */
    void onServiceDisconnected(CallingInfo callingInfo);

    /**
     * Informs the provider when an app has died.
     *
     * @param callingInfo represents the app for which the sandbox has died.
     */
    void onAppDeath(CallingInfo callingInfo);

    /**
     * Informs the provider when the sandbox service has died.
     *
     * @param callingInfo represents the app for which the sandbox service has died.
     */
    void onSandboxDeath(CallingInfo callingInfo);

    /**
     * Returns true if the sandbox is currently bound for the given app.
     *
     * @param callingInfo app for which the sandbox bound status is being requested.
     */
    boolean isSandboxBoundForApp(CallingInfo callingInfo);

    /**
     * Returns the status of the sandbox for the given app.
     *
     * @param callingInfo app for which the sandbox status is being requested.
     */
    @SandboxStatus
    int getSandboxStatusForApp(CallingInfo callingInfo);

    /** Dump debug information for adb shell dumpsys */
    default void dump(PrintWriter writer) {
    }

    /**
     * Returns sandbox process name for the passed app package name.
     *
     * @param callingInfo app for which the sandbox status is being requested.
     */
    @NonNull
    String toSandboxProcessName(@NonNull CallingInfo callingInfo)
            throws PackageManager.NameNotFoundException;

    /**
     * Returns name of the sdk sandbox process that corresponds to the given client app.
     *
     * @param callingInfo app for which the sandbox status is being requested.
     * @return name of the sdk sandbox process to be instrumented
     */
    @NonNull
    String toSandboxProcessNameForInstrumentation(@NonNull CallingInfo callingInfo)
            throws PackageManager.NameNotFoundException;
}
