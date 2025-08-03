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
package com.android.wallpaper.util;

import static android.graphics.Matrix.MSCALE_X;
import static android.graphics.Matrix.MSCALE_Y;
import static android.graphics.Matrix.MSKEW_X;
import static android.graphics.Matrix.MSKEW_Y;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.service.wallpaper.IWallpaperConnection;
import android.service.wallpaper.IWallpaperEngine;
import android.service.wallpaper.IWallpaperService;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager.LayoutParams;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link IWallpaperConnection} that handles communication with a
 * {@link android.service.wallpaper.WallpaperService}
 */
public class WallpaperConnection extends IWallpaperConnection.Stub implements ServiceConnection {

    /**
     * Defines different possible scenarios for which we need to dispatch a command from picker to
     * the wallpaper.
     */
    public enum WhichPreview {
        /**
         * Represents the case when we preview a currently applied wallpaper (home/lock) simply
         * by tapping on it.
         */
        PREVIEW_CURRENT(0),
        /**
         * Represents the case when we are editing the currently applied wallpaper.
         */
        EDIT_CURRENT(1),
        /**
         * Represents the case when we are editing a wallpaper that's not currently applied.
         */
        EDIT_NON_CURRENT(2);

        private final int mValue;

        WhichPreview(int value) {
            this.mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    /**
     * Returns whether live preview is available in framework.
     */
    public static boolean isPreviewAvailable() {
        try {
            return IWallpaperEngine.class.getMethod("mirrorSurfaceControl") != null;
        } catch (NoSuchMethodException | SecurityException e) {
            return false;
        }
    }

    private static final String TAG = "WallpaperConnection";
    private static final Looper sMainLooper = Looper.getMainLooper();
    private final Context mContext;
    private final Intent mIntent;
    private final List<SurfaceControl> mMirrorSurfaceControls = new ArrayList<>();
    private WallpaperConnectionListener mListener;
    private SurfaceView mContainerView;
    private SurfaceView mSecondContainerView;
    private IWallpaperService mService;
    @Nullable private IWallpaperEngine mEngine;
    @Nullable private Point mDisplayMetrics;
    private boolean mConnected;
    private boolean mIsVisible;
    private boolean mIsEngineVisible;
    private boolean mEngineReady;
    private boolean mDestroyed;
    private int mDestinationFlag;
    private WhichPreview mWhichPreview;

    /**
     * @param intent used to bind the wallpaper service
     * @param context Context used to start and bind the live wallpaper service
     * @param listener if provided, it'll be notified of connection/disconnection events
     * @param containerView SurfaceView that will display the wallpaper
     */
    public WallpaperConnection(Intent intent, Context context,
            @Nullable WallpaperConnectionListener listener, @NonNull SurfaceView containerView,
            WhichPreview preview) {
        this(intent, context, listener, containerView, null, null,
                preview);
    }

    /**
     * @param intent used to bind the wallpaper service
     * @param context Context used to start and bind the live wallpaper service
     * @param listener if provided, it'll be notified of connection/disconnection events
     * @param containerView SurfaceView that will display the wallpaper
     * @param secondaryContainerView optional SurfaceView that will display a second, mirrored
     *                               version of the wallpaper
     * @param destinationFlag one of WallpaperManager.FLAG_SYSTEM, WallpaperManager.FLAG_LOCK
     *                        indicating for which screen we're previewing the wallpaper, or null if
     *                        unknown
     */
    public WallpaperConnection(Intent intent, Context context,
            @Nullable WallpaperConnectionListener listener, @NonNull SurfaceView containerView,
            @Nullable SurfaceView secondaryContainerView,
            @Nullable @WallpaperManager.SetWallpaperFlags Integer destinationFlag,
            WhichPreview preview) {
        mContext = context.getApplicationContext();
        mIntent = intent;
        mListener = listener;
        mContainerView = containerView;
        mSecondContainerView = secondaryContainerView;
        mDestinationFlag = destinationFlag == null ? WallpaperManager.FLAG_SYSTEM : destinationFlag;
        mWhichPreview = preview;
    }

    /**
     * Bind the Service for this connection.
     */
    public boolean connect() {
        if (mDestroyed) {
            throw new IllegalStateException("Cannot connect on a destroyed WallpaperConnection");
        }
        synchronized (this) {
            if (mConnected) {
                return true;
            }
            if (!mContext.bindService(mIntent, this,
                    Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT
                            | Context.BIND_ALLOW_ACTIVITY_STARTS)) {
                return false;
            }

            mConnected = true;
        }

        if (mListener != null) {
            mListener.onConnected();
        }

        return true;
    }

    /**
     * Disconnect and destroy the WallpaperEngine for this connection.
     */
    public void disconnect() {
        synchronized (this) {
            mConnected = false;
            if (mEngine != null) {
                try {
                    mEngine.destroy();
                    for (SurfaceControl control : mMirrorSurfaceControls) {
                        control.release();
                    }
                    mMirrorSurfaceControls.clear();
                } catch (RemoteException e) {
                    // Ignore
                }
                mEngine = null;
            }
            try {
                mContext.unbindService(this);
            } catch (IllegalArgumentException e) {
                Log.i(TAG, "Can't unbind wallpaper service. "
                        + "It might have crashed, just ignoring.");
            }
            mService = null;
        }
        if (mListener != null) {
            mListener.onDisconnected();
        }
    }

    /**
     * Clean up references on this WallpaperConnection.
     * After calling this method, {@link #connect()} cannot be called again.
     */
    public void destroy() {
        disconnect();
        mContainerView = null;
        mSecondContainerView = null;
        mListener = null;
        mDestroyed = true;
    }

    /**
     * @see ServiceConnection#onServiceConnected(ComponentName, IBinder)
     */
    public void onServiceConnected(ComponentName name, IBinder service) {
        if (mContainerView == null) {
            return;
        }
        mService = IWallpaperService.Stub.asInterface(service);
        if (mContainerView.getDisplay() == null) {
            mContainerView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    attachConnection(v.getDisplay().getDisplayId());
                    mContainerView.removeOnAttachStateChangeListener(this);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {}
            });
        } else {
            attachConnection(mContainerView.getDisplay().getDisplayId());
        }
    }

