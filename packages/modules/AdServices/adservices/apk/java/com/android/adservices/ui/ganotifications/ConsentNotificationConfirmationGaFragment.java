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
package com.android.adservices.ui.ganotifications;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DISMISS_NOTIFICATION_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX;
import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.CONFIRMATION_PAGE_DISMISSED;
import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.CONFIRMATION_PAGE_DISPLAYED;
import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.CONFIRMATION_PAGE_OPT_IN_GOT_IT_BUTTON_CLICKED;
import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.CONFIRMATION_PAGE_OPT_IN_MORE_INFO_CLICKED;
import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.CONFIRMATION_PAGE_OPT_IN_SETTINGS_CLICKED;
import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.CONFIRMATION_PAGE_OPT_OUT_GOT_IT_BUTTON_CLICKED;
import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.CONFIRMATION_PAGE_OPT_OUT_MORE_INFO_CLICKED;
import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.CONFIRMATION_PAGE_OPT_OUT_SETTINGS_CLICKED;
import static com.android.adservices.ui.notifications.ConsentNotificationTrigger.NOTIFICATION_ID;
import static com.android.adservices.ui.settings.activities.AdServicesSettingsMainActivity.FROM_NOTIFICATION_KEY;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnScrollChangeListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;

import com.android.adservices.LogUtil;
import com.android.adservices.api.R;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.ui.notifications.ConsentNotificationActivity;
import com.android.adservices.ui.settings.activities.AdServicesSettingsMainActivity;

/**
 * Fragment for the confirmation view after accepting or rejecting to be part of Privacy Sandbox
 * Beta.
 */
// TODO(b/269798827): Enable for R.
// TODO(b/274955086): add logging for more button and scrolling
@RequiresApi(Build.VERSION_CODES.S)
public class ConsentNotificationConfirmationGaFragment extends Fragment {
    public static final String IS_FlEDGE_MEASUREMENT_INFO_VIEW_EXPANDED_KEY =
            "is_fledge_measurement_info_view_expanded";
    private boolean mIsInfoViewExpanded = false;
    private @Nullable ScrollToBottomController mScrollToBottomController;

    private boolean mTopicsOptIn;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        AdServicesApiConsent topicsConsent =
                ConsentManager.getInstance().getConsent(AdServicesApiType.TOPICS);
        mTopicsOptIn = topicsConsent != null ? topicsConsent.isGiven() : false;

        dismissNotificationIfNeeded();

