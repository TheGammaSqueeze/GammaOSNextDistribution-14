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

package com.android.car.settings.common;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

/**
 * {@link PreferenceGroup} which does not display a title, icon, or summary. This allows for
 * logical grouping of preferences without indications in the UI.
 *
 * <P>Items added to this group will automatically added into the parent of this group. When the
 * parent is set to isOrderingAsAdded the order will be preserved and items will be placed after
 * this group and before any other already added items of the parent. The order how children are
 * added will be also preserved.
 *
 * <P>Example:
 * <ul>
 *     <li>A</li>
 *     <li>B</li>
 * </ul>
 * A.addPreference(A_1);
 * A.addPreference(A_2);
 *
 * will result in
 * <ul>
 *     <li>A
 *         <ul>
 *             <li>A_1</li>
 *             <li>A_2</li>
 *         </ul>
 *     </li>
 *     <li>B</li>
 * </ul>
 */
public class LogicalRootPreferenceGroup extends LogicalPreferenceGroup {

    private int mInsertedPreferences = 0;

    public LogicalRootPreferenceGroup(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public LogicalRootPreferenceGroup(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LogicalRootPreferenceGroup(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LogicalRootPreferenceGroup(Context context) {
        super(context);
    }

    @Override
    public boolean addPreference(Preference preference) {
        updateShowChevron(preference);

        boolean result = getParent().addPreference(preference);
        updateOrder(preference);
        return result;
    }

    private void updateOrder(Preference newPreference) {
        if (!getParent().isOrderingAsAdded()) {
            return;
        }

        int preferenceOrder = getOrder();
        for (int index = 0; index < getParent().getPreferenceCount(); index++) {
            Preference preference = getParent().getPreference(index);
            if (preference != this
                    && preference != newPreference
                    && preference.getOrder() > preferenceOrder + mInsertedPreferences) {
                preference.setOrder(preference.getOrder() + 1);
            }
        }
        mInsertedPreferences++;
        newPreference.setOrder(preferenceOrder + mInsertedPreferences);
        notifyHierarchyChanged();
    }
}
