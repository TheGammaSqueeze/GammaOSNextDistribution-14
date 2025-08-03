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

package android.server.wm.overlay;

import static android.view.Display.DEFAULT_DISPLAY;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.server.wm.shared.IUntrustedTouchTestService;
import android.util.ArrayMap;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.Map;


public class UntrustedTouchTestService extends Service {
    public static final int BACKGROUND_COLOR = 0xFF00FF00;

    /** Map from view to the service manager that manages it. */
    private final Map<View, WindowManager> mViewManagers = Collections.synchronizedMap(
            new ArrayMap<>());

    /** Can only be accessed from the main thread. */
    private Toast mToast;

    private final IUntrustedTouchTestService mBinder = new Binder();
    private volatile Handler mMainHandler;
    private volatile Context mSawContext;
    private volatile WindowManager mWindowManager;
    private volatile WindowManager mSawWindowManager;

    @Override
    public void onCreate() {
        mMainHandler = new Handler(Looper.getMainLooper());
        mWindowManager = getSystemService(WindowManager.class);
        mSawContext = getContextForSaw(this);
        mSawWindowManager = mSawContext.getSystemService(WindowManager.class);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder.asBinder();
    }

    @Override
    public void onDestroy() {
        removeOverlays();
    }

    private class Binder extends IUntrustedTouchTestService.Stub {
        private final UntrustedTouchTestService mService = UntrustedTouchTestService.this;

        @Override
        public void showToast() {
            mMainHandler.post(() -> {
                mToast = Toast.makeText(mService, "Toast " + getPackageName(), Toast.LENGTH_LONG);
                mToast.show();
            });
        }

        @Override
        public void showSystemAlertWindow(String name, float opacity, int viewX, int viewY) {
            View view = getView(mSawContext);
            LayoutParams params = newOverlayLayoutParams(name,
                    LayoutParams.TYPE_APPLICATION_OVERLAY);
            params.setTitle(name);
            params.alpha = opacity;
            params.x = viewX;
            params.y = viewY;
            mMainHandler.post(() -> mSawWindowManager.addView(view, params));
            mViewManagers.put(view, mSawWindowManager);
        }

        @Override
        public void showActivityChildWindow(String name, IBinder token) throws RemoteException {
            View view = getView(mService);
            LayoutParams params = newOverlayLayoutParams(name, LayoutParams.TYPE_APPLICATION);
            params.token = token;
            mMainHandler.post(() -> mWindowManager.addView(view, params));
            mViewManagers.put(view, mWindowManager);
        }

        public void removeOverlays() {
            mService.removeOverlays();
        }
    }

    private void removeOverlays() {
        synchronized (mViewManagers) {
            for (View view : mViewManagers.keySet()) {
                mViewManagers.get(view).removeView(view);
            }
            mViewManagers.clear();
        }
        mMainHandler.post(() -> {
            if (mToast != null) {
                mToast.cancel();
            }
        });
    }

    private static Context getContextForSaw(Context context) {
        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        Display display = displayManager.getDisplay(DEFAULT_DISPLAY);
        Context displayContext = context.createDisplayContext(display);
        return displayContext.createWindowContext(LayoutParams.TYPE_APPLICATION_OVERLAY, null);
    }

    private static View getView(Context context) {
        View view = new View(context);
        view.setBackgroundColor(BACKGROUND_COLOR);
        return view;
    }

    private static LayoutParams newOverlayLayoutParams(String windowName, int type) {
        LayoutParams params = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                type,
                LayoutParams.FLAG_NOT_TOUCHABLE | LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.setTitle(windowName);
        return params;
    }
}
