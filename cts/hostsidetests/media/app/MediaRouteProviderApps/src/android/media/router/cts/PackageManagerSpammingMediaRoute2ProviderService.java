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

package android.media.router.cts;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.MediaRoute2ProviderService;

/**
 * {@link MediaRoute2ProviderService} implementation that spams {@link PackageManager} with
 * operations in an attempt to keep itself alive.
 */
public final class PackageManagerSpammingMediaRoute2ProviderService
        extends BaseFakeRouteProviderService {

    @Override
    public void onCreate() {
        super.onCreate();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        toggleEnabled();
                    }
                },
                filter);
        toggleEnabled();
    }

    private void toggleEnabled() {
        // To trigger a package scan, it's sufficient to cause a PACKAGE_CHANGE event for any
        // package. So we use a different placeholder service so as to not disable this one,
        // ensuring it's always available for scanning. Otherwise, it could be disabled at the
        // moment when scanning would otherwise re-start it.
        ComponentName componentName =
                new ComponentName(this, FakeMediaRoute2ProviderService2.class);
        PackageManager pm = getApplicationContext().getPackageManager();
        if (pm.getComponentEnabledSetting(componentName)
                == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    /* flags= */ PackageManager.DONT_KILL_APP);
        } else {
            pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    /* flags= */ PackageManager.DONT_KILL_APP);
        }
    }
}
