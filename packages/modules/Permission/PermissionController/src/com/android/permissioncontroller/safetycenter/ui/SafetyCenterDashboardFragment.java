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

import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.safetycenter.SafetyCenterConstants.QUICK_SETTINGS_SAFETY_CENTER_FRAGMENT;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.safetycenter.SafetyCenterData;
import android.safetycenter.SafetyCenterEntry;
import android.safetycenter.SafetyCenterEntryGroup;
import android.safetycenter.SafetyCenterEntryOrGroup;
import android.safetycenter.SafetyCenterIssue;
import android.safetycenter.SafetyCenterStaticEntry;
import android.safetycenter.SafetyCenterStaticEntryGroup;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.recyclerview.widget.RecyclerView;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterUiData;
import com.android.permissioncontroller.safetycenter.ui.model.StatusUiData;
import com.android.safetycenter.internaldata.SafetyCenterBundles;
import com.android.safetycenter.resources.SafetyCenterResourcesApk;

import kotlin.Unit;

import java.util.List;
import java.util.Map;

/** Dashboard fragment for the Safety Center. */
@RequiresApi(TIRAMISU)
public final class SafetyCenterDashboardFragment extends SafetyCenterFragment {

    private static final String TAG = SafetyCenterDashboardFragment.class.getSimpleName();

    private static final String SAFETY_STATUS_KEY = "safety_status";
    private static final String ISSUES_GROUP_KEY = "issues_group";
    private static final String ENTRIES_GROUP_KEY = "entries_group";
    private static final String STATIC_ENTRIES_GROUP_KEY = "static_entries_group";
    private static final String SPACER_KEY = "spacer";

    private SafetyStatusPreference mSafetyStatusPreference;
    private final CollapsableGroupCardHelper mCollapsableGroupCardHelper =
            new CollapsableGroupCardHelper();
    private PreferenceGroup mIssuesGroup;
    private PreferenceGroup mEntriesGroup;
    private PreferenceGroup mStaticEntriesGroup;
    private boolean mIsQuickSettingsFragment;

    public SafetyCenterDashboardFragment() {}

    /**
     * Create instance of SafetyCenterDashboardFragment with the arguments set
     *
     * @param isQuickSettingsFragment Denoting if it is the quick settings fragment
     * @return SafetyCenterDashboardFragment with the arguments set
     */
    public static SafetyCenterDashboardFragment newInstance(
            long sessionId, boolean isQuickSettingsFragment) {
        Bundle args = new Bundle();
        args.putLong(EXTRA_SESSION_ID, sessionId);
        args.putBoolean(QUICK_SETTINGS_SAFETY_CENTER_FRAGMENT, isQuickSettingsFragment);

        SafetyCenterDashboardFragment frag = new SafetyCenterDashboardFragment();
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.safety_center_dashboard, rootKey);

        if (getArguments() != null) {
            mIsQuickSettingsFragment =
                    getArguments().getBoolean(QUICK_SETTINGS_SAFETY_CENTER_FRAGMENT, false);
        }
        mCollapsableGroupCardHelper.restoreState(savedInstanceState);

        mSafetyStatusPreference =
                requireNonNull(getPreferenceScreen().findPreference(SAFETY_STATUS_KEY));
        mSafetyStatusPreference.setViewModel(getSafetyCenterViewModel());

        mIssuesGroup = getPreferenceScreen().findPreference(ISSUES_GROUP_KEY);
        mEntriesGroup = getPreferenceScreen().findPreference(ENTRIES_GROUP_KEY);
        mStaticEntriesGroup = getPreferenceScreen().findPreference(STATIC_ENTRIES_GROUP_KEY);

        if (mIsQuickSettingsFragment) {
            getPreferenceScreen().removePreference(mEntriesGroup);
            mEntriesGroup = null;
            getPreferenceScreen().removePreference(mStaticEntriesGroup);
            mStaticEntriesGroup = null;
            Preference spacerPreference = getPreferenceScreen().findPreference(SPACER_KEY);
            getPreferenceScreen().removePreference(spacerPreference);
        }
        getSafetyCenterViewModel().getStatusUiLiveData().observe(this, this::updateStatus);

