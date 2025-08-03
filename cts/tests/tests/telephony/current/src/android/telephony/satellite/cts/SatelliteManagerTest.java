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

package android.telephony.satellite.cts;

import static android.telephony.satellite.SatelliteManager.SATELLITE_COMMUNICATION_RESTRICTION_REASON_GEOLOCATION;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.satellite.EnableRequestAttributes;
import android.telephony.satellite.NtnSignalStrength;
import android.telephony.satellite.NtnSignalStrengthCallback;
import android.telephony.satellite.SatelliteCapabilities;
import android.telephony.satellite.SatelliteCapabilitiesCallback;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.internal.telephony.flags.Flags;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class SatelliteManagerTest extends SatelliteManagerTestBase {
    private static boolean sIsSatelliteSupported = false;
    private static boolean sOriginalIsSatelliteEnabled = false;
    private static boolean sOriginalIsSatelliteProvisioned = false;
    private static boolean sIsProvisionable = false;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        logd("beforeAllTests");

        if (!shouldTestSatellite()) return;

        beforeAllTestsBase();

        grantSatellitePermission();
        sIsSatelliteSupported = isSatelliteSupported();
        if (sIsSatelliteSupported) {
            sOriginalIsSatelliteProvisioned = isSatelliteProvisioned();

            boolean provisioned = sOriginalIsSatelliteProvisioned;
            if (!sOriginalIsSatelliteProvisioned) {
                SatelliteProvisionStateCallbackTest satelliteProvisionStateCallback =
                        new SatelliteProvisionStateCallbackTest();
                long registerError = sSatelliteManager.registerForProvisionStateChanged(
                        getContext().getMainExecutor(), satelliteProvisionStateCallback);
                assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerError);

                provisioned = provisionSatellite();
                if (provisioned) {
                    sIsProvisionable = true;
                    assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
                    assertTrue(satelliteProvisionStateCallback.isProvisioned);
                }
                sSatelliteManager.unregisterForProvisionStateChanged(
                        satelliteProvisionStateCallback);
            }

            sOriginalIsSatelliteEnabled = isSatelliteEnabled();
            if (provisioned && !sOriginalIsSatelliteEnabled) {
                logd("Enable satellite");

                SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
                long registerResult = sSatelliteManager.registerForModemStateChanged(
                        getContext().getMainExecutor(), callback);
                assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
                assertTrue(callback.waitUntilResult(1));

                requestSatelliteEnabled(true);

                assertTrue(callback.waitUntilResult(1));
                assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);
                assertTrue(isSatelliteEnabled());
                sSatelliteManager.unregisterForModemStateChanged(callback);
            }
        }
        revokeSatellitePermission();
    }

    @AfterClass
    public static void afterAllTests() {
        logd("afterAllTests");

        if (!shouldTestSatellite()) return;

        grantSatellitePermission();
        if (sIsSatelliteSupported) {
            logd("Restore enabled state");
            if (isSatelliteEnabled() != sOriginalIsSatelliteEnabled) {
                SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
                long registerResult = sSatelliteManager.registerForModemStateChanged(
                        getContext().getMainExecutor(), callback);
                assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
                assertTrue(callback.waitUntilResult(1));

                requestSatelliteEnabled(sOriginalIsSatelliteEnabled);

                assertTrue(callback.waitUntilResult(1));
                assertEquals(sOriginalIsSatelliteEnabled
                        ? SatelliteManager.SATELLITE_MODEM_STATE_IDLE :
                        SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
                assertTrue(isSatelliteEnabled() == sOriginalIsSatelliteEnabled);
                sSatelliteManager.unregisterForModemStateChanged(callback);
            }

            logd("Restore provision state");
            SatelliteProvisionStateCallbackTest satelliteProvisionStateCallback =
                    new SatelliteProvisionStateCallbackTest();
            long registerError = sSatelliteManager.registerForProvisionStateChanged(
                    getContext().getMainExecutor(), satelliteProvisionStateCallback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerError);

            if (sOriginalIsSatelliteProvisioned) {
                if (!isSatelliteProvisioned()) {
                    boolean provisioned = provisionSatellite();
                    if (provisioned) {
                        sIsProvisionable = true;
                        assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
                        assertTrue(satelliteProvisionStateCallback.isProvisioned);
                    }
                }
            } else {
                if (isSatelliteProvisioned()) {
                    assertTrue(deprovisionSatellite());
                    assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
                    assertFalse(satelliteProvisionStateCallback.isProvisioned);
                }
            }
            sSatelliteManager.unregisterForProvisionStateChanged(
                    satelliteProvisionStateCallback);
        }
        revokeSatellitePermission();

        afterAllTestsBase();
    }
    @Before
    public void setUp() throws Exception {
        logd("setUp");

        if (!shouldTestSatellite()) return;
        assumeTrue(sSatelliteManager != null);
    }

    @After
    public void tearDown() {
        logd("tearDown");
    }

    @Test
    public void testSatelliteTransmissionUpdates() throws Exception {
        if (!shouldTestSatellite()) return;

        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest callback =
                new SatelliteTransmissionUpdateCallbackTest();

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> sSatelliteManager.startTransmissionUpdates(
                        getContext().getMainExecutor(), error::offer, callback));

        grantSatellitePermission();
        sSatelliteManager.startTransmissionUpdates(
                getContext().getMainExecutor(), error::offer, callback);
        Integer errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        assertNotNull(errorCode);
        if (errorCode == SatelliteManager.SATELLITE_RESULT_SUCCESS) {
            Log.d(TAG, "Successfully started transmission updates.");
        } else {
            Log.d(TAG, "Failed to start transmission updates: " + errorCode);
        }
        getContext().getSystemService(SatelliteManager.class).stopTransmissionUpdates(
                callback, getContext().getMainExecutor(), error::offer);
        errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        assertNotNull(errorCode);
        Log.d(TAG, "Stop transmission updates: " + errorCode);

        sSatelliteManager.stopTransmissionUpdates(
                callback, getContext().getMainExecutor(), error::offer);
        errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        assertNotNull(errorCode);
        assertEquals(SatelliteManager.SATELLITE_RESULT_INVALID_ARGUMENTS, (long) errorCode);
        revokeSatellitePermission();
    }

    @Test
    public void testProvisionSatelliteService() {
        if (!shouldTestSatellite()) return;

        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        String mText = "This is test provision data.";
        byte[] testProvisionData = mText.getBytes();

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> sSatelliteManager.provisionService(
                        "", testProvisionData, null,
                        getContext().getMainExecutor(), error::offer));

        if (!sIsSatelliteSupported) {
            Log.d(TAG, "testProvisionSatelliteService: Satellite is not supported "
                    + "on the device.");
            return;
        }

        grantSatellitePermission();

        SatelliteProvisionStateCallbackTest satelliteProvisionStateCallback =
                new SatelliteProvisionStateCallbackTest();
        long registerError = sSatelliteManager.registerForProvisionStateChanged(
                getContext().getMainExecutor(), satelliteProvisionStateCallback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerError);

        if (isSatelliteProvisioned()) {
            if (!deprovisionSatellite()) {
                Log.d(TAG, "testProvisionSatelliteService: deprovisionSatellite failed");
                return;
            }
            assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
            assertFalse(satelliteProvisionStateCallback.isProvisioned);
        }

        CancellationSignal cancellationSignal = new CancellationSignal();
        sSatelliteManager.provisionService(TOKEN, testProvisionData, cancellationSignal,
                getContext().getMainExecutor(), error::offer);
        cancellationSignal.cancel();
        Integer errorCode;
        try {
            errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testProvisionSatelliteService: Got InterruptedException ex=" + ex);
            return;
        }
        assertNotNull(errorCode);
        Log.d(TAG, "testProvisionSatelliteService: provision result=" + errorCode);

        if (sIsProvisionable) {
            /**
             * Modem might send either 1 or 2 provision state change events.
             */
            satelliteProvisionStateCallback.waitUntilResult(2);
            assertFalse(satelliteProvisionStateCallback.isProvisioned);
            assertFalse(isSatelliteProvisioned());

            assertTrue(provisionSatellite());
            assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
            assertTrue(satelliteProvisionStateCallback.isProvisioned);
            assertTrue(isSatelliteProvisioned());
        }
        sSatelliteManager.unregisterForProvisionStateChanged(
                satelliteProvisionStateCallback);

        revokeSatellitePermission();
    }

    @Test
    public void testDeprovisionSatelliteService() {
        if (!shouldTestSatellite()) return;

        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> sSatelliteManager.deprovisionService(
                        "", getContext().getMainExecutor(), error::offer));
    }

    @Test
    public void testRegisterForSatelliteProvisionStateChanged() {
        if (!shouldTestSatellite()) return;

        SatelliteProvisionStateCallbackTest satelliteProvisionStateCallback =
                new SatelliteProvisionStateCallbackTest();

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> sSatelliteManager.registerForProvisionStateChanged(
                        getContext().getMainExecutor(), satelliteProvisionStateCallback));
    }

    @Test
    public void testRequestIsSatelliteProvisioned() {
        if (!shouldTestSatellite()) return;

        final AtomicReference<Boolean> provisioned = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        provisioned.set(result);
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                    }
                };

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> sSatelliteManager.requestIsProvisioned(
                        getContext().getMainExecutor(), receiver));
    }

    @Test
    public void testRequestSatelliteEnabled() throws Exception {
        if (!shouldTestSatellite()) return;

        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, () -> sSatelliteManager.requestEnabled(
                new EnableRequestAttributes.Builder(true).build(),
                getContext().getMainExecutor(), error::offer));
        assertThrows(SecurityException.class, () -> sSatelliteManager.requestEnabled(
                new EnableRequestAttributes.Builder(false).build(),
                getContext().getMainExecutor(), error::offer));
    }

    @Test
    public void testRequestIsSatelliteEnabled() {
        if (!shouldTestSatellite()) return;

        final AtomicReference<Boolean> enabled = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        Log.d(TAG, "onResult: result=" + result);
                        enabled.set(result);
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        Log.d(TAG, "onError: onError=" + exception);
                        errorCode.set(exception.getErrorCode());
                    }
                };

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, () -> sSatelliteManager.requestIsEnabled(
                getContext().getMainExecutor(), receiver));
    }

    @Test
    public void testRequestIsSatelliteSupported() throws Exception {
        if (!shouldTestSatellite()) return;

        final AtomicReference<Boolean> supported = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        supported.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.requestIsSupported(getContext().getMainExecutor(),
                receiver);
        assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));

        Integer error = errorCode.get();
        Boolean isSupported = supported.get();
        if (error == null) {
            assertNotNull(isSupported);
            Log.d(TAG, "testRequestIsSatelliteSupported isSupported=" + isSupported);
        } else {
            assertNull(isSupported);
            Log.d(TAG, "testRequestIsSatelliteSupported error=" + error);
        }
    }

    @Test
    public void testRequestSatelliteCapabilities() {
        if (!shouldTestSatellite()) return;

        final AtomicReference<SatelliteCapabilities> capabilities = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<SatelliteCapabilities, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(SatelliteCapabilities result) {
                        capabilities.set(result);
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                    }
                };

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, () -> sSatelliteManager.requestCapabilities(
                getContext().getMainExecutor(), receiver));
    }

    @Test
    public void testSatelliteModemStateChanged() {
        if (!shouldTestSatellite()) return;

        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, ()-> sSatelliteManager
                .registerForModemStateChanged(getContext().getMainExecutor(), callback));

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, ()-> sSatelliteManager
                .unregisterForModemStateChanged(callback));

        if (!sIsSatelliteSupported) {
            Log.d(TAG, "Satellite is not supported on the device");
            return;
        }

        grantSatellitePermission();

        if (!isSatelliteProvisioned()) {
            Log.d(TAG, "Satellite is not provisioned yet");
            return;
        }

        boolean originalEnabledState = isSatelliteEnabled();
        boolean registerCallback = false;
        if (originalEnabledState) {
            registerCallback = true;

            long registerResult = sSatelliteManager.registerForModemStateChanged(
                    getContext().getMainExecutor(), callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));

            requestSatelliteEnabled(false);

            assertTrue(callback.waitUntilResult(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
        }
        if (!registerCallback) {
            long registerResult = sSatelliteManager
                    .registerForModemStateChanged(getContext().getMainExecutor(),
                            callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
        }

        requestSatelliteEnabled(true);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);
        assertTrue(isSatelliteEnabled());

        SatelliteModemStateCallbackTest callback1 = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager
                .registerForModemStateChanged(getContext().getMainExecutor(), callback1);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback1.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback1.modemState);
        sSatelliteManager.unregisterForModemStateChanged(callback);

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        String mText = "This is a test datagram message";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        callback1.clearModemStates();
        sSatelliteManager.sendDatagram(
                SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE, datagram, true,
                getContext().getMainExecutor(), resultListener::offer);

        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSatelliteModemStateChanged: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        Log.d(TAG, "testSatelliteModemStateChanged: sendDatagram errorCode="
                + errorCode);

        assertFalse(callback.waitUntilResult(1));
        assertTrue(callback1.waitUntilResult(2));
        assertTrue(callback1.getTotalCountOfModemStates() >= 2);
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback1.getModemState(0));
        if (errorCode == SatelliteManager.SATELLITE_RESULT_SUCCESS) {
            /**
             * Modem state should have the following transitions:
             * 1) IDLE to TRANSFERRING.
             * 2) TRANSFERRING to LISTENING.
             * 3) LISTENING to IDLE
             */
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_LISTENING,
                    callback1.getModemState(1));
            /**
             * Satellite will stay at LISTENING mode for 3 minutes by default. Thus, we will skip
             * checking the last state transition.
             */
        } else {
            /**
             * Modem state should have the following transitions:
             * 1) IDLE to TRANSFERRING.
             * 2) TRANSFERRING to IDLE.
             */
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE,
                    callback1.getModemState(1));
        }

        if (!originalEnabledState) {
            // Restore original modem enabled state.
            requestSatelliteEnabled(false);

            assertFalse(callback.waitUntilResult(1));
            assertTrue(callback1.waitUntilResult(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback1.modemState);
            assertFalse(isSatelliteEnabled());
        }
        sSatelliteManager.unregisterForModemStateChanged(callback1);

        revokeSatellitePermission();
    }

    @Test
    public void testSatelliteDatagramCallback() throws Exception {
        if (!shouldTestSatellite()) return;

        SatelliteDatagramCallbackTest callback = new SatelliteDatagramCallbackTest();

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, ()-> sSatelliteManager
                .registerForIncomingDatagram(getContext().getMainExecutor(), callback));

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, ()-> sSatelliteManager
                .unregisterForIncomingDatagram(callback));
    }

    @Test
    public void testPollPendingSatelliteDatagrams() throws Exception {
        if (!shouldTestSatellite()) return;

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                ()-> sSatelliteManager.pollPendingDatagrams(
                        getContext().getMainExecutor(), resultListener::offer));
    }

    @Test
    public void testSendSatelliteDatagram() {
        if (!shouldTestSatellite()) return;

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);

        String mText = "This is a test datagram message";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                ()-> sSatelliteManager.sendDatagram(
                        SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE, datagram, true,
                        getContext().getMainExecutor(), resultListener::offer));
        // TODO: add detailed test
    }

    @Test
    public void testRequestIsSatelliteCommunicationAllowedForCurrentLocation() {
        if (!shouldTestSatellite()) return;

        final AtomicReference<Boolean> enabled = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        enabled.set(result);
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                    }
                };

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, () -> sSatelliteManager.requestIsEnabled(
                getContext().getMainExecutor(), receiver));
    }

    @Test
    public void testRequestTimeForNextSatelliteVisibility() {
        if (!shouldTestSatellite()) return;

        final AtomicReference<Duration> nextVisibilityDuration = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<Duration, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Duration result) {
                        nextVisibilityDuration.set(result);
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                    }
                };

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> sSatelliteManager.requestTimeForNextSatelliteVisibility(
                        getContext().getMainExecutor(), receiver));
    }

    @Test
    public void testRequestSatelliteAttachEnabledForCarrier() throws Exception {
        if (!shouldTestSatellite()) return;

        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> sSatelliteManager.requestAttachEnabledForCarrier(
                        getActiveSubIDForCarrierSatelliteTest(), true,
                        getContext().getMainExecutor(), error::offer));
        assertThrows(SecurityException.class,
                () -> sSatelliteManager.requestAttachEnabledForCarrier(
                        getActiveSubIDForCarrierSatelliteTest(), false,
                        getContext().getMainExecutor(), error::offer));
    }

    @Test
    public void testRequestIsSatelliteAttachEnabledForCarrier() {
        if (!shouldTestSatellite()) return;

        final AtomicReference<Boolean> enabled = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        Log.d(TAG, "onResult: result=" + result);
                        enabled.set(result);
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        Log.d(TAG, "onError: onError=" + exception);
                        errorCode.set(exception.getErrorCode());
                    }
                };

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> sSatelliteManager.requestIsAttachEnabledForCarrier(
                        getActiveSubIDForCarrierSatelliteTest(),
                        getContext().getMainExecutor(), receiver));
    }

    @Test
    public void testAddSatelliteAttachRestrictionForCarrier() {
        if (!shouldTestSatellite()) return;

        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> sSatelliteManager.addAttachRestrictionForCarrier(
                        getActiveSubIDForCarrierSatelliteTest(),
                        SATELLITE_COMMUNICATION_RESTRICTION_REASON_GEOLOCATION,
                        getContext().getMainExecutor(), error::offer));
    }

    @Test
    public void testRemoveSatelliteAttachRestrictionForCarrier() {
        if (!shouldTestSatellite()) return;

        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> sSatelliteManager.removeAttachRestrictionForCarrier(
                        getActiveSubIDForCarrierSatelliteTest(),
                        SATELLITE_COMMUNICATION_RESTRICTION_REASON_GEOLOCATION,
                        getContext().getMainExecutor(), error::offer));
    }

    @Test
    public void testGetSatelliteCommunicationRestrictionReasons() {
        if (!shouldTestSatellite()) return;

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> sSatelliteManager.getAttachRestrictionReasonsForCarrier(
                        getActiveSubIDForCarrierSatelliteTest()));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_OEM_ENABLED_SATELLITE_FLAG)
    public void testRequestNtnSignalStrength() {
        if (!shouldTestSatellite()) return;

        final AtomicReference<NtnSignalStrength> enabled = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<NtnSignalStrength, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(NtnSignalStrength result) {
                        Log.d(TAG, "onResult: result=" + result);
                        enabled.set(result);
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        Log.d(TAG, "onError: onError=" + exception);
                        errorCode.set(exception.getErrorCode());
                    }
                };
        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> sSatelliteManager.requestNtnSignalStrength(getContext().getMainExecutor(),
                        receiver));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_OEM_ENABLED_SATELLITE_FLAG)
    public void testRegisterForNtnSignalStrengthChanged() {
        if (!shouldTestSatellite()) return;

        NtnSignalStrengthCallback callback = new NtnSignalStrengthCallback() {
            @Override
            public void onNtnSignalStrengthChanged(@NonNull NtnSignalStrength ntnSignalStrength) {
                logd("onNtnSignalStrengthChanged(" + ntnSignalStrength + ")");
            }
        };

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> sSatelliteManager.registerForNtnSignalStrengthChanged(
                        getContext().getMainExecutor(), callback));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_OEM_ENABLED_SATELLITE_FLAG)
    public void testUnregisterForNtnSignalStrengthChanged() {
        if (!shouldTestSatellite()) return;

        NtnSignalStrengthCallback callback = new NtnSignalStrengthCallback() {
            @Override
            public void onNtnSignalStrengthChanged(@NonNull NtnSignalStrength ntnSignalStrength) {
                logd("onNtnSignalStrengthChanged(" + ntnSignalStrength + ")");
            }
        };

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> sSatelliteManager.registerForNtnSignalStrengthChanged(
                        getContext().getMainExecutor(), callback));
        assertThrows(SecurityException.class,
                () -> sSatelliteManager.unregisterForNtnSignalStrengthChanged(callback));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_OEM_ENABLED_SATELLITE_FLAG)
    public void testRegisterForSatelliteCapabilitiesChanged() {
        if (!shouldTestSatellite()) return;

        SatelliteCapabilitiesCallback callback =
                capabilities -> logd("onSatelliteCapabilitiesChanged(" + capabilities + ")");

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> sSatelliteManager.registerForCapabilitiesChanged(
                        getContext().getMainExecutor(), callback));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_OEM_ENABLED_SATELLITE_FLAG)
    public void testUnregisterForSatelliteCapabilitiesChanged() {
        if (!shouldTestSatellite()) return;

        SatelliteCapabilitiesCallback callback =
                capabilities -> logd("onSatelliteCapabilitiesChanged(" + capabilities + ")");

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> sSatelliteManager.registerForCapabilitiesChanged(
                        getContext().getMainExecutor(), callback));
        assertThrows(SecurityException.class,
                () -> sSatelliteManager.unregisterForCapabilitiesChanged(callback));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ENABLED_SATELLITE_FLAG)
    public void testGetAggregateSatellitePlmnListForCarrier() {
        if (!shouldTestSatellite()) return;

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> sSatelliteManager.getSatellitePlmnsForCarrier(
                        getActiveSubIDForCarrierSatelliteTest()));
    }
}
