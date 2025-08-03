/*
 * Copyright (C) 2023 Google LLC.
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

package com.android.compatibility.common.deviceinfo;

import android.annotation.TargetApi;
import android.media.MediaDrm;
import android.os.Build;
import android.util.Log;

import com.android.compatibility.common.deviceinfo.DeviceInfo;
import com.android.compatibility.common.util.DeviceInfoStore;
import com.android.compatibility.common.util.SystemUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Collects Media Drm related properties in WVTS tests.
 */
public class MediaDrmDeviceInfo extends DeviceInfo {
    private static final String TAG = MediaDrmDeviceInfo.class.getSimpleName();

    @Override
    @TargetApi(Build.VERSION_CODES.R)
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        List<UUID> supportedCryptoSchemes = MediaDrm.getSupportedCryptoSchemes();

        store.startArray("media_drm_info");
        for (UUID scheme : supportedCryptoSchemes) {
            Log.i(TAG, scheme.toString());
            store.startGroup();
            store.addResult("scheme_uuid ", scheme.toString());
            try (MediaDrm mediaDrm = new MediaDrm(scheme)) {
                for (String key : new String[]{MediaDrm.PROPERTY_VENDOR, MediaDrm.PROPERTY_VERSION,
                        MediaDrm.PROPERTY_DESCRIPTION}) {
                    try {
                        String value = mediaDrm.getPropertyString(key);
                        Log.i(TAG, String.format("key %s, value %s", key, value));
                        store.addResult(key, value);
                    } catch (Exception e) {
                        // leave value blank
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to collect device info", e);
                Log.e(TAG + "scheme", String.valueOf(scheme));
            }
            store.endGroup();
        }
        store.endArray();

        try {
            String output = SystemUtil.runShellCommand("pgrep -fl android.hardware.drm");
            ArrayList<String> list = new ArrayList<>(Arrays.asList(output.split("\n")));
            for (int i = list.size() - 1; i >= 0; i--) {
                String drmHal = list.remove(i);
                int n = drmHal.indexOf("/");
                list.add(drmHal.substring(n, drmHal.length()));
            }
            store.addListResult("media_drm_hal", list);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }
}
