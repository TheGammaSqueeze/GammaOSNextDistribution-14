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

package com.android.bedstead.nene.utils;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;

import com.android.bedstead.nene.TestApis;

import java.util.concurrent.TimeUnit;

/**
 * Provider of a blocking version of {@link IntentSender}.
 *
 * <p>To use:
 * {@code
 *     try(BlockingIntentSender blockingIntentSender = BlockingIntentSender.create()) {
 *          IntentSender intentSender = blockingIntentSender.intentSender();
 *          // Use the intentSender for something
 *     }
 *     // we will block on the intent sender being used before exiting the try block, an exception
 *     // will be thrown if it is not used
 * }
 */
public class BlockingIntentSender implements AutoCloseable {

    private Intent mIntent;

    /** Create and register a {@link BlockingIntentSender}. */
    public static BlockingIntentSender create() {
        BlockingIntentSender blockingIntentSender = new BlockingIntentSender();
        blockingIntentSender.register();

        return blockingIntentSender;
    }

    private IntentSender mIntentSender;
    private BlockingIntentSender() {
    }

    private void register() {
        mIntent = BlockingIntentSenderService.register();

        PendingIntent pendingIntent = PendingIntent.getService(
                TestApis.context().instrumentedContext(),
                /* requestCode= */ 0, mIntent,
                PendingIntent.FLAG_MUTABLE);
        mIntentSender = pendingIntent.getIntentSender();
    }

    /** Wait for the {@link #intentSender()} to be used. */
    public Intent await() {
        return BlockingIntentSenderService.await(mIntent);
    }

    /** Wait for the {@link #intentSender()} to be used. */
    public Intent await(long timeoutMillis) {
        return BlockingIntentSenderService.await(mIntent, timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /** Get the intent sender. */
    public IntentSender intentSender() {
        return mIntentSender;
    }

    @Override
    public void close() {
        if (mIntent != null) {
            BlockingIntentSenderService.unregister(mIntent);
        }
    }
}
