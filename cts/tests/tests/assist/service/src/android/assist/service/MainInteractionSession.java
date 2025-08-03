/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.assist.service;

import static android.view.WindowInsets.Type.displayCutout;
import static android.view.WindowInsets.Type.statusBars;

import android.app.assist.AssistContent;
import android.app.assist.AssistStructure;
import android.assist.common.Utils;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

public class MainInteractionSession extends VoiceInteractionSession {
    static final String TAG = "MainInteractionSession";

    Context mContext;
    Bundle mAssistData = new Bundle();

    private boolean hasReceivedAssistData = false;
    private boolean hasReceivedScreenshot = false;
    private boolean mScreenshotNeeded = true;
    private int mCurColor;
    private int mDisplayHeight;
    private int mDisplayWidth;
    private Rect mDisplayAreaBounds;
    private BroadcastReceiver mReceiver;
    private String mTestName;
    private View mContentView;
    private RemoteCallback mRemoteCallback;
    private Bundle mOnShowArgs;

    MainInteractionSession(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(Utils.HIDE_SESSION)) {
                    hide();
                }

                Bundle bundle = new Bundle();
                bundle.putString(Utils.EXTRA_REMOTE_CALLBACK_ACTION, Utils.HIDE_SESSION_COMPLETE);
                mRemoteCallback.sendResult(bundle);
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Utils.HIDE_SESSION);
        mContext.registerReceiver(mReceiver, filter,
                Context.RECEIVER_VISIBLE_TO_INSTANT_APPS | Context.RECEIVER_EXPORTED);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy()");
        super.onDestroy();
        if (mReceiver != null) {
            try {
                mContext.unregisterReceiver(mReceiver);
            } catch (IllegalArgumentException e) {
                // Ignore this exception when unregisterReceiver fails. Due to there will be timing
                // case to destroy VoiceInteractionSessionService before VoiceInteractionSession.
                Log.e(TAG, "Failed to unregister receiver in onDestroy.", e);
            }
        }
    }

    @Override
    public void onPrepareShow(Bundle args, int showFlags) {
        if (Utils.LIFECYCLE_NOUI.equals(args.getString(Utils.TESTCASE_TYPE, ""))) {
            setUiEnabled(false);
        } else {
            setUiEnabled(true);
        }
    }

    @Override
    public void onShow(Bundle args, int showFlags) {
        if (args == null) {
            Log.e(TAG, "onshow() received null args");
            return;
        }
        mOnShowArgs = args;
        mScreenshotNeeded = (showFlags & SHOW_WITH_SCREENSHOT) != 0;
        mTestName = args.getString(Utils.TESTCASE_TYPE, "");
        mCurColor = args.getInt(Utils.SCREENSHOT_COLOR_KEY);
        mDisplayHeight = args.getInt(Utils.DISPLAY_HEIGHT_KEY);
        mDisplayWidth = args.getInt(Utils.DISPLAY_WIDTH_KEY);
        mDisplayAreaBounds = args.getParcelable(Utils.DISPLAY_AREA_BOUNDS_KEY);
        mRemoteCallback = args.getParcelable(Utils.EXTRA_REMOTE_CALLBACK);
        super.onShow(args, showFlags);
        if (mContentView == null) return; // Happens when ui is not enabled.
        mContentView.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    mContentView.getViewTreeObserver().removeOnPreDrawListener(this);
                    Display d = mContentView.getDisplay();
                    Point displayPoint = new Point();
                    // The voice interaction window layer is higher than keyguard, status bar,
                    // nav bar now. So we should take both status bar, nav bar into consideration.
                    // The voice interaction hide the nav bar, so the height only need to consider
                    // status bar. The status bar may contain display cutout but the display cutout
                    // is device specific, we need to check it.
                    WindowManager wm = mContext.getSystemService(WindowManager.class);
                    WindowMetrics windowMetrics = wm.getCurrentWindowMetrics();
                    Rect bound = windowMetrics.getBounds();
                    WindowInsets windowInsets = windowMetrics.getWindowInsets();
                    android.graphics.Insets statusBarInsets =
                            windowInsets.getInsets(statusBars());
                    android.graphics.Insets displayCutoutInsets =
                            windowInsets.getInsets(displayCutout());
                    android.graphics.Insets min =
                            android.graphics.Insets.min(statusBarInsets, displayCutoutInsets);
                    boolean statusBarContainsCutout = !android.graphics.Insets.NONE.equals(min);
                    Log.d(TAG, "statusBarContainsCutout=" + statusBarContainsCutout);
                    displayPoint.y = statusBarContainsCutout
                            ? bound.height() - min.top - min.bottom : bound.height();
                    displayPoint.x = bound.width();
                    DisplayCutout dc = d.getCutout();
                    if (dc != null) {
                        // Means the device has a cutout area
                        android.graphics.Insets wi = d.getCutout().getWaterfallInsets();

                        if (wi != android.graphics.Insets.NONE) {
                            // Waterfall cutout. Considers only the display
                            // useful area discarding the cutout.
                            displayPoint.x -= (wi.left + wi.right);
                        }
                    }
                    Bundle bundle = new Bundle();
                    bundle.putString(Utils.EXTRA_REMOTE_CALLBACK_ACTION,
                            Utils.BROADCAST_CONTENT_VIEW_HEIGHT);
                    bundle.putInt(Utils.EXTRA_CONTENT_VIEW_HEIGHT, mContentView.getHeight());
                    bundle.putInt(Utils.EXTRA_CONTENT_VIEW_WIDTH, mContentView.getWidth());
                    bundle.putParcelable(Utils.EXTRA_DISPLAY_POINT, displayPoint);
                    mRemoteCallback.sendResult(bundle);
                    return true;
                }
            });
    }

    @Override
    public void onHandleAssist(AssistState state) {
        super.onHandleAssist(state);
        Bundle data = state.getAssistData();
        AssistStructure structure = state.getAssistStructure();
        AssistContent content = state.getAssistContent();
        ComponentName activity = structure == null ? null : structure.getActivityComponent();
        Log.i(TAG, "onHandleAssist()");
        Log.i(TAG, String.format("Bundle: %s, Activity: %s, Structure: %s, Content: %s",
                data, activity, structure, content));

        // The structure becomes null under following conditions
        // May be null if assist data has been disabled by the user or device policy;
        // Will be an empty stub if the application has disabled assist by marking its window as secure.
        // The CTS testcase will fail under the condition(automotive usecases) where
        // there are multiple displays and some of the displays are marked with FLAG_SECURE

        if ((Utils.isAutomotive(mContext)) && (structure == null)) {
            Log.i(TAG, "Ignoring... Structure is null");
            return;
        }

        if (activity != null && Utils.isAutomotive(mContext)
                && !activity.getPackageName().startsWith("android.assist")) {
            // TODO: automotive has multiple activities / displays, so the test might fail if it
            // receives one of them (like the cluster activity) instead of what's expecting. This is
            // a quick fix for the issue; a better solution would be refactoring the infra to
            // either send all events, or let the test specifify which activity it's waiting for
            Log.i(TAG, "Ignoring " + activity.flattenToShortString() + " on automotive");
            return;
        }

        if (structure != null && structure.isHomeActivity() && !state.isFocused()) {
            // If the system has multiple display areas, the launcher may be visible and resumed
            // when the tests are in progress, so the tests might fail if they receives unexpected
            // state from the launcher. Ignore the states from unfocused launcher to avoid this
            // failure.
            Log.i(TAG, "Ignoring the state from unfocused launcher");
            return;
        }

        // send to test to verify that this is accurate.
        mAssistData.putBoolean(Utils.ASSIST_IS_ACTIVITY_ID_NULL, state.getActivityId() == null);
        mAssistData.putParcelable(Utils.ASSIST_STRUCTURE_KEY, structure);
        mAssistData.putParcelable(Utils.ASSIST_CONTENT_KEY, content);
        mAssistData.putBundle(Utils.ASSIST_BUNDLE_KEY, data);
        hasReceivedAssistData = true;
        maybeBroadcastResults();
    }

    @Override
    public void onAssistStructureFailure(Throwable failure) {
        Log.e(TAG, "onAssistStructureFailure(): D'OH!!!", failure);
    }

    @Override
    public void onHandleScreenshot(/*@Nullable*/ Bitmap screenshot) {
        Log.i(TAG, String.format("onHandleScreenshot - Screenshot: %s", screenshot));
        super.onHandleScreenshot(screenshot);

        if (screenshot != null) {
            mAssistData.putBoolean(Utils.ASSIST_SCREENSHOT_KEY, true);

            if (mTestName.equals(Utils.SCREENSHOT)) {
                boolean screenshotMatches = compareScreenshot(screenshot, mCurColor);
                Log.i(TAG, "this is a screenshot test. Matches? " + screenshotMatches);
                mAssistData.putBoolean(
                    Utils.COMPARE_SCREENSHOT_KEY, screenshotMatches);
            }
        } else {
            mAssistData.putBoolean(Utils.ASSIST_SCREENSHOT_KEY, false);
        }
        hasReceivedScreenshot = true;
        maybeBroadcastResults();
    }

    private boolean compareScreenshot(Bitmap screenshot, int color) {
        // TODO(b/215668037): Uncomment when we find a reliable approach across different form
        // factors.
        // The current approach does not handle overridden screen sizes, and there's no clear way
        // to handle that and multiple display areas at the same time.
//        Point size = new Point(mDisplayWidth, mDisplayHeight);

//        if (screenshot.getWidth() != size.x || screenshot.getHeight() != size.y) {
//            Log.i(TAG, "width  or height didn't match: " + size + " vs " + screenshot.getWidth()
//                    + "," + screenshot.getHeight());
//            return false;
//        }
        Point size = new Point(screenshot.getWidth(), screenshot.getHeight());
        int[] pixels = new int[size.x * size.y];
        screenshot.getPixels(pixels, 0, size.x, 0, 0, size.x, size.y);

        // screenshot bitmap contains the screenshot for the entire physical display. A single
        // physical display could have multiple display area with different applications.
        // Let's grab the region of the display area from the original screenshot.
        Bitmap displayAreaScreenshot = Bitmap.createBitmap(screenshot, mDisplayAreaBounds.left,
                mDisplayAreaBounds.top, mDisplayAreaBounds.width(), mDisplayAreaBounds.height());
        int expectedColor = 0;
        for (int pixel : pixels) {
            // Check for roughly the same because there are rounding errors converting from the
            // screenshot's color space to SRGB, which is what getPixels does.
            if ((Color.red(pixel) - Color.red(color) < 5)
                    && (Color.green(pixel) - Color.green(color) < 5)
                    && (Color.blue(pixel) - Color.blue(color) < 5)) {
                expectedColor += 1;
            }
        }

        int pixelCount = displayAreaScreenshot.getWidth() * displayAreaScreenshot.getHeight();
        double colorRatio = (double) expectedColor / pixelCount;
        Log.i(TAG, "the ratio is " + colorRatio);
        return colorRatio >= 0.6;
    }

    private void maybeBroadcastResults() {
        if (!hasReceivedAssistData) {
            Log.i(TAG, "waiting for assist data before broadcasting results");
        } else if (mScreenshotNeeded && !hasReceivedScreenshot) {
            Log.i(TAG, "waiting for screenshot before broadcasting results");
        } else {
            Bundle bundle = new Bundle();
            bundle.putString(Utils.EXTRA_REMOTE_CALLBACK_ACTION, Utils.BROADCAST_ASSIST_DATA_INTENT);
            bundle.putBundle(Utils.ON_SHOW_ARGS_KEY, mOnShowArgs);
            bundle.putAll(mAssistData);
            mRemoteCallback.sendResult(bundle);

            hasReceivedAssistData = false;
            hasReceivedScreenshot = false;
        }
    }

    @Override
    public View onCreateContentView() {
        LayoutInflater f = getLayoutInflater();
        if (f == null) {
            Log.wtf(TAG, "layout inflater was null");
        }
        mContentView = f.inflate(R.layout.assist_layer,null);
        Log.i(TAG, "onCreateContentView");
        return mContentView;
    }
}
