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
import android.app.smartspace.SmartspaceConfig;
import android.app.smartspace.SmartspaceTarget;
import android.content.ComponentName;
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
 * Tests for {@link SmartspaceAction}
 *
 * atest CtsSmartspaceServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class SmartspaceActionTest {

    private static final String TAG = "SmartspaceTargetTest";

    @Test
    public void testCreateSmartspaceAction() {
        Bundle extras = new Bundle();
        extras.putString("key", "value");

        Intent intent = new Intent();
        PendingIntent dummyPendingIntent = TaskStackBuilder.create(getContext())
                .addNextIntent(intent)
                .getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE);
        Icon icon  = Icon.createWithBitmap(
                Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8));
        SmartspaceAction action = new SmartspaceAction.Builder("id", "title")
                .setIcon(icon)
                .setContentDescription("description")
                .setExtras(extras)
                .setPendingIntent(dummyPendingIntent)
                .setIntent(intent)
                .setUserHandle(Process.myUserHandle())
                .setSubtitle("subtitle")
                .build();


        assertThat(action.getIcon()).isEqualTo(icon);
        assertThat(action.getContentDescription()).isEqualTo("description");
        assertThat(action.getExtras()).isEqualTo(extras);
        assertThat(action.getPendingIntent()).isEqualTo(dummyPendingIntent);
        assertThat(action.getIntent()).isEqualTo(intent);
        assertThat(action.getUserHandle()).isEqualTo(Process.myUserHandle());
        assertThat(action.getSubtitle()).isEqualTo("subtitle");

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        action.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        SmartspaceAction copy = SmartspaceAction.CREATOR.createFromParcel(parcel);
        assertThat(action.getSubtitle()).isEqualTo(copy.getSubtitle());
        parcel.recycle();
    }

}
