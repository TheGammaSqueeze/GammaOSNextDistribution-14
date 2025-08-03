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
package android.smartspace.cts;

import static androidx.test.InstrumentationRegistry.getContext;

import static com.google.common.truth.Truth.assertThat;

import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.app.smartspace.uitemplatedata.TapAction;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Process;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link TapAction}
 *
 * atest CtsSmartspaceServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class TapActionTest {

    private static final String TAG = "TapActionTest";

    @Test
    public void testCreateTapAction() {
        Bundle extras = new Bundle();
        extras.putString("key", "value");

        Intent intent = new Intent();
        PendingIntent pendingIntent = TaskStackBuilder.create(getContext())
                .addNextIntent(intent)
                .getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE);

        TapAction tapAction = new TapAction.Builder("id")
                .setIntent(intent)
                .setPendingIntent(pendingIntent)
                .setUserHandle(Process.myUserHandle())
                .setExtras(extras)
                .setShouldShowOnLockscreen(true)
                .build();

        assertThat(tapAction.getId()).isEqualTo("id");
        assertThat(tapAction.getIntent()).isEqualTo(intent);
        assertThat(tapAction.getPendingIntent()).isEqualTo(pendingIntent);
        assertThat(tapAction.getUserHandle()).isEqualTo(Process.myUserHandle());
        assertThat(tapAction.getExtras()).isEqualTo(extras);
        assertThat(tapAction.shouldShowOnLockscreen()).isEqualTo(true);

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        tapAction.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        TapAction copyTapAction = TapAction.CREATOR.createFromParcel(parcel);
        assertThat(tapAction).isEqualTo(copyTapAction);
        parcel.recycle();
    }
}
