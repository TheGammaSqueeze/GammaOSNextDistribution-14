/*
 * Copyright 2023 The Android Open Source Project
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

package android.remoteauth;

import android.app.SystemServiceRegistry;
import android.content.Context;

/**
 * Class for initializing RemoteAuth service.
 *
 * @hide
 */
// TODO(b/290092977): Add back after M-2023-11 release - @SystemApi(client = MODULE_LIBRARIES)
public final class RemoteAuthFrameworkInitializer {
    private RemoteAuthFrameworkInitializer() {}

    /**
     * Called by {@link SystemServiceRegistry}'s static initializer and registers all Nearby
     * services to {@link Context}, so that {@link Context#getSystemService} can return them.
     *
     * @throws IllegalStateException if this is called from anywhere besides {@link
     *     SystemServiceRegistry}
     */
    public static void registerServiceWrappers() {
        // TODO(b/290092977): Change to Context.REMOTE_AUTH_SERVICE after aosp/2681375
        // is automerges from aosp-main to udc-mainline-prod
        SystemServiceRegistry.registerContextAwareService(
                RemoteAuthManager.REMOTE_AUTH_SERVICE,
                RemoteAuthManager.class,
                (context, serviceBinder) -> {
                    IRemoteAuthService service = IRemoteAuthService.Stub.asInterface(serviceBinder);
                    return new RemoteAuthManager(context, service);
                });
    }
}
