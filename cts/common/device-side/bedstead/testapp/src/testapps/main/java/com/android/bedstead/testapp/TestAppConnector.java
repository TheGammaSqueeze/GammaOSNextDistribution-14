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

package com.android.bedstead.testapp;

import android.content.Context;

import com.google.android.enterprise.connectedapps.ConnectionBinder;
import com.google.android.enterprise.connectedapps.ProfileConnector;
import com.google.android.enterprise.connectedapps.annotations.CustomProfileConnector;
import com.google.android.enterprise.connectedapps.annotations.GeneratedProfileConnector;
import com.google.android.enterprise.connectedapps.annotations.UncaughtExceptionsPolicy;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** Connector for use for communication with Test Apps. */
@GeneratedProfileConnector
@CustomProfileConnector(uncaughtExceptionsPolicy = UncaughtExceptionsPolicy.NOTIFY_SUPPRESS)
public interface TestAppConnector extends ProfileConnector {

    ScheduledExecutorService EXECUTOR_SERVICE = Executors.newScheduledThreadPool(5);

    /** Create {@link TestAppConnector}. */
    static TestAppConnector create(Context context, ConnectionBinder binder) {
        return GeneratedTestAppConnector.builder(context)
                .setBinder(binder)
                .setScheduledExecutorService(EXECUTOR_SERVICE)
                .build();
    }
}
