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

import static android.appenumeration.cts.Constants.ACTION_GET_ENABLED_INPUT_METHOD_LIST;
import static android.appenumeration.cts.Constants.ACTION_GET_ENABLED_INPUT_METHOD_SUBTYPE_LIST;
import static android.appenumeration.cts.Constants.ACTION_GET_INPUT_METHOD_LIST;
import static android.appenumeration.cts.Constants.CTS_MOCK_IME_APK;
import static android.appenumeration.cts.Constants.EXTRA_INPUT_METHOD_INFO;
import static android.appenumeration.cts.Constants.MOCK_IME_PKG;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING;
import static android.appenumeration.cts.Constants.QUERIES_PACKAGE;
import static android.appenumeration.cts.Utils.installPackage;
import static android.appenumeration.cts.Utils.uninstallPackage;
import static android.content.Intent.EXTRA_RETURN_RESULT;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.Process;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class InputMethodEnumerationTests extends AppEnumerationTestsBase {
    private static final String MOCK_IME_ID =
            ComponentName.createRelative(MOCK_IME_PKG, ".CtsInputMethod1").flattenToShortString();

    @BeforeClass
    public static void prepareInputMethod() throws Exception {
        installPackage(CTS_MOCK_IME_APK);
        PollingCheck.check("Failed to wait for " + MOCK_IME_PKG
                + " getting ready", DEFAULT_TIMEOUT_MS, () -> hasInputMethod(MOCK_IME_PKG));

        runShellCommand(enableIme(MOCK_IME_ID, myUserId()));
        PollingCheck.check("Failed to wait for " + MOCK_IME_PKG
                + " enabled", DEFAULT_TIMEOUT_MS, () -> hasEnabledInputMethod(MOCK_IME_PKG));
    }

    @AfterClass
    public static void cleanUp() throws Exception {
        runShellCommand(disableIme(MOCK_IME_ID, myUserId()));
        uninstallPackage(MOCK_IME_PKG);
    }

    @Test
    public void queriesNothing_getInputMethodList_cannotSeeMockIme() throws Exception {
        assertNotVisible(QUERIES_NOTHING, MOCK_IME_PKG, this::getInputMethodList);
    }

    @Test
    public void queriesPackage_getInputMethodList_canSeeMockIme() throws Exception {
        assertVisible(QUERIES_PACKAGE, MOCK_IME_PKG, this::getInputMethodList);
    }

    @Test
    public void queriesNothing_getEnabledInputMethodList_cannotSeeMockIme() throws Exception {
        assertNotVisible(QUERIES_NOTHING, MOCK_IME_PKG, this::getEnabledInputMethodList);
    }

    @Test
    public void queriesPackage_getEnabledInputMethodList_canSeeMockIme() throws Exception {
        assertVisible(QUERIES_PACKAGE, MOCK_IME_PKG, this::getEnabledInputMethodList);
    }

    @Test
    public void queriesNothing_getEnabledInputMethodSubtypeList_cannotSeeMockIme()
            throws Exception {
        assertNotVisible(QUERIES_NOTHING, MOCK_IME_PKG, this::getEnabledInputMethodSubtypeList);
    }

    @Test
    public void queriesPackage_getEnabledInputMethodSubtypeList_canSeeMockIme() throws Exception {
        assertVisible(QUERIES_PACKAGE, MOCK_IME_PKG, this::getEnabledInputMethodSubtypeList);
    }

    private String[] getInputMethodList(String sourcePackage) throws Exception {
        final Bundle response = sendCommandBlocking(sourcePackage, null /* targetPackageName */,
                null /* intentExtra */, ACTION_GET_INPUT_METHOD_LIST);
        final List<InputMethodInfo> infos =
                response.getParcelableArrayList(EXTRA_RETURN_RESULT, InputMethodInfo.class);
        return infos.stream()
                .map(info -> info.getPackageName())
                .distinct()
                .toArray(String[]::new);
    }

    private String[] getEnabledInputMethodList(String sourcePackage) throws Exception {
        final Bundle response = sendCommandBlocking(sourcePackage, null /* targetPackageName */,
                null /* intentExtra */, ACTION_GET_ENABLED_INPUT_METHOD_LIST);
        final List<InputMethodInfo> infos =
                response.getParcelableArrayList(EXTRA_RETURN_RESULT, InputMethodInfo.class);
        return infos.stream()
                .map(info -> info.getPackageName())
                .distinct()
                .toArray(String[]::new);
    }

    private String[] getEnabledInputMethodSubtypeList(String sourcePackage, String targetPackage)
            throws Exception {
        final InputMethodInfo imi = getInputMethod(targetPackage);
        assertThat(imi, notNullValue());
        final Bundle extras = new Bundle();
        extras.putParcelable(EXTRA_INPUT_METHOD_INFO, imi);
        final Bundle response = sendCommandBlocking(sourcePackage, null /* targetPackageName */,
                extras, ACTION_GET_ENABLED_INPUT_METHOD_SUBTYPE_LIST);
        final List<InputMethodSubtype> infos =
                response.getParcelableArrayList(EXTRA_RETURN_RESULT, InputMethodSubtype.class);
        return !infos.isEmpty() ? new String[]{targetPackage} : new String[0];
    }

    private static boolean hasInputMethod(String packageName) {
        final List<InputMethodInfo> inputMethods =
                sContext.getSystemService(InputMethodManager.class).getInputMethodList();
        return inputMethods.stream()
                .anyMatch(info -> info.getPackageName().equals(packageName));
    }

    private static boolean hasEnabledInputMethod(String packageName) {
        final List<InputMethodInfo> inputMethods =
                sContext.getSystemService(InputMethodManager.class).getEnabledInputMethodList();
        return inputMethods.stream()
                .anyMatch(info -> info.getPackageName().equals(packageName));
    }

    private static InputMethodInfo getInputMethod(String packageName) {
        final List<InputMethodInfo> inputMethods =
                sContext.getSystemService(InputMethodManager.class).getInputMethodList();
        return inputMethods.stream().filter(imi -> imi.getPackageName().equals(packageName))
                .findFirst().orElse(null);
    }

    private static int myUserId() {
        return Process.myUserHandle().getIdentifier();
    }

    private static String enableIme(String imeId, int userId) {
        return String.format("ime enable --user %d %s", userId, imeId);
    }

    private static String disableIme(String imeId, int userId) {
        return String.format("ime disable --user %d %s", userId, imeId);
    }
}
