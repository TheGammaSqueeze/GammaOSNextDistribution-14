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

package com.android.adservices.service.signals.evict;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.adservices.common.CommonFixture;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.service.signals.updateprocessors.UpdateOutput;

import org.junit.Rule;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class FifoSignalEvictorTest {

    private static final int MAX_ALLOWED_SIGNAL_SIZE = 100;
    private static final int MAX_ALLOWED_SIGNAL_SIZE_WITH_OVERSUBSCRIPTION = 200;

    SignalEvictor mFifoSignalEvictor = new FifoSignalEvictor();

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Test
    public void testFifoEvict_signalNotLargeEnoughForEviction() {
        List<DBProtectedSignal> signalInput = getListOfSignalWithCreationTimeIncrease(5, 0);
        UpdateOutput updateOutput = new UpdateOutput();

        assertFalse(
                mFifoSignalEvictor.evict(
                        CommonFixture.VALID_BUYER_1,
                        signalInput,
                        updateOutput,
                        MAX_ALLOWED_SIGNAL_SIZE,
                        MAX_ALLOWED_SIGNAL_SIZE_WITH_OVERSUBSCRIPTION));

        assertEquals(getListOfSignalWithCreationTimeIncrease(5, 0), signalInput);
        assertUpdateOutputEquals(new UpdateOutput(), updateOutput);
    }

    @Test
    public void testFifoEvict_signalEvicted() {
        List<DBProtectedSignal> signalInput = getListOfSignalWithCreationTimeIncrease(20, 0);
        List<DBProtectedSignal> expectedOutput = getListOfSignalWithCreationTimeIncrease(20, 10);
        int sizeOfSignalsInput = SignalSizeCalculator.calculate(signalInput);
        int sizeOfSignalsOutput = SignalSizeCalculator.calculate(expectedOutput);
        UpdateOutput updateOutput = new UpdateOutput();

        assertTrue(
                mFifoSignalEvictor.evict(
                        CommonFixture.VALID_BUYER_1,
                        signalInput,
                        updateOutput,
                        sizeOfSignalsOutput + 2,
                        sizeOfSignalsInput - 5));

        assertEquals(
                expectedOutput.stream()
                        .sorted(Comparator.comparing(DBProtectedSignal::getCreationTime))
                        .collect(Collectors.toList()),
                signalInput.stream()
                        .sorted(Comparator.comparing(DBProtectedSignal::getCreationTime))
                        .collect(Collectors.toList()));
        assertUpdateOutputEquals(getUpdatedOutput(10), updateOutput);
    }

    @Test
    public void testFifoEvict_signalLargerThanMaxButWithinOversubscribe() {
        List<DBProtectedSignal> signalInput = getListOfSignalWithCreationTimeIncrease(20, 0);
        int sizeOfSignalsInput = SignalSizeCalculator.calculate(signalInput);
        UpdateOutput updateOutput = new UpdateOutput();

        assertFalse(
                mFifoSignalEvictor.evict(
                        CommonFixture.VALID_BUYER_1,
                        signalInput,
                        updateOutput,
                        MAX_ALLOWED_SIGNAL_SIZE,
                        sizeOfSignalsInput + 5));

        assertEquals(getListOfSignalWithCreationTimeIncrease(20, 0), signalInput);
        assertUpdateOutputEquals(new UpdateOutput(), updateOutput);
    }

    private UpdateOutput getUpdatedOutput(int evicted) {
        UpdateOutput updateOutput = new UpdateOutput();
        updateOutput.getToRemove().addAll(getListOfSignalWithCreationTimeIncrease(evicted, 0));
        return updateOutput;
    }

    private List<DBProtectedSignal> getListOfSignalWithCreationTimeIncrease(
            int signalCount, int truncate) {
        ArrayList<DBProtectedSignal> list = new ArrayList<>();
        for (int i = truncate; i < signalCount; i++) {
            list.add(
                    DBProtectedSignal.builder()
                            .setBuyer(CommonFixture.VALID_BUYER_1)
                            .setCreationTime(
                                    CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.plusSeconds(
                                            200L * i))
                            .setId((long) i)
                            .setPackageName(CommonFixture.TEST_PACKAGE_NAME_1)
                            .setKey(("key" + i).getBytes(StandardCharsets.UTF_8))
                            .setValue(("val" + i).getBytes(StandardCharsets.UTF_8))
                            .build());
        }
        return list;
    }

    private void assertUpdateOutputEquals(UpdateOutput expected, UpdateOutput actual) {
        assertEquals(
                expected.getToAdd().stream()
                        .map(DBProtectedSignal.Builder::build)
                        .collect(Collectors.toList()),
                actual.getToAdd().stream()
                        .map(DBProtectedSignal.Builder::build)
                        .collect(Collectors.toList()));
        assertEquals(expected.getUpdateEncoderEvent(), actual.getUpdateEncoderEvent());
        assertEquals(expected.getToRemove(), actual.getToRemove());
        assertEquals(expected.getKeysTouched(), actual.getKeysTouched());
    }
}
