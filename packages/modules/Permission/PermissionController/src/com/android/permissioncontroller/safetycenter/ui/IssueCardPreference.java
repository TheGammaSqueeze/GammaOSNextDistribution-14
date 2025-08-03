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
import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import static java.util.Objects.requireNonNull;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.safetycenter.SafetyCenterIssue;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.modules.utils.build.SdkLevel;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterViewModel;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.shape.AbsoluteCornerSize;
import com.google.android.material.shape.CornerSize;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.Objects;

/** A preference that displays a card representing a {@link SafetyCenterIssue}. */
@RequiresApi(TIRAMISU)
public class IssueCardPreference extends Preference implements ComparablePreference {

    public static final String TAG = IssueCardPreference.class.getSimpleName();

    private final IssueCardAnimator mIssueCardAnimator =
            new IssueCardAnimator(this::markIssueResolvedUiCompleted);
    private final SafetyCenterViewModel mSafetyCenterViewModel;
    private final SafetyCenterIssue mIssue;
    private final FragmentManager mDialogFragmentManager;
    @Nullable private String mResolvedIssueActionId;
    @Nullable private final Integer mTaskId;
    private final boolean mIsDismissed;
    private final PositionInCardList mPositionInCardList;

    public IssueCardPreference(
            Context context,
            SafetyCenterViewModel safetyCenterViewModel,
            SafetyCenterIssue issue,
            @Nullable String resolvedIssueActionId,
            FragmentManager dialogFragmentManager,
            @Nullable Integer launchTaskId,
            boolean isDismissed,
            PositionInCardList positionInCardList) {
        super(context);
        setLayoutResource(R.layout.preference_issue_card);

        mSafetyCenterViewModel = requireNonNull(safetyCenterViewModel);
        mIssue = requireNonNull(issue);
        mDialogFragmentManager = dialogFragmentManager;
        mResolvedIssueActionId = resolvedIssueActionId;
        mTaskId = launchTaskId;
        mIsDismissed = isDismissed;
        mPositionInCardList = positionInCardList;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        holder.itemView.setBackgroundResource(mPositionInCardList.getBackgroundDrawableResId());
        int topMargin = getTopMargin(mPositionInCardList, getContext());
        MarginLayoutParams layoutParams = (MarginLayoutParams) holder.itemView.getLayoutParams();
        if (layoutParams.topMargin != topMargin) {
            layoutParams.topMargin = topMargin;
            holder.itemView.setLayoutParams(layoutParams);
        }

        // Set default group visibility in case view is being reused
        holder.findViewById(R.id.default_issue_content).setVisibility(View.VISIBLE);
        holder.findViewById(R.id.resolved_issue_content).setVisibility(View.GONE);

        configureDismissButton(holder.findViewById(R.id.issue_card_dismiss_btn));

        TextView titleTextView = (TextView) holder.findViewById(R.id.issue_card_title);
        titleTextView.setText(mIssue.getTitle());
        ((TextView) holder.findViewById(R.id.issue_card_summary)).setText(mIssue.getSummary());

        TextView attributionTitleTextView =
                (TextView) holder.findViewById(R.id.issue_card_attribution_title);
        maybeDisplayText(
                SdkLevel.isAtLeastU() ? mIssue.getAttributionTitle() : null,
                attributionTitleTextView);

        TextView subtitleTextView = (TextView) holder.findViewById(R.id.issue_card_subtitle);
        maybeDisplayText(mIssue.getSubtitle(), subtitleTextView);

        holder.itemView.setClickable(false);

        configureContentDescription(attributionTitleTextView, titleTextView);
        configureButtonList(holder);
        configureSafetyProtectionView(holder);
        maybeStartResolutionAnimation(holder);

        mSafetyCenterViewModel.getInteractionLogger().recordIssueViewed(mIssue, mIsDismissed);
    }

    private void maybeDisplayText(@Nullable CharSequence maybeText, TextView textView) {
        if (TextUtils.isEmpty(maybeText)) {
            textView.setVisibility(View.GONE);
        } else {
            textView.setText(maybeText);
            textView.setVisibility(View.VISIBLE);
        }
    }

