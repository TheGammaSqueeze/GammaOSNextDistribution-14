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

package com.android.adservices.service.adselection;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.Process;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.measurement.MeasurementImpl;
import com.android.adservices.service.stats.AdServicesLogger;

import com.google.common.util.concurrent.ListeningExecutorService;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EventReporterFactoryTest {
    @Mock private AdSelectionEntryDao mAdSelectionEntryDaoMock;
    @Mock private AdServicesHttpsClient mHttpsClientMock;
    private ListeningExecutorService mLightweightExecutorService =
            AdServicesExecutors.getLightWeightExecutor();
    private ListeningExecutorService mBackgroundExecutorService =
            AdServicesExecutors.getBackgroundExecutor();
    @Mock private AdServicesLogger mAdServicesLoggerMock;
    @Mock private FledgeAuthorizationFilter mFledgeAuthorizationFilterMock;
    @Mock private AdSelectionServiceFilter mAdSelectionServiceFilterMock;
    private static final int MY_UID = Process.myUid();
    @Mock private MeasurementImpl mMeasurementServiceMock;
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private Context mContextMock;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testFactory_registerAdBeaconDisabled_allDisabled() {
        EventReporter eventReporter =
                getEventReporter(
                        new EventReporterFactoryTestFlags(
                                /* registerAdBeaconEnabled = */ false,
                                /* reportAndRegisterEventApiEnabled = */ false,
                                /* reportAndRegisterEventApiFallbackEnabled = */ false));
        assertThat(eventReporter).isInstanceOf(ReportEventDisabledImpl.class);
    }

    @Test
    public void testFactory_registerAdBeaconDisabled_reportAndRegisterEnabled() {
        EventReporter eventReporter =
                getEventReporter(
                        new EventReporterFactoryTestFlags(
                                /* registerAdBeaconEnabled = */ false,
                                /* reportAndRegisterEventApiEnabled = */ true,
                                /* reportAndRegisterEventApiFallbackEnabled = */ false));
        assertThat(eventReporter).isInstanceOf(ReportEventDisabledImpl.class);
    }

    @Test
    public void testFactory_registerAdBeaconDisabled_reportAndRegisterFallbackEnabled() {
        EventReporter eventReporter =
                getEventReporter(
                        new EventReporterFactoryTestFlags(
                                /* registerAdBeaconEnabled = */ false,
                                /* reportAndRegisterEventApiEnabled = */ true,
                                /* reportAndRegisterEventApiFallbackEnabled = */ true));
        assertThat(eventReporter).isInstanceOf(ReportEventDisabledImpl.class);
    }

    @Test
    public void testFactory_reportAndRegisterEventDisabled_registerAdBeaconEnabled() {
        EventReporter eventReporter =
                getEventReporter(
                        new EventReporterFactoryTestFlags(
                                /* registerAdBeaconEnabled = */ true,
                                /* reportAndRegisterEventApiEnabled = */ false,
                                /* reportAndRegisterEventApiFallbackEnabled = */ false));
        assertThat(eventReporter).isInstanceOf(ReportEventImpl.class);
    }

    @Test
    public void testFactory_reportAndRegisterFallbackDisabled_reportAndRegisterEventEnabled() {
        EventReporter eventReporter =
                getEventReporter(
                        new EventReporterFactoryTestFlags(
                                /* registerAdBeaconEnabled = */ true,
                                /* reportAndRegisterEventApiEnabled = */ true,
                                /* reportAndRegisterEventApiFallbackEnabled = */ false));
        assertThat(eventReporter).isInstanceOf(ReportAndRegisterEventImpl.class);
    }

    @Test
    public void testFactory_reportAndRegisterEventApiFallbackEnabled() {
        EventReporter eventReporter =
                getEventReporter(
                        new EventReporterFactoryTestFlags(
                                /* registerAdBeaconEnabled = */ true,
                                /* reportAndRegisterEventApiEnabled = */ true,
                                /* reportAndRegisterEventApiFallbackEnabled = */ true));
        assertThat(eventReporter).isInstanceOf(ReportAndRegisterEventFallbackImpl.class);
    }

    private EventReporter getEventReporter(Flags flags) {
        return new EventReporterFactory(
                        mAdSelectionEntryDaoMock,
                        mHttpsClientMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mAdServicesLoggerMock,
                        flags,
                        mAdSelectionServiceFilterMock,
                        MY_UID,
                        mFledgeAuthorizationFilterMock,
                        DevContext.createForDevOptionsDisabled(),
                        mMeasurementServiceMock,
                        mConsentManagerMock,
                        mContextMock,
                        false)
                .getEventReporter();
    }

    private static class EventReporterFactoryTestFlags implements Flags {
        private final boolean mRegisterAdBeaconEnabled;
        private final boolean mReportAndRegisterEventApiEnabled;
        private final boolean mReportAndRegisterEventApiFallbackEnabled;

        EventReporterFactoryTestFlags(
                boolean registerAdBeaconEnabled,
                boolean reportAndRegisterEventApiEnabled,
                boolean reportAndRegisterEventApiFallbackEnabled) {
            mRegisterAdBeaconEnabled = registerAdBeaconEnabled;
            mReportAndRegisterEventApiEnabled = reportAndRegisterEventApiEnabled;
            mReportAndRegisterEventApiFallbackEnabled = reportAndRegisterEventApiFallbackEnabled;
        }

        @Override
        public boolean getFledgeRegisterAdBeaconEnabled() {
            return mRegisterAdBeaconEnabled;
        }

        @Override
        public boolean getFledgeMeasurementReportAndRegisterEventApiEnabled() {
            return mReportAndRegisterEventApiEnabled;
        }

        @Override
        public boolean getFledgeMeasurementReportAndRegisterEventApiFallbackEnabled() {
            return mReportAndRegisterEventApiFallbackEnabled;
        }
    }
}
