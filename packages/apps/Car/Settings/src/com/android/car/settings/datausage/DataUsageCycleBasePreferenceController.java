/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.car.settings.datausage;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.text.format.DateUtils;

import androidx.annotation.CallSuper;
import androidx.annotation.VisibleForTesting;
import androidx.preference.ListPreference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.settingslib.net.DataUsageController;
import com.android.settingslib.net.NetworkCycleData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base preference controller for loading data cycle information
 *
 * @param <T> type of the {@link NetworkCycleData} objects that will be loaded.
 */
public abstract class DataUsageCycleBasePreferenceController<T extends NetworkCycleData> extends
        PreferenceController<ListPreference> {
    private List<T> mList;
    private boolean mIsLoaded = false;
    private DataCyclePickedListener<T> mDataCyclePickedListener;
    private Map<CharSequence, T> mDataUsages = new HashMap<>();
    private DataUsageController.DataUsageInfo mDataUsageInfo;

    /** A listener that is called when a data cycle is selected.
     *
     * @param <T> type of the {@link NetworkCycleData} objects that was loaded.
     */
    public interface DataCyclePickedListener<T> {
        /** Callback when a new data cycle is selected */
        void onDataCyclePicked(String cycle, Map<CharSequence, T> usages);
    }

    public DataUsageCycleBasePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<ListPreference> getPreferenceType() {
        return ListPreference.class;
    }

    @Override
    @CallSuper
    protected void updateState(ListPreference preference) {
        CharSequence entry = getPreference().getEntry();
        if (entry != null) {
            preference.setEnabled(true);
            getPreference().setSummary(entry);
        } else {
            preference.setEnabled(false);
            getPreference().setSummary(mDataUsageInfo.period);
        }
        String cycle = getPreference().getValue();
        if (mIsLoaded) {
            mDataCyclePickedListener.onDataCyclePicked(cycle, mDataUsages);
        }
    }

    @Override
    @CallSuper
    protected void checkInitialized() {
        if (mDataCyclePickedListener == null || mDataUsageInfo == null) {
            throw new IllegalStateException("DataCyclePickedListener and DataUsageInfo should be "
                    + "set before calling this function.");
        }
    }

    /** Set DataCyclePickedListener */
    public DataUsageCycleBasePreferenceController<T> setDataCyclePickedListener(
            DataCyclePickedListener<T> listener) {
        mDataCyclePickedListener = listener;
        return this;
    }

    /** Set DataUsageInfo */
    public void setDataUsageInfo(DataUsageController.DataUsageInfo dataUsageInfo) {
        mDataUsageInfo = dataUsageInfo;
    }

    /** Called to parse loaded data */
    protected void onLoaded(List<T> data) {
        mList = data;
        // TODO: (b/202973988) The data loaded is empty occasionally. In that case, we will not
        //  refresh the UI or mark it as loaded.
        if (mList != null && !mList.isEmpty()) {
            updateCycle(getPreference());
            mIsLoaded = true;
            refreshUi();
        }
    }

    private void updateCycle(ListPreference preference) {
        if (mIsLoaded) {
            return;
        }
        ArrayList<CharSequence> entries = new ArrayList<>();
        ArrayList<CharSequence> entryValues = new ArrayList<>();
        for (int i = 0; i < mList.size(); i++) {
            T networkCycleData = mList.get(i);
            CharSequence entry = formatDate(
                    networkCycleData.getStartTime(), networkCycleData.getEndTime());
            CharSequence entryValue = String.valueOf(i);
            entries.add(entry);
            entryValues.add(entryValue);
            mDataUsages.put(entryValue, networkCycleData);
        }
        preference.setEntries(entries.toArray(new CharSequence[entries.size()]));
        preference.setEntryValues(entryValues.toArray(new CharSequence[entries.size()]));
        preference.setSummary(entries.get(0));
        preference.setValueIndex(0);
    }

    @VisibleForTesting
    String formatDate(long startTime, long endTime) {
        return DateUtils.formatDateRange(getContext(), startTime, endTime,
                DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_DATE);
    }
}
