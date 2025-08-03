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

package com.android.bedstead.testapp;

import android.app.Activity;
import android.os.UserHandle;

import com.android.bedstead.nene.activities.NeneActivity;
import com.android.bedstead.nene.packages.ComponentReference;

/**
 * A reference to an instance of an activity in a test app.
 *
 * <p>Many of the APIs exposed by this class perform a remote call and will make the call on the
 * actual {@link Activity} instance inside the test app.
 */
public abstract class TestAppActivity extends TestAppActivityReference implements
        NeneActivity {
    TestAppActivity(TestAppInstance instance, ComponentReference component) {
        super(instance, component);
    }

    @Override
    public UserHandle user() {
        return testAppInstance().user().userHandle();
    }
}
