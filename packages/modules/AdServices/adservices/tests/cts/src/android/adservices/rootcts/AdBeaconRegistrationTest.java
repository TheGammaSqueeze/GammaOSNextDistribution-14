/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.adservices.rootcts;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.utils.FledgeScenarioTest;
import android.adservices.utils.ScenarioDispatcher;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.Before;
import org.junit.Test;

public class AdBeaconRegistrationTest extends FledgeScenarioTest {

    private BackgroundJobHelper mBackgroundJobHelper;
    private ScenarioDispatcher mScenarioDispatcher;

    @Before
    public void setup() {
        mBackgroundJobHelper = new BackgroundJobHelper(sContext);
    }

    @Test
    public void testAdBeaconRegistration_BeaconCannotRegister_reportEventNotHappen()
            throws Exception {
        setRegisterAdBeaconEnabled(false);
        setMeasurementReportAndRegisterEventApiEnabled(false);
        setMeasurementReportAndRegisterEventApiFallbackEnabled(false);

        runAdSelectionAndReporting("scenarios/remarketing-cuj-187.json", false);
    }

    @Test
    public void testAdBeaconRegistration_RegisteringEventDisabled_eventShouldOnlyBeReported()
            throws Exception {
        setRegisterAdBeaconEnabled(true);
        setMeasurementReportAndRegisterEventApiEnabled(false);
        setMeasurementReportAndRegisterEventApiFallbackEnabled(false);

        runAdSelectionAndReporting("scenarios/remarketing-cuj-188.json", false);
    }

    @Test
    public void
            testAdBeaconRegistration_RegisteringEventEnabled_eventShouldBeReportedInCombinedCall()
                    throws Exception {
        setRegisterAdBeaconEnabled(true);
        setMeasurementReportAndRegisterEventApiEnabled(false);
        setMeasurementReportAndRegisterEventApiFallbackEnabled(false);

        runAdSelectionAndReporting("scenarios/remarketing-cuj-188.json", true);
    }

    @Test
    public void testAdBeaconRegistration_useBothMeasurementAndEventReporting() throws Exception {
        setRegisterAdBeaconEnabled(true);
        setMeasurementReportAndRegisterEventApiEnabled(true);
        setMeasurementReportAndRegisterEventApiFallbackEnabled(true);

        runAdSelectionAndReporting("scenarios/remarketing-cuj-188.json", true);
    }

    private void runAdSelectionAndReporting(String scenarioJson, boolean shouldRunBackgroundJob)
            throws Exception {
        mScenarioDispatcher = ScenarioDispatcher.fromScenario(scenarioJson, getCacheBusterPrefix());
        setupDefaultMockWebServer(mScenarioDispatcher);
        joinCustomAudience(SHOES_CA);
        AdSelectionOutcome outcome = doSelectAds(makeAdSelectionConfig());
        doReportImpression(outcome.getAdSelectionId(), makeAdSelectionConfig());
        try {
            doReportEvent(outcome.getAdSelectionId(), "view");
        } catch (Exception e) {
            // Pass through.
        }

        if (shouldRunBackgroundJob) {
            mBackgroundJobHelper.runJob(20);
        }

        assertThat(mScenarioDispatcher.getCalledPaths())
                .containsAtLeastElementsIn(mScenarioDispatcher.getVerifyCalledPaths());
        assertThat(mScenarioDispatcher.getCalledPaths())
                .containsNoneIn(mScenarioDispatcher.getVerifyNotCalledPaths());
    }

    protected static void setRegisterAdBeaconEnabled(boolean enabled) {
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_register_ad_beacon_enabled %s",
                enabled ? "true" : "false");
    }

    protected static void setMeasurementReportAndRegisterEventApiEnabled(boolean enabled) {
        ShellUtils.runShellCommand(
                "device_config put adservices"
                        + " fledge_measurement_report_and_register_event_api_enabled %s",
                enabled ? "true" : "false");
    }

    protected static void setMeasurementReportAndRegisterEventApiFallbackEnabled(boolean enabled) {
        ShellUtils.runShellCommand(
                "device_config put adservices"
                        + " fledge_measurement_report_and_register_event_api_fallback_enabled %s",
                enabled ? "true" : "false");
    }
}
