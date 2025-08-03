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

package com.android.bedstead.nene.context;

import android.content.pm.PackageManager;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.UserReference;

/** Test APIs related to Context. */
public final class Context {

    private static final String ANDROID_PACKAGE = "android";

    private static final android.content.Context sInstrumentedContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private static final android.content.Context sInstrumentationContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    public static final Context sInstance = new Context();

    private Context() {

    }

    /**
     * Get the {@link android.content.Context} for the instrumented test app.
     */
    public android.content.Context instrumentedContext() {
        return sInstrumentedContext;
    }

    /**
     * Get the {@link android.content.Context} for the instrumented test app in another user.
     */
    public android.content.Context instrumentedContextAsUser(UserReference user) {
        return sInstrumentedContext.createContextAsUser(user.userHandle(), /* flags= */ 0);
    }

    /**
     * Get the {@link android.content.Context} for the instrumentation app.
     */
    public android.content.Context instrumentationContext() {
        return sInstrumentationContext;
    }

    /**
     * Get the {@link android.content.Context} for the instrumentation app in another user.
     */
    public android.content.Context instrumentationContextAsUser(UserReference user) {
        return sInstrumentationContext.createContextAsUser(user.userHandle(), /* flags= */ 0);
    }

    /**
     * Get the {@link android.content.Context} for the "android" package in the given user.
     * Note: The packageName returned will match the test package rather than 'android'.
     * This is a property of [createPackageContextAsUser]
     */
    public android.content.Context androidContextAsUser(UserReference user) {
        try {
            return sInstrumentedContext.createPackageContextAsUser(
                    ANDROID_PACKAGE, /* flags= */ 0, user.userHandle());
        } catch (PackageManager.NameNotFoundException e) {
            throw new NeneException("Could not find android installed in user " + user, e);
        }
    }
}