        prerenderCurrentSafetyCenterData();
    }

    @Override
    public RecyclerView onCreateRecyclerView(
            LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        RecyclerView recyclerView =
                super.onCreateRecyclerView(inflater, parent, savedInstanceState);

        if (mIsQuickSettingsFragment) {
            recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            recyclerView.setVerticalScrollBarEnabled(false);
        }
        return recyclerView;
    }

    // Set the default divider line between preferences to be transparent
    @Override
    public void setDivider(Drawable divider) {
        super.setDivider(new ColorDrawable(Color.TRANSPARENT));
    }

    @Override
    public void setDividerHeight(int height) {
        super.setDividerHeight(0);
    }

    @Override
    public void onResume() {
        super.onResume();
        getSafetyCenterViewModel().pageOpen();
    }

    @Override
    public void configureInteractionLogger() {
        InteractionLogger logger = getSafetyCenterViewModel().getInteractionLogger();
        logger.setSessionId(getSafetyCenterSessionId());
        logger.setViewType(mIsQuickSettingsFragment ? ViewType.QUICK_SETTINGS : ViewType.FULL);

        Intent intent = requireActivity().getIntent();
        logger.setNavigationSource(NavigationSource.fromIntent(intent));
        logger.setNavigationSensor(Sensor.fromIntent(intent));
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mCollapsableGroupCardHelper.saveState(outState);
    }

    private void updateStatus(StatusUiData statusUiData) {
        if (mIsQuickSettingsFragment) {
            SafetyCenterResourcesApk safetyCenterResourcesApk =
                    new SafetyCenterResourcesApk(requireContext());
            boolean hasPendingActions =
                    safetyCenterResourcesApk
                            .getStringByName("overall_severity_level_ok_review_summary")
                            .equals(statusUiData.getOriginalSummary().toString());

            statusUiData = statusUiData.copyForPendingActions(hasPendingActions);
        }

        mSafetyStatusPreference.setData(statusUiData);
    }

    @Override
    public void renderSafetyCenterData(@Nullable SafetyCenterUiData uiData) {
        if (uiData == null) return;
        SafetyCenterData data = uiData.getSafetyCenterData();

        Log.v(TAG, String.format("renderSafetyCenterData called with: %s", data));
        Context context = getContext();
        if (context == null) {
            return;
        }

        // TODO(b/208212820): Only update entries that have changed since last
        // update, rather than deleting and re-adding all.
        updateIssues(context, data.getIssues(), uiData.getResolvedIssues());

        if (!mIsQuickSettingsFragment) {
            updateSafetyEntries(context, data.getEntriesOrGroups());
            updateStaticSafetyEntries(context, data);
        }
    }

    private void updateIssues(
            Context context, List<SafetyCenterIssue> issues, Map<String, String> resolvedIssues) {
        mIssuesGroup.removeAll();
        getCollapsableIssuesCardHelper()
                .addIssues(
                        context,
                        getSafetyCenterViewModel(),
                        getChildFragmentManager(),
                        mIssuesGroup,
                        issues,
                        emptyList(),
                        resolvedIssues,
                        getActivity().getTaskId());
    }

    // TODO(b/208212820): Add groups and move to separate controller
    private void updateSafetyEntries(
            Context context, List<SafetyCenterEntryOrGroup> entriesOrGroups) {
        mEntriesGroup.removeAll();

        for (int i = 0, size = entriesOrGroups.size(); i < size; i++) {
            SafetyCenterEntryOrGroup entryOrGroup = entriesOrGroups.get(i);
            SafetyCenterEntry entry = entryOrGroup.getEntry();
            SafetyCenterEntryGroup group = entryOrGroup.getEntryGroup();

            boolean isFirstElement = i == 0;
            boolean isLastElement = i == size - 1;

            if (SafetyCenterUiFlags.getShowSubpages() && group != null) {
                mEntriesGroup.addPreference(
                        new SafetyHomepageEntryPreference(
                                context, group, getSafetyCenterSessionId()));
            } else if (entry != null) {
                addTopLevelEntry(context, entry, isFirstElement, isLastElement);
            } else if (group != null) {
                addGroupEntries(context, group, isFirstElement, isLastElement);
            }
        }
    }

    private void addTopLevelEntry(
            Context context,
            SafetyCenterEntry entry,
            boolean isFirstElement,
            boolean isLastElement) {
        mEntriesGroup.addPreference(
                new SafetyEntryPreference(
                        context,
                        PendingIntentSender.getTaskIdForEntry(
                                entry.getId(), getSameTaskSourceIds(), requireActivity()),
                        entry,
                        PositionInCardList.calculate(isFirstElement, isLastElement),
                        getSafetyCenterViewModel()));
    }

    private void addGroupEntries(
            Context context,
            SafetyCenterEntryGroup group,
            boolean isFirstCard,
            boolean isLastCard) {
        mEntriesGroup.addPreference(
                new SafetyGroupPreference(
                        context,
                        group,
                        mCollapsableGroupCardHelper::isGroupExpanded,
                        isFirstCard,
                        isLastCard,
                        (entryId) ->
                                PendingIntentSender.getTaskIdForEntry(
                                        entryId, getSameTaskSourceIds(), requireActivity()),
                        getSafetyCenterViewModel(),
                        (groupId) -> {
                            mCollapsableGroupCardHelper.onGroupExpanded(groupId);
                            return Unit.INSTANCE;
                        },
                        (groupId) -> {
                            mCollapsableGroupCardHelper.onGroupCollapsed(groupId);
                            return Unit.INSTANCE;
                        }));
    }

    private void updateStaticSafetyEntries(Context context, SafetyCenterData data) {
        mStaticEntriesGroup.removeAll();

        for (SafetyCenterStaticEntryGroup group : data.getStaticEntryGroups()) {
            PreferenceCategory category = new ComparablePreferenceCategory(context);
            category.setTitle(group.getTitle());
            mStaticEntriesGroup.addPreference(category);

            for (SafetyCenterStaticEntry entry : group.getStaticEntries()) {
                category.addPreference(
                        new StaticSafetyEntryPreference(
                                context,
                                requireActivity().getTaskId(),
                                entry,
                                SafetyCenterBundles.getStaticEntryId(data, entry),
                                getSafetyCenterViewModel()));
            }
        }
    }
}
