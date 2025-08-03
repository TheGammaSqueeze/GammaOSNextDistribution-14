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

package android.grammaticalinflection.cts;

import static android.content.res.Configuration.GRAMMATICAL_GENDER_NOT_SPECIFIED;

import static com.google.common.truth.Truth.assertThat;

import android.app.Flags;
import android.app.GrammaticalInflectionManager;
import android.content.ComponentName;
import android.content.res.Configuration;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.TestJournalProvider;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class GrammaticalInflectionManagerTest extends ActivityManagerTestBase {
    private static final String CMD_GET_GRAMMATICAL_GENDER =
            "cmd grammatical_inflection get-system-grammatical-gender";
    private static final String CMD_SET_GRAMMATICAL_GENDER_ENABLED =
            "cmd grammatical_inflection set-system-grammatical-gender";

    private final ComponentName TEST_APP_MAIN_ACTIVITY = new ComponentName(
            TestMainActivity.class.getPackageName(),
            TestMainActivity.class.getCanonicalName());
    private final ComponentName TEST_APP_HANDLE_CONFIG_CHANGE = new ComponentName(
            TestHandleConfigChangeActivity.class.getPackageName(),
            TestHandleConfigChangeActivity.class.getCanonicalName());

    private String mOriginalGrammaticalGender;
    private GrammaticalInflectionManager mGrammaticalInflectionManager;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        super.setUp();

        mGrammaticalInflectionManager = mContext.getSystemService(
                GrammaticalInflectionManager.class);
    }

    @After
    public void tearDown() throws Exception {
        if (mOriginalGrammaticalGender == null) {
            // clear gender value and wait the configuration change
            TestJournalProvider.TestJournalContainer.start();
            launchActivity(TEST_APP_MAIN_ACTIVITY);
            mGrammaticalInflectionManager.setRequestedApplicationGrammaticalGender(
                    GRAMMATICAL_GENDER_NOT_SPECIFIED);
            assertActivityLifecycle(TEST_APP_MAIN_ACTIVITY, true /* relaunch */);
        } else {
            // Reset grammatical gender and flag.
            Pattern pattern = Pattern.compile("\\((\\d+)\\)");
            Matcher matcher = pattern.matcher(mOriginalGrammaticalGender);
            if (matcher.find()) {
                mOriginalGrammaticalGender = matcher.group(1);
            }
            setGrammaticalGender(mOriginalGrammaticalGender);
        }
    }

    @Test
    public void testSetApplicationGender_setFeminine_returnFeminineAfterReCreating() {
        TestJournalProvider.TestJournalContainer.start();
        launchActivity(TEST_APP_MAIN_ACTIVITY);
        mWmState.assertVisibility(TEST_APP_MAIN_ACTIVITY, /* visible*/ true);

        mGrammaticalInflectionManager.setRequestedApplicationGrammaticalGender(
                Configuration.GRAMMATICAL_GENDER_FEMININE);

        assertActivityLifecycle(TEST_APP_MAIN_ACTIVITY, true /* relaunch */);
        assertThat(mGrammaticalInflectionManager.getApplicationGrammaticalGender())
                .isEqualTo(Configuration.GRAMMATICAL_GENDER_FEMININE);
    }

    @Test
    public void testSetApplicationGender_setMasculine_returnMasculineAfterReCreating() {
        TestJournalProvider.TestJournalContainer.start();
        launchActivity(TEST_APP_MAIN_ACTIVITY);
        mWmState.assertVisibility(TEST_APP_MAIN_ACTIVITY, /* visible*/ true);

        mGrammaticalInflectionManager.setRequestedApplicationGrammaticalGender(
                Configuration.GRAMMATICAL_GENDER_MASCULINE);

        assertActivityLifecycle(TEST_APP_MAIN_ACTIVITY, true /* relaunch */);
        assertThat(mGrammaticalInflectionManager.getApplicationGrammaticalGender())
                .isEqualTo(Configuration.GRAMMATICAL_GENDER_MASCULINE);
    }

    @Test
    public void testSetApplicationGender_setMasculine_returnMasculineWithoutReCreating() {
        launchActivity(TEST_APP_HANDLE_CONFIG_CHANGE);
        TestJournalProvider.TestJournalContainer.start();
        mWmState.assertVisibility(TEST_APP_HANDLE_CONFIG_CHANGE, /* visible*/ true);

        mGrammaticalInflectionManager.setRequestedApplicationGrammaticalGender(
                Configuration.GRAMMATICAL_GENDER_MASCULINE);

        assertActivityLifecycle(TEST_APP_HANDLE_CONFIG_CHANGE, false /* relaunch */);
        assertThat(mGrammaticalInflectionManager.getApplicationGrammaticalGender())
                .isEqualTo(Configuration.GRAMMATICAL_GENDER_MASCULINE);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SYSTEM_TERMS_OF_ADDRESS_ENABLED)
    public void testGetSystemGrammaticalGender_setMasculineForSysApp_returnMasculineToSysApp() {
        mOriginalGrammaticalGender = SystemUtil.runShellCommand(String.format(
                CMD_GET_GRAMMATICAL_GENDER + " --user %d ", mContext.getUserId()));

        setGrammaticalGender(String.valueOf(Configuration.GRAMMATICAL_GENDER_MASCULINE));

        String value = SystemUtil.runShellCommand(String.format(
                CMD_GET_GRAMMATICAL_GENDER + " --user %d ", mContext.getUserId()));
        Pattern pattern = Pattern.compile("\\((\\d+)\\)");
        Matcher matcher = pattern.matcher(value);
        if (matcher.find()) {
            value = matcher.group(1);
        }
        assertThat(Integer.parseInt(value)).isEqualTo(Configuration.GRAMMATICAL_GENDER_MASCULINE);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SYSTEM_TERMS_OF_ADDRESS_ENABLED)
    public void testGetSystemGrammaticalGender_setNeutralForSysApp_returnNotSpecifiedTo3rdApp() {
        mOriginalGrammaticalGender = SystemUtil.runShellCommand(String.format(
                CMD_GET_GRAMMATICAL_GENDER + " --user %d ", mContext.getUserId()));

        setGrammaticalGender(String.valueOf(Configuration.GRAMMATICAL_GENDER_NEUTRAL));

        assertThat(mGrammaticalInflectionManager.getSystemGrammaticalGender()).isEqualTo(
                Configuration.GRAMMATICAL_GENDER_NOT_SPECIFIED);
    }

    private void setGrammaticalGender(String grammaticalGender) {
        SystemUtil.runShellCommand(String.format(
                CMD_SET_GRAMMATICAL_GENDER_ENABLED + " --user %d "
                        + "--grammaticalGender %s",
                mContext.getUserId(),
                grammaticalGender
        ));
    }
}
