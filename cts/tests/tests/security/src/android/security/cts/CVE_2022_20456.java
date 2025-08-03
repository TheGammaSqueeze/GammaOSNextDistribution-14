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

package android.security.cts;

import static android.app.NotificationManager.INTERRUPTION_FILTER_UNKNOWN;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;

import android.app.AutomaticZenRule;
import android.content.ComponentName;
import android.net.Uri;
import android.os.Parcel;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


// This CTS test has been created taking reference from the tests present in
// frameworks/base/core/tests/coretests/src/android/app/AutomaticZenRuleTest.java
@RunWith(AndroidJUnit4.class)
public class CVE_2022_20456 extends StsExtraBusinessLogicTestCase {
    private static final int INPUT_STRING_LENGTH = 2000; // 2 * 'MAX_STRING_LENGTH'
    private static final String CLASS_NAME = "className";
    private static final String PACKAGE_NAME = "packageName";
    private static final String URI_STRING = "condition://android";
    private static final String ZEN_RULE_NAME = "ZenRuleName";
    private ComponentName mComponentNameWithLongFields;
    private ComponentName mValidComponentName;
    private String mLongString;
    private Uri mLongUri;
    private Uri mValidUri;
    private List<String> mViolations;

    private void checkFields(AutomaticZenRule rule, boolean ownerFlag, boolean configActivityFlag,
            String tag) {
        // Check all fields
        if (INPUT_STRING_LENGTH <= rule.getName().length()) {
            mViolations.add(tag + "input string length <= rule name length");
        }
        if (mLongUri.toString().length() <= rule.getConditionId().toString().length()) {
            mViolations.add(tag + "input uri length <= rule conditionId length");
        }
        if (ownerFlag) {
            if (INPUT_STRING_LENGTH <= rule.getOwner().getPackageName().length()) {
                mViolations.add(tag + "input string length <= rule owner package name length");
            }
            if (INPUT_STRING_LENGTH <= rule.getOwner().getClassName().length()) {
                mViolations.add(tag + "input string length <= rule owner class name length");
            }
        }
        if (configActivityFlag) {
            if (INPUT_STRING_LENGTH <= rule.getConfigurationActivity().getPackageName().length()) {
                mViolations.add(tag
                        + "input string length <= rule configurationActivity package name length");
            }
            if (INPUT_STRING_LENGTH <= rule.getConfigurationActivity().getClassName().length()) {
                mViolations.add(tag
                        + "input string length <= rule configurationActivity class name length");
            }
        }
    }

    private void checkConstructor(boolean ownerFlag, boolean configActivityFlag) {
        ComponentName owner = ownerFlag ? mComponentNameWithLongFields : null;
        ComponentName configActivity = configActivityFlag ? mComponentNameWithLongFields : null;
        AutomaticZenRule rule = new AutomaticZenRule(mLongString, owner, configActivity, mLongUri,
                null, INTERRUPTION_FILTER_UNKNOWN, /* enabled */ true);
        checkFields(rule, ownerFlag, configActivityFlag, "\ncheckConstructor (owner=" + ownerFlag
                + ", configActivity=" + configActivityFlag + "): ");
    }

    private void testIsConstructorVulnerable() {
        // Check all three variants i.e. with owner, with configuration activity and with both
        // owner and configuration activity. Although third case is mostly redundant, adding it to
        // complete checks on all possible variants.
        checkConstructor(/* ownerFlag */ true, /* configActivityFlag */ false);
        checkConstructor(/* ownerFlag */ false, /* configActivityFlag */ true);
        checkConstructor(/* ownerFlag */ true, /* configActivityFlag */ true);
    }

    private void checkFieldSetters(boolean ownerFlag, boolean configActivityFlag) {
        ComponentName owner = ownerFlag ? mValidComponentName : null;
        ComponentName configActivity = configActivityFlag ? mValidComponentName : null;
        AutomaticZenRule rule = new AutomaticZenRule(ZEN_RULE_NAME, owner, configActivity,
                mValidUri, null, INTERRUPTION_FILTER_UNKNOWN, /* enabled */ true);

        // Check all fields that can be set via setter methods of AutomaticZenRule class
        rule.setName(mLongString);
        rule.setConditionId(mLongUri);
        rule.setConfigurationActivity(mComponentNameWithLongFields);
        checkFields(rule, /* ownerFlag */ false, /* configActivityFlag */ true,
                "\ncheckFieldSetters (owner=" + ownerFlag + ", configActivity=" + configActivityFlag
                        + "): ");
    }

