/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.app.appsearch.safeparcel;

import static com.google.common.truth.Truth.assertThat;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Parcel;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for reading and writing PendingIntents to Parcel. */
@RunWith(AndroidJUnit4.class)
public final class SafeParcelablePendingIntentTest {
    private static final String CALLBACK_INTENT_ACTION =
            SafeParcelablePendingIntentTest.class.getName() + "_INTENT";

    private Parcel parcel;

    @Before
    public void setup() {
        parcel = Parcel.obtain();
    }

    @After
    public void tearDown() {
        parcel.recycle();
    }

    @Test
    public void testWriteRead_success() {
        Intent intent = new Intent(CALLBACK_INTENT_ACTION);

        Context context = ApplicationProvider.getApplicationContext();
        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        context,
                        1234,
                        intent,
                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        // Writing pending intent
        parcel.setDataPosition(0);
        SafeParcelWriter.writePendingIntent(parcel, 1, pendingIntent, false);

        // Reading pending intent
        parcel.setDataPosition(0);

        int header = SafeParcelReader.readHeader(parcel);
        PendingIntent resultIntent = SafeParcelReader.readPendingIntent(parcel, header);
        assertThat(resultIntent).isEqualTo(pendingIntent);
    }

    @Test
    public void testWriteReadNull_success() {

        // Writing pending intent
        parcel.setDataPosition(0);
        int pos = 0;
        SafeParcelWriter.writePendingIntent(parcel, 1, null, false);

        assertThat(parcel.dataPosition()).isEqualTo(pos);

        // Reading pending intent
        parcel.setDataPosition(0);
        SafeParcelWriter.writePendingIntent(parcel, 1, null, true);
        assertThat(parcel.dataPosition()).isNotEqualTo(pos);

        parcel.setDataPosition(0);
        int header = SafeParcelReader.readHeader(parcel);
        PendingIntent resultIntent = SafeParcelReader.readPendingIntent(parcel, header);

        assertThat(resultIntent).isNull();
    }
}
