/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.documentsui.sidebar;

import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.ResolveInfo;

import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.documentsui.TestConfigStore;
import com.android.documentsui.TestUserManagerState;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.UserId;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestProvidersAccess;
import com.android.documentsui.testing.TestResolveInfo;
import com.android.modules.utils.build.SdkLevel;

import com.google.android.collect.Lists;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A unit test for RootsFragment.
 */
@RunWith(Parameterized.class)
@MediumTest
public class RootsFragmentTest {

    private Context mContext;
    private DevicePolicyManager mDevicePolicyManager;
    private RootsFragment mRootsFragment;
    private TestEnv mEnv;
    private final TestConfigStore mTestConfigStore = new TestConfigStore();
    private final TestUserManagerState mTestUserManagerState = new TestUserManagerState();

    private static final String[] EXPECTED_SORTED_RESULT = {
            TestProvidersAccess.RECENTS.title,
            TestProvidersAccess.IMAGE.title,
            TestProvidersAccess.VIDEO.title,
            TestProvidersAccess.AUDIO.title,
            TestProvidersAccess.DOCUMENT.title,
            TestProvidersAccess.DOWNLOADS.title,
            "" /* SpacerItem */,
            TestProvidersAccess.EXTERNALSTORAGE.title,
            TestProvidersAccess.HAMMY.title,
            "" /* SpacerItem */,
            TestProvidersAccess.INSPECTOR.title,
            TestProvidersAccess.PICKLES.title};

    @Parameter(0)
    public boolean isPrivateSpaceEnabled;

    /**
     * Parametrize values for {@code isPrivateSpaceEnabled} to run all the tests twice once with
     * private space flag enabled and once with it disabled.
     */
    @Parameters(name = "privateSpaceEnabled={0}")
    public static Iterable<?> data() {
        return Lists.newArrayList(true, false);
    }

    @Before
    public void setUp() {
        mEnv = TestEnv.create();
        mEnv.state.configStore = mTestConfigStore;

        mContext = mock(Context.class);
        mDevicePolicyManager = mock(DevicePolicyManager.class);
        when(mContext.getResources()).thenReturn(
                InstrumentationRegistry.getInstrumentation().getTargetContext().getResources());
        when(mContext.getSystemService(Context.DEVICE_POLICY_SERVICE))
                .thenReturn(mDevicePolicyManager);
        when(mContext.getApplicationContext()).thenReturn(
                InstrumentationRegistry.getInstrumentation().getTargetContext());

        isPrivateSpaceEnabled = SdkLevel.isAtLeastS() && isPrivateSpaceEnabled;
        if (isPrivateSpaceEnabled) {
            mTestConfigStore.enablePrivateSpaceInPhotoPicker();
            mTestUserManagerState.canFrowardToProfileIdMap.put(UserId.DEFAULT_USER, true);
        }
        mRootsFragment = new RootsFragment();
    }

    @Test
    public void testSortLoadResult_WithCorrectOrder() {
        List<Item> items = mRootsFragment.sortLoadResult(
                mContext,
                mEnv.state,
                createFakeRootInfoList(),
                null /* excludePackage */, null /* handlerAppIntent */, new TestProvidersAccess(),
                UserId.DEFAULT_USER,
                Collections.singletonList(UserId.DEFAULT_USER),
                /* maybeShowBadge */ false, mTestUserManagerState);
        assertTrue(assertSortedResult(items));
    }

    @Test
    public void testItemComparator_WithCorrectOrder() {
        final String testPackageName = "com.test1";
        final String errorTestPackageName = "com.test2";
        final RootsFragment.ItemComparator comp = new RootsFragment.ItemComparator(testPackageName);
        final List<Item> rootList = new ArrayList<>();
        rootList.add(new RootItem(TestProvidersAccess.HAMMY, null /* actionHandler */,
                errorTestPackageName, /* maybeShowBadge= */ false));
        rootList.add(new RootItem(TestProvidersAccess.INSPECTOR, null /* actionHandler */,
                errorTestPackageName, /* maybeShowBadge= */ false));
        rootList.add(new RootItem(TestProvidersAccess.PICKLES, null /* actionHandler */,
                testPackageName, /* maybeShowBadge= */ false));
        Collections.sort(rootList, comp);

        assertEquals(rootList.get(0).title, TestProvidersAccess.PICKLES.title);
        assertEquals(rootList.get(1).title, TestProvidersAccess.HAMMY.title);
        assertEquals(rootList.get(2).title, TestProvidersAccess.INSPECTOR.title);
    }

    @Test
    public void testItemComparator_differentItemTypes_WithCorrectOrder() {
        final String testPackageName = "com.test1";
        final RootsFragment.ItemComparator comp = new RootsFragment.ItemComparator(testPackageName);
        final List<Item> rootList = new ArrayList<>();
        rootList.add(new RootItem(TestProvidersAccess.HAMMY, null /* actionHandler */,
                testPackageName, /* maybeShowBadge= */ false));

        final ResolveInfo info = TestResolveInfo.create();
        info.activityInfo.packageName = testPackageName;

        rootList.add(new AppItem(info, TestProvidersAccess.PICKLES.title, UserId.DEFAULT_USER,
                null /* actionHandler */));
        rootList.add(new RootAndAppItem(TestProvidersAccess.INSPECTOR, info,
                null /* actionHandler */, /* maybeShowBadge= */ false));

        Collections.sort(rootList, comp);

        assertEquals(rootList.get(0).title, TestProvidersAccess.HAMMY.title);
        assertEquals(rootList.get(1).title, TestProvidersAccess.INSPECTOR.title);
        assertEquals(rootList.get(2).title, TestProvidersAccess.PICKLES.title);
    }

    private boolean assertSortedResult(List<Item> items) {
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item instanceof RootItem) {
                assertEquals(EXPECTED_SORTED_RESULT[i], ((RootItem) item).root.title);
            } else if (item instanceof SpacerItem) {
                assertTrue(EXPECTED_SORTED_RESULT[i].isEmpty());
            } else {
                return false;
            }
        }
        return true;
    }

    private List<RootInfo> createFakeRootInfoList() {
        final List<RootInfo> fakeRootInfoList = new ArrayList<>();
        fakeRootInfoList.add(TestProvidersAccess.PICKLES);
        fakeRootInfoList.add(TestProvidersAccess.HAMMY);
        fakeRootInfoList.add(TestProvidersAccess.INSPECTOR);
        fakeRootInfoList.add(TestProvidersAccess.DOWNLOADS);
        fakeRootInfoList.add(TestProvidersAccess.AUDIO);
        fakeRootInfoList.add(TestProvidersAccess.VIDEO);
        fakeRootInfoList.add(TestProvidersAccess.RECENTS);
        fakeRootInfoList.add(TestProvidersAccess.IMAGE);
        fakeRootInfoList.add(TestProvidersAccess.EXTERNALSTORAGE);
        fakeRootInfoList.add(TestProvidersAccess.DOCUMENT);
        return fakeRootInfoList;
    }
}
