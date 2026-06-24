/* //device/content/providers/media/src/com/android/providers/media/MediaScannerReceiver.java
**
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package com.android.providers.media;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.android.providers.media.stableuris.job.StableUriIdleMaintenanceService;

public class MediaReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // GammaOS Nano: skip all broadcast handling when JobScheduler is unavailable, and in
        // minimal boot. Nano does not use MediaStore (the XMB does its own file scanning), so
        // skip scheduling idle scans and enqueuing mount/scan work - otherwise MediaProvider
        // floods CPU/IO scanning a full /sdcard, which on a 1GB device with a game running
        // (and the foreground-protective lmkd config) can thrash the device into a freeze.
        if (context.getSystemService(android.app.job.JobScheduler.class) == null
                || "1".equals(android.os.SystemProperties.get("sys.gammaos.minimal_boot", "0"))) {
            return;
        }
        final String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            // Register our idle maintenance service
            IdleService.scheduleIdlePass(context);
            StableUriIdleMaintenanceService.scheduleIdlePass(context);
        } else {
            // All other operations are heavier-weight, so redirect them through
            // service to ensure they have breathing room to finish
            intent.setComponent(new ComponentName(context, MediaService.class));
            MediaService.enqueueWork(context, intent);
        }
    }
}
