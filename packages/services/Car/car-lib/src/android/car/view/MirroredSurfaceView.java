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

package android.car.view;

import android.annotation.MainThread;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.app.Activity;
import android.car.Car;
import android.car.app.CarActivityManager;
import android.car.builtin.util.Slogf;
import android.car.builtin.view.TouchableInsetsProvider;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.IBinder;
import android.util.AttributeSet;
import android.util.Dumpable;
import android.util.Log;
import android.util.Pair;
import android.util.Slog;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;

/**
 * {@link SurfaceView} which can render the {@link Surface} of mirrored Task.
 *
 * @hide
 */
@SystemApi
@SuppressWarnings("[NotCloseable]") // View object won't be used in try-with-resources statement.
public final class MirroredSurfaceView extends SurfaceView {
    private static final String TAG = MirroredSurfaceView.class.getSimpleName();
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    private final SurfaceControl.Transaction mTransaction;
    private final TouchableInsetsProvider mTouchableInsetsProvider;

    private SurfaceControl mMirroredSurface;
    private Rect mSourceBounds;
    private CarActivityManager mCarAM;

    public MirroredSurfaceView(@NonNull Context context) {
        this(context, /* attrs= */ null);
    }

    public MirroredSurfaceView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, /* defStyle= */ 0);
    }

    public MirroredSurfaceView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyle) {
        this(context, attrs, defStyle, /* defStyleRes= */ 0);
    }

    public MirroredSurfaceView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        this(context, attrs, defStyleAttr, defStyleRes,
                new SurfaceControl.Transaction(), /* touchableInsetsProvider= */ null);
    }

    @VisibleForTesting
    MirroredSurfaceView(@NonNull Context context, @Nullable AttributeSet attrs,
                        int defStyleAttr, int defStyleRes,
                        SurfaceControl.Transaction transaction,
                        TouchableInsetsProvider touchableInsetsProvider) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mTransaction = transaction;
        mTouchableInsetsProvider = touchableInsetsProvider != null
                ? touchableInsetsProvider : new TouchableInsetsProvider(this);

        getHolder().addCallback(mSurfaceCallback);
        if (context instanceof Activity) {
            ((Activity) context).addDumpable(mDumper);
        }

        Car.createCar(/* context= */ context, /* handler= */ null,
                Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                (car, ready) -> {
                    if (!ready) {
                        Log.w(TAG, "CarService looks crashed");
                        mCarAM = null;
                        return;
                    }
                    mCarAM = car.getCarManager(CarActivityManager.class);
                });
    }

    /**
     * Attaches the mirrored Surface which is represented by the given token to this View.
     * <p>
     * <b>Note:</b> MirroredSurfaceView will hold the Surface unless you call {@link #release()}
     * explicitly. This is so that the host can keep the Surface when {@link Activity#onStop()} and
     * {@link Activity#onStart()} are called again.
     *
     * @param token A token to access the Task Surface to mirror.
     * @return true if the operation is successful.
     */
    @RequiresPermission(Car.PERMISSION_ACCESS_MIRRORRED_SURFACE)
    @MainThread
    public boolean mirrorSurface(@NonNull IBinder token) {
        if (mCarAM == null) {
            Slogf.e(TAG, "Failed to mirrorSurface because CarService isn't ready yet");
            return false;
        }
        if (mMirroredSurface != null) {
            removeMirroredSurface();
        }
        Pair<SurfaceControl, Rect> mirroredSurfaceInfo = mCarAM.getMirroredSurface(token);
        if (mirroredSurfaceInfo == null) {
            Slogf.e(TAG, "Failed to getMirroredSurface: token=%s", token);
            return false;
        }
        mMirroredSurface = mirroredSurfaceInfo.first;
        mSourceBounds = mirroredSurfaceInfo.second;
        if (getHolder() == null) {
            // reparentMirroredSurface() will happen when the SurfaceHolder is created.
            if (DBG) Slog.d(TAG, "mirrorSurface: Surface is not ready");
            return true;
        }
        reparentMirroredSurface();
        return true;
    }

    /**
     * Indicates a region of the view that is not touchable.
     *
     * @param obscuredRegion the obscured region of the view.
     */
    @MainThread
    public void setObscuredTouchRegion(@Nullable Region obscuredRegion) {
        mTouchableInsetsProvider.setObscuredTouchRegion(obscuredRegion);
    }

    /**
     * Releases {@link MirroredSurfaceView} and associated {@link Surface}.
     */
    @MainThread
    public void release() {
        getHolder().removeCallback(mSurfaceCallback);
        removeMirroredSurface();
    }

    @Override
    protected void finalize() throws Throwable {
        if (mMirroredSurface != null) {
            removeMirroredSurface();
        }
        super.finalize();
    }

    private void reparentMirroredSurface() {
        if (DBG) Slog.d(TAG, "reparentMirroredSurface");
        calculateScale();
        mTransaction.setVisibility(mMirroredSurface, /* visible= */true)
                .reparent(mMirroredSurface, getSurfaceControl())
                .apply();
    }

    private void removeMirroredSurface() {
        if (mMirroredSurface == null) {
            Slog.w(TAG, "Skip removeMirroredSurface() on null Surface.");
            return;
        }
        mTransaction.reparent(mMirroredSurface, null).apply();
        mMirroredSurface.release();
        mMirroredSurface = null;
    }

    private void calculateScale() {
        if (mMirroredSurface == null) {
            Slog.i(TAG, "Skip calculateScale() since MirroredSurface is not attached");
            return;
        }
        if (getWidth() == 0 || getHeight() == 0) {
            Slog.i(TAG, "Skip calculateScale() since the View is not inflated.");
            return;
        }
        // scale: > 1.0 Zoom out, < 1.0 Zoom in
        float horizontalScale = (float) mSourceBounds.width() / getWidth();
        float verticalScale = (float) mSourceBounds.height() / getHeight();
        float mirroringScale = Math.max(horizontalScale, verticalScale);

        int width = (int) Math.ceil(mSourceBounds.width() / mirroringScale);
        int height = (int) Math.ceil(mSourceBounds.height() / mirroringScale);
        Rect destBounds = new Rect(0, 0, width, height);

        if (DBG) Slogf.d(TAG, "calculateScale: scale=%f", mirroringScale);
        mTransaction.setGeometry(mMirroredSurface, mSourceBounds, destBounds, Surface.ROTATION_0)
                .apply();
    }

    private final SurfaceHolder.Callback mSurfaceCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            if (mMirroredSurface == null) {
                // reparentMirroredSurface() will happen when mirrorSurface() is called.
                if (DBG) {
                    Slog.d(TAG, "surfaceCreated: skip reparenting"
                            + " because the mirrored Surface isn't ready.");
                }
                return;
            }
            reparentMirroredSurface();
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format,
                int width, int height) {
            calculateScale();
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            // Don't remove mMirroredSurface autonomously, because it may not get it again
            // after some timeout. So the host Activity needs to keep it for the next onStart event.
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mTouchableInsetsProvider.addToViewTreeObserver();
    }

    @Override
    protected void onDetachedFromWindow() {
        mTouchableInsetsProvider.removeFromViewTreeObserver();
        super.onDetachedFromWindow();
    }

    private final Dumpable mDumper = new Dumpable() {
        private static final String INDENTATION = "  ";
        @NonNull
        @Override
        public String getDumpableName() {
            return TAG;
        }

        @Override
        public void dump(@NonNull PrintWriter writer, @Nullable String[] args) {
            writer.println(TAG + ": id=#" + Integer.toHexString(getId()));
            writer.println(INDENTATION + "mirroredSurface=" + mMirroredSurface);
            writer.println(INDENTATION + "sourceBound=" + mSourceBounds);
            writer.println(INDENTATION + "touchableInsetsProvider=" + mTouchableInsetsProvider);
        }
    };
}
