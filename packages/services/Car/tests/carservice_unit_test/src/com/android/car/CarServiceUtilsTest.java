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

package com.android.car;

import static android.car.test.mocks.AndroidMockitoHelper.mockAmGetCurrentUser;
import static android.car.test.mocks.AndroidMockitoHelper.mockContextCreateContextAsUser;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STARTING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STOPPING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;
import static android.os.Process.INVALID_UID;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.car.builtin.content.pm.PackageManagerHelper;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.automotive.vehicle.SubscribeOptions;
import android.os.Process;
import android.text.TextUtils;

import com.android.car.util.TransitionLog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoSession;

import java.util.UUID;

public class CarServiceUtilsTest extends AbstractExtendedMockitoTestCase {

    private static final int TEST_PROP = 1;
    private static final int TEST_AREA_ID = 2;
    private static final float MIN_SAMPLE_RATE = 1.0f;
    private static final int CURRENT_USER_ID = 1000;
    private static final int NON_CURRENT_USER_ID = 1001;
    private static final String TAG = CarServiceUtilsTest.class.getSimpleName();
    private static final String KEY_ALIAS_CAR_SERVICE_UTILS_TEST =
            "KEY_ALIAS_CAR_SERVICE_UTILS_TEST";

    private static final UserLifecycleEvent USER_STARTING_EVENT =
            new UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_STARTING, 111);

    private MockitoSession mSession;
    @Mock
    private Context mMockContext;
    @Mock
    private Context mMockUserContext;

    @Mock
    private Context mContext;

    @Mock
    private PackageManager mPm;

    public CarServiceUtilsTest() {
        super(TAG);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(ActivityManager.class).spyStatic(PackageManagerHelper.class);
    }

    @Before
    public void setUp() {
        mockAmGetCurrentUser(CURRENT_USER_ID);
        when(mContext.getPackageManager()).thenReturn(mPm);
        when(mContext.getSystemService(PackageManager.class)).thenReturn(mPm);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
            mSession = null;
        }
    }

    @Test
    public void testSubscribeOptionsToHidl() {
        SubscribeOptions aidlOptions = new SubscribeOptions();
        aidlOptions.propId = TEST_PROP;
        aidlOptions.sampleRate = MIN_SAMPLE_RATE;
        // areaIds would be ignored because HIDL subscribeOptions does not support it.
        aidlOptions.areaIds = new int[]{TEST_AREA_ID};
        android.hardware.automotive.vehicle.V2_0.SubscribeOptions hidlOptions =
                new android.hardware.automotive.vehicle.V2_0.SubscribeOptions();
        hidlOptions.propId = TEST_PROP;
        hidlOptions.sampleRate = MIN_SAMPLE_RATE;
        hidlOptions.flags = android.hardware.automotive.vehicle.V2_0.SubscribeFlags.EVENTS_FROM_CAR;

        android.hardware.automotive.vehicle.V2_0.SubscribeOptions gotHidlOptions =
                CarServiceUtils.subscribeOptionsToHidl(aidlOptions);

        assertThat(gotHidlOptions).isEqualTo(hidlOptions);
    }

    @Test
    public void testStartSystemUiForUser() {
        int userId = NON_CURRENT_USER_ID;
        Resources resources = mock(Resources.class);
        String systemUiComponent = "test.systemui/test.systemui.TestSystemUIService";
        when(resources.getString(com.android.internal.R.string.config_systemUIServiceComponent))
                .thenReturn(systemUiComponent);
        when(mMockContext.getResources()).thenReturn(resources);
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);

        CarServiceUtils.startSystemUiForUser(mMockContext, userId);

        verify(mMockContext).bindServiceAsUser(intentCaptor.capture(), any(), anyInt(), any());
        assertThat(intentCaptor.getValue().getComponent()).isEqualTo(
                ComponentName.unflattenFromString(systemUiComponent));
    }

    @Test
    public void testStopSystemUiForUser() {
        int userId = NON_CURRENT_USER_ID;
        mockContextCreateContextAsUser(mMockContext, mMockUserContext, userId);
        Resources resources = mock(Resources.class);
        String systemUiComponent = "test.systemui/test.systemui.TestSystemUIService";
        when(resources.getString(com.android.internal.R.string.config_systemUIServiceComponent))
                .thenReturn(systemUiComponent);
        when(mMockContext.getResources()).thenReturn(resources);
        ActivityManager mockActivityManager = mock(ActivityManager.class);
        when(mMockContext.getSystemService(ActivityManager.class)).thenReturn(mockActivityManager);

        CarServiceUtils.stopSystemUiForUser(mMockContext, userId);

        verify(mockActivityManager).forceStopPackageAsUserEvenWhenStopping("test.systemui", userId);
    }

    @Test
    public void testTransitionLogToString() {
        TransitionLog transitionLog = new TransitionLog("serviceName", "state1", "state2",
                1623777864000L);
        String result = transitionLog.toString();

        // Should match the date pattern "MM-dd HH:mm:ss".
        expectWithMessage("transitionLog %s", result).that(result).matches(
                "^[01]\\d-[0-3]\\d [0-2]\\d:[0-6]\\d:[0-6]\\d\\s+.*");
        expectWithMessage("transitionLog %s", result).that(result).contains("serviceName:");
        expectWithMessage("transitionLog %s", result).that(result).contains(
                "from state1 to state2");
    }

    @Test
    public void testTransitionLogToString_withExtra() {
        TransitionLog transitionLog = new TransitionLog("serviceName", "state1", "state2",
                1623777864000L, "extra");
        String result = transitionLog.toString();

        // Should match the date pattern "MM-dd HH:mm:ss".
        expectWithMessage("transitionLog %s", result).that(result).matches(
                "^[01]\\d-[0-3]\\d [0-2]\\d:[0-6]\\d:[0-6]\\d\\s+.*");
        expectWithMessage("transitionLog %s", result).that(result).contains("serviceName:");
        expectWithMessage("transitionLog %s", result).that(result).contains("extra");
        expectWithMessage("transitionLog %s", result).that(result).contains(
                "from state1 to state2");
    }

    @Test
    public void testLongToBytes() {
        long longValue = 1234567890L;
        Byte[] expected = new Byte[] {0, 0, 0, 0, 73, -106, 2, -46};

        assertThat(CarServiceUtils.longToBytes(longValue)).asList().containsExactlyElementsIn(
                expected).inOrder();
    }

    @Test
    public void testBytesToLong() {
        byte[] bytes = new byte[] {0, 0, 0, 0, 73, -106, 2, -46};
        long expected = 1234567890L;

        assertThat(CarServiceUtils.bytesToLong(bytes)).isEqualTo(expected);
    }

    @Test
    public void testByteArrayToHexString() {
        assertThat(CarServiceUtils.byteArrayToHexString(new byte[]{0, 1, 2, -3})).isEqualTo(
                "000102fd");
    }

    @Test
    public void testUuidToBytes() {
        UUID uuid = new UUID(123456789L, 987654321L);
        Byte[] expected = new Byte[] {0, 0, 0, 0, 7, 91, -51, 21, 0, 0, 0, 0, 58, -34, 104, -79};

        assertThat(CarServiceUtils.uuidToBytes(uuid)).asList().containsExactlyElementsIn(
                expected).inOrder();
    }

    @Test
    public void testBytesToUUID() {
        byte[] bytes = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, -9, -8, -7, -6, -5, -4, -3};
        UUID expected = new UUID(72623859790382856L, 718316418130246909L);
        UUID result = CarServiceUtils.bytesToUUID(bytes);

        expectWithMessage("Least significant digits of %s", result).that(
                result.getLeastSignificantBits()).isEqualTo(718316418130246909L);
        expectWithMessage("Most significant digits of %s", result).that(
                result.getMostSignificantBits()).isEqualTo(72623859790382856L);
        expectWithMessage("Result %s", result).that(result)
                .isEqualTo(expected);
    }

    @Test
    public void testBytesToUUID_invalidLength() {
        byte[] bytes = new byte[] {0};

        assertThat(CarServiceUtils.bytesToUUID(bytes)).isNull();
    }

    @Test
    public void testGenerateRandomNumberString() {
        String result = CarServiceUtils.generateRandomNumberString(25);

        expectWithMessage("Random number string %s", result).that(result).hasLength(25);
        expectWithMessage("Is digits only %s", result).that(
                TextUtils.isDigitsOnly(result)).isTrue();
    }

    @Test
    public void testConcatByteArrays() {
        byte[] bytes1 = new byte[] {1, 2, 3};
        byte[] bytes2 = new byte[] {4, 5, 6};
        Byte[] expected = new Byte[] {1, 2, 3, 4, 5, 6};

        assertThat(CarServiceUtils.concatByteArrays(bytes1, bytes2)).asList()
                .containsExactlyElementsIn(expected).inOrder();
    }

    @Test
    public void testIsEventOfType_returnsTrue() {
        assertThat(CarServiceUtils.isEventOfType(TAG, USER_STARTING_EVENT,
                USER_LIFECYCLE_EVENT_TYPE_STARTING)).isTrue();
    }

    @Test
    @ExpectWtf
    public void testIsEventOfType_returnsFalse() {
        assertThat(CarServiceUtils.isEventOfType(TAG, USER_STARTING_EVENT,
                USER_LIFECYCLE_EVENT_TYPE_SWITCHING)).isFalse();
    }

    @Test
    public void testIsEventAnyOfTypes_returnsTrue() {
        assertThat(CarServiceUtils.isEventAnyOfTypes(TAG, USER_STARTING_EVENT,
                USER_LIFECYCLE_EVENT_TYPE_SWITCHING, USER_LIFECYCLE_EVENT_TYPE_STARTING)).isTrue();
    }

    @Test
    @ExpectWtf
    public void testIsEventAnyOfTypes_emptyEventTypes_returnsFalse() {
        assertThat(CarServiceUtils.isEventAnyOfTypes(TAG, USER_STARTING_EVENT)).isFalse();
    }

    @Test
    @ExpectWtf
    public void testIsEventAnyOfTypes_returnsFalse() {
        assertThat(CarServiceUtils.isEventAnyOfTypes(TAG, USER_STARTING_EVENT,
                USER_LIFECYCLE_EVENT_TYPE_SWITCHING, USER_LIFECYCLE_EVENT_TYPE_STOPPING)).isFalse();
    }

    @Test
    public void testCheckCalledByPackage_isNotOwner() throws Exception {
        String packageName = "Bond.James.Bond";
        int myUid = Process.myUid();
        when(mPm.getPackageUidAsUser(eq(packageName), anyInt())).thenReturn(INVALID_UID);

        SecurityException e = assertThrows(SecurityException.class,
                () -> CarServiceUtils.checkCalledByPackage(mContext, packageName));

        String msg = e.getMessage();
        expectWithMessage("exception message (pkg)").that(msg).contains(packageName);
        expectWithMessage("exception message (uid)").that(msg).contains(String.valueOf(myUid));
    }

    @Test
    public void testCheckCalledByPackage_isOwner() throws Exception {
        String packageName = "Bond.James.Bond";
        int myUid = Process.myUid();
        when(mPm.getPackageUidAsUser(eq(packageName), anyInt())).thenReturn(myUid);

        CarServiceUtils.checkCalledByPackage(mContext, packageName);

        // No need to assert, test would fail if it threw
    }

    @Test
    public void toIntArraySet() {
        int[] values = {1, 2, 3};

        expectWithMessage("Converted int array set").that(CarServiceUtils.toIntArraySet(values))
                .containsExactly(1, 2, 3);
    }

    @Test
    public void toIntArraySet_withNullValues_fails() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                CarServiceUtils.toIntArraySet(/* values= */ null)
        );

        expectWithMessage("Null values exception").that(thrown).hasMessageThat()
                .contains("Values to convert to array set");
    }

    @Test
    public void asList() {
        int[] values = {1, 2, 3};

        expectWithMessage("Converted int array list").that(CarServiceUtils.asList(values))
                .containsExactly(1, 2, 3).inOrder();
    }

    @Test
    public void asList_withNullValues_fails() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                CarServiceUtils.asList(/* array= */ null)
        );

        expectWithMessage("Null array exception").that(thrown).hasMessageThat()
                .contains("Array to convert to list");
    }

    @Test
    public void testEncryptDecryptData() {
        String input = "test string";
        byte[] inputBytes = input.getBytes();
        CarServiceUtils.EncryptedData data = CarServiceUtils.encryptData(inputBytes,
                KEY_ALIAS_CAR_SERVICE_UTILS_TEST);

        expectWithMessage("Expected data").that(data).isNotNull();
        expectWithMessage("Encrypted text").that(data.getEncryptedData()).isNotEqualTo(inputBytes);

        byte[] decryptedData = CarServiceUtils.decryptData(data, KEY_ALIAS_CAR_SERVICE_UTILS_TEST);

        expectWithMessage("Expected decrypted data").that(decryptedData).isNotNull();
        expectWithMessage("Decrypted text").that(decryptedData).isEqualTo(inputBytes);
    }

    @Test
    public void testEncryptData_sameDataDifferentIv() {
        String input = "test string";
        byte[] inputBytes = input.getBytes();

        CarServiceUtils.EncryptedData dataOne = CarServiceUtils.encryptData(inputBytes,
                KEY_ALIAS_CAR_SERVICE_UTILS_TEST);
        CarServiceUtils.EncryptedData dataTwo = CarServiceUtils.encryptData(inputBytes,
                KEY_ALIAS_CAR_SERVICE_UTILS_TEST);

        expectWithMessage("Encrypted text").that(dataOne.getEncryptedData())
                .isNotEqualTo(dataTwo.getEncryptedData());
        expectWithMessage("Initialization vendor").that(dataOne.getIv())
                .isNotEqualTo(dataTwo.getIv());
    }

    @Test
    public void testEncryptedDataEquals() {
        byte[] dataOne = new byte[]{'1', '2', '3', '4'};
        byte[] dataTwo = new byte[]{'1', '2', '3', '4'};
        byte[] ivOne = new byte[]{'9', '8', '7'};
        byte[] ivTwo = new byte[]{'9', '8', '7'};

        CarServiceUtils.EncryptedData one = new CarServiceUtils.EncryptedData(dataOne, ivOne);
        CarServiceUtils.EncryptedData two = new CarServiceUtils.EncryptedData(dataTwo, ivTwo);

        expectWithMessage("The same encrypted data").that(one).isEqualTo(two);
    }

    @Test
    public void testEncryptedDataEquals_notEqualData() {
        byte[] dataOne = new byte[]{'1', '2', '3', '4'};
        byte[] dataTwo = new byte[]{'5', '6', '7', '8'};
        byte[] ivOne = new byte[]{'9', '8', '7'};
        byte[] ivTwo = new byte[]{'9', '8', '7'};

        CarServiceUtils.EncryptedData one = new CarServiceUtils.EncryptedData(dataOne, ivOne);
        CarServiceUtils.EncryptedData two = new CarServiceUtils.EncryptedData(dataTwo, ivTwo);

        expectWithMessage("The same encrypted data").that(one).isNotEqualTo(two);
    }

    @Test
    public void testEncryptedDataEquals_notEqualIv() {
        byte[] dataOne = new byte[]{'1', '2', '3', '4'};
        byte[] dataTwo = new byte[]{'1', '2', '3', '4'};
        byte[] ivOne = new byte[]{'9', '8', '7'};
        byte[] ivTwo = new byte[]{'0', 'A', 'V'};

        CarServiceUtils.EncryptedData one = new CarServiceUtils.EncryptedData(dataOne, ivOne);
        CarServiceUtils.EncryptedData two = new CarServiceUtils.EncryptedData(dataTwo, ivTwo);

        expectWithMessage("The same encrypted data").that(one).isNotEqualTo(two);
    }
}
