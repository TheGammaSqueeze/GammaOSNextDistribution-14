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

import com.android.queryable.Queryable;
import com.android.queryable.annotations.Query;
import com.android.queryable.info.ActivityInfo;
import com.android.queryable.info.ReceiverInfo;
import com.android.queryable.info.ServiceInfo;
import com.android.queryable.queries.BooleanQuery;
import com.android.queryable.queries.BooleanQueryHelper;
import com.android.queryable.queries.BundleQuery;
import com.android.queryable.queries.BundleQueryHelper;
import com.android.queryable.queries.IntegerQuery;
import com.android.queryable.queries.IntegerQueryHelper;
import com.android.queryable.queries.SetQuery;
import com.android.queryable.queries.SetQueryHelper;
import com.android.queryable.queries.StringQuery;
import com.android.queryable.queries.StringQueryHelper;

import com.google.auto.value.AutoAnnotation;

/** Builder for progressively building {@link TestApp} queries. */
public final class TestAppQueryBuilder implements Queryable {
    private final TestAppProvider mProvider;

    StringQueryHelper<TestAppQueryBuilder> mLabel = new StringQueryHelper<>(this);
    StringQueryHelper<TestAppQueryBuilder> mPackageName = new StringQueryHelper<>(this);
    BundleQueryHelper<TestAppQueryBuilder> mMetadata = new BundleQueryHelper<>(this);
    IntegerQueryHelper<TestAppQueryBuilder> mMinSdkVersion = new IntegerQueryHelper<>(this);
    IntegerQueryHelper<TestAppQueryBuilder> mMaxSdkVersion = new IntegerQueryHelper<>(this);
    IntegerQueryHelper<TestAppQueryBuilder> mTargetSdkVersion = new IntegerQueryHelper<>(this);
    SetQueryHelper<TestAppQueryBuilder, String> mPermissions =
            new SetQueryHelper<>(this);
    BooleanQueryHelper<TestAppQueryBuilder> mTestOnly = new BooleanQueryHelper<>(this);
    BooleanQueryHelper<TestAppQueryBuilder> mCrossProfile = new BooleanQueryHelper<>(this);
    SetQueryHelper<TestAppQueryBuilder, ActivityInfo> mActivities =
            new SetQueryHelper<>(this);
    SetQueryHelper<TestAppQueryBuilder, ServiceInfo> mServices =
            new SetQueryHelper<>(this);
    BooleanQueryHelper<TestAppQueryBuilder> mIsDeviceAdmin = new BooleanQueryHelper<>(this);
    StringQueryHelper<TestAppQueryBuilder> mSharedUserId = new StringQueryHelper<>(this);
    SetQueryHelper<TestAppQueryBuilder, ReceiverInfo> mReceivers = new SetQueryHelper<>(this);
    BooleanQueryHelper<TestAppQueryBuilder> mIsHeadlessDOSingleUser = new BooleanQueryHelper<>(this);
    private boolean mAllowInternalBedsteadTestApps = false;

    /**
     * Returns a {@link TestAppQueryBuilder} not linked to a specific {@link TestAppProvider}.
     *
     * <p>Note that attempts to resolve this query will fail.
     */
    public static TestAppQueryBuilder queryBuilder() {
        return new TestAppQueryBuilder();
    }

    private TestAppQueryBuilder() {
        mProvider = null;
    }

    TestAppQueryBuilder(TestAppProvider provider) {
        if (provider == null) {
            throw new NullPointerException();
        }
        mProvider = provider;
    }

    /**
     * Apply the query parameters inside the {@link Query} to this {@link TestAppQueryBuilder}.
     */
    public TestAppQueryBuilder applyAnnotation(Query query) {
        if (query == null) {
            return this;
        }

        TestAppQueryBuilder queryBuilder = this;
        queryBuilder = queryBuilder.whereTargetSdkVersion().matchesAnnotation(query.targetSdkVersion());
        queryBuilder = queryBuilder.whereMinSdkVersion().matchesAnnotation(query.minSdkVersion());
        queryBuilder = queryBuilder.whereMaxSdkVersion().matchesAnnotation(query.maxSdkVersion());
        queryBuilder = queryBuilder.wherePackageName().matchesAnnotation(query.packageName());
        queryBuilder = queryBuilder.whereIsDeviceAdmin().matchesAnnotation(query.isDeviceAdmin());
        queryBuilder = queryBuilder.whereIsHeadlessDOSingleUser().matchesAnnotation(
                query.isHeadlessDOSingleUser());
        return queryBuilder;
    }

    /**
     * Query for a {@link TestApp} which declares the given label.
     */
    public StringQuery<TestAppQueryBuilder> whereLabel() {
        return mLabel;
    }