    private void configureContentDescription(
            TextView attributionTitleTextView, TextView titleTextView) {
        TextView firstVisibleTextView;
        if (attributionTitleTextView.getVisibility() == View.VISIBLE) {
            // Attribution title might not be present for an issue, title always is.
            firstVisibleTextView = attributionTitleTextView;

            // Clear the modified title description in case this view is reused.
            titleTextView.setContentDescription(null);
        } else {
            firstVisibleTextView = titleTextView;
        }

        // We would like to say "alert" before reading the content of the issue card. Best way to
        // do that is by modifying the content description of the first view that would be read
        // in the issue card.
        firstVisibleTextView.setContentDescription(
                getContext()
                        .getString(
                                R.string.safety_center_issue_card_prefix_content_description,
                                firstVisibleTextView.getText()));
    }

    private void configureButtonList(PreferenceViewHolder holder) {
        LinearLayout buttonList =
                ((LinearLayout) holder.findViewById(R.id.issue_card_action_button_list));
        buttonList.removeAllViews(); // This view may be recycled from another issue

        for (int i = 0; i < mIssue.getActions().size(); i++) {
            SafetyCenterIssue.Action action = mIssue.getActions().get(i);
            ActionButtonBuilder builder =
                    new ActionButtonBuilder(action, holder.itemView.getContext())
                            .setIndex(i)
                            .setActionButtonListSize(mIssue.getActions().size())
                            .setIsDismissed(mIsDismissed)
                            .setIsLargeScreen(buttonList instanceof EqualWidthContainer);
            builder.buildAndAddToView(buttonList);
        }
    }

    private int getTopMargin(PositionInCardList position, Context context) {
        switch (position) {
            case LIST_START_END:
            case LIST_START_CARD_END:
                return context.getResources()
                        .getDimensionPixelSize(
                                mIsDismissed ? R.dimen.sc_card_margin : R.dimen.sc_spacing_large);
            default:
                return position.getTopMargin(context);
        }
    }

    private void configureSafetyProtectionView(PreferenceViewHolder holder) {
        View safetyProtectionSectionView =
                holder.findViewById(R.id.issue_card_protected_by_android);
        if (safetyProtectionSectionView.getVisibility() == View.GONE) {
            holder.itemView.setPaddingRelative(
                    holder.itemView.getPaddingStart(),
                    holder.itemView.getPaddingTop(),
                    holder.itemView.getPaddingEnd(),
                    /* bottom= */ getContext()
                            .getResources()
                            .getDimensionPixelSize(R.dimen.sc_card_margin_bottom));
        } else {
            holder.itemView.setPaddingRelative(
                    holder.itemView.getPaddingStart(),
                    holder.itemView.getPaddingTop(),
                    holder.itemView.getPaddingEnd(),
                    /* bottom= */ 0);
        }
    }

    private void maybeStartResolutionAnimation(PreferenceViewHolder holder) {
        if (mResolvedIssueActionId == null) {
            return;
        }

        for (SafetyCenterIssue.Action action : mIssue.getActions()) {
            if (action.getId().equals(mResolvedIssueActionId)) {
                mIssueCardAnimator.transitionToIssueResolvedThenMarkComplete(
                        getContext(), holder, action);
            }
        }
    }

    public int getSeverityLevel() {
        return mIssue.getSeverityLevel();
    }

    private void configureDismissButton(View dismissButton) {
        if (mIssue.isDismissible() && !mIsDismissed) {
            dismissButton.setOnClickListener(
                    mIssue.shouldConfirmDismissal()
                            ? new ConfirmDismissalOnClickListener()
                            : new DismissOnClickListener());
            dismissButton.setVisibility(View.VISIBLE);

            SafetyCenterTouchTarget.configureSize(
                    dismissButton, R.dimen.sc_icon_button_touch_target_size);
        } else {
            dismissButton.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean isSameItem(Preference preference) {
        return (preference instanceof IssueCardPreference)
                && TextUtils.equals(
                        mIssue.getId(), ((IssueCardPreference) preference).mIssue.getId());
    }

    @Override
    public boolean hasSameContents(Preference preference) {
        return (preference instanceof IssueCardPreference)
                && mIssue.equals(((IssueCardPreference) preference).mIssue)
                && Objects.equals(
                        mResolvedIssueActionId,
                        ((IssueCardPreference) preference).mResolvedIssueActionId)
                && mIsDismissed == ((IssueCardPreference) preference).mIsDismissed
                && mPositionInCardList == ((IssueCardPreference) preference).mPositionInCardList;
    }

    private class DismissOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            mSafetyCenterViewModel.dismissIssue(mIssue);
            mSafetyCenterViewModel
                    .getInteractionLogger()
                    .recordForIssue(Action.ISSUE_DISMISS_CLICKED, mIssue, mIsDismissed);
        }
    }

