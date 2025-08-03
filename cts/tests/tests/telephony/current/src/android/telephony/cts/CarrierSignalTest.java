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

package android.telephony.cts;

import static org.junit.Assert.assertEquals;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.RequiredFeatureRule;
import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class CarrierSignalTest {
    private class TestReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            mIntentFuture.complete(intent);
        }
    }

    @Rule
    public final RequiredFeatureRule mTelephonyRequiredRule =
            new RequiredFeatureRule(PackageManager.FEATURE_TELEPHONY);

    private static final int TEST_TIMEOUT_MILLIS = 5000;
    private Context mContext;
    private CarrierConfigManager mCarrierConfigManager;
    private int mTestSub;
    private CompletableFuture<Intent> mIntentFuture = new CompletableFuture<>();
    private final TestReceiver mReceiver = new TestReceiver();

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mCarrierConfigManager = mContext.getSystemService(CarrierConfigManager.class);
        mTestSub = SubscriptionManager.getDefaultSubscriptionId();

        String[] carrierConfigData = new String[] {
                new ComponentName(mContext.getPackageName(),
                        mReceiver.getClass().getName()).flattenToString()
                        + ":"
                        // add more actions here as tests increase.
                        + String.join(",", TelephonyManager.ACTION_CARRIER_SIGNAL_RESET)
        };
        PersistableBundle b = new PersistableBundle();
        b.putStringArray(CarrierConfigManager.KEY_CARRIER_APP_NO_WAKE_SIGNAL_CONFIG_STRING_ARRAY,
                carrierConfigData);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mCarrierConfigManager,
                (cm) -> cm.overrideConfig(mTestSub, b));
        // We have no way of knowing when CarrierSignalAgent processes this broadcast, so sleep
        // and hope for the best.
        Thread.sleep(1000);
    }

    @After
    public void tearDown() {
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mCarrierConfigManager,
                (cm) -> cm.overrideConfig(mTestSub, null));
        ConnectivityManager cm = mContext.getSystemService(ConnectivityManager.class);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(cm,
                x -> x.setAirplaneMode(false));
        try {
            mContext.unregisterReceiver(mReceiver);
        } catch (Throwable t) { }
    }

    @Test
    public void testResetBroadcast() throws Exception {
        mIntentFuture = new CompletableFuture<>();
        mContext.registerReceiver(mReceiver,
                new IntentFilter(TelephonyManager.ACTION_CARRIER_SIGNAL_RESET));

        // Enable airplane mode to force the reset action
        ConnectivityManager cm = mContext.getSystemService(ConnectivityManager.class);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(cm,
                x -> x.setAirplaneMode(true));

        Intent receivedIntent = mIntentFuture.get(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertEquals(mTestSub,
                receivedIntent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1));
    }
}
