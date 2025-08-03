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

package android.adservices.test.scenario.adservices.fledge;

import static org.junit.Assert.assertEquals;

import android.adservices.adselection.ReportEventRequest;
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.customaudience.CustomAudience;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.Assert;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;

import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

// TODO(b/295089418): Add test for server auction.
public class FledgeAppNotAllowedMemoryTest extends AbstractPerfTest {

    @Test
    public void ca_testNoUserConsent() throws Throwable {
        ShellUtils.runShellCommand("setprop debug.adservices.consent_manager_debug_mode false");
        tryToJoinAndLeaveCA(null);
        ShellUtils.runShellCommand("setprop debug.adservices.consent_manager_debug_mode true");
    }

    @Test
    public void ca_testAppNotAllowed() throws Throwable {
        ShellUtils.runShellCommand("device_config put adservices ppapi_app_allow_list \"\"");
        tryToJoinAndLeaveCA(SecurityException.class);
        ShellUtils.runShellCommand("device_config delete adservices ppapi_app_allow_list");
    }

    @Test
    public void adSelection_testNoUserConsent() throws Throwable {
        ShellUtils.runShellCommand("setprop debug.adservices.consent_manager_debug_mode false");
        tryToRunAdSelectionAndReporting(null);
        ShellUtils.runShellCommand("setprop debug.adservices.consent_manager_debug_mode true");
    }

    @Test
    public void adSelection_testAppNotAllowed() throws Throwable {
        ShellUtils.runShellCommand("device_config put adservices ppapi_app_allow_list \"\"");
        tryToRunAdSelectionAndReporting(SecurityException.class);
        ShellUtils.runShellCommand("device_config delete adservices ppapi_app_allow_list");
    }

    private void tryToRunAdSelectionAndReporting(Class<? extends Throwable> throwable)
            throws Throwable {
        assertThrowIfPresent(
                () ->
                        mAdSelectionClient
                                .selectAds(createAdSelectionConfig())
                                .get(5, TimeUnit.SECONDS),
                throwable);
        assertThrowIfPresent(
                () ->
                        mAdSelectionClient
                                .reportImpression(
                                        new ReportImpressionRequest(20L, createAdSelectionConfig()))
                                .get(5, TimeUnit.SECONDS),
                throwable);
        assertThrowIfPresent(
                () ->
                        mAdSelectionClient
                                .reportEvent(
                                        new ReportEventRequest.Builder(20L, "KEY", "None", 1)
                                                .build())
                                .get(5, TimeUnit.SECONDS),
                throwable);
    }

    private void tryToJoinAndLeaveCA(Class<? extends Throwable> throwable) throws Throwable {
        CustomAudience ca =
                createCustomAudience(
                        BUYER_1, CUSTOM_AUDIENCE_SHOES, Collections.singletonList(1.0));
        assertThrowIfPresent(
                () -> mCustomAudienceClient.joinCustomAudience(ca).get(5, TimeUnit.SECONDS),
                throwable);
        assertThrowIfPresent(
                () ->
                        mCustomAudienceClient
                                .leaveCustomAudience(ca.getBuyer(), ca.getName())
                                .get(5, TimeUnit.SECONDS),
                throwable);
    }

    private void assertThrowIfPresent(
            ThrowingRunnable runnable, Class<? extends Throwable> throwable) throws Throwable {
        if (throwable == null) {
            runnable.run();
        } else {
            Exception e = Assert.assertThrows(ExecutionException.class, runnable);
            assertEquals(
                    e.getCause().toString(),
                    throwable.getCanonicalName(),
                    e.getCause().getClass().getCanonicalName());
        }
    }
}
