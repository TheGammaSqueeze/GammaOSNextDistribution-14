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

package com.android.server.wifi.hal;

import android.annotation.Nullable;
import android.net.MacAddress;
import android.net.wifi.aware.ConfigRequest;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.WifiAwareDataPathSecurityConfig;

import com.android.server.wifi.aware.Capabilities;

/** Abstraction of WifiNanIface */
public interface IWifiNanIface {
    /**
     * Enable verbose logging.
     */
    void enableVerboseLogging(boolean verbose);

    /**
     * Register a framework callback to receive notifications from the HAL.
     *
     * @param callback Instance of {@link WifiNanIface.Callback}.
     * @return true if successful, false otherwise
     */
    boolean registerFrameworkCallback(WifiNanIface.Callback callback);

    /**
     * Get the name of this interface.
     *
     * @return Name of this interface, or null on error.
     */
    @Nullable
    String getName();

    /**
     * Query the firmware's capabilities.
     *
     * @param transactionId Transaction ID for the transaction - used in the async callback to
     *                      match with the original request.
     */
    boolean getCapabilities(short transactionId);

    /**
     * Enable and configure Aware.
     *
     * @param transactionId Transaction ID for the transaction - used in the
     *            async callback to match with the original request.
     * @param configRequest Requested Aware configuration.
     * @param notifyIdentityChange Indicates whether to get address change callbacks.
     * @param initialConfiguration Specifies whether initial configuration
     *            (true) or an update (false) to the configuration.
     * @param rangingEnabled Indicates whether to enable ranging.
     * @param isInstantCommunicationEnabled Indicates whether to enable instant communication
     * @param instantModeChannel
     * @param clusterId Indicate which cluster to join.
     * @param macAddressRandomizationIntervalSec
     * @param powerParameters Instance of {@link WifiNanIface.PowerParameters} containing the
     *                        parameters to use in our config request.
     */
    boolean enableAndConfigure(short transactionId, ConfigRequest configRequest,
            boolean notifyIdentityChange, boolean initialConfiguration, boolean rangingEnabled,
            boolean isInstantCommunicationEnabled, int instantModeChannel, int clusterId,
            int macAddressRandomizationIntervalSec, WifiNanIface.PowerParameters powerParameters);

    /**
     * Disable Aware.
     *
     * @param transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     */
    boolean disable(short transactionId);

    /**
     * Start or modify a service publish session.
     *  @param transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     * @param publishId ID of the requested session - 0 to request a new publish
     *            session.
     * @param publishConfig Configuration of the discovery session.
     * @param nanIdentityKey
     */
    boolean publish(short transactionId, byte publishId, PublishConfig publishConfig,
            byte[] nanIdentityKey);

    /**
     * Start or modify a service subscription session.
     * @param transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     * @param subscribeId ID of the requested session - 0 to request a new
     *            subscribe session.
     * @param subscribeConfig Configuration of the discovery session.
     * @param nanIdentityKey
     */
    boolean subscribe(short transactionId, byte subscribeId, SubscribeConfig subscribeConfig,
            byte[] nanIdentityKey);

    /**
     * Send a message through an existing discovery session.
     *
     * @param transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     * @param pubSubId ID of the existing publish/subscribe session.
     * @param requestorInstanceId ID of the peer to communicate with - obtained
     *            through a previous discovery (match) operation with that peer.
     * @param dest MAC address of the peer to communicate with - obtained
     *            together with requestorInstanceId.
     * @param message Message.
     */
    boolean sendMessage(short transactionId, byte pubSubId, int requestorInstanceId,
            MacAddress dest, byte[] message);

    /**
     * Terminate a publish discovery session.
     *
     * @param transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     * @param pubSubId ID of the publish/subscribe session - obtained when
     *            creating a session.
     */
    boolean stopPublish(short transactionId, byte pubSubId);