    private void testIsFieldSetterVulnerable() {
        checkFieldSetters(/* ownerFlag */ true, /* configActivityFlag */ false);
        checkFieldSetters(/* ownerFlag */ false, /* configActivityFlag */ true);
        checkFieldSetters(/* ownerFlag */ true, /* configActivityFlag */ true);
    }

    private void checkParcelInput(boolean ownerFlag, boolean configActivityFlag)
            throws ClassNotFoundException, IllegalAccessException, NoSuchFieldException {
        ComponentName owner = ownerFlag ? mValidComponentName : null;
        ComponentName configActivity = configActivityFlag ? mValidComponentName : null;
        AutomaticZenRule rule = new AutomaticZenRule(ZEN_RULE_NAME, owner, configActivity,
                mValidUri, null, INTERRUPTION_FILTER_UNKNOWN, /* enabled */ true);

        // Create rules with long fields set directly via reflection so that we can confirm that a
        // rule with too-long fields that comes in via a parcel has its fields truncated directly.
        Class automaticZenRuleClass = Class.forName("android.app.AutomaticZenRule");
        Field fieldName = automaticZenRuleClass.getDeclaredField("name");
        fieldName.setAccessible(/* flag */ true);
        fieldName.set(rule, mLongString);
        Field fieldConditionId = automaticZenRuleClass.getDeclaredField("conditionId");
        fieldConditionId.setAccessible(/* flag */ true);
        fieldConditionId.set(rule, mLongUri);
        if (ownerFlag) {
            Field fieldOwner = automaticZenRuleClass.getDeclaredField("owner");
            fieldOwner.setAccessible(/* flag */ true);
            fieldOwner.set(rule, mComponentNameWithLongFields);
        }
        if (configActivityFlag) {
            Field fieldConfigActivity =
                    automaticZenRuleClass.getDeclaredField("configurationActivity");
            fieldConfigActivity.setAccessible(/* flag */ true);
            fieldConfigActivity.set(rule, mComponentNameWithLongFields);
        }

        // Write AutomaticZenRule object to parcel
        Parcel parcel = Parcel.obtain();
        rule.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        // Instantiate AutomaticZenRule object using parcel
        AutomaticZenRule ruleFromParcel = new AutomaticZenRule(parcel);

        checkFields(ruleFromParcel, ownerFlag, configActivityFlag, "\ncheckParcelInput (owner="
                + ownerFlag + ", configActivity=" + configActivityFlag + "): ");
    }

    private void testIsInputFromParcelVulnerable()
            throws ClassNotFoundException, IllegalAccessException, NoSuchFieldException {
        checkParcelInput(/* ownerFlag */ true, /* configActivityFlag */ false);
        checkParcelInput(/* ownerFlag */ false, /* configActivityFlag */ true);
        checkParcelInput(/* ownerFlag */ true, /* configActivityFlag */ true);
    }

    // b/242703460, b/242703505, b/242703780, b/242704043, b/243794204
    // Vulnerable library : framework.jar
    // Vulnerable module  : Not applicable
    // Is Play managed    : No
    @AsbSecurityTest(cveBugId = {242703460, 242703505, 242703780, 242704043, 243794204})
    @Test
    public void testPocCVE_2022_20456() {
        try {
            mLongString = String.join("", Collections.nCopies(INPUT_STRING_LENGTH, "A"));
            mComponentNameWithLongFields = new ComponentName(mLongString, mLongString);
            mValidComponentName = new ComponentName(PACKAGE_NAME, CLASS_NAME);
            mLongUri = Uri.parse("condition://" + mLongString);
            mValidUri = Uri.parse(URI_STRING);
            mViolations = new ArrayList<String>();

            // Check AutomaticZenRule constructor
            testIsConstructorVulnerable();

            // Check AutomaticZenRule field setters
            testIsFieldSetterVulnerable();

            // Check AutomaticZenRule constructor using parcel input
            testIsInputFromParcelVulnerable();

            assertTrue("Device is vulnerable to at least one of the following vulnerabilities : "
                    + "b/242703460(CVE-2022-20489), b/242703505(CVE-2022-20490), b/242703780"
                    + "(CVE-2022-20456), b/242704043(CVE-2022-20492), b/243794204(CVE-2022-20494)"
                    + " due to these violations where input string length=" + INPUT_STRING_LENGTH
                    + " and input uri length=" + mLongUri.toString().length() + ":" + mViolations,
                    mViolations.isEmpty());
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
