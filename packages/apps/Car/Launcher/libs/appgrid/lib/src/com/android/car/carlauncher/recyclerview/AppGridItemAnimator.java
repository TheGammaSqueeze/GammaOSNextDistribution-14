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

package com.android.car.carlauncher.recyclerview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewPropertyAnimator;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.car.carlauncher.R;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView.ItemAnimator that animates the dropping of drag shadow onto the new view holder.
 */
public class AppGridItemAnimator extends DefaultItemAnimator {
    private long mDropDuration = 50L;
    private final ArrayList<DropInfo> mPendingDrops = new ArrayList<>();
    private final ArrayList<ViewHolder> mDropAnimations = new ArrayList<>();
    private final ArrayList<ViewHolder> mQueuedMoveAnimations = new ArrayList<>();

    private static class DropInfo {
        public AppItemViewHolder holder;
        DropInfo(AppItemViewHolder holder) {
            this.holder = holder;
        }
    }

    @Override
    public boolean animateMove(ViewHolder holder, int fromX, int fromY,
            int toX, int toY) {
        AppItemViewHolder viewHolder = (AppItemViewHolder) holder;
        if (viewHolder.isMostRecentlySelected()) {
            return animateDrop(viewHolder);
        }
        resetAnimation(viewHolder);
        viewHolder.resetTranslationZ();
        mQueuedMoveAnimations.add(viewHolder);
        return super.animateMove(holder, fromX, fromY, toX, toY);
    }

    @Override
    public void onMoveStarting(ViewHolder holder) {
        AppItemViewHolder viewHolder = (AppItemViewHolder) holder;
        if (mQueuedMoveAnimations.contains(viewHolder)) {
            mQueuedMoveAnimations.remove(viewHolder);
            if (mQueuedMoveAnimations.isEmpty()) {
                Runnable dropper = new Runnable() {
                    @Override
                    public void run() {
                        for (DropInfo dropInfo : mPendingDrops) {
                            animateDropImpl(dropInfo.holder);
                        }
                        mPendingDrops.clear();
                    }
                };
                dropper.run();
            }
        }
    }

    @Override
    public boolean animateAdd(ViewHolder holder) {
        AppItemViewHolder viewHolder = (AppItemViewHolder) holder;
        if (viewHolder.isMostRecentlySelected()) {
            return animateDrop(viewHolder);
        }
        return super.animateAdd(holder);
    }

    /**
     * Called when an item is dropped in the RecyclerView. Implementors can choose
     * whether and how to animate that change, but must always call
     * {@link #dispatchDropFinished(ViewHolder)} when done, either
     * immediately (if no animation will occur) or after the animation actually finishes.
     * The return value indicates whether an animation has been set up and whether the
     * ItemAnimator's {@link #runPendingAnimations()} method should be called at the
     * next opportunity.
     */

    public boolean animateDrop(ViewHolder holder) {
        AppItemViewHolder viewHolder = (AppItemViewHolder) holder;
        resetAnimation(viewHolder);
        viewHolder.prepareForDropAnimation();
        mPendingDrops.add(new DropInfo(viewHolder));
        return true;
    }

    private void animateDropImpl(AppItemViewHolder viewHolder) {
        mDropAnimations.add(viewHolder);
        final ViewPropertyAnimator dropAnimation = viewHolder.getDropAnimation();
        dropAnimation.setDuration(getDropDuration())
                .setStartDelay(viewHolder.itemView.getResources().getInteger(
                        R.integer.ms_drop_animation_delay))
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animator) {
                        dispatchDropStarting(viewHolder);
                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        dropAnimation.setListener(null);
                        mDropAnimations.remove(viewHolder);
                        dispatchDropFinished(viewHolder);
                        if (!isRunning()) {
                            dispatchAnimationsFinished();
                        }
                    }
                }).start();
    }

    @Override
    public boolean isRunning() {
        return (!mPendingDrops.isEmpty()
                || !mQueuedMoveAnimations.isEmpty()
                || !mDropAnimations.isEmpty()
                || super.isRunning());
    }

    /**
     * Method to be called by subclasses when a drop animation is being started.
     */
    public void dispatchDropStarting(ViewHolder item) {
        onDropStarting(item);
    }

    /**
     * Called when a drop animation is being started on a given ViewHolder.
     * The default implementation does nothing. Subclasses may wish to override
     * this method to handle any ViewHolder-specific operations linked to animation
     * lifecycles.
     */
    public void onDropStarting(ViewHolder item) {
    }

    /**
     * Method to be called by subclasses when a drop animation is done.
     */
    public void dispatchDropFinished(ViewHolder item) {
        onDropFinished(item);
        dispatchAnimationFinished(item);
    }

    /**
     * Called when a drop animation has ended on a given ViewHolder.
     * The default implementation does nothing. Subclasses may wish to override
     * this method to handle any ViewHolder-specific operations linked to animation
     * lifecycles.
     */
    public void onDropFinished(ViewHolder item) {
    }

    @Override
    public void endAnimations() {
        int count = mPendingDrops.size();
        for (int i = count - 1; i >= 0; i--) {
            ViewHolder holder = mPendingDrops.get(i).holder;
            View view = holder.itemView;
            view.setTranslationX(0);
            view.setTranslationY(0);
            dispatchDropFinished(holder);
            mPendingDrops.remove(i);
        }
        cancelAll(mDropAnimations);
        mDropAnimations.clear();
        mQueuedMoveAnimations.clear();
        super.endAnimations();
    }

    private void resetAnimation(ViewHolder holder) {
        holder.itemView.animate().setInterpolator(new ValueAnimator().getInterpolator());
        endAnimation(holder);
    }

    private void cancelAll(List<ViewHolder> viewHolders) {
        for (int i = viewHolders.size() - 1; i >= 0; i--) {
            viewHolders.get(i).itemView.animate().cancel();
        }
    }

    public void setDropDuration(long duration) {
        mDropDuration = duration;
    }

    public long getDropDuration() {
        return mDropDuration;
    }
}
