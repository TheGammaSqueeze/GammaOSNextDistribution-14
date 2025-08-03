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

import static android.content.Intent.ACTION_SAFETY_CENTER;
import static android.content.Intent.FLAG_ACTIVITY_FORWARD_RESULT;
import static android.os.Build.VERSION_CODES.TIRAMISU;
import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
import static android.safetycenter.SafetyCenterManager.EXTRA_SAFETY_SOURCES_GROUP_ID;

import static com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_SIGNAL_NOTIFICATION_INTERACTION;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_SIGNAL_NOTIFICATION_INTERACTION__ACTION__NOTIFICATION_CLICKED;
import static com.android.permissioncontroller.safetycenter.SafetyCenterConstants.EXTRA_SETTINGS_FRAGMENT_ARGS_KEY;
import static com.android.permissioncontroller.safetycenter.SafetyCenterConstants.PERSONAL_PROFILE_SUFFIX;
import static com.android.permissioncontroller.safetycenter.SafetyCenterConstants.PRIVACY_SOURCES_GROUP_ID;
import static com.android.permissioncontroller.safetycenter.SafetyCenterConstants.WORK_PROFILE_SUFFIX;

import android.app.ActionBar;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.provider.Settings;
import android.safetycenter.SafetyCenterManager;
import android.safetycenter.config.SafetyCenterConfig;
import android.safetycenter.config.SafetySource;
import android.safetycenter.config.SafetySourcesGroup;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import com.android.permissioncontroller.Constants;
import com.android.permissioncontroller.PermissionControllerStatsLog;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.utils.Utils;
import com.android.permissioncontroller.safetycenter.ui.model.PrivacyControlsViewModel.Pref;
import com.android.settingslib.activityembedding.ActivityEmbeddingUtils;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import java.util.List;
import java.util.Objects;

/** Entry-point activity for SafetyCenter. */
@RequiresApi(TIRAMISU)
public final class SafetyCenterActivity extends CollapsingToolbarBaseActivity {

    private static final String TAG = SafetyCenterActivity.class.getSimpleName();
    private static final String PRIVACY_CONTROLS_ACTION = "android.settings.PRIVACY_CONTROLS";
    private static final String MENU_KEY_SAFETY_CENTER = "top_level_safety_center";
    private static final String EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_INTENT_URI =
            "android.provider.extra.SETTINGS_EMBEDDED_DEEP_LINK_INTENT_URI";
    private static final String EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_HIGHLIGHT_MENU_KEY =
            "android.provider.extra.SETTINGS_EMBEDDED_DEEP_LINK_HIGHLIGHT_MENU_KEY";
    private static final String EXTRA_PREVENT_TRAMPOLINE_TO_SETTINGS =
            "com.android.permissioncontroller.safetycenter.extra.PREVENT_TRAMPOLINE_TO_SETTINGS";

    private SafetyCenterManager mSafetyCenterManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSafetyCenterManager = getSystemService(SafetyCenterManager.class);

        if (maybeRedirectIfDisabled()) {
            return;
        }

        if (maybeRedirectIntoTwoPaneSettings()) {
            return;
        }

        Fragment frag;
        final boolean maybeOpenSubpage =
                SafetyCenterUiFlags.getShowSubpages()
                        && getIntent().getAction().equals(ACTION_SAFETY_CENTER);
        if (maybeOpenSubpage && getIntent().hasExtra(EXTRA_SAFETY_SOURCES_GROUP_ID)) {
            String groupId = getIntent().getStringExtra(EXTRA_SAFETY_SOURCES_GROUP_ID);
            frag = openRelevantSubpage(groupId);
        } else if (maybeOpenSubpage && getIntent().hasExtra(EXTRA_SETTINGS_FRAGMENT_ARGS_KEY)) {
            String preferenceKey = getIntent().getStringExtra(EXTRA_SETTINGS_FRAGMENT_ARGS_KEY);
            String groupId = getParentGroupId(preferenceKey);
            frag = openRelevantSubpage(groupId);
        } else if (getIntent().getAction().equals(PRIVACY_CONTROLS_ACTION)) {
            setTitle(R.string.privacy_controls_title);
            frag = PrivacyControlsFragment.newInstance();
        } else {
            frag = openHomepage();
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(com.android.settingslib.collapsingtoolbar.R.id.content_frame, frag)
                    .commitNow();
        }

