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

package com.android.server.thread;

import static com.android.server.thread.ThreadPersistentSettings.THREAD_ENABLED;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.res.Resources;
import android.os.PersistableBundle;
import android.util.AtomicFile;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;
import com.android.connectivity.resources.R;
import com.android.server.connectivity.ConnectivityResources;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link ThreadPersistentSettings}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class ThreadPersistentSettingsTest {
    @Mock private AtomicFile mAtomicFile;
    @Mock Resources mResources;
    @Mock ConnectivityResources mConnectivityResources;

    private ThreadPersistentSettings mThreadPersistentSettings;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mConnectivityResources.get()).thenReturn(mResources);
        when(mResources.getBoolean(eq(R.bool.config_thread_default_enabled))).thenReturn(true);

        FileOutputStream fos = mock(FileOutputStream.class);
        when(mAtomicFile.startWrite()).thenReturn(fos);
        mThreadPersistentSettings =
                new ThreadPersistentSettings(mAtomicFile, mConnectivityResources);
    }

    /** Called after each test */
    @After
    public void tearDown() {
        validateMockitoUsage();
    }

    @Test
    public void initialize_readsFromFile() throws Exception {
        byte[] data = createXmlForParsing(THREAD_ENABLED.key, false);
        setupAtomicFileMockForRead(data);

        mThreadPersistentSettings.initialize();

        assertThat(mThreadPersistentSettings.get(THREAD_ENABLED)).isFalse();
    }

    @Test
    public void initialize_ThreadDisabledInResources_returnsThreadDisabled() throws Exception {
        when(mResources.getBoolean(eq(R.bool.config_thread_default_enabled))).thenReturn(false);
        setupAtomicFileMockForRead(new byte[0]);

        mThreadPersistentSettings.initialize();

        assertThat(mThreadPersistentSettings.get(THREAD_ENABLED)).isFalse();
    }

    @Test
    public void initialize_ThreadDisabledInResourcesButEnabledInXml_returnsThreadEnabled()
            throws Exception {
        when(mResources.getBoolean(eq(R.bool.config_thread_default_enabled))).thenReturn(false);
        byte[] data = createXmlForParsing(THREAD_ENABLED.key, true);
        setupAtomicFileMockForRead(data);

        mThreadPersistentSettings.initialize();

        assertThat(mThreadPersistentSettings.get(THREAD_ENABLED)).isTrue();
    }

    @Test
    public void put_ThreadFeatureEnabledTrue_returnsTrue() throws Exception {
        mThreadPersistentSettings.put(THREAD_ENABLED.key, true);

        assertThat(mThreadPersistentSettings.get(THREAD_ENABLED)).isTrue();
        // Confirm that file writes have been triggered.
        verify(mAtomicFile).startWrite();
        verify(mAtomicFile).finishWrite(any());
    }

    @Test
    public void put_ThreadFeatureEnabledFalse_returnsFalse() throws Exception {
        mThreadPersistentSettings.put(THREAD_ENABLED.key, false);

        assertThat(mThreadPersistentSettings.get(THREAD_ENABLED)).isFalse();
        // Confirm that file writes have been triggered.
        verify(mAtomicFile).startWrite();
        verify(mAtomicFile).finishWrite(any());
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
        when(is.available()).thenReturn(dataToRead.length).thenReturn(0);
        doAnswer(
                        invocation -> {
                            byte[] data = invocation.getArgument(0);
                            int pos = invocation.getArgument(1);
                            if (pos == dataToRead.length) return 0; // read complete.
                            System.arraycopy(dataToRead, 0, data, 0, dataToRead.length);
                            return dataToRead.length;
                        })
                .when(is)
                .read(any(), anyInt(), anyInt());
    }
}
