/*
 * Copyright (c) 2021 The Android Open Source Project
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

package android.net.wifi;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.validateMockitoUsage;

import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;


/**
 * Unit tests for {@link android.net.wifi.WifiConnectedSessionInfo}.
 */
@SmallTest
public class WifiConnectedSessionInfoTest {
    /**
     * Setup before tests.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    /**
     * Clean up after tests.
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
    }

    /**
     * Verify parcel read/write for Wifi connected session information.
     */
    @Test
    public void verifySessionInfoWriteAndThenRead() throws Exception {
        WifiConnectedSessionInfo writeResult = createResult();
        WifiConnectedSessionInfo readResult = parcelWriteRead(writeResult);
        assertWifiConnectedSessionInfoEquals(writeResult, readResult);
    }

    /**
     * Write the provided {@link WifiConnectedSessionInfo} to a parcel and deserialize it.
     */
    private static WifiConnectedSessionInfo parcelWriteRead(
            WifiConnectedSessionInfo writeResult) throws Exception {
        Parcel parcel = Parcel.obtain();
        writeResult.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);    // Rewind data position back to the beginning for read.
        return WifiConnectedSessionInfo.CREATOR.createFromParcel(parcel);
    }

    private static WifiConnectedSessionInfo createResult() {
        return new WifiConnectedSessionInfo.Builder(101)
                .setUserSelected(true)
                .build();
    }

    private static void assertWifiConnectedSessionInfoEquals(
            WifiConnectedSessionInfo expected,
            WifiConnectedSessionInfo actual) {
        assertEquals(expected.getSessionId(), actual.getSessionId());
        assertEquals(expected.isUserSelected(), actual.isUserSelected());
    }
}
