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

package com.android.libraries.rcs.simpleclient.registration;

import android.os.Build.VERSION_CODES;
import android.telephony.ims.DelegateRegistrationState;
import android.telephony.ims.DelegateRequest;
import android.telephony.ims.FeatureTagState;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.SipDelegateConfiguration;
import android.telephony.ims.SipDelegateConnection;
import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.SipMessage;
import android.telephony.ims.stub.DelegateConnectionMessageCallback;
import android.telephony.ims.stub.DelegateConnectionStateCallback;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.libraries.rcs.simpleclient.protocol.sip.SipSession;
import com.android.libraries.rcs.simpleclient.protocol.sip.SipSessionConfiguration;
import com.android.libraries.rcs.simpleclient.protocol.sip.SipSessionListener;
import com.android.libraries.rcs.simpleclient.service.ImsService;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.text.ParseException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

import javax.sip.message.Message;

/**
 * Actual implementation built upon SipDelegateConnection as a SIP transport.
 * Feature tag registration state changes will trigger callbacks SimpleRcsClient to
 * enable/disable related ImsServices.
 */
@RequiresApi(api = VERSION_CODES.R)
public class RegistrationControllerImpl implements RegistrationController {
    private static final String TAG = RegistrationControllerImpl.class.getCanonicalName();

    private final Executor executor;
    private final int subscriptionId;
    private SipDelegateManager sipDelegateManager;
    private RegistrationContext context;
    private RegistrationStateChangeCallback callback;

    public RegistrationControllerImpl(int subscriptionId, Executor executor,
            ImsManager imsManager) {
        this.subscriptionId = subscriptionId;
        this.executor = executor;
        this.sipDelegateManager = imsManager.getSipDelegateManager(subscriptionId);
    }

    @Override
    public void register(ImsService imsService, RegistrationStateChangeCallback callback) {
        Log.i(TAG, "register");
        this.callback = callback;
        context = new RegistrationContext(this, imsService);
        context.register();
    }

