/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.services.telephony;

import static android.telephony.CarrierConfigManager.KEY_USE_ONLY_DIALED_SIM_ECC_LIST_BOOL;
import static android.telephony.DomainSelectionService.SELECTOR_TYPE_CALLING;
import static android.telephony.TelephonyManager.HAL_SERVICE_VOICE;

import static com.android.internal.telephony.PhoneConstants.PHONE_TYPE_GSM;
import static com.android.internal.telephony.flags.Flags.carrierEnabledSatelliteFlag;

import android.annotation.NonNull;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.os.PersistableBundle;
import android.telecom.Conference;
import android.telecom.Conferenceable;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.AccessNetworkConstants;
import android.telephony.Annotation.DisconnectCauses;
import android.telephony.CarrierConfigManager;
import android.telephony.DataSpecificRegistrationInfo;
import android.telephony.DomainSelectionService;
import android.telephony.DomainSelectionService.SelectionAttributes;
import android.telephony.EmergencyRegistrationResult;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.PhoneNumberUtils;
import android.telephony.RadioAccessFamily;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.emergency.EmergencyNumber;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.text.TextUtils;
import android.util.Pair;
import android.view.WindowManager;

import com.android.ims.ImsManager;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallFailCause;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.GsmCdmaPhone;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.RIL;
import com.android.internal.telephony.d2d.Communicator;
import com.android.internal.telephony.data.PhoneSwitcher;
import com.android.internal.telephony.domainselection.DomainSelectionConnection;
import com.android.internal.telephony.domainselection.DomainSelectionResolver;
import com.android.internal.telephony.domainselection.EmergencyCallDomainSelectionConnection;
import com.android.internal.telephony.domainselection.NormalCallDomainSelectionConnection;
import com.android.internal.telephony.emergency.EmergencyStateTracker;
import com.android.internal.telephony.emergency.RadioOnHelper;
import com.android.internal.telephony.emergency.RadioOnStateListener;
import com.android.internal.telephony.flags.Flags;
import com.android.internal.telephony.imsphone.ImsExternalCallTracker;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.imsphone.ImsPhoneConnection;
import com.android.internal.telephony.imsphone.ImsPhoneMmiCode;
import com.android.internal.telephony.satellite.SatelliteController;
import com.android.internal.telephony.satellite.SatelliteSOSMessageRecommender;
import com.android.internal.telephony.subscription.SubscriptionInfoInternal;
import com.android.internal.telephony.subscription.SubscriptionManagerService;
import com.android.phone.FrameworksUtils;
import com.android.phone.MMIDialogActivity;
import com.android.phone.PhoneUtils;
import com.android.phone.R;
import com.android.phone.callcomposer.CallComposerPictureManager;
import com.android.phone.settings.SuppServicesUiUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.annotation.Nullable;

/**
 * Service for making GSM and CDMA connections.
 */
public class TelephonyConnectionService extends ConnectionService {
    private static final String LOG_TAG = TelephonyConnectionService.class.getSimpleName();
    // Timeout before we continue with the emergency call without waiting for DDS switch response
    // from the modem.
    private static final int DEFAULT_DATA_SWITCH_TIMEOUT_MS = 1000;

    // Timeout to start dynamic routing of normal routing emergency numbers.
    @VisibleForTesting
    public static final int TIMEOUT_TO_DYNAMIC_ROUTING_MS = 10000;

    // Timeout before we terminate the outgoing DSDA call if HOLD did not complete in time on the
    // existing call.
    private static final int DEFAULT_DSDA_OUTGOING_CALL_HOLD_TIMEOUT_MS = 2000;

    // Timeout to wait for the termination of incoming call before continue with the emergency call.
    private static final int DEFAULT_REJECT_INCOMING_CALL_TIMEOUT_MS = 10 * 1000; // 10 seconds.

    // If configured, reject attempts to dial numbers matching this pattern.
    private static final Pattern CDMA_ACTIVATION_CODE_REGEX_PATTERN =
            Pattern.compile("\\*228[0-9]{0,2}");

    private final TelephonyConnectionServiceProxy mTelephonyConnectionServiceProxy =
            new TelephonyConnectionServiceProxy() {
        @Override
        public Collection<Connection> getAllConnections() {
            return TelephonyConnectionService.this.getAllConnections();
        }
        @Override
        public void addConference(TelephonyConference mTelephonyConference) {
            TelephonyConnectionService.this.addTelephonyConference(mTelephonyConference);
        }
        @Override
        public void addConference(ImsConference mImsConference) {
            Connection conferenceHost = mImsConference.getConferenceHost();
            if (conferenceHost instanceof TelephonyConnection) {
                TelephonyConnection tcConferenceHost = (TelephonyConnection) conferenceHost;
                tcConferenceHost.setTelephonyConnectionService(TelephonyConnectionService.this);
                tcConferenceHost.setPhoneAccountHandle(mImsConference.getPhoneAccountHandle());
            }
            TelephonyConnectionService.this.addTelephonyConference(mImsConference);
        }
        @Override
        public void addExistingConnection(PhoneAccountHandle phoneAccountHandle,
                                          Connection connection) {
            TelephonyConnectionService.this
                    .addExistingConnection(phoneAccountHandle, connection);
        }
        @Override
        public void addExistingConnection(PhoneAccountHandle phoneAccountHandle,
                Connection connection, Conference conference) {
            TelephonyConnectionService.this
                    .addExistingConnection(phoneAccountHandle, connection, conference);
        }
        @Override
        public void addConnectionToConferenceController(TelephonyConnection connection) {
            TelephonyConnectionService.this.addConnectionToConferenceController(connection);
        }
    };

