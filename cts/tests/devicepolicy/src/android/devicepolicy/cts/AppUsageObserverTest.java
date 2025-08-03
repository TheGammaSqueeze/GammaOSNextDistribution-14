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

package android.devicepolicy.cts;

import static com.android.bedstead.nene.permissions.CommonPermissions.OBSERVE_APP_USAGE;
import static com.android.bedstead.nene.utils.Assert.assertThrows;

import android.app.PendingIntent;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

// TODO(b/288224789): Find the correct owner of AppUsageObserverTest
@RunWith(BedsteadJUnit4.class)
public final class AppUsageObserverTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final UsageStatsManager sUsageStatsManager =
            sContext.getSystemService(UsageStatsManager.class);

    private static final Intent sIntent =
            new Intent(Intent.ACTION_MAIN).setPackage(sContext.getPackageName());
    private static final PendingIntent sPendingIntent = PendingIntent.getActivity(
            sContext, /* requestCode= */ 1, sIntent, PendingIntent.FLAG_MUTABLE);
    private static final Duration DURATION_TEN_SECS = Duration.ofSeconds(10);
    private static final Duration DURATION_FIFTY_NINE_SECS = Duration.ofSeconds(59);
    private static final Duration DURATION_SIXTY_SECS = Duration.ofSeconds(60);
    private static final int OBSERVER_ID = 0;
    private static final int OBSERVER_LIMIT = 1000;

    private static final String[] sPackages = { "not.real.package.name" };

    @EnsureHasPermission(OBSERVE_APP_USAGE)
    @Test
    @ApiTest(apis = "android.app.usage.UsageStatsManager#registerAppUsageObserver")
    @Postsubmit(reason = "new test")
    public void registerAppUsageObserver_setTimeLowerThanLimit_throwsException() {
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> sUsageStatsManager.registerAppUsageObserver(
                            OBSERVER_ID, sPackages, 59, TimeUnit.SECONDS, sPendingIntent));
        } finally {
            // we clean up in case the exception is not thrown.
            sUsageStatsManager.unregisterAppUsageObserver(OBSERVER_ID);
        }
    }

    @EnsureHasPermission(OBSERVE_APP_USAGE)
    @Test
    @ApiTest(apis = "android.app.usage.UsageStatsManager#registerUsageSessionObserver")
    @Postsubmit(reason = "new test")
    public void registerUsageSessionObserver_setTimeLowerThanLimit_throwsException() {
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> sUsageStatsManager.registerUsageSessionObserver(
                            OBSERVER_ID, sPackages, DURATION_FIFTY_NINE_SECS, DURATION_TEN_SECS,
                            sPendingIntent,
                            /* sessionEndCallbackIntent= */ null));
        } finally {
            // we clean up in case the exception is not thrown.
            sUsageStatsManager.unregisterUsageSessionObserver(OBSERVER_ID);
        }
    }

    @EnsureHasPermission(OBSERVE_APP_USAGE)
    @Test
    @ApiTest(apis = "android.app.usage.UsageStatsManager#registerAppUsageObserver")
    @Postsubmit(reason = "new test")
    public void registerAppUsageObserver_exceedsObserversLimit_throwsException() {
        try {
            for (int observerId = 0; observerId < OBSERVER_LIMIT; observerId++) {
                sUsageStatsManager.registerAppUsageObserver(
                        observerId, sPackages, 60, TimeUnit.SECONDS, sPendingIntent);
            }

            assertThrows(IllegalStateException.class,
                    () -> sUsageStatsManager.registerAppUsageObserver(
                            OBSERVER_LIMIT, sPackages, 60, TimeUnit.SECONDS,
                            sPendingIntent));
        } finally {
            for (int observerId = 0; observerId < OBSERVER_LIMIT; observerId++) {
                sUsageStatsManager.unregisterAppUsageObserver(observerId);
            }
        }
    }

    @EnsureHasPermission(OBSERVE_APP_USAGE)
    @Test
    @ApiTest(apis = "android.app.usage.UsageStatsManager#registerUsageSessionObserver")
    @Postsubmit(reason = "new test")
    public void registerUsageSessionObserver_exceedsObserversLimit_throwsException() {
        try {
            for (int observerId = 0; observerId < OBSERVER_LIMIT; observerId++) {
                sUsageStatsManager.registerUsageSessionObserver(
                        observerId, sPackages, DURATION_SIXTY_SECS, DURATION_TEN_SECS,
                        sPendingIntent,/* sessionEndCallbackIntent= */ null);
            }

            assertThrows(IllegalStateException.class,
                    () -> sUsageStatsManager.registerUsageSessionObserver(
                            OBSERVER_LIMIT, sPackages, DURATION_SIXTY_SECS, DURATION_TEN_SECS,
                            sPendingIntent,/* sessionEndCallbackIntent= */ null));
        } finally {
            for (int observerId = 0; observerId < OBSERVER_LIMIT; observerId++) {
                sUsageStatsManager.unregisterUsageSessionObserver(OBSERVER_ID);
            }
        }
    }

}
