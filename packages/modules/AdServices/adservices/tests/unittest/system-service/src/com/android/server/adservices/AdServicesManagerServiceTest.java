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

package com.android.server.adservices;

import static com.android.adservices.shared.testing.common.DumpHelper.dump;
import static com.android.adservices.service.CommonFlagsConstants.KEY_ADSERVICES_SHELL_COMMAND_ENABLED;
import static com.android.server.adservices.PhFlags.KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.app.adservices.AdServicesManager;
import android.app.adservices.consent.ConsentParcel;
import android.app.adservices.topics.TopicParcel;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.VersionedPackage;
import android.content.rollback.RollbackManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.util.ArrayMap;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.modules.utils.testing.TestableDeviceConfig;
import com.android.server.adservices.consent.AppConsentManagerFixture;
import com.android.server.adservices.data.topics.TopicsDao;
import com.android.server.adservices.data.topics.TopicsDbHelper;
import com.android.server.adservices.data.topics.TopicsDbTestUtil;
import com.android.server.adservices.data.topics.TopicsTables;
import com.android.server.adservices.feature.PrivacySandboxEnrollmentChannelCollection;
import com.android.server.adservices.feature.PrivacySandboxFeatureType;
import com.android.server.adservices.feature.PrivacySandboxUxCollection;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Tests for {@link AdServicesManagerService} */
public class AdServicesManagerServiceTest {
    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule mDeviceConfigRule =
            new TestableDeviceConfig.TestableDeviceConfigRule();

    private AdServicesManagerService mService;
    private UserInstanceManager mUserInstanceManager;
    private Context mSpyContext;
    @Mock private PackageManager mMockPackageManager;
    @Mock private RollbackManager mMockRollbackManager;

    private static final String PPAPI_PACKAGE_NAME = "com.google.android.adservices.api";
    private static final String ADSERVICES_APEX_PACKAGE_NAME = "com.google.android.adservices";
    private static final String PACKAGE_NAME = "com.package.example";
    private static final String PACKAGE_CHANGED_BROADCAST =
            "com.android.adservices.PACKAGE_CHANGED";
    private static final String PACKAGE_FULLY_REMOVED = "package_fully_removed";
    private static final String PACKAGE_ADDED = "package_added";
    private static final String PACKAGE_DATA_CLEARED = "package_data_cleared";
    private static final long TAXONOMY_VERSION = 1L;
    private static final long MODEL_VERSION = 1L;
    private static final int PACKAGE_UID = 12345;
    private static final Context PPAPI_CONTEXT = ApplicationProvider.getApplicationContext();
    private static final String BASE_DIR = PPAPI_CONTEXT.getFilesDir().getAbsolutePath();
    private final TopicsDbHelper mDBHelper = TopicsDbTestUtil.getDbHelperForTest();
    private static final int TEST_ROLLED_BACK_FROM_MODULE_VERSION = 339990000;
    private static final int TEST_ROLLED_BACK_TO_MODULE_VERSION = 330000000;
    private static final int ROLLBACK_ID = 1768705420;
    private static final String USER_INSTANCE_MANAGER_DUMP = "D'OHump!";

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mSpyContext = Mockito.spy(context);

