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

package android.federatedcompute.common;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.protobuf.ByteString;

import org.junit.Test;
import org.junit.runner.RunWith;

import javax.annotation.Nullable;

@RunWith(AndroidJUnit4.class)
public final class ExampleConsumptionTest {

    @Test
    public void testBuilder_emptyTaskName() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ExampleConsumption.Builder()
                                .setTaskId("")
                                .setExampleCount(10)
                                .setSelectionCriteria(new byte[] {10, 0, 1})
                                .build());
    }

    @Test
    public void testBuilder_nullTaskName() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ExampleConsumption.Builder()
                                .setTaskId(null)
                                .setExampleCount(10)
                                .setSelectionCriteria(new byte[] {10, 0, 1})
                                .build());
    }

    @Test
    public void testBuilder_normalCaseWithoutResumptionToken() {
        String taskId = "my_task";
        byte[] selectionCriteria = new byte[] {10, 0, 1};
        int exampleCount = 10;
        ExampleConsumption consumption =
                createExampleConsumption(taskId, selectionCriteria, exampleCount, null);
        assertThat(consumption.getTaskId()).isEqualTo(taskId);
        assertThat(consumption.getExampleCount()).isEqualTo(exampleCount);
        assertThat(ByteString.copyFrom(consumption.getSelectionCriteria()))
                .isEqualTo(ByteString.copyFrom(selectionCriteria));
        assertThat(consumption.getResumptionToken()).isNull();
    }

    @Test
    public void testBuilder_normalCaseWithResumptionToken() {
        String taskId = "my_task";
        byte[] selectionCriteria = new byte[] {10, 0, 1};
        int exampleCount = 10;
        byte[] resumptionToken = new byte[] {25, 10, 4, 56};
        ExampleConsumption consumption =
                createExampleConsumption(taskId, selectionCriteria, exampleCount, resumptionToken);
        assertThat(consumption.getTaskId()).isEqualTo(taskId);
        assertThat(consumption.getExampleCount()).isEqualTo(exampleCount);
        assertThat(ByteString.copyFrom(consumption.getSelectionCriteria()))
                .isEqualTo(ByteString.copyFrom(selectionCriteria));
        assertThat(ByteString.copyFrom(consumption.getResumptionToken()))
                .isEqualTo(ByteString.copyFrom(resumptionToken));
    }

    @Test
    public void testWriteToParcel() {
        String taskId = "my_task";
        byte[] selectionCriteria = new byte[] {10, 0, 1};
        int exampleCount = 10;
        byte[] resumptionToken = new byte[] {25, 10, 4, 56};
        ExampleConsumption consumption =
                createExampleConsumption(taskId, selectionCriteria, exampleCount, resumptionToken);

        Parcel parcel = Parcel.obtain();
        consumption.writeToParcel(parcel, 0);

        // Reset data position before recreating the parcelable.
        parcel.setDataPosition(0);
        ExampleConsumption recoveredConsumption =
                ExampleConsumption.CREATOR.createFromParcel(parcel);
        assertThat(recoveredConsumption.getTaskId()).isEqualTo(taskId);
        assertThat(recoveredConsumption.getExampleCount()).isEqualTo(exampleCount);
        assertThat(ByteString.copyFrom(recoveredConsumption.getSelectionCriteria()))
                .isEqualTo(ByteString.copyFrom(selectionCriteria));
        assertThat(ByteString.copyFrom(recoveredConsumption.getResumptionToken()))
                .isEqualTo(ByteString.copyFrom(resumptionToken));
    }

    private static ExampleConsumption createExampleConsumption(
            String taskId,
            byte[] selectionCriteria,
            int exampleCount,
            @Nullable byte[] resumptionToken) {
        ExampleConsumption.Builder builder =
                new ExampleConsumption.Builder()
                        .setTaskId(taskId)
                        .setSelectionCriteria(selectionCriteria)
                        .setExampleCount(exampleCount);
        if (resumptionToken != null) {
            builder.setResumptionToken(resumptionToken);
        }
        return builder.build();
    }
}
