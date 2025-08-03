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

package android.view.surfacecontrol.cts;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.KeyguardManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.Gravity;
import android.view.SurfaceControlViewHost;
import android.view.View;
import android.view.ViewGroup;
import android.view.cts.surfacevalidator.IAttachEmbeddedWindow;
import android.view.cts.surfacevalidator.SurfaceControlViewHostHelper;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SurfaceSyncGroupActivity extends Activity {
    private static final String TAG = "SurfaceSyncGroupActivity";
    private SurfaceControlViewHostHelper mSurfaceControlViewHostHelper;
    private final CountDownLatch mCountDownLatch = new CountDownLatch(1);

    private ViewGroup mParentView;
    private static final Size sSize = new Size(500, 500);

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSurfaceControlViewHostHelper = new SurfaceControlViewHostHelper(TAG,
                mCountDownLatch,
                this, 0, sSize);

        mParentView = new FrameLayout(this);
        setContentView(mParentView);

        KeyguardManager km = getSystemService(KeyguardManager.class);
        km.requestDismissKeyguard(this, null);
    }

    public Pair<SurfaceControlViewHost.SurfacePackage, IAttachEmbeddedWindow> setupEmbeddedSCVH() {
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(sSize.getWidth(),
                sSize.getHeight());
        layoutParams.gravity = Gravity.CENTER;
        runOnUiThread(() -> mSurfaceControlViewHostHelper.attachSurfaceView(mParentView,
                layoutParams));
        mSurfaceControlViewHostHelper.bindEmbeddedService(false /* inProcess */);

        boolean ready = false;
        try {
            ready = mCountDownLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Failed to wait for SCVH to attach");
        }

        assertTrue("Failed to attach SCVH", ready);

        return new Pair<>(mSurfaceControlViewHostHelper.getSurfacePackage(),
                mSurfaceControlViewHostHelper.getAttachedEmbeddedWindow());
    }

    public View getBackgroundView() {
        return mParentView;
    }
}