    /**
     * Terminate a subscribe discovery session.
     *
     * @param transactionId transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     * @param pubSubId ID of the publish/subscribe session - obtained when
     *            creating a session.
     */
    boolean stopSubscribe(short transactionId, byte pubSubId);

    /**
     * Create an Aware network interface. This only creates the Linux interface - it doesn't
     * actually create the data connection.
     *
     * @param transactionId Transaction ID for the transaction - used in the async callback to
     *                      match with the original request.
     * @param interfaceName The name of the interface, e.g. "aware0".
     */
    boolean createAwareNetworkInterface(short transactionId, String interfaceName);

    /**
     * Deletes an Aware network interface. The data connection can (should?) be torn
     * down previously.
     *
     * @param transactionId Transaction ID for the transaction - used in the async callback to
     *                      match with the original request.
     * @param interfaceName The name of the interface, e.g. "aware0".
     */
    boolean deleteAwareNetworkInterface(short transactionId, String interfaceName);

    /**
     * Initiates setting up a data-path between device and peer. Security is provided by either
     * PMK or Passphrase (not both) - if both are null then an open (unencrypted) link is set up.
     *
     * @param transactionId      Transaction ID for the transaction - used in the async callback to
     *                           match with the original request.
     * @param peerId             ID of the peer ID to associate the data path with. A value of 0
     *                           indicates that not associated with an existing session.
     * @param channelRequestType Indicates whether the specified channel is available, if available
     *                           requested or forced (resulting in failure if cannot be
     *                           accommodated).
     * @param channel            The channel on which to set up the data-path.
     * @param peer               The MAC address of the peer to create a connection with.
     * @param interfaceName      The interface on which to create the data connection.
     * @param isOutOfBand        Is the data-path out-of-band (i.e. without a corresponding Aware
     *                           discovery
     *                           session).
     * @param appInfo            Arbitrary binary blob transmitted to the peer.
     * @param capabilities       The capabilities of the firmware.
     * @param securityConfig     Security config to encrypt the data-path
     */
    boolean initiateDataPath(short transactionId, int peerId, int channelRequestType,
            int channel, MacAddress peer, String interfaceName,
            boolean isOutOfBand, byte[] appInfo, Capabilities capabilities,
            WifiAwareDataPathSecurityConfig securityConfig, byte pubSubId);

    /**
     * Responds to a data request from a peer. Security is provided by either PMK or Passphrase (not
     * both) - if both are null then an open (unencrypted) link is set up.
     *
     * @param transactionId  Transaction ID for the transaction - used in the async callback to
     *                       match with the original request.
     * @param accept         Accept (true) or reject (false) the original call.
     * @param ndpId          The NDP (Aware data path) ID. Obtained from the request callback.
     * @param interfaceName  The interface on which the data path will be setup. Obtained from the
     *                       request callback.
     * @param appInfo        Arbitrary binary blob transmitted to the peer.
     * @param isOutOfBand    Is the data-path out-of-band (i.e. without a corresponding Aware
     *                       discovery
     *                       session).
     * @param capabilities   The capabilities of the firmware.
     * @param securityConfig Security config to encrypt the data-path
     */
    boolean respondToDataPathRequest(short transactionId, boolean accept, int ndpId,
            String interfaceName, byte[] appInfo,
            boolean isOutOfBand, Capabilities capabilities,
            WifiAwareDataPathSecurityConfig securityConfig, byte pubSubId);

    /**
     * Terminate an existing data-path (does not delete the interface).
     *
     * @param transactionId Transaction ID for the transaction - used in the async callback to
     *                      match with the original request.
     * @param ndpId The NDP (Aware data path) ID to be terminated.
     */
    boolean endDataPath(short transactionId, int ndpId);

