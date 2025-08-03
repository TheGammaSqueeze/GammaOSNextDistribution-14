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

package android.security.cts.AutomaticZenRuleTests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.app.AutomaticZenRule;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.platform.test.annotations.AsbSecurityTest;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public class AutomaticZenRuleTests extends StsExtraBusinessLogicTestCase {
    private static final String URI_STRING = "condition://android";
    private static final String ZEN_RULE_NAME = "ZenRuleName";
    private boolean mNotificationPolicyAccessGranted = false;
    private NotificationManager mNotificationManager = null;
    private String mPackageName = null;
    private UiAutomation mUiautomation = null;

    @Before
    public void setUp() {
        try {
            final int timeoutDuration = 5000;
            final int waitDuration = 100;
            Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
            Context context = instrumentation.getContext();
            mNotificationManager = context.getSystemService(NotificationManager.class);
            mPackageName = context.getPackageName();
            mUiautomation = instrumentation.getUiAutomation();
            mUiautomation.executeShellCommand("cmd notification allow_dnd " + mPackageName);
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < timeoutDuration) {
                // Busy wait until notification policy access is granted
                if (mNotificationManager.isNotificationPolicyAccessGranted()) {
                    mNotificationPolicyAccessGranted = true;
                    break;
                }
                Thread.sleep(waitDuration);
            }
            assumeTrue("Notification policy access not granted", mNotificationPolicyAccessGranted);
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    @After
    public void tearDown() {
        try {
            mUiautomation.executeShellCommand("cmd notification disallow_dnd " + mPackageName);
        } catch (Exception ignoredException) {
            // Ignoring exceptions
        }
    }

    private boolean testUsingAppComponents(ComponentName owner, ComponentName components[]) {
        int automaticZenRules = 0;
        boolean isVulnerable = true;
        final int ruleLimitPerPackage = (components != null && components.length > 1) ? 60 : 200;
        ArrayList<String> ruleIds = new ArrayList<>();
        try {
            // Storing the number of automaticZenRules present before test run
            automaticZenRules = mNotificationManager.getAutomaticZenRules().size();

            for (int i = 0; i < ruleLimitPerPackage; ++i) {
                if (components != null) {
                    for (int j = 0; j < components.length; ++j) {
                        int ruleNo = i * components.length + j;
                        Uri conditionId = Uri.parse(URI_STRING + ruleNo);
                        AutomaticZenRule rule = new AutomaticZenRule(ZEN_RULE_NAME + ruleNo, owner,
                                components[j], conditionId, null,
                                NotificationManager.INTERRUPTION_FILTER_ALL, true);
                        String id = mNotificationManager.addAutomaticZenRule(rule);
                        ruleIds.add(id);
                    }
                } else {
                    Uri conditionId = Uri.parse(URI_STRING + i);
                    AutomaticZenRule rule = new AutomaticZenRule(ZEN_RULE_NAME + i, owner, null,
                            conditionId, null, NotificationManager.INTERRUPTION_FILTER_ALL, true);
                    String id = mNotificationManager.addAutomaticZenRule(rule);
                    ruleIds.add(id);
                }
            }
        } catch (Exception e) {
            isVulnerable = false;
            if (!(e instanceof IllegalArgumentException)) {
                assumeNoException(e);
            }
        } finally {
            try {
                if (mNotificationPolicyAccessGranted) {
                    // Retrieving the total number of automaticZenRules added by test so that the
                    // test fails only if all automaticZenRules were added successfully
                    automaticZenRules =
                            mNotificationManager.getAutomaticZenRules().size() - automaticZenRules;
                    for (String id : ruleIds) {
                        mNotificationManager.removeAutomaticZenRule(id);
                    }
                }
                int ruleLimitPerPackageTotal =
                        components != null ? ruleLimitPerPackage * components.length
                                : ruleLimitPerPackage;
                boolean allZenRulesAdded = ruleLimitPerPackageTotal == automaticZenRules;
                isVulnerable = (isVulnerable && allZenRulesAdded);
            } catch (Exception ignoredException) {
                // Ignoring exceptions
            }
        }
        return isVulnerable;
    }

    // b/220735360
    // Vulnerable library : framework.jar
    // Vulnerable module : Not applicable
    // Is Play managed : No
    @AsbSecurityTest(cveBugId = 220735360)
    @Test
    public void testPocCVE_2022_20143() {
        try {
            ComponentName appComponent =
                    new ComponentName(mPackageName, PocActivity.class.getCanonicalName());
            ComponentName[] components = {appComponent};
            boolean testUsingAppSingleComponentStatus = testUsingAppComponents(null, components);
            assertFalse("Device is vulnerable to b/220735360!! System can be corrupted by adding"
                    + " many automaticZenRules via NotificationManager#addAutomaticZenRule."
                    + " Note: Device also seems to be vulnerable to b/235823407 and"
                    + " b/242537431", testUsingAppSingleComponentStatus);
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    // b/235823407
    // Vulnerable library : framework.jar
    // Vulnerable module : Not applicable
    // Is Play managed : No
    @AsbSecurityTest(cveBugId = 235823407)
    @Test
    public void testPocCVE_2022_20425() {
        try {
            ComponentName firstConfigurationActivity =
                    new ComponentName(mPackageName, PocActivity.class.getCanonicalName());
            ComponentName secondConfigurationActivity =
                    new ComponentName(mPackageName, SecondPocActivity.class.getCanonicalName());
            ComponentName[] components = {firstConfigurationActivity, secondConfigurationActivity};
            boolean testUsingAppMultipleComponentStatus = testUsingAppComponents(null, components);
            assertFalse(
                    "Device is vulnerable to b/235823407!! Bypass fix of CVE-2022-20143:"
                            + " Bypass zen rule limit with different configuration Activity"
                            + " Note: Device also seems to be vulnerable to b/242537431",
                    testUsingAppMultipleComponentStatus);
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    // b/242537431
    // Vulnerable library : framework.jar
    // Vulnerable module : Not applicable
    // Is Play managed : No
    @AsbSecurityTest(cveBugId = 242537431)
    @Test
    public void testPocCVE_2022_20455() {
        try {
            ComponentName systemComponent =
                    new ComponentName("android", "ScheduleConditionProvider");
            boolean testUsingSystemComponentStatus = testUsingAppComponents(systemComponent, null);
            assertFalse(
                    "Device is vulnerable to b/242537431!! System can be corrupted by adding"
                            + " many automaticZenRules via NotificationManager#addAutomaticZenRule",
                    testUsingSystemComponentStatus);
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
