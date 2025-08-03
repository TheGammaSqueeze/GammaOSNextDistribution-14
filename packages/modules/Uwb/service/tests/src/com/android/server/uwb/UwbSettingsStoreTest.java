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

package com.android.server.uwb;

import static com.android.server.uwb.UwbSettingsStore.SETTINGS_TOGGLE_STATE;
import static com.android.server.uwb.UwbSettingsStore.SETTINGS_TOGGLE_STATE_KEY_FOR_MIGRATION;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Handler;
import android.os.PersistableBundle;
import android.os.test.TestLooper;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.util.AtomicFile;
import android.uwb.UwbManager;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

/**
 * Unit tests for {@link com.android.server.uwb.UwbSettingsStore}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class UwbSettingsStoreTest {
    @Mock private Context mContext;
    @Mock private AtomicFile mAtomicFile;
    @Mock private UwbInjector mUwbInjector;

    private TestLooper mLooper;
    private UwbSettingsStore mUwbSettingsStore;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mLooper = new TestLooper();

        FileOutputStream fos = mock(FileOutputStream.class);
        when(mAtomicFile.startWrite()).thenReturn(fos);
        mUwbSettingsStore = new UwbSettingsStore(
                mContext, new Handler(mLooper.getLooper()), mAtomicFile, mUwbInjector);
    }

    /**
     * Called after each test
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
    }

    @Test
    public void testSetterGetter() throws Exception {
        assertThat(mUwbSettingsStore.get(SETTINGS_TOGGLE_STATE)).isTrue();
        mUwbSettingsStore.put(SETTINGS_TOGGLE_STATE, false);
        mLooper.dispatchAll();
        assertThat(mUwbSettingsStore.get(SETTINGS_TOGGLE_STATE)).isFalse();

        // Confirm that file writes have been triggered.
        verify(mAtomicFile).startWrite();
        verify(mAtomicFile).finishWrite(any());
    }

    @Test
    public void testChangeListener() {
        UwbSettingsStore.OnSettingsChangedListener listener = mock(
                UwbSettingsStore.OnSettingsChangedListener.class);
        mUwbSettingsStore.registerChangeListener(SETTINGS_TOGGLE_STATE, listener,
                new Handler(mLooper.getLooper()));

        mUwbSettingsStore.put(SETTINGS_TOGGLE_STATE, true);
        mLooper.dispatchAll();
        verify(listener).onSettingsChanged(SETTINGS_TOGGLE_STATE, true);

        mUwbSettingsStore.unregisterChangeListener(SETTINGS_TOGGLE_STATE, listener);
        mUwbSettingsStore.put(SETTINGS_TOGGLE_STATE, false);
        mLooper.dispatchAll();
        verifyNoMoreInteractions(listener);
    }

    @Test
    public void testLoadFromStore() throws Exception {
        byte[] data = createXmlForParsing(SETTINGS_TOGGLE_STATE.key, false);
        setupAtomicFileMockForRead(data);

        // Trigger file read.
        mUwbSettingsStore.initialize();
        mLooper.dispatchAll();

        // Return the persisted value.
        assertThat(mUwbSettingsStore.get(SETTINGS_TOGGLE_STATE)).isFalse();

        // No write should be triggered on load.
        verify(mAtomicFile, never()).startWrite();
    }

    @Test
    public void testMigrationWhenStoreFileEmptyOrNotFound() throws Exception {
        doThrow(new FileNotFoundException()).when(mAtomicFile).openRead();

        // Toggle off before migration.
        when(mUwbInjector.getGlobalSettingsInt(SETTINGS_TOGGLE_STATE_KEY_FOR_MIGRATION)).thenReturn(
                UwbManager.AdapterStateCallback.STATE_DISABLED);

        // Trigger file read.
        mUwbSettingsStore.initialize();
        mLooper.dispatchAll();

        // Return the migrated value.
        assertThat(mUwbSettingsStore.get(SETTINGS_TOGGLE_STATE)).isFalse();

        // Write should be triggered after migration.
        verify(mAtomicFile, times(1)).startWrite();
    }


    @Test
    public void testNoMigrationLoadFromStoreWhenStoreFileEmptyOrNotFound() throws Exception {
        doThrow(new FileNotFoundException()).when(mAtomicFile).openRead();
        doThrow(new Settings.SettingNotFoundException("")).when(mUwbInjector).getGlobalSettingsInt(
                SETTINGS_TOGGLE_STATE_KEY_FOR_MIGRATION);

        // Trigger file read.
        mUwbSettingsStore.initialize();
        mLooper.dispatchAll();

        // Return the default value.
        assertThat(mUwbSettingsStore.get(SETTINGS_TOGGLE_STATE)).isTrue();

        // No write should be triggered on load since no migration was done.
        verify(mAtomicFile, never()).startWrite();
    }

    private byte[] createXmlForParsing(String key, Boolean value) throws Exception {
        PersistableBundle bundle = new PersistableBundle();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bundle.putBoolean(key, value);
        bundle.writeToStream(outputStream);
        return outputStream.toByteArray();
    }

    private void setupAtomicFileMockForRead(byte[] dataToRead) throws Exception {
        FileInputStream is = mock(FileInputStream.class);
        when(mAtomicFile.openRead()).thenReturn(is);
        when(is.available())
                .thenReturn(dataToRead.length)
                .thenReturn(0);
        doAnswer(invocation -> {
            byte[] data = invocation.getArgument(0);
            int pos = invocation.getArgument(1);
            if (pos == dataToRead.length) return 0; // read complete.
            System.arraycopy(dataToRead, 0, data, 0, dataToRead.length);
            return dataToRead.length;
        }).when(is).read(any(), anyInt(), anyInt());
    }
}
