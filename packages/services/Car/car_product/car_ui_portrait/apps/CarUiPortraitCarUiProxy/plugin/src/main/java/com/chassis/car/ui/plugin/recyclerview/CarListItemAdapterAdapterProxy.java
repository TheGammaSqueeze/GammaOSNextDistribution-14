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

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.ui.plugin.oemapis.recyclerview.AdapterDataObserverOEMV1;
import com.android.car.ui.plugin.oemapis.recyclerview.AdapterOEMV1;
import com.android.car.ui.plugin.oemapis.recyclerview.RecyclerViewOEMV1;
import com.android.car.ui.plugin.oemapis.recyclerview.ViewHolderOEMV1;
import com.android.car.ui.recyclerview.CarUiListItemAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapts a {@link CarUiListItemAdapter} into a {@link AdapterOEMV1}
 */
public class CarListItemAdapterAdapterProxy implements
        AdapterOEMV1<CarListItemAdapterAdapterProxy.ViewHolderWrapper> {

    private final CarUiListItemAdapter mDelegateAdapter;
    private final Context mPluginContext;


    public CarListItemAdapterAdapterProxy(CarUiListItemAdapter carUiListItemAdapter,
            Context pluginContext) {
        mPluginContext = pluginContext;
        mDelegateAdapter = carUiListItemAdapter;
    }

    @Override
    public int getItemCount() {
        return mDelegateAdapter.getItemCount();
    }

    @Override
    public long getItemId(int position) {
        return mDelegateAdapter.getItemId(position);
    }

    @Override
    public int getItemViewType(int position) {
        return mDelegateAdapter.getItemViewType(position);
    }

    @Override
    public int getStateRestorationPolicyInt() {
        return mDelegateAdapter.getStateRestorationPolicy().ordinal();
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerViewOEMV1 recyclerViewOEMV1) {
        //TODO: CarUiListItemAdapterAdapterV1 passes a null here. Is there a better path
        // are we wanting clients to call these methods
        mDelegateAdapter.onAttachedToRecyclerView(null);
    }

    @Override
    public void onDetachedFromRecyclerView(RecyclerViewOEMV1 recyclerViewOEMV1) {
        //TODO: CarUiListItemAdapterAdapterV1 passes a null here. Is there a better path
        // are we wanting clients to call these methods
        mDelegateAdapter.onDetachedFromRecyclerView(null);
    }

    @Override
    public void bindViewHolder(ViewHolderWrapper viewHolderWrapper, int position) {
        mDelegateAdapter.onBindViewHolder(viewHolderWrapper.getViewHolder(), position);
    }

    @Override
    public ViewHolderWrapper createViewHolder(ViewGroup viewGroup, int viewType) {
        // Fake parent need for the correct context
        FrameLayout fakeParent = new FrameLayout(mPluginContext);
        return new ViewHolderWrapper(mDelegateAdapter.createViewHolder(fakeParent, viewType));
    }

    @Override
    public boolean onFailedToRecycleView(ViewHolderWrapper viewHolderWrapper) {
        return mDelegateAdapter.onFailedToRecycleView(viewHolderWrapper.getViewHolder());
    }

    @Override
    public void onViewAttachedToWindow(ViewHolderWrapper viewHolderWrapper) {
        mDelegateAdapter.onViewAttachedToWindow(viewHolderWrapper.getViewHolder());
    }

    @Override
    public void onViewDetachedFromWindow(ViewHolderWrapper viewHolderWrapper) {
        mDelegateAdapter.onViewDetachedFromWindow(viewHolderWrapper.getViewHolder());
    }

    @Override
    public void onViewRecycled(ViewHolderWrapper viewHolderWrapper) {
        mDelegateAdapter.onViewRecycled(viewHolderWrapper.getViewHolder());
    }


    @Override
    public void registerAdapterDataObserver(AdapterDataObserverOEMV1 observer) {
        if (observer == null) {
            return;
        }
        mAdapterDataObservers.add(observer);
        if (!mDelegateAdapter.hasObservers()) {
            mDelegateAdapter.registerAdapterDataObserver(mAdapterDataObserver);
        }
    }

    @Override
    public void unregisterAdapterDataObserver(AdapterDataObserverOEMV1 observer) {
        if (observer == null) {
            return;
        }
        mAdapterDataObservers.remove(observer);
        if (mAdapterDataObservers.isEmpty()) {
            mDelegateAdapter.unregisterAdapterDataObserver(mAdapterDataObserver);
        }
    }

    @Override
    public boolean hasStableIds() {
        return mDelegateAdapter.hasStableIds();
    }


    @Override
    public void setMaxItems(int i) {
        mDelegateAdapter.setMaxItems(i);
    }


    @NonNull
    private final List<AdapterDataObserverOEMV1> mAdapterDataObservers = new ArrayList<>();
    private final RecyclerView.AdapterDataObserver mAdapterDataObserver =
            new RecyclerView.AdapterDataObserver() {
                @Override
                public void onChanged() {
                    for (AdapterDataObserverOEMV1 observer : mAdapterDataObservers) {
                        observer.onChanged();
                    }
                }

                @Override
                public void onItemRangeChanged(int positionStart, int itemCount) {
                    for (AdapterDataObserverOEMV1 observer : mAdapterDataObservers) {
                        observer.onItemRangeChanged(positionStart, itemCount);
                    }
                }

                @Override
                public void onItemRangeChanged(int positionStart, int itemCount,
                        @Nullable Object payload) {
                    for (AdapterDataObserverOEMV1 observer : mAdapterDataObservers) {
                        observer.onItemRangeChanged(positionStart, itemCount, payload);
                    }
                }

                @Override
                public void onItemRangeInserted(int positionStart, int itemCount) {
                    for (AdapterDataObserverOEMV1 observer : mAdapterDataObservers) {
                        observer.onItemRangeInserted(positionStart, itemCount);
                    }
                }

                @Override
                public void onItemRangeRemoved(int positionStart, int itemCount) {
                    for (AdapterDataObserverOEMV1 observer : mAdapterDataObservers) {
                        observer.onItemRangeRemoved(positionStart, itemCount);
                    }
                }

                @Override
                public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                    for (AdapterDataObserverOEMV1 observer : mAdapterDataObservers) {
                        for (int i = 0; i < itemCount; i++) {
                            observer.onItemMoved(fromPosition + i, toPosition + i);
                        }
                    }
                }

                @Override
                public void onStateRestorationPolicyChanged() {
                    for (AdapterDataObserverOEMV1 observer : mAdapterDataObservers) {
                        observer.onStateRestorationPolicyChanged();
                    }
                }
            };

    /**
     * Holds views for each element in the list.
     */
    public static class ViewHolderWrapper implements ViewHolderOEMV1 {
        @NonNull
        private final RecyclerView.ViewHolder mViewHolder;

        ViewHolderWrapper(@NonNull RecyclerView.ViewHolder viewHolder) {
            mViewHolder = viewHolder;
        }

        @NonNull
        public RecyclerView.ViewHolder getViewHolder() {
            return mViewHolder;
        }

        @Override
        public boolean isRecyclable() {
            return mViewHolder.isRecyclable();
        }

        @Override
        public View getItemView() {
            return mViewHolder.itemView;
        }
    }
}
