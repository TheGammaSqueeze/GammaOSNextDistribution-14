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

package android.platform.helpers.rules;

import static android.app.NotificationManager.IMPORTANCE_LOW;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import androidx.test.InstrumentationRegistry;

import org.junit.rules.ExternalResource;

/**
 * The TemporaryNotificationChannel Rule allows creation of a notification channel that will be
 * deleted when the test finishes.
 */
public class TemporaryNotificationChannel extends ExternalResource {

    private static final String DEFAULT_ID = "temporary_channel_id";
    private static final String DEFAULT_NAME = "Temporary Channel";
    private static final int DEFAULT_IMPORTANCE = IMPORTANCE_LOW;

    private final String mId;
    private final String mName;
    private final int mImportance;

    public TemporaryNotificationChannel() {
        this(DEFAULT_ID, DEFAULT_NAME, DEFAULT_IMPORTANCE);
    }

    public TemporaryNotificationChannel(String id, String name, int importance) {
        mId = id;
        mName = name;
        mImportance = importance;
    }

    public String getId() {
        return mId;
    }

    @Override
    protected void before() {
        NotificationChannel channel = new NotificationChannel(mId, mName, mImportance);
        getNotificationManager().createNotificationChannel(channel);
    }

    @Override
    protected void after() {
        getNotificationManager().deleteNotificationChannel(mId);
    }

    private NotificationManager getNotificationManager() {
        return InstrumentationRegistry
                .getTargetContext()
                .getSystemService(NotificationManager.class);
    }
}
