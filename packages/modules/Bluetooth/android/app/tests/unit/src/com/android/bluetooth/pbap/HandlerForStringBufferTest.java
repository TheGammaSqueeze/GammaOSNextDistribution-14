/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.pbap;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.obex.Operation;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.OutputStream;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class HandlerForStringBufferTest {

    @Mock
    private Operation mOperation;

    @Mock
    private OutputStream mOutputStream;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mOperation.openOutputStream()).thenReturn(mOutputStream);
    }

    @Test
    public void init_withNonNullOwnerVCard_returnsTrue() throws Exception {
        String ownerVcard = "testOwnerVcard";
        HandlerForStringBuffer buffer = new HandlerForStringBuffer(mOperation, ownerVcard);

        assertThat(buffer.init()).isTrue();
        verify(mOutputStream).write(ownerVcard.getBytes());
    }

    @Test
    public void init_withNullOwnerVCard_returnsTrue() throws Exception {
        String ownerVcard = null;
        HandlerForStringBuffer buffer = new HandlerForStringBuffer(mOperation, ownerVcard);

        assertThat(buffer.init()).isTrue();
        verify(mOutputStream, never()).write(any());
    }

    @Test
    public void init_withIOExceptionWhenOpeningOutputStream_returnsFalse() throws Exception {
        doThrow(new IOException()).when(mOperation).openOutputStream();

        String ownerVcard = "testOwnerVcard";
        HandlerForStringBuffer buffer = new HandlerForStringBuffer(mOperation, ownerVcard);

        assertThat(buffer.init()).isFalse();
    }

    @Test
    public void writeVCard_withNonNullOwnerVCard_returnsTrue() throws Exception {
        String ownerVcard = null;
        HandlerForStringBuffer buffer = new HandlerForStringBuffer(mOperation, ownerVcard);
        buffer.init();

        String newVcard = "newEntryVcard";

        assertThat(buffer.writeVCard(newVcard)).isTrue();
    }

    @Test
    public void writeVCard_withNullOwnerVCard_returnsFalse() throws Exception {
        String ownerVcard = null;
        HandlerForStringBuffer buffer = new HandlerForStringBuffer(mOperation, ownerVcard);
        buffer.init();

        String newVcard = null;

        assertThat(buffer.writeVCard(newVcard)).isFalse();
    }

    @Test
    public void writeVCard_withIOExceptionWhenWritingToStream_returnsFalse() throws Exception {
        doThrow(new IOException()).when(mOutputStream).write(any(byte[].class));
        HandlerForStringBuffer buffer = new HandlerForStringBuffer(mOperation, /*ownerVcard=*/null);
        buffer.init();

        String newVCard = "newVCard";

        assertThat(buffer.writeVCard(newVCard)).isFalse();
    }

    @Test
    public void terminate() throws Exception {
        String ownerVcard = "testOwnerVcard";
        HandlerForStringBuffer buffer = new HandlerForStringBuffer(mOperation, ownerVcard);
        buffer.init();

        buffer.terminate();

        verify(mOutputStream).close();
    }
}