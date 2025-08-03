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

package android.assist.cts;

import static com.google.common.truth.Truth.assertThat;

import android.app.assist.ActivityId;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link android.app.assist.ActivityId}.
 *
 * <p>To run: {@code atest CtsAssistTestCases:ActivityIdTest}
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ActivityIdTest {

    @Test
    public void testParcel() throws Exception {
        int taskId = 6;
        IBinder activityId = new Binder();
        ActivityId original = new ActivityId(taskId, activityId);

        ActivityId fromParcel = parcelAndUnparcel(original);
        assertThat(fromParcel.getTaskId()).isEqualTo(taskId);
        assertThat(fromParcel.getToken()).isSameInstanceAs(activityId);
    }

    private static ActivityId parcelAndUnparcel(ActivityId activityId) {
        Parcel parcel = Parcel.obtain();
        activityId.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        return ActivityId.CREATOR.createFromParcel(parcel);
    }
}
