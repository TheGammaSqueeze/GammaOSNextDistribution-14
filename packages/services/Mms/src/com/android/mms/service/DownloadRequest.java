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

package com.android.mms.service;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Telephony;
import android.service.carrier.CarrierMessagingService;
import android.service.carrier.CarrierMessagingServiceWrapper;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.mms.service.exception.MmsHttpException;
import com.android.mms.service.metrics.MmsStats;

import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.GenericPdu;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduParser;
import com.google.android.mms.pdu.PduPersister;
import com.google.android.mms.pdu.RetrieveConf;
import com.google.android.mms.util.SqliteWrapper;

/**
 * Request to download an MMS
 */
public class DownloadRequest extends MmsRequest {
    private static final String LOCATION_SELECTION =
            Telephony.Mms.MESSAGE_TYPE + "=? AND " + Telephony.Mms.CONTENT_LOCATION + " =?";

    private final String mLocationUrl;
    private final PendingIntent mDownloadedIntent;
    private final Uri mContentUri;

    public DownloadRequest(RequestManager manager, int subId, String locationUrl,
            Uri contentUri, PendingIntent downloadedIntent, String creator,
            Bundle configOverrides, Context context, long messageId, MmsStats mmsStats,
            TelephonyManager telephonyManager) {
        super(manager, subId, creator, configOverrides, context, messageId, mmsStats,
                telephonyManager);
        mLocationUrl = locationUrl;
        mDownloadedIntent = downloadedIntent;
        mContentUri = contentUri;
    }

    @Override
    protected byte[] doHttp(Context context, MmsNetworkManager netMgr, ApnSettings apn)
            throws MmsHttpException {
        final String requestId = getRequestId();
        final MmsHttpClient mmsHttpClient = netMgr.getOrCreateHttpClient();
        if (mmsHttpClient == null) {
            LogUtil.e(requestId, "MMS network is not ready! "
                    + MmsService.formatCrossStackMessageId(mMessageId));
            throw new MmsHttpException(0/*statusCode*/, "MMS network is not ready. "
                    + MmsService.formatCrossStackMessageId(mMessageId));
        }
        return mmsHttpClient.execute(
                mLocationUrl,
                null/*pud*/,
                MmsHttpClient.METHOD_GET,
                apn.isProxySet(),
                apn.getProxyAddress(),
                apn.getProxyPort(),
                mMmsConfig,
                mSubId,
                requestId);
    }

    @Override
    protected PendingIntent getPendingIntent() {
        return mDownloadedIntent;
    }

    @Override
    protected int getQueueType() {
        return MmsService.QUEUE_INDEX_DOWNLOAD;
    }

