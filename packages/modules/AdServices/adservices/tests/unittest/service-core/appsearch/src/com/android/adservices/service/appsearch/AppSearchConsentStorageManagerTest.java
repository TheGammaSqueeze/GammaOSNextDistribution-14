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

import android.app.adservices.topics.TopicParcel;
import android.content.Context;
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
import com.android.adservices.service.consent.AdServicesStorageManager;
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
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;

@SpyStatic(AppSearchConsentWorker.class)
@SpyStatic(PackageManagerCompatUtils.class)
@SpyStatic(SdkLevel.class)
@SpyStatic(FlagsFactory.class)
public class AppSearchConsentStorageManagerTest extends AdServicesExtendedMockitoTestCase {

    @Mock private AppSearchConsentWorker mAppSearchConsentWorker;
    @Mock private AdServicesStorageManager mAdServicesStorageManager;
    @Mock private BooleanFileDatastore mDatastore;
    @Mock private SharedPreferences mSharedPrefs;
    @Mock private AppConsentDao mAppConsentDao;
    @Mock private SharedPreferences.Editor mEditor;

    @Mock private Flags mMockFlags;

    private AppSearchConsentStorageManager mAppSearchConsentStorageManager;
    private static final AdServicesApiType API_TYPE = AdServicesApiType.TOPICS;
    private static final String PACKAGE_NAME1 = "foo.bar.one";
    private static final String PACKAGE_NAME2 = "foo.bar.two";
    private static final String PACKAGE_NAME3 = "foo.bar.three";
    private static final Topic TOPIC1 = Topic.create(1, 1, 1);
    private static final Topic TOPIC2 = Topic.create(12, 12, 12);
    private static final Topic TOPIC3 = Topic.create(123, 123, 123);

