/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.carrierapi.cts.targetprep;

import static com.google.common.truth.Truth.assertThat;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.Manifest;
import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.UiccSlotMapping;
import android.util.Log;
import android.util.Pair;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.UiccUtil.ApduCommand;
import com.android.compatibility.common.util.UiccUtil.ApduResponse;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class ApduScriptUtil {
    private static final String TAG = "ApduScriptUtil";

    private static final long SET_SIM_POWER_TIMEOUT_SECONDS = 30;
    private static final long APP_STATE_ADDITIONAL_WAIT_MILLIS = TimeUnit.SECONDS.toMillis(5);
    // TelephonyManager constants are @hide, so manually copy them here
    private static final int CARD_POWER_DOWN = 0;
    private static final int CARD_POWER_UP = 1;
    private static final int CARD_POWER_UP_PASS_THROUGH = 2;
    // TODO(b/236950019): Find a deterministic way to detect SIM power state change
    // from DOWN to PASS_THROUGH or vice versa and then remove the stub SIM state thoroughly.
    // Stub SIM state indicating we don't have deterministic target SIM card/app state to wait for.
    private static final int SIM_SATE_UNDEFINED = -1;

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    /**
     * Executes an APDU script over the basic channel.
     *
     * <p>The sequence of events is as follows:
     *
     * <ol>
     *   <li>Power the SIM card (as specified by {@code subId}) down
     *   <li>Power the SIM card back up in pass-through mode (see {@link
     *       TelephonyManager#CARD_POWER_UP_PASS_THROUGH})
     *   <li>Transmit {@code apdus} over the basic channel to the SIM
     *   <li>Power the SIM card down
     *   <li>Power the SIM card back up
     * </ol>
     *
     * <p>If any of the response statuses from the SIM are not {@code 9000} or {@code 91xx}, that is
     * considered an error and an exception will be thrown, terminating the script execution. {@code
     * 61xx} statuses are handled internally.
     *
     * <p>NOTE: {@code subId} must correspond to an active SIM.
     */
    public static void runApduScript(int subId, List<ApduCommand> apdus)
            throws InterruptedException {
        SubscriptionInfo sub =
                getContext()
                        .getSystemService(SubscriptionManager.class)
                        .getActiveSubscriptionInfo(subId);
        assertThat(sub).isNotNull();
        assertThat(sub.getSimSlotIndex()).isNotEqualTo(SubscriptionManager.INVALID_SIM_SLOT_INDEX);
        int logicalSlotId = sub.getSimSlotIndex();
        // We need a physical slot ID + port to send APDU to in the case when we power the SIM up in
        // pass-through mode, which will result in a temporary lack of SubscriptionInfo until we
        // restore it to the normal power mode.
        int physicalSlotId = -1;
        int portIndex = -1;
        Collection<UiccSlotMapping> slotMappings =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        getContext().getSystemService(TelephonyManager.class),
                        TelephonyManager::getSimSlotMapping,
                        Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        for (UiccSlotMapping slotMapping : slotMappings) {
            if (slotMapping.getLogicalSlotIndex() == logicalSlotId) {
                physicalSlotId = slotMapping.getPhysicalSlotIndex();
                portIndex = slotMapping.getPortIndex();
                break;
            }
        }
        if (physicalSlotId == -1 || portIndex == -1) {
            throw new IllegalStateException(
                    "Unable to determine physical slot + port from logical slot: " + logicalSlotId);
        }

        Pair<Integer, Integer> simHalVersion = getContext().getSystemService(TelephonyManager.class)
                .getHalVersion(TelephonyManager.HAL_SERVICE_SIM);
        Log.i(TAG, "runApduScript with Sim hal version: " + simHalVersion.first
                + "." + simHalVersion.second);
        boolean listenToSimCardStateChange = true;
        // After hal version 1.6, powers SIM card down will not generate SIM ABSENT or
        // SIM PRESENT events, we have to switch to listen to SIM application states instead.
        if ((simHalVersion.first == 1 && simHalVersion.second == 6) || simHalVersion.first == 2) {
            listenToSimCardStateChange = false;
        }

        try {
            // Note: Even if it won't wipe out subId after hal version 1.6, we still use the
            // slot/port-based APDU method while in pass-through mode to make compatible with
            // older hal version.
            rebootSimCard(subId, logicalSlotId, CARD_POWER_UP_PASS_THROUGH,
                    listenToSimCardStateChange, false /* isCardPowerUpPassThrough */);
            sendApdus(physicalSlotId, portIndex, apdus);
        } finally {
            // Even if rebootSimCard failed midway through (leaving the SIM in POWER_DOWN) or timed
            // out waiting for the right SIM state after rebooting in POWER_UP_PASS_THROUGH, we try
            // to bring things back to the normal POWER_UP state to avoid breaking other suites.
            rebootSimCard(subId, logicalSlotId, CARD_POWER_UP, listenToSimCardStateChange,
                    true /* isCardPowerUpPassThrough */);
        }
    }

    /**
     * Powers the SIM card down firstly and then powers it back up on the {@code
     * targetPowerState}
     *
     * Due to the RADIO HAL interface behavior changed after version 1.6, we have to
     * listen to SIM card states before hal version 1.6 and SIM application states after.
     * In specific, the behavior of the method is below:
     * <p> Before hal version 1.6, powers the SIM card down and waits for it to become
     *     ABSENT, then powers it back up in {@code targetPowerState} and waits for it to
           become PRESENT.
     * <p> After hal version 1.6, powers the SIM card down and waits for the SIM application
     *     state to become NOT_READY, then powers it back up in {@code targetPowerState} and
     *     waits for it to become NOT_READY {@code CARD_POWER_UP_PASS_THROUGH} or
     *     LOADED {@code CARD_POWER_UP}.
     *     The SIM application state keeps in NOT_READY state after simPower moving from
     *     CARD_POWER_DOWN to CARD_POWER_UP_PASS_THROUGH.
     */
    private static void rebootSimCard(int subId, int logicalSlotId, int targetPowerState,
            boolean listenToSimCardStateChange, boolean isCardPowerUpPassThrough)
            throws InterruptedException {
        if (listenToSimCardStateChange) {
            setSimPowerAndWaitForCardState(subId,
                    logicalSlotId, CARD_POWER_DOWN,
                    TelephonyManager.SIM_STATE_ABSENT, listenToSimCardStateChange);
            setSimPowerAndWaitForCardState(subId,
                    logicalSlotId, targetPowerState,
                    TelephonyManager.SIM_STATE_PRESENT, listenToSimCardStateChange);
        } else {
            // Distinguish power transition cases of UP_PASS_THROUGH->DOWN from UP->DOWN.
            // The former has no unified SIM state to monitor but the latter one has.
            setSimPowerAndWaitForCardState(subId,
                    logicalSlotId, CARD_POWER_DOWN,
                    isCardPowerUpPassThrough ? SIM_SATE_UNDEFINED
                            : TelephonyManager.SIM_STATE_NOT_READY, listenToSimCardStateChange);
            if (targetPowerState == CARD_POWER_UP) {
                setSimPowerAndWaitForCardState(subId,
                        logicalSlotId, targetPowerState,
                        TelephonyManager.SIM_STATE_LOADED, listenToSimCardStateChange);
            } else if (targetPowerState == CARD_POWER_UP_PASS_THROUGH) {
                setSimPowerAndWaitForCardState(subId,
                        logicalSlotId, targetPowerState,
                        SIM_SATE_UNDEFINED, listenToSimCardStateChange);
            }
        }
    }

    private static void setSimPowerAndWaitForCardState(
            int subId, int logicalSlotId, int targetPowerState,
            int targetSimState, boolean listenToSimCardStateChange)
            throws InterruptedException {
        // A small little state machine:
        // 1. Call setSimPower(targetPowerState)
        // 2. Wait for callback passed to setSimPower to complete, fail if not SUCCESS
        // 3. Wait for SIM state broadcast to match targetSimState
        // TODO(b/229790522) figure out a cleaner expression here.
        AtomicInteger powerResult = new AtomicInteger(Integer.MIN_VALUE);
        CountDownLatch powerLatch = new CountDownLatch(1);
        CountDownLatch cardStateLatch = new CountDownLatch(1);
        BroadcastReceiver cardStateReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if ((!TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED.equals(
                                intent.getAction())) &&
                            (!TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED.equals(
                                intent.getAction()))) {
                            return;
                        }
                        int slotId =
                                intent.getIntExtra(
                                        SubscriptionManager.EXTRA_SLOT_INDEX,
                                        SubscriptionManager.INVALID_SIM_SLOT_INDEX);
                        if (slotId != logicalSlotId) return;
                        int simState =
                                intent.getIntExtra(
                                        TelephonyManager.EXTRA_SIM_STATE,
                                        TelephonyManager.SIM_STATE_UNKNOWN);
                        if (simState == targetSimState) {
                            if (powerLatch.getCount() == 0) {
                                cardStateLatch.countDown();
                            } else {
                                Log.w(
                                        TAG,
                                        "Received SIM state "
                                                + simState
                                                + " prior to setSimPowerState callback");
                            }
                        } else {
                            Log.d(TAG, "Unwanted SIM state: " + simState);
                        }
                    }
                };

        // Since we need to listen to a broadcast that requires READ_PRIVILEGED_PHONE_STATE at
        // onReceive time, just take all the permissions we need for all our component API calls and
        // drop them at the end.
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity(
                    Manifest.permission.MODIFY_PHONE_STATE,
                    Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED);
            intentFilter.addAction(TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED);
            getContext().registerReceiver(cardStateReceiver, intentFilter);
            Log.i(
                    TAG,
                    "Setting SIM " + logicalSlotId + " power state to " + targetPowerState + "...");
            getContext()
                    .getSystemService(TelephonyManager.class)
                    .setSimPowerStateForSlot(
                            logicalSlotId,
                            targetPowerState,
                            Runnable::run,
                            result -> {
                                powerResult.set(result);
                                powerLatch.countDown();
                            });
            if (!powerLatch.await(SET_SIM_POWER_TIMEOUT_SECONDS, SECONDS)) {
                throw new IllegalStateException(
                        "Failed to receive SIM power result within "
                                + SET_SIM_POWER_TIMEOUT_SECONDS
                                + " seconds");
            } else if (powerResult.get() != TelephonyManager.SET_SIM_POWER_STATE_SUCCESS) {
                throw new IllegalStateException(
                        "Unexpected SIM power result: " + powerResult.get());
            }

            // Once the RIL request completes successfully, wait for the SIM to move to the desired
            // state (from the broadcast).
            int simApplicationState = getContext().getSystemService(TelephonyManager.class)
                    .createForSubscriptionId(subId).getSimApplicationState();
            Log.i(TAG, "Waiting for SIM " + logicalSlotId
                    + " to become " + targetSimState + " from " + simApplicationState);
            if (!listenToSimCardStateChange && ((targetSimState == simApplicationState)
                    || targetSimState == SIM_SATE_UNDEFINED)) {
                Log.w(TAG, "This SIM power transition results in an ambiguous SIM state, waiting "
                        + APP_STATE_ADDITIONAL_WAIT_MILLIS + "ms and assuming success");
                Thread.sleep(APP_STATE_ADDITIONAL_WAIT_MILLIS);
            } else if (!cardStateLatch.await(SET_SIM_POWER_TIMEOUT_SECONDS, SECONDS)) {
                throw new IllegalStateException(
                        "Failed to receive SIM state "
                                + targetSimState
                                + " within "
                                + SET_SIM_POWER_TIMEOUT_SECONDS
                                + " seconds");
            }
        } finally {
            getContext().unregisterReceiver(cardStateReceiver);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private static void sendApdus(int physicalSlotId, int portIndex, List<ApduCommand> apdus) {
        TelephonyManager telMan = getContext().getSystemService(TelephonyManager.class);

        for (int lineNum = 0; lineNum < apdus.size(); ++lineNum) {
            ApduCommand apdu = apdus.get(lineNum);
            Log.i(TAG, "APDU #" + (lineNum + 1) + ": " + apdu);

            // Format: data=response[0,len-4), sw1=response[len-4,len-2), sw2=response[len-2,len)
            String response =
                    ShellIdentityUtils.invokeMethodWithShellPermissions(
                            telMan,
                            tm ->
                                    tm.iccTransmitApduBasicChannelByPort(
                                            physicalSlotId,
                                            portIndex,
                                            apdu.cla,
                                            apdu.ins,
                                            apdu.p1,
                                            apdu.p2,
                                            apdu.p3,
                                            apdu.data),
                            Manifest.permission.MODIFY_PHONE_STATE);
            if (response == null || response.length() < 4) {
                Log.e(TAG, "  response=" + response + " (unexpected)");
                throw new IllegalStateException(
                        "Unexpected APDU response on line " + (lineNum + 1) + ": " + response);
            }
            StringBuilder responseBuilder = new StringBuilder();
            responseBuilder.append(response.substring(0, response.length() - 4));
            String lastStatusWords = response.substring(response.length() - 4);

            // If we got a 61xx status, send repeated GET RESPONSE commands until we get a different
            // status word back.
            while (ApduResponse.SW1_MORE_RESPONSE.equals(lastStatusWords.substring(0, 2))) {
                int moreResponseLength = Integer.parseInt(lastStatusWords.substring(2), 16);
                Log.i(TAG, "  fetching " + moreResponseLength + " bytes of data...");
                response =
                        ShellIdentityUtils.invokeMethodWithShellPermissions(
                                telMan,
                                tm ->
                                        tm.iccTransmitApduBasicChannelByPort(
                                                physicalSlotId,
                                                portIndex,
                                                // Use unencrypted class byte when getting more data
                                                apdu.cla & ~4,
                                                ApduCommand.INS_GET_RESPONSE,
                                                0,
                                                0,
                                                moreResponseLength,
                                                ""),
                                Manifest.permission.MODIFY_PHONE_STATE);
                if (response == null || response.length() < 4) {
                    Log.e(
                            TAG,
                            "  response="
                                    + response
                                    + " (unexpected), partialResponse="
                                    + responseBuilder.toString()
                                    + " (incomplete)");
                    throw new IllegalStateException(
                            "Unexpected APDU response on line " + (lineNum + 1) + ": " + response);
                }
                responseBuilder.append(response.substring(0, response.length() - 4));
                lastStatusWords = response.substring(response.length() - 4);
            }

            // Now check the final status after we've gotten all the data coming out of the SIM.
            String fullResponse = responseBuilder.toString();
            if (ApduResponse.SW1_SW2_OK.equals(lastStatusWords)
                    || ApduResponse.SW1_OK_PROACTIVE_COMMAND.equals(
                            lastStatusWords.substring(0, 2))) {
                // 9000 is standard "ok" status, and 91xx is "ok with pending proactive command"
                Log.i(TAG, "  response=" + fullResponse + ", statusWords=" + lastStatusWords);
            } else {
                // Anything else is considered a fatal error; stop the script and fail this
                // precondition.
                Log.e(
                        TAG,
                        "  response="
                                + fullResponse
                                + ", statusWords="
                                + lastStatusWords
                                + " (unexpected)");
                throw new IllegalStateException(
                        "Unexpected APDU response on line " + (lineNum + 1) + ": " + fullResponse);
            }
        }
    }
}
