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

package com.android.adservices.service.measurement;

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_IO_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.view.MotionEvent.ACTION_BUTTON_PRESS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.adservices.measurement.DeletionParam;
import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.SourceRegistrationRequest;
import android.adservices.measurement.SourceRegistrationRequestInternal;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebSourceRegistrationRequestInternal;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.adservices.measurement.WebTriggerRegistrationRequestInternal;
import android.annotation.NonNull;
import android.app.adservices.AdServicesManager;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;
import android.view.InputEvent;
import android.view.MotionEvent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.common.WebUtil;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.SQLDatastoreManager;
import com.android.adservices.data.measurement.deletion.MeasurementDataDeleter;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.measurement.attribution.TriggerContentProvider;
import com.android.adservices.service.measurement.inputverification.ClickVerifier;
import com.android.adservices.service.measurement.registration.AsyncRegistrationContentProvider;
import com.android.adservices.service.measurement.rollback.MeasurementRollbackCompatManager;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.MeasurementWipeoutStats;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Unit tests for {@link MeasurementImpl} */
@SmallTest
public final class MeasurementImplTest {
    @Rule
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(AdServicesManager.class)
                    .spyStatic(MeasurementRollbackCompatManager.class)
                    .spyStatic(FlagsFactory.class)
                    .spyStatic(AdServicesLoggerImpl.class)
                    .mockStatic(SdkLevel.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    private static final Context DEFAULT_CONTEXT = ApplicationProvider.getApplicationContext();
    private static final Uri DEFAULT_URI = Uri.parse("android-app://com.example.abc");
    private static final Uri REGISTRATION_URI_1 = WebUtil.validUri("https://foo.test/bar?ad=134");
    private static final Uri REGISTRATION_URI_2 = WebUtil.validUri("https://foo.test/bar?ad=256");
    private static final String SDK_PACKAGE_NAME = "sdk.package.name";
    private static final String DEFAULT_ENROLLMENT = "enrollment-id";
    private static final Uri INVALID_WEB_DESTINATION = Uri.parse("https://example.not_a_tld");
    private static final Uri WEB_DESTINATION = WebUtil.validUri("https://web-destination.test");
    private static final Uri OTHER_WEB_DESTINATION =
            WebUtil.validUri("https://other-web-destination.test");
    private static final Uri APP_DESTINATION = Uri.parse("android-app://com.app_destination");
    private static final Uri OTHER_APP_DESTINATION =
            Uri.parse("android-app://com.other_app_destination");
    private static final boolean DEFAULT_AD_ID_PERMISSION = false;

    private static final WebSourceParams INPUT_SOURCE_REGISTRATION_1 =
            new WebSourceParams.Builder(REGISTRATION_URI_1).setDebugKeyAllowed(true).build();
    private static final WebSourceParams INPUT_SOURCE_REGISTRATION_2 =
            new WebSourceParams.Builder(REGISTRATION_URI_2).setDebugKeyAllowed(false).build();
    private static final WebTriggerParams INPUT_TRIGGER_REGISTRATION_1 =
            new WebTriggerParams.Builder(REGISTRATION_URI_1).setDebugKeyAllowed(true).build();
    private static final WebTriggerParams INPUT_TRIGGER_REGISTRATION_2 =
            new WebTriggerParams.Builder(REGISTRATION_URI_2).setDebugKeyAllowed(false).build();
    private static final long REQUEST_TIME = 10000L;
    private static final String POST_BODY = "{\"ad_location\":\"bottom_right\"}";
    private static final String AD_ID_VALUE = "ad_id_value";

    @Mock AdServicesErrorLogger mErrorLogger;

    @Spy
    private DatastoreManager mDatastoreManager =
            new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger);

    @Mock private ContentProviderClient mMockContentProviderClient;
    @Mock private ContentResolver mContentResolver;
    @Mock private ClickVerifier mClickVerifier;
    private MeasurementImpl mMeasurementImpl;
    @Mock EnrollmentDao mEnrollmentDao;
    @Mock MeasurementDataDeleter mMeasurementDataDeleter;

    private static EnrollmentData getEnrollment(String enrollmentId) {
        return new EnrollmentData.Builder().setEnrollmentId(enrollmentId).build();
    }

    public static InputEvent getInputEvent() {
        return MotionEvent.obtain(0, 0, ACTION_BUTTON_PRESS, 0, 0, 0);
    }

    private static WebTriggerRegistrationRequestInternal createWebTriggerRegistrationRequest(
            Uri destination) {
        WebTriggerRegistrationRequest webTriggerRegistrationRequest =
                new WebTriggerRegistrationRequest.Builder(
                                Arrays.asList(
                                        INPUT_TRIGGER_REGISTRATION_1, INPUT_TRIGGER_REGISTRATION_2),
                                destination)
                        .build();
        return new WebTriggerRegistrationRequestInternal.Builder(
                        webTriggerRegistrationRequest,
                        DEFAULT_CONTEXT.getPackageName(),
                        SDK_PACKAGE_NAME)
                .build();
    }

