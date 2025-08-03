/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.media.player.cts;

import android.content.Context;
import android.media.AsyncPlayer;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.Settings;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@NonMainlineTest
@RunWith(AndroidJUnit4.class)
public class AsyncPlayerTest {

    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    @After
    public void tearDown() {
        mContext = null;
    }

    @Test
    public void testAsyncPlayer() throws Exception {
        final Uri PLAY_URI = Settings.System.DEFAULT_NOTIFICATION_URI;
        AsyncPlayer asyncPlayer = new AsyncPlayer(null);
        asyncPlayer.play(mContext, PLAY_URI, true, AudioManager.STREAM_RING);
        final int PLAY_TIME = 3000;
        Thread.sleep(PLAY_TIME);
        asyncPlayer.stop();
    }

    @Test
    public void testAsyncPlayerAudioAttributes() throws Exception {
        final Uri PLAY_URI = Settings.System.DEFAULT_NOTIFICATION_URI;
        AsyncPlayer asyncPlayer = new AsyncPlayer(null);
        asyncPlayer.play(mContext, PLAY_URI, true,
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
        final int PLAY_TIME = 3000;
        Thread.sleep(PLAY_TIME);
        asyncPlayer.stop();
    }
}
