/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.server.telecom.components;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.UserHandle;
import android.os.UserManager;
import android.telecom.Log;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;

import com.android.server.telecom.CallIntentProcessor;
import com.android.server.telecom.R;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.TelephonyUtil;
import com.android.server.telecom.UserUtil;
import com.android.server.telecom.flags.FeatureFlags;

// TODO: Needed for move to system service: import com.android.internal.R;

/**
 * Handles system CALL actions and forwards them to {@link CallIntentProcessor}.
 * Handles all three CALL action types: CALL, CALL_PRIVILEGED, and CALL_EMERGENCY.
 *
 * Pre-L, the only way apps were were allowed to make outgoing emergency calls was the
 * ACTION_CALL_PRIVILEGED action (which requires the system only CALL_PRIVILEGED permission).
 *
 * In L, any app that has the CALL_PRIVILEGED permission can continue to make outgoing emergency
 * calls via ACTION_CALL_PRIVILEGED.
 *
 * In addition, the default dialer (identified via
 * {@link android.telecom.TelecomManager#getDefaultDialerPackage()} will also be granted the
 * ability to make emergency outgoing calls using the CALL action. In order to do this, it must
 * use the {@link TelecomManager#placeCall(Uri, android.os.Bundle)} method to allow its package
 * name to be passed to {@link UserCallIntentProcessor}. Calling startActivity will continue to
 * work on all non-emergency numbers just like it did pre-L.
 */
public class UserCallIntentProcessor {

    private final Context mContext;
    private final UserHandle mUserHandle;
    private FeatureFlags mFeatureFlags;

    public UserCallIntentProcessor(Context context, UserHandle userHandle,
            FeatureFlags featureFlags) {
        mContext = context;
        mUserHandle = userHandle;
        mFeatureFlags = featureFlags;
    }

    /**
     * Processes intents sent to the activity.
     *
     * @param intent The intent.
     * @param callingPackageName The package name of the calling app.
     * @param isSelfManaged      {@code true} if SelfManaged profile enabled.
     * @param canCallNonEmergency {@code true} if the caller is permitted to call non-emergency
     *                            numbers.
     * @param isLocalInvocation {@code true} if the caller is within the system service (i.e. the
     *                            caller is {@link com.android.server.telecom.TelecomServiceImpl})
     *                            and we can skip the re-broadcast of the intent to Telecom.
     *                            When {@code false}, we need to re-broadcast the intent to Telcom
     *                            to trampoline it to the system service where the Telecom
     *                            service resides.
     */
    public void processIntent(Intent intent, String callingPackageName,
            boolean isSelfManaged, boolean canCallNonEmergency,
            boolean isLocalInvocation) {
        String action = intent.getAction();

        if (Intent.ACTION_CALL.equals(action) ||
                Intent.ACTION_CALL_PRIVILEGED.equals(action) ||
                Intent.ACTION_CALL_EMERGENCY.equals(action)) {
            processOutgoingCallIntent(intent, callingPackageName, isSelfManaged,
                    canCallNonEmergency, isLocalInvocation);
        }
    }

    private void processOutgoingCallIntent(Intent intent, String callingPackageName,
            boolean isSelfManaged, boolean canCallNonEmergency,
            boolean isLocalInvocation) {
        Uri handle = intent.getData();
        if (handle == null) return;
        String scheme = handle.getScheme();
        String uriString = handle.getSchemeSpecificPart();

        // Ensure sip URIs dialed using TEL scheme get converted to SIP scheme.
        if (PhoneAccount.SCHEME_TEL.equals(scheme) && PhoneNumberUtils.isUriNumber(uriString)) {
            handle = Uri.fromParts(PhoneAccount.SCHEME_SIP, uriString, null);
        }

       if (UserUtil.hasOutgoingCallsUserRestriction(mContext, mUserHandle, handle, isSelfManaged,
               UserCallIntentProcessor.class.getCanonicalName(), mFeatureFlags)) {
           return;
       }

        if (!isSelfManaged && !canCallNonEmergency &&
                !TelephonyUtil.shouldProcessAsEmergency(mContext, handle)) {
            String reason = android.Manifest.permission.CALL_PHONE + " permission is not granted.";
            UserUtil.showErrorDialogForRestrictedOutgoingCall(mContext,
                    R.string.outgoing_call_not_allowed_no_permission,
                    this.getClass().getCanonicalName(), reason);
            return;
        }

        int videoState = intent.getIntExtra(
                TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                VideoProfile.STATE_AUDIO_ONLY);
        Log.d(this, "processOutgoingCallIntent videoState = " + videoState);

        // Save the user handle of current user before forwarding the intent to primary user.
        intent.putExtra(CallIntentProcessor.KEY_INITIATING_USER, mUserHandle);

        sendIntentToDestination(intent, isLocalInvocation, callingPackageName);
    }

    /**
     * Potentially trampolines the intent to Telecom via TelecomServiceImpl.
     * If the caller is local to the Telecom service, we send the intent to Telecom without
     * sending it through TelecomServiceImpl.
     */
    private boolean sendIntentToDestination(Intent intent, boolean isLocalInvocation,
            String callingPackage) {
        intent.putExtra(CallIntentProcessor.KEY_IS_INCOMING_CALL, false);
        intent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        if (isLocalInvocation) {
            // We are invoking this from TelecomServiceImpl, so TelecomSystem is available.  Don't
            // bother trampolining the intent, just sent it directly to the call intent processor.
            // TODO: We should not be using an intent here; this whole flows needs cleanup.
            Log.i(this, "sendIntentToDestination: send intent to Telecom directly.");
            synchronized (TelecomSystem.getInstance().getLock()) {
                TelecomSystem.getInstance().getCallIntentProcessor().processIntent(intent,
                        callingPackage);
            }
        } else {
            // We're calling from the UserCallActivity, so the TelecomSystem is not in the same
            // process; we need to trampoline to TelecomSystem in the system server process.
            Log.i(this, "sendIntentToDestination: trampoline to Telecom.");
            TelecomManager tm = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
            tm.handleCallIntent(intent, callingPackage);
        }
        return true;
    }
}
