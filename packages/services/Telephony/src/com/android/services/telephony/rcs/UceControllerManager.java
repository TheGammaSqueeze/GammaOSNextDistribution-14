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

import android.annotation.Nullable;
import android.content.Context;
import android.net.Uri;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ImsException;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.RcsUceAdapter.PublishState;
import android.telephony.ims.aidl.IRcsUceControllerCallback;
import android.telephony.ims.aidl.IRcsUcePublishStateCallback;
import android.util.IndentingPrintWriter;
import android.util.Log;

import com.android.ims.RcsFeatureManager;
import com.android.ims.rcs.uce.UceController;
import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Responsible for managing the creation and destruction of UceController. It also received the
 * requests from {@link com.android.phone.ImsRcsController} and pass these requests to
 * {@link UceController}
 */
public class UceControllerManager implements RcsFeatureController.Feature {

    private static final String LOG_TAG = "UceControllerManager";

    private final int mSlotId;
    private final Context mContext;
    private final ExecutorService mExecutorService;

    private volatile @Nullable UceController mUceController;
    private volatile @Nullable RcsFeatureManager mRcsFeatureManager;

    public UceControllerManager(Context context, int slotId, int subId) {
        Log.d(LOG_TAG, "create: slotId=" + slotId + ", subId=" + subId);
        mSlotId = slotId;
        mContext = context;
        mExecutorService = Executors.newSingleThreadExecutor();
        initUceController(subId);
    }

    /**
     * Constructor to inject dependencies for testing.
     */
    @VisibleForTesting
    public UceControllerManager(Context context, int slotId, ExecutorService executor,
            UceController uceController) {
        mSlotId = slotId;
        mContext = context;
        mExecutorService = executor;
        mUceController = uceController;
    }

    @Override
    public void onRcsConnected(RcsFeatureManager manager) {
        mExecutorService.submit(() -> {
            mRcsFeatureManager = manager;
            if (mUceController != null) {
                mUceController.onRcsConnected(manager);
            } else {
                Log.d(LOG_TAG, "onRcsConnected: UceController is null");
            }
        });
    }

    @Override
    public void onRcsDisconnected() {
        mExecutorService.submit(() -> {
            mRcsFeatureManager = null;
            if (mUceController != null) {
                mUceController.onRcsDisconnected();
            } else {
                Log.d(LOG_TAG, "onRcsDisconnected: UceController is null");
            }
        });
    }

    @Override
    public void onDestroy() {
        mExecutorService.submit(() -> {
            Log.d(LOG_TAG, "onDestroy");
            if (mUceController != null) {
                mUceController.onDestroy();
            }
        });
        // When the shutdown is called, it will refuse any new tasks and let existing tasks finish.
        mExecutorService.shutdown();
    }

    /**
     * This method will be called when the subscription ID associated with the slot has
     * changed.
     */
    @Override
    public void onAssociatedSubscriptionUpdated(int newSubId) {
        mExecutorService.submit(() -> {
            Log.i(LOG_TAG, "onAssociatedSubscriptionUpdated: slotId=" + mSlotId
                    + ", newSubId=" + newSubId);

            // Check and create the UceController with the new updated subscription ID.
            initUceController(newSubId);

            // The RCS should be connected when the mRcsFeatureManager is not null. Set it to the
            // new UceController instance.
            if (mUceController != null && mRcsFeatureManager != null) {
                mUceController.onRcsConnected(mRcsFeatureManager);
            }
        });
    }

    /**
     * This method will be called when the carrier config of the subscription associated with this
     * manager has changed.
     */
    @Override
    public void onCarrierConfigChanged() {
        mExecutorService.submit(() -> {
            Log.i(LOG_TAG, "onCarrierConfigChanged");
            if (mUceController != null) {
                mUceController.onCarrierConfigChanged();
            } else {
                Log.d(LOG_TAG, "onCarrierConfigChanged: UceController is null");
            }
        });
    }

    /**
     * Request the capabilities for contacts.
     *
     * @param contactNumbers A list of numbers that the capabilities are being requested for.
     * @param c A callback for when the request for capabilities completes.
     * @throws ImsException if the ImsService connected to this controller is currently down.
     */
    public void requestCapabilities(List<Uri> contactNumbers, IRcsUceControllerCallback c)
            throws ImsException {
        Future future = mExecutorService.submit(() -> {
            checkUceControllerState();
            mUceController.requestCapabilities(contactNumbers, c);
            return true;
        });

        try {
            future.get();
        } catch (ExecutionException | InterruptedException e) {
            Log.w(LOG_TAG, "requestCapabilities: " + e);
            Throwable cause = e.getCause();
            if (cause instanceof ImsException) {
                throw (ImsException) cause;
            }
        }
    }

