/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.provider.cts.contactkeys;

import static android.provider.E2eeContactKeysManager.getMaxKeySizeBytes;


import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

import static org.junit.Assert.assertThrows;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.E2eeContactKeysManager;
import android.provider.E2eeContactKeysManager.E2eeContactKey;
import android.provider.E2eeContactKeysManager.E2eeSelfKey;
import android.provider.Flags;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@RequiresFlagsEnabled(Flags.FLAG_USER_KEYS)
@RunWith(AndroidJUnit4.class)
public class E2eeContactKeysManagerTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String LOOKUP_KEY = "0r1-423A2E4644502A2E50";
    private static final String DEVICE_ID = "device_id_value";
    private static final String DEVICE_ID_2 = "device_id_value_2";
    private static final String ACCOUNT_ID = "+1 (555) 555-1234";
    private static final byte[] KEY_VALUE = new byte[]{(byte) 0xba, (byte) 0x8a};
    private static final byte[] KEY_VALUE_2 = new byte[]{(byte) 0x5c, (byte) 0xab};
    private static final long STRIPPED_TIME_UPDATED = -1;
    private static final String HELPER_APP_PACKAGE = "android.provider.cts.visibleapp";
    private static final String HELPER_APP_CLASS =
            "android.provider.cts.visibleapp.VisibleService";
    private static final String HELPER_APP_LOOKUP_KEY = "0r1-423A2E4644502A2E50";
    private static final String HELPER_APP_DEVICE_ID = "someDeviceId";
    private static final String HELPER_APP_ACCOUNT_ID = "someAccountId";
    private static final String OWNER_PACKAGE_NAME = "android.provider.cts.contactkeys";

    private E2eeContactKeysManager mContactKeysManager;
    private Context mContext;

    @Before
    public void setUp() {
        mContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        mContactKeysManager = (E2eeContactKeysManager)
                mContext.getSystemService(Context.CONTACT_KEYS_SERVICE);
    }

    @After
    public void tearDown() {
        mContactKeysManager.removeE2eeContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID);
        mContactKeysManager.removeE2eeContactKey(LOOKUP_KEY, DEVICE_ID_2, ACCOUNT_ID);
        mContactKeysManager.removeE2eeSelfKey(DEVICE_ID, ACCOUNT_ID);
        mContactKeysManager.removeE2eeSelfKey(DEVICE_ID_2, ACCOUNT_ID);
    }

    @Test
    public void testUpdateOrInsertContactKey_insertsNewEntry() {
        mContactKeysManager.updateOrInsertE2eeContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID,
                KEY_VALUE);

        E2eeContactKey contactKey =
                mContactKeysManager.getE2eeContactKey(LOOKUP_KEY, DEVICE_ID,
                        ACCOUNT_ID);

        assertThat(contactKey.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(contactKey.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(contactKey.getKeyValue()).isEqualTo(KEY_VALUE);
        assertThat(contactKey.getLocalVerificationState())
                .isEqualTo(E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED);
        assertThat(contactKey.getRemoteVerificationState())
                .isEqualTo(E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED);
        assertThat(contactKey.getOwnerPackageName()).isEqualTo(OWNER_PACKAGE_NAME);
        assertThat(contactKey.getDisplayName()).isEqualTo(null);
        assertThat(contactKey.getEmailAddress()).isEqualTo(null);
        assertThat(contactKey.getPhoneNumber()).isEqualTo(null);
    }

    @Test
    public void testUpdateOrInsertContactKey_updatesExistingEntry() {
        mContactKeysManager.updateOrInsertE2eeContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID,
                KEY_VALUE);
        int localVerificationState = E2eeContactKeysManager.VERIFICATION_STATE_VERIFIED;
        int remoteVerificationState = E2eeContactKeysManager.VERIFICATION_STATE_VERIFIED;
        mContactKeysManager.updateE2eeContactKeyLocalVerificationState(LOOKUP_KEY, DEVICE_ID,
                ACCOUNT_ID, localVerificationState);
        mContactKeysManager.updateE2eeContactKeyRemoteVerificationState(LOOKUP_KEY, DEVICE_ID,
                ACCOUNT_ID, remoteVerificationState);

        mContactKeysManager.updateOrInsertE2eeContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID,
                KEY_VALUE_2);

        E2eeContactKey updatedContactKey =
                mContactKeysManager.getE2eeContactKey(LOOKUP_KEY, DEVICE_ID,
                        ACCOUNT_ID);
        assertNotNull(updatedContactKey);
        assertThat(updatedContactKey.getKeyValue()).isEqualTo(KEY_VALUE_2);
        assertThat(updatedContactKey.getLocalVerificationState())
                .isEqualTo(localVerificationState);
        assertThat(updatedContactKey.getRemoteVerificationState())
                .isEqualTo(remoteVerificationState);
    }

    @Test
    public void testUpdateOrInsertContactKey_keyTooLargeThrows() {
        byte[] largeKey = new byte[getMaxKeySizeBytes() + 1];
        Arrays.fill(largeKey, (byte) 42);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mContactKeysManager.updateOrInsertE2eeContactKey(LOOKUP_KEY, DEVICE_ID,
                        ACCOUNT_ID, largeKey));
        assertThat(e).hasMessageThat().contains("Key value length is " + largeKey.length + "."
                + " Should be more than 0 and less than " + getMaxKeySizeBytes());
    }

    @Test
    public void testUpdateOrInsertContactKey_emptyKeyThrows() {
        byte[] emptyKey = new byte[0];
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mContactKeysManager.updateOrInsertE2eeContactKey(LOOKUP_KEY, DEVICE_ID,
                        ACCOUNT_ID, emptyKey));
        assertThat(e).hasMessageThat().contains("Key value length is " + emptyKey.length + "."
                + " Should be more than 0 and less than " + getMaxKeySizeBytes());
    }

    @Test
    public void testUpdateOrInsertContactKey_nullKeyThrows() {
        assertThrows(NullPointerException.class,
                () -> mContactKeysManager.updateOrInsertE2eeContactKey(LOOKUP_KEY, DEVICE_ID,
                        ACCOUNT_ID, null));
    }

    @Test
    public void testGetContactKey_returnsNullForNonexistentEntry() {
        E2eeContactKey contactKey =
                mContactKeysManager.getE2eeContactKey(LOOKUP_KEY, DEVICE_ID,
                        ACCOUNT_ID);

        assertNull(contactKey);
    }

    @Test
    public void testGetOwnerContactKeys_returnsEntriesForCaller() {
        mContactKeysManager.updateOrInsertE2eeContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID,
                KEY_VALUE);
        mContactKeysManager.updateOrInsertE2eeContactKey(LOOKUP_KEY, DEVICE_ID_2, ACCOUNT_ID,
                KEY_VALUE_2);

        List<E2eeContactKey> contactKeys = mContactKeysManager.getOwnerE2eeContactKeys(LOOKUP_KEY);

        assertThat(contactKeys.size()).isEqualTo(2);
    }

    @Test
    public void testGetAllContactKeys_callerIsSameAsOwner() {
        mContactKeysManager.updateOrInsertE2eeContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID,
                KEY_VALUE);

        List<E2eeContactKey> contactKeys = mContactKeysManager.getAllE2eeContactKeys(LOOKUP_KEY);

        assertThat(contactKeys.size()).isEqualTo(1);
        E2eeContactKey actualKey = contactKeys.get(0);
        // Check that deviceId, timeUpdated and keyValue data is stripped
        assertThat(actualKey.getDeviceId()).isNull();
        assertThat(actualKey.getTimeUpdated()).isEqualTo(STRIPPED_TIME_UPDATED);
        assertThat(actualKey.getKeyValue()).isNull();
        assertThat(actualKey.getLocalVerificationState()).isEqualTo(
                E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED);
        assertThat(actualKey.getRemoteVerificationState()).isEqualTo(
                E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED);
    }

    @Test
    public void testGetAllContactKeys_callerIsDifferentFromOwner() {
        // Creates a contact key by another owner
        startHelperApp();

        List<E2eeContactKey> contactKeys = mContactKeysManager.getAllE2eeContactKeys(LOOKUP_KEY);

        // This also verifies that the keys created by an app (CtsContactKeysProviderInvisibleApp)
        // that is not queryable by CTS test are not visible
        assertThat(contactKeys.size()).isEqualTo(1);
        E2eeContactKey contactKey = contactKeys.get(0);
        // Check that deviceId, timeUpdated and keyValue data is stripped
        assertThat(contactKey.getDeviceId()).isNull();
        assertThat(contactKey.getTimeUpdated()).isEqualTo(STRIPPED_TIME_UPDATED);
        assertThat(contactKey.getKeyValue()).isNull();
        assertThat(contactKey.getLocalVerificationState()).isEqualTo(
                E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED);
        assertThat(contactKey.getRemoteVerificationState()).isEqualTo(
                E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED);
        stopHelperApp();
    }

    @Test
    public void testGetAllContactKeys_returnsEmptyListForNonexistentEntries() {
        List<E2eeContactKey> contactKeys =
                mContactKeysManager.getAllE2eeContactKeys(LOOKUP_KEY);

        assertThat(contactKeys.size()).isEqualTo(0);
    }

    @Test
    public void testUpdateContactKeyLocalVerificationState_updatesState() {
        mContactKeysManager.updateOrInsertE2eeContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID,
                KEY_VALUE);

        mContactKeysManager.updateE2eeContactKeyLocalVerificationState(LOOKUP_KEY, DEVICE_ID,
                ACCOUNT_ID, E2eeContactKeysManager.VERIFICATION_STATE_VERIFIED);

        E2eeContactKey updatedContactKey =
                mContactKeysManager.getE2eeContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID);
        assertThat(updatedContactKey.getLocalVerificationState())
                .isEqualTo(E2eeContactKeysManager.VERIFICATION_STATE_VERIFIED);
    }


    @Test
    public void testUpdateContactKeyLocalVerificationState_illegalState() {
        mContactKeysManager.updateOrInsertE2eeContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID,
                KEY_VALUE);

        int illegalVerificationState = 4;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mContactKeysManager.updateE2eeContactKeyLocalVerificationState(LOOKUP_KEY,
                        DEVICE_ID, ACCOUNT_ID, illegalVerificationState));
        assertThat(e).hasMessageThat().contains("Verification state value "
                + illegalVerificationState + " is not supported");
    }

    @Test
    public void testUpdateContactKeyLocalVerificationState_securityExceptionThrows() {
        startHelperApp();
        List<E2eeContactKey> contactKeys = mContactKeysManager.getAllE2eeContactKeys(LOOKUP_KEY);
        assertThat(contactKeys.size()).isEqualTo(1);

        SecurityException e = assertThrows(SecurityException.class,
                () ->mContactKeysManager.updateE2eeContactKeyLocalVerificationState(
                        HELPER_APP_LOOKUP_KEY, HELPER_APP_DEVICE_ID,
                        HELPER_APP_ACCOUNT_ID, HELPER_APP_PACKAGE,
                        E2eeContactKeysManager.VERIFICATION_STATE_VERIFIED));

        assertThat(e).hasMessageThat().contains("The caller must have the "
                + "android.permission.WRITE_VERIFICATION_STATE_E2EE_CONTACT_KEYS permission");
        stopHelperApp();
    }

    @Test
    public void testUpdateContactKeyRemoteVerificationState_securityExceptionThrows() {
        startHelperApp();
        List<E2eeContactKey> contactKeys = mContactKeysManager.getAllE2eeContactKeys(LOOKUP_KEY);
        assertThat(contactKeys.size()).isEqualTo(1);

        SecurityException e = assertThrows(SecurityException.class,
                () ->mContactKeysManager.updateE2eeContactKeyRemoteVerificationState(
                        HELPER_APP_LOOKUP_KEY, HELPER_APP_DEVICE_ID,
                        HELPER_APP_ACCOUNT_ID, HELPER_APP_PACKAGE,
                        E2eeContactKeysManager.VERIFICATION_STATE_VERIFIED));

        assertThat(e).hasMessageThat().contains("The caller must have the "
                + "android.permission.WRITE_VERIFICATION_STATE_E2EE_CONTACT_KEYS permission");
        stopHelperApp();
    }

    @Test
    public void testUpdateContactKeyRemoteVerificationState_updatesState() {
        mContactKeysManager.updateOrInsertE2eeContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID,
                KEY_VALUE);

        mContactKeysManager.updateE2eeContactKeyRemoteVerificationState(LOOKUP_KEY, DEVICE_ID,
                ACCOUNT_ID, E2eeContactKeysManager.VERIFICATION_STATE_VERIFIED);

        E2eeContactKey updatedContactKey =
                mContactKeysManager.getE2eeContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID);
        assertThat(updatedContactKey.getRemoteVerificationState())
                .isEqualTo(E2eeContactKeysManager.VERIFICATION_STATE_VERIFIED);
    }

    @Test
    public void testUpdateContactKeyRemoteVerificationState_illegalState() {
        mContactKeysManager.updateOrInsertE2eeContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID,
                KEY_VALUE);

        int illegalVerificationState = 4;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mContactKeysManager.updateE2eeContactKeyRemoteVerificationState(LOOKUP_KEY,
                        DEVICE_ID, ACCOUNT_ID, illegalVerificationState));
        assertThat(e).hasMessageThat().contains("Verification state value "
                + illegalVerificationState + " is not supported");
    }

    @Test
    public void testRemoveContactKey_deletesEntry() {
        mContactKeysManager.updateOrInsertE2eeContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID,
                KEY_VALUE);

        mContactKeysManager.removeE2eeContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID);

        E2eeContactKey contactKey =
                mContactKeysManager.getE2eeContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID);
        assertNull(contactKey);
    }

    @Test
    public void testUpdateOrInsertSelfKey_insertsNewEntry() {
        mContactKeysManager.updateOrInsertE2eeSelfKey(DEVICE_ID, ACCOUNT_ID, KEY_VALUE);

        E2eeSelfKey newSelfKey = mContactKeysManager.getE2eeSelfKey(DEVICE_ID,
                ACCOUNT_ID);
        assertNotNull(newSelfKey);
        assertThat(newSelfKey.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(newSelfKey.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(newSelfKey.getKeyValue()).isEqualTo(KEY_VALUE);
        assertThat(newSelfKey.getOwnerPackageName()).isEqualTo(OWNER_PACKAGE_NAME);
    }

    @Test
    public void testUpdateOrInsertSelfKey_updatesExistingEntry() {
        mContactKeysManager.updateOrInsertE2eeSelfKey(DEVICE_ID, ACCOUNT_ID, KEY_VALUE);

        mContactKeysManager.updateOrInsertE2eeSelfKey(DEVICE_ID, ACCOUNT_ID, KEY_VALUE_2);

        E2eeSelfKey updatedSelfKey = mContactKeysManager.getE2eeSelfKey(DEVICE_ID,
                ACCOUNT_ID);
        assertNotNull(updatedSelfKey);
        assertThat(updatedSelfKey.getKeyValue()).isEqualTo(KEY_VALUE_2);
    }

    @Test
    public void testUpdateOrInsertSelfKey_keyTooLargeThrows() {
        byte[] largeKey = new byte[getMaxKeySizeBytes() + 1];
        Arrays.fill(largeKey, (byte) 42);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mContactKeysManager.updateOrInsertE2eeSelfKey(DEVICE_ID,
                        ACCOUNT_ID, largeKey));
        assertThat(e).hasMessageThat().contains("Key value length is " + largeKey.length + "."
                + " Should be more than 0 and less than "
                + getMaxKeySizeBytes());
    }

    @Test
    public void testUpdateOrInsertSelfKey_emptyKeyThrows() {
        byte[] emptyKey = new byte[0];
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mContactKeysManager.updateOrInsertE2eeSelfKey(DEVICE_ID,
                        ACCOUNT_ID, emptyKey));
        assertThat(e).hasMessageThat().contains("Key value length is " + emptyKey.length + "."
                + " Should be more than 0 and less than "
                + getMaxKeySizeBytes());
    }

    @Test
    public void testUpdateSelfKeyRemoteVerificationState_updatesState() {
        mContactKeysManager.updateOrInsertE2eeSelfKey(DEVICE_ID, ACCOUNT_ID, KEY_VALUE);

        mContactKeysManager.updateE2eeSelfKeyRemoteVerificationState(DEVICE_ID,
                ACCOUNT_ID, E2eeContactKeysManager.VERIFICATION_STATE_VERIFIED);

        E2eeSelfKey updatedSelfKey = mContactKeysManager.getE2eeSelfKey(DEVICE_ID,
                ACCOUNT_ID);
        assertThat(updatedSelfKey.getRemoteVerificationState())
                .isEqualTo(E2eeContactKeysManager.VERIFICATION_STATE_VERIFIED);
    }

    @Test
    public void testUpdateSelfKeyRemoteVerificationState_securityExceptionThrows() {
        startHelperApp();
        List<E2eeSelfKey> selfKeys = mContactKeysManager.getAllE2eeSelfKeys();
        assertThat(selfKeys.size()).isEqualTo(1);

        SecurityException e = assertThrows(SecurityException.class,
                () ->mContactKeysManager.updateE2eeSelfKeyRemoteVerificationState(
                        HELPER_APP_DEVICE_ID, HELPER_APP_ACCOUNT_ID,
                        HELPER_APP_PACKAGE, E2eeContactKeysManager.VERIFICATION_STATE_VERIFIED));
        assertThat(e).hasMessageThat().contains("The caller must have the "
                + "android.permission.WRITE_VERIFICATION_STATE_E2EE_CONTACT_KEYS permission");

        stopHelperApp();
    }

    @Test
    public void testUpdateSelfKeyRemoteVerificationState_illegalState() {
        mContactKeysManager.updateOrInsertE2eeSelfKey(DEVICE_ID, ACCOUNT_ID, KEY_VALUE);

        int illegalVerificationState = 4;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mContactKeysManager.updateE2eeSelfKeyRemoteVerificationState(DEVICE_ID,
                        ACCOUNT_ID, illegalVerificationState));
        assertThat(e).hasMessageThat().contains("Verification state value "
                + illegalVerificationState + " is not supported");
    }

    @Test
    public void testGetSelfKey_returnsExpectedSelfKey() {
        mContactKeysManager.updateOrInsertE2eeSelfKey(DEVICE_ID, ACCOUNT_ID, KEY_VALUE);

        E2eeSelfKey selfKey = mContactKeysManager.getE2eeSelfKey(DEVICE_ID,
                ACCOUNT_ID);

        assertNotNull(selfKey);
        assertThat(selfKey.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(selfKey.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(selfKey.getKeyValue()).isEqualTo(KEY_VALUE);
    }

    @Test
    public void testGetSelfKey_returnsNullForNonexistentEntry() {
        E2eeSelfKey selfKey = mContactKeysManager.getE2eeSelfKey(DEVICE_ID, ACCOUNT_ID);

        assertNull(selfKey);
    }

    @Test
    public void testGetOwnerSelfKeys_returnsEntriesForCaller() {
        mContactKeysManager.updateOrInsertE2eeSelfKey(DEVICE_ID, ACCOUNT_ID, KEY_VALUE);
        mContactKeysManager.updateOrInsertE2eeSelfKey(DEVICE_ID_2, ACCOUNT_ID, KEY_VALUE_2);

        List<E2eeSelfKey> selfKeys = mContactKeysManager.getOwnerE2eeSelfKeys();

        assertThat(selfKeys.size()).isEqualTo(2);
    }

    @Test
    public void testGetAllSelfKeys_callerIsSameAsOwner() {
        mContactKeysManager.updateOrInsertE2eeSelfKey(DEVICE_ID, ACCOUNT_ID, KEY_VALUE);

        List<E2eeSelfKey> selfKeys = mContactKeysManager.getAllE2eeSelfKeys();

        assertThat(selfKeys.size()).isEqualTo(1);
        E2eeSelfKey actualKey = selfKeys.get(0);
        // Check that deviceId, timeUpdated and keyValue data is stripped
        assertThat(actualKey.getDeviceId()).isNull();
        assertThat(actualKey.getTimeUpdated()).isEqualTo(STRIPPED_TIME_UPDATED);
        assertThat(actualKey.getKeyValue()).isNull();
        assertThat(actualKey.getRemoteVerificationState()).isEqualTo(
                E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED);
    }

    @Test
    public void testGetAllSelfKeys_callerIsDifferentFromOwner() {
        // Creates a self key by another owner
        startHelperApp();

        List<E2eeSelfKey> selfKeys = mContactKeysManager.getAllE2eeSelfKeys();

        // This also verifies that the keys created by an app (CtsContactKeysProviderInvisibleApp)
        // that is not queryable by CTS test are not visible
        assertThat(selfKeys.size()).isEqualTo(1);
        // Check that deviceId, timeUpdated and keyValue data is stripped
        E2eeSelfKey selfKey = selfKeys.get(0);
        assertThat(selfKey.getDeviceId()).isNull();
        assertThat(selfKey.getTimeUpdated()).isEqualTo(STRIPPED_TIME_UPDATED);
        assertThat(selfKey.getKeyValue()).isNull();
        assertThat(selfKey.getRemoteVerificationState()).isEqualTo(
                E2eeContactKeysManager.VERIFICATION_STATE_UNVERIFIED);
        stopHelperApp();
    }

    @Test
    public void testGetAllSelfKeys_returnsEmptyListForNonexistentEntries() {
        List<E2eeSelfKey> selfKeys = mContactKeysManager.getAllE2eeSelfKeys();

        assertThat(selfKeys.size()).isEqualTo(0);
    }

    /**
     * Starting helper app triggers creating one contact and one self key for test purposes.
     */
    private void startHelperApp() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(
                HELPER_APP_PACKAGE,
                HELPER_APP_CLASS));
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getTargetContext().startForegroundService(intent);
        // Wait as service start (that includes creation of the keys) is not immediate
        try {
            Thread.sleep(500);
        } catch (Exception e) {
            // Do nothing
        }
    }

    /**
     * Stopping helper app triggers removing previously created contact and self key.
     */
    private void stopHelperApp() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(
                HELPER_APP_PACKAGE,
                HELPER_APP_CLASS));
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getTargetContext().stopService(intent);
        // Wait as service stop (that includes removal of the keys) is not immediate
        try {
            Thread.sleep(500);
        } catch (Exception e) {
            // Do nothing
        }
    }
}
