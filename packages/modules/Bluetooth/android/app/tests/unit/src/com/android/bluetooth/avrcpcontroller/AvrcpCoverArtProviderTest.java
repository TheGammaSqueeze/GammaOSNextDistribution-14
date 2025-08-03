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

package com.android.bluetooth.avrcpcontroller;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.net.Uri;

import androidx.test.filters.SmallTest;
import androidx.test.rule.ServiceTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.FileNotFoundException;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AvrcpCoverArtProviderTest {
    private static final String TEST_MODE = "test_mode";

    private final byte[] mTestAddress = new byte[]{01, 01, 01, 01, 01, 01};

    private BluetoothAdapter mAdapter;
    private BluetoothDevice mTestDevice = null;
    private AvrcpCoverArtProvider mArtProvider;

    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();
    @Mock private Uri mUri;
    @Mock private AdapterService mAdapterService;
    @Mock private AvrcpControllerNativeInterface mNativeInterface;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        TestUtils.setAdapterService(mAdapterService);
        AvrcpControllerNativeInterface.setInstance(mNativeInterface);
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mTestDevice = mAdapter.getRemoteDevice(mTestAddress);
        mArtProvider = new AvrcpCoverArtProvider();
    }

    @After
    public void tearDown() throws Exception {
        AvrcpControllerNativeInterface.setInstance(null);
        TestUtils.clearAdapterService(mAdapterService);
    }

    @Test
    public void openFile_whenFileNotFoundExceptionIsCaught() {
        when(mUri.getQueryParameter("device")).thenReturn("00:01:02:03:04:05");
        when(mUri.getQueryParameter("uuid")).thenReturn("1111");
        assertThat(mArtProvider.onCreate()).isTrue();

        assertThrows(FileNotFoundException.class, () -> mArtProvider.openFile(mUri, TEST_MODE));
    }

    @Test
    public void openFile_whenNullPointerExceptionIsCaught() {
        when(mUri.getQueryParameter("device")).thenThrow(NullPointerException.class);

        assertThrows(FileNotFoundException.class, () -> mArtProvider.openFile(mUri, TEST_MODE));
    }

    @Test
    public void openFile_whenIllegalArgumentExceptionIsCaught() {
        // This causes device address to be null, invoking an IllegalArgumentException
        when(mUri.getQueryParameter("device")).thenReturn(null);
        when(mUri.getQueryParameter("uuid")).thenReturn("1111");
        assertThat(mArtProvider.onCreate()).isTrue();

        assertThrows(FileNotFoundException.class, () -> mArtProvider.openFile(mUri, TEST_MODE));
    }

    @Test
    public void getImageUri_withEmptyImageUuid() {
        assertThat(AvrcpCoverArtProvider.getImageUri(mTestDevice, "")).isNull();
    }

    @Test
    public void getImageUri_withValidImageUuid() {
        String uuid = "1111";
        Uri expectedUri = AvrcpCoverArtProvider.CONTENT_URI.buildUpon().appendQueryParameter(
                "device", mTestDevice.getAddress()).appendQueryParameter("uuid", uuid).build();

        assertThat(AvrcpCoverArtProvider.getImageUri(mTestDevice, uuid)).isEqualTo(expectedUri);
    }

    @Test
    public void onCreate() {
        assertThat(mArtProvider.onCreate()).isTrue();
    }

    @Test
    public void query() {
        assertThat(mArtProvider.query(null, null, null, null, null)).isNull();
    }

    @Test
    public void insert() {
        assertThat(mArtProvider.insert(null, null)).isNull();
    }

    @Test
    public void delete() {
        assertThat(mArtProvider.delete(null, null, null)).isEqualTo(0);
    }

    @Test
    public void update() {
        assertThat(mArtProvider.update(null, null, null, null)).isEqualTo(0);
    }

    @Test
    public void getType() {
        assertThat(mArtProvider.getType(null)).isNull();
    }
}
