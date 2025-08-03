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
 * limitations under the License
 */

package com.android.server.art;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import android.os.SystemProperties;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.art.model.ArtFlags;
import com.android.server.art.testing.StaticMockitoRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ReasonMappingTest {
    @Rule public StaticMockitoRule mockitoRule = new StaticMockitoRule(SystemProperties.class);

    @Test
    public void testGetCompilerFilterForReason() {
        when(SystemProperties.get("pm.dexopt.foo")).thenReturn("speed");
        assertThat(ReasonMapping.getCompilerFilterForReason("foo")).isEqualTo("speed");
    }

    @Test(expected = IllegalStateException.class)
    public void testGetCompilerFilterForReasonInvalidFilter() throws Exception {
        when(SystemProperties.get("pm.dexopt.foo")).thenReturn("invalid-filter");
        ReasonMapping.getCompilerFilterForReason("foo");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetCompilerFilterForReasonInvalidReason() throws Exception {
        ReasonMapping.getCompilerFilterForReason("foo");
    }

    @Test
    public void testGetCompilerFilterForShared() {
        when(SystemProperties.get("pm.dexopt.shared")).thenReturn("speed");
        assertThat(ReasonMapping.getCompilerFilterForShared()).isEqualTo("speed");
    }

    @Test(expected = IllegalStateException.class)
    public void testGetCompilerFilterForSharedProfileGuidedFilter() throws Exception {
        when(SystemProperties.get("pm.dexopt.shared")).thenReturn("speed-profile");
        ReasonMapping.getCompilerFilterForShared();
    }

    @Test
    public void testGetPriorityClassForReason() throws Exception {
        assertThat(ReasonMapping.getPriorityClassForReason("install"))
                .isEqualTo(ArtFlags.PRIORITY_INTERACTIVE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetPriorityClassForReasonInvalidReason() throws Exception {
        ReasonMapping.getPriorityClassForReason("foo");
    }

    @Test
    public void testGetConcurrencyForReason() {
        var defaultCaptor = ArgumentCaptor.forClass(Integer.class);
        lenient()
                .when(SystemProperties.getInt(
                        eq("persist.device_config.runtime.bg-dexopt_concurrency"),
                        defaultCaptor.capture()))
                .thenAnswer(invocation -> defaultCaptor.getValue());
        lenient()
                .when(SystemProperties.getInt(eq("pm.dexopt.bg-dexopt.concurrency"), anyInt()))
                .thenReturn(3);
        assertThat(ReasonMapping.getConcurrencyForReason("bg-dexopt")).isEqualTo(3);
    }

    @Test
    public void testGetConcurrencyForReasonFromPhFlag() {
        lenient()
                .when(SystemProperties.getInt(
                        eq("persist.device_config.runtime.bg-dexopt_concurrency"), anyInt()))
                .thenReturn(4);
        lenient()
                .when(SystemProperties.getInt(eq("pm.dexopt.bg-dexopt.concurrency"), anyInt()))
                .thenReturn(3);
        assertThat(ReasonMapping.getConcurrencyForReason("bg-dexopt")).isEqualTo(4);
    }
}
