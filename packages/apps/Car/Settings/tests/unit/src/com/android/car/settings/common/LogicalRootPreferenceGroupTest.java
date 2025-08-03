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

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LogicalRootPreferenceGroupTest {

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LogicalRootPreferenceGroup mPreferenceGroup;
    private PreferenceScreen mPreferenceScreen;

    @Before
    @UiThreadTest
    public void setUp() {
        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        mPreferenceScreen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceGroup = new LogicalRootPreferenceGroup(mContext);
        mPreferenceGroup.setKey("group1");
        mPreferenceScreen.addPreference(mPreferenceGroup);
    }

    @Test
    public void addPreference_toParent_notToGroup() {
        Preference preference = new Preference(mContext);
        preference.setTitle("Title1");
        mPreferenceGroup.addPreference(preference);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(0);
        assertThat(mPreferenceScreen.getPreferenceCount()).isEqualTo(2);
        assertThat(mPreferenceScreen.getPreference(1).getTitle())
                .isEqualTo(preference.getTitle());
    }

    @Test
    public void injectPreferencesToParent_whenOrderingAsAdded() {
        // adding second preference to screen
        mPreferenceScreen.setOrderingAsAdded(true);
        Preference preference0 = new Preference(mContext);
        preference0.setTitle("Title0");
        mPreferenceScreen.addPreference(preference0);

        // Preference to insert
        Preference insertedPreference1 = new Preference(mContext);
        insertedPreference1.setTitle("Title1");
        mPreferenceGroup.addPreference(insertedPreference1);

        Preference insertedPreference2 = new Preference(mContext);
        insertedPreference2.setTitle("Title2");
        mPreferenceGroup.addPreference(insertedPreference2);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(0);
        assertThat(mPreferenceScreen.getPreferenceCount()).isEqualTo(4);
        assertThat(mPreferenceScreen.getPreference(0).getOrder())
                .isEqualTo(0);
        assertThat(mPreferenceScreen.getPreference(1).getOrder())
                .isEqualTo(3);
        // this is insertedPreference1, but with order 1
        assertThat(mPreferenceScreen.getPreference(2).getOrder())
                .isEqualTo(1);
        assertThat(mPreferenceScreen.getPreference(2).getTitle())
                .isEqualTo(insertedPreference1.getTitle());

        // this is insertedPreference2, but with order 2
        assertThat(mPreferenceScreen.getPreference(3).getOrder())
                .isEqualTo(2);
        assertThat(mPreferenceScreen.getPreference(3).getTitle())
                .isEqualTo(insertedPreference2.getTitle());
    }

    @Test
    public void injectPreferencesToParent_NotOrderingAsAdded_fail() {
        // adding second preference to screen
        mPreferenceScreen.setOrderingAsAdded(false);
        Preference preference0 = new Preference(mContext);
        preference0.setTitle("Title1");
        mPreferenceScreen.addPreference(preference0);

        // Preference to append
        Preference appendedPreference = new Preference(mContext);
        appendedPreference.setTitle("Title2");
        mPreferenceGroup.addPreference(appendedPreference);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(0);
        assertThat(mPreferenceScreen.getPreferenceCount()).isEqualTo(3);
        assertThat(mPreferenceScreen.getPreference(0).getKey())
                .isEqualTo(mPreferenceGroup.getKey());
        assertThat(mPreferenceScreen.getPreference(1).getTitle())
                .isEqualTo(preference0.getTitle());
        assertThat(mPreferenceScreen.getPreference(2).getTitle())
                .isEqualTo(appendedPreference.getTitle());
    }
}
