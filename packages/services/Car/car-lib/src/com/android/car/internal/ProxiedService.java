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

package com.android.car.internal;

import android.app.Service;
import android.content.Context;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Helper class for {@code Service} that is proxied from car service to car service updated.
 *
 * <p> This is used as an interface between builtin and updatable car service. Do not change it
 * without compatibility check. Adding a new method is ok but should have no-op default
 * implementation.
 *
 * @hide
 */
public abstract class ProxiedService extends Service {

    private Context mCarServiceBuiltinPackageContext;

    /** Check {@link Service#attachBaseContext(Context)}. */
    public void  doAttachBaseContext(Context newBase) {
        attachBaseContext(newBase);
    }

    /** Used by builtin CarService to set builtin {@code Context}. */
    public void setBuiltinPackageContext(Context context) {
        mCarServiceBuiltinPackageContext = context;
    }

    /** Returns the {@code Context} of builtin car service */
    public Context getBuiltinPackageContext() {
        return mCarServiceBuiltinPackageContext;
    }

    /** Check {@link Service#dump(FileDescriptor, PrintWriter, String[])}. */
    public void doDump(FileDescriptor fd, PrintWriter writer, String[] args) {
        dump(fd, writer, args);
    }
}