    private static WebSourceRegistrationRequestInternal createWebSourceRegistrationRequest(
            Uri appDestination, Uri webDestination, Uri verifiedDestination) {
        return createWebSourceRegistrationRequest(
                appDestination, webDestination, verifiedDestination, DEFAULT_URI);
    }

    private static WebSourceRegistrationRequestInternal createWebSourceRegistrationRequest(
            Uri appDestination, Uri webDestination, Uri verifiedDestination, Uri topOriginUri) {
        WebSourceRegistrationRequest sourceRegistrationRequest =
                new WebSourceRegistrationRequest.Builder(
                                Arrays.asList(
                                        INPUT_SOURCE_REGISTRATION_1, INPUT_SOURCE_REGISTRATION_2),
                                topOriginUri)
                        .setAppDestination(appDestination)
                        .setWebDestination(webDestination)
                        .setVerifiedDestination(verifiedDestination)
                        .build();
        return new WebSourceRegistrationRequestInternal.Builder(
                        sourceRegistrationRequest,
                        DEFAULT_CONTEXT.getPackageName(),
                        SDK_PACKAGE_NAME,
                        REQUEST_TIME)
                .build();
    }

    private static SourceRegistrationRequestInternal createSourceRegistrationRequest() {
        SourceRegistrationRequest request =
                new SourceRegistrationRequest.Builder(
                                Arrays.asList(REGISTRATION_URI_1, REGISTRATION_URI_2))
                        .build();
        return new SourceRegistrationRequestInternal.Builder(
                        request, DEFAULT_CONTEXT.getPackageName(), SDK_PACKAGE_NAME, REQUEST_TIME)
                .build();
    }

    @Before
    public void before() throws RemoteException {
        MockitoAnnotations.initMocks(this);
        when(mContentResolver.acquireContentProviderClient(TriggerContentProvider.TRIGGER_URI))
                .thenReturn(mMockContentProviderClient);
        when(mContentResolver.acquireContentProviderClient(
                        AsyncRegistrationContentProvider.TRIGGER_URI))
                .thenReturn(mMockContentProviderClient);
        when(mMockContentProviderClient.insert(eq(TriggerContentProvider.TRIGGER_URI), any()))
                .thenReturn(TriggerContentProvider.TRIGGER_URI);
        when(mMockContentProviderClient.insert(
                        eq(AsyncRegistrationContentProvider.TRIGGER_URI), any()))
                .thenReturn(AsyncRegistrationContentProvider.TRIGGER_URI);
        mMeasurementImpl =
                spy(
                        new MeasurementImpl(
                                DEFAULT_CONTEXT,
                                mDatastoreManager,
                                mClickVerifier,
                                mMeasurementDataDeleter,
                                mContentResolver));
        doReturn(true).when(mClickVerifier).isInputEventVerifiable(any(), anyLong(), anyString());
        when(mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(any()))
                .thenReturn(getEnrollment(DEFAULT_ENROLLMENT));
    }

    @Test
    public void testDeleteRegistrations_successfulNoOptionalParameters() {
        disableRollbackDeletion();

        MeasurementImpl measurement =
                new MeasurementImpl(
                        DEFAULT_CONTEXT,
                        new SQLDatastoreManager(
                                DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger),
                        mClickVerifier,
                        mMeasurementDataDeleter,
                        mContentResolver);
        doReturn(true).when(mMeasurementDataDeleter).delete(any());
        final int result =
                measurement.deleteRegistrations(
                        new DeletionParam.Builder(
                                        Collections.emptyList(),
                                        Collections.emptyList(),
                                        Instant.ofEpochMilli(Long.MIN_VALUE),
                                        Instant.ofEpochMilli(Long.MAX_VALUE),
                                        DEFAULT_CONTEXT.getPackageName(),
                                        SDK_PACKAGE_NAME)
                                .build());
        assertEquals(STATUS_SUCCESS, result);
    }

    @Test
    public void testRegisterWebSource_verifiedDestination_webDestinationMismatch() {
        final int result =
                mMeasurementImpl.registerWebSource(
                        createWebSourceRegistrationRequest(
                                APP_DESTINATION, WEB_DESTINATION, OTHER_WEB_DESTINATION),
                        DEFAULT_AD_ID_PERMISSION,
                        System.currentTimeMillis());
        assertEquals(STATUS_INVALID_ARGUMENT, result);
    }