    @Override
    public void deregister() {
        Log.i(TAG, "deregister");
        if (context != null && context.sipDelegateConnection != null) {
            sipDelegateManager.destroySipDelegate(context.sipDelegateConnection,
                    SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        }
    }

    @Override
    public void onRegistrationStateChange(RegistrationStateChangeCallback callback) {
        throw new IllegalStateException("Not implemented!");
    }

    /**
     * Envelopes the registration data for a single ImsService instance.
     */
    private class RegistrationContext implements SipSession, SipSessionConfiguration {

        private final RegistrationControllerImpl controller;
        private final ImsService imsService;
        private final SettableFuture<SipSession> sessionFuture = SettableFuture.create();

        protected SipDelegateConnection sipDelegateConnection;
        private SipDelegateConfiguration mConfiguration;
        private final DelegateConnectionStateCallback connectionCallback =
                new DelegateConnectionStateCallback() {

                    @Override
                    public void onCreated(SipDelegateConnection c) {
                        sipDelegateConnection = c;
                    }

                    @Override
                    public void onConfigurationChanged(
                            SipDelegateConfiguration registeredSipConfig) {
                        Log.d(
                                TAG,
                                "onSipConfigurationChanged: version="
                                        + registeredSipConfig.getVersion());
                        Log.i(TAG, "onSipConfigurationChanged: " + registeredSipConfig);
                        dumpConfig(registeredSipConfig);
                        RegistrationContext.this.mConfiguration = registeredSipConfig;
                    }

                    @Override
                    public void onFeatureTagStatusChanged(
                            @NonNull DelegateRegistrationState registrationState,
                            @NonNull Set<FeatureTagState> deniedFeatureTags) {
                        dumpFeatureTagState(registrationState, deniedFeatureTags);
                        if (registrationState
                                .getRegisteredFeatureTags()
                                .containsAll(imsService.getFeatureTags())) {
                            // registered;
                            callback.onSuccess(RegistrationContext.this);
                        } else {
                            callback.onFailure("feature tag not registered");
                        }
                    }

                    @Override
                    public void onDestroyed(int reason) {
                        Log.d(TAG, "onDestroyed:" + reason);
                        callback.onFailure("delegate destroyed");

                    }
                };
        private SipSessionListener sipSessionListener;
        // Callback for incoming messages on the modem connection
        private final DelegateConnectionMessageCallback messageCallback =
                new DelegateConnectionMessageCallback() {
                    @Override
                    public void onMessageReceived(@NonNull SipMessage message) {
                        message = repairHeaderSection(message);
                        SipSessionListener listener = sipSessionListener;
                        if (listener != null) {
                            try {
                                listener.onMessageReceived(
                                        MessageConverter.toStackMessage(message));
                            } catch (ParseException e) {
                                // TODO: logging here
                            }
                        }
                    }

                    @Override
                    public void onMessageSendFailure(@NonNull String viaTransactionId, int reason) {
                        Log.i(TAG, "onMessageSendFailure: viaTransactionId:"
                                + viaTransactionId + ", reason:" + reason);
                    }

                    @Override
                    public void onMessageSent(@NonNull String viaTransactionId) {
                        Log.i(TAG, "onMessageSent: viaTransactionId:" + viaTransactionId);
                    }

                };

        public RegistrationContext(RegistrationControllerImpl controller,
                ImsService imsService) {
            this.controller = controller;
            this.imsService = imsService;
        }

        public ListenableFuture<SipSession> getFuture() {
            return sessionFuture;
        }

        @Override
        public SipSessionConfiguration getSessionConfiguration() {
            return this;
        }

        public void register() {
            Log.i(TAG, "createSipDelegate");
            DelegateRequest request = new DelegateRequest(imsService.getFeatureTags());
            try {
                controller.sipDelegateManager.createSipDelegate(
                        request, controller.executor, connectionCallback, messageCallback);
            } catch (ImsException e) {
                // TODO: ...
            }
        }

        private void dumpFeatureTagState(DelegateRegistrationState registrationState,
                Set<FeatureTagState> deniedFeatureTags) {
            StringBuilder stringBuilder = new StringBuilder(
                    "onFeatureTagStatusChanged ").append(
                    " deniedFeatureTags:[");
            Iterator<FeatureTagState> iterator = deniedFeatureTags.iterator();
            while (iterator.hasNext()) {
                FeatureTagState featureTagState = iterator.next();
                stringBuilder.append(featureTagState.getFeatureTag()).append(" ").append(
                        featureTagState.getState());
            }
            Set<String> registeredFt = registrationState.getRegisteredFeatureTags();
            Iterator<String> iteratorStr = registeredFt.iterator();
            stringBuilder.append("] registeredFT:[");
            while (iteratorStr.hasNext()) {
                String ft = iteratorStr.next();
                stringBuilder.append(ft).append(" ");
            }
            stringBuilder.append("]");
            String result = stringBuilder.toString();
            Log.i(TAG, result);
        }

        private void dumpConfig(SipDelegateConfiguration config) {
            String result = "SipDelegateConfiguration{"
                    + "mVersion=" + config.getVersion()
                    + ", \n\tmTransportType=" + config.getTransportType()
                    + ", \n\tmLocalAddr=" + config.getLocalAddress()
                    + ", \n\tmSipServerAddr=" + config.getSipServerAddress()
                    + ", \n\tmIsSipCompactFormEnabled=" + config.isSipCompactFormEnabled()
                    + ", \n\tmIsSipKeepaliveEnabled=" + config.isSipKeepaliveEnabled()
                    + ", \n\tmMaxUdpPayloadSize=" + config.getMaxUdpPayloadSizeBytes()
                    + ", \n\tmPublicUserIdentifier=" + config.getPublicUserIdentifier()
                    + ", \n\tmPrivateUserIdentifier=" + config.getPrivateUserIdentifier()
                    + ", \n\tmHomeDomain=" + config.getHomeDomain()
                    + ", \n\tmImei=" + config.getImei()
                    + ", \n\tmGruu=" + config.getPublicGruuUri()
                    + ", \n\tmSipAuthHeader=" + config.getSipAuthenticationHeader()
                    + ", \n\tmSipAuthNonce=" + config.getSipAuthenticationNonce()
                    + ", \n\tmServiceRouteHeader=" + config.getSipServiceRouteHeader()
                    + ", \n\tmPathHeader=" + config.getSipPathHeader()
                    + ", \n\tmUserAgentHeader=" + config.getSipUserAgentHeader()
                    + ", \n\tmContactUserParam=" + config.getSipContactUserParameter()
                    + ", \n\tmPaniHeader=" + config.getSipPaniHeader()
                    + ", \n\tmPlaniHeader=" + config.getSipPlaniHeader()
                    + ", \n\tmCniHeader=" + config.getSipCniHeader()
                    + ", \n\tmAssociatedUriHeader=" + config.getSipAssociatedUriHeader()
                    + ", \n\tmIpSecConfiguration=" + config.getIpSecConfiguration()
                    + ", \n\tmNatConfiguration=" + config.getNatSocketAddress() + '}';
            Log.i(TAG, result);
        }

        @Override
        public void setSessionListener(SipSessionListener listener) {
            sipSessionListener = listener;
        }

        @Override
        public ListenableFuture<Boolean> send(Message message) {
            sipDelegateConnection.sendMessage(MessageConverter.toPlatformMessage(message),
                    getVersion());
            // TODO: check on transaction
            return Futures.immediateFuture(true);
        }

        // Config values here.

        @Override
        public long getVersion() {
            return mConfiguration.getVersion();
        }

        @Override
        public String getOutboundProxyAddr() {
            return mConfiguration.getSipServerAddress().getAddress().getHostAddress();
        }

        @Override
        public int getOutboundProxyPort() {
            return mConfiguration.getSipServerAddress().getPort();
        }

        @Override
        public String getLocalIpAddress() {
            return mConfiguration.getLocalAddress().getAddress().getHostAddress();
        }

        @Override
        public int getLocalPort() {
            return mConfiguration.getLocalAddress().getPort();
        }

        @Override
        public String getSipTransport() {
            int sipTransport = mConfiguration.getTransportType();
            return (sipTransport == SipDelegateConfiguration.SIP_TRANSPORT_TCP) ? "TCP" : "UDP";
        }

        @Override
        public String getPublicUserIdentity() {
            return mConfiguration.getPublicUserIdentifier();
        }

        @Override
        public String getDomain() {
            return mConfiguration.getHomeDomain();
        }

        @Override
        public List<String> getAssociatedUris() {
            String associatedUris = mConfiguration.getSipAssociatedUriHeader();
            if (!TextUtils.isEmpty(associatedUris)) {
                return Splitter.on(',').trimResults(CharMatcher.anyOf("<>")).splitToList(
                        associatedUris);
            }

            return ImmutableList.of();
        }

        @Override
        public String getSecurityVerifyHeader() {
            SipDelegateConfiguration.IpSecConfiguration c = mConfiguration.getIpSecConfiguration();
            if (c == null) {
                return null;
            }
            return c.getSipSecurityVerifyHeader();
        }

        @Override
        public List<String> getServiceRouteHeaders() {
            String serviceRoutes =
                    mConfiguration.getSipServiceRouteHeader();
            if (TextUtils.isEmpty(serviceRoutes)) {
                return Collections.emptyList();
            }
            return Splitter.on(',').trimResults().splitToList(serviceRoutes);
        }

        @Override
        public String getContactUser() {
            return mConfiguration.getSipContactUserParameter();
        }

        @Override
        public String getImei() {
            return mConfiguration.getImei();
        }

        @Override
        public String getPaniHeader() {
            return mConfiguration.getSipPaniHeader();
        }

        @Override
        public String getPlaniHeader() {
            return mConfiguration.getSipPlaniHeader();
        }

        @Override
        public String getUserAgentHeader() {
            return mConfiguration.getSipUserAgentHeader();
        }

        @Override
        public int getMaxPayloadSizeOnUdp() {
            return mConfiguration.getMaxUdpPayloadSizeBytes() > 0
                    ? mConfiguration.getMaxUdpPayloadSizeBytes() : 1500;
        }

        /**
         * There is a modem issue where "ia:" is returned back instead of "Via:". Fix that locally
         * for now.
         * @return A SipMessage with the corrected header section.
         */
        private SipMessage repairHeaderSection(SipMessage message) {
            String headers = message.getHeaderSection();

            if (headers.startsWith("ia:")) {
                headers = "V" + headers;
                Log.i(TAG, "repairHeaderSection: detected malformed via: "
                        + message.getHeaderSection().substring(0, 10) + "->"
                        + headers.substring(0, 10));
            }
            return new SipMessage(message.getStartLine(), headers, message.getContent());
        }
    }
}

