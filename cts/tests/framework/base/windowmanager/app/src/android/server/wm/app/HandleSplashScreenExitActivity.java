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

import static android.app.UiModeManager.MODE_NIGHT_AUTO;
import static android.server.wm.app.Components.TestStartingWindowKeys.CANCEL_HANDLE_EXIT;
import static android.server.wm.app.Components.TestStartingWindowKeys.CENTER_VIEW_IS_SURFACE_VIEW;
import static android.server.wm.app.Components.TestStartingWindowKeys.CONTAINS_BRANDING_VIEW;
import static android.server.wm.app.Components.TestStartingWindowKeys.CONTAINS_CENTER_VIEW;
import static android.server.wm.app.Components.TestStartingWindowKeys.DELAY_RESUME;
import static android.server.wm.app.Components.TestStartingWindowKeys.GET_NIGHT_MODE_ACTIVITY_CHANGED;
import static android.server.wm.app.Components.TestStartingWindowKeys.HANDLE_SPLASH_SCREEN_EXIT;
import static android.server.wm.app.Components.TestStartingWindowKeys.ICON_ANIMATION_DURATION;
import static android.server.wm.app.Components.TestStartingWindowKeys.ICON_ANIMATION_START;
import static android.server.wm.app.Components.TestStartingWindowKeys.ICON_BACKGROUND_COLOR;
import static android.server.wm.app.Components.TestStartingWindowKeys.RECEIVE_SPLASH_SCREEN_EXIT;
import static android.server.wm.app.Components.TestStartingWindowKeys.REQUEST_HANDLE_EXIT_ON_CREATE;
import static android.server.wm.app.Components.TestStartingWindowKeys.REQUEST_HANDLE_EXIT_ON_RESUME;
import static android.server.wm.app.Components.TestStartingWindowKeys.REQUEST_SET_NIGHT_MODE_ON_CREATE;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.server.wm.TestJournalProvider;
import android.view.SurfaceView;
import android.view.ViewTreeObserver;
import android.window.SplashScreen;

public class HandleSplashScreenExitActivity extends Activity {
    private SplashScreen mSSM;
    private UiModeManager mUiModeManager;
    private boolean mReportSplashScreenNightMode;

    /** Gets the key to indicate the owner for test journal. */
    protected String getOwnerKey() {
        return HANDLE_SPLASH_SCREEN_EXIT;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSSM = getSplashScreen();
        if (getIntent().getBooleanExtra(REQUEST_HANDLE_EXIT_ON_CREATE, false)) {
            mSSM.setOnExitAnimationListener(mSplashScreenExitHandler);
        }
        final String nightMode = getIntent().getStringExtra(REQUEST_SET_NIGHT_MODE_ON_CREATE);
        if (nightMode != null) {
            mUiModeManager = getSystemService(UiModeManager.class);
            final int setNightMode = Integer.parseInt(nightMode);
            mUiModeManager.setApplicationNightMode(setNightMode);
            mReportSplashScreenNightMode = true;
        }
        if (getIntent().getBooleanExtra(DELAY_RESUME, false)) {
            SystemClock.sleep(5000);
        }
        deferDraw(this);
    }

    private final SplashScreen.OnExitAnimationListener mSplashScreenExitHandler =
            view -> {
                final Context baseContext = getBaseContext();
                final boolean containsCenter = view.getIconView() != null;
                final boolean containsBranding = view.getBrandingView() != null
                        && view.getBrandingView().getBackground() != null;
                final Drawable background = containsCenter
                        ? view.getIconView().getBackground() : null;
                final int iconBackground;
                if (background != null) {
                    Bitmap bitmap = ((BitmapDrawable) background).getBitmap();
                    iconBackground = bitmap.getPixel(bitmap.getWidth() / 2, bitmap.getHeight() / 2);
                } else {
                    iconBackground = Color.TRANSPARENT;
                }
                TestJournalProvider.putExtras(baseContext, getOwnerKey(), bundle -> {
                    bundle.putBoolean(RECEIVE_SPLASH_SCREEN_EXIT, true);
                    bundle.putBoolean(CONTAINS_CENTER_VIEW, containsCenter);
                    bundle.putBoolean(CONTAINS_BRANDING_VIEW, containsBranding);
                    bundle.putInt(ICON_BACKGROUND_COLOR, iconBackground);
                    bundle.putLong(ICON_ANIMATION_DURATION, view.getIconAnimationDuration() != null
                            ? view.getIconAnimationDuration().toMillis() : 0);
                    bundle.putLong(ICON_ANIMATION_START, view.getIconAnimationStart() != null
                            ? view.getIconAnimationStart().toEpochMilli() : 0);
                    bundle.putBoolean(CENTER_VIEW_IS_SURFACE_VIEW,
                            view.getIconView() instanceof SurfaceView);
                });
                view.postDelayed(view::remove, 500);
            };

    @Override
    public void onResume() {
        super.onResume();
        if (getIntent().getBooleanExtra(REQUEST_HANDLE_EXIT_ON_RESUME, false)) {
            mSSM.setOnExitAnimationListener(mSplashScreenExitHandler);
        }
        if (getIntent().getBooleanExtra(CANCEL_HANDLE_EXIT, false)) {
            mSSM.clearOnExitAnimationListener();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mReportSplashScreenNightMode) {
            final int configNightMode = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
            final Context baseContext = getBaseContext();
            TestJournalProvider.putExtras(baseContext, getOwnerKey(), bundle -> {
                bundle.putInt(GET_NIGHT_MODE_ACTIVITY_CHANGED, configNightMode);
            });
            // reset after test done
            mReportSplashScreenNightMode = false;
            mUiModeManager.setApplicationNightMode(MODE_NIGHT_AUTO);
        }
    }

    /** Deferring show app window to let splash screen display for a while. */
    static void deferDraw(Activity a) {
        a.getWindow().getDecorView().getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    final long mCreateTime = SystemClock.uptimeMillis();

                    @Override
                    public boolean onPreDraw() {
                        return SystemClock.uptimeMillis() - mCreateTime > 500;
                    }
                });
    }
}
