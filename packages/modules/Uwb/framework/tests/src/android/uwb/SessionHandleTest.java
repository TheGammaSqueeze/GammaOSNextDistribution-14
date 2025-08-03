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

package android.uwb;

import static org.junit.Assert.assertEquals;

import android.content.AttributionSource;
import android.os.Parcel;
import android.os.Process;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test of {@link SessionHandle}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SessionHandleTest {
    private static final int UID = Process.myUid();
    private static final String PACKAGE_NAME = "com.uwb.test";
    private static final AttributionSource ATTRIBUTION_SOURCE =
            new AttributionSource.Builder(UID).setPackageName(PACKAGE_NAME).build();
    private static final int HANDLE_ID = 12;
    private static final int PID = Process.myPid();

    @Test
    public void testBasic() {
        SessionHandle handle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);
        assertEquals(handle.getId(), HANDLE_ID);
        assertEquals(handle.getPackageName(), PACKAGE_NAME);
        assertEquals(handle.getUid(), UID);
        assertEquals(handle.getPid(), PID);
    }

    @Test
    public void testParcel() {
        Parcel parcel = Parcel.obtain();
        SessionHandle handle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);
        handle.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        SessionHandle fromParcel = SessionHandle.CREATOR.createFromParcel(parcel);
        assertEquals(handle, fromParcel);
    }
}