        configureHomeButton();
    }

    @Override
    protected void onStart() {
        super.onStart();
        maybeRedirectIfDisabled();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // We don't set configChanges, but small screen size changes may still be delivered here.
        super.onConfigurationChanged(newConfig);
        configureHomeButton();
    }

    /** Decide whether a home/back button should be shown or not. */
    private void configureHomeButton() {
        ActionBar actionBar = getActionBar();
        Fragment frag = getSupportFragmentManager().findFragmentById(
                com.android.settingslib.collapsingtoolbar.R.id.content_frame);
        if (actionBar == null || frag == null) {
            return;
        }

        // Only the homepage can be considered a "second layer" page as it's the only one that
        // can be reached from the Settings menu. The other pages are only reachable using
        // a direct intent (e.g. notification, "first layer") and/or by navigating within Safety
        // Center ("third layer").
        // Note that the homepage can also be a "first layer" page, but that would only happen
        // if the activity is not embedded.
        boolean isSecondLayerPage = frag instanceof SafetyCenterScrollWrapperFragment;
        if (ActivityEmbeddingUtils.shouldHideNavigateUpButton(this, isSecondLayerPage)) {
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setHomeButtonEnabled(false);
        }
    }

    private boolean maybeRedirectIfDisabled() {
        if (mSafetyCenterManager == null || !mSafetyCenterManager.isSafetyCenterEnabled()) {
            Log.w(TAG, "Safety Center disabled, redirecting to settings page");
            startActivity(
                    new Intent(getActionToRedirectWhenDisabled())
                            .addFlags(FLAG_ACTIVITY_FORWARD_RESULT));
            finish();
            return true;
        }
        return false;
    }

    private String getActionToRedirectWhenDisabled() {
        boolean isPrivacyControls =
                TextUtils.equals(getIntent().getAction(), PRIVACY_CONTROLS_ACTION);
        if (isPrivacyControls) {
            return Settings.ACTION_PRIVACY_SETTINGS;
        }
        return Settings.ACTION_SETTINGS;
    }

    private boolean maybeRedirectIntoTwoPaneSettings() {
        return shouldUseTwoPaneSettings() && tryRedirectTwoPaneSettings();
    }

    private boolean shouldUseTwoPaneSettings() {
        if (!ActivityEmbeddingUtils.isEmbeddingActivityEnabled(this)) {
            return false;
        }

        Bundle extras = getIntent().getExtras();
        if (extras != null && extras.getBoolean(EXTRA_PREVENT_TRAMPOLINE_TO_SETTINGS, false)) {
            return false;
        }

        return isTaskRoot() && !ActivityEmbeddingUtils.isActivityEmbedded(this);
    }

    /** Return {@code true} if the redirection was attempted. */
    private boolean tryRedirectTwoPaneSettings() {
        Intent twoPaneIntent = getTwoPaneIntent();
        if (twoPaneIntent == null) {
            return false;
        }

        Log.i(TAG, "Safety Center restarting in Settings two-pane layout");
        startActivity(twoPaneIntent);
        finishAndRemoveTask();
        return true;
    }

    @Nullable
    private Intent getTwoPaneIntent() {
        Intent twoPaneIntent = ActivityEmbeddingUtils.buildEmbeddingActivityBaseIntent(this);
        if (twoPaneIntent == null) {
            return null;
        }

        twoPaneIntent.putExtras(getIntent());
        twoPaneIntent.putExtra(
                EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_INTENT_URI,
                getIntent().toUri(Intent.URI_INTENT_SCHEME));
        twoPaneIntent.putExtra(
                EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_HIGHLIGHT_MENU_KEY, MENU_KEY_SAFETY_CENTER);
        return twoPaneIntent;
    }

    private void logPrivacySourceMetric() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(Constants.EXTRA_PRIVACY_SOURCE)) {
            int privacySource = intent.getIntExtra(Constants.EXTRA_PRIVACY_SOURCE, -1);
            int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
            long sessionId =
                    intent.getLongExtra(Constants.EXTRA_SESSION_ID, Constants.INVALID_SESSION_ID);
            Log.i(
                    TAG,
                    "privacy source notification metric, source "
                            + privacySource
                            + " uid "
                            + uid
                            + " sessionId "
                            + sessionId);
            PermissionControllerStatsLog.write(
                    PRIVACY_SIGNAL_NOTIFICATION_INTERACTION,
                    privacySource,
                    uid,
                    PRIVACY_SIGNAL_NOTIFICATION_INTERACTION__ACTION__NOTIFICATION_CLICKED,
                    sessionId);
        }
    }

    private Fragment openHomepage() {
        logPrivacySourceMetric();
        setTitle(getString(R.string.safety_center_dashboard_page_title));
        return new SafetyCenterScrollWrapperFragment();
    }

    @RequiresApi(UPSIDE_DOWN_CAKE)
    private Fragment openRelevantSubpage(String groupId) {
        if (groupId.isEmpty()) {
            return openHomepage();
        }

        long sessionId = Utils.getOrGenerateSessionId(getIntent());
        if (Objects.equals(groupId, PRIVACY_SOURCES_GROUP_ID)) {
            logPrivacySourceMetric();
            return PrivacySubpageFragment.newInstance(sessionId);
        }

        return SafetyCenterSubpageFragment.newInstance(sessionId, groupId);
    }

    @RequiresApi(UPSIDE_DOWN_CAKE)
    private String getParentGroupId(String preferenceKey) {
        if (Pref.findByKey(preferenceKey) != null) {
            return PRIVACY_SOURCES_GROUP_ID;
        }

        SafetyCenterConfig safetyCenterConfig = mSafetyCenterManager.getSafetyCenterConfig();
        String[] splitKey;
        if (preferenceKey.endsWith(PERSONAL_PROFILE_SUFFIX)) {
            splitKey = preferenceKey.split("_" + PERSONAL_PROFILE_SUFFIX);
        } else if (preferenceKey.endsWith(WORK_PROFILE_SUFFIX)) {
            splitKey = preferenceKey.split("_" + WORK_PROFILE_SUFFIX);
        } else {
            return "";
        }

        if (safetyCenterConfig == null || splitKey.length == 0) {
            return "";
        }

        List<SafetySourcesGroup> groups = safetyCenterConfig.getSafetySourcesGroups();
        for (SafetySourcesGroup group : groups) {
            if (group.getType() != SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_STATEFUL) {
                // Hidden and static groups are not opened in a subpage.
                continue;
            }
            for (SafetySource source : group.getSafetySources()) {
                if (Objects.equals(source.getId(), splitKey[0])) {
                    return group.getId();
                }
            }
        }
        return "";
    }
}
