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

package com.chassis.car.ui.plugin.recyclerview;

import static androidx.recyclerview.widget.RecyclerView.VERTICAL;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.OnChildAttachStateChangeListener;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.car.ui.plugin.oemapis.recyclerview.AdapterOEMV1;
import com.android.car.ui.plugin.oemapis.recyclerview.LayoutStyleOEMV1;
import com.android.car.ui.plugin.oemapis.recyclerview.OnChildAttachStateChangeListenerOEMV1;
import com.android.car.ui.plugin.oemapis.recyclerview.RecyclerViewAttributesOEMV1;
import com.android.car.ui.plugin.oemapis.recyclerview.RecyclerViewOEMV2;
import com.android.car.ui.plugin.oemapis.recyclerview.ViewHolderOEMV1;
import com.android.car.ui.recyclerview.CarUiRecyclerView;
import com.android.car.ui.recyclerview.CarUiRecyclerViewImpl;
import com.android.car.ui.recyclerview.RecyclerViewAdapterV1;

import java.util.ArrayList;
import java.util.List;

/**
 *  Adapts a {@link RecyclerViewAdapterV1} into a {@link RecyclerViewOEMV2}.
 */
public class RecyclerViewAdapterProxyV2 implements RecyclerViewOEMV2 {
    private Context mPluginContext;
    private CarUiRecyclerViewImpl mRecyclerView;

    public RecyclerViewAdapterProxyV2(Context pluginContext, CarUiRecyclerViewImpl recyclerView,
            RecyclerViewAttributesOEMV1 recyclerViewAttributesOEMV1) {
        mPluginContext = pluginContext;
        mRecyclerView = recyclerView;
        setLayoutStyle(recyclerViewAttributesOEMV1.getLayoutStyle());
    }

    @NonNull
    private final List<OnScrollListenerOEMV2> mScrollListeners = new ArrayList<>();

