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

import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.CONFIRMATION_PAGE_DISMISSED;
import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.CONFIRMATION_PAGE_DISPLAYED;
import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.CONFIRMATION_PAGE_OPT_OUT_MORE_INFO_CLICKED;
import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.CONFIRMATION_PAGE_OPT_OUT_SETTINGS_CLICKED;
import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.LANDING_PAGE_ADDITIONAL_INFO_2_CLICKED;
import static com.android.adservices.ui.settings.activities.AdServicesSettingsMainActivity.FROM_NOTIFICATION_KEY;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import com.android.adservices.api.R;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.ui.UxUtil;
import com.android.adservices.ui.notifications.ConsentNotificationActivity;
import com.android.adservices.ui.settings.activities.AdServicesSettingsMainActivity;

/**
 * Fragment for the confirmation view after accepting or rejecting to be part of Privacy Sandbox
 * Beta.
 */
@RequiresApi(Build.VERSION_CODES.S)
public class ConsentNotificationPasFragment extends Fragment {
    public static final String INFO_VIEW_EXPANDED_1 = "info_view_expanded_1";
    public static final String INFO_VIEW_EXPANDED_2 = "info_view_expanded_2";
    private boolean mIsInfoViewExpanded1 = false;
    private boolean mIsInfoViewExpanded2 = false;
    private boolean mIsEUDevice;
    private boolean mIsRenotify;
    private boolean mIsFirstTimeRow;
    private @Nullable ScrollToBottomController mScrollToBottomController;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View inflatedView;
        mIsEUDevice = UxUtil.isEeaDevice(requireActivity(), getContext());
        mIsRenotify = isFledgeOrMsmtEnabled();
        mIsFirstTimeRow = false;
        if (mIsRenotify) {
            // renotify version
            inflatedView =
                    inflater.inflate(R.layout.consent_notification_screen_1_pas, container, false);
            TextView title = inflatedView.findViewById(R.id.notification_title);
            title.setText(R.string.notificationUI_pas_renotify_header_title);
        } else if (mIsEUDevice) {
            // first-time version
            inflatedView =
                    inflater.inflate(R.layout.consent_notification_screen_1_pas, container, false);
        } else {
            // combined version
            mIsFirstTimeRow = true;
            inflatedView =
                    inflater.inflate(
                            R.layout.consent_notification_pas_first_time_row, container, false);
        }
        return inflatedView;
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
        TextView howItWorksExpander = requireActivity().findViewById(R.id.how_it_works_expander);
        if (savedInstanceState != null) {
            setInfoViewState1(savedInstanceState.getBoolean(INFO_VIEW_EXPANDED_1, false));
        }
        howItWorksExpander.setOnClickListener(
                view -> {
                    ConsentNotificationActivity.handleAction(
                            CONFIRMATION_PAGE_OPT_OUT_MORE_INFO_CLICKED, getContext());

                    setInfoViewState1(!mIsInfoViewExpanded1);
                });
        ((TextView) requireActivity().findViewById(R.id.learn_more_from_privacy_policy))
                .setMovementMethod(LinkMovementMethod.getInstance());

        if (!mIsFirstTimeRow) {
            TextView howItWorksExpander2 =
                    requireActivity().findViewById(R.id.how_it_works_expander2);
            if (savedInstanceState != null) {
                setInfoViewState2(savedInstanceState.getBoolean(INFO_VIEW_EXPANDED_2, false));
            }
            howItWorksExpander2.setOnClickListener(
                    view -> {
                        ConsentNotificationActivity.handleAction(
                                LANDING_PAGE_ADDITIONAL_INFO_2_CLICKED, getContext());
                        setInfoViewState2(!mIsInfoViewExpanded2);
                    });
            ((TextView) requireActivity().findViewById(R.id.learn_more_from_privacy_policy2))
                    .setMovementMethod(LinkMovementMethod.getInstance());
        }

        Button leftControlButton = requireActivity().findViewById(R.id.leftControlButton);
        leftControlButton.setOnClickListener(
                view -> {
                    ConsentNotificationActivity.handleAction(
                            CONFIRMATION_PAGE_OPT_OUT_SETTINGS_CLICKED, getContext());

                    // go to settings activity
                    Intent intent =
                            new Intent(requireActivity(), AdServicesSettingsMainActivity.class);
                    // users should be able to go back to notification if clicked manage settings
                    intent.putExtra(FROM_NOTIFICATION_KEY, true);
                    startActivity(intent);
                    requireActivity().finish();
                });

        Button rightControlButton = requireActivity().findViewById(R.id.rightControlButton);
        ScrollView scrollView = requireView().findViewById(R.id.notification_fragment_scrollview);
        mScrollToBottomController =
                new ScrollToBottomController(
                        scrollView, leftControlButton, rightControlButton, savedInstanceState);
        mScrollToBottomController.bind();
        // check whether it can scroll vertically and update buttons after layout can be measured
        scrollView.post(() -> mScrollToBottomController.updateButtonsIfHasScrolledToBottom());
    }

    private void setInfoViewState1(boolean expanded) {
        View text = requireActivity().findViewById(R.id.how_it_works_expanded_text);
        TextView expander = requireActivity().findViewById(R.id.how_it_works_expander);
        mIsInfoViewExpanded1 = infoViewChanger(expanded, text, expander);
    }

    private void setInfoViewState2(boolean expanded) {
        View text = requireActivity().findViewById(R.id.how_it_works_expanded_text2);
        TextView expander = requireActivity().findViewById(R.id.how_it_works_expander2);
        mIsInfoViewExpanded2 = infoViewChanger(expanded, text, expander);
    }

    // returns the state of the info view
    private boolean infoViewChanger(boolean expanded, View text, TextView expander) {
        if (expanded) {
            text.setVisibility(View.VISIBLE);
            expander.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    0, 0, R.drawable.ic_chevron_up, 0);
        } else {
            text.setVisibility(View.GONE);
            expander.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    0, 0, R.drawable.ic_chevron_down, 0);
        }
        return expanded;
    }

    // helper method to start topics consent notification screen for EU users
    private void startTopicsConsentNotificationFragment() {
        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(
                        R.id.fragment_container_view,
                        ConsentNotificationGaV2Screen2Fragment.class,
                        null)
                .setReorderingAllowed(true)
                .addToBackStack(null)
                .commit();
    }

    private static boolean isFledgeOrMsmtEnabled() {
        ConsentManager consentManager = ConsentManager.getInstance();
        return consentManager.getConsent(AdServicesApiType.FLEDGE).isGiven()
                || consentManager.getConsent(AdServicesApiType.MEASUREMENTS).isGiven();
    }

    /**
     * Allows the positive, acceptance button to scroll the view.
     *
     * <p>When the positive button first appears it will show the text "More". When the user taps
     * the button, the view will scroll to the bottom. Once the view has scrolled to the bottom, the
     * button text will be replaced with the acceptance text. Once the text has changed, the button
     * will trigger the positive action no matter where the view is scrolled.
     */
    private class ScrollToBottomController implements View.OnScrollChangeListener {
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
                // screen 2
                if (!mIsRenotify && mIsEUDevice) {
                    startTopicsConsentNotificationFragment();
                } else {
                    requireActivity().finish();
                }
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