    /**
     * Response to a NAN pairing request for this from this session
     *
     * @param transactionId      Transaction ID for the transaction - used in the
     *                           async callback to match with the original request.
     * @param pairingId          The id of the current pairing session
     * @param accept             True if accpect, false otherwise
     * @param pairingIdentityKey NAN identity key
     * @param requestType        Setup or verification
     * @param pmk                credential for the pairing verification
     * @param password           credential for the pairing setup
     * @param akm                Key exchange method is used for pairing
     * @return True if the request send succeed.
     */
    boolean respondToPairingRequest(short transactionId, int pairingId, boolean accept,
            byte[] pairingIdentityKey, boolean enablePairingCache, int requestType, byte[] pmk,
            String password, int akm, int cipherSuite);

    /**
     * Initiate a NAN pairing request for this publish/subscribe session
     *
     * @param transactionId      Transaction ID for the transaction - used in the
     *                           async callback to match with the original request.
     * @param peerId             ID of the peer. Obtained through previous communication (a
     *                           match indication).
     * @param pairingIdentityKey NAN identity key
     * @param requestType        Setup or verification
     * @param pmk                credential for the pairing verification
     * @param password           credential for the pairing setup
     * @param akm                Key exchange method is used for pairing
     * @return True if the request send succeed.
     */
    boolean initiateNanPairingRequest(short transactionId, int peerId, MacAddress peer,
            byte[] pairingIdentityKey, boolean enablePairingCache, int requestType, byte[] pmk,
            String password, int akm, int cipherSuite);

    /**
     * Terminate an existing pairing setup
     *
     * @param transactionId Transaction ID for the transaction - used in the async callback to
     *                      match with the original request.
     * @param pairingId The id of the pairing session
     */
    boolean endPairing(short transactionId, int pairingId);

    /**
     * Initiate a bootstrapping request for this publish/subscribe session
     *
     * @param transactionId Transaction ID for the transaction - used in the
     *                      async callback to match with the original request.
     * @param peerId        ID of the peer. Obtained through previous communication (a
     *                      match indication).
     * @param peer          The MAC address of the peer to create a connection with.
     * @param method        the proposed bootstrapping method
     * @param pubSubId      ID of the publish/subscribe session - obtained when creating a session.
     * @param isComeBack    If the request is for a previous comeback response
     * @return True if the request send succeed.
     */
    boolean initiateNanBootstrappingRequest(short transactionId, int peerId, MacAddress peer,
            int method, byte[] cookie, byte pubSubId, boolean isComeBack);

    /**
     * Respond to a bootstrapping request
     *
     * @param transactionId   Transaction ID for the transaction - used in the
     *                        async callback to match with the original request.
     * @param bootstrappingId the id of this bootstrapping session
     * @param accept          True if the proposed bootstrapping method is accepted.
     * @param pubSubId        ID of the publish/subscribe session - obtained when creating a
     *                        session.
     * @return True if the request send succeed.
     */
    boolean respondToNanBootstrappingRequest(short transactionId, int bootstrappingId,
            boolean accept, byte pubSubId);

    /**
     * Suspend the specified Aware session. During the suspend state, the Wi-Fi Aware device
     * must not transmit or receive frames for this Wi-Fi Aware discovery session including any
     * active NDPs. If all discovery sessions are suspended then the Wi-Fi Aware device must not
     * transmit or receive any Wi-Fi Aware frames.
     *
     * @param transactionId Transaction ID for the transaction - used in the
     *            async callback to match with the original request.
     * @param pubSubId ID of the existing publish/subscribe session.
     * @return True if the request sent successfully.
     */
    boolean suspend(short transactionId, byte pubSubId);

    /**
     * Resume the specified (suspended) Aware session. Resume cancels an ongoing suspend for this
     * Wi-Fi Aware discovery session and automatically resumes the session and any associated
     * NDPs to the state before they were suspended. The Wi-Fi Aware resume operation should be
     * faster than recreating the corresponding discovery session and NDPs with the same benefit of
     * power.
     *
     * @param transactionId Transaction ID for the transaction - used in the
     *            async callback to match with the original request.
     * @param pubSubId ID of the existing publish/subscribe session.
     * @return True if the request sent successfully.
     */
    boolean resume(short transactionId, byte pubSubId);
}
