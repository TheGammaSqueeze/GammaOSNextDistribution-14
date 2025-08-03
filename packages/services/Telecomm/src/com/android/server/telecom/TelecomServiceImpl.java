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

package com.android.server.telecom;

import static android.Manifest.permission.CALL_PHONE;
import static android.Manifest.permission.CALL_PRIVILEGED;
import static android.Manifest.permission.DUMP;
import static android.Manifest.permission.MANAGE_OWN_CALLS;
import static android.Manifest.permission.MODIFY_PHONE_STATE;
import static android.Manifest.permission.READ_PHONE_NUMBERS;
import static android.Manifest.permission.READ_PHONE_STATE;
import static android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE;
import static android.Manifest.permission.READ_SMS;
import static android.Manifest.permission.REGISTER_SIM_SUBSCRIPTION;
import static android.Manifest.permission.WRITE_SECURE_SETTINGS;
import static android.telecom.CallAttributes.DIRECTION_INCOMING;
import static android.telecom.CallAttributes.DIRECTION_OUTGOING;
import static android.telecom.CallException.CODE_ERROR_UNKNOWN;
import static android.telecom.TelecomManager.TELECOM_TRANSACTION_SUCCESS;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.UiModeManager;
import android.app.compat.CompatChanges;
import android.content.AttributionSource;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.PermissionChecker;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ParceledListSlice;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.BlockedNumberContract;
import android.provider.Settings;
import android.telecom.CallAttributes;
import android.telecom.CallException;
import android.telecom.Log;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.StatusHints;
import android.telecom.TelecomAnalytics;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.EventLog;

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telecom.ICallControl;
import com.android.internal.telecom.ICallEventCallback;
import com.android.internal.telecom.ITelecomService;
import com.android.internal.util.IndentingPrintWriter;
import com.android.server.telecom.components.UserCallIntentProcessorFactory;
import com.android.server.telecom.flags.FeatureFlags;
import com.android.server.telecom.settings.BlockedNumbersActivity;
import com.android.server.telecom.voip.IncomingCallTransaction;
import com.android.server.telecom.voip.OutgoingCallTransaction;
import com.android.server.telecom.voip.TransactionManager;
import com.android.server.telecom.voip.VoipCallTransaction;
import com.android.server.telecom.voip.VoipCallTransactionResult;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

// TODO: Needed for move to system service: import com.android.internal.R;

/**
 * Implementation of the ITelecom interface.
 */
public class TelecomServiceImpl {

    public interface SubscriptionManagerAdapter {
        int getDefaultVoiceSubId();
    }

    static class SubscriptionManagerAdapterImpl implements SubscriptionManagerAdapter {
        @Override
        public int getDefaultVoiceSubId() {
            return SubscriptionManager.getDefaultVoiceSubscriptionId();
        }
    }

    public interface SettingsSecureAdapter {
        void putStringForUser(ContentResolver resolver, String name, String value, int userHandle);

        String getStringForUser(ContentResolver resolver, String name, int userHandle);
    }

    static class SettingsSecureAdapterImpl implements SettingsSecureAdapter {
        @Override
        public void putStringForUser(ContentResolver resolver, String name, String value,
            int userHandle) {
            Settings.Secure.putStringForUser(resolver, name, value, userHandle);
        }

        @Override
        public String getStringForUser(ContentResolver resolver, String name, int userHandle) {
            return Settings.Secure.getStringForUser(resolver, name, userHandle);
        }
    }

    private static final String TAG = "TelecomServiceImpl";
    private static final String TIME_LINE_ARG = "timeline";
    private static final int DEFAULT_VIDEO_STATE = -1;
    private static final String PERMISSION_HANDLE_CALL_INTENT =
            "android.permission.HANDLE_CALL_INTENT";
    private static final String ADD_CALL_ERR_MSG = "Call could not be created or found. "
            + "Retry operation.";
    private AnomalyReporterAdapter mAnomalyReporter = new AnomalyReporterAdapterImpl();

    /**
     * Anomaly Report UUIDs and corresponding error descriptions specific to TelecomServiceImpl.
     */
    public static final UUID REGISTER_PHONE_ACCOUNT_ERROR_UUID =
            UUID.fromString("0e49f82e-6acc-48a9-b088-66c8296c1eb5");
    public static final String REGISTER_PHONE_ACCOUNT_ERROR_MSG =
            "Exception thrown while registering phone account.";
    public static final UUID SET_USER_PHONE_ACCOUNT_ERROR_UUID =
            UUID.fromString("80866066-7818-4869-bd44-1f7f689543e2");
    public static final String SET_USER_PHONE_ACCOUNT_ERROR_MSG =
            "Exception thrown while setting the user selected outgoing phone account.";
    public static final UUID GET_CALL_CAPABLE_ACCOUNTS_ERROR_UUID =
            UUID.fromString("4f39b865-01f2-4c1f-83a5-37ce52807e83");
    public static final String GET_CALL_CAPABLE_ACCOUNTS_ERROR_MSG =
            "Exception thrown while getting the call capable phone accounts";
    public static final UUID GET_PHONE_ACCOUNT_ERROR_UUID =
            UUID.fromString("b653c1f0-91b4-45c8-ad05-3ee4d1006c7f");
    public static final String GET_PHONE_ACCOUNT_ERROR_MSG =
            "Exception thrown while retrieving the phone account.";
    public static final UUID GET_SIM_MANAGER_ERROR_UUID =
            UUID.fromString("4244cb3f-bd02-4cc5-9f90-f41ea62ce0bb");
    public static final String GET_SIM_MANAGER_ERROR_MSG =
            "Exception thrown while retrieving the SIM CallManager.";
    public static final UUID GET_SIM_MANAGER_FOR_USER_ERROR_UUID =
            UUID.fromString("5d347ce7-7527-40d3-b98a-09b423ad031c");
    public static final String GET_SIM_MANAGER_FOR_USER_ERROR_MSG =
            "Exception thrown while retrieving the SIM CallManager based on the provided user.";
    public static final UUID PLACE_CALL_SECURITY_EXCEPTION_ERROR_UUID =
            UUID.fromString("4edf6c8d-1e43-4c94-b0fc-a40c8d80cfe8");
    public static final String PLACE_CALL_SECURITY_EXCEPTION_ERROR_MSG =
            "Security exception thrown while placing an outgoing call.";

    @VisibleForTesting
    public void setAnomalyReporterAdapter(AnomalyReporterAdapter mAnomalyReporterAdapter){
        mAnomalyReporter = mAnomalyReporterAdapter;
    }

