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

package android.os.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import android.os.CombinedVibration;
import android.os.Parcel;
import android.os.VibrationEffect;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class CombinedVibrationTest {

    private static final VibrationEffect TEST_EFFECT =
            VibrationEffect.get(VibrationEffect.EFFECT_CLICK);

    private static final CombinedVibration TEST_MONO =
            CombinedVibration.createParallel(TEST_EFFECT);
    private static final CombinedVibration TEST_STEREO =
            CombinedVibration.startParallel()
                    .addVibrator(1, TEST_EFFECT)
                    .addVibrator(2, TEST_EFFECT)
                    .combine();
    private static final CombinedVibration TEST_SEQUENTIAL =
            CombinedVibration.startSequential()
                    .addNext(TEST_MONO)
                    .addNext(1, TEST_EFFECT, /* delay= */ 100)
                    .combine();

    @Test
    public void testcreateParallel() {
        CombinedVibration.Mono mono =
                (CombinedVibration.Mono) CombinedVibration.createParallel(TEST_EFFECT);
        assertEquals(TEST_EFFECT, mono.getEffect());
        assertEquals(TEST_EFFECT.getDuration(), mono.getDuration());
    }

    @Test
    public void testStartParallel() {
        CombinedVibration.Stereo stereo =
                (CombinedVibration.Stereo) CombinedVibration.startParallel()
                        .addVibrator(1, TEST_EFFECT)
                        .combine();
        assertEquals(1, stereo.getEffects().size());
        assertEquals(TEST_EFFECT, stereo.getEffects().get(1));
        assertEquals(TEST_EFFECT.getDuration(), stereo.getDuration());
    }

    @Test
    public void testStartParallelEmptyCombinationIsInvalid() {
        try {
            CombinedVibration.startParallel().combine();
            fail("Illegal combination, should throw IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    @Test
    public void testParallelEquals() {
        CombinedVibration otherMono = CombinedVibration.createParallel(
                VibrationEffect.get(VibrationEffect.EFFECT_CLICK));
        assertEquals(TEST_MONO, otherMono);
        assertEquals(TEST_MONO.hashCode(), otherMono.hashCode());

        CombinedVibration otherStereo = CombinedVibration.startParallel()
                .addVibrator(1, VibrationEffect.get(VibrationEffect.EFFECT_CLICK))
                .addVibrator(2, VibrationEffect.get(VibrationEffect.EFFECT_CLICK))
                .combine();
        assertEquals(TEST_STEREO, otherStereo);
        assertEquals(TEST_STEREO.hashCode(), otherStereo.hashCode());
    }

    @Test
    public void testParallelNotEqualsDifferentEffect() {
        CombinedVibration otherMono = CombinedVibration.createParallel(
                VibrationEffect.get(VibrationEffect.EFFECT_TICK));
        assertNotEquals(TEST_MONO, otherMono);
    }

    @Test
    public void testParallelNotEqualsDifferentVibrators() {
        CombinedVibration otherStereo = CombinedVibration.startParallel()
                .addVibrator(5, TEST_EFFECT)
                .combine();
        assertNotEquals(TEST_STEREO, otherStereo);
    }

    @Test
    public void testCreateSequential() {
        CombinedVibration.Sequential sequential =
                (CombinedVibration.Sequential) CombinedVibration.startSequential()
                        .addNext(TEST_MONO)
                        .addNext(TEST_STEREO, /* delay= */ 100)
                        .addNext(1, TEST_EFFECT)
                        .combine();
        assertEquals(
                Arrays.asList(TEST_MONO, TEST_STEREO,
                        CombinedVibration.startParallel().addVibrator(1,
                                TEST_EFFECT).combine()),
                sequential.getEffects());
        assertEquals(-1, sequential.getDuration());
    }

    @Test
    public void testStartSequentialEmptyCombinationIsInvalid() {
        try {
            CombinedVibration.startSequential().combine();
            fail("Illegal combination, should throw IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    @Test
    public void testSequentialEquals() {
        CombinedVibration otherSequential =
                CombinedVibration.startSequential()
                        .addNext(TEST_MONO)
                        .addNext(1, TEST_EFFECT, /* delay= */ 100)
                        .combine();
        assertEquals(TEST_SEQUENTIAL, otherSequential);
        assertEquals(TEST_SEQUENTIAL.hashCode(), otherSequential.hashCode());
    }

    @Test
    public void testSequentialNotEqualsDifferentEffects() {
        CombinedVibration otherSequential =
                CombinedVibration.startSequential()
                        .addNext(TEST_STEREO)
                        .combine();
        assertNotEquals(TEST_SEQUENTIAL, otherSequential);
    }

    @Test
    public void testSequentialNotEqualsDifferentOrder() {
        CombinedVibration otherSequential =
                CombinedVibration.startSequential()
                        .addNext(1, TEST_EFFECT, /* delay= */ 100)
                        .addNext(TEST_MONO)
                        .combine();
        assertNotEquals(TEST_SEQUENTIAL, otherSequential);
    }

    @Test
    public void testSequentialNotEqualsDifferentDelays() {
        CombinedVibration otherSequential =
                CombinedVibration.startSequential()
                        .addNext(TEST_MONO)
                        .addNext(1, TEST_EFFECT, /* delay= */ 1)
                        .combine();
        assertNotEquals(TEST_SEQUENTIAL, otherSequential);
    }

    @Test
    public void testSequentialNotEqualsDifferentVibrator() {
        CombinedVibration otherSequential =
                CombinedVibration.startSequential()
                        .addNext(TEST_MONO)
                        .addNext(5, TEST_EFFECT, /* delay= */ 100)
                        .combine();
        assertNotEquals(TEST_SEQUENTIAL, otherSequential);
    }

    @Test
    public void testParcelingParallelMono() {
        Parcel p = Parcel.obtain();
        TEST_MONO.writeToParcel(p, 0);
        p.setDataPosition(0);
        CombinedVibration parceled = CombinedVibration.CREATOR.createFromParcel(p);
        assertEquals(TEST_MONO, parceled);
    }

    @Test
    public void testParcelingParallelStereo() {
        Parcel p = Parcel.obtain();
        TEST_STEREO.writeToParcel(p, 0);
        p.setDataPosition(0);
        CombinedVibration parceled = CombinedVibration.CREATOR.createFromParcel(p);
        assertEquals(TEST_STEREO, parceled);
    }

    @Test
    public void testParcelingSequential() {
        Parcel p = Parcel.obtain();
        TEST_SEQUENTIAL.writeToParcel(p, 0);
        p.setDataPosition(0);
        CombinedVibration parceled = CombinedVibration.CREATOR.createFromParcel(p);
        assertEquals(TEST_SEQUENTIAL, parceled);
    }

    @Test
    public void testDescribeContents() {
        TEST_MONO.describeContents();
        TEST_STEREO.describeContents();
        TEST_SEQUENTIAL.describeContents();
    }

    @SuppressWarnings("ReturnValueIgnored")
    @Test
    public void testToString() {
        TEST_MONO.toString();
        TEST_STEREO.toString();
        TEST_SEQUENTIAL.toString();
    }

    @Test
    public void testParallelMonoCombinationDuration() {
        CombinedVibration effect = CombinedVibration.createParallel(
                VibrationEffect.createOneShot(100, 100));
        assertEquals(100, effect.getDuration());
    }

    @Test
    public void testParallelStereoCombinationDuration() {
        CombinedVibration effect = CombinedVibration.startParallel()
                .addVibrator(1, VibrationEffect.createOneShot(1, 100))
                .addVibrator(2, VibrationEffect.createOneShot(100, 100))
                .addVibrator(3, VibrationEffect.createOneShot(10, 100))
                .combine();
        assertEquals(100, effect.getDuration());
    }

    @Test
    public void testParallelCombinationUnknownDuration() {
        CombinedVibration effect = CombinedVibration.startParallel()
                .addVibrator(1, VibrationEffect.get(VibrationEffect.EFFECT_CLICK))
                .addVibrator(2, VibrationEffect.createOneShot(100, 100))
                .combine();
        assertEquals(-1, effect.getDuration());
    }

    @Test
    public void testParallelCombinationRepeatingDuration() {
        CombinedVibration effect = CombinedVibration.startParallel()
                .addVibrator(1, VibrationEffect.createWaveform(new long[]{1}, new int[]{1}, 0))
                .addVibrator(2, VibrationEffect.get(VibrationEffect.EFFECT_CLICK))
                .addVibrator(3, VibrationEffect.createOneShot(100, 100))
                .combine();
        assertEquals(Long.MAX_VALUE, effect.getDuration());
    }

    @Test
    public void testSequentialCombinationDuration() {
        CombinedVibration effect = CombinedVibration.startSequential()
                .addNext(1, VibrationEffect.createOneShot(10, 100), /* delay= */ 1)
                .addNext(1, VibrationEffect.createOneShot(10, 100), /* delay= */ 1)
                .addNext(1, VibrationEffect.createOneShot(10, 100), /* delay= */ 1)
                .combine();
        assertEquals(33, effect.getDuration());
    }

    @Test
    public void testSequentialCombinationUnknownDuration() {
        CombinedVibration effect = CombinedVibration.startSequential()
                .addNext(1, VibrationEffect.get(VibrationEffect.EFFECT_CLICK))
                .addNext(1, VibrationEffect.createOneShot(100, 100))
                .combine();
        assertEquals(-1, effect.getDuration());
    }

    @Test
    public void testSequentialCombinationRepeatingDuration() {
        CombinedVibration effect = CombinedVibration.startSequential()
                .addNext(1, VibrationEffect.createWaveform(new long[]{1}, new int[]{1}, 0))
                .addNext(1, VibrationEffect.get(VibrationEffect.EFFECT_CLICK))
                .addNext(1, VibrationEffect.createOneShot(100, 100))
                .combine();
        assertEquals(Long.MAX_VALUE, effect.getDuration());
    }
}
