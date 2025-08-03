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

package android.device.collectors;

import android.device.collectors.annotations.OptionClass;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.VisibleForTesting;

import com.android.helpers.AppVersionHelper;

/**
 * A {@link AppVersionListener} that captures and records the versions of the app packages
 * on the device.
 * <p>Options:
 * <ul>
 *   <li>-e pkg-names [pkgNames] : the pkg names we want to get versions for.
 * </ul>
 */
@OptionClass(alias = "app-version-listener")
public class AppVersionListener extends BaseCollectionListener<Long> {

    private static final String TAG = AppVersionHelper.class.getSimpleName();
    private AppVersionHelper mAppVersionHelper = new AppVersionHelper();
    @VisibleForTesting static final String PKG_NAME_SEPARATOR = ",";
    @VisibleForTesting static final String PKG_NAMES_ARG = "app-version-pkg-names";

    public AppVersionListener() {
        createHelperInstance(mAppVersionHelper);
    }

    /**
     * Constructor to simulate receiving the instrumentation arguments. Should not be used except
     * for testing.
     */
    @VisibleForTesting
    public AppVersionListener(Bundle args, AppVersionHelper helper) {
        super(args, helper);
        mAppVersionHelper = helper;
        createHelperInstance(mAppVersionHelper);
    }

    /**
     * Adds the options for app version collector.
     */
    @Override
    public void setupAdditionalArgs() {
        Bundle args = getArgsBundle();
        String pkgNamesString = args.getString(PKG_NAMES_ARG);
        if (pkgNamesString == null) {
            Log.w(TAG, "No package name provided. All packages will be collected");
            mAppVersionHelper.setUp();
        } else {
            mAppVersionHelper.setUp(pkgNamesString.split(PKG_NAME_SEPARATOR));
        }
    }
}