    @Override
    protected Uri persistIfRequired(Context context, int result, byte[] response) {
        final String requestId = getRequestId();
        // Let any mms apps running as secondary user know that a new mms has been downloaded.
        notifyOfDownload(context);

        if (!mRequestManager.getAutoPersistingPref()) {
            return null;
        }
        LogUtil.d(requestId, "persistIfRequired. "
                + MmsService.formatCrossStackMessageId(mMessageId));
        if (response == null || response.length < 1) {
            LogUtil.e(requestId, "persistIfRequired: empty response. "
                    + MmsService.formatCrossStackMessageId(mMessageId));
            return null;
        }
        final long identity = Binder.clearCallingIdentity();
        try {
            final boolean supportMmsContentDisposition =
                    mMmsConfig.getBoolean(SmsManager.MMS_CONFIG_SUPPORT_MMS_CONTENT_DISPOSITION);
            final GenericPdu pdu = (new PduParser(response, supportMmsContentDisposition)).parse();
            if (pdu == null || !(pdu instanceof RetrieveConf)) {
                LogUtil.e(requestId, "persistIfRequired: invalid parsed PDU. "
                        + MmsService.formatCrossStackMessageId(mMessageId));
                return null;
            }
            final RetrieveConf retrieveConf = (RetrieveConf) pdu;
            final int status = retrieveConf.getRetrieveStatus();
            if (status != PduHeaders.RETRIEVE_STATUS_OK) {
                LogUtil.e(requestId, "persistIfRequired: retrieve failed " + status
                        + ", " + MmsService.formatCrossStackMessageId(mMessageId));
                // Update the retrieve status of the NotificationInd
                final ContentValues values = new ContentValues(1);
                values.put(Telephony.Mms.RETRIEVE_STATUS, status);
                SqliteWrapper.update(
                        context,
                        context.getContentResolver(),
                        Telephony.Mms.CONTENT_URI,
                        values,
                        LOCATION_SELECTION,
                        new String[] {
                                Integer.toString(PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND),
                                mLocationUrl
                        });
                return null;
            }
            // Store the downloaded message
            final PduPersister persister = PduPersister.getPduPersister(context);
            final Uri messageUri = persister.persist(
                    pdu,
                    Telephony.Mms.Inbox.CONTENT_URI,
                    true/*createThreadId*/,
                    true/*groupMmsEnabled*/,
                    null/*preOpenedFiles*/);
            if (messageUri == null) {
                LogUtil.e(requestId, "persistIfRequired: can not persist message. "
                        + MmsService.formatCrossStackMessageId(mMessageId));
                return null;
            }
            // Update some of the properties of the message
            final ContentValues values = new ContentValues();
            values.put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000L);
            values.put(Telephony.Mms.READ, 0);
            values.put(Telephony.Mms.SEEN, 0);
            if (!TextUtils.isEmpty(mCreator)) {
                values.put(Telephony.Mms.CREATOR, mCreator);
            }
            values.put(Telephony.Mms.SUBSCRIPTION_ID, mSubId);
            if (SqliteWrapper.update(
                    context,
                    context.getContentResolver(),
                    messageUri,
                    values,
                    null/*where*/,
                    null/*selectionArg*/) != 1) {
                LogUtil.e(requestId, "persistIfRequired: can not update message. "
                        + MmsService.formatCrossStackMessageId(mMessageId));
            }
            // Delete the corresponding NotificationInd
            SqliteWrapper.delete(context,
                    context.getContentResolver(),
                    Telephony.Mms.CONTENT_URI,
                    LOCATION_SELECTION,
                    new String[]{
                            Integer.toString(PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND),
                            mLocationUrl
                    });

            return messageUri;
        } catch (MmsException e) {
            LogUtil.e(requestId, "persistIfRequired: can not persist message. "
                    + MmsService.formatCrossStackMessageId(mMessageId), e);
        } catch (SQLiteException e) {
            LogUtil.e(requestId, "persistIfRequired: can not update message. "
                    + MmsService.formatCrossStackMessageId(mMessageId), e);
        } catch (RuntimeException e) {
            LogUtil.e(requestId, "persistIfRequired: can not parse response. "
                    + MmsService.formatCrossStackMessageId(mMessageId), e);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return null;
    }

    private void notifyOfDownload(Context context) {
        final Intent intent = new Intent(Telephony.Sms.Intents.MMS_DOWNLOADED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_NO_ABORT);

        // Get a list of currently started users.
        int[] users = null;
        try {
            users = ActivityManager.getService().getRunningUserIds();
        } catch (RemoteException re) {
        }
        if (users == null) {
            users = new int[] {UserHandle.ALL.getIdentifier()};
        }
        final UserManager userManager =
                (UserManager) context.getSystemService(Context.USER_SERVICE);

        // Deliver the broadcast only to those running users that are permitted
        // by user policy.
        for (int i = users.length - 1; i >= 0; i--) {
            UserHandle targetUser = new UserHandle(users[i]);
            if (users[i] != UserHandle.USER_SYSTEM) {
                // Is the user not allowed to use SMS?
                if (userManager.hasUserRestriction(UserManager.DISALLOW_SMS, targetUser)) {
                    continue;
                }
                // Skip unknown users and managed profiles as well
                UserInfo info = userManager.getUserInfo(users[i]);
                if (info == null || info.isManagedProfile()) {
                    continue;
                }
            }
            context.sendOrderedBroadcastAsUser(intent, targetUser,
                    android.Manifest.permission.RECEIVE_MMS,
                    AppOpsManager.OP_RECEIVE_MMS,
                    null,
                    null, Activity.RESULT_OK, null, null);
        }
    }

    /**
     * Transfer the received response to the caller (for download requests write to content uri)
     *
     * @param fillIn the intent that will be returned to the caller
     * @param response the pdu to transfer
     */
    @Override
    protected boolean transferResponse(Intent fillIn, final byte[] response) {
        return mRequestManager.writePduToContentUri(mContentUri, response);
    }

    @Override
    protected boolean prepareForHttpRequest() {
        return true;
    }

