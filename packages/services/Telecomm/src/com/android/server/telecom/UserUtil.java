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

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.net.Uri;
import android.os.UserHandle;
import android.os.UserManager;
import android.telecom.Log;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;

import com.android.server.telecom.components.ErrorDialogActivity;
import com.android.server.telecom.flags.FeatureFlags;

public final class UserUtil {

    private UserUtil() {
    }

    private static UserInfo getUserInfoFromUserHandle(Context context, UserHandle userHandle) {
        UserManager userManager = context.getSystemService(UserManager.class);
        return userManager.getUserInfo(userHandle.getIdentifier());
    }

    public static boolean isManagedProfile(Context context, UserHandle userHandle,
            FeatureFlags featureFlags) {
        UserManager userManager = context.createContextAsUser(userHandle, 0)
                .getSystemService(UserManager.class);
        UserInfo userInfo = getUserInfoFromUserHandle(context, userHandle);
        return featureFlags.telecomResolveHiddenDependencies()
                ? userManager != null && userManager.isManagedProfile()
                : userInfo != null && userInfo.isManagedProfile();
    }

    public static boolean isProfile(Context context, UserHandle userHandle,
            FeatureFlags featureFlags) {
        UserManager userManager = context.createContextAsUser(userHandle, 0)
                .getSystemService(UserManager.class);
        UserInfo userInfo = getUserInfoFromUserHandle(context, userHandle);
        return featureFlags.telecomResolveHiddenDependencies()
                ? userManager != null && userManager.isProfile()
                : userInfo != null && userInfo.profileGroupId != userInfo.id;
    }

    public static void showErrorDialogForRestrictedOutgoingCall(Context context,
            int stringId, String tag, String reason) {
        final Intent intent = new Intent(context, ErrorDialogActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(ErrorDialogActivity.ERROR_MESSAGE_ID_EXTRA, stringId);
        context.startActivityAsUser(intent, UserHandle.CURRENT);
        Log.w(tag, "Rejecting non-emergency phone call because "
                + reason);
    }

    public static boolean hasOutgoingCallsUserRestriction(Context context,
            UserHandle userHandle, Uri handle, boolean isSelfManaged, String tag,
            FeatureFlags featureFlags) {
        // Set handle for conference calls. Refer to {@link Connection#ADHOC_CONFERENCE_ADDRESS}.
        if (handle == null) {
            handle = Uri.parse("tel:conf-factory");
        }

        if(!isSelfManaged) {
            // Check DISALLOW_OUTGOING_CALLS restriction. Note: We are skipping this
            // check in a managed profile user because this check can always be bypassed
            // by copying and pasting the phone number into the personal dialer.
            if (!UserUtil.isManagedProfile(context, userHandle, featureFlags)) {
                final UserManager userManager = context.getSystemService(UserManager.class);
                boolean hasUserRestriction = featureFlags.telecomResolveHiddenDependencies()
                        ? userManager.hasUserRestrictionForUser(
                                UserManager.DISALLOW_OUTGOING_CALLS, userHandle)
                        : userManager.hasUserRestriction(
                                UserManager.DISALLOW_OUTGOING_CALLS, userHandle);
                // Only emergency calls are allowed for users with the DISALLOW_OUTGOING_CALLS
                // restriction.
                if (!TelephonyUtil.shouldProcessAsEmergency(context, handle)) {
                    if (userManager.hasBaseUserRestriction(UserManager.DISALLOW_OUTGOING_CALLS,
                            userHandle)) {
                        String reason = "of DISALLOW_OUTGOING_CALLS restriction";
                        showErrorDialogForRestrictedOutgoingCall(context,
                                R.string.outgoing_call_not_allowed_user_restriction, tag, reason);
                        return true;
                    } else if (hasUserRestriction) {
                        final DevicePolicyManager dpm =
                                context.getSystemService(DevicePolicyManager.class);
                        if (dpm == null) {
                            return true;
                        }
                        final Intent adminSupportIntent = dpm.createAdminSupportIntent(
                                UserManager.DISALLOW_OUTGOING_CALLS);
                        if (adminSupportIntent != null) {
                            context.startActivityAsUser(adminSupportIntent, userHandle);
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Gets the associated user for the given call. Note: this is applicable to all calls except
     * outgoing calls as the associated user is already based off of the user placing the
     * call.
     *
     * @param phoneAccountRegistrar
     * @param currentUser Current user profile (this can either be the admin or a secondary/guest
     *                    user). Note that work profile users fall under the admin user.
     * @param targetPhoneAccount The phone account to retrieve the {@link UserHandle} from.
     * @return current user if it isn't the admin or if the work profile is paused for the target
     * phone account handle user, otherwise return the target phone account handle user. If the
     * flag is disabled, return the legacy {@link UserHandle}.
     */
    public static UserHandle getAssociatedUserForCall(boolean isAssociatedUserFlagEnabled,
            PhoneAccountRegistrar phoneAccountRegistrar, UserHandle currentUser,
            PhoneAccountHandle targetPhoneAccount) {
        if (!isAssociatedUserFlagEnabled) {
            return targetPhoneAccount.getUserHandle();
        }
        // For multi-user phone accounts, associate the call with the profile receiving/placing
        // the call. For SIM accounts (that are assigned to specific users), the user association
        // will be placed on the target phone account handle user.
        PhoneAccount account = phoneAccountRegistrar.getPhoneAccountUnchecked(targetPhoneAccount);
        if (account != null) {
            return account.hasCapabilities(PhoneAccount.CAPABILITY_MULTI_USER)
                    ? currentUser
                    : targetPhoneAccount.getUserHandle();
        }
        // If target phone account handle is null or account cannot be found,
        // return the current user.
        return currentUser;
    }
}
