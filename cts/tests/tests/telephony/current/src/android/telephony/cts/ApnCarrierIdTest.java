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

package android.telephony.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Telephony.Carriers;
import android.telephony.AccessNetworkConstants;
import android.telephony.PreciseDataConnectionState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.text.TextUtils;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.internal.telephony.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

/**
 * Ensures that APNs that use carrier ID instead of legacy identifiers such as MCCMNC, MVNO type and
 * match data are able to establish a data connection.
 */
@ApiTest(
        apis = {
            "android.provider.Telephony.Carriers#CONTENT_URI",
            "android.provider.Telephony.Carriers#CARRIER_ID"
        })
@RunWith(AndroidJUnit4.class)
public class ApnCarrierIdTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final Uri CARRIER_TABLE_URI = Carriers.CONTENT_URI;
    /**
     * A base selection string of columns we use to query an APN in the APN database. This excludes
     * the numeric/carrier ID.
     *
     * <p>While it would be ideal to include the Carrier.TYPE here, the ordering of APN types
     * generated from ApnSetting may not match the ordering when we query the type from the APN
     * database, which makes it more non-trivial to query using type.
     */
    private static final String BASE_APN_SELECTION_COLUMNS =
            generateSelectionString(
                    List.of(
                            Carriers.NAME,
                            Carriers.APN,
                            Carriers.PROTOCOL,
                            Carriers.ROAMING_PROTOCOL,
                            Carriers.NETWORK_TYPE_BITMASK));

    private static final String APN_SELECTION_STRING_WITH_NUMERIC =
            BASE_APN_SELECTION_COLUMNS + "AND " + Carriers.NUMERIC + "=?";
    private static final String APN_SELECTION_STRING_WITH_CARRIER_ID =
            BASE_APN_SELECTION_COLUMNS + "AND " + Carriers.CARRIER_ID + "=?";

    // The wait time is padded to account for varying modem performance. Note that this is a
    // timeout, not an enforced wait time, so in most cases, a callback will be received prior to
    // the wait time elapsing.
    private static final long WAIT_TIME_MILLIS = 10000L;

    private Context mContext;
    private ContentResolver mContentResolver;

    private final Executor mSimpleExecutor = Runnable::run;

    private TelephonyManager mTelephonyManager;
    private PreciseDataConnectionState mPreciseDataConnectionState;

    /**
     * The original APN that belongs to the existing data connection. Required to re-insert it
     * during teardown.
     */
    private ContentValues mExistingApn;
    /** Selection args for the carrier ID APN. Required to delete the test APN during teardown. */
    private String[] mInsertedApnSelectionArgs;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mContext.getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY_DATA));
        } else {
            assumeTrue(mContext.getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY));
        }
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);

        if (mTelephonyManager.getSimState() != TelephonyManager.SIM_STATE_READY
                || mTelephonyManager.getSubscriptionId()
                        == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            fail("This test requires a SIM card with an active subscription/data connection.");
        }

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity();
        PreciseDataConnectionStateListener preciseDataConnectionStateCallback =
                new PreciseDataConnectionStateListener(
                        mTelephonyManager, /* desiredDataState= */ TelephonyManager.DATA_CONNECTED);
        preciseDataConnectionStateCallback.awaitDataStateChanged(WAIT_TIME_MILLIS);

        // The initial data state should be DATA_CONNECTED.
        if (mPreciseDataConnectionState == null
                || mPreciseDataConnectionState.getState() != TelephonyManager.DATA_CONNECTED) {
            fail("This test requires an active data connection.");
        }

        mContentResolver = mContext.getContentResolver();
    }

    @After
    public void tearDown() {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            if (!mContext.getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY_DATA)) {
                return;
            }
        } else {
            if (!mContext.getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY)) {
                return;
            }
        }

        if (mInsertedApnSelectionArgs != null) {
            int deleted =
                    mContentResolver.delete(
                            CARRIER_TABLE_URI,
                            APN_SELECTION_STRING_WITH_CARRIER_ID,
                            mInsertedApnSelectionArgs);
        }
        if (mExistingApn != null) {
            PreciseDataConnectionStateListener pdcsCallback =
                    new PreciseDataConnectionStateListener(
                            mTelephonyManager,
                            /* desiredDataState= */ TelephonyManager.DATA_CONNECTED);
            mContentResolver.insert(CARRIER_TABLE_URI, mExistingApn);
            try {
                pdcsCallback.awaitDataStateChanged(WAIT_TIME_MILLIS);
            } catch (InterruptedException e) {
                // do nothing - we just want to ensure the teardown is complete.
            }
        }

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    /**
     * Ensures that APNs that consist of a carrier ID column and no other identifying columns such
     * as MCCMNC/numeric can establish a data connection.
     */
    @Test
    public void validateDataConnectionWithCarrierIdApn() throws Exception {
        ApnSetting currentApn = mPreciseDataConnectionState.getApnSetting();
        validateAndSetupInitialState(currentApn);
        int carrierId = mTelephonyManager.getSimSpecificCarrierId();
        ContentValues apnWithCarrierId = getApnWithCarrierId(currentApn, carrierId);

        // Insert the carrier ID APN.
        mPreciseDataConnectionState = null;
        PreciseDataConnectionStateListener pdcsCallback =
                new PreciseDataConnectionStateListener(
                        mTelephonyManager, /* desiredDataState= */ TelephonyManager.DATA_CONNECTED);
        int rowsInserted =
                mContentResolver.bulkInsert(
                        CARRIER_TABLE_URI, new ContentValues[] {apnWithCarrierId});
        assertThat(rowsInserted).isEqualTo(1);
        pdcsCallback.awaitDataStateChanged(WAIT_TIME_MILLIS);
        // Generate selection arguments for the APN and store it so we can delete it in cleanup.
        mInsertedApnSelectionArgs = generateSelectionArgs(currentApn, String.valueOf(carrierId));

        // Ensure our APN value wasn't somehow overridden (such as in the event a carrier app
        // exists).
        assertThat(mPreciseDataConnectionState.getApnSetting().getCarrierId()).isEqualTo(carrierId);
        assertThat(mPreciseDataConnectionState.getState())
                .isEqualTo(TelephonyManager.DATA_CONNECTED);
    }

    /**
     * Performs initial setup and validation for the test.
     *
     * <p>This skips the test if the existing APN already uses carrier ID. Otherwise, it deletes the
     * existing APN and ensures data is disconnected.
     */
    private void validateAndSetupInitialState(ApnSetting currentApn) throws Exception {
        // Skip the test if the APN already uses carrier ID and data is connected.
        assumeFalse(
                "Skipping the test as the APN on the current SIM already uses carrier ID and has a"
                        + " data connection.",
                apnAlreadyUsesCarrierId(currentApn));

        mPreciseDataConnectionState = null;
        PreciseDataConnectionStateListener pdcsCallback =
                new PreciseDataConnectionStateListener(
                        mTelephonyManager,
                        /* desiredDataState= */ TelephonyManager.DATA_DISCONNECTED);
        int deletedRowCount =
                mContentResolver.delete(
                        CARRIER_TABLE_URI,
                        APN_SELECTION_STRING_WITH_NUMERIC,
                        generateSelectionArgs(currentApn, currentApn.getOperatorNumeric()));
        assertThat(deletedRowCount).isEqualTo(1);
        // Store the APN so we can re-insert it once the test is complete.
        mExistingApn = currentApn.toContentValues();
        pdcsCallback.awaitDataStateChanged(WAIT_TIME_MILLIS);

        // Data should disconnect without any identifying fields in the default APN.
        assertThat(mPreciseDataConnectionState.getState())
                .isEqualTo(TelephonyManager.DATA_DISCONNECTED);
    }

    private boolean apnAlreadyUsesCarrierId(ApnSetting apnSetting) {
        return apnSetting.getCarrierId() != TelephonyManager.UNKNOWN_CARRIER_ID
                && TextUtils.isEmpty(apnSetting.getOperatorNumeric());
    }

    /**
     * Replaces the existing APNs identifying fields with carrier ID and returns it as a
     * ContentValues object.
     */
    private ContentValues getApnWithCarrierId(ApnSetting apnSetting, int carrierId) {
        ContentValues apnWithCarrierId = ApnSetting.makeApnSetting(apnSetting).toContentValues();
        // Remove non carrier ID identifying fields and insert the carrier ID.
        List<String> identifyingColumnsToDelete =
                List.of(
                        Carriers.NUMERIC,
                        Carriers.MCC,
                        Carriers.MNC,
                        Carriers.MVNO_TYPE,
                        Carriers.MVNO_MATCH_DATA);
        for (String identifyingColumn : identifyingColumnsToDelete) {
            apnWithCarrierId.remove(identifyingColumn);
        }
        apnWithCarrierId.put(Carriers.CARRIER_ID, carrierId);
        return apnWithCarrierId;
    }

    /** Generates a selection string for matching the given coluns in a database. */
    private static String generateSelectionString(List<String> columns) {
        return String.join("=? AND ", columns) + "=?";
    }

    /**
     * Generates selection arguments for an APN.
     *
     * <p>The selection arguments are based on {@link #BASE_APN_SELECTION_COLUMNS} with the final
     * argument being either the carrier ID or the numeric to match {@link
     * #APN_SELECTION_STRING_WITH_NUMERIC} or {@link #APN_SELECTION_STRING_WITH_CARRIER_ID}.
     */
    private String[] generateSelectionArgs(ApnSetting baseApn, String numericOrCarrierId) {
        return new String[] {
            baseApn.getEntryName(),
            baseApn.getApnName(),
            ApnSetting.getProtocolStringFromInt(baseApn.getProtocol()),
            ApnSetting.getProtocolStringFromInt(baseApn.getRoamingProtocol()),
            Integer.toString(baseApn.getNetworkTypeBitmask()),
            numericOrCarrierId,
        };
    }

    /**
     * A oneshot PreciseDataConnectionState listener that listens for a desired data state change on
     * a cellular network.
     *
     * <p>The listener will register itself once instantiated and will unregister itself after
     * calling {@link PreciseDataConnectionStateListener#awaitDataStateChanged}
     */
    private class PreciseDataConnectionStateListener extends TelephonyCallback
            implements TelephonyCallback.PreciseDataConnectionStateListener {
        private final int mDesiredDataState;

        private final CountDownLatch mCountDownLatch = new CountDownLatch(1);
        private final Object mLock = new Object();
        /**
         * Instantiates and registers a PreciseDataConnectionStateListener instance.
         *
         * @param telephonyManager the TelephonyManager instance to register the callback on
         * @param desiredDataState the data state that is expected after performing an action. A
         *     callback will only be fired for this state. See {@link
         *     #onPreciseDataConnectionStateChanged(PreciseDataConnectionState)} for additional
         *     information.
         */
        PreciseDataConnectionStateListener(
                TelephonyManager telephonyManager, int desiredDataState) {
            mDesiredDataState = desiredDataState;
            mPreciseDataConnectionState = null;
            telephonyManager.registerTelephonyCallback(mSimpleExecutor, this);
        }

        void awaitDataStateChanged(long timeoutMillis) throws InterruptedException {
            try {
                mCountDownLatch.await(timeoutMillis, MILLISECONDS);
            } finally {
                mTelephonyManager.unregisterTelephonyCallback(this);
            }
        }

        @Override
        public void onPreciseDataConnectionStateChanged(PreciseDataConnectionState state) {
            synchronized (mLock) {
                ApnSetting apnSetting = state.getApnSetting();
                int dataState = state.getState();
                // We should only notify if the following conditions are satisfied:
                // 1. The PDCS belongs to a cellular network.
                // 2. The APN attached to the PDCS is an internet APN.
                // 3. The state is the desired data state.
                boolean isInternetApn =
                        (state.getApnSetting().getApnTypeBitmask() & ApnSetting.TYPE_DEFAULT)
                                == ApnSetting.TYPE_DEFAULT;
                if (isInternetApn
                        && state.getTransportType() == AccessNetworkConstants.TRANSPORT_TYPE_WWAN
                        && dataState == mDesiredDataState) {
                    mPreciseDataConnectionState = state;
                    mCountDownLatch.countDown();
                }
            }
        }
    }
}
