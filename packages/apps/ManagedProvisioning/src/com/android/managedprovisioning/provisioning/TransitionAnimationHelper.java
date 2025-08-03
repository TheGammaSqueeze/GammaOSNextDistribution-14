/*
 * Copyright 2019, The Android Open Source Project
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
package com.android.managedprovisioning.provisioning;

import static java.util.Objects.requireNonNull;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.CrossFadeHelper;
import com.android.managedprovisioning.common.CrossFadeHelper.Callback;
import com.android.managedprovisioning.common.StylerHelper;
import com.android.managedprovisioning.provisioning.ProvisioningModeWrapperProvider.ProvisioningModeWrapper;
import com.android.managedprovisioning.util.LazyStringResource;

import com.airbnb.lottie.LottieAnimationView;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Handles the animated transitions in the education screens. Transitions consist of cross fade
 * animations between different headers and banner images.
 */
class TransitionAnimationHelper {

    interface TransitionAnimationCallback {
        void onAllTransitionsShown();

        void onAnimationSetup(LottieAnimationView animationView);

    }
    interface TransitionAnimationStateManager {
        void saveState(TransitionAnimationState state);

        TransitionAnimationState restoreState();
    }

    private static final int TRANSITION_TIME_MILLIS = 5000;
    private static final int CROSSFADE_ANIMATION_DURATION_MILLIS = 500;

    private final CrossFadeHelper mCrossFadeHelper;
    private final AnimationComponents mAnimationComponents;
    private final Runnable mStartNextTransitionRunnable = this::startNextAnimation;
    private final boolean mShowAnimations;
    private TransitionAnimationCallback mCallback;
    private TransitionAnimationStateManager mStateManager;
    private final ProvisioningModeWrapper mProvisioningModeWrapper;

    private Handler mUiThreadHandler = new Handler(Looper.getMainLooper());
    private TransitionAnimationState mTransitionAnimationState;
    private final StylerHelper mStylerHelper;

    TransitionAnimationHelper(
            AnimationComponents animationComponents,
            TransitionAnimationCallback callback,
            TransitionAnimationStateManager stateManager,
            StylerHelper stylerHelper,
            ProvisioningModeWrapper provisioningModeWrapper) {
        mAnimationComponents = requireNonNull(animationComponents);
        mCallback = requireNonNull(callback);
        mStateManager = requireNonNull(stateManager);
        mProvisioningModeWrapper = provisioningModeWrapper;
        mCrossFadeHelper = getCrossFadeHelper();
        mShowAnimations = shouldShowAnimations();
        mStylerHelper = requireNonNull(stylerHelper);

        applyContentDescription(
                mAnimationComponents.mAnimationView, mProvisioningModeWrapper.mSummary);
    }

    boolean areAllTransitionsShown() {
        return mTransitionAnimationState.mAnimationIndex
                == mProvisioningModeWrapper.mTransitions.size() - 1;
    }

    void start() {
        mTransitionAnimationState = maybeRestoreState();
        scheduleNextTransition(getTimeLeftForTransition(mTransitionAnimationState));
        updateUiValues(mTransitionAnimationState.mAnimationIndex);
        startCurrentAnimatedDrawable(mTransitionAnimationState.mProgress);
    }

    private long getTimeLeftForTransition(TransitionAnimationState transitionAnimationState) {
        long timeSinceLastTransition =
                System.currentTimeMillis() - transitionAnimationState.mLastTransitionTimestamp;
        return TRANSITION_TIME_MILLIS - timeSinceLastTransition;
    }

    void stop() {
        updateState();
        mStateManager.saveState(mTransitionAnimationState);
        clean();
    }

    private void updateState() {
        mTransitionAnimationState.mProgress = mAnimationComponents.mAnimationView.getProgress();
    }

    private TransitionAnimationState maybeRestoreState() {
        TransitionAnimationState transitionAnimationState = mStateManager.restoreState();
        if (transitionAnimationState == null) {
            return new TransitionAnimationState(
                    /* animationIndex */ 0,
                    /* progress */ 0,
                    /* lastTransitionTimestamp */ System.currentTimeMillis());
        }
        return transitionAnimationState;
    }

