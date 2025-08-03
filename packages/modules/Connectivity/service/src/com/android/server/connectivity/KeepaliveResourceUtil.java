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

package com.android.server.connectivity;

import android.annotation.NonNull;
import android.content.Context;
import android.content.res.Resources;
import android.net.NetworkCapabilities;
import android.text.TextUtils;
import android.util.AndroidRuntimeException;

import com.android.connectivity.resources.R;

/**
 * Utilities to fetch keepalive configuration from resources.
 */
public abstract class KeepaliveResourceUtil {

    /**
     * Read supported keepalive count for each transport type from overlay resource.
     *
     * @param context The context to read resource from.
     * @return An array of supported keepalive count for each transport type.
     */
    @NonNull
    public static int[] getSupportedKeepalives(@NonNull Context context) {
        String[] res = null;
        try {
            final ConnectivityResources connRes = new ConnectivityResources(context);
            res = connRes.get().getStringArray(R.array.config_networkSupportedKeepaliveCount);
        } catch (Resources.NotFoundException unused) {
        }
        if (res == null) throw new KeepaliveDeviceConfigurationException("invalid resource");

        final int[] ret = new int[NetworkCapabilities.MAX_TRANSPORT + 1];
        for (final String row : res) {
            if (TextUtils.isEmpty(row)) {
                throw new KeepaliveDeviceConfigurationException("Empty string");
            }
            final String[] arr = row.split(",");
            if (arr.length != 2) {
                throw new KeepaliveDeviceConfigurationException("Invalid parameter length");
            }

            int transport;
            int supported;
            try {
                transport = Integer.parseInt(arr[0]);
                supported = Integer.parseInt(arr[1]);
            } catch (NumberFormatException e) {
                throw new KeepaliveDeviceConfigurationException("Invalid number format");
            }

            if (!NetworkCapabilities.isValidTransport(transport)) {
                throw new KeepaliveDeviceConfigurationException("Invalid transport " + transport);
            }

            if (supported < 0) {
                throw new KeepaliveDeviceConfigurationException(
                        "Invalid supported count " + supported + " for "
                                + NetworkCapabilities.transportNameOf(transport));
            }
            ret[transport] = supported;
        }
        return ret;
    }

    /**
     * An exception thrown when the keepalive resource configuration is invalid.
     */
    public static class KeepaliveDeviceConfigurationException extends AndroidRuntimeException {
        public KeepaliveDeviceConfigurationException(final String msg) {
            super(msg);
        }
    }
}