    private final ITelecomService.Stub mBinderImpl = new ITelecomService.Stub() {

        @Override
        public void addCall(CallAttributes callAttributes, ICallEventCallback callEventCallback,
                String callId, String callingPackage) {
            try {
                Log.startSession("TSI.aC", Log.getPackageAbbreviation(callingPackage));
                Log.i(TAG, "addCall: id=[%s], attributes=[%s]", callId, callAttributes);
                PhoneAccountHandle handle = callAttributes.getPhoneAccountHandle();

                // enforce permissions and arguments
                enforcePermission(android.Manifest.permission.MANAGE_OWN_CALLS);
                enforceUserHandleMatchesCaller(handle);
                enforcePhoneAccountIsNotManaged(handle);// only allow self-managed packages (temp.)
                enforcePhoneAccountIsRegisteredEnabled(handle, handle.getUserHandle());
                enforceCallingPackage(callingPackage, "addCall");

                // add extras about info used for FGS delegation
                Bundle extras = new Bundle();
                extras.putInt(CallAttributes.CALLER_UID_KEY, Binder.getCallingUid());
                extras.putInt(CallAttributes.CALLER_PID_KEY, Binder.getCallingPid());

                VoipCallTransaction transaction = null;
                // create transaction based on the call direction
                switch (callAttributes.getDirection()) {
                    case DIRECTION_OUTGOING:
                        transaction = new OutgoingCallTransaction(callId, mContext, callAttributes,
                                mCallsManager, extras);
                        break;
                    case DIRECTION_INCOMING:
                        transaction = new IncomingCallTransaction(callId, callAttributes,
                                mCallsManager, extras);
                        break;
                    default:
                        throw new IllegalArgumentException(String.format("Invalid Call Direction. "
                                        + "Was [%d] but should be within [%d,%d]",
                                callAttributes.getDirection(), DIRECTION_INCOMING,
                                DIRECTION_OUTGOING));
                }

                mTransactionManager.addTransaction(transaction, new OutcomeReceiver<>() {
                    @Override
                    public void onResult(VoipCallTransactionResult result) {
                        Log.d(TAG, "addCall: onResult");
                        Call call = result.getCall();

                        if (call == null || !call.getId().equals(callId)) {
                            Log.i(TAG, "addCall: onResult: call is null or id mismatch");
                            onAddCallControl(callId, callEventCallback, null,
                                    new CallException(ADD_CALL_ERR_MSG, CODE_ERROR_UNKNOWN));
                            return;
                        }

                        TransactionalServiceWrapper serviceWrapper =
                                mTransactionalServiceRepository
                                        .addNewCallForTransactionalServiceWrapper(handle,
                                                callEventCallback, mCallsManager, call);

                        call.setTransactionServiceWrapper(serviceWrapper);
                        if (mFeatureFlags.transactionalVideoState()) {
                            call.setTransactionalCallSupportsVideoCalling(callAttributes);
                        }
                        ICallControl clientCallControl = serviceWrapper.getICallControl();

                        if (clientCallControl == null) {
                            throw new IllegalStateException("TransactionalServiceWrapper"
                                    + "#ICallControl is null.");
                        }

                        // finally, send objects back to the client
                        onAddCallControl(callId, callEventCallback, clientCallControl, null);
                    }

                    @Override
                    public void onError(@NonNull CallException exception) {
                        Log.d(TAG, "addCall: onError: e=[%s]", exception.toString());
                        onAddCallControl(callId, callEventCallback, null, exception);
                    }
                });
            } finally {
                Log.endSession();
            }
        }

        private void onAddCallControl(String callId, ICallEventCallback callEventCallback,
                ICallControl callControl, CallException callException) {
            try {
                if (callException == null) {
                    callEventCallback.onAddCallControl(callId, TELECOM_TRANSACTION_SUCCESS,
                            callControl, null);
                } else {
                    callEventCallback.onAddCallControl(callId,
                            CallException.CODE_ERROR_UNKNOWN,
                            null, callException);
                }
            } catch (RemoteException remoteException) {
                throw remoteException.rethrowAsRuntimeException();
            }
        }

        @Override
        public PhoneAccountHandle getDefaultOutgoingPhoneAccount(String uriScheme,
                String callingPackage, String callingFeatureId) {
            try {
                Log.startSession("TSI.gDOPA", Log.getPackageAbbreviation(callingPackage));
                synchronized (mLock) {
                    PhoneAccountHandle phoneAccountHandle = null;
                    final UserHandle callingUserHandle = Binder.getCallingUserHandle();
                    long token = Binder.clearCallingIdentity();
                    try {
                        phoneAccountHandle = mPhoneAccountRegistrar
                                .getOutgoingPhoneAccountForScheme(uriScheme, callingUserHandle);
                    } catch (Exception e) {
                        Log.e(this, e, "getDefaultOutgoingPhoneAccount");
                        throw e;
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                    if (isCallerSimCallManager(phoneAccountHandle)
                            || canReadPhoneState(
                            callingPackage,
                            callingFeatureId,
                            "getDefaultOutgoingPhoneAccount")) {
                        return phoneAccountHandle;
                    }
                    return null;
                }
            } finally {
                Log.endSession();
            }
        }

        @Override
        public PhoneAccountHandle getUserSelectedOutgoingPhoneAccount(String callingPackage) {
            synchronized (mLock) {
                try {
                    Log.startSession("TSI.gUSOPA", Log.getPackageAbbreviation(callingPackage));
                    if (!isDialerOrPrivileged(callingPackage, "getDefaultOutgoingPhoneAccount")) {
                        throw new SecurityException("Only the default dialer, or caller with "
                                + "READ_PRIVILEGED_PHONE_STATE can call this method.");
                    }
                    final UserHandle callingUserHandle = Binder.getCallingUserHandle();
                    return mPhoneAccountRegistrar.getUserSelectedOutgoingPhoneAccount(
                            callingUserHandle);
                } catch (Exception e) {
                    Log.e(this, e, "getUserSelectedOutgoingPhoneAccount");
                    throw e;
                } finally {
                    Log.endSession();
                }
            }
        }

        @Override
        public void setUserSelectedOutgoingPhoneAccount(PhoneAccountHandle accountHandle) {
            try {
                Log.startSession("TSI.sUSOPA");
                synchronized (mLock) {
                    enforceModifyPermission();
                    UserHandle callingUserHandle = Binder.getCallingUserHandle();
                    long token = Binder.clearCallingIdentity();
                    try {
                        mPhoneAccountRegistrar.setUserSelectedOutgoingPhoneAccount(
                                accountHandle, callingUserHandle);
                    } catch (Exception e) {
                        Log.e(this, e, "setUserSelectedOutgoingPhoneAccount");
                        mAnomalyReporter.reportAnomaly(SET_USER_PHONE_ACCOUNT_ERROR_UUID,
                                SET_USER_PHONE_ACCOUNT_ERROR_MSG);
                        throw e;
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        @Override
        public ParceledListSlice<PhoneAccountHandle> getCallCapablePhoneAccounts(
                boolean includeDisabledAccounts, String callingPackage,
                String callingFeatureId, boolean acrossProfiles) {
            try {
                Log.startSession("TSI.gCCPA", Log.getPackageAbbreviation(callingPackage));

                if (mTelephonyFeatureFlags.workProfileApiSplit()) {
                    if (acrossProfiles) {
                        enforceInAppCrossProfilePermission();
                    }

                    if (includeDisabledAccounts && !canReadPrivilegedPhoneState(
                            callingPackage, "getCallCapablePhoneAccounts")) {
                        throw new SecurityException(
                                "Requires READ_PRIVILEGED_PHONE_STATE permission.");
                    }

                    if (!includeDisabledAccounts && !canReadPhoneState(callingPackage,
                            callingFeatureId, "Requires READ_PHONE_STATE permission.")) {
                        throw new SecurityException("Requires READ_PHONE_STATE permission.");
                    }
                }

                if (includeDisabledAccounts &&
                        !canReadPrivilegedPhoneState(
                                callingPackage, "getCallCapablePhoneAccounts")) {
                    return ParceledListSlice.emptyList();
                }
                if (!canReadPhoneState(callingPackage, callingFeatureId,
                        "getCallCapablePhoneAccounts")) {
                    return ParceledListSlice.emptyList();
                }
                synchronized (mLock) {
                    final UserHandle callingUserHandle = Binder.getCallingUserHandle();
                    boolean crossUserAccess = mTelephonyFeatureFlags.workProfileApiSplit()
                            && !acrossProfiles ? false
                            : (mTelephonyFeatureFlags.workProfileApiSplit()
                                    ? hasInAppCrossProfilePermission()
                                    : hasInAppCrossUserPermission());
                    long token = Binder.clearCallingIdentity();
                    try {
                        return new ParceledListSlice<>(
                                mPhoneAccountRegistrar.getCallCapablePhoneAccounts(null,
                                        includeDisabledAccounts, callingUserHandle,
                                        crossUserAccess));
                    } catch (Exception e) {
                        Log.e(this, e, "getCallCapablePhoneAccounts");
                        mAnomalyReporter.reportAnomaly(GET_CALL_CAPABLE_ACCOUNTS_ERROR_UUID,
                                GET_CALL_CAPABLE_ACCOUNTS_ERROR_MSG);
                        throw e;
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        @Override
        public ParceledListSlice<PhoneAccountHandle> getSelfManagedPhoneAccounts(
                String callingPackage, String callingFeatureId) {
            try {
                Log.startSession("TSI.gSMPA", Log.getPackageAbbreviation(callingPackage));
                if (!canReadPhoneState(callingPackage, callingFeatureId,
                        "Requires READ_PHONE_STATE permission.")) {
                    throw new SecurityException("Requires READ_PHONE_STATE permission.");
                }
                synchronized (mLock) {
                    final UserHandle callingUserHandle = Binder.getCallingUserHandle();
                    long token = Binder.clearCallingIdentity();
                    try {
                        return new ParceledListSlice<>(mPhoneAccountRegistrar
                                .getSelfManagedPhoneAccounts(callingUserHandle));
                    } catch (Exception e) {
                        Log.e(this, e, "getSelfManagedPhoneAccounts");
                        throw e;
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        @Override
        public ParceledListSlice<PhoneAccountHandle> getOwnSelfManagedPhoneAccounts(
                String callingPackage, String callingFeatureId) {
            try {
                Log.startSession("TSI.gOSMPA", Log.getPackageAbbreviation(callingPackage));
                try {
                    enforceCallingPackage(callingPackage, "getOwnSelfManagedPhoneAccounts");
                } catch (SecurityException se) {
                    EventLog.writeEvent(0x534e4554, "231986341", Binder.getCallingUid(),
                            "getOwnSelfManagedPhoneAccounts: invalid calling package");
                    throw se;
                }
                if (!canReadMangeOwnCalls("Requires MANAGE_OWN_CALLS permission.")) {
                    throw new SecurityException("Requires MANAGE_OWN_CALLS permission.");
                }
                synchronized (mLock) {
                    final UserHandle callingUserHandle = Binder.getCallingUserHandle();
                    long token = Binder.clearCallingIdentity();
                    try {
                        return new ParceledListSlice<>(mPhoneAccountRegistrar
                                .getSelfManagedPhoneAccountsForPackage(callingPackage,
                                        callingUserHandle));
                    } catch (Exception e) {
                        Log.e(this, e,
                                "getSelfManagedPhoneAccountsForPackage");
                        throw e;
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        @Override
        public ParceledListSlice<PhoneAccountHandle> getPhoneAccountsSupportingScheme(
                String uriScheme, String callingPackage) {
            try {
                Log.startSession("TSI.gPASS", Log.getPackageAbbreviation(callingPackage));
                try {
                    enforceModifyPermission(
                            "getPhoneAccountsSupportingScheme requires MODIFY_PHONE_STATE");
                } catch (SecurityException e) {
                    EventLog.writeEvent(0x534e4554, "62347125", Binder.getCallingUid(),
                            "getPhoneAccountsSupportingScheme: " + callingPackage);
                    return ParceledListSlice.emptyList();
                }

                synchronized (mLock) {
                    final UserHandle callingUserHandle = Binder.getCallingUserHandle();
                    long token = Binder.clearCallingIdentity();
                    try {
                        return new ParceledListSlice<>(mPhoneAccountRegistrar
                            .getCallCapablePhoneAccounts(uriScheme, false,
                                    callingUserHandle, false));
                    } catch (Exception e) {
                        Log.e(this, e, "getPhoneAccountsSupportingScheme %s", uriScheme);
                        throw e;
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        @Override
        public ParceledListSlice<PhoneAccountHandle> getPhoneAccountsForPackage(
                String packageName) {
            //TODO: Deprecate this in S
            try {
                enforceCallingPackage(packageName, "getPhoneAccountsForPackage");
            } catch (SecurityException se1) {
                EventLog.writeEvent(0x534e4554, "153995334", Binder.getCallingUid(),
                        "getPhoneAccountsForPackage: invalid calling package");
                throw se1;
            }

            try {
                enforcePermission(READ_PRIVILEGED_PHONE_STATE);
            } catch (SecurityException se2) {
                EventLog.writeEvent(0x534e4554, "153995334", Binder.getCallingUid(),
                        "getPhoneAccountsForPackage: no permission");
                throw se2;
            }

            synchronized (mLock) {
                final UserHandle callingUserHandle = Binder.getCallingUserHandle();
                long token = Binder.clearCallingIdentity();
                try {
                    Log.startSession("TSI.gPAFP");
                    return new ParceledListSlice<>(mPhoneAccountRegistrar
                            .getAllPhoneAccountHandlesForPackage(callingUserHandle, packageName));
                } catch (Exception e) {
                    Log.e(this, e, "getPhoneAccountsForPackage %s", packageName);
                    throw e;
                } finally {
                    Binder.restoreCallingIdentity(token);
                    Log.endSession();
                }
            }
        }

        @Override
        public PhoneAccount getPhoneAccount(PhoneAccountHandle accountHandle,
                String callingPackage) {
            try {
                Log.startSession("TSI.gPA", Log.getPackageAbbreviation(callingPackage));
                try {
                    enforceCallingPackage(callingPackage, "getPhoneAccount");
                } catch (SecurityException se) {
                    EventLog.writeEvent(0x534e4554, "196406138", Binder.getCallingUid(),
                            "getPhoneAccount: invalid calling package");
                    throw se;
                }
                synchronized (mLock) {
                    final UserHandle callingUserHandle = Binder.getCallingUserHandle();
                    if (CompatChanges.isChangeEnabled(
                            TelecomManager.ENABLE_GET_PHONE_ACCOUNT_PERMISSION_PROTECTION,
                            callingPackage, Binder.getCallingUserHandle())) {
                        if (Binder.getCallingUid() != Process.SHELL_UID &&
                                !canGetPhoneAccount(callingPackage, accountHandle)) {
                            SecurityException e = new SecurityException(
                                    "getPhoneAccount API requires" +
                                            "READ_PHONE_NUMBERS");
                            Log.e(this, e, "getPhoneAccount %s", accountHandle);
                            throw e;
                        }
                    }
                    Set<String> permissions = computePermissionsForBoundPackage(
                            Set.of(MODIFY_PHONE_STATE), null);
                    long token = Binder.clearCallingIdentity();
                    try {
                        // In ideal case, we should not resolve the handle across profiles. But
                        // given the fact that profile's call is handled by its parent user's
                        // in-call UI, parent user's in call UI need to be able to get phone account
                        // from the profile's phone account handle.
                        PhoneAccount account = mPhoneAccountRegistrar
                                .getPhoneAccount(accountHandle, callingUserHandle,
                                        /* acrossProfiles */ true);
                        return maybeCleansePhoneAccount(account, permissions);
                    } catch (Exception e) {
                        Log.e(this, e, "getPhoneAccount %s", accountHandle);
                        mAnomalyReporter.reportAnomaly(GET_PHONE_ACCOUNT_ERROR_UUID,
                                GET_PHONE_ACCOUNT_ERROR_MSG);
                        throw e;
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        @Override
        public ParceledListSlice<PhoneAccount> getRegisteredPhoneAccounts(String callingPackage,
                String callingFeatureId) {
            try {
                Log.startSession("TSI.gRPA", Log.getPackageAbbreviation(callingPackage));
                try {
                    enforceCallingPackage(callingPackage, "getRegisteredPhoneAccounts");
                } catch (SecurityException se) {
                    EventLog.writeEvent(0x534e4554, "307609763", Binder.getCallingUid(),
                            "getRegisteredPhoneAccounts: invalid calling package");
                    throw se;
                }

                boolean hasCrossUserAccess = false;
                try {
                    enforceInAppCrossUserPermission();
                    hasCrossUserAccess = true;
                } catch (SecurityException e) {
                    // pass through
                }

                synchronized (mLock) {
                    final UserHandle callingUserHandle = Binder.getCallingUserHandle();
                    long token = Binder.clearCallingIdentity();
                    try {
                        return new ParceledListSlice<>(
                                mPhoneAccountRegistrar.getPhoneAccounts(
                                        0 /* capabilities */,
                                        0 /* excludedCapabilities */,
                                        null /* UriScheme */,
                                        callingPackage,
                                        true /* includeDisabledAccounts */,
                                        callingUserHandle,
                                        hasCrossUserAccess /* crossUserAccess */,
                                        false /* includeAll */));
                    } catch (Exception e) {
                        Log.e(this, e, "getRegisteredPhoneAccounts");
                        throw e;
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        @Override
        public int getAllPhoneAccountsCount() {
            try {
                Log.startSession("TSI.gAPAC");
                try {
                    enforceModifyPermission(
                            "getAllPhoneAccountsCount requires MODIFY_PHONE_STATE permission.");
                } catch (SecurityException e) {
                    EventLog.writeEvent(0x534e4554, "62347125", Binder.getCallingUid(),
                            "getAllPhoneAccountsCount");
                    throw e;
                }

                synchronized (mLock) {
                    try {
                        // This list is pre-filtered for the calling user.
                        return getAllPhoneAccounts().getList().size();
                    } catch (Exception e) {
                        Log.e(this, e, "getAllPhoneAccountsCount");
                        throw e;

                    }
                }
            } finally {
                Log.endSession();
            }
        }

        @Override
        public ParceledListSlice<PhoneAccount> getAllPhoneAccounts() {
            synchronized (mLock) {
                try {
                    Log.startSession("TSI.gAPA");
                    try {
                        enforceModifyPermission(
                                "getAllPhoneAccounts requires MODIFY_PHONE_STATE permission.");
                    } catch (SecurityException e) {
                        EventLog.writeEvent(0x534e4554, "62347125", Binder.getCallingUid(),
                                "getAllPhoneAccounts");
                        throw e;
                    }

                    final UserHandle callingUserHandle = Binder.getCallingUserHandle();
                    long token = Binder.clearCallingIdentity();
                    try {
                        return new ParceledListSlice<>(mPhoneAccountRegistrar
                                .getAllPhoneAccounts(callingUserHandle, false));
                    } catch (Exception e) {
                        Log.e(this, e, "getAllPhoneAccounts");
                        throw e;
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                } finally {
                    Log.endSession();
                }
            }
        }

        @Override
        public ParceledListSlice<PhoneAccountHandle> getAllPhoneAccountHandles() {
            try {
                Log.startSession("TSI.gAPAH");

                try {
                    enforceModifyPermission(
                            "getAllPhoneAccountHandles requires MODIFY_PHONE_STATE permission.");
                } catch (SecurityException e) {
                    EventLog.writeEvent(0x534e4554, "62347125", Binder.getCallingUid(),
                            "getAllPhoneAccountHandles");
                    throw e;
                }

                synchronized (mLock) {
                    final UserHandle callingUserHandle = Binder.getCallingUserHandle();
                    boolean crossUserAccess = hasInAppCrossUserPermission();
                    long token = Binder.clearCallingIdentity();
                    try {
                        return new ParceledListSlice<>(mPhoneAccountRegistrar
                                .getAllPhoneAccountHandles(callingUserHandle,
                                        crossUserAccess));
                    } catch (Exception e) {
                        Log.e(this, e, "getAllPhoneAccountsHandles");
                        throw e;
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        @Override
        public PhoneAccountHandle getSimCallManager(int subId, String callingPackage) {
            synchronized (mLock) {
                try {
                    Log.startSession("TSI.gSCM", Log.getPackageAbbreviation(callingPackage));
                    final int callingUid = Binder.getCallingUid();
                    final int user = UserHandle.getUserId(callingUid);
                    long token = Binder.clearCallingIdentity();
                    try {
                        if (user != ActivityManager.getCurrentUser()) {
                            enforceCrossUserPermission(callingUid);
                        }
                        return mPhoneAccountRegistrar.getSimCallManager(subId, UserHandle.of(user));
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                } catch (Exception e) {
                    Log.e(this, e, "getSimCallManager");
                    mAnomalyReporter.reportAnomaly(GET_SIM_MANAGER_ERROR_UUID,
                            GET_SIM_MANAGER_ERROR_MSG);
                    throw e;
                } finally {
                    Log.endSession();
                }
            }
        }

        @Override
        public PhoneAccountHandle getSimCallManagerForUser(int user, String callingPackage) {
            synchronized (mLock) {
                try {
                    Log.startSession("TSI.gSCMFU", Log.getPackageAbbreviation(callingPackage));
                    final int callingUid = Binder.getCallingUid();
                    if (user != ActivityManager.getCurrentUser()) {
                        enforceCrossUserPermission(callingUid);
                    }
                    long token = Binder.clearCallingIdentity();
                    try {
                        return mPhoneAccountRegistrar.getSimCallManager(UserHandle.of(user));
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                } catch (Exception e) {
                    Log.e(this, e, "getSimCallManager");
                    mAnomalyReporter.reportAnomaly(GET_SIM_MANAGER_FOR_USER_ERROR_UUID,
                            GET_SIM_MANAGER_FOR_USER_ERROR_MSG);
                    throw e;
                } finally {
                    Log.endSession();
                }
            }
        }

        @Override
        public void registerPhoneAccount(PhoneAccount account, String callingPackage) {
            try {
                Log.startSession("TSI.rPA", Log.getPackageAbbreviation(callingPackage));
                synchronized (mLock) {
                    try {
                        enforcePhoneAccountModificationForPackage(
                                account.getAccountHandle().getComponentName().getPackageName());
                        if (account.hasCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)) {
                            enforceRegisterSelfManaged();
                            if (account.hasCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER) ||
                                    account.hasCapabilities(
                                            PhoneAccount.CAPABILITY_CONNECTION_MANAGER) ||
                                    account.hasCapabilities(
                                            PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) {
                                throw new SecurityException("Self-managed ConnectionServices " +
                                        "cannot also be call capable, connection managers, or " +
                                        "SIM accounts.");
                            }

                            // For self-managed CS, the phone account registrar will override the
                            // label the user has set for the phone account.  This ensures the
                            // self-managed cs implementation can't spoof their app name.
                        }
                        if (account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) {
                            enforceRegisterSimSubscriptionPermission();
                        }
                        if (account.hasCapabilities(PhoneAccount.CAPABILITY_MULTI_USER)) {
                            enforceRegisterMultiUser();
                        }
                        // These capabilities are for SIM-based accounts only, so only the platform
                        // and carrier-designated SIM call manager can register accounts with these
                        // capabilities.
                        if (account.hasCapabilities(
                                PhoneAccount.CAPABILITY_SUPPORTS_VOICE_CALLING_INDICATIONS)
                                || account.hasCapabilities(
                                PhoneAccount.CAPABILITY_VOICE_CALLING_AVAILABLE)) {
                            enforceRegisterVoiceCallingIndicationCapabilities(account);
                        }
                        Bundle extras = account.getExtras();
                        if (extras != null
                                && extras.getBoolean(PhoneAccount.EXTRA_SKIP_CALL_FILTERING)) {
                            // System apps should be granted the MODIFY_PHONE_STATE permission.
                            enforceModifyPermission(
                                    "registerPhoneAccount requires MODIFY_PHONE_STATE permission.");
                            enforceRegisterSkipCallFiltering();
                        }
                        final int callingUid = Binder.getCallingUid();
                        if (callingUid != Process.SHELL_UID) {
                            enforceUserHandleMatchesCaller(account.getAccountHandle());
                        }

                        if (TextUtils.isEmpty(account.getGroupId())
                                && mContext.checkCallingOrSelfPermission(MODIFY_PHONE_STATE)
                                != PackageManager.PERMISSION_GRANTED) {
                            Log.w(this, "registerPhoneAccount - attempt to set a"
                                    + " group from a non-system caller.");
                            // Not permitted to set group, so null it out.
                            account = new PhoneAccount.Builder(account)
                                    .setGroupId(null)
                                    .build();
                        }

                        // Validate the profile boundary of the given image URI.
                        validateAccountIconUserBoundary(account.getIcon());

                        if (mTelephonyFeatureFlags.simultaneousCallingIndications()
                                && account.hasSimultaneousCallingRestriction()) {
                            validateSimultaneousCallingPackageNames(
                                    account.getAccountHandle().getComponentName().getPackageName(),
                                    account.getSimultaneousCallingRestriction());
                        }

                        final long token = Binder.clearCallingIdentity();
                        try {
                            Log.i(this, "registerPhoneAccount: account=%s",
                                    account);
                            mPhoneAccountRegistrar.registerPhoneAccount(account);
                        } finally {
                            Binder.restoreCallingIdentity(token);
                        }
                    } catch (Exception e) {
                        Log.e(this, e, "registerPhoneAccount %s", account);
                        mAnomalyReporter.reportAnomaly(REGISTER_PHONE_ACCOUNT_ERROR_UUID,
                                REGISTER_PHONE_ACCOUNT_ERROR_MSG);
                        throw e;
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        @Override
        public void unregisterPhoneAccount(PhoneAccountHandle accountHandle,
                String callingPackage) {
            synchronized (mLock) {
                try {
                    Log.startSession("TSI.uPA", Log.getPackageAbbreviation(callingPackage));
                    enforcePhoneAccountModificationForPackage(
                            accountHandle.getComponentName().getPackageName());
                    enforceUserHandleMatchesCaller(accountHandle);
                    final long token = Binder.clearCallingIdentity();
                    try {
                        mPhoneAccountRegistrar.unregisterPhoneAccount(accountHandle);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                } catch (Exception e) {
                    Log.e(this, e, "unregisterPhoneAccount %s", accountHandle);
                    throw e;
                } finally {
                    Log.endSession();
                }
            }
        }

        @Override
        public void clearAccounts(String packageName) {
            synchronized (mLock) {
                try {
                    Log.startSession("TSI.cA");
                    enforcePhoneAccountModificationForPackage(packageName);
                    mPhoneAccountRegistrar
                            .clearAccounts(packageName, Binder.getCallingUserHandle());
                } catch (Exception e) {
                    Log.e(this, e, "clearAccounts %s", packageName);
                    throw e;
                } finally {
                    Log.endSession();
                }
            }
        }

        /**
         * @see android.telecom.TelecomManager#isVoiceMailNumber
         */
        @Override
        public boolean isVoiceMailNumber(PhoneAccountHandle accountHandle, String number,
                String callingPackage, String callingFeatureId) {
            try {
                Log.startSession("TSI.iVMN", Log.getPackageAbbreviation(callingPackage));
                synchronized (mLock) {
                    if (!canReadPhoneState(callingPackage, callingFeatureId, "isVoiceMailNumber")) {
                        return false;
                    }
                    final UserHandle callingUserHandle = Binder.getCallingUserHandle();
                    if (!isPhoneAccountHandleVisibleToCallingUser(accountHandle,
                            callingUserHandle)) {
                        Log.d(this, "%s is not visible for the calling user [iVMN]", accountHandle);
                        return false;
                    }
                    long token = Binder.clearCallingIdentity();
                    try {
                        return mPhoneAccountRegistrar.isVoiceMailNumber(accountHandle, number);
                    } catch (Exception e) {
                        Log.e(this, e, "getSubscriptionIdForPhoneAccount");
                        throw e;
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see android.telecom.TelecomManager#getVoiceMailNumber
         */
        @Override
        public String getVoiceMailNumber(PhoneAccountHandle accountHandle, String callingPackage,
                String callingFeatureId) {
            try {
                Log.startSession("TSI.gVMN", Log.getPackageAbbreviation(callingPackage));
                if (!canReadPhoneState(callingPackage, callingFeatureId, "getVoiceMailNumber")) {
                    return null;
                }
                try {
                    final UserHandle callingUserHandle = Binder.getCallingUserHandle();
                    if (!isPhoneAccountHandleVisibleToCallingUser(accountHandle,
                            callingUserHandle)) {
                        Log.d(this, "%s is not visible for the calling user [gVMN]",
                                accountHandle);
                        return null;
                    }
                    int subId = mSubscriptionManagerAdapter.getDefaultVoiceSubId();
                    synchronized (mLock) {
                        if (accountHandle != null) {
                            subId = mPhoneAccountRegistrar
                                    .getSubscriptionIdForPhoneAccount(accountHandle);
                        }
                    }
                    return getTelephonyManager(subId).getVoiceMailNumber();
                } catch (Exception e) {
                    Log.e(this, e, "getSubscriptionIdForPhoneAccount");
                    throw e;
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see android.telecom.TelecomManager#getLine1Number
         */
        @Override
        public String getLine1Number(PhoneAccountHandle accountHandle, String callingPackage,
                String callingFeatureId) {
            try {
                Log.startSession("getL1N", Log.getPackageAbbreviation(callingPackage));
                if (!canReadPhoneNumbers(callingPackage, callingFeatureId, "getLine1Number")) {
                    return null;
                }

                final UserHandle callingUserHandle = Binder.getCallingUserHandle();
                if (!isPhoneAccountHandleVisibleToCallingUser(accountHandle,
                        callingUserHandle)) {
                    Log.d(this, "%s is not visible for the calling user [gL1N]", accountHandle);
                    return null;
                }

                long token = Binder.clearCallingIdentity();
                try {
                    int subId;
                    synchronized (mLock) {
                        subId = mPhoneAccountRegistrar.getSubscriptionIdForPhoneAccount(
                                accountHandle);
                    }
                    return getTelephonyManager(subId).getLine1Number();
                } catch (Exception e) {
                    Log.e(this, e, "getSubscriptionIdForPhoneAccount");
                    throw e;
                } finally {
                    Binder.restoreCallingIdentity(token);
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see android.telecom.TelecomManager#silenceRinger
         */
        @Override
        public void silenceRinger(String callingPackage) {
            try {
                Log.startSession("TSI.sR", Log.getPackageAbbreviation(callingPackage));
                synchronized (mLock) {
                    enforcePermissionOrPrivilegedDialer(MODIFY_PHONE_STATE, callingPackage);
                    UserHandle callingUserHandle = Binder.getCallingUserHandle();
                    boolean crossUserAccess = hasInAppCrossUserPermission();
                    long token = Binder.clearCallingIdentity();
                    try {
                        Log.i(this, "Silence Ringer requested by %s", callingPackage);
                        Set<UserHandle> userHandles = mCallsManager.getCallAudioManager().
                                silenceRingers(mContext, callingUserHandle,
                                        crossUserAccess);
                        mCallsManager.getInCallController().silenceRinger(userHandles);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see android.telecom.TelecomManager#getDefaultPhoneApp
         * @deprecated - Use {@link android.telecom.TelecomManager#getDefaultDialerPackage()}
         * instead.
         */
        @Override
        public ComponentName getDefaultPhoneApp() {
            try {
                Log.startSession("TSI.gDPA");
                return mDefaultDialerCache.getDialtactsSystemDialerComponent();
            } finally {
                Log.endSession();
            }
        }

        /**
         * @return the package name of the current user-selected default dialer. If no default
         * has been selected, the package name of the system dialer is returned. If
         * neither exists, then {@code null} is returned.
         * @see android.telecom.TelecomManager#getDefaultDialerPackage
         */
        @Override
        public String getDefaultDialerPackage(String callingPackage) {
            try {
                Log.startSession("TSI.gDDP", Log.getPackageAbbreviation(callingPackage));
                int callerUserId = UserHandle.getCallingUserId();
                final long token = Binder.clearCallingIdentity();
                try {
                    return mDefaultDialerCache.getDefaultDialerApplication(
                            callerUserId);
                } finally {
                    Binder.restoreCallingIdentity(token);
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * @param userId user id to get the default dialer package for
         * @return the package name of the current user-selected default dialer. If no default
         * has been selected, the package name of the system dialer is returned. If
         * neither exists, then {@code null} is returned.
         * @see android.telecom.TelecomManager#getDefaultDialerPackage
         */
        @Override
        public String getDefaultDialerPackageForUser(int userId) {
            try {
                Log.startSession("TSI.gDDPU");
                mContext.enforceCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE,
                        "READ_PRIVILEGED_PHONE_STATE permission required.");

                final long token = Binder.clearCallingIdentity();
                try {
                    return mDefaultDialerCache.getDefaultDialerApplication(userId);
                } finally {
                    Binder.restoreCallingIdentity(token);
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see android.telecom.TelecomManager#getSystemDialerPackage
         */
        @Override
        public String getSystemDialerPackage(String callingPackage) {
            try {
                Log.startSession("TSI.gSDP", Log.getPackageAbbreviation(callingPackage));
                return mDefaultDialerCache.getSystemDialerApplication();
            } finally {
                Log.endSession();
            }
        }

        public void setSystemDialer(ComponentName testComponentName) {
            try {
                Log.startSession("TSI.sSD");
                enforceModifyPermission();
                enforceShellOnly(Binder.getCallingUid(), "setSystemDialer");
                synchronized (mLock) {
                    long token = Binder.clearCallingIdentity();
                    try {
                        mDefaultDialerCache.setSystemDialerComponentName(testComponentName);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see android.telecom.TelecomManager#isInCall
         */
        @Override
        public boolean isInCall(String callingPackage, String callingFeatureId) {
            try {
                Log.startSession("TSI.iIC", Log.getPackageAbbreviation(callingPackage));
                if (!canReadPhoneState(callingPackage, callingFeatureId, "isInCall")) {
                    return false;
                }

                synchronized (mLock) {
                    return mCallsManager.hasOngoingCalls(Binder.getCallingUserHandle(),
                            hasInAppCrossUserPermission());
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see android.telecom.TelecomManager#hasManageOngoingCallsPermission
         */
        @Override
        public boolean hasManageOngoingCallsPermission(String callingPackage) {
            try {
                Log.startSession("TSI.hMOCP", Log.getPackageAbbreviation(callingPackage));
                enforceCallingPackage(callingPackage, "hasManageOngoingCallsPermission");
                return PermissionChecker.checkPermissionForDataDeliveryFromDataSource(
                        mContext, Manifest.permission.MANAGE_ONGOING_CALLS,
                        Binder.getCallingPid(),
                        new AttributionSource(mContext.getAttributionSource(),
                                new AttributionSource(Binder.getCallingUid(),
                                        callingPackage, /*attributionTag*/ null)),
                        "Checking whether the caller has MANAGE_ONGOING_CALLS permission")
                        == PermissionChecker.PERMISSION_GRANTED;
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see android.telecom.TelecomManager#isInManagedCall
         */
        @Override
        public boolean isInManagedCall(String callingPackage, String callingFeatureId) {
            try {
                Log.startSession("TSI.iIMC", Log.getPackageAbbreviation(callingPackage));
                if (!canReadPhoneState(callingPackage, callingFeatureId, "isInManagedCall")) {
                    throw new SecurityException("Only the default dialer or caller with " +
                            "READ_PHONE_STATE permission can use this method.");
                }

                synchronized (mLock) {
                    return mCallsManager.hasOngoingManagedCalls(Binder.getCallingUserHandle(),
                            hasInAppCrossUserPermission());
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see android.telecom.TelecomManager#isRinging
         */
        @Override
        public boolean isRinging(String callingPackage) {
            try {
                Log.startSession("TSI.iR");
                if (!isPrivilegedDialerCalling(callingPackage)) {
                    try {
                        enforceModifyPermission(
                                "isRinging requires MODIFY_PHONE_STATE permission.");
                    } catch (SecurityException e) {
                        EventLog.writeEvent(0x534e4554, "62347125", "isRinging: " + callingPackage);
                        throw e;
                    }
                }

                synchronized (mLock) {
                    // Note: We are explicitly checking the calls telecom is tracking rather than
                    // relying on mCallsManager#getCallState(). Since getCallState() relies on the
                    // current state as tracked by PhoneStateBroadcaster, any failure to properly
                    // track the current call state there could result in the wrong ringing state
                    // being reported by this API.
                    return mCallsManager.hasRingingOrSimulatedRingingCall();
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see TelecomManager#getCallState()
         * @deprecated this is only being kept due to an @UnsupportedAppUsage tag. Apps targeting
         * API 31+ must use {@link #getCallStateUsingPackage(String, String)} below.
         */
        @Deprecated
        @Override
        public int getCallState() {
            try {
                Log.startSession("TSI.getCallState(DEPRECATED)");
                if (CompatChanges.isChangeEnabled(
                        TelecomManager.ENABLE_GET_CALL_STATE_PERMISSION_PROTECTION,
                        Binder.getCallingUid())) {
                    // Do not allow this API to be called on API version 31+, it should only be
                    // called on old apps using this Binder call directly.
                    throw new SecurityException("This method can only be used for applications "
                            + "targeting API version 30 or less.");
                }
                synchronized (mLock) {
                    return mCallsManager.getCallState();
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see TelecomManager#getCallState()
         */
        @Override
        public int getCallStateUsingPackage(String callingPackage, String callingFeatureId) {
            try {
                Log.startSession("TSI.getCallStateUsingPackage");

                // ensure the callingPackage is not spoofed
                // skip check for privileged UIDs and throw SE if package does not match records
                if (!isPrivilegedUid(callingPackage)
                        && !callingUidMatchesPackageManagerRecords(callingPackage)) {
                    EventLog.writeEvent(0x534e4554, "236813210", Binder.getCallingUid(),
                            "getCallStateUsingPackage");
                    Log.i(this,
                            "getCallStateUsingPackage: packageName does not match records for "
                                    + "callingPackage=[%s], callingUid=[%d]",
                            callingPackage, Binder.getCallingUid());
                    throw new SecurityException(String.format("getCallStateUsingPackage: "
                                    + "enforceCallingPackage: callingPackage=[%s], callingUid=[%d]",
                            callingPackage, Binder.getCallingUid()));
                }

                if (CompatChanges.isChangeEnabled(
                        TelecomManager.ENABLE_GET_CALL_STATE_PERMISSION_PROTECTION, callingPackage,
                        Binder.getCallingUserHandle())) {
                    // Bypass canReadPhoneState check if this is being called from SHELL UID
                    if (Binder.getCallingUid() != Process.SHELL_UID && !canReadPhoneState(
                            callingPackage, callingFeatureId, "getCallState")) {
                        throw new SecurityException("getCallState API requires READ_PHONE_STATE"
                                + " for API version 31+");
                    }
                }
                synchronized (mLock) {
                    return mCallsManager.getCallState();
                }
            } finally {
                Log.endSession();
            }
        }

        private boolean isPrivilegedUid(String callingPackage) {
            int callingUid = Binder.getCallingUid();
            boolean isPrivileged = false;
            switch (callingUid) {
                case Process.ROOT_UID:
                case Process.SYSTEM_UID:
                case Process.SHELL_UID:
                    isPrivileged = true;
                    break;
            }
            return isPrivileged;
        }

        /**
         * @see android.telecom.TelecomManager#endCall
         */
        @Override
        public boolean endCall(String callingPackage) {
            try {
                Log.startSession("TSI.eC", Log.getPackageAbbreviation(callingPackage));
                synchronized (mLock) {
                    if (!enforceAnswerCallPermission(callingPackage, Binder.getCallingUid())) {
                        throw new SecurityException("requires ANSWER_PHONE_CALLS permission");
                    }

                    long token = Binder.clearCallingIdentity();
                    try {
                        return endCallInternal(callingPackage);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see android.telecom.TelecomManager#acceptRingingCall
         */
        @Override
        public void acceptRingingCall(String packageName) {
            try {
                Log.startSession("TSI.aRC", Log.getPackageAbbreviation(packageName));
                synchronized (mLock) {
                    if (!enforceAnswerCallPermission(packageName, Binder.getCallingUid())) return;

                    long token = Binder.clearCallingIdentity();
                    try {
                        acceptRingingCallInternal(DEFAULT_VIDEO_STATE, packageName);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see android.telecom.TelecomManager#acceptRingingCall(int)
         */
        @Override
        public void acceptRingingCallWithVideoState(String packageName, int videoState) {
            try {
                Log.startSession("TSI.aRCWVS", Log.getPackageAbbreviation(packageName));
                synchronized (mLock) {
                    if (!enforceAnswerCallPermission(packageName, Binder.getCallingUid())) return;

                    long token = Binder.clearCallingIdentity();
                    try {
                        acceptRingingCallInternal(videoState, packageName);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see android.telecom.TelecomManager#showInCallScreen
         */
        @Override
        public void showInCallScreen(boolean showDialpad, String callingPackage,
                String callingFeatureId) {
            try {
                Log.startSession("TSI.sICS", Log.getPackageAbbreviation(callingPackage));
                if (!canReadPhoneState(callingPackage, callingFeatureId, "showInCallScreen")) {
                    return;
                }

                synchronized (mLock) {

                    UserHandle callingUser = Binder.getCallingUserHandle();
                    long token = Binder.clearCallingIdentity();
                    try {
                        mCallsManager.getInCallController().bringToForeground(showDialpad, callingUser);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see android.telecom.TelecomManager#cancelMissedCallsNotification
         */
        @Override
        public void cancelMissedCallsNotification(String callingPackage) {
            try {
                Log.startSession("TSI.cMCN", Log.getPackageAbbreviation(callingPackage));
                synchronized (mLock) {
                    enforcePermissionOrPrivilegedDialer(MODIFY_PHONE_STATE, callingPackage);
                    UserHandle userHandle = Binder.getCallingUserHandle();
                    long token = Binder.clearCallingIdentity();
                    try {
                        mCallsManager.getMissedCallNotifier().clearMissedCalls(userHandle);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see android.telecom.TelecomManager#handleMmi
         */
        @Override
        public boolean handlePinMmi(String dialString, String callingPackage) {
            try {
                Log.startSession("TSI.hPM", Log.getPackageAbbreviation(callingPackage));
                enforcePermissionOrPrivilegedDialer(MODIFY_PHONE_STATE, callingPackage);

                // Switch identity so that TelephonyManager checks Telecom's permissions
                // instead.
                long token = Binder.clearCallingIdentity();
                boolean retval = false;
                try {
                    retval = getTelephonyManager(
                            SubscriptionManager.getDefaultVoiceSubscriptionId())
                            .handlePinMmi(dialString);
                } finally {
                    Binder.restoreCallingIdentity(token);
                }

                return retval;
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see android.telecom.TelecomManager#handleMmi
         */
        @Override
        public boolean handlePinMmiForPhoneAccount(PhoneAccountHandle accountHandle,
                String dialString, String callingPackage) {
            try {
                Log.startSession("TSI.hPMFPA", Log.getPackageAbbreviation(callingPackage));

                enforcePermissionOrPrivilegedDialer(MODIFY_PHONE_STATE, callingPackage);
                UserHandle callingUserHandle = Binder.getCallingUserHandle();
                synchronized (mLock) {
                    if (!isPhoneAccountHandleVisibleToCallingUser(accountHandle,
                            callingUserHandle)) {
                        Log.d(this, "%s is not visible for the calling user [hMMI]",
                                accountHandle);
                        return false;
                    }
                }

                // Switch identity so that TelephonyManager checks Telecom's permissions
                // instead.
                long token = Binder.clearCallingIdentity();
                boolean retval = false;
                int subId;
                try {
                    synchronized (mLock) {
                        subId = mPhoneAccountRegistrar.getSubscriptionIdForPhoneAccount(
                                accountHandle);
                    }
                    retval = getTelephonyManager(subId)
                            .handlePinMmiForSubscriber(subId, dialString);
                } finally {
                    Binder.restoreCallingIdentity(token);
                }
                return retval;
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see android.telecom.TelecomManager#getAdnUriForPhoneAccount
         */
        @Override
        public Uri getAdnUriForPhoneAccount(PhoneAccountHandle accountHandle,
                String callingPackage) {
            try {
                Log.startSession("TSI.aAUFPA", Log.getPackageAbbreviation(callingPackage));
                enforcePermissionOrPrivilegedDialer(MODIFY_PHONE_STATE, callingPackage);
                synchronized (mLock) {
                    if (!isPhoneAccountHandleVisibleToCallingUser(accountHandle,
                            Binder.getCallingUserHandle())) {
                        Log.d(this, "%s is not visible for the calling user [gA4PA]",
                                accountHandle);
                        return null;
                    }
                }
                // Switch identity so that TelephonyManager checks Telecom's permissions
                // instead.
                long token = Binder.clearCallingIdentity();
                String retval = "content://icc/adn/";
                try {
                    long subId = mPhoneAccountRegistrar
                            .getSubscriptionIdForPhoneAccount(accountHandle);
                    retval = retval + "subId/" + subId;
                } finally {
                    Binder.restoreCallingIdentity(token);
                }

                return Uri.parse(retval);
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see android.telecom.TelecomManager#isTtySupported
         */
        @Override
        public boolean isTtySupported(String callingPackage, String callingFeatureId) {
            try {
                Log.startSession("TSI.iTS", Log.getPackageAbbreviation(callingPackage));
                if (!canReadPhoneState(callingPackage, callingFeatureId, "isTtySupported")) {
                    throw new SecurityException("Only default dialer or an app with" +
                            "READ_PRIVILEGED_PHONE_STATE or READ_PHONE_STATE can call this api");
                }

                synchronized (mLock) {
                    return mCallsManager.isTtySupported();
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see android.telecom.TelecomManager#getCurrentTtyMode
         */
        @Override
        public int getCurrentTtyMode(String callingPackage, String callingFeatureId) {
            try {
                Log.startSession("TSI.gCTM", Log.getPackageAbbreviation(callingPackage));
                if (!canReadPhoneState(callingPackage, callingFeatureId, "getCurrentTtyMode")) {
                    return TelecomManager.TTY_MODE_OFF;
                }

                synchronized (mLock) {
                    return mCallsManager.getCurrentTtyMode();
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see android.telecom.TelecomManager#addNewIncomingCall
         */
        @Override
        public void addNewIncomingCall(PhoneAccountHandle phoneAccountHandle, Bundle extras,
                String callingPackage) {
            try {
                Log.startSession("TSI.aNIC", Log.getPackageAbbreviation(callingPackage));
                synchronized (mLock) {
                    Log.i(this, "Adding new incoming call with phoneAccountHandle %s",
                            phoneAccountHandle);
                    if (phoneAccountHandle != null &&
                            phoneAccountHandle.getComponentName() != null) {
                        if (isCallerSimCallManager(phoneAccountHandle)
                                && TelephonyUtil.isPstnComponentName(
                                phoneAccountHandle.getComponentName())) {
                            Log.v(this, "Allowing call manager to add incoming call with PSTN" +
                                    " handle");
                        } else {
                            mAppOpsManager.checkPackage(
                                    Binder.getCallingUid(),
                                    phoneAccountHandle.getComponentName().getPackageName());
                            // Make sure it doesn't cross the UserHandle boundary
                            enforceUserHandleMatchesCaller(phoneAccountHandle);
                            enforcePhoneAccountIsRegisteredEnabled(phoneAccountHandle,
                                    phoneAccountHandle.getUserHandle());
                            if (isSelfManagedConnectionService(phoneAccountHandle)) {
                                // Self-managed phone account, ensure it has MANAGE_OWN_CALLS.
                                mContext.enforceCallingOrSelfPermission(
                                        android.Manifest.permission.MANAGE_OWN_CALLS,
                                        "Self-managed phone accounts must have MANAGE_OWN_CALLS " +
                                                "permission.");

                                // Self-managed ConnectionServices can ONLY add new incoming calls
                                // using their own PhoneAccounts.  The checkPackage(..) app opps
                                // check above ensures this.
                            }
                        }
                        long token = Binder.clearCallingIdentity();
                        try {
                            Intent intent = new Intent(TelecomManager.ACTION_INCOMING_CALL);
                            intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                                    phoneAccountHandle);
                            intent.putExtra(CallIntentProcessor.KEY_IS_INCOMING_CALL, true);
                            if (extras != null) {
                                extras.setDefusable(true);
                                intent.putExtra(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, extras);
                            }
                            mCallIntentProcessorAdapter.processIncomingCallIntent(
                                    mCallsManager, intent);
                            if (mFeatureFlags.earlyBindingToIncallService()) {
                                PhoneAccount account =
                                        mPhoneAccountRegistrar.getPhoneAccountUnchecked(
                                                phoneAccountHandle);
                                Bundle accountExtra =
                                        account == null ? new Bundle() : account.getExtras();
                                PackageManager packageManager = mContext.getPackageManager();
                                // Start binding to InCallServices for wearable calls that do not
                                // require call filtering. This is to wake up default dialer earlier
                                // to mitigate InCallService binding latency.
                                if (packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
                                        && accountExtra != null && accountExtra.getBoolean(
                                        PhoneAccount.EXTRA_SKIP_CALL_FILTERING,
                                        false)) {
                                    mCallsManager.getInCallController().bindToServices(null, false);
                                }
                            }
                        } finally {
                            Binder.restoreCallingIdentity(token);
                        }
                    } else {
                        Log.w(this, "Null phoneAccountHandle. Ignoring request to add new" +
                                " incoming call");
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see android.telecom.TelecomManager#addNewIncomingConference
         */
        @Override
        public void addNewIncomingConference(PhoneAccountHandle phoneAccountHandle, Bundle extras,
                String callingPackage) {
            try {
                Log.startSession("TSI.aNIC", Log.getPackageAbbreviation(callingPackage));
                synchronized (mLock) {
                    Log.i(this, "Adding new incoming conference with phoneAccountHandle %s",
                            phoneAccountHandle);
                    if (phoneAccountHandle != null &&
                            phoneAccountHandle.getComponentName() != null) {
                        if (isCallerSimCallManager(phoneAccountHandle)
                                && TelephonyUtil.isPstnComponentName(
                                phoneAccountHandle.getComponentName())) {
                            Log.v(this, "Allowing call manager to add incoming conference" +
                                    " with PSTN handle");
                        } else {
                            mAppOpsManager.checkPackage(
                                    Binder.getCallingUid(),
                                    phoneAccountHandle.getComponentName().getPackageName());
                            // Make sure it doesn't cross the UserHandle boundary
                            enforceUserHandleMatchesCaller(phoneAccountHandle);
                            enforcePhoneAccountIsRegisteredEnabled(phoneAccountHandle,
                                    Binder.getCallingUserHandle());
                            if (isSelfManagedConnectionService(phoneAccountHandle)) {
                                throw new SecurityException(
                                        "Self-Managed ConnectionServices cannot add "
                                                + "adhoc conference calls");
                            }
                        }
                        long token = Binder.clearCallingIdentity();
                        try {
                            mCallsManager.processIncomingConference(
                                    phoneAccountHandle, extras);
                        } finally {
                            Binder.restoreCallingIdentity(token);
                        }
                    } else {
                        Log.w(this, "Null phoneAccountHandle. Ignoring request to add new" +
                                " incoming conference");
                    }
                }
            } finally {
                Log.endSession();
            }
        }


        /**
         * @see android.telecom.TelecomManager#acceptHandover
         */
        @Override
        public void acceptHandover(Uri srcAddr, int videoState, PhoneAccountHandle destAcct,
                String callingPackage) {
            try {
                Log.startSession("TSI.aHO", Log.getPackageAbbreviation(callingPackage));
                synchronized (mLock) {
                    Log.i(this, "acceptHandover; srcAddr=%s, videoState=%s, dest=%s",
                            Log.pii(srcAddr), VideoProfile.videoStateToString(videoState),
                            destAcct);

                    if (destAcct != null && destAcct.getComponentName() != null) {
                        mAppOpsManager.checkPackage(
                                Binder.getCallingUid(),
                                destAcct.getComponentName().getPackageName());
                        enforceUserHandleMatchesCaller(destAcct);
                        enforcePhoneAccountIsRegisteredEnabled(destAcct,
                                Binder.getCallingUserHandle());
                        if (isSelfManagedConnectionService(destAcct)) {
                            // Self-managed phone account, ensure it has MANAGE_OWN_CALLS.
                            mContext.enforceCallingOrSelfPermission(
                                    android.Manifest.permission.MANAGE_OWN_CALLS,
                                    "Self-managed phone accounts must have MANAGE_OWN_CALLS " +
                                            "permission.");
                        }
                        if (!enforceAcceptHandoverPermission(
                                destAcct.getComponentName().getPackageName(),
                                Binder.getCallingUid())) {
                            throw new SecurityException("App must be granted runtime "
                                    + "ACCEPT_HANDOVER permission.");
                        }

                        long token = Binder.clearCallingIdentity();
                        try {
                            mCallsManager.acceptHandover(srcAddr, videoState, destAcct);
                        } finally {
                            Binder.restoreCallingIdentity(token);
                        }
                    } else {
                        Log.w(this, "Null phoneAccountHandle. Ignoring request " +
                                "to handover the call");
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see android.telecom.TelecomManager#addNewUnknownCall
         */
        @Override
        public void addNewUnknownCall(PhoneAccountHandle phoneAccountHandle, Bundle extras) {
            try {
                Log.startSession("TSI.aNUC");
                try {
                    enforceModifyPermission(
                            "addNewUnknownCall requires MODIFY_PHONE_STATE permission.");
                } catch (SecurityException e) {
                    EventLog.writeEvent(0x534e4554, "62347125", Binder.getCallingUid(),
                            "addNewUnknownCall");
                    throw e;
                }

                synchronized (mLock) {
                    if (phoneAccountHandle != null &&
                            phoneAccountHandle.getComponentName() != null) {
                        mAppOpsManager.checkPackage(
                                Binder.getCallingUid(),
                                phoneAccountHandle.getComponentName().getPackageName());

                        // Make sure it doesn't cross the UserHandle boundary
                        enforceUserHandleMatchesCaller(phoneAccountHandle);
                        enforcePhoneAccountIsRegisteredEnabled(phoneAccountHandle,
                                Binder.getCallingUserHandle());
                        long token = Binder.clearCallingIdentity();

                        try {
                            Intent intent = new Intent(TelecomManager.ACTION_NEW_UNKNOWN_CALL);
                            if (extras != null) {
                                extras.setDefusable(true);
                                intent.putExtras(extras);
                            }
                            intent.putExtra(CallIntentProcessor.KEY_IS_UNKNOWN_CALL, true);
                            intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                                    phoneAccountHandle);
                            mCallIntentProcessorAdapter.processUnknownCallIntent(mCallsManager,
                                    intent);
                        } finally {
                            Binder.restoreCallingIdentity(token);
                        }
                    } else {
                        Log.i(this,
                                "Null phoneAccountHandle or not initiated by Telephony. " +
                                        "Ignoring request to add new unknown call.");
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see android.telecom.TelecomManager#startConference.
         */
        @Override
        public void startConference(List<Uri> participants, Bundle extras,
                String callingPackage) {
            try {
                Log.startSession("TSI.sC", Log.getPackageAbbreviation(callingPackage));
                if (!canCallPhone(callingPackage, "startConference")) {
                    throw new SecurityException("Package " + callingPackage + " is not allowed"
                            + " to start conference call");
                }

                long token = Binder.clearCallingIdentity();
                try {
                    mCallsManager.startConference(participants, extras, callingPackage,
                            Binder.getCallingUserHandle());
                } finally {
                    Binder.restoreCallingIdentity(token);
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see android.telecom.TelecomManager#placeCall
         */
        @Override
        public void placeCall(Uri handle, Bundle extras, String callingPackage,
                String callingFeatureId) {
            try {
                Log.startSession("TSI.pC", Log.getPackageAbbreviation(callingPackage));
                enforceCallingPackage(callingPackage, "placeCall");

                PhoneAccountHandle phoneAccountHandle = null;
                if (extras != null) {
                    phoneAccountHandle = extras.getParcelable(
                            TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE);
                    if (extras.containsKey(TelecomManager.EXTRA_IS_HANDOVER)) {
                        // This extra is for Telecom use only so should never be passed in.
                        extras.remove(TelecomManager.EXTRA_IS_HANDOVER);
                    }
                }
                ComponentName phoneAccountComponentName = phoneAccountHandle != null
                        ? phoneAccountHandle.getComponentName() : null;
                String phoneAccountPackageName = phoneAccountComponentName != null
                        ? phoneAccountComponentName.getPackageName() : null;
                boolean isCallerOwnerOfPhoneAccount =
                        callingPackage.equals(phoneAccountPackageName);
                boolean isSelfManagedPhoneAccount =
                        isSelfManagedConnectionService(phoneAccountHandle);
                // Ensure the app's calling package matches the PhoneAccount package name before
                // checking self-managed status so that we do not leak installed package
                // information.
                boolean isSelfManagedRequest = isCallerOwnerOfPhoneAccount &&
                        isSelfManagedPhoneAccount;
                if (isSelfManagedRequest) {
                    // The package name of the caller matches the package name of the
                    // PhoneAccountHandle, so ensure the app has MANAGE_OWN_CALLS permission if
                    // self-managed.
                    mContext.enforceCallingOrSelfPermission(
                            Manifest.permission.MANAGE_OWN_CALLS,
                            "Self-managed ConnectionServices require MANAGE_OWN_CALLS permission.");
                } else if (!canCallPhone(callingPackage, callingFeatureId,
                        "CALL_PHONE permission required to place calls.")) {
                    // not self-managed, so CALL_PHONE is required.
                    mAnomalyReporter.reportAnomaly(PLACE_CALL_SECURITY_EXCEPTION_ERROR_UUID,
                            PLACE_CALL_SECURITY_EXCEPTION_ERROR_MSG);
                    throw new SecurityException(
                            "CALL_PHONE permission required to place calls.");
                }

                // An application can not place a call with a self-managed PhoneAccount that
                // they do not own. If this is the case (and the app has CALL_PHONE permission),
                // remove the PhoneAccount from the request and place the call as if it was a
                // managed call request with no PhoneAccount specified.
                if (!isCallerOwnerOfPhoneAccount && isSelfManagedPhoneAccount) {
                    // extras can not be null if isSelfManagedPhoneAccount is true
                    extras.remove(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE);
                }

                // Note: we can still get here for the default/system dialer, even if the Phone
                // permission is turned off. This is because the default/system dialer is always
                // allowed to attempt to place a call (regardless of permission state), in case
                // it turns out to be an emergency call. If the permission is denied and the
                // call is being made to a non-emergency number, the call will be denied later on
                // by {@link UserCallIntentProcessor}.

                final boolean hasCallAppOp = mAppOpsManager.noteOp(AppOpsManager.OP_CALL_PHONE,
                        Binder.getCallingUid(), callingPackage, callingFeatureId, null)
                        == AppOpsManager.MODE_ALLOWED;

                final boolean hasCallPermission = mContext.checkCallingOrSelfPermission(CALL_PHONE)
                        == PackageManager.PERMISSION_GRANTED;
                // The Emergency Dialer has call privileged permission and uses this to place
                // emergency calls.  We ensure permission checks in
                // NewOutgoingCallIntentBroadcaster#process pass by sending this to
                // Telecom as an ACTION_CALL_PRIVILEGED intent (which makes sense since the
                // com.android.phone process has that permission).
                final boolean hasCallPrivilegedPermission = mContext.checkCallingOrSelfPermission(
                        CALL_PRIVILEGED) == PackageManager.PERMISSION_GRANTED;

                synchronized (mLock) {
                    final UserHandle userHandle = Binder.getCallingUserHandle();
                    long token = Binder.clearCallingIdentity();
                    try {
                        final Intent intent = new Intent(hasCallPrivilegedPermission ?
                                Intent.ACTION_CALL_PRIVILEGED : Intent.ACTION_CALL, handle);
                        if (extras != null) {
                            extras.setDefusable(true);
                            intent.putExtras(extras);
                        }
                        mUserCallIntentProcessorFactory.create(mContext, userHandle)
                                .processIntent(intent, callingPackage, isSelfManagedRequest,
                                        (hasCallAppOp && hasCallPermission)
                                                || hasCallPrivilegedPermission,
                                        true /* isLocalInvocation */);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see android.telecom.TelecomManager#enablePhoneAccount
         */
        @Override
        public boolean enablePhoneAccount(PhoneAccountHandle accountHandle, boolean isEnabled) {
            try {
                Log.startSession("TSI.ePA");
                enforceModifyPermission();
                synchronized (mLock) {
                    long token = Binder.clearCallingIdentity();
                    try {
                        // enable/disable phone account
                        return mPhoneAccountRegistrar.enablePhoneAccount(accountHandle, isEnabled);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        @Override
        public boolean setDefaultDialer(String packageName) {
            try {
                Log.startSession("TSI.sDD");
                enforcePermission(MODIFY_PHONE_STATE);
                enforcePermission(WRITE_SECURE_SETTINGS);
                synchronized (mLock) {
                    int callerUserId = UserHandle.getCallingUserId();
                    long token = Binder.clearCallingIdentity();
                    try {
                        return mDefaultDialerCache.setDefaultDialer(packageName,
                                callerUserId);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        @Override
        public void stopBlockSuppression() {
            try {
                Log.startSession("TSI.sBS");
                enforceModifyPermission();
                if (Binder.getCallingUid() != Process.SHELL_UID
                        && Binder.getCallingUid() != Process.ROOT_UID) {
                    throw new SecurityException("Shell-only API.");
                }
                synchronized (mLock) {
                    long token = Binder.clearCallingIdentity();
                    try {
                        BlockedNumberContract.BlockedNumbers.endBlockSuppression(mContext);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        @Override
        public TelecomAnalytics dumpCallAnalytics() {
            try {
                Log.startSession("TSI.dCA");
                enforcePermission(DUMP);
                return Analytics.dumpToParcelableAnalytics();
            } finally {
                Log.endSession();
            }
        }

        /**
         * Dumps the current state of the TelecomService.  Used when generating problem
         * reports.
         *
         * @param fd     The file descriptor.
         * @param writer The print writer to dump the state to.
         * @param args   Optional dump arguments.
         */
        @Override
        protected void dump(FileDescriptor fd, final PrintWriter writer, String[] args) {
            if (mContext.checkCallingOrSelfPermission(
                    android.Manifest.permission.DUMP)
                    != PackageManager.PERMISSION_GRANTED) {
                writer.println("Permission Denial: can't dump TelecomService " +
                        "from from pid=" + Binder.getCallingPid() + ", uid=" +
                        Binder.getCallingUid());
                return;
            }


            if (args != null && args.length > 0 && Analytics.ANALYTICS_DUMPSYS_ARG.equals(
                    args[0])) {
                long token = Binder.clearCallingIdentity();
                try {
                    Analytics.dumpToEncodedProto(mContext, writer, args);
                } finally {
                    Binder.restoreCallingIdentity(token);
                }
                return;
            }

            boolean isTimeLineView =
                    (args != null && args.length > 0 && TIME_LINE_ARG.equalsIgnoreCase(args[0]));

            final IndentingPrintWriter pw = new IndentingPrintWriter(writer, "  ");
            if (mCallsManager != null) {
                pw.println("CallsManager: ");
                pw.increaseIndent();
                mCallsManager.dump(pw, args);
                pw.decreaseIndent();

                pw.println("PhoneAccountRegistrar: ");
                pw.increaseIndent();
                mPhoneAccountRegistrar.dump(pw);
                pw.decreaseIndent();

                pw.println("Analytics:");
                pw.increaseIndent();
                Analytics.dump(pw);
                pw.decreaseIndent();

                pw.println("Flag Configurations: ");
                pw.increaseIndent();
                reflectAndPrintFlagConfigs(pw);
                pw.decreaseIndent();

                pw.println("TransactionManager: ");
                pw.increaseIndent();
                TransactionManager.getInstance().dump(pw);
                pw.decreaseIndent();
            }
            if (isTimeLineView) {
                Log.dumpEventsTimeline(pw);
            } else {
                Log.dumpEvents(pw);
            }
        }

        /**
         * Print all feature flag configurations that Telecom is using for debugging purposes.
         */
        private void reflectAndPrintFlagConfigs(IndentingPrintWriter pw) {

            try {
                // Look away, a forbidden technique (reflection) is being used to allow us to get
                // all flag configs without having to add them manually to this method.
                Method[] methods = FeatureFlags.class.getMethods();
                if (methods.length == 0) {
                    pw.println("NONE");
                    return;
                }
                for (Method m : methods) {
                    pw.println(m.getName() + "-> " + m.invoke(mFeatureFlags));
                }
            } catch (Exception e) {
                pw.println("[ERROR]");
            }

        }

        /**
         * @see android.telecom.TelecomManager#createManageBlockedNumbersIntent
         */
        @Override
        public Intent createManageBlockedNumbersIntent(String callingPackage) {
            try {
                Log.startSession("TSI.cMBNI", Log.getPackageAbbreviation(callingPackage));
                return BlockedNumbersActivity.getIntentForStartingActivity();
            } finally {
                Log.endSession();
            }
        }


        @Override
        public Intent createLaunchEmergencyDialerIntent(String number) {
            String packageName = mContext.getApplicationContext().getString(
                    com.android.internal.R.string.config_emergency_dialer_package);
            Intent intent = new Intent(Intent.ACTION_DIAL_EMERGENCY)
                    .setPackage(packageName);
            ResolveInfo resolveInfo = mPackageManager.resolveActivity(intent, 0 /* flags*/);
            if (resolveInfo == null) {
                // No matching activity from config, fallback to default platform implementation
                intent.setPackage(null);
            }
            if (!TextUtils.isEmpty(number) && TextUtils.isDigitsOnly(number)) {
                intent.setData(Uri.parse("tel:" + number));
            }
            return intent;
        }

        /**
         * @see android.telecom.TelecomManager#isIncomingCallPermitted(PhoneAccountHandle)
         */
        @Override
        public boolean isIncomingCallPermitted(PhoneAccountHandle phoneAccountHandle,
                String callingPackage) {
            Log.startSession("TSI.iICP", Log.getPackageAbbreviation(callingPackage));
            try {
                enforceCallingPackage(callingPackage, "isIncomingCallPermitted");
                enforcePhoneAccountHandleMatchesCaller(phoneAccountHandle, callingPackage);
                enforcePermission(android.Manifest.permission.MANAGE_OWN_CALLS);
                enforceUserHandleMatchesCaller(phoneAccountHandle);
                synchronized (mLock) {
                    long token = Binder.clearCallingIdentity();
                    try {
                        return mCallsManager.isIncomingCallPermitted(phoneAccountHandle);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * @see android.telecom.TelecomManager#isOutgoingCallPermitted(PhoneAccountHandle)
         */
        @Override
        public boolean isOutgoingCallPermitted(PhoneAccountHandle phoneAccountHandle,
                String callingPackage) {
            Log.startSession("TSI.iOCP", Log.getPackageAbbreviation(callingPackage));
            try {
                enforceCallingPackage(callingPackage, "isOutgoingCallPermitted");
                enforcePhoneAccountHandleMatchesCaller(phoneAccountHandle, callingPackage);
                enforcePermission(android.Manifest.permission.MANAGE_OWN_CALLS);
                enforceUserHandleMatchesCaller(phoneAccountHandle);
                synchronized (mLock) {
                    long token = Binder.clearCallingIdentity();
                    try {
                        return mCallsManager.isOutgoingCallPermitted(phoneAccountHandle);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * Blocks until all Telecom handlers have completed their current work.
         *
         * See {@link com.android.commands.telecom.Telecom}.
         */
        @Override
        public void waitOnHandlers() {
            try {
                Log.startSession("TSI.wOH");
                enforceModifyPermission();
                synchronized (mLock) {
                    long token = Binder.clearCallingIdentity();
                    try {
                        Log.i(this, "waitOnHandlers");
                        mCallsManager.waitOnHandlers();
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        @Override
        public void setTestEmergencyPhoneAccountPackageNameFilter(String packageName) {
            try {
                Log.startSession("TSI.sTPAPNF");
                enforceModifyPermission();
                enforceShellOnly(Binder.getCallingUid(),
                        "setTestEmergencyPhoneAccountPackageNameFilter");
                synchronized (mLock) {
                    long token = Binder.clearCallingIdentity();
                    try {
                        mPhoneAccountRegistrar.setTestPhoneAccountPackageNameFilter(packageName);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * See {@link TelecomManager#isInEmergencyCall()}
         */
        @Override
        public boolean isInEmergencyCall() {
            try {
                Log.startSession("TSI.iIEC");
                enforceModifyPermission();
                synchronized (mLock) {
                    long token = Binder.clearCallingIdentity();
                    try {
                        boolean isInEmergencyCall = mCallsManager.isInEmergencyCall();
                        Log.i(this, "isInEmergencyCall: %b", isInEmergencyCall);
                        return isInEmergencyCall;
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * See {@link TelecomManager#handleCallIntent(Intent, String)}
         */
        @Override
        public void handleCallIntent(Intent intent, String callingPackage) {
            try {
                Log.startSession("TSI.hCI");
                synchronized (mLock) {
                    mContext.enforceCallingOrSelfPermission(PERMISSION_HANDLE_CALL_INTENT,
                            "handleCallIntent is for internal use only.");

                    long token = Binder.clearCallingIdentity();
                    try {
                        Log.i(this, "handleCallIntent: handling call intent");
                        mCallIntentProcessorAdapter.processOutgoingCallIntent(mContext,
                                mCallsManager, intent, callingPackage, mFeatureFlags);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * A method intended for use in testing to clean up any calls that get stuck in the
         * {@link CallState#DISCONNECTED} or {@link CallState#DISCONNECTING} states. Stuck
         * calls
         * during CTS cause cascading failures, so if the CTS test detects such a state, it
         * should
         * call this method via a shell command to clean up before moving on to the next
         * test.
         * Also cleans up any pending futures related to
         * {@link android.telecom.CallDiagnosticService}s.
         */
        @Override
        public void cleanupStuckCalls() {
            Log.startSession("TCI.cSC");
            try {
                synchronized (mLock) {
                    enforceShellOnly(Binder.getCallingUid(), "cleanupStuckCalls");
                    long token = Binder.clearCallingIdentity();
                    try {
                        Set<UserHandle> userHandles = new HashSet<>();
                        for (Call call : mCallsManager.getCalls()) {
                            call.cleanup();
                            if (call.getState() == CallState.DISCONNECTED
                                    || call.getState() == CallState.DISCONNECTING) {
                                mCallsManager.markCallAsRemoved(call);
                            }
                            userHandles.add(call.getAssociatedUser());
                        }
                        for (UserHandle userHandle : userHandles) {
                            mCallsManager.getInCallController().unbindFromServices(userHandle);
                        }
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * A method intended for test to clean up orphan {@link PhoneAccount}. An orphan
         * {@link PhoneAccount} is a phone account belongs to an invalid {@link UserHandle}
         * or a
         * deleted package.
         *
         * @return the number of orphan {@code PhoneAccount} deleted.
         */
        @Override
        public int cleanupOrphanPhoneAccounts() {
            Log.startSession("TCI.cOPA");
            try {
                synchronized (mLock) {
                    enforceShellOnly(Binder.getCallingUid(), "cleanupOrphanPhoneAccounts");
                    long token = Binder.clearCallingIdentity();
                    try {
                        return mPhoneAccountRegistrar.cleanupOrphanedPhoneAccounts();
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * A method intended for use in testing to query whether a particular non-ui inCallService
         * is bound in a call.
         * @param packageName of the service to query.
         * @return whether it is bound or not.
         */
        @Override
        public boolean isNonUiInCallServiceBound(String packageName) {
            Log.startSession("TCI.iNUICSB");
            try {
                synchronized (mLock) {
                    enforceShellOnly(Binder.getCallingUid(), "isNonUiInCallServiceBound");
                    if (!(mContext.checkCallingOrSelfPermission(READ_PHONE_STATE)
                            == PackageManager.PERMISSION_GRANTED) ||
                            !(mContext.checkCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE)
                                    == PackageManager.PERMISSION_GRANTED)) {
                        throw new SecurityException("isNonUiInCallServiceBound requires the"
                                + " READ_PHONE_STATE or READ_PRIVILEGED_PHONE_STATE permission");
                    }
                    long token = Binder.clearCallingIdentity();
                    try {
                        return mCallsManager
                                .getInCallController()
                                .isNonUiInCallServiceBound(packageName);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * A method intended for use in testing to reset car mode at all priorities.
         *
         * Runs during setup to avoid cascading failures from failing car mode CTS.
         */
        @Override
        public void resetCarMode() {
            Log.startSession("TCI.rCM");
            try {
                synchronized (mLock) {
                    enforceShellOnly(Binder.getCallingUid(), "resetCarMode");
                    long token = Binder.clearCallingIdentity();
                    try {
                        UiModeManager uiModeManager =
                                mContext.getSystemService(UiModeManager.class);
                        uiModeManager.disableCarMode(UiModeManager.DISABLE_CAR_MODE_ALL_PRIORITIES);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        @Override
        public void setTestDefaultCallRedirectionApp(String packageName) {
            try {
                Log.startSession("TSI.sTDCRA");
                enforceModifyPermission();
                if (!Build.IS_USERDEBUG) {
                    throw new SecurityException("Test-only API.");
                }
                synchronized (mLock) {
                    long token = Binder.clearCallingIdentity();
                    try {
                        mCallsManager.getRoleManagerAdapter().setTestDefaultCallRedirectionApp(
                                packageName);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        @Override
        public void setTestDefaultCallScreeningApp(String packageName) {
            try {
                Log.startSession("TSI.sTDCSA");
                enforceModifyPermission();
                if (!Build.IS_USERDEBUG) {
                    throw new SecurityException("Test-only API.");
                }
                synchronized (mLock) {
                    long token = Binder.clearCallingIdentity();
                    try {
                        mCallsManager.getRoleManagerAdapter().setTestDefaultCallScreeningApp(
                                packageName);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        @Override
        public void addOrRemoveTestCallCompanionApp(String packageName, boolean isAdded) {
            try {
                Log.startSession("TSI.aORTCCA");
                enforceModifyPermission();
                enforceShellOnly(Binder.getCallingUid(), "addOrRemoveTestCallCompanionApp");
                synchronized (mLock) {
                    long token = Binder.clearCallingIdentity();
                    try {
                        mCallsManager.getRoleManagerAdapter().addOrRemoveTestCallCompanionApp(
                                packageName, isAdded);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        @Override
        public void requestLogMark(String message) {
            try {
                Log.startSession("TSI.rLM");
                enforceShellOnly(Binder.getCallingUid(), "requestLogMark is for shell only");
                synchronized (mLock) {
                    long token = Binder.clearCallingIdentity();
                    try {
                        mCallsManager.requestLogMark(message);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        @Override
        public void setTestPhoneAcctSuggestionComponent(String flattenedComponentName) {
            try {
                Log.startSession("TSI.sPASA");
                enforceModifyPermission();
                if (Binder.getCallingUid() != Process.SHELL_UID
                        && Binder.getCallingUid() != Process.ROOT_UID) {
                    throw new SecurityException("Shell-only API.");
                }
                synchronized (mLock) {
                    PhoneAccountSuggestionHelper.setOverrideServiceName(flattenedComponentName);
                }
            } finally {
                Log.endSession();
            }
        }

        @Override
        public void setTestDefaultDialer(String packageName) {
            try {
                Log.startSession("TSI.sTDD");
                enforceModifyPermission();
                if (Binder.getCallingUid() != Process.SHELL_UID
                        && Binder.getCallingUid() != Process.ROOT_UID) {
                    throw new SecurityException("Shell-only API.");
                }
                synchronized (mLock) {
                    long token = Binder.clearCallingIdentity();
                    try {
                        mCallsManager.getRoleManagerAdapter().setTestDefaultDialer(packageName);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        @Override
        public void setTestCallDiagnosticService(String packageName) {
            try {
                Log.startSession("TSI.sTCDS");
                enforceModifyPermission();
                enforceShellOnly(Binder.getCallingUid(), "setTestCallDiagnosticService is for "
                        + "shell use only.");
                synchronized (mLock) {
                    long token = Binder.clearCallingIdentity();
                    try {
                        CallDiagnosticServiceController controller =
                                mCallsManager.getCallDiagnosticServiceController();
                        if (controller != null) {
                            controller.setTestCallDiagnosticService(packageName);
                        }
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        /**
         * Determines whether there are any ongoing {@link PhoneAccount#CAPABILITY_SELF_MANAGED}
         * calls for a given {@code packageName} and {@code userHandle}.
         *
         * @param packageName    the package name of the app to check calls for.
         * @param userHandle     the user handle on which to check for calls.
         * @param callingPackage The caller's package name.
         * @param detectForAllUsers indicates if calls should be detected across all users. If the
         *                          caller does not have the ability to interact across users, get
         *                          managed calls for the caller instead.
         * @return {@code true} if there are ongoing calls, {@code false} otherwise.
         */
        @Override
        public boolean isInSelfManagedCall(String packageName, UserHandle userHandle,
                String callingPackage, boolean detectForAllUsers) {
            try {
                mContext.enforceCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE,
                        "READ_PRIVILEGED_PHONE_STATE required.");
                // Ensure that the caller has the INTERACT_ACROSS_USERS permission if it's trying
                // to access calls that don't belong to it.
                if (detectForAllUsers || (userHandle != null
                        && !Binder.getCallingUserHandle().equals(userHandle))) {
                    enforceInAppCrossUserPermission();
                } else {
                    // If INTERACT_ACROSS_USERS doesn't need to be enforced, ensure that the user
                    // being checked is the caller.
                    userHandle = Binder.getCallingUserHandle();
                }

                Log.startSession("TSI.iISMC", Log.getPackageAbbreviation(callingPackage));
                synchronized (mLock) {
                    long token = Binder.clearCallingIdentity();
                    try {
                        return mCallsManager.isInSelfManagedCallCrossUsers(
                                packageName, userHandle, detectForAllUsers);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            } finally {
                Log.endSession();
            }
        }
    };

    private boolean enforceCallStreamingPermission(String packageName, PhoneAccountHandle handle,
            int uid) {
        // TODO: implement this permission check (make sure the calling package is the d2di package)
        PhoneAccount account = mPhoneAccountRegistrar.getPhoneAccount(handle,
                UserHandle.getUserHandleForUid(uid));
        if (account == null
                || !account.hasCapabilities(PhoneAccount.CAPABILITY_SUPPORTS_CALL_STREAMING)) {
            throw new SecurityException(
                    "The phone account handle in requesting can't support call streaming: "
                            + handle);
        }
        return true;
    }

    /**
     * @return whether to return early without doing the action/throwing
     * @throws SecurityException same as {@link Context#enforceCallingOrSelfPermission}
     */
    private boolean enforceAnswerCallPermission(String packageName, int uid) {
        try {
            enforceModifyPermission();
        } catch (SecurityException e) {
            final String permission = Manifest.permission.ANSWER_PHONE_CALLS;
            enforcePermission(permission);

            final int opCode = AppOpsManager.permissionToOpCode(permission);
            if (opCode != AppOpsManager.OP_NONE
                    && mAppOpsManager.checkOp(opCode, uid, packageName)
                        != AppOpsManager.MODE_ALLOWED) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return {@code true} if the app has the handover permission and has received runtime
     * permission to perform that operation, {@code false}.
     * @throws SecurityException same as {@link Context#enforceCallingOrSelfPermission}
     */
    private boolean enforceAcceptHandoverPermission(String packageName, int uid) {
        mContext.enforceCallingOrSelfPermission(Manifest.permission.ACCEPT_HANDOVER,
                "App requires ACCEPT_HANDOVER permission to accept handovers.");

        final int opCode = AppOpsManager.permissionToOpCode(Manifest.permission.ACCEPT_HANDOVER);
        if (opCode != AppOpsManager.OP_ACCEPT_HANDOVER || (
                mAppOpsManager.checkOp(opCode, uid, packageName)
                        != AppOpsManager.MODE_ALLOWED)) {
            return false;
        }
        return true;
    }

    private Context mContext;
    private AppOpsManager mAppOpsManager;
    private PackageManager mPackageManager;
    private CallsManager mCallsManager;
    private final PhoneAccountRegistrar mPhoneAccountRegistrar;
    private final CallIntentProcessor.Adapter mCallIntentProcessorAdapter;
    private final UserCallIntentProcessorFactory mUserCallIntentProcessorFactory;
    private final DefaultDialerCache mDefaultDialerCache;
    private final SubscriptionManagerAdapter mSubscriptionManagerAdapter;
    private final SettingsSecureAdapter mSettingsSecureAdapter;
    private final TelecomSystem.SyncRoot mLock;
    private TransactionManager mTransactionManager;
    private final TransactionalServiceRepository mTransactionalServiceRepository;
    private final FeatureFlags mFeatureFlags;
    private final com.android.internal.telephony.flags.FeatureFlags mTelephonyFeatureFlags;


    public TelecomServiceImpl(
            Context context,
            CallsManager callsManager,
            PhoneAccountRegistrar phoneAccountRegistrar,
            CallIntentProcessor.Adapter callIntentProcessorAdapter,
            UserCallIntentProcessorFactory userCallIntentProcessorFactory,
            DefaultDialerCache defaultDialerCache,
            SubscriptionManagerAdapter subscriptionManagerAdapter,
            SettingsSecureAdapter settingsSecureAdapter,
            FeatureFlags featureFlags,
            com.android.internal.telephony.flags.FeatureFlags telephonyFeatureFlags,
            TelecomSystem.SyncRoot lock) {
        mContext = context;
        mAppOpsManager = (AppOpsManager) mContext.getSystemService(Context.APP_OPS_SERVICE);

        mPackageManager = mContext.getPackageManager();

        mCallsManager = callsManager;
        mFeatureFlags = featureFlags;
        if (telephonyFeatureFlags != null) {
            mTelephonyFeatureFlags = telephonyFeatureFlags;
        } else {
            mTelephonyFeatureFlags =
                    new com.android.internal.telephony.flags.FeatureFlagsImpl();
        }
        mLock = lock;
        mPhoneAccountRegistrar = phoneAccountRegistrar;
        mUserCallIntentProcessorFactory = userCallIntentProcessorFactory;
        mDefaultDialerCache = defaultDialerCache;
        mCallIntentProcessorAdapter = callIntentProcessorAdapter;
        mSubscriptionManagerAdapter = subscriptionManagerAdapter;
        mSettingsSecureAdapter = settingsSecureAdapter;

        mDefaultDialerCache.observeDefaultDialerApplication(mContext.getMainExecutor(), userId -> {
            String defaultDialer = mDefaultDialerCache.getDefaultDialerApplication(userId);
            if (defaultDialer == null) {
                // We are replacing the dialer, just wait for the upcoming callback.
                return;
            }
            final Intent intent = new Intent(TelecomManager.ACTION_DEFAULT_DIALER_CHANGED)
                    .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                            defaultDialer);
            mContext.sendBroadcastAsUser(intent, UserHandle.of(userId));
        });

        mTransactionManager = TransactionManager.getInstance();
        mTransactionalServiceRepository = new TransactionalServiceRepository();
    }

    @VisibleForTesting
    public void setTransactionManager(TransactionManager transactionManager){
        mTransactionManager = transactionManager;
    }

    public ITelecomService.Stub getBinder() {
        return mBinderImpl;
    }

    //
    // Supporting methods for the ITelecomService interface implementation.
    //

    private boolean isPhoneAccountHandleVisibleToCallingUser(
            PhoneAccountHandle phoneAccountUserHandle, UserHandle callingUser) {
        synchronized (mLock) {
            return mPhoneAccountRegistrar.getPhoneAccount(phoneAccountUserHandle, callingUser)
                    != null;
        }
    }

    private boolean isCallerSystemApp() {
        int uid = Binder.getCallingUid();
        String[] packages = mPackageManager.getPackagesForUid(uid);
        for (String packageName : packages) {
            if (isPackageSystemApp(packageName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPackageSystemApp(String packageName) {
        try {
            ApplicationInfo applicationInfo = mPackageManager.getApplicationInfo(packageName,
                    PackageManager.GET_META_DATA);
            if ((applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                return true;
            }
        } catch (PackageManager.NameNotFoundException e) {
        }
        return false;
    }

    private void acceptRingingCallInternal(int videoState, String packageName) {
        Call call = mCallsManager.getFirstCallWithState(CallState.RINGING,
                CallState.SIMULATED_RINGING);
        if (call != null) {
            if (call.isSelfManaged()) {
                Log.addEvent(call, LogUtils.Events.REQUEST_ACCEPT,
                        "self-mgd accept ignored from " + packageName);
                return;
            }

            if (videoState == DEFAULT_VIDEO_STATE || !isValidAcceptVideoState(videoState)) {
                videoState = call.getVideoState();
            }
            mCallsManager.answerCall(call, videoState);
        }
    }

    private boolean endCallInternal(String callingPackage) {
        // Always operate on the foreground call if one exists, otherwise get the first call in
        // priority order by call-state.
        Call call = mCallsManager.getForegroundCall();
        if (call == null) {
            call = mCallsManager.getFirstCallWithState(
                    CallState.ACTIVE,
                    CallState.DIALING,
                    CallState.PULLING,
                    CallState.RINGING,
                    CallState.SIMULATED_RINGING,
                    CallState.ON_HOLD);
        }

        if (call != null) {
            if (call.isEmergencyCall()) {
                android.util.EventLog.writeEvent(0x534e4554, "132438333", -1, "");
                return false;
            }

            if (call.isSelfManaged()) {
                Log.addEvent(call, LogUtils.Events.REQUEST_DISCONNECT,
                        "self-mgd disconnect ignored from " + callingPackage);
                return false;
            }

            if (call.getState() == CallState.RINGING
                    || call.getState() == CallState.SIMULATED_RINGING) {
                mCallsManager.rejectCall(call, false /* rejectWithMessage */, null);
            } else {
                mCallsManager.disconnectCall(call);
            }
            return true;
        }

        return false;
    }

    // Enforce that the PhoneAccountHandle being passed in is both registered to the current user
    // and enabled.
    private void enforcePhoneAccountIsRegisteredEnabled(PhoneAccountHandle phoneAccountHandle,
                                                        UserHandle callingUserHandle) {
        PhoneAccount phoneAccount = mPhoneAccountRegistrar.getPhoneAccount(phoneAccountHandle,
                callingUserHandle);
        if(phoneAccount == null) {
            EventLog.writeEvent(0x534e4554, "26864502", Binder.getCallingUid(), "R");
            throw new SecurityException("This PhoneAccountHandle is not registered for this user!");
        }
        if(!phoneAccount.isEnabled()) {
            EventLog.writeEvent(0x534e4554, "26864502", Binder.getCallingUid(), "E");
            throw new SecurityException("This PhoneAccountHandle is not enabled for this user!");
        }
    }

    // Enforce that the PhoneAccountHandle is tied to a self-managed package and not managed (aka
    // sim calling, etc.)
    private void enforcePhoneAccountIsNotManaged(PhoneAccountHandle phoneAccountHandle) {
        PhoneAccount phoneAccount = mPhoneAccountRegistrar.getPhoneAccount(phoneAccountHandle,
                phoneAccountHandle.getUserHandle());
        if (phoneAccount == null) {
            throw new IllegalArgumentException("enforcePhoneAccountIsNotManaged:"
                    + " phoneAccount is null");
        }
        if (phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) {
            throw new IllegalArgumentException("enforcePhoneAccountIsNotManaged:"
                    + " CAPABILITY_SIM_SUBSCRIPTION is not allowed");
        }
        if (phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)) {
            throw new IllegalArgumentException("enforcePhoneAccountIsNotManaged:"
                    + " CAPABILITY_CALL_PROVIDER is not allowed");
        }
        if (phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_CONNECTION_MANAGER)) {
            throw new IllegalArgumentException("enforcePhoneAccountIsNotManaged:"
                    + " CAPABILITY_CONNECTION_MANAGER is not allowed");
        }
    }

    private void enforcePhoneAccountModificationForPackage(String packageName) {
        // TODO: Use a new telecomm permission for this instead of reusing modify.

        int result = mContext.checkCallingOrSelfPermission(MODIFY_PHONE_STATE);

        // Callers with MODIFY_PHONE_STATE can use the PhoneAccount mechanism to implement
        // built-in behavior even when PhoneAccounts are not exposed as a third-part API. They
        // may also modify PhoneAccounts on behalf of any 'packageName'.

        if (result != PackageManager.PERMISSION_GRANTED) {
            // Other callers are only allowed to modify PhoneAccounts if the relevant system
            // feature is enabled ...
            enforceTelecomFeature();
            // ... and the PhoneAccounts they refer to are for their own package.
            enforceCallingPackage(packageName, "enforcePhoneAccountModificationForPackage");
        }
    }

    private void enforcePermissionOrPrivilegedDialer(String permission, String packageName) {
        if (!isPrivilegedDialerCalling(packageName)) {
            try {
                enforcePermission(permission);
            } catch (SecurityException e) {
                Log.e(this, e, "Caller must be the default or system dialer, or have the permission"
                        + " %s to perform this operation.", permission);
                throw e;
            }
        }
    }

    private void enforceCallingPackage(String packageName, String message) {
        int callingUid = Binder.getCallingUid();

        if (callingUid != Process.ROOT_UID &&
                !callingUidMatchesPackageManagerRecords(packageName)) {
            throw new SecurityException(message + ": Package " + packageName
                    + " does not belong to " + callingUid);
        }
    }

    /**
     * helper method that compares the binder_uid to what the packageManager_uid reports for the
     * passed in packageName.
     *
     * returns true if the binder_uid matches the packageManager_uid records
     */
    private boolean callingUidMatchesPackageManagerRecords(String packageName) {
        int packageUid = -1;
        int callingUid = Binder.getCallingUid();
        PackageManager pm;
        long token = Binder.clearCallingIdentity();
        try{
            pm = mContext.createContextAsUser(
                    UserHandle.getUserHandleForUid(callingUid), 0).getPackageManager();
        }
        catch (Exception e){
            Log.i(this, "callingUidMatchesPackageManagerRecords:"
                            + " createContextAsUser hit exception=[%s]", e.toString());
            return false;
        } finally {
            Binder.restoreCallingIdentity(token);
        }
        if (pm != null) {
            try {
                packageUid = pm.getPackageUid(packageName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                // packageUid is -1.
            }
        }

        if (packageUid != callingUid) {
            Log.i(this, "callingUidMatchesPackageManagerRecords: uid mismatch found for"
                            + "packageName=[%s]. packageManager reports packageUid=[%d] but "
                    + "binder reports callingUid=[%d]", packageName, packageUid, callingUid);
        }

        return packageUid == callingUid;
    }

    /**
     * Note: This method should be called BEFORE clearing the binder identity.
     *
     * @param permissionsToValidate      set of permissions that should be checked
     * @param alreadyComputedPermissions a list of permissions that were already checked
     * @return all the permissions that
     */
    private Set<String> computePermissionsForBoundPackage(
            Set<String> permissionsToValidate,
            Set<String> alreadyComputedPermissions) {
        Set<String> permissions = Objects.requireNonNullElseGet(alreadyComputedPermissions,
                HashSet::new);
        for (String permission : permissionsToValidate) {
            if (mContext.checkCallingPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                permissions.add(permission);
            }
        }
        return permissions;
    }

    /**
     * This method should be used to clear {@link PhoneAccount} properties based on a
     * callingPackages permissions.
     *
     * @param account     to clear properties from
     * @param permissions the list of permissions the callingPackge has
     * @return the account that callingPackage will receive
     */
    private PhoneAccount maybeCleansePhoneAccount(PhoneAccount account,
            Set<String> permissions) {
        if (account == null) {
            return null;
        }
        PhoneAccount.Builder accountBuilder = new PhoneAccount.Builder(account);
        if (!permissions.contains(MODIFY_PHONE_STATE)) {
            accountBuilder.setGroupId("***");
        }
        return accountBuilder.build();
    }

    private void enforceTelecomFeature() {
        PackageManager pm = mContext.getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TELECOM)
                && !pm.hasSystemFeature(PackageManager.FEATURE_CONNECTION_SERVICE)) {
            throw new UnsupportedOperationException(
                    "System does not support feature " + PackageManager.FEATURE_TELECOM);
        }
    }

    private void enforceRegisterSimSubscriptionPermission() {
        enforcePermission(REGISTER_SIM_SUBSCRIPTION);
    }

    private void enforceModifyPermission() {
        enforcePermission(MODIFY_PHONE_STATE);
    }

    private void enforceModifyPermission(String message) {
        mContext.enforceCallingOrSelfPermission(MODIFY_PHONE_STATE, message);
    }

    private void enforcePermission(String permission) {
        mContext.enforceCallingOrSelfPermission(permission, null);
    }

    private void enforceRegisterSelfManaged() {
        mContext.enforceCallingPermission(android.Manifest.permission.MANAGE_OWN_CALLS, null);
    }

    private void enforceRegisterMultiUser() {
        if (!isCallerSystemApp()) {
            throw new SecurityException("CAPABILITY_MULTI_USER is only available to system apps.");
        }
    }

    private void enforceRegisterVoiceCallingIndicationCapabilities(PhoneAccount account) {
        // Caller must be able to register a SIM PhoneAccount or be the SIM call manager (as named
        // in carrier config) to declare the two voice indication capabilities.
        boolean prerequisiteCapabilitiesOk =
                account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                        || account.hasCapabilities(PhoneAccount.CAPABILITY_CONNECTION_MANAGER);
        boolean permissionsOk =
                isCallerSimCallManagerForAnySim(account.getAccountHandle())
                        || mContext.checkCallingOrSelfPermission(REGISTER_SIM_SUBSCRIPTION)
                                == PackageManager.PERMISSION_GRANTED;
        if (!prerequisiteCapabilitiesOk || !permissionsOk) {
            throw new SecurityException(
                    "Only SIM subscriptions and connection managers are allowed to declare "
                            + "CAPABILITY_SUPPORTS_VOICE_CALLING_INDICATIONS and "
                            + "CAPABILITY_VOICE_CALLING_AVAILABLE");
        }
    }

    private void enforceRegisterSkipCallFiltering() {
        if (!isCallerSystemApp()) {
            throw new SecurityException(
                "EXTRA_SKIP_CALL_FILTERING is only available to system apps.");
        }
    }

    private void enforceUserHandleMatchesCaller(PhoneAccountHandle accountHandle) {
        if (!Binder.getCallingUserHandle().equals(accountHandle.getUserHandle())) {
            // Enforce INTERACT_ACROSS_USERS if the calling user handle does not match
            // phone account's user handle
            enforceInAppCrossUserPermission();
        }
    }

    private void enforcePhoneAccountHandleMatchesCaller(PhoneAccountHandle phoneAccountHandle,
            String callingPackage) {
        if (!callingPackage.equals(phoneAccountHandle.getComponentName().getPackageName())) {
            throw new SecurityException("Caller does not own the PhoneAccountHandle");
        }
    }

    private void enforceCrossUserPermission(int callingUid) {
        if (callingUid != Process.SYSTEM_UID && callingUid != 0) {
            mContext.enforceCallingOrSelfPermission(
                    android.Manifest.permission.INTERACT_ACROSS_USERS_FULL, "Must be system or have"
                            + " INTERACT_ACROSS_USERS_FULL permission");
        }
    }

    private void enforceInAppCrossUserPermission() {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.INTERACT_ACROSS_USERS, "Must be system or have"
                        + " INTERACT_ACROSS_USERS permission");
    }

    private void enforceInAppCrossProfilePermission() {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.INTERACT_ACROSS_PROFILES, "Must be system or have"
                        + " INTERACT_ACROSS_PROFILES permission");
    }

    private boolean hasInAppCrossUserPermission() {
        return mContext.checkCallingOrSelfPermission(
                Manifest.permission.INTERACT_ACROSS_USERS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasInAppCrossProfilePermission() {
        return mContext.checkCallingOrSelfPermission(
                Manifest.permission.INTERACT_ACROSS_PROFILES)
                == PackageManager.PERMISSION_GRANTED;
    }

    // to be used for TestApi methods that can only be called with SHELL UID.
    private void enforceShellOnly(int callingUid, String message) {
        if (callingUid == Process.SHELL_UID || callingUid == Process.ROOT_UID) {
            return; // okay
        }

        throw new SecurityException(message + ": Only shell user can call it");
    }

    private boolean canReadPhoneState(String callingPackage, String callingFeatureId,
            String message) {
        // The system/default dialer can always read phone state - so that emergency calls will
        // still work.
        if (isPrivilegedDialerCalling(callingPackage)) {
            return true;
        }

        try {
            mContext.enforceCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE, message);
            // SKIP checking run-time OP_READ_PHONE_STATE since caller or self has PRIVILEGED
            // permission
            return true;
        } catch (SecurityException e) {
            // Accessing phone state is gated by a special permission.
            mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, message);

            // Some apps that have the permission can be restricted via app ops.
            return mAppOpsManager.noteOp(AppOpsManager.OP_READ_PHONE_STATE, Binder.getCallingUid(),
                    callingPackage, callingFeatureId, message) == AppOpsManager.MODE_ALLOWED;
        }
    }

    private boolean canReadMangeOwnCalls(String message) {
        try {
            mContext.enforceCallingOrSelfPermission(MANAGE_OWN_CALLS, message);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    private boolean canReadPhoneNumbers(String callingPackage, String callingFeatureId,
            String message) {
        boolean targetSdkPreR = false;
        int uid = Binder.getCallingUid();
        try {
            ApplicationInfo applicationInfo = mPackageManager.getApplicationInfoAsUser(
                    callingPackage, 0, UserHandle.getUserHandleForUid(Binder.getCallingUid()));
            targetSdkPreR = applicationInfo != null
                    && applicationInfo.targetSdkVersion < Build.VERSION_CODES.R;
        } catch (PackageManager.NameNotFoundException e) {
            // In the case that the PackageManager cannot find the specified calling package apply
            // the more restrictive target R+ requirements.
        }
        // Apps targeting pre-R can access phone numbers via READ_PHONE_STATE
        if (targetSdkPreR) {
            try {
                return canReadPhoneState(callingPackage, callingFeatureId, message);
            } catch (SecurityException e) {
                // Apps targeting pre-R can still access phone numbers via the additional checks
                // below.
            }
        } else {
            // The system/default dialer can always read phone state - so that emergency calls will
            // still work.
            if (isPrivilegedDialerCalling(callingPackage)) {
                return true;
            }
            if (mContext.checkCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE)
                    == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        if (mContext.checkCallingOrSelfPermission(READ_PHONE_NUMBERS)
                == PackageManager.PERMISSION_GRANTED && mAppOpsManager.noteOpNoThrow(
                AppOpsManager.OPSTR_READ_PHONE_NUMBERS, uid, callingPackage, callingFeatureId,
                message) == AppOpsManager.MODE_ALLOWED) {
            return true;
        }
        if (mContext.checkCallingOrSelfPermission(READ_SMS) == PackageManager.PERMISSION_GRANTED
                && mAppOpsManager.noteOpNoThrow(AppOpsManager.OPSTR_READ_SMS, uid, callingPackage,
                callingFeatureId, message) == AppOpsManager.MODE_ALLOWED) {
            return true;
        }
        // The default SMS app with the WRITE_SMS appop granted can access phone numbers.
        if (mAppOpsManager.noteOpNoThrow(AppOpsManager.OPSTR_WRITE_SMS, uid, callingPackage,
                callingFeatureId, message) == AppOpsManager.MODE_ALLOWED) {
            return true;
        }
        throw new SecurityException("Package " + callingPackage
                + " does not meet the requirements to access the phone number");
    }

    private boolean canReadPrivilegedPhoneState(String callingPackage, String message) {
        // The system/default dialer can always read phone state - so that emergency calls will
        // still work.
        if (isPrivilegedDialerCalling(callingPackage)) {
            return true;
        }

        mContext.enforceCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE, message);
        return true;
    }

    private boolean isDialerOrPrivileged(String callingPackage, String message) {
        // The system/default dialer can always read phone state - so that emergency calls will
        // still work.
        if (isPrivilegedDialerCalling(callingPackage)) {
            return true;
        }

        mContext.enforceCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE, message);
        // SKIP checking run-time OP_READ_PHONE_STATE since caller or self has PRIVILEGED
        // permission
        return true;
    }

    private boolean isSelfManagedConnectionService(PhoneAccountHandle phoneAccountHandle) {
        if (phoneAccountHandle != null) {
                PhoneAccount phoneAccount = mPhoneAccountRegistrar.getPhoneAccountUnchecked(
                        phoneAccountHandle);
                return phoneAccount != null && phoneAccount.isSelfManaged();
        }
        return false;
    }

    private boolean canCallPhone(String callingPackage, String message) {
        return canCallPhone(callingPackage, null /* featureId */, message);
    }

    private boolean canCallPhone(String callingPackage, String callingFeatureId, String message) {
        // The system/default dialer can always read phone state - so that emergency calls will
        // still work.
        if (isPrivilegedDialerCalling(callingPackage)) {
            return true;
        }

        if (mContext.checkCallingOrSelfPermission(CALL_PRIVILEGED)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }

        // Accessing phone state is gated by a special permission.
        mContext.enforceCallingOrSelfPermission(CALL_PHONE, message);

        // Some apps that have the permission can be restricted via app ops.
        return mAppOpsManager.noteOp(AppOpsManager.OP_CALL_PHONE,
                Binder.getCallingUid(), callingPackage, callingFeatureId, message)
                == AppOpsManager.MODE_ALLOWED;
    }

    private boolean canGetPhoneAccount(String callingPackage, PhoneAccountHandle accountHandle) {
        // Allow default dialer, system dialer and sim call manager to be able to do this without
        // extra permission
        try {
            if (isPrivilegedDialerCalling(callingPackage) || isCallerSimCallManager(
                    accountHandle)) {
                return true;
            }
        } catch (SecurityException e) {
            // ignore
        }

        try {
            mContext.enforceCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE, null);
            return true;
        } catch (SecurityException e) {
            // Accessing phone state is gated by a special permission.
            mContext.enforceCallingOrSelfPermission(READ_PHONE_NUMBERS, null);
            return true;
        }
    }

    private boolean isCallerSimCallManager(PhoneAccountHandle targetPhoneAccount) {
        long token = Binder.clearCallingIdentity();
        PhoneAccountHandle accountHandle = null;
        try {
            accountHandle = mPhoneAccountRegistrar.getSimCallManagerFromHandle(targetPhoneAccount,
                    mCallsManager.getCurrentUserHandle());
        } finally {
            Binder.restoreCallingIdentity(token);
        }

        if (accountHandle != null) {
            try {
                mAppOpsManager.checkPackage(
                        Binder.getCallingUid(), accountHandle.getComponentName().getPackageName());
                return true;
            } catch (SecurityException e) {
            }
        }
        return false;
    }

    /**
     * Similar to {@link #isCallerSimCallManager}, but works for all SIMs and does not require
     * {@code accountHandle} to be registered yet.
     */
    private boolean isCallerSimCallManagerForAnySim(PhoneAccountHandle accountHandle) {
        if (isCallerSimCallManager(accountHandle)) {
            // The caller has already registered a CONNECTION_MANAGER PhoneAccount, so let them pass
            // (this allows the SIM call manager through in case of SIM switches, where carrier
            // config may be in a transient state)
            return true;
        }
        // If the caller isn't already registered, then we have to look at the active PSTN
        // PhoneAccounts and check their carrier configs to see if any point to this one's component
        final long token = Binder.clearCallingIdentity();
        try {
            return !mPhoneAccountRegistrar
                    .getSimPhoneAccountsFromSimCallManager(accountHandle)
                    .isEmpty();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private boolean isPrivilegedDialerCalling(String callingPackage) {
        mAppOpsManager.checkPackage(Binder.getCallingUid(), callingPackage);

        // Note: Important to clear the calling identity since the code below calls into RoleManager
        // to check who holds the dialer role, and that requires MANAGE_ROLE_HOLDERS permission
        // which is a system permission.
        long token = Binder.clearCallingIdentity();
        try {
            return mDefaultDialerCache.isDefaultOrSystemDialer(
                    callingPackage, Binder.getCallingUserHandle().getIdentifier());
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private TelephonyManager getTelephonyManager(int subId) {
        return ((TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE))
                .createForSubscriptionId(subId);
    }

    /**
     * Determines if a video state is valid for accepting an incoming call.
     * For the purpose of accepting a call, states {@link VideoProfile#STATE_AUDIO_ONLY}, and
     * any combination of {@link VideoProfile#STATE_RX_ENABLED} and
     * {@link VideoProfile#STATE_TX_ENABLED} are considered valid.
     *
     * @param videoState The video state.
     * @return {@code true} if the video state is valid, {@code false} otherwise.
     */
    private boolean isValidAcceptVideoState(int videoState) {
        // Given a video state input, turn off TX and RX so that we can determine if those were the
        // only bits set.
        int remainingState = videoState & ~VideoProfile.STATE_TX_ENABLED;
        remainingState = remainingState & ~VideoProfile.STATE_RX_ENABLED;

        // If only TX or RX were set (or neither), the video state is valid.
        return remainingState == 0;
    }

    private void broadcastCallScreeningAppChangedIntent(String componentName,
        boolean isDefault) {
        if (TextUtils.isEmpty(componentName)) {
            return;
        }

        ComponentName broadcastComponentName = ComponentName.unflattenFromString(componentName);

        if (broadcastComponentName != null) {
            Intent intent = new Intent(TelecomManager
                .ACTION_DEFAULT_CALL_SCREENING_APP_CHANGED);
            intent.putExtra(TelecomManager
                .EXTRA_IS_DEFAULT_CALL_SCREENING_APP, isDefault);
            intent.putExtra(TelecomManager
                .EXTRA_DEFAULT_CALL_SCREENING_APP_COMPONENT_NAME, componentName);
            intent.setPackage(broadcastComponentName.getPackageName());
            mContext.sendBroadcast(intent);
        }
    }

    private void validateAccountIconUserBoundary(Icon icon) {
        // Refer to Icon#getUriString for context. The URI string is invalid for icons of
        // incompatible types.
        if (icon != null && (icon.getType() == Icon.TYPE_URI
                || icon.getType() == Icon.TYPE_URI_ADAPTIVE_BITMAP)) {
            int callingUserId = UserHandle.getCallingUserId();
            int requestingUserId = StatusHints.getUserIdFromAuthority(
                    icon.getUri().getAuthority(), callingUserId);
            if(callingUserId != requestingUserId) {
                // If we are transcending the profile boundary, throw an error.
                throw new IllegalArgumentException("Attempting to register a phone account with"
                        + " an image icon belonging to another user.");
            }
        }
    }

    private void validateSimultaneousCallingPackageNames(String appPackageName,
            Set<PhoneAccountHandle> handles) {
        for (PhoneAccountHandle handle : handles) {
            ComponentName name = handle.getComponentName();
            if (name == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            String restrictionPackageName = name.getPackageName();
            if (!appPackageName.equals(restrictionPackageName)) {
                throw new SecurityException("The package name of the PhoneAccount does not "
                        + "match one or more of the package names set in the simultaneous "
                        + "calling restriction.");
            }
        }
    }
}
