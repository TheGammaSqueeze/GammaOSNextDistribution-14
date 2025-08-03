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
package com.android.adservices.ui.u18notifications;

import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.LANDING_PAGE_DISMISSED;
import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.LANDING_PAGE_DISPLAYED;
import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.LANDING_PAGE_GOT_IT_CLICKED;
import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.LANDING_PAGE_MORE_BUTTON_CLICKED;
import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.LANDING_PAGE_SCROLLED;
import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.LANDING_PAGE_SCROLLED_TO_BOTTOM;
import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.LANDING_PAGE_SETTINGS_BUTTON_CLICKED;
import static com.android.adservices.ui.settings.activities.AdServicesSettingsMainActivity.FROM_NOTIFICATION_KEY;
import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.RVC_UX;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
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
import androidx.fragment.app.Fragment;

import com.android.adservices.api.R;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.ui.notifications.ConsentNotificationActivity;
import com.android.adservices.ui.settings.activities.AdServicesSettingsMainActivity;

/** Fragment for the Notification landing page for U18 users. */
@RequiresApi(Build.VERSION_CODES.S)
public class ConsentNotificationU18Fragment extends Fragment {
    public static final String IS_TOPICS_INFO_VIEW_EXPANDED_KEY = "is_topics_info_view_expanded";

    private @Nullable ScrollToBottomController mScrollToBottomController;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return setupActivity(inflater, container);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        setupListeners(savedInstanceState);
        ConsentNotificationActivity.handleAction(LANDING_PAGE_DISPLAYED, getContext());
        if (ConsentManager.getInstance().getUx() == RVC_UX) {
            ConsentManager.getInstance().enable(requireContext(), AdServicesApiType.MEASUREMENTS);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        ConsentNotificationActivity.handleAction(LANDING_PAGE_DISMISSED, getContext());
        if (mScrollToBottomController != null) {
            mScrollToBottomController.saveInstanceState(savedInstanceState);
        }
    }

    private View setupActivity(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.consent_notification_u18_fragment, container, false);
    }

    private void setupListeners(Bundle savedInstanceState) {
        // set up privacy policy link movement
        ((TextView) requireActivity().findViewById(R.id.learn_more_from_privacy_policy))
                .setMovementMethod(LinkMovementMethod.getInstance());

        // set up left control button and right control button
        Button leftControlButton = requireActivity().findViewById(R.id.leftControlButton);
        leftControlButton.setOnClickListener(
                view -> {
                    ConsentNotificationActivity.handleAction(
                            LANDING_PAGE_SETTINGS_BUTTON_CLICKED, getContext());

                    // go to settings activity
                    Intent intent =
                            new Intent(requireActivity(), AdServicesSettingsMainActivity.class);
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
                mRightControlButton.setText(R.string.notificationUI_right_control_button_text);
            } else {
                mLeftControlButton.setVisibility(View.INVISIBLE);
                mRightControlButton.setText(R.string.notificationUI_more_button_text);
            }
        }

        private void onMoreOrAcceptClicked(View view) {
            Context context = getContext();
            if (context == null) {
                return;
            }

            if (mHasScrolledToBottom) {

                ConsentNotificationActivity.handleAction(LANDING_PAGE_GOT_IT_CLICKED, getContext());

                if (FlagsFactory.getFlags().getRecordManualInteractionEnabled()) {
                    ConsentManager.getInstance()
                            .recordUserManualInteractionWithConsent(
                                    ConsentManager.MANUAL_INTERACTIONS_RECORDED);

                    // acknowledge and dismiss
                    requireActivity().finish();
                }
            } else {
                ConsentNotificationActivity.handleAction(
                        LANDING_PAGE_MORE_BUTTON_CLICKED, getContext());

                mScrollContainer.smoothScrollTo(
                        0,
                        mScrollContainer.getScrollY()
                                + (int) (mScrollContainer.getHeight() * SCROLL_MULTIPLIER));
            }
        }

        @Override
        public void onScrollChange(
                View view, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
            ConsentNotificationActivity.handleAction(LANDING_PAGE_SCROLLED, getContext());
            updateButtonsIfHasScrolledToBottom();
        }

        void updateButtonsIfHasScrolledToBottom() {
            if (!mScrollContainer.canScrollVertically(SCROLL_DIRECTION_DOWN)) {
                ConsentNotificationActivity.handleAction(
                        LANDING_PAGE_SCROLLED_TO_BOTTOM, getContext());
                mHasScrolledToBottom = true;
                updateControlButtons();
            }
        }
    }
}
