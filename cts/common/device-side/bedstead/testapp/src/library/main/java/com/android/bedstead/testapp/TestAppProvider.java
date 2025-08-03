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

import android.content.Context;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import com.android.bedstead.nene.TestApis;
import com.android.queryable.annotations.Query;
import com.android.queryable.info.ActivityInfo;
import com.android.queryable.info.ReceiverInfo;
import com.android.queryable.info.ServiceInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Entry point to Test App. Used for querying for {@link TestApp} instances. */
public final class TestAppProvider {

    private static final String TAG = TestAppProvider.class.getSimpleName();

    // Must be instrumentation context to access resources
    private static final Context sContext = TestApis.context().instrumentationContext();
    private boolean mTestAppsInitialised = false;
    private final List<TestAppDetails> mTestApps = new ArrayList<>();
    private Set<TestAppDetails> mTestAppsSnapshot = null;

    public TestAppProvider() {
        initTestApps();
    }

    /** Begin a query for a {@link TestApp}. */
    public TestAppQueryBuilder query() {
        return new TestAppQueryBuilder(this);
    }

    /** Create a query for a {@link TestApp} starting with a {@link Query}. */
    public TestAppQueryBuilder query(Query query) {
        return query().applyAnnotation(query);
    }

    /** Get any {@link TestApp}. */
    public TestApp any() {
        TestApp testApp = query().get();
        Log.d(TAG, "any(): returning " + testApp);
        return testApp;
    }

    List<TestAppDetails> testApps() {
        return mTestApps;
    }

    /** Save the state of the provider, to be reset by {@link #restore()}. */
    public void snapshot() {
        mTestAppsSnapshot = new HashSet<>(mTestApps);
    }

    /**
     * Restore the state of the provider to that recorded by {@link #snapshot()}.
     */
    public void restore() {
        if (mTestAppsSnapshot == null) {
            throw new IllegalStateException("You must call snapshot() before restore()");
        }
        mTestApps.clear();
        mTestApps.addAll(mTestAppsSnapshot);
    }

    private void initTestApps() {
        if (mTestAppsInitialised) {
            return;
        }
        mTestAppsInitialised = true;

        int indexId = sContext.getResources().getIdentifier(
                "raw/index", /* defType= */ null, sContext.getPackageName());

        try (InputStream inputStream = sContext.getResources().openRawResource(indexId)) {
            TestappProtos.TestAppIndex index = TestappProtos.TestAppIndex.parseFrom(inputStream);
            for (int i = 0; i < index.getAppsCount(); i++) {
                loadApk(index.getApps(i));
            }
            Collections.sort(mTestApps,
                    Comparator.comparing((testAppDetails) -> testAppDetails.mApp.getPackageName()));
        } catch (IOException e) {
            throw new RuntimeException("Error loading testapp index", e);
        }
    }

    private void loadApk(TestappProtos.AndroidApp app) {
        TestAppDetails details = new TestAppDetails();
        details.mApp = app;

        details.mResourceIdentifier = sContext.getResources().getIdentifier(
                "raw/" + getApkNameWithoutSuffix(app.getApkName()),
                /* defType= */ null, sContext.getPackageName());

        for (int i = 0; i < app.getMetadataCount(); i++) {
            TestappProtos.Metadata metadataEntry = app.getMetadata(i);
            details.mMetadata.putString(metadataEntry.getName(), metadataEntry.getValue());
        }

        for (int i = 0; i < app.getPermissionsCount(); i++) {
            details.mPermissions.add(app.getPermissions(i).getName());
        }

        for (int i = 0; i < app.getActivitiesCount(); i++) {
            TestappProtos.Activity activityEntry = app.getActivities(i);
            details.mActivities.add(ActivityInfo.builder()
                    .activityClass(activityEntry.getName())
                    .exported(activityEntry.getExported())
                    .intentFilters(intentFilterSetFromProtoList(
                            activityEntry.getIntentFiltersList()))
                    .permission(activityEntry.getPermission().equals("") ? null
                            : activityEntry.getPermission())
                    .build());
        }

        for (int i = 0; i < app.getServicesCount(); i++) {
            TestappProtos.Service serviceEntry = app.getServices(i);
            details.mServices.add(ServiceInfo.builder()
                    .serviceClass(serviceEntry.getName())
                    .intentFilters(intentFilterSetFromProtoList(
                            serviceEntry.getIntentFiltersList()))
                    .build());
        }

        for (int i = 0; i < app.getReceiversCount(); i++) {
            TestappProtos.Receiver receiverEntry = app.getReceivers(i);
            details.mReceivers.add(ReceiverInfo.builder()
                    .name(receiverEntry.getName())
                    .metadata(metadataSetFromProtoList(receiverEntry.getMetadataList()))
                    .build());
        }

        mTestApps.add(details);
    }

    private Set<IntentFilter> intentFilterSetFromProtoList(
            List<TestappProtos.IntentFilter> list) {
        Set<IntentFilter> filterInfoSet = new HashSet<>();

        for (TestappProtos.IntentFilter filter : list) {
            IntentFilter filterInfo = intentFilterFromProto(filter);
            filterInfoSet.add(filterInfo);
        }

        return filterInfoSet;
    }

    private IntentFilter intentFilterFromProto(TestappProtos.IntentFilter filterProto) {
        IntentFilter filter = new IntentFilter();

        for (String action : filterProto.getActionsList()) {
            filter.addAction(action);
        }
        for (String category : filterProto.getCategoriesList()) {
            filter.addCategory(category);
        }

        return filter;
    }

    private Set<Bundle> metadataSetFromProtoList(
            List<TestappProtos.Metadata> list) {
        Set<Bundle> metadataSet = new HashSet<>();

        for (TestappProtos.Metadata metadata : list) {
            Bundle metadataBundle = new Bundle();
            metadataBundle.putString(metadata.getName(), metadata.getValue());
            metadataSet.add(metadataBundle);
        }

        return metadataSet;
    }

    private String getApkNameWithoutSuffix(String apkName) {
        return apkName.split("\\.", 2)[0];
    }

    void markTestAppUsed(TestAppDetails testApp) {
        mTestApps.remove(testApp);
    }
}
