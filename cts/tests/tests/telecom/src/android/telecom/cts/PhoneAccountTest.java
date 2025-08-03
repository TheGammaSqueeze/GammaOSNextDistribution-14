/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.telecom.cts;

import android.content.ComponentName;
import android.os.Parcel;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.test.AndroidTestCase;
import android.util.ArraySet;

import com.android.internal.telephony.flags.Flags;

public class PhoneAccountTest extends AndroidTestCase {
    public static final String ACCOUNT_LABEL = "CTSPhoneAccountTest";

    public void testPhoneAccountCapabilitiesForCallComposer() {
        PhoneAccount testPhoneAccount = PhoneAccount.builder(
                TestUtils.TEST_PHONE_ACCOUNT_HANDLE, ACCOUNT_LABEL)
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_COMPOSER)
                .build();
        assertTrue(testPhoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_CALL_COMPOSER));

        testPhoneAccount = PhoneAccount.builder(
                TestUtils.TEST_PHONE_ACCOUNT_HANDLE, ACCOUNT_LABEL)
                .setCapabilities(PhoneAccount.CAPABILITY_VIDEO_CALLING)
                .build();
        assertFalse(testPhoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_CALL_COMPOSER));
    }

    public void testPhoneAccountSetCallingRestriction() {
        if (!Flags.simultaneousCallingIndications()) {
            return;
        }
        PhoneAccountHandle handle1 = new PhoneAccountHandle(
                ComponentName.createRelative("pkg", "cls"), "123");
        PhoneAccountHandle handle2 = new PhoneAccountHandle(
                ComponentName.createRelative("pkg", "cls"), "456");
        PhoneAccountHandle handle3 = new PhoneAccountHandle(
                ComponentName.createRelative("pkg", "cls"), "789");

        ArraySet<PhoneAccountHandle> testRestriction = new ArraySet<>(2);
        testRestriction.add(handle2);
        testRestriction.add(handle3);
        PhoneAccount testPhoneAccount = new PhoneAccount.Builder(handle1, "test1")
                .setSimultaneousCallingRestriction(testRestriction)
                .build();
        assertTrue(testPhoneAccount.hasSimultaneousCallingRestriction());

        Parcel dataParceled = Parcel.obtain();
        testPhoneAccount.writeToParcel(dataParceled, 0);
        dataParceled.setDataPosition(0);
        PhoneAccount resultPA =
                PhoneAccount.CREATOR.createFromParcel(dataParceled);
        dataParceled.recycle();

        assertEquals(testPhoneAccount, resultPA);
        assertTrue(resultPA.hasSimultaneousCallingRestriction());
        assertEquals(testRestriction, resultPA.getSimultaneousCallingRestriction());
    }

    public void testPhoneAccountClearCallingRestriction() {
        if (!Flags.simultaneousCallingIndications()) {
            return;
        }
        PhoneAccountHandle handle1 = new PhoneAccountHandle(
                ComponentName.createRelative("pkg", "cls"), "123");
        PhoneAccountHandle handle2 = new PhoneAccountHandle(
                ComponentName.createRelative("pkg", "cls"), "456");
        PhoneAccountHandle handle3 = new PhoneAccountHandle(
                ComponentName.createRelative("pkg", "cls"), "789");

        ArraySet<PhoneAccountHandle> testRestriction = new ArraySet<>(2);
        testRestriction.add(handle2);
        testRestriction.add(handle3);
        PhoneAccount pa = new PhoneAccount.Builder(handle1, "test1")
                .setSimultaneousCallingRestriction(testRestriction)
                .build();
        PhoneAccount testPhoneAccount = new PhoneAccount.Builder(pa)
                .clearSimultaneousCallingRestriction()
                .build();
        assertFalse(testPhoneAccount.hasSimultaneousCallingRestriction());

        Parcel dataParceled = Parcel.obtain();
        testPhoneAccount.writeToParcel(dataParceled, 0);
        dataParceled.setDataPosition(0);
        PhoneAccount resultPA =
                PhoneAccount.CREATOR.createFromParcel(dataParceled);
        dataParceled.recycle();

        assertEquals(testPhoneAccount, resultPA);
        assertFalse(testPhoneAccount.hasSimultaneousCallingRestriction());
        try {
            resultPA.getSimultaneousCallingRestriction();
            fail("getSimultaneousCallingRestriction should throw IllegalStateException if not set");
        } catch (IllegalStateException e) {
            // expected
        }
    }
}