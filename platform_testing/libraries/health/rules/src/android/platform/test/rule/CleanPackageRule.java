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
package android.platform.test.rule;

import android.system.helpers.PackageHelper;

import androidx.test.InstrumentationRegistry;

import org.junit.runner.Description;
import org.junit.runners.model.InitializationError;

/** This rule will clear the package data both on statup and end of the test. */
public class CleanPackageRule extends TestWatcher {
    private final String mPackage;
    private final boolean mClearOnStarting;
    private final boolean mClearOnFinished;

    public CleanPackageRule() throws InitializationError {
        throw new InitializationError("Must supply a package to clear.");
    }

    public CleanPackageRule(String pkg) {
        this(pkg, true, true);
    }

    public CleanPackageRule(String pkg, boolean clearOnStarting, boolean ClearOnFinished) {
        mPackage = pkg;
        mClearOnStarting = clearOnStarting;
        mClearOnFinished = ClearOnFinished;
    }

    @Override
    protected void starting(Description description) {
        if (mClearOnStarting) {
            cleanPackage();
        }
    }

    @Override
    protected void finished(Description description) {
        if (mClearOnFinished) {
            cleanPackage();
        }
    }

    public void cleanPackage() {
        PackageHelper.getInstance(InstrumentationRegistry.getInstrumentation())
                .cleanPackage(mPackage);
    }
}
