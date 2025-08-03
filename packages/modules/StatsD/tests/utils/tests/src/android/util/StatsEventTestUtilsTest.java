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

package android.util;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.os.AtomsProto.Atom.TOMB_STONE_OCCURRED_FIELD_NUMBER;
import static com.google.common.truth.Truth.assertThat;

import android.util.StatsLog;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.os.AtomsProto.Atom;
import com.android.os.AtomsProto.TestAtomReported;
import com.android.os.AtomsProto.TrainExperimentIds;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

/**
 * Internal tests for {@link StatsEventTestUtils}.
 */
public final class StatsEventTestUtilsTest {
    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).mockStatic(StatsLog.class).build();

    @Captor ArgumentCaptor<StatsEvent> mStatsEventCaptor;

    @Ignore("b/287773614")
    @Test
    public void testOnlyAtomId() throws Exception {
        Atom atom = StatsEventTestUtils.convertToAtom(
                StatsEvent.newBuilder()
                        .setAtomId(TOMB_STONE_OCCURRED_FIELD_NUMBER)
                        .usePooledBuffer()
                        .build());

        assertThat(atom.hasTombStoneOccurred()).isTrue();
    }

    @Test
    public void testOneField() throws Exception {
        StatsdTestStatsLog.write(StatsdTestStatsLog.BATTERY_LEVEL_CHANGED, 3);
        verify(() -> StatsLog.write(mStatsEventCaptor.capture()));
        Atom atom = StatsEventTestUtils.convertToAtom(mStatsEventCaptor.getValue());

        assertThat(atom.hasBatteryLevelChanged()).isTrue();
        assertThat(atom.getBatteryLevelChanged().getBatteryLevel()).isEqualTo(3);
    }

    @Test
    public void testTestAtomReported() throws Exception {
        TrainExperimentIds trainExpIds =
                TrainExperimentIds.newBuilder().addExperimentId(10L).addExperimentId(20L).build();

        StatsdTestStatsLog.write(StatsdTestStatsLog.TEST_ATOM_REPORTED,
                /* uid */ new int[] {1000},
                /* tag */ new String[] {"tag"},
                /* int_field */ 1,
                /* long_field */ 2L,
                /* float_field */ 3.5f,
                /* string_field */ "abc",
                /* boolean_field */ true,
                /* state */ StatsdTestStatsLog.TEST_ATOM_REPORTED__STATE__ON,
                /* bytes_field */ trainExpIds.toByteArray(),
                /* repeated_int_field */ new int[] {4, 5, 6},
                /* repeated_long_field */ new long[] {7L, 8L},
                /* repeated_float_field */ new float[] {},
                /* repeated_string_field */ new String[] {"xyz"},
                /* repeated_boolean_field */ new boolean[] {false, false},
                /* repeated_enum_field */
                new int[] {StatsdTestStatsLog.TEST_ATOM_REPORTED__STATE__OFF});
        verify(() -> StatsLog.write(mStatsEventCaptor.capture()));
        Atom atom = StatsEventTestUtils.convertToAtom(mStatsEventCaptor.getValue());

        assertThat(atom.hasTestAtomReported()).isTrue();
        TestAtomReported tar = atom.getTestAtomReported();
        assertThat(tar.getAttributionNodeCount()).isEqualTo(1);
        assertThat(tar.getAttributionNode(0).getUid()).isEqualTo(1000);
        assertThat(tar.getAttributionNode(0).getTag()).isEqualTo("tag");
        assertThat(tar.getIntField()).isEqualTo(1);
        assertThat(tar.getLongField()).isEqualTo(2L);
        assertThat(tar.getFloatField()).isEqualTo(3.5f);
        assertThat(tar.getStringField()).isEqualTo("abc");
        assertThat(tar.getBooleanField()).isEqualTo(true);
        assertThat(tar.getState()).isEqualTo(TestAtomReported.State.ON);
        assertThat(tar.getBytesField().getExperimentIdList()).containsExactly(10L, 20L);
        assertThat(tar.getRepeatedIntFieldList()).containsExactly(4, 5, 6);
        assertThat(tar.getRepeatedLongFieldList()).containsExactly(7L, 8L);
        assertThat(tar.getRepeatedFloatFieldCount()).isEqualTo(0);
        assertThat(tar.getRepeatedStringFieldList()).containsExactly("xyz");
        assertThat(tar.getRepeatedBooleanFieldList()).containsExactly(false, false);
        assertThat(tar.getRepeatedEnumFieldList()).containsExactly(TestAtomReported.State.OFF);
    }
}
