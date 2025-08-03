/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.media.projection.cts;

import android.content.ComponentName;
import android.content.Intent;
import android.media.cts.MediaProjectionActivity;
import android.os.Bundle;

import java.util.Optional;

public class MediaProjectionCustomIntentActivity extends MediaProjectionActivity {
    private static final String TAG = "MediaProjectionCustomIntentActivity";
    public static final String EXTRA_SCREEN_CAPTURE_INTENT = "extra_screen_capture_intent";
    public static final String EXTRA_FGS_CLASS = "extra_fgs_class";
    private Optional<Intent> mProvidedScreenCaptureIntent;

    @Override
    protected Intent getScreenCaptureIntent() {
        return mProvidedScreenCaptureIntent.orElseGet(super::getScreenCaptureIntent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Save intent before invoking super
        final Intent originalIntent = getIntent();
        if (originalIntent.hasExtra(EXTRA_SCREEN_CAPTURE_INTENT)) {
            mProvidedScreenCaptureIntent = Optional.of(
                    originalIntent.getParcelableExtra(EXTRA_SCREEN_CAPTURE_INTENT, Intent.class));
        } else {
            mProvidedScreenCaptureIntent = Optional.empty();
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    public ComponentName getForegroundServiceComponentName() {
        final String cls = getIntent().getStringExtra(EXTRA_FGS_CLASS);
        return cls != null ? new ComponentName(this, cls)
                : super.getForegroundServiceComponentName();
    }
}
