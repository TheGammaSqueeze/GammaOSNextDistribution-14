/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.frameworkpackagestubs.test;

import static android.app.Activity.RESULT_CANCELED;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.DownloadManager;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tests for Car FrameworkPackageStubs */
@SmallTest
@RunWith(AndroidJUnit4.class)
public final class StubsTest {
    static final String TAG = "StubsTest";
    // Undefined code other than RESULT_OK: -1, RESULT_CANCELED: 0, RESULT_FIRST_USER: 1
    private static final int UNDEFINED_RESULT_CODE = 777;
    // Regex to match: com.android.[deviceType].frameworkpackagestubs.Stubs[$activityName]
    private static final Pattern REGEX_FRAMEWORK_PACKAGE_STUBS =
            Pattern.compile("^(com\\.android\\.)(.*)(\\.frameworkpackagestubs\\.Stubs)(.*)");
    // Regex to match:  com.android.internal.app.ResolverActivity or
    //                  com.android.car.activityresolver.CarResolverActivity
    private static final Pattern REGEX_RESOLVER_ACTIVITY =
            Pattern.compile("^(com\\.android\\.)(.*)(ResolverActivity)");

    private Context mContext;
    private PackageManager mPackageManager;
    private Instrumentation mInstrumentation;
    private String mMimeType;
    private String mCategory;
    private String mData;
    private int mExpectedResult;

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
        mPackageManager = mContext.getPackageManager();
        mMimeType = null;
        mCategory = null;
        mData = null;
        // Tests should set RESULT_CANCELED explicitly if the intent expects output.
        mExpectedResult = UNDEFINED_RESULT_CODE;
    }

    @Test
    public void testManageUnknownAppSources() {
        mExpectedResult = RESULT_CANCELED;
        checkIfHandleByStub(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
    }

    @Test
    public void testManageUnknownAppSourcesByPackage() {
        mData = "package:com.android.car.frameworkpackagestubs.test";
        mExpectedResult = RESULT_CANCELED;
        checkIfHandleByStub(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
    }

    @Test
    public void testOpenDocument() {
        mMimeType = "*/*";
        mCategory = Intent.CATEGORY_OPENABLE;
        mExpectedResult = RESULT_CANCELED;
        checkIfHandleByStub(Intent.ACTION_OPEN_DOCUMENT);
    }

    @Test
    public void testCreateDocument() {
        mMimeType = "*/*";
        mCategory = Intent.CATEGORY_OPENABLE;
        mExpectedResult = RESULT_CANCELED;
        checkIfHandleByStub(Intent.ACTION_CREATE_DOCUMENT);
    }

    @Test
    public void testGetContent() {
        // A media picker, etc. may support ACTION_GET_CONTENT with other MIME-types. Therefore,
        // a non-existent MIME-type: "type/nonexistent" is used to check the general file picker.
        mMimeType = "type/nonexistent";
        mCategory = Intent.CATEGORY_OPENABLE;
        mExpectedResult = RESULT_CANCELED;
        checkIfHandleByStub(Intent.ACTION_GET_CONTENT);
    }

    @Test
    public void testOpenDocumentTree() {
        mExpectedResult = RESULT_CANCELED;
        checkIfHandleByStub(Intent.ACTION_OPEN_DOCUMENT_TREE);
    }

    @Test
    public void testViewDocumentRoot() {
        mMimeType = "vnd.android.document/root";
        checkIfHandleByStub(Intent.ACTION_VIEW);
    }

    @Test
    public void testViewDocumentDirectory() {
        mMimeType = "vnd.android.document/directory";
        checkIfHandleByStub(Intent.ACTION_VIEW);
    }

    @Test
    public void testViewDownloads() {
        checkIfHandleByStub(DownloadManager.ACTION_VIEW_DOWNLOADS);
    }

    private void checkIfHandleByStub(String strIntent) {
        Intent intent = new Intent(strIntent);
        if (mMimeType != null) {
            intent.setType(mMimeType);
        }
        if (mCategory != null) {
            intent.addCategory(mCategory);
        }
        if (mData != null) {
            Uri dataUri = Uri.parse(mData);
            intent.setData(dataUri);
        }

        Log.d(TAG, "Check if frameworkpackagestubs handles " + strIntent);
        ResolveInfo resolverInfo;
        resolverInfo = mPackageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        assertThat(resolverInfo).isNotNull();
        ActivityInfo actInfo = resolverInfo.activityInfo;
        assertThat(resolverInfo.activityInfo).isNotNull();
        Log.d(TAG, "ResolveInfo.ActivityInfo.name = " + actInfo.name);
        Matcher matchResolverActivity = REGEX_RESOLVER_ACTIVITY.matcher(actInfo.name);
        assertWithMessage(
                        "Remove the stub or the app activity "
                                + "because only one should handle "
                                + strIntent)
                .that(matchResolverActivity.matches())
                .isFalse();
        Matcher matchStubs = REGEX_FRAMEWORK_PACKAGE_STUBS.matcher(actInfo.name);
        assertWithMessage(
                        strIntent
                                + " should be stubbed by FrameworkPackageStubs or properly validate"
                                + " "
                                + actInfo.name)
                .that(matchStubs.matches())
                .isTrue();
        Log.d(TAG, "Starting " + strIntent + " should not crash.");
        if (mExpectedResult == UNDEFINED_RESULT_CODE) {
            // a test case needs FLAG_ACTIVITY_NEW_TASK to start an activity
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
        } else {
            Log.d(TAG, "startActivityForResult() should return " + mExpectedResult);
            int result = startActivityForResult(intent);
            assertWithMessage(strIntent + " should return " + mExpectedResult)
                    .that(result)
                    .isEqualTo(mExpectedResult);
        }
    }

    private int startActivityForResult(Intent intent) {
        GetResultActivity getResultActivity =
                GetResultActivity.startActivitySync(mContext, mInstrumentation);
        mInstrumentation.waitForIdleSync();
        getResultActivity.startActivityForResult(intent, 0);
        int resultCode = getResultActivity.poolResultCode();
        Log.d(TAG, "startActivityForResult() returns " + resultCode);
        return resultCode;
    }
}
