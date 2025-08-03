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

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.SdkConstant;
import android.annotation.SystemApi;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ProviderInfo;
import android.os.IBinder;

import com.android.sdksandbox.flags.Flags;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.MemberAccessFlags;

/**
 * Exposes APIs to {@code system_server} components outside of the module boundaries.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
public interface SdkSandboxManagerLocal {

    @SdkConstant(SdkConstant.SdkConstantType.SERVICE_ACTION)
    String SERVICE_INTERFACE = "com.android.sdksandbox.SdkSandboxService";

    /**
     * Broadcast Receiver listen to sufficient verifier requests from Package Manager when install
     * new SDK, to verifier SDK code during installation time and terminate install if SDK not
     * compatible with privacy sandbox restrictions.
     */
    @SdkConstant(SdkConstant.SdkConstantType.BROADCAST_INTENT_ACTION)
    @KeepForApi(
            description =
                    "Package Manager reflects on the class denoted by this field. See b/255754931",
            additionalTargets = {
                @KeepTarget(
                        kind = KeepItemKind.CLASS_AND_MEMBERS,
                        classConstant = SdkSandboxVerifierReceiver.class,
                        memberAccess = {MemberAccessFlags.PUBLIC})
            })
    String VERIFIER_RECEIVER = "com.android.server.sdksandbox.SdkSandboxVerifierReceiver";

    /**
     * Enforces that the sdk sandbox process is allowed to broadcast a given intent.
     *
     * @deprecated Use {@link SdkSandboxManagerLocal#canSendBroadcast(Intent)} instead.
     * @param intent the intent to check.
     * @throws SecurityException if the intent is not allowed to be broadcast.
     */
    @Deprecated
    void enforceAllowedToSendBroadcast(@NonNull Intent intent);

    /**
     * Whether the sdk sandbox process is allowed to broadcast a given intent.
     *
     * @param intent the intent to check.
     * @return true if the intent is allowed to be broadcast, otherwise false
     */
    boolean canSendBroadcast(@NonNull Intent intent);

    /**
     * Enforces that the sdk sandbox process is allowed to start an activity with a given intent.
     *
     * @param intent the intent to check.
     * @throws SecurityException if the activity is not allowed to be started.
     */
    void enforceAllowedToStartActivity(@NonNull Intent intent);

    /**
     * Enforces that the sdk sandbox process is allowed to start or bind to a service with a given
     * intent.
     *
     * @param intent the intent to check.
     * @throws SecurityException if the service is not allowed to be started or bound to.
     */
    void enforceAllowedToStartOrBindService(@NonNull Intent intent);

    /**
     * Whether the sdk sandbox process is allowed to access a given ContentProvider.
     *
     * @param providerInfo info about the Content Provider being accessed.
     * @return true if the sandbox uid is allowed to access the ContentProvider, false otherwise.
     */
    boolean canAccessContentProviderFromSdkSandbox(@NonNull ProviderInfo providerInfo);

    /**
     * Enforces that the caller app is allowed to start a {@code SandboxedActivity} inside its
     * sandbox process.
     *
     * @param intent the activity intent
     * @param clientAppUid uid of the client app
     * @param clientAppPackageName package name of the client app
     * @throws SecurityException if the caller app is not allowed to start {@code
     *     SandboxedActivity}.
     */
    void enforceAllowedToHostSandboxedActivity(
            @NonNull Intent intent, int clientAppUid, @NonNull String clientAppPackageName);

    /**
     * Whether the sdk sandbox process is allowed to register a broadcast receiver with a given
     * intentFilter.
     *
     * @param intentFilter the intentFilter to check.
     * @param flags flags that the ActivityManagerService.registerReceiver method was called with.
     * @param onlyProtectedBroadcasts true if all actions in {@link android.content.IntentFilter}
     *     are protected broadcasts
     * @return true if sandbox is allowed to register a broadcastReceiver, otherwise false.
     */
    boolean canRegisterBroadcastReceiver(
            @NonNull IntentFilter intentFilter, int flags, boolean onlyProtectedBroadcasts);

    /**
     * Returns name of the sdk sandbox process that corresponds to the given client app.
     *
     * @param clientAppInfo {@link ApplicationInfo} of the given client app
     * @return name of the sdk sandbox process to be instrumented
     */
    @NonNull
    String getSdkSandboxProcessNameForInstrumentation(@NonNull ApplicationInfo clientAppInfo);

    /**
     * Returns the application info of the sdk sandbox process that corresponds to the given client
     * app.
     *
     * @param clientAppInfo {@link ApplicationInfo} of the client app
     * @param isSdkInSandbox specifies whether to create an application info for the sandbox or for
     *     an Sdk running inside the sandbox.
     * @return {@link ApplicationInfo} of the sdk sandbox process to be instrumented
     * @throws NameNotFoundException if the sandbox package name cannot be found.
     */
    @NonNull
    @FlaggedApi(Flags.FLAG_SDK_SANDBOX_INSTRUMENTATION_INFO)
    ApplicationInfo getSdkSandboxApplicationInfoForInstrumentation(
            @NonNull ApplicationInfo clientAppInfo, boolean isSdkInSandbox)
            throws NameNotFoundException;

    /**
     * Called by the {@code ActivityManagerService} to notify that instrumentation of the
     * sdk sandbox process that belongs to the client app is about to start.
     *
     * <p>If there is a running instance of the sdk sandbox process, then it will be stopped.
     * While the instrumentation for the sdk sandbox is running, the corresponding client app
     * won't be allowed to connect to the instrumented sdk sandbox process.
     *
     * @param clientAppPackageName package name of the client app
     * @param clientAppUid         uid of the client app
     */
    void notifyInstrumentationStarted(@NonNull String clientAppPackageName, int clientAppUid);

    /**
     * Called by the {@code ActivityManagerService} to notify that instrumentation of the
     * sdk sandbox process that belongs to the client app finished.
     *
     * <p>This method must be called after the instrumented sdk sandbox process has been stopped.
     *
     * <p>Once the instrumentation finishes, the client app will be able to connect to the new
     * instance of its sdk sandbox process.
     *
     * @param clientAppPackageName package name of the client app
     * @param clientAppUid         uid of the client app
     */
    void notifyInstrumentationFinished(@NonNull String clientAppPackageName, int clientAppUid);

    /**
     * Returns true if instrumentation of the sdk sandbox process belonging to the client app is
     * currently running, false otherwise.
     *
     * @param clientAppPackageName package name of the client app
     * @param clientAppUid uid of the client app
     * @hide
     */
    boolean isInstrumentationRunning(@NonNull String clientAppPackageName, int clientAppUid);

    // TODO(b/282239822): Remove this workaround on Android VIC
    /**
     * Register the AdServicesManager System Service
     *
     * @param iBinder The AdServicesManagerService Binder.
     * @hide
     */
    void registerAdServicesManagerService(IBinder iBinder, boolean published);

    /**
     * Returns the effective target Sdk version for the sdk sandbox process.
     *
     * <p>Sdk sandbox process hosts multiple SDKs inside it with each having its own
     * targetSdkVersion. We allocate a targetSdkVersion for the host process such that it is
     * compatible with all the SDKs it's hosting.
     *
     * @param sdkSandboxUid uid of the sdk sandbox process.
     * @return effective target Sdk version for the sdk sandbox process.
     * @throws NameNotFoundException if the client package for the given sdk sandbox uid is not
     *     found.
     * @hide
     */
    int getEffectiveTargetSdkVersion(int sdkSandboxUid) throws NameNotFoundException;
}
