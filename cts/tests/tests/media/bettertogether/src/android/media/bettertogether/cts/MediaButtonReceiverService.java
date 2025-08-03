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

package android.media.bettertogether.cts;

import static org.junit.Assert.assertEquals;

import android.app.IntentService;
import android.content.Intent;
import android.view.KeyEvent;

import java.util.function.Consumer;

public class MediaButtonReceiverService extends IntentService {
    private static final String TAG = "MediaButtonReceiverService";
    public static Consumer<KeyEvent> mCallback;

    public MediaButtonReceiverService() {
        super(TAG);
    }

    public static synchronized void setCallback(Consumer<KeyEvent> callback) {
        synchronized (MediaButtonReceiverService.class) {
            mCallback = callback;
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        assertEquals(Intent.ACTION_MEDIA_BUTTON, intent.getAction());
        KeyEvent keyEvent = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        synchronized (MediaButtonReceiverService.class) {
            if (mCallback != null) {
                mCallback.accept(keyEvent);
            }
        }
    }
}
