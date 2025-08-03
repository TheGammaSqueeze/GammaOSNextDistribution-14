/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.inputmethodservice.cts.devicetest;

import static android.inputmethodservice.cts.common.BusyWaitUtils.pollingCheck;

import static org.junit.Assert.assertFalse;

import android.Manifest;
import android.content.Context;
import android.inputmethodservice.cts.common.Ime1Constants;
import android.inputmethodservice.cts.common.Ime2Constants;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

/**
 * Test public APIs defined in InputMethodManager.
 */
@RunWith(AndroidJUnit4.class)
public class InputMethodManagerDeviceTest {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    private static final long EXPECTED_TIMEOUT = TimeUnit.SECONDS.toMillis(2);

    private InputMethodManager mImm;
    private Context mContext;

    /**
     * Set up {@link #mImm} from the target {@link Context}.
     */
    @Before
    public void setUpInputMethodManager() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mImm = mContext.getSystemService(InputMethodManager.class);
    }

    /**
     * Tear down {@link #mImm} with {@code null}.
     */
    @After
    public void tearDownInputMethodManager() {
        mImm = null;
    }

    /**
     * Make sure {@link InputMethodManager#getInputMethodList()} contains
     * {@link Ime1Constants#IME_ID}.
     */
    @Test
    public void testIme1InInputMethodList() throws Throwable {
        pollingCheck(() -> mImm.getInputMethodList().stream().anyMatch(
                imi -> TextUtils.equals(imi.getId(), Ime1Constants.IME_ID)),
                TIMEOUT, "Ime1 must exist.");
    }

    /**
     * Make sure {@link InputMethodManager#getCurrentInputMethodInfo()} contains
     * {@link Ime1Constants#IME_ID}.
     */
    @Test
    public void testIme1InCurrentInputMethodInfo() throws Throwable {
        pollingCheck(() -> TextUtils.equals(
                mImm.getCurrentInputMethodInfo().getId(), Ime1Constants.IME_ID),
                TIMEOUT, "Ime1 must be the current IME. Found "
                        + mImm.getCurrentInputMethodInfo().getId());
    }

    /**
     * Make sure {@link InputMethodManager#getCurrentInputMethodInfo()} does not contain
     * {@link Ime1Constants#IME_ID}.
     */
    @Test
    public void testIme1NotCurrentInputMethodInfo() throws Throwable {
        pollingCheck(() -> {
            final InputMethodInfo info  = mImm.getCurrentInputMethodInfo();
            if (info == null) {
                return true;
            }
            return !TextUtils.equals(info.getId(), Ime1Constants.IME_ID);
        }, TIMEOUT, "Ime1 must not be the current IME.");
    }

    /**
     * Make sure {@link InputMethodManager#getCurrentInputMethodInfo()} contains
     * {@link Ime2Constants#IME_ID}.
     */
    @Test
    public void testIme2InCurrentInputMethodInfo() throws Throwable {
        pollingCheck(() -> TextUtils.equals(
                        mImm.getCurrentInputMethodInfo().getId(), Ime2Constants.IME_ID),
                TIMEOUT, "Ime2 must be the current IME. Found "
                        + mImm.getCurrentInputMethodInfo().getId());
    }

    /**
     * Make sure {@link InputMethodManager#getCurrentInputMethodInfo()} does not contain
     * {@link Ime2Constants#IME_ID}.
     */
    @Test
    public void testIme2NotCurrentInputMethodInfo() throws Throwable {
        pollingCheck(() -> {
            final InputMethodInfo info  = mImm.getCurrentInputMethodInfo();
            if (info == null) {
                return true;
            }
            return !TextUtils.equals(info.getId(), Ime2Constants.IME_ID);
        }, TIMEOUT, "Ime2 must not be the current IME.");
    }

    /**
     * Make sure {@link InputMethodManager#getInputMethodList()} does not contain
     * {@link Ime1Constants#IME_ID}.
     */
    @Test
    public void testIme1NotInInputMethodList() {
        SystemClock.sleep(EXPECTED_TIMEOUT);
        assertFalse(mImm.getInputMethodList().stream().anyMatch(
                imi -> TextUtils.equals(imi.getId(), Ime1Constants.IME_ID)));
    }

    /**
     * Make sure {@link InputMethodManager#getEnabledInputMethodList()} contains
     * {@link Ime1Constants#IME_ID}.
     */
    @Test
    public void testIme1InEnabledInputMethodList() throws Throwable {
        pollingCheck(() -> mImm.getEnabledInputMethodList().stream().anyMatch(
                imi -> TextUtils.equals(imi.getId(), Ime1Constants.IME_ID)),
                TIMEOUT, "Ime1 must be enabled.");
    }

    /**
     * Make sure {@link InputMethodManager#getEnabledInputMethodList()} does not contain
     * {@link Ime1Constants#IME_ID}.
     */
    @Test
    public void testIme1NotInEnabledInputMethodList() {
        SystemClock.sleep(EXPECTED_TIMEOUT);
        assertFalse(mImm.getEnabledInputMethodList().stream().anyMatch(
                imi -> TextUtils.equals(imi.getId(), Ime1Constants.IME_ID)));
    }

    /**
     * Make sure {@link InputMethodManager#getInputMethodList()} contains
     * {@link Ime2Constants#IME_ID}.
     */
    @Test
    public void testIme2InInputMethodList() throws Throwable {
        pollingCheck(() -> mImm.getInputMethodList().stream().anyMatch(
                imi -> TextUtils.equals(imi.getId(), Ime2Constants.IME_ID)),
                TIMEOUT, "Ime1 must exist.");
    }

    /**
     * Make sure {@link InputMethodManager#getInputMethodList()} does not contain
     * {@link Ime2Constants#IME_ID}.
     */
    @Test
    public void testIme2NotInInputMethodList() {
        SystemClock.sleep(EXPECTED_TIMEOUT);
        assertFalse(mImm.getInputMethodList().stream().anyMatch(
                imi -> TextUtils.equals(imi.getId(), Ime2Constants.IME_ID)));
    }

    /**
     * Make sure {@link InputMethodManager#getEnabledInputMethodList()} contains
     * {@link Ime2Constants#IME_ID}.
     */
    @Test
    public void testIme2InEnabledInputMethodList() throws Throwable {
        pollingCheck(() -> mImm.getEnabledInputMethodList().stream().anyMatch(
                imi -> TextUtils.equals(imi.getId(), Ime2Constants.IME_ID)),
                TIMEOUT, "Ime1 must be enabled.");
    }

    /**
     * Make sure {@link InputMethodManager#getEnabledInputMethodList()} does not contain
     * {@link Ime2Constants#IME_ID}.
     */
    @Test
    public void testIme2NotInEnabledInputMethodList() {
        SystemClock.sleep(EXPECTED_TIMEOUT);
        assertFalse(mImm.getEnabledInputMethodList().stream().anyMatch(
                imi -> TextUtils.equals(imi.getId(), Ime2Constants.IME_ID)));
    }

    /**
     * Make sure
     * {@link InputMethodManager#getEnabledInputMethodSubtypeList(InputMethodInfo, boolean)} for
     * {@link Ime1Constants#IME_ID} returns the implicitly enabled subtype.
     */
    @Test
    public void testIme1ImplicitlyEnabledSubtypeExists() throws Throwable {
        pollingCheck(() -> mImm.getInputMethodList().stream()
                        .filter(imi -> TextUtils.equals(imi.getId(), Ime1Constants.IME_ID))
                        .flatMap(imi -> mImm.getEnabledInputMethodSubtypeList(imi, true).stream())
                        .anyMatch(InputMethodSubtype::overridesImplicitlyEnabledSubtype),
                TIMEOUT, "Implicitly enabled Subtype must exist for IME1.");
    }

    /**
     * Make sure
     * {@link InputMethodManager#getEnabledInputMethodSubtypeList(InputMethodInfo, boolean)} for
     * {@link Ime1Constants#IME_ID} does not return the implicitly enabled subtype.
     */
    @Test
    public void testIme1ImplicitlyEnabledSubtypeNotExist() {
        SystemClock.sleep(EXPECTED_TIMEOUT);
        assertFalse(mImm.getInputMethodList().stream()
                .filter(imi -> TextUtils.equals(imi.getId(), Ime1Constants.IME_ID))
                .flatMap(imi -> mImm.getEnabledInputMethodSubtypeList(imi, true).stream())
                .anyMatch(InputMethodSubtype::overridesImplicitlyEnabledSubtype));
    }

    /**
     * Make sure {@link InputMethodManager#isStylusHandwritingAvailable()} returns result accurately
     * for profile user
     */
    @Test
    public void testIsStylusHandwritingAvailableForProfile() throws Throwable {
        // Turn stylus handwriting pref OFF for profile user.
        SystemUtil.runWithShellPermissionIdentity(() -> {
            Settings.Secure.putInt(mContext.getContentResolver(),
                    Settings.Secure.STYLUS_HANDWRITING_ENABLED, 0);
        }, Manifest.permission.WRITE_SECURE_SETTINGS);

        // Stylus pref should still be picked from parent profile i.e. default true.
        pollingCheck(() -> mImm.isStylusHandwritingAvailable(), TIMEOUT,
                "Handwriting should be enabled on profile user as primary user has it enabled.");
    }
}
