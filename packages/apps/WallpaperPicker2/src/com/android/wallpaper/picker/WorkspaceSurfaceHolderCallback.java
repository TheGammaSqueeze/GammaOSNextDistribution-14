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
package com.android.wallpaper.picker;

import android.app.WallpaperColors;
import android.os.Bundle;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.Nullable;

import com.android.wallpaper.util.PreviewUtils;
import com.android.wallpaper.util.SurfaceViewUtils;

import java.util.concurrent.atomic.AtomicBoolean;

/** A surface holder callback that renders user's workspace on the passed in surface view. */
public class WorkspaceSurfaceHolderCallback implements SurfaceHolder.Callback {

    /**
     * Listener to be called when workspace surface is updated with a new Surface Package.
     */
    public interface WorkspaceRenderListener {
        /**
         * Called on the main thread after the workspace surface is updated from the provider
         */
        void onWorkspaceRendered();
    }

    private static final String TAG = "WsSurfaceHolderCallback";
    private static final String KEY_WALLPAPER_COLORS = "wallpaper_colors";
    public static final int MESSAGE_ID_UPDATE_PREVIEW = 1337;
    public static final String KEY_HIDE_BOTTOM_ROW = "hide_bottom_row";
    public static final int MESSAGE_ID_COLOR_OVERRIDE = 1234;
    public static final String KEY_COLOR_OVERRIDE = "color_override"; // ColorInt Encoded as string
    private final SurfaceView mWorkspaceSurface;
    private final PreviewUtils mPreviewUtils;
    private final boolean mShouldUseWallpaperColors;
    private final AtomicBoolean mRequestPending = new AtomicBoolean(false);

    private WallpaperColors mWallpaperColors;
    private boolean mHideBottomRow;
    private boolean mIsWallpaperColorsReady;
    private Surface mLastSurface;
    private Message mCallback;
    private Message mDelayedMessage;
    private WorkspaceRenderListener mListener;

    private boolean mNeedsToCleanUp;
    @Nullable private final Bundle mExtras;

    private int mWidth = -1;

    private int mHeight = -1;

    public WorkspaceSurfaceHolderCallback(
            SurfaceView workspaceSurface,
            PreviewUtils previewUtils) {
        this(workspaceSurface, previewUtils, false, null);
    }

    /**
     * Creates a new instance of {@link WorkspaceSurfaceHolderCallback} specifying if wallpaper
     * colors should be used to preview the workspace.
     *
     * @param shouldUseWallpaperColors if true, the workspace preview won't be requested until both
     *                                 the surface is created and wallpaper colors are set via
     *                                 {@link #setWallpaperColors(WallpaperColors)}
     */
    public WorkspaceSurfaceHolderCallback(
            SurfaceView workspaceSurface,
            PreviewUtils previewUtils,
            boolean shouldUseWallpaperColors) {
        this(
                workspaceSurface,
                previewUtils,
                shouldUseWallpaperColors,
                null);
    }

    public WorkspaceSurfaceHolderCallback(
            SurfaceView workspaceSurface,
            PreviewUtils previewUtils,
            @Nullable Bundle extras) {
        this(workspaceSurface, previewUtils, false, extras);
    }