    @Override
    public void onLocalWallpaperColorsChanged(RectF area,
            WallpaperColors colors, int displayId) {

    }

    /**
     * @see ServiceConnection#onServiceDisconnected(ComponentName)
     */
    public void onServiceDisconnected(ComponentName name) {
        mService = null;
        mEngine = null;
        Log.w(TAG, "Wallpaper service gone: " + name);
    }

    /**
     * @see IWallpaperConnection#attachEngine(IWallpaperEngine, int)
     */
    public void attachEngine(IWallpaperEngine engine, int displayId) {
        synchronized (this) {
            if (mConnected) {
                mEngine = engine;
                if (mIsVisible) {
                    setEngineVisibility(true);
                }

                try {
                    Point displayMetrics = getDisplayMetrics();
                    // Reset the live wallpaper preview with the correct screen dimensions. It is
                    // a known issue that the wallpaper service maybe get the Activity window size
                    // which may differ from the actual physical device screen size, e.g. when in
                    // 2-pane mode.
                    // TODO b/262750854 Fix wallpaper service to get the actual physical device
                    //      screen size instead of the window size that might be smaller when in
                    //      2-pane mode.
                    mEngine.resizePreview(new Rect(0, 0, displayMetrics.x, displayMetrics.y));
                    // Some wallpapers don't trigger #onWallpaperColorsChanged from remote.
                    // Requesting wallpaper color here to ensure the #onWallpaperColorsChanged
                    // would get called.
                    mEngine.requestWallpaperColors();
                } catch (RemoteException | NullPointerException e) {
                    Log.w(TAG, "Failed calling WallpaperEngine APIs", e);
                }
            } else {
                try {
                    engine.destroy();
                } catch (RemoteException e) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Returns the engine handled by this WallpaperConnection
     */
    @Nullable
    public IWallpaperEngine getEngine() {
        return mEngine;
    }

    /**
     * @see IWallpaperConnection#setWallpaper(String)
     */
    public ParcelFileDescriptor setWallpaper(String name) {
        return null;
    }

    @Override
    public void onWallpaperColorsChanged(WallpaperColors colors, int displayId) {
        if (mContainerView != null) {
            mContainerView.post(() -> {
                if (mListener != null) {
                    mListener.onWallpaperColorsChanged(colors, displayId);
                }
            });
        }
    }

    @Override
    public void engineShown(IWallpaperEngine engine) {
        mEngineReady = true;
        Bundle bundle = new Bundle();
        bundle.putInt("which_preview", mWhichPreview.getValue());
        try {
            engine.dispatchWallpaperCommand("android.wallpaper.previewinfo", 0, 0, 0, bundle);
        } catch (RemoteException e) {
            Log.e(TAG, "Error dispatching wallpaper command: " + mWhichPreview.toString());
        }
        if (mContainerView != null) {
            mContainerView.post(() -> reparentWallpaperSurface(mContainerView));
        }
        if (mSecondContainerView != null) {
            mSecondContainerView.post(() -> reparentWallpaperSurface(mSecondContainerView));
        }
        if (mContainerView != null) {
            mContainerView.post(() -> {
                if (mListener != null) {
                    mListener.onEngineShown();
                }
            });
        }
    }

    /**
     * Returns true if the wallpaper engine has been initialized.
     */
    public boolean isEngineReady() {
        return mEngineReady;
    }

    /**
     * Sets the engine's visibility.
     */
    public void setVisibility(boolean visible) {
        synchronized (this) {
            mIsVisible = visible;
            setEngineVisibility(visible);
        }
    }


    /**
     * Set the {@link android.app.WallpaperManager.SetWallpaperFlags} to the Engine to indicate
     * which screen it's being applied/previewed to.
     */
    public void setWallpaperFlags(@WallpaperManager.SetWallpaperFlags int wallpaperFlags)
            throws RemoteException {
        if (mEngine != null && mEngineReady) {
            mEngine.setWallpaperFlags(wallpaperFlags);
        }
    }

    private void attachConnection(int displayId) {
        try {
            try {
                Method preUMethod = mService.getClass().getMethod("attach",
                        IWallpaperConnection.class, IBinder.class, int.class, boolean.class,
                        int.class, int.class, Rect.class, int.class);
                preUMethod.invoke(mService, this, mContainerView.getWindowToken(),
                        LayoutParams.TYPE_APPLICATION_MEDIA, true, mContainerView.getWidth(),
                        mContainerView.getHeight(), new Rect(0, 0, 0, 0), displayId);
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                Log.d(TAG, "IWallpaperService#attach method without which argument not available, "
                        + "will use newer version");
                // Let's try the new attach method that takes "which" argument
                mService.attach(this, mContainerView.getWindowToken(),
                        LayoutParams.TYPE_APPLICATION_MEDIA, true, mContainerView.getWidth(),
                        mContainerView.getHeight(), new Rect(0, 0, 0, 0), displayId,
                        mDestinationFlag, null);
            }
        } catch (RemoteException e) {
            Log.w(TAG, "Failed attaching wallpaper; clearing", e);
        }
    }

    private void setEngineVisibility(boolean visible) {
        if (mEngine != null && visible != mIsEngineVisible) {
            try {
                mEngine.setVisibility(visible);
                mIsEngineVisible = visible;
            } catch (RemoteException e) {
                Log.w(TAG, "Failure setting wallpaper visibility ", e);
            }
        }
    }

    private void reparentWallpaperSurface(SurfaceView parentSurface) {
        if (parentSurface == null) {
            return;
        }
        synchronized (this) {
            if (mEngine == null) {
                Log.i(TAG, "Engine is null, was the service disconnected?");
                return;
            }
        }
        if (parentSurface.getSurfaceControl() != null) {
            mirrorAndReparent(parentSurface);
        } else {
            Log.d(TAG, "SurfaceView not initialized yet, adding callback");
            parentSurface.getHolder().addCallback(new Callback() {
                @Override
                public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

                }

                @Override
                public void surfaceCreated(SurfaceHolder surfaceHolder) {
                    mirrorAndReparent(parentSurface);
                    parentSurface.getHolder().removeCallback(this);
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

                }
            });
        }
    }

    private void mirrorAndReparent(SurfaceView parentSurface) {
        IWallpaperEngine engine;
        synchronized (this) {
            if (mEngine == null) {
                Log.i(TAG, "Engine is null, was the service disconnected?");
                return;
            }
            engine = mEngine;
        }
        try {
            SurfaceControl parentSC = parentSurface.getSurfaceControl();
            SurfaceControl wallpaperMirrorSC = engine.mirrorSurfaceControl();
            if (wallpaperMirrorSC == null) {
                return;
            }
            float[] values = getScale(parentSurface);
            try (SurfaceControl.Transaction t = new SurfaceControl.Transaction()) {
                t.setMatrix(wallpaperMirrorSC, values[MSCALE_X], values[MSKEW_Y],
                        values[MSKEW_X], values[MSCALE_Y]);
                t.reparent(wallpaperMirrorSC, parentSC);
                t.show(wallpaperMirrorSC);
                t.apply();
            }
            synchronized (this) {
                mMirrorSurfaceControls.add(wallpaperMirrorSC);
            }
        } catch (RemoteException | NullPointerException e) {
            Log.e(TAG, "Couldn't reparent wallpaper surface", e);
        }
    }

    private float[] getScale(SurfaceView parentSurface) {
        Matrix m = new Matrix();
        float[] values = new float[9];
        Rect surfacePosition = parentSurface.getHolder().getSurfaceFrame();
        Point displayMetrics = getDisplayMetrics();
        m.postScale(((float) surfacePosition.width()) / displayMetrics.x,
                ((float) surfacePosition.height()) / displayMetrics.y);
        m.getValues(values);
        return values;
    }

    /**
     * Get display metrics. Only call this when the display is attached to the window.
     */
    private Point getDisplayMetrics() {
        if (mDisplayMetrics != null) {
            return mDisplayMetrics;
        }
        ScreenSizeCalculator screenSizeCalculator = ScreenSizeCalculator.getInstance();
        Display display = mContainerView.getDisplay();
        if (display == null) {
            throw new NullPointerException(
                    "Display is null due to the view not currently attached to a window.");
        }
        mDisplayMetrics = screenSizeCalculator.getScreenSize(display);
        return mDisplayMetrics;
    }

    /**
     * Interface to be notified of connect/disconnect events from {@link WallpaperConnection}
     */
    public interface WallpaperConnectionListener {
        /**
         * Called after the Wallpaper service has been bound.
         */
        default void onConnected() {}

        /**
         * Called after the Wallpaper engine has been terminated and the service has been unbound.
         */
        default void onDisconnected() {}

        /**
         * Called after the wallpaper has been rendered for the first time.
         */
        default void onEngineShown() {}

        /**
         * Called after the wallpaper color is available or updated.
         */
        default void onWallpaperColorsChanged(WallpaperColors colors, int displayId) {}
    }
}
