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

package com.android.permissioncontroller.safetycenter.ui;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import android.content.Context;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.safetycenter.SafetyCenterStatus;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterViewModel;
import com.android.permissioncontroller.safetycenter.ui.model.StatusUiData;
import com.android.permissioncontroller.safetycenter.ui.view.StatusCardView;

import kotlin.Pair;

import java.util.List;
import java.util.Objects;

/** Preference which displays a visual representation of {@link SafetyCenterStatus}. */
@RequiresApi(TIRAMISU)
public class SafetyStatusPreference extends Preference implements ComparablePreference {

    private static final String TAG = "SafetyStatusPreference";

    @Nullable private StatusUiData mStatus;
    @Nullable private SafetyCenterViewModel mViewModel;

    private final TextFadeAnimator mTitleTextAnimator = new TextFadeAnimator(R.id.status_title);

    private final TextFadeAnimator mSummaryTextAnimator = new TextFadeAnimator(R.id.status_summary);

    private final TextFadeAnimator mAllTextAnimator =
            new TextFadeAnimator(List.of(R.id.status_title, R.id.status_summary));

    private boolean mFirstBind = true;

    public SafetyStatusPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_safety_status);
    }

    private boolean mIsTextChangeAnimationRunning;
    private final SafetyStatusAnimationSequencer mSequencer = new SafetyStatusAnimationSequencer();

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        Log.v(TAG, String.format("onBindViewHolder called for status %s", mStatus));

        if (mStatus == null) {
            return;
        }

        Context context = getContext();
        StatusCardView statusCardView = (StatusCardView) holder.itemView;
        configureButtons(context, statusCardView);
        statusCardView
                .getTitleAndSummaryContainerView()
                .setContentDescription(mStatus.getContentDescription(context));

        updateStatusIcon(statusCardView);

        updateStatusText(statusCardView.getTitleView(), statusCardView.getSummaryView());

        mFirstBind = false;
    }

    private void configureButtons(Context context, StatusCardView statusCardView) {
        statusCardView
                .getRescanButton()
                .setOnClickListener(
                        unused -> {
                            SafetyCenterViewModel viewModel = requireViewModel();
                            viewModel.rescan();
                            viewModel.getInteractionLogger().record(Action.SCAN_INITIATED);
                        });
        statusCardView
                .getReviewSettingsButton()
                .setOnClickListener(
                        unused -> {
                            SafetyCenterViewModel viewModel = requireViewModel();
                            viewModel.navigateToSafetyCenter(
                                    context, NavigationSource.QUICK_SETTINGS_TILE);
                            viewModel.getInteractionLogger().record(Action.REVIEW_SETTINGS_CLICKED);
                        });

        updateButtonState(statusCardView);
    }

    private void updateButtonState(StatusCardView statusCardView) {
        if (mStatus == null) return; // Shouldn't happen in practice but we do it for null safety.
        statusCardView.showButtons(mStatus);
    }

    private void updateStatusText(TextView title, TextView summary) {
        if (mFirstBind) {
            title.setText(mStatus.getTitle());
            summary.setText(mStatus.getSummary(getContext()));
        }
        runTextAnimationIfNeeded(title, summary);
    }

    private void updateStatusIcon(StatusCardView statusCardView) {
        int severityLevel = mStatus.getSeverityLevel();
        boolean isRefreshing = mStatus.isRefreshInProgress();

        handleAnimationSequencerAction(
                mSequencer.onUpdateReceived(isRefreshing, severityLevel),
                statusCardView,
                /* scanningAnimation= */ null);
    }

    private void runTextAnimationIfNeeded(TextView titleView, TextView summaryView) {
        if (mIsTextChangeAnimationRunning) {
            return;
        }
        Log.v(TAG, "Starting status text animation");
        String titleText = mStatus.getTitle().toString();
        String summaryText = mStatus.getSummary(getContext()).toString();
        boolean titleEquals = titleView.getText().toString().equals(titleText);
        boolean summaryEquals = summaryView.getText().toString().equals(summaryText);
        Runnable onFinish =
                () -> {
                    Log.v(TAG, "Finishing status text animation");
                    mIsTextChangeAnimationRunning = false;
                    runTextAnimationIfNeeded(titleView, summaryView);
                };
        mIsTextChangeAnimationRunning = !titleEquals || !summaryEquals;
        if (!titleEquals && !summaryEquals) {
            Pair<TextView, String> titleChange = new Pair<>(titleView, titleText);
            Pair<TextView, String> summaryChange = new Pair<>(summaryView, summaryText);
            mAllTextAnimator.animateChangeText(List.of(titleChange, summaryChange), onFinish);
        } else if (!titleEquals) {
            mTitleTextAnimator.animateChangeText(titleView, titleText, onFinish);
        } else if (!summaryEquals) {
            mSummaryTextAnimator.animateChangeText(summaryView, summaryText, onFinish);
        }
    }

    private void startScanningAnimation(StatusCardView statusCardView) {
        mSequencer.onStartScanningAnimationStart();
        ImageView statusImage = statusCardView.getStatusImageView();
        statusImage.setImageResource(
                StatusAnimationResolver.getScanningStartAnimation(
                        mSequencer.getCurrentlyVisibleSeverityLevel()));
        AnimatedVectorDrawable animation = (AnimatedVectorDrawable) statusImage.getDrawable();
        animation.registerAnimationCallback(
                new Animatable2.AnimationCallback() {
                    @Override
                    public void onAnimationEnd(Drawable drawable) {
                        handleAnimationSequencerAction(
                                mSequencer.onStartScanningAnimationEnd(),
                                statusCardView,
                                /* scanningAnimation= */ null);
                    }
                });
        animation.start();
    }

    private void continueScanningAnimation(StatusCardView statusCardView) {
        ImageView statusImage = statusCardView.getStatusImageView();

        // clear previous scan animation in case we need to continue with different severity level
        Drawable statusDrawable = statusImage.getDrawable();
        if (statusDrawable instanceof AnimatedVectorDrawable) {
            ((AnimatedVectorDrawable) statusDrawable).clearAnimationCallbacks();
        }

        statusImage.setImageResource(
                StatusAnimationResolver.getScanningAnimation(
                        mSequencer.getCurrentlyVisibleSeverityLevel()));
        AnimatedVectorDrawable scanningAnim = (AnimatedVectorDrawable) statusImage.getDrawable();
        scanningAnim.registerAnimationCallback(
                new Animatable2.AnimationCallback() {
                    @Override
                    public void onAnimationEnd(Drawable drawable) {
                        handleAnimationSequencerAction(
                                mSequencer.onContinueScanningAnimationEnd(
                                        mStatus.isRefreshInProgress(), mStatus.getSeverityLevel()),
                                statusCardView,
                                scanningAnim);
                    }
                });
        scanningAnim.start();
    }

    private void endScanningAnimation(StatusCardView statusCardView) {
        ImageView statusImage = statusCardView.getStatusImageView();
        Drawable statusDrawable = statusImage.getDrawable();
        int finishingSeverityLevel = mStatus.getSeverityLevel();
        if (!(statusDrawable instanceof AnimatedVectorDrawable)) {
            finishScanAnimation(statusCardView, finishingSeverityLevel);
            return;
        }
        AnimatedVectorDrawable animatedStatusDrawable = (AnimatedVectorDrawable) statusDrawable;

        if (!animatedStatusDrawable.isRunning()) {
            finishScanAnimation(statusCardView, finishingSeverityLevel);
            return;
        }

        int scanningSeverityLevel = mSequencer.getCurrentlyVisibleSeverityLevel();
        animatedStatusDrawable.clearAnimationCallbacks();
        animatedStatusDrawable.registerAnimationCallback(
                new Animatable2.AnimationCallback() {
                    @Override
                    public void onAnimationEnd(Drawable drawable) {
                        statusImage.setImageResource(
                                StatusAnimationResolver.getScanningEndAnimation(
                                        scanningSeverityLevel, finishingSeverityLevel));
                        AnimatedVectorDrawable animatedDrawable =
                                (AnimatedVectorDrawable) statusImage.getDrawable();
                        animatedDrawable.registerAnimationCallback(
                                new Animatable2.AnimationCallback() {
                                    @Override
                                    public void onAnimationEnd(Drawable drawable) {
                                        super.onAnimationEnd(drawable);
                                        finishScanAnimation(statusCardView, finishingSeverityLevel);
                                    }
                                });
                        animatedDrawable.start();
                    }
                });
    }

    private void finishScanAnimation(StatusCardView statusCardView, int finishedSeverityLevel) {
        updateButtonState(statusCardView);
        handleAnimationSequencerAction(
                mSequencer.onFinishScanAnimationEnd(
                        mStatus.isRefreshInProgress(), finishedSeverityLevel),
                statusCardView,
                /* scanningAnimation= */ null);
    }

    private void startIconChangeAnimation(StatusCardView statusCardView) {
        int finalSeverityLevel = mStatus.getSeverityLevel();
        int changeAnimationResId =
                StatusAnimationResolver.getStatusChangeAnimation(
                        mSequencer.getCurrentlyVisibleSeverityLevel(), finalSeverityLevel);
        if (changeAnimationResId == 0) {
            handleAnimationSequencerAction(
                    mSequencer.onCouldNotStartIconChangeAnimation(
                            mStatus.isRefreshInProgress(), finalSeverityLevel),
                    statusCardView,
                    /* scanningAnimation= */ null);
            return;
        }
        mSequencer.onIconChangeAnimationStart();
        statusCardView.getStatusImageView().setImageResource(changeAnimationResId);
        AnimatedVectorDrawable animation =
                (AnimatedVectorDrawable) statusCardView.getStatusImageView().getDrawable();
        animation.clearAnimationCallbacks();
        animation.registerAnimationCallback(
                new Animatable2.AnimationCallback() {
                    @Override
                    public void onAnimationEnd(Drawable drawable) {
                        handleAnimationSequencerAction(
                                mSequencer.onIconChangeAnimationEnd(
                                        mStatus.isRefreshInProgress(), finalSeverityLevel),
                                statusCardView,
                                /* scanningAnimation= */ null);
                    }
                });
        animation.start();
    }

    private void handleAnimationSequencerAction(
            @Nullable SafetyStatusAnimationSequencer.Action action,
            StatusCardView statusCardView,
            @Nullable AnimatedVectorDrawable scanningAnimation) {
        if (action == null) {
            return;
        }
        switch (action) {
            case START_SCANNING_ANIMATION:
                startScanningAnimation(statusCardView);
                break;
            case CONTINUE_SCANNING_ANIMATION:
                if (scanningAnimation != null) {
                    scanningAnimation.start();
                } else {
                    continueScanningAnimation(statusCardView);
                }
                break;
            case RESET_SCANNING_ANIMATION:
                continueScanningAnimation(statusCardView);
                break;
            case FINISH_SCANNING_ANIMATION:
                endScanningAnimation(statusCardView);
                break;
            case START_ICON_CHANGE_ANIMATION:
                startIconChangeAnimation(statusCardView);
                break;
            case CHANGE_ICON_WITHOUT_ANIMATION:
                setSettledStatus(statusCardView);
                break;
        }
    }

    private void setSettledStatus(StatusCardView statusCardView) {
        Drawable statusDrawable = statusCardView.getStatusImageView().getDrawable();
        if (statusDrawable instanceof AnimatedVectorDrawable) {
            ((AnimatedVectorDrawable) statusDrawable).clearAnimationCallbacks();
        }
        statusCardView
                .getStatusImageView()
                .setImageResource(
                        StatusUiData.Companion.getStatusImageResId(
                                mSequencer.getCurrentlyVisibleSeverityLevel()));
    }

    void setData(StatusUiData statusUiData) {
        if (Objects.equals(mStatus, statusUiData)) {
            return;
        }

        mStatus = statusUiData;
        Log.v(TAG, String.format("setData called for status %s", mStatus));
        safeNotifyChanged();
    }

    void setViewModel(SafetyCenterViewModel viewModel) {
        mViewModel = Objects.requireNonNull(viewModel);
    }

    private SafetyCenterViewModel requireViewModel() {
        return Objects.requireNonNull(mViewModel);
    }

    // Calling notifyChanged while recyclerview is scrolling or computing layout will result in an
    // IllegalStateException. Post to handler to wait for UI to settle.
    private void safeNotifyChanged() {
        new Handler(Looper.getMainLooper())
                .post(
                        () -> {
                            Log.v(
                                    TAG,
                                    String.format("Calling notifyChanged for status %s", mStatus));
                            notifyChanged();
                        });
    }

    @Override
    public boolean isSameItem(Preference preference) {
        return preference instanceof SafetyStatusPreference
                && TextUtils.equals(getKey(), preference.getKey());
    }

    @Override
    public boolean hasSameContents(Preference preference) {
        if (!(preference instanceof SafetyStatusPreference)) {
            return false;
        }
        SafetyStatusPreference other = (SafetyStatusPreference) preference;
        return Objects.equals(mStatus, other.mStatus);
    }
}
