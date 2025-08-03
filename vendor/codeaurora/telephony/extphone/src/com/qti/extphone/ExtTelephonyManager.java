/*
 * Copyright (c) 2020-2021, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   * Neither the name of The Linux Foundation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * Changes from Qualcomm Innovation Center are provided under the following license:
 *
 * Copyright (c) 2022-2024 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.qti.extphone;

import android.annotation.RequiresPermission;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Message;
import android.os.IBinder;
import android.os.Handler;
import android.os.RemoteException;
import android.telephony.ImsiEncryptionInfo;
import android.telephony.NetworkScanRequest;
import android.util.Log;

import com.qti.extphone.MsimPreference;

import java.lang.Integer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
* ExtTelephonyManager class provides ExtTelephonyService interface to
* the clients. Clients needs to instantiate this class to use APIs from
* IExtPhone.aidl. ExtTelephonyManager class has the logic to connect
* or disconnect to the bound service.
*/
public class ExtTelephonyManager {

    private static final String LOG_TAG = "ExtTelephonyManager";
    private static final boolean DBG = true;
    private static final int INVALID = -1;

    private static ExtTelephonyManager mInstance;

    private Context mContext;
    private ExtTelephonyServiceConnection mConnection =
            new ExtTelephonyServiceConnection();
    private IExtPhone mExtTelephonyService = null;

    private List<ServiceCallback> mServiceCbs =
            Collections.synchronizedList(new ArrayList<>());
    private AtomicBoolean mServiceConnected = new AtomicBoolean();

    /**
     * This represents the state of the SIM before SIM_STATE_LOADED, when only the
     * essential records have been loaded.
     */
    public static final String SIM_STATE_ESSENTIAL_RECORDS_LOADED = "ESSENTIAL_LOADED";

    /**
     * Intent action broadcasted when Sms Callback Mode changed.
     */
    public static final String ACTION_SMS_CALLBACK_MODE_CHANGED =
            "org.codeaurora.intent.action.SMS_CALLBACK_MODE_CHANGED";
    /**
     * Extra included in {@link #ACTION_SMS_CALLBACK_MODE_CHANGED}.
     * Indicates whether the phone is in an sms callback mode.
     */
    public static final String EXTRA_PHONE_IN_SCM_STATE =
            "org.codeaurora.extra.PHONE_IN_SCM_STATE";

    /**
     * Intent broadcasted to indicate the sms callback mode blocks
     * datacall/sms.
     */
    public static final String ACTION_SHOW_NOTICE_SCM_BLOCK_OTHERS =
            "org.codeaurora.intent.action.SHOW_NOTICE_SCM_BLOCK_OTHERS";

    /** PLMN access mode */
    public static final int ACCESS_MODE_PLMN = 1;

    /** SNPN access mode */
    public static final int ACCESS_MODE_SNPN = 2;