    /**
     * Query for a {@link TestApp} with a given package name.
     *
     * <p>Only use this filter when you are relying specifically on the package name itself. If you
     * are relying on features you know the {@link TestApp} with that package name has, query for
     * those features directly.
     */
    public StringQuery<TestAppQueryBuilder> wherePackageName() {
        return mPackageName;
    }

    /**
     * Query for a {@link TestApp} by metadata.
     */
    public BundleQuery<TestAppQueryBuilder> whereMetadata() {
        return mMetadata;
    }

    /**
     * Query for a {@link TestApp} by minSdkVersion.
     */
    public IntegerQuery<TestAppQueryBuilder> whereMinSdkVersion() {
        return mMinSdkVersion;
    }

    /**
     * Query for a {@link TestApp} by maxSdkVersion.
     */
    public IntegerQuery<TestAppQueryBuilder> whereMaxSdkVersion() {
        return mMaxSdkVersion;
    }

    /**
     * Query for a {@link TestApp} by targetSdkVersion.
     */
    public IntegerQuery<TestAppQueryBuilder> whereTargetSdkVersion() {
        return mTargetSdkVersion;
    }

    /**
     * Query for a {@link TestApp} by declared permissions.
     */
    public SetQuery<TestAppQueryBuilder, String> wherePermissions() {
        return mPermissions;
    }

    /**
     * Query for a {@link TestApp} by the testOnly attribute.
     */
    public BooleanQuery<TestAppQueryBuilder> whereTestOnly() {
        return mTestOnly;
    }

    /**
     * Query for a {@link TestApp} by the crossProfile attribute.
     */
    public BooleanQuery<TestAppQueryBuilder> whereCrossProfile() {
        return mCrossProfile;
    }

    /**
     * Query for an app which is a device admin.
     */
    public BooleanQuery<TestAppQueryBuilder> whereIsDeviceAdmin() {
        return mIsDeviceAdmin;
    }

    /**
     * Query for an app which is a headless device owner single user.
     */
    public BooleanQuery<TestAppQueryBuilder> whereIsHeadlessDOSingleUser() {
        return mIsHeadlessDOSingleUser;
    }

    /**
     * Query for a {@link TestApp} by its sharedUserId;
     */
    public StringQuery<TestAppQueryBuilder> whereSharedUserId() {
        return mSharedUserId;
    }

    /**
     * Query for a {@link TestApp} by its activities.
     */
    public SetQuery<TestAppQueryBuilder, ActivityInfo> whereActivities() {
        return mActivities;
    }

    /**
     * Query for a {@link TestApp} by its services.
     */
    public SetQuery<TestAppQueryBuilder, ServiceInfo> whereServices() {
        return mServices;
    }

    /**
     * Query for a {@link TestApp} by its receivers.
     */
    public SetQuery<TestAppQueryBuilder, ReceiverInfo> whereReceivers() {
        return mReceivers;
    }

    /**
     * Allow the query to return internal bedstead testapps.
     */
    public TestAppQueryBuilder allowInternalBedsteadTestApps() {
        mAllowInternalBedsteadTestApps = true;
        return this;
    }

    /**
     * Get the {@link TestApp} matching the query.
     *
     * @throws NotFoundException if there is no matching @{link TestApp}.
     */
    public TestApp get() {
        // TODO(scottjonathan): Provide instructions on adding the TestApp if the query fails
        return new TestApp(resolveQuery());
    }

    /**
     * Checks if the query matches the specified test app
     */
    public boolean matches(TestApp testApp) {
        TestAppDetails details = testApp.mDetails;
        return matches(details);
    }

    private TestAppDetails resolveQuery() {
        if (mProvider == null) {
            throw new IllegalStateException("Cannot resolve testApps in an empty query. You must"
                    + " create the query using a testAppProvider.query() rather than "
                    + "TestAppQueryBuilder.query() in order to get results");
        }

        for (TestAppDetails details : mProvider.testApps()) {
            if (!matches(details)) {
                continue;
            }

            mProvider.markTestAppUsed(details);
            return details;
        }

        throw new NotFoundException(this);
    }

    @Override
    public boolean isEmptyQuery() {
        return Queryable.isEmptyQuery(mPackageName)
                && Queryable.isEmptyQuery(mLabel)
                && Queryable.isEmptyQuery(mMetadata)
                && Queryable.isEmptyQuery(mMinSdkVersion)
                && Queryable.isEmptyQuery(mMaxSdkVersion)
                && Queryable.isEmptyQuery(mTargetSdkVersion)
                && Queryable.isEmptyQuery(mActivities)
                && Queryable.isEmptyQuery(mServices)
                && Queryable.isEmptyQuery(mPermissions)
                && Queryable.isEmptyQuery(mTestOnly)
                && Queryable.isEmptyQuery(mCrossProfile)
                && Queryable.isEmptyQuery(mIsDeviceAdmin)
                && Queryable.isEmptyQuery(mSharedUserId)
                && Queryable.isEmptyQuery(mIsHeadlessDOSingleUser);
    }

