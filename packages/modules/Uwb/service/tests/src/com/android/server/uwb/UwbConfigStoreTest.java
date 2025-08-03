/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.uwb;

import static com.android.server.uwb.UwbConfigStore.STORE_FILE_SHARED_GENERAL;
import static com.android.server.uwb.UwbConfigStore.STORE_FILE_USER_GENERAL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.content.Context;
import android.os.Handler;
import android.os.UserManager;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.proto.uwb.UwbConfigProto;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
@Presubmit
public class UwbConfigStoreTest {
    @Mock private Context mContext;
    @Mock private Handler mHandler;
    @Mock private UwbInjector mUwbInjector;
    @Mock private AlarmManager mAlarmManager;
    @Mock private UserManager mUserManager;
    @Mock private UwbConfigStore.StoreFile  mSharedStoreFile;
    private MockStoreData mStoreData;
    private MockStoreFile mUserStoreFile;
    private final List<UwbConfigStore.StoreFile> mUserStores = new ArrayList<>();

    private UwbConfigStore mUwbConfigStore;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mContext.getSystemService(AlarmManager.class)).thenReturn(mAlarmManager);
        when(mUwbInjector.getUserManager()).thenReturn(mUserManager);

        List<UwbConfigStore.StoreFile> mSharedStores = new ArrayList<>();
        mSharedStores.add(mSharedStoreFile);
        mUwbConfigStore = new UwbConfigStore(mContext, mHandler, mUwbInjector, mSharedStores);
        mUserStoreFile = new MockStoreFile(STORE_FILE_USER_GENERAL);
        mStoreData = new MockStoreData(STORE_FILE_USER_GENERAL);
        mUserStores.add(mUserStoreFile);
    }

    @Test
    public void testRegisterStoreData() {
        mUwbConfigStore.registerStoreData(mStoreData);
        assertEquals(1, mUwbConfigStore.getStoreDataList().size());
    }

    @Test
    public void testLoadFromStore() throws IOException {
        UwbConfigStore.StoreData userStoreData = mock(UwbConfigStore.StoreData.class);

        when(userStoreData.getStoreFileId()).thenReturn(STORE_FILE_USER_GENERAL);
        when(userStoreData.hasNewDataToSerialize()).thenReturn(true);

        assertTrue(mUwbConfigStore.registerStoreData(userStoreData));

        mUwbConfigStore.setUserStores(mUserStores);
        mUwbConfigStore.write(false);
        mUwbConfigStore.saveToStore(true);

        verify(userStoreData, times(2)).hasNewDataToSerialize();
        assertTrue(mUserStoreFile.isStoreWritten());
        assertTrue(mUwbConfigStore.loadFromStore());
    }

    @Test
    public void testSharedData() throws IOException {
        UwbConfigStore.StoreData sharedStoreData = mock(UwbConfigStore.StoreData.class);

        when(sharedStoreData.getStoreFileId()).thenReturn(STORE_FILE_SHARED_GENERAL);
        when(sharedStoreData.hasNewDataToSerialize()).thenReturn(true);

        assertTrue(mUwbConfigStore.registerStoreData(sharedStoreData));

        mUwbConfigStore.write(true);

        verify(sharedStoreData).hasNewDataToSerialize();
        assertTrue(mUwbConfigStore.loadFromStore());
    }

    @Test
    public void testForceWrite() throws IOException {
        UwbConfigStore.StoreData userStoreData = mock(UwbConfigStore.StoreData.class);

        when(userStoreData.getStoreFileId()).thenReturn(STORE_FILE_USER_GENERAL);
        when(userStoreData.hasNewDataToSerialize()).thenReturn(true);

        assertTrue(mUwbConfigStore.registerStoreData(userStoreData));

        mUwbConfigStore.setUserStores(mUserStores);
        mUwbConfigStore.write(true);

        verify(userStoreData).hasNewDataToSerialize();

        assertTrue(mUserStoreFile.isStoreWritten());
    }

    @Test
    public void testHandleUserSwitch() throws IOException {
        UwbConfigStore.StoreData userStoreData = mock(UwbConfigStore.StoreData.class);

        when(userStoreData.getStoreFileId()).thenReturn(STORE_FILE_USER_GENERAL);
        when(userStoreData.hasNewDataToSerialize()).thenReturn(true);
        when(mUserManager.isUserUnlockingOrUnlocked(any())).thenReturn(true);

        assertTrue(mUwbConfigStore.registerStoreData(userStoreData));

        mUwbConfigStore.setUserStores(mUserStores);
        mUwbConfigStore.write(false);
        // Verify user store is written only after user switch
        assertFalse(mUserStoreFile.isStoreWritten());

        mUwbConfigStore.handleUserSwitch(2);
        assertTrue(mUserStoreFile.isStoreWritten());
    }

    /**
     * Mock Store File to redirect all file writes from WifiConfigStore to local buffers.
     * This can be used to examine the data output by WifiConfigStore.
     */
    private static class MockStoreFile extends UwbConfigStore.StoreFile {
        private byte[] mStoreBytes;
        private boolean mStoreWritten;

        MockStoreFile(@UwbConfigStore.StoreFileId int fileId) {
            super(new File("MockStoreFile"), fileId);
        }

        @Override
        public byte[] readRawData() {
            return mStoreBytes;
        }

        @Override
        public void storeRawDataToWrite(byte[] data) {
            mStoreBytes = data;
            mStoreWritten = false;
        }

        @Override
        public void writeBufferedRawData() {
            mStoreWritten = true;
        }

        public boolean isStoreWritten() {
            return mStoreWritten;
        }
    }

    /**
     * Mock data container for providing test data for the store file.
     */
    private static class MockStoreData implements UwbConfigStore.StoreData {
        private static final String TEST_STORE_DATA = "TestStoreData";

        @UwbConfigStore.StoreFileId
        private final int mFileId;
        private byte[] mData;

        MockStoreData(@UwbConfigStore.StoreFileId int fileId) {
            mFileId = fileId;
        }

        @Override
        public void serializeData(UwbConfigProto.UwbConfig.Builder builder)
                throws NullPointerException {

        }

        @Override
        public void deserializeData(UwbConfigProto.UwbConfig uwbConfig) {
        }

        @Override
        public void resetData() {
            mData = null;
        }

        @Override
        public boolean hasNewDataToSerialize() {
            return true;
        }

        @Override
        public String getName() {
            return TEST_STORE_DATA;
        }

        @Override
        @UwbConfigStore.StoreFileId
        public int getStoreFileId() {
            return mFileId;
        }

        public byte[] getData() {
            return mData;
        }

        public void setData(byte[] data) {
            mData = data;
        }

    }
}