    @Before
    public void setup() {
        doReturn(mAppSearchConsentWorker).when(() -> AppSearchConsentWorker.getInstance());
        extendedMockito.mockGetFlags(mMockFlags);
        mAppSearchConsentStorageManager =
                new AppSearchConsentStorageManager(mAppSearchConsentWorker);
        ApplicationInfo app1 = new ApplicationInfo();
        app1.packageName = PACKAGE_NAME1;
        ApplicationInfo app2 = new ApplicationInfo();
        app2.packageName = PACKAGE_NAME2;
        ApplicationInfo app3 = new ApplicationInfo();
        app3.packageName = PACKAGE_NAME3;
        doReturn(List.of(app1, app2, app3))
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));
    }

    @Test
    public void testGetConsent() {
        when(mAppSearchConsentWorker.getConsent(API_TYPE.toPpApiDatastoreKey())).thenReturn(false);
        expect.that(mAppSearchConsentStorageManager.getConsent(API_TYPE).isGiven()).isFalse();

        when(mAppSearchConsentWorker.getConsent(API_TYPE.toPpApiDatastoreKey())).thenReturn(true);
        expect.that(mAppSearchConsentStorageManager.getConsent(API_TYPE).isGiven()).isTrue();
    }

    @Test
    public void testSetConsent() {
        mAppSearchConsentStorageManager.setConsent(API_TYPE, true);
        verify(mAppSearchConsentWorker).setConsent(API_TYPE.toPpApiDatastoreKey(), true);

        mAppSearchConsentStorageManager.setConsent(API_TYPE, false);
        verify(mAppSearchConsentWorker).setConsent(API_TYPE.toPpApiDatastoreKey(), false);
    }

    @Test
    public void testKnownAppsWithConsent() {
        String consentType = AppSearchAppConsentDao.APPS_WITH_CONSENT;
        when(mAppSearchConsentWorker.getAppsWithConsent(eq(consentType)))
                .thenReturn(List.of(PACKAGE_NAME1, PACKAGE_NAME2));
        List<String> result = mAppSearchConsentStorageManager.getKnownAppsWithConsent();
        expect.that(result).containsExactly(PACKAGE_NAME1, PACKAGE_NAME2);
    }

    @Test
    public void testAppsWithRevokedConsent() {
        String consentType = AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT;
        when(mAppSearchConsentWorker.getAppsWithConsent(eq(consentType)))
                .thenReturn(List.of(PACKAGE_NAME1, PACKAGE_NAME2));
        List<String> result = mAppSearchConsentStorageManager.getAppsWithRevokedConsent();
        expect.that(result).hasSize(2);

        expect.that(result).containsExactly(PACKAGE_NAME1, PACKAGE_NAME2);
    }

    @Test
    public void testRevokeConsentForApp() {
        App app = App.create(PACKAGE_NAME1);
        mAppSearchConsentStorageManager.setConsentForApp(PACKAGE_NAME1, true);
        verify(mAppSearchConsentWorker)
                .addAppWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT, app.getPackageName());
        verify(mAppSearchConsentWorker)
                .removeAppWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_CONSENT, app.getPackageName());
    }

    @Test
    public void testRestoreConsentForApp() {
        mAppSearchConsentStorageManager.setConsentForApp(PACKAGE_NAME1, false);
        verify(mAppSearchConsentWorker)
                .addAppWithConsent(AppSearchAppConsentDao.APPS_WITH_CONSENT, PACKAGE_NAME1);
        verify(mAppSearchConsentWorker)
                .removeAppWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT, PACKAGE_NAME1);
    }

    @Test
    public void testClearAllAppConsentData() {
        mAppSearchConsentStorageManager.clearAllAppConsentData();
        verify(mAppSearchConsentWorker)
                .clearAppsWithConsent(AppSearchAppConsentDao.APPS_WITH_CONSENT);
        verify(mAppSearchConsentWorker)
                .clearAppsWithConsent(AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT);
    }

    @Test
    public void testClearKnownAppsWithConsent() throws Exception {
        mAppSearchConsentStorageManager.clearKnownAppsWithConsent();
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
        expect.that(mAppSearchConsentStorageManager.isConsentRevokedForApp(PACKAGE_NAME1))
                .isFalse();
    }

    @Test
    public void testIsFledgeConsentRevokedForApp_revoked() {
        when(mAppSearchConsentWorker.getAppsWithConsent(AppSearchAppConsentDao.APPS_WITH_CONSENT))
                .thenReturn(List.of(PACKAGE_NAME1));
        when(mAppSearchConsentWorker.getAppsWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT))
                .thenReturn(List.of(PACKAGE_NAME1));
        expect.that(mAppSearchConsentStorageManager.isConsentRevokedForApp(PACKAGE_NAME1)).isTrue();
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUse() {
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        when(mAppSearchConsentWorker.getAppsWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT))
                .thenReturn(List.of());
        when(mAppSearchConsentWorker.addAppWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_CONSENT, PACKAGE_NAME1))
                .thenReturn(true);
        boolean result =
                mAppSearchConsentStorageManager.setConsentForAppIfNew(PACKAGE_NAME1, false);
        expect.that(result).isFalse();
        verify(mAppSearchConsentWorker)
                .addAppWithConsent(AppSearchAppConsentDao.APPS_WITH_CONSENT, PACKAGE_NAME1);
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUse_revoked() {
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        when(mAppSearchConsentWorker.getAppsWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT))
                .thenReturn(List.of(PACKAGE_NAME2));
        boolean result =
                mAppSearchConsentStorageManager.setConsentForAppIfNew(PACKAGE_NAME2, false);
        expect.that(result).isTrue();
        verify(mAppSearchConsentWorker)
                .getAppsWithConsent(AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT);

        // Verify that addAppWithConsent is not called
        verifyNoMoreInteractions(mAppSearchConsentWorker);
    }

    @Test
    public void testClearConsentForUninstalledApp() {
        mAppSearchConsentStorageManager.clearConsentForUninstalledApp(PACKAGE_NAME1);
        verify(mAppSearchConsentWorker)
                .removeAppWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT, PACKAGE_NAME1);
        verify(mAppSearchConsentWorker)
                .removeAppWithConsent(AppSearchAppConsentDao.APPS_WITH_CONSENT, PACKAGE_NAME1);
    }

    @Test
    public void testRecordNotificationDisplayed() {
        mAppSearchConsentStorageManager.recordNotificationDisplayed(true);
        verify(mAppSearchConsentWorker).recordNotificationDisplayed(true);
    }

    @Test
    public void testRecordGaUxNotificationDisplayed() {
        mAppSearchConsentStorageManager.recordGaUxNotificationDisplayed(true);
        verify(mAppSearchConsentWorker).recordGaUxNotificationDisplayed(true);
    }

    @Test
    public void testWasNotificationDisplayed() {
        when(mAppSearchConsentWorker.wasNotificationDisplayed()).thenReturn(false);
        expect.that(mAppSearchConsentStorageManager.wasNotificationDisplayed()).isFalse();
        verify(mAppSearchConsentWorker).wasNotificationDisplayed();
    }

    @Test
    public void testWasGaUxNotificationDisplayed() {
        when(mAppSearchConsentWorker.wasGaUxNotificationDisplayed()).thenReturn(false);
        expect.that(mAppSearchConsentStorageManager.wasGaUxNotificationDisplayed()).isFalse();
        verify(mAppSearchConsentWorker).wasGaUxNotificationDisplayed();
    }

    @Test
    public void testGetCurrentPrivacySandboxFeature() {
        when(mAppSearchConsentWorker.getPrivacySandboxFeature())
                .thenReturn(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);
        expect.that(mAppSearchConsentStorageManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);
        verify(mAppSearchConsentWorker).getPrivacySandboxFeature();
    }

    @Test
    public void testSetCurrentPrivacySandboxFeature() {
        mAppSearchConsentStorageManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT);
        verify(mAppSearchConsentWorker)
                .setCurrentPrivacySandboxFeature(
                        PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT);
    }

    @Test
    public void testGetUserManualInteractionsWithConsent() {
        when(mAppSearchConsentWorker.getUserManualInteractionWithConsent())
                .thenReturn(ConsentManager.MANUAL_INTERACTIONS_RECORDED);
        expect.that(mAppSearchConsentStorageManager.getUserManualInteractionWithConsent())
                .isEqualTo(ConsentManager.MANUAL_INTERACTIONS_RECORDED);
        verify(mAppSearchConsentWorker).getUserManualInteractionWithConsent();
    }

    @Test
    public void testRecordUserManualInteractionWithConsent() {
        mAppSearchConsentStorageManager.recordUserManualInteractionWithConsent(
                ConsentManager.NO_MANUAL_INTERACTIONS_RECORDED);
        verify(mAppSearchConsentWorker)
                .recordUserManualInteractionWithConsent(
                        ConsentManager.NO_MANUAL_INTERACTIONS_RECORDED);
    }

    @Test
    public void testBlockTopic() {
        mAppSearchConsentStorageManager.blockTopic(TOPIC1);
        verify(mAppSearchConsentWorker).recordBlockedTopic(eq(TOPIC1));
    }

    @Test
    public void testUnblockTopic() {
        mAppSearchConsentStorageManager.unblockTopic(TOPIC2);
        verify(mAppSearchConsentWorker).recordUnblockedTopic(eq(TOPIC2));
    }

    @Test
    public void testClearAllBlockedTopics() {
        mAppSearchConsentStorageManager.clearAllBlockedTopics();
        verify(mAppSearchConsentWorker).clearBlockedTopics();
    }

    @Test
    public void testRetrieveAllBlockedTopics() {
        when(mAppSearchConsentWorker.getBlockedTopics())
                .thenReturn(List.of(TOPIC1, TOPIC2, TOPIC3));
        List<Topic> result = mAppSearchConsentStorageManager.retrieveAllBlockedTopics();
        verify(mAppSearchConsentWorker).getBlockedTopics();
        expect.that(result.size()).isEqualTo(3);
        expect.that(result).containsExactly(TOPIC1, TOPIC2, TOPIC3);
    }

    @Test
    public void testShouldInitConsentDataFromAppSearch_notT() {
        ExtendedMockito.doReturn(false).when(() -> SdkLevel.isAtLeastT());
        SharedPreferences mSharedPrefs = mock(SharedPreferences.class);
        AdServicesStorageManager mAdServicesStorageManager = mock(AdServicesStorageManager.class);
        boolean result =
                mAppSearchConsentStorageManager.shouldInitConsentDataFromAppSearch(
                        mSharedPrefs, mDatastore, mAdServicesStorageManager);
        expect.that(result).isFalse();
    }

    @Test
    public void testShouldInitConsentDataFromAppSearch_flagDisabled() {
        ExtendedMockito.doReturn(true).when(() -> SdkLevel.isAtLeastT());
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(false);

        boolean result =
                mAppSearchConsentStorageManager.shouldInitConsentDataFromAppSearch(
                        mSharedPrefs, mDatastore, mAdServicesStorageManager);
        expect.that(result).isFalse();
    }

    @Test
    public void testShouldInitConsentDataFromAppSearch_hasMigrated() {
        ExtendedMockito.doReturn(true).when(() -> SdkLevel.isAtLeastT());
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        when(mSharedPrefs.getBoolean(any(), eq(false))).thenReturn(true);

        boolean result =
                mAppSearchConsentStorageManager.shouldInitConsentDataFromAppSearch(
                        mSharedPrefs, mDatastore, mAdServicesStorageManager);
        expect.that(result).isFalse();
    }

    @Test
    public void testShouldInitConsentDataFromAppSearch_notificationWasDisplayedInSystemServer() {
        ExtendedMockito.doReturn(true).when(() -> SdkLevel.isAtLeastT());
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        when(mSharedPrefs.getBoolean(any(), eq(true))).thenReturn(true);
        when(mAdServicesStorageManager.wasNotificationDisplayed()).thenReturn(true);

        boolean result =
                mAppSearchConsentStorageManager.shouldInitConsentDataFromAppSearch(
                        mSharedPrefs, mDatastore, mAdServicesStorageManager);
        expect.that(result).isFalse();
    }

    @Test
    public void testShouldInitConsentDataFromAppSearch_notificationWasDisplayedInPPAPI() {
        ExtendedMockito.doReturn(true).when(() -> SdkLevel.isAtLeastT());
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        when(mSharedPrefs.getBoolean(any(), eq(true))).thenReturn(true);
        when(mAdServicesStorageManager.wasNotificationDisplayed()).thenReturn(false);
        when(mAdServicesStorageManager.wasGaUxNotificationDisplayed()).thenReturn(false);
        when(mDatastore.get(any())).thenReturn(true);

        boolean result =
                mAppSearchConsentStorageManager.shouldInitConsentDataFromAppSearch(
                        mSharedPrefs, mDatastore, mAdServicesStorageManager);
        expect.that(result).isFalse();
    }

    @Test
    public void testShouldInitConsentDataFromAppSearch_notificationNotDisplayed() {
        ExtendedMockito.doReturn(true).when(() -> SdkLevel.isAtLeastT());
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        when(mSharedPrefs.getBoolean(any(), eq(true))).thenReturn(true);
        when(mAdServicesStorageManager.wasNotificationDisplayed()).thenReturn(false);
        when(mAdServicesStorageManager.wasGaUxNotificationDisplayed()).thenReturn(false);
        when(mDatastore.get(any())).thenReturn(false);
        when(mAppSearchConsentWorker.wasNotificationDisplayed()).thenReturn(false);
        when(mAppSearchConsentWorker.wasGaUxNotificationDisplayed()).thenReturn(false);

        boolean result =
                mAppSearchConsentStorageManager.shouldInitConsentDataFromAppSearch(
                        mSharedPrefs, mDatastore, mAdServicesStorageManager);
        expect.that(result).isFalse();
    }

    @Test
    public void testShouldInitConsentDataFromAppSearch() {
        initConsentDataForMigration();
        boolean result =
                mAppSearchConsentStorageManager.shouldInitConsentDataFromAppSearch(
                        mSharedPrefs, mDatastore, mAdServicesStorageManager);
        expect.that(result).isTrue();
    }

    @Test
    public void testMigrateConsentData_notificationNotDisplayed() throws IOException {
        initConsentDataForMigration();
        when(mAppSearchConsentWorker.wasNotificationDisplayed()).thenReturn(false);
        when(mAppSearchConsentWorker.wasGaUxNotificationDisplayed()).thenReturn(false);
        boolean result =
                mAppSearchConsentStorageManager.migrateConsentDataIfNeeded(
                        mSharedPrefs, mDatastore, mAdServicesStorageManager, mAppConsentDao);
        expect.that(result).isFalse();
    }

    @Test
    public void testMigrateConsentData_betaUxNotificationDisplayed() throws IOException {
        initConsentDataForMigration();
        when(mAppSearchConsentWorker.wasNotificationDisplayed()).thenReturn(true);
        when(mAppSearchConsentWorker.wasGaUxNotificationDisplayed()).thenReturn(false);
        when(mAppSearchConsentWorker.getAppsWithConsent(any())).thenReturn(List.of());
        when(mAppSearchConsentWorker.getPrivacySandboxFeature())
                .thenReturn(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);
        boolean result =
                mAppSearchConsentStorageManager.migrateConsentDataIfNeeded(
                        mSharedPrefs, mDatastore, mAdServicesStorageManager, mAppConsentDao);
        expect.that(result).isTrue();
        verify(mDatastore).put(eq(ConsentConstants.NOTIFICATION_DISPLAYED_ONCE), eq(true));
        verify(mAdServicesStorageManager).recordNotificationDisplayed(true);
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

        boolean result =
                mAppSearchConsentStorageManager.migrateConsentDataIfNeeded(
                        mSharedPrefs, mDatastore, mAdServicesStorageManager, mAppConsentDao);
        expect.that(result).isTrue();

        verify(mDatastore).put(eq(ConsentConstants.GA_UX_NOTIFICATION_DISPLAYED_ONCE), eq(true));
        verify(mAdServicesStorageManager).recordGaUxNotificationDisplayed(true);
        verify(mAppConsentDao).setConsentForApp(eq(PACKAGE_NAME1), eq(false));
        verify(mAppConsentDao).setConsentForApp(eq(PACKAGE_NAME2), eq(false));
        verify(mAppConsentDao).setConsentForApp(eq(PACKAGE_NAME3), eq(true));
        verify(mAdServicesStorageManager).setConsentForApp(eq(PACKAGE_NAME1), eq(false));
        verify(mAdServicesStorageManager).setConsentForApp(eq(PACKAGE_NAME2), eq(false));
        verify(mAdServicesStorageManager).setConsentForApp(eq(PACKAGE_NAME3), eq(true));
        verify(mDatastore)
                .put(eq(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT.name()), eq(true));
        verify(mDatastore, atLeast(4)).put(any(), eq(false));
        verify(mEditor)
                .putBoolean(eq(BlockedTopicsManager.SHARED_PREFS_KEY_HAS_MIGRATED), eq(true));
        verify(mEditor).commit();
        // Verify blocked topics migration.
        ArgumentCaptor<List<TopicParcel>> argument = ArgumentCaptor.forClass(List.class);
        verify(mAdServicesStorageManager).recordBlockedTopic(argument.capture());
        expect.that(argument.getValue().size()).isEqualTo(blockedTopics.size());
        for (TopicParcel topicParcel : argument.getValue()) {
            expect.that(List.of(TOPIC1.getTopic(), TOPIC2.getTopic(), TOPIC3.getTopic()))
                    .contains(topicParcel.getTopicId());
            List taxonomies =
                    List.of(
                            TOPIC1.getTaxonomyVersion(),
                            TOPIC2.getTaxonomyVersion(),
                            TOPIC3.getTaxonomyVersion());
            expect.that(taxonomies).contains(topicParcel.getTaxonomyVersion());
            List models =
                    List.of(
                            TOPIC1.getModelVersion(),
                            TOPIC2.getModelVersion(),
                            TOPIC3.getModelVersion());
            expect.that(models).contains(topicParcel.getModelVersion());
        }
    }

    @Test
    public void testMigrateConsentData_FromExtServices() throws Exception {
        Context spyContext = Mockito.spy(appContext.get());
        doReturn("com." + AdServicesCommon.ADEXTSERVICES_PACKAGE_NAME_SUFFIX)
                .when(spyContext)
                .getPackageName();
        appContext.set(spyContext);
        initConsentDataForMigration();
        boolean result =
                mAppSearchConsentStorageManager.migrateConsentDataIfNeeded(
                        mSharedPrefs, mDatastore, mAdServicesStorageManager, mAppConsentDao);
        assertWithMessage("result from migrateConsentDataIfNeeded on extServices")
                .that(result)
                .isFalse();
    }

    private void initConsentDataForMigration() {
        ExtendedMockito.doReturn(true).when(() -> SdkLevel.isAtLeastT());
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        when(mSharedPrefs.getBoolean(any(), eq(true))).thenReturn(true);
        when(mAdServicesStorageManager.wasNotificationDisplayed()).thenReturn(false);
        when(mAdServicesStorageManager.wasGaUxNotificationDisplayed()).thenReturn(false);
        when(mAppSearchConsentWorker.wasNotificationDisplayed()).thenReturn(false);
        when(mDatastore.get(any())).thenReturn(false);
        when(mAppSearchConsentWorker.wasGaUxNotificationDisplayed()).thenReturn(true);
        when(mSharedPrefs.edit()).thenReturn(mEditor);
    }

    @Test
    public void setAdIdEnabledTest_trueBit() {
        setAdIdEnabledTest(true);
    }

    @Test
    public void setAdIdEnabledTest_falseBit() {
        setAdIdEnabledTest(false);
    }

    private void setAdIdEnabledTest(boolean isAdIdEnabled) {
        mAppSearchConsentStorageManager.setAdIdEnabled(isAdIdEnabled);
        verify(mAppSearchConsentWorker).setAdIdEnabled(isAdIdEnabled);
    }

    @Test
    public void isAdIdEnabledTest_defaultFalseBit() {
        when(mAppSearchConsentWorker.isAdIdEnabled()).thenReturn(false);
        expect.that(mAppSearchConsentStorageManager.isAdIdEnabled()).isFalse();
        verify(mAppSearchConsentWorker).isAdIdEnabled();
    }

    @Test
    public void setU18AccountTest_trueBit() {
        setU18AccountTest(true);
    }

    @Test
    public void setU18AccountTest_falseBit() {
        setU18AccountTest(false);
    }

    private void setU18AccountTest(boolean isU18Account) {
        mAppSearchConsentStorageManager.setU18Account(isU18Account);
        verify(mAppSearchConsentWorker).setU18Account(isU18Account);
    }

    @Test
    public void isU18AccountTest_defaultFalseBit() {
        when(mAppSearchConsentWorker.isU18Account()).thenReturn(false);
        expect.that(mAppSearchConsentStorageManager.isU18Account()).isFalse();
        verify(mAppSearchConsentWorker).isU18Account();
    }

    @Test
    public void setEntryPointEnabledTest_trueBit() {
        setEntryPointEnabledTest(true);
    }

    @Test
    public void setEntryPointEnabledTest_falseBit() {
        setEntryPointEnabledTest(false);
    }

    private void setEntryPointEnabledTest(boolean isEntryPointEnabled) {
        mAppSearchConsentStorageManager.setEntryPointEnabled(isEntryPointEnabled);
        verify(mAppSearchConsentWorker).setEntryPointEnabled(isEntryPointEnabled);
    }

    @Test
    public void isEntryPointEnabledTest_defaultFalseBit() {
        when(mAppSearchConsentWorker.isEntryPointEnabled()).thenReturn(false);
        expect.that(mAppSearchConsentStorageManager.isEntryPointEnabled()).isFalse();
        verify(mAppSearchConsentWorker).isEntryPointEnabled();
    }

    @Test
    public void setAdultAccountTest_trueBit() {
        setAdultAccountTest(true);
    }

    @Test
    public void setAdultAccountTest_falseBit() {
        setAdultAccountTest(false);
    }

    private void setAdultAccountTest(boolean isAdultAccount) {
        mAppSearchConsentStorageManager.setAdultAccount(isAdultAccount);
        verify(mAppSearchConsentWorker).setAdultAccount(isAdultAccount);
    }

    @Test
    public void isAdultAccountTest_defaultFalseBit() {
        when(mAppSearchConsentWorker.isAdultAccount()).thenReturn(false);
        expect.that(mAppSearchConsentStorageManager.isAdultAccount()).isFalse();
        verify(mAppSearchConsentWorker).isAdultAccount();
    }

    @Test
    public void setU18NotificationDisplayedTest_trueBit() {
        setU18NotificationDisplayedTest(true);
    }

    @Test
    public void setU18NotificationDisplayedTest_falseBit() {
        setU18NotificationDisplayedTest(false);
    }

    private void setU18NotificationDisplayedTest(boolean wasU18NotificationDisplayed) {
        mAppSearchConsentStorageManager.setU18NotificationDisplayed(wasU18NotificationDisplayed);
        verify(mAppSearchConsentWorker).setU18NotificationDisplayed(wasU18NotificationDisplayed);
    }

    @Test
    public void wasU18NotificationDisplayedTest_defaultFalseBit() {
        when(mAppSearchConsentWorker.wasU18NotificationDisplayed()).thenReturn(false);
        expect.that(mAppSearchConsentStorageManager.wasU18NotificationDisplayed()).isFalse();
        verify(mAppSearchConsentWorker).wasU18NotificationDisplayed();
    }

    @Test
    public void getUxTest() {
        mAppSearchConsentStorageManager.getUx();
        verify(mAppSearchConsentWorker).getUx();
    }

    @Test
    public void setUxTest() {
        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            mAppSearchConsentStorageManager.setUx(ux);
            verify(mAppSearchConsentWorker).setUx(ux);
        }
    }

    @Test
    public void getEnrollmentChannelTest() {
        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            mAppSearchConsentStorageManager.getEnrollmentChannel(ux);
            verify(mAppSearchConsentWorker).getEnrollmentChannel(ux);
        }
    }

    @Test
    public void setEnrollmentChannelTest() {
        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            for (PrivacySandboxEnrollmentChannelCollection channel :
                    ux.getEnrollmentChannelCollection()) {
                mAppSearchConsentStorageManager.setEnrollmentChannel(ux, channel);
                verify(mAppSearchConsentWorker).setEnrollmentChannel(ux, channel);
            }
        }
    }

    @Test
    public void uxConformanceTest() {
        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            doReturn(ux).when(mAppSearchConsentWorker).getUx();
            mAppSearchConsentStorageManager.setUx(ux);
            expect.that(mAppSearchConsentStorageManager.getUx()).isEqualTo(ux);
        }
    }

    @Test
    public void enrollmentChannelConformanceTest() {
        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            for (PrivacySandboxEnrollmentChannelCollection channel :
                    ux.getEnrollmentChannelCollection()) {
                doReturn(channel).when(mAppSearchConsentWorker).getEnrollmentChannel(ux);
                mAppSearchConsentStorageManager.setEnrollmentChannel(ux, channel);
                expect.that(mAppSearchConsentStorageManager.getEnrollmentChannel(ux))
                        .isEqualTo(channel);
            }
        }
    }
}
