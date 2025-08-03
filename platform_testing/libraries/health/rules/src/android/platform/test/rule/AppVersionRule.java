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

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

import android.platform.helpers.exceptions.TestHelperException;

import androidx.test.InstrumentationRegistry;

import junit.framework.Assert;

import org.junit.runner.Description;
import org.junit.runners.model.InitializationError;

/** This rule will validate a package version before starting the test. */
public class AppVersionRule extends TestWatcher {
    private final String mPackage;
    private final String mVersion;

    public AppVersionRule() throws InitializationError {
        throw new InitializationError("Must supply a package and version to check.");
    }

    public AppVersionRule(String pkg, String version) {
        mPackage = pkg;
        mVersion = version;
    }

    @Override
    protected void starting(Description description) {
        try {
            Assert.assertEquals(
                    "Test cannot run on this version of the app", mVersion, getVersion());
        } catch (NameNotFoundException nne) {
            Assert.fail(String.format("Package %s not found", mPackage));
        }
    }

    public String getVersion() throws NameNotFoundException {
        PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        PackageInfo pInfo = pm.getPackageInfo(mPackage, 0);
        String version = pInfo.versionName;
        if (null == version || version.isEmpty()) {
            throw new TestHelperException(
                    String.format("Version isn't found for package, %s", mPackage));
        }
        return version;
    }
}
