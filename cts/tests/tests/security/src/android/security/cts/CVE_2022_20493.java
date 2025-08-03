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

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeNoException;

import android.net.Uri;
import android.os.Parcel;
import android.platform.test.annotations.AsbSecurityTest;
import android.service.notification.Condition;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import java.lang.reflect.Field;
import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;

/*
 * This CTS test has been created taking reference from the tests present in
 * frameworks/base/core/tests/coretests/src/android/service/notification/ConditionTest.java
 */

@RunWith(AndroidJUnit4.class)
public class CVE_2022_20493 extends StsExtraBusinessLogicTestCase {
    private static final int INPUT_STRING_LENGTH = 2000;
    private String mLongString;
    private String mValidString;
    private Uri mLongUri;
    private Uri mValidUri;

    private boolean checkFields(Condition condition, boolean checkLine) {
        // Check all fields
        boolean status = (mLongUri.toString().length() <= condition.id.toString().length())
                || (INPUT_STRING_LENGTH <= condition.summary.length());
        if (checkLine) {
            status = status || (INPUT_STRING_LENGTH <= condition.line1.length())
                    || (INPUT_STRING_LENGTH <= condition.line2.length());
        }
        return status;
    }

    private boolean testLongFieldsInConstructors() {
        // Confirm strings are truncated via short constructor
        Condition firstCondition = new Condition(mLongUri, mLongString, Condition.STATE_TRUE);

        // Confirm strings are truncated via long constructor
        Condition secondCondition = new Condition(mLongUri, mLongString, mLongString, mLongString,
                -1, Condition.STATE_TRUE, Condition.FLAG_RELEVANT_ALWAYS);
        return checkFields(firstCondition, false) || checkFields(secondCondition, true);
    }

    private boolean setFieldsUsingReflection(boolean setLine)
            throws ClassNotFoundException, IllegalAccessException, NoSuchFieldException {
        // Set fields via reflection to force them to be long, then parcel and unparcel to make sure
        // it gets truncated upon unparcelling.
        Condition condition;
        if (setLine) {
            condition = new Condition(mValidUri, mValidString, mValidString, mValidString, -1,
                    Condition.STATE_TRUE, Condition.FLAG_RELEVANT_ALWAYS);
        } else {
            condition = new Condition(mValidUri, mValidString, Condition.STATE_TRUE);
        }

        Class conditionClass = Class.forName("android.service.notification.Condition");
        Field id = conditionClass.getDeclaredField("id");
        id.setAccessible(true);
        id.set(condition, mLongUri);
        Field summary = conditionClass.getDeclaredField("summary");
        summary.setAccessible(true);
        summary.set(condition, mLongString);
        if (setLine) {
            Field line1 = conditionClass.getDeclaredField("line1");
            line1.setAccessible(true);
            line1.set(condition, mLongString);
            Field line2 = conditionClass.getDeclaredField("line2");
            line2.setAccessible(true);
            line2.set(condition, mLongString);
        }

        Parcel parcel = Parcel.obtain();
        condition.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        Condition conditionFromParcel = new Condition(parcel);
        return checkFields(conditionFromParcel, setLine);
    }

    private boolean testLongFieldsFromParcel()
            throws ClassNotFoundException, IllegalAccessException, NoSuchFieldException {
        return setFieldsUsingReflection(true) || setFieldsUsingReflection(false);
    }

    /**
     * b/242846316
     * Vulnerable library : framework.jar
     * Vulnerable module  : Not applicable
     * Is Play managed    : No
     */
    @AsbSecurityTest(cveBugId = 242846316)
    @Test
    public void testPocCVE_2022_20493() {
        try {
            mLongString = String.join("", Collections.nCopies(INPUT_STRING_LENGTH, "A"));
            mLongUri = Uri.parse("condition://" + mLongString);
            mValidUri = Uri.parse("condition://android");
            mValidString = "placeholder";
            boolean firstResult = testLongFieldsInConstructors();
            boolean secondResult = testLongFieldsFromParcel();
            assertFalse("Device is vulnerable to b/242846316!", firstResult || secondResult);
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
