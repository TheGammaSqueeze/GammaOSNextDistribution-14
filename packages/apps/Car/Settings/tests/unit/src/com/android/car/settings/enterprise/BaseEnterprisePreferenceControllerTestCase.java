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
package com.android.car.settings.enterprise;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import static java.util.stream.Collectors.toList;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.common.FragmentController;

import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

abstract class BaseEnterprisePreferenceControllerTestCase extends BaseEnterpriseTestCase {
    protected final String mPreferenceKey = "Da Key";
    protected final CarUxRestrictions mUxRestrictions = new CarUxRestrictions.Builder(
            /* reqOpt= */ true, CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED, /* time= */ 0)
                    .build();
    @Mock
    protected FragmentController mFragmentController;

    protected static final void assertPreferenceSummary(Preference preference, String summary) {
        assertWithMessage("(preference %s).getSummary()", preference).that(preference.getSummary())
                .isEqualTo(summary);
    }

    protected void verifyPreferenceTitles(DummyPreferenceGroup preferenceGroup,
            CharSequence... titles) {
        assertThat(preferenceGroup.getPreferences().stream()
                .map(p -> p.getTitle()).collect(toList())).containsExactly(titles);
    }

    protected static final void verifyPreferenceVisibleSet(Preference preference, boolean visible) {
        verify(preference).setVisible(visible);
    }

    protected static final void verifyPreferenceTitleSet(Preference preference,
            CharSequence title) {
        verify(preference).setTitle(title);
    }

    protected static final void verifyPreferenceTitleNeverSet(Preference preference) {
        verify(preference, never()).setTitle(any());
    }

    protected static final void verifyPreferenceSummarySet(Preference preference,
            CharSequence summary) {
        verify(preference).setSummary(summary);
    }

    protected static final void verifyPreferenceSummarySet(Preference preference, int resId) {
        verify(preference).setSummary(resId);
    }

    protected static final void verifyPreferenceSummaryNeverSet(Preference preference) {
        verify(preference, never()).setSummary(any());
    }

    protected static final void verifyPreferenceIconSet(Preference preference) {
        verify(preference).setIcon(notNull());
    }

    protected static final void verifyPreferenceIconNeverSet(Preference preference) {
        verify(preference, never()).setIcon(notNull());
    }

    protected static final void verifyPreferenceDisabled(Preference preference) {
        verify(preference).setEnabled(false);
    }

    protected static final void verifyPreferenceEnabled(Preference preference) {
        verify(preference).setEnabled(true);
    }

    static final class DummyPreferenceGroup extends PreferenceGroup {

        private final List<Preference> mList = new ArrayList<>();

        DummyPreferenceGroup(Context context) {
            super(context, null);
        }

        @Override public void removeAll() {
            mList.clear();
        }

        @Override public boolean addPreference(Preference preference) {
            mList.add(preference);
            return true;
        }

        @Override public int getPreferenceCount() {
            return mList.size();
        }

        @Override public Preference getPreference(int index) {
            return mList.get(index);
        }

        public List<Preference> getPreferences() {
            return mList;
        }
    }
}
