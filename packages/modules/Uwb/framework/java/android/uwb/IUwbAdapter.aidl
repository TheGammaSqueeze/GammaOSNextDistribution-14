/*
 * Copyright 2020 The Android Open Source Project
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

package android.uwb;

import android.content.AttributionSource;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.uwb.IUwbAdapterStateCallbacks;
import android.uwb.IUwbAdfProvisionStateCallbacks;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.SessionHandle;
import android.uwb.UwbAddress;
import android.uwb.IUwbVendorUciCallback;
import android.uwb.IUwbOemExtensionCallback;
import android.uwb.IOnUwbActivityEnergyInfoListener;

/**
 * @hide
 * TODO(b/211025367): Remove all the duplicate javadocs here.
 */
interface IUwbAdapter {
  /*
   * Register the callbacks used to notify the framework of events and data
   *
   * The provided callback's IUwbAdapterStateCallbacks#onAdapterStateChanged
   * function must be called immediately following registration with the current
   * state of the UWB adapter.
   *
   * @param callbacks callback to provide range and status updates to the framework
   */
  void registerAdapterStateCallbacks(in IUwbAdapterStateCallbacks adapterStateCallbacks);

   /*
    * Register the callbacks used to notify the framework of events and data
    *
    * The provided callback's IUwbUciVendorCallback#onVendorNotificationReceived
    * function must be called immediately following vendorNotification received
    *
    * @param callbacks callback to provide Notification data updates to the framework
    */
   void registerVendorExtensionCallback(in IUwbVendorUciCallback callbacks);

   /*
    * Unregister the callbacks used to notify the framework of events and data
    *
    * Calling this function with an unregistered callback is a no-op
    *
    * @param callbacks callback to unregister
    */
   void unregisterVendorExtensionCallback(in IUwbVendorUciCallback callbacks);

   /*
   * Unregister the callbacks used to notify the framework of events and data
   *
   * Calling this function with an unregistered callback is a no-op
   *
   * @param callbacks callback to unregister
   */
  void unregisterAdapterStateCallbacks(in IUwbAdapterStateCallbacks callbacks);

  void registerOemExtensionCallback(in IUwbOemExtensionCallback callbacks);

  void unregisterOemExtensionCallback(in IUwbOemExtensionCallback callbacks);

  /**
   * Get the accuracy of the ranging timestamps
   *
   * @param chipId identifier of UWB chip for multi-HAL devices
   *
   * @return accuracy of the ranging timestamps in nanoseconds
   */
  long getTimestampResolutionNanos(in String chipId);

  /**
   * Provides the capabilities and features of the device
   *
   * @param chipId identifier of UWB chip for multi-HAL devices
   *
   * @return specification specific capabilities and features of the device
   */
  PersistableBundle getSpecificationInfo(in String chipId);

  /**
   * Request to open a new ranging session
   *
   * This function does not start the ranging session, but all necessary
   * components must be initialized and ready to start a new ranging
   * session prior to calling IUwbAdapterCallback#onRangingOpened.
   *
   * IUwbAdapterCallbacks#onRangingOpened must be called within
   * RANGING_SESSION_OPEN_THRESHOLD_MS milliseconds of #openRanging being
   * called if the ranging session is opened successfully.
   *
   * IUwbAdapterCallbacks#onRangingOpenFailed must be called within
   * RANGING_SESSION_OPEN_THRESHOLD_MS milliseconds of #openRanging being called
   * if the ranging session fails to be opened.
   *
   * If the provided sessionHandle is already open for the calling client, then
   * #onRangingOpenFailed must be called and the new session must not be opened.
   *
   * @param attributionSource AttributionSource to use for permission enforcement.
   * @param sessionHandle the session handle to open ranging for
   * @param rangingCallbacks the callbacks used to deliver ranging information
   * @param parameters the configuration to use for ranging
   * @param chipId identifier of UWB chip for multi-HAL devices
   */
  void openRanging(in AttributionSource attributionSource,
                   in SessionHandle sessionHandle,
                   in IUwbRangingCallbacks rangingCallbacks,
                   in PersistableBundle parameters,
                   in String chipId);

