/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.keystore.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.hardware.security.keymint.ErrorCode;
import android.security.KeyStoreException;
import android.security.keymaster.KeymasterDefs;
import android.text.TextUtils;

import androidx.test.runner.AndroidJUnit4;

import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class KeyStoreExceptionTest {
    @Test
    public void testAllKeymasterDefsAreCovered() throws IllegalAccessException {
        ImmutableList<Field> kmErrors = getKeymasterDefsFields();
        assertTrue("Test bug: there should be errors to look up",
                kmErrors.size() > 0);

        for (Field f : kmErrors) {
            assertTrue(String.format("Missing entry for field %s", f.getName()),
                    KeyStoreException.hasFailureInfoForError(f.getInt(null)));
        }
    }

    @Test
    public void testErrorCodesMapping() throws IllegalAccessException {
        ImmutableList<Field> kmDefsFields = getKeymasterDefsFields();
        Field[] errorFields = ErrorCode.class.getDeclaredFields();
        List<Integer> excludedErrorCodes = new ArrayList<Integer>();
        excludedErrorCodes.add(-62); // -62 is KEY_REQUIRES_UPGRADE and is handled by Keystore.
        for (Field code : errorFields) {
            final int codeVal = code.getInt(null);
            if (excludedErrorCodes.contains(codeVal)) {
                continue;
            }
            assertTrue(
                    TextUtils.formatSimple("ErrorCode %d is not defined in KeymasterDefs", codeVal),
                    kmDefsFields.stream().anyMatch(f -> {
                        try {
                            return f.getType() == int.class && f.getInt(null) == codeVal;
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    })
            );
        }
    }

    @Test
    public void testSystemErrorFlaggedCorrectly() {
        final int[] someSystemErrors = {-4, -6, -7, -9, -28, -64, -66, -67, -72, -1000, 3, 4};
        for (int i : someSystemErrors) {
            KeyStoreException systemEx = new KeyStoreException(i, null);
            assertTrue("Error code " + i + " is not correctly marked as system error.",
                    systemEx.isSystemError());
        }

        final int[] someNonSystemErrors = {-3, -5, -8, -11, -29, 6};
        for (int i : someNonSystemErrors) {
            KeyStoreException nonSystemEx = new KeyStoreException(i, null);
            assertFalse("Error code " + i + " is incorrectly marked as system error.",
                    nonSystemEx.isSystemError());
        }
    }

    @Test
    public void testRequiresUserAuthenticationFlaggedCorrectly() {
        final int[] errorsRequiringAuthentication = {-26, -72, 2};

        for (int i : errorsRequiringAuthentication) {
            KeyStoreException ex = new KeyStoreException(i, null);
            assertTrue("Error code " + i + " is not correctly marked as requiring user auth.",
                    ex.requiresUserAuthentication());
        }

        KeyStoreException regularEx = new KeyStoreException(6 /* permission denied */, null);
        assertFalse(regularEx.requiresUserAuthentication());
    }

    public static ImmutableList<Field> getKeymasterDefsFields() {
        ImmutableList.Builder<Field> errorFieldsBuilder = new ImmutableList.Builder<>();

        Class kmDefsClass = KeymasterDefs.class;
        for (Field f : kmDefsClass.getDeclaredFields()) {
            if (f.getName().startsWith("KM_ERROR") && f.getType().equals(int.class)) {
                errorFieldsBuilder.add(f);
            }
        }

        return errorFieldsBuilder.build();
    }

    @Test
    public void testOutOfKeysFailureServerUnavailable() {
        KeyStoreException ex = new KeyStoreException(22, "RKP failure",
                1 /* temporarily unavailable */);
        assertEquals("Error must indicate key exhaustion",
                KeyStoreException.ERROR_ATTESTATION_KEYS_UNAVAILABLE,
                ex.getNumericErrorCode());
        assertTrue("Must indicate a system issue",
                ex.isSystemError());
        assertTrue("Must indicate a transient failure",
                ex.isTransientFailure());
        assertEquals("Retry policy must be exponential back-off",
                KeyStoreException.RETRY_WITH_EXPONENTIAL_BACKOFF,
                ex.getRetryPolicy());
    }

    @Test
    public void testOutOfKeysFailurePendingConnectivity() {
        KeyStoreException ex = new KeyStoreException(22, "RKP failure",
                3 /* fetching pending connectivity */);
        assertEquals("Error must indicate key exhaustion",
                KeyStoreException.ERROR_ATTESTATION_KEYS_UNAVAILABLE,
                ex.getNumericErrorCode());
        assertTrue("Must indicate a system issue",
                ex.isSystemError());
        assertTrue("Must indicate a transient failure",
                ex.isTransientFailure());
        assertEquals("Retry policy must be when connectivity is resumed",
                KeyStoreException.RETRY_WHEN_CONNECTIVITY_AVAILABLE,
                ex.getRetryPolicy());
    }

    @Test
    public void testOutOfKeysFailureDeviceNotRegistered() {
        KeyStoreException ex = new KeyStoreException(22, "RKP failure",
                2 /* server refused issuance */);
        assertEquals("Error must indicate key exhaustion",
                KeyStoreException.ERROR_ATTESTATION_KEYS_UNAVAILABLE,
                ex.getNumericErrorCode());
        assertTrue("Must indicate a system issue",
                ex.isSystemError());
        assertFalse("Must indicate a permanent failure",
                ex.isTransientFailure());
        assertEquals("Retry policy must be never",
                KeyStoreException.RETRY_NEVER,
                ex.getRetryPolicy());
    }

    @Test
    public void testRkpFailureDeviceContainPotentiallyVulnerableSoftware() {
        KeyStoreException ex = new KeyStoreException(23, "RKP failure");
        assertEquals("Error must indicate key exhaustion",
                KeyStoreException.ERROR_DEVICE_REQUIRES_UPGRADE_FOR_ATTESTATION,
                ex.getNumericErrorCode());
        assertTrue("Must indicate a system issue", ex.isSystemError());
        assertTrue("Must indicate a non permanent failure", ex.isTransientFailure());
        assertEquals("Retry policy must be next reboot",
                KeyStoreException.RETRY_AFTER_NEXT_REBOOT,
                ex.getRetryPolicy());
    }

    @Test
    public void testRkpFailurePendingConnectivity() {
        KeyStoreException ex = new KeyStoreException(24, "RKP failure");
        assertEquals("Error must indicate key exhaustion",
                KeyStoreException.ERROR_ATTESTATION_KEYS_UNAVAILABLE,
                ex.getNumericErrorCode());
        assertTrue("Must indicate a system issue",
                ex.isSystemError());
        assertTrue("Must indicate a transient failure",
                ex.isTransientFailure());
        assertEquals("Retry policy must be when connectivity is resumed",
                KeyStoreException.RETRY_WHEN_CONNECTIVITY_AVAILABLE,
                ex.getRetryPolicy());
    }

    @Test
    public void testRkpFailureTransient() {
        KeyStoreException ex = new KeyStoreException(25, "RKP failure");
        assertEquals("Error must indicate key exhaustion",
                KeyStoreException.ERROR_ATTESTATION_KEYS_UNAVAILABLE,
                ex.getNumericErrorCode());
        assertTrue("Must indicate a system issue",
                ex.isSystemError());
        assertTrue("Must indicate a transient failure",
                ex.isTransientFailure());
        assertEquals("Retry policy must be with backoff",
                KeyStoreException.RETRY_WITH_EXPONENTIAL_BACKOFF,
                ex.getRetryPolicy());
    }

    @Test
    public void testRkpFailurePermanent() {
        KeyStoreException ex = new KeyStoreException(26, "RKP failure");
        assertEquals("Error must indicate key exhaustion",
                KeyStoreException.ERROR_ATTESTATION_KEYS_UNAVAILABLE,
                ex.getNumericErrorCode());
        assertTrue("Must indicate a system issue",
                ex.isSystemError());
        assertFalse("Must indicate a permanent failure",
                ex.isTransientFailure());
        assertEquals("Retry policy must be never",
                KeyStoreException.RETRY_NEVER,
                ex.getRetryPolicy());
    }
}
