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

package com.android.test.notificationapp;

import android.app.Activity;
import android.app.NotificationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;

/**
 * An activity that's only meant to determine whether NotificationManager#matchesCallFilter
 * was allowed to run, for permission-checking reasons.
 * It is not used for functionality tests, as it will only call matchesCallFilter on a
 * meaningless uri.
 */
public class MatchesCallFilterTestActivity extends Activity {
    private NotificationManager mNotificationManager;
    private Uri mCallDest;

    // result codes for return
    private static int sNotPermitted = 0;
    private static int sPermitted = 1;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mNotificationManager = getSystemService(NotificationManager.class);
        mCallDest = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode("+16175551212"));
    }

    @Override
    public void onResume() {
        super.onResume();
        callMatchesCallFilter();
        finish();
    }

    private void callMatchesCallFilter() {
        try {
            mNotificationManager.matchesCallFilter(mCallDest);
            setResult(sPermitted);
        } catch (SecurityException e) {
            setResult(sNotPermitted);
        }
    }
}
