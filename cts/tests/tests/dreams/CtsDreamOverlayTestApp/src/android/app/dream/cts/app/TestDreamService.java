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

import android.content.Intent;
import android.graphics.Color;
import android.service.dreams.DreamService;
import android.widget.FrameLayout;

/**
 * {@link TestDreamService} is a minimal concrete {@link DreamService} implementation that sets
 * the entire window to be blue.
 */
public class TestDreamService extends DreamService {
    private static final String TEST_PACKAGE = "android.dreams.cts";
    private static final String ACTION_DREAM_SHOWN =
            "android.app.dream.cts.app.action.dream_shown";

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        setInteractive(false);
        setFullscreen(true);

        final FrameLayout frameLayout = new FrameLayout(getApplicationContext());
        frameLayout.setBackgroundColor(Color.BLUE);
        setContentView(frameLayout);
    }

    @Override
    public void onDreamingStarted() {
        final Intent intent = new Intent(ACTION_DREAM_SHOWN);
        intent.setPackage(TEST_PACKAGE);
        sendBroadcast(intent);
    }

    @Override
    public void onDreamingStopped() {
        finish();
    }
}
