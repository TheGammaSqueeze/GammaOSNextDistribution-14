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

package android.media.session.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaSession;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class MediaSessionTest {

    private Context mContext;
    private static final String FAKE_RECEIVER_CLASS = "FakeBroadcastReceiver";

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    @Test
    public void setMediaButtonBroadcastReceiver_withFakeReceiver_changeEnabled_throwsIAE() {
        MediaSession session = new MediaSession(mContext, "TAG");
        ComponentName fakeReceiver =
                new ComponentName(mContext.getPackageName(), FAKE_RECEIVER_CLASS);
        assertThrows(
                "Non-existent component name was allowed. This should throw.",
                IllegalArgumentException.class,
                () -> session.setMediaButtonBroadcastReceiver(fakeReceiver));
    }

    @Test
    public void setMediaButtonBroadcastReceiver_withFakeReceiver_changeDisabled_isIgnored() {
        MediaSession session = new MediaSession(mContext, "TAG");
        ComponentName fakeReceiver =
                new ComponentName(mContext.getPackageName(), FAKE_RECEIVER_CLASS);
        session.setMediaButtonBroadcastReceiver(fakeReceiver);
    }

    @Test
    public void setMediaButtonReceiver_withActivity_changeEnabled_throwsIAE() {
        MediaSession session = new MediaSession(mContext, "TAG");
        try {
            Intent intent = new Intent(mContext, Activity.class);
            PendingIntent pi = PendingIntent.getActivity(mContext, /* requestCode */ 0,
                    intent, PendingIntent.FLAG_IMMUTABLE);
            assertThat(pi.isActivity()).isTrue();
            assertThrows(
                    "PendingIntent targeting activity was allowed. This should throw.",
                    IllegalArgumentException.class,
                    () -> session.setMediaButtonReceiver(pi));
        } finally {
            session.release();
        }
    }

    @Test
    public void setMediaButtonReceiver_withActivity_changeDisabled_isIgnored() {
        MediaSession session = new MediaSession(mContext, "TAG");
        try {
            Intent intent = new Intent(mContext, Activity.class);
            PendingIntent pi = PendingIntent.getActivity(mContext, /* requestCode */ 0,
                    intent, PendingIntent.FLAG_IMMUTABLE);
            assertThat(pi.isActivity()).isTrue();
            session.setMediaButtonReceiver(pi);
        } finally {
            session.release();
        }
    }
}
