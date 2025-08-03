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

package com.android.bedstead.testapp;

import static com.android.compatibility.common.util.FileUtils.readInputStreamFully;

import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.annotation.Nullable;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.users.Users;
import com.android.bedstead.testapp.processor.annotations.TestAppSender;
import com.android.queryable.collections.QueryableActivityInfoHashSet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Set;

/** Represents a single test app which can be installed and interacted with. */
@TestAppSender
public final class TestApp {
    // Must be instrumentation context to access resources
    private static final Context sContext = TestApis.context().instrumentationContext();
    private static final String LOG_TAG = "TestApp";
    final TestAppDetails mDetails;

    TestApp(TestAppDetails details) {
        if (details == null) {
            throw new NullPointerException();
        }
        mDetails = details;
    }

    /**
     * Get a {@link Package} for the {@link TestApp}.
     *
     * <p>This will only be resolvable after the app is installed.
     */
    public Package pkg() {
        return TestApis.packages().find(packageName());
    }

    /**
     * Install the {@link TestApp} on the device for the instrumented user.
     *
     * <p>See {@link Users#instrumented()}
     */
    public TestAppInstance install() {
        return install(TestApis.users().instrumented());
    }

    /**
     * Install the {@link TestApp} on the device for the given {@link UserReference}.
     */
    public TestAppInstance install(UserReference user) {
        try {
            pkg().installBytes(user, this::apkBytes);
//            pkg().setAllowTestApiAccess(true);
        } catch (NeneException e) {
            throw new NeneException("Error while installing TestApp " + this, e);
        }

        return new TestAppInstance(this, user);
    }

    /**
     * Install the {@link TestApp} on the device for the given {@link UserHandle}.
     */
    public TestAppInstance install(UserHandle user) {
        install(TestApis.users().find(user));
        return instance(user);
    }

    /**
     * Uninstall the {@link TestApp} on the device from the instrumented user.
     *
     * <p>See {@link Users#instrumented()}
     */
    public void uninstall() {
        uninstall(TestApis.users().instrumented());
    }

    /**
     * Check if {@link TestApp} is installed on the given {@link UserReference}
     */
    public boolean installedOnUser(UserReference user) {
        return pkg().installedOnUser(user);
    }

    /**
     * Check if {@link TestApp} is installed on the given {@link UserHandle}
     */
    public boolean installedOnUser(UserHandle user) {
        return pkg().installedOnUser(user);
    }

    /**
     * Uninstall the {@link TestApp} from the device from all users.
     */
    public void uninstallFromAllUsers() {
        pkg().uninstallFromAllUsers();
    }

    /**
     * Uninstall the {@link TestApp} on the device from the given {@link UserReference}.
     */
    public void uninstall(UserReference user) {
        pkg().uninstall(user);
    }

    /**
     * Uninstall the {@link TestApp} on the device from the given {@link UserHandle}.
     */
    public void uninstall(UserHandle user) {
        uninstall(TestApis.users().find(user));
    }

    /**
     * Get a reference to the specific instance of this test app on a given user.
     *
     * <p>This does not check if the user exists, or if the test app is installed on the user.
     */
    public TestAppInstance instance(UserHandle user) {
        return instance(TestApis.users().find(user));
    }

    /**
     * Get a reference to the specific instance of this test app on a given user.
     *
     * <p>This does not check if the user exists, or if the test app is installed on the user.
     */
    public TestAppInstance instance(UserReference user) {
        if (user == null) {
            throw new NullPointerException();
        }
        return new TestAppInstance(this, user);
    }

    /**
     * Returns an {@link InputStream} of the apk for this app.
     */
    public InputStream apkStream() {
        return sContext.getResources().openRawResource(mDetails.mResourceIdentifier);
    }

    /**
     * Returns a byte array of the apk for this app.
     */
    public byte[] apkBytes() {
        try (InputStream inputStream = apkStream()) {
            return readInputStreamFully(inputStream);
        } catch (IOException e) {
            throw new NeneException("Error when reading TestApp bytes", e);
        }
    }

    /** Write the APK file to the given {@link File}. */
    public void writeApkFile(File outputFile) throws IOException {
        outputFile.getParentFile().mkdirs();
        try (FileOutputStream output = new FileOutputStream(outputFile)) {
            output.write(apkBytes());
        }
    }

    /** The package label, or the package name if no label is specified. */
    public String label() {
        return mDetails.label() != null ? mDetails.label() : packageName();
    }

    /** The package name of the test app. */
    public String packageName() {
        return mDetails.mApp.getPackageName();
    }

    /** The testOnly attribute for the test app. */
    public boolean testOnly() {
        return mDetails.mApp.getTestOnly();
    }

    /** The minSdkVersion of the test app. */
    public int minSdkVersion() {
        return mDetails.mApp.getUsesSdk().getMinSdkVersion();
    }

    /** The maxSdkVersion of the test app. */
    public int maxSdkVersion() {
        return mDetails.mApp.getUsesSdk().getMaxSdkVersion();
    }

    /** The targetSdkVersion of the test app. */
    public int targetSdkVersion() {
        return mDetails.mApp.getUsesSdk().getTargetSdkVersion();
    }

    /** The permissions declared by the test app. */
    public Set<String> permissions() {
        return mDetails.mPermissions;
    }

    /** The activities which exist in the test app. */
    public QueryableActivityInfoHashSet activities() {
        return new QueryableActivityInfoHashSet(mDetails.mActivities);
    }

    /**
     * The metadata declared by the test app.
     *
     * <p>Note that currently all values are of type String.
     */
    public Bundle metadata() {
        return mDetails.mMetadata;
    }

    @Override
    public String toString() {
        return "TestApp{"
                + "packageName=" + packageName()
                + "}";
    }

    /** The shared user id of the test app, if any. */
    @Nullable
    public String sharedUserId() {
        return mDetails.sharedUserId();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestApp)) return false;
        TestApp testApp = (TestApp) o;
        return mDetails.mApp.getPackageName().equals(testApp.mDetails.mApp.getPackageName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(mDetails.mApp.getPackageName());
    }
}
