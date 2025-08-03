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

package android.app.cts.pendingintentapi33;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.test.AndroidTestCase;

import androidx.test.platform.app.InstrumentationRegistry;

/**
 * Context: Starting with {@link android.os.Build.VERSION_CODES#UPSIDE_DOWN_CAKE}, for apps that
 * target SDK {@link android.os.Build.VERSION_CODES#UPSIDE_DOWN_CAKE} or higher, creation of a
 * PendingIntent with {@link PendingIntent#FLAG_MUTABLE} and an implicit Intent within will throw an
 * {@link IllegalArgumentException} for security reasons.
 * <p>
 * This test tests that apps that target lower than
 * {@link android.os.Build.VERSION_CODES#UPSIDE_DOWN_CAKE} are still able to create any permutation
 * of mutable/immutable PendingIntents and implicit/explicit Intents within.
 */
public class PendingIntentApi33Test extends AndroidTestCase {

    private Context mContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getContext();
    }

    public void testGetActivity() {
        Intent intent = new Intent(mContext, PendingIntentApi33Test.class);

        // creating a mutable explicit PendingIntent works fine
        PendingIntent.getActivity(mContext, 1, intent, PendingIntent.FLAG_MUTABLE);

        // make intent implicit
        intent.setComponent(null);
        intent.setPackage(null);

        // creating an immutable implicit PendingIntent works fine
        PendingIntent.getActivity(mContext, 1, intent, PendingIntent.FLAG_IMMUTABLE);

        // retrieving a mutable implicit PendingIntent with NO_CREATE works fine
        PendingIntent.getActivity(mContext, 1, intent, PendingIntent.FLAG_MUTABLE
                | PendingIntent.FLAG_NO_CREATE);

        // creating a mutable implicit PendingIntent with ALLOW_UNSAFE_IMPLICIT_INTENT works fine
        PendingIntent.getActivity(mContext, 1, intent, PendingIntent.FLAG_MUTABLE
                | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);

        // creating a mutable implicit PendingIntent works fine
        PendingIntent.getActivity(mContext, 1, intent, PendingIntent.FLAG_MUTABLE);

        // No exception thrown means app successfully retrieved the pending intent
    }

    public void testGetActivities() {
        Intent[] intents = new Intent[]{
                new Intent(mContext, PendingIntentApi33Test.class),
                new Intent(mContext, PendingIntentApi33Test.class)
        };

        // creating a mutable explicit PendingIntent works fine
        PendingIntent.getActivities(mContext, 1, intents,
                PendingIntent.FLAG_MUTABLE);

        // make intents implicit
        for (int i = 0; i < intents.length; i++) {
            intents[i].setComponent(null);
            intents[i].setPackage(null);
        }

        // creating an immutable implicit PendingIntent works fine
        PendingIntent.getActivities(mContext, 1, intents, PendingIntent.FLAG_IMMUTABLE);

        // retrieving a mutable implicit PendingIntent with NO_CREATE works fine
        PendingIntent.getActivities(mContext, 1, intents, PendingIntent.FLAG_MUTABLE
                | PendingIntent.FLAG_NO_CREATE);

        // creating a mutable implicit PendingIntent with ALLOW_UNSAFE_IMPLICIT_INTENT works fine
        PendingIntent.getActivities(mContext, 1, intents, PendingIntent.FLAG_MUTABLE
                | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);

        // creating a mutable implicit PendingIntent works fine
        PendingIntent.getActivities(mContext, 1, intents, PendingIntent.FLAG_MUTABLE);

        // No exception thrown means app successfully retrieved the pending intent
    }

    public void testGetBroadcast() {
        Intent intent = new Intent(mContext, PendingIntentApi33Test.class);

        // creating a mutable explicit PendingIntent works fine
        PendingIntent.getBroadcast(mContext, 1, intent, PendingIntent.FLAG_MUTABLE);

        // make intent implicit
        intent.setComponent(null);
        intent.setPackage(null);

        // creating an immutable implicit PendingIntent works fine
        PendingIntent.getBroadcast(mContext, 1, intent, PendingIntent.FLAG_IMMUTABLE);

        // retrieving a mutable implicit PendingIntent with NO_CREATE works fine
        PendingIntent.getBroadcast(mContext, 1, intent, PendingIntent.FLAG_MUTABLE
                | PendingIntent.FLAG_NO_CREATE);

        // creating a mutable implicit PendingIntent with ALLOW_UNSAFE_IMPLICIT_INTENT works fine
        PendingIntent.getBroadcast(mContext, 1, intent, PendingIntent.FLAG_MUTABLE
                | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);

        // creating a mutable implicit PendingIntent works fine
        PendingIntent.getBroadcast(mContext, 1, intent, PendingIntent.FLAG_MUTABLE);

        // No exception thrown means app successfully retrieved the pending intent
    }

    public void testGetService() {
        Intent intent = new Intent(mContext, PendingIntentApi33Test.class);

        // creating a mutable explicit PendingIntent works fine
        PendingIntent.getService(mContext, 1, intent, PendingIntent.FLAG_MUTABLE);

        // make intent implicit
        intent.setComponent(null);
        intent.setPackage(null);

        // creating an immutable implicit PendingIntent works fine
        PendingIntent.getService(mContext, 1, intent, PendingIntent.FLAG_IMMUTABLE);

        // retrieving a mutable implicit PendingIntent with NO_CREATE works fine
        PendingIntent.getService(mContext, 1, intent, PendingIntent.FLAG_MUTABLE
                | PendingIntent.FLAG_NO_CREATE);

        // creating a mutable implicit PendingIntent with ALLOW_UNSAFE_IMPLICIT_INTENT works fine
        PendingIntent.getService(mContext, 1, intent, PendingIntent.FLAG_MUTABLE
                | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);

        // creating a mutable implicit PendingIntent works fine
        PendingIntent.getService(mContext, 1, intent, PendingIntent.FLAG_MUTABLE);

        // No exception thrown means app successfully retrieved the pending intent
    }

    public void testCreatePendingResult() {
        Intent intent = new Intent(mContext, MockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        intent = new Intent(mContext, PendingIntentApi33Test.class);

        // creating a mutable explicit PendingResult works fine
        activity.createPendingResult(1, intent, PendingIntent.FLAG_MUTABLE);

        // make intent implicit
        intent.setComponent(null);
        intent.setPackage(null);

        // creating an immutable implicit PendingResult works fine
        activity.createPendingResult(1, intent, PendingIntent.FLAG_IMMUTABLE);

        // retrieving a mutable implicit PendingResult with NO_CREATE works fine
        activity.createPendingResult(1, intent, PendingIntent.FLAG_MUTABLE
                | PendingIntent.FLAG_NO_CREATE);

        // creating a mutable implicit PendingResult with ALLOW_UNSAFE_IMPLICIT_INTENT works fine
        activity.createPendingResult(1, intent, PendingIntent.FLAG_MUTABLE
                | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);

        // creating a mutable implicit PendingResult works fine
        activity.createPendingResult(1, intent, PendingIntent.FLAG_MUTABLE);

        // No exception thrown means app successfully retrieved the pending intent
    }
}
