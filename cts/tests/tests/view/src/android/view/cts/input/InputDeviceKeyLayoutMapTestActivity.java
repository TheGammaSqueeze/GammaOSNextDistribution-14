/*
 * Copyright 2020 The Android Open Source Project
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

package android.view.cts.input;

import static org.junit.Assert.fail;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class InputDeviceKeyLayoutMapTestActivity extends Activity {
    private static final String TAG = "InputDeviceKeyLayoutMapTestActivity";

    private final BlockingQueue<KeyEvent> mEvents = new LinkedBlockingQueue<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        try {
            mEvents.put(new KeyEvent(ev));
        } catch (InterruptedException ex) {
            fail("interrupted while adding a KeyEvent to the queue");
        }
        return true;
    }

    /**
     * Get a KeyEvent from event queue or timeout.
     * @param timeoutSeconds Timeout in unit of second
     * @return KeyEvent delivered to test activity, null if timeout.
     */
    public KeyEvent getKeyEvent(int timeoutSeconds) {
        try {
            return mEvents.poll(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("unexpectedly interrupted while waiting for InputEvent", e);
        }
    }

}
