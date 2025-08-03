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

package android.grammaticalinflection.cts;

import static com.android.os.grammaticalinflection.GrammaticalInflectionExtensionAtoms.APPLICATION_GRAMMATICAL_INFLECTION_CHANGED_FIELD_NUMBER;

import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.os.grammaticalinflection.ApplicationGrammaticalInflectionChanged;
import com.android.os.grammaticalinflection.GrammaticalInflectionExtensionAtoms;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import com.google.protobuf.ExtensionRegistry;

import java.util.List;

public class GrammaticalGenderAtomTests extends DeviceTestCase implements IBuildReceiver {

    public static final int GRAMMATICAL_GENDER_NOT_SPECIFIED = 0;
    public static final int GRAMMATICAL_GENDER_NEUTRAL = 1;
    public static final int GRAMMATICAL_GENDER_FEMININE = 2;
    public static final int GRAMMATICAL_GENDER_MASCULINE = 3;
    public static final String SETTING_GRAMMATICAL_GENDER_ACTIVITY =
            "SettingGrammaticalGenderActivity";
    private static final String INSTALLED_PACKAGE_NAME_APP =
            "android.grammaticalinflection.atom.app";
    private final static String GENDER_KEY = "gender";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                APPLICATION_GRAMMATICAL_INFLECTION_CHANGED_FIELD_NUMBER);
    }

    @Override
    protected void tearDown() throws Exception {
        resetAppGender();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
    }

    public void testAtomLogging_genderSpecified_logsAtomSuccessfully() throws Exception {
        // executing API to change gender of the installed application, this should trigger an
        // ApplicationGrammaticalInflectionChanged atom entry to be logged.
        executeSetApplicationGrammaticalGenderCommand(GRAMMATICAL_GENDER_MASCULINE);

        // Retrieving logged metric entries and asserting if they are as expected.
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        GrammaticalInflectionExtensionAtoms.registerAllExtensions(registry);
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(),
                registry);
        assertEquals(1, data.size());
        ApplicationGrammaticalInflectionChanged result = data.get(0).getAtom().getExtension(
                GrammaticalInflectionExtensionAtoms.applicationGrammaticalInflectionChanged);
        verifyAtomDetails(result,
                ApplicationGrammaticalInflectionChanged.SourceId.OTHERS,
                true);
    }

    public void testAtomLogging_genderNotSpecified_logsAtomSuccessfully() throws Exception {
        // executing API to change gender of the installed application, this should trigger an
        // ApplicationGrammaticalInflectionChanged atom entry to be logged.
        executeSetApplicationGrammaticalGenderCommand(GRAMMATICAL_GENDER_NOT_SPECIFIED);

        // Retrieving logged metric entries and asserting if they are as expected.
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        GrammaticalInflectionExtensionAtoms.registerAllExtensions(registry);
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(),
                registry);
        assertEquals(1, data.size());
        ApplicationGrammaticalInflectionChanged result = data.get(0).getAtom().getExtension(
                GrammaticalInflectionExtensionAtoms.applicationGrammaticalInflectionChanged);
        verifyAtomDetails(result,
                ApplicationGrammaticalInflectionChanged.SourceId.OTHERS,
                false);
    }

    private void verifyAtomDetails(ApplicationGrammaticalInflectionChanged result,
            ApplicationGrammaticalInflectionChanged.SourceId sourceId,
            boolean isGrammaticalGenderSpecified) {
        assertEquals(sourceId, result.getSourceId());
        assertEquals(isGrammaticalGenderSpecified, result.getIsGrammaticalGenderSpecified());
    }

    private void resetAppGender() throws Exception {
        executeSetApplicationGrammaticalGenderCommand(GRAMMATICAL_GENDER_NOT_SPECIFIED);
    }

    private void executeSetApplicationGrammaticalGenderCommand(int gender) throws Exception {
        String activity = INSTALLED_PACKAGE_NAME_APP + "/." + SETTING_GRAMMATICAL_GENDER_ACTIVITY;
        getDevice().executeShellCommand(
                String.format("am start -W -n %s --ei %s %d", activity, GENDER_KEY, gender));
    }
}
