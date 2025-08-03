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

package com.android.bedstead.nene.credentials;

import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.Manifest.permission.QUERY_ADMIN_POLICY;
import static android.Manifest.permission.READ_DEVICE_CONFIG;
import static android.Manifest.permission.WRITE_SECURE_SETTINGS;
import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

import static com.android.bedstead.nene.permissions.CommonPermissions.QUERY_ALL_PACKAGES;

import android.content.Context;
import android.credentials.CredentialManager;
import android.credentials.CredentialProviderInfo;

import androidx.annotation.RequiresApi;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Versions;

import java.util.HashSet;
import java.util.Set;

/** Helper methods for using credential manager. */
public final class Credentials {
    public static final Credentials sInstance = new Credentials();

    private Credentials() {}

    /** Gets all discovered credential providers for the instrumented user. */
    @RequiresApi(UPSIDE_DOWN_CAKE)
    @Experimental
    public Set<CredentialProviderInfo> getCredentialProviderServices() {
        return getCredentialProviderServices(TestApis.users().instrumented());
    }

    /** Gets all discovered credential providers for the given user. */
    @RequiresApi(UPSIDE_DOWN_CAKE)
    @Experimental
    public Set<CredentialProviderInfo> getCredentialProviderServices(UserReference user) {
        Versions.requireMinimumVersion(UPSIDE_DOWN_CAKE);

        try (PermissionContext p =
                TestApis.permissions()
                        .withPermission(
                                INTERACT_ACROSS_USERS,
                                // TODO(274917251): If these are required to call the API they should be in the
                                // javadoc...
                                QUERY_ADMIN_POLICY,
                                WRITE_SECURE_SETTINGS,
                                READ_DEVICE_CONFIG,
                                QUERY_ALL_PACKAGES)) {
            return new HashSet<>(
                    credentialManager(user)
                            .getCredentialProviderServicesForTesting(
                                    CredentialManager.PROVIDER_FILTER_ALL_PROVIDERS));
        }
    }

    /**
     * Get the current {@link CredentialManager} service or throw a {@link
     * UnsupportedOperationException} if the service is not available.
     */
    private static CredentialManager credentialManager(UserReference user) {
        Versions.requireMinimumVersion(UPSIDE_DOWN_CAKE);

        Context context = TestApis.context().androidContextAsUser(user);

        if (!CredentialManager.isServiceEnabled(context)) {
            throw new UnsupportedOperationException(
                    "Credential Manager is not available on this device");
        }

        return context.getSystemService(CredentialManager.class);
    }
}
