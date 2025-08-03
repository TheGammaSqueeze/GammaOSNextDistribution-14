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

package android.nfc.cts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.CardEmulation;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class NfcPreferredPaymentTest {
    private final static String mTag = "Nfc";

    private final static String mRouteDestination = "Host";
    private final static String mDescription = "CTS Nfc Test Service";
    private final static String NFC_PAYMENT_DEFAULT_COMPONENT = "nfc_payment_default_component";
    private final static List<String> mAids = Arrays.asList("A000000004101011",
                                                            "A000000004101012",
                                                            "A000000004101013");
    private static final ComponentName CtsNfcTestService =
            new ComponentName("android.nfc.cts", "android.nfc.cts.CtsMyHostApduService");

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private NfcAdapter mAdapter;
    private CardEmulation mCardEmulation;
    private Context mContext;

    private WalletRoleTestUtils.RoleContext mRoleContext;

    private boolean supportsHardware() {
        final PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        boolean existAnyReqFeature =
                pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
                || pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION_NFCF)
                || pm.hasSystemFeature(PackageManager.FEATURE_NFC_OFF_HOST_CARD_EMULATION_ESE)
                || pm.hasSystemFeature(PackageManager.FEATURE_NFC_OFF_HOST_CARD_EMULATION_UICC);
        return existAnyReqFeature;
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue(supportsHardware());
        mContext = InstrumentationRegistry.getContext();
        mAdapter = NfcAdapter.getDefaultAdapter(mContext);
        assertNotNull(mAdapter);
        mCardEmulation = CardEmulation.getInstance(mAdapter);
    }

    @After
    public void tearDown() throws Exception {}

    /** Tests getAidsForPreferredPaymentService API */
    @Test
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testAidsForPreferredPaymentService() {
        DefaultPaymentProviderTestUtils.runWithDefaultPaymentService(mContext, CtsNfcTestService,
                mDescription, () -> {
                    try {
                        List<String> aids = mCardEmulation.getAidsForPreferredPaymentService();
                        for (String aid :aids) {
                            Log.i(mTag, "AidsForPreferredPaymentService: " + aid);
                        }

                        assertTrue("Retrieve incorrect preferred payment aid list",
                                mAids.equals(aids));
                    } catch (Exception e) {
                        fail("Unexpected Exception " + e);
                    }
                });
    }

    /** Tests getRouteDestinationForPreferredPaymentService API */
    @Test
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testRouteDestinationForPreferredPaymentService() {
        DefaultPaymentProviderTestUtils.runWithDefaultPaymentService(mContext, CtsNfcTestService,
                mDescription, () -> {
                    try {
                        String routeDestination =
                                mCardEmulation.getRouteDestinationForPreferredPaymentService();
                        Log.i(mTag,
                                "RouteDestinationForPreferredPaymentService: "
                                        + routeDestination);

                        assertTrue("Retrieve incorrect preferred payment route destination",
                                routeDestination.equals(mRouteDestination));
                    } catch (Exception e) {
                        fail("Unexpected Exception " + e);
                    }
                });
    }

    /** Tests getDescriptionForPreferredPaymentService API */
    @Test
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testDescriptionForPreferredPaymentService() {
        DefaultPaymentProviderTestUtils.runWithDefaultPaymentService(mContext, CtsNfcTestService,
                mDescription, () -> {
                    try {
                        CharSequence description =
                                mCardEmulation.getDescriptionForPreferredPaymentService();
                        Log.i(mTag,
                                "DescriptionForPreferredPaymentService: "
                                        + description.toString());

                        assertTrue("Retrieve incorrect preferred payment description",
                                description.toString().equals(mDescription.toString()));
                    } catch (Exception e) {
                        fail("Unexpected Exception " + e);
                    }
                });
    }

    /** Tests getSelectionModeForCategory API
     *  CardEmulation.CATEGORY_PAYMENT */
    @Test
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testGetSelectionModeForCategoryPayment() {
        DefaultPaymentProviderTestUtils.runWithDefaultPaymentService(mContext, CtsNfcTestService,
                mDescription, () -> {
                    try {
                        int mode = mCardEmulation.getSelectionModeForCategory(
                                CardEmulation.CATEGORY_PAYMENT);
                        Log.i(mTag, "getSelectionModeForCategory for Payment: " + mode);

                        assertTrue("Retrieve incorrect SelectionMode for Payment",
                                CardEmulation.SELECTION_MODE_PREFER_DEFAULT == mode);
                    } catch (Exception e) {
                        fail("Unexpected Exception " + e);
                    }
                });
    }

    /** Tests getSelectionModeForCategory API
     *  CardEmulation.CATEGORY_OTHER */

    @Test
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testGetSelectionModeForCategoryOther() {
        DefaultPaymentProviderTestUtils.runWithDefaultPaymentService(mContext, CtsNfcTestService,
                mDescription, () -> {
                    try {
                        int mode = mCardEmulation.getSelectionModeForCategory(
                                CardEmulation.CATEGORY_OTHER);
                        Log.i(mTag, "getSelectionModeForCategory for Other: " + mode);

                        assertTrue("Retrieve incorrect SelectionMode for Other",
                                CardEmulation.SELECTION_MODE_ASK_IF_CONFLICT == mode);
                    } catch (Exception e) {
                        fail("Unexpected Exception " + e);
                    }
                });
    }

    /** Tests getAidsForPreferredPaymentService API */
    @Test
    @RequiresFlagsEnabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testAidsForPreferredPaymentService_walletRoleEnabled() {
        WalletRoleTestUtils.runWithRole(mContext, WalletRoleTestUtils.WALLET_HOLDER_PACKAGE_NAME,
                () -> {
                    try {
                        List<String> aids = mCardEmulation.getAidsForPreferredPaymentService();
                        for (String aid :aids) {
                            Log.i(mTag, "AidsForPreferredPaymentService: " + aid);
                        }

                        assertTrue("Retrieve incorrect preferred payment aid list",
                                WalletRoleTestUtils.WALLET_HOLDER_AIDS.equals(aids));
                    } catch (Exception e) {
                        fail("Unexpected Exception " + e);
                    }
                });

    }

    /** Tests getRouteDestinationForPreferredPaymentService API */
    @Test
    @RequiresFlagsEnabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testRouteDestinationForPreferredPaymentService_walletRoleEnabled() {
        WalletRoleTestUtils.runWithRole(mContext, WalletRoleTestUtils.CTS_PACKAGE_NAME,
                () -> {
                    try {
                        String routeDestination =
                                mCardEmulation.getRouteDestinationForPreferredPaymentService();
                        Log.i(mTag, "RouteDestinationForPreferredPaymentService: "
                                + routeDestination);

                        assertTrue("Retrieve incorrect preferred payment route destination",
                                routeDestination.equals(mRouteDestination));
                    } catch (Exception e) {
                        fail("Unexpected Exception " + e);
                    }
                });
    }

    /** Tests getDescriptionForPreferredPaymentService API */
    @Test
    @RequiresFlagsEnabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testDescriptionForPreferredPaymentService_walletRoleEnabled() {
        WalletRoleTestUtils.runWithRole(mContext, WalletRoleTestUtils.CTS_PACKAGE_NAME,
                () -> {
                    try {
                        CharSequence description =
                                mCardEmulation.getDescriptionForPreferredPaymentService();
                        Log.i(mTag, "DescriptionForPreferredPaymentService: "
                                + description.toString());

                        assertTrue("Retrieve incorrect preferred payment description",
                                description.toString().equals(mDescription.toString()));
                    } catch (Exception e) {
                        fail("Unexpected Exception " + e);
                    }
                });
    }

    /** Tests getSelectionModeForCategory API
     *  CardEmulation.CATEGORY_PAYMENT */
    @Test
    @RequiresFlagsEnabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testGetSelectionModeForCategoryPayment_walletRoleEnabled() {
        WalletRoleTestUtils.runWithRole(mContext, WalletRoleTestUtils.CTS_PACKAGE_NAME,
                () -> {
                    try {
                        int mode = mCardEmulation
                                .getSelectionModeForCategory(CardEmulation.CATEGORY_PAYMENT);
                        Log.i(mTag, "getSelectionModeForCategory for Payment: " + mode);

                        assertTrue("Retrieve incorrect SelectionMode for Payment",
                                CardEmulation.SELECTION_MODE_PREFER_DEFAULT == mode);
                    } catch (Exception e) {
                        fail("Unexpected Exception " + e);
                    }
                });
    }

    /** Tests getSelectionModeForCategory API
     *  CardEmulation.CATEGORY_OTHER */
    @RequiresFlagsEnabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    @Test
    public void testGetSelectionModeForCategoryOther_walletRoleEnabled() {
        WalletRoleTestUtils.runWithRole(mContext, WalletRoleTestUtils.CTS_PACKAGE_NAME,
                () -> {
                    try {
                        int mode = mCardEmulation
                                .getSelectionModeForCategory(CardEmulation.CATEGORY_OTHER);
                        Log.i(mTag, "getSelectionModeForCategory for Other: " + mode);

                        assertTrue("Retrieve incorrect SelectionMode for Other",
                                CardEmulation.SELECTION_MODE_ASK_IF_CONFLICT == mode);
                    } catch (Exception e) {
                        fail("Unexpected Exception " + e);
                    }
                });
    }
}