    private void clean() {
        stopCurrentAnimatedDrawable();
        mCrossFadeHelper.cleanup();
        mUiThreadHandler.removeCallbacksAndMessages(null);
        mUiThreadHandler = null;
        mCallback = null;
        mStateManager = null;
    }

    @VisibleForTesting
    CrossFadeHelper getCrossFadeHelper() {
        return new CrossFadeHelper(
            mAnimationComponents.asList(),
            CROSSFADE_ANIMATION_DURATION_MILLIS,
            new Callback() {
                @Override
                public void fadeOutCompleted() {
                    stopCurrentAnimatedDrawable();
                    mTransitionAnimationState.mAnimationIndex++;
                    updateUiValues(mTransitionAnimationState.mAnimationIndex);
                    startCurrentAnimatedDrawable(/* startProgress */ 0f);
                }

                @Override
                public void fadeInCompleted() {
                    mTransitionAnimationState.mLastTransitionTimestamp = System.currentTimeMillis();
                    scheduleNextTransition(TRANSITION_TIME_MILLIS);
                }
            });
    }

    private void scheduleNextTransition(long timeLeftForTransition) {
        mUiThreadHandler.postDelayed(mStartNextTransitionRunnable, timeLeftForTransition);
    }

    @VisibleForTesting
    void startNextAnimation() {
        if (mTransitionAnimationState.mAnimationIndex
                >= mProvisioningModeWrapper.mTransitions.size() - 1) {
            if (mCallback != null) {
                mCallback.onAllTransitionsShown();
            }
            return;
        }
        mCrossFadeHelper.start();
    }

    @VisibleForTesting
    void startCurrentAnimatedDrawable(float startProgress) {
        if (!mShowAnimations) {
            return;
        }
        boolean shouldLoop =
                getTransitionForIndex(mTransitionAnimationState.mAnimationIndex).shouldLoop;
        mAnimationComponents.mAnimationView.loop(shouldLoop);
        mAnimationComponents.mAnimationView.setProgress(startProgress);
        mAnimationComponents.mAnimationView.playAnimation();
    }

    @VisibleForTesting
    void stopCurrentAnimatedDrawable() {
        if (!mShowAnimations) {
            return;
        }
        mAnimationComponents.mAnimationView.pauseAnimation();
    }

    @VisibleForTesting
    void updateUiValues(int currentTransitionIndex) {
        final TransitionScreenWrapper transition =
                getTransitionForIndex(currentTransitionIndex);
        setupHeaderText(transition);
        setupDescriptionText(transition);
        setupAnimation(transition);

        boolean isTextBasedEduScreen = transition.subHeaderIcon != 0;
        updateItemValues(
                mAnimationComponents.mItem1,
                transition.subHeaderIcon,
                transition.subHeaderTitle,
                transition.subHeader,
                isTextBasedEduScreen);
        updateItemValues(
                mAnimationComponents.mItem2,
                transition.secondarySubHeaderIcon,
                transition.secondarySubHeaderTitle,
                transition.secondarySubHeader,
                isTextBasedEduScreen);
        updateSpaceVisibility(mAnimationComponents.mSpace1, isTextBasedEduScreen);
        updateSpaceVisibility(mAnimationComponents.mSpace2, isTextBasedEduScreen);
    }

    private void setupHeaderText(TransitionScreenWrapper transition) {
        var context = mAnimationComponents.mHeader.getContext();
        mAnimationComponents.mHeader.setText(transition.header.value(context));
        triggerTextToSpeechIfFocused(mAnimationComponents.mHeader);
    }

    private void triggerTextToSpeechIfFocused(TextView view) {
        if (view.isAccessibilityFocused()) {
            view.announceForAccessibility(view.getText().toString());
        }
    }

    private void setupAnimation(TransitionScreenWrapper transition) {
        if (mShowAnimations && transition.drawable != 0) {
            mAnimationComponents.mAnimationView.setAnimation(transition.drawable);
            mCallback.onAnimationSetup(mAnimationComponents.mAnimationView);
            mAnimationComponents.mImageContainer.setVisibility(View.VISIBLE);
        } else {
            mAnimationComponents.mImageContainer.setVisibility(View.GONE);
        }
    }

