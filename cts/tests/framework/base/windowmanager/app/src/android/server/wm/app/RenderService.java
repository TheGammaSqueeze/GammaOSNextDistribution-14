/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.server.wm.app.Components.RenderService.BROADCAST_EMBED_CONTENT;
import static android.server.wm.app.Components.RenderService.EXTRAS_BUNDLE;
import static android.server.wm.app.Components.RenderService.EXTRAS_DISPLAY_ID;
import static android.server.wm.app.Components.RenderService.EXTRAS_HOST_TOKEN;
import static android.server.wm.app.Components.RenderService.EXTRAS_SURFACE_PACKAGE;
import static android.server.wm.app.Components.UnresponsiveActivity.EXTRA_ON_MOTIONEVENT_DELAY_MS;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceControlViewHost;
import android.view.View;
import android.widget.Button;

import java.util.concurrent.CountDownLatch;

public class RenderService extends Service {
    private int mOnMotionEventDelayMs;
    CountDownLatch mViewDrawnCallbackLatch = new CountDownLatch(1);

    private boolean onTouch(View v, MotionEvent event) {
        SystemClock.sleep(mOnMotionEventDelayMs);
        // Don't consume the event.
        return false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Bundle b = intent.getBundleExtra(EXTRAS_BUNDLE);
        IBinder hostToken = b.getBinder(EXTRAS_HOST_TOKEN);
        int hostDisplayId = b.getInt(EXTRAS_DISPLAY_ID);
        mOnMotionEventDelayMs = b.getInt(EXTRA_ON_MOTIONEVENT_DELAY_MS);

        SurfaceControlViewHost surfaceControlViewHost = getSurfaceControlViewHost(hostToken,
                hostDisplayId);
        new Thread(()-> sendSurfacePackage(surfaceControlViewHost.getSurfacePackage())).start();
        return null;
    }

    private SurfaceControlViewHost getSurfaceControlViewHost(IBinder hostToken, int hostDisplayId) {
        final Context displayContext = getDisplayContext(hostDisplayId);
        SurfaceControlViewHost surfaceControlViewHost =
                new SurfaceControlViewHost(displayContext, displayContext.getDisplay(), hostToken);

        View embeddedView = new Button(this);
        embeddedView.setOnTouchListener(this::onTouch);
        embeddedView.getViewTreeObserver().registerFrameCommitCallback(
                mViewDrawnCallbackLatch::countDown);
        DisplayMetrics metrics = new DisplayMetrics();
        displayContext.getDisplay().getMetrics(metrics);
        surfaceControlViewHost.setView(embeddedView, metrics.widthPixels, metrics.heightPixels);
        return surfaceControlViewHost;
    }

    private Context getDisplayContext(int hostDisplayId) {
        final DisplayManager displayManager = getSystemService(DisplayManager.class);
        final Display targetDisplay = displayManager.getDisplay(hostDisplayId);
        return createDisplayContext(targetDisplay);
    }

    private void sendSurfacePackage(SurfaceControlViewHost.SurfacePackage surfacePackage) {
        try {
            // wait until we commit a frame from the embedded viewrootimpl
            mViewDrawnCallbackLatch.await();
        } catch (InterruptedException ignored) {
        }
        Intent broadcast = new Intent(BROADCAST_EMBED_CONTENT);
        broadcast.putExtra(EXTRAS_SURFACE_PACKAGE, surfacePackage);
        sendBroadcast(broadcast);
    }
}
