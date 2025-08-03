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

package android.telecom.cts.apps;

import static android.telecom.Call.STATE_ACTIVE;
import static android.telecom.Call.STATE_DISCONNECTED;
import static android.telecom.Call.STATE_RINGING;
import static android.telecom.cts.apps.AttributesUtil.getDefaultAttributesForApp;
import static android.telecom.cts.apps.AttributesUtil.getDefaultAttributesForManaged;
import static android.telecom.cts.apps.AttributesUtil.getRandomAttributesForApp;
import static android.telecom.cts.apps.AttributesUtil.getRandomAttributesForManaged;
import static android.telecom.cts.apps.ShellCommandExecutor.COMMAND_CLEANUP_STUCK_CALLS;
import static android.telecom.cts.apps.ShellCommandExecutor.COMMAND_RESET_CAR;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceApp;
import static android.telecom.cts.apps.WaitForInCallService.verifyCallState;
import static android.telecom.cts.apps.WaitForInCallService.waitForInCallServiceBinding;
import static android.telecom.cts.apps.WaitForInCallService.waitUntilExpectCallCount;
import static android.telecom.cts.apps.WaitUntil.DEFAULT_TIMEOUT_MS;
import static android.telecom.cts.apps.WaitUntil.waitUntilConditionIsTrueOrTimeout;

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.AppOpsManager;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.RemoteException;
import android.telecom.Call;
import android.telecom.CallAttributes;
import android.telecom.CallEndpoint;
import android.telecom.CallException;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import com.android.compatibility.common.util.ShellIdentityUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * This class implements all the methods test classes call into to perform some action on an
 * application that is bound to in the cts/tests/tests/telecom-apps dir.
 */
public class BaseAppVerifierImpl {
    static final boolean HAS_TELECOM = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    private static final String REGISTER_SIM_SUBSCRIPTION_PERMISSION =
            "android.permission.REGISTER_SIM_SUBSCRIPTION";
    private static final String MODIFY_PHONE_STATE_PERMISSION =
            "android.permission.MODIFY_PHONE_STATE";
    public Context mContext;
    public TelecomManager mTelecomManager;
    private final BindUtils mBindUtils = new BindUtils();
    private final List<PhoneAccount> mManagedAccounts;
    private final Instrumentation mInstrumentation;
    private final InCallServiceMethods mVerifierMethods;
    private final String mCallingPackageName;
    private final AudioManager mAudioManager;
    public String mPreviousDefaultDialer = "";
    public PhoneAccountHandle mPreviousDefaultPhoneAccount = null;

    public BaseAppVerifierImpl(Instrumentation i, List<PhoneAccount> pAs, InCallServiceMethods vm) {
        mInstrumentation = i;
        mContext = i.getContext();
        mTelecomManager = mContext.getSystemService(TelecomManager.class);
        mManagedAccounts = pAs;
        mVerifierMethods = vm;
        mCallingPackageName = mContext.getPackageName();
        mAudioManager = mContext.getSystemService(AudioManager.class);
    }

    public void setUp() throws Exception {
        ShellCommandExecutor.executeShellCommand(mInstrumentation, COMMAND_RESET_CAR);
        AppOpsManager aom = mContext.getSystemService(AppOpsManager.class);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(aom,
                (appOpsMan) -> appOpsMan.setUidMode(AppOpsManager.OPSTR_PROCESS_OUTGOING_CALLS,
                        Process.myUid(), AppOpsManager.MODE_ALLOWED));
        mPreviousDefaultDialer = ShellCommandExecutor.getDefaultDialer(mInstrumentation);
        ShellCommandExecutor.setDefaultDialer(mInstrumentation, mCallingPackageName);
    }

    public void tearDown() throws Exception {
        ShellCommandExecutor.executeShellCommand(mInstrumentation, COMMAND_CLEANUP_STUCK_CALLS);
        if (!mPreviousDefaultDialer.equals("")) {
            ShellCommandExecutor.setDefaultDialer(mInstrumentation, mPreviousDefaultDialer);
        }
        clearUserDefaultPhoneAccountOverride();
        ShellIdentityUtils.dropShellPermissionIdentity();
    }