        ConsentManager.getInstance().enable(requireContext(), AdServicesApiType.FLEDGE);
        ConsentManager.getInstance().enable(requireContext(), AdServicesApiType.MEASUREMENTS);
        return inflater.inflate(
                R.layout.consent_notification_fledge_measurement_fragment_eu, container, false);
    }

    private void dismissNotificationIfNeeded() {
        try {
            NotificationManagerCompat notificationManager =
                    NotificationManagerCompat.from(requireContext());
            notificationManager.cancel(NOTIFICATION_ID);
        } catch (Exception e) {
            LogUtil.e(e.toString());
            ErrorLogUtil.e(e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DISMISS_NOTIFICATION_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        setupListeners(savedInstanceState);

        ConsentNotificationActivity.handleAction(CONFIRMATION_PAGE_DISPLAYED, getContext());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        if (mScrollToBottomController != null) {
            mScrollToBottomController.saveInstanceState(savedInstanceState);
        }
        ConsentNotificationActivity.handleAction(CONFIRMATION_PAGE_DISMISSED, getContext());
    }

    private void setupListeners(Bundle savedInstanceState) {
        TextView howItWorksExpander =
                requireActivity().findViewById(R.id.how_it_works_fledge_measurement_expander);
        if (savedInstanceState != null) {
            setInfoViewState(
                    savedInstanceState.getBoolean(
                            IS_FlEDGE_MEASUREMENT_INFO_VIEW_EXPANDED_KEY, false));
        }
        howItWorksExpander.setOnClickListener(
                view -> {
                    if (mTopicsOptIn) {
                        ConsentNotificationActivity.handleAction(
                                CONFIRMATION_PAGE_OPT_IN_MORE_INFO_CLICKED, getContext());
                    } else {
                        ConsentNotificationActivity.handleAction(
                                CONFIRMATION_PAGE_OPT_OUT_MORE_INFO_CLICKED, getContext());
                    }

                    setInfoViewState(!mIsInfoViewExpanded);
                });

        Button leftControlButton =
                requireActivity().findViewById(R.id.leftControlButtonConfirmation);
        leftControlButton.setOnClickListener(
                view -> {
                    if (mTopicsOptIn) {
                        ConsentNotificationActivity.handleAction(
                                CONFIRMATION_PAGE_OPT_IN_SETTINGS_CLICKED, getContext());
                    } else {
                        ConsentNotificationActivity.handleAction(
                                CONFIRMATION_PAGE_OPT_OUT_SETTINGS_CLICKED, getContext());
                    }

                    // go to settings activity
                    Intent intent =
                            new Intent(requireActivity(), AdServicesSettingsMainActivity.class);
                    intent.putExtra(FROM_NOTIFICATION_KEY, true);
                    startActivity(intent);
                    requireActivity().finish();
                });

        Button rightControlButton =
                requireActivity().findViewById(R.id.rightControlButtonConfirmation);
        rightControlButton.setOnClickListener(
                view -> {
                    if (mTopicsOptIn) {
                        ConsentNotificationActivity.handleAction(
                                CONFIRMATION_PAGE_OPT_IN_GOT_IT_BUTTON_CLICKED, getContext());
                    } else {
                        ConsentNotificationActivity.handleAction(
                                CONFIRMATION_PAGE_OPT_OUT_GOT_IT_BUTTON_CLICKED, getContext());
                    }

                    // acknowledge and dismiss
                    requireActivity().finish();
                });

        ScrollView scrollView =
                requireView().findViewById(R.id.consent_notification_fledge_measurement_view);

        mScrollToBottomController =
                new ScrollToBottomController(
                        scrollView, leftControlButton, rightControlButton, savedInstanceState);
        mScrollToBottomController.bind();
        // check whether it can scroll vertically and update buttons after layout can be measured
        scrollView.post(() -> mScrollToBottomController.updateButtonsIfHasScrolledToBottom());
    }

    private void setInfoViewState(boolean expanded) {
        View text =
                requireActivity().findViewById(R.id.how_it_works_fledge_measurement_expanded_text);
        TextView expander =
                requireActivity().findViewById(R.id.how_it_works_fledge_measurement_expander);
        if (expanded) {
            mIsInfoViewExpanded = true;
            text.setVisibility(View.VISIBLE);
            expander.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    0, 0, R.drawable.ic_minimize, 0);
        } else {
            mIsInfoViewExpanded = false;
            text.setVisibility(View.GONE);
            expander.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_expand, 0);
        }
    }

    /**
     * Allows the positive, acceptance button to scroll the view.
     *
     * <p>When the positive button first appears it will show the text "More". When the user taps
     * the button, the view will scroll to the bottom. Once the view has scrolled to the bottom, the
     * button text will be replaced with the acceptance text. Once the text has changed, the button
     * will trigger the positive action no matter where the view is scrolled.
     */
    private class ScrollToBottomController implements OnScrollChangeListener {
        private static final String STATE_HAS_SCROLLED_TO_BOTTOM = "has_scrolled_to_bottom";
        private static final int SCROLL_DIRECTION_DOWN = 1;
        private static final double SCROLL_MULTIPLIER = 0.8;

        private final ScrollView mScrollContainer;
        private final Button mLeftControlButton;
        private final Button mRightControlButton;

        private boolean mHasScrolledToBottom;

        ScrollToBottomController(
                ScrollView scrollContainer,
                Button leftControlButton,
                Button rightControlButton,
                @Nullable Bundle savedInstanceState) {
            this.mScrollContainer = scrollContainer;
            this.mLeftControlButton = leftControlButton;
            this.mRightControlButton = rightControlButton;
            mHasScrolledToBottom =
                    savedInstanceState != null
                            && savedInstanceState.containsKey(STATE_HAS_SCROLLED_TO_BOTTOM)
                            && savedInstanceState.getBoolean(STATE_HAS_SCROLLED_TO_BOTTOM);
        }

        public void bind() {
            mScrollContainer.setOnScrollChangeListener(this);
            mRightControlButton.setOnClickListener(this::onMoreOrAcceptClicked);
            updateControlButtons();
        }

        public void saveInstanceState(Bundle bundle) {
            if (mHasScrolledToBottom) {
                bundle.putBoolean(STATE_HAS_SCROLLED_TO_BOTTOM, true);
            }
        }

        private void updateControlButtons() {
            if (mHasScrolledToBottom) {
                mLeftControlButton.setVisibility(View.VISIBLE);
                mRightControlButton.setText(
                        R.string.notificationUI_confirmation_right_control_button_text);
            } else {
                mLeftControlButton.setVisibility(View.INVISIBLE);
                mRightControlButton.setText(R.string.notificationUI_more_button_text);
            }
        }

        private void onMoreOrAcceptClicked(View view) {
            if (mHasScrolledToBottom) {
                // acknowledge and dismiss
                requireActivity().finish();
            } else {
                mScrollContainer.smoothScrollTo(
                        0,
                        mScrollContainer.getScrollY()
                                + (int) (mScrollContainer.getHeight() * SCROLL_MULTIPLIER));
            }
        }

        @Override
        public void onScrollChange(
                View view, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
            updateButtonsIfHasScrolledToBottom();
        }

        void updateButtonsIfHasScrolledToBottom() {
            if (!mScrollContainer.canScrollVertically(SCROLL_DIRECTION_DOWN)) {
                mHasScrolledToBottom = true;
                updateControlButtons();
            }
        }
    }
}
