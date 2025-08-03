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

package com.android.adservices.service.appsearch;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.adservices.AdServicesManager;
import android.app.adservices.topics.TopicParcel;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.data.common.BooleanFileDatastore;
import com.android.adservices.data.consent.AppConsentDao;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.service.common.feature.PrivacySandboxFeatureType;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.App;
import com.android.adservices.service.consent.ConsentConstants;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.topics.BlockedTopicsManager;
import com.android.adservices.service.ui.enrollment.collection.PrivacySandboxEnrollmentChannelCollection;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.io.IOException;
import java.util.List;

@SpyStatic(AppSearchConsentWorker.class)
@SpyStatic(PackageManagerCompatUtils.class)
@SpyStatic(SdkLevel.class)
@SpyStatic(FlagsFactory.class)
public final class AppSearchConsentManagerTest extends AdServicesExtendedMockitoTestCase {

    private static final String API_TYPE = AdServicesApiType.TOPICS.toPpApiDatastoreKey();
    private static final String PACKAGE_NAME1 = "foo.bar.one";
    private static final String PACKAGE_NAME2 = "foo.bar.two";
    private static final String PACKAGE_NAME3 = "foo.bar.three";
    private static final Topic TOPIC1 = Topic.create(1, 1, 1);
    private static final Topic TOPIC2 = Topic.create(12, 12, 12);
    private static final Topic TOPIC3 = Topic.create(123, 123, 123);

    private AppSearchConsentManager mAppSearchConsentManager;
    @Mock private AppSearchConsentWorker mAppSearchConsentWorker;
    @Mock private Flags mFlags;
    @Mock private AdServicesManager mAdServicesManager;
    @Mock private BooleanFileDatastore mDatastore;
    @Mock private SharedPreferences mSharedPrefs;
    @Mock private AppConsentDao mAppConsentDao;
    @Mock private SharedPreferences.Editor mEditor;