    /**
     * Try downloading via the carrier app.
     *
     * @param context The context
     * @param carrierMessagingServicePackage The carrier messaging service handling the download
     */
    public void tryDownloadingByCarrierApp(Context context, String carrierMessagingServicePackage) {
        final CarrierDownloadManager carrierDownloadManger = new CarrierDownloadManager();
        final CarrierDownloadCompleteCallback downloadCallback =
                new CarrierDownloadCompleteCallback(context, carrierDownloadManger);
        carrierDownloadManger.downloadMms(context, carrierMessagingServicePackage,
                downloadCallback);
    }

    @Override
    protected void revokeUriPermission(Context context) {
        context.revokeUriPermission(mContentUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }

    /**
     * Downloads the MMS through through the carrier app.
     */
    private final class CarrierDownloadManager {
        // Initialized in downloadMms
        private volatile CarrierDownloadCompleteCallback mCarrierDownloadCallback;
        private final CarrierMessagingServiceWrapper mCarrierMessagingServiceWrapper =
                new CarrierMessagingServiceWrapper();

        void disposeConnection(Context context) {
            mCarrierMessagingServiceWrapper.disconnect();
        }

        void downloadMms(Context context, String carrierMessagingServicePackage,
                CarrierDownloadCompleteCallback carrierDownloadCallback) {
            mCarrierDownloadCallback = carrierDownloadCallback;
            if (mCarrierMessagingServiceWrapper.bindToCarrierMessagingService(
                    context, carrierMessagingServicePackage, Runnable::run,
                    ()->onServiceReady())) {
                LogUtil.v("bindService() for carrier messaging service: "
                        + carrierMessagingServicePackage + " succeeded. "
                        + MmsService.formatCrossStackMessageId(mMessageId));
            } else {
                LogUtil.e("bindService() for carrier messaging service: "
                        + carrierMessagingServicePackage + " failed. "
                        + MmsService.formatCrossStackMessageId(mMessageId));
                carrierDownloadCallback.onDownloadMmsComplete(
                        CarrierMessagingService.DOWNLOAD_STATUS_RETRY_ON_CARRIER_NETWORK);
            }
        }

        private void onServiceReady() {
            try {
                mCarrierMessagingServiceWrapper.downloadMms(
                        mContentUri, mSubId, Uri.parse(mLocationUrl), Runnable::run,
                        mCarrierDownloadCallback);
            } catch (RuntimeException e) {
                LogUtil.e("Exception downloading MMS for "
                        + MmsService.formatCrossStackMessageId(mMessageId)
                        + " using the carrier messaging service: " + e, e);
                mCarrierDownloadCallback.onDownloadMmsComplete(
                        CarrierMessagingService.DOWNLOAD_STATUS_RETRY_ON_CARRIER_NETWORK);
            }
        }
    }

    /**
     * A callback which notifies carrier messaging app send result. Once the result is ready, the
     * carrier messaging service connection is disposed.
     */
    private final class CarrierDownloadCompleteCallback extends
            MmsRequest.CarrierMmsActionCallback {
        private final Context mContext;
        private final CarrierDownloadManager mCarrierDownloadManager;

        public CarrierDownloadCompleteCallback(Context context,
                CarrierDownloadManager carrierDownloadManager) {
            mContext = context;
            mCarrierDownloadManager = carrierDownloadManager;
        }

        @Override
        public void onSendMmsComplete(int result, byte[] sendConfPdu) {
            LogUtil.e("Unexpected onSendMmsComplete call with result: " + result
                    + ", " + MmsService.formatCrossStackMessageId(mMessageId));
        }

        @Override
        public void onDownloadMmsComplete(int result) {
            LogUtil.d("Carrier app result for download: " + result
                    + ", " + MmsService.formatCrossStackMessageId(mMessageId));
            mCarrierDownloadManager.disposeConnection(mContext);

            if (!maybeFallbackToRegularDelivery(result)) {
                processResult(mContext, toSmsManagerResult(result), null/* response */,
                        0/* httpStatusCode */, /* handledByCarrierApp= */ true);
            }
        }
    }

    protected long getPayloadSize() {
        long wapSize = 0;
        try {
            wapSize = SmsManager.getSmsManagerForSubscriptionId(mSubId)
                    .getWapMessageSize(mLocationUrl);
        } catch (java.util.NoSuchElementException e) {
            // The download url wasn't found in the wap push cache. Since we're connected to
            // a satellite and don't know the size of the download, block the download with a
            // wapSize of 0.
        }
        return wapSize;
    }
}
