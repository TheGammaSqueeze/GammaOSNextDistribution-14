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

package com.android.tests.statsd.framework.appnopermission;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.app.PendingIntent;
import android.app.StatsManager;
import android.app.StatsManager.StatsUnavailableException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.test.InstrumentationRegistry;
import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import org.junit.Test;

public class StatsdPermissionTest {
  /**
   * Broadcast receiver to test registration of a FetchOperation.
   */
  public static final class SimpleReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context mContext, Intent intent) {}
  }

  /**
   * Tests that calling apis without permissions throws the appropriate exception.
   */
  @Test
  public void testApisThrowExceptionWithoutPermission() throws Exception {
    Context context = InstrumentationRegistry.getTargetContext();
    StatsManager statsManager = context.getSystemService(StatsManager.class);
    StatsUnavailableException e = assertThrows(StatsManager.StatsUnavailableException.class,
        ()
            -> statsManager.addConfig(1234,
                StatsdConfig.newBuilder()
                    .setId(1234)
                    .addAllowedLogSource("foo")
                    .build()
                    .toByteArray()));
    assertThat(e).hasCauseThat().isInstanceOf(SecurityException.class);

    e = assertThrows(
        StatsManager.StatsUnavailableException.class, () -> statsManager.removeConfig(1234));
    assertThat(e).hasCauseThat().isInstanceOf(SecurityException.class);

    e = assertThrows(
        StatsManager.StatsUnavailableException.class, () -> statsManager.getReports(1234));
    // expectations are:
    // - for Android T+ receive IllegalStateException
    // - for previous versions receive SecurityExceptions
    assertThat(e.getCause().getClass()).
            isAnyOf(SecurityException.class, IllegalStateException.class);

    e = assertThrows(
        StatsManager.StatsUnavailableException.class, () -> statsManager.getStatsMetadata());
    assertThat(e).hasCauseThat().isInstanceOf(SecurityException.class);

    e = assertThrows(StatsManager.StatsUnavailableException.class,
        () -> statsManager.getRegisteredExperimentIds());
    assertThat(e).hasCauseThat().isInstanceOf(SecurityException.class);

    PendingIntent pi = PendingIntent.getBroadcast(
        context, 1, new Intent(context, SimpleReceiver.class), PendingIntent.FLAG_MUTABLE);

    e = assertThrows(StatsManager.StatsUnavailableException.class,
        () -> statsManager.setBroadcastSubscriber(pi, 123, 0));
    assertThat(e).hasCauseThat().isInstanceOf(SecurityException.class);

    e = assertThrows(StatsManager.StatsUnavailableException.class,
        () -> statsManager.setBroadcastSubscriber(null, 123, 0));
    assertThat(e).hasCauseThat().isInstanceOf(SecurityException.class);

    e = assertThrows(StatsManager.StatsUnavailableException.class,
        () -> statsManager.setFetchReportsOperation(pi, 123));
    assertThat(e).hasCauseThat().isInstanceOf(SecurityException.class);

    e = assertThrows(StatsManager.StatsUnavailableException.class,
        () -> statsManager.setFetchReportsOperation(null, 123));
    assertThat(e).hasCauseThat().isInstanceOf(SecurityException.class);

    e = assertThrows(StatsManager.StatsUnavailableException.class,
        () -> statsManager.setActiveConfigsChangedOperation(pi));
    assertThat(e).hasCauseThat().isInstanceOf(SecurityException.class);

    e = assertThrows(StatsManager.StatsUnavailableException.class,
        () -> statsManager.setActiveConfigsChangedOperation(null));
    assertThat(e).hasCauseThat().isInstanceOf(SecurityException.class);

    // Setting a pull atom callback is not tested because it is oneway,
    // so the security exception would not propagate to the client.
  }
}