    @Before
    public void setup() {
        extendedMockito.mockGetFlags(mFlags);
        mAppSearchConsentManager =
                new AppSearchConsentManager(mSpyContext, mAppSearchConsentWorker);
        ApplicationInfo app1 = new ApplicationInfo();
        app1.packageName = PACKAGE_NAME1;
        ApplicationInfo app2 = new ApplicationInfo();
        app2.packageName = PACKAGE_NAME2;
        ApplicationInfo app3 = new ApplicationInfo();
        app3.packageName = PACKAGE_NAME3;
        ExtendedMockito.doReturn(List.of(app1, app2, app3))
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));
    }

    @Test
    public void testGetConsent() {
        when(mAppSearchConsentWorker.getConsent(API_TYPE)).thenReturn(false);
        assertThat(mAppSearchConsentManager.getConsent(API_TYPE)).isEqualTo(false);

        when(mAppSearchConsentWorker.getConsent(API_TYPE)).thenReturn(true);
        assertThat(mAppSearchConsentManager.getConsent(API_TYPE)).isEqualTo(true);
    }

    @Test
    public void testSetConsent() {
        mAppSearchConsentManager.setConsent(API_TYPE, true);
        verify(mAppSearchConsentWorker).setConsent(API_TYPE, true);

        mAppSearchConsentManager.setConsent(API_TYPE, false);
        verify(mAppSearchConsentWorker).setConsent(API_TYPE, false);
    }

    @Test
    public void testKnownAppsWithConsent() {
        String consentType = AppSearchAppConsentDao.APPS_WITH_CONSENT;
        when(mAppSearchConsentWorker.getAppsWithConsent(eq(consentType)))
                .thenReturn(List.of(PACKAGE_NAME1, PACKAGE_NAME2));
        List<App> result = mAppSearchConsentManager.getKnownAppsWithConsent();
        assertThat(result.size()).isEqualTo(2);

        String package1 = result.get(0).getPackageName();
        String package2 = result.get(1).getPackageName();
        assertThat(package1.equals(PACKAGE_NAME1) || package2.equals(PACKAGE_NAME1)).isTrue();
        assertThat(package1.equals(PACKAGE_NAME2) || package2.equals(PACKAGE_NAME2)).isTrue();
    }

    @Test
    public void testAppsWithRevokedConsent() {
        String consentType = AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT;
        when(mAppSearchConsentWorker.getAppsWithConsent(eq(consentType)))
                .thenReturn(List.of(PACKAGE_NAME1, PACKAGE_NAME2));
        List<App> result = mAppSearchConsentManager.getAppsWithRevokedConsent();
        assertThat(result.size()).isEqualTo(2);

        String package1 = result.get(0).getPackageName();
        String package2 = result.get(1).getPackageName();
        assertThat(package1.equals(PACKAGE_NAME1) || package2.equals(PACKAGE_NAME1)).isTrue();
        assertThat(package1.equals(PACKAGE_NAME2) || package2.equals(PACKAGE_NAME2)).isTrue();
    }

    @Test
    public void testRevokeConsentForApp() {
        App app = App.create(PACKAGE_NAME1);
        mAppSearchConsentManager.revokeConsentForApp(app);
        verify(mAppSearchConsentWorker)
                .addAppWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT, app.getPackageName());
        verify(mAppSearchConsentWorker)
                .removeAppWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_CONSENT, app.getPackageName());
    }

    @Test
    public void testRestoreConsentForApp() {
        App app = App.create(PACKAGE_NAME1);
        mAppSearchConsentManager.restoreConsentForApp(app);
        verify(mAppSearchConsentWorker)
                .addAppWithConsent(AppSearchAppConsentDao.APPS_WITH_CONSENT, app.getPackageName());
        verify(mAppSearchConsentWorker)
                .removeAppWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT, app.getPackageName());
    }

    @Test
    public void testClearAllAppConsentData() {
        mAppSearchConsentManager.clearAllAppConsentData();
        verify(mAppSearchConsentWorker)
                .clearAppsWithConsent(AppSearchAppConsentDao.APPS_WITH_CONSENT);
        verify(mAppSearchConsentWorker)
                .clearAppsWithConsent(AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT);
    }

    @Test
    public void testClearKnownAppsWithConsent() throws Exception {
        mAppSearchConsentManager.clearKnownAppsWithConsent();
        verify(mAppSearchConsentWorker)
                .clearAppsWithConsent(AppSearchAppConsentDao.APPS_WITH_CONSENT);
    }

    @Test
    public void testIsFledgeConsentRevokedForApp_consented() {
        when(mAppSearchConsentWorker.getAppsWithConsent(AppSearchAppConsentDao.APPS_WITH_CONSENT))
                .thenReturn(List.of(PACKAGE_NAME1));
        when(mAppSearchConsentWorker.getAppsWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT))
                .thenReturn(List.of());
        assertThat(mAppSearchConsentManager.isFledgeConsentRevokedForApp(PACKAGE_NAME1)).isFalse();
    }

    @Test
    public void testIsFledgeConsentRevokedForApp_revoked() {
        when(mAppSearchConsentWorker.getAppsWithConsent(AppSearchAppConsentDao.APPS_WITH_CONSENT))
                .thenReturn(List.of(PACKAGE_NAME1));
        when(mAppSearchConsentWorker.getAppsWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT))
                .thenReturn(List.of(PACKAGE_NAME1));
        assertThat(mAppSearchConsentManager.isFledgeConsentRevokedForApp(PACKAGE_NAME1)).isTrue();
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUse() {
        when(mAppSearchConsentWorker.getAppsWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT))
                .thenReturn(List.of());
        when(mAppSearchConsentWorker.addAppWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_CONSENT, PACKAGE_NAME1))
                .thenReturn(true);
        boolean result =
                mAppSearchConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        PACKAGE_NAME1);
        assertThat(result).isFalse();
        verify(mAppSearchConsentWorker)
                .addAppWithConsent(AppSearchAppConsentDao.APPS_WITH_CONSENT, PACKAGE_NAME1);
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUse_revoked() {
        when(mAppSearchConsentWorker.getAppsWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT))
                .thenReturn(List.of(PACKAGE_NAME2));
        boolean result =
                mAppSearchConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        PACKAGE_NAME2);
        assertThat(result).isTrue();
        verify(mAppSearchConsentWorker)
                .getAppsWithConsent(AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT);

        // Verify that addAppWithConsent is not called
        verifyNoMoreInteractions(mAppSearchConsentWorker);
    }

    @Test
    public void testClearConsentForUninstalledApp() {
        mAppSearchConsentManager.clearConsentForUninstalledApp(PACKAGE_NAME1);
        verify(mAppSearchConsentWorker)
                .removeAppWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT, PACKAGE_NAME1);
        verify(mAppSearchConsentWorker)
                .removeAppWithConsent(AppSearchAppConsentDao.APPS_WITH_CONSENT, PACKAGE_NAME1);
    }

    @Test
    public void testRecordNotificationDisplayed() {
        mAppSearchConsentManager.recordNotificationDisplayed(true);
        verify(mAppSearchConsentWorker).recordNotificationDisplayed(true);
    }

    @Test
    public void testRecordGaUxNotificationDisplayed() {
        mAppSearchConsentManager.recordGaUxNotificationDisplayed(true);
        verify(mAppSearchConsentWorker).recordGaUxNotificationDisplayed(true);
    }

    @Test
    public void testWasNotificationDisplayed() {
        when(mAppSearchConsentWorker.wasNotificationDisplayed()).thenReturn(false);
        assertThat(mAppSearchConsentManager.wasNotificationDisplayed()).isFalse();
        verify(mAppSearchConsentWorker).wasNotificationDisplayed();
    }

    @Test
    public void testWasGaUxNotificationDisplayed() {
        when(mAppSearchConsentWorker.wasGaUxNotificationDisplayed()).thenReturn(false);
        assertThat(mAppSearchConsentManager.wasGaUxNotificationDisplayed()).isFalse();
        verify(mAppSearchConsentWorker).wasGaUxNotificationDisplayed();
    }

    @Test
    public void testGetCurrentPrivacySandboxFeature() {
        when(mAppSearchConsentWorker.getPrivacySandboxFeature())
                .thenReturn(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);
        assertThat(mAppSearchConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);
        verify(mAppSearchConsentWorker).getPrivacySandboxFeature();
    }

    @Test
    public void testSetCurrentPrivacySandboxFeature() {
        mAppSearchConsentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT);
        verify(mAppSearchConsentWorker)
                .setCurrentPrivacySandboxFeature(
                        PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT);
    }

    @Test
    public void testGetUserManualInteractionsWithConsent() {
        when(mAppSearchConsentWorker.getUserManualInteractionWithConsent())
                .thenReturn(ConsentManager.MANUAL_INTERACTIONS_RECORDED);
        assertThat(mAppSearchConsentManager.getUserManualInteractionWithConsent())
                .isEqualTo(ConsentManager.MANUAL_INTERACTIONS_RECORDED);
        verify(mAppSearchConsentWorker).getUserManualInteractionWithConsent();
    }

    @Test
    public void testRecordUserManualInteractionWithConsent() {
        mAppSearchConsentManager.recordUserManualInteractionWithConsent(
                ConsentManager.NO_MANUAL_INTERACTIONS_RECORDED);
        verify(mAppSearchConsentWorker)
                .recordUserManualInteractionWithConsent(
                        ConsentManager.NO_MANUAL_INTERACTIONS_RECORDED);
    }

    @Test
    public void testBlockTopic() {
        mAppSearchConsentManager.blockTopic(TOPIC1);
        verify(mAppSearchConsentWorker).recordBlockedTopic(eq(TOPIC1));
    }

    @Test
    public void testUnblockTopic() {
        mAppSearchConsentManager.unblockTopic(TOPIC2);
        verify(mAppSearchConsentWorker).recordUnblockedTopic(eq(TOPIC2));
    }

    @Test
    public void testClearAllBlockedTopics() {
        mAppSearchConsentManager.clearAllBlockedTopics();
        verify(mAppSearchConsentWorker).clearBlockedTopics();
    }

    @Test
    public void testRetrieveAllBlockedTopics() {
        when(mAppSearchConsentWorker.getBlockedTopics())
                .thenReturn(List.of(TOPIC1, TOPIC2, TOPIC3));
        List<Topic> result = mAppSearchConsentManager.retrieveAllBlockedTopics();
        verify(mAppSearchConsentWorker).getBlockedTopics();
        assertThat(result.size()).isEqualTo(3);
        assertThat(result.contains(TOPIC1)).isTrue();
        assertThat(result.contains(TOPIC2)).isTrue();
        assertThat(result.contains(TOPIC3)).isTrue();
    }

    @Test
    public void testShouldInitConsentDataFromAppSearch_notT() {
        ExtendedMockito.doReturn(false).when(() -> SdkLevel.isAtLeastT());
        SharedPreferences mSharedPrefs = mock(SharedPreferences.class);
        AdServicesManager mAdServicesManager = mock(AdServicesManager.class);
        boolean result =
                mAppSearchConsentManager.shouldInitConsentDataFromAppSearch(
                        mSharedPrefs, mDatastore, mAdServicesManager, false);
        assertThat(result).isFalse();
    }

    @Test
    public void testShouldInitConsentDataFromAppSearch_flagDisabled() {
        ExtendedMockito.doReturn(true).when(() -> SdkLevel.isAtLeastT());
        when(mFlags.getEnableAppsearchConsentData()).thenReturn(false);

        boolean result =
                mAppSearchConsentManager.shouldInitConsentDataFromAppSearch(
                        mSharedPrefs, mDatastore, mAdServicesManager, false);
        assertThat(result).isFalse();
    }

    @Test
    public void testShouldInitConsentDataFromAppSearch_hasMigrated() {
        ExtendedMockito.doReturn(true).when(() -> SdkLevel.isAtLeastT());
        when(mFlags.getEnableAppsearchConsentData()).thenReturn(true);
        when(mSharedPrefs.getBoolean(any(), eq(false))).thenReturn(true);

        boolean result =
                mAppSearchConsentManager.shouldInitConsentDataFromAppSearch(
                        mSharedPrefs, mDatastore, mAdServicesManager, false);
        assertThat(result).isFalse();
    }

    @Test
    public void testShouldInitConsentDataFromAppSearch_notificationWasDisplayedInSystemServer() {
        ExtendedMockito.doReturn(true).when(() -> SdkLevel.isAtLeastT());
        when(mFlags.getEnableAppsearchConsentData()).thenReturn(true);
        when(mSharedPrefs.getBoolean(any(), eq(true))).thenReturn(true);
        when(mAdServicesManager.wasNotificationDisplayed()).thenReturn(true);

        boolean result =
                mAppSearchConsentManager.shouldInitConsentDataFromAppSearch(
                        mSharedPrefs, mDatastore, mAdServicesManager, false);
        assertThat(result).isFalse();
    }

    @Test
    public void testShouldInitConsentDataFromAppSearch_notificationWasDisplayedInPPAPI() {
        ExtendedMockito.doReturn(true).when(() -> SdkLevel.isAtLeastT());
        when(mFlags.getEnableAppsearchConsentData()).thenReturn(true);
        when(mSharedPrefs.getBoolean(any(), eq(true))).thenReturn(true);
        when(mAdServicesManager.wasNotificationDisplayed()).thenReturn(false);
        when(mAdServicesManager.wasGaUxNotificationDisplayed()).thenReturn(false);
        when(mDatastore.get(any())).thenReturn(true);

        boolean result =
                mAppSearchConsentManager.shouldInitConsentDataFromAppSearch(
                        mSharedPrefs, mDatastore, mAdServicesManager, false);
        assertThat(result).isFalse();
    }

    @Test
    public void testShouldInitConsentDataFromAppSearch_notificationNotDisplayed() {
        ExtendedMockito.doReturn(true).when(() -> SdkLevel.isAtLeastT());
        when(mFlags.getEnableAppsearchConsentData()).thenReturn(true);
        when(mSharedPrefs.getBoolean(any(), eq(true))).thenReturn(true);
        when(mAdServicesManager.wasNotificationDisplayed()).thenReturn(false);
        when(mAdServicesManager.wasGaUxNotificationDisplayed()).thenReturn(false);
        when(mDatastore.get(any())).thenReturn(false);
        when(mAppSearchConsentWorker.wasNotificationDisplayed()).thenReturn(false);
        when(mAppSearchConsentWorker.wasGaUxNotificationDisplayed()).thenReturn(false);

        boolean result =
                mAppSearchConsentManager.shouldInitConsentDataFromAppSearch(
                        mSharedPrefs, mDatastore, mAdServicesManager, false);
        assertThat(result).isFalse();
    }

    @Test
    public void testShouldInitConsentDataFromAppSearch() {
        initConsentDataForMigration();
        boolean result =
                mAppSearchConsentManager.shouldInitConsentDataFromAppSearch(
                        mSharedPrefs, mDatastore, mAdServicesManager, false);
        assertThat(result).isTrue();
    }

    @Test
    public void testMigrateConsentData_notificationNotDisplayed_u18MigrationDisabled()
            throws IOException {
        initConsentDataForMigration();
        when(mFlags.getEnableU18AppsearchMigration()).thenReturn(false);
        when(mAppSearchConsentWorker.wasNotificationDisplayed()).thenReturn(false);
        when(mAppSearchConsentWorker.wasGaUxNotificationDisplayed()).thenReturn(false);
        boolean result =
                mAppSearchConsentManager.migrateConsentDataIfNeeded(
                        mSharedPrefs, mDatastore, mAdServicesManager, mAppConsentDao);
        assertThat(result).isFalse();
    }

    @Test
    public void testMigrateConsentData_notificationNotDisplayed_u18MigrationEnabled()
            throws IOException {
        initConsentDataForMigration();
        when(mFlags.getEnableU18AppsearchMigration()).thenReturn(true);
        when(mAppSearchConsentWorker.wasNotificationDisplayed()).thenReturn(false);
        when(mAppSearchConsentWorker.wasGaUxNotificationDisplayed()).thenReturn(false);
        when(mAppSearchConsentWorker.wasU18NotificationDisplayed()).thenReturn(false);
        boolean result =
                mAppSearchConsentManager.migrateConsentDataIfNeeded(
                        mSharedPrefs, mDatastore, mAdServicesManager, mAppConsentDao);
        assertThat(result).isFalse();
    }

    @Test
    public void testMigrateConsentData_u18UxNotificationDisplayed_u18MigrationEnabled()
            throws IOException {
        initConsentDataForMigration();
        when(mFlags.getEnableU18AppsearchMigration()).thenReturn(true);
        when(mAppSearchConsentWorker.wasU18NotificationDisplayed()).thenReturn(true);
        when(mAppSearchConsentWorker.wasNotificationDisplayed()).thenReturn(false);
        when(mAppSearchConsentWorker.wasGaUxNotificationDisplayed()).thenReturn(false);
        when(mAppSearchConsentWorker.getAppsWithConsent(any())).thenReturn(List.of());
        when(mAppSearchConsentWorker.getPrivacySandboxFeature())
                .thenReturn(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);
        boolean result =
                mAppSearchConsentManager.migrateConsentDataIfNeeded(
                        mSharedPrefs, mDatastore, mAdServicesManager, mAppConsentDao);
        expect.that(result).isTrue();
        verify(mDatastore).put(eq(ConsentConstants.WAS_U18_NOTIFICATION_DISPLAYED), eq(true));
        verify(mAdServicesManager).setU18NotificationDisplayed(true);
        verify(mDatastore, atLeast(5)).put(any(), anyBoolean());
        verify(mEditor)
                .putBoolean(eq(BlockedTopicsManager.SHARED_PREFS_KEY_HAS_MIGRATED), eq(true));
        verify(mEditor).commit();
    }

    @Test
    public void testMigrateConsentData_betaUxNotificationDisplayed() throws IOException {
        initConsentDataForMigration();
        when(mFlags.getEnableU18AppsearchMigration()).thenReturn(false);
        when(mAppSearchConsentWorker.wasNotificationDisplayed()).thenReturn(true);
        when(mAppSearchConsentWorker.wasGaUxNotificationDisplayed()).thenReturn(false);
        when(mAppSearchConsentWorker.getAppsWithConsent(any())).thenReturn(List.of());
        when(mAppSearchConsentWorker.getPrivacySandboxFeature())
                .thenReturn(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);
        boolean result =
                mAppSearchConsentManager.migrateConsentDataIfNeeded(
                        mSharedPrefs, mDatastore, mAdServicesManager, mAppConsentDao);
        assertThat(result).isTrue();
        verify(mDatastore).put(eq(ConsentConstants.NOTIFICATION_DISPLAYED_ONCE), eq(true));
        verify(mAdServicesManager).recordNotificationDisplayed(true);
        verify(mDatastore, atLeast(5)).put(any(), anyBoolean());
        verify(mEditor)
                .putBoolean(eq(BlockedTopicsManager.SHARED_PREFS_KEY_HAS_MIGRATED), eq(true));
        verify(mEditor).commit();
    }

    @Test
    public void testMigrateConsentData() throws IOException {
        List appsWithConsent = List.of(PACKAGE_NAME1, PACKAGE_NAME2);
        List appsRevoked = List.of(PACKAGE_NAME3);
        List<Topic> blockedTopics = List.of(TOPIC1, TOPIC2, TOPIC3);
        when(mAppSearchConsentWorker.wasNotificationDisplayed()).thenReturn(false);
        when(mAppSearchConsentWorker.wasGaUxNotificationDisplayed()).thenReturn(true);
        when(mAppSearchConsentWorker.getAppsWithConsent(
                        eq(AppSearchAppConsentDao.APPS_WITH_CONSENT)))
                .thenReturn(appsWithConsent);
        when(mAppSearchConsentWorker.getAppsWithConsent(
                        eq(AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT)))
                .thenReturn(appsRevoked);
        when(mAppSearchConsentWorker.getPrivacySandboxFeature())
                .thenReturn(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);
        when(mAppSearchConsentWorker.getBlockedTopics()).thenReturn(blockedTopics);
        initConsentDataForMigration();
        when(mFlags.getEnableU18AppsearchMigration()).thenReturn(false);

        boolean result =
                mAppSearchConsentManager.migrateConsentDataIfNeeded(
                        mSharedPrefs, mDatastore, mAdServicesManager, mAppConsentDao);
        assertThat(result).isTrue();

        verify(mDatastore).put(eq(ConsentConstants.GA_UX_NOTIFICATION_DISPLAYED_ONCE), eq(true));
        verify(mAdServicesManager).recordGaUxNotificationDisplayed(true);
        verify(mAppConsentDao).setConsentForApp(eq(PACKAGE_NAME1), eq(false));
        verify(mAppConsentDao).setConsentForApp(eq(PACKAGE_NAME2), eq(false));
        verify(mAppConsentDao).setConsentForApp(eq(PACKAGE_NAME3), eq(true));
        verify(mAdServicesManager).setConsentForApp(eq(PACKAGE_NAME1), anyInt(), eq(false));
        verify(mAdServicesManager).setConsentForApp(eq(PACKAGE_NAME2), anyInt(), eq(false));
        verify(mAdServicesManager).setConsentForApp(eq(PACKAGE_NAME3), anyInt(), eq(true));
        verify(mDatastore)
                .put(eq(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT.name()), eq(true));
        verify(mDatastore, atLeast(4)).put(any(), eq(false));
        verify(mEditor)
                .putBoolean(eq(BlockedTopicsManager.SHARED_PREFS_KEY_HAS_MIGRATED), eq(true));
        verify(mEditor).commit();
        // Verify blocked topics migration.
        ArgumentCaptor<List<TopicParcel>> argument = ArgumentCaptor.forClass(List.class);
        verify(mAdServicesManager).recordBlockedTopic(argument.capture());
        assertThat(argument.getValue().size()).isEqualTo(blockedTopics.size());
        for (TopicParcel topicParcel : argument.getValue()) {
            assertThat(List.of(TOPIC1.getTopic(), TOPIC2.getTopic(), TOPIC3.getTopic()))
                    .contains(topicParcel.getTopicId());
            List taxonomies =
                    List.of(
                            TOPIC1.getTaxonomyVersion(),
                            TOPIC2.getTaxonomyVersion(),
                            TOPIC3.getTaxonomyVersion());
            assertThat(taxonomies).contains(topicParcel.getTaxonomyVersion());
            List models =
                    List.of(
                            TOPIC1.getModelVersion(),
                            TOPIC2.getModelVersion(),
                            TOPIC3.getModelVersion());
            assertThat(models).contains(topicParcel.getModelVersion());
        }
    }

    @Test
    public void testMigrateConsentData_FromExtServices() throws Exception {
        initConsentDataForMigration();
        when(mFlags.getEnableU18AppsearchMigration()).thenReturn(false);
        doReturn("com." + AdServicesCommon.ADEXTSERVICES_PACKAGE_NAME_SUFFIX)
                .when(mSpyContext)
                .getPackageName();
        boolean result =
                mAppSearchConsentManager.migrateConsentDataIfNeeded(
                        mSharedPrefs, mDatastore, mAdServicesManager, mAppConsentDao);
        assertWithMessage("result from migrateConsentDataIfNeeded on extServices")
                .that(result)
                .isFalse();
    }

    private void initConsentDataForMigration() {
        ExtendedMockito.doReturn(true).when(() -> SdkLevel.isAtLeastT());
        when(mFlags.getEnableAppsearchConsentData()).thenReturn(true);
        when(mSharedPrefs.getBoolean(any(), eq(true))).thenReturn(true);
        when(mAdServicesManager.wasNotificationDisplayed()).thenReturn(false);
        when(mAdServicesManager.wasGaUxNotificationDisplayed()).thenReturn(false);
        when(mAppSearchConsentWorker.wasNotificationDisplayed()).thenReturn(false);
        when(mDatastore.get(any())).thenReturn(false);
        when(mAppSearchConsentWorker.wasGaUxNotificationDisplayed()).thenReturn(true);
        when(mSharedPrefs.edit()).thenReturn(mEditor);
    }

    @Test
    public void setAdIdEnabledTest_trueBit() {
        mAppSearchConsentManager.setAdIdEnabled(true);
        verify(mAppSearchConsentWorker).setAdIdEnabled(true);
    }

    @Test
    public void setAdIdEnabledTest_falseBit() {
        mAppSearchConsentManager.setAdIdEnabled(false);
        verify(mAppSearchConsentWorker).setAdIdEnabled(false);
    }

    private void setAdIdEnabledTest(boolean isAdIdEnabled) {
        mAppSearchConsentManager.setAdIdEnabled(isAdIdEnabled);
        verify(mAppSearchConsentWorker).setAdIdEnabled(isAdIdEnabled);
    }

    @Test
    public void isAdIdEnabledTest_defaultFalseBit() {
        when(mAppSearchConsentWorker.isAdIdEnabled()).thenReturn(false);
        assertThat(mAppSearchConsentManager.isAdIdEnabled()).isFalse();
        verify(mAppSearchConsentWorker).isAdIdEnabled();
    }

    @Test
    public void setU18AccountTest_trueBit() {
        mAppSearchConsentManager.setU18Account(true);
        verify(mAppSearchConsentWorker).setU18Account(true);
    }

    @Test
    public void setU18AccountTest_falseBit() {
        mAppSearchConsentManager.setU18Account(false);
        verify(mAppSearchConsentWorker).setU18Account(false);
    }

    private void setU18AccountTest(boolean isU18Account) {
        mAppSearchConsentManager.setU18Account(isU18Account);
        verify(mAppSearchConsentWorker).setU18Account(isU18Account);
    }

    @Test
    public void isU18AccountTest_defaultFalseBit() {
        when(mAppSearchConsentWorker.isU18Account()).thenReturn(false);
        assertThat(mAppSearchConsentManager.isU18Account()).isFalse();
        verify(mAppSearchConsentWorker).isU18Account();
    }

    @Test
    public void setEntryPointEnabledTest_trueBit() {
        mAppSearchConsentManager.setEntryPointEnabled(true);
        verify(mAppSearchConsentWorker).setEntryPointEnabled(true);
    }

    @Test
    public void setEntryPointEnabledTest_falseBit() {
        mAppSearchConsentManager.setEntryPointEnabled(false);
        verify(mAppSearchConsentWorker).setEntryPointEnabled(false);
    }

    private void setEntryPointEnabledTest(boolean isEntryPointEnabled) {
        mAppSearchConsentManager.setEntryPointEnabled(isEntryPointEnabled);
        verify(mAppSearchConsentWorker).setEntryPointEnabled(isEntryPointEnabled);
    }

    @Test
    public void isEntryPointEnabledTest_defaultFalseBit() {
        when(mAppSearchConsentWorker.isEntryPointEnabled()).thenReturn(false);
        assertThat(mAppSearchConsentManager.isEntryPointEnabled()).isFalse();
        verify(mAppSearchConsentWorker).isEntryPointEnabled();
    }

    @Test
    public void setAdultAccountTest_trueBit() {
        mAppSearchConsentManager.setAdultAccount(true);
        verify(mAppSearchConsentWorker).setAdultAccount(true);
    }

    @Test
    public void setAdultAccountTest_falseBit() {
        mAppSearchConsentManager.setAdultAccount(false);
        verify(mAppSearchConsentWorker).setAdultAccount(false);
    }

    private void setAdultAccountTest(boolean isAdultAccount) {
        mAppSearchConsentManager.setAdultAccount(isAdultAccount);
        verify(mAppSearchConsentWorker).setAdultAccount(isAdultAccount);
    }

    @Test
    public void isAdultAccountTest_defaultFalseBit() {
        when(mAppSearchConsentWorker.isAdultAccount()).thenReturn(false);
        assertThat(mAppSearchConsentManager.isAdultAccount()).isFalse();
        verify(mAppSearchConsentWorker).isAdultAccount();
    }

    @Test
    public void setU18NotificationDisplayedTest_trueBit() {
        mAppSearchConsentManager.setU18NotificationDisplayed(true);
        verify(mAppSearchConsentWorker).setU18NotificationDisplayed(true);
    }

    @Test
    public void setU18NotificationDisplayedTest_falseBit() {
        mAppSearchConsentManager.setU18NotificationDisplayed(false);
        verify(mAppSearchConsentWorker).setU18NotificationDisplayed(false);
    }

    private void setU18NotificationDisplayedTest(boolean wasU18NotificationDisplayed) {
        mAppSearchConsentManager.setU18NotificationDisplayed(wasU18NotificationDisplayed);
        verify(mAppSearchConsentWorker).setU18NotificationDisplayed(wasU18NotificationDisplayed);
    }

    @Test
    public void wasU18NotificationDisplayedTest_defaultFalseBit() {
        when(mAppSearchConsentWorker.wasU18NotificationDisplayed()).thenReturn(false);
        assertThat(mAppSearchConsentManager.wasU18NotificationDisplayed()).isFalse();
        verify(mAppSearchConsentWorker).wasU18NotificationDisplayed();
    }

    @Test
    public void getUxTest() {
        mAppSearchConsentManager.getUx();
        verify(mAppSearchConsentWorker).getUx();
    }

    @Test
    public void setUxTest() {
        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            mAppSearchConsentManager.setUx(ux);
            verify(mAppSearchConsentWorker).setUx(ux);
        }
    }

    @Test
    public void getEnrollmentChannelTest() {
        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            mAppSearchConsentManager.getEnrollmentChannel(ux);
            verify(mAppSearchConsentWorker).getEnrollmentChannel(ux);
        }
    }

    @Test
    public void setEnrollmentChannelTest() {
        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            for (PrivacySandboxEnrollmentChannelCollection channel :
                    ux.getEnrollmentChannelCollection()) {
                mAppSearchConsentManager.setEnrollmentChannel(ux, channel);
                verify(mAppSearchConsentWorker).setEnrollmentChannel(ux, channel);
            }
        }
    }

    @Test
    public void uxConformanceTest() {
        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            doReturn(ux).when(mAppSearchConsentWorker).getUx();
            mAppSearchConsentManager.setUx(ux);
            assertThat(mAppSearchConsentManager.getUx()).isEqualTo(ux);
        }
    }

    @Test
    public void enrollmentChannelConformanceTest() {
        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            for (PrivacySandboxEnrollmentChannelCollection channel :
                    ux.getEnrollmentChannelCollection()) {
                doReturn(channel).when(mAppSearchConsentWorker).getEnrollmentChannel(ux);
                mAppSearchConsentManager.setEnrollmentChannel(ux, channel);
                assertThat(mAppSearchConsentManager.getEnrollmentChannel(ux)).isEqualTo(channel);
            }
        }
    }
}