    private class ConfirmDismissalOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            ConfirmDismissalDialogFragment.newInstance(mIssue)
                    .showNow(mDialogFragmentManager, /* tag= */ null);
        }
    }

    /** Fragment to display a dismissal confirmation dialog for an {@link IssueCardPreference}. */
    public static class ConfirmDismissalDialogFragment extends DialogFragment {
        private static final String ISSUE_KEY = "confirm_dialog_sc_issue";

        private static ConfirmDismissalDialogFragment newInstance(SafetyCenterIssue issue) {
            ConfirmDismissalDialogFragment fragment = new ConfirmDismissalDialogFragment();

            Bundle args = new Bundle();
            args.putParcelable(ISSUE_KEY, issue);
            fragment.setArguments(args);

            return fragment;
        }

        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            SafetyCenterViewModel safetyCenterViewModel =
                    ((SafetyCenterFragment) requireParentFragment()).getSafetyCenterViewModel();
            SafetyCenterIssue issue =
                    requireNonNull(
                            requireArguments().getParcelable(ISSUE_KEY, SafetyCenterIssue.class));
            return new AlertDialog.Builder(getContext())
                    .setTitle(R.string.safety_center_issue_card_dismiss_confirmation_title)
                    .setMessage(R.string.safety_center_issue_card_dismiss_confirmation_message)
                    .setPositiveButton(
                            R.string.safety_center_issue_card_confirm_dismiss_button,
                            (dialog, which) -> {
                                safetyCenterViewModel.dismissIssue(issue);
                                safetyCenterViewModel
                                        .getInteractionLogger()
                                        .recordForIssue(
                                                Action.ISSUE_DISMISS_CLICKED,
                                                issue,
                                                // You can only dismiss non-dismissed issues
                                                /* isDismissed= */ false);
                            })
                    .setNegativeButton(
                            R.string.safety_center_issue_card_cancel_dismiss_button, null)
                    .create();
        }
    }

    /** A dialog to prompt for a confirmation to performn an Action. */
    @RequiresApi(UPSIDE_DOWN_CAKE)
    public static class ConfirmActionDialogFragment extends DialogFragment {
        private static final String ISSUE_KEY = "issue";
        private static final String ACTION_KEY = "action";
        private static final String TASK_ID_KEY = "taskId";
        private static final String IS_PRIMARY_BUTTON_KEY = "isPrimaryButton";
        private static final String IS_DISMISSED_KEY = "isDismissed";

        /** Create new fragment with the data it will need. */
        public static ConfirmActionDialogFragment newInstance(
                SafetyCenterIssue issue,
                SafetyCenterIssue.Action action,
                @Nullable Integer taskId,
                boolean isFirstButton,
                boolean isDismissed) {
            ConfirmActionDialogFragment fragment = new ConfirmActionDialogFragment();

            Bundle args = new Bundle();
            args.putParcelable(ISSUE_KEY, issue);
            args.putParcelable(ACTION_KEY, action);
            args.putBoolean(IS_PRIMARY_BUTTON_KEY, isFirstButton);
            args.putBoolean(IS_DISMISSED_KEY, isDismissed);

            if (taskId != null) {
                args.putInt(TASK_ID_KEY, taskId);
            }

            fragment.setArguments(args);

            return fragment;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            SafetyCenterViewModel safetyCenterViewModel =
                    ((SafetyCenterFragment) requireParentFragment()).getSafetyCenterViewModel();
            SafetyCenterIssue issue =
                    requireNonNull(
                            requireArguments().getParcelable(ISSUE_KEY, SafetyCenterIssue.class));
            SafetyCenterIssue.Action action =
                    requireNonNull(
                            requireArguments()
                                    .getParcelable(ACTION_KEY, SafetyCenterIssue.Action.class));
            boolean isPrimaryButton = requireArguments().getBoolean(IS_PRIMARY_BUTTON_KEY);
            boolean isDismissed = requireArguments().getBoolean(IS_DISMISSED_KEY);

            Integer taskId =
                    requireArguments().containsKey(TASK_ID_KEY)
                            ? requireArguments().getInt(TASK_ID_KEY)
                            : null;

            return new AlertDialog.Builder(getContext())
                    .setTitle(action.getConfirmationDialogDetails().getTitle())
                    .setMessage(action.getConfirmationDialogDetails().getText())
                    .setPositiveButton(
                            action.getConfirmationDialogDetails().getAcceptButtonText(),
                            (dialog, which) -> {
                                safetyCenterViewModel.executeIssueAction(issue, action, taskId);
                                // TODO(b/269097766): Is this the best logging model?
                                safetyCenterViewModel
                                        .getInteractionLogger()
                                        .recordForIssue(
                                                isPrimaryButton
                                                        ? Action.ISSUE_PRIMARY_ACTION_CLICKED
                                                        : Action.ISSUE_SECONDARY_ACTION_CLICKED,
                                                issue,
                                                isDismissed);
                            })
                    .setNegativeButton(
                            action.getConfirmationDialogDetails().getDenyButtonText(), null)
                    .create();
        }
    }

    private void markIssueResolvedUiCompleted() {
        if (mResolvedIssueActionId != null) {
            mResolvedIssueActionId = null;
            mSafetyCenterViewModel.markIssueResolvedUiCompleted(mIssue.getId());
        }
    }

    private class ActionButtonBuilder {
        private final SafetyCenterIssue.Action mAction;
        private final Context mContext;
        private final ContextThemeWrapper mContextThemeWrapper;
        private int mIndex;
        private int mActionButtonListSize;
        private boolean mIsDismissed = false;
        private boolean mIsLargeScreen = false;

        ActionButtonBuilder(SafetyCenterIssue.Action action, Context context) {
            mAction = action;
            mContext = context;

            TypedValue buttonThemeValue = new TypedValue();
            mContext.getTheme()
                    .resolveAttribute(
                            R.attr.scActionButtonTheme,
                            buttonThemeValue,
                            /* resolveRefs= */ false);
            mContextThemeWrapper = new ContextThemeWrapper(context, buttonThemeValue.data);
        }

        public ActionButtonBuilder setIndex(int index) {
            mIndex = index;
            return this;
        }

        public ActionButtonBuilder setActionButtonListSize(int actionButtonListSize) {
            mActionButtonListSize = actionButtonListSize;
            return this;
        }

        public ActionButtonBuilder setIsDismissed(boolean isDismissed) {
            mIsDismissed = isDismissed;
            return this;
        }

        public ActionButtonBuilder setIsLargeScreen(boolean isLargeScreen) {
            mIsLargeScreen = isLargeScreen;
            return this;
        }

        private boolean isPrimaryButton() {
            return mIndex == 0;
        }

        private boolean isLastButton() {
            return mIndex == (mActionButtonListSize - 1);
        }

        private boolean isFilled() {
            return isPrimaryButton() && !mIsDismissed;
        }

        public void buildAndAddToView(LinearLayout buttonList) {
            MaterialButton button = new MaterialButton(mContextThemeWrapper, null, getStyle());
            if (SdkLevel.isAtLeastU() && !mIsLargeScreen) {
                configureGroupStyleCorners(button);
            }
            setButtonColors(button);
            setButtonLayout(button);
            button.setText(mAction.getLabel());
            button.setEnabled(!mAction.isInFlight());
            button.setOnClickListener(
                    view -> {
                        if (SdkLevel.isAtLeastU()
                                && mAction.getConfirmationDialogDetails() != null) {
                            ConfirmActionDialogFragment.newInstance(
                                            mIssue,
                                            mAction,
                                            mTaskId,
                                            isPrimaryButton(),
                                            mIsDismissed)
                                    .showNow(mDialogFragmentManager, /* tag= */ null);
                        } else {
                            if (mAction.willResolve()) {
                                // Without a confirmation, the button remains tappable. Disable the
                                // button to prevent double-taps.
                                // We ideally want to do this on any button press, however out of an
                                // abundance of caution we only do it with actions that indicate
                                // they will resolve (and therefore we can rely on a model update to
                                // redraw state - either to isInFlight() or simply resolving the
                                // issue.
                                button.setEnabled(false);
                            }
                            mSafetyCenterViewModel.executeIssueAction(mIssue, mAction, mTaskId);
                            mSafetyCenterViewModel
                                    .getInteractionLogger()
                                    .recordForIssue(
                                            isPrimaryButton()
                                                    ? Action.ISSUE_PRIMARY_ACTION_CLICKED
                                                    : Action.ISSUE_SECONDARY_ACTION_CLICKED,
                                            mIssue,
                                            mIsDismissed);
                        }
                    });

            maybeAddSpaceToView(buttonList);
            buttonList.addView(button);
        }

        /**
         * Configures "group-style" corners for this button, where the first button in the list has
         * large corners on top and the last button in the list has large corners on bottom.
         */
        @RequiresApi(UPSIDE_DOWN_CAKE)
        private void configureGroupStyleCorners(MaterialButton button) {
            button.setCornerRadiusResource(R.dimen.sc_button_corner_radius_small);
            ShapeAppearanceModel.Builder shapeAppearanceModelBuilder =
                    button.getShapeAppearanceModel().toBuilder();

            CornerSize largeCornerSize =
                    new AbsoluteCornerSize(
                            mContext.getResources()
                                    .getDimensionPixelSize(R.dimen.sc_button_corner_radius));
            if (isPrimaryButton()) {
                shapeAppearanceModelBuilder
                        .setTopLeftCornerSize(largeCornerSize)
                        .setTopRightCornerSize(largeCornerSize);
            }
            if (isLastButton()) {
                shapeAppearanceModelBuilder
                        .setBottomLeftCornerSize(largeCornerSize)
                        .setBottomRightCornerSize(largeCornerSize);
            }

            button.setShapeAppearanceModel(shapeAppearanceModelBuilder.build());
        }

        private void maybeAddSpaceToView(LinearLayout buttonList) {
            if (isPrimaryButton()) {
                return;
            }

            int marginRes =
                    mIsLargeScreen
                            ? R.dimen.sc_action_button_list_margin_large_screen
                            : R.dimen.sc_action_button_list_margin;
            int margin = mContext.getResources().getDimensionPixelSize(marginRes);
            Space space = new Space(mContext);
            space.setLayoutParams(new ViewGroup.LayoutParams(margin, margin));
            buttonList.addView(space);
        }

        private int getStyle() {
            return isFilled() ? R.attr.scActionButtonStyle : R.attr.scSecondaryActionButtonStyle;
        }

        private void setButtonColors(MaterialButton button) {
            if (isFilled()) {
                button.setBackgroundTintList(
                        ContextCompat.getColorStateList(
                                mContext,
                                getPrimaryButtonColorFromSeverity(mIssue.getSeverityLevel())));
            } else {
                button.setStrokeColor(
                        ContextCompat.getColorStateList(
                                mContext,
                                getSecondaryButtonStrokeColorFromSeverity(
                                        mIssue.getSeverityLevel())));
            }
        }

        private void setButtonLayout(Button button) {
            MarginLayoutParams layoutParams = new MarginLayoutParams(layoutWidth(), WRAP_CONTENT);
            button.setLayoutParams(layoutParams);
        }

        private int layoutWidth() {
            if (mIsLargeScreen) {
                return WRAP_CONTENT;
            } else {
                return MATCH_PARENT;
            }
        }

        @ColorRes
        private int getPrimaryButtonColorFromSeverity(int issueSeverityLevel) {
            return pickColorForSeverityLevel(
                    issueSeverityLevel,
                    R.color.safety_center_button_info,
                    R.color.safety_center_button_recommend,
                    R.color.safety_center_button_warn);
        }

        @ColorRes
        private int getSecondaryButtonStrokeColorFromSeverity(int issueSeverityLevel) {
            return pickColorForSeverityLevel(
                    issueSeverityLevel,
                    R.color.safety_center_outline_button_info,
                    R.color.safety_center_outline_button_recommend,
                    R.color.safety_center_outline_button_warn);
        }

        @ColorRes
        private int pickColorForSeverityLevel(
                int issueSeverityLevel,
                @ColorRes int infoColor,
                @ColorRes int recommendColor,
                @ColorRes int warnColor) {
            switch (issueSeverityLevel) {
                case SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK:
                    return infoColor;
                case SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION:
                    return recommendColor;
                case SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING:
                    return warnColor;
                default:
                    Log.w(
                            TAG,
                            String.format("Unexpected issueSeverityLevel: %s", issueSeverityLevel));
                    return infoColor;
            }
        }
    }
}
