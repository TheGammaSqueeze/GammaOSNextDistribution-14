/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.car.settings.common.ExtraSettingsLoader.META_DATA_PREFERENCE_IS_TOP_LEVEL;
import static com.android.settingslib.drawer.TileUtils.META_DATA_KEY_ORDER;
import static com.android.settingslib.drawer.TileUtils.META_DATA_PREFERENCE_ICON_URI;
import static com.android.settingslib.drawer.TileUtils.META_DATA_PREFERENCE_SUMMARY;
import static com.android.settingslib.drawer.TileUtils.META_DATA_PREFERENCE_SUMMARY_URI;
import static com.android.settingslib.drawer.TileUtils.META_DATA_PREFERENCE_TITLE;
import static com.android.settingslib.drawer.TileUtils.META_DATA_PREFERENCE_TITLE_URI;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class ExtraSettingsLoaderTest {
    private static final String META_DATA_PREFERENCE_CATEGORY = "com.android.settings.category";
    private static final String FAKE_CATEGORY = "fake_category";
    private static final String FAKE_TITLE = "fake_title";
    private static final String FAKE_TITLE1 = "fake_title1";
    private static final String FAKE_TITLE2 = "fake_title2";
    private static final String FAKE_SUMMARY = "fake_summary";
    private static final String TEST_CONTENT_PROVIDER =
            "content://com.android.car.settings.testutils.TestContentProvider";
    private static final String DEVICE_CATEGORY = "com.android.settings.category.ia.device";

    private Context mContext = ApplicationProvider.getApplicationContext();
    private ExtraSettingsLoader mExtraSettingsLoader;

    @Mock
    private PackageManager mPm;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mExtraSettingsLoader = new ExtraSettingsLoader(mContext);
        mExtraSettingsLoader.setPackageManager(mPm);
    }

    private ResolveInfo createResolveInfo(String packageName, String className, Bundle metaData,
            boolean isSystem) {
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = packageName;
        activityInfo.name = className;
        activityInfo.metaData = metaData;

        ResolveInfo resolveInfoSystem = new ResolveInfo();
        resolveInfoSystem.system = isSystem;
        resolveInfoSystem.activityInfo = activityInfo;

        return resolveInfoSystem;
    }

    private Map<Preference, Bundle> executeLoadPreferences(List<ResolveInfo> resolveInfoList,
            String category) {
        Intent intent = new Intent();
        intent.putExtra(META_DATA_PREFERENCE_CATEGORY, category);

        when(mPm.queryIntentActivitiesAsUser(eq(intent), eq(PackageManager.GET_META_DATA),
                anyInt())).thenReturn(resolveInfoList);
        return mExtraSettingsLoader.loadPreferences(intent);
    }

    @Test
    public void testLoadPreference_uriResources_shouldNotLoadStaticResources() {
        Bundle bundle = new Bundle();
        bundle.putString(META_DATA_PREFERENCE_TITLE, FAKE_TITLE);
        bundle.putString(META_DATA_PREFERENCE_SUMMARY, FAKE_SUMMARY);
        bundle.putString(META_DATA_PREFERENCE_CATEGORY, FAKE_CATEGORY);
        bundle.putString(META_DATA_PREFERENCE_TITLE_URI, TEST_CONTENT_PROVIDER);
        bundle.putString(META_DATA_PREFERENCE_SUMMARY_URI, TEST_CONTENT_PROVIDER);
        bundle.putString(META_DATA_PREFERENCE_ICON_URI, TEST_CONTENT_PROVIDER);

        ResolveInfo resolveInfoSystem = createResolveInfo("package_name", "class_name",
                bundle, /* isSystem= */ true);

        Map<Preference, Bundle> preferenceToBundleMap =
                executeLoadPreferences(Collections.singletonList(resolveInfoSystem), FAKE_CATEGORY);

        assertThat(preferenceToBundleMap).hasSize(1);

        for (Preference p : preferenceToBundleMap.keySet()) {
            assertThat(p.getTitle()).isNull();
            assertThat(p.getSummary()).isNull();
            assertThat(p.getIcon()).isNull();
        }
    }

    @Test
    public void testLoadPreference_sortPreferences_byMetadata() {
        Bundle bundle1 = new Bundle();
        bundle1.putString(META_DATA_PREFERENCE_TITLE, FAKE_TITLE1);
        bundle1.putString(META_DATA_PREFERENCE_CATEGORY, FAKE_CATEGORY);
        bundle1.putInt(META_DATA_KEY_ORDER, 1);

        ResolveInfo resolveInfoSystem1 = createResolveInfo("package_name1", "class_name1",
                bundle1, /* isSystem= */ true);

        Bundle bundle2 = new Bundle();
        bundle2.putString(META_DATA_PREFERENCE_TITLE, FAKE_TITLE2);
        bundle2.putString(META_DATA_PREFERENCE_CATEGORY, FAKE_CATEGORY);
        bundle2.putInt(META_DATA_KEY_ORDER, 2);

        ResolveInfo resolveInfoSystem2 = createResolveInfo("package_name2", "class_name2",
                bundle2, /* isSystem= */ true);

        List<ResolveInfo> resolveInfoList = new ArrayList<>();
        resolveInfoList.add(resolveInfoSystem1);
        resolveInfoList.add(resolveInfoSystem2);

        Map<Preference, Bundle> preferenceToBundleMap =
                executeLoadPreferences(resolveInfoList, FAKE_CATEGORY);

        assertThat(preferenceToBundleMap).hasSize(2);

        Iterator<Preference> iter = preferenceToBundleMap.keySet().iterator();
        assertThat(iter.next().getTitle().toString()).isEqualTo(FAKE_TITLE2);
        assertThat(iter.next().getTitle().toString()).isEqualTo(FAKE_TITLE1);
    }

    @Test
    public void testLoadPreference_sortPreferences_byPackageName() {
        Bundle bundle1 = new Bundle();
        bundle1.putString(META_DATA_PREFERENCE_TITLE, FAKE_TITLE1);
        bundle1.putString(META_DATA_PREFERENCE_CATEGORY, FAKE_CATEGORY);

        ResolveInfo resolveInfoSystem1 = createResolveInfo("package_name1", "class_name1",
                bundle1, /* isSystem= */ true);

        Bundle bundle2 = new Bundle();
        bundle2.putString(META_DATA_PREFERENCE_TITLE, FAKE_TITLE2);
        bundle2.putString(META_DATA_PREFERENCE_CATEGORY, FAKE_CATEGORY);

        ResolveInfo resolveInfoSystem2 = createResolveInfo("package_name2", "class_name2",
                bundle2, /* isSystem= */ true);

        List<ResolveInfo> resolveInfoList = new ArrayList<>();
        resolveInfoList.add(resolveInfoSystem2);
        resolveInfoList.add(resolveInfoSystem1);

        Map<Preference, Bundle> preferenceToBundleMap =
                executeLoadPreferences(resolveInfoList, FAKE_CATEGORY);

        assertThat(preferenceToBundleMap).hasSize(2);

        Iterator<Preference> iter = preferenceToBundleMap.keySet().iterator();
        assertThat(iter.next().getTitle().toString()).isEqualTo(FAKE_TITLE1);
        assertThat(iter.next().getTitle().toString()).isEqualTo(FAKE_TITLE2);
    }

    @Test
    public void testLoadPreference_sortPreferences_prioritizeMetadata() {
        Bundle bundle1 = new Bundle();
        bundle1.putString(META_DATA_PREFERENCE_TITLE, FAKE_TITLE1);
        bundle1.putString(META_DATA_PREFERENCE_CATEGORY, FAKE_CATEGORY);

        ResolveInfo resolveInfoSystem1 = createResolveInfo("package_name1", "class_name1",
                bundle1, /* isSystem= */ true);

        Bundle bundle2 = new Bundle();
        bundle2.putString(META_DATA_PREFERENCE_TITLE, FAKE_TITLE2);
        bundle2.putString(META_DATA_PREFERENCE_CATEGORY, FAKE_CATEGORY);
        bundle2.putInt(META_DATA_KEY_ORDER, 2);

        ResolveInfo resolveInfoSystem2 = createResolveInfo("package_name2", "class_name2",
                bundle2, /* isSystem= */ true);

        List<ResolveInfo> resolveInfoList = new ArrayList<>();
        resolveInfoList.add(resolveInfoSystem1);
        resolveInfoList.add(resolveInfoSystem2);

        Map<Preference, Bundle> preferenceToBundleMap =
                executeLoadPreferences(resolveInfoList, FAKE_CATEGORY);

        assertThat(preferenceToBundleMap).hasSize(2);

        Iterator<Preference> iter = preferenceToBundleMap.keySet().iterator();
        assertThat(iter.next().getTitle().toString()).isEqualTo(FAKE_TITLE2);
        assertThat(iter.next().getTitle().toString()).isEqualTo(FAKE_TITLE1);
    }

    @Test
    public void testLoadPreference_isTopLevel_topLevelMetadataSet() {
        Bundle bundle = new Bundle();
        bundle.putString(META_DATA_PREFERENCE_TITLE, FAKE_TITLE);
        bundle.putString(META_DATA_PREFERENCE_SUMMARY, FAKE_SUMMARY);
        bundle.putString(META_DATA_PREFERENCE_CATEGORY, DEVICE_CATEGORY);

        ResolveInfo resolveInfo = createResolveInfo("package_name", "class_name",
                bundle, /* isSystem= */ true);

        Map<Preference, Bundle> preferenceToBundleMap = executeLoadPreferences(
                Collections.singletonList(resolveInfo), DEVICE_CATEGORY);

        assertThat(preferenceToBundleMap).hasSize(1);
        for (Bundle b : preferenceToBundleMap.values()) {
            assertThat(b.getBoolean(META_DATA_PREFERENCE_IS_TOP_LEVEL)).isTrue();
        }
    }

    @Test
    public void testLoadPreference_notSystem_notLoaded() {
        Bundle bundle = new Bundle();
        bundle.putString(META_DATA_PREFERENCE_TITLE, FAKE_TITLE);
        bundle.putString(META_DATA_PREFERENCE_CATEGORY, FAKE_CATEGORY);

        ResolveInfo resolveInfo = createResolveInfo("package_name1", "class_name1",
                bundle, /* isSystem= */ false);

        Map<Preference, Bundle> preferenceToBundleMap =
                executeLoadPreferences(Collections.singletonList(resolveInfo), FAKE_CATEGORY);

        assertThat(preferenceToBundleMap).hasSize(0);
    }

    @Test
    public void testLoadPreference_noMetaData_notLoaded() {
        ResolveInfo resolveInfo = createResolveInfo("package_name1", "class_name1",
                /* metaData= */ null, /* isSystem= */ true);

        Map<Preference, Bundle> preferenceToBundleMap =
                executeLoadPreferences(Collections.singletonList(resolveInfo), FAKE_CATEGORY);

        assertThat(preferenceToBundleMap).hasSize(0);
    }
}
