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

import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.UserProperties;
import android.os.UserManager;
import android.view.View;
import android.widget.Button;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.documentsui.CrossProfileQuietModeException;
import com.android.documentsui.Model;
import com.android.documentsui.R;
import com.android.documentsui.TestConfigStore;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.documentsui.testing.TestActionHandler;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestProvidersAccess;
import com.android.documentsui.testing.UserManagers;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.HashMap;
import java.util.Map;

@SmallTest
@RunWith(Parameterized.class)
public final class InflateMessageDocumentHolderTest {

    private Context mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private Runnable mDefaultCallback = () -> {
    };
    private Message mInflateMessage;
    private TestActionHandler mTestActionHandler = new TestActionHandler();
    private InflateMessageDocumentHolder mHolder;
    private TestConfigStore mTestConfigStore = new TestConfigStore();

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
        DocumentsAdapter.Environment env =
                new TestEnvironment(mContext, TestEnv.create(), mTestActionHandler);
        env.getDisplayState().action = State.ACTION_GET_CONTENT;
        env.getDisplayState().supportsCrossProfile = true;

        mContext.setTheme(R.style.DocumentsTheme);
        mContext.getTheme().applyStyle(R.style.DocumentsDefaultTheme,  /* force= */false);

        isPrivateSpaceEnabled = SdkLevel.isAtLeastS() && isPrivateSpaceEnabled;
        if (isPrivateSpaceEnabled) {
            mTestConfigStore.enablePrivateSpaceInPhotoPicker();
            Map<UserId, String> userIdToLabelMap = new HashMap<>();
            userIdToLabelMap.put(TestProvidersAccess.USER_ID, "Personal");
            userIdToLabelMap.put(TestProvidersAccess.OtherUser.USER_ID, "Work");
            env.getDisplayState().canForwardToProfileIdMap.put(TestProvidersAccess.USER_ID, true);
            env.getDisplayState().canForwardToProfileIdMap.put(
                    TestProvidersAccess.OtherUser.USER_ID, true);
            UserManager userManager = UserManagers.create();
            if (SdkLevel.isAtLeastV()) {
                userIdToLabelMap.put(TestProvidersAccess.AnotherUser.USER_ID, "Private");
                env.getDisplayState().canForwardToProfileIdMap.put(
                        TestProvidersAccess.AnotherUser.USER_ID, true);
                UserProperties otherUserProperties = new UserProperties.Builder()
                        .setShowInQuietMode(UserProperties.SHOW_IN_QUIET_MODE_PAUSED)
                        .build();
                UserProperties anotherUserProperties = new UserProperties.Builder()
                        .setShowInQuietMode(UserProperties.SHOW_IN_QUIET_MODE_PAUSED)
                        .build();
                when(userManager.getUserProperties(TestProvidersAccess.OtherUser.USER_HANDLE))
                        .thenReturn(otherUserProperties);
                when(userManager.getUserProperties(TestProvidersAccess.AnotherUser.USER_HANDLE))
                        .thenReturn(anotherUserProperties);
            }

            mInflateMessage = new Message.InflateMessage(env, mDefaultCallback,
                    TestProvidersAccess.USER_ID,
                    TestProvidersAccess.OtherUser.USER_ID, userIdToLabelMap, userManager,
                    mTestConfigStore);
        } else {
            mInflateMessage = new Message.InflateMessage(env, mDefaultCallback, mTestConfigStore);
            env.getDisplayState().canShareAcrossProfile = true;
        }
        mHolder = new InflateMessageDocumentHolder(mContext, /* parent= */null, mTestConfigStore);
    }

    @Test
    public void testClickingButtonShouldShowProgressBar() {
        if (SdkLevel.isAtLeastV()) return;
        Model.Update error = new Model.Update(
                new CrossProfileQuietModeException(TestProvidersAccess.OtherUser.USER_ID),
                /* remoteActionsEnabled= */ true);
        mInflateMessage.update(error);

        mHolder.bind(mInflateMessage);

        View content = mHolder.itemView.findViewById(R.id.content);
        View crossProfile = mHolder.itemView.findViewById(R.id.cross_profile);
        View crossProfileContent = mHolder.itemView.findViewById(R.id.cross_profile_content);
        View progress = mHolder.itemView.findViewById(R.id.cross_profile_progress);
        Button button = mHolder.itemView.findViewById(R.id.button);

        assertThat(content.getVisibility()).isEqualTo(View.GONE);
        assertThat(crossProfile.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(crossProfileContent.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(progress.getVisibility()).isEqualTo(View.GONE);

        if (button.getVisibility() == View.VISIBLE) {
            // The button is visible when docsUI has the permission to modify quiet mode.
            assertThat(button.callOnClick()).isTrue();
            assertThat(crossProfile.getVisibility()).isEqualTo(View.VISIBLE);
            assertThat(crossProfileContent.getVisibility()).isEqualTo(View.GONE);
            assertThat(progress.getVisibility()).isEqualTo(View.VISIBLE);
        }
    }
}