    private void setupDescriptionText(TransitionScreenWrapper transition) {
        var context = mAnimationComponents.mDescription.getContext();
        if (transition.description.isBlank(context)) {
            mAnimationComponents.mDescription.setText(transition.description.value(context));
            mAnimationComponents.mDescription.setVisibility(View.VISIBLE);
            triggerTextToSpeechIfFocused(mAnimationComponents.mDescription);
        } else {
            mAnimationComponents.mDescription.setVisibility(View.GONE);
        }
    }

    private void updateItemValues(
            ViewGroup item,
            int icon,
            LazyStringResource subHeaderTitle,
            LazyStringResource subHeader,
            boolean isTextBasedEduScreen) {
        var context = item.getContext();
        if (isTextBasedEduScreen) {
            ((ImageView) item.findViewById(R.id.sud_items_icon)).setImageResource(icon);
            ((TextView) item.findViewById(R.id.sud_items_title)).setText(
                    subHeaderTitle.value(context));
            ((TextView) item.findViewById(R.id.sud_items_summary)).setText(
                    subHeader.value(context));
            mStylerHelper.applyListItemStyling(
                    item, new LinearLayout.LayoutParams(item.getLayoutParams()));
            item.setVisibility(View.VISIBLE);
        } else {
            item.setVisibility(View.GONE);
        }
    }

    private void updateSpaceVisibility(Space space, boolean isTextBasedEduScreen) {
        if (isTextBasedEduScreen) {
            space.setVisibility(View.VISIBLE);
        } else {
            space.setVisibility(View.GONE);
        }
    }

    private TransitionScreenWrapper getTransitionForIndex(int currentTransitionIndex) {
        var transitions = mProvisioningModeWrapper.mTransitions;
        return transitions.get(currentTransitionIndex % transitions.size());
    }

    private boolean shouldShowAnimations() {
        final Context context = mAnimationComponents.mHeader.getContext();
        return context.getResources().getBoolean(R.bool.show_edu_animations);
    }

    private void applyContentDescription(View view, LazyStringResource summaryRes) {
        view.setContentDescription(summaryRes.value(view.getContext()));
    }

    static final class AnimationComponents {
        private final TextView mHeader;
        private final TextView mDescription;
        private final LottieAnimationView mAnimationView;
        private final ViewGroup mImageContainer;
        private final ViewGroup mItem1;
        private final ViewGroup mItem2;
        private final Space mSpace1;
        private final Space mSpace2;

        AnimationComponents(TextView header, TextView description, ViewGroup item1,
                ViewGroup item2, LottieAnimationView animationView, ViewGroup imageContainer,
                Space space1, Space space2) {
            this.mHeader = requireNonNull(header);
            this.mDescription = requireNonNull(description);
            this.mItem1 = requireNonNull(item1);
            this.mItem2 = requireNonNull(item2);
            this.mImageContainer = requireNonNull(imageContainer);
            this.mAnimationView = requireNonNull(animationView);
            this.mSpace1 = requireNonNull(space1);
            this.mSpace2 = requireNonNull(space2);
        }

        List<View> asList() {
            return Arrays.asList(mHeader, mItem1, mItem2, mImageContainer);
        }
    }

    static final class TransitionAnimationState {
        private int mAnimationIndex;
        private float mProgress;
        private long mLastTransitionTimestamp;

        TransitionAnimationState(
                int animationIndex,
                float progress,
                long lastTransitionTimestamp) {
            mAnimationIndex = animationIndex;
            mProgress = progress;
            mLastTransitionTimestamp = lastTransitionTimestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TransitionAnimationState)) return false;
            TransitionAnimationState that = (TransitionAnimationState) o;
            return mAnimationIndex == that.mAnimationIndex &&
                    Float.compare(that.mProgress, mProgress) == 0 &&
                    mLastTransitionTimestamp == that.mLastTransitionTimestamp;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mAnimationIndex, mProgress, mLastTransitionTimestamp);
        }
    }
}