  /**
   * Request to start ranging
   *
   * IUwbAdapterCallbacks#onRangingStarted must be called within
   * RANGING_SESSION_START_THRESHOLD_MS milliseconds of #startRanging being
   * called if the ranging session starts successfully.
   *
   * IUwbAdapterCallbacks#onRangingStartFailed must be called within
   * RANGING_SESSION_START_THRESHOLD_MS milliseconds of #startRanging being
   * called if the ranging session fails to be started.
   *
   * @param sessionHandle the session handle to start ranging for
   * @param parameters additional configuration required to start ranging
   */
  void startRanging(in SessionHandle sessionHandle,
                    in PersistableBundle parameters);

  /**
   * Request to reconfigure ranging
   *
   * IUwbAdapterCallbacks#onRangingReconfigured must be called after
   * successfully reconfiguring the session.
   *
   * IUwbAdapterCallbacks#onRangingReconfigureFailed must be called after
   * failing to reconfigure the session.
   *
   * A session must not be modified by a failed call to #reconfigureRanging.
   *
   * @param sessionHandle the session handle to start ranging for
   * @param parameters the parameters to reconfigure and their new values
   */
  void reconfigureRanging(in SessionHandle sessionHandle,
                          in PersistableBundle parameters);

  /**
   * Request to stop ranging
   *
   * IUwbAdapterCallbacks#onRangingStopped must be called after
   * successfully stopping the session.
   *
   * IUwbAdapterCallbacks#onRangingStopFailed must be called after failing
   * to stop the session.
   *
   * @param sessionHandle the session handle to stop ranging for
   */
  void stopRanging(in SessionHandle sessionHandle);

  /**
   * Close ranging for the session associated with the given handle
   *
   * Calling with an invalid handle or a handle that has already been closed
   * is a no-op.
   *
   * IUwbAdapterCallbacks#onRangingClosed must be called within
   * RANGING_SESSION_CLOSE_THRESHOLD_MS of #closeRanging being called.
   *
   * @param sessionHandle the session handle to close ranging for
   */
  void closeRanging(in SessionHandle sessionHandle);

  /**
   * Add a new controlee to an ongoing session.
   * <p>This call may be made when the session is open.
   *
   * <p>On successfully adding a new controlee to the session
   * {@link RangingSession.Callback#onControleeAdded(PersistableBundle)} is invoked.
   *
   * <p>On failure to add a new controlee to the session,
   * {@link RangingSession.Callback#onControleeAddFailed(int, PersistableBundle)}is invoked.
   *
   * @param sessionHandle the session handle to close ranging for
   * @param params the parameters for the new controlee.
   */
  void addControlee(in SessionHandle sessionHandle, in PersistableBundle params);

  /**
   * Remove an existing controlee from an ongoing session.
   * <p>This call may be made when the session is open.
   *
   * <p>On successfully removing an existing controlee from the session
   * {@link RangingSession.Callback#onControleeRemoved(PersistableBundle)} is invoked.
   *
   * <p>On failure to remove an existing controlee from the session,
   * {@link RangingSession.Callback#onControleeRemoveFailed(int, PersistableBundle)}is invoked.
   *
   * @param sessionHandle the session handle to close ranging for
   * @param params the parameters for the existing controlee.
   */
  void removeControlee(in SessionHandle sessionHandle, in PersistableBundle params);

  /**
   * Suspends an ongoing ranging session.
   *
   * <p>A session that has been pauseed may be resumed by calling
   * {@link RangingSession#resume(PersistableBundle)} without the need to open a new session.
   *
   * <p>Suspending a {@link RangingSession} is useful when the lower layers should skip a few
   * ranging rounds for a session without stopping it.
   *
   * <p>If the {@link RangingSession} is no longer needed, use {@link RangingSession#stop()} or
   * {@link RangingSession#close()} to completely close the session.
   *
   * <p>On successfully pauseing the session,
   * {@link RangingSession.Callback#onPaused(PersistableBundle)} is invoked.
   *
   * <p>On failure to pause the session,
   * {@link RangingSession.Callback#onPauseFailed(int, PersistableBundle)} is invoked.
   *
   * @param sessionHandle the session handle to close ranging for
   * @param params protocol specific parameters for pauseing the session.
   */
  void pause(in SessionHandle sessionHandle, in PersistableBundle params);