    /**
    * Constructor
    * @param context context in which the bindService will be
    *                initiated.
    */
    public ExtTelephonyManager(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context is null");
        }
        this.mContext = context;
        mServiceConnected.set(false);
        log("ExtTelephonyManager() ...");
    }

    /**
    * This method returns the singleton instance of ExtTelephonyManager object
    */
    public static synchronized ExtTelephonyManager getInstance(Context context) {
        synchronized (ExtTelephonyManager.class) {
            if (mInstance == null) {
                if (context != null) {
                    Context appContext = context.getApplicationContext();
                    mInstance = new ExtTelephonyManager(appContext);
                } else {
                    throw new IllegalArgumentException("Context is null");
                }
            }
            return mInstance;
        }
    }

    /**
    * To check if the service is connected or not
    * @return boolean true if service is connected, false oterwise
    */
    public boolean isServiceConnected() {
        return mServiceConnected.get();
    }

    public boolean isFeatureSupported(int feature) {
        boolean ret = false;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return ret;
        }
        try {
            ret = mExtTelephonyService.isFeatureSupported(feature);
        } catch (RemoteException e){
            Log.e(LOG_TAG, "isFeatureSupported, remote exception", e);
        }
        return ret;
    }

    /**
    * Initiate connection with the service.
    *
    * @param serviceCallback {@link ServiceCallback} to receive
    *                        service-level callbacks.
    *
    * @return boolean Immediate result of the operation. true if
    *                 successful.
    *                 NOTE: This does not garuntee a successful
    *                 connection. The client needs to use handler
    *                 to listen to the Result.
    */
    public boolean connectService(ServiceCallback cb) {
        boolean success = true;
        if (!isServiceConnected() && mServiceCbs.isEmpty()) {
            log("Creating ExtTelephonyService. If not started yet, start ...");
            addServiceCallback(cb);
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.qti.phone",
                    "com.qti.phone.ExtTelephonyService"));
            success = mContext.bindService(intent, mConnection,
                    Context.BIND_AUTO_CREATE);
            log("bind Service result: " + success);
        } else {
            addServiceCallback(cb);
            if (isServiceConnected() && cb != null) {
                cb.onConnected();
            }
        }
        return success;
    }

    private void addServiceCallback(ServiceCallback cb) {
        if (cb != null && !mServiceCbs.contains(cb)) {
             mServiceCbs.add(cb);
        }
    }

    /**
    * Disconnect the connection with the Service.
    *
    * @deprecated Replaced by {@link #disconnectService(ServiceCallback)}
    *
    */
    public void disconnectService() {
        disconnectService(null);
    }

    /**
    * Disconnect the connection with the Service.
    *
    * @param serviceCallback {@link ServiceCallback} to receive
    * service-level callbacks.
    *
    */
    public void disconnectService(ServiceCallback cb) {
        if (cb != null) {
            if (!isServiceConnected()) {
                cb.onDisconnected();
            }
            if (mServiceCbs.contains(cb)) {
                mServiceCbs.remove(cb);
            }
        }
        if (isServiceConnected() && mServiceCbs.isEmpty()) {
            mContext.unbindService(mConnection);
            log("Set ServiceConnected to false");
            mServiceConnected.set(false);
        }
    }

    private void notifyConnected() {
        for (ServiceCallback cb : mServiceCbs) {
            cb.onConnected();
        }
    }

    private void notifyDisconnected() {
        for (ServiceCallback cb : mServiceCbs) {
            cb.onDisconnected();
        }
    }

    /**
    *
    * Internal helper functions/variables
    */
    private class ExtTelephonyServiceConnection implements ServiceConnection {

        public void onServiceConnected(ComponentName name, IBinder boundService) {
            mExtTelephonyService = IExtPhone.Stub.asInterface((IBinder) boundService);
            if (mExtTelephonyService == null) {
                log("ExtTelephonyService Connect Failed (onServiceConnected)... ");
            } else {
                log("ExtTelephonyService connected ... ");
            }
            mServiceConnected.set(true);
            notifyConnected();
        }

        public void onServiceDisconnected(ComponentName name) {
            log("The connection to the service got disconnected!");
            mExtTelephonyService = null;
            mServiceConnected.set(false);
            notifyDisconnected();
        }
    }

    /**
    * Get value assigned to vendor property
    * @param - property name
    * @param - default value of property
    * @return - integer value assigned
    */
    public int getPropertyValueInt(String property, int def) {
        int ret = INVALID;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return ret;
        }
        log("getPropertyValueInt: property=" + property);
        try {
            ret = mExtTelephonyService.getPropertyValueInt(property, def);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "getPropertyValueInt, remote exception", e);
        }
        return ret;
    }

    /**
    * Get value assigned to vendor property
    * @param - property name
    * @param - default value of property
    * @return - boolean value assigned
    */
    public boolean getPropertyValueBool(String property, boolean def) {
        boolean ret = def;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return ret;
        }
        log("getPropertyValueBool: property=" + property);
        try {
            ret = mExtTelephonyService.getPropertyValueBool(property, def);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "getPropertyValueBool, remote exception", e);
        }
        return ret;
    }

    /**
    * Get value assigned to vendor property
    * @param - property name
    * @param - default value of property
    * @return - string value assigned
    */
    public String getPropertyValueString(String property, String def) {
        String ret = def;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return ret;
        }
        log("getPropertyValueString: property=" + property);
        try {
            ret = mExtTelephonyService.getPropertyValueString(property, def);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "getPropertyValueString, remote exception", e);
        }
        return ret;
    }

    /**
    * Check if slotId has PrimaryCarrier SIM card present or not.
    * @param - slotId
    * @return true or false
    */
    public boolean isPrimaryCarrierSlotId(int slotId) {
        boolean ret = false;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return ret;
        }
        try {
            ret = mExtTelephonyService.isPrimaryCarrierSlotId(slotId);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "isPrimaryCarrierSlotId, remote exception", e);
        }
        return ret;
    }

    /**
    * Get current primary card slot Id.
    * @param - void
    * @return slot index
    */
    public int getCurrentPrimaryCardSlotId() {
        int ret = INVALID;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return ret;
        }
        try {
            ret = mExtTelephonyService.getCurrentPrimaryCardSlotId();
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "getCurrentPrimaryCardSlotId, remote exception", e);
        }
        return ret;
    }

    /**
    * Returns ID of the slot in which PrimaryCarrier SIM card is present.
    * If none of the slots contains PrimaryCarrier SIM, this would return '-1'
    * Supported values: 0, 1, -1
    */
    public int getPrimaryCarrierSlotId() {
        int ret = INVALID;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return ret;
        }
        try {
            ret = mExtTelephonyService.getPrimaryCarrierSlotId();
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "getPrimaryCarrierSlotId, remote exception", e);
        }
        return ret;
    }

    /**
    * Set Primary card on given slot.
    * @param - slotId to be set as Primary Card.
    * @return void
    */
    public void setPrimaryCardOnSlot(int slotId) {
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return;
        }
        try {
            mExtTelephonyService.setPrimaryCardOnSlot(slotId);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "setPrimaryCardOnSlot, remote exception", e);
        }
    }

    /**
    * Perform incremental scan using QCRIL hooks.
    * @param - slotId
    *          Range: 0 <= slotId < {@link TelephonyManager#getActiveModemCount()}
    * @return true if the request has successfully been sent to the modem, false otherwise.
    * Requires permission: android.Manifest.permission.MODIFY_PHONE_STATE
    */
    public boolean performIncrementalScan(int slotId) {
        boolean ret = false;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return ret;
        }
        try {
            ret = mExtTelephonyService.performIncrementalScan(slotId);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "performIncrementalScan, remote exception", e);
        }
        return ret;
    }

    /**
    * Abort incremental scan using QCRIL hooks.
    * @param - slotId
    *          Range: 0 <= slotId < {@link TelephonyManager#getActiveModemCount()}
    * @return true if the request has successfully been sent to the modem, false otherwise.
    * Requires permission: android.Manifest.permission.MODIFY_PHONE_STATE
    */
    public boolean abortIncrementalScan(int slotId) {
        boolean ret = false;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return ret;
        }
        try {
            ret = mExtTelephonyService.abortIncrementalScan(slotId);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "abortIncrementalScan, remote exception", e);
        }
        return ret;
    }

    /**
    * Check for Sms Prompt is Enabled or Not.
    * @return
    *        true - Sms Prompt is Enabled
    *        false - Sms prompt is Disabled
    * Requires Permission: android.Manifest.permission.READ_PHONE_STATE
    */
    public boolean isSMSPromptEnabled() {
        boolean ret = false;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return ret;
        }
        try {
            ret = mExtTelephonyService.isSMSPromptEnabled();
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "isSMSPromptEnabled, remote exception", e);
        }
        return ret;
    }

    /**
    * Enable/Disable Sms prompt option.
    * @param - enabled
    *        true - to enable Sms prompt
    *        false - to disable Sms prompt
    * Requires Permission: android.Manifest.permission.MODIFY_PHONE_STATE
    */
    public void setSMSPromptEnabled(boolean enabled) {
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return;
        }
        try {
            mExtTelephonyService.setSMSPromptEnabled(enabled);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "setSMSPromptEnabled, remote exception", e);
        }
    }

    /**
    * supply pin to unlock sim locked on network.
    * @param - netpin - network pin to unlock the sim.
    * @param - type - PersoSubState for which the sim is locked onto.
    * @param - callback - callback to notify UI, whether the request was success or failure.
    * @param - phoneId - slot id on which the pin request is sent.
    * @return void
    */
    public void supplyIccDepersonalization(String netpin, String type,
            IDepersoResCallback callback, int phoneId) {
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return;
        }
        try {
            mExtTelephonyService.supplyIccDepersonalization(netpin,
                    type, callback, phoneId);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "supplyIccDepersonalization, remote exception", e);
        }
    }

    public Token enableEndc(int slot, boolean enable, Client client) {
        Token token = null;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.enableEndc(slot, enable, client);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "enableEndc, remote exception", e);
        }
        return token;
    }

    public Token queryNrIconType(int slot, Client client) {
        Token token = null;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.queryNrIconType(slot, client);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "queryNrIconType, remote exception", e);
        }
        return token;
    }

    public Token queryEndcStatus(int slot, Client client) {
        Token token = null;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.queryEndcStatus(slot, client);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "queryEndcStatus, remote exception", e);
        }
        return token;
    }

    public Token setNrConfig(int slot, NrConfig config, Client client) {
        Token token = null;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.setNrConfig(slot, config, client);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "setNrConfig, remote exception", e);
        }
        return token;
    }

    public Token setNetworkSelectionModeAutomatic(int slot, int accessType, Client client) {
        Token token = null;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.setNetworkSelectionModeAutomatic(slot, accessType, client);
        } catch(RemoteException e) {
            Log.e(LOG_TAG, "setNetworkSelectionModeAutomatic, remote exception", e);
        }
        return token;
    }

    public Token getNetworkSelectionMode(int slot, Client client) {
        Token token = null;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.getNetworkSelectionMode(slot, client);
        } catch(RemoteException e) {
            Log.e(LOG_TAG, "getNetworkSelectionMode, remote exception", e);
        }
        return token;
    }

    public Token queryNrConfig(int slot, Client client) {
        Token token = null;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.queryNrConfig(slot, client);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "queryNrConfig, remote exception", e);
        }
        return token;
    }

    public Token sendCdmaSms(int slot, byte[] pdu, boolean expectMore, Client client) {
        Token token = null;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.sendCdmaSms(slot, pdu, expectMore, client);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "sendCdmaSms, remote exception", e);
        }
        return token;
    }

    public Token startNetworkScan(int slot, NetworkScanRequest networkScanRequest, Client client) {
        Token token = null;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.startNetworkScan(slot, networkScanRequest, client);
        } catch(RemoteException e) {
            Log.e(LOG_TAG, "startNetworkScan, remote exception", e);
        }
        return token;
    }

    public Token stopNetworkScan(int slot, Client client) {
        Token token = null;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.stopNetworkScan(slot, client);
        } catch(RemoteException e) {
            Log.e(LOG_TAG, "stopNetworkScan, remote exception", e);
        }
        return token;
    }

    public Token setNetworkSelectionModeManual(int slot, QtiSetNetworkSelectionMode mode,
            Client client) {
        Token token = null;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.setNetworkSelectionModeManual(slot, mode, client);
        } catch(RemoteException e) {
            Log.e(LOG_TAG, "startNetworkScan, remote exception", e);
        }
        return token;
    }

    public Token getQtiRadioCapability(int slotId, Client client) throws RemoteException {
        Token token = null;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        token = mExtTelephonyService.getQtiRadioCapability(slotId, client);
        return token;
    }

    public Token enable5g(int slot, Client client) {
        Token token = null;
        if(!isServiceConnected()){
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.enable5g(slot, client);
        } catch (RemoteException e){
            Log.e(LOG_TAG, "enable5g, remote exception", e);
        }
        return token;
    }

    public Token disable5g(int slot, Client client) {
        Token token = null;
        if(!isServiceConnected()){
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.disable5g(slot, client);
        } catch (RemoteException e){
            Log.e(LOG_TAG, "disable5g, remote exception", e);
        }
        return token;
    }

    public Token queryNrBearerAllocation(int slot, Client client) {
        Token token = null;
        if(!isServiceConnected()){
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.queryNrBearerAllocation(slot, client);
        } catch (RemoteException e){
            Log.e(LOG_TAG, "queryNrBearerAllocation, remote exception", e);
        }
        return token;
    }

    public Token setCarrierInfoForImsiEncryption(int slot, ImsiEncryptionInfo info,
            Client client) {
        Token token = null;
        if(!isServiceConnected()){
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.setCarrierInfoForImsiEncryption(slot, info, client);
        } catch (RemoteException e){
            Log.e(LOG_TAG, "setCarrierInfoForImsiEncryption, remote exception", e);
        }
        return token;
    }

    public Token enable5gOnly(int slot, Client client) {
        Token token = null;
        if(!isServiceConnected()){
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.enable5gOnly(slot, client);
        } catch (RemoteException e){
            Log.e(LOG_TAG, "enable5gOnly, remote exception", e);
        }
        return token;
    }

    public Token query5gStatus(int slot, Client client) {
        Token token = null;
        if(!isServiceConnected()){
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.query5gStatus(slot, client);
        } catch (RemoteException e){
            Log.e(LOG_TAG, "query5gStatus, remote exception", e);
        }
        return token;
    }

    public Token queryNrDcParam(int slot, Client client) {
        Token token = null;
        if(!isServiceConnected()){
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.queryNrDcParam(slot, client);
        } catch (RemoteException e){
            Log.e(LOG_TAG, "queryNrDcParam, remote exception", e);
        }
        return token;
    }

    public Token queryNrSignalStrength(int slot, Client client) {
        Token token = null;
        if(!isServiceConnected()){
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.queryNrSignalStrength(slot, client);
        } catch (RemoteException e){
            Log.e(LOG_TAG, "queryNrSignalStrength, remote exception", e);
        }
        return token;
    }

    public Token queryUpperLayerIndInfo(int slot, Client client) {
        Token token = null;
        if(!isServiceConnected()){
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.queryUpperLayerIndInfo(slot, client);
        } catch (RemoteException e){
            Log.e(LOG_TAG, "queryUpperLayerIndInfo, remote exception", e);
        }
        return token;
    }

    public Token query5gConfigInfo(int slot, Client client) {
        Token token = null;
        if(!isServiceConnected()){
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.query5gConfigInfo(slot, client);
        } catch (RemoteException e){
            Log.e(LOG_TAG, "query5gConfigInfo, remote exception", e);
        }
        return token;
    }

    public void queryCallForwardStatus(int slotId, int cfReason, int serviceClass, String number,
            boolean expectMore, Client client) throws RemoteException {
        try {
            mExtTelephonyService.queryCallForwardStatus(slotId, cfReason, serviceClass, number,
                    expectMore, client);
        } catch (RemoteException e){
            throw new RemoteException("queryCallForwardStatus ended in remote exception");
        }
    }

    public void getFacilityLockForApp(int slotId, String facility, String password,
            int serviceClass, String appId, boolean expectMore, Client client)
            throws RemoteException {
        try {
            mExtTelephonyService.getFacilityLockForApp(slotId, facility, password, serviceClass,
                    appId, expectMore, client);
        } catch (RemoteException e){
            throw new RemoteException("getFacilityLockForApp ended in remote exception");
        }
    }

   /**
    * To get the IMEI information of all slots on device.
    * @return
    *        QtiImeiInfo[], contains array of imeiInfo(i.e slotId, IMEI string and IMEI type).
    *
    * The calling application should not assume returned array index as slotId, instead the
    * application has to use the slotId that present in QtiImeiInfo object to know the IMEI
    * corresponds to a slot.
    *
    * Requires Permission: android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE
    */
    public QtiImeiInfo[] getImeiInfo() {
        try {
            return mExtTelephonyService.getImeiInfo();
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "getImeiInfo ended in remote exception", e);
        }
        return null;
    }

    public boolean isSmartDdsSwitchFeatureAvailable() throws RemoteException {
        try {
            return mExtTelephonyService.isSmartDdsSwitchFeatureAvailable();
        } catch (RemoteException e) {
            throw new RemoteException("isSmartDdsSwitchFeatureAvailable ended in remote exception");
        }
    }

    public void setSmartDdsSwitchToggle(boolean isEnabled, Client client)
            throws RemoteException {
        try {
            mExtTelephonyService.setSmartDdsSwitchToggle(isEnabled, client);
        } catch (RemoteException e) {
            throw new RemoteException("setSmartDdsSwitchToggle ended in remote exception");
        }
    }

    public Token getDdsSwitchCapability(int slot, Client client) {
        Token token = null;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.getDdsSwitchCapability(slot, client);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "getDdsSwitchCapability, remote exception", e);
        }
        return token;
    }

    public Token sendUserPreferenceForDataDuringVoiceCall(int slot,
            boolean userPreference, Client client) {
        Token token = null;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.sendUserPreferenceForDataDuringVoiceCall(slot,
                    userPreference, client);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "sendUserPreferenceForDataDuringVoiceCall, remote exception", e);
        }
        return token;
    }

    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    public boolean setAirplaneMode(boolean on) throws RemoteException {
        try {
            return mExtTelephonyService.setAirplaneMode(on);
        } catch (RemoteException e) {
            throw new RemoteException("setAirplaneMode ended in remote exception");
        }
    }

    @RequiresPermission(android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE)
    public boolean getAirplaneMode() throws RemoteException {
        try {
            return mExtTelephonyService.getAirplaneMode();
        } catch (RemoteException e) {
            throw new RemoteException("getAirplaneMode ended in remote exception");
        }
    }

    @RequiresPermission(android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE)
    public boolean checkSimPinLockStatus(int subId) throws RemoteException {
        try {
            return mExtTelephonyService.checkSimPinLockStatus(subId);
        } catch (RemoteException e) {
            throw new RemoteException("checkSimPinLockStatus ended in remote exception");
        }
    }

    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    public boolean toggleSimPinLock(int subId, boolean enabled, String pin) throws RemoteException {
        try {
            return mExtTelephonyService.toggleSimPinLock(subId, enabled, pin);
        } catch (RemoteException e) {
            throw new RemoteException("toggleSimPinLock ended in remote exception");
        }
    }

    @RequiresPermission(android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE)
    public boolean verifySimPin(int subId, String pin) throws RemoteException {
        try {
            return mExtTelephonyService.verifySimPin(subId, pin);
        } catch (RemoteException e) {
            throw new RemoteException("verifySimPin ended in remote exception");
        }
    }

    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    public boolean verifySimPukChangePin(int subId, String puk, String newPin)
            throws RemoteException {
        try {
            return mExtTelephonyService.verifySimPukChangePin(subId, puk, newPin);
        } catch (RemoteException e) {
            throw new RemoteException("verifySimPukChangePin ended in remote exception");
        }
    }

    public boolean isEpdgOverCellularDataSupported(int slot) throws RemoteException {
        boolean support = false;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return support;
        }
        try {
            support = mExtTelephonyService.isEpdgOverCellularDataSupported(slot);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "isEpdgOverCellularDataSupported, remote exception", e);
        }
        return support;
    }

    public Token getQosParameters(int slotId, int cid, Client client) throws RemoteException {
        Log.d(LOG_TAG, "[" + slotId + "] getQosParameters, cid: " + cid);
        Token token = null;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.getQosParameters(slotId, cid, client);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "getQosParameters ended in remote exception", e);
        }
        return token;
    }

    public Token getSecureModeStatus(Client client) throws RemoteException {
        Token token = null;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.getSecureModeStatus(client);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "getSecureModeStatus ended in remote exception", e);
        }
        return token;
    }

    public Token setMsimPreference(Client client, MsimPreference pref) throws RemoteException {
        Token token = null;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.setMsimPreference(client, pref);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "setMsimPreference ended in remote exception", e);
        }
        return token;
    }

   /**
    * Get the supported Sim Type information on all available slots
    *
    * @return - Array of SimType corresponds to each Slot, the supported
    *           Sim Types are Physical/eSIM or iUICC or both.
    *
    * Requires Permission: android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE
    */
    public QtiSimType[] getSupportedSimTypes() {
        if (isServiceConnected()) {
            try {
                return mExtTelephonyService.getSupportedSimTypes();
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "getSupportedSimTypes ended in remote exception", e);
            }
        } else {
            Log.e(LOG_TAG, "service not connected!");
        }
        return null;
    }

   /**
    * Get current active Sim Type, Physical/eSIM or iUICC
    *
    * @return - Array of SimType corresponds to each Slot.
    *
    * Requires Permission: android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE
    */
    public QtiSimType[] getCurrentSimType() {
        if (isServiceConnected()) {
            try {
                return mExtTelephonyService.getCurrentSimType();
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "getCurrentSimType ended in remote exception", e);
            }
        } else {
            Log.e(LOG_TAG, "getCurrentSimType, service not connected!");
        }
        return null;
    }

   /**
    * Set SIM Type to either Physical/eSIM or iUICC
    *
    * @param client - Client registered with package name to receive callbacks.
    * @param simType - QtiSimType array contains the SimType to be set for all the slots.
    * @return - Integer Token can be used to compare with the response, null Token value
    *        can be returned if request cannot be processed.
    *
    * @Response would be sent over IExtPhoneCallback.setSimTypeResponse()
    *
    * Requires Permission: android.Manifest.permission.MODIFY_PHONE_STATE
    */
    public Token setSimType(Client client, QtiSimType[] simType) throws RemoteException {
        if (isServiceConnected()) {
            try {
                return mExtTelephonyService.setSimType(client, simType);
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "setSimType ended in remote exception", e);
            }
        } else {
            Log.e(LOG_TAG, "setSimType, service not connected!");
        }
        return null;
    }

    public CiwlanConfig getCiwlanConfig(int slotId) throws RemoteException {
        CiwlanConfig config = null;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return config;
        }
        try {
            config = mExtTelephonyService.getCiwlanConfig(slotId);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "getCiwlanConfig ended in remote exception", e);
        }
        return config;
    }

    /**
     * Request dual data capability.
     * It is a static modem capability.
     *
     * @return - boolean TRUE/FALSE based on modem supporting dual data capability feature.
     */
    public boolean getDualDataCapability() {
        if (isServiceConnected()) {
            try {
                return mExtTelephonyService.getDualDataCapability();
            } catch (RemoteException ex) {
                Log.e(LOG_TAG, "getDualDataCapability Failed.", ex);
            }
        }
        return false;
    }

    /**
     * Set dual data user preference.
     * In a multi-SIM device, inform modem if user wants dual data feature or not.
     * Modem will not send any recommendations to HLOS to support dual data
     * if user does not opt in the feature even if UE is dual data capable.
     *
     * @param client - Client registered with package name to receive callbacks.
     * @param enable - Dual data selection opted by user. True if preference is enabled.
     * @return - Integer Token can be used to compare with the response, null Token value
     *        can be returned if request cannot be processed.
     *
     * Response function is IExtPhoneCallback#setDualDataUserPreferenceResponse().
     */
    public Token setDualDataUserPreference(Client client, boolean enable) throws RemoteException {
        Token token = null;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return token;
        }
        try {
            token = mExtTelephonyService.setDualDataUserPreference(client, enable);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "setDualDataUserPreference ended in remote exception", e);
        }
        return token;
    }

    public QtiPersoUnlockStatus getSimPersoUnlockStatus(int slotId) {
        QtiPersoUnlockStatus persoUnlockStatus = null;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return persoUnlockStatus;
        }
        try {
            persoUnlockStatus = mExtTelephonyService.getSimPersoUnlockStatus(slotId);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "Remote exception for getSimPersoUnlockStatus", e);
        }
        return persoUnlockStatus;
    }

    public Client registerCallback(String packageName, IExtPhoneCallback callback) {
        Client client = null;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return client;
        }
        try {
            client = mExtTelephonyService.registerCallback(packageName, callback);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "registerCallback, remote exception", e);
        }
        return client;
    }

    public Client registerCallbackWithEvents(String packageName, ExtPhoneCallbackListener callback,
            int[] events) {
        Client client = null;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return client;
        }
        try {
            client = mExtTelephonyService.registerCallbackWithEvents(packageName,
                    callback.mCallback, events);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "registerCallbackWithEvents, remote exception", e);
        }
        return client;
    }

    public void unRegisterCallback(IExtPhoneCallback callback) {
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "service not connected!");
            return;
        }
        try {
            mExtTelephonyService.unRegisterCallback(callback);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "unRegisterCallback, remote exception ", e);
        }
    }

    public void unregisterCallback(ExtPhoneCallbackListener callback) {
        callback.cleanUp();
        unRegisterCallback(callback.mCallback);
    }

    public Client registerQtiRadioConfigCallback(String packageName, IExtPhoneCallback callback) {
        Client client = null;
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "Service not connected!");
            return client;
        }
        try {
            client = mExtTelephonyService.registerQtiRadioConfigCallback(packageName, callback);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "registerQtiRadioConfigCallback, remote exception", e);
        }
        return client;
    }

    public void unregisterQtiRadioConfigCallback(IExtPhoneCallback callback) {
        if (!isServiceConnected()) {
            Log.e(LOG_TAG, "Service not connected!");
            return;
        }
        try {
            mExtTelephonyService.unregisterQtiRadioConfigCallback(callback);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "unregisterQtiRadioConfigCallback, remote exception", e);
        }
    }

    private void log(String str) {
        if (DBG) {
            Log.d(LOG_TAG, str);
        }
    }
}