    @NonNull
    private final CarUiRecyclerView.OnScrollListener mOnScrollListener =
            new CarUiRecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull CarUiRecyclerView recyclerView, int dx, int dy) {
                    for (OnScrollListenerOEMV2 listener : mScrollListeners) {
                        listener.onScrolled(RecyclerViewAdapterProxyV2.this, dx, dy);
                    }
                }

                @Override
                public void onScrollStateChanged(@NonNull CarUiRecyclerView recyclerView,
                        int newState) {
                    for (OnScrollListenerOEMV2 listener : mScrollListeners) {
                        listener.onScrollStateChanged(RecyclerViewAdapterProxyV2.this,
                                toInternalScrollState(newState));
                    }
                }
            };
    @NonNull
    private final List<OnChildAttachStateChangeListenerOEMV1> mOnChildAttachStateChangeListeners =
            new ArrayList<>();

    @NonNull
    private final OnChildAttachStateChangeListener mOnChildAttachStateChangeListener =
            new OnChildAttachStateChangeListener() {
                @Override
                public void onChildViewAttachedToWindow(@NonNull View view) {
                    for (OnChildAttachStateChangeListenerOEMV1 listener :
                            mOnChildAttachStateChangeListeners) {
                        listener.onChildViewAttachedToWindow(view);
                    }
                }

                @Override
                public void onChildViewDetachedFromWindow(@NonNull View view) {
                    for (OnChildAttachStateChangeListenerOEMV1 listener :
                            mOnChildAttachStateChangeListeners) {
                        listener.onChildViewDetachedFromWindow(view);
                    }
                }
            };

    private static int toInternalScrollState(int state) {
        /* default to RecyclerView.SCROLL_STATE_IDLE */
        int internalState = RecyclerViewOEMV2.SCROLL_STATE_IDLE;
        switch (state) {
            case RecyclerView.SCROLL_STATE_DRAGGING:
                internalState = RecyclerViewOEMV2.SCROLL_STATE_DRAGGING;
                break;
            case RecyclerView.SCROLL_STATE_SETTLING:
                internalState = RecyclerViewOEMV2.SCROLL_STATE_SETTLING;
                break;
        }
        return internalState;
    }


    @Override
    public <V extends ViewHolderOEMV1> void setAdapter(AdapterOEMV1<V> adapterOEMV1) {
        if (adapterOEMV1 == null) {
            mRecyclerView.setAdapter(null);
        } else {
            mRecyclerView.setAdapter(new RVAdapterWrapper(adapterOEMV1));
        }
    }

    @Override
    public void addOnScrollListener(@NonNull OnScrollListenerOEMV2 onScrollListenerOEMV2) {
        if (mScrollListeners.isEmpty()) {
            mRecyclerView.addOnScrollListener(mOnScrollListener);
        }
        mScrollListeners.add(onScrollListenerOEMV2);
    }

    @Override
    public void removeOnScrollListener(@NonNull OnScrollListenerOEMV2 onScrollListenerOEMV2) {
        mScrollListeners.remove(onScrollListenerOEMV2);
        if (mScrollListeners.isEmpty()) {
            mRecyclerView.removeOnScrollListener(mOnScrollListener);
        }
    }

    @Override
    public void clearOnScrollListeners() {
        if (!mScrollListeners.isEmpty()) {
            mScrollListeners.clear();
            mRecyclerView.clearOnScrollListeners();
        }
    }

    @Override
    public void scrollToPosition(int i) {
        mRecyclerView.scrollToPosition(i);
    }

    @Override
    public void smoothScrollBy(int i, int i1) {
        mRecyclerView.smoothScrollBy(i, i1);
    }

    @Override
    public void smoothScrollToPosition(int i) {
        mRecyclerView.smoothScrollToPosition(i);
    }

    @Override
    public void setHasFixedSize(boolean b) {
        mRecyclerView.setHasFixedSize(b);
    }

    @Override
    public boolean hasFixedSize() {
        return mRecyclerView.hasFixedSize();
    }

    @Nullable
    private LayoutStyleOEMV1 mLayoutStyle;
    @Override
    public void setLayoutStyle(LayoutStyleOEMV1 layoutStyleOEMV1) {
        mLayoutStyle = layoutStyleOEMV1;

        int orientation = layoutStyleOEMV1 == null ? VERTICAL : layoutStyleOEMV1.getOrientation();
        boolean reverseLayout = layoutStyleOEMV1 != null && layoutStyleOEMV1.getReverseLayout();

        if (layoutStyleOEMV1 == null
                || layoutStyleOEMV1.getLayoutType() == LayoutStyleOEMV1.LAYOUT_TYPE_LINEAR) {
            mRecyclerView.setLayoutManager(new LinearLayoutManager(mPluginContext,
                    orientation,
                    reverseLayout));
        } else {
            GridLayoutManager glm = new GridLayoutManager(mPluginContext,
                    layoutStyleOEMV1.getSpanCount(),
                    orientation,
                    reverseLayout);
            glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    return layoutStyleOEMV1.getSpanSize(position);
                }
            });
            mRecyclerView.setLayoutManager(glm);
        }
    }

    @Override
    public LayoutStyleOEMV1 getLayoutStyle() {
        return mLayoutStyle;
    }

    @Override
    public View getView() {
        return mRecyclerView.getView();
    }

    @Override
    public void setPadding(int i, int i1, int i2, int i3) {
        mRecyclerView.setPadding(i, i1, i2, i3);
    }

    @Override
    public void setPaddingRelative(int i, int i1, int i2, int i3) {
        mRecyclerView.setPaddingRelative(i, i1, i2, i3);
    }

    @Override
    public void setClipToPadding(boolean b) {
        mRecyclerView.setClipToPadding(b);
    }

    @Override
    public int findFirstCompletelyVisibleItemPosition() {
        return mRecyclerView.findFirstCompletelyVisibleItemPosition();
    }

    @Override
    public int findFirstVisibleItemPosition() {
        return mRecyclerView.findFirstVisibleItemPosition();
    }

    @Override
    public int findLastCompletelyVisibleItemPosition() {
        return mRecyclerView.findLastVisibleItemPosition();
    }

    @Override
    public int findLastVisibleItemPosition() {
        return mRecyclerView.findLastVisibleItemPosition();
    }

    @Override
    public int getScrollState() {
        return toInternalScrollState(mRecyclerView.getScrollState());
    }

    @Override
    public void setContentDescription(CharSequence charSequence) {
        mRecyclerView.setContentDescription(charSequence);
    }

    @Override
    public void setAlpha(float v) {
        mRecyclerView.setAlpha(v);
    }

    @Override
    public int getEndAfterPadding() {
        return mRecyclerView.getEndAfterPadding();
    }

    @Override
    public int getStartAfterPadding() {
        return mRecyclerView.getStartAfterPadding();
    }

    @Override
    public int getTotalSpace() {
        return mRecyclerView.getTotalSpace();
    }

    @Override
    public int getRecyclerViewChildCount() {
        return mRecyclerView.getRecyclerViewChildCount();
    }

    @Override
    public View getRecyclerViewChildAt(int i) {
        return mRecyclerView.getRecyclerViewChildAt(i);
    }

    @Override
    public int getRecyclerViewChildPosition(View view) {
        return mRecyclerView.getRecyclerViewChildPosition(view);
    }

    @Override
    public ViewHolderOEMV1 findViewHolderForAdapterPosition(int position) {
        ViewHolder viewHolder = mRecyclerView.findViewHolderForAdapterPosition(position);
        if (viewHolder instanceof RVAdapterWrapper.ViewHolderWrapper) {
            return ((RVAdapterWrapper.ViewHolderWrapper) viewHolder).getViewHolder();
        }
        return null;
    }

    @Override
    public ViewHolderOEMV1 findViewHolderForLayoutPosition(int position) {
        ViewHolder viewHolder = mRecyclerView.findViewHolderForLayoutPosition(position);
        if (viewHolder instanceof RVAdapterWrapper.ViewHolderWrapper) {
            return ((RVAdapterWrapper.ViewHolderWrapper) viewHolder).getViewHolder();
        }
        return null;
    }

    @Override
    public void addOnChildAttachStateChangeListener(
            OnChildAttachStateChangeListenerOEMV1 listener) {
        if (listener == null) {
            return;
        }
        if (mOnChildAttachStateChangeListeners.isEmpty()) {
            mRecyclerView.addOnChildAttachStateChangeListener(mOnChildAttachStateChangeListener);
        }
        mOnChildAttachStateChangeListeners.add(listener);
    }

    @Override
    public void removeOnChildAttachStateChangeListener(
            OnChildAttachStateChangeListenerOEMV1 listener) {
        if (listener == null) {
            return;
        }
        mOnChildAttachStateChangeListeners.remove(listener);
        if (mOnChildAttachStateChangeListeners.isEmpty()) {
            mRecyclerView.removeOnChildAttachStateChangeListener(mOnChildAttachStateChangeListener);
        }
    }

    @Override
    public void clearOnChildAttachStateChangeListener() {
        if (!mOnChildAttachStateChangeListeners.isEmpty()) {
            mOnChildAttachStateChangeListeners.clear();
            mRecyclerView.clearOnChildAttachStateChangeListeners();
        }
    }

    @Override
    public int getChildLayoutPosition(View view) {
        return mRecyclerView.getChildLayoutPosition(view);
    }

    @Override
    public int getDecoratedStart(View view) {
        return mRecyclerView.getDecoratedStart(view);
    }

    @Override
    public int getDecoratedEnd(View view) {
        return mRecyclerView.getDecoratedEnd(view);
    }

    @Override
    public int getDecoratedMeasuredHeight(View view) {
        return mRecyclerView.getDecoratedMeasuredHeight(view);
    }

    @Override
    public int getDecoratedMeasuredWidth(View view) {
        return mRecyclerView.getDecoratedMeasuredWidth(view);
    }

    @Override
    public int getDecoratedMeasurementInOther(View view) {
        return mRecyclerView.getDecoratedMeasurementInOther(view);
    }

    @Override
    public int getDecoratedMeasurement(View view) {
        return mRecyclerView.getDecoratedMeasurement(view);
    }

    @Override
    public View findViewByPosition(int i) {
        return mRecyclerView.findViewByPosition(i);
    }

    @Override
    public boolean isComputingLayout() {
        return !mRecyclerView.isLayoutCompleted();
    }

    @Override
    public void addOnLayoutCompleteListener(@Nullable Runnable runnable) {
        mRecyclerView.addOnLayoutCompleteListener(runnable);
    }

    @Override
    public void removeOnLayoutCompleteListener(@Nullable Runnable runnable) {
        mRecyclerView.removeOnLayoutCompleteListener(runnable);
    }

    @Override
    public void scrollToPositionWithOffset(int i, int i1) {
        mRecyclerView.scrollToPositionWithOffset(i, i1);
    }
}
