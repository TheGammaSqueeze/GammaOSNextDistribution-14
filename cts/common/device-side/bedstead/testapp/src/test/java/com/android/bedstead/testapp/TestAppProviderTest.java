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

import static com.android.queryable.annotations.IntegerQuery.DEFAULT_INT_QUERY_PARAMETERS_VALUE;
import static com.android.queryable.queries.ActivityQuery.activity;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.nene.types.OptionalBoolean;
import com.android.queryable.annotations.BooleanQuery;
import com.android.queryable.annotations.IntegerQuery;
import com.android.queryable.annotations.Query;
import com.android.queryable.annotations.StringQuery;

import com.google.auto.value.AutoAnnotation;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(BedsteadJUnit4.class)
public final class TestAppProviderTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    // Expects that this package name matches an actual test app
    private static final String EXISTING_PACKAGENAME = "com.android.bedstead.testapp.EmptyTestApp";

    // Expects that this package name does not match an actual test app
    private static final String NOT_EXISTING_PACKAGENAME = "not.existing.test.app";

    // Expected to be a class name which is used in a test app
    private static final String KNOWN_EXISTING_TESTAPP_ACTIVITY_CLASSNAME =
            "android.testapp.activity";

    private static final String QUERY_ONLY_TEST_APP_PACKAGE_NAME = "com.android.cts.RemoteDPC";

    private static final String PERMISSION_DECLARED_BY_TESTAPP = "android.permission.READ_CALENDAR";

    private static final String METADATA_KEY = "test-metadata-key";
    private static final String METADATA_VALUE = "test-metadata-value";

    private static final String STRING_VALUE = "String";
    private static final String DIFFERENT_STRING_VALUE = "Different String";

    private static final int INTEGER_VALUE = 10;
    private static final int HIGHER_INTEGER_VALUE = 11;
    private static final int LOWER_INTEGER_VALUE = 9;

    private TestAppProvider mTestAppProvider;

    @AutoAnnotation
    public static Query query(StringQuery packageName, IntegerQuery minSdkVersion, IntegerQuery maxSdkVersion, IntegerQuery targetSdkVersion, BooleanQuery isDeviceAdmin, BooleanQuery isHeadlessDOSingleUser) {
        return new AutoAnnotation_TestAppProviderTest_query(packageName, minSdkVersion, maxSdkVersion, targetSdkVersion, isDeviceAdmin, isHeadlessDOSingleUser);
    }

    @AutoAnnotation
    public static StringQuery stringQuery(String startsWith, String isEqualTo, String isNotEqualTo, OptionalBoolean isNull) {
        return new AutoAnnotation_TestAppProviderTest_stringQuery(startsWith, isEqualTo, isNotEqualTo, isNull);
    }

    @AutoAnnotation
    public static IntegerQuery integerQuery(int isEqualTo, int isGreaterThan, int isGreaterThanOrEqualTo, int isLessThan, int isLessThanOrEqualTo) {
        return new AutoAnnotation_TestAppProviderTest_integerQuery(isEqualTo, isGreaterThan, isGreaterThanOrEqualTo, isLessThan, isLessThanOrEqualTo);
    }

    @AutoAnnotation
    public static BooleanQuery booleanQuery(OptionalBoolean isEqualTo) {
        return new AutoAnnotation_TestAppProviderTest_booleanQuery(isEqualTo);
    }

    static final class QueryBuilder {
        private StringQuery mPackageName = null;
        private IntegerQuery mMinSdkVersion = null;
        private IntegerQuery mMaxSdkVersion = null;
        private IntegerQuery mTargetSdkVersion = null;
        private BooleanQuery mIsDeviceAdmin = null;
        private BooleanQuery mIsHeadlessDOSingleUser = null;

        public QueryBuilder packageName(StringQuery packageName) {
            mPackageName = packageName;
            return this;
        }

        public QueryBuilder minSdkVersion(IntegerQuery minSdkVersion) {
            mMinSdkVersion = minSdkVersion;
            return this;
        }

        public QueryBuilder maxSdkVersion(IntegerQuery maxSdkVersion) {
            mMaxSdkVersion = maxSdkVersion;
            return this;
        }

        public QueryBuilder targetSdkVersion(IntegerQuery targetSdkVersion) {
            mTargetSdkVersion = targetSdkVersion;
            return this;
        }

        public QueryBuilder isDeviceAdmin(BooleanQuery isDeviceAdmin) {
            mIsDeviceAdmin = isDeviceAdmin;
            return this;
        }

        public QueryBuilder isHeadlessDOSingleUser(BooleanQuery isHeadlessDOSingleUser) {
            mIsHeadlessDOSingleUser = isHeadlessDOSingleUser;
            return this;
        }

        public Query build() {
            return query(
                    mPackageName != null ? mPackageName : stringQueryBuilder().build(),
                    mMinSdkVersion != null ? mMinSdkVersion : integerQueryBuilder().build(),
                    mMaxSdkVersion != null ? mMaxSdkVersion : integerQueryBuilder().build(),
                    mTargetSdkVersion != null ? mTargetSdkVersion : integerQueryBuilder().build(),
                    mIsDeviceAdmin != null ? mIsDeviceAdmin : booleanQueryBuilder().build(),
                    mIsHeadlessDOSingleUser != null ? mIsHeadlessDOSingleUser : booleanQueryBuilder().build()
            );
        }
    }

    static final class StringQueryBuilder {
        private String mStartsWith = StringQuery.DEFAULT_STRING_QUERY_PARAMETERS_VALUE;
        private String mIsEqualTo = StringQuery.DEFAULT_STRING_QUERY_PARAMETERS_VALUE;
        private String mIsNotEqualTo = StringQuery.DEFAULT_STRING_QUERY_PARAMETERS_VALUE;
        private OptionalBoolean mIsNull = OptionalBoolean.ANY;

        public StringQueryBuilder startsWith(String startsWith) {
            mStartsWith = startsWith;
            return this;
        }

        public StringQueryBuilder isEqualTo(String isEqualTo) {
            mIsEqualTo = isEqualTo;
            return this;
        }

        public StringQueryBuilder isNotEqualTo(String isNotEqualTo) {
            mIsNotEqualTo = isNotEqualTo;
            return this;
        }

        public StringQueryBuilder isNull(OptionalBoolean isNull) {
            mIsNull = isNull;
            return this;
        }

        public StringQuery build() {
            return stringQuery(mStartsWith, mIsEqualTo, mIsNotEqualTo, mIsNull);
        }
    }

    static final class IntegerQueryBuilder {
        private int mIsEqualTo = DEFAULT_INT_QUERY_PARAMETERS_VALUE;
        private int mIsGreaterThan = DEFAULT_INT_QUERY_PARAMETERS_VALUE;
        private int mIsGreaterThanOrEqualTo = DEFAULT_INT_QUERY_PARAMETERS_VALUE;
        private int mIsLessThan = DEFAULT_INT_QUERY_PARAMETERS_VALUE;
        private int mIsLessThanOrEqualTo = DEFAULT_INT_QUERY_PARAMETERS_VALUE;

        public IntegerQueryBuilder isEqualTo(int isEqualTo) {
            mIsEqualTo = isEqualTo;
            return this;
        }

        public IntegerQueryBuilder isGreaterThan(int isGreaterThan) {
            mIsGreaterThan = isGreaterThan;
            return this;
        }

        public IntegerQueryBuilder isGreaterThanOrEqualTo(int isGreaterThanOrEqualTo) {
            mIsGreaterThanOrEqualTo = isGreaterThanOrEqualTo;
            return this;
        }

        public IntegerQueryBuilder isLessThan(int isLessThan) {
            mIsLessThan = isLessThan;
            return this;
        }

        public IntegerQueryBuilder isLessThanOrEqualTo(int isLessThanOrEqualTo) {
            mIsLessThanOrEqualTo = isLessThanOrEqualTo;
            return this;
        }

        public IntegerQuery build() {
            return integerQuery(
                    mIsEqualTo,
                    mIsGreaterThan, mIsGreaterThanOrEqualTo,
                    mIsLessThan, mIsLessThanOrEqualTo);
        }
    }

    static final class BooleanQueryBuilder {
        private OptionalBoolean mIsEqualTo = OptionalBoolean.ANY;

        public BooleanQueryBuilder isEqualTo(OptionalBoolean isEqualTo) {
            mIsEqualTo = isEqualTo;
            return this;
        }

        public BooleanQuery build() {
            return booleanQuery(mIsEqualTo);
        }
    }

    // TODO: The below AutoBuilder should work instead of the custom one but we get
    // [AutoBuilderNoVisible] No visible constructor for com.android.queryable.annotations.Query

