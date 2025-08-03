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

package android.nfc.cts;

import android.content.ComponentName;
import android.content.Context;
import android.nfc.Constants;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.CardEmulation;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.compatibility.common.util.CommonTestUtils;

import org.junit.Assert;

public final class DefaultPaymentProviderTestUtils {

    static final ComponentName CTS_MY_HOSTAPDU_SERVICE =
            new ComponentName("android.nfc.cts", "android.nfc.cts.CtsMyHostApduService");

    static final String CTS_MY_HOSTAPDU_SERVICE_DESC = "CTS Nfc Test Service";

    private DefaultPaymentProviderTestUtils() {}

    static ComponentName setDefaultPaymentService(Class serviceClass, Context context) {
        ComponentName componentName = setDefaultPaymentService(
                new ComponentName(context, serviceClass), context);
        return componentName;
    }

    static ComponentName setDefaultPaymentService(ComponentName serviceName, Context context) {
        try {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().adoptShellPermissionIdentity();

            ComponentName originalValue = CardEmulation.getPreferredPaymentService(context);
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(context);
            CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
            CardEmulationTest.SettingsObserver settingsObserver =
                    new CardEmulationTest.SettingsObserver(new Handler(Looper.getMainLooper()));
            context.getContentResolver().registerContentObserverAsUser(
                    Settings.Secure.getUriFor(
                            Constants.SETTINGS_SECURE_NFC_PAYMENT_DEFAULT_COMPONENT),
                    true, settingsObserver, UserHandle.ALL);
            Settings.Secure.putString(context.getContentResolver(),
                    Constants.SETTINGS_SECURE_NFC_PAYMENT_DEFAULT_COMPONENT,
                    serviceName == null ? null
                            : serviceName.flattenToString());
            int count = 0;
            while (!settingsObserver.mSeenChange
                    && !cardEmulation.isDefaultServiceForCategory(serviceName,
                    CardEmulation.CATEGORY_PAYMENT) && count < 10) {
                synchronized (settingsObserver) {
                    try {
                        settingsObserver.wait(200);
                    } catch (InterruptedException ie) {
                    }
                    count++;
                }
            }
            Assert.assertTrue(count < 10);
            Assert.assertTrue(serviceName == null
                    ? null == CardEmulation.getPreferredPaymentService(context)
                    : serviceName.equals(cardEmulation.getPreferredPaymentService(context)));
            return originalValue;
        } finally {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().dropShellPermissionIdentity();
        }
    }

    static void ensurePreferredService(String serviceDesc, Context context) {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(context);
        final CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        try {
            CommonTestUtils.waitUntil("Default service hasn't updated", 6,
                    () -> serviceDesc.equals(
                            cardEmulation.getDescriptionForPreferredPaymentService()));
        } catch (InterruptedException ie) { }
    }

    static void runWithDefaultPaymentService(Context context,
            ComponentName service, String description, Runnable runnable) {
        ComponentName originalValue = setDefaultPaymentService(service, context);
        ensurePreferredService(description, context);
        runnable.run();
        if (originalValue != null) {
            setDefaultPaymentService(originalValue, context);
        }
    }
}
