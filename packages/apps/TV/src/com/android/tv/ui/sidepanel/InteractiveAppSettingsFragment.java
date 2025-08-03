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
package com.android.tv.ui.sidepanel;

import com.android.tv.R;
import com.android.tv.util.TvSettings;
import java.util.ArrayList;
import java.util.List;

public class InteractiveAppSettingsFragment extends SideFragment {
    private static final String TRACKER_LABEL = "Interactive Application Settings";
    @Override
    protected String getTitle() {
        return getString(R.string.interactive_app_settings);
    }
    @Override
    public String getTrackerLabel() {
        return TRACKER_LABEL;
    }
    @Override
    protected List<Item> getItemList() {
        List<Item> items = new ArrayList<>();
        items.add(
                new SwitchItem(
                        getString(R.string.tv_iapp_on),
                        getString(R.string.tv_iapp_off)) {
                    @Override
                    protected void onUpdate() {
                        super.onUpdate();
                        setChecked(TvSettings.isTvIAppOn(getContext()));
                    }
                    @Override
                    protected void onSelected() {
                        super.onSelected();
                        boolean checked = isChecked();
                        TvSettings.setTvIAppOn(getContext(), checked);
                    }
                });
        return items;
    }
}
