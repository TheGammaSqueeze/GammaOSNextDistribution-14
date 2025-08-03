/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.voiceinteraction.cts.unittests;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.service.voice.VisibleActivityInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class VisibleActivityInfoTest {

    private final int mTaskId = 100;
    private final IBinder mAssistToken = new Binder();

    @Test
    public void testVisibleActivityInfoData() throws Exception {
        final VisibleActivityInfo visibleActivityInfo = new VisibleActivityInfo(mTaskId,
                mAssistToken);

        assertThat(visibleActivityInfo.getActivityId()).isNotNull();
    }

    @Test
    public void testVisibleActivityInfoParcelizeDeparcelize() throws Exception {
        final VisibleActivityInfo visibleActivityInfo = new VisibleActivityInfo(mTaskId,
                mAssistToken);

        final Parcel p = Parcel.obtain();
        visibleActivityInfo.writeToParcel(p, 0);
        p.setDataPosition(0);

        final VisibleActivityInfo targetVisibleActivityInfo =
                VisibleActivityInfo.CREATOR.createFromParcel(p);
        p.recycle();

        assertThat(visibleActivityInfo).isEqualTo(targetVisibleActivityInfo);
    }

    @Test
    public void testVisibleActivityInfo_nullAssistToken() {
        assertThrows(NullPointerException.class,
                () -> new VisibleActivityInfo(mTaskId, /* assistToken= */ null));
    }
}