    @Test
    public void testDeleteRegistrations_successfulWithRange() {
        disableRollbackDeletion();

        doReturn(true).when(mMeasurementDataDeleter).delete(any());
        final int result =
                mMeasurementImpl.deleteRegistrations(
                        new DeletionParam.Builder(
                                        Collections.emptyList(),
                                        Collections.emptyList(),
                                        Instant.now().minusSeconds(1),
                                        Instant.now(),
                                        DEFAULT_CONTEXT.getPackageName(),
                                        SDK_PACKAGE_NAME)
                                .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_DELETE)
                                .setDeletionMode(DeletionRequest.DELETION_MODE_ALL)
                                .build());
        assertEquals(STATUS_SUCCESS, result);
    }

    @Test
    public void testDeleteRegistrations_successfulWithOrigin() {
        disableRollbackDeletion();

        DeletionParam deletionParam =
                new DeletionParam.Builder(
                                Collections.singletonList(DEFAULT_URI),
                                Collections.emptyList(),
                                Instant.ofEpochMilli(Long.MIN_VALUE),
                                Instant.ofEpochMilli(Long.MAX_VALUE),
                                DEFAULT_CONTEXT.getPackageName(),
                                SDK_PACKAGE_NAME)
                        .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_DELETE)
                        .setDeletionMode(DeletionRequest.DELETION_MODE_ALL)
                        .build();
        when(mMeasurementDataDeleter.delete(deletionParam)).thenReturn(true);
        final int result = mMeasurementImpl.deleteRegistrations(deletionParam);
        assertEquals(STATUS_SUCCESS, result);
    }

    @Test
    public void testDeleteRegistrations_internalError() {
        doReturn(false).when(mDatastoreManager).runInTransaction(any());
        doReturn(false).when(mMeasurementDataDeleter).delete(any());
        final int result =
                mMeasurementImpl.deleteRegistrations(
                        new DeletionParam.Builder(
                                        Collections.emptyList(),
                                        Collections.emptyList(),
                                        Instant.MIN,
                                        Instant.MAX,
                                        DEFAULT_CONTEXT.getPackageName(),
                                        SDK_PACKAGE_NAME)
                                .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_DELETE)
                                .setDeletionMode(DeletionRequest.DELETION_MODE_ALL)
                                .build());
        assertEquals(STATUS_INTERNAL_ERROR, result);
    }

    @Test
    public void testRegisterWebSource_invalidWebDestination() {
        final int result =
                mMeasurementImpl.registerWebSource(
                        createWebSourceRegistrationRequest(null, INVALID_WEB_DESTINATION, null),
                        DEFAULT_AD_ID_PERMISSION,
                        System.currentTimeMillis());
        assertEquals(STATUS_INVALID_ARGUMENT, result);
    }

    @Test
    public void testRegisterWebTrigger_invalidDestination() {
        final int result =
                mMeasurementImpl.registerWebTrigger(
                        createWebTriggerRegistrationRequest(INVALID_WEB_DESTINATION),
                        DEFAULT_AD_ID_PERMISSION,
                        System.currentTimeMillis());
        assertEquals(STATUS_INVALID_ARGUMENT, result);
    }

    @Test
    public void testRegisterWebSource_verifiedDestination_appDestinationMismatch() {
        final int result =
                mMeasurementImpl.registerWebSource(
                        createWebSourceRegistrationRequest(
                                APP_DESTINATION, WEB_DESTINATION, OTHER_APP_DESTINATION),
                        DEFAULT_AD_ID_PERMISSION,
                        System.currentTimeMillis());
        assertEquals(STATUS_INVALID_ARGUMENT, result);
    }

    @Test
    public void testRegisterEvent_noOptionalParameters_success() {
        final int result =
                mMeasurementImpl.registerEvent(
                        REGISTRATION_URI_1,
                        DEFAULT_CONTEXT.getPackageName(),
                        SDK_PACKAGE_NAME,
                        false,
                        null,
                        null,
                        null);
        assertEquals(STATUS_SUCCESS, result);
    }

    @Test
    public void testRegisterEvent_optionalParameters_success() {
        doReturn(true).when(mClickVerifier).isInputEventVerifiable(any(), anyLong(), anyString());
        final int result =
                mMeasurementImpl.registerEvent(
                        REGISTRATION_URI_1,
                        DEFAULT_CONTEXT.getPackageName(),
                        SDK_PACKAGE_NAME,
                        false,
                        POST_BODY,
                        getInputEvent(),
                        AD_ID_VALUE);
        assertEquals(STATUS_SUCCESS, result);
    }

    @Test
    public void testGetSourceType_verifiedInputEvent_returnsNavigationSourceType() {
        doReturn(true).when(mClickVerifier).isInputEventVerifiable(any(), anyLong(), anyString());
        assertEquals(
                Source.SourceType.NAVIGATION,
                mMeasurementImpl.getSourceType(getInputEvent(), 1000L, "app_name"));
    }

    @Test
    public void testGetSourceType_noInputEventGiven() {
        assertEquals(
                Source.SourceType.EVENT, mMeasurementImpl.getSourceType(null, 1000L, "app_name"));
    }

    @Test
    public void testGetSourceType_inputEventNotVerifiable_returnsEventSourceType() {
        doReturn(false).when(mClickVerifier).isInputEventVerifiable(any(), anyLong(), anyString());
        assertEquals(
                Source.SourceType.EVENT,
                mMeasurementImpl.getSourceType(getInputEvent(), 1000L, "app_name"));
    }

    @Test
    public void testGetSourceType_clickVerificationDisabled_returnsNavigationSourceType() {
        Flags mockFlags = Mockito.mock(Flags.class);
        ClickVerifier mockClickVerifier = Mockito.mock(ClickVerifier.class);
        doReturn(false)
                .when(mockClickVerifier)
                .isInputEventVerifiable(any(), anyLong(), anyString());
        doReturn(false).when(mockFlags).getMeasurementIsClickVerificationEnabled();
        ExtendedMockito.doReturn(mockFlags).when(FlagsFactory::getFlagsForTest);
        MeasurementImpl measurementImpl =
                new MeasurementImpl(
                        DEFAULT_CONTEXT,
                        mDatastoreManager,
                        mockClickVerifier,
                        mMeasurementDataDeleter,
                        mContentResolver);

        // Because click verification is disabled, the SourceType is NAVIGATION even if the
        // input event is not verifiable.
        assertEquals(
                Source.SourceType.NAVIGATION,
                measurementImpl.getSourceType(getInputEvent(), 1000L, "app_name"));
    }

    @Test
    public void testDeleteRegistrations_success_recordsDeletionInSystemServer() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        Flags mockFlags = Mockito.mock(Flags.class);

        doReturn(false).when(mockFlags).getMeasurementRollbackDeletionKillSwitch();
        ExtendedMockito.doReturn(mockFlags).when(FlagsFactory::getFlags);

        AdServicesManager mockAdServicesManager = Mockito.mock(AdServicesManager.class);
        ExtendedMockito.doReturn(mockAdServicesManager)
                .when(() -> AdServicesManager.getInstance(any()));

        doDeleteRegistrations();

        Mockito.verify(mockAdServicesManager)
                .recordAdServicesDeletionOccurred(AdServicesManager.MEASUREMENT_DELETION);
    }

    @Test
    public void testDeleteRegistrations_success_recordsDeletion_S() {
        Assume.assumeTrue(!SdkLevel.isAtLeastT());
        Assume.assumeTrue(SdkLevel.isAtLeastS());

        Flags mockFlags = Mockito.mock(Flags.class);
        doReturn(false).when(mockFlags).getMeasurementRollbackDeletionKillSwitch();

        MeasurementRollbackCompatManager mockRollbackManager =
                doDeleteRegistrationsCompat(mockFlags);

        Mockito.verify(mockRollbackManager).recordAdServicesDeletionOccurred();
    }

    @Test
    public void testDeleteRegistrations_success_recordsDeletion_R() {
        Assume.assumeTrue(!SdkLevel.isAtLeastS());
        Flags mockFlags = Mockito.mock(Flags.class);

        doReturn(false).when(mockFlags).getMeasurementRollbackDeletionKillSwitch();
        doReturn(true).when(mockFlags).getMeasurementRollbackDeletionREnabled();
        MeasurementRollbackCompatManager mockRollbackManager =
                doDeleteRegistrationsCompat(mockFlags);

        Mockito.verify(mockRollbackManager).recordAdServicesDeletionOccurred();
    }

    @Test
    public void testDeleteRegistrations_success_recordsDeletionInSystemServer_flagOff() {
        Flags mockFlags = Mockito.mock(Flags.class);

        doReturn(true).when(mockFlags).getMeasurementRollbackDeletionKillSwitch();
        ExtendedMockito.doReturn(mockFlags).when(FlagsFactory::getFlags);

        AdServicesManager mockAdServicesManager = Mockito.mock(AdServicesManager.class);
        ExtendedMockito.doReturn(mockAdServicesManager)
                .when(() -> AdServicesManager.getInstance(any()));

        doDeleteRegistrations();

        Mockito.verify(mockAdServicesManager, Mockito.never())
                .recordAdServicesDeletionOccurred(AdServicesManager.MEASUREMENT_DELETION);
    }

    @Test
    public void testDeleteRegistrations_success_recordsDeletionInAppSearch_flagOff_S() {
        ExtendedMockito.doReturn(false).when(SdkLevel::isAtLeastT);
        ExtendedMockito.doReturn(true).when(SdkLevel::isAtLeastS);
        Flags mockFlags = Mockito.mock(Flags.class);

        doReturn(true).when(mockFlags).getMeasurementRollbackDeletionAppSearchKillSwitch();
        MeasurementRollbackCompatManager mockRollbackManager =
                doDeleteRegistrationsCompat(mockFlags);

        Mockito.verify(mockRollbackManager, Mockito.never()).recordAdServicesDeletionOccurred();
    }

    @Test
    public void testDeleteRegistrations_success_recordsDeletionInAppSearch_flagOff_R() {
        ExtendedMockito.doReturn(false).when(SdkLevel::isAtLeastS);
        Flags mockFlags = Mockito.mock(Flags.class);

        doReturn(false).when(mockFlags).getMeasurementRollbackDeletionREnabled();
        MeasurementRollbackCompatManager mockRollbackManager =
                doDeleteRegistrationsCompat(mockFlags);

        Mockito.verify(mockRollbackManager, Mockito.never()).recordAdServicesDeletionOccurred();
    }

    private MeasurementRollbackCompatManager doDeleteRegistrationsCompat(Flags mockFlags) {
        ExtendedMockito.doReturn(mockFlags).when(FlagsFactory::getFlags);

        MeasurementRollbackCompatManager mockRollbackManager =
                Mockito.mock(MeasurementRollbackCompatManager.class);
        ExtendedMockito.doReturn(mockRollbackManager)
                .when(
                        () ->
                                MeasurementRollbackCompatManager.getInstance(
                                        any(), eq(AdServicesManager.MEASUREMENT_DELETION)));

        doDeleteRegistrations();
        return mockRollbackManager;
    }

    private void doDeleteRegistrations() {
        MeasurementImpl measurement = createMeasurementImpl();
        doReturn(true).when(mMeasurementDataDeleter).delete(any());
        measurement.deleteRegistrations(
                new DeletionParam.Builder(
                                Collections.emptyList(),
                                Collections.emptyList(),
                                Instant.ofEpochMilli(Long.MIN_VALUE),
                                Instant.ofEpochMilli(Long.MAX_VALUE),
                                DEFAULT_CONTEXT.getPackageName(),
                                SDK_PACKAGE_NAME)
                        .build());
    }

    @Test
    public void testDeletePackageRecords_success_recordsDeletionInSystemServer() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());

        Flags mockFlags = Mockito.mock(Flags.class);

        doReturn(false).when(mockFlags).getMeasurementRollbackDeletionKillSwitch();
        ExtendedMockito.doReturn(mockFlags).when(FlagsFactory::getFlags);

        AdServicesManager mockAdServicesManager = Mockito.mock(AdServicesManager.class);
        ExtendedMockito.doReturn(mockAdServicesManager)
                .when(() -> AdServicesManager.getInstance(any()));

        doReturn(Optional.of(true)).when(mDatastoreManager).runInTransactionWithResult(any());
        doReturn(true).when(mDatastoreManager).runInTransaction(any());

        doDeletePackageRecords();

        Mockito.verify(mockAdServicesManager)
                .recordAdServicesDeletionOccurred(AdServicesManager.MEASUREMENT_DELETION);
    }

    @Test
    public void testDeletePackageRecords_success_recordsDeletion_S() {
        Assume.assumeTrue(!SdkLevel.isAtLeastT());
        Assume.assumeTrue(SdkLevel.isAtLeastS());

        Flags mockFlags = Mockito.mock(Flags.class);
        doReturn(false).when(mockFlags).getMeasurementRollbackDeletionKillSwitch();

        MeasurementRollbackCompatManager mockRollbackManager =
                doDeletePackageRecordsCompat(mockFlags);

        Mockito.verify(mockRollbackManager).recordAdServicesDeletionOccurred();
    }

    @Test
    public void testDeletePackageRecords_success_recordsDeletion_R() {
        Assume.assumeTrue(!SdkLevel.isAtLeastS());

        Flags mockFlags = Mockito.mock(Flags.class);
        doReturn(false).when(mockFlags).getMeasurementRollbackDeletionKillSwitch();
        doReturn(true).when(mockFlags).getMeasurementRollbackDeletionREnabled();

        MeasurementRollbackCompatManager mockRollbackManager =
                doDeletePackageRecordsCompat(mockFlags);

        Mockito.verify(mockRollbackManager).recordAdServicesDeletionOccurred();
    }

    @Test
    public void testDeletePackageRecords_noDeletion_doesNotRecordDeletion() {
        Flags mockFlags = Mockito.mock(Flags.class);

        doReturn(false).when(mockFlags).getMeasurementRollbackDeletionKillSwitch();
        ExtendedMockito.doReturn(mockFlags).when(FlagsFactory::getFlags);

        AdServicesManager mockAdServicesManager = Mockito.mock(AdServicesManager.class);
        ExtendedMockito.doReturn(mockAdServicesManager)
                .when(() -> AdServicesManager.getInstance(any()));

        doReturn(Optional.of(false)).when(mDatastoreManager).runInTransactionWithResult(any());
        doReturn(true).when(mDatastoreManager).runInTransaction(any());

        doDeletePackageRecords();

        Mockito.verify(mockAdServicesManager, Mockito.never())
                .recordAdServicesDeletionOccurred(AdServicesManager.MEASUREMENT_DELETION);
    }

    private MeasurementRollbackCompatManager doDeletePackageRecordsCompat(Flags mockFlags) {
        ExtendedMockito.doReturn(mockFlags).when(FlagsFactory::getFlags);

        MeasurementRollbackCompatManager mockRollbackManager =
                Mockito.mock(MeasurementRollbackCompatManager.class);
        ExtendedMockito.doReturn(mockRollbackManager)
                .when(
                        () ->
                                MeasurementRollbackCompatManager.getInstance(
                                        any(), eq(AdServicesManager.MEASUREMENT_DELETION)));

        doReturn(true).when(mMeasurementDataDeleter).deleteAppUninstalledData(any());

        doDeletePackageRecords();
        return mockRollbackManager;
    }

    private void doDeletePackageRecords() {
        MeasurementImpl measurement = createMeasurementImpl();
        measurement.deletePackageRecords(DEFAULT_URI);
    }

    @Test
    public void testDeleteAllMeasurementData_success_recordsDeletionInSystemServer() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        Flags mockFlags = Mockito.mock(Flags.class);

        doReturn(false).when(mockFlags).getMeasurementRollbackDeletionKillSwitch();
        ExtendedMockito.doReturn(mockFlags).when(FlagsFactory::getFlags);

        AdServicesManager mockAdServicesManager = Mockito.mock(AdServicesManager.class);
        ExtendedMockito.doReturn(mockAdServicesManager)
                .when(() -> AdServicesManager.getInstance(any()));

        doDeleteAllMeasurementData();

        Mockito.verify(mockAdServicesManager)
                .recordAdServicesDeletionOccurred(AdServicesManager.MEASUREMENT_DELETION);
    }

    @Test
    public void testDeleteAllMeasurementData_success_recordsDeletion_S() {
        Assume.assumeTrue(!SdkLevel.isAtLeastT());
        Assume.assumeTrue(SdkLevel.isAtLeastS());

        Flags mockFlags = Mockito.mock(Flags.class);
        doReturn(false).when(mockFlags).getMeasurementRollbackDeletionKillSwitch();

        MeasurementRollbackCompatManager mockRollbackManager =
                doDeleteAllMeasurementDataCompat(mockFlags);

        Mockito.verify(mockRollbackManager).recordAdServicesDeletionOccurred();
    }

    @Test
    public void testDeleteAllMeasurementData_success_recordsDeletion_R() {
        Assume.assumeTrue(!SdkLevel.isAtLeastS());

        Flags mockFlags = Mockito.mock(Flags.class);
        doReturn(false).when(mockFlags).getMeasurementRollbackDeletionKillSwitch();
        doReturn(true).when(mockFlags).getMeasurementRollbackDeletionREnabled();

        MeasurementRollbackCompatManager mockRollbackManager =
                doDeleteAllMeasurementDataCompat(mockFlags);

        Mockito.verify(mockRollbackManager).recordAdServicesDeletionOccurred();
    }

    private MeasurementRollbackCompatManager doDeleteAllMeasurementDataCompat(Flags mockFlags) {
        ExtendedMockito.doReturn(mockFlags).when(FlagsFactory::getFlags);

        MeasurementRollbackCompatManager mockRollbackManager =
                Mockito.mock(MeasurementRollbackCompatManager.class);
        ExtendedMockito.doReturn(mockRollbackManager)
                .when(
                        () ->
                                MeasurementRollbackCompatManager.getInstance(
                                        any(), eq(AdServicesManager.MEASUREMENT_DELETION)));

        doDeleteAllMeasurementData();
        return mockRollbackManager;
    }

    private void doDeleteAllMeasurementData() {
        MeasurementImpl measurement = createMeasurementImpl();
        measurement.deleteAllMeasurementData(Collections.EMPTY_LIST);
    }

    @Test
    public void testDeleteAllUninstalledMeasurementData_success_recordsDeletionInSystemServer() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        Flags mockFlags = Mockito.mock(Flags.class);

        doReturn(false).when(mockFlags).getMeasurementRollbackDeletionKillSwitch();
        ExtendedMockito.doReturn(mockFlags).when(FlagsFactory::getFlags);

        AdServicesManager mockAdServicesManager = Mockito.mock(AdServicesManager.class);
        ExtendedMockito.doReturn(mockAdServicesManager)
                .when(() -> AdServicesManager.getInstance(any()));

        doReturn(Optional.of(true)).when(mDatastoreManager).runInTransactionWithResult(any());
        doReturn(true).when(mDatastoreManager).runInTransaction(any());

        MeasurementImpl measurement = createMeasurementImpl();
        measurement.deleteAllUninstalledMeasurementData();

        Mockito.verify(mockAdServicesManager)
                .recordAdServicesDeletionOccurred(AdServicesManager.MEASUREMENT_DELETION);
    }

    @Test
    public void testDeleteAllUninstalledMeasurementData_success_recordsDeletion_S() {
        Assume.assumeTrue(!SdkLevel.isAtLeastT());
        Assume.assumeTrue(SdkLevel.isAtLeastS());

        Flags mockFlags = Mockito.mock(Flags.class);
        doReturn(false).when(mockFlags).getMeasurementRollbackDeletionKillSwitch();

        MeasurementRollbackCompatManager mockRollbackManager =
                doDeleteAllUninstalledMeasurementDataCompat(mockFlags);

        Mockito.verify(mockRollbackManager).recordAdServicesDeletionOccurred();
    }

    @Test
    public void testDeleteAllUninstalledMeasurementData_success_recordsDeletion_R() {
        Assume.assumeTrue(!SdkLevel.isAtLeastS());

        Flags mockFlags = Mockito.mock(Flags.class);
        doReturn(false).when(mockFlags).getMeasurementRollbackDeletionKillSwitch();
        doReturn(true).when(mockFlags).getMeasurementRollbackDeletionREnabled();

        MeasurementRollbackCompatManager mockRollbackManager =
                doDeleteAllUninstalledMeasurementDataCompat(mockFlags);

        Mockito.verify(mockRollbackManager).recordAdServicesDeletionOccurred();
    }

    private MeasurementRollbackCompatManager doDeleteAllUninstalledMeasurementDataCompat(
            Flags mockFlags) {
        ExtendedMockito.doReturn(mockFlags).when(FlagsFactory::getFlags);

        MeasurementRollbackCompatManager mockRollbackManager =
                Mockito.mock(MeasurementRollbackCompatManager.class);
        ExtendedMockito.doReturn(mockRollbackManager)
                .when(
                        () ->
                                MeasurementRollbackCompatManager.getInstance(
                                        any(), eq(AdServicesManager.MEASUREMENT_DELETION)));

        doReturn(true).when(mMeasurementDataDeleter).deleteAppUninstalledData(any());

        MeasurementImpl measurement = createMeasurementImpl();
        measurement.deleteAllUninstalledMeasurementData();
        return mockRollbackManager;
    }

    @Test
    public void testDeleteAllUninstalledMeasurementData_noDeletion_doesNotRecordDeletion() {
        Flags mockFlags = Mockito.mock(Flags.class);

        doReturn(false).when(mockFlags).getMeasurementRollbackDeletionKillSwitch();
        ExtendedMockito.doReturn(mockFlags).when(FlagsFactory::getFlags);

        AdServicesManager mockAdServicesManager = Mockito.mock(AdServicesManager.class);
        ExtendedMockito.doReturn(mockAdServicesManager)
                .when(() -> AdServicesManager.getInstance(any()));

        doReturn(Optional.of(List.of(Uri.parse("android-app://foo"))))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());
        doReturn(false).when(mMeasurementDataDeleter).deleteAppUninstalledData(any());

        MeasurementImpl measurement = createMeasurementImpl();
        measurement.deleteAllUninstalledMeasurementData();

        Mockito.verify(mockAdServicesManager, Mockito.never())
                .recordAdServicesDeletionOccurred(AdServicesManager.MEASUREMENT_DELETION);
    }

    @Test
    public void testCheckIfNeedsToHandleReconciliation_S() {
        ExtendedMockito.doReturn(false).when(SdkLevel::isAtLeastT);
        ExtendedMockito.doReturn(true).when(SdkLevel::isAtLeastS);

        Flags mockFlags = Mockito.mock(Flags.class);
        doReturn(false).when(mockFlags).getMeasurementRollbackDeletionKillSwitch();
        doReturn(false).when(mockFlags).getMeasurementRollbackDeletionAppSearchKillSwitch();
        checkIfNeedsToHandleReconciliationCompat(mockFlags, false);
    }

    @Test
    public void testCheckIfNeedsToHandleReconciliation_R() {
        ExtendedMockito.doReturn(false).when(SdkLevel::isAtLeastS);

        Flags mockFlags = Mockito.mock(Flags.class);
        doReturn(false).when(mockFlags).getMeasurementRollbackDeletionKillSwitch();
        doReturn(true).when(mockFlags).getMeasurementRollbackDeletionREnabled();
        checkIfNeedsToHandleReconciliationCompat(mockFlags, false);
    }

    @Test
    public void testCheckIfNeedsToHandleReconciliation_clearsData_S() {
        ExtendedMockito.doReturn(false).when(SdkLevel::isAtLeastT);
        ExtendedMockito.doReturn(true).when(SdkLevel::isAtLeastS);

        Flags mockFlags = Mockito.mock(Flags.class);
        doReturn(false).when(mockFlags).getMeasurementRollbackDeletionKillSwitch();
        checkIfNeedsToHandleReconciliationCompat(mockFlags, true);
    }

    @Test
    public void testCheckIfNeedsToHandleReconciliation_clearsData_R() {
        ExtendedMockito.doReturn(false).when(SdkLevel::isAtLeastT);
        ExtendedMockito.doReturn(true).when(SdkLevel::isAtLeastS);

        Flags mockFlags = Mockito.mock(Flags.class);
        doReturn(false).when(mockFlags).getMeasurementRollbackDeletionKillSwitch();
        doReturn(true).when(mockFlags).getMeasurementRollbackDeletionREnabled();
        checkIfNeedsToHandleReconciliationCompat(mockFlags, true);
    }

    private void checkIfNeedsToHandleReconciliationCompat(Flags mockFlags, boolean returnValue) {
        ExtendedMockito.doReturn(mockFlags).when(FlagsFactory::getFlags);

        MeasurementRollbackCompatManager mockManager =
                Mockito.mock(MeasurementRollbackCompatManager.class);
        ExtendedMockito.doReturn(mockManager)
                .when(() -> MeasurementRollbackCompatManager.getInstance(any(), anyInt()));

        doReturn(returnValue).when(mockManager).needsToHandleRollbackReconciliation();
        MeasurementImpl measurement = createMeasurementImpl();
        assertThat(measurement.checkIfNeedsToHandleReconciliation()).isEqualTo(returnValue);

        Mockito.verify(mockManager).needsToHandleRollbackReconciliation();
    }

    @Test
    public void testCheckIfNeedsToHandleReconciliation_flagOff_S() {
        ExtendedMockito.doReturn(false).when(SdkLevel::isAtLeastT);
        ExtendedMockito.doReturn(true).when(SdkLevel::isAtLeastS);

        Flags mockFlags = Mockito.mock(Flags.class);
        doReturn(true).when(mockFlags).getMeasurementRollbackDeletionAppSearchKillSwitch();
        ExtendedMockito.doReturn(mockFlags).when(FlagsFactory::getFlags);

        MeasurementImpl measurement = createMeasurementImpl();
        assertThat(measurement.checkIfNeedsToHandleReconciliation()).isFalse();
        ExtendedMockito.verify(
                () -> MeasurementRollbackCompatManager.getInstance(any(), anyInt()), never());
    }

    @Test
    public void testCheckIfNeedsToHandleReconciliation_flagOff_R() {
        ExtendedMockito.doReturn(false).when(SdkLevel::isAtLeastS);

        Flags mockFlags = Mockito.mock(Flags.class);
        doReturn(true).when(mockFlags).getMeasurementRollbackDeletionAppSearchKillSwitch();
        doReturn(false).when(mockFlags).getMeasurementRollbackDeletionREnabled();
        ExtendedMockito.doReturn(mockFlags).when(FlagsFactory::getFlags);

        MeasurementImpl measurement = createMeasurementImpl();
        assertThat(measurement.checkIfNeedsToHandleReconciliation()).isFalse();
        ExtendedMockito.verify(
                () -> MeasurementRollbackCompatManager.getInstance(any(), anyInt()), never());
    }

    @Test
    public void testCheckIfNeedsToHandleReconciliation_TPlus() {
        ExtendedMockito.doReturn(true).when(SdkLevel::isAtLeastT);

        AdServicesManager mockManager = Mockito.mock(AdServicesManager.class);
        ExtendedMockito.doReturn(mockManager).when(() -> AdServicesManager.getInstance(any()));

        doReturn(true).when(mockManager).needsToHandleRollbackReconciliation(anyInt());

        MeasurementImpl measurement = createMeasurementImpl();

        assertThat(measurement.checkIfNeedsToHandleReconciliation()).isTrue();
        Mockito.verify(mockManager)
                .needsToHandleRollbackReconciliation(eq(AdServicesManager.MEASUREMENT_DELETION));

        // Verify that the code doesn't accidentally fall through into the Android S part.
        ExtendedMockito.verify(FlagsFactory::getFlags, never());
    }

    @Test
    public void testDeleteOnRollback_logsWipeout() {
        Flags mockFlags = Mockito.mock(Flags.class);
        AdServicesLoggerImpl mockLogger = Mockito.mock(AdServicesLoggerImpl.class);
        MeasurementRollbackCompatManager mockManager =
                Mockito.mock(MeasurementRollbackCompatManager.class);

        ExtendedMockito.doReturn(mockFlags).when(FlagsFactory::getFlags);
        ExtendedMockito.doReturn(mockLogger).when(AdServicesLoggerImpl::getInstance);
        ExtendedMockito.doReturn(false).when(SdkLevel::isAtLeastT);
        ExtendedMockito.doReturn(true).when(SdkLevel::isAtLeastS);
        ExtendedMockito.doReturn(mockManager)
                .when(() -> MeasurementRollbackCompatManager.getInstance(any(), anyInt()));

        doReturn(true).when(mockManager).needsToHandleRollbackReconciliation();
        doReturn(true).when(mDatastoreManager).runInTransaction(any());
        doReturn(false).when(mockFlags).getMeasurementRollbackDeletionKillSwitch();
        doReturn(false).when(mockFlags).getMeasurementRollbackDeletionAppSearchKillSwitch();

        MeasurementImpl measurement = new MeasurementImpl(DEFAULT_CONTEXT);

        assertThat(measurement.checkIfNeedsToHandleReconciliation()).isTrue();
        ArgumentCaptor<MeasurementWipeoutStats> statusArg =
                ArgumentCaptor.forClass(MeasurementWipeoutStats.class);
        Mockito.verify(mockLogger).logMeasurementWipeoutStats(statusArg.capture());
        MeasurementWipeoutStats measurementWipeoutStats = statusArg.getValue();
        assertEquals("", measurementWipeoutStats.getSourceRegistrant());
        assertEquals(
                WipeoutStatus.WipeoutType.ROLLBACK_WIPEOUT_CAUSE.getValue(),
                measurementWipeoutStats.getWipeoutType());
    }

    @Test
    public void testRegisterSources_success() {
        final int result =
                mMeasurementImpl.registerSources(
                        createSourceRegistrationRequest(), System.currentTimeMillis());
        assertEquals(STATUS_SUCCESS, result);
    }

    @Test
    public void testRegisterSources_ioError() {
        doReturn(false).when(mDatastoreManager).runInTransaction(any());
        final int result =
                mMeasurementImpl.registerSources(
                        createSourceRegistrationRequest(), System.currentTimeMillis());
        assertEquals(STATUS_IO_ERROR, result);
    }

    private void disableRollbackDeletion() {
        final Flags mockFlags = Mockito.mock(Flags.class);
        ExtendedMockito.doReturn(true).when(mockFlags).getMeasurementRollbackDeletionKillSwitch();
        ExtendedMockito.doReturn(mockFlags).when(FlagsFactory::getFlags);
    }

    @NonNull
    private MeasurementImpl createMeasurementImpl() {
        return new MeasurementImpl(
                DEFAULT_CONTEXT,
                mDatastoreManager,
                mClickVerifier,
                mMeasurementDataDeleter,
                mContentResolver);
    }
}
