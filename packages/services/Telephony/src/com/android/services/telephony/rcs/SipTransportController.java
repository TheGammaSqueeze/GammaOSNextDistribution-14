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

package com.android.services.telephony.rcs;

import android.app.role.OnRoleHoldersChangedListener;
import android.app.role.RoleManager;
import android.content.Context;
import android.os.PersistableBundle;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telephony.CarrierConfigManager;
import android.telephony.ims.DelegateRequest;
import android.telephony.ims.FeatureTagState;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsService;
import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.SipDialogState;
import android.telephony.ims.aidl.IImsRegistration;
import android.telephony.ims.aidl.ISipDelegate;
import android.telephony.ims.aidl.ISipDelegateConnectionStateCallback;
import android.telephony.ims.aidl.ISipDelegateMessageCallback;
import android.telephony.ims.aidl.ISipTransport;
import android.telephony.ims.stub.DelegateConnectionMessageCallback;
import android.telephony.ims.stub.DelegateConnectionStateCallback;
import android.telephony.ims.stub.SipDelegate;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.LocalLog;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.ims.RcsFeatureManager;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.ISipDialogStateCallback;
import com.android.internal.telephony.util.RemoteCallbackListExt;
import com.android.internal.util.IndentingPrintWriter;
import com.android.phone.RcsProvisioningMonitor;

import com.google.common.base.Objects;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Manages the creation and destruction of SipDelegates in response to an IMS application requesting
 * a SipDelegateConnection registered to one or more IMS feature tags.
 * <p>
 * This allows an IMS application to forward traffic related to those feature tags over the existing
 * IMS registration managed by the {@link ImsService} associated with this cellular subscription
 * instead of requiring that the IMS application manage its own IMS registration over-the-top. This
 * is required for some cellular carriers, which mandate that all IMS SIP traffic must be sent
 * through a single IMS registration managed by the system IMS service.
 *
 * //TODO: Support other roles besides SMS
 * //TODO: Bring in carrier provisioning to influence features that can be created.
 * //TODO: Generate registration change events.
 */
