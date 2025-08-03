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

package com.android.car.qc;

import android.R;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;

public abstract class QCItemTestCase<T extends QCItem> {
    protected static final String BUNDLE_KEY = "BUNDLE_KEY";
    protected static final String TEST_TITLE = "TEST TITLE";
    protected static final String TEST_SUBTITLE = "TEST SUBTITLE";

    protected final Context mContext = ApplicationProvider.getApplicationContext();

    protected PendingIntent mDefaultAction = PendingIntent.getActivity(mContext,
            /* requestCode= */ 0, new Intent(), PendingIntent.FLAG_IMMUTABLE);
    protected PendingIntent mDefaultDisabledAction = PendingIntent.getActivity(mContext,
            /* requestCode= */ 1, new Intent(), PendingIntent.FLAG_IMMUTABLE);
    protected Icon mDefaultIcon = Icon.createWithResource(mContext, R.drawable.btn_star);

    protected T writeAndLoadFromBundle(T item) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(BUNDLE_KEY, item);
        return bundle.getParcelable(BUNDLE_KEY);
    }
}
