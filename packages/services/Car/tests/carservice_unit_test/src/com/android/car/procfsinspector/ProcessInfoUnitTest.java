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

package com.android.car.procfsinspector;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.Parcel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@RunWith(MockitoJUnitRunner.class)
public final class ProcessInfoUnitTest {

    private ProcessInfo mProcessInfo;

    @Before
    public void setUp() {
        mProcessInfo = new ProcessInfo(1, 2);
    }

    @Test
    public void testDescribeContents() {
        assertThat(mProcessInfo.describeContents()).isEqualTo(0);
    }

    @Test
    public void testWriteToParcel() {
        ArgumentCaptor<Integer> parcelArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
        Parcel parcel = mock(Parcel.class);

        mProcessInfo.writeToParcel(parcel, 0);

        verify(parcel, times(2)).writeInt(parcelArgumentCaptor.capture());

        List<Integer> values = parcelArgumentCaptor.getAllValues();
        assertThat(values).containsExactlyElementsIn(Arrays.asList(1, 2));
    }

    @Test
    public void testEquals() {
        ProcessInfo processInfo = new ProcessInfo(1, 2);
        assertThat(mProcessInfo.equals(processInfo)).isTrue();
    }

    @Test
    public void testNotEquals() {
        ProcessInfo processInfo = new ProcessInfo(3, 2);
        assertThat(mProcessInfo.equals(processInfo)).isFalse();
    }

    @Test
    public void testEqualsWithInvalidObject() {
        assertThat(mProcessInfo.equals(12)).isFalse();
    }

    @Test
    public void testHashCode() {
        assertThat(mProcessInfo.hashCode()).isEqualTo(Objects.hash(1, 2));
    }

    @Test
    public void testToString() {
        assertThat(mProcessInfo.toString()).isEqualTo("pid = 1, uid = 2");
    }
}
