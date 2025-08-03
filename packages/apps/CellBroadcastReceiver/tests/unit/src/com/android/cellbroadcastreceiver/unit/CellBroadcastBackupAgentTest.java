/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cellbroadcastreceiver.unit;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupHelperDispatcher;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;

import com.android.cellbroadcastreceiver.CellBroadcastBackupAgent;
import com.android.cellbroadcastreceiver.CellBroadcastInternalReceiver;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;

/**
 * Unittest for class com.android.cellbroadcastreceiver.CellBroadcastBackupAgent.
 */
public class CellBroadcastBackupAgentTest {
    @Mock
    private BackupHelperDispatcher mMockDispatcher;
    @Mock
    private Context mMockContext;
    /** The CellBroadcastBackupAgent object to test against. */
    private CellBroadcastBackupAgent mBackupAgentUT = new CellBroadcastBackupAgent();

    /**
     * Replace BackupAgentHelper#mDispatcher with mock object.
     */
    private void mockBackupDispatcher() throws Exception {
        Field field = BackupAgentHelper.class.getDeclaredField("mDispatcher");
        field.setAccessible(true);
        field.set(mBackupAgentUT, mMockDispatcher);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testOnCreate() throws Exception {
        final String packageName = CellBroadcastInternalReceiver.class.getPackage().getName();
        doReturn(packageName).when(mMockContext).getPackageName();

        mBackupAgentUT.attach(mMockContext);
        mockBackupDispatcher();
        mBackupAgentUT.onCreate();
        // Ideally we should verify creation of SharedPreferencesBackupHelper, but it's not quite
        // testable. So now we just match it with any().
        verify(mMockDispatcher).addHelper(eq(CellBroadcastBackupAgent.SHARED_KEY), any());
    }

    @Test
    public void testRestoreFinish() throws Exception {
        final String packageName = CellBroadcastInternalReceiver.class.getPackage().getName();
        final String className = CellBroadcastInternalReceiver.class.getName();
        doReturn(mMockContext).when(mMockContext).getApplicationContext();
        doReturn(packageName).when(mMockContext).getPackageName();
        mockBackupDispatcher();
        mBackupAgentUT.attach(mMockContext);

        ArgumentCaptor<Intent> intentArg = ArgumentCaptor.forClass(Intent.class);
        mBackupAgentUT.onRestoreFinished();
        verify(mMockContext).sendBroadcastAsUser(intentArg.capture(), eq(UserHandle.SYSTEM));
        assertEquals(packageName, intentArg.getValue().getComponent().getPackageName());
        assertEquals(className, intentArg.getValue().getComponent().getClassName());
    }
}