    public static boolean shouldTestTelecom(Context context) {
        if (!HAS_TELECOM) {
            return false;
        }
        final PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TELECOM);
    }

    public AppControlWrapper bindToApp(TelecomTestApp applicationName) throws Exception {
        AppControlWrapper control = mBindUtils.bindToApplication(mContext, applicationName);
        if (isManagedConnectionService(applicationName)) {
            for (PhoneAccount pA : mManagedAccounts) {
                registerManagedPhoneAccount(pA);
            }
        }
        return control;
    }

    private boolean isManagedConnectionService(TelecomTestApp applicationName) {
        return applicationName.equals(ManagedConnectionServiceApp);
    }

    public List<AppControlWrapper> bindToApps(List<TelecomTestApp> applicationNames)
            throws Exception {
        ArrayList<AppControlWrapper> controls = new ArrayList<>();
        for (TelecomTestApp name : applicationNames) {
            AppControlWrapper control = bindToApp(name);
            controls.add(control);
        }
        return controls;
    }

    public void tearDownApp(AppControlWrapper appControl) {
        if (appControl != null) {
            mBindUtils.unbindFromApplication(mContext, appControl);
        }
    }

    public void tearDownApps(List<AppControlWrapper> appControls) {
        for (AppControlWrapper control : appControls) {
            tearDownApp(control);
        }
    }

    /***********************************************************
     /                 core methods
     /***********************************************************/

    public CallAttributes getDefaultAttributes(TelecomTestApp name,
            boolean isOutgoing)
            throws Exception {
        if (name.equals(ManagedConnectionServiceApp)) {
            // Treat the first element in mManagedAccounts as the "default"
            return getDefaultAttributesForManaged(mManagedAccounts.get(0).getAccountHandle(),
                    isOutgoing);
        }
        return getDefaultAttributesForApp(name, isOutgoing);
    }

    public CallAttributes getDefaultAttributes(TelecomTestApp name, PhoneAccountHandle pAH,
            boolean isOutgoing)
            throws Exception {
        if (name.equals(ManagedConnectionServiceApp)) {
            return getDefaultAttributesForManaged(pAH, isOutgoing);
        }
        return getDefaultAttributesForApp(name, isOutgoing);
    }

    public CallAttributes getRandomAttributes(TelecomTestApp name,
            boolean isOutgoing,
            boolean isHoldable)
            throws Exception {
        if (name.equals(ManagedConnectionServiceApp)) {
            // Treat the first element in mManagedAccounts as the "default"
            return getRandomAttributesForManaged(mManagedAccounts.get(0).getAccountHandle(),
                    isOutgoing, isHoldable);
        }
        return getRandomAttributesForApp(name, isOutgoing, isHoldable);
    }

    public String addCallAndVerify(AppControlWrapper appControl, CallAttributes attributes)
            throws Exception {
        int currentCallCount = mVerifierMethods.getCurrentCallCount();
        appControl.addCall(attributes);
        waitForInCallServiceBinding(mVerifierMethods);
        waitUntilExpectCallCount(mVerifierMethods, currentCallCount + 1);
        return mVerifierMethods.getLastAddedCall().getDetails().getId();
    }

    // -- call state
    public void setCallState(AppControlWrapper appControl, String id, int callState)
            throws Exception {
        appControl.setCallState(id, callState, true, new Bundle());
    }

    public void setCallStateAndVerify(AppControlWrapper appControl, String id, int callState)
            throws Exception {
        appControl.setCallState(id, callState, true, new Bundle());
        verifyCallState(mVerifierMethods, id, callState);
    }

    public void setCallStateAndVerify(AppControlWrapper appControl, String id, int targetCallState,
            int arg) throws Exception {
        Bundle extras = new Bundle();
        if (targetCallState == STATE_ACTIVE) {
            verifyCallIsInState(id, STATE_RINGING);
            extras = CallControlExtras.addVideoStateExtra(extras, arg);
        } else if (targetCallState == STATE_DISCONNECTED) {
            extras = CallControlExtras.addDisconnectCauseExtra(extras, arg);
        }
        appControl.setCallState(id, targetCallState, true, extras);
        // verify the call was added in the ICS
        verifyCallState(mVerifierMethods, id, targetCallState);
    }

    public CallException setCallStateButExpectOnError(AppControlWrapper appControl,
            String id,
            int targetCallState)
            throws Exception {
        verifyAppIsTransactional(appControl);
        return appControl.setCallState(id, targetCallState, false, new Bundle());

    }

    private void verifyAppIsTransactional(AppControlWrapper appControlWrapper) throws Exception {
        if (!appControlWrapper.isTransactionalControl()) {
            throw new Exception("This method is only for Transactional Apps");
        }
    }

    public CallException setCallStateButExpectOnError(AppControlWrapper appControl,
            String id,
            int targetCallState,
            int arg) throws Exception {
        verifyAppIsTransactional(appControl);
        Bundle extras = new Bundle();
        if (targetCallState == STATE_ACTIVE) {
            verifyCallIsInState(id, STATE_RINGING);
            extras = CallControlExtras.addVideoStateExtra(extras, arg);
        } else if (targetCallState == STATE_DISCONNECTED) {
            extras = CallControlExtras.addDisconnectCauseExtra(extras, arg);
        }
        return appControl.setCallState(id, targetCallState, false, extras);
    }

    public void verifyCallIsInState(String id, int state) throws Exception {
        waitForInCallServiceBinding(mVerifierMethods);
        verifyCallState(mVerifierMethods, id, state);
    }

    public void answerViaInCallServiceAndVerify(String id, int videoState) throws Exception {
        waitForInCallServiceBinding(mVerifierMethods);
        List<Call> calls = mVerifierMethods.getOngoingCalls();
        Call targetCall = null;
        for (Call call : calls) {
            if (call.getDetails().getId().equals(id)) {
                targetCall = call;
                break;
            }
        }
        if (targetCall == null) {
            fail("answerViaInCallServiceAndVerify: failed to find target call id=" + id);
        }
        targetCall.answer(videoState);
        verifyCallIsInState(id, STATE_ACTIVE);
    }

    // -- audio state
    public CallEndpoint getAnotherCallEndpoint(AppControlWrapper appControl, String id)
            throws Exception {
        CallEndpoint currentCallEndpoint = getCurrentCallEndpoint(appControl, id);
        List<CallEndpoint> endpoints = getAvailableCallEndpoints(appControl, id);

        if (currentCallEndpoint == null) {
            fail("currentCallEndpoint is NULL");
        }
        if (endpoints == null) {
            fail("available endpoints list is NULL");
        }
        if (endpoints.size() == 1) {
            return null;
        }
        for (CallEndpoint endpoint : endpoints) {
            if (endpoint.getEndpointType() != currentCallEndpoint.getEndpointType()) {
                return endpoint;
            }
        }
        return null;
    }

    public CallEndpoint getCurrentCallEndpoint(AppControlWrapper appControl, String id)
            throws Exception {
        return appControl.getCurrentCallEndpoint(id);
    }

    public List<CallEndpoint> getAvailableCallEndpoints(AppControlWrapper appControl, String id)
            throws Exception {
        return appControl.getAvailableCallEndpoints(id);
    }


    public void setAudioRouteStateAndVerify(AppControlWrapper appControl, String id,
            CallEndpoint newCallEndpoint) throws Exception {
        appControl.setAudioRouteStateAndVerify(id, newCallEndpoint);
    }

    public boolean isMuted(AppControlWrapper appControl, String id) throws RemoteException {
        return appControl.isMuted(id);
    }

    public void setMuteState(AppControlWrapper appControl, String id, boolean isMuted)
            throws RemoteException {
        appControl.setMuteState(id, isMuted);
    }

    // -- phone accounts
    public void registerDefaultPhoneAccount(AppControlWrapper appControl) throws RemoteException {
        appControl.registerDefaultPhoneAccount();
        if (appControl.isManagedAppControl()) {
            for (PhoneAccount pa : mManagedAccounts) {
                assertTrue("Managed PhoneAccount [ +" + pa.getAccountHandle() + " ] is not"
                        + "registered.", isPhoneAccountRegistered(pa.getAccountHandle()));
            }
        } else {
            PhoneAccount account = appControl.getDefaultPhoneAccount();
            assertTrue(isPhoneAccountRegistered(account.getAccountHandle()));
        }
    }

    public void registerCustomPhoneAccount(AppControlWrapper appControl, PhoneAccount account)
            throws RemoteException {
        appControl.registerCustomPhoneAccount(account);
        assertTrue(isPhoneAccountRegistered(account.getAccountHandle()));
    }

    public void registerManagedPhoneAccount(PhoneAccount pa) throws Exception {
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelecomManager,
                tm -> tm.registerPhoneAccount(pa),
                MODIFY_PHONE_STATE_PERMISSION,
                REGISTER_SIM_SUBSCRIPTION_PERMISSION);
        ShellCommandExecutor.enablePhoneAccount(mInstrumentation, pa.getAccountHandle());
    }

    public void setUserDefaultPhoneAccountOverride(PhoneAccountHandle handle) throws Exception {
        mPreviousDefaultPhoneAccount = mTelecomManager.getUserSelectedOutgoingPhoneAccount();
        ShellCommandExecutor.setUserDefaultPhoneAccount(mInstrumentation, handle);
        assertEquals("Could not set " + handle + "as the user default" , handle,
                mTelecomManager.getUserSelectedOutgoingPhoneAccount());
    }

    private void clearUserDefaultPhoneAccountOverride() throws Exception {
        ShellCommandExecutor.setUserDefaultPhoneAccount(mInstrumentation,
                mPreviousDefaultPhoneAccount);
    }

    public void unregisterPhoneAccountWithHandle(AppControlWrapper appControl,
            PhoneAccountHandle handle) throws RemoteException {
        appControl.unregisterPhoneAccountWithHandle(handle);
        assertFalse(isPhoneAccountRegistered(handle));
    }

    public List<PhoneAccountHandle> getAccountHandlesForApp(AppControlWrapper appControl)
            throws RemoteException {
        return appControl.getAccountHandlesForApp();
    }

    public void verifyCallPhoneAccount(String id, PhoneAccountHandle handle) {
        waitForInCallServiceBinding(mVerifierMethods);
        List<Call> calls = mVerifierMethods.getOngoingCalls();
        Call targetCall = null;
        for (Call call : calls) {
            if (call.getDetails().getId().equals(id)) {
                targetCall = call;
                break;
            }
        }
        if (targetCall == null) {
            fail("verifyCallPhoneAccount: failed to find target call id=" + id);
        }
        if (targetCall.getDetails() == null) {
            fail("verifyCallPhoneAccount: failed to find target call details, id=" + id);
        }
        assertEquals("Call PhoneAccount did not match expected", handle,
                targetCall.getDetails().getAccountHandle());
    }

    public boolean isPhoneAccountRegistered(PhoneAccountHandle handle) {
        return mTelecomManager.getPhoneAccount(handle) != null;
    }

    public void assertAudioMode(final int expectedMode) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return mAudioManager.getMode() == expectedMode;
                    }
                },
                DEFAULT_TIMEOUT_MS,
                "Audio mode was expected to be " + expectedMode
        );
    }

    public void verifyNotificationPostedForCall(AppControlWrapper appControlWrapper, String callId){
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        try {
                            return appControlWrapper.isNotificationPostedForCall(callId);
                        } catch (RemoteException e) {
                            throw new RuntimeException(e);
                        }
                    }
                },
                DEFAULT_TIMEOUT_MS,
               String.format("Expected to find notification for call with id=[%s], "
                               + "for application=[%s], but no notification was posted by the"
                               + " notification manager", callId,
                                appControlWrapper.getTelecomApps()));
    }
}
