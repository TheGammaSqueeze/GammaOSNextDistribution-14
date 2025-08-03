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
package android.smartspace.cts;

import static androidx.test.InstrumentationRegistry.getContext;

import static com.google.common.truth.Truth.assertThat;

import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.app.smartspace.SmartspaceAction;
import android.app.smartspace.SmartspaceSessionId;
import android.app.smartspace.SmartspaceTarget;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Process;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link SmartspaceSessionId}
 *
 * atest CtsSmartspaceServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class SmartspaceSessionIdTest {

    private static final String TAG = "SmartspaceTargetTest";

    @Test
    public void testCreateSmartspaceSessionId() {

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        parcel.writeString("id");
        parcel.writeTypedObject(Process.myUserHandle(), 0);
        parcel.setDataPosition(0);
        SmartspaceSessionId sessionId = SmartspaceSessionId.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(sessionId.getId()).isEqualTo("id");
        assertThat(sessionId.getUserHandle()).isEqualTo(Process.myUserHandle());

        parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        sessionId.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        SmartspaceSessionId copy = SmartspaceSessionId.CREATOR.createFromParcel(parcel);
        assertThat(sessionId).isEqualTo(copy);
        parcel.recycle();
    }
}
