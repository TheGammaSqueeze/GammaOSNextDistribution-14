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

package com.android.documentsui.dirlist;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.UserHandle;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.documentsui.TestConfigStore;
import com.android.documentsui.TestUserManagerState;
import com.android.documentsui.ThumbnailCache;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@SmallTest
@RunWith(Parameterized.class)
public final class IconHelperTest {
    private final UserId mSystemUser = UserId.of(UserHandle.SYSTEM);
    private final UserId mManagedUser = UserId.of(100);
    private final UserId mPrivateUser = UserId.of(101);
    private Context mContext;
    private IconHelper mIconHelper;
    private ThumbnailCache mThumbnailCache = new ThumbnailCache(1000);
    private final TestUserManagerState mTestUserManagerState = new TestUserManagerState();
    private final TestConfigStore mTestConfigStore = new TestConfigStore();

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
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        isPrivateSpaceEnabled = SdkLevel.isAtLeastS() && isPrivateSpaceEnabled;
        mIconHelper = isPrivateSpaceEnabled
                ? new IconHelper(mContext, State.MODE_LIST, /* maybeShowBadge= */ true,
                mThumbnailCache, null, mTestUserManagerState, mTestConfigStore)
                : new IconHelper(mContext, State.MODE_LIST, /* maybeShowBadge= */ true,
                        mThumbnailCache, mManagedUser, null, mTestConfigStore);
        if (isPrivateSpaceEnabled) {
            mTestConfigStore.enablePrivateSpaceInPhotoPicker();
            mTestUserManagerState.userIds = SdkLevel.isAtLeastV()
                    ? Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser)
                    : Lists.newArrayList(mSystemUser, mManagedUser);
        }
    }

    @Test
    public void testShouldShowBadge_returnFalse_onSystemUser() {
        assertThat(mIconHelper.shouldShowBadge(mSystemUser.getIdentifier())).isFalse();
    }

    @Test
    public void testShouldShowBadge_returnTrue_onManagedUser() {
        assertThat(mIconHelper.shouldShowBadge(mManagedUser.getIdentifier())).isTrue();
    }

    @Test
    public void testShouldShowBadge_returnTrue_onPrivateUser() {
        if (!SdkLevel.isAtLeastV() || !isPrivateSpaceEnabled) return;
        assertThat(mIconHelper.shouldShowBadge(mPrivateUser.getIdentifier())).isTrue();
    }

    @Test
    public void testShouldShowBadge_returnFalse_onManagedUser_doNotShowBadge() {
        if (isPrivateSpaceEnabled) return;
        mIconHelper = new IconHelper(mContext, State.MODE_LIST, /* maybeShowBadge= */ false,
                mThumbnailCache, mManagedUser, null, mTestConfigStore);
        assertThat(mIconHelper.shouldShowBadge(mManagedUser.getIdentifier())).isFalse();
    }

    @Test
    public void testShouldShowBadge_returnFalseOnPrivateUser_doNotShowBadge() {
        if (!isPrivateSpaceEnabled) return;
        mIconHelper = new IconHelper(mContext, State.MODE_LIST, /* maybeShowBadge= */ false,
                mThumbnailCache, null, mTestUserManagerState, mTestConfigStore);
        assertThat(mIconHelper.shouldShowBadge(mPrivateUser.getIdentifier())).isFalse();
    }

    @Test
    public void testShouldShowBadge_returnFalseOnManagedUser_withoutManagedUser() {
        if (isPrivateSpaceEnabled) return;
        mIconHelper = new IconHelper(mContext, State.MODE_LIST, /* maybeShowBadge= */ true,
                mThumbnailCache, /* mManagedUser= */ null, null, mTestConfigStore);
        assertThat(mIconHelper.shouldShowBadge(mManagedUser.getIdentifier())).isFalse();
    }

    @Test
    public void testShouldShowBadge_returnFalseOnManagedUser_withoutMultipleUsers() {
        if (!isPrivateSpaceEnabled) return;
        mTestUserManagerState.userIds = Lists.newArrayList(mManagedUser);
        mIconHelper = new IconHelper(mContext, State.MODE_LIST, /* maybeShowBadge= */ true,
                mThumbnailCache, /* mManagedUser= */ null, mTestUserManagerState, mTestConfigStore);
        assertThat(mIconHelper.shouldShowBadge(mManagedUser.getIdentifier())).isFalse();
    }

    @Test
    public void testShouldShowBadge_returnFalseOnPrivateUser_withoutMultipleUsers() {
        if (!SdkLevel.isAtLeastV() || !isPrivateSpaceEnabled) return;
        mTestUserManagerState.userIds = Lists.newArrayList(mPrivateUser);
        mIconHelper = new IconHelper(mContext, State.MODE_LIST, /* maybeShowBadge= */ true,
                mThumbnailCache, /* mManagedUser= */ null, mTestUserManagerState, mTestConfigStore);
        assertThat(mIconHelper.shouldShowBadge(mPrivateUser.getIdentifier())).isFalse();
    }
}
