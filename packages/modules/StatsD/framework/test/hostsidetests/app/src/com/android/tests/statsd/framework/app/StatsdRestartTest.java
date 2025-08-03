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

package com.android.tests.statsd.framework.app;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.app.StatsManager;
import android.app.StatsManager.StatsUnavailableException;
import android.content.Context;

import androidx.test.InstrumentationRegistry;
import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import org.junit.Test;

public class StatsdRestartTest {
  /**
   * Tests that calling apis when statsd is not alive throws the appropriate
   * exception.
   */
  @Test
  public void testApisThrowExceptionWhenStatsdNotAlive() throws Exception {
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
    assertThat(e).hasCauseThat().isInstanceOf(IllegalStateException.class);

    e = assertThrows(
        StatsManager.StatsUnavailableException.class, () -> statsManager.removeConfig(1234));
    assertThat(e).hasCauseThat().isInstanceOf(IllegalStateException.class);

    e = assertThrows(
        StatsManager.StatsUnavailableException.class, () -> statsManager.getReports(1234));
    assertThat(e).hasCauseThat().isInstanceOf(IllegalStateException.class);

    e = assertThrows(
        StatsManager.StatsUnavailableException.class, () -> statsManager.getStatsMetadata());
    assertThat(e).hasCauseThat().isInstanceOf(IllegalStateException.class);

    e = assertThrows(StatsManager.StatsUnavailableException.class,
        () -> statsManager.getRegisteredExperimentIds());
    assertThat(e).hasCauseThat().isInstanceOf(IllegalStateException.class);
  }
}