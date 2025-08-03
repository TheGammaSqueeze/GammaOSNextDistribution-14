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

package com.google.android.utils.chre;

import static com.google.common.truth.Truth.assertWithMessage;

import android.hardware.location.NanoAppMessage;

import java.util.List;

/** A helper class to handle incoming messages by comparing against a set of expected messages. */
public class ContextHubClientMessageValidator {
    /** The list of expected messages to received by onMessageReceipt. */
    private final List<NanoAppMessage> mExpectedMessages;

    /** The index of the next expected message. */
    private int mExpectedMessageIndex = 0;

    /** The message to invoke when all messages have been received. */
    private final Runnable mOnComplete;

    public ContextHubClientMessageValidator(
            List<NanoAppMessage> expectedMessages, Runnable onComplete) {
        mExpectedMessages = expectedMessages;
        mOnComplete = onComplete;
    }

    /**
     * asserts that the message received is expected.
     *
     * <p>{@link #mOnComplete} is only called when all the expected messages are received.
     */
    public synchronized void assertMessageReceivedIsExpected(NanoAppMessage message) {
        assertWithMessage("Received more than expected messages from nanoapp")
                .that(mExpectedMessageIndex)
                .isLessThan(mExpectedMessages.size());

        NanoAppMessage expectedMessage = mExpectedMessages.get(mExpectedMessageIndex);
        assertWithMessage("Received unexpected message contents from nanoapp")
                .that(message.getMessageBody())
                .isEqualTo(expectedMessage.getMessageBody());

        mExpectedMessageIndex++;
        if (mExpectedMessageIndex == mExpectedMessages.size()) {
            mOnComplete.run();
        }
    }
}
