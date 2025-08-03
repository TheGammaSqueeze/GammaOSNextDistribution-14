/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.providers.blockednumber;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.app.backup.BackupDataInput;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@RunWith(JUnit4.class)
public class BlockedNumberBackupAgentTest {

    private BlockedNumberBackupAgent mBlockedNumberBackupAgent;

    @Before
    public void setUp() throws Exception {
        initMocks(this);

        mBlockedNumberBackupAgent = new BlockedNumberBackupAgent();
    }

    /**
     * Verifies that attempting to restore from a version newer than what the backup agent defines
     * will result in no restored rows.
     */
    @Test
    public void testRestoreFromHigherVersion() throws IOException {
        // The backup format is not well structured, and consists of a bunch of persisted bytes, so
        // making the mock data is a bit gross.
        BackupDataInput backupDataInput = Mockito.mock(BackupDataInput.class);
        when(backupDataInput.getKey()).thenReturn("1");
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(new ByteArrayOutputStream());
        dataOutputStream.writeInt(10000); // version
        byte[] data = byteArrayOutputStream.toByteArray();
        when(backupDataInput.getDataSize()).thenReturn(data.length);
        when(backupDataInput.readEntityData(any(), anyInt(), anyInt())).thenAnswer(
                new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        byte[] bytes = invocation.getArgument(0);
                        System.arraycopy(data, 0, bytes, 0, data.length);
                        return null;
                    }
                }
        );

        // Well, this is awkward.  BackupDataInput has no way to get the number of data elements
        // it contains.  So we'll mock out "readNextHeader" to emulate there being some non-zero
        // number of items to restore.
        final int[] executionLimit = {1};
        when(backupDataInput.readNextHeader()).thenAnswer(
                new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        executionLimit[0]--;
                        return executionLimit[0] >= 0;
                    }
                }
        );

        mBlockedNumberBackupAgent.onRestore(backupDataInput, Integer.MAX_VALUE, null);
        assertEquals(0, mBlockedNumberBackupAgent.getRestoredCount());
    }
}
