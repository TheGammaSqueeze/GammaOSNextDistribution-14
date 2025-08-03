/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.app.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;

import android.app.ActivityOptions;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ActivityOptionsTest {

    @Test
    public void testActivityOptionsBundle_makeBasic() throws Throwable {
        ActivityOptions options = ActivityOptions.makeBasic();
        Bundle bundle = options.toBundle();

        assertNotNull(bundle);
    }

    @Test
    public void testGetPendingIntentBackgroundActivityLaunchAllowedDefault() {
        ActivityOptions options = ActivityOptions.makeBasic();

        // backwards compatibility
        checkPendingIntentBackgroundActivityStartModeBeforeAndAfterBundle(options, true,
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_SYSTEM_DEFINED);
    }

    @Test
    public void testGetSetPendingIntentBackgroundActivityLaunchAllowedTrue() {
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setPendingIntentBackgroundActivityLaunchAllowed(true);

        checkPendingIntentBackgroundActivityStartModeBeforeAndAfterBundle(options, true,
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
    }

    @Test
    public void testGetSetPendingIntentBackgroundActivityLaunchAllowedFalse() {
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setPendingIntentBackgroundActivityLaunchAllowed(false);

        checkPendingIntentBackgroundActivityStartModeBeforeAndAfterBundle(options, false,
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_DENIED);
    }

    @Test
    public void testGetSetPendingIntentBackgroundActivityStartModeAllowed() {
        ActivityOptions options = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        checkPendingIntentBackgroundActivityStartModeBeforeAndAfterBundle(options, true,

                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
    }

    @Test
    public void testGetSetPendingIntentBackgroundActivityStartModeDenied() {
        ActivityOptions options = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_DENIED);

        checkPendingIntentBackgroundActivityStartModeBeforeAndAfterBundle(options, false,
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_DENIED);
    }

    private void checkPendingIntentBackgroundActivityStartModeBeforeAndAfterBundle(
            ActivityOptions options, boolean allowed, int mode) {
        assertThat(options.isPendingIntentBackgroundActivityLaunchAllowed()).isEqualTo(allowed);
        assertThat(options.getPendingIntentBackgroundActivityStartMode()).isEqualTo(mode);

        Bundle bundle = options.toBundle();

        String key = "android.pendingIntent.backgroundActivityAllowed";
        if (mode == ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_SYSTEM_DEFINED) {
            assertThat(bundle.containsKey(key)).isFalse();
        } else {
            assertThat(bundle.containsKey(key)).isTrue();
            assertThat(bundle.getBoolean(key)).isEqualTo(allowed);
        }
    }

    @Test
    public void testGetPendingIntentCreatorBackgroundActivityLaunchAllowedDefault() {
        ActivityOptions options = ActivityOptions.makeBasic();

        // backwards compatibility
        checkPendingIntentCreatorBackgroundActivityStartModeBeforeAndAfterBundle(options,
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_SYSTEM_DEFINED);
    }

    @Test
    public void testGetPendingIntentCreatorBackgroundActivityStartModeAllowed() {
        ActivityOptions options = ActivityOptions.makeBasic()
                .setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        checkPendingIntentCreatorBackgroundActivityStartModeBeforeAndAfterBundle(options,

                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
    }

    @Test
    public void testGetPendingIntentCreatorBackgroundActivityStartModeDenied() {
        ActivityOptions options = ActivityOptions.makeBasic()
                .setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_DENIED);

        checkPendingIntentCreatorBackgroundActivityStartModeBeforeAndAfterBundle(options,
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_DENIED);
    }

    private void checkPendingIntentCreatorBackgroundActivityStartModeBeforeAndAfterBundle(
            ActivityOptions options, int mode) {
        assertThat(options.getPendingIntentCreatorBackgroundActivityStartMode()).isEqualTo(mode);

        Bundle bundle = options.toBundle();

        String key = "android.activity.pendingIntentCreatorBackgroundActivityStartMode";
        if (mode == ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_SYSTEM_DEFINED) {
            assertThat(bundle.containsKey(key)).isFalse();
        } else {
            assertThat(bundle.containsKey(key)).isTrue();
            assertThat(bundle.getInt(key)).isEqualTo(mode);
        }
    }

}