  /**
   * Resumes a pauseed ranging session.
   *
   * <p>A session that has been previously pauseed using
   * {@link RangingSession#pause(PersistableBundle)} can be resumed by calling
   * {@link RangingSession#resume(PersistableBundle)}.
   *
   * <p>On successfully resuming the session,
   * {@link RangingSession.Callback#onResumed(PersistableBundle)} is invoked.
   *
   * <p>On failure to pause the session,
   * {@link RangingSession.Callback#onResumeFailed(int, PersistableBundle)} is invoked.
   *
   * @param sessionHandle the session handle to close ranging for
   * @param params protocol specific parameters the resuming the session.
   */
  void resume(in SessionHandle sessionHandle, in PersistableBundle params);

  /**
   * Send data to a remote device which is part of this ongoing session.
   * The data is sent by piggybacking the provided data over RRM (initiator -> responder) or
   * RIM (responder -> initiator).
   * <p>This is only functional on a FIRA 2.0 compliant device.
   *
   * <p>On successfully sending the data,
   * {@link RangingSession.Callback#onDataSent(UwbAddress, PersistableBundle)} is invoked.
   *
   * <p>On failure to send the data,
   * {@link RangingSession.Callback#onDataSendFailed(UwbAddress, int, PersistableBundle)} is
   * invoked.
   *
   * @param sessionHandle the session handle to close ranging for
   * @param remoteDeviceAddress remote device's address.
   * @param params protocol specific parameters the sending the data.
   * @param data Raw data to be sent.
   */
  void sendData(in SessionHandle sessionHandle, in UwbAddress remoteDeviceAddress,
          in PersistableBundle params, in byte[] data);

  /**
    * Set data transfer phase configuration during ranging as well as dedicated data transfer.
    * <p>This is only functional on a FIRA 2.0 compliant device.
    *
    * <p>On successfully sending the data transfer phase config,
    * {@link RangingSession.Callback#onDataTransferPhaseConfigured(PersistableBundle)} is
    * invoked.
    *
    * <p>On failure to send the data transfer phase config,
    * {@link RangingSession.Callback#onDataTransferPhaseConfigFailed(PersistableBundle)} is
    * invoked.
    *
    * @param params Protocol specific data transfer phase configuration parameters
    */
  void setDataTransferPhaseConfig(in SessionHandle sessionHandle, in PersistableBundle params);

  /**
   * Disables or enables UWB for a user
   *
   * The provided callback's IUwbAdapterStateCallbacks#onAdapterStateChanged
   * function must be called immediately following state change.
   *
   * @param enabled value representing intent to disable or enable UWB. If
   * true, any subsequent calls to #openRanging will be allowed. If false,
   * all active ranging sessions will be closed and subsequent calls to
   * #openRanging will be disallowed.
   */
  void setEnabled(boolean enabled);

  /**
   * Returns the current enabled/disabled UWB state.
   *
   * Possible values are:
   * IUwbAdapterState#STATE_DISABLED
   * IUwbAdapterState#STATE_ENABLED_ACTIVE
   * IUwbAdapterState#STATE_ENABLED_INACTIVE
   *
   * @return value representing enabled/disabled UWB state.
   */
  int getAdapterState();

  boolean isHwIdleTurnOffEnabled();
  void requestHwEnabled(boolean enabled, in AttributionSource attributionSource, IBinder binder);
  boolean isHwEnableRequested(in AttributionSource attributionSource);

  /**
   * Returns a list of UWB chip infos in a {@link PersistableBundle}.
   *
   * Callers can invoke methods on a specific UWB chip by passing its {@code chipId} to the
   * method, which can be determined by calling:
   * <pre>
   * List<PersistableBundle> chipInfos = getChipInfos();
   * for (PersistableBundle chipInfo : chipInfos) {
   *     String chipId = ChipInfoParams.fromBundle(chipInfo).getChipId();
   * }
   * </pre>
   *
   * @return list of {@link PersistableBundle} containing info about UWB chips for a multi-HAL
   * system, or a list of info for a single chip for a single HAL system.
   */
  List<PersistableBundle> getChipInfos();

