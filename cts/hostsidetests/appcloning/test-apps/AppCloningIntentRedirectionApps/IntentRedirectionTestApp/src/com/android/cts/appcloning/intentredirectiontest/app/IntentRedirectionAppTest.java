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

package com.android.cts.appcloning.intentredirectiontest.app;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

/**
 * App which will execute on device side to verify intent redirection between clone and owner
 * profile
 */
@RunWith(JUnit4.class)
public class IntentRedirectionAppTest {
    private static final String TAG = "IntentRedirectionAppTest";
    private Context mContext;
    private PackageManager mPackageManager;
    private UiAutomation mUiAutomation;

    private static final String CLONE_APP_PACKAGE = "com.android.cts.appcloning.cloneprofile.app";
    private static final String OWNER_APP_PACKAGE = "com.android.cts.appcloning.ownerprofile.app";
    private static final String QUERY_CLONED_APPS = "android.permission.QUERY_CLONED_APPS";

    @Before
    public void setUp() throws Exception {
        mUiAutomation = getInstrumentation().getUiAutomation();
        mContext = getInstrumentation().getContext();
        mPackageManager = mContext.getPackageManager();
    }

    /**
     * Query the intent based on intent action and verify if owner and clone apps are present, if
     * they should be present.
     */
    @Test
    public void testIntentResolutionForUser() {
        String intentAction = getTestArgumentValueForGivenKey("intent_action");
        int userId = Integer.valueOf(getTestArgumentValueForGivenKey("user_id"));
        boolean cloneAppShouldBePresent =
                Boolean.valueOf(getTestArgumentValueForGivenKey("clone_app_present"));
        boolean ownerAppShouldBePresent =
                Boolean.valueOf(getTestArgumentValueForGivenKey("owner_app_present"));
        boolean isMatchCloneProfileFlagSet =
                Boolean.valueOf(getTestArgumentValueForGivenKey(
                        "match_clone_profile_flag"));
        boolean shouldGrantQueryClonedAppsPermission =
                Boolean.valueOf(getTestArgumentValueForGivenKey(
                        "grant_query_cloned_apps_permission"));

        Log.d(TAG, "test for intent : " + intentAction);
        Intent intent = buildIntentForTest(intentAction);
        assertThat(intent).isNotNull();
        int queryFlag = PackageManager.MATCH_ALL;
        if (isMatchCloneProfileFlagSet) {
            queryFlag |= PackageManager.MATCH_CLONE_PROFILE;
        }

        if (shouldGrantQueryClonedAppsPermission) {
            mUiAutomation.adoptShellPermissionIdentity(QUERY_CLONED_APPS);
        }

        List<ResolveInfo> resolveInfos = new ArrayList<>();

        try {
            resolveInfos = mPackageManager.queryIntentActivities(intent,
                    queryFlag);
        } finally {
            if (shouldGrantQueryClonedAppsPermission) {
                mUiAutomation.dropShellPermissionIdentity();
            }
        }

        Log.i(TAG, "resolveInfos : " + resolveInfos);
        boolean isCloneAppPresent = false;
        boolean isOwnerAppPresent = false;
        for  (ResolveInfo resolveInfo : resolveInfos) {
            if (resolveInfo == null) continue;
            if (resolveInfo.activityInfo != null) {
                if (CLONE_APP_PACKAGE.equals(resolveInfo.activityInfo.packageName)) {
                    isCloneAppPresent = true;
                }
                if (OWNER_APP_PACKAGE.equals(resolveInfo.activityInfo.packageName)) {
                    isOwnerAppPresent = true;
                }
            }
        }

        // Assert that clone app is present if it should be present
        assertThat(isCloneAppPresent).isEqualTo(cloneAppShouldBePresent);
        // Assert that owner app is present if it should be present
        assertThat(isOwnerAppPresent).isEqualTo(ownerAppShouldBePresent);
    }

    /**
     * Build intent for test based on intent action
     * @param intentAction action for intent request
     * @return intent request
     */
    private Intent buildIntentForTest(String intentAction) {
        Intent intent = null;
        switch(intentAction) {
            case Intent.ACTION_VIEW :
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"));
                break;
            case Intent.ACTION_SENDTO :
                intent = new Intent(Intent.ACTION_SENDTO);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, "Hello world!");
                break;
            case Intent.ACTION_SEND :
                intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, "Hello world!");
                break;
            case Intent.ACTION_SEND_MULTIPLE :
                intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                intent.setType("text/plain");
                intent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[] { "" });
                intent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Subject");
                intent.putExtra(android.content.Intent.EXTRA_TEXT, "Text");
                break;
            case MediaStore.ACTION_IMAGE_CAPTURE :
                intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                // specifying owner package as Android 11+ only system specified apps can serve
                // camera implicit intent and only clone->owner redirection is allowed
                intent.setPackage(OWNER_APP_PACKAGE);
                break;
            case MediaStore.ACTION_VIDEO_CAPTURE :
                intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                // specifying owner package as Android 11+ only system specified apps can serve
                // camera implicit intent and only clone->owner redirection is allowed
                intent.setPackage(OWNER_APP_PACKAGE);
                break;
        }
        return intent;
    }

    /**
     * Returns argument from InstrumentationRegistry
     * @param testArgumentKey key name
     * @return value passed in argument or "" if not defined
     */
    private String getTestArgumentValueForGivenKey(String testArgumentKey) {
        final Bundle testArguments = InstrumentationRegistry.getArguments();
        String testArgumentValue = testArguments.getString(testArgumentKey, "");
        return testArgumentValue;
    }
}
