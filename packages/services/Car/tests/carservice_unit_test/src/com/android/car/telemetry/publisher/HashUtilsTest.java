/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.telemetry.publisher;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class HashUtilsTest {
    @Test
    public void testSha256() {
        assertThat(HashUtils.sha256("")).isEqualTo(1449310910991872227L);
        assertThat(HashUtils.sha256("a")).isEqualTo(-3837880752741967926L);
        assertThat(HashUtils.sha256("aa")).isEqualTo(-8157175689457624170L);
        assertThat(HashUtils.sha256("b")).isEqualTo(5357375904281011006L);
    }

    @Test
    public void testMurmur2Hash64() {
        assertThat(HashUtils.murmur2Hash64("")).isEqualTo(-9117937525680267717L);
        assertThat(HashUtils.murmur2Hash64("abcd")).isEqualTo(-4207384847647213435L);
        assertThat(HashUtils.murmur2Hash64("com.sample.process.name"))
            .isEqualTo(-5639285681030453830L);
    }
}
