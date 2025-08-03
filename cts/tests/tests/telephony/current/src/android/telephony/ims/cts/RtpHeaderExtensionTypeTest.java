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

package android.telephony.ims.cts;

import static junit.framework.Assert.assertEquals;

import android.net.Uri;
import android.os.Parcel;
import android.telephony.ims.RtpHeaderExtensionType;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RtpHeaderExtensionTypeTest {
    private static final int TEST_ID = 1;
    private static final Uri TEST_URI = Uri.parse("http://developer.android.com/092020/test1");

    @Test
    public void testParceling() {
        RtpHeaderExtensionType test = new RtpHeaderExtensionType(TEST_ID, TEST_URI);
        assertEquals(TEST_ID,  test.getLocalIdentifier());
        assertEquals(TEST_URI, test.getUri());

        Parcel dataParceled = Parcel.obtain();
        test.writeToParcel(dataParceled, 0);
        dataParceled.setDataPosition(0);
        RtpHeaderExtensionType unparceledData =
                RtpHeaderExtensionType.CREATOR.createFromParcel(dataParceled);
        dataParceled.recycle();

        assertEquals(test, unparceledData);
    }
}