    /**
     * Request the capabilities for the given contact.
     * @param contactNumber The contact of the capabilities are being requested for.
     * @param c A callback for when the request for capabilities completes.
     * @throws ImsException if the ImsService connected to this controller is currently down.
     */
    public void requestNetworkAvailability(Uri contactNumber, IRcsUceControllerCallback c)
            throws ImsException {
        Future future = mExecutorService.submit(() -> {
            checkUceControllerState();
            mUceController.requestAvailability(contactNumber, c);
            return true;
        });

        try {
            future.get();
        } catch (ExecutionException | InterruptedException e) {
            Log.w(LOG_TAG, "requestNetworkAvailability exception: " + e);
            Throwable cause = e.getCause();
            if (cause instanceof ImsException) {
                throw (ImsException) cause;
            }
        }
    }

    /**
     * Get the UCE publish state.
     *
     * @throws ImsException if the ImsService connected to this controller is currently down.
     */
    public @PublishState int getUcePublishState(boolean isSupportPublishingState)
            throws ImsException {
        Future<Integer> future = mExecutorService.submit(() -> {
            checkUceControllerState();
            return mUceController.getUcePublishState(isSupportPublishingState);
        });

        try {
            return future.get();
        } catch (ExecutionException | InterruptedException e) {
            Log.w(LOG_TAG, "getUcePublishState exception: " + e);
            Throwable cause = e.getCause();
            if (cause instanceof ImsException) {
                throw (ImsException) cause;
            }
            return RcsUceAdapter.PUBLISH_STATE_OTHER_ERROR;
        }
    }

    /**
     * Add new feature tags to the Set used to calculate the capabilities in PUBLISH.
     */
    public RcsContactUceCapability addUceRegistrationOverride(
            Set<String> featureTags) throws ImsException {
        Future<RcsContactUceCapability> future = mExecutorService.submit(() -> {
            checkUceControllerState();
            return mUceController.addRegistrationOverrideCapabilities(featureTags);
        });

        try {
            return future.get();
        } catch (ExecutionException | InterruptedException e) {
            Log.w(LOG_TAG, "addUceRegistrationOverride exception: " + e);
            Throwable cause = e.getCause();
            if (cause instanceof ImsException) {
                throw (ImsException) cause;
            }
            return null;
        }
    }

    /**
     * Remove existing feature tags to the Set used to calculate the capabilities in PUBLISH.
     */
    public RcsContactUceCapability removeUceRegistrationOverride(
            Set<String> featureTags) throws ImsException {
        Future<RcsContactUceCapability> future = mExecutorService.submit(() -> {
            checkUceControllerState();
            return mUceController.removeRegistrationOverrideCapabilities(featureTags);
        });

        try {
            return future.get();
        } catch (ExecutionException | InterruptedException e) {
            Log.w(LOG_TAG, "removeUceRegistrationOverride exception: " + e);
            Throwable cause = e.getCause();
            if (cause instanceof ImsException) {
                throw (ImsException) cause;
            }
            return null;
        }
    }

    /**
     * Clear all overrides in the Set used to calculate the capabilities in PUBLISH.
     */
    public RcsContactUceCapability clearUceRegistrationOverride() throws ImsException {
        Future<RcsContactUceCapability> future = mExecutorService.submit(() -> {
            checkUceControllerState();
            return mUceController.clearRegistrationOverrideCapabilities();
        });

        try {
            return future.get();
        } catch (ExecutionException | InterruptedException e) {
            Log.w(LOG_TAG, "clearUceRegistrationOverride exception: " + e);
            Throwable cause = e.getCause();
            if (cause instanceof ImsException) {
                throw (ImsException) cause;
            }
            return null;
        }
    }

    /**
     * @return current RcsContactUceCapability instance that will be used for PUBLISH.
     */
    public RcsContactUceCapability getLatestRcsContactUceCapability() throws ImsException {
        Future<RcsContactUceCapability> future = mExecutorService.submit(() -> {
            checkUceControllerState();
            return mUceController.getLatestRcsContactUceCapability();
        });

        try {
            return future.get();
        } catch (ExecutionException | InterruptedException e) {
            Log.w(LOG_TAG, "getLatestRcsContactUceCapability exception: " + e);
            Throwable cause = e.getCause();
            if (cause instanceof ImsException) {
                throw (ImsException) cause;
            }
            return null;
        }
    }

    /**
     *
     * @return The last PIDF XML sent to the IMS stack to be published.
     */
    public String getLastPidfXml() throws ImsException {
        Future<String> future = mExecutorService.submit(() -> {
            checkUceControllerState();
            return mUceController.getLastPidfXml();
        });

        try {
            return future.get();
        } catch (ExecutionException | InterruptedException e) {
            Log.w(LOG_TAG, "getLastPidfXml exception: " + e);
            Throwable cause = e.getCause();
            if (cause instanceof ImsException) {
                throw (ImsException) cause;
            }
            return null;
        }
    }

