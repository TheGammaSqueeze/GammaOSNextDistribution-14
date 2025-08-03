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

package com.android.rkpdapp;

import com.android.rkpdapp.IGetRegistrationCallback;
import com.android.rkpdapp.IRegistration;

/**
 * {@link IRemoteProvisioning} is the interface provided to use the remote key
 * provisioning functionality from the Remote Key Provisioning Daemon (RKPD).
 * This would be the first service that RKPD clients would interact with. The
 * intent is for the clients to get the {@link IRegistration} object from this
 * interface and use it for actual remote provisioning work.
 *
 * @hide
 */
oneway interface IRemoteProvisioning {
    /**
     * Takes a remotely provisioned component service name and gets a
     * registration bound to that service and the caller's UID.
     *
     * @param callerUid The caller who is requesting a registration. This cannot
     * be determined via getCallingUid, because calls are routed from the actual
     * clients (e.g. keystore) through system server. Thus, we rely on system
     * server to pass the actual caller's UID as a parameter.
     * @param irpcName The name of the {@code IRemotelyProvisionedComponent}
     * for which remotely provisioned keys should be managed.
     * @param callback Receives the result of the call. A callback must only
     * be used with one {@code getRegistration} call at a time.
     *
     * Notes:
     * - This function will attempt to get the service named by irpcName. This
     *   implies that a lazy/dynamic aidl service will be instantiated, and this
     *   function blocks until the service is up. Upon return, any binder tokens
     *   are dropped, allowing the lazy/dynamic service to shutdown.
     * - The created registration object is unique per caller. If two different
     *   UIDs call getRegistration with the same irpcName, they will receive
     *   different registrations. This prevents two different applications from
     *   being able to see the same keys.
     * - This function is idempotent per calling UID. Additional calls to
     *   getRegistration with the same parameters, from the same caller, will have
     *   no side effects.
     *
     * @see IRegistration#getKey()
     * @see IRemotelyProvisionedComponent
     */
    void getRegistration(int callerUid, String irpcName, IGetRegistrationCallback callback);

    /**
     * Cancel a getRegistration call. If the call is already completed, this method
     * is a noop.
     *
     * @param callback the callback previously passed to getRegistration, indicating
     * which call should be cancelled.
     */
    void cancelGetRegistration(IGetRegistrationCallback callback);
}
