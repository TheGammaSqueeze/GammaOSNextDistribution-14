/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.bugreport;

import static com.android.car.bugreport.BugStorageProvider.COLUMN_ID;
import static com.android.car.bugreport.BugStorageProvider.COLUMN_TTL_POINTS;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.util.Log;

import java.util.List;

/**
 * Decrements TTL (time-to-live) points of bugreports with zip files by 1 every boot. When TTL
 * points reach zero, {@link ExpireOldBugReportsJob} will expire them and remove zip files.
 *
 * <p>It's called once by {@link StartUpBootReceiver} after every boot completed. It is not
 * triggered when the device goes to the sleep and wakes up.
 *
 * <p>Default ttl points value for new bugreports is {@code BugStorageProvider#DEFAULT_TTL_POINTS}.
 */
public class TtlPointsDecremental implements Runnable {
    private static final String TAG = TtlPointsDecremental.class.getSimpleName();

    private final Context mContext;
    private final Runnable mOnFinishCallback;

    TtlPointsDecremental(Context context, Runnable onFinishCallback) {
        mContext = context;
        mOnFinishCallback = onFinishCallback;
    }

    @Override
    public void run() {
        Log.d(TAG, "Running TtlPointsDecremental");
        List<MetaBugReport> unexpiredBugReports =
                BugStorageUtils.getUnexpiredBugReportsWithZipFile(
                        mContext, /* ttlPointsReachedZero= */ false);
        ContentResolver resolver = mContext.getContentResolver();
        for (MetaBugReport bugReport : unexpiredBugReports) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_TTL_POINTS, bugReport.getTtlPoints() - 1);
            String where = COLUMN_ID + " = " + bugReport.getId();
            resolver.update(BugStorageProvider.BUGREPORT_CONTENT_URI, values, where, null);
        }
        Log.d(TAG, "Updated " + unexpiredBugReports.size() + " bugreports");
        mOnFinishCallback.run();
    }
}
