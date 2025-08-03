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

package android.input.cts

import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.fail

class EventVerifier(val getInputEvent: () -> InputEvent?) {
    fun assertReceivedMotion(matcher: Matcher<MotionEvent>) {
        val event = getMotionEvent()
        assertThat("Additional MotionEvent checks", event, matcher)
    }

    private fun getMotionEvent(): MotionEvent {
        val event = getInputEvent() ?: fail("Failed to receive input event")
        val motionEvent =
            event as? MotionEvent ?: fail("Instead of MotionEvent, got: $event")
        return motionEvent as MotionEvent
    }

    fun assertReceivedKey(matcher: Matcher<KeyEvent>) {
        val event = getKeyEvent()
        assertThat("Additional KeyEvent checks", event, matcher)
    }

    private fun getKeyEvent(): KeyEvent {
        val event = getInputEvent() ?: fail("Failed to receive input event")
        val keyEvent =
            event as? KeyEvent ?: fail("Instead of KeyEvent, got: $event")
        return keyEvent as KeyEvent
    }
}