    private WorkspaceSurfaceHolderCallback(
            SurfaceView workspaceSurface,
            PreviewUtils previewUtils,
            boolean shouldUseWallpaperColors,
            @Nullable Bundle extras) {
        mWorkspaceSurface = workspaceSurface;
        mPreviewUtils = previewUtils;
        mShouldUseWallpaperColors = shouldUseWallpaperColors;
        mExtras = extras;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mPreviewUtils.supportsPreview() && mLastSurface != holder.getSurface()) {
            mLastSurface = holder.getSurface();
            maybeRenderPreview();
        }
    }

    /**
     * Set the current wallpaper's colors. This method must be called  if this instance was created
     * with shouldUseWallpaperColors = true (even with {@code null} colors), and conversely, calling
     * this method when {@code shouldUseWallpaperColors = false} will be a no-op.
     *
     * @param colors WallpaperColors extracted from the current wallpaper preview, or {@code null}
     *               if none are available.
     * @see #WorkspaceSurfaceHolderCallback(SurfaceView, PreviewUtils, boolean)
     */
    public void setWallpaperColors(@Nullable WallpaperColors colors) {
        if (!mShouldUseWallpaperColors) {
            return;
        }
        mWallpaperColors = colors;
        mIsWallpaperColorsReady = true;
    }

    /**
     * Set the current flag if we should hide the workspace bottom row.
     */
    public void setHideBottomRow(boolean hideBottomRow) {
        mHideBottomRow = hideBottomRow;
    }

    public void setListener(WorkspaceRenderListener listener) {
        mListener = listener;
    }

    /**
     * Render the preview with the current selected {@link #mWallpaperColors} and
     * {@link #mHideBottomRow}.
     */
    public void maybeRenderPreview() {
        if ((mShouldUseWallpaperColors && !mIsWallpaperColorsReady) || mLastSurface == null) {
            return;
        }
        mRequestPending.set(true);
        requestPreview(mWorkspaceSurface, (result) -> {
            mRequestPending.set(false);
            if (result != null && mLastSurface != null) {
                mWorkspaceSurface.setChildSurfacePackage(
                        SurfaceViewUtils.getSurfacePackage(result));
                mCallback = SurfaceViewUtils.getCallback(result);
                if (mCallback != null && mDelayedMessage != null) {
                    try {
                        mCallback.replyTo.send(mDelayedMessage);
                    } catch (RemoteException e) {
                        Log.w(TAG, "Couldn't send message to workspace preview", e);
                    }
                    mDelayedMessage = null;
                }
                if (mNeedsToCleanUp) {
                    cleanUp();
                } else if (mListener != null) {
                    mListener.onWorkspaceRendered();
                }
            }
        });
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if ((mWidth != -1 || mHeight != -1) && (mWidth != width || mHeight != height)) {
            maybeRenderPreview();
        }
        mWidth = width;
        mHeight = height;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    /**
     * Sends a message to the remote renderer.
     *
     * @param what An ID for the message (the remote side can pick this up through
     * {@link Message#what}.
     * @param bundle The data of the message (the remote side can pick this up through
     * {@link Message#getData()}.
     */
    public void send(final int what, @Nullable Bundle bundle) {
        final Message message = new Message();
        message.what = what;
        message.setData(bundle);
        if (mCallback != null) {
            try {
                mCallback.replyTo.send(message);
            } catch (RemoteException e) {
                Log.w(TAG, "Couldn't send message to workspace preview", e);
            }
        } else {
            mDelayedMessage = message;
        }
    }

    public void cleanUp() {
        if (mCallback != null) {
            try {
                mCallback.replyTo.send(mCallback);
                mNeedsToCleanUp = false;
            } catch (RemoteException e) {
                Log.w(TAG, "Couldn't call cleanup on workspace preview", e);
            } finally {
                mCallback = null;
            }
        } else {
            if (mRequestPending.get()) {
                mNeedsToCleanUp = true;
            }
        }
    }

    public void resetLastSurface() {
        mLastSurface = null;
    }

    protected void requestPreview(SurfaceView workspaceSurface,
            PreviewUtils.WorkspacePreviewCallback callback) {
        if (workspaceSurface.getDisplay() == null) {
            Log.w(TAG,
                    "No display ID, avoiding asking for workspace preview, lest WallpaperPicker "
                            + "crash");
            return;
        }
        Bundle request = SurfaceViewUtils.createSurfaceViewRequest(workspaceSurface, mExtras);
        if (mWallpaperColors != null) {
            request.putParcelable(KEY_WALLPAPER_COLORS, mWallpaperColors);
        }
        request.putBoolean(KEY_HIDE_BOTTOM_ROW, mHideBottomRow);
        mPreviewUtils.renderPreview(request, callback);
    }
}
