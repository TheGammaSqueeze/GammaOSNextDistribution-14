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

package android.appenumeration.cts;

import static android.appenumeration.cts.Constants.ACTION_GET_ENABLED_SPELL_CHECKER_INFOS;
import static android.appenumeration.cts.Constants.CTS_MOCK_SPELL_CHECKER_APK;
import static android.appenumeration.cts.Constants.MOCK_SPELL_CHECKER_PKG;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING;
import static android.appenumeration.cts.Constants.QUERIES_PACKAGE;
import static android.appenumeration.cts.Utils.installPackage;
import static android.appenumeration.cts.Utils.uninstallPackage;
import static android.content.Intent.EXTRA_RETURN_RESULT;

import android.os.Bundle;
import android.view.textservice.SpellCheckerInfo;
import android.view.textservice.TextServicesManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class TextServicesEnumerationTests extends AppEnumerationTestsBase {

    @BeforeClass
    public static void prepareSpellChecker() throws Exception {
        installPackage(CTS_MOCK_SPELL_CHECKER_APK);

        PollingCheck.check("Failed to wait for " + MOCK_SPELL_CHECKER_PKG
                + " getting ready", DEFAULT_TIMEOUT_MS,
                () -> hasSpellChecker(MOCK_SPELL_CHECKER_PKG));
    }

    @AfterClass
    public static void cleanUp() throws Exception {
        uninstallPackage(MOCK_SPELL_CHECKER_PKG);
    }

    @Test
    public void queriesNothing_getEnabledSpellCheckerInfos_cannotSeeMockSpellChecker()
            throws Exception {
        assertNotVisible(
                QUERIES_NOTHING, MOCK_SPELL_CHECKER_PKG, this::getEnabledSpellCheckerInfos);
    }

    @Test
    public void queriesPackage_getEnabledSpellCheckerInfos_canSeeMockSpellChecker()
            throws Exception {
        assertVisible(
                QUERIES_PACKAGE, MOCK_SPELL_CHECKER_PKG, this::getEnabledSpellCheckerInfos);
    }

    private String[] getEnabledSpellCheckerInfos(String sourcePackage)
            throws Exception {
        final Bundle response = sendCommandBlocking(sourcePackage, null /* targetPackageName */,
                null /* intentExtra */, ACTION_GET_ENABLED_SPELL_CHECKER_INFOS);
        final List<SpellCheckerInfo> infos =
                response.getParcelableArrayList(EXTRA_RETURN_RESULT, SpellCheckerInfo.class);
        return infos.stream()
                .map(info -> info.getPackageName())
                .distinct()
                .toArray(String[]::new);
    }

    private static boolean hasSpellChecker(String packageName) {
        final List<SpellCheckerInfo> spellCheckerInfos = sContext.getSystemService(
                TextServicesManager.class).getEnabledSpellCheckerInfos();
        return spellCheckerInfos.stream()
                .anyMatch(info -> info.getPackageName().equals(packageName));
    }
}