    private final BroadcastReceiver mTtyBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.v(this, "onReceive, action: %s", action);
            if (action.equals(TelecomManager.ACTION_TTY_PREFERRED_MODE_CHANGED)) {
                int newPreferredTtyMode = intent.getIntExtra(
                        TelecomManager.EXTRA_TTY_PREFERRED_MODE, TelecomManager.TTY_MODE_OFF);

                boolean isTtyNowEnabled = newPreferredTtyMode != TelecomManager.TTY_MODE_OFF;
                if (isTtyNowEnabled != mIsTtyEnabled) {
                    handleTtyModeChange(isTtyNowEnabled);
                }
            }
        }
    };

    private final TelephonyConferenceController mTelephonyConferenceController =
            new TelephonyConferenceController(mTelephonyConnectionServiceProxy);
    private final CdmaConferenceController mCdmaConferenceController =
            new CdmaConferenceController(this);
    private ImsConferenceController mImsConferenceController;

    private ComponentName mExpectedComponentName = null;
    private RadioOnHelper mRadioOnHelper;
    private EmergencyTonePlayer mEmergencyTonePlayer;
    private HoldTracker mHoldTracker;
    private boolean mIsTtyEnabled;
    /** Set to true when there is an emergency call pending which will potential trigger a dial.
     * This must be set to false when the call is dialed. */
    private volatile boolean mIsEmergencyCallPending;

    // Contains one TelephonyConnection that has placed a call and a memory of which Phones it has
    // already tried to connect with. There should be only one TelephonyConnection trying to place a
    // call at one time. We also only access this cache from a TelephonyConnection that wishes to
    // redial, so we use a WeakReference that will become stale once the TelephonyConnection is
    // destroyed.
    @VisibleForTesting
    public Pair<WeakReference<TelephonyConnection>, Queue<Phone>> mEmergencyRetryCache;
    private DeviceState mDeviceState = new DeviceState();
    private EmergencyStateTracker mEmergencyStateTracker;
    private SatelliteSOSMessageRecommender mSatelliteSOSMessageRecommender;
    private DomainSelectionResolver mDomainSelectionResolver;
    private EmergencyCallDomainSelectionConnection mEmergencyCallDomainSelectionConnection;
    private TelephonyConnection mEmergencyConnection;
    private Executor mDomainSelectionMainExecutor;
    private ImsManager mImsManager = null;
    private DomainSelectionConnection mDomainSelectionConnection;
    private TelephonyConnection mNormalCallConnection;
    private SatelliteController mSatelliteController;

    /**
     * Keeps track of the status of a SIM slot.
     */
    private static class SlotStatus {
        public int slotId;
        public int activeSubId;
        // RAT capabilities
        public int capabilities;
        // By default, we will assume that the slots are not locked.
        public boolean isLocked = false;
        // Is the emergency number associated with the slot
        public boolean hasDialedEmergencyNumber = false;
        //SimState.
        public int simState;

        //helper to check if sim is really 'present' in the traditional sense.
        // since eSIM always reports SIM_STATE_READY
        public boolean isSubActiveAndSimPresent() {
            return (simState != TelephonyManager.SIM_STATE_ABSENT
                && activeSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        }

        public SlotStatus(int slotId, int capabilities, int activeSubId) {
            this.slotId = slotId;
            this.capabilities = capabilities;
            this.activeSubId = activeSubId;
        }
    }

    /**
     * SubscriptionManager dependencies for testing.
     */
    @VisibleForTesting
    public interface SubscriptionManagerProxy {
        int getDefaultVoicePhoneId();
        int getDefaultDataPhoneId();
        int getSimStateForSlotIdx(int slotId);
        int getPhoneId(int subId);
    }

    private SubscriptionManagerProxy mSubscriptionManagerProxy = new SubscriptionManagerProxy() {
        @Override
        public int getDefaultVoicePhoneId() {
            return SubscriptionManager.getDefaultVoicePhoneId();
        }

        @Override
        public int getDefaultDataPhoneId() {
            return getPhoneId(SubscriptionManager.getDefaultDataSubscriptionId());
        }

        @Override
        public int getSimStateForSlotIdx(int slotId) {
            return TelephonyManager.getSimStateForSlotIndex(slotId);
        }

        @Override
        public int getPhoneId(int subId) {
            return SubscriptionManager.getPhoneId(subId);
        }

    };

    /**
     * TelephonyManager dependencies for testing.
     */
    @VisibleForTesting
    public interface TelephonyManagerProxy {
        int getPhoneCount();
        boolean isCurrentEmergencyNumber(String number);
        Map<Integer, List<EmergencyNumber>> getCurrentEmergencyNumberList();

        /**
         * Determines whether concurrent IMS calls across both SIMs are possible, based on whether
         * the device is DSDA capable, or if the DSDS device supports virtual DSDA.
         */
        boolean isConcurrentCallsPossible();

        /**
         * Gets the maximum number of SIMs that can be active, based on the device's multisim
         * configuration. Returns 1 for DSDS, 2 for DSDA.
         */
        int getMaxNumberOfSimultaneouslyActiveSims();
    }

    private TelephonyManagerProxy mTelephonyManagerProxy;

    private class TelephonyManagerProxyImpl implements TelephonyManagerProxy {
        private final TelephonyManager mTelephonyManager;


        TelephonyManagerProxyImpl(Context context) {
            mTelephonyManager = new TelephonyManager(context);
        }

        @Override
        public int getPhoneCount() {
            return mTelephonyManager.getPhoneCount();
        }

        @Override
        public boolean isCurrentEmergencyNumber(String number) {
            try {
                return mTelephonyManager.isEmergencyNumber(number);
            } catch (IllegalStateException ise) {
                return false;
            }
        }

        @Override
        public Map<Integer, List<EmergencyNumber>> getCurrentEmergencyNumberList() {
            try {
                return mTelephonyManager.getEmergencyNumberList();
            } catch (IllegalStateException ise) {
                return new HashMap<>();
            }
        }

        @Override
        public int getMaxNumberOfSimultaneouslyActiveSims() {
            try {
                return mTelephonyManager.getMaxNumberOfSimultaneouslyActiveSims();
            } catch (IllegalStateException ise) {
                return 1;
            }
        }

        @Override
        public boolean isConcurrentCallsPossible() {
            try {
                return getMaxNumberOfSimultaneouslyActiveSims() > 1
                    || mTelephonyManager.getPhoneCapability().getMaxActiveVoiceSubscriptions() > 1;
            } catch (IllegalStateException ise) {
                return false;
            }
        }
    }

    /**
     * PhoneFactory Dependencies for testing.
     */
    @VisibleForTesting
    public interface PhoneFactoryProxy {
        Phone getPhone(int index);
        Phone getDefaultPhone();
        Phone[] getPhones();
    }

    private PhoneFactoryProxy mPhoneFactoryProxy = new PhoneFactoryProxy() {
        @Override
        public Phone getPhone(int index) {
            return PhoneFactory.getPhone(index);
        }

        @Override
        public Phone getDefaultPhone() {
            return PhoneFactory.getDefaultPhone();
        }

        @Override
        public Phone[] getPhones() {
            return PhoneFactory.getPhones();
        }
    };

    /**
     * PhoneUtils dependencies for testing.
     */
    @VisibleForTesting
    public interface PhoneUtilsProxy {
        int getSubIdForPhoneAccountHandle(PhoneAccountHandle accountHandle);
        PhoneAccountHandle makePstnPhoneAccountHandle(Phone phone);
        PhoneAccountHandle makePstnPhoneAccountHandleWithPrefix(Phone phone, String prefix,
                boolean isEmergency);
    }

    private PhoneUtilsProxy mPhoneUtilsProxy = new PhoneUtilsProxy() {
        @Override
        public int getSubIdForPhoneAccountHandle(PhoneAccountHandle accountHandle) {
            return PhoneUtils.getSubIdForPhoneAccountHandle(accountHandle);
        }

        @Override
        public PhoneAccountHandle makePstnPhoneAccountHandle(Phone phone) {
            return PhoneUtils.makePstnPhoneAccountHandle(phone);
        }

        @Override
        public PhoneAccountHandle makePstnPhoneAccountHandleWithPrefix(Phone phone, String prefix,
                boolean isEmergency) {
            return PhoneUtils.makePstnPhoneAccountHandleWithPrefix(
                    phone, prefix, isEmergency, phone.getUserHandle());
        }
    };

    /**
     * PhoneNumberUtils dependencies for testing.
     */
    @VisibleForTesting
    public interface PhoneNumberUtilsProxy {
        String convertToEmergencyNumber(Context context, String number);
    }

    private PhoneNumberUtilsProxy mPhoneNumberUtilsProxy = new PhoneNumberUtilsProxy() {
        @Override
        public String convertToEmergencyNumber(Context context, String number) {
            return PhoneNumberUtils.convertToEmergencyNumber(context, number);
        }
    };

    /**
     * PhoneSwitcher dependencies for testing.
     */
    @VisibleForTesting
    public interface PhoneSwitcherProxy {
        PhoneSwitcher getPhoneSwitcher();
    }

    private PhoneSwitcherProxy mPhoneSwitcherProxy = new PhoneSwitcherProxy() {
        @Override
        public PhoneSwitcher getPhoneSwitcher() {
            return PhoneSwitcher.getInstance();
        }
    };

    /**
     * DisconnectCause depends on PhoneGlobals in order to get a system context. Mock out
     * dependency for testing.
     */
    @VisibleForTesting
    public interface DisconnectCauseFactory {
        DisconnectCause toTelecomDisconnectCause(int telephonyDisconnectCause, String reason);
        DisconnectCause toTelecomDisconnectCause(int telephonyDisconnectCause,
                String reason, int phoneId);
    }

    private DisconnectCauseFactory mDisconnectCauseFactory = new DisconnectCauseFactory() {
        @Override
        public DisconnectCause toTelecomDisconnectCause(int telephonyDisconnectCause,
                String reason) {
            return DisconnectCauseUtil.toTelecomDisconnectCause(telephonyDisconnectCause, reason);
        }

        @Override
        public DisconnectCause toTelecomDisconnectCause(int telephonyDisconnectCause, String reason,
                int phoneId) {
            return DisconnectCauseUtil.toTelecomDisconnectCause(telephonyDisconnectCause, reason,
                    phoneId);
        }
    };

    /**
     * Overrides SubscriptionManager dependencies for testing.
     */
    @VisibleForTesting
    public void setSubscriptionManagerProxy(SubscriptionManagerProxy proxy) {
        mSubscriptionManagerProxy = proxy;
    }

    /**
     * Overrides TelephonyManager dependencies for testing.
     */
    @VisibleForTesting
    public void setTelephonyManagerProxy(TelephonyManagerProxy proxy) {
        mTelephonyManagerProxy = proxy;
    }

    /**
     * Overrides PhoneFactory dependencies for testing.
     */
    @VisibleForTesting
    public void setPhoneFactoryProxy(PhoneFactoryProxy proxy) {
        mPhoneFactoryProxy = proxy;
    }

    /**
     * Overrides configuration and settings dependencies for testing.
     */
    @VisibleForTesting
    public void setDeviceState(DeviceState state) {
        mDeviceState = state;
    }

    /**
     * Overrides radioOnHelper for testing.
     */
    @VisibleForTesting
    public void setRadioOnHelper(RadioOnHelper radioOnHelper) {
        mRadioOnHelper = radioOnHelper;
    }

    /**
     * Overrides PhoneSwitcher dependencies for testing.
     */
    @VisibleForTesting
    public void setPhoneSwitcherProxy(PhoneSwitcherProxy proxy) {
        mPhoneSwitcherProxy = proxy;
    }

    /**
     * Overrides PhoneNumberUtils dependencies for testing.
     */
    @VisibleForTesting
    public void setPhoneNumberUtilsProxy(PhoneNumberUtilsProxy proxy) {
        mPhoneNumberUtilsProxy = proxy;
    }

    /**
     * Overrides PhoneUtils dependencies for testing.
     */
    @VisibleForTesting
    public void setPhoneUtilsProxy(PhoneUtilsProxy proxy) {
        mPhoneUtilsProxy = proxy;
    }

    /**
     * Override DisconnectCause creation for testing.
     */
    @VisibleForTesting
    public void setDisconnectCauseFactory(DisconnectCauseFactory factory) {
        mDisconnectCauseFactory = factory;
    }

    /**
     * A listener for emergency calls.
     */
    private final TelephonyConnection.TelephonyConnectionListener mEmergencyConnectionListener =
            new TelephonyConnection.TelephonyConnectionListener() {
                @Override
                public void onOriginalConnectionConfigured(TelephonyConnection c) {
                    com.android.internal.telephony.Connection origConn = c.getOriginalConnection();
                    if ((origConn == null) || (mEmergencyStateTracker == null)) {
                        // mEmergencyStateTracker is null when no emergency call has been dialed
                        // after bootup and normal call fails with 380 response.
                        return;
                    }
                    // Update the domain in the case that it changes,for example during initial
                    // setup or when there was an srvcc or internal redial.
                    mEmergencyStateTracker.onEmergencyCallDomainUpdated(origConn.getPhoneType(), c);
                }

                @Override
                public void onStateChanged(Connection connection,
                        @Connection.ConnectionState int state) {
                    if (mEmergencyCallDomainSelectionConnection == null) return;
                    if (connection == null) return;
                    TelephonyConnection c = (TelephonyConnection) connection;
                    Log.i(this, "onStateChanged callId=" + c.getTelecomCallId()
                            + ", state=" + state);
                    if (c.getState() == Connection.STATE_ACTIVE) {
                        mEmergencyStateTracker.onEmergencyCallStateChanged(
                                c.getOriginalConnection().getState(), c);
                        releaseEmergencyCallDomainSelection(false, true);
                    }
                }

                @Override
                public void onConnectionPropertiesChanged(Connection connection,
                        int connectionProperties) {
                    if ((connection == null) || (mEmergencyStateTracker == null)) {
                        return;
                    }
                    TelephonyConnection c = (TelephonyConnection) connection;
                    com.android.internal.telephony.Connection origConn = c.getOriginalConnection();
                    if ((origConn == null) || (!origConn.getState().isAlive())) {
                        // ignore if there is no original connection alive
                        Log.i(this, "onConnectionPropertiesChanged without orig connection alive");
                        return;
                    }
                    Log.i(this, "onConnectionPropertiesChanged prop=" + connectionProperties);
                    mEmergencyStateTracker.onEmergencyCallPropertiesChanged(
                            connectionProperties, c);
                }
            };

    private final TelephonyConnection.TelephonyConnectionListener
            mEmergencyConnectionSatelliteListener =
            new TelephonyConnection.TelephonyConnectionListener() {
                @Override
                public void onStateChanged(Connection connection,
                        @Connection.ConnectionState int state) {
                    if (connection == null) {
                        Log.d(this,
                                "onStateChanged for satellite listener: connection is null");
                        return;
                    }
                    if (mSatelliteSOSMessageRecommender == null) {
                        Log.d(this, "onStateChanged for satellite listener: "
                                + "mSatelliteSOSMessageRecommender is null");
                        return;
                    }

                    TelephonyConnection c = (TelephonyConnection) connection;
                    mSatelliteSOSMessageRecommender.onEmergencyCallConnectionStateChanged(
                            c.getTelecomCallId(), state);
                    if (state == Connection.STATE_DISCONNECTED
                            || state == Connection.STATE_ACTIVE) {
                        c.removeTelephonyConnectionListener(mEmergencyConnectionSatelliteListener);
                        mSatelliteSOSMessageRecommender = null;
                    }
                }
            };

    private void clearNormalCallDomainSelectionConnection() {
        if (mDomainSelectionConnection != null) {
            mDomainSelectionConnection.finishSelection();
            mDomainSelectionConnection = null;
        }
    }

    /**
     * A listener for calls.
     */
    private final TelephonyConnection.TelephonyConnectionListener mNormalCallConnectionListener =
            new TelephonyConnection.TelephonyConnectionListener() {
                @Override
                public void onStateChanged(
                        Connection connection, @Connection.ConnectionState int state) {
                    TelephonyConnection c = (TelephonyConnection) connection;
                    if (c != null) {
                        switch(c.getState()) {
                            case Connection.STATE_ACTIVE: {
                                clearNormalCallDomainSelectionConnection();
                                mNormalCallConnection = null;
                            }
                            break;

                            case Connection.STATE_DISCONNECTED: {
                                // Clear connection if the call state changes from
                                // DIALING -> DISCONNECTED without ACTIVE State.
                                clearNormalCallDomainSelectionConnection();
                                c.removeTelephonyConnectionListener(mNormalCallConnectionListener);
                            }
                            break;
                        }
                    }
                }
            };

    private static class StateHoldingListener extends
            TelephonyConnection.TelephonyConnectionListener {
        private final CompletableFuture<Boolean> mStateHoldingFuture;

        StateHoldingListener(CompletableFuture<Boolean> future) {
            mStateHoldingFuture = future;
        }

        @Override
        public void onStateChanged(
                Connection connection, @Connection.ConnectionState int state) {
            TelephonyConnection c = (TelephonyConnection) connection;
            if (c != null) {
                switch (c.getState()) {
                    case Connection.STATE_HOLDING: {
                        Log.d(LOG_TAG, "Connection " + connection.getTelecomCallId()
                                + " changed to STATE_HOLDING!");
                        mStateHoldingFuture.complete(true);
                        c.removeTelephonyConnectionListener(this);
                    }
                    break;
                    case Connection.STATE_DISCONNECTED: {
                        Log.d(LOG_TAG, "Connection " + connection.getTelecomCallId()
                                + " changed to STATE_DISCONNECTED!");
                        mStateHoldingFuture.complete(false);
                        c.removeTelephonyConnectionListener(this);
                    }
                    break;
                }
            }
        }
    }

    private static class OnDisconnectListener extends
            com.android.internal.telephony.Connection.ListenerBase {
        private final CompletableFuture<Boolean> mFuture;

        OnDisconnectListener(CompletableFuture<Boolean> future) {
            mFuture = future;
        }

        @Override
        public void onDisconnect(int cause) {
            mFuture.complete(true);
        }
    };

    private final DomainSelectionConnection.DomainSelectionConnectionCallback
            mEmergencyDomainSelectionConnectionCallback =
                    new DomainSelectionConnection.DomainSelectionConnectionCallback() {
        @Override
        public void onSelectionTerminated(@DisconnectCauses int cause) {
            mDomainSelectionMainExecutor.execute(() -> {
                Log.i(this, "onSelectionTerminated cause=" + cause);
                if (mEmergencyCallDomainSelectionConnection == null) {
                    Log.i(this, "onSelectionTerminated no DomainSelectionConnection");
                    return;
                }

                // Cross stack redial
                if (cause == android.telephony.DisconnectCause.EMERGENCY_TEMP_FAILURE
                        || cause == android.telephony.DisconnectCause.EMERGENCY_PERM_FAILURE) {
                    if (mEmergencyConnection != null) {
                        boolean isPermanentFailure =
                                cause == android.telephony.DisconnectCause.EMERGENCY_PERM_FAILURE;
                        Log.i(this, "onSelectionTerminated permanent=" + isPermanentFailure);
                        TelephonyConnection c = mEmergencyConnection;
                        Phone phone = mEmergencyCallDomainSelectionConnection.getPhone();
                        mEmergencyConnection.removeTelephonyConnectionListener(
                                mEmergencyConnectionListener);
                        releaseEmergencyCallDomainSelection(true, false);
                        mEmergencyStateTracker.endCall(c);
                        retryOutgoingOriginalConnection(c, phone, isPermanentFailure);
                        return;
                    }
                }
                if (mEmergencyConnection != null) {
                    mEmergencyConnection.hangup(android.telephony.DisconnectCause.OUT_OF_NETWORK);
                    mEmergencyConnection = null;
                }
            });
        }
    };

    private final DomainSelectionConnection.DomainSelectionConnectionCallback
            mCallDomainSelectionConnectionCallback =
            new DomainSelectionConnection.DomainSelectionConnectionCallback() {
                @Override
                public void onSelectionTerminated(@DisconnectCauses int cause) {
                    mDomainSelectionMainExecutor.execute(new Runnable() {
                        int mCause = cause;

                        @Override
                        public void run() {
                            Log.v(this, "Call domain selection terminated.");
                            if (mDomainSelectionConnection != null) {
                                if (mNormalCallConnection != null) {

                                    NormalCallDomainSelectionConnection ncdsConn =
                                            (NormalCallDomainSelectionConnection)
                                                    mDomainSelectionConnection;

                                    // If cause is NOT_VALID then, it's a redial cancellation
                                    if (mCause == android.telephony.DisconnectCause.NOT_VALID) {
                                        mCause = ncdsConn.getDisconnectCause();
                                    }

                                    Log.d(this, "Call connection closed. PreciseCause: "
                                            + ncdsConn.getPreciseDisconnectCause()
                                            + " DisconnectCause: " + ncdsConn.getDisconnectCause()
                                            + " Reason: " + ncdsConn.getReasonMessage());

                                    mNormalCallConnection.setTelephonyConnectionDisconnected(
                                            DisconnectCauseUtil.toTelecomDisconnectCause(mCause,
                                                    ncdsConn.getPreciseDisconnectCause(),
                                                    ncdsConn.getReasonMessage(),
                                                    ncdsConn.getPhoneId(),
                                                    ncdsConn.getImsReasonInfo(),
                                                    new FlagsAdapterImpl()));

                                    mNormalCallConnection.close();
                                    mNormalCallConnection = null;
                                } else {
                                    Log.v(this, "NormalCallConnection is null.");
                                }

                                mDomainSelectionConnection = null;

                            } else {
                                Log.v(this, "DomainSelectionConnection is null.");
                            }
                        }
                    });
                }
            };

    /**
     * A listener to actionable events specific to the TelephonyConnection.
     */
    private final TelephonyConnection.TelephonyConnectionListener mTelephonyConnectionListener =
            new TelephonyConnection.TelephonyConnectionListener() {
        @Override
        public void onOriginalConnectionConfigured(TelephonyConnection c) {
            addConnectionToConferenceController(c);
        }

        @Override
        public void onOriginalConnectionRetry(TelephonyConnection c, boolean isPermanentFailure) {
            retryOutgoingOriginalConnection(c, c.getPhone(), isPermanentFailure);
        }
    };

    private final TelephonyConferenceBase.TelephonyConferenceListener mTelephonyConferenceListener =
            new TelephonyConferenceBase.TelephonyConferenceListener() {
        @Override
        public void onConferenceMembershipChanged(Connection connection) {
            mHoldTracker.updateHoldCapability();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mImsConferenceController = new ImsConferenceController(
                TelecomAccountRegistry.getInstance(this),
                mTelephonyConnectionServiceProxy,
                // FeatureFlagProxy; used to determine if standalone call emulation is enabled.
                // TODO: Move to carrier config
                () -> true);
        setTelephonyManagerProxy(new TelephonyManagerProxyImpl(getApplicationContext()));
        mExpectedComponentName = new ComponentName(this, this.getClass());
        mEmergencyTonePlayer = new EmergencyTonePlayer(this);
        TelecomAccountRegistry.getInstance(this).setTelephonyConnectionService(this);
        mHoldTracker = new HoldTracker();
        mIsTtyEnabled = mDeviceState.isTtyModeEnabled(this);
        mDomainSelectionMainExecutor = getApplicationContext().getMainExecutor();
        mDomainSelectionResolver = DomainSelectionResolver.getInstance();
        mSatelliteController = SatelliteController.getInstance();

        IntentFilter intentFilter = new IntentFilter(
                TelecomManager.ACTION_TTY_PREFERRED_MODE_CHANGED);
        registerReceiver(mTtyBroadcastReceiver, intentFilter,
                android.Manifest.permission.MODIFY_PHONE_STATE, null, Context.RECEIVER_EXPORTED);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        unregisterReceiver(mTtyBroadcastReceiver);
        return super.onUnbind(intent);
    }

    private Conference placeOutgoingConference(ConnectionRequest request,
            Connection resultConnection, Phone phone) {
        if (resultConnection instanceof TelephonyConnection) {
            return placeOutgoingConference((TelephonyConnection) resultConnection, phone, request);
        }
        return null;
    }

    private Conference placeOutgoingConference(TelephonyConnection conferenceHostConnection,
            Phone phone, ConnectionRequest request) {
        updatePhoneAccount(conferenceHostConnection, phone);
        com.android.internal.telephony.Connection originalConnection = null;
        try {
            originalConnection = phone.startConference(
                    getParticipantsToDial(request.getParticipants()),
                    new ImsPhone.ImsDialArgs.Builder()
                    .setVideoState(request.getVideoState())
                    .setRttTextStream(conferenceHostConnection.getRttTextStream())
                    .build());
        } catch (CallStateException e) {
            Log.e(this, e, "placeOutgoingConference, phone.startConference exception: " + e);
            handleCallStateException(e, conferenceHostConnection, phone);
            return null;
        }

        if (originalConnection == null) {
            Log.d(this, "placeOutgoingConference, phone.startConference returned null");
            conferenceHostConnection.setDisconnected(DisconnectCauseUtil.toTelecomDisconnectCause(
                    android.telephony.DisconnectCause.OUTGOING_FAILURE,
                    "conferenceHostConnection is null",
                    phone.getPhoneId()));
            conferenceHostConnection.clearOriginalConnection();
            conferenceHostConnection.destroy();
        } else {
            conferenceHostConnection.setOriginalConnection(originalConnection);
        }

        return prepareConference(conferenceHostConnection, request.getAccountHandle());
    }

    Conference prepareConference(Connection conn, PhoneAccountHandle phoneAccountHandle) {
        if (!(conn instanceof TelephonyConnection)) {
            Log.w(this, "prepareConference returning NULL conference");
            return null;
        }

        TelephonyConnection connection = (TelephonyConnection)conn;

        ImsConference conference = new ImsConference(TelecomAccountRegistry.getInstance(this),
                mTelephonyConnectionServiceProxy, connection,
                phoneAccountHandle, () -> true,
                ImsConferenceController.getCarrierConfig(connection.getPhone()));
        mImsConferenceController.addConference(conference);
        conference.setVideoState(connection,
                connection.getVideoState());
        conference.setVideoProvider(connection,
                connection.getVideoProvider());
        conference.setStatusHints(connection.getStatusHints());
        conference.setAddress(connection.getAddress(),
                connection.getAddressPresentation());
        conference.setCallerDisplayName(connection.getCallerDisplayName(),
                connection.getCallerDisplayNamePresentation());
        conference.setParticipants(connection.getParticipants());
        return conference;
    }

    @Override
    public @Nullable Conference onCreateIncomingConference(
            @Nullable PhoneAccountHandle connectionManagerPhoneAccount,
            @NonNull final ConnectionRequest request) {
        Log.i(this, "onCreateIncomingConference, request: " + request);
        Connection connection = onCreateIncomingConnection(connectionManagerPhoneAccount, request);
        Log.d(this, "onCreateIncomingConference, connection: %s", connection);
        if (connection == null) {
            Log.i(this, "onCreateIncomingConference, implementation returned null connection.");
            return Conference.createFailedConference(
                    new DisconnectCause(DisconnectCause.ERROR, "IMPL_RETURNED_NULL_CONNECTION"),
                    request.getAccountHandle());
        }

        final Phone phone = getPhoneForAccount(request.getAccountHandle(),
                false /* isEmergencyCall*/, null /* not an emergency call */);
        if (phone == null) {
            Log.d(this, "onCreateIncomingConference, phone is null");
            return Conference.createFailedConference(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.OUT_OF_SERVICE,
                            "Phone is null"),
                    request.getAccountHandle());
        }

        return prepareConference(connection, request.getAccountHandle());
    }

    @Override
    public @Nullable Conference onCreateOutgoingConference(
            @Nullable PhoneAccountHandle connectionManagerPhoneAccount,
            @NonNull final ConnectionRequest request) {
        Log.i(this, "onCreateOutgoingConference, request: " + request);
        Connection connection = onCreateOutgoingConnection(connectionManagerPhoneAccount, request);
        Log.d(this, "onCreateOutgoingConference, connection: %s", connection);
        if (connection == null) {
            Log.i(this, "onCreateOutgoingConference, implementation returned null connection.");
            return Conference.createFailedConference(
                    new DisconnectCause(DisconnectCause.ERROR, "IMPL_RETURNED_NULL_CONNECTION"),
                    request.getAccountHandle());
        }

        final Phone phone = getPhoneForAccount(request.getAccountHandle(),
                false /* isEmergencyCall*/, null /* not an emergency call */);
        if (phone == null) {
            Log.d(this, "onCreateOutgoingConference, phone is null");
            return Conference.createFailedConference(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.OUT_OF_SERVICE,
                            "Phone is null"),
                    request.getAccountHandle());
        }

        return placeOutgoingConference(request, connection, phone);
    }

    private String[] getParticipantsToDial(List<Uri> participants) {
        String[] participantsToDial = new String[participants.size()];
        int i = 0;
        for (Uri participant : participants) {
           participantsToDial[i] = participant.getSchemeSpecificPart();
           i++;
        }
        return participantsToDial;
    }

    @Override
    public Connection onCreateOutgoingConnection(
            PhoneAccountHandle connectionManagerPhoneAccount,
            final ConnectionRequest request) {
        Log.i(this, "onCreateOutgoingConnection, request: " + request);

        Uri handle = request.getAddress();
        boolean isAdhocConference = request.isAdhocConferenceCall();

        if (!isAdhocConference && handle == null) {
            Log.d(this, "onCreateOutgoingConnection, handle is null");
            return Connection.createFailedConnection(
                    mDisconnectCauseFactory.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.NO_PHONE_NUMBER_SUPPLIED,
                            "No phone number supplied"));
        }

        String scheme = handle.getScheme();
        String number;
        if (PhoneAccount.SCHEME_VOICEMAIL.equals(scheme)) {
            // TODO: We don't check for SecurityException here (requires
            // CALL_PRIVILEGED permission).
            final Phone phone = getPhoneForAccount(request.getAccountHandle(),
                    false /* isEmergencyCall */, null /* not an emergency call */);
            if (phone == null) {
                Log.d(this, "onCreateOutgoingConnection, phone is null");
                return Connection.createFailedConnection(
                        mDisconnectCauseFactory.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.OUT_OF_SERVICE,
                                "Phone is null"));
            }
            number = phone.getVoiceMailNumber();
            if (TextUtils.isEmpty(number)) {
                Log.d(this, "onCreateOutgoingConnection, no voicemail number set.");
                return Connection.createFailedConnection(
                        mDisconnectCauseFactory.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.VOICEMAIL_NUMBER_MISSING,
                                "Voicemail scheme provided but no voicemail number set.",
                                phone.getPhoneId()));
            }

            // Convert voicemail: to tel:
            handle = Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null);
        } else {
            if (!PhoneAccount.SCHEME_TEL.equals(scheme)) {
                Log.d(this, "onCreateOutgoingConnection, Handle %s is not type tel", scheme);
                return Connection.createFailedConnection(
                        mDisconnectCauseFactory.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.INVALID_NUMBER,
                                "Handle scheme is not type tel"));
            }

            number = handle.getSchemeSpecificPart();
            if (TextUtils.isEmpty(number)) {
                Log.d(this, "onCreateOutgoingConnection, unable to parse number");
                return Connection.createFailedConnection(
                        mDisconnectCauseFactory.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.INVALID_NUMBER,
                                "Unable to parse number"));
            }

            final Phone phone = getPhoneForAccount(request.getAccountHandle(),
                    false /* isEmergencyCall*/, null /* not an emergency call */);
            if (phone != null && CDMA_ACTIVATION_CODE_REGEX_PATTERN.matcher(number).matches()) {
                // Obtain the configuration for the outgoing phone's SIM. If the outgoing number
                // matches the *228 regex pattern, fail the call. This number is used for OTASP, and
                // when dialed could lock LTE SIMs to 3G if not prohibited..
                boolean disableActivation = false;
                CarrierConfigManager cfgManager = (CarrierConfigManager)
                        phone.getContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
                if (cfgManager != null) {
                    disableActivation = cfgManager.getConfigForSubId(phone.getSubId())
                            .getBoolean(CarrierConfigManager.KEY_DISABLE_CDMA_ACTIVATION_CODE_BOOL);
                }

                if (disableActivation) {
                    return Connection.createFailedConnection(
                            mDisconnectCauseFactory.toTelecomDisconnectCause(
                                    android.telephony.DisconnectCause
                                            .CDMA_ALREADY_ACTIVATED,
                                    "Tried to dial *228",
                                    phone.getPhoneId()));
                }
            }
        }

        final boolean isEmergencyNumber = mTelephonyManagerProxy.isCurrentEmergencyNumber(number);
        // Find out if this is a test emergency number
        final boolean isTestEmergencyNumber = isEmergencyNumberTestNumber(number);

        // Convert into emergency number if necessary
        // This is required in some regions (e.g. Taiwan).
        if (isEmergencyNumber) {
            final Phone phone = getPhoneForAccount(request.getAccountHandle(), false,
                    handle.getSchemeSpecificPart());
            // We only do the conversion if the phone is not in service. The un-converted
            // emergency numbers will go to the correct destination when the phone is in-service,
            // so they will only need the special emergency call setup when the phone is out of
            // service.
            if (phone == null || phone.getServiceState().getState()
                    != ServiceState.STATE_IN_SERVICE) {
                String convertedNumber = mPhoneNumberUtilsProxy.convertToEmergencyNumber(this,
                        number);
                if (!TextUtils.equals(convertedNumber, number)) {
                    Log.i(this, "onCreateOutgoingConnection, converted to emergency number");
                    number = convertedNumber;
                    handle = Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null);
                }
            }
        }
        final String numberToDial = number;

        final boolean isAirplaneModeOn = mDeviceState.isAirplaneModeOn(this);

        boolean needToTurnOffSatellite = isSatelliteBlockingCall(isEmergencyNumber);

        // Get the right phone object from the account data passed in.
        final Phone phone = getPhoneForAccount(request.getAccountHandle(), isEmergencyNumber,
                /* Note: when not an emergency, handle can be null for unknown callers */
                handle == null ? null : handle.getSchemeSpecificPart());
        ImsPhone imsPhone = phone != null ? (ImsPhone) phone.getImsPhone() : null;

        boolean isPhoneWifiCallingEnabled = phone != null && phone.isWifiCallingEnabled();
        boolean needToTurnOnRadio = (isEmergencyNumber && (!isRadioOn() || isAirplaneModeOn))
                || (isRadioPowerDownOnBluetooth() && !isPhoneWifiCallingEnabled);

        if (mDomainSelectionResolver.isDomainSelectionSupported()) {
            // Normal routing emergency number shall be handled by normal call domain selctor.
            if (isEmergencyNumber && !isNormalRouting(phone, number)) {
                final Connection resultConnection =
                        placeEmergencyConnection(phone,
                                request, numberToDial, isTestEmergencyNumber,
                                handle, needToTurnOnRadio);
                if (resultConnection != null) return resultConnection;
            }
        }

        if (needToTurnOnRadio || needToTurnOffSatellite) {
            final Uri resultHandle = handle;
            final int originalPhoneType = (phone == null) ? PHONE_TYPE_GSM : phone.getPhoneType();
            final Connection resultConnection = getTelephonyConnection(request, numberToDial,
                    isEmergencyNumber, resultHandle, phone);
            if (mRadioOnHelper == null) {
                mRadioOnHelper = new RadioOnHelper(this);
            }

            if (isEmergencyNumber) {
                mIsEmergencyCallPending = true;
            }
            int timeoutToOnTimeoutCallback = mDomainSelectionResolver.isDomainSelectionSupported()
                    ? TIMEOUT_TO_DYNAMIC_ROUTING_MS : 0;
            final Phone phoneForEmergency = phone;
            mRadioOnHelper.triggerRadioOnAndListen(new RadioOnStateListener.Callback() {
                @Override
                public void onComplete(RadioOnStateListener listener, boolean isRadioReady) {
                    handleOnComplete(isRadioReady, isEmergencyNumber, resultConnection, request,
                            numberToDial, resultHandle, originalPhoneType, phone);
                }

                @Override
                public boolean onTimeout(Phone phone, int serviceState, boolean imsVoiceCapable) {
                    if (mDomainSelectionResolver.isDomainSelectionSupported()) {
                        return isEmergencyNumber;
                    }
                    return false;
                }

                @Override
                public boolean isOkToCall(Phone phone, int serviceState, boolean imsVoiceCapable) {
                    // HAL 1.4 introduced a new variant of dial for emergency calls, which includes
                    // an isTesting parameter. For HAL 1.4+, do not wait for IN_SERVICE, this will
                    // be handled at the RIL/vendor level by emergencyDial(...).
                    boolean waitForInServiceToDialEmergency = isTestEmergencyNumber
                            && phone.getHalVersion(HAL_SERVICE_VOICE)
                            .less(RIL.RADIO_HAL_VERSION_1_4);
                    if (mDomainSelectionResolver.isDomainSelectionSupported()) {
                        if (isEmergencyNumber && phone == phoneForEmergency) {
                            // Since the domain selection service is enabled,
                            // dilaing normal routing emergency number only reaches here.
                            if (!isVoiceInService(phone, imsVoiceCapable)) {
                                // Wait for voice in service.
                                // That is, wait for IMS registration on PS only network.
                                serviceState = ServiceState.STATE_OUT_OF_SERVICE;
                                waitForInServiceToDialEmergency = true;
                            }
                        }
                    }
                    if (isEmergencyNumber && !waitForInServiceToDialEmergency) {
                        // We currently only look to make sure that the radio is on before dialing.
                        // We should be able to make emergency calls at any time after the radio has
                        // been powered on and isn't in the UNAVAILABLE state, even if it is
                        // reporting the OUT_OF_SERVICE state.
                        return phone.getState() == PhoneConstants.State.OFFHOOK
                                || (phone.getServiceStateTracker().isRadioOn()
                                && !mSatelliteController.isSatelliteEnabled());
                    } else {
                        SubscriptionInfoInternal subInfo = SubscriptionManagerService
                                .getInstance().getSubscriptionInfoInternal(phone.getSubId());
                        // Wait until we are in service and ready to make calls. This can happen
                        // when we power down the radio on bluetooth to save power on watches or
                        // if it is a test emergency number and we have to wait for the device
                        // to move IN_SERVICE before the call can take place over normal
                        // routing.
                        return phone.getState() == PhoneConstants.State.OFFHOOK
                                // Do not wait for voice in service on opportunistic SIMs.
                                || subInfo != null && subInfo.isOpportunistic()
                                || (serviceState == ServiceState.STATE_IN_SERVICE
                                && !isSatelliteBlockingCall(isEmergencyNumber));
                    }
                }
            }, isEmergencyNumber && !isTestEmergencyNumber, phone, isTestEmergencyNumber,
                    timeoutToOnTimeoutCallback);
            // Return the still unconnected GsmConnection and wait for the Radios to boot before
            // connecting it to the underlying Phone.
            return resultConnection;
        } else {
            if (!canAddCall() && !isEmergencyNumber) {
                Log.d(this, "onCreateOutgoingConnection, cannot add call .");
                return Connection.createFailedConnection(
                        new DisconnectCause(DisconnectCause.ERROR,
                                getApplicationContext().getText(
                                        R.string.incall_error_cannot_add_call),
                                getApplicationContext().getText(
                                        R.string.incall_error_cannot_add_call),
                                "Add call restricted due to ongoing video call"));
            }

            if (!isEmergencyNumber) {
                if ((mSatelliteController.isSatelliteEnabled()
                        || isCallDisallowedDueToSatellite(phone))
                        && (imsPhone == null || !imsPhone.canMakeWifiCall())) {
                    Log.d(this, "onCreateOutgoingConnection, cannot make call in satellite mode.");
                    return Connection.createFailedConnection(
                            mDisconnectCauseFactory.toTelecomDisconnectCause(
                                    android.telephony.DisconnectCause.SATELLITE_ENABLED,
                                    "Call failed because satellite modem is enabled."));
                }
                final Connection resultConnection = getTelephonyConnection(request, numberToDial,
                        false, handle, phone);
                if (isAdhocConference) {
                    if (resultConnection instanceof TelephonyConnection) {
                        TelephonyConnection conn = (TelephonyConnection)resultConnection;
                        conn.setParticipants(request.getParticipants());
                    }
                    return resultConnection;
                } else {
                    if (mTelephonyManagerProxy.isConcurrentCallsPossible()) {
                        Conferenceable c = maybeHoldCallsOnOtherSubs(request.getAccountHandle());
                        if (c != null) {
                            delayDialForOtherSubHold(phone, c, (success) -> {
                                Log.d(this,
                                        "onCreateOutgoingConn - delayDialForOtherSubHold"
                                                + " success = " + success);
                                if (success) {
                                    placeOutgoingConnection(request, resultConnection,
                                            phone);
                                } else {
                                    ((TelephonyConnection) resultConnection).hangup(
                                            android.telephony.DisconnectCause.LOCAL);
                                }
                            });
                            return resultConnection;
                        }
                    }
                    return placeOutgoingConnection(request, resultConnection, phone);
                }
            } else {
                final Connection resultConnection = getTelephonyConnection(request, numberToDial,
                        true, handle, phone);

                CompletableFuture<Void> maybeHoldFuture =
                        checkAndHoldCallsOnOtherSubsForEmergencyCall(request,
                                resultConnection, phone);
                Consumer<Boolean> ddsSwitchConsumer = (result) -> {
                    Log.i(this, "onCreateOutgoingConn emergency-"
                            + " delayDialForDdsSwitch result = " + result);
                    placeOutgoingConnection(request, resultConnection, phone);
                };
                maybeHoldFuture.thenRun(() -> delayDialForDdsSwitch(phone, ddsSwitchConsumer));
                return resultConnection;
            }
        }
    }

    private CompletableFuture<Void> checkAndHoldCallsOnOtherSubsForEmergencyCall(
            ConnectionRequest request, Connection resultConnection, Phone phone) {
        CompletableFuture<Void> maybeHoldFuture = CompletableFuture.completedFuture(null);
        if (mTelephonyManagerProxy.isConcurrentCallsPossible()
                && shouldHoldForEmergencyCall(phone)) {
            // If the PhoneAccountHandle was adjusted on building the TelephonyConnection,
            // the relevant PhoneAccountHandle will be updated in resultConnection.
            PhoneAccountHandle phoneAccountHandle =
                    resultConnection.getPhoneAccountHandle() == null
                    ? request.getAccountHandle() : resultConnection.getPhoneAccountHandle();
            Conferenceable c = maybeHoldCallsOnOtherSubs(phoneAccountHandle);
            if (c != null) {
                maybeHoldFuture = delayDialForOtherSubHold(phone, c, (success) -> {
                    Log.i(this, "checkAndHoldCallsOnOtherSubsForEmergencyCall"
                            + " delayDialForOtherSubHold success = " + success);
                    if (!success) {
                        // Terminates the existing call to make way for the emergency call.
                        hangup(c, android.telephony.DisconnectCause
                                .OUTGOING_EMERGENCY_CALL_PLACED);
                    }
                });
            }
        }
        return maybeHoldFuture;
    }

    private Connection placeOutgoingConnection(ConnectionRequest request,
            Connection resultConnection, Phone phone) {
        // If there was a failure, the resulting connection will not be a TelephonyConnection,
        // so don't place the call!
        if (resultConnection instanceof TelephonyConnection) {
            if (request.getExtras() != null && request.getExtras().getBoolean(
                    TelecomManager.EXTRA_USE_ASSISTED_DIALING, false)) {
                ((TelephonyConnection) resultConnection).setIsUsingAssistedDialing(true);
            }
            placeOutgoingConnection((TelephonyConnection) resultConnection, phone, request);
        }
        return resultConnection;
    }

    private boolean isEmergencyNumberTestNumber(String number) {
        number = PhoneNumberUtils.stripSeparators(number);
        Map<Integer, List<EmergencyNumber>> list =
                mTelephonyManagerProxy.getCurrentEmergencyNumberList();
        // Do not worry about which subscription the test emergency call is on yet, only detect that
        // it is an emergency.
        for (Integer sub : list.keySet()) {
            for (EmergencyNumber eNumber : list.get(sub)) {
                if (number.equals(eNumber.getNumber())
                        && eNumber.isFromSources(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_TEST)) {
                    Log.i(this, "isEmergencyNumberTestNumber: " + number + " has been detected as "
                            + "a test emergency number.,");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @return whether radio has recently been turned on for emergency call but hasn't actually
     * dialed the call yet.
     */
    public boolean isEmergencyCallPending() {
        return mIsEmergencyCallPending;
    }

    /**
     * Whether the cellular radio is power off because the device is on Bluetooth.
     */
    private boolean isRadioPowerDownOnBluetooth() {
        final boolean allowed = mDeviceState.isRadioPowerDownAllowedOnBluetooth(this);
        final int cellOn = mDeviceState.getCellOnStatus(this);
        return (allowed && cellOn == PhoneConstants.CELL_ON_FLAG && !isRadioOn());
    }

    /**
     * Handle the onComplete callback of RadioOnStateListener.
     */
    private void handleOnComplete(boolean isRadioReady, boolean isEmergencyNumber,
            Connection originalConnection, ConnectionRequest request, String numberToDial,
            Uri handle, int originalPhoneType, Phone phone) {
        // Make sure the Call has not already been canceled by the user.
        if (originalConnection.getState() == Connection.STATE_DISCONNECTED) {
            Log.i(this, "Call disconnected before the outgoing call was placed. Skipping call "
                    + "placement.");
            if (isEmergencyNumber) {
                // If call is already canceled by the user, notify modem to exit emergency call
                // mode by sending radio on with forEmergencyCall=false.
                for (Phone curPhone : mPhoneFactoryProxy.getPhones()) {
                    curPhone.setRadioPower(true, false, false, true);
                }
                mIsEmergencyCallPending = false;
            }
            return;
        }
        if (isRadioReady) {
            if (!isEmergencyNumber) {
                adjustAndPlaceOutgoingConnection(phone, originalConnection, request, numberToDial,
                        handle, originalPhoneType, false);
            } else {
                delayDialForDdsSwitch(phone, result -> {
                    Log.i(this, "handleOnComplete - delayDialForDdsSwitch "
                            + "result = " + result);
                    adjustAndPlaceOutgoingConnection(phone, originalConnection, request,
                            numberToDial, handle, originalPhoneType, true);
                    mIsEmergencyCallPending = false;
                });
            }
        } else {
            if (isSatelliteBlockingCall(isEmergencyNumber)) {
                Log.w(LOG_TAG, "handleOnComplete, failed to turn off satellite modem");
                closeOrDestroyConnection(originalConnection,
                        mDisconnectCauseFactory.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.SATELLITE_ENABLED,
                                "Failed to turn off satellite modem."));
            } else {
                Log.w(LOG_TAG, "handleOnComplete, failed to turn on radio");
                closeOrDestroyConnection(originalConnection,
                        mDisconnectCauseFactory.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.POWER_OFF,
                                "Failed to turn on radio."));
            }
            mIsEmergencyCallPending = false;
        }
    }

    private void adjustAndPlaceOutgoingConnection(Phone phone, Connection connectionToEvaluate,
            ConnectionRequest request, String numberToDial, Uri handle, int originalPhoneType,
            boolean isEmergencyNumber) {
        // If the PhoneType of the Phone being used is different than the Default Phone, then we
        // need to create a new Connection using that PhoneType and replace it in Telecom.
        if (phone.getPhoneType() != originalPhoneType) {
            Connection repConnection = getTelephonyConnection(request, numberToDial,
                    isEmergencyNumber, handle, phone);
            // If there was a failure, the resulting connection will not be a TelephonyConnection,
            // so don't place the call, just return!
            if (repConnection instanceof TelephonyConnection) {
                placeOutgoingConnection((TelephonyConnection) repConnection, phone, request);
            }
            // Notify Telecom of the new Connection type.
            // TODO: Switch out the underlying connection instead of creating a new
            // one and causing UI Jank.
            boolean noActiveSimCard = SubscriptionManagerService.getInstance()
                    .getActiveSubInfoCount(phone.getContext().getOpPackageName(),
                            phone.getContext().getAttributionTag(), true/*isForAllProfile*/) == 0;
            // If there's no active sim card and the device is in emergency mode, use E account.
            addExistingConnection(mPhoneUtilsProxy.makePstnPhoneAccountHandleWithPrefix(
                    phone, "", isEmergencyNumber && noActiveSimCard), repConnection);
            // Remove the old connection from Telecom after.
            closeOrDestroyConnection(connectionToEvaluate,
                    mDisconnectCauseFactory.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.OUTGOING_CANCELED,
                            "Reconnecting outgoing Emergency Call.",
                            phone.getPhoneId()));
        } else {
            placeOutgoingConnection((TelephonyConnection) connectionToEvaluate, phone, request);
        }
    }

    /**
     * @return {@code true} if any other call is disabling the ability to add calls, {@code false}
     *      otherwise.
     */
    private boolean canAddCall() {
        Collection<Connection> connections = getAllConnections();
        for (Connection connection : connections) {
            if (connection.getExtras() != null &&
                    connection.getExtras().getBoolean(Connection.EXTRA_DISABLE_ADD_CALL, false)) {
                return false;
            }
        }
        return true;
    }

    private Connection getTelephonyConnection(final ConnectionRequest request, final String number,
            boolean isEmergencyNumber, final Uri handle, Phone phone) {

        if (phone == null) {
            final Context context = getApplicationContext();
            if (mDeviceState.shouldCheckSimStateBeforeOutgoingCall(this)) {
                // Check SIM card state before the outgoing call.
                // Start the SIM unlock activity if PIN_REQUIRED.
                final Phone defaultPhone = mPhoneFactoryProxy.getDefaultPhone();
                final IccCard icc = defaultPhone.getIccCard();
                IccCardConstants.State simState = IccCardConstants.State.UNKNOWN;
                if (icc != null) {
                    simState = icc.getState();
                }
                if (simState == IccCardConstants.State.PIN_REQUIRED) {
                    final String simUnlockUiPackage = context.getResources().getString(
                            R.string.config_simUnlockUiPackage);
                    final String simUnlockUiClass = context.getResources().getString(
                            R.string.config_simUnlockUiClass);
                    if (simUnlockUiPackage != null && simUnlockUiClass != null) {
                        Intent simUnlockIntent = new Intent().setComponent(new ComponentName(
                                simUnlockUiPackage, simUnlockUiClass));
                        simUnlockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            context.startActivity(simUnlockIntent);
                        } catch (ActivityNotFoundException exception) {
                            Log.e(this, exception, "Unable to find SIM unlock UI activity.");
                        }
                    }
                    return Connection.createFailedConnection(
                            mDisconnectCauseFactory.toTelecomDisconnectCause(
                                    android.telephony.DisconnectCause.OUT_OF_SERVICE,
                                    "SIM_STATE_PIN_REQUIRED"));
                }
            }

            Log.d(this, "onCreateOutgoingConnection, phone is null");
            return Connection.createFailedConnection(
                    mDisconnectCauseFactory.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.OUT_OF_SERVICE, "Phone is null"));
        }

        // Check both voice & data RAT to enable normal CS call,
        // when voice RAT is OOS but Data RAT is present.
        int state = phone.getServiceState().getState();
        if (state == ServiceState.STATE_OUT_OF_SERVICE) {
            int dataNetType = phone.getServiceState().getDataNetworkType();
            if (dataNetType == TelephonyManager.NETWORK_TYPE_LTE ||
                    dataNetType == TelephonyManager.NETWORK_TYPE_LTE_CA ||
                    dataNetType == TelephonyManager.NETWORK_TYPE_NR) {
                state = phone.getServiceState().getDataRegistrationState();
            }
        }

        // If we're dialing a non-emergency number and the phone is in ECM mode, reject the call if
        // carrier configuration specifies that we cannot make non-emergency calls in ECM mode.
        if (!isEmergencyNumber && phone.isInEcm()) {
            boolean allowNonEmergencyCalls = true;
            CarrierConfigManager cfgManager = (CarrierConfigManager)
                    phone.getContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
            if (cfgManager != null) {
                allowNonEmergencyCalls = cfgManager.getConfigForSubId(phone.getSubId())
                        .getBoolean(CarrierConfigManager.KEY_ALLOW_NON_EMERGENCY_CALLS_IN_ECM_BOOL);
            }

            if (!allowNonEmergencyCalls) {
                return Connection.createFailedConnection(
                        mDisconnectCauseFactory.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.CDMA_NOT_EMERGENCY,
                                "Cannot make non-emergency call in ECM mode.",
                                phone.getPhoneId()));
            }
        }

        if (!isEmergencyNumber) {
            switch (state) {
                case ServiceState.STATE_IN_SERVICE:
                case ServiceState.STATE_EMERGENCY_ONLY:
                    break;
                case ServiceState.STATE_OUT_OF_SERVICE:
                    if (phone.isUtEnabled() && number.endsWith("#")) {
                        Log.d(this, "onCreateOutgoingConnection dial for UT");
                        break;
                    } else {
                        return Connection.createFailedConnection(
                                mDisconnectCauseFactory.toTelecomDisconnectCause(
                                        android.telephony.DisconnectCause.OUT_OF_SERVICE,
                                        "ServiceState.STATE_OUT_OF_SERVICE",
                                        phone.getPhoneId()));
                    }
                case ServiceState.STATE_POWER_OFF:
                    // Don't disconnect if radio is power off because the device is on Bluetooth.
                    if (isRadioPowerDownOnBluetooth()) {
                        break;
                    }
                    return Connection.createFailedConnection(
                            mDisconnectCauseFactory.toTelecomDisconnectCause(
                                    android.telephony.DisconnectCause.POWER_OFF,
                                    "ServiceState.STATE_POWER_OFF",
                                    phone.getPhoneId()));
                default:
                    Log.d(this, "onCreateOutgoingConnection, unknown service state: %d", state);
                    return Connection.createFailedConnection(
                            mDisconnectCauseFactory.toTelecomDisconnectCause(
                                    android.telephony.DisconnectCause.OUTGOING_FAILURE,
                                    "Unknown service state " + state,
                                    phone.getPhoneId()));
            }
        }

        final boolean isTtyModeEnabled = mDeviceState.isTtyModeEnabled(this);
        if (VideoProfile.isVideo(request.getVideoState()) && isTtyModeEnabled
                && !isEmergencyNumber) {
            return Connection.createFailedConnection(mDisconnectCauseFactory.toTelecomDisconnectCause(
                    android.telephony.DisconnectCause.VIDEO_CALL_NOT_ALLOWED_WHILE_TTY_ENABLED,
                    null, phone.getPhoneId()));
        }

        // Check for additional limits on CDMA phones.
        final Connection failedConnection = checkAdditionalOutgoingCallLimits(phone);
        if (failedConnection != null) {
            return failedConnection;
        }

        // Check roaming status to see if we should block custom call forwarding codes
        if (blockCallForwardingNumberWhileRoaming(phone, number)) {
            return Connection.createFailedConnection(
                    mDisconnectCauseFactory.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.DIALED_CALL_FORWARDING_WHILE_ROAMING,
                            "Call forwarding while roaming",
                            phone.getPhoneId()));
        }

        PhoneAccountHandle accountHandle = adjustAccountHandle(phone, request.getAccountHandle());
        final TelephonyConnection connection =
                createConnectionFor(phone, null, true /* isOutgoing */, accountHandle,
                        request.getTelecomCallId(), request.isAdhocConferenceCall());
        if (connection == null) {
            return Connection.createFailedConnection(
                    mDisconnectCauseFactory.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.OUTGOING_FAILURE,
                            "Invalid phone type",
                            phone.getPhoneId()));
        }
        if (!Objects.equals(request.getAccountHandle(), accountHandle)) {
            Log.i(this, "onCreateOutgoingConnection, update phoneAccountHandle, accountHandle = "
                    + accountHandle);
            connection.setPhoneAccountHandle(accountHandle);
        }
        connection.setAddress(handle, PhoneConstants.PRESENTATION_ALLOWED);
        connection.setTelephonyConnectionInitializing();
        connection.setTelephonyVideoState(request.getVideoState());
        connection.setRttTextStream(request.getRttTextStream());
        connection.setTtyEnabled(isTtyModeEnabled);
        connection.setIsAdhocConferenceCall(request.isAdhocConferenceCall());
        connection.setParticipants(request.getParticipants());
        return connection;
    }

    @Override
    public Connection onCreateIncomingConnection(
            PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        Log.i(this, "onCreateIncomingConnection, request: " + request);
        // If there is an incoming emergency CDMA Call (while the phone is in ECBM w/ No SIM),
        // make sure the PhoneAccount lookup retrieves the default Emergency Phone.
        PhoneAccountHandle accountHandle = request.getAccountHandle();
        boolean isEmergency = false;
        if (accountHandle != null && PhoneUtils.EMERGENCY_ACCOUNT_HANDLE_ID.equals(
                accountHandle.getId())) {
            Log.i(this, "Emergency PhoneAccountHandle is being used for incoming call... " +
                    "Treat as an Emergency Call.");
            isEmergency = true;
        }
        Phone phone = getPhoneForAccount(accountHandle, isEmergency,
                /* Note: when not an emergency, handle can be null for unknown callers */
                request.getAddress() == null ? null : request.getAddress().getSchemeSpecificPart());
        if (phone == null) {
            return Connection.createFailedConnection(
                    mDisconnectCauseFactory.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.ERROR_UNSPECIFIED,
                            "Phone is null"));
        }

        Bundle extras = request.getExtras();
        String disconnectMessage = null;
        if (extras.containsKey(TelecomManager.EXTRA_CALL_DISCONNECT_MESSAGE)) {
            disconnectMessage = extras.getString(TelecomManager.EXTRA_CALL_DISCONNECT_MESSAGE);
            Log.i(this, "onCreateIncomingConnection Disconnect message " + disconnectMessage);
        }

        Call call = phone.getRingingCall();
        if (!call.getState().isRinging()
                || (disconnectMessage != null
                && disconnectMessage.equals(TelecomManager.CALL_AUTO_DISCONNECT_MESSAGE_STRING))) {
            Log.i(this, "onCreateIncomingConnection, no ringing call");
            Connection connection = Connection.createFailedConnection(
                    mDisconnectCauseFactory.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.INCOMING_MISSED,
                            "Found no ringing call",
                            phone.getPhoneId()));

            long time = extras.getLong(TelecomManager.EXTRA_CALL_CREATED_EPOCH_TIME_MILLIS);
            if (time != 0) {
                Log.i(this, "onCreateIncomingConnection. Set connect time info.");
                connection.setConnectTimeMillis(time);
            }

            Uri address = extras.getParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS);
            if (address != null) {
                Log.i(this, "onCreateIncomingConnection. Set caller id info.");
                connection.setAddress(address, TelecomManager.PRESENTATION_ALLOWED);
            }

            return connection;
        }

        // If there are multiple Connections tracked in a call, grab the latest, since it is most
        // likely to be the incoming call.
        com.android.internal.telephony.Connection originalConnection = call.getLatestConnection();
        if (isOriginalConnectionKnown(originalConnection)) {
            Log.i(this, "onCreateIncomingConnection, original connection already registered");
            return Connection.createCanceledConnection();
        }

        TelephonyConnection connection =
                createConnectionFor(phone, originalConnection, false /* isOutgoing */,
                        request.getAccountHandle(), request.getTelecomCallId(),
                        request.isAdhocConferenceCall());

        handleIncomingRtt(request, originalConnection);
        if (connection == null) {
            return Connection.createCanceledConnection();
        } else {
            // Add extra to call if answering this incoming call would cause an in progress call on
            // another subscription to be disconnected.
            maybeIndicateAnsweringWillDisconnect(connection, request.getAccountHandle());

            connection.setTtyEnabled(mDeviceState.isTtyModeEnabled(getApplicationContext()));
            return connection;
        }
    }

    private void handleIncomingRtt(ConnectionRequest request,
            com.android.internal.telephony.Connection originalConnection) {
        if (originalConnection == null
                || originalConnection.getPhoneType() != PhoneConstants.PHONE_TYPE_IMS) {
            if (request.isRequestingRtt()) {
                Log.w(this, "Requesting RTT on non-IMS call, ignoring");
            }
            return;
        }

        ImsPhoneConnection imsOriginalConnection = (ImsPhoneConnection) originalConnection;
        if (!request.isRequestingRtt()) {
            if (imsOriginalConnection.isRttEnabledForCall()) {
                Log.w(this, "Incoming call requested RTT but we did not get a RttTextStream");
            }
            return;
        }

        Log.i(this, "Setting RTT stream on ImsPhoneConnection in case we need it later");
        imsOriginalConnection.setCurrentRttTextStream(request.getRttTextStream());

        if (!imsOriginalConnection.isRttEnabledForCall()) {
            if (request.isRequestingRtt()) {
                Log.w(this, "Incoming call processed as RTT but did not come in as one. Ignoring");
            }
            return;
        }

        Log.i(this, "Setting the call to be answered with RTT on.");
        imsOriginalConnection.getImsCall().setAnswerWithRtt();
    }

    /**
     * Called by the {@link ConnectionService} when a newly created {@link Connection} has been
     * added to the {@link ConnectionService} and sent to Telecom.  Here it is safe to send
     * connection events.
     *
     * @param connection the {@link Connection}.
     */
    @Override
    public void onCreateConnectionComplete(Connection connection) {
        if (connection instanceof TelephonyConnection) {
            TelephonyConnection telephonyConnection = (TelephonyConnection) connection;
            maybeSendInternationalCallEvent(telephonyConnection);
        }
    }

    @Override
    public void onCreateIncomingConnectionFailed(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        Log.i(this, "onCreateIncomingConnectionFailed, request: " + request);
        // If there is an incoming emergency CDMA Call (while the phone is in ECBM w/ No SIM),
        // make sure the PhoneAccount lookup retrieves the default Emergency Phone.
        PhoneAccountHandle accountHandle = request.getAccountHandle();
        boolean isEmergency = false;
        if (accountHandle != null && PhoneUtils.EMERGENCY_ACCOUNT_HANDLE_ID.equals(
                accountHandle.getId())) {
            Log.w(this, "onCreateIncomingConnectionFailed:Emergency call failed... ");
            isEmergency = true;
        }
        Phone phone = getPhoneForAccount(accountHandle, isEmergency,
                /* Note: when not an emergency, handle can be null for unknown callers */
                request.getAddress() == null ? null : request.getAddress().getSchemeSpecificPart());
        if (phone == null) {
            Log.w(this, "onCreateIncomingConnectionFailed: can not find corresponding phone.");
            return;
        }

        Call call = phone.getRingingCall();
        if (!call.getState().isRinging()) {
            Log.w(this, "onCreateIncomingConnectionFailed, no ringing call found for failed call");
            return;
        }

        com.android.internal.telephony.Connection originalConnection =
                call.getState() == Call.State.WAITING
                        ? call.getLatestConnection() : call.getEarliestConnection();
        TelephonyConnection knownConnection =
                getConnectionForOriginalConnection(originalConnection);
        if (knownConnection != null) {
            Log.w(this, "onCreateIncomingConnectionFailed, original connection already registered."
                    + " Hanging it up.");
            knownConnection.onAbort();
            return;
        }

        TelephonyConnection connection =
                createConnectionFor(phone, originalConnection, false /* isOutgoing */,
                        request.getAccountHandle(), request.getTelecomCallId());
        if (connection == null) {
            Log.w(this, "onCreateIncomingConnectionFailed, TelephonyConnection created as null, "
                    + "ignoring.");
            return;
        }

        // We have to do all of this work because in some cases, hanging up the call maps to
        // different underlying signaling (CDMA), which is already encapsulated in
        // TelephonyConnection.
        connection.onReject();
        connection.close();
    }

    /**
     * Called by the {@link ConnectionService} when a newly created {@link Conference} has been
     * added to the {@link ConnectionService} and sent to Telecom.  Here it is safe to send
     * connection events.
     *
     * @param conference the {@link Conference}.
     */
    @Override
    public void onCreateConferenceComplete(Conference conference) {
        if (conference instanceof ImsConference) {
            ImsConference imsConference = (ImsConference)conference;
            TelephonyConnection telephonyConnection =
                    (TelephonyConnection)(imsConference.getConferenceHost());
            maybeSendInternationalCallEvent(telephonyConnection);
        }
    }

    public void onCreateIncomingConferenceFailed(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        Log.i(this, "onCreateIncomingConferenceFailed, request: " + request);
        onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request);
    }

    @Override
    public void triggerConferenceRecalculate() {
        if (mTelephonyConferenceController.shouldRecalculate()) {
            mTelephonyConferenceController.recalculate();
        }
    }

    @Override
    public Connection onCreateUnknownConnection(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        Log.i(this, "onCreateUnknownConnection, request: " + request);
        // Use the registered emergency Phone if the PhoneAccountHandle is set to Telephony's
        // Emergency PhoneAccount
        PhoneAccountHandle accountHandle = request.getAccountHandle();
        boolean isEmergency = false;
        if (accountHandle != null && PhoneUtils.EMERGENCY_ACCOUNT_HANDLE_ID.equals(
                accountHandle.getId())) {
            Log.i(this, "Emergency PhoneAccountHandle is being used for unknown call... " +
                    "Treat as an Emergency Call.");
            isEmergency = true;
        }
        Phone phone = getPhoneForAccount(accountHandle, isEmergency,
                /* Note: when not an emergency, handle can be null for unknown callers */
                request.getAddress() == null ? null : request.getAddress().getSchemeSpecificPart());
        if (phone == null) {
            return Connection.createFailedConnection(
                    mDisconnectCauseFactory.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.ERROR_UNSPECIFIED,
                            "Phone is null"));
        }
        Bundle extras = request.getExtras();

        final List<com.android.internal.telephony.Connection> allConnections = new ArrayList<>();

        // Handle the case where an unknown connection has an IMS external call ID specified; we can
        // skip the rest of the guesswork and just grad that unknown call now.
        if (phone.getImsPhone() != null && extras != null &&
                extras.containsKey(ImsExternalCallTracker.EXTRA_IMS_EXTERNAL_CALL_ID)) {

            ImsPhone imsPhone = (ImsPhone) phone.getImsPhone();
            ImsExternalCallTracker externalCallTracker = imsPhone.getExternalCallTracker();
            int externalCallId = extras.getInt(ImsExternalCallTracker.EXTRA_IMS_EXTERNAL_CALL_ID,
                    -1);

            if (externalCallTracker != null) {
                com.android.internal.telephony.Connection connection =
                        externalCallTracker.getConnectionById(externalCallId);

                if (connection != null) {
                    allConnections.add(connection);
                }
            }
        }

        if (allConnections.isEmpty()) {
            final Call ringingCall = phone.getRingingCall();
            if (ringingCall.hasConnections()) {
                allConnections.addAll(ringingCall.getConnections());
            }
            final Call foregroundCall = phone.getForegroundCall();
            if ((foregroundCall.getState() != Call.State.DISCONNECTED)
                    && (foregroundCall.hasConnections())) {
                allConnections.addAll(foregroundCall.getConnections());
            }
            if (phone.getImsPhone() != null) {
                final Call imsFgCall = phone.getImsPhone().getForegroundCall();
                if ((imsFgCall.getState() != Call.State.DISCONNECTED) && imsFgCall
                        .hasConnections()) {
                    allConnections.addAll(imsFgCall.getConnections());
                }
            }
            final Call backgroundCall = phone.getBackgroundCall();
            if (backgroundCall.hasConnections()) {
                allConnections.addAll(phone.getBackgroundCall().getConnections());
            }
        }

        com.android.internal.telephony.Connection unknownConnection = null;
        for (com.android.internal.telephony.Connection telephonyConnection : allConnections) {
            if (!isOriginalConnectionKnown(telephonyConnection)) {
                unknownConnection = telephonyConnection;
                Log.d(this, "onCreateUnknownConnection: conn = " + unknownConnection);
                break;
            }
        }

        if (unknownConnection == null) {
            Log.i(this, "onCreateUnknownConnection, did not find previously unknown connection.");
            return Connection.createCanceledConnection();
        }

        // We should rely on the originalConnection to get the video state.  The request coming
        // from Telecom does not know the video state of the unknown call.
        int videoState = unknownConnection != null ? unknownConnection.getVideoState() :
                VideoProfile.STATE_AUDIO_ONLY;

        TelephonyConnection connection =
                createConnectionFor(phone, unknownConnection,
                        !unknownConnection.isIncoming() /* isOutgoing */,
                        request.getAccountHandle(), request.getTelecomCallId()
                );

        if (connection == null) {
            return Connection.createCanceledConnection();
        } else {
            connection.updateState();
            return connection;
        }
    }

    /**
     * Conferences two connections.
     *
     * Note: The {@link android.telecom.RemoteConnection#setConferenceableConnections(List)} API has
     * a limitation in that it can only specify conferenceables which are instances of
     * {@link android.telecom.RemoteConnection}.  In the case of an {@link ImsConference}, the
     * regular {@link Connection#setConferenceables(List)} API properly handles being able to merge
     * a {@link Conference} and a {@link Connection}.  As a result when, merging a
     * {@link android.telecom.RemoteConnection} into a {@link android.telecom.RemoteConference}
     * require merging a {@link ConferenceParticipantConnection} which is a child of the
     * {@link Conference} with a {@link TelephonyConnection}.  The
     * {@link ConferenceParticipantConnection} class does not have the capability to initiate a
     * conference merge, so we need to call
     * {@link TelephonyConnection#performConference(Connection)} on either {@code connection1} or
     * {@code connection2}, one of which is an instance of {@link TelephonyConnection}.
     *
     * @param connection1 A connection to merge into a conference call.
     * @param connection2 A connection to merge into a conference call.
     */
    @Override
    public void onConference(Connection connection1, Connection connection2) {
        if (connection1 instanceof TelephonyConnection) {
            ((TelephonyConnection) connection1).performConference(connection2);
        } else if (connection2 instanceof TelephonyConnection) {
            ((TelephonyConnection) connection2).performConference(connection1);
        } else {
            Log.w(this, "onConference - cannot merge connections " +
                    "Connection1: %s, Connection2: %2", connection1, connection2);
        }
    }

    @Override
    public void onConnectionAdded(Connection connection) {
        if (connection instanceof Holdable && !isExternalConnection(connection)) {
            mHoldTracker.addHoldable((Holdable) connection);
        }
    }

    @Override
    public void onConnectionRemoved(Connection connection) {
        if (connection instanceof Holdable && !isExternalConnection(connection)) {
            mHoldTracker.removeHoldable((Holdable) connection);
        }
    }

    @Override
    public void onConferenceAdded(Conference conference) {
        if (conference instanceof Holdable) {
            mHoldTracker.addHoldable((Holdable) conference);
        }
    }

    @Override
    public void onConferenceRemoved(Conference conference) {
        if (conference instanceof Holdable) {
            mHoldTracker.removeHoldable((Holdable) conference);
        }
    }

    private boolean isExternalConnection(Connection connection) {
        return (connection.getConnectionProperties() & Connection.PROPERTY_IS_EXTERNAL_CALL)
                == Connection.PROPERTY_IS_EXTERNAL_CALL;
    }

    private boolean blockCallForwardingNumberWhileRoaming(Phone phone, String number) {
        if (phone == null || TextUtils.isEmpty(number) || !phone.getServiceState().getRoaming()) {
            return false;
        }
        boolean allowPrefixIms = true;
        String[] blockPrefixes = null;
        CarrierConfigManager cfgManager = (CarrierConfigManager)
                phone.getContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (cfgManager != null) {
            allowPrefixIms = cfgManager.getConfigForSubId(phone.getSubId()).getBoolean(
                    CarrierConfigManager.KEY_SUPPORT_IMS_CALL_FORWARDING_WHILE_ROAMING_BOOL,
                    true);
            if (allowPrefixIms && useImsForAudioOnlyCall(phone)) {
                return false;
            }
            blockPrefixes = cfgManager.getConfigForSubId(phone.getSubId()).getStringArray(
                    CarrierConfigManager.KEY_CALL_FORWARDING_BLOCKS_WHILE_ROAMING_STRING_ARRAY);
        }

        if (blockPrefixes != null) {
            for (String prefix : blockPrefixes) {
                if (number.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean useImsForAudioOnlyCall(Phone phone) {
        Phone imsPhone = phone.getImsPhone();

        return imsPhone != null
                && (imsPhone.isVoiceOverCellularImsEnabled() || imsPhone.isWifiCallingEnabled())
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE);
    }

    private boolean isRadioOn() {
        boolean result = false;
        for (Phone phone : mPhoneFactoryProxy.getPhones()) {
            result |= phone.isRadioOn();
        }
        return result;
    }

    private boolean isSatelliteBlockingCall(boolean isEmergencyNumber) {
        if (isEmergencyNumber) {
            return mSatelliteController.isSatelliteEnabled();
        } else {
            return mSatelliteController.isDemoModeEnabled();
        }
    }

    private Pair<WeakReference<TelephonyConnection>, Queue<Phone>> makeCachedConnectionPhonePair(
            TelephonyConnection c) {
        Queue<Phone> phones = new LinkedList<>(Arrays.asList(mPhoneFactoryProxy.getPhones()));
        return new Pair<>(new WeakReference<>(c), phones);
    }

    // Update the mEmergencyRetryCache by removing the Phone used to call the last failed emergency
    // number and then moving it to the back of the queue if it is not a permanent failure cause
    // from the modem.
    private void updateCachedConnectionPhonePair(TelephonyConnection c, Phone phone,
            boolean isPermanentFailure) {
        // No cache exists, create a new one.
        if (mEmergencyRetryCache == null) {
            Log.i(this, "updateCachedConnectionPhonePair, cache is null. Generating new cache");
            mEmergencyRetryCache = makeCachedConnectionPhonePair(c);
        // Cache is stale, create a new one with the new TelephonyConnection.
        } else if (mEmergencyRetryCache.first.get() != c) {
            Log.i(this, "updateCachedConnectionPhonePair, cache is stale. Regenerating.");
            mEmergencyRetryCache = makeCachedConnectionPhonePair(c);
        }

        Queue<Phone> cachedPhones = mEmergencyRetryCache.second;
        // Need to refer default phone considering ImsPhone because
        // cachedPhones is a list that contains default phones.
        Phone phoneUsed = phone.getDefaultPhone();
        if (phoneUsed == null) {
            return;
        }
        // Remove phone used from the list, but for temporary fail cause, it will be added
        // back to list further in this method. However in case of permanent failure, the
        // phone shouldn't be reused, hence it will not be added back again.
        cachedPhones.remove(phoneUsed);
        Log.i(this, "updateCachedConnectionPhonePair, isPermanentFailure:" + isPermanentFailure);
        if (!isPermanentFailure) {
            // In case of temporary failure, add the phone back, this will result adding it
            // to tail of list mEmergencyRetryCache.second, giving other phone more
            // priority and that is what we want.
            cachedPhones.offer(phoneUsed);
        }
    }

    /**
     * Updates a cache containing all of the slots that are available for redial at any point.
     *
     * - If a Connection returns with the disconnect cause EMERGENCY_TEMP_FAILURE, keep that phone
     * in the cache, but move it to the lowest priority in the list. Then, place the emergency call
     * on the next phone in the list.
     * - If a Connection returns with the disconnect cause EMERGENCY_PERM_FAILURE, remove that phone
     * from the cache and pull another phone from the cache to place the emergency call.
     *
     * This will continue until there are no more slots to dial on.
     */
    @VisibleForTesting
    public void retryOutgoingOriginalConnection(TelephonyConnection c,
            Phone phone, boolean isPermanentFailure) {
        int phoneId = (phone == null) ? -1 : phone.getPhoneId();
        updateCachedConnectionPhonePair(c, phone, isPermanentFailure);
        // Pull next phone to use from the cache or null if it is empty
        Phone newPhoneToUse = (mEmergencyRetryCache.second != null)
                ? mEmergencyRetryCache.second.peek() : null;
        if (newPhoneToUse != null) {
            int videoState = c.getVideoState();
            Bundle connExtras = c.getExtras();
            Log.i(this, "retryOutgoingOriginalConnection, redialing on Phone Id: " + newPhoneToUse);
            c.clearOriginalConnection();
            if (phoneId != newPhoneToUse.getPhoneId()) {
                if (mTelephonyManagerProxy.getMaxNumberOfSimultaneouslyActiveSims() < 2) {
                    disconnectAllCallsOnOtherSubs(
                            mPhoneUtilsProxy.makePstnPhoneAccountHandle(newPhoneToUse));
                }
                updatePhoneAccount(c, newPhoneToUse);
            }
            if (mDomainSelectionResolver.isDomainSelectionSupported()) {
                onEmergencyRedial(c, newPhoneToUse);
                return;
            }
            placeOutgoingConnection(c, newPhoneToUse, videoState, connExtras);
        } else {
            // We have run out of Phones to use. Disconnect the call and destroy the connection.
            Log.i(this, "retryOutgoingOriginalConnection, no more Phones to use. Disconnecting.");
            closeOrDestroyConnection(c, new DisconnectCause(DisconnectCause.ERROR));
        }
    }

    private void updatePhoneAccount(TelephonyConnection connection, Phone phone) {
        PhoneAccountHandle pHandle = mPhoneUtilsProxy.makePstnPhoneAccountHandle(phone);
        // For ECall handling on MSIM, until the request reaches here (i.e PhoneApp), we don't know
        // on which phone account ECall can be placed. After deciding, we should notify Telecom of
        // the change so that the proper PhoneAccount can be displayed.
        Log.i(this, "updatePhoneAccount setPhoneAccountHandle, account = " + pHandle);
        connection.setPhoneAccountHandle(pHandle);
    }

    private void placeOutgoingConnection(
            TelephonyConnection connection, Phone phone, ConnectionRequest request) {
        placeOutgoingConnection(connection, phone, request.getVideoState(), request.getExtras());
    }

    private void placeOutgoingConnection(
            TelephonyConnection connection, Phone phone, int videoState, Bundle extras) {

        String number = (connection.getAddress() != null)
                ? connection.getAddress().getSchemeSpecificPart()
                : "";

        if (showDataDialog(phone, number)) {
            connection.setDisconnected(DisconnectCauseUtil.toTelecomDisconnectCause(
                        android.telephony.DisconnectCause.DIALED_MMI, "UT is not available"));
            return;
        }

        if (extras != null && extras.containsKey(TelecomManager.EXTRA_OUTGOING_PICTURE)) {
            ParcelUuid uuid = extras.getParcelable(TelecomManager.EXTRA_OUTGOING_PICTURE);
            CallComposerPictureManager.getInstance(phone.getContext(), phone.getSubId())
                    .storeUploadedPictureToCallLog(uuid.getUuid(), (uri) -> {
                        if (uri != null) {
                            try {
                                Bundle b = new Bundle();
                                b.putParcelable(TelecomManager.EXTRA_PICTURE_URI, uri);
                                connection.putTelephonyExtras(b);
                            } catch (Exception e) {
                                Log.e(this, e, "Couldn't set picture extra on outgoing call");
                            }
                        }
                    });
        }

        final com.android.internal.telephony.Connection originalConnection;
        try {
            if (phone != null) {
                boolean isEmergency = mTelephonyManagerProxy.isCurrentEmergencyNumber(number);
                Log.i(this, "placeOutgoingConnection isEmergency=" + isEmergency);
                if (isEmergency) {
                    handleEmergencyCallStartedForSatelliteSOSMessageRecommender(connection, phone);
                    if (!getAllConnections().isEmpty()) {
                        if (!shouldHoldForEmergencyCall(phone)) {
                            // If we do not support holding ongoing calls for an outgoing
                            // emergency call, disconnect the ongoing calls.
                            for (Connection c : getAllConnections()) {
                                if (!c.equals(connection)
                                        && c.getState() != Connection.STATE_DISCONNECTED
                                        && c instanceof TelephonyConnection) {
                                    ((TelephonyConnection) c).hangup(
                                            android.telephony.DisconnectCause
                                                    .OUTGOING_EMERGENCY_CALL_PLACED);
                                }
                            }
                            for (Conference c : getAllConferences()) {
                                if (c.getState() != Connection.STATE_DISCONNECTED
                                        && c instanceof Conference) {
                                    ((Conference) c).onDisconnect();
                                }
                            }
                        } else if (!isVideoCallHoldAllowed(phone)) {
                            // If we do not support holding ongoing video call for an outgoing
                            // emergency call, disconnect the ongoing video call.
                            for (Connection c : getAllConnections()) {
                                if (!c.equals(connection)
                                        && c.getState() == Connection.STATE_ACTIVE
                                        && VideoProfile.isVideo(c.getVideoState())
                                        && c instanceof TelephonyConnection) {
                                    ((TelephonyConnection) c).hangup(
                                            android.telephony.DisconnectCause
                                                    .OUTGOING_EMERGENCY_CALL_PLACED);
                                    break;
                                }
                            }
                        }
                    }
                    if (mDomainSelectionResolver.isDomainSelectionSupported()) {
                        if (isNormalRouting(phone, number)
                                    && handleOutgoingCallConnection(number, connection,
                                            phone, videoState)) {
                            /** Normal routing emergency number shall be handled
                             * by normal call domain selctor.*/
                            Log.i(this, "placeOutgoingConnection normal routing number");
                            return;
                        }
                    }
                } else if (handleOutgoingCallConnection(number, connection,
                        phone, videoState)) {
                    return;
                }
                originalConnection = phone.dial(number, new ImsPhone.ImsDialArgs.Builder()
                                .setVideoState(videoState)
                                .setIntentExtras(extras)
                                .setRttTextStream(connection.getRttTextStream())
                                .build(),
                        // We need to wait until the phone has been chosen in GsmCdmaPhone to
                        // register for the associated TelephonyConnection call event listeners.
                        connection::registerForCallEvents);
            } else {
                originalConnection = null;
            }
        } catch (CallStateException e) {
            Log.e(this, e, "placeOutgoingConnection, phone.dial exception: " + e);
            if (mDomainSelectionResolver.isDomainSelectionSupported()) {
                // Notify EmergencyStateTracker and DomainSelector of the cancellation by exception
                onLocalHangup(connection);
            }
            connection.unregisterForCallEvents();
            handleCallStateException(e, connection, phone);
            return;
        }
        if (originalConnection == null) {
            Log.d(this, "placeOutgoingConnection, phone.dial returned null");

            // On GSM phones, null connection means that we dialed an MMI code
            int telephonyDisconnectCause = handleMmiCode(
                    phone, android.telephony.DisconnectCause.OUTGOING_FAILURE);
            connection.setTelephonyConnectionDisconnected(
                    mDisconnectCauseFactory.toTelecomDisconnectCause(telephonyDisconnectCause,
                            "Connection is null", phone.getPhoneId()));
            connection.close();
        } else {
            if (!getMainThreadHandler().getLooper().isCurrentThread()) {
                Log.w(this, "placeOriginalConnection - Unexpected, this call "
                        + "should always be on the main thread.");
                getMainThreadHandler().post(() -> {
                    if (connection.getOriginalConnection() == null) {
                        connection.setOriginalConnection(originalConnection);
                    }
                });
            } else {
                connection.setOriginalConnection(originalConnection);
            }
        }
    }

    private int handleMmiCode(Phone phone, int telephonyDisconnectCause) {
        int disconnectCause = telephonyDisconnectCause;
        if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM
                || phone.isUtEnabled()) {
            Log.d(this, "dialed MMI code");
            int subId = phone.getSubId();
            Log.d(this, "subId: " + subId);
            disconnectCause = android.telephony.DisconnectCause.DIALED_MMI;
            final Intent intent = new Intent(this, MMIDialogActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            if (SubscriptionManager.isValidSubscriptionId(subId)) {
                SubscriptionManager.putSubscriptionIdExtra(intent, subId);
            }
            startActivity(intent);
        }
        return disconnectCause;
    }

    private void handleOutgoingCallConnectionByCallDomainSelection(
            int domain, Phone phone, String number, int videoState) {
        Log.d(this, "Call Domain Selected : " + domain);
        try {
            Bundle extras = mNormalCallConnection.getExtras();
            if (extras == null) {
                extras = new Bundle();
            }
            extras.putInt(PhoneConstants.EXTRA_DIAL_DOMAIN, domain);

            if (phone != null) {
                Log.v(LOG_TAG, "Call dialing. Domain: " + domain);
                com.android.internal.telephony.Connection connection =
                        phone.dial(number, new ImsPhone.ImsDialArgs.Builder()
                                        .setVideoState(videoState)
                                        .setIntentExtras(extras)
                                        .setRttTextStream(mNormalCallConnection.getRttTextStream())
                                        .setIsWpsCall(PhoneNumberUtils.isWpsCallNumber(number))
                                        .build(),
                                mNormalCallConnection::registerForCallEvents);

                if (connection == null) {
                    Log.d(this, "placeOutgoingConnection, phone.dial returned null");

                    // On GSM phones, null connection means that we dialed an MMI code
                    int telephonyDisconnectCause = handleMmiCode(
                            phone, android.telephony.DisconnectCause.OUTGOING_FAILURE);
                    if (mNormalCallConnection.getState() != Connection.STATE_DISCONNECTED) {
                        mNormalCallConnection.setTelephonyConnectionDisconnected(
                                mDisconnectCauseFactory.toTelecomDisconnectCause(
                                        telephonyDisconnectCause,
                                        "Connection is null",
                                        phone.getPhoneId()));
                        mNormalCallConnection.close();
                    }
                    clearNormalCallDomainSelectionConnection();
                    return;
                }

                mNormalCallConnection.setOriginalConnection(connection);
                mNormalCallConnection.addTelephonyConnectionListener(mNormalCallConnectionListener);
                return;
            } else {
                Log.w(this, "placeOutgoingCallConnection. Dialing failed. Phone is null");
                mNormalCallConnection.setTelephonyConnectionDisconnected(
                        mDisconnectCauseFactory.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.OUTGOING_FAILURE,
                                "Phone is null", phone.getPhoneId()));
                mNormalCallConnection.close();
            }
        } catch (CallStateException e) {
            Log.e(this, e, "Call placeOutgoingCallConnection, phone.dial exception: " + e);
            mNormalCallConnection.unregisterForCallEvents();
            handleCallStateException(e, mNormalCallConnection, phone);
        } catch (Exception e) {
            Log.e(this, e, "Call exception in placeOutgoingCallConnection:" + e);
            mNormalCallConnection.unregisterForCallEvents();
            mNormalCallConnection.setTelephonyConnectionDisconnected(DisconnectCauseUtil
                    .toTelecomDisconnectCause(android.telephony.DisconnectCause.OUTGOING_FAILURE,
                            e.getMessage(), phone.getPhoneId()));
            mNormalCallConnection.close();
        }
        clearNormalCallDomainSelectionConnection();
        mNormalCallConnection = null;
    }

    private boolean handleOutgoingCallConnection(
            String number, TelephonyConnection connection, Phone phone, int videoState) {

        if (!mDomainSelectionResolver.isDomainSelectionSupported()) {
            return false;
        }

        if (phone == null) {
            return false;
        }

        String dialPart = PhoneNumberUtils.extractNetworkPortionAlt(
                PhoneNumberUtils.stripSeparators(number));
        boolean isMmiCode = (dialPart.startsWith("*") || dialPart.startsWith("#"))
                && dialPart.endsWith("#");
        boolean isSuppServiceCode = ImsPhoneMmiCode.isSuppServiceCodes(dialPart, phone);
        boolean isPotentialUssdCode = isMmiCode && !isSuppServiceCode;

        // If the number is both an MMI code and a supplementary service code,
        // it shall be treated as UT. In this case, domain selection is not performed.
        if (isMmiCode && isSuppServiceCode) {
            Log.v(LOG_TAG, "UT code not handled by call domain selection.");
            return false;
        }

        /* For USSD codes, connection is closed and MMIDialogActivity is started.
           To avoid connection close and return false. isPotentialUssdCode is handled after
            all condition checks. */

        // Check and select same domain as ongoing call on the same subscription (if exists)
        int activeCallDomain = getActiveCallDomain(phone.getSubId());
        if (activeCallDomain != NetworkRegistrationInfo.DOMAIN_UNKNOWN
                && !PhoneNumberUtils.isWpsCallNumber(number)) {
            Log.d(LOG_TAG, "Selecting same domain as ongoing call on same subId");
            mNormalCallConnection = connection;
            handleOutgoingCallConnectionByCallDomainSelection(
                    activeCallDomain, phone, number, videoState);
            return true;
        }

        mDomainSelectionConnection = mDomainSelectionResolver
                .getDomainSelectionConnection(phone, SELECTOR_TYPE_CALLING, false);
        if (mDomainSelectionConnection == null) {
            return false;
        }
        Log.d(LOG_TAG, "Call Connection created");
        SelectionAttributes selectionAttributes =
                new SelectionAttributes.Builder(phone.getPhoneId(), phone.getSubId(),
                        SELECTOR_TYPE_CALLING)
                        .setAddress(Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null))
                        .setEmergency(false)
                        .setVideoCall(VideoProfile.isVideo(videoState))
                        .build();

        NormalCallDomainSelectionConnection normalCallDomainSelectionConnection =
                (NormalCallDomainSelectionConnection) mDomainSelectionConnection;
        CompletableFuture<Integer> future = normalCallDomainSelectionConnection
                .createNormalConnection(selectionAttributes,
                        mCallDomainSelectionConnectionCallback);
        Log.d(LOG_TAG, "Call Domain selection triggered.");

        mNormalCallConnection = connection;
        future.thenAcceptAsync((domain) -> handleOutgoingCallConnectionByCallDomainSelection(
                domain, phone, number, videoState), mDomainSelectionMainExecutor);

        if (isPotentialUssdCode) {
            Log.v(LOG_TAG, "PotentialUssdCode. Closing connection with DisconnectCause.DIALED_MMI");
            connection.setTelephonyConnectionDisconnected(
                    mDisconnectCauseFactory.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.DIALED_MMI,
                            "Dialing USSD", phone.getPhoneId()));
            connection.close();
        }
        return true;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private Connection placeEmergencyConnection(
            final Phone phone, final ConnectionRequest request,
            final String numberToDial, final boolean isTestEmergencyNumber,
            final Uri handle, final boolean needToTurnOnRadio) {

        final Connection resultConnection =
                getTelephonyConnection(request, numberToDial, true, handle, phone);

        if (resultConnection instanceof TelephonyConnection) {
            Log.i(this, "placeEmergencyConnection");

            mIsEmergencyCallPending = true;
            mEmergencyConnection = (TelephonyConnection) resultConnection;
        }

        CompletableFuture<Void> maybeHoldFuture =
                checkAndHoldCallsOnOtherSubsForEmergencyCall(request, resultConnection, phone);
        maybeHoldFuture.thenRun(() -> placeEmergencyConnectionInternal(resultConnection,
                phone, request, numberToDial, isTestEmergencyNumber, needToTurnOnRadio));

        // Non TelephonyConnection type instance means dialing failure.
        return resultConnection;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private void placeEmergencyConnectionInternal(final Connection resultConnection,
            final Phone phone, final ConnectionRequest request,
            final String numberToDial, final boolean isTestEmergencyNumber,
            final boolean needToTurnOnRadio) {

        if (mEmergencyConnection == null) {
            Log.i(this, "placeEmergencyConnectionInternal dialing canceled");
            return;
        }

        if (resultConnection instanceof TelephonyConnection) {
            Log.i(this, "placeEmergencyConnectionInternal");

            ((TelephonyConnection) resultConnection).addTelephonyConnectionListener(
                    mEmergencyConnectionListener);

            if (mEmergencyStateTracker == null) {
                mEmergencyStateTracker = EmergencyStateTracker.getInstance();
            }

            CompletableFuture<Integer> future = mEmergencyStateTracker.startEmergencyCall(
                    phone, resultConnection, isTestEmergencyNumber);
            future.thenAccept((result) -> {
                Log.d(this, "startEmergencyCall-complete result=" + result);
                if (mEmergencyConnection == null) {
                    Log.i(this, "startEmergencyCall-complete dialing canceled");
                    return;
                }
                if (result == android.telephony.DisconnectCause.NOT_DISCONNECTED) {
                    createEmergencyConnection(phone, (TelephonyConnection) resultConnection,
                            numberToDial, isTestEmergencyNumber, request, needToTurnOnRadio,
                            mEmergencyStateTracker.getEmergencyRegistrationResult());
                } else {
                    mEmergencyConnection = null;
                    String reason = "Couldn't setup emergency call";
                    if (result == android.telephony.DisconnectCause.POWER_OFF) {
                        reason = "Failed to turn on radio.";
                    }
                    ((TelephonyConnection) resultConnection).setTelephonyConnectionDisconnected(
                            mDisconnectCauseFactory.toTelecomDisconnectCause(result, reason));
                    ((TelephonyConnection) resultConnection).close();
                    mIsEmergencyCallPending = false;
                }
            });
        }
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private void createEmergencyConnection(final Phone phone,
            final TelephonyConnection resultConnection, final String number,
            final boolean isTestEmergencyNumber,
            final ConnectionRequest request, boolean needToTurnOnRadio,
            final EmergencyRegistrationResult regResult) {
        Log.i(this, "createEmergencyConnection");

        if (phone.getImsPhone() == null) {
            // Dialing emergency calls over IMS is not available without ImsPhone instance.
            Log.w(this, "createEmergencyConnection no ImsPhone");
            dialCsEmergencyCall(phone, resultConnection, request);
            return;
        }

        ImsManager imsManager = mImsManager;
        if (imsManager == null) {
            // mImsManager is not null only while unit test.
            imsManager = ImsManager.getInstance(phone.getContext(), phone.getPhoneId());
        }
        if (!imsManager.isNonTtyOrTtyOnVolteEnabled()) {
            Log.w(this, "createEmergencyConnection - TTY on VoLTE is not supported.");
            dialCsEmergencyCall(phone, resultConnection, request);
            return;
        }

        DomainSelectionConnection selectConnection =
                mDomainSelectionResolver.getDomainSelectionConnection(
                        phone, SELECTOR_TYPE_CALLING, true);

        if (selectConnection == null) {
            // While the domain selection service is enabled, the valid
            // {@link DomainSelectionConnection} is not available.
            // This can happen when the domain selection service is not available.
            Log.w(this, "createEmergencyConnection - no selectionConnection");
            dialCsEmergencyCall(phone, resultConnection, request);
            return;
        }

        mEmergencyCallDomainSelectionConnection =
                (EmergencyCallDomainSelectionConnection) selectConnection;

        DomainSelectionService.SelectionAttributes attr =
                EmergencyCallDomainSelectionConnection.getSelectionAttributes(
                        phone.getPhoneId(), phone.getSubId(), needToTurnOnRadio,
                        request.getTelecomCallId(), number, isTestEmergencyNumber,
                        0, null, regResult);

        CompletableFuture<Integer> future =
                mEmergencyCallDomainSelectionConnection.createEmergencyConnection(
                        attr, mEmergencyDomainSelectionConnectionCallback);
        future.thenAcceptAsync((result) -> {
            Log.d(this, "createEmergencyConnection-complete result=" + result);
            if (mEmergencyConnection == null) {
                Log.i(this, "createEmergencyConnection-complete dialing canceled");
                return;
            }
            Bundle extras = request.getExtras();
            extras.putInt(PhoneConstants.EXTRA_DIAL_DOMAIN, result);
            CompletableFuture<Void> rejectFuture = checkAndRejectIncomingCall(phone, (ret) -> {
                if (!ret) {
                    Log.i(this, "createEmergencyConnection reject incoming call failed");
                }
            });
            rejectFuture.thenRun(() -> placeEmergencyConnectionOnSelectedDomain(request,
                    resultConnection, phone));
        }, mDomainSelectionMainExecutor);
    }

    private void dialCsEmergencyCall(final Phone phone,
            final TelephonyConnection resultConnection, final ConnectionRequest request) {
        Log.d(this, "dialCsEmergencyCall");
        Bundle extras = request.getExtras();
        extras.putInt(PhoneConstants.EXTRA_DIAL_DOMAIN, NetworkRegistrationInfo.DOMAIN_CS);
        mDomainSelectionMainExecutor.execute(
                () -> {
                    if (mEmergencyConnection == null) {
                        Log.i(this, "dialCsEmergencyCall dialing canceled");
                        return;
                    }
                    CompletableFuture<Void> future = checkAndRejectIncomingCall(phone, (ret) -> {
                        if (!ret) {
                            Log.i(this, "dialCsEmergencyCall reject incoming call failed");
                        }
                    });
                    future.thenRun(() -> placeEmergencyConnectionOnSelectedDomain(request,
                            resultConnection, phone));
                });
    }

    private void placeEmergencyConnectionOnSelectedDomain(ConnectionRequest request,
            TelephonyConnection resultConnection, Phone phone) {
        if (mEmergencyConnection == null) {
            Log.i(this, "placeEmergencyConnectionOnSelectedDomain dialing canceled");
            return;
        }
        placeOutgoingConnection(request, resultConnection, phone);
        mIsEmergencyCallPending = false;
    }

    private void releaseEmergencyCallDomainSelection(boolean cancel, boolean isActive) {
        if (mEmergencyCallDomainSelectionConnection != null) {
            if (cancel) mEmergencyCallDomainSelectionConnection.cancelSelection();
            else mEmergencyCallDomainSelectionConnection.finishSelection();
            mEmergencyCallDomainSelectionConnection = null;
        }
        mIsEmergencyCallPending = false;
        if (!isActive) {
            mEmergencyConnection = null;
        }
    }

    /**
     * Determine whether reselection of domain is required or not.
     * @param c the {@link Connection} instance.
     * {@link com.android.internal.telephony.CallFailCause}.
     * @param reasonInfo the reason why PS call is disconnected.
     * @param showPreciseCause Indicates whether this connection supports showing precise
     *                         call failed cause.
     * @param overrideCause Provides a DisconnectCause associated with a hang up request.
     * @return {@code true} if reselection of domain is required.
     */
    public boolean maybeReselectDomain(final TelephonyConnection c, ImsReasonInfo reasonInfo,
                                       boolean showPreciseCause, int overrideCause) {
        if (!mDomainSelectionResolver.isDomainSelectionSupported()) return false;

        int callFailCause = c.getOriginalConnection().getPreciseDisconnectCause();

        Log.i(this, "maybeReselectDomain csCause=" +  callFailCause + ", psCause=" + reasonInfo);
        if (mEmergencyConnection == c) {
            if (mEmergencyCallDomainSelectionConnection != null) {
                return maybeReselectDomainForEmergencyCall(c, callFailCause, reasonInfo);
            }
            Log.i(this, "maybeReselectDomain endCall()");
            c.removeTelephonyConnectionListener(mEmergencyConnectionListener);
            releaseEmergencyCallDomainSelection(false, false);
            mEmergencyStateTracker.endCall(c);
            return false;
        }

        if (reasonInfo != null) {
            int reasonCode = reasonInfo.getCode();
            int extraCode = reasonInfo.getExtraCode();
            if ((reasonCode == ImsReasonInfo.CODE_SIP_ALTERNATE_EMERGENCY_CALL)
                    || (reasonCode == ImsReasonInfo.CODE_LOCAL_CALL_CS_RETRY_REQUIRED
                            && extraCode == ImsReasonInfo.EXTRA_CODE_CALL_RETRY_EMERGENCY)) {
                // clear normal call domain selector
                c.removeTelephonyConnectionListener(mNormalCallConnectionListener);
                clearNormalCallDomainSelectionConnection();
                mNormalCallConnection = null;

                onEmergencyRedial(c, c.getPhone().getDefaultPhone());
                return true;
            }
        }

        return maybeReselectDomainForNormalCall(c, reasonInfo, showPreciseCause, overrideCause);
    }

    private boolean maybeReselectDomainForEmergencyCall(final TelephonyConnection c,
            int callFailCause, ImsReasonInfo reasonInfo) {
        Log.i(this, "maybeReselectDomainForEmergencyCall "
                + "csCause=" +  callFailCause + ", psCause=" + reasonInfo);

        if (c.getOriginalConnection() != null
                && c.getOriginalConnection().getDisconnectCause()
                        != android.telephony.DisconnectCause.LOCAL
                && c.getOriginalConnection().getDisconnectCause()
                        != android.telephony.DisconnectCause.POWER_OFF) {

            DomainSelectionService.SelectionAttributes attr =
                    EmergencyCallDomainSelectionConnection.getSelectionAttributes(
                            c.getPhone().getPhoneId(), c.getPhone().getSubId(), false,
                            c.getTelecomCallId(), c.getAddress().getSchemeSpecificPart(),
                            false, callFailCause, reasonInfo, null);

            CompletableFuture<Integer> future =
                    mEmergencyCallDomainSelectionConnection.reselectDomain(attr);
            // TeleponyConnection will clear original connection. Keep the reference to Phone.
            final Phone phone = c.getPhone().getDefaultPhone();
            if (future != null) {
                future.thenAcceptAsync((result) -> {
                    Log.d(this, "reselectDomain-complete");
                    if (mEmergencyConnection == null) {
                        Log.i(this, "reselectDomain-complete dialing canceled");
                        return;
                    }
                    onEmergencyRedialOnDomain(c, phone, result);
                }, mDomainSelectionMainExecutor);
                return true;
            }
        }

        Log.i(this, "maybeReselectDomainForEmergencyCall endCall()");
        c.removeTelephonyConnectionListener(mEmergencyConnectionListener);
        releaseEmergencyCallDomainSelection(true, false);
        mEmergencyStateTracker.endCall(c);
        return false;
    }

    private boolean isEmergencyNumberAllowedOnDialedSim(Phone phone, String number) {
        CarrierConfigManager cfgManager = (CarrierConfigManager)
                phone.getContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (cfgManager != null) {
            PersistableBundle b = cfgManager.getConfigForSubId(phone.getSubId(),
                    KEY_USE_ONLY_DIALED_SIM_ECC_LIST_BOOL);
            if (b == null) {
                b = CarrierConfigManager.getDefaultConfig();
            }
            // We need to check only when KEY_USE_ONLY_DIALED_SIM_ECC_LIST_BOOL is true.
            if (b.getBoolean(KEY_USE_ONLY_DIALED_SIM_ECC_LIST_BOOL, false)
                      && (phone.getEmergencyNumberTracker() != null)) {
                if (!phone.getEmergencyNumberTracker().isEmergencyNumber(number)) {
                    Log.i(this, "isEmergencyNumberAllowedOnDialedSim false");
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isNormalRouting(Phone phone, String number) {
        // Check isEmergencyNumberAllowedOnDialedSim(): some carriers do not want to handle
        // dial requests for numbers which are in the emergency number list on another SIM,
        // but not on their own. Such numbers shall be handled by normal call domain selector.
        return (isNormalRoutingNumber(phone, number)
                || !isEmergencyNumberAllowedOnDialedSim(phone, number));
    }

    private boolean isNormalRoutingNumber(Phone phone, String number) {
        if (phone.getEmergencyNumberTracker() != null) {
            // Note: There can potentially be multiple instances of EmergencyNumber found; if any of
            // them have normal routing, then use normal routing.
            List<EmergencyNumber> nums = phone.getEmergencyNumberTracker().getEmergencyNumbers(
                    number);
            return nums.stream().anyMatch(n ->
                    n.getEmergencyCallRouting() == EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);
        }
        return false;
    }

    /**
     * Determines the phone to use for a normal routed emergency call.
     * @param number The emergency number.
     * @return The {@link Phone} to place the normal routed emergency call on, or {@code null} if
     * none was found.
     */
    @VisibleForTesting
    public Phone getPhoneForNormalRoutedEmergencyCall(String number) {
        return Stream.of(mPhoneFactoryProxy.getPhones())
                .filter(p -> p.shouldPreferInServiceSimForNormalRoutedEmergencyCall()
                        && isNormalRoutingNumber(p, number)
                        && isAvailableForEmergencyCalls(p,
                                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL))
                .findFirst().orElse(null);
    }

    private boolean isVoiceInService(Phone phone, boolean imsVoiceCapable) {
        // Dialing normal call is available.
        if (phone.isWifiCallingEnabled()) {
            Log.i(this, "isVoiceInService VoWi-Fi available");
            return true;
        }

        ServiceState ss = phone.getServiceStateTracker().getServiceState();
        if (ss.getState() != ServiceState.STATE_IN_SERVICE) return false;

        NetworkRegistrationInfo regState = ss.getNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        if (regState != null) {
            int registrationState = regState.getRegistrationState();
            if (registrationState != NetworkRegistrationInfo.REGISTRATION_STATE_HOME
                    && registrationState != NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING) {
                return true;
            }

            int networkType = regState.getAccessNetworkTechnology();
            if (networkType == TelephonyManager.NETWORK_TYPE_LTE) {
                DataSpecificRegistrationInfo regInfo = regState.getDataSpecificInfo();
                if (regInfo.getLteAttachResultType()
                        == DataSpecificRegistrationInfo.LTE_ATTACH_TYPE_COMBINED) {
                    Log.i(this, "isVoiceInService combined attach");
                    return true;
                }
            }

            if (networkType == TelephonyManager.NETWORK_TYPE_NR
                    || networkType == TelephonyManager.NETWORK_TYPE_LTE) {
                Log.i(this, "isVoiceInService PS only network, IMS available " + imsVoiceCapable);
                return imsVoiceCapable;
            }
        }
        return true;
    }

    private boolean maybeReselectDomainForNormalCall(
            final TelephonyConnection c, ImsReasonInfo reasonInfo,
            boolean showPreciseCause, int overrideCause) {

        Log.i(LOG_TAG, "maybeReselectDomainForNormalCall");

        com.android.internal.telephony.Connection originalConn = c.getOriginalConnection();
        if (mDomainSelectionConnection != null && originalConn != null) {
            Phone phone = c.getPhone().getDefaultPhone();
            final String number = c.getAddress().getSchemeSpecificPart();
            int videoState = originalConn.getVideoState();

            SelectionAttributes selectionAttributes = NormalCallDomainSelectionConnection
                    .getSelectionAttributes(phone.getPhoneId(), phone.getSubId(),
                            c.getTelecomCallId(), number, VideoProfile.isVideo(videoState),
                            originalConn.getPreciseDisconnectCause(), reasonInfo);

            CompletableFuture<Integer> future = mDomainSelectionConnection
                    .reselectDomain(selectionAttributes);
            if (future != null) {
                int preciseDisconnectCause = CallFailCause.NOT_VALID;
                if (showPreciseCause) {
                    preciseDisconnectCause = originalConn.getPreciseDisconnectCause();
                }

                int disconnectCause = originalConn.getDisconnectCause();
                if ((overrideCause != android.telephony.DisconnectCause.NOT_VALID)
                        && (overrideCause != disconnectCause)) {
                    Log.i(LOG_TAG, "setDisconnected: override cause: " + disconnectCause
                            + " -> " + overrideCause);
                    disconnectCause = overrideCause;
                }

                ((NormalCallDomainSelectionConnection) mDomainSelectionConnection)
                        .setDisconnectCause(disconnectCause, preciseDisconnectCause,
                                originalConn.getVendorDisconnectCause());

                Log.d(LOG_TAG, "Reselecting the domain for call");
                mNormalCallConnection = c;

                future.thenAcceptAsync((result) -> {
                    onNormalCallRedial(c, phone, result, videoState);
                }, mDomainSelectionMainExecutor);
                return true;
            }
        }

        c.removeTelephonyConnectionListener(mTelephonyConnectionListener);
        clearNormalCallDomainSelectionConnection();
        mNormalCallConnection = null;
        Log.d(LOG_TAG, "Reselect call domain not triggered.");
        return false;
    }

    private void onEmergencyRedialOnDomain(final TelephonyConnection connection,
            final Phone phone, @NetworkRegistrationInfo.Domain int domain) {
        Log.i(this, "onEmergencyRedialOnDomain phoneId=" + phone.getPhoneId()
                + ", domain=" + DomainSelectionService.getDomainName(domain));

        final Bundle extras = new Bundle();
        extras.putInt(PhoneConstants.EXTRA_DIAL_DOMAIN, domain);

        CompletableFuture<Void> future = checkAndRejectIncomingCall(phone, (ret) -> {
            if (!ret) {
                Log.i(this, "onEmergencyRedialOnDomain reject incoming call failed");
            }
        });
        future.thenRun(() -> onEmergencyRedialOnDomainInternal(connection, phone, extras));
    }

    private void onEmergencyRedialOnDomainInternal(TelephonyConnection connection,
            Phone phone, Bundle extras) {
        if (mEmergencyConnection == null) {
            Log.i(this, "onEmergencyRedialOnDomainInternal dialing canceled");
            return;
        }

        String number = connection.getAddress().getSchemeSpecificPart();

        // Indicates undetectable emergency number with DialArgs
        boolean isEmergency = false;
        int eccCategory = EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED;
        if (connection.getEmergencyServiceCategory() != null) {
            isEmergency = true;
            eccCategory = connection.getEmergencyServiceCategory();
            Log.i(this, "onEmergencyRedialOnDomainInternal eccCategory=" + eccCategory);
        }

        com.android.internal.telephony.Connection originalConnection =
                connection.getOriginalConnection();
        try {
            if (phone != null) {
                originalConnection = phone.dial(number, new ImsPhone.ImsDialArgs.Builder()
                        .setVideoState(VideoProfile.STATE_AUDIO_ONLY)
                        .setIntentExtras(extras)
                        .setRttTextStream(connection.getRttTextStream())
                        .setIsEmergency(isEmergency)
                        .setEccCategory(eccCategory)
                        .build(),
                        connection::registerForCallEvents);
            }
        } catch (CallStateException e) {
            Log.e(this, e, "onEmergencyRedialOnDomainInternal, exception: " + e);
            onLocalHangup(connection);
            connection.unregisterForCallEvents();
            handleCallStateException(e, connection, phone);
            return;
        }
        if (originalConnection == null) {
            Log.d(this, "onEmergencyRedialOnDomainInternal, phone.dial returned null");
            onLocalHangup(connection);
            connection.setTelephonyConnectionDisconnected(
                    mDisconnectCauseFactory.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.ERROR_UNSPECIFIED,
                                "unknown error"));
            connection.close();
        } else {
            connection.setOriginalConnection(originalConnection);
        }
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private void onEmergencyRedial(final TelephonyConnection c, final Phone phone) {
        Log.i(this, "onEmergencyRedial phoneId=" + phone.getPhoneId());

        final String number = c.getAddress().getSchemeSpecificPart();
        final boolean isTestEmergencyNumber = isEmergencyNumberTestNumber(number);

        mIsEmergencyCallPending = true;
        c.addTelephonyConnectionListener(mEmergencyConnectionListener);
        handleEmergencyCallStartedForSatelliteSOSMessageRecommender(c, phone);

        if (mEmergencyStateTracker == null) {
            mEmergencyStateTracker = EmergencyStateTracker.getInstance();
        }

        mEmergencyConnection = c;
        CompletableFuture<Integer> future = mEmergencyStateTracker.startEmergencyCall(
                phone, c, isTestEmergencyNumber);
        future.thenAccept((result) -> {
            Log.d(this, "onEmergencyRedial-complete result=" + result);
            if (mEmergencyConnection == null) {
                Log.i(this, "onEmergencyRedial-complete dialing canceled");
                return;
            }
            if (result == android.telephony.DisconnectCause.NOT_DISCONNECTED) {
                DomainSelectionConnection selectConnection =
                        mDomainSelectionResolver.getDomainSelectionConnection(
                                phone, SELECTOR_TYPE_CALLING, true);

                if (selectConnection == null) {
                    Log.w(this, "onEmergencyRedial no selectionConnection, dial CS emergency call");
                    mIsEmergencyCallPending = false;
                    mDomainSelectionMainExecutor.execute(
                            () -> recreateEmergencyConnection(c, phone,
                                    NetworkRegistrationInfo.DOMAIN_CS));
                    return;
                }

                mEmergencyCallDomainSelectionConnection =
                        (EmergencyCallDomainSelectionConnection) selectConnection;

                DomainSelectionService.SelectionAttributes attr =
                        EmergencyCallDomainSelectionConnection.getSelectionAttributes(
                                phone.getPhoneId(),
                                phone.getSubId(), false,
                                c.getTelecomCallId(),
                                c.getAddress().getSchemeSpecificPart(), isTestEmergencyNumber,
                                0, null, mEmergencyStateTracker.getEmergencyRegistrationResult());

                CompletableFuture<Integer> domainFuture =
                        mEmergencyCallDomainSelectionConnection.createEmergencyConnection(
                                attr, mEmergencyDomainSelectionConnectionCallback);
                domainFuture.thenAcceptAsync((domain) -> {
                    Log.d(this, "onEmergencyRedial-createEmergencyConnection-complete domain="
                            + domain);
                    recreateEmergencyConnection(c, phone, domain);
                    mIsEmergencyCallPending = false;
                }, mDomainSelectionMainExecutor);
            } else {
                mEmergencyConnection = null;
                c.setTelephonyConnectionDisconnected(
                        mDisconnectCauseFactory.toTelecomDisconnectCause(result, "unknown error"));
                c.close();
                mIsEmergencyCallPending = false;
            }
        });
    }

    private void recreateEmergencyConnection(final TelephonyConnection connection,
            final Phone phone, final @NetworkRegistrationInfo.Domain int result) {
        Log.d(this, "recreateEmergencyConnection result=" + result);
        if (mEmergencyConnection == null) {
            Log.i(this, "recreateEmergencyConnection dialing canceled");
            return;
        }
        if (!getAllConnections().isEmpty()) {
            if (!shouldHoldForEmergencyCall(phone)) {
                // If we do not support holding ongoing calls for an outgoing
                // emergency call, disconnect the ongoing calls.
                for (Connection c : getAllConnections()) {
                    if (!c.equals(connection)
                            && c.getState() != Connection.STATE_DISCONNECTED
                            && c instanceof TelephonyConnection) {
                        ((TelephonyConnection) c).hangup(
                                android.telephony.DisconnectCause
                                        .OUTGOING_EMERGENCY_CALL_PLACED);
                    }
                }
                for (Conference c : getAllConferences()) {
                    if (c.getState() != Connection.STATE_DISCONNECTED) {
                        c.onDisconnect();
                    }
                }
            } else if (!isVideoCallHoldAllowed(phone)) {
                // If we do not support holding ongoing video call for an outgoing
                // emergency call, disconnect the ongoing video call.
                for (Connection c : getAllConnections()) {
                    if (!c.equals(connection)
                            && c.getState() == Connection.STATE_ACTIVE
                            && VideoProfile.isVideo(c.getVideoState())
                            && c instanceof TelephonyConnection) {
                        ((TelephonyConnection) c).hangup(
                                android.telephony.DisconnectCause
                                        .OUTGOING_EMERGENCY_CALL_PLACED);
                        break;
                    }
                }
            }
        }
        onEmergencyRedialOnDomain(connection, phone, result);
    }

    private void onNormalCallRedial(TelephonyConnection connection, Phone phone,
            @NetworkRegistrationInfo.Domain int domain, int videocallState) {

        Log.v(LOG_TAG, "Redialing the call in domain:"
                + DomainSelectionService.getDomainName(domain));

        String number = connection.getAddress().getSchemeSpecificPart();

        Bundle extras = new Bundle();
        extras.putInt(PhoneConstants.EXTRA_DIAL_DOMAIN, domain);
        com.android.internal.telephony.Connection originalConnection =
                connection.getOriginalConnection();
        if (originalConnection instanceof ImsPhoneConnection) {
            if (((ImsPhoneConnection) originalConnection).isRttEnabledForCall()) {
                extras.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_RTT, true);
            }
        }

        try {
            if (phone != null) {
                Log.d(LOG_TAG, "Redialing Call.");
                originalConnection = phone.dial(number, new ImsPhone.ImsDialArgs.Builder()
                                .setVideoState(videocallState)
                                .setIntentExtras(extras)
                                .setRttTextStream(connection.getRttTextStream())
                                .setIsEmergency(false)
                                .build(),
                        connection::registerForCallEvents);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, e, "Call redial exception: " + e);
        }
        if (originalConnection == null) {
            Log.e(LOG_TAG, new Exception("Phone is null"),
                    "Call redial failure due to phone.dial returned null");
            connection.setDisconnected(mDisconnectCauseFactory.toTelecomDisconnectCause(
                    android.telephony.DisconnectCause.OUTGOING_FAILURE, "connection is null"));
            connection.close();
        } else {
            connection.setOriginalConnection(originalConnection);
        }
    }

    protected void onLocalHangup(TelephonyConnection c) {
        if (mEmergencyConnection == c) {
            Log.i(this, "onLocalHangup " + c.getTelecomCallId());
            c.removeTelephonyConnectionListener(mEmergencyConnectionListener);
            releaseEmergencyCallDomainSelection(true, false);
            mEmergencyStateTracker.endCall(c);
        }
    }

    @VisibleForTesting
    public TelephonyConnection getEmergencyConnection() {
        return mEmergencyConnection;
    }

    @VisibleForTesting
    public void setEmergencyConnection(TelephonyConnection c) {
        mEmergencyConnection = c;
    }

    @VisibleForTesting
    public TelephonyConnection.TelephonyConnectionListener getEmergencyConnectionListener() {
        return mEmergencyConnectionListener;
    }

    @VisibleForTesting
    public TelephonyConnection.TelephonyConnectionListener
            getEmergencyConnectionSatelliteListener() {
        return mEmergencyConnectionSatelliteListener;
    }

    private boolean isVideoCallHoldAllowed(Phone phone) {
         CarrierConfigManager cfgManager = (CarrierConfigManager)
                phone.getContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (cfgManager == null) {
            // For some reason CarrierConfigManager is unavailable, return default
            Log.w(this, "isVideoCallHoldAllowed: couldn't get CarrierConfigManager");
            return true;
        }
        return cfgManager.getConfigForSubId(phone.getSubId()).getBoolean(
                CarrierConfigManager.KEY_ALLOW_HOLD_VIDEO_CALL_BOOL, true);
    }

    private boolean shouldHoldForEmergencyCall(Phone phone) {
        CarrierConfigManager cfgManager = (CarrierConfigManager)
                phone.getContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (cfgManager == null) {
            // For some reason CarrierConfigManager is unavailable, return default
            Log.w(this, "shouldHoldForEmergencyCall: couldn't get CarrierConfigManager");
            return true;
        }
        return cfgManager.getConfigForSubId(phone.getSubId()).getBoolean(
                CarrierConfigManager.KEY_ALLOW_HOLD_CALL_DURING_EMERGENCY_BOOL, true);
    }

    private void handleCallStateException(CallStateException e, TelephonyConnection connection,
            Phone phone) {
        int cause = android.telephony.DisconnectCause.OUTGOING_FAILURE;
        switch (e.getError()) {
            case CallStateException.ERROR_OUT_OF_SERVICE:
                cause = android.telephony.DisconnectCause.OUT_OF_SERVICE;
                break;
            case CallStateException.ERROR_POWER_OFF:
                 cause = android.telephony.DisconnectCause.POWER_OFF;
                 break;
            case CallStateException.ERROR_ALREADY_DIALING:
                 cause = android.telephony.DisconnectCause.ALREADY_DIALING;
                 break;
            case CallStateException.ERROR_CALL_RINGING:
                 cause = android.telephony.DisconnectCause.CANT_CALL_WHILE_RINGING;
                 break;
            case CallStateException.ERROR_CALLING_DISABLED:
                 cause = android.telephony.DisconnectCause.CALLING_DISABLED;
                 break;
            case CallStateException.ERROR_TOO_MANY_CALLS:
                 cause = android.telephony.DisconnectCause.TOO_MANY_ONGOING_CALLS;
                 break;
            case CallStateException.ERROR_OTASP_PROVISIONING_IN_PROCESS:
                 cause = android.telephony.DisconnectCause.OTASP_PROVISIONING_IN_PROCESS;
                 break;
            case CallStateException.ERROR_FDN_BLOCKED:
                 cause = android.telephony.DisconnectCause.FDN_BLOCKED;
                 break;
        }
        connection.setTelephonyConnectionDisconnected(
                DisconnectCauseUtil.toTelecomDisconnectCause(cause, e.getMessage(),
                        phone.getPhoneId()));
        connection.close();
    }

    private TelephonyConnection createConnectionFor(
            Phone phone,
            com.android.internal.telephony.Connection originalConnection,
            boolean isOutgoing,
            PhoneAccountHandle phoneAccountHandle,
            String telecomCallId) {
            return createConnectionFor(phone, originalConnection, isOutgoing, phoneAccountHandle,
                    telecomCallId, false);
    }

    private TelephonyConnection createConnectionFor(
            Phone phone,
            com.android.internal.telephony.Connection originalConnection,
            boolean isOutgoing,
            PhoneAccountHandle phoneAccountHandle,
            String telecomCallId,
            boolean isAdhocConference) {
        TelephonyConnection returnConnection = null;
        int phoneType = phone.getPhoneType();
        int callDirection = isOutgoing ? android.telecom.Call.Details.DIRECTION_OUTGOING
                : android.telecom.Call.Details.DIRECTION_INCOMING;
        if (phoneType == TelephonyManager.PHONE_TYPE_GSM) {
            returnConnection = new GsmConnection(originalConnection, telecomCallId, callDirection);
        } else if (phoneType == TelephonyManager.PHONE_TYPE_CDMA) {
            boolean allowsMute = allowsMute(phone);
            returnConnection = new CdmaConnection(originalConnection, mEmergencyTonePlayer,
                    allowsMute, callDirection, telecomCallId);
        }
        if (returnConnection != null) {
            if (!isAdhocConference) {
                // Listen to Telephony specific callbacks from the connection
                returnConnection.addTelephonyConnectionListener(mTelephonyConnectionListener);
            }
            returnConnection.setVideoPauseSupported(
                    TelecomAccountRegistry.getInstance(this).isVideoPauseSupported(
                            phoneAccountHandle));
            returnConnection.setManageImsConferenceCallSupported(
                    TelecomAccountRegistry.getInstance(this).isManageImsConferenceCallSupported(
                            phoneAccountHandle));
            returnConnection.setShowPreciseFailedCause(
                    TelecomAccountRegistry.getInstance(this).isShowPreciseFailedCause(
                            phoneAccountHandle));
            returnConnection.setTelephonyConnectionService(this);
        }
        return returnConnection;
    }

    private boolean isOriginalConnectionKnown(
            com.android.internal.telephony.Connection originalConnection) {
        return (getConnectionForOriginalConnection(originalConnection) != null);
    }

    private TelephonyConnection getConnectionForOriginalConnection(
            com.android.internal.telephony.Connection originalConnection) {
        for (Connection connection : getAllConnections()) {
            if (connection instanceof TelephonyConnection) {
                TelephonyConnection telephonyConnection = (TelephonyConnection) connection;
                if (telephonyConnection.getOriginalConnection() == originalConnection) {
                    return telephonyConnection;
                }
            }
        }
        return null;
    }

    /**
     * Determines which {@link Phone} will be used to place the call.
     * @param accountHandle The {@link PhoneAccountHandle} which was sent from Telecom to place the
     *      call on.
     * @param isEmergency {@code true} if this is an emergency call, {@code false} otherwise.
     * @param emergencyNumberAddress When {@code isEmergency} is {@code true}, will be the phone
     *      of the emergency call.  Otherwise, this can be {@code null}  .
     * @return
     */
    private Phone getPhoneForAccount(PhoneAccountHandle accountHandle, boolean isEmergency,
                                     @Nullable String emergencyNumberAddress) {
        Phone chosenPhone = null;
        int subId = mPhoneUtilsProxy.getSubIdForPhoneAccountHandle(accountHandle);
        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            int phoneId = mSubscriptionManagerProxy.getPhoneId(subId);
            chosenPhone = mPhoneFactoryProxy.getPhone(phoneId);
            Log.i(this, "getPhoneForAccount: handle=%s, subId=%s", accountHandle,
                    (chosenPhone == null ? "null" : chosenPhone.getSubId()));
        }

        // If this isn't an emergency call, just use the chosen phone (or null if none was found).
        if (!isEmergency) {
            return chosenPhone;
        }

        // Check if this call should be treated as a normal routed emergency call; we'll return null
        // if this is not a normal routed emergency call.
        Phone normalRoutingPhone = getPhoneForNormalRoutedEmergencyCall(emergencyNumberAddress);
        if (normalRoutingPhone != null) {
            Log.i(this, "getPhoneForAccount: normal routed emergency number,"
                            + "using phoneId=%d/subId=%d", normalRoutingPhone.getPhoneId(),
                    normalRoutingPhone.getSubId());
            return normalRoutingPhone;
        }

        // Default emergency call phone selection logic:
        // This is an emergency call and the phone we originally planned to make this call
        // with is not in service or was invalid, try to find one that is in service, using the
        // default as a last chance backup.
        if (chosenPhone == null || !isAvailableForEmergencyCalls(chosenPhone)) {
            Log.d(this, "getPhoneForAccount: phone for phone acct handle %s is out of service "
                    + "or invalid for emergency call.", accountHandle);
            chosenPhone = getPhoneForEmergencyCall(emergencyNumberAddress);
            Log.i(this, "getPhoneForAccount: emergency call - using subId: %s",
                    (chosenPhone == null ? "null" : chosenPhone.getSubId()));
        }
        return chosenPhone;
    }

    /**
     * If needed, block until the default data is switched for outgoing emergency call, or
     * timeout expires.
     * @param phone The Phone to switch the DDS on.
     * @param completeConsumer The consumer to call once the default data subscription has been
     *                         switched, provides {@code true} result if the switch happened
     *                         successfully or {@code false} if the operation timed out/failed.
     */
    private void delayDialForDdsSwitch(Phone phone, Consumer<Boolean> completeConsumer) {
        if (phone == null) {
            // Do not block indefinitely.
            completeConsumer.accept(false);
            return;
        }
        try {
            // Waiting for PhoneSwitcher to complete the operation.
            CompletableFuture<Boolean> future = possiblyOverrideDefaultDataForEmergencyCall(phone);
            // In the case that there is an issue or bug in PhoneSwitcher logic, do not wait
            // indefinitely for the future to complete. Instead, set a timeout that will complete
            // the future as to not block the outgoing call indefinitely.
            CompletableFuture<Boolean> timeout = new CompletableFuture<>();
            phone.getContext().getMainThreadHandler().postDelayed(
                    () -> timeout.complete(false), DEFAULT_DATA_SWITCH_TIMEOUT_MS);
            // Also ensure that the Consumer is completed on the main thread.
            future.acceptEitherAsync(timeout, completeConsumer,
                    phone.getContext().getMainExecutor());
        } catch (Exception e) {
            Log.w(this, "delayDialForDdsSwitch - exception= "
                    + e.getMessage());

        }
    }

    /**
     * If needed, block until Default Data subscription is switched for outgoing emergency call.
     *
     * In some cases, we need to try to switch the Default Data subscription before placing the
     * emergency call on DSDS devices. This includes the following situation:
     * - The modem does not support processing GNSS SUPL requests on the non-default data
     * subscription. For some carriers that do not provide a control plane fallback mechanism, the
     * SUPL request will be dropped and we will not be able to get the user's location for the
     * emergency call. In this case, we need to swap default data temporarily.
     * @param phone Evaluates whether or not the default data should be moved to the phone
     *              specified. Should not be null.
     */
    private CompletableFuture<Boolean> possiblyOverrideDefaultDataForEmergencyCall(
            @NonNull Phone phone) {
        int phoneCount = mTelephonyManagerProxy.getPhoneCount();
        // Do not override DDS if this is a single SIM device.
        if (phoneCount <= PhoneConstants.MAX_PHONE_COUNT_SINGLE_SIM) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        // Do not switch Default data if this device supports emergency SUPL on non-DDS.
        final boolean gnssSuplRequiresDefaultData =
                mDeviceState.isSuplDdsSwitchRequiredForEmergencyCall(this);
        if (!gnssSuplRequiresDefaultData) {
            Log.d(this, "possiblyOverrideDefaultDataForEmergencyCall: not switching DDS, does not "
                    + "require DDS switch.");
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        CarrierConfigManager cfgManager = (CarrierConfigManager)
                phone.getContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (cfgManager == null) {
            // For some reason CarrierConfigManager is unavailable. Do not block emergency call.
            Log.w(this, "possiblyOverrideDefaultDataForEmergencyCall: couldn't get"
                    + "CarrierConfigManager");
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        // Only override default data if we are IN_SERVICE already.
        if (!isAvailableForEmergencyCalls(phone)) {
            Log.d(this, "possiblyOverrideDefaultDataForEmergencyCall: not switching DDS");
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        // Only override default data if we are not roaming, we do not want to switch onto a network
        // that only supports data plane only (if we do not know).
        boolean isRoaming = phone.getServiceState().getVoiceRoaming();
        // In some roaming conditions, we know the roaming network doesn't support control plane
        // fallback even though the home operator does. For these operators we will need to do a DDS
        // switch anyway to make sure the SUPL request doesn't fail.
        boolean roamingNetworkSupportsControlPlaneFallback = true;
        String[] dataPlaneRoamPlmns = cfgManager.getConfigForSubId(phone.getSubId()).getStringArray(
                CarrierConfigManager.Gps.KEY_ES_SUPL_DATA_PLANE_ONLY_ROAMING_PLMN_STRING_ARRAY);
        if (dataPlaneRoamPlmns != null && Arrays.asList(dataPlaneRoamPlmns).contains(
                phone.getServiceState().getOperatorNumeric())) {
            roamingNetworkSupportsControlPlaneFallback = false;
        }
        if (isRoaming && roamingNetworkSupportsControlPlaneFallback) {
            Log.d(this, "possiblyOverrideDefaultDataForEmergencyCall: roaming network is assumed "
                    + "to support CP fallback, not switching DDS.");
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }
        // Do not try to swap default data if we support CS fallback or it is assumed that the
        // roaming network supports control plane fallback, we do not want to introduce
        // a lag in emergency call setup time if possible.
        final boolean supportsCpFallback = cfgManager.getConfigForSubId(phone.getSubId())
                .getInt(CarrierConfigManager.Gps.KEY_ES_SUPL_CONTROL_PLANE_SUPPORT_INT,
                        CarrierConfigManager.Gps.SUPL_EMERGENCY_MODE_TYPE_CP_ONLY)
                != CarrierConfigManager.Gps.SUPL_EMERGENCY_MODE_TYPE_DP_ONLY;
        if (supportsCpFallback && roamingNetworkSupportsControlPlaneFallback) {
            Log.d(this, "possiblyOverrideDefaultDataForEmergencyCall: not switching DDS, carrier "
                    + "supports CP fallback.");
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        // Get extension time, may be 0 for some carriers that support ECBM as well. Use
        // CarrierConfig default if format fails.
        int extensionTime = 0;
        try {
            extensionTime = Integer.parseInt(cfgManager.getConfigForSubId(phone.getSubId())
                    .getString(CarrierConfigManager.Gps.KEY_ES_EXTENSION_SEC_STRING, "0"));
        } catch (NumberFormatException e) {
            // Just use default.
        }
        CompletableFuture<Boolean> modemResultFuture = new CompletableFuture<>();
        try {
            Log.d(this, "possiblyOverrideDefaultDataForEmergencyCall: overriding DDS for "
                    + extensionTime + "seconds");
            mPhoneSwitcherProxy.getPhoneSwitcher().overrideDefaultDataForEmergency(
                    phone.getPhoneId(), extensionTime, modemResultFuture);
            // Catch all exceptions, we want to continue with emergency call if possible.
        } catch (Exception e) {
            Log.w(this, "possiblyOverrideDefaultDataForEmergencyCall: exception = "
                    + e.getMessage());
            modemResultFuture = CompletableFuture.completedFuture(Boolean.FALSE);
        }
        return modemResultFuture;
    }

    private void addTelephonyConnectionListener(Conferenceable c,
            TelephonyConnection.TelephonyConnectionListener listener) {
        if (c instanceof TelephonyConnection) {
            TelephonyConnection telephonyConnection = (TelephonyConnection) c;
            telephonyConnection.addTelephonyConnectionListener(listener);
        } else if (c instanceof ImsConference) {
            ImsConference imsConference = (ImsConference) c;
            TelephonyConnection conferenceHost =
                    (TelephonyConnection) imsConference.getConferenceHost();
            conferenceHost.addTelephonyConnectionListener(listener);
        } else {
            throw new IllegalArgumentException(
                    "addTelephonyConnectionListener(): Unexpected conferenceable! " + c);
        }
    }

    private CompletableFuture<Boolean> listenForHoldStateChanged(
            @NonNull Conferenceable conferenceable) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        final StateHoldingListener stateHoldingListener = new StateHoldingListener(future);
        addTelephonyConnectionListener(conferenceable, stateHoldingListener);
        return future;
    }

    // Returns a future that waits for the STATE_HOLDING confirmation on the input
    // {@link Conferenceable}, or times out.
    private CompletableFuture<Void> delayDialForOtherSubHold(Phone phone, Conferenceable c,
            Consumer<Boolean> completeConsumer) {
        if (c == null || phone == null) {
            // Unexpected inputs
            completeConsumer.accept(false);
            return CompletableFuture.completedFuture(null);
        }

        try {
            CompletableFuture<Boolean> stateHoldingFuture = listenForHoldStateChanged(c);
            // a timeout that will complete the future to not block the outgoing call indefinitely.
            CompletableFuture<Boolean> timeout = new CompletableFuture<>();
            phone.getContext().getMainThreadHandler().postDelayed(
                    () -> timeout.complete(false), DEFAULT_DSDA_OUTGOING_CALL_HOLD_TIMEOUT_MS);
            // Ensure that the Consumer is completed on the main thread.
            return stateHoldingFuture.acceptEitherAsync(timeout, completeConsumer,
                    phone.getContext().getMainExecutor());
        } catch (Exception e) {
            Log.w(this, "delayDialForOtherSubHold - exception= "
                    + e.getMessage());
            completeConsumer.accept(false);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * If needed, block until an incoming call is disconnected for outgoing emergency call,
     * or timeout expires.
     * @param phone The Phone to reject the incoming call
     * @param completeConsumer The consumer to call once rejecting incoming call has been
     *        completed. {@code true} result if the operation commpletes successfully, or
     *        {@code false} if the operation timed out/failed.
     */
    private CompletableFuture<Void> checkAndRejectIncomingCall(Phone phone,
            Consumer<Boolean> completeConsumer) {
        if (phone == null) {
            // Unexpected inputs
            Log.i(this, "checkAndRejectIncomingCall phone is null");
            completeConsumer.accept(false);
            return CompletableFuture.completedFuture(null);
        }

        Call ringingCall = phone.getRingingCall();
        if (ringingCall == null || !ringingCall.isRinging()) {
            completeConsumer.accept(true);
            return CompletableFuture.completedFuture(null);
        }
        Log.i(this, "checkAndRejectIncomingCall found a ringing call");

        try {
            ringingCall.hangup();
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            com.android.internal.telephony.Connection cn = ringingCall.getLatestConnection();
            cn.addListener(new OnDisconnectListener(future));
            // A timeout that will complete the future to not block the outgoing call indefinitely.
            CompletableFuture<Boolean> timeout = new CompletableFuture<>();
            phone.getContext().getMainThreadHandler().postDelayed(
                    () -> timeout.complete(false), DEFAULT_REJECT_INCOMING_CALL_TIMEOUT_MS);
            // Ensure that the Consumer is completed on the main thread.
            return future.acceptEitherAsync(timeout, completeConsumer,
                    phone.getContext().getMainExecutor()).exceptionally((ex) -> {
                        Log.w(this, "checkAndRejectIncomingCall - exceptionally= " + ex);
                        return null;
                    });
        } catch (Exception e) {
            Log.w(this, "checkAndRejectIncomingCall - exception= " + e.getMessage());
            completeConsumer.accept(false);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Get the Phone to use for an emergency call of the given emergency number address:
     *  a) If there are multiple Phones with the Subscriptions that support the emergency number
     *     address, and one of them is the default voice Phone, consider the default voice phone
     *     if 1.4 HAL is supported, or if it is available for emergency call.
     *  b) If there are multiple Phones with the Subscriptions that support the emergency number
     *     address, and none of them is the default voice Phone, use one of these Phones if 1.4 HAL
     *     is supported, or if it is available for emergency call.
     *  c) If there is no Phone that supports the emergency call for the address, use the defined
     *     Priority list to select the Phone via {@link #getFirstPhoneForEmergencyCall}.
     */
    public Phone getPhoneForEmergencyCall(String emergencyNumberAddress) {
        // Find the list of available Phones for the given emergency number address
        List<Phone> potentialEmergencyPhones = new ArrayList<>();
        int defaultVoicePhoneId = mSubscriptionManagerProxy.getDefaultVoicePhoneId();
        for (Phone phone : mPhoneFactoryProxy.getPhones()) {
            if (phone.getEmergencyNumberTracker() != null) {
                if (phone.getEmergencyNumberTracker().isEmergencyNumber(
                        emergencyNumberAddress)) {
                    if (isAvailableForEmergencyCalls(phone)) {
                        // a)
                        if (phone.getPhoneId() == defaultVoicePhoneId) {
                            Log.i(this, "getPhoneForEmergencyCall, Phone Id that supports"
                                    + " emergency number: " + phone.getPhoneId());
                            return phone;
                        }
                        potentialEmergencyPhones.add(phone);
                    }
                }
            }
        }
        // b)
        if (potentialEmergencyPhones.size() > 0) {
            Log.i(this, "getPhoneForEmergencyCall, Phone Id that supports emergency number:"
                    + potentialEmergencyPhones.get(0).getPhoneId());
            return getFirstPhoneForEmergencyCall(potentialEmergencyPhones);
        }
        // c)
        return getFirstPhoneForEmergencyCall();
    }

    @VisibleForTesting
    public Phone getFirstPhoneForEmergencyCall() {
        return getFirstPhoneForEmergencyCall(null);
    }

    /**
     * Retrieves the most sensible Phone to use for an emergency call using the following Priority
     *  list (for multi-SIM devices):
     *  1) The Phone that is in emergency SMS mode
     *  2) The phone based on User's SIM preference of Voice calling or Data in order
     *  3) The First Phone that is currently IN_SERVICE or is available for emergency calling
     *  4) Prioritize phones that have the dialed emergency number as part of their emergency
     *     number list
     *  5) If there is a PUK locked SIM, compare the SIMs that are not PUK locked. If all the SIMs
     *     are locked, skip to condition 6).
     *  6) The Phone with more Capabilities.
     *  7) The First Phone that has a SIM card in it (Starting from Slot 0...N)
     *  8) The Default Phone (Currently set as Slot 0)
     */
    @VisibleForTesting
    @NonNull
    public Phone getFirstPhoneForEmergencyCall(List<Phone> phonesWithEmergencyNumber) {
        int phoneCount = mTelephonyManagerProxy.getPhoneCount();
        for (int i = 0; i < phoneCount; i++) {
            Phone phone = mPhoneFactoryProxy.getPhone(i);
            // 1)
            if (phone != null && phone.isInEmergencySmsMode()) {
                if (isAvailableForEmergencyCalls(phone)) {
                    if (phonesWithEmergencyNumber == null
                            || phonesWithEmergencyNumber.contains(phone)) {
                        return phone;
                    }
                }
            }
        }

        // 2)
        int phoneId = mSubscriptionManagerProxy.getDefaultVoicePhoneId();
        if (phoneId == SubscriptionManager.INVALID_PHONE_INDEX) {
            phoneId = mSubscriptionManagerProxy.getDefaultDataPhoneId();
        }
        if (phoneId != SubscriptionManager.INVALID_PHONE_INDEX) {
            Phone selectedPhone = mPhoneFactoryProxy.getPhone(phoneId);
            if (selectedPhone != null && isAvailableForEmergencyCalls(selectedPhone)) {
                if (phonesWithEmergencyNumber == null
                        || phonesWithEmergencyNumber.contains(selectedPhone)) {
                    return selectedPhone;
                }
            }
        }

        Phone firstPhoneWithSim = null;
        List<SlotStatus> phoneSlotStatus = new ArrayList<>(phoneCount);
        for (int i = 0; i < phoneCount; i++) {
            Phone phone = mPhoneFactoryProxy.getPhone(i);
            if (phone == null) {
                continue;
            }
            // 3)
            if (isAvailableForEmergencyCalls(phone)) {
                if (phonesWithEmergencyNumber == null
                        || phonesWithEmergencyNumber.contains(phone)) {
                    // the slot has the radio on & state is in service.
                    Log.i(this,
                            "getFirstPhoneForEmergencyCall, radio on & in service, Phone Id:" + i);
                    return phone;
                }
            }
            // 6)
            // Store the RAF Capabilities for sorting later.
            int radioAccessFamily = phone.getRadioAccessFamily();
            SlotStatus status = new SlotStatus(i, radioAccessFamily, phone.getSubId());
            phoneSlotStatus.add(status);
            Log.i(this, "getFirstPhoneForEmergencyCall, RAF:" +
                Integer.toHexString(radioAccessFamily) + " saved for Phone Id:" + i + " subId:"
                + phone.getSubId());
            // 5)
            // Report Slot's PIN/PUK lock status for sorting later.
            int simState = mSubscriptionManagerProxy.getSimStateForSlotIdx(i);
            // Record SimState.
            status.simState = simState;
            if (simState == TelephonyManager.SIM_STATE_PIN_REQUIRED ||
                    simState == TelephonyManager.SIM_STATE_PUK_REQUIRED) {
                status.isLocked = true;
            }

            // 4) Store if the Phone has the corresponding emergency number
            if (phonesWithEmergencyNumber != null) {
                for (Phone phoneWithEmergencyNumber : phonesWithEmergencyNumber) {
                    if (phoneWithEmergencyNumber != null
                            && phoneWithEmergencyNumber.getPhoneId() == i) {
                        status.hasDialedEmergencyNumber = true;
                    }
                }
            }
            // 7)
            if (firstPhoneWithSim == null &&
                (phone.getSubId() != SubscriptionManager.INVALID_SIM_SLOT_INDEX)) {
                // The slot has a SIM card inserted (and an active subscription), but is not in
                // service, so keep track of this Phone.
                // Do not return because we want to make sure that none of the other Phones
                // are in service (because that is always faster).
                firstPhoneWithSim = phone;
                Log.i(this, "getFirstPhoneForEmergencyCall, SIM with active sub, Phone Id:" +
                    firstPhoneWithSim.getPhoneId());
            }
        }
        // 8)
        if (firstPhoneWithSim == null && phoneSlotStatus.isEmpty()) {
            if (phonesWithEmergencyNumber != null) {
                for (Phone phoneWithEmergencyNumber : phonesWithEmergencyNumber) {
                    if (phoneWithEmergencyNumber != null) {
                        return phoneWithEmergencyNumber;
                    }
                }
            }

            // No Phones available, get the default
            Log.i(this, "getFirstPhoneForEmergencyCall, return default phone");
            return  mPhoneFactoryProxy.getDefaultPhone();
        } else {
            // 6)
            final int defaultPhoneId = mPhoneFactoryProxy.getDefaultPhone().getPhoneId();
            final Phone firstOccupiedSlot = firstPhoneWithSim;
            if (!phoneSlotStatus.isEmpty()) {
                Log.i(this, "getFirstPhoneForEmergencyCall, list size: " + phoneSlotStatus.size()
                    + " defaultPhoneId: " + defaultPhoneId + " firstOccupiedSlot: "
                    + firstOccupiedSlot);
                // Only sort if there are enough elements to do so.
                if (phoneSlotStatus.size() > 1) {
                    Collections.sort(phoneSlotStatus, (o1, o2) -> {
                        // Sort by non-absent SIM (SIM without active sub is considered absent).
                        if (o1.isSubActiveAndSimPresent() && !o2.isSubActiveAndSimPresent()) {
                            return 1;
                        }
                        if (o2.isSubActiveAndSimPresent() && !o1.isSubActiveAndSimPresent()) {
                            return -1;
                        }
                        // First start by seeing if either of the phone slots are locked. If they
                        // are, then sort by non-locked SIM first. If they are both locked, sort
                        // by capability instead.
                        if (o1.isLocked && !o2.isLocked) {
                            return -1;
                        }
                        if (o2.isLocked && !o1.isLocked) {
                            return 1;
                        }
                        // Prefer slots where the number is considered emergency.
                        if (!o1.hasDialedEmergencyNumber && o2.hasDialedEmergencyNumber) {
                            return -1;
                        }
                        if (o1.hasDialedEmergencyNumber && !o2.hasDialedEmergencyNumber) {
                            return 1;
                        }
                        // sort by number of RadioAccessFamily Capabilities.
                        int compare = RadioAccessFamily.compare(o1.capabilities, o2.capabilities);
                        if (compare == 0) {
                            if (firstOccupiedSlot != null) {
                                // If the RAF capability is the same, choose based on whether or
                                // not any of the slots are occupied with a SIM card (if both
                                // are, always choose the first).
                                if (o1.slotId == firstOccupiedSlot.getPhoneId()) {
                                    return 1;
                                } else if (o2.slotId == firstOccupiedSlot.getPhoneId()) {
                                    return -1;
                                }
                            } else {
                                // No slots have SIMs detected in them, so weight the default
                                // Phone Id greater than the others.
                                if (o1.slotId == defaultPhoneId) {
                                    return 1;
                                } else if (o2.slotId == defaultPhoneId) {
                                    return -1;
                                }
                            }
                        }
                        return compare;
                    });
                }
                int mostCapablePhoneId = phoneSlotStatus.get(phoneSlotStatus.size() - 1).slotId;
                Log.i(this, "getFirstPhoneForEmergencyCall, Using Phone Id: " + mostCapablePhoneId +
                        "with highest capability");
                return mPhoneFactoryProxy.getPhone(mostCapablePhoneId);
            } else {
                // 7)
                return firstPhoneWithSim;
            }
        }
    }

    private boolean isAvailableForEmergencyCalls(Phone phone) {
        return isAvailableForEmergencyCalls(phone,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_EMERGENCY);
    }

    /**
     * Determines if the phone is available for an emergency call given the specified routing.
     *
     * @param phone the phone to check the service availability for
     * @param routing the emergency call routing for this call
     */
    @VisibleForTesting
    public boolean isAvailableForEmergencyCalls(Phone phone,
            @EmergencyNumber.EmergencyCallRouting int routing) {
        if (isCallDisallowedDueToSatellite(phone)) {
            // Phone is connected to satellite due to which it is not preferred for emergency call.
            return false;
        }

        if (phone.getImsRegistrationTech() == ImsRegistrationImplBase.REGISTRATION_TECH_CROSS_SIM) {
            // When a Phone is registered to Cross-SIM calling, there must always be a Phone on the
            // other sub which is registered to cellular, so that must be selected.
            Log.d(this, "isAvailableForEmergencyCalls: skipping over phone "
                    + phone + " as it is registered to CROSS_SIM");
            return false;
        }

        // In service phones are always appropriate for emergency calls.
        if (ServiceState.STATE_IN_SERVICE == phone.getServiceState().getState()) {
            return true;
        }

        // If the call routing is unknown or is using emergency routing, an emergency only attach is
        // sufficient for placing the emergency call.  Normal routed emergency calls cannot be
        // placed on an emergency-only phone.
        return (routing != EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL
                && phone.getServiceState().isEmergencyOnly());
    }

    /**
     * Determines if the connection should allow mute.
     *
     * @param phone The current phone.
     * @return {@code True} if the connection should allow mute.
     */
    private boolean allowsMute(Phone phone) {
        // For CDMA phones, check if we are in Emergency Callback Mode (ECM).  Mute is disallowed
        // in ECM mode.
        if (phone.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
            if (phone.isInEcm()) {
                return false;
            }
        }

        return true;
    }

    TelephonyConnection.TelephonyConnectionListener getTelephonyConnectionListener() {
        return mTelephonyConnectionListener;
    }

    /**
     * When a {@link TelephonyConnection} has its underlying original connection configured,
     * we need to add it to the correct conference controller.
     *
     * @param connection The connection to be added to the controller
     */
    public void addConnectionToConferenceController(TelephonyConnection connection) {
        // TODO: Need to revisit what happens when the original connection for the
        // TelephonyConnection changes.  If going from CDMA --> GSM (for example), the
        // instance of TelephonyConnection will still be a CdmaConnection, not a GsmConnection.
        // The CDMA conference controller makes the assumption that it will only have CDMA
        // connections in it, while the other conference controllers aren't as restrictive.  Really,
        // when we go between CDMA and GSM we should replace the TelephonyConnection.
        if (connection.isImsConnection()) {
            Log.d(this, "Adding IMS connection to conference controller: " + connection);
            mImsConferenceController.add(connection);
            mTelephonyConferenceController.remove(connection);
            if (connection instanceof CdmaConnection) {
                mCdmaConferenceController.remove((CdmaConnection) connection);
            }
        } else {
            int phoneType = connection.getCall().getPhone().getPhoneType();
            if (phoneType == TelephonyManager.PHONE_TYPE_GSM) {
                Log.d(this, "Adding GSM connection to conference controller: " + connection);
                mTelephonyConferenceController.add(connection);
                if (connection instanceof CdmaConnection) {
                    mCdmaConferenceController.remove((CdmaConnection) connection);
                }
            } else if (phoneType == TelephonyManager.PHONE_TYPE_CDMA &&
                    connection instanceof CdmaConnection) {
                Log.d(this, "Adding CDMA connection to conference controller: " + connection);
                mCdmaConferenceController.add((CdmaConnection) connection);
                mTelephonyConferenceController.remove(connection);
            }
            Log.d(this, "Removing connection from IMS conference controller: " + connection);
            mImsConferenceController.remove(connection);
        }
    }

    /**
     * Create a new CDMA connection. CDMA connections have additional limitations when creating
     * additional calls which are handled in this method.  Specifically, CDMA has a "FLASH" command
     * that can be used for three purposes: merging a call, swapping unmerged calls, and adding
     * a new outgoing call. The function of the flash command depends on the context of the current
     * set of calls. This method will prevent an outgoing call from being made if it is not within
     * the right circumstances to support adding a call.
     */
    private Connection checkAdditionalOutgoingCallLimits(Phone phone) {
        if (phone.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
            // Check to see if any CDMA conference calls exist, and if they do, check them for
            // limitations.
            for (Conference conference : getAllConferences()) {
                if (conference instanceof CdmaConference) {
                    CdmaConference cdmaConf = (CdmaConference) conference;

                    // If the CDMA conference has not been merged, add-call will not work, so fail
                    // this request to add a call.
                    if ((cdmaConf.getConnectionCapabilities()
                            & Connection.CAPABILITY_MERGE_CONFERENCE) != 0) {
                        return Connection.createFailedConnection(new DisconnectCause(
                                    DisconnectCause.RESTRICTED,
                                    null,
                                    getResources().getString(R.string.callFailed_cdma_call_limit),
                                    "merge-capable call exists, prevent flash command."));
                    }
                }
            }
        }

        return null; // null means nothing went wrong, and call should continue.
    }

    /**
     * For outgoing dialed calls, potentially send a ConnectionEvent if the user is on WFC and is
     * dialing an international number.
     * @param telephonyConnection The connection.
     */
    private void maybeSendInternationalCallEvent(TelephonyConnection telephonyConnection) {
        if (telephonyConnection == null || telephonyConnection.getPhone() == null ||
                telephonyConnection.getPhone().getDefaultPhone() == null) {
            return;
        }
        Phone phone = telephonyConnection.getPhone().getDefaultPhone();
        if (phone instanceof GsmCdmaPhone) {
            GsmCdmaPhone gsmCdmaPhone = (GsmCdmaPhone) phone;
            if (telephonyConnection.isOutgoingCall() &&
                    gsmCdmaPhone.isNotificationOfWfcCallRequired(
                            telephonyConnection.getOriginalConnection().getOrigDialString())) {
                // Send connection event to InCall UI to inform the user of the fact they
                // are potentially placing an international call on WFC.
                Log.i(this, "placeOutgoingConnection - sending international call on WFC " +
                        "confirmation event");
                telephonyConnection.sendTelephonyConnectionEvent(
                        TelephonyManager.EVENT_NOTIFY_INTERNATIONAL_CALL_ON_WFC, null);
            }
        }
    }

    private void handleTtyModeChange(boolean isTtyEnabled) {
        Log.i(this, "handleTtyModeChange; isTtyEnabled=%b", isTtyEnabled);
        mIsTtyEnabled = isTtyEnabled;
        for (Connection connection : getAllConnections()) {
            if (connection instanceof TelephonyConnection) {
                TelephonyConnection telephonyConnection = (TelephonyConnection) connection;
                telephonyConnection.setTtyEnabled(isTtyEnabled);
            }
        }
    }

    private void closeOrDestroyConnection(Connection connection, DisconnectCause cause) {
        if (connection instanceof TelephonyConnection) {
            TelephonyConnection telephonyConnection = (TelephonyConnection) connection;
            telephonyConnection.setTelephonyConnectionDisconnected(cause);
            // Close destroys the connection and notifies TelephonyConnection listeners.
            telephonyConnection.close();
        } else {
            connection.setDisconnected(cause);
            connection.destroy();
        }
    }

    private boolean showDataDialog(Phone phone, String number) {
        boolean ret = false;
        final Context context = getApplicationContext();
        String suppKey = MmiCodeUtil.getSuppServiceKey(number);
        if (suppKey != null) {
            boolean clirOverUtPrecautions = false;
            boolean cfOverUtPrecautions = false;
            boolean cbOverUtPrecautions = false;
            boolean cwOverUtPrecautions = false;

            CarrierConfigManager cfgManager = (CarrierConfigManager)
                phone.getContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
            if (cfgManager != null) {
                clirOverUtPrecautions = cfgManager.getConfigForSubId(phone.getSubId())
                    .getBoolean(CarrierConfigManager.KEY_CALLER_ID_OVER_UT_WARNING_BOOL);
                cfOverUtPrecautions = cfgManager.getConfigForSubId(phone.getSubId())
                    .getBoolean(CarrierConfigManager.KEY_CALL_FORWARDING_OVER_UT_WARNING_BOOL);
                cbOverUtPrecautions = cfgManager.getConfigForSubId(phone.getSubId())
                    .getBoolean(CarrierConfigManager.KEY_CALL_BARRING_OVER_UT_WARNING_BOOL);
                cwOverUtPrecautions = cfgManager.getConfigForSubId(phone.getSubId())
                    .getBoolean(CarrierConfigManager.KEY_CALL_WAITING_OVER_UT_WARNING_BOOL);
            }

            boolean isSsOverUtPrecautions = SuppServicesUiUtil
                .isSsOverUtPrecautions(context, phone);
            if (isSsOverUtPrecautions) {
                boolean showDialog = false;
                if (suppKey == MmiCodeUtil.BUTTON_CLIR_KEY && clirOverUtPrecautions) {
                    showDialog = true;
                } else if (suppKey == MmiCodeUtil.CALL_FORWARDING_KEY && cfOverUtPrecautions) {
                    showDialog = true;
                } else if (suppKey == MmiCodeUtil.CALL_BARRING_KEY && cbOverUtPrecautions) {
                    showDialog = true;
                } else if (suppKey == MmiCodeUtil.BUTTON_CW_KEY && cwOverUtPrecautions) {
                    showDialog = true;
                }

                if (showDialog) {
                    Log.d(this, "Creating UT Data enable dialog");
                    String message = SuppServicesUiUtil.makeMessage(context, suppKey, phone);
                    AlertDialog.Builder builder = FrameworksUtils.makeAlertDialogBuilder(context);
                    DialogInterface.OnClickListener networkSettingsClickListener =
                            new Dialog.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent intent = new Intent(Intent.ACTION_MAIN);
                                    ComponentName mobileNetworkSettingsComponent
                                        = new ComponentName(
                                                context.getString(
                                                    R.string.mobile_network_settings_package),
                                                context.getString(
                                                    R.string.mobile_network_settings_class));
                                    intent.setComponent(mobileNetworkSettingsComponent);
                                    context.startActivity(intent);
                                }
                            };
                    Dialog dialog = builder.setMessage(message)
                        .setNeutralButton(context.getResources().getString(
                                R.string.settings_label),
                                networkSettingsClickListener)
                        .setPositiveButton(context.getResources().getString(
                                R.string.supp_service_over_ut_precautions_dialog_dismiss), null)
                        .create();
                    dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                    dialog.show();
                    ret = true;
                }
            }
        }
        return ret;
    }

    /**
     * Adds a {@link Conference} to the telephony ConnectionService and registers a listener for
     * changes to the conference.  Should be used instead of {@link #addConference(Conference)}.
     * @param conference The conference.
     */
    public void addTelephonyConference(@NonNull TelephonyConferenceBase conference) {
        addConference(conference);
        conference.addTelephonyConferenceListener(mTelephonyConferenceListener);
    }

    /**
     * Sends a test device to device message on the active call which supports it.
     * Used exclusively from the telephony shell command to send a test message.
     *
     * @param message the message
     * @param value the value
     */
    public void sendTestDeviceToDeviceMessage(int message, int value) {
       getAllConnections().stream()
               .filter(f -> f instanceof TelephonyConnection)
               .forEach(t -> {
                   TelephonyConnection tc = (TelephonyConnection) t;
                   if (!tc.isImsConnection()) {
                       Log.w(this, "sendTestDeviceToDeviceMessage: not an IMS connection");
                       return;
                   }
                   Communicator c = tc.getCommunicator();
                   if (c == null) {
                       Log.w(this, "sendTestDeviceToDeviceMessage: D2D not enabled");
                       return;
                   }

                   c.sendMessages(Set.of(new Communicator.Message(message, value)));

               });
    }

    /**
     * Overrides the current D2D transport, forcing the specified one to be active.  Used for test.
     * @param transport The class simple name of the transport to make active.
     */
    public void setActiveDeviceToDeviceTransport(@NonNull String transport) {
        getAllConnections().stream()
                .filter(f -> f instanceof TelephonyConnection)
                .forEach(t -> {
                    TelephonyConnection tc = (TelephonyConnection) t;
                    Communicator c = tc.getCommunicator();
                    if (c == null) {
                        Log.w(this, "setActiveDeviceToDeviceTransport: D2D not enabled");
                        return;
                    }
                    Log.i(this, "setActiveDeviceToDeviceTransport: callId=%s, set to: %s",
                            tc.getTelecomCallId(), transport);
                    c.setTransportActive(transport);
                });
    }

    private PhoneAccountHandle adjustAccountHandle(Phone phone,
            PhoneAccountHandle origAccountHandle) {
        int origSubId = PhoneUtils.getSubIdForPhoneAccountHandle(origAccountHandle);
        int subId = phone.getSubId();
        if (origSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                && origSubId != subId) {
            PhoneAccountHandle handle = TelecomAccountRegistry.getInstance(this)
                .getPhoneAccountHandleForSubId(subId);
            if (handle != null) {
                return handle;
            }
        }
        return origAccountHandle;
    }

    /*
     * Returns true if both existing connections on-device and the incoming connection support HOLD,
     * false otherwise. Assumes that a TelephonyConference supports HOLD.
     */
    private boolean allCallsSupportHold(@NonNull TelephonyConnection incomingConnection) {
        if (Flags.callExtraForNonHoldSupportedCarriers()) {
            if (getAllConnections().stream()
                    .filter(c ->
                            // Exclude multiendpoint calls as they're not on this device.
                            (c.getConnectionProperties() & Connection.PROPERTY_IS_EXTERNAL_CALL)
                                    == 0
                                    && (c.getConnectionCapabilities()
                                    & Connection.CAPABILITY_SUPPORT_HOLD) != 0).count() == 0) {
                return false;
            }
            if ((incomingConnection.getConnectionCapabilities()
                    & Connection.CAPABILITY_SUPPORT_HOLD) == 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * For the passed in incoming {@link TelephonyConnection}, for non-dual active voice devices,
     * adds {@link Connection#EXTRA_ANSWERING_DROPS_FG_CALL} if there are ongoing calls on another
     * subscription (ie phone account handle) than the one passed in. For dual active voice devices,
     * still sets the EXTRA if either subscription has connections that don't support hold.
     * @param connection The connection.
     * @param phoneAccountHandle The {@link PhoneAccountHandle} the incoming call originated on;
     *                           this is passed in because
     *                           {@link Connection#getPhoneAccountHandle()} is not set until after
     *                           {@link ConnectionService#onCreateIncomingConnection(
     *                           PhoneAccountHandle, ConnectionRequest)} returns.
     */
    public void maybeIndicateAnsweringWillDisconnect(@NonNull TelephonyConnection connection,
            @NonNull PhoneAccountHandle phoneAccountHandle) {
        if (isCallPresentOnOtherSub(phoneAccountHandle)) {
            if (mTelephonyManagerProxy.isConcurrentCallsPossible()
                    && allCallsSupportHold(connection)) {
                return;
            }
            Log.i(this, "maybeIndicateAnsweringWillDisconnect; answering call %s will cause a call "
                    + "on another subscription to drop.", connection.getTelecomCallId());
            Bundle extras = new Bundle();
            extras.putBoolean(Connection.EXTRA_ANSWERING_DROPS_FG_CALL, true);
            connection.putExtras(extras);
        }
    }

    /**
     * Checks to see if there are calls present on a sub other than the one passed in.
     * @param incomingHandle The new incoming connection {@link PhoneAccountHandle}
     */
    private boolean isCallPresentOnOtherSub(@NonNull PhoneAccountHandle incomingHandle) {
        return getAllConnections().stream()
                .filter(c ->
                        // Exclude multiendpoint calls as they're not on this device.
                        (c.getConnectionProperties() & Connection.PROPERTY_IS_EXTERNAL_CALL) == 0
                        // Include any calls not on same sub as current connection.
                        && !Objects.equals(c.getPhoneAccountHandle(), incomingHandle))
                .count() > 0;
    }

    /**
     * Where there are ongoing calls on another subscription other than the one specified,
     * disconnect these calls. This is used where there is an incoming call on one sub, but there
     * are ongoing calls on another sub which need to be disconnected.
     * @param incomingHandle The incoming {@link PhoneAccountHandle}.
     * @param answeringDropsFgCall Whether for dual-SIM dual active devices, answering the incoming
     *                            call should drop the second call.
     */
    public void maybeDisconnectCallsOnOtherSubs(
            @NonNull PhoneAccountHandle incomingHandle, boolean answeringDropsFgCall) {
        Log.i(this, "maybeDisconnectCallsOnOtherSubs: check for calls not on %s", incomingHandle);
        maybeDisconnectCallsOnOtherSubs(getAllConnections(), incomingHandle, answeringDropsFgCall,
                mTelephonyManagerProxy);
    }

    /**
     * Used by {@link #maybeDisconnectCallsOnOtherSubs(PhoneAccountHandle)} to evaluate and perform
     * call disconnection. This method exists as a convenience so that it is possible to unit test
     * the core functionality.
     * @param connections the calls to check.
     * @param incomingHandle the incoming handle.
     * @param answeringDropsFgCall Whether for dual-SIM dual active devices, answering the incoming
     *                            call should drop the second call.
     * @param telephonyManagerProxy the proxy to the {@link TelephonyManager} instance.
     */
    @VisibleForTesting
    public static void maybeDisconnectCallsOnOtherSubs(@NonNull Collection<Connection> connections,
            @NonNull PhoneAccountHandle incomingHandle,
            boolean answeringDropsFgCall,
            TelephonyManagerProxy telephonyManagerProxy) {
        if (telephonyManagerProxy.isConcurrentCallsPossible() && !answeringDropsFgCall) {
            return;
        }
        connections.stream()
                .filter(c ->
                        // Exclude multiendpoint calls as they're not on this device.
                        (c.getConnectionProperties() & Connection.PROPERTY_IS_EXTERNAL_CALL)
                                == 0
                                // Include any calls not on same sub as current connection.
                                && !Objects.equals(c.getPhoneAccountHandle(), incomingHandle))
                .forEach(c -> {
                    if (c instanceof TelephonyConnection) {
                        TelephonyConnection tc = (TelephonyConnection) c;
                        if (!tc.shouldTreatAsEmergencyCall()) {
                            Log.i(LOG_TAG,
                                    "maybeDisconnectCallsOnOtherSubs: disconnect %s due to "
                                            + "incoming call on other sub.",
                                    tc.getTelecomCallId());
                            // Note: intentionally calling hangup instead of onDisconnect.
                            // onDisconnect posts the disconnection to a handle which means that the
                            // disconnection will take place AFTER we answer the incoming call.
                            tc.hangup(android.telephony.DisconnectCause.LOCAL);
                        }
                    }
                });
    }

    static boolean isStateActive(Conferenceable conferenceable) {
        if (conferenceable instanceof Connection) {
            Connection connection = (Connection) conferenceable;
            return connection.getState() == Connection.STATE_ACTIVE;
        } else if (conferenceable instanceof Conference) {
            Conference conference = (Conference) conferenceable;
            return conference.getState() == Connection.STATE_ACTIVE;
        } else {
            throw new IllegalArgumentException(
                    "isStateActive(): Unexpected conferenceable! " + conferenceable);
        }
    }

    static void onHold(Conferenceable conferenceable) {
        if (conferenceable instanceof Connection) {
            Connection connection = (Connection) conferenceable;
            connection.onHold();
        } else if (conferenceable instanceof Conference) {
            Conference conference = (Conference) conferenceable;
            conference.onHold();
        } else {
            throw new IllegalArgumentException(
                    "onHold(): Unexpected conferenceable! " + conferenceable);
        }
    }

    static void onUnhold(Conferenceable conferenceable) {
        if (conferenceable instanceof Connection) {
            Connection connection = (Connection) conferenceable;
            connection.onUnhold();
        } else if (conferenceable instanceof Conference) {
            Conference conference = (Conference) conferenceable;
            conference.onUnhold();
        } else {
            throw new IllegalArgumentException(
                    "onUnhold(): Unexpected conferenceable! " + conferenceable);
        }
    }

    private static void hangup(Conferenceable conferenceable, int code) {
        if (conferenceable instanceof TelephonyConnection) {
            ((TelephonyConnection) conferenceable).hangup(code);
        } else if (conferenceable instanceof Conference) {
            ((Conference) conferenceable).onDisconnect();
        } else {
            Log.w(LOG_TAG, "hangup(): Unexpected conferenceable! " + conferenceable);
        }
    }

     /**
     * Evaluates whether a connection or conference exists on subscriptions other than the one
     * corresponding to the existing {@link PhoneAccountHandle}.
     * @param connections all individual connections, including conference participants.
     * @param conferences all conferences.
     * @param currentHandle the existing call handle;
     * @param telephonyManagerProxy the proxy to the {@link TelephonyManager} instance.
     */
    private static @Nullable Conferenceable maybeGetFirstConferenceableFromOtherSubscription(
            @NonNull Collection<Connection> connections,
            @NonNull Collection<Conference> conferences,
            @NonNull PhoneAccountHandle currentHandle,
            TelephonyManagerProxy telephonyManagerProxy) {
        if (!telephonyManagerProxy.isConcurrentCallsPossible()) {
            return null;
        }

        List<Conference> otherSubConferences = conferences.stream()
                .filter(c ->
                        // Exclude multiendpoint calls as they're not on this device.
                        (c.getConnectionProperties()
                                & Connection.PROPERTY_IS_EXTERNAL_CALL) == 0
                                // Include any conferences not on same sub as current connection.
                                && !Objects.equals(c.getPhoneAccountHandle(),
                                currentHandle))
                .toList();
        if (!otherSubConferences.isEmpty()) {
            Log.i(LOG_TAG, "maybeGetFirstConferenceable: found "
                    + otherSubConferences.get(0).getTelecomCallId() + " on "
                    + otherSubConferences.get(0).getPhoneAccountHandle());
            return otherSubConferences.get(0);
        }

        // Considers Connections (including conference participants) only if no conferences.
        List<Connection> otherSubConnections = connections.stream()
                .filter(c ->
                        // Exclude multiendpoint calls as they're not on this device.
                        (c.getConnectionProperties() & Connection.PROPERTY_IS_EXTERNAL_CALL) == 0
                                // Include any calls not on same sub as current connection.
                                && !Objects.equals(c.getPhoneAccountHandle(),
                                currentHandle)).toList();

        if (!otherSubConnections.isEmpty()) {
            if (otherSubConnections.size() > 1) {
                Log.w(LOG_TAG, "Unexpected number of connections: "
                        + otherSubConnections.size() + " on other sub!");
            }
            Log.i(LOG_TAG, "maybeGetFirstConferenceable: found "
                    + otherSubConnections.get(0).getTelecomCallId() + " on "
                    + otherSubConnections.get(0).getPhoneAccountHandle());
            return otherSubConnections.get(0);
        }
        return null;
    }

    /**
     * Where there are ongoing calls on multiple subscriptions for DSDA devices, let the 'hold'
     * button perform an unhold on the other sub's Connection or Conference. This covers for Dialer
     * apps that may not have a dedicated 'swap' button for calls across different subs.
     * @param currentHandle The {@link PhoneAccountHandle} of the current active voice call.
     */
    public void maybeUnholdCallsOnOtherSubs(
            @NonNull PhoneAccountHandle currentHandle) {
        Log.i(this, "maybeUnholdCallsOnOtherSubs: check for calls not on %s",
                currentHandle);
        maybeUnholdCallsOnOtherSubs(getAllConnections(), getAllConferences(),
                currentHandle, mTelephonyManagerProxy);
    }

    /**
     * Where there are ongoing calls on multiple subscriptions for DSDA devices, let the 'hold'
     * button perform an unhold on the other sub's Connection or Conference. This is a convenience
     * method to unit test the core functionality.
     *
     * @param connections all individual connections, including conference participants.
     * @param conferences all conferences.
     * @param currentHandle The {@link PhoneAccountHandle} of the current active call.
     * @param telephonyManagerProxy the proxy to the {@link TelephonyManager} instance.
     */
    @VisibleForTesting
    protected static void maybeUnholdCallsOnOtherSubs(@NonNull Collection<Connection> connections,
            @NonNull Collection<Conference> conferences,
            @NonNull PhoneAccountHandle currentHandle,
            TelephonyManagerProxy telephonyManagerProxy) {
        Conferenceable c = maybeGetFirstConferenceableFromOtherSubscription(
                connections, conferences, currentHandle, telephonyManagerProxy);
        if (c != null) {
            onUnhold(c);
        }
    }

    /**
     * For DSDA devices, when an outgoing call is dialed out from the 2nd sub, holds the first call.
     *
     * @param outgoingHandle The outgoing {@link PhoneAccountHandle}.
     * @return the Conferenceable representing the Connection or Conference to be held.
     */
    private @Nullable Conferenceable maybeHoldCallsOnOtherSubs(
            @NonNull PhoneAccountHandle outgoingHandle) {
        Log.i(this, "maybeHoldCallsOnOtherSubs: check for calls not on %s",
                outgoingHandle);
        return maybeHoldCallsOnOtherSubs(getAllConnections(), getAllConferences(),
                outgoingHandle, mTelephonyManagerProxy);
    }

    /**
     * For DSDA devices, when an outgoing call is dialed out from the 2nd sub, holds the first call.
     * This is a convenience method to unit test the core functionality.
     *
     * @param connections all individual connections, including conference participants.
     * @param conferences all conferences.
     * @param outgoingHandle The outgoing {@link PhoneAccountHandle}.
     * @param telephonyManagerProxy the proxy to the {@link TelephonyManager} instance.
     * @return the {@link Conferenceable} representing the Connection or Conference to be held.
     */
    @VisibleForTesting
    protected static @Nullable Conferenceable maybeHoldCallsOnOtherSubs(
            @NonNull Collection<Connection> connections,
            @NonNull Collection<Conference> conferences,
            @NonNull PhoneAccountHandle outgoingHandle,
            TelephonyManagerProxy telephonyManagerProxy) {
        Conferenceable c = maybeGetFirstConferenceableFromOtherSubscription(
                connections, conferences, outgoingHandle, telephonyManagerProxy);
        if (c != null && isStateActive(c)) {
            onHold(c);
            return c;
        }
        return null;
    }

    private void disconnectAllCallsOnOtherSubs (@NonNull PhoneAccountHandle handle) {
        Collection<Connection>connections = getAllConnections();
        connections.stream()
                .filter(c ->
                        (c.getState() == Connection.STATE_ACTIVE
                                || c.getState() == Connection.STATE_HOLDING)
                                // Include any calls not on same sub as current connection.
                                && !Objects.equals(c.getPhoneAccountHandle(), handle))
                .forEach(c -> {
                    if (c instanceof TelephonyConnection) {
                        TelephonyConnection tc = (TelephonyConnection) c;
                        Log.i(LOG_TAG, "disconnectAllCallsOnOtherSubs: disconnect" +
                                " %s due to redial happened on other sub.",
                                tc.getTelecomCallId());
                        tc.hangup(android.telephony.DisconnectCause.LOCAL);
                    }
                });
    }

    private @NetworkRegistrationInfo.Domain int getActiveCallDomain(int subId) {
        for (Connection c: getAllConnections()) {
            if ((c instanceof TelephonyConnection)) {
                TelephonyConnection connection = (TelephonyConnection) c;
                Phone phone = connection.getPhone();
                if (phone == null) {
                    continue;
                }

                if (phone.getSubId() == subId) {
                    if (phone instanceof GsmCdmaPhone) {
                        return NetworkRegistrationInfo.DOMAIN_CS;
                    } else if (phone instanceof ImsPhone) {
                        return NetworkRegistrationInfo.DOMAIN_PS;
                    }
                }
            }
        }
        return NetworkRegistrationInfo.DOMAIN_UNKNOWN;
    }

    private void handleEmergencyCallStartedForSatelliteSOSMessageRecommender(
            @NonNull TelephonyConnection connection, @NonNull Phone phone) {
        if (mSatelliteSOSMessageRecommender == null) {
            mSatelliteSOSMessageRecommender = new SatelliteSOSMessageRecommender(phone.getContext(),
                    phone.getContext().getMainLooper());
        }
        connection.addTelephonyConnectionListener(mEmergencyConnectionSatelliteListener);
        mSatelliteSOSMessageRecommender.onEmergencyCallStarted(connection);
    }

    /**
     * Check whether making a call is disallowed while using satellite
     * @param phone phone object whose supported services needs to be checked
     * @return {@code true} if network does not support calls while using satellite
     * else {@code false}.
     */
    private boolean isCallDisallowedDueToSatellite(Phone phone) {
        if (!carrierEnabledSatelliteFlag()) {
            return false;
        }

        if (phone == null) {
            return false;
        }

        ServiceState serviceState = phone.getServiceState();
        if (serviceState == null) {
            return false;
        }

        if (!serviceState.isUsingNonTerrestrialNetwork()) {
            // Device is not connected to satellite
            return false;
        }

        for (NetworkRegistrationInfo nri : serviceState.getNetworkRegistrationInfoList()) {
            if (nri.isNonTerrestrialNetwork()
                    && nri.getAvailableServices().contains(
                    NetworkRegistrationInfo.SERVICE_TYPE_VOICE)) {
                // Call is supported while using satellite
                return false;
            }
        }

        // Call is disallowed while using satellite
        return true;
    }
}