//    @AutoBuilder(ofClass = Query.class)
//    interface QueryBuilder {
//        QueryBuilder packageName(StringQuery packageName);
//        QueryBuilder minSdkVersion(IntegerQuery minSdkVersion);
//        QueryBuilder maxSdkVersion(IntegerQuery maxSdkVersion);
//        QueryBuilder targetSdkVersion(IntegerQuery targetSdkVersion);
//        Query build();
//    }

//    @AutoBuilder(ofClass = StringQuery.class)
//    interface StringQueryBuilder {
//        StringQueryBuilder startsWith(String startsWith);
//        StringQueryBuilder isEqualTo(String isEqualTo);
//        StringQueryBuilder isNotEqualTo(String isNotEqualTo);
//        StringQueryBuilder isNull(OptionalBoolean isNull);
//        StringQuery build();
//    }
//
//    @AutoBuilder(ofClass = IntegerQuery.class)
//    interface IntegerQueryBuilder {
//        IntegerQueryBuilder isEqualTo(int isEqualTo);
//        IntegerQueryBuilder isGreaterThan(int isGreaterThan);
//        IntegerQueryBuilder isGreaterThanOrEqualTo(int isGreaterThanOrEqualTo);
//        IntegerQueryBuilder isLessThan(int isLessThan);
//        IntegerQueryBuilder isLessThanOrEqualTo(int isLessThanOrEqualTo);
//        IntegerQuery build();
//    }

    static QueryBuilder queryBuilder() {
        return new QueryBuilder();
    }

    static StringQueryBuilder stringQueryBuilder() {
        return new StringQueryBuilder();
    }

    static IntegerQueryBuilder integerQueryBuilder() {
        return new IntegerQueryBuilder();
    }

    static BooleanQueryBuilder booleanQueryBuilder() {
        return new BooleanQueryBuilder();
    }

    @Before
    public void setup() {
        mTestAppProvider = new TestAppProvider();
    }

    @Test
    public void get_queryMatches_returnsTestApp() {
        TestAppQueryBuilder query = mTestAppProvider.query()
                .wherePackageName().isEqualTo(EXISTING_PACKAGENAME);

        assertThat(query.get()).isNotNull();
    }

    @Test
    public void get_queryMatches_packageNameIsSet() {
        TestAppQueryBuilder query = mTestAppProvider.query()
                .wherePackageName().isEqualTo(EXISTING_PACKAGENAME);

        assertThat(query.get().packageName()).isEqualTo(EXISTING_PACKAGENAME);
    }

    @Test
    public void get_queryDoesNotMatch_throwsException() {
        TestAppQueryBuilder query = mTestAppProvider.query()
                .wherePackageName().isEqualTo(NOT_EXISTING_PACKAGENAME);

        assertThrows(NotFoundException.class, query::get);
    }

    @Test
    public void any_returnsTestApp() {
        assertThat(mTestAppProvider.any()).isNotNull();
    }

    @Test
    public void any_returnsDifferentTestApps() {
        assertThat(mTestAppProvider.any()).isNotEqualTo(mTestAppProvider.any());
    }

    @Test
    public void query_onlyReturnsTestAppOnce() {
        mTestAppProvider.query().wherePackageName().isEqualTo(EXISTING_PACKAGENAME).get();

        TestAppQueryBuilder query = mTestAppProvider.query().wherePackageName().isEqualTo(EXISTING_PACKAGENAME);

        assertThrows(NotFoundException.class, query::get);
    }

    @Test
    public void query_afterRestore_returnsTestAppAgain() {
        mTestAppProvider.snapshot();
        mTestAppProvider.query().wherePackageName().isEqualTo(EXISTING_PACKAGENAME).get();

        mTestAppProvider.restore();

        assertThat(mTestAppProvider.query().wherePackageName()
                .isEqualTo(EXISTING_PACKAGENAME).get()).isNotNull();
    }

    @Test
    public void query_afterRestoreWithAppAlreadyUsed_doesNotReturnTestAppAgain() {
        mTestAppProvider.query().wherePackageName().isEqualTo(EXISTING_PACKAGENAME).get();
        mTestAppProvider.snapshot();

        mTestAppProvider.restore();

        TestAppQueryBuilder query =
                mTestAppProvider.query().wherePackageName().isEqualTo(EXISTING_PACKAGENAME);
        assertThrows(NotFoundException.class, query::get);
    }

    @Test
    public void restore_noSnapshot_throwsException() {
        assertThrows(IllegalStateException.class, mTestAppProvider::restore);
    }

    @Test
    public void any_doesNotReturnPackageQueryOnlyTestApps() {
        Set<String> testAppPackageNames = new HashSet<>();

        while (true) {
            try {
                testAppPackageNames.add(mTestAppProvider.any().packageName());
            } catch (NotFoundException e) {
                // Expected when we run out of test apps
                break;
            }
        }

        assertThat(testAppPackageNames).doesNotContain(QUERY_ONLY_TEST_APP_PACKAGE_NAME);
    }

    @Test
    public void query_queryByPackageName_doesReturnPackageQueryOnlyTestApps() {
        assertThat(
                mTestAppProvider.query()
                        .wherePackageName().isEqualTo(QUERY_ONLY_TEST_APP_PACKAGE_NAME)
                        .get()).isNotNull();
    }

    @Test
    public void query_byFeature_returnsDifferentTestAppsForSameQuery() {
        TestApp firstResult = mTestAppProvider.query()
                .whereTestOnly().isFalse()
                .get();
        TestApp secondResult = mTestAppProvider.query()
                .whereTestOnly().isFalse()
                .get();

        assertThat(firstResult).isNotEqualTo(secondResult);
    }

    @Test
    public void query_testOnly_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .whereTestOnly().isTrue()
                .get();

        assertThat(testApp.testOnly()).isTrue();
    }

    @Test
    public void query_notTestOnly_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .whereTestOnly().isFalse()
                .get();

        assertThat(testApp.testOnly()).isFalse();
    }

    @Test
    public void query_minSdkVersion_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .whereMinSdkVersion().isGreaterThanOrEqualTo(28)
                .get();

        assertThat(testApp.minSdkVersion()).isAtLeast(28);
    }

    @Test
    public void query_targetSdkVersion_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .whereTargetSdkVersion().isGreaterThanOrEqualTo(28)
                .get();

        assertThat(testApp.targetSdkVersion()).isAtLeast(28);
    }

    @Test
    public void query_withPermission_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .wherePermissions().contains(PERMISSION_DECLARED_BY_TESTAPP)
                .get();

        assertThat(testApp.permissions()).contains(PERMISSION_DECLARED_BY_TESTAPP);
    }

    @Test
    public void query_withoutPermission_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .wherePermissions().doesNotContain(PERMISSION_DECLARED_BY_TESTAPP)
                .get();

        assertThat(testApp.permissions()).doesNotContain(PERMISSION_DECLARED_BY_TESTAPP);
    }

    @Test
    public void query_metadata_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .whereMetadata().key(METADATA_KEY).stringValue().isEqualTo(METADATA_VALUE)
                .get();

        assertThat(testApp.metadata().get(METADATA_KEY)).isEqualTo(METADATA_VALUE);
    }

    @Test
    public void query_withExistingActivity_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .whereActivities().contains(
                        activity().where().activityClass()
                            .className().isEqualTo(KNOWN_EXISTING_TESTAPP_ACTIVITY_CLASSNAME)
                )
                .get();

        Set<String> activityClassNames = testApp.activities().stream()
                .map(a -> a.className()).collect(Collectors.toSet());
        assertThat(activityClassNames).contains(KNOWN_EXISTING_TESTAPP_ACTIVITY_CLASSNAME);
    }

    @Test
    public void query_withAnyActivity_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .whereActivities().isNotEmpty()
                .get();

        assertThat(testApp.activities()).isNotEmpty();
    }

    @Test
    public void query_withNoActivity_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .whereActivities().isEmpty()
                .get();

        assertThat(testApp.activities()).isEmpty();
    }

    @Test
    @Ignore // Restore when we have a way of querying for device admin in nene
    public void query_isDeviceAdmin_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .whereIsDeviceAdmin().isTrue()
                .get();

        assertThat(testApp.packageName()).endsWith("DeviceAdminTestApp");
    }

    @Test
    public void query_isNotDeviceAdmin_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .whereIsDeviceAdmin().isFalse()
                .get();

        assertThat(testApp.packageName()).isNotEqualTo(
                "com.android.bedstead.testapp.DeviceAdminTestApp");
    }

    @Test
    public void query_isHeadlessDOSingleUser_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .allowInternalBedsteadTestApps()
                .whereIsHeadlessDOSingleUser().isTrue()
                .get();

        assertThat(testApp.metadata().getString("headless_do_single_user"))
                .isEqualTo("true");
    }

    @Test
    public void query_isNotHeadlessDOSingleUser_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .allowInternalBedsteadTestApps()
                .whereIsHeadlessDOSingleUser().isFalse()
                .get();

        assertThat(testApp.metadata().getString("headless_do_single_user")).isNull();
    }

    @Test
    public void query_doesNotSpecifySharedUserId_sharedUserIdIsNull() {
        TestApp testApp = mTestAppProvider.query()
                .get();

        assertThat(testApp.sharedUserId()).isNull();
    }

    @Test
    @Ignore("re-enable when we have a test app which has a shareuserid")
    public void query_doesSpecifySharedUserId_matches() {
        TestApp testApp = mTestAppProvider.query()
                .whereSharedUserId().isEqualTo("com.android.bedstead")
                .get();

        assertThat(testApp.sharedUserId()).isEqualTo("com.android.bedstead");
    }

    @Test
    public void query_specifiesNullSharedUserId_matches() {
        TestApp testApp = mTestAppProvider.query()
                .whereSharedUserId().isNull()
                .get();

        assertThat(testApp.sharedUserId()).isNull();
    }
    
    @Test
    public void query_queryAnnotationSpecifiesPackageName_matches() {
        Query queryAnnotation = queryBuilder()
                .packageName(stringQueryBuilder().isEqualTo(STRING_VALUE).build())
                .build();

        TestAppQueryBuilder queryBuilder = mTestAppProvider.query(queryAnnotation);

        assertThat(queryBuilder.mPackageName.matches(STRING_VALUE)).isTrue();
        assertThat(queryBuilder.mPackageName.matches(DIFFERENT_STRING_VALUE)).isFalse();
    }

    @Test
    public void query_queryAnnotationSpecifiesTargetSdkVersion_matches() {
        Query queryAnnotation = queryBuilder()
                .targetSdkVersion(integerQueryBuilder().isEqualTo(INTEGER_VALUE).build())
                .build();

        TestAppQueryBuilder queryBuilder = mTestAppProvider.query(queryAnnotation);

        assertThat(queryBuilder.mTargetSdkVersion.matches(INTEGER_VALUE)).isTrue();
        assertThat(queryBuilder.mTargetSdkVersion.matches(HIGHER_INTEGER_VALUE)).isFalse();
    }

    @Test
    public void query_queryAnnotationSpecifiesMaxSdkVersion_matches() {
        Query queryAnnotation = queryBuilder()
                .maxSdkVersion(integerQueryBuilder().isEqualTo(INTEGER_VALUE).build())
                .build();

        TestAppQueryBuilder queryBuilder = mTestAppProvider.query(queryAnnotation);

        assertThat(queryBuilder.mMaxSdkVersion.matches(INTEGER_VALUE)).isTrue();
        assertThat(queryBuilder.mMaxSdkVersion.matches(HIGHER_INTEGER_VALUE)).isFalse();
    }

    @Test
    public void query_queryAnnotationSpecifiesMinSdkVersion_matches() {
        Query queryAnnotation = queryBuilder()
                .minSdkVersion(integerQueryBuilder().isEqualTo(INTEGER_VALUE).build())
                .build();

        TestAppQueryBuilder queryBuilder = mTestAppProvider.query(queryAnnotation);

        assertThat(queryBuilder.mMinSdkVersion.matches(INTEGER_VALUE)).isTrue();
        assertThat(queryBuilder.mMinSdkVersion.matches(HIGHER_INTEGER_VALUE)).isFalse();
    }

    @Test
    public void query_stringQueryAnnotationSpecifiesIsEqualTo_matches() {
        Query queryAnnotation = queryBuilder()
                .packageName(stringQueryBuilder().isEqualTo(STRING_VALUE).build())
                .build();

        TestAppQueryBuilder queryBuilder = mTestAppProvider.query(queryAnnotation);

        assertThat(queryBuilder.mPackageName.matches(STRING_VALUE)).isTrue();
        assertThat(queryBuilder.mPackageName.matches(DIFFERENT_STRING_VALUE)).isFalse();
    }

    @Test
    public void query_stringQueryAnnotationSpecifiesIsNotEqualTo_matches() {
        Query queryAnnotation = queryBuilder()
                .packageName(stringQueryBuilder().isNotEqualTo(STRING_VALUE).build())
                .build();

        TestAppQueryBuilder queryBuilder = mTestAppProvider.query(queryAnnotation);

        assertThat(queryBuilder.mPackageName.matches(STRING_VALUE)).isFalse();
        assertThat(queryBuilder.mPackageName.matches(DIFFERENT_STRING_VALUE)).isTrue();
    }

    @Test
    public void query_stringQueryAnnotationSpecifiesStartsWith_matches() {
        Query queryAnnotation = queryBuilder()
                .packageName(stringQueryBuilder().startsWith(STRING_VALUE).build())
                .build();

        TestAppQueryBuilder queryBuilder = mTestAppProvider.query(queryAnnotation);

        assertThat(queryBuilder.mPackageName.matches(STRING_VALUE + "A")).isTrue();
        assertThat(queryBuilder.mPackageName.matches(DIFFERENT_STRING_VALUE)).isFalse();
    }

    @Test
    public void query_stringQueryAnnotationSpecifiesIsNullTrue_matches() {
        Query queryAnnotation = queryBuilder()
                .packageName(stringQueryBuilder().isNull(OptionalBoolean.TRUE).build())
                .build();

        TestAppQueryBuilder queryBuilder = mTestAppProvider.query(queryAnnotation);

        assertThat(queryBuilder.mPackageName.matches(null)).isTrue();
        assertThat(queryBuilder.mPackageName.matches(STRING_VALUE)).isFalse();
    }

    @Test
    public void query_stringQueryAnnotationSpecifiesIsNullFalse_matches() {
        Query queryAnnotation = queryBuilder()
                .packageName(stringQueryBuilder().isNull(OptionalBoolean.FALSE).build())
                .build();

        TestAppQueryBuilder queryBuilder = mTestAppProvider.query(queryAnnotation);

        assertThat(queryBuilder.mPackageName.matches(null)).isFalse();
        assertThat(queryBuilder.mPackageName.matches(STRING_VALUE)).isTrue();
    }

    @Test
    public void query_integerQueryAnnotationSpecifiesIsEqualTo_matches() {
        Query queryAnnotation = queryBuilder()
                .minSdkVersion(integerQueryBuilder().isEqualTo(INTEGER_VALUE).build())
                .build();

        TestAppQueryBuilder queryBuilder = mTestAppProvider.query(queryAnnotation);

        assertThat(queryBuilder.mMinSdkVersion.matches(INTEGER_VALUE)).isTrue();
        assertThat(queryBuilder.mMinSdkVersion.matches(HIGHER_INTEGER_VALUE)).isFalse();
    }

    @Test
    public void query_integerQueryAnnotationSpecifiesIsGreaterThan_matches() {
        Query queryAnnotation = queryBuilder()
                .minSdkVersion(integerQueryBuilder().isGreaterThan(INTEGER_VALUE).build())
                .build();

        TestAppQueryBuilder queryBuilder = mTestAppProvider.query(queryAnnotation);

        assertThat(queryBuilder.mMinSdkVersion.matches(INTEGER_VALUE)).isFalse();
        assertThat(queryBuilder.mMinSdkVersion.matches(HIGHER_INTEGER_VALUE)).isTrue();
    }

    @Test
    public void query_integerQueryAnnotationSpecifiesIsGreaterThanOrEqualTo_matches() {
        Query queryAnnotation = queryBuilder()
                .minSdkVersion(integerQueryBuilder().isGreaterThanOrEqualTo(INTEGER_VALUE).build())
                .build();

        TestAppQueryBuilder queryBuilder = mTestAppProvider.query(queryAnnotation);

        assertThat(queryBuilder.mMinSdkVersion.matches(INTEGER_VALUE)).isTrue();
        assertThat(queryBuilder.mMinSdkVersion.matches(HIGHER_INTEGER_VALUE)).isTrue();
        assertThat(queryBuilder.mMinSdkVersion.matches(LOWER_INTEGER_VALUE)).isFalse();
    }

    @Test
    public void query_integerQueryAnnotationSpecifiesIsLessThan_matches() {
        Query queryAnnotation = queryBuilder()
                .minSdkVersion(integerQueryBuilder().isLessThan(INTEGER_VALUE).build())
                .build();

        TestAppQueryBuilder queryBuilder = mTestAppProvider.query(queryAnnotation);

        assertThat(queryBuilder.mMinSdkVersion.matches(INTEGER_VALUE)).isFalse();
        assertThat(queryBuilder.mMinSdkVersion.matches(LOWER_INTEGER_VALUE)).isTrue();
    }

    @Test
    public void query_integerQueryAnnotationSpecifiesIsLessThanOrEqualTo_matches() {
        Query queryAnnotation = queryBuilder()
                .minSdkVersion(integerQueryBuilder().isLessThanOrEqualTo(INTEGER_VALUE).build())
                .build();

        TestAppQueryBuilder queryBuilder = mTestAppProvider.query(queryAnnotation);

        assertThat(queryBuilder.mMinSdkVersion.matches(INTEGER_VALUE)).isTrue();
        assertThat(queryBuilder.mMinSdkVersion.matches(LOWER_INTEGER_VALUE)).isTrue();
        assertThat(queryBuilder.mMinSdkVersion.matches(HIGHER_INTEGER_VALUE)).isFalse();
    }
}
