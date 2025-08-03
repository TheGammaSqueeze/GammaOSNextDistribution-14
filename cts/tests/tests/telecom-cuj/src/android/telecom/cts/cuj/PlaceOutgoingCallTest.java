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

package android.telecom.cts.cuj;

import android.app.Notification.CallStyle;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.OutcomeReceiver;
import android.service.notification.NotificationListenerService;
import android.telecom.CallAttributes;
import android.telecom.CallControlCallback;
import android.telecom.CallEventCallback;
import android.telecom.TelecomManager;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;

/**
 * <h1>Purpose</h1>
 * <p class="purpose">
 *    These tests verify that a user is able to place outgoing calls successfully.
 * </p>
 * <p>
 *    These test cases are run once for each call type {@code X}:
 *    <ul>
 *        <li>PSTN / managed - these calls integrate with the system dialer</li>
 *        <li>Self-managed (i.e. {@link android.telecom.PhoneAccount#CAPABILITY_SELF_MANAGED}
 *        {@link android.telecom.ConnectionService})</li>
 *        <li>Telecom VoIP V2 (i.e.
 *        {@link TelecomManager#addCall(CallAttributes, Executor, OutcomeReceiver,
 *        CallControlCallback, CallEventCallback)}</li>
 *    </ul>
 * </p>
 */
@RunWith(AndroidJUnit4.class)
public class PlaceOutgoingCallTest {
    /**
     * Test the nominal case for placing an outgoing call of type {@code X} while there are no other
     * calls on the device.
     *
     * <h3>Test Parameters</h3>
     * <ul>
     *     <li>{@code X} - Managed ConnectionService, ConnectionService VoIP, Transactional
     *     VoIP</li>
     * </ul>
     *
     * <h3>Test Conditions</h3>
     * <table>
     *     <tr>
     *         <td><strong>Calls</strong></td>
     *         <td>No other ongoing calls.</td>
     *     </tr>
     *     <tr>
     *         <td><strong>Audio Peripherals</strong></td>
     *         <td>No audio peripherals are connected.</td>
     *     </tr>
     * </table>
     *
     * <h3>Test Steps</h3>
     * <table>
     *     <tr>
     *         <td><strong>Step</strong></td>
     *         <td><strong>User Action</strong></td>
     *         <td><strong>App API Action</strong></td>
     *         <td><strong>Functional Validation</strong></td>
     *     </tr>
     *     <tr>
     *         <td>1</td>
     *         <td>Start outgoing call.</td>
     *         <td>Start call with {@link TelecomManager}</td>
     *         <td>Call starts.</td>
     *     </tr>
     *     <tr>
     *         <td>2</td>
     *         <td></td>
     *         <td>Post {@link CallStyle} notification.</td>
     *         <td>An ongoing call notification is visible.</td>
     *     </tr>
     *     <tr>
     *         <td>3</td>
     *         <td></td>
     *         <td>Start call media record/playback.</td>
     *         <td>
     *             <ul>
     *                 <li>Microphone is not silenced.</li>
     *                 <li>Inbound audio can be heard.</li>
     *                 <li>Audio is routed to baseline route (earpiece).</li>
     *             </ul>
     *         </td>
     *     </tr>
     *     <tr>
     *         <td>5</td>
     *         <td>Disconnect call</td>
     *         <td>Inform Telecom of call disconnection.</td>
     *         <td>
     *             <ul>
     *                  <li>Audio mode returns to "normal".  Volume control does not default to
     *                  call audio.</li>
     *                  <li>Call loses focus.  Media playback can resume.</li>
     *                  <li>Call style notification no longer present.</li>
     *             </ul>
     *         </td>
     *     </tr>
     * </table>
     *
     * <h3>Design notes:</h3>
     * <ol>
     *     <li> {@link TelecomManager#isInCall()} is {@code true}.</li>
     *     <li>Verify {@link CallStyle} notification posted ; {@link NotificationListenerService}
     *     </li>
     *     <li>{@link AudioManager#getMode} is {@link AudioManager#MODE_IN_COMMUNICATION}.
     *         {@link AudioRecord} is not silenced. {@link AudioTrack} has focus.
     *         {@link AudioManager#getCommunicationDevice()} is baseline route.</li>
     *     <li>{@link AudioManager#getMode} is {@link AudioManager#MODE_NORMAL},
     *     {@link TelecomManager#isInCall()} is {@code false}.
     * </ol>
     */
    @Test
    public void testPlaceCallNominal() {
        // TODO: Write the test
    }

    /**
     * Test the case where an outgoing call of type {@code X} is placed while there is another
     * ongoing call of type {@code Y} that cannot be held.
     *
     * <h3>Test Parameters</h3>
     * <ul>
     *     <li>{@code X} - Managed ConnectionService, ConnectionService VoIP, Transactional
     *     VoIP</li>
     *     <li>{@code Y} - Managed ConnectionService, ConnectionService VoIP, Transactional
     *     VoIP</li>
     * </ul>
     *
     * <h3>Test Conditions</h3>
     * <table>
     *     <tr>
     *         <td><strong>Calls</strong></td>
     *         <td>No other ongoing calls.</td>
     *     </tr>
     *     <tr>
     *         <td><strong>Audio Peripherals</strong></td>
     *         <td>No audio peripherals are connected.</td>
     *     </tr>
     * </table>
     *
     * <h3>Test Steps</h3>
     * <table>
     *     <tr>
     *         <td><strong>Step</strong></td>
     *         <td><strong>User Action</strong></td>
     *         <td><strong>App API Action</strong></td>
     *         <td><strong>Functional Validation</strong></td>
     *     </tr>
     *     <tr>
     *         <td>1</td>
     *         <td>Start outgoing call {@code Y} and make it active.</td>
     *         <td>Start call with {@link TelecomManager}, set call un-holdable.</td>
     *         <td>Call starts and goes active.</td>
     *     </tr>
     *     <tr>
     *         <td>2</td>
     *         <td>Start outgoing call {@code X}.</td>
     *         <td>Telecom should deny the outgoing call creation.</td>
     *         <td>The second call does not start.</td>
     *     </tr>
     *     <tr>
     *         <td>3</td>
     *         <td>Disconnect call {@code Y}.</td>
     *         <td></td>
     *         <td>
     *             Call {@code Y} disconnected.
     *         </td>
     *     </tr>
     *     <tr>
     *         <td>4</td>
     *         <td>Start outgoing call {@code X} a second time.</td>
     *         <td>Telecom should allow the outgoing call creation.</td>
     *         <td>The second call starts.</td>
     *     </tr>
     * </table>
     *
     */
    @Test
    public void testPlaceCallWhenAnotherCallCantBeHeld() {
    }

}
