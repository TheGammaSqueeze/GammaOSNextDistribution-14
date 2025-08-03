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

package com.android.car.settings.wifi;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceViewHolder;

import com.android.car.settings.common.Logger;
import com.android.car.ui.preference.CarUiTwoActionIconPreference;
import com.android.wifitrackerlib.WifiEntry;

/** Renders a {@link WifiEntry} as a preference. */
public class WifiEntryPreference extends CarUiTwoActionIconPreference
        implements WifiEntry.WifiEntryCallback{
    private static final Logger LOG = new Logger(WifiEntryPreference.class);
    private static final int[] STATE_SECURED = {
            com.android.settingslib.R.attr.state_encrypted
    };
    private static final int[] STATE_NONE = {};
    private static final int[] sWifiSignalAttributes = {com.android.settingslib.R.attr.wifi_signal};

    private final WifiEntry mWifiEntry;
    @Nullable
    private final StateListDrawable mWifiSld;

    public WifiEntryPreference(Context context, WifiEntry wifiEntry) {
        super(context);
        LOG.d("creating preference for: " + wifiEntry);
        mWifiSld = (StateListDrawable) context.getTheme()
                .obtainStyledAttributes(sWifiSignalAttributes).getDrawable(0);
        if (mWifiSld != null) {
            mWifiSld.mutate();
        }
        mWifiEntry = wifiEntry;
        mWifiEntry.setListener(this);
        setKey(wifiEntry.getKey());
        setSecondaryActionVisible(false);
        setShowChevron(false);
        refresh();
    }

    /**
     * Returns the {@link WifiEntry} that is represented by this preference.
     */
    public WifiEntry getWifiEntry() {
        return mWifiEntry;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        setIcon(getWifiEntryIcon());
    }

    @Override
    public void onUpdated() {
        refresh();
    }

    private void refresh() {
        setTitle(mWifiEntry.getTitle());
        setSummary(mWifiEntry.getSummary(/* concise= */ false));
        setIcon(getWifiEntryIcon());
    }

    private Drawable getWifiEntryIcon() {
        if (mWifiSld == null) {
            LOG.w("wifiSld is null.");
            return null;
        }
        mWifiSld.setState(
                WifiUtil.isOpenNetwork(mWifiEntry.getSecurity())
                        ? STATE_NONE
                        : STATE_SECURED);
        Drawable drawable = mWifiSld.getCurrent();
        drawable.setLevel(mWifiEntry.getLevel());
        return drawable;
    }
}