  List<String> getChipIds();

  /**
   * Returns the default UWB chip identifier.
   *
   * If callers do not pass a specific {@code chipId} to UWB methods, then the method will be
   * invoked on the default chip, which is determined at system initialization from a configuration
   * file.
   *
   * @return default UWB chip identifier for a multi-HAL system, or the identifier of the only UWB
   * chip in a single HAL system.
   */
  String getDefaultChipId();

  PersistableBundle addServiceProfile(in PersistableBundle parameters);

  int removeServiceProfile(in PersistableBundle parameters);

  PersistableBundle getAllServiceProfiles();

  PersistableBundle getAdfProvisioningAuthorities(in PersistableBundle parameters);

  PersistableBundle getAdfCertificateAndInfo(in PersistableBundle parameters);

  void provisionProfileAdfByScript(in PersistableBundle serviceProfileBundle,
            in IUwbAdfProvisionStateCallbacks callback);

  int removeProfileAdf(in PersistableBundle serviceProfileBundle);

  /**
   * Updates the device pose. This helps the filter engine distinguish position noise from device
   * motion. The device pose would typically come from ARCore. This requires that an application
   * pose source was indicated in the call to openSession.
   */
  void updatePose(in SessionHandle sessionHandle, in PersistableBundle params);

  int sendVendorUciMessage(int mt, int gid, int oid, in byte[] payload);

  /**
   * @hide
   * Sets the Hybrid UWB Session Configuration
   *
   * @param SessionHandle Primary session handle
   * @param params protocol specific parameters to initiate the hybrid session
   * @return HUS configuration status code
   * <p>{@link UwbUciConstants#STATUS_CODE_OK} UWBS successfully processes the command
   *
   * <p>{@link UwbUciConstants#STATUS_CODE_FAILED} Intended operation is failed to complete
   *
   * <p>{@link UwbUciConstants#STATUS_CODE_ERROR_SESSION_NOT_EXIST} Primary session or
   * secondary session is not existing or not created
   *
   * <p>{@link UwbUciConstants#STATUS_CODE_ERROR_SESSION_NOT_CONFIGURED} Primary session or
   * secondary session has not been configured (i.e. SESSION_STATE_IDLE)
   *
   * <p>{@link UwbUciConstants#STATUS_CODE_ERROR_SESSION_DUPLICATE} Session Handle in phase
   * list is repeated
   */
  int setHybridSessionConfiguration(in SessionHandle sessionHandle,
        in PersistableBundle params);

  void updateRangingRoundsDtTag(in SessionHandle sessionHandle, in PersistableBundle parameters);

  void getUwbActivityEnergyInfoAsync(in IOnUwbActivityEnergyInfoListener listener);

  /**
   * Returns the max Application Data payload size that can be sent by the UWBS in one ranging
   * round.
   */
  int queryMaxDataSizeBytes(in SessionHandle sessionHandle);

  /**
   * @hide
   *
   * @return timestamp in microseconds
   */
   long queryUwbsTimestampMicros();

  /**
   * The maximum allowed time to open a ranging session.
   */
  const int RANGING_SESSION_OPEN_THRESHOLD_MS = 3000; // Value TBD

  /**
   * The maximum allowed time to start a ranging session.
   */
  const int RANGING_SESSION_START_THRESHOLD_MS = 3000; // Value TBD

  /**
   * The maximum allowed time to notify the framework that a session has been
   * closed.
   */
  const int RANGING_SESSION_CLOSE_THRESHOLD_MS = 3000; // Value TBD

  /**
   * The maximum allowed time to configure ranging rounds update for DT Tag
   */
  const int RANGING_ROUNDS_UPDATE_DT_TAG_THRESHOLD_MS = 3000; // Value TBD

  /**
   * The maximum allowed time to configure session data transfer phase config
   */
  const int SESSION_DATA_TRANSFER_PHASE_CONFIG_THRESHOLD_MS = 3000; // Value TBD
}
