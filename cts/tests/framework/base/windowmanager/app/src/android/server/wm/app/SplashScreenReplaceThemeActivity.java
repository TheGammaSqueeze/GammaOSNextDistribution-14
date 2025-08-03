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

package android.server.wm.app;

import static android.server.wm.app.Components.TestStartingWindowKeys.OVERRIDE_THEME_COLOR;
import static android.server.wm.app.Components.TestStartingWindowKeys.OVERRIDE_THEME_COMPONENT;
import static android.server.wm.app.Components.TestStartingWindowKeys.OVERRIDE_THEME_ENABLED;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.server.wm.TestJournalProvider;
import android.window.SplashScreen;
import android.window.SplashScreenView;

public class SplashScreenReplaceThemeActivity extends Activity {
    private final SplashScreen.OnExitAnimationListener mSplashScreenExitHandler =
            this::onSplashScreenExit;
    private boolean mOverrideTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSplashScreen().setOnExitAnimationListener(mSplashScreenExitHandler);
        mOverrideTheme = getIntent().getBooleanExtra(OVERRIDE_THEME_ENABLED, false);
        HandleSplashScreenExitActivity.deferDraw(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mOverrideTheme) {
            getSplashScreen().setSplashScreenTheme(R.style.SplashScreenOverrideTheme);
        } else {
            getSplashScreen().setSplashScreenTheme(Resources.ID_NULL);
        }
    }

    private void onSplashScreenExit(SplashScreenView view) {
        final Context baseContext = getBaseContext();
        TestJournalProvider.putExtras(baseContext, OVERRIDE_THEME_COMPONENT,
                bundle -> bundle.putInt(OVERRIDE_THEME_COLOR,
                        ((ColorDrawable) view.getBackground()).getColor()));
        view.remove();
        finish();
    }
}
