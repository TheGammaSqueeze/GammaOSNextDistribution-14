/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.volume;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.util.SparseArray;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.plugins.VolumeDialog;

import java.util.ArrayList;

/**
 * GammaOS: A {@link VolumeDialog} implementation that can host one {@link VolumeDialogImpl}
 * instance per physical display.
 *
 * <p>When {@link GammaMultiVolumeStore#isMultiVolumeEnabled()} is false, this behaves like the
 * default implementation by only creating the dialog on the default display.</p>
 */
public final class GammaMultiDisplayVolumeDialog implements VolumeDialog {

    /** Factory used to create per-display {@link VolumeDialogImpl} instances. */
    public interface DialogFactory {
        @NonNull
        VolumeDialogImpl create(@NonNull Context displayContext);
    }

    private final Context mContext;
    private final DisplayManager mDisplayManager;
    private final DialogFactory mFactory;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    // displayId -> dialog
    private final SparseArray<VolumeDialogImpl> mDialogs = new SparseArray<>();

    private int mWindowType;
    @Nullable
    private Callback mCallback;
    private boolean mInited;

    private final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                    refreshDialogs();
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                    refreshDialogs();
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    // Ignore.
                }
            };

    public GammaMultiDisplayVolumeDialog(@NonNull Context context, @NonNull DialogFactory factory) {
        mContext = context;
        mDisplayManager = context.getSystemService(DisplayManager.class);
        SystemProperties.addChangeCallback(() ->
                mMainHandler.post(this::refreshDialogs));
        mFactory = factory;
    }

    @Override
    public void init(int windowType, @NonNull Callback callback) {
        mWindowType = windowType;
        mCallback = callback;
        mInited = true;

        if (mDisplayManager != null) {
            mDisplayManager.registerDisplayListener(mDisplayListener, mMainHandler);
        }

        refreshDialogs();
    }

    @Override
    public void destroy() {
        if (mDisplayManager != null) {
            try {
                mDisplayManager.unregisterDisplayListener(mDisplayListener);
            } catch (IllegalArgumentException e) {
                // Ignore.
            }
        }
        for (int i = 0; i < mDialogs.size(); i++) {
            mDialogs.valueAt(i).destroy();
        }
        mDialogs.clear();
        mInited = false;
        mCallback = null;
    }

    private void refreshDialogs() {
        if (!mInited || mCallback == null) {
            return;
        }

        final boolean multiEnabled = GammaMultiVolumeStore.isMultiVolumeEnabled();

        final ArrayList<Integer> desiredDisplays = new ArrayList<>();
        if (multiEnabled && mDisplayManager != null) {
            for (Display d : mDisplayManager.getDisplays()) {
                if (shouldShowOnDisplay(d)) {
                    desiredDisplays.add(d.getDisplayId());
                }
            }
        }
        if (desiredDisplays.isEmpty()) {
            desiredDisplays.add(Display.DEFAULT_DISPLAY);
        }

        // Remove dialogs that are no longer desired.
        for (int i = mDialogs.size() - 1; i >= 0; i--) {
            final int displayId = mDialogs.keyAt(i);
            if (!desiredDisplays.contains(displayId)) {
                final VolumeDialogImpl dialog = mDialogs.valueAt(i);
                dialog.destroy();
                mDialogs.removeAt(i);
            }
        }

        // Add missing dialogs.
        for (int i = 0; i < desiredDisplays.size(); i++) {
            final int displayId = desiredDisplays.get(i);
            if (mDialogs.get(displayId) != null) {
                continue;
            }
            final Context displayContext = createDisplayContext(displayId);
            final VolumeDialogImpl dialog = mFactory.create(displayContext);
            dialog.init(mWindowType, mCallback);
            mDialogs.put(displayId, dialog);
        }
    }

    private boolean shouldShowOnDisplay(@Nullable Display display) {
        if (display == null) {
            return false;
        }
        final int id = display.getDisplayId();
        if (id == Display.INVALID_DISPLAY) {
            return false;
        }
        if (id == Display.DEFAULT_DISPLAY) {
            return true;
        }
        final int flags = display.getFlags();
        return (flags & Display.FLAG_PRESENTATION) != 0
                || (flags & Display.FLAG_TRUSTED) != 0;
    }

    @NonNull
    private Context createDisplayContext(int displayId) {
        if (mDisplayManager == null || displayId == Display.DEFAULT_DISPLAY) {
            return mContext;
        }
        final Display display = mDisplayManager.getDisplay(displayId);
        if (display == null) {
            return mContext;
        }
        return mContext.createDisplayContext(display);
    }
}
