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
package android.app.dream.cts.app;

import android.annotation.NonNull;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * {@link DreamOverlayService} provides a test implementation of
 * {@link android.service.dreams.DreamOverlayService}. When informed of the dream state, the service
 * populates a child window with a simple view.Once that view's visibility changes, the dream
 * broadcasts an action that tests wait upon as a signal the overlay has been displayed.
 */
public class DreamOverlayService extends android.service.dreams.DreamOverlayService {
    public static final String ACTION_DREAM_OVERLAY_SHOWN =
            "android.app.dream.cts.app.action.overlay_shown";
    public static final String ACTION_DREAM_OVERLAY_REMOVED =
            "android.app.dream.cts.app.action.overlay_removed";
    public static final String TEST_PACKAGE = "android.dreams.cts";

    private FrameLayout mLayout;

    private boolean mDreamStarted;
    private boolean mDreamEnded;

    @Override
    public void onStartDream(@NonNull WindowManager.LayoutParams layoutParams) {
        if (mDreamStarted) {
            throw new IllegalStateException("onStartDream already called");
        }

        mDreamStarted = true;
        addWindowOverlay(layoutParams);
    }

    @Override
    public void onEndDream() {
        if (mDreamEnded) {
            throw new IllegalStateException("onEndDream already called");
        }

        mDreamEnded = true;
        final WindowManager wm = getSystemService(WindowManager.class);
        wm.removeView(mLayout);
        stopSelf();
    }

    private void addWindowOverlay(WindowManager.LayoutParams layoutParams) {
        mLayout = new FrameLayout(this);
        mLayout.setBackgroundColor(Color.YELLOW);
        mLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mLayout.addOnAttachStateChangeListener(
                new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                        final Intent intent = new Intent();
                        intent.setPackage(TEST_PACKAGE);
                        intent.setAction(ACTION_DREAM_OVERLAY_SHOWN);
                        sendBroadcast(intent);
                    }

                    @Override
                    public void onViewDetachedFromWindow(View v) {
                        final Intent intent = new Intent();
                        intent.setPackage(TEST_PACKAGE);
                        intent.setAction(ACTION_DREAM_OVERLAY_REMOVED);
                        sendBroadcast(intent);
                    }
                }
        );

        final WindowManager wm = getSystemService(WindowManager.class);
        wm.addView(mLayout, layoutParams);
    }
}
