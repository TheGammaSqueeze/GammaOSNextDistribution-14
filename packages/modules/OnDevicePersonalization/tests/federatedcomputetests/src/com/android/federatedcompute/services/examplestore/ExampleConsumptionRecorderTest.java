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

package com.android.federatedcompute.services.examplestore;

import static com.google.common.truth.Truth.assertThat;

import android.federatedcompute.common.ExampleConsumption;

import com.android.federatedcompute.services.examplestore.ExampleConsumptionRecorder.SingleQueryRecorder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.charset.Charset;

@RunWith(JUnit4.class)
public final class ExampleConsumptionRecorderTest {

    @Test
    public void testNoIncrement() {
        ExampleConsumptionRecorder recorder = new ExampleConsumptionRecorder();
        assertThat(recorder.finishRecordingAndGet()).isEmpty();
    }

    @Test
    public void testIncrementSameTaskNameAndCriteria() {
        String taskName = "taskName";
        byte[] selectionCriteria = new byte[] {10, 0, 1};
        ExampleConsumptionRecorder recorder = new ExampleConsumptionRecorder();
        byte[] token1 = "token1".getBytes(Charset.defaultCharset());
        SingleQueryRecorder singleRecorder =
                recorder.createRecorderForTracking(taskName, selectionCriteria);
        singleRecorder.incrementAndUpdateResumptionToken(token1);
        byte[] token2 = "token2".getBytes(Charset.defaultCharset());
        singleRecorder.incrementAndUpdateResumptionToken(token2);
        assertThat(recorder.finishRecordingAndGet())
                .containsExactly(
                        new ExampleConsumption.Builder()
                                .setTaskId(taskName)
                                .setExampleCount(2)
                                .setSelectionCriteria(selectionCriteria)
                                .setResumptionToken(token2)
                                .build());
    }

    @Test
    public void testIncrementDifferentTaskName() {
        String taskName = "taskName";
        byte[] criteria = new byte[] {10, 0, 1};
        ExampleConsumptionRecorder recorder = new ExampleConsumptionRecorder();
        byte[] token1 = "token1".getBytes(Charset.defaultCharset());
        SingleQueryRecorder singleRecorder1 =
                recorder.createRecorderForTracking(taskName, criteria);
        singleRecorder1.incrementAndUpdateResumptionToken(token1);
        String taskName2 = "taskName2";
        byte[] token2 = "token2".getBytes(Charset.defaultCharset());
        SingleQueryRecorder singleQueryRecorder2 =
                recorder.createRecorderForTracking(taskName2, criteria);
        singleQueryRecorder2.incrementAndUpdateResumptionToken(token2);
        assertThat(recorder.finishRecordingAndGet())
                .containsExactly(
                        new ExampleConsumption.Builder()
                                .setTaskId(taskName)
                                .setSelectionCriteria(criteria)
                                .setExampleCount(1)
                                .setResumptionToken(token1)
                                .build(),
                        new ExampleConsumption.Builder()
                                .setTaskId(taskName2)
                                .setExampleCount(1)
                                .setSelectionCriteria(criteria)
                                .setResumptionToken(token2)
                                .build());
    }
}
