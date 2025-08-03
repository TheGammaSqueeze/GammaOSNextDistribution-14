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

package android.accessibilityservice.cts;

import static com.google.common.truth.Truth.assertThat;

import android.accessibility.cts.common.InstrumentedAccessibilityService;
import android.app.UiAutomation;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;

import com.android.compatibility.common.util.TestUtils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A stub accessibility service for waiting on {@link AccessibilityEvent}s using an
 * {@link UiAutomation.AccessibilityEventFilter}.
 */
public class StubEventCapturingAccessibilityService extends InstrumentedAccessibilityService {
    private final AtomicBoolean mReceivedEvent = new AtomicBoolean();
    private final Object mWaitObject = new Object();
    private UiAutomation.AccessibilityEventFilter mEventFilter;

    @Override
    public void onAccessibilityEvent(@NonNull AccessibilityEvent event) {
        if (mEventFilter != null) {
            if (mEventFilter.accept(event)) {
                synchronized (mWaitObject) {
                    mReceivedEvent.set(true);
                    mWaitObject.notifyAll();
                }
            }
        }
    }

    /**
     * Waits on an {@link AccessibilityEvent} to be accepted by the event filter.
     *
     * Resets the filter & waiting state after a successful wait.
     *
     * @throws AssertionError on timeout.
     * @see #setEventFilter
     */
    public void waitOnEvent(long timeoutMs, String condition) {
        assertThat(mEventFilter).isNotNull();
        TestUtils.waitOn(mWaitObject, mReceivedEvent::get, timeoutMs, condition);
        mEventFilter = null;
        mReceivedEvent.set(false);
    }

    public void setEventFilter(@NonNull UiAutomation.AccessibilityEventFilter filter) {
        mEventFilter = filter;
    }
}