    /**
     * Remove UCE requests cannot be sent to the network status.
     * @return true if this command is successful.
     */
    public boolean removeUceRequestDisallowedStatus() throws ImsException {
        Future<Boolean> future = mExecutorService.submit(() -> {
            if (mUceController == null) {
                throw new ImsException("UCE controller is null",
                        ImsException.CODE_ERROR_SERVICE_UNAVAILABLE);
            }
            mUceController.removeRequestDisallowedStatus();
            return true;
        });

        try {
            return future.get();
        } catch (ExecutionException | InterruptedException e) {
            Log.w(LOG_TAG, "removeUceRequestDisallowedStatus exception: " + e);
            Throwable cause = e.getCause();
            if (cause instanceof ImsException) {
                throw (ImsException) cause;
            }
            return false;
        }
    }

    /**
     * Set the timeout for contact capabilities request.
     * @param timeoutAfterMs How long when the capabilities request will time up.
     * @return true if this command is successful.
     */
    public boolean setCapabilitiesRequestTimeout(long timeoutAfterMs)  throws ImsException {
        Future<Boolean> future = mExecutorService.submit(() -> {
            if (mUceController == null) {
                throw new ImsException("UCE controller is null",
                        ImsException.CODE_ERROR_SERVICE_UNAVAILABLE);
            }
            mUceController.setCapabilitiesRequestTimeout(timeoutAfterMs);
            return true;
        });

        try {
            return future.get();
        } catch (ExecutionException | InterruptedException e) {
            Log.w(LOG_TAG, "setCapabilitiesRequestTimeout exception: " + e);
            Throwable cause = e.getCause();
            if (cause instanceof ImsException) {
                throw (ImsException) cause;
            }
            return false;
        }
    }

    /**
     * Register the Publish state changed callback.
     *
     * @throws ImsException if the ImsService connected to this controller is currently down.
     */
    public void registerPublishStateCallback(IRcsUcePublishStateCallback c,
            boolean supportPublishingState) throws ImsException {
        Future future = mExecutorService.submit(() -> {
            checkUceControllerState();
            mUceController.registerPublishStateCallback(c, supportPublishingState);
            return true;
        });

        try {
            future.get();
        } catch (ExecutionException | InterruptedException e) {
            Log.w(LOG_TAG, "registerPublishStateCallback exception: " + e);
            Throwable cause = e.getCause();
            if (cause instanceof ImsException) {
                throw (ImsException) cause;
            }
        }
    }

    /**
     * Unregister the existing publish state changed callback.
     */
    public void unregisterPublishStateCallback(IRcsUcePublishStateCallback c) {
        Future future = mExecutorService.submit(() -> {
            if (checkUceControllerState()) {
                mUceController.unregisterPublishStateCallback(c);
            }
            return true;
        });

        try {
            future.get();
        } catch (ExecutionException | InterruptedException e) {
            Log.w(LOG_TAG, "unregisterPublishStateCallback exception: " + e);
        }
    }

    /**
     * Initialize the UceController instance associated with the given subscription ID.
     * The existing UceController will be destroyed if the original subscription ID is different
     * from the new subscription ID.
     * If the new subscription ID is invalid, the UceController instance will be null.
     */
    private void initUceController(int newSubId) {
        Log.d(LOG_TAG, "initUceController: newSubId=" + newSubId + ", current UceController subId="
                + ((mUceController == null) ? "null" : mUceController.getSubId()));
        if (mUceController == null) {
            // Create new UceController only when the subscription ID is valid.
            if (SubscriptionManager.isValidSubscriptionId(newSubId)) {
                mUceController = new UceController(mContext, newSubId);
            }
        } else if (mUceController.getSubId() != newSubId) {
            // The subscription ID is updated. Remove the old UceController instance.
            mUceController.onDestroy();
            mUceController = null;
            // Create new UceController only when the subscription ID is valid.
            if (SubscriptionManager.isValidSubscriptionId(newSubId)) {
                mUceController = new UceController(mContext, newSubId);
            }
        }
    }

    private boolean checkUceControllerState() throws ImsException {
        if (mUceController == null || mUceController.isUnavailable()) {
            throw new ImsException("UCE controller is unavailable",
                    ImsException.CODE_ERROR_SERVICE_UNAVAILABLE);
        }
        return true;
    }

    /**
     * Get the UceController instance.
     * <p>
     * Used for testing ONLY.
     */
    @VisibleForTesting
    public UceController getUceController() {
        return mUceController;
    }

    @Override
    public void dump(PrintWriter printWriter) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println("UceControllerManager" + "[" + mSlotId + "]:");
        pw.increaseIndent();
        if (mUceController != null) {
            mUceController.dump(pw);
        } else {
            pw.println("UceController is null.");
        }
        pw.decreaseIndent();
    }
}
