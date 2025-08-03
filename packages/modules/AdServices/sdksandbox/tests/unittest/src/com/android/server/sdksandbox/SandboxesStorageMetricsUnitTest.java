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

package com.android.server.sdksandbox;

import static com.android.server.sdksandbox.SandboxesStorageMetrics.StorageStatsEvent;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SandboxesStorageMetricsUnitTest {

    @Test
    public void testStorageStatsEvents_noLog() {
        final SandboxesStorageMetrics sandboxesStorageMetrics = new SandboxesStorageMetrics();
        assertThat(sandboxesStorageMetrics.consumeStorageStatsEvents().size()).isEqualTo(0);
    }

    @Test
    public void testStorageStatsEvents_singleLog() {
        final SandboxesStorageMetrics sandboxesStorageMetrics = new SandboxesStorageMetrics();

        sandboxesStorageMetrics.log(/*uid=*/ 100, /*sharedStorageKb=*/ 10, /*sdkStorageKb=*/ 10);
        assertThat(sandboxesStorageMetrics.consumeStorageStatsEvents().size()).isEqualTo(2);
    }

    @Test
    public void testGetStorageStatsEvents_checkLimit() {
        final SandboxesStorageMetrics sandboxesStorageMetrics = new SandboxesStorageMetrics();
        final List<StorageStatsEvent> expectedStorageStatsEvents = new ArrayList<>();
        final int sharedStorageKb = 10;
        final int privateStorageKb = 20;
        /** Insert elements more than the limit and check that the limit is not surpassed */
        for (int i = 0; i < 6; i++) {
            sandboxesStorageMetrics.log(/*uid=*/ 100, sharedStorageKb + i, privateStorageKb + i);
        }
        for (int i = 1; i < 6; i++) {
            expectedStorageStatsEvents.addAll(
                    buildStorageStatsEvents(
                            sharedStorageKb + i, privateStorageKb + i, /*uid=*/ 100));
        }
        final List<StorageStatsEvent> storageStatsEvents =
                sandboxesStorageMetrics.consumeStorageStatsEvents();

        assertThat(storageStatsEvents).containsExactlyElementsIn(expectedStorageStatsEvents);
    }

    @Test
    public void testGetStorageStatsEvents_eventsAreConsumed() {
        final SandboxesStorageMetrics sandboxesStorageMetrics = new SandboxesStorageMetrics();
        sandboxesStorageMetrics.log(/*uid=*/ 100, /*sharedStorageKb=*/ 10, /*sdkStorageKb=*/ 10);
        List<StorageStatsEvent> storageStatsEvents =
                sandboxesStorageMetrics.consumeStorageStatsEvents();

        assertThat(storageStatsEvents.size()).isEqualTo(2);

        storageStatsEvents = sandboxesStorageMetrics.consumeStorageStatsEvents();
        /**
         * Calling this method the second time should return an empty list because the first call
         * calls clear() function after fetching the information
         */
        assertThat(storageStatsEvents.size()).isEqualTo(0);
    }

    private List<StorageStatsEvent> buildStorageStatsEvents(
            int sharedStorageKb, int sdkStorageKb, int uid) {
        return Arrays.asList(
                new StorageStatsEvent(/*shared= */ true, sharedStorageKb, uid),
                new StorageStatsEvent(/*shared= */ false, sdkStorageKb, uid));
    }
}
