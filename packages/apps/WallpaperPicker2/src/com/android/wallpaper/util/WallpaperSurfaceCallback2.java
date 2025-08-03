/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.android.wallpaper.util.WallpaperSurfaceCallback.LOW_RES_BITMAP_BLUR_RADIUS;

import android.content.Context;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.android.wallpaper.model.WallpaperInfo.ColorInfo;
import com.android.wallpaper.module.Injector;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.PackageStatusNotifier;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Default implementation of {@link SurfaceHolder.Callback} to render a static wallpaper when the
 * surface has been created.
 */
public class WallpaperSurfaceCallback2 implements SurfaceHolder.Callback {

    /**
     * Listener used to be notified when this surface is created
     */
    public interface SurfaceListener {
        /**
         * Called when {@link WallpaperSurfaceCallback2#surfaceCreated(SurfaceHolder)} is called.
         */
        void onSurfaceCreated();
    }

    private static final String TAG = "WallpaperSurfaceCallback2";
    private Surface mLastSurface;
    private SurfaceControlViewHost mHost;
    // Home workspace surface is behind the app window, and so must the home image wallpaper like
    // the live wallpaper. This view is rendered on here for home image wallpaper.
    private ImageView mHomeImageWallpaper;
    private final Context mAppContext;
    private final SurfaceView mWallpaperSurface;
    @Nullable
    private final SurfaceListener mListener;
    @Nullable
    private final Future<ColorInfo> mColorFuture;
    private boolean mSurfaceCreated;

    private final PackageStatusNotifier.Listener mAppStatusListener;
    private final PackageStatusNotifier mPackageStatusNotifier;

    public WallpaperSurfaceCallback2(Context context,
            SurfaceView wallpaperSurface,
            @Nullable Future<ColorInfo> colorFuture,
            @Nullable SurfaceListener listener) {
        mAppContext = context.getApplicationContext();
        mWallpaperSurface = wallpaperSurface;
        mListener = listener;

        // Notify WallpaperSurface to reset image wallpaper when encountered live wallpaper's
        // package been changed in background.
        Injector injector = InjectorProvider.getInjector();
        mPackageStatusNotifier = injector.getPackageStatusNotifier(context);
        mAppStatusListener = (packageName, status) -> {
            if (status != PackageStatusNotifier.PackageStatus.REMOVED) {
                resetHomeImageWallpaper();
            }
        };
        mPackageStatusNotifier.addListener(mAppStatusListener,
                WallpaperService.SERVICE_INTERFACE);
        mColorFuture = colorFuture;
    }

    public WallpaperSurfaceCallback2(Context context, SurfaceView wallpaperSurface,
            @Nullable SurfaceListener listener) {
        this(context, wallpaperSurface, /* colorFuture= */ null, listener);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mLastSurface != holder.getSurface()) {
            mLastSurface = holder.getSurface();
            setupSurfaceWallpaper(/* forceClean= */ true);
        }
        if (mListener != null) {
            mListener.onSurfaceCreated();
        }
        mSurfaceCreated = true;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mSurfaceCreated = false;
    }

    /**
     * Call to release resources and app status listener.
     */
    public void cleanUp() {
        releaseHost();
        if (mHomeImageWallpaper != null) {
            mHomeImageWallpaper.setImageDrawable(null);
        }
        mPackageStatusNotifier.removeListener(mAppStatusListener);
    }

    private void releaseHost() {
        if (mHost != null) {
            mHost.release();
            mHost = null;
        }
    }

    /**
     * Reset existing image wallpaper by creating a new ImageView for SurfaceControlViewHost
     * if surface state is not created.
     */
    private void resetHomeImageWallpaper() {
        if (mSurfaceCreated) {
            return;
        }

        if (mHost != null) {
            setupSurfaceWallpaper(/* forceClean= */ false);
        }
    }

    private void setupSurfaceWallpaper(boolean forceClean) {
        mHomeImageWallpaper = new ImageView(mAppContext);
        mHomeImageWallpaper.setBackgroundColor(getPlaceHolderColor());
        mHomeImageWallpaper.measure(makeMeasureSpec(mWallpaperSurface.getWidth(), EXACTLY),
                makeMeasureSpec(mWallpaperSurface.getHeight(), EXACTLY));
        mHomeImageWallpaper.layout(0, 0, mWallpaperSurface.getWidth(),
                mWallpaperSurface.getHeight());
        if (forceClean) {
            releaseHost();
            mHost = new SurfaceControlViewHost(mAppContext,
                    mWallpaperSurface.getDisplay(), mWallpaperSurface.getHostToken());
        }

        if (mHost != null) {
            mHost.setView(mHomeImageWallpaper, mHomeImageWallpaper.getWidth(),
                    mHomeImageWallpaper.getHeight());
            mWallpaperSurface.setChildSurfacePackage(mHost.getSurfacePackage());
        }
    }

    private int getPlaceHolderColor() {
        if (mColorFuture != null && mColorFuture.isDone()) {
            try {
                ColorInfo colorInfo = mColorFuture.get();
                if (colorInfo != null) {
                    return colorInfo.getPlaceholderColor();
                }
            } catch (InterruptedException | ExecutionException e) {
                Log.e(TAG, "Couldn't get placeholder from ColorInfo.");
            }
        }
        return getDefaultPlaceHolderColor();
    }

    private int getDefaultPlaceHolderColor() {
        return ResourceUtils.getColorAttr(mAppContext, android.R.attr.colorSecondary);
    }

    @Nullable
    public ImageView getHomeImageWallpaper() {
        return mHomeImageWallpaper;
    }

    /**
     * @param blur whether to blur the home image wallpaper
     */
    public void setHomeImageWallpaperBlur(boolean blur) {
        if (mHomeImageWallpaper == null) {
            return;
        }
        if (blur) {
            mHomeImageWallpaper.setRenderEffect(
                    RenderEffect.createBlurEffect(LOW_RES_BITMAP_BLUR_RADIUS,
                            LOW_RES_BITMAP_BLUR_RADIUS, Shader.TileMode.CLAMP));
        } else {
            mHomeImageWallpaper.setRenderEffect(null);
        }
    }
}