        TopicsDao topicsDao = new TopicsDao(mDBHelper);
        mUserInstanceManager =
                new UserInstanceManager(
                        topicsDao,
                        /* adServicesBaseDir= */ context.getFilesDir().getAbsolutePath()) {
                    @Override
                    public void dump(PrintWriter writer, String[] args) {
                        writer.println(USER_INSTANCE_MANAGER_DUMP);
                    }
                };

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.INTERACT_ACROSS_USERS_FULL);

        doReturn(mMockPackageManager).when(mSpyContext).getPackageManager();
        doReturn(mMockRollbackManager).when(mSpyContext).getSystemService(RollbackManager.class);
    }

    @After
    public void tearDown() {
        // We need tear down this instance since it can have underlying persisted Data Store.
        mUserInstanceManager.tearDownForTesting();

        // Clear BlockedTopics table in the database.
        TopicsDbTestUtil.deleteTable(TopicsTables.BlockedTopicsContract.TABLE);
    }

    @Test
    public void testAdServicesSystemService_enabled_then_disabled() throws Exception {
        // First enable the flag.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED,
                Boolean.toString(Boolean.TRUE),
                /* makeDefault */ false);

        // This will trigger the registration of the Receiver.
        mService = new AdServicesManagerService(mSpyContext, mUserInstanceManager);

        ArgumentCaptor<BroadcastReceiver> argumentReceiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        ArgumentCaptor<IntentFilter> argumentIntentFilter =
                ArgumentCaptor.forClass(IntentFilter.class);
        ArgumentCaptor<String> argumentPermission = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Handler> argumentHandler = ArgumentCaptor.forClass(Handler.class);

        // Calling the second time will not register again.
        mService.registerReceivers();

        // We have 2 receivers which are PackageChangeReceiver and UserActionReceiver.
        int numReceivers = 2;
        // The flag is enabled so we call registerReceiverForAllUsers
        Mockito.verify(mSpyContext, Mockito.times(numReceivers))
                .registerReceiverForAllUsers(
                        argumentReceiver.capture(),
                        argumentIntentFilter.capture(),
                        argumentPermission.capture(),
                        argumentHandler.capture());

        List<BroadcastReceiver> receiverList = argumentReceiver.getAllValues();
        assertThat(receiverList).hasSize(numReceivers);

        // Validate PackageChangeReceiver
        List<IntentFilter> intentFilterList = argumentIntentFilter.getAllValues();
        IntentFilter packageIntentFilter = intentFilterList.get(0);
        assertThat(packageIntentFilter.hasAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)).isTrue();
        assertThat(packageIntentFilter.hasAction(Intent.ACTION_PACKAGE_DATA_CLEARED)).isTrue();
        assertThat(packageIntentFilter.hasAction(Intent.ACTION_PACKAGE_ADDED)).isTrue();
        assertThat(packageIntentFilter.countActions()).isEqualTo(3);
        assertThat(packageIntentFilter.getDataScheme(0)).isEqualTo("package");

        assertThat(argumentPermission.getAllValues().get(0)).isNull();
        assertThat(argumentHandler.getAllValues().get(0)).isNotNull();

        // Validate UserActionReceiver
        IntentFilter userActionIntentFilter = intentFilterList.get(1);
        assertThat(userActionIntentFilter.hasAction(Intent.ACTION_USER_REMOVED)).isTrue();
        assertThat(userActionIntentFilter.countActions()).isEqualTo(1);
        assertThat(argumentPermission.getAllValues().get(1)).isNull();
        assertThat(argumentHandler.getAllValues().get(1)).isNotNull();

        // Now disable the flag.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED,
                Boolean.toString(Boolean.FALSE),
                /* makeDefault */ false);

        // When flag value is changed above, then TestableDeviceConfig invokes the DeviceConfig
        // .OnPropertiesChangedListener. The listener is invoked on the separate thread. So, we
        // need to add a wait time to ensure the listener gets executed. If listener gets
        // executed after the test is finished, we hit READ_DEVICE_CONFIG exception.
        Thread.sleep(500);

        // Calling when the flag is disabled will unregister the Receiver!
        mService.registerReceivers();
        Mockito.verify(mSpyContext, Mockito.times(numReceivers))
                .unregisterReceiver(argumentReceiver.capture());

        // The unregistered is called on the same receiver when registered above.
        assertThat(argumentReceiver.getAllValues().get(0)).isSameInstanceAs(receiverList.get(0));
        assertThat(argumentReceiver.getAllValues().get(1)).isSameInstanceAs(receiverList.get(1));
    }

    @Test
    public void testAdServicesSystemService_disabled() {
        // Disable the flag.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED,
                Boolean.toString(Boolean.FALSE),
                /* makeDefault */ false);

        mService = new AdServicesManagerService(mSpyContext, mUserInstanceManager);

        // The flag is disabled so there is no registerReceiverForAllUsers
        Mockito.verify(mSpyContext, Mockito.times(0))
                .registerReceiverForAllUsers(
                        any(BroadcastReceiver.class),
                        any(IntentFilter.class),
                        any(String.class),
                        any(Handler.class));
    }

    @Test
    public void testAdServicesSystemService_enabled_setAdServicesApexVersion() {
        // First enable the flag.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED,
                Boolean.toString(Boolean.TRUE),
                /* makeDefault */ false);

        setupMockInstalledPackages();

        // This will trigger the lookup of the AdServices version.
        mService = new AdServicesManagerService(mSpyContext, mUserInstanceManager);

        ArgumentCaptor<PackageManager.PackageInfoFlags> argumentPackageInfoFlags =
                ArgumentCaptor.forClass(PackageManager.PackageInfoFlags.class);

        Mockito.verify(mSpyContext, Mockito.times(1)).getPackageManager();

        Mockito.verify(mMockPackageManager, Mockito.times(1))
                .getInstalledPackages(argumentPackageInfoFlags.capture());

        assertThat(argumentPackageInfoFlags.getAllValues().get(0).getValue())
                .isEqualTo(PackageManager.MATCH_APEX);

        assertThat(mService.getAdServicesApexVersion())
                .isEqualTo(TEST_ROLLED_BACK_FROM_MODULE_VERSION);
    }

    @Test
    public void testAdServicesSystemService_disabled_setAdServicesApexVersion() {
        // Disable the flag.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED,
                Boolean.toString(Boolean.FALSE),
                /* makeDefault */ false);

        mService = new AdServicesManagerService(mSpyContext, mUserInstanceManager);

        // The flag is disabled so there is no call to the packageManager
        Mockito.verify(mSpyContext, Mockito.times(0)).getPackageManager();
    }

    @Test
    public void testAdServicesShellCommand_disabled() throws Exception {
        setShellCommandEnabled(false);
        String expectedOutput = handleShellCommand(new Binder());

        mService = new AdServicesManagerService(mSpyContext, mUserInstanceManager);

        String result = handleShellCommand(mService);

        assertWithMessage("shell command output").that(result).contains(expectedOutput);
    }

    @Test
    public void testAdServicesShellCommand_enabled() throws Exception {
        setShellCommandEnabled(true);
        mService = new AdServicesManagerService(mSpyContext, mUserInstanceManager);
        String expectedOutput =
                String.format(AdServicesShellCommand.WRONG_UID_TEMPLATE, Binder.getCallingUid());

        String result = handleShellCommand(mService);

        assertWithMessage("shell command output").that(result).contains(expectedOutput);
    }

    @Test
    public void testSendBroadcastForPackageFullyRemoved() {
        mService = new AdServicesManagerService(mSpyContext, mUserInstanceManager);

        Intent i = new Intent(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        i.setData(Uri.parse("package:" + PACKAGE_NAME));
        i.putExtra(Intent.EXTRA_UID, PACKAGE_UID);

        ArgumentCaptor<Intent> argumentIntent = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<UserHandle> argumentUser = ArgumentCaptor.forClass(UserHandle.class);

        setupMockResolveInfo();
        Mockito.doNothing().when(mSpyContext).sendBroadcastAsUser(Mockito.any(), Mockito.any());

        mService.onPackageChange(i, mSpyContext.getUser());

        Mockito.verify(mSpyContext, Mockito.times(1))
                .sendBroadcastAsUser(argumentIntent.capture(), argumentUser.capture());

        assertThat(argumentIntent.getValue().getAction()).isEqualTo(PACKAGE_CHANGED_BROADCAST);
        assertThat(argumentIntent.getValue().getData()).isEqualTo(i.getData());
        assertThat(argumentIntent.getValue().getStringExtra("action"))
                .isEqualTo(PACKAGE_FULLY_REMOVED);
        assertThat(argumentIntent.getValue().getIntExtra(Intent.EXTRA_UID, -1))
                .isEqualTo(PACKAGE_UID);
        assertThat(argumentUser.getValue()).isEqualTo(mSpyContext.getUser());
    }

    @Test
    public void testOnUserRemoved() throws IOException {
        mService = new AdServicesManagerService(mSpyContext, mUserInstanceManager);
        int userId = 1;
        String consentDataStoreDir = BASE_DIR + "/" + userId;
        Path packageDir = Paths.get(consentDataStoreDir);
        Intent intent = new Intent(Intent.ACTION_USER_REMOVED);
        intent.putExtra(Intent.EXTRA_USER, UserHandle.of(userId));
        mUserInstanceManager.getOrCreateUserConsentManagerInstance(userId);
        assertThat(Files.exists(packageDir)).isTrue();
        assertThat(mUserInstanceManager.getUserConsentManagerInstance(userId)).isNotNull();

        mService.onUserRemoved(intent);

        assertThat(Files.exists(packageDir)).isFalse();
        assertThat(mUserInstanceManager.getUserConsentManagerInstance(userId)).isNull();
    }

    @Test
    public void testOnUserRemoved_userIdNotPresentInIntent() throws IOException {
        mService = new AdServicesManagerService(mSpyContext, mUserInstanceManager);
        Intent intent = new Intent(Intent.ACTION_USER_REMOVED);
        // userId 1 is not present in the intent.
        int userId = 1;
        mUserInstanceManager.getOrCreateUserConsentManagerInstance(userId);
        assertThat(mUserInstanceManager.getUserConsentManagerInstance(userId)).isNotNull();

        mService.onUserRemoved(intent);

        assertThat(mUserInstanceManager.getUserConsentManagerInstance(userId)).isNotNull();
    }

    @Test
    public void testOnUserRemoved_removeNonexistentUserId() throws IOException {
        mService = new AdServicesManagerService(mSpyContext, mUserInstanceManager);
        Intent intent = new Intent(Intent.ACTION_USER_REMOVED);
        // userId 1 does not have consent directory.
        int userId = 1;
        intent.putExtra(Intent.EXTRA_USER, UserHandle.of(2));

        mService.onUserRemoved(intent);

        assertThat(mUserInstanceManager.getUserConsentManagerInstance(userId)).isNull();
    }

    @Test
    public void testClearAllBlockedTopics() {
        mService = spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));
        disableEnforceAdServicesManagerPermission(mService);

        final int topicId = 1;

        TopicParcel topicParcel =
                new TopicParcel.Builder()
                        .setTopicId(topicId)
                        .setTaxonomyVersion(TAXONOMY_VERSION)
                        .setModelVersion(MODEL_VERSION)
                        .build();
        mService.recordBlockedTopic(List.of(topicParcel));

        //  Verify the topic is recorded.
        List<TopicParcel> resultTopicParcels = mService.retrieveAllBlockedTopics();
        assertThat(resultTopicParcels).hasSize(1);
        assertThat(resultTopicParcels.get(0)).isEqualTo(topicParcel);

        // Verify the topic is  removed
        mService.clearAllBlockedTopics();
        assertThat(mService.retrieveAllBlockedTopics()).isEmpty();
    }

    @Test
    public void testSendBroadcastForPackageAdded() {
        mService = new AdServicesManagerService(mSpyContext, mUserInstanceManager);

        Intent i = new Intent(Intent.ACTION_PACKAGE_ADDED);
        i.setData(Uri.parse("package:" + PACKAGE_NAME));
        i.putExtra(Intent.EXTRA_UID, PACKAGE_UID);
        i.putExtra(Intent.EXTRA_REPLACING, false);

        ArgumentCaptor<Intent> argumentIntent = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<UserHandle> argumentUser = ArgumentCaptor.forClass(UserHandle.class);

        setupMockResolveInfo();
        Mockito.doNothing().when(mSpyContext).sendBroadcastAsUser(Mockito.any(), Mockito.any());

        mService.onPackageChange(i, mSpyContext.getUser());

        Mockito.verify(mSpyContext, Mockito.times(1))
                .sendBroadcastAsUser(argumentIntent.capture(), argumentUser.capture());

        assertThat(argumentIntent.getValue().getAction()).isEqualTo(PACKAGE_CHANGED_BROADCAST);
        assertThat(argumentIntent.getValue().getData()).isEqualTo(i.getData());
        assertThat(argumentIntent.getValue().getStringExtra("action")).isEqualTo(PACKAGE_ADDED);
        assertThat(argumentIntent.getValue().getIntExtra(Intent.EXTRA_UID, -1))
                .isEqualTo(PACKAGE_UID);
        assertThat(argumentUser.getValue()).isEqualTo(mSpyContext.getUser());
    }

    @Test
    public void testSendBroadcastForPackageDataCleared() {
        mService = new AdServicesManagerService(mSpyContext, mUserInstanceManager);

        Intent i = new Intent(Intent.ACTION_PACKAGE_DATA_CLEARED);
        i.setData(Uri.parse("package:" + PACKAGE_NAME));
        i.putExtra(Intent.EXTRA_UID, PACKAGE_UID);

        ArgumentCaptor<Intent> argumentIntent = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<UserHandle> argumentUser = ArgumentCaptor.forClass(UserHandle.class);

        setupMockResolveInfo();
        Mockito.doNothing().when(mSpyContext).sendBroadcastAsUser(Mockito.any(), Mockito.any());

        mService.onPackageChange(i, mSpyContext.getUser());

        Mockito.verify(mSpyContext, Mockito.times(1))
                .sendBroadcastAsUser(argumentIntent.capture(), argumentUser.capture());

        assertThat(argumentIntent.getValue().getAction()).isEqualTo(PACKAGE_CHANGED_BROADCAST);
        assertThat(argumentIntent.getValue().getData()).isEqualTo(i.getData());
        assertThat(argumentIntent.getValue().getStringExtra("action"))
                .isEqualTo(PACKAGE_DATA_CLEARED);
        assertThat(argumentIntent.getValue().getIntExtra(Intent.EXTRA_UID, -1))
                .isEqualTo(PACKAGE_UID);
        assertThat(argumentUser.getValue()).isEqualTo(mSpyContext.getUser());
    }

    @Test
    public void testGetConsent_unSet() throws IOException {
        AdServicesManagerService service =
                spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service);

        // Newly initialized ConsentManager has consent = false.
        assertThat(service.getConsent(ConsentParcel.ALL_API).isIsGiven()).isFalse();
        assertThat(service.getConsent(ConsentParcel.TOPICS).isIsGiven()).isFalse();
        assertThat(service.getConsent(ConsentParcel.FLEDGE).isIsGiven()).isFalse();
        assertThat(service.getConsent(ConsentParcel.MEASUREMENT).isIsGiven()).isFalse();
    }

    @Test
    public void testGetAndSetConsent_null() throws IOException {
        AdServicesManagerService service =
                spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service);

        service.setConsent(
                new ConsentParcel.Builder()
                        .setConsentApiType(ConsentParcel.ALL_API)
                        .setIsGiven(null)
                        .build());
        // null means the consent is not given (false).
        assertThat(service.getConsent(ConsentParcel.ALL_API).isIsGiven()).isFalse();

        service.setConsent(
                new ConsentParcel.Builder()
                        .setConsentApiType(ConsentParcel.TOPICS)
                        .setIsGiven(null)
                        .build());
        // null means the consent is not given (false).
        assertThat(service.getConsent(ConsentParcel.TOPICS).isIsGiven()).isFalse();

        service.setConsent(
                new ConsentParcel.Builder()
                        .setConsentApiType(ConsentParcel.FLEDGE)
                        .setIsGiven(null)
                        .build());
        // null means the consent is not given (false).
        assertThat(service.getConsent(ConsentParcel.FLEDGE).isIsGiven()).isFalse();

        service.setConsent(
                new ConsentParcel.Builder()
                        .setConsentApiType(ConsentParcel.MEASUREMENT)
                        .setIsGiven(null)
                        .build());
        // null means the consent is not given (false).
        assertThat(service.getConsent(ConsentParcel.MEASUREMENT).isIsGiven()).isFalse();
    }

    @Test
    public void testGetAndSetConsent_nonNull() {
        AdServicesManagerService service =
                spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service);

        service.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.ALL_API));
        assertThat(service.getConsent(ConsentParcel.ALL_API).isIsGiven()).isFalse();

        service.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API));
        assertThat(service.getConsent(ConsentParcel.ALL_API).isIsGiven()).isTrue();

        service.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.TOPICS));
        assertThat(service.getConsent(ConsentParcel.TOPICS).isIsGiven()).isFalse();

        service.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.TOPICS));
        assertThat(service.getConsent(ConsentParcel.TOPICS).isIsGiven()).isTrue();

        service.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.FLEDGE));
        assertThat(service.getConsent(ConsentParcel.FLEDGE).isIsGiven()).isFalse();

        service.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE));
        assertThat(service.getConsent(ConsentParcel.FLEDGE).isIsGiven()).isTrue();

        service.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.MEASUREMENT));
        assertThat(service.getConsent(ConsentParcel.MEASUREMENT).isIsGiven()).isFalse();

        service.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.MEASUREMENT));
        assertThat(service.getConsent(ConsentParcel.MEASUREMENT).isIsGiven()).isTrue();

        // Verify that all the setConsent calls were persisted by creating a new instance of
        // AdServicesManagerService, and it has the same value as the above instance.
        // Note: In general, AdServicesManagerService instance is a singleton obtained via
        // context.getSystemService(). However, when the system server restarts, there will be
        // another singleton instance of AdServicesManagerService. This test here verifies that
        // the Consents are persisted correctly across restarts.
        AdServicesManagerService service2 =
                spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service2);

        assertThat(service2.getConsent(ConsentParcel.ALL_API).isIsGiven()).isTrue();
        assertThat(service2.getConsent(ConsentParcel.TOPICS).isIsGiven()).isTrue();
        assertThat(service2.getConsent(ConsentParcel.FLEDGE).isIsGiven()).isTrue();
        assertThat(service2.getConsent(ConsentParcel.MEASUREMENT).isIsGiven()).isTrue();
    }

    @Test
    public void testRecordNotificationDisplayed() throws IOException {
        AdServicesManagerService service =
                spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service);

        // First, the notification displayed is false.
        assertThat(service.wasNotificationDisplayed()).isFalse();
        service.recordNotificationDisplayed(true);
        assertThat(service.wasNotificationDisplayed()).isTrue();
    }

    @Test
    public void testEnforceAdServicesManagerPermission() {
        AdServicesManagerService service =
                spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));

        // Throw due to non-IPC call
        assertThrows(SecurityException.class, () -> service.getConsent(ConsentParcel.ALL_API));
    }

    @Test
    public void testRecordGaUxNotificationDisplayed() throws IOException {
        AdServicesManagerService service =
                spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service);

        // First, the notification displayed is false.
        assertThat(service.wasGaUxNotificationDisplayed()).isFalse();
        service.recordGaUxNotificationDisplayed(true);
        assertThat(service.wasGaUxNotificationDisplayed()).isTrue();
    }

    @Test
    public void testRecordPasNotificationDisplayed() {
        AdServicesManagerService service =
                spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service);

        // First, the notification displayed is false.
        assertThat(service.wasPasNotificationDisplayed()).isFalse();
        service.recordPasNotificationDisplayed(true);
        assertThat(service.wasPasNotificationDisplayed()).isTrue();
    }

    @Test
    public void recordUserManualInteractionWithConsent() throws IOException {
        AdServicesManagerService service =
                spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service);

        // First, the topic consent page displayed is false.
        assertThat(service.getUserManualInteractionWithConsent()).isEqualTo(0);
        service.recordUserManualInteractionWithConsent(1);
        assertThat(service.getUserManualInteractionWithConsent()).isEqualTo(1);
    }

    @Test
    public void testSetAppConsent() {
        mService = spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));
        disableEnforceAdServicesManagerPermission(mService);

        mService.setConsentForApp(
                AppConsentManagerFixture.APP10_PACKAGE_NAME,
                AppConsentManagerFixture.APP10_UID,
                false);
        assertFalse(
                mService.isConsentRevokedForApp(
                        AppConsentManagerFixture.APP10_PACKAGE_NAME,
                        AppConsentManagerFixture.APP10_UID));

        mService.setConsentForAppIfNew(
                AppConsentManagerFixture.APP20_PACKAGE_NAME,
                AppConsentManagerFixture.APP20_UID,
                false);
        assertFalse(
                mService.isConsentRevokedForApp(
                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                        AppConsentManagerFixture.APP20_UID));

        mService.setConsentForApp(
                AppConsentManagerFixture.APP20_PACKAGE_NAME,
                AppConsentManagerFixture.APP20_UID,
                true);
        assertTrue(
                mService.isConsentRevokedForApp(
                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                        AppConsentManagerFixture.APP20_UID));
        assertTrue(
                mService.setConsentForAppIfNew(
                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                        AppConsentManagerFixture.APP20_UID,
                        false));

        assertFalse(
                mService.setConsentForAppIfNew(
                        AppConsentManagerFixture.APP30_PACKAGE_NAME,
                        AppConsentManagerFixture.APP30_UID,
                        false));

        assertThat(
                        mService.getKnownAppsWithConsent(
                                List.of(
                                        AppConsentManagerFixture.APP10_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP30_PACKAGE_NAME)))
                .hasSize(2);
        assertThat(
                        mService.getAppsWithRevokedConsent(
                                List.of(
                                        AppConsentManagerFixture.APP10_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP30_PACKAGE_NAME)))
                .hasSize(1);
    }

    @Test
    public void testClearAppConsent() {
        mService = spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));
        disableEnforceAdServicesManagerPermission(mService);

        mService.setConsentForApp(
                AppConsentManagerFixture.APP10_PACKAGE_NAME,
                AppConsentManagerFixture.APP10_UID,
                false);
        mService.setConsentForApp(
                AppConsentManagerFixture.APP20_PACKAGE_NAME,
                AppConsentManagerFixture.APP20_UID,
                false);
        mService.setConsentForApp(
                AppConsentManagerFixture.APP30_PACKAGE_NAME,
                AppConsentManagerFixture.APP30_UID,
                true);
        assertThat(
                        mService.getKnownAppsWithConsent(
                                List.of(
                                        AppConsentManagerFixture.APP10_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP30_PACKAGE_NAME)))
                .hasSize(2);
        assertThat(
                        mService.getAppsWithRevokedConsent(
                                List.of(
                                        AppConsentManagerFixture.APP10_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP30_PACKAGE_NAME)))
                .hasSize(1);

        mService.clearConsentForUninstalledApp(
                AppConsentManagerFixture.APP10_PACKAGE_NAME, AppConsentManagerFixture.APP10_UID);
        assertThat(
                        mService.getKnownAppsWithConsent(
                                List.of(
                                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP30_PACKAGE_NAME)))
                .hasSize(1);
        assertThat(
                        mService.getAppsWithRevokedConsent(
                                List.of(
                                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP30_PACKAGE_NAME)))
                .hasSize(1);

        mService.clearKnownAppsWithConsent();
        assertThat(
                        mService.getKnownAppsWithConsent(
                                List.of(
                                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP30_PACKAGE_NAME)))
                .hasSize(0);
        assertThat(
                        mService.getAppsWithRevokedConsent(
                                List.of(
                                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP30_PACKAGE_NAME)))
                .hasSize(1);

        mService.setConsentForApp(
                AppConsentManagerFixture.APP20_PACKAGE_NAME,
                AppConsentManagerFixture.APP20_UID,
                false);
        assertThat(
                        mService.getKnownAppsWithConsent(
                                List.of(
                                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP30_PACKAGE_NAME)))
                .hasSize(1);
        assertThat(
                        mService.getAppsWithRevokedConsent(
                                List.of(
                                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP30_PACKAGE_NAME)))
                .hasSize(1);

        mService.clearAllAppConsentData();
        assertThat(
                        mService.getKnownAppsWithConsent(
                                List.of(
                                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP30_PACKAGE_NAME)))
                .hasSize(0);
        assertThat(
                        mService.getAppsWithRevokedConsent(
                                List.of(
                                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                                        AppConsentManagerFixture.APP30_PACKAGE_NAME)))
                .hasSize(0);
    }

    @Test
    public void testRecordAndRetrieveBlockedTopic() {
        mService = spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));
        disableEnforceAdServicesManagerPermission(mService);

        final int topicId = 1;

        TopicParcel topicParcel =
                new TopicParcel.Builder()
                        .setTopicId(topicId)
                        .setTaxonomyVersion(TAXONOMY_VERSION)
                        .setModelVersion(MODEL_VERSION)
                        .build();
        mService.recordBlockedTopic(List.of(topicParcel));

        //  Verify the topic is recorded.
        List<TopicParcel> resultTopicParcels = mService.retrieveAllBlockedTopics();
        assertThat(resultTopicParcels).hasSize(1);
        assertThat(resultTopicParcels.get(0)).isEqualTo(topicParcel);
    }

    @Test
    public void testRecordAndRemoveBlockedTopic() {
        mService = spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));
        disableEnforceAdServicesManagerPermission(mService);

        final int topicId = 1;

        TopicParcel topicParcel =
                new TopicParcel.Builder()
                        .setTopicId(topicId)
                        .setTaxonomyVersion(TAXONOMY_VERSION)
                        .setModelVersion(MODEL_VERSION)
                        .build();
        mService.recordBlockedTopic(List.of(topicParcel));

        //  Verify the topic is recorded.
        List<TopicParcel> resultTopicParcels = mService.retrieveAllBlockedTopics();
        assertThat(resultTopicParcels).hasSize(1);
        assertThat(resultTopicParcels.get(0)).isEqualTo(topicParcel);

        // Verify the topic is  removed
        mService.removeBlockedTopic(topicParcel);
        assertThat(mService.retrieveAllBlockedTopics()).isEmpty();
    }

    @Test
    public void testRecordMeasurementDeletionOccurred() throws IOException {
        AdServicesManagerService service =
                spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service);

        // Mock the setting of the AdServices module version in the system server.
        setAdServicesModuleVersion(service, TEST_ROLLED_BACK_FROM_MODULE_VERSION);

        // First, the has measurement deletion occurred is false.
        assertThat(service.hasAdServicesDeletionOccurred(AdServicesManager.MEASUREMENT_DELETION))
                .isFalse();
        service.recordAdServicesDeletionOccurred(AdServicesManager.MEASUREMENT_DELETION);
        assertThat(service.hasAdServicesDeletionOccurred(AdServicesManager.MEASUREMENT_DELETION))
                .isTrue();
    }

    @Test
    public void testNeedsToHandleRollbackReconciliation_noRollback_returnsFalse() {
        AdServicesManagerService service =
                spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));

        disableEnforceAdServicesManagerPermission(service);

        // Set the rolled back from package to null, indicating there was not a rollback.
        doReturn(Collections.emptyMap()).when(service).getAdServicesPackagesRolledBackFrom();

        doReturn(true).when(service).hasAdServicesDeletionOccurred(Mockito.anyInt());

        assertThat(
                        service.needsToHandleRollbackReconciliation(
                                AdServicesManager.MEASUREMENT_DELETION))
                .isFalse();
    }

    @Test
    public void testNeedsToHandleRollbackReconciliation_noDeletion_returnsFalse() {
        AdServicesManagerService service =
                spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));

        disableEnforceAdServicesManagerPermission(service);

        setAdServicesRolledBackFromVersionedPackage(
                service, TEST_ROLLED_BACK_FROM_MODULE_VERSION, ROLLBACK_ID);
        setAdServicesRolledBackToVersionedPackage(
                service, TEST_ROLLED_BACK_TO_MODULE_VERSION, ROLLBACK_ID);

        // Set the deletion bit to false.
        doReturn(false).when(service).hasAdServicesDeletionOccurred(Mockito.anyInt());

        doReturn(TEST_ROLLED_BACK_FROM_MODULE_VERSION)
                .when(service)
                .getPreviousStoredVersion(Mockito.anyInt());
        setAdServicesModuleVersion(service, TEST_ROLLED_BACK_TO_MODULE_VERSION);

        assertThat(
                        service.needsToHandleRollbackReconciliation(
                                AdServicesManager.MEASUREMENT_DELETION))
                .isFalse();
    }

    @Test
    public void testNeedsToHandleRollbackReconciliation_versionFromDoesNotEqual_returnsFalse() {
        AdServicesManagerService service =
                spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));

        disableEnforceAdServicesManagerPermission(service);

        setAdServicesRolledBackFromVersionedPackage(
                service, TEST_ROLLED_BACK_FROM_MODULE_VERSION, ROLLBACK_ID);
        setAdServicesRolledBackToVersionedPackage(
                service, TEST_ROLLED_BACK_TO_MODULE_VERSION, ROLLBACK_ID);

        // Set the deletion bit to false.
        doReturn(true).when(service).hasAdServicesDeletionOccurred(Mockito.anyInt());

        doReturn(TEST_ROLLED_BACK_FROM_MODULE_VERSION + 1)
                .when(service)
                .getPreviousStoredVersion(Mockito.anyInt());
        setAdServicesModuleVersion(service, TEST_ROLLED_BACK_TO_MODULE_VERSION);

        assertThat(
                        service.needsToHandleRollbackReconciliation(
                                AdServicesManager.MEASUREMENT_DELETION))
                .isFalse();
    }

    @Test
    public void testNeedsToHandleRollbackReconciliation_versionToDoesNotEqual_returnsFalse() {
        AdServicesManagerService service =
                spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));

        disableEnforceAdServicesManagerPermission(service);

        setAdServicesRolledBackFromVersionedPackage(
                service, TEST_ROLLED_BACK_FROM_MODULE_VERSION, ROLLBACK_ID);
        setAdServicesRolledBackToVersionedPackage(
                service, TEST_ROLLED_BACK_TO_MODULE_VERSION, ROLLBACK_ID);

        // Set the deletion bit to false.
        doReturn(true).when(service).hasAdServicesDeletionOccurred(Mockito.anyInt());

        doReturn(TEST_ROLLED_BACK_FROM_MODULE_VERSION)
                .when(service)
                .getPreviousStoredVersion(Mockito.anyInt());
        setAdServicesModuleVersion(service, TEST_ROLLED_BACK_TO_MODULE_VERSION + 1);

        assertThat(
                        service.needsToHandleRollbackReconciliation(
                                AdServicesManager.MEASUREMENT_DELETION))
                .isFalse();
    }

    @Test
    public void testNeedsToHandleRollbackReconciliation_returnsTrue() {
        AdServicesManagerService service =
                spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));

        disableEnforceAdServicesManagerPermission(service);

        setAdServicesRolledBackFromVersionedPackage(
                service, TEST_ROLLED_BACK_FROM_MODULE_VERSION, ROLLBACK_ID);
        setAdServicesRolledBackToVersionedPackage(
                service, TEST_ROLLED_BACK_TO_MODULE_VERSION, ROLLBACK_ID);

        // Set the deletion bit to false.
        doReturn(true).when(service).hasAdServicesDeletionOccurred(Mockito.anyInt());

        doReturn(TEST_ROLLED_BACK_FROM_MODULE_VERSION)
                .when(service)
                .getPreviousStoredVersion(Mockito.anyInt());
        setAdServicesModuleVersion(service, TEST_ROLLED_BACK_TO_MODULE_VERSION);

        assertThat(
                        service.needsToHandleRollbackReconciliation(
                                AdServicesManager.MEASUREMENT_DELETION))
                .isTrue();
        Mockito.verify(service, times(1))
                .resetAdServicesDeletionOccurred(AdServicesManager.MEASUREMENT_DELETION);
    }

    @Test
    public void setCurrentPrivacySandboxFeatureWithConsent() throws IOException {
        AdServicesManagerService service =
                spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service);

        // The default feature is PRIVACY_SANDBOX_UNSUPPORTED
        assertThat(service.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED.name());
        service.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT.name());
        assertThat(service.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT.name());
        service.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT.name());
        assertThat(service.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT.name());
        service.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED.name());
        assertThat(service.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED.name());
    }

    @Test
    public void uxConformanceTest() throws IOException {
        AdServicesManagerService service =
                spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service);

        // The default UX is UNSUPPORTED_UX
        assertThat(service.getUx()).isEqualTo(PrivacySandboxUxCollection.UNSUPPORTED_UX.toString());

        Stream.of(PrivacySandboxUxCollection.values())
                .forEach(
                        ux -> {
                            service.setUx(ux.toString());
                            assertThat(service.getUx()).isEqualTo(ux.toString());
                        });
    }

    @Test
    public void testDump_noPermission() throws Exception {
        mService = new AdServicesManagerService(mSpyContext, mUserInstanceManager);

        assertThrows(
                SecurityException.class,
                () -> mService.dump(/* fd= */ null, /* pw= */ null, /* args= */ null));
    }

    @Test
    public void testDump() throws Exception {
        doNothing()
                .when(mSpyContext)
                .enforceCallingPermission(eq(android.Manifest.permission.DUMP), isNull());
        mService = new AdServicesManagerService(mSpyContext, mUserInstanceManager);

        String dump = dump(pw -> mService.dump(/* fd= */ null, pw, /* args= */ null));

        // Content doesn't matter much, we just wanna make sure it doesn't crash (for example,
        // by using the wrong %s / %d tokens) and that its components are dumped
        assertWithMessage("content of dump()").that(dump).contains(USER_INSTANCE_MANAGER_DUMP);
    }

    // Mock the call to get the AdServices module version from the PackageManager.
    private void setAdServicesModuleVersion(AdServicesManagerService service, int version) {
        doReturn(version).when(service).getAdServicesApexVersion();
    }

    // Mock the call to get the rolled back from versioned package.
    private void setAdServicesRolledBackFromVersionedPackage(
            AdServicesManagerService service, int version, int rollbackId) {
        Map<Integer, VersionedPackage> packagesRolledBackFrom = new ArrayMap<>();
        VersionedPackage versionedPackage =
                new VersionedPackage(ADSERVICES_APEX_PACKAGE_NAME, version);
        packagesRolledBackFrom.put(rollbackId, versionedPackage);
        doReturn(packagesRolledBackFrom).when(service).getAdServicesPackagesRolledBackFrom();
    }

    // Mock the call to get the rolled back to versioned package.
    private void setAdServicesRolledBackToVersionedPackage(
            AdServicesManagerService service, int version, int rollbackId) {
        Map<Integer, VersionedPackage> packagesRolledBackTo = new ArrayMap<>();
        VersionedPackage versionedPackage =
                new VersionedPackage(ADSERVICES_APEX_PACKAGE_NAME, version);
        packagesRolledBackTo.put(rollbackId, versionedPackage);
        doReturn(packagesRolledBackTo).when(service).getAdServicesPackagesRolledBackTo();
    }

    // Since unit test cannot execute an IPC call, disable the permission check.
    private void disableEnforceAdServicesManagerPermission(AdServicesManagerService service) {
        doNothing().when(service).enforceAdServicesManagerPermission();
    }

    private void setupMockResolveInfo() {
        ResolveInfo resolveInfo = new ResolveInfo();
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = PPAPI_PACKAGE_NAME;
        activityInfo.name = "SomeName";
        resolveInfo.activityInfo = activityInfo;
        ArrayList<ResolveInfo> resolveInfoList = new ArrayList<>();
        resolveInfoList.add(resolveInfo);
        when(mMockPackageManager.queryBroadcastReceiversAsUser(
                        any(Intent.class),
                        any(PackageManager.ResolveInfoFlags.class),
                        any(UserHandle.class)))
                .thenReturn(resolveInfoList);
    }

    private void setupMockInstalledPackages() {
        PackageInfo packageInfo = Mockito.spy(PackageInfo.class);
        packageInfo.packageName = ADSERVICES_APEX_PACKAGE_NAME;
        packageInfo.isApex = true;
        doReturn((long) TEST_ROLLED_BACK_FROM_MODULE_VERSION)
                .when(packageInfo)
                .getLongVersionCode();
        ArrayList<PackageInfo> packageInfoList = new ArrayList<>();
        packageInfoList.add(packageInfo);
        when(mMockPackageManager.getInstalledPackages(any(PackageManager.PackageInfoFlags.class)))
                .thenReturn(packageInfoList);
    }

    @Test
    public void isAdIdEnabledTest() throws IOException {
        AdServicesManagerService service =
                spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));

        disableEnforceAdServicesManagerPermission(service);

        assertThat(service.isAdIdEnabled()).isFalse();
        service.setAdIdEnabled(true);
        assertThat(service.isAdIdEnabled()).isTrue();
    }

    @Test
    public void isU18AccountTest() throws IOException {
        AdServicesManagerService service =
                spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));

        disableEnforceAdServicesManagerPermission(service);

        assertThat(service.isU18Account()).isFalse();
        service.setU18Account(true);
        assertThat(service.isU18Account()).isTrue();
    }

    @Test
    public void isEntryPointEnabledTest() throws IOException {
        AdServicesManagerService service =
                spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));

        disableEnforceAdServicesManagerPermission(service);

        assertThat(service.isEntryPointEnabled()).isFalse();
        service.setEntryPointEnabled(true);
        assertThat(service.isEntryPointEnabled()).isTrue();
    }

    @Test
    public void isAdultAccountTest() throws IOException {
        AdServicesManagerService service =
                spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));

        disableEnforceAdServicesManagerPermission(service);

        assertThat(service.isAdultAccount()).isFalse();
        service.setAdultAccount(true);
        assertThat(service.isAdultAccount()).isTrue();
    }

    @Test
    public void wasU18NotificationDisplayedTest() throws IOException {
        AdServicesManagerService service =
                spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));

        disableEnforceAdServicesManagerPermission(service);

        assertThat(service.wasU18NotificationDisplayed()).isFalse();
        service.setU18NotificationDisplayed(true);
        assertThat(service.wasU18NotificationDisplayed()).isTrue();
    }

    @Test
    public void enrollmentChannelConformanceTest() throws IOException {
        AdServicesManagerService service =
                spy(new AdServicesManagerService(mSpyContext, mUserInstanceManager));
        // Since unit test cannot execute an IPC call currently, disable the permission check.
        disableEnforceAdServicesManagerPermission(service);

        // The default enrollment channel is null.
        assertThat(service.getEnrollmentChannel()).isEqualTo(null);

        Stream.of(PrivacySandboxEnrollmentChannelCollection.values())
                .forEach(
                        channel -> {
                            service.setEnrollmentChannel(channel.toString());
                            assertThat(service.getEnrollmentChannel())
                                    .isEqualTo(channel.toString());
                        });
    }

    private static void setShellCommandEnabled(boolean value) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ADSERVICES_SHELL_COMMAND_ENABLED,
                Boolean.toString(value),
                /* makeDefault */ false);
    }

    private static String handleShellCommand(Binder binder) throws IOException {
        ParcelFileDescriptor[] inPipe = ParcelFileDescriptor.createPipe();
        ParcelFileDescriptor[] outAndErrPipe = ParcelFileDescriptor.createPipe();
        ParcelFileDescriptor readEnd = outAndErrPipe[0];
        ParcelFileDescriptor writeEnd = outAndErrPipe[1];

        binder.handleShellCommand(
                /* in= */ inPipe[1], /* out= */ writeEnd, /* err= */ writeEnd, /* args= */ null);
        writeEnd.close();

        // Input is not used, but cannot be null (and must be closed)
        inPipe[1].close(); // write end
        inPipe[0].close(); // read end

        String output;
        try (InputStream is = new ParcelFileDescriptor.AutoCloseInputStream(readEnd)) {
            output =
                    new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                            .lines()
                            .collect(Collectors.joining("\n"));
        }
        return output.trim();
    }
}
