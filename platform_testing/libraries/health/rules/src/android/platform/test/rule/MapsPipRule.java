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

package android.platform.test.rule;

import android.os.SystemClock;
import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IMapsHelper;

import androidx.annotation.VisibleForTesting;

import org.junit.runner.Description;

/** This rule allows to execute CUJ while Maps in pip state. */
public class MapsPipRule extends TestWatcher {

    @VisibleForTesting static final String MAPS_SEARCH_ADDRESS = "maps-close-to-pip-address";
    String mapsAddressOption = "Golden Gate Bridge";

    @VisibleForTesting static final String MAPS_TIMEOUT = "maps-timeout";
    long mapsTimeout = 2000;

    private static HelperAccessor<IMapsHelper> sMapsHelper =
            new HelperAccessor<>(IMapsHelper.class);

    @Override
    protected void starting(Description description) {
        mapsAddressOption = getArguments().getString(MAPS_SEARCH_ADDRESS, "Golden Gate Bridge");
        mapsTimeout = Long.valueOf(getArguments().getString(MAPS_TIMEOUT, "2000"));

        sMapsHelper.get().open();
        sMapsHelper.get().doSearch(mapsAddressOption);
        sMapsHelper.get().getDirections();
        sMapsHelper.get().startNavigation();
        sMapsHelper.get().goToNavigatePip();
        SystemClock.sleep(mapsTimeout);
    }

    @Override
    protected void finished(Description description) {
        executeShellCommand(String.format("am force-stop %s", sMapsHelper.get().getPackage()));
    }
}