public class SipTransportController implements RcsFeatureController.Feature,
        OnRoleHoldersChangedListener {
    public static final String LOG_TAG = "SipTransportC";
    static final int LOG_SIZE = 50;

    /**See {@link TimerAdapter#getReevaluateThrottleTimerMilliseconds()}.*/
    private static final int REEVALUATE_THROTTLE_DEFAULT_MS = 1000;
    /**See {@link TimerAdapter#getUpdateRegistrationDelayMilliseconds()}.*/
    private static final int TRIGGER_UPDATE_REGISTRATION_DELAY_DEFAULT_MS = 1000;

    /**
     * {@link RoleManager} is final so we have to wrap the implementation for testing.
     */
    @VisibleForTesting
    public interface RoleManagerAdapter {
        /** See {@link RoleManager#getRoleHolders(String)} */
        List<String> getRoleHolders(String roleName);
        /** See {@link RoleManager#addOnRoleHoldersChangedListenerAsUser} */
        void addOnRoleHoldersChangedListenerAsUser(Executor executor,
                OnRoleHoldersChangedListener listener, UserHandle user);
        /** See {@link RoleManager#removeOnRoleHoldersChangedListenerAsUser} */
        void removeOnRoleHoldersChangedListenerAsUser(OnRoleHoldersChangedListener listener,
                UserHandle user);
    }

    /**
     * Adapter for timers related to this class so they can be modified during testing.
     */
    @VisibleForTesting
    public interface TimerAdapter {
        /**
         * Time we will delay after a {@link #createSipDelegate} or {@link #destroySipDelegate}
         * command to re-evaluate and apply any changes to the list of tracked
         * SipDelegateControllers.
         * <p>
         * Another create/destroy request sent during this time will not postpone re-evaluation
         * again.
         */
        int getReevaluateThrottleTimerMilliseconds();

        /**
         * Time after re-evaluate we will wait to trigger the update of IMS registration.
         * <p>
         * Another re-evaluate while waiting to trigger a registration update will cause this
         * controller to cancel and reschedule the event again, further delaying the trigger to send
         * a registration update.
         */
        int getUpdateRegistrationDelayMilliseconds();
    }

    private static class TimerAdapterImpl implements TimerAdapter {

        @Override
        public int getReevaluateThrottleTimerMilliseconds() {
            return REEVALUATE_THROTTLE_DEFAULT_MS;
        }

        @Override
        public int getUpdateRegistrationDelayMilliseconds() {
            return TRIGGER_UPDATE_REGISTRATION_DELAY_DEFAULT_MS;
        }
    }

    private static class RoleManagerAdapterImpl implements RoleManagerAdapter {

        private final RoleManager mRoleManager;

        private RoleManagerAdapterImpl(Context context) {
            mRoleManager = context.getSystemService(RoleManager.class);
        }

        @Override
        public List<String> getRoleHolders(String roleName) {
            return mRoleManager.getRoleHolders(roleName);
        }

        @Override
        public void addOnRoleHoldersChangedListenerAsUser(Executor executor,
                OnRoleHoldersChangedListener listener, UserHandle user) {
            mRoleManager.addOnRoleHoldersChangedListenerAsUser(executor, listener, user);
        }

        @Override
        public void removeOnRoleHoldersChangedListenerAsUser(OnRoleHoldersChangedListener listener,
                UserHandle user) {
            mRoleManager.removeOnRoleHoldersChangedListenerAsUser(listener, user);
        }
    }

    /**
     * Extends RemoteCallbackList to track callbacks to the IMS applications with
     * SipDelegateConnections and cleans them up if they die.
     */
    private class TrackedAppBinders extends RemoteCallbackList<ISipDelegateMessageCallback> {
        @Override
        public void onCallbackDied(ISipDelegateMessageCallback callback, Object cookie) {
            mExecutorService.execute(() -> {
                if (cookie instanceof SipDelegateController) {
                    SipDelegateController c = (SipDelegateController) cookie;
                    logi("onCallbackDied - Cleaning up delegates associated with package: "
                            + c.getPackageName());
                    boolean isNewDestroyQueued = addPendingDestroy(c,
                            SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SERVICE_DEAD);
                    if (isNewDestroyQueued) {
                        CompletableFuture<Void> f = new CompletableFuture<>();
                        scheduleReevaluateNow(f);
                        f.thenRun(() -> logi("onCallbackDied - clean up complete for package: "
                                + c.getPackageName()));
                    }
                } else {
                    logw("onCallbackDied: unexpected - cookie is not an instance of "
                            + "SipDelegateController");
                }
            });
        }
    }

    /**
     * Used in {@link #destroySipDelegate(int, ISipDelegate, int)} to store pending destroy
     * requests.
     */
    private static final class DestroyRequest {
        public final SipDelegateController controller;
        public final int reason;

        DestroyRequest(SipDelegateController c, int r) {
            controller = c;
            reason = r;
        }


        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DestroyRequest that = (DestroyRequest) o;
            // Only use controller for comparison, as we want to only have one DestroyRequest active
            // per controller.
            return controller.equals(that.controller);
        }

        @Override
        public int hashCode() {
            // Only use controller for hash, as we want to only have one DestroyRequest active per
            // controller.
            return java.util.Objects.hash(controller);
        }

        @Override
        public String toString() {
            return "DestroyRequest{" + "controller=" + controller + ", reason=" + reason + '}';
        }
    }

    /**
     * This is to handle with dialogs of all available delegates that have dialogs.
     */
    private final class SipDialogsStateHandle implements SipDialogsStateListener {

        private Executor mExecutor = Runnable::run;
        Map<String, List<SipDialogState>> mMapDialogState = new HashMap<>();

        /**
         * This will be called using the {@link SipDialogsStateListener}
         * @param key This is the ID of the SipSessionTracker for handling the dialogs of
         *               each created delegates.
         * @param dialogStates This is a list of dialog states tracked in SipSessionTracker.
         */
        @Override
        public void reMappingSipDelegateState(String key,
                List<SipDialogState> dialogStates) {
            mExecutor.execute(()->processReMappingSipDelegateState(key, dialogStates));
        }

        /**
         * Notify SipDialogState information with
         * {@link com.android.internal.telephony.ISipDialogStateCallback}
         */
        @Override
        public void notifySipDialogState() {
            mExecutor.execute(()->processNotifySipDialogState());
        }

        private void processReMappingSipDelegateState(String key,
                List<SipDialogState> dialogStates) {
            if (dialogStates.isEmpty()) {
                mMapDialogState.remove(key);
            } else {
                mMapDialogState.put(key, dialogStates);
            }
            notifySipDialogState();
        }

        private void processNotifySipDialogState() {
            List<SipDialogState> finalDialogStates = new ArrayList<>();
            for (List<SipDialogState> d : mMapDialogState.values()) {
                finalDialogStates.addAll(d);
            }

            if (mSipDialogStateCallbacks.getRegisteredCallbackCount() == 0) {
                return;
            }
            mSipDialogStateCallbacks.broadcastAction((c) -> {
                try {
                    c.onActiveSipDialogsChanged(finalDialogStates);
                } catch (RemoteException e) {
                    Log.e(LOG_TAG,
                            "onActiveSipDialogsChanged() - Skipping callback." + e);
                }
            });
        }
    }

    /**
     * Allow the ability for tests to easily mock out the SipDelegateController for testing.
     */
    @VisibleForTesting
    public interface SipDelegateControllerFactory {
        /** See {@link SipDelegateController} */
        SipDelegateController create(int subId, int uid, DelegateRequest initialRequest,
                String packageName, ISipTransport sipTransportImpl,
                IImsRegistration registrationImpl, ScheduledExecutorService executorService,
                ISipDelegateConnectionStateCallback stateCallback,
                ISipDelegateMessageCallback messageCallback);
    }

    private SipDelegateControllerFactory mDelegateControllerFactory = SipDelegateController::new;
    private final int mSlotId;
    private final ScheduledExecutorService mExecutorService;
    private final RoleManagerAdapter mRoleManagerAdapter;
    private final TimerAdapter mTimerAdapter;
    private final LocalLog mLocalLog = new LocalLog(LOG_SIZE);

    // A priority queue of active SipDelegateControllers, where the oldest SipDelegate gets
    // access to the feature tag if multiple apps are allowed to request the same feature tag.
    private final List<SipDelegateController> mDelegatePriorityQueue = new ArrayList<>();
    // SipDelegateControllers who have been created and are pending to be added to the priority
    // queue. Will be added into the queue in the same order as they were added here.
    private final List<SipDelegateController> mDelegatePendingCreate = new ArrayList<>();
    // SipDelegateControllers that are pending to be destroyed.
    private final List<DestroyRequest> mDelegatePendingDestroy = new ArrayList<>();
    // SipDialogStateCallback that are adding to use callback.
    private final RemoteCallbackListExt<ISipDialogStateCallback> mSipDialogStateCallbacks =
            new RemoteCallbackListExt<>();
    // To listen the state information if the dialog status is changed from the SipSessionTracker.
    private final SipDialogsStateListener mSipDialogsListener = new SipDialogsStateHandle();

    // Cache of Binders to remote IMS applications for tracking their potential death
    private final TrackedAppBinders mActiveAppBinders = new TrackedAppBinders();

    // Future scheduled for operations that require the list of SipDelegateControllers to
    // be evaluated. When the timer expires and triggers the reevaluate method, this controller
    // will iterate through mDelegatePriorityQueue and assign Feature Tags based on role+priority.
    private ScheduledFuture<?> mScheduledEvaluateFuture;
    // mPendingEvaluateFTFuture creates this CompletableFuture, exposed in order to stop other
    // evaluates from occurring while another is waiting for a result on other threads.
    private CompletableFuture<Void> mEvaluateCompleteFuture;
    // Future scheduled that will trigger the ImsService to update the IMS registration for the
    // SipDelegate configuration. Will be scheduled TRIGGER_UPDATE_REGISTRATION_DELAY_MS
    // milliseconds after a pending evaluate completes.
    private ScheduledFuture<?> mPendingUpdateRegistrationFuture;
    // Subscription id will change as new subscriptions are loaded on the slot.
    private int mSubId;
    // Will go up/down as the ImsService associated with this slotId goes up/down.
    private RcsFeatureManager mRcsManager;
    // Cached package name of the app that is considered the default SMS app.
    private String mCachedSmsRolePackageName = "";
    // Callback to monitor rcs provisioning change
    private CarrierConfigManager mCarrierConfigManager;
    // Cached allowed feature tags from carrier config
    private ArraySet<String> mFeatureTagsAllowed = new ArraySet<>();

    /**
     * Create an instance of SipTransportController.
     * @param context The Context associated with this controller.
     * @param slotId The slot index associated with this controller.
     * @param subId The subscription ID associated with this controller when it was first created.
     */
    public SipTransportController(Context context, int slotId, int subId) {
        mSlotId = slotId;
        mSubId = subId;

        mRoleManagerAdapter = new RoleManagerAdapterImpl(context);
        mTimerAdapter = new TimerAdapterImpl();
        mExecutorService = Executors.newSingleThreadScheduledExecutor();
        mCarrierConfigManager = context.getSystemService(CarrierConfigManager.class);
    }

    /**
     * Constructor to inject dependencies for testing.
     */
    @VisibleForTesting
    public SipTransportController(Context context, int slotId, int subId,
            SipDelegateControllerFactory delegateFactory, RoleManagerAdapter roleManagerAdapter,
            TimerAdapter timerAdapter, ScheduledExecutorService executor) {
        mSlotId = slotId;
        mSubId = subId;

        mRoleManagerAdapter = roleManagerAdapter;
        mTimerAdapter = timerAdapter;
        mDelegateControllerFactory = delegateFactory;
        mExecutorService = executor;
        mCarrierConfigManager = context.getSystemService(CarrierConfigManager.class);
        logi("created");
    }

    @Override
    public void onRcsConnected(RcsFeatureManager manager) {
        mExecutorService.submit(() -> onRcsManagerChanged(manager));
    }

    @Override
    public void onRcsDisconnected() {
        mExecutorService.submit(() -> onRcsManagerChanged(null));
    }

    @Override
    public void onAssociatedSubscriptionUpdated(int subId) {
        mExecutorService.submit(()-> onSubIdChanged(subId));
    }

    @Override
    public void onCarrierConfigChanged() {
        mExecutorService.submit(this::onCarrierConfigChangedInternal);
    }

    @Override
    public void onDestroy() {
        mExecutorService.submit(()-> {
            // Ensure new create/destroy requests are denied.
            mSubId = -1;
            triggerDeregistrationEvent();
            scheduleDestroyDelegates(
                    SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SUBSCRIPTION_TORN_DOWN)
                    .thenRun(mExecutorService::shutdown);
        });
    }

    /**
     * Optionally create a new {@link SipDelegate} based off of the {@link DelegateRequest} given
     * based on the state of this controller and associate it with the given callbacks.
     * <p>
     * Once the {@link SipDelegate} has been created,
     * {@link ISipDelegateConnectionStateCallback#onCreated(ISipDelegate)} must be called with
     * the AIDL instance corresponding to the remote {@link SipDelegate}.
     * @param subId the subId associated with the request.
     * @param uid the uid associated with the request
     * @param request The request parameters used to create the {@link SipDelegate}.
     * @param delegateState The {@link DelegateConnectionStateCallback} Binder connection.
     * @param delegateMessage The {@link DelegateConnectionMessageCallback} Binder Connection
     * @throws ImsException if the request to create the {@link SipDelegate} did not complete.
     */
    public void createSipDelegate(int subId, int uid, DelegateRequest request, String packageName,
            ISipDelegateConnectionStateCallback delegateState,
            ISipDelegateMessageCallback delegateMessage) throws ImsException {
        logi("createSipDelegate: request= " + request + ", packageName= " + packageName);
        CompletableFuture<ImsException> result = new CompletableFuture<>();
        mExecutorService.submit(() -> createSipDelegateInternal(subId, uid, request, packageName,
                delegateState,
                // Capture any ImsExceptions generated during the process.
                delegateMessage, result::complete));
        try {
            ImsException e = result.get();
            logi("createSipDelegate: request finished");
            if (e != null) {
                throw e;
            }
        } catch (InterruptedException | ExecutionException e) {
            logw("createSipDelegate: exception completing future: " + e);
        }
    }

    /**
     * The remote IMS application has requested the destruction of an existing {@link SipDelegate}.
     * @param subId The subId associated with the request.
     * @param connection The internal Binder connection associated with the {@link SipDelegate}.
     * @param reason The reason for why the {@link SipDelegate} was destroyed.
     */
    public void destroySipDelegate(int subId, ISipDelegate connection, int reason) {
        mExecutorService.execute(() -> destroySipDelegateInternal(subId, connection, reason));
    }

    /**
     * The remote IMS application has requested that the ImsService tear down and re-register for
     * IMS features due to an error it received on the network in response to a SIP request.
     */
    public void triggerFullNetworkRegistration(int subId, ISipDelegate connection, int sipCode,
            String sipReason) {
        mExecutorService.execute(() -> triggerFullNetworkRegistrationInternal(subId, connection,
                sipCode, sipReason));
    }

    /**
     * @return Whether or not SipTransports are supported on the connected ImsService. This can
     * change based on the capabilities of the ImsService.
     * @throws ImsException if the ImsService connected to this controller is currently down.
     */
    public boolean isSupported(int subId) throws ImsException {
        Boolean result = waitForMethodToComplete(() -> isSupportedInternal(subId));
        if (result == null) {
            logw("isSupported, unexpected null result, returning false");
            return false;
        }
        return result;
    }

    private void createSipDelegateInternal(int subId, int uid, DelegateRequest request,
            String packageName, ISipDelegateConnectionStateCallback delegateState,
            ISipDelegateMessageCallback delegateMessage,
            Consumer<ImsException> startedErrorConsumer) {
        ISipTransport transport;
        IImsRegistration registration;
        // Send back any errors via Consumer early in creation process if it is clear that the
        // SipDelegate will never be created.
        try {
            checkStateOfController(subId);
            transport = mRcsManager.getSipTransport();
            registration = mRcsManager.getImsRegistration();
            if (transport == null) {
                logw("createSipDelegateInternal, transport null during request.");
                startedErrorConsumer.accept(new ImsException("SipTransport not supported",
                        ImsException.CODE_ERROR_UNSUPPORTED_OPERATION));
                return;
            } else {
                // Release the binder thread as there were no issues processing the initial request.
                startedErrorConsumer.accept(null);
            }
        } catch (ImsException e) {
            logw("createSipDelegateInternal, ImsException during create: " + e);
            startedErrorConsumer.accept(e);
            return;
        }

        SipDelegateController c = mDelegateControllerFactory.create(subId, uid, request,
                packageName, transport, registration, mExecutorService, delegateState,
                delegateMessage);
        logi("createSipDelegateInternal: request= " + request + ", packageName= " + packageName
                + ", controller created: " + c);
        addPendingCreateAndEvaluate(c);
        // If SipDialogStateCallback is registered, listener will be set.
        if (mSipDialogStateCallbacks.getRegisteredCallbackCount() > 0) {
            c.setSipDialogsListener(mSipDialogsListener, false);
        }
    }

    private void destroySipDelegateInternal(int subId, ISipDelegate connection, int reason) {
        if (subId != mSubId) {
            logw("destroySipDelegateInternal: ignoring destroy, as this is about to be destroyed "
                    + "anyway due to subId change, delegate=" + connection);
            return;
        }
        if (connection == null) {
            logw("destroySipDelegateInternal: ignoring destroy, null connection binder.");
            return;
        }
        SipDelegateController match = null;
        for (SipDelegateController controller : mDelegatePriorityQueue) {
            if (Objects.equal(connection.asBinder(),
                    controller.getSipDelegateInterface().asBinder())) {
                match = controller;
                break;
            }
        }
        if (match == null) {
            logw("destroySipDelegateInternal: could not find matching connection=" + connection);
            return;
        }

        logi("destroySipDelegateInternal: destroy queued for connection= " + connection);
        addPendingDestroyAndEvaluate(match, reason);
    }

    private void triggerFullNetworkRegistrationInternal(int subId, ISipDelegate connection,
            int sipCode, String sipReason) {
        if (subId != mSubId) {
            logw("triggerFullNetworkRegistrationInternal: ignoring network reg request, as this is"
                    + "about to be destroyed anyway due to subId change, delegate=" + connection);
            return;
        }
        if (connection == null) {
            logw("triggerFullNetworkRegistrationInternal: ignoring, null connection binder.");
            return;
        }
        // Ensure the requester has a valid SipDelegate registered.
        SipDelegateController match = null;
        for (SipDelegateController controller : mDelegatePriorityQueue) {
            if (Objects.equal(connection.asBinder(),
                    controller.getSipDelegateInterface().asBinder())) {
                match = controller;
                break;
            }
        }
        if (match == null) {
            logw("triggerFullNetworkRegistrationInternal: could not find matching connection, "
                    + "ignoring");
            return;
        }

        match.triggerFullNetworkRegistration(sipCode, sipReason);
    }

    /**
     * Cancel pending update IMS registration events if they exist and instead send a deregister
     * event.
     */
    private void triggerDeregistrationEvent() {
        logi("triggerDeregistrationEvent: Sending deregister event to ImsService");
        cancelPendingUpdateRegistration();

        IImsRegistration registrationImpl = mRcsManager.getImsRegistration();
        if (registrationImpl != null) {
            try {
                registrationImpl.triggerSipDelegateDeregistration();
            } catch (RemoteException e) {
                logi("triggerDeregistrationEvent: received RemoteException: " + e);
            }
        }
    }

    /**
     * Schedule an update to the IMS registration. If there is an existing update scheduled, cancel
     * it and reschedule.
     * <p>
     * We want to wait because this can directly result in changes to the IMS registration on the
     * network, so we need to wait for a steady state where all changes have been made before
     * triggering an update to the network registration.
     */
    private void scheduleUpdateRegistration() {
        cancelPendingUpdateRegistration();

        ScheduledFuture<?> f = mExecutorService.schedule(this::triggerUpdateRegistrationEvent,
                mTimerAdapter.getUpdateRegistrationDelayMilliseconds(), TimeUnit.MILLISECONDS);
        logi("scheduleUpdateRegistration: scheduling new event: " + f);
        mPendingUpdateRegistrationFuture = f;
    }

    /**
     * Cancel an existing pending task to update the IMS registration associated with SIP delegates.
     */
    private void cancelPendingUpdateRegistration() {
        if (mPendingUpdateRegistrationFuture == null
                || mPendingUpdateRegistrationFuture.isDone()) {
            return;
        }
        // Cancel the old pending operation and reschedule again.
        mPendingUpdateRegistrationFuture.cancel(false);
        logi("scheduleUpdateRegistration: cancelling existing reg update event: "
                + mPendingUpdateRegistrationFuture);
    }

    /**
     * Triggers an event to update the IMS registration of the ImsService. Should only be called
     * from {@link #scheduleUpdateRegistration()}.
     */
    private void triggerUpdateRegistrationEvent() {
        logi("triggerUpdateRegistrationEvent: Sending update registration event to ImsService");
        IImsRegistration registrationImpl = mRcsManager.getImsRegistration();
        if (registrationImpl != null) {
            try {
                registrationImpl.triggerUpdateSipDelegateRegistration();
            } catch (RemoteException e) {
                logi("triggerUpdateRegistrationEvent: received RemoteException: " + e);
            }
        }
    }

    /**
     * Returns whether or not the ImsService implementation associated with the supplied subId
     * supports the SipTransport APIs.
     * <p>
     * This should only be called on the ExecutorService.
     * @return true if SipTransport is supported on this subscription, false otherwise.
     * @throws ImsException thrown if there was an error determining the state of the ImsService.
     */
    private boolean isSupportedInternal(int subId) throws ImsException {
        checkStateOfController(subId);
        return (mRcsManager.getSipTransport() != null);
    }

    private boolean addPendingDestroy(SipDelegateController c, int reason) {
        DestroyRequest request = new DestroyRequest(c, reason);
        if (!mDelegatePendingDestroy.contains(request)) {
            return mDelegatePendingDestroy.add(request);
        }
        return false;
    }

    /**
     * The supplied SipDelegateController has been destroyed and associated feature tags have been
     * released. Trigger the re-evaluation of the priority queue with the new configuration.
     */
    private void addPendingDestroyAndEvaluate(SipDelegateController c, int reason) {
        if (addPendingDestroy(c, reason)) {
            scheduleThrottledReevaluate();
        }
    }

    /**
     * A new SipDelegateController has been created, add to the back of the priority queue and
     * trigger the re-evaluation of the priority queue with the new configuration.
     */
    private void addPendingCreateAndEvaluate(SipDelegateController c) {
        mDelegatePendingCreate.add(c);
        scheduleThrottledReevaluate();
    }

    /**
     * The priority queue has changed, which will cause a re-evaluation of the feature tags granted
     * to each SipDelegate.
     * <p>
     * Note: re-evaluations are throttled to happen at a minimum to occur every
     * REEVALUATE_THROTTLE_MS seconds. We also do not reevaluate while another reevaluate operation
     * is in progress, so in this case, defer schedule itself.
     */
    private void scheduleThrottledReevaluate() {
        if (isEvaluatePendingAndNotInProgress()) {
            logi("scheduleThrottledReevaluate: throttling reevaluate, eval already pending: "
                    + mScheduledEvaluateFuture);
        } else {
            mScheduledEvaluateFuture = mExecutorService.schedule(this::reevaluateDelegates,
                    mTimerAdapter.getReevaluateThrottleTimerMilliseconds(), TimeUnit.MILLISECONDS);
            logi("scheduleThrottledReevaluate: new reevaluate scheduled: "
                    + mScheduledEvaluateFuture);
        }
    }

    /**
     * @return true if there is a evaluate pending, false if there is not. If evaluate has already
     * begun, but we are waiting for it to complete, this will also return false.
     */
    private boolean isEvaluatePendingAndNotInProgress() {
        boolean isEvalScheduled = mScheduledEvaluateFuture != null
                && !mScheduledEvaluateFuture.isDone();
        boolean isEvalInProgress = mEvaluateCompleteFuture != null
                && !mEvaluateCompleteFuture.isDone();
        return isEvalScheduled && !isEvalInProgress;
    }

    /**
     * Cancel any pending re-evaluates and perform it as soon as possible. This is done in the case
     * where we need to do something like tear down this controller or change subId.
     */
    private void scheduleReevaluateNow(CompletableFuture<Void> onDoneFuture) {
        if (isEvaluatePendingAndNotInProgress()) {
            mScheduledEvaluateFuture.cancel(false /*interrupt*/);
            logi("scheduleReevaluateNow: cancelling pending reevaluate: "
                    + mScheduledEvaluateFuture);
        }
        // we have tasks that depend on this potentially, so once the last reevaluate is done,
        // schedule a new one right away.
        if (mEvaluateCompleteFuture != null && !mEvaluateCompleteFuture.isDone()) {
            mEvaluateCompleteFuture.thenRunAsync(
                    () -> scheduleReevaluateNow(onDoneFuture), mExecutorService);
            return;
        }

        reevaluateDelegates();
        mEvaluateCompleteFuture.thenAccept(onDoneFuture::complete);
    }

    /**
     * Apply all operations that have been pending by collecting pending create/destroy operations
     * and batch applying them to the mDelegatePriorityQueue.
     *
     * First perform the operation of destroying all SipDelegateConnections that have been pending
     * destroy. Next, add all pending new SipDelegateControllers to the end of
     * mDelegatePriorityQueue and loop through all in the queue, applying feature tags to the
     * appropriate SipDelegateController if they pass role checks and have not already been claimed
     * by another delegate higher in the priority queue.
     */
    private void reevaluateDelegates() {
        // We need to cancel the pending update now and reschedule IMS registration update for
        // after the reevaluate is complete.
        cancelPendingUpdateRegistration();
        if (mEvaluateCompleteFuture != null && !mEvaluateCompleteFuture.isDone()) {
            logw("reevaluateDelegates: last evaluate not done, deferring new request");
            // Defer re-evaluate until after the pending re-evaluate is complete.
            mEvaluateCompleteFuture.thenRunAsync(this::scheduleThrottledReevaluate,
                    mExecutorService);
            return;
        }

        // Remove tracking for all SipDelegates being destroyed first
        for (DestroyRequest d : mDelegatePendingDestroy) {
            logi("reevaluateDelegates: starting destroy for: " + d.controller.getPackageName());
            mActiveAppBinders.unregister(d.controller.getAppMessageCallback());
        }
        // Destroy all pending destroy delegates first. Order doesn't matter.
        List<CompletableFuture<Void>> pendingDestroyList = mDelegatePendingDestroy.stream()
                .map(d -> triggerDestroy(d.controller, d.reason)).collect(
                Collectors.toList());
        CompletableFuture<Void> pendingDestroy = CompletableFuture.allOf(
                pendingDestroyList.toArray(new CompletableFuture[mDelegatePendingDestroy.size()]));
        mDelegatePriorityQueue.removeAll(mDelegatePendingDestroy.stream().map(d -> d.controller)
                .collect(Collectors.toList()));
        mDelegatePendingDestroy.clear();

        // Add newly created SipDelegates to end of queue before evaluating associated features.
        mDelegatePriorityQueue.addAll(mDelegatePendingCreate);
        for (SipDelegateController c : mDelegatePendingCreate) {
            logi("reevaluateDelegates: pending create: " + c.getPackageName());
            mActiveAppBinders.register(c.getAppMessageCallback(), c);
        }
        mDelegatePendingCreate.clear();

        // Wait for destroy stages to complete, then loop from oldest to most recent and associate
        // feature tags that the app has requested to the SipDelegate.
        // Each feature tag can only be associated with one SipDelegate, so as feature tags are
        // taken, do not allow other SipDelegates to be associated with those tags as well. Each
        // stage of the CompletableFuture chain passes the previously claimed feature tags into the
        // next stage so that those feature tags can be denied if already claimed.
        // Executor doesn't matter here, just composing here to transform to the next stage.
        CompletableFuture<Set<String>> pendingChange = pendingDestroy.thenCompose((ignore) -> {
            logi("reevaluateDelegates: destroy phase complete");
            return CompletableFuture.completedFuture(new ArraySet<>());
        });
        final String cachedSmsRolePackage = mCachedSmsRolePackageName;
        for (SipDelegateController c : mDelegatePriorityQueue) {
            logi("reevaluateDelegates: pending reeval: " + c);
            pendingChange = pendingChange.thenComposeAsync((takenTags) -> {
                logi("reevaluateDelegates: last stage completed with result:" + takenTags);
                if (takenTags == null) {
                    // return early, the ImsService is no longer available. This will eventually be
                    // destroyed.
                    return CompletableFuture.completedFuture(null /*failed*/);
                }
                return changeSupportedFeatureTags(c, cachedSmsRolePackage, takenTags);
            }, mExecutorService);
        }

        // Executor doesn't matter here, schedule an event to update the IMS registration.
        mEvaluateCompleteFuture = pendingChange
                .whenComplete((f, ex) -> {
                    if (ex != null) {
                        logw("reevaluateDelegates: Exception caught: " + ex);
                    }
                }).thenAccept((associatedFeatures) -> {
                    logi("reevaluateDelegates: reevaluate complete, feature tags associated: "
                            + associatedFeatures);
                    scheduleUpdateRegistration();
                });
        logi("reevaluateDelegates: future created.");
    }

    private CompletableFuture<Void> triggerDestroy(SipDelegateController c, int reason) {
        return c.destroy(isForcedFromReason(reason), reason)
                // Executor doesn't matter here, just for logging.
                .thenAccept((delegateReason) -> logi("destroy triggered with " + reason
                        + " and finished with reason= " + delegateReason));
    }

    private boolean isForcedFromReason(int reason) {
        switch (reason) {
            case SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_UNKNOWN:
                logw("isForcedFromReason, unknown reason");
                /*intentional fallthrough*/
            case SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP:
                /*intentional fallthrough*/
            case SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_USER_DISABLED_RCS:
                return false;
            case SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SERVICE_DEAD:
                /*intentional fallthrough*/
            case SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SUBSCRIPTION_TORN_DOWN:
                return true;
        }
        logw("isForcedFromReason, unexpected reason: " + reason);
        return false;
    }

    /**
     * Called by RoleManager when a role has changed so that we can query the new role holder.
     * @param roleName the name of the role whose holders are changed
     * @param user the user for this role holder change
     */
    // Called on mExecutorThread
    @Override
    public void onRoleHoldersChanged(@NonNull String roleName, @NonNull UserHandle user) {
        logi("onRoleHoldersChanged, roleName= " + roleName + ", user= " + user);
        // Only monitor changes on the system
        if (!UserHandle.SYSTEM.equals(user)) {
            return;
        }

        if (!RoleManager.ROLE_SMS.equals(roleName)) {
            logi("onRoleHoldersChanged, ignoring non SMS role change");
            // TODO: only target default sms app for now and add new roles later using
            // CarrierConfigManager
            return;
        }
        boolean roleChanged = updateRoleCache();
        if (roleChanged) {
            if (!TextUtils.isEmpty(mCachedSmsRolePackageName)) {
                // the role change event will go A -> "" and then "" -> B. In order to not send two
                // events to the modem, only trigger the deregistration event when we move to a
                // non-empty package name.
                triggerDeregistrationEvent();
            }
            // new denied tags will be picked up when reevaluate completes.
            scheduleThrottledReevaluate();
        }
    }


    /**
     * @return true, if the role cache has changed, false otherwise.
     */
    private boolean updateRoleCache() {
        String newSmsRolePackageName = "";
        try {
            // Only one app can fulfill the SMS role.
            newSmsRolePackageName = mRoleManagerAdapter.getRoleHolders(RoleManager.ROLE_SMS)
                    .stream().findFirst().orElse("");
        } catch (Exception e) {
            logi("updateRoleCache: exception=" + e);
        }

        logi("updateRoleCache: new packageName=" + newSmsRolePackageName);
        if (TextUtils.equals(mCachedSmsRolePackageName, newSmsRolePackageName)) {
            logi("updateRoleCache, skipping, role did not change");
            return false;
        }
        mCachedSmsRolePackageName = newSmsRolePackageName;
        return true;
    }

    /**
     * Check the requested roles for the specified package name and return the tags that were
     * applied to that SipDelegateController.
     * @param controller Controller to attribute feature tags to.
     * @param alreadyRequestedTags The feature tags that were already granted to other SipDelegates.
     * @return Once complete, contains the set of feature tags that the SipDelegate now has
     * associated with it along with the feature tags that previous SipDelegates had.
     *
     * // TODO: we currently only track SMS role, extend to support other roles as well.
     */
    private CompletableFuture<Set<String>> changeSupportedFeatureTags(
            SipDelegateController controller, String smsRolePackageName,
            Set<String> alreadyRequestedTags) {
        Set<String> requestedFeatureTags = controller.getInitialRequest().getFeatureTags();
        String packageName = controller.getPackageName();
        if (!smsRolePackageName.equals(packageName)) {
            // Deny all tags.
            Set<FeatureTagState> deniedTags = new ArraySet<>();
            for (String s : requestedFeatureTags) {
                deniedTags.add(new FeatureTagState(s,
                        SipDelegateManager.DENIED_REASON_NOT_ALLOWED));
            }
            CompletableFuture<Boolean> pendingDeny = controller.changeSupportedFeatureTags(
                    Collections.emptySet(), deniedTags);
            logi("changeSupportedFeatureTags pendingDeny=" + pendingDeny);
            // do not worry about executor used here, this stage used to interpret result + add log.
            return pendingDeny.thenApply((completedSuccessfully) ->  {
                logi("changeSupportedFeatureTags: deny completed: " + completedSuccessfully);
                if (!completedSuccessfully) return null;
                // Return back the previous list of requested tags, as we did not add any more.
                return alreadyRequestedTags;
            });
        }

        ArraySet<String> previouslyGrantedTags = new ArraySet<>(alreadyRequestedTags);
        ArraySet<String> candidateFeatureTags = new ArraySet<>(requestedFeatureTags);
        Set<FeatureTagState> deniedTags =
                updateSupportedTags(candidateFeatureTags, previouslyGrantedTags);

        // Add newly granted tags to the already requested tags list.
        previouslyGrantedTags.addAll(candidateFeatureTags);
        CompletableFuture<Boolean> pendingChange = controller.changeSupportedFeatureTags(
                candidateFeatureTags, deniedTags);
        logi("changeSupportedFeatureTags pendingChange=" + pendingChange);
        // do not worry about executor used here, this stage used to interpret result + add log.
        return pendingChange.thenApply((completedSuccessfully) ->  {
            logi("changeSupportedFeatureTags: change completed: " + completedSuccessfully);
            if (!completedSuccessfully) return null;
            return previouslyGrantedTags;
        });
    }

    /**
     * Update candidate feature tags according to feature tags allowed by carrier config,
     * and previously granted by other SipDelegates.
     *
     * @param candidateFeatureTags The candidate feature tags to be updated. It will be
     * updated as needed per the carrier config and previously granted feature tags.
     * @param previouslyGrantedTags The feature tags already granted by other SipDelegates.
     * @return The set of denied feature tags.
     */
    private Set<FeatureTagState> updateSupportedTags(Set<String> candidateFeatureTags,
            Set<String> previouslyGrantedTags) {
        Boolean overrideRes = RcsProvisioningMonitor.getInstance()
                .getImsFeatureValidationOverride(mSubId);
        // deny tags already used by other delegates
        Set<FeatureTagState> deniedTags = new ArraySet<>();

        // match config if feature validation is not overridden
        if (overrideRes == null) {
            Iterator<String> it = candidateFeatureTags.iterator();
            while (it.hasNext()) {
                String tag = it.next();
                if (previouslyGrantedTags.contains(tag)) {
                    logi(tag + " has already been granted previously.");
                    it.remove();
                    deniedTags.add(new FeatureTagState(tag,
                            SipDelegateManager.DENIED_REASON_IN_USE_BY_ANOTHER_DELEGATE));
                } else if (!mFeatureTagsAllowed.contains(tag.trim().toLowerCase(Locale.ROOT))) {
                    logi(tag + " is not allowed per config.");
                    it.remove();
                    deniedTags.add(new FeatureTagState(tag,
                              SipDelegateManager.DENIED_REASON_NOT_ALLOWED));
                }
            }
        } else if (Boolean.FALSE.equals(overrideRes)) {
            logi("all features are denied for test purpose.");
            for (String s : candidateFeatureTags) {
                deniedTags.add(new FeatureTagState(s,
                        SipDelegateManager.DENIED_REASON_NOT_ALLOWED));
            }
            candidateFeatureTags.clear();
        }

        return deniedTags;
    }

    /**
     * Run a Callable on the ExecutorService Thread and wait for the result.
     * If an ImsException is thrown, catch it and rethrow it to caller.
     */
    private <T> T waitForMethodToComplete(Callable<T> callable) throws ImsException {
        Future<T> r = mExecutorService.submit(callable);
        T result;
        try {
            result = r.get();
        } catch (InterruptedException e) {
            result = null;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ImsException) {
                // Rethrow the exception
                throw (ImsException) cause;
            }
            logw("Unexpected Exception, returning null: " + cause);
            result = null;
        }
        return result;
    }

    /**
     * Throw an ImsException for common scenarios where the state of the controller is not ready
     * for communication.
     * <p>
     * This should only be called while running on the on the ExecutorService.
     */
    private void checkStateOfController(int subId) throws ImsException {
        if (mSubId != subId) {
            // sub ID has changed while this was in the queue.
            throw new ImsException("subId is no longer valid for this request.",
                    ImsException.CODE_ERROR_INVALID_SUBSCRIPTION);
        }
        if (mRcsManager == null) {
            throw new ImsException("Connection to ImsService is not available",
                    ImsException.CODE_ERROR_SERVICE_UNAVAILABLE);
        }
    }

    private void onRcsManagerChanged(RcsFeatureManager m) {
        logi("manager changed, " + mRcsManager + "->" + m);
        if (mRcsManager == m) return;
        mRcsManager = m;
        if (mRcsManager == null) {
            logi("onRcsManagerChanged: lost connection to ImsService, tearing down...");
            unregisterListeners();
            scheduleDestroyDelegates(SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SERVICE_DEAD);
        } else {
            logi("onRcsManagerChanged: registering listeners/updating role cache...");
            registerListeners();
            updateRoleCache();
        }
    }

    private void registerListeners() {
        try {
            mRoleManagerAdapter.addOnRoleHoldersChangedListenerAsUser(mExecutorService, this,
                    UserHandle.SYSTEM);
        } catch (Exception e) {
            logi("registerListeners: exception=" + e);
        }
    }

    private void unregisterListeners() {
        mCachedSmsRolePackageName = "";
        mRoleManagerAdapter.removeOnRoleHoldersChangedListenerAsUser(this, UserHandle.SYSTEM);
    }

    /**
     * Called when the sub ID associated with the slot has changed.
     */
    private void onSubIdChanged(int newSubId) {
        logi("subId changed, " + mSubId + "->" + newSubId);
        if (mSubId != newSubId) {
            // Swap subId, any pending create/destroy on old subId will be denied.
            mSubId = newSubId;
            scheduleDestroyDelegates(
                    SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SUBSCRIPTION_TORN_DOWN);
        }

        onCarrierConfigChangedInternal();
    }

    /**
     * Called when the carrier configuration associated with the same subId has changed.
     */
    private void onCarrierConfigChangedInternal() {
        logi("Carrier Config changed for subId: " + mSubId);
        mFeatureTagsAllowed.clear();
        PersistableBundle carrierConfig = mCarrierConfigManager.getConfigForSubId(mSubId);
        String[] tagConfigs = carrierConfig.getStringArray(
                CarrierConfigManager.Ims.KEY_RCS_FEATURE_TAG_ALLOWED_STRING_ARRAY);
        if (tagConfigs != null && tagConfigs.length > 0) {
            for (String tag : tagConfigs) {
                mFeatureTagsAllowed.add(tag.trim().toLowerCase(Locale.ROOT));
            }
        }
    }

    /**
     * Destroy all tracked SipDelegateConnections due to the subscription being torn down.
     * @return A CompletableFuture that will complete when all SipDelegates have been torn down.
     */
    private CompletableFuture<Void> scheduleDestroyDelegates(int reason) {
        boolean addedDestroy = false;
        for (SipDelegateController c : mDelegatePriorityQueue) {
            logi("scheduleDestroyDelegates: Controller pending destroy: " + c);
            addedDestroy |= addPendingDestroy(c, reason);
        }
        if (addedDestroy) {
            CompletableFuture<Void> pendingDestroy = new CompletableFuture<>();
            scheduleReevaluateNow(pendingDestroy);
            return pendingDestroy;
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Adds a callback that gets called when SipDialog status has changed.
     * @param subId The subId associated with the request.
     * @param cb A {@link android.telephony.ims.SipDialogStateCallback} that will notify the caller
     *          when Dialog status has changed.
     */
    public void addCallbackForSipDialogState(int subId, ISipDialogStateCallback cb) {
        if (subId != mSubId) {
            logw("addCallbackForSipDialogState the subId is not supported");
            return;
        }
        // callback register and no delegate : register this callback / notify (empty state)
        // callback register and delegates : register this callback / release listener / notify
        mSipDialogStateCallbacks.register(cb);
        if (!mDelegatePriorityQueue.isEmpty()) {
            for (SipDelegateController dc : mDelegatePriorityQueue) {
                dc.setSipDialogsListener(mSipDialogsListener, true);
            }
        } else {
            mSipDialogsListener.notifySipDialogState();
        }
    }

    /**
     * Unregister previously registered callback
     * @param subId The subId associated with the request.
     * @param cb A {@link android.telephony.ims.SipDialogStateCallback} that will be unregistering.
     */
    public void removeCallbackForSipDialogState(int subId, ISipDialogStateCallback cb) {
        if (subId != mSubId) {
            logw("addCallbackForSipDialogState the subId is not supported");
            return;
        }
        if (cb == null) {
            throw new IllegalArgumentException("callback is null");
        }

        // remove callback register and no delegate : only unregister this callback
        // remove callback register and delegates :
        // unregister this callback and setListener(null)
        mSipDialogStateCallbacks.unregister(cb);
        if (mSipDialogStateCallbacks.getRegisteredCallbackCount() == 0) {
            if (!mDelegatePriorityQueue.isEmpty()) {
                for (SipDelegateController dc : mDelegatePriorityQueue) {
                    dc.setSipDialogsListener(null, false);
                }
            }
        }
    }

    @Override
    public void dump(PrintWriter printWriter) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println("SipTransportController" + "[" + mSlotId  + "->" + mSubId + "]:");
        pw.increaseIndent();
        pw.println("LocalLog:");
        pw.increaseIndent();
        mLocalLog.dump(pw);
        pw.decreaseIndent();
        pw.println("SipDelegateControllers (in priority order):");
        pw.increaseIndent();
        if (mDelegatePriorityQueue.isEmpty()) {
            pw.println("[NONE]");
        } else {
            for (SipDelegateController c : mDelegatePriorityQueue) {
                c.dump(pw);
            }
        }
        pw.decreaseIndent();
        pw.decreaseIndent();
    }

    private void logi(String log) {
        Log.i(LOG_TAG, "[" + mSlotId  + "->" + mSubId + "] " + log);
        mLocalLog.log("[I] " + log);
    }

    private void logw(String log) {
        Log.w(LOG_TAG, "[" + mSlotId  + "->" + mSubId + "] " + log);
        mLocalLog.log("[W] " + log);
    }
}
