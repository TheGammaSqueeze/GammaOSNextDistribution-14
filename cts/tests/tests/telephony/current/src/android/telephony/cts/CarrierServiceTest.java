/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.telephony.cts;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.PersistableBundle;
import android.service.carrier.CarrierIdentifier;
import android.service.carrier.CarrierService;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.test.ServiceTestCase;
import android.util.Log;

import com.android.compatibility.common.util.CarrierPrivilegeUtils;

public class CarrierServiceTest extends ServiceTestCase<CarrierServiceTest.TestCarrierService> {
    private static final String TAG = CarrierServiceTest.class.getSimpleName();

    public CarrierServiceTest() { super(TestCarrierService.class); }

    @Override
    protected void runTest() throws Throwable {
        if (!hasCellular()) {
            Log.e(TAG, "No cellular support, all tests will be skipped.");
            return;
        }
        super.runTest();
    }

    private static boolean hasCellular() {
        PackageManager packageManager = getInstrumentation().getContext().getPackageManager();
        TelephonyManager telephonyManager =
                getInstrumentation().getContext().getSystemService(TelephonyManager.class);
        return packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION)
                && telephonyManager.getPhoneCount() > 0;
    }

    public void testNotifyCarrierNetworkChange_true() {
        notifyCarrierNetworkChangeWithoutCarrierPrivileges(/*active=*/ true);
        notifyCarrierNetworkChangeWithCarrierPrivileges(/*active=*/ true);
    }

    public void testNotifyCarrierNetworkChange_false() {
        notifyCarrierNetworkChangeWithoutCarrierPrivileges(/*active=*/ false);
        notifyCarrierNetworkChangeWithCarrierPrivileges(/*active=*/ false);
    }

    private void notifyCarrierNetworkChangeWithoutCarrierPrivileges(boolean active) {
        Intent intent = new Intent(getContext(), TestCarrierService.class);
        startService(intent);

        try {
            getService().notifyCarrierNetworkChange(active);
            fail("Expected SecurityException for notifyCarrierNetworkChange(" + active + ")");
        } catch (SecurityException expected) {
        }
    }

    private void notifyCarrierNetworkChangeWithCarrierPrivileges(boolean active) {
        Intent intent = new Intent(getContext(), TestCarrierService.class);
        startService(intent);

        try {
            CarrierPrivilegeUtils.withCarrierPrivileges(
                    getContext(),
                    SubscriptionManager.getDefaultSubscriptionId(),
                    () -> getService().notifyCarrierNetworkChange(active));
        } catch (SecurityException se) {
            fail("notifyCarrierNetworkChange should not throw SecurityException when has carrier "
                    + "privileges");
        } catch (Exception e) {
            fail("Exception thrown when try to get carrier privileges.");
        }
    }

    public void testNotifyCarrierNetworkChangeWithSubId_true() {
        notifyCarrierNetworkChangeForSubIdWithoutCarrierPrivileges(/*active=*/ true);
        notifyCarrierNetworkChangeForSubIdWithCarrierPrivileges(/*active=*/true);
    }

    public void testNotifyCarrierNetworkChangeWithSubId_false() {
        notifyCarrierNetworkChangeForSubIdWithoutCarrierPrivileges(/*active=*/ false);
        notifyCarrierNetworkChangeForSubIdWithCarrierPrivileges(/*active=*/false);
    }

    private void notifyCarrierNetworkChangeForSubIdWithoutCarrierPrivileges(boolean active) {
        Intent intent = new Intent(getContext(), TestCarrierService.class);
        startService(intent);

        try {
            int subId = SubscriptionManager.getDefaultSubscriptionId();
            getService().notifyCarrierNetworkChange(subId, active);
            fail("Expected SecurityException for notifyCarrierNetworkChangeWithSubId(" + subId
                    + ", " + active + ")");
        } catch (SecurityException expected) {
        }
    }

    private void notifyCarrierNetworkChangeForSubIdWithCarrierPrivileges(boolean active) {
        Intent intent = new Intent(getContext(), TestCarrierService.class);
        startService(intent);

        try {
            int subId = SubscriptionManager.getDefaultSubscriptionId();
            CarrierPrivilegeUtils.withCarrierPrivileges(
                    getContext(),
                    subId,
                    () -> getService().notifyCarrierNetworkChange(subId, active));
        } catch (SecurityException securityException) {
            fail("notifyCarrierNetworkChange with subId should not throw SecurityException when "
                    + "has carrier privileges");
        } catch (Exception e) {
            fail("Exception thrown when try to get carrier privileges.");
        }
    }

    public static class TestCarrierService extends CarrierService {
        @Override
        public PersistableBundle onLoadConfig(CarrierIdentifier id) {
            return null;
        }
        public PersistableBundle onLoadConfig(int subId, CarrierIdentifier id) {
            return null;
        }
    }
}
