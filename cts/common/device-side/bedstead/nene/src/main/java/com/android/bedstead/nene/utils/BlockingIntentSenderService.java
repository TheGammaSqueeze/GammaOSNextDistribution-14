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

package com.android.bedstead.nene.utils;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import com.android.bedstead.nene.TestApis;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Service used by {@link BlockingIntentSender}.
 *
 * <p>See {@link BlockingIntentSender} for usage.
 */
public final class BlockingIntentSenderService extends IntentService {

    private static final String LOG_TAG = "BlockingIntentSenderService";

    private static final String BLOCKING_INTENT_SENDER_ID_KEY = "BlockingIntentSenderId";

    private static final long DEFAULT_TIMEOUT = 30;
    private static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.SECONDS;

    private static final Map<String, CountDownLatch> sLatches = new ConcurrentHashMap<>();
    private static final Map<String, Intent> sReceivedIntents = new ConcurrentHashMap<>();

    // TODO(b/273197248): This only allows us to have one intentsender at a time - this is because
    // for some reason it appeared to be losing the extra value on receiving the intent -
    // to reproduce just comment out the FIXED_ID stuff and run
    // com.android.bedstead.nene.packages.PackagesTest#install_instrumentedUser_isInstalled
    private static final String FIXED_ID = "FIXED";

    /** Only for use by {@link BlockingIntentSender}. */
    public BlockingIntentSenderService() {
        super("BlockingIntentSenderService");
    }

    /** Only for use by {@link BlockingIntentSender}. */
    public static Intent register() {
        String id = Long.toString(new Random().nextLong());
        id = FIXED_ID;

        Intent intent = new Intent();
        intent.setClass(
                TestApis.context().instrumentedContext(), BlockingIntentSenderService.class);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.putExtra(BLOCKING_INTENT_SENDER_ID_KEY, id);

        sLatches.put(id, new CountDownLatch(1));

        return intent;
    }

    private static String getId(Intent intent) {
//        return intent.getStringExtra(BLOCKING_INTENT_SENDER_ID_KEY);
        return FIXED_ID;
    }

    /** Only for use by {@link BlockingIntentSender}. */
    public static void unregister(Intent intent) {
        String id = getId(intent);
        sLatches.remove(id);
        sReceivedIntents.remove(id);
    }

    /** Only for use by {@link BlockingIntentSender}. */
    public static Intent await(Intent intent) {
        return await(intent, DEFAULT_TIMEOUT, DEFAULT_TIME_UNIT);
    }

    /** Only for use by {@link BlockingIntentSender}. */
    public static Intent await(Intent intent, long timeout, TimeUnit unit) {
        String id = getId(intent);

        CountDownLatch latch = sLatches.get(id);
        if (latch == null) {
            throw new IllegalStateException(
                    "Awaiting but no latch registered for intent " + intent);
        }

        try {
            latch.await(timeout, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return sReceivedIntents.get(id);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String id = getId(intent);

        CountDownLatch latch = sLatches.get(id);
        if (latch == null) {
            Log.e(LOG_TAG,
                    "Received start intent at BlockingIntentSenderService but no "
                            + "latch registered for id " + id + " intent " + intent);
            return;
        }

        sReceivedIntents.put(id, intent);
        latch.countDown();
    }
}
