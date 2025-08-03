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

package android.telecom.cts.apps;

import static android.telecom.CallAttributes.DIRECTION_INCOMING;
import static android.telecom.CallAttributes.DIRECTION_OUTGOING;

import android.net.Uri;
import android.os.Bundle;
import android.telecom.CallAttributes;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import java.util.Random;


public class AttributesUtil {
    private static final Random sRandom = new Random(0);
    private static final Uri TEST_URI_OUT = Uri.parse("tel:123-TEST");
    private static final String TEST_NAME_OUT = "Mike Tyson";
    private static final Uri TEST_URI_IN = Uri.parse("tel:456-TEST");
    private static final String TEST_NAME_IN = "Alan Turing";

    /**
     * @return true if the call is holdable according to the CallAttributes
     */
    public static boolean hasSetInactiveCapabilities(CallAttributes callAttributes) {
        return (callAttributes.getCallCapabilities() & CallAttributes.SUPPORTS_SET_INACTIVE)
                == CallAttributes.SUPPORTS_SET_INACTIVE;
    }

    /**
     * @return a CallAttributes object for the given TelecomAppName. The Name and Address are
     * defaulted depending on the direction.
     */
    public static CallAttributes getDefaultAttributesForApp(TelecomTestApp name, boolean isOutgoing)
            throws Exception {
        return new CallAttributes.Builder(
                getPhoneAccountHandleForApp(name),
                getDirectionFromBool(isOutgoing),
                getDefaultName(isOutgoing), getDefaultAddress(isOutgoing))
                .setCallType(CallAttributes.AUDIO_CALL)
                .setCallCapabilities(CallAttributes.SUPPORTS_SET_INACTIVE)
                .build();
    }

    /**
     * @return a CallAttributes object for the given TelecomAppName. The Name and Address are
     * randomized!
     */
    public static CallAttributes getRandomAttributesForApp(TelecomTestApp name,
            boolean isOutgoing,
            boolean isHoldable)
            throws Exception {
        return new CallAttributes.Builder(
                getPhoneAccountHandleForApp(name),
                getDirectionFromBool(isOutgoing),
                getRandomName(), getRandomAddress())
                .setCallType(CallAttributes.AUDIO_CALL)
                .setCallCapabilities(setCallCapabilities(isHoldable))
                .build();
    }

    /**
     * @return a CallAttributes object for the MANAGED_APP. The Name and Address are
     * defaulted depending on the direction.
     */
    public static CallAttributes getDefaultAttributesForManaged(PhoneAccountHandle handle,
            boolean isOutgoing) {
        return new CallAttributes.Builder(
                handle,
                getDirectionFromBool(isOutgoing),
                TEST_NAME_IN, getDefaultAddress(isOutgoing))
                .setCallType(CallAttributes.AUDIO_CALL)
                .setCallCapabilities(CallAttributes.SUPPORTS_SET_INACTIVE)
                .build();
    }

    /**
     * @return a CallAttributes object for the MANAGED_APP. The Name and Address are
     * randomized!
     */
    public static CallAttributes getRandomAttributesForManaged(PhoneAccountHandle handle,
            boolean isOutgoing,
            boolean isHoldable) {
        return new CallAttributes.Builder(
                handle,
                getDirectionFromBool(isOutgoing),
                getRandomName(), getRandomAddress())
                .setCallType(CallAttributes.AUDIO_CALL)
                .setCallCapabilities(setCallCapabilities(isHoldable))
                .build();
    }

    /**
     * This method should help set the CallCapabilities that are defined in the CallAttributes
     * class
     */
    public static int setCallCapabilities(boolean isHoldable) {
        return isHoldable ? CallAttributes.SUPPORTS_SET_INACTIVE : 0;
    }

    /**
     * @return The [PhoneAccountHandle] for the given TelecomAppName that is defined in the
     * application.info package
     */
    public static PhoneAccountHandle getPhoneAccountHandleForApp(TelecomTestApp name)
            throws Exception {
        switch (name) {
            case TransactionalVoipAppMain -> {
                return TelecomTestApp.TRANSACTIONAL_APP_DEFAULT_HANDLE;
            }
            case TransactionalVoipAppClone -> {
                return TelecomTestApp.TRANSACTIONAL_APP_CLONE_DEFAULT_HANDLE;
            }
            case ConnectionServiceVoipAppMain -> {
                return TelecomTestApp.SELF_MANAGED_CS_MAIN_HANDLE;
            }
            case ConnectionServiceVoipAppClone -> {
                return TelecomTestApp.SELF_MANAGED_CS_CLONE_HANDLE;
            }
            case ManagedConnectionServiceApp -> {
                throw new Exception("The PhoneAccount for ManagedConnectionServiceApp is not"
                        + " is kept in BaseAppVerifier");
            }
        }
        throw new Exception(String.format("%s does not have a PhoneAccount mapping", name));
    }

    /**
     * @return injects the [PhoneAccountHandle] that is defined in the [CallAttribute]s object into
     * a Bundle.
     */
    public static Bundle getExtrasWithPhoneAccount(CallAttributes callAttributes) {
        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                callAttributes.getPhoneAccountHandle());
        if (!isOutgoing(callAttributes)) {
            extras.putParcelable(
                    TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,
                    callAttributes.getAddress()
            );
        }
        return extras;
    }

    /**
     * @return true if the call is outgoing bases on the [CallAttribute]s object
     */
    public static boolean isOutgoing(CallAttributes callAttributes) {
        return callAttributes.getDirection() == DIRECTION_OUTGOING;
    }

    private static int getDirectionFromBool(boolean isOutgoing) {
        return isOutgoing ? DIRECTION_OUTGOING : DIRECTION_INCOMING;
    }

    private static Uri getDefaultAddress(boolean isOutgoing) {
        return isOutgoing ? TEST_URI_OUT : TEST_URI_IN;
    }

    private static String getDefaultName(boolean isOutgoing) {
        return isOutgoing ? TEST_NAME_IN : TEST_NAME_OUT;
    }

    private static String getRandomName() {
        byte[] array = new byte[16];
        sRandom.nextBytes(array);
        return sRandom.toString();
    }

    private static Uri getRandomAddress() {
        byte[] array = new byte[11];
        sRandom.nextBytes(array);
        return Uri.parse("tel:" + sRandom);
    }
}
