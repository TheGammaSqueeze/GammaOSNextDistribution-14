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

package com.android.server.wifi;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link com.android.server.wifi.BackupRestoreControllerTest}.
 */
@SmallTest
public class BackupRestoreControllerTest extends WifiBaseTest {

    public static final String XML_GENERAL_BEGINNING =
            "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n";

    public static final String TEST_WIFI_UNION_BACKUP_DATA_WITH_SETTINGS_IN_V =
            XML_GENERAL_BEGINNING
            + "<WifiBackupDataUnion>\n"
            + WifiSettingsBackupRestoreTest.generateTestWifiSettingsTestingXml("")
            + "</WifiBackupDataUnion>\n";

    @Mock WifiSettingsBackupRestore mWifiSettingsBackupRestore;
    @Mock Clock mClock;


    private BackupRestoreController mBackupRestoreController;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mBackupRestoreController = new BackupRestoreController(
            mWifiSettingsBackupRestore, mClock);
    }

    /**
     * Verify that a data return when retrieve backup data is serialized correctly.
     */
    @Test
    public void testNormalBackupDataSerializedDeserialized() {
        mBackupRestoreController.retrieveBackupData();
        verify(mWifiSettingsBackupRestore).retrieveBackupDataFromSettingsConfigStore(any(), any());
        mBackupRestoreController.parserBackupDataAndDispatch(
                TEST_WIFI_UNION_BACKUP_DATA_WITH_SETTINGS_IN_V.getBytes());
        verify(mWifiSettingsBackupRestore).restoreSettingsFromBackupData(any(), eq(1));
    }
}