    private boolean matches(TestAppDetails details) {
        if (!StringQueryHelper.matches(mPackageName, details.mApp.getPackageName())) {
            return false;
        }

        if (!StringQueryHelper.matches(mLabel, details.label())) {
            return false;
        }

        if (!BundleQueryHelper.matches(mMetadata, details.mMetadata)) {
            return false;
        }

        if (!IntegerQueryHelper.matches(
                mMinSdkVersion, details.mApp.getUsesSdk().getMinSdkVersion())) {
            return false;
        }

        if (!IntegerQueryHelper.matches(
                mMaxSdkVersion, details.mApp.getUsesSdk().getMaxSdkVersion())) {
            return false;
        }

        if (!IntegerQueryHelper.matches(
                mTargetSdkVersion, details.mApp.getUsesSdk().getTargetSdkVersion())) {
            return false;
        }

        if (!SetQueryHelper.matches(mActivities, details.mActivities)) {
            return false;
        }

        if (!SetQueryHelper.matches(mServices, details.mServices)) {
            return false;
        }

        if (!SetQueryHelper.matches(mPermissions, details.mPermissions)) {
            return false;
        }

        if (!BooleanQueryHelper.matches(mTestOnly, details.mApp.getTestOnly())) {
            return false;
        }

        if (!BooleanQueryHelper.matches(mCrossProfile, details.mApp.getCrossProfile())) {
            return false;
        }

        if (!SetQueryHelper.matches(mReceivers, details.mReceivers)) {
            return false;
        }

        // TODO(b/198419895): Actually query for the correct receiver + metadata
        boolean isDeviceAdmin = details.mApp.getPackageName().contains(
                "DeviceAdminTestApp");
        if (!BooleanQueryHelper.matches(mIsDeviceAdmin, isDeviceAdmin)) {
            return false;
        }

        // TODO(b/320666412): Enable querying test apps using xml content
        boolean isHeadlessDOSingleUser = details.mMetadata.getString("headless_do_single_user",
                "false").equals("true");
        if (!BooleanQueryHelper.matches(mIsHeadlessDOSingleUser, isHeadlessDOSingleUser)) {
            return false;
        }

        if (mSharedUserId.isEmpty()) {
            if (details.sharedUserId() != null) {
                return false;
            }
        } else {
            if (!StringQueryHelper.matches(mSharedUserId, details.sharedUserId())) {
                return false;
            }
        }

        if (!mAllowInternalBedsteadTestApps
                && details.mMetadata.getString("testapp-package-query-only", "false")
                .equals("true")) {
            if (!mPackageName.isQueryingForExactMatch()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String describeQuery(String fieldName) {
        return "{" + Queryable.joinQueryStrings(
                mPackageName.describeQuery("packageName"),
                mLabel.describeQuery("label"),
                mMetadata.describeQuery("metadata"),
                mMinSdkVersion.describeQuery("minSdkVersion"),
                mMaxSdkVersion.describeQuery("maxSdkVersion"),
                mTargetSdkVersion.describeQuery("targetSdkVersion"),
                mActivities.describeQuery("activities"),
                mServices.describeQuery("services"),
                mPermissions.describeQuery("permissions"),
                mSharedUserId.describeQuery("sharedUserId"),
                mTestOnly.describeQuery("testOnly"),
                mCrossProfile.describeQuery("crossProfile"),
                mIsDeviceAdmin.describeQuery("isDeviceAdmin"),
                mIsHeadlessDOSingleUser.describeQuery("isHeadlessDOSingleUser")
        ) + "}";
    }

    @Override
    public String toString() {
        return "TestAppQueryBuilder" + describeQuery(null);
    }

    public Query toAnnotation() {
        return query(mPackageName.toAnnotation(),
                mTargetSdkVersion.toAnnotation(),
                mMinSdkVersion.toAnnotation(),
                mMaxSdkVersion.toAnnotation(),
                mIsDeviceAdmin.toAnnotation(),
                mIsHeadlessDOSingleUser.toAnnotation());
    }

    @AutoAnnotation
    private static Query query(
            com.android.queryable.annotations.StringQuery packageName,
            com.android.queryable.annotations.IntegerQuery targetSdkVersion,
            com.android.queryable.annotations.IntegerQuery minSdkVersion,
            com.android.queryable.annotations.IntegerQuery maxSdkVersion,
            com.android.queryable.annotations.BooleanQuery isDeviceAdmin,
            com.android.queryable.annotations.BooleanQuery isHeadlessDOSingleUser) {
        return new AutoAnnotation_TestAppQueryBuilder_query(
                packageName, targetSdkVersion, minSdkVersion, maxSdkVersion, isDeviceAdmin,
                isHeadlessDOSingleUser);
    }
}
