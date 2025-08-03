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

package android.car.builtin.view;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.annotation.UiThread;
import android.car.builtin.util.Slogf;
import android.graphics.Rect;
import android.graphics.Region;
import android.view.View;
import android.view.ViewTreeObserver.InternalInsetsInfo;
import android.view.ViewTreeObserver.OnComputeInternalInsetsListener;

/**
 * Calculates {@link InternalInsetsInfo#TOUCHABLE_INSETS_REGION} for the given {@link View}.
 * <p>The touch events on the View will pass through the host and be delivered to the window
 * below it.
 *
 * <p>It also provides the api {@link #setObscuredTouchRegion(Region)} to specify the region which
 * the view host can accept the touch events on it.
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
@UiThread
public final class TouchableInsetsProvider {
    private static final String TAG = TouchableInsetsProvider.class.getSimpleName();
    private final View mView;
    private final OnComputeInternalInsetsListener mListener = this::onComputeInternalInsets;
    private final int[] mLocation = new int[2];
    private final Rect mRect = new Rect();

    @Nullable private Region mObscuredTouchRegion;

    public TouchableInsetsProvider(@NonNull View view) {
        mView = view;
    }

    /**
     * Specifies the region of the view which the view host can accept the touch events.
     *
     * @param obscuredRegion the obscured region of the view.
     */
    public void setObscuredTouchRegion(@Nullable Region obscuredRegion) {
        mObscuredTouchRegion = obscuredRegion;
    }

    private void onComputeInternalInsets(InternalInsetsInfo inoutInfo) {
        if (!mView.isVisibleToUser()) {
            Slogf.d(TAG, "Skip onComputeInternalInsets since the view is invisible");
            return;
        }
        if (inoutInfo.touchableRegion.isEmpty()) {
            // This is the first View to set touchableRegion, then set the entire Window as
            // touchableRegion first, then subtract each View's region from it.
            inoutInfo.setTouchableInsets(InternalInsetsInfo.TOUCHABLE_INSETS_REGION);
            View root = mView.getRootView();
            root.getLocationInWindow(mLocation);
            mRect.set(mLocation[0], mLocation[1],
                    mLocation[0] + root.getWidth(), mLocation[1] + root.getHeight());
            inoutInfo.touchableRegion.set(mRect);
        }
        mView.getLocationInWindow(mLocation);
        mRect.set(mLocation[0], mLocation[1],
                mLocation[0] + mView.getWidth(), mLocation[1] + mView.getHeight());
        inoutInfo.touchableRegion.op(mRect, Region.Op.DIFFERENCE);

        if (mObscuredTouchRegion != null) {
            inoutInfo.touchableRegion.op(mObscuredTouchRegion, Region.Op.UNION);
        }
    };

    /** Registers this to the internal insets computation callback. */
    public void addToViewTreeObserver() {
        mView.getViewTreeObserver().addOnComputeInternalInsetsListener(mListener);
    }

    /** Removes this from the internal insets computation callback. */
    public void removeFromViewTreeObserver() {
        mView.getViewTreeObserver().removeOnComputeInternalInsetsListener(mListener);
    }

    @Override
    public String toString() {
        return TAG + "(rect=" + mRect + ", obscuredTouch=" + mObscuredTouchRegion + ")";
    }
}

