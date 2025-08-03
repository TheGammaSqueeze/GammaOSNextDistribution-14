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

package android.net.thread.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.net.thread.OperationalDatasetTimestamp;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.google.common.testing.EqualsTester;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;

/** Tests for {@link OperationalDatasetTimestamp}. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public final class OperationalDatasetTimestampTest {
    @Test
    public void fromInstant_tooLargeInstant_throwsIllegalArgument() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        OperationalDatasetTimestamp.fromInstant(
                                Instant.ofEpochSecond(0xffffffffffffL + 1L)));
    }

    @Test
    public void fromInstant_ticksIsRounded() {
        Instant instant = Instant.ofEpochSecond(100L);

        // 32767.5 / 32768 * 1000000000 = 999984741.2109375 and given the `ticks` is rounded, so
        // the `ticks` should be 32767 for 999984741 and 0 (carried over to seconds) for 999984742.
        OperationalDatasetTimestamp timestampTicks32767 =
                OperationalDatasetTimestamp.fromInstant(instant.plusNanos(999984741));
        OperationalDatasetTimestamp timestampTicks0 =
                OperationalDatasetTimestamp.fromInstant(instant.plusNanos(999984742));

        assertThat(timestampTicks32767.getSeconds()).isEqualTo(100L);
        assertThat(timestampTicks0.getSeconds()).isEqualTo(101L);
        assertThat(timestampTicks32767.getTicks()).isEqualTo(32767);
        assertThat(timestampTicks0.getTicks()).isEqualTo(0);
        assertThat(timestampTicks32767.isAuthoritativeSource()).isTrue();
        assertThat(timestampTicks0.isAuthoritativeSource()).isTrue();
    }

    @Test
    public void toInstant_nanosIsRounded() {
        // 32767 / 32768 * 1000000000 = 999969482.421875
        assertThat(new OperationalDatasetTimestamp(100L, 32767, false).toInstant().getNano())
                .isEqualTo(999969482);

        // 32766 / 32768 * 1000000000 = 999938964.84375
        assertThat(new OperationalDatasetTimestamp(100L, 32766, false).toInstant().getNano())
                .isEqualTo(999938965);
    }

    @Test
    public void toInstant_onlyAuthoritativeSourceDiscarded() {
        OperationalDatasetTimestamp timestamp1 =
                new OperationalDatasetTimestamp(100L, 0x7fff, false);

        OperationalDatasetTimestamp timestamp2 =
                OperationalDatasetTimestamp.fromInstant(timestamp1.toInstant());

        assertThat(timestamp2.getSeconds()).isEqualTo(100L);
        assertThat(timestamp2.getTicks()).isEqualTo(0x7fff);
        assertThat(timestamp2.isAuthoritativeSource()).isTrue();
    }

    @Test
    public void constructor_tooLargeSeconds_throwsIllegalArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new OperationalDatasetTimestamp(
                                /* seconds= */ 0x0001112233445566L,
                                /* ticks= */ 0,
                                /* isAuthoritativeSource= */ true));
    }

    @Test
    public void constructor_tooLargeTicks_throwsIllegalArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new OperationalDatasetTimestamp(
                                /* seconds= */ 0x01L,
                                /* ticks= */ 0x8000,
                                /* isAuthoritativeSource= */ true));
    }

    @Test
    public void equalityTests() {
        new EqualsTester()
                .addEqualityGroup(
                        new OperationalDatasetTimestamp(100, 100, false),
                        new OperationalDatasetTimestamp(100, 100, false))
                .addEqualityGroup(
                        new OperationalDatasetTimestamp(0, 0, false),
                        new OperationalDatasetTimestamp(0, 0, false))
                .addEqualityGroup(
                        new OperationalDatasetTimestamp(0xffffffffffffL, 0x7fff, true),
                        new OperationalDatasetTimestamp(0xffffffffffffL, 0x7fff, true))
                .testEquals();
    }
}
