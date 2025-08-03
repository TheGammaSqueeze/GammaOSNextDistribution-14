/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.ons;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.euicc.DownloadableSubscription;
import android.telephony.euicc.EuiccManager;
import android.util.Log;
import android.util.Pair;

import com.android.ons.ONSProfileDownloader.DownloadProfileResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ONSProfileDownloaderTest extends ONSBaseTest {
    private static final String TAG = ONSProfileDownloaderTest.class.getName();
    private static final int TEST_SUB_ID = 1;
    private static final String TEST_SMDP_ADDRESS = "TEST-ESIM.COM";

    @Mock
    Context mMockContext;
    @Mock
    EuiccManager mMockEUICCManager;
    @Mock
    SubscriptionInfo mMockSubInfo;
    @Mock
    CarrierConfigManager mMockCarrierConfigManager;
    @Mock
    private ONSProfileConfigurator mMockONSProfileConfig;
    @Mock
    ONSProfileDownloader.IONSProfileDownloaderListener mMockDownloadListener;

    @Before
    public void setUp() throws Exception {
        super.setUp("ONSTest");
        MockitoAnnotations.initMocks(this);
        Looper.prepare();
    }

    static class WorkerThread extends Thread {
        Looper mWorkerLooper;
        private final Runnable mRunnable;

        WorkerThread(Runnable runnable) {
            mRunnable = runnable;
        }

        @Override
        public void run() {
            super.run();
            Looper.prepare();
            mWorkerLooper = Looper.myLooper();
            mRunnable.run();
            mWorkerLooper.loop();
        }

        public void exit() {
            mWorkerLooper.quitSafely();
        }
    }

    @Test
    public void testNullSMDPAddress() {
        doReturn(TEST_SUB_ID).when(mMockSubInfo).getSubscriptionId();
        PersistableBundle config = new PersistableBundle();
        config.putString(CarrierConfigManager.KEY_SMDP_SERVER_ADDRESS_STRING, null);
        doReturn(config).when(mMockCarrierConfigManager).getConfigForSubId(TEST_SUB_ID);

        ONSProfileDownloader onsProfileDownloader = new ONSProfileDownloader(mMockContext,
                mMockCarrierConfigManager, mMockEUICCManager, mMockONSProfileConfig, null);

        onsProfileDownloader.downloadProfile(mMockSubInfo.getSubscriptionId());

        verify(mMockEUICCManager, never()).downloadSubscription(null, true, null);
    }

    @Test
    public void testDownloadSuccessCallback() {

        final Object lock = new Object();
        final ONSProfileDownloader.IONSProfileDownloaderListener mListener =
                new ONSProfileDownloader.IONSProfileDownloaderListener() {
                    @Override
                    public void onDownloadComplete(int primarySubId) {
                        assertEquals(primarySubId, TEST_SUB_ID);
                        synchronized (lock) {
                            lock.notify();
                        }
                    }

                    @Override
                    public void onDownloadError(
                            int pSIMSubId,
                            ONSProfileDownloader.DownloadRetryResultCode operationCode,
                            int detailedErrorCode) {

                    }
                };

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                ONSProfileDownloader onsProfileDownloader = new ONSProfileDownloader(mMockContext,
                        mMockCarrierConfigManager, mMockEUICCManager, mMockONSProfileConfig,
                        mListener);

                Intent intent = new Intent(mContext, ONSProfileResultReceiver.class);
                intent.setAction(ONSProfileDownloader.ACTION_ONS_ESIM_DOWNLOAD);
                intent.putExtra(Intent.EXTRA_COMPONENT_NAME, ONSProfileDownloader.class.getName());
                intent.putExtra(ONSProfileDownloader.PARAM_PRIMARY_SUBID, TEST_SUB_ID);
                intent.putExtra(ONSProfileDownloader.PARAM_REQUEST_TYPE,
                        ONSProfileDownloader.REQUEST_CODE_DOWNLOAD_SUB);
                intent.putExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_OPERATION_CODE, 0);

                onsProfileDownloader.onCallbackIntentReceived(intent,
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK);
            }
        };

        WorkerThread workerThread = new WorkerThread(runnable);
        workerThread.start();

        synchronized (lock) {
            try {
                lock.wait();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        }

        workerThread.exit();
    }

    @Test
    public void testDownloadFailureUnresolvableError() {
        PersistableBundle config = new PersistableBundle();
        config.putInt(CarrierConfigManager.KEY_ESIM_DOWNLOAD_RETRY_BACKOFF_TIMER_SEC_INT, 1);
        config.putInt(CarrierConfigManager.KEY_ESIM_MAX_DOWNLOAD_RETRY_ATTEMPTS_INT, 2);
        config.putString(CarrierConfigManager.KEY_SMDP_SERVER_ADDRESS_STRING, TEST_SMDP_ADDRESS);
        doReturn(config).when(mMockCarrierConfigManager).getConfigForSubId(TEST_SUB_ID);

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                ONSProfileDownloader onsProfileDownloader = new ONSProfileDownloader(mMockContext,
                        mMockCarrierConfigManager, mMockEUICCManager, mMockONSProfileConfig,
                        mMockDownloadListener);

                Intent intent = new Intent(mContext, ONSProfileResultReceiver.class);
                intent.setAction(ONSProfileDownloader.ACTION_ONS_ESIM_DOWNLOAD);
                intent.putExtra(Intent.EXTRA_COMPONENT_NAME, ONSProfileDownloader.class.getName());
                intent.putExtra(ONSProfileDownloader.PARAM_PRIMARY_SUBID, TEST_SUB_ID);
                intent.putExtra(ONSProfileDownloader.PARAM_REQUEST_TYPE,
                        ONSProfileDownloader.REQUEST_CODE_DOWNLOAD_SUB);

                onsProfileDownloader.onCallbackIntentReceived(intent,
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR);
            }
        };

        WorkerThread workerThread = new WorkerThread(runnable);
        workerThread.start();

        try {
            Thread.sleep(3000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        verifyZeroInteractions(mMockEUICCManager);
        workerThread.exit();
    }

    @Test
    public void testDownloadFailureMemoryFullError() {

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                ONSProfileDownloader onsProfileDownloader = new ONSProfileDownloader(mMockContext,
                        mMockCarrierConfigManager, mMockEUICCManager, mMockONSProfileConfig,
                        mMockDownloadListener);

                Intent intent = new Intent(mContext, ONSProfileResultReceiver.class);
                intent.setAction(ONSProfileDownloader.ACTION_ONS_ESIM_DOWNLOAD);
                intent.putExtra(Intent.EXTRA_COMPONENT_NAME, ONSProfileDownloader.class.getName());
                intent.putExtra(ONSProfileDownloader.PARAM_PRIMARY_SUBID, TEST_SUB_ID);
                intent.putExtra(ONSProfileDownloader.PARAM_REQUEST_TYPE,
                        ONSProfileDownloader.REQUEST_CODE_DOWNLOAD_SUB);
                intent.putExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_OPERATION_CODE,
                        EuiccManager.OPERATION_EUICC_GSMA);
                intent.putExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_ERROR_CODE,
                        EuiccManager.ERROR_EUICC_INSUFFICIENT_MEMORY);

                onsProfileDownloader.onCallbackIntentReceived(intent,
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR);
            }
        };

        WorkerThread workerThread = new WorkerThread(runnable);
        workerThread.start();

        try {
            Thread.sleep(1000);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        verify(mMockDownloadListener).onDownloadError(
                TEST_SUB_ID, ONSProfileDownloader.DownloadRetryResultCode.ERR_MEMORY_FULL, 0);
        workerThread.exit();
    }

    @Test
    public void testDownloadFailureConnectionError() {
        PersistableBundle config = new PersistableBundle();
        config.putInt(CarrierConfigManager.KEY_ESIM_DOWNLOAD_RETRY_BACKOFF_TIMER_SEC_INT, 1);
        config.putInt(CarrierConfigManager.KEY_ESIM_MAX_DOWNLOAD_RETRY_ATTEMPTS_INT, 2);
        config.putString(CarrierConfigManager.KEY_SMDP_SERVER_ADDRESS_STRING, TEST_SMDP_ADDRESS);
        doReturn(config).when(mMockCarrierConfigManager).getConfigForSubId(TEST_SUB_ID);
        doNothing().when(mMockDownloadListener).onDownloadError(
                TEST_SUB_ID, ONSProfileDownloader.DownloadRetryResultCode.ERR_RETRY_DOWNLOAD, 0);

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                ONSProfileDownloader onsProfileDownloader = new ONSProfileDownloader(mContext,
                        mMockCarrierConfigManager, mMockEUICCManager, mMockONSProfileConfig,
                        mMockDownloadListener);

                Intent intent = new Intent(mContext, ONSProfileResultReceiver.class);
                intent.setAction(ONSProfileDownloader.ACTION_ONS_ESIM_DOWNLOAD);
                intent.putExtra(Intent.EXTRA_COMPONENT_NAME, ONSProfileDownloader.class.getName());
                intent.putExtra(ONSProfileDownloader.PARAM_PRIMARY_SUBID, TEST_SUB_ID);
                intent.putExtra(ONSProfileDownloader.PARAM_REQUEST_TYPE,
                        ONSProfileDownloader.REQUEST_CODE_DOWNLOAD_SUB);
                intent.putExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_ERROR_CODE,
                        EuiccManager.ERROR_CONNECTION_ERROR);
                intent.putExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_OPERATION_CODE,
                        EuiccManager.OPERATION_SMDX);

                onsProfileDownloader.onCallbackIntentReceived(intent,
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR);
            }
        };

        WorkerThread workerThread = new WorkerThread(runnable);
        workerThread.start();

        //After first download error, next download will be triggered between 1 & 2*
        //CarrierConfigManager.KEY_ESIM_DOWNLOAD_RETRY_BACKOFF_TIMER_SEC_INT(1sec for testing)
        //Should take less than 2 secs for download re-attempt.
        try {
            Thread.sleep(3000);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        String testActCode = DownloadableSubscription.forActivationCode(TEST_SMDP_ADDRESS)
                .getEncodedActivationCode();

        verify(mMockDownloadListener).onDownloadError(
                TEST_SUB_ID, ONSProfileDownloader.DownloadRetryResultCode.ERR_RETRY_DOWNLOAD, 0);

        workerThread.exit();
    }

    @Test
    public void testDownloadFailureTimeout() {
        PersistableBundle config = new PersistableBundle();
        config.putInt(CarrierConfigManager.KEY_ESIM_DOWNLOAD_RETRY_BACKOFF_TIMER_SEC_INT, 1);
        config.putInt(CarrierConfigManager.KEY_ESIM_MAX_DOWNLOAD_RETRY_ATTEMPTS_INT, 2);
        config.putString(CarrierConfigManager.KEY_SMDP_SERVER_ADDRESS_STRING, TEST_SMDP_ADDRESS);
        doReturn(config).when(mMockCarrierConfigManager).getConfigForSubId(TEST_SUB_ID);
        doNothing().when(mMockDownloadListener).onDownloadError(
                TEST_SUB_ID, ONSProfileDownloader.DownloadRetryResultCode.ERR_RETRY_DOWNLOAD, 0);

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                ONSProfileDownloader onsProfileDownloader = new ONSProfileDownloader(mContext,
                        mMockCarrierConfigManager, mMockEUICCManager, mMockONSProfileConfig,
                        mMockDownloadListener);

                Intent intent = new Intent(mContext, ONSProfileResultReceiver.class);
                intent.setAction(ONSProfileDownloader.ACTION_ONS_ESIM_DOWNLOAD);
                intent.putExtra(Intent.EXTRA_COMPONENT_NAME, ONSProfileDownloader.class.getName());
                intent.putExtra(ONSProfileDownloader.PARAM_PRIMARY_SUBID, TEST_SUB_ID);
                intent.putExtra(ONSProfileDownloader.PARAM_REQUEST_TYPE,
                        ONSProfileDownloader.REQUEST_CODE_DOWNLOAD_SUB);
                intent.putExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_OPERATION_CODE,
                        EuiccManager.OPERATION_SIM_SLOT);
                intent.putExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_ERROR_CODE,
                        EuiccManager.ERROR_TIME_OUT);

                onsProfileDownloader.onCallbackIntentReceived(intent,
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR);
            }
        };

        WorkerThread workerThread = new WorkerThread(runnable);
        workerThread.start();

        //After first download error, next download will be triggered between 1 & 2*
        //CarrierConfigManager.KEY_ESIM_DOWNLOAD_RETRY_BACKOFF_TIMER_SEC_INT(1sec for testing)
        //Should take less than 2 secs for download re-attempt.
        try {
            Thread.sleep(3000);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        String testActCode = DownloadableSubscription.forActivationCode(TEST_SMDP_ADDRESS)
                .getEncodedActivationCode();

        verify(mMockDownloadListener).onDownloadError(
                TEST_SUB_ID, ONSProfileDownloader.DownloadRetryResultCode.ERR_RETRY_DOWNLOAD, 0);

        workerThread.exit();
    }

    @Test
    public void testDownloadFailureOperationBusy() {
        PersistableBundle config = new PersistableBundle();
        config.putInt(CarrierConfigManager.KEY_ESIM_DOWNLOAD_RETRY_BACKOFF_TIMER_SEC_INT, 1);
        config.putInt(CarrierConfigManager.KEY_ESIM_MAX_DOWNLOAD_RETRY_ATTEMPTS_INT, 2);
        config.putString(CarrierConfigManager.KEY_SMDP_SERVER_ADDRESS_STRING, TEST_SMDP_ADDRESS);
        doReturn(config).when(mMockCarrierConfigManager).getConfigForSubId(TEST_SUB_ID);
        doNothing().when(mMockDownloadListener).onDownloadError(
                TEST_SUB_ID, ONSProfileDownloader.DownloadRetryResultCode.ERR_RETRY_DOWNLOAD, 0);

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                ONSProfileDownloader onsProfileDownloader = new ONSProfileDownloader(mContext,
                        mMockCarrierConfigManager, mMockEUICCManager, mMockONSProfileConfig,
                        mMockDownloadListener);

                Intent intent = new Intent(mContext, ONSProfileResultReceiver.class);
                intent.setAction(ONSProfileDownloader.ACTION_ONS_ESIM_DOWNLOAD);
                intent.putExtra(Intent.EXTRA_COMPONENT_NAME, ONSProfileDownloader.class.getName());
                intent.putExtra(ONSProfileDownloader.PARAM_PRIMARY_SUBID, TEST_SUB_ID);
                intent.putExtra(ONSProfileDownloader.PARAM_REQUEST_TYPE,
                        ONSProfileDownloader.REQUEST_CODE_DOWNLOAD_SUB);
                intent.putExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_OPERATION_CODE,
                        EuiccManager.OPERATION_DOWNLOAD);
                intent.putExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_ERROR_CODE,
                        EuiccManager.ERROR_OPERATION_BUSY);

                onsProfileDownloader.onCallbackIntentReceived(intent,
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR);
            }
        };

        WorkerThread workerThread = new WorkerThread(runnable);
        workerThread.start();

        //After first download error, next download will be triggered between 1 & 2*
        //CarrierConfigManager.KEY_ESIM_DOWNLOAD_RETRY_BACKOFF_TIMER_SEC_INT(1sec for testing)
        //Should take less than 2 secs for download re-attempt.
        try {
            Thread.sleep(3000);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        String testActCode = DownloadableSubscription.forActivationCode(TEST_SMDP_ADDRESS)
                .getEncodedActivationCode();

        verify(mMockDownloadListener).onDownloadError(
                TEST_SUB_ID, ONSProfileDownloader.DownloadRetryResultCode.ERR_RETRY_DOWNLOAD, 0);

        workerThread.exit();
    }

    @Test
    public void testDownloadFailureInvalidResponse() {
        PersistableBundle config = new PersistableBundle();
        config.putInt(CarrierConfigManager.KEY_ESIM_DOWNLOAD_RETRY_BACKOFF_TIMER_SEC_INT, 1);
        config.putInt(CarrierConfigManager.KEY_ESIM_MAX_DOWNLOAD_RETRY_ATTEMPTS_INT, 2);
        config.putString(CarrierConfigManager.KEY_SMDP_SERVER_ADDRESS_STRING, TEST_SMDP_ADDRESS);
        doReturn(config).when(mMockCarrierConfigManager).getConfigForSubId(TEST_SUB_ID);
        doNothing().when(mMockDownloadListener).onDownloadError(
                TEST_SUB_ID, ONSProfileDownloader.DownloadRetryResultCode.ERR_RETRY_DOWNLOAD, 0);

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                ONSProfileDownloader onsProfileDownloader = new ONSProfileDownloader(mContext,
                        mMockCarrierConfigManager, mMockEUICCManager, mMockONSProfileConfig,
                        mMockDownloadListener);

                Intent intent = new Intent(mContext, ONSProfileResultReceiver.class);
                intent.setAction(ONSProfileDownloader.ACTION_ONS_ESIM_DOWNLOAD);
                intent.putExtra(Intent.EXTRA_COMPONENT_NAME, ONSProfileDownloader.class.getName());
                intent.putExtra(ONSProfileDownloader.PARAM_PRIMARY_SUBID, TEST_SUB_ID);
                intent.putExtra(ONSProfileDownloader.PARAM_REQUEST_TYPE,
                        ONSProfileDownloader.REQUEST_CODE_DOWNLOAD_SUB);
                intent.putExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_OPERATION_CODE,
                        EuiccManager.OPERATION_SMDX);
                intent.putExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_ERROR_CODE,
                        EuiccManager.ERROR_INVALID_RESPONSE);

                onsProfileDownloader.onCallbackIntentReceived(intent,
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR);
            }
        };

        WorkerThread workerThread = new WorkerThread(runnable);
        workerThread.start();

        try {
            Thread.sleep(3000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        verify(mMockDownloadListener).onDownloadError(
                TEST_SUB_ID, ONSProfileDownloader.DownloadRetryResultCode.ERR_UNRESOLVABLE, 0);
        workerThread.exit();
    }

    @Test
    public void testDownloadOpCode() {
        final Object lock = new Object();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                ONSProfileDownloader onsProfileDownloader = new ONSProfileDownloader(mMockContext,
                        mMockCarrierConfigManager, mMockEUICCManager, mMockONSProfileConfig,
                        mMockDownloadListener);

                ONSProfileDownloader.DownloadHandler downloadHandler =
                        onsProfileDownloader.new DownloadHandler();

                ONSProfileDownloader.DownloadRetryResultCode res =
                        downloadHandler.mapDownloaderErrorCode(
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK, 0, 0, 0);
                assertEquals(
                        ONSProfileDownloader.DownloadRetryResultCode.DOWNLOAD_SUCCESSFUL, res);

                res = downloadHandler.mapDownloaderErrorCode(
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR, 0,
                        EuiccManager.OPERATION_EUICC_GSMA,
                        EuiccManager.ERROR_EUICC_INSUFFICIENT_MEMORY);
                assertEquals(ONSProfileDownloader.DownloadRetryResultCode
                        .ERR_MEMORY_FULL, res);

                res = downloadHandler.mapDownloaderErrorCode(
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR, 0,
                        EuiccManager.OPERATION_SIM_SLOT,
                        EuiccManager.ERROR_TIME_OUT);
                assertEquals(ONSProfileDownloader.DownloadRetryResultCode
                        .ERR_RETRY_DOWNLOAD, res);

                res = downloadHandler.mapDownloaderErrorCode(
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR, 0,
                        EuiccManager.OPERATION_SMDX,
                        EuiccManager.ERROR_CONNECTION_ERROR);
                assertEquals(ONSProfileDownloader.DownloadRetryResultCode
                        .ERR_RETRY_DOWNLOAD, res);

                res = downloadHandler.mapDownloaderErrorCode(
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR, 0,
                        EuiccManager.OPERATION_SMDX,
                        EuiccManager.ERROR_INVALID_RESPONSE);
                assertEquals(ONSProfileDownloader.DownloadRetryResultCode
                        .ERR_UNRESOLVABLE, res);

                res = downloadHandler.mapDownloaderErrorCode(
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR, 0,
                        EuiccManager.OPERATION_SMDX,
                        EuiccManager.ERROR_INVALID_RESPONSE);
                assertEquals(ONSProfileDownloader.DownloadRetryResultCode
                        .ERR_UNRESOLVABLE, res);

                res = downloadHandler.mapDownloaderErrorCode(
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR, 0xA810048,
                        EuiccManager.OPERATION_SMDX_SUBJECT_REASON_CODE, 0);
                assertEquals(ONSProfileDownloader.DownloadRetryResultCode
                        .ERR_MEMORY_FULL, res);

                synchronized (lock) {
                    lock.notifyAll();
                }
            }
        };

        WorkerThread workerThread = new WorkerThread(runnable);
        workerThread.start();

        synchronized (lock) {
            try {
                lock.wait();
            } catch (Exception e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
        }

        workerThread.exit();
    }

    @Test
    public void testSMDPErrorParsing() {
        final Object lock = new Object();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                Pair<String, String> res = ONSProfileDownloader
                        .decodeSmdxSubjectAndReasonCode(0xA8B1051);
                //0A->OPERATION_SMDX_SUBJECT_REASON_CODE
                //8B1 -> 8.11.1
                //051 -> 5.1
                assertEquals("8.11.1", res.first);
                assertEquals("5.1", res.second);

                res = ONSProfileDownloader
                        .decodeSmdxSubjectAndReasonCode(0xA810061);
                //0A->OPERATION_SMDX_SUBJECT_REASON_CODE
                //810 -> 8.1.0
                //061 -> 6.1
                assertEquals("8.1.0", res.first);
                assertEquals("6.1", res.second);

                res = ONSProfileDownloader
                        .decodeSmdxSubjectAndReasonCode(0xA810048);
                //0A->OPERATION_SMDX_SUBJECT_REASON_CODE
                //810 -> 8.1.0
                //048 -> 4.8
                assertEquals("8.1.0", res.first);
                assertEquals("4.8", res.second);

                res = ONSProfileDownloader
                        .decodeSmdxSubjectAndReasonCode(0xA8B1022);
                //0A->OPERATION_SMDX_SUBJECT_REASON_CODE
                //8B1 -> 8.11.1
                //022 -> 2.2
                assertEquals("8.11.1", res.first);
                assertEquals("2.2", res.second);

                synchronized (lock) {
                    lock.notifyAll();
                }
            }
        };

        WorkerThread workerThread = new WorkerThread(runnable);
        workerThread.start();

        synchronized (lock) {
            try {
                lock.wait();
            } catch (Exception e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
        }

        workerThread.exit();
    }

    @Test
    public void testMultipleDownloadRequests() {
        doReturn(TEST_SUB_ID).when(mMockSubInfo).getSubscriptionId();
        PersistableBundle config = new PersistableBundle();
        config.putString(CarrierConfigManager.KEY_SMDP_SERVER_ADDRESS_STRING, TEST_SMDP_ADDRESS);
        doReturn(config).when(mMockCarrierConfigManager).getConfigForSubId(TEST_SUB_ID);

        ONSProfileDownloader onsProfileDownloader = new ONSProfileDownloader(mContext,
                mMockCarrierConfigManager, mMockEUICCManager, mMockONSProfileConfig,
                mMockDownloadListener);

        //When multiple download requests are received, download should be triggered only once.
        onsProfileDownloader.downloadProfile(mMockSubInfo.getSubscriptionId());
        onsProfileDownloader.downloadProfile(mMockSubInfo.getSubscriptionId());
        onsProfileDownloader.downloadProfile(mMockSubInfo.getSubscriptionId());
        onsProfileDownloader.downloadProfile(mMockSubInfo.getSubscriptionId());
        verify(mMockEUICCManager, times(1)).downloadSubscription(any(), eq(true), any());

        //Simulate response for download request from LPA.
        Intent intent = new Intent(mContext, ONSProfileResultReceiver.class);
        intent.setAction(ONSProfileDownloader.ACTION_ONS_ESIM_DOWNLOAD);
        intent.putExtra(Intent.EXTRA_COMPONENT_NAME, ONSProfileDownloader.class.getName());
        intent.putExtra(ONSProfileDownloader.PARAM_PRIMARY_SUBID, TEST_SUB_ID);
        intent.putExtra(ONSProfileDownloader.PARAM_REQUEST_TYPE,
                ONSProfileDownloader.REQUEST_CODE_DOWNLOAD_SUB);
        intent.putExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_OPERATION_CODE,
                EuiccManager.OPERATION_DOWNLOAD);

        onsProfileDownloader.onCallbackIntentReceived(intent,
                EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK);

        //Trigger new download after a sec
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        //After download response is received, new download requests should be processed.
        onsProfileDownloader.downloadProfile(mMockSubInfo.getSubscriptionId());
        onsProfileDownloader.downloadProfile(mMockSubInfo.getSubscriptionId());
        onsProfileDownloader.downloadProfile(mMockSubInfo.getSubscriptionId());
        onsProfileDownloader.downloadProfile(mMockSubInfo.getSubscriptionId());
        verify(mMockEUICCManager, times(1)).downloadSubscription(any(), eq(true), any());
    }

    @Test
    public void testDownloadRequestFailures() {
        doReturn(TEST_SUB_ID).when(mMockSubInfo).getSubscriptionId();
        PersistableBundle invalidConfig = new PersistableBundle();
        invalidConfig.putString(CarrierConfigManager.KEY_SMDP_SERVER_ADDRESS_STRING, "");

        PersistableBundle validConfig = new PersistableBundle();
        validConfig.putString(
                CarrierConfigManager.KEY_SMDP_SERVER_ADDRESS_STRING, TEST_SMDP_ADDRESS);

        // Only the first download request, will receive invalid SMDP server address.
        doReturn(invalidConfig, validConfig)
                .when(mMockCarrierConfigManager)
                .getConfigForSubId(TEST_SUB_ID);

        ONSProfileDownloader onsProfileDownloader =
                new ONSProfileDownloader(
                        mContext,
                        mMockCarrierConfigManager,
                        mMockEUICCManager,
                        mMockONSProfileConfig,
                        mMockDownloadListener);

        // First download request to be failed with INVALID_SMDP_ADDRESS error because of empty SMDP
        // server address in configuration.
        DownloadProfileResult retryResultCode =
                onsProfileDownloader.downloadProfile(mMockSubInfo.getSubscriptionId());
        assertEquals(DownloadProfileResult.INVALID_SMDP_ADDRESS, retryResultCode);

        verify(mMockEUICCManager, never()).downloadSubscription(any(), eq(true), any());

        // Second Download request should be success and processed to EuiccManager.
        retryResultCode = onsProfileDownloader.downloadProfile(mMockSubInfo.getSubscriptionId());
        assertEquals(DownloadProfileResult.SUCCESS, retryResultCode);
        verify(mMockEUICCManager).downloadSubscription(any(), eq(true), any());

        // Since download request is in progress, no further request to be sent to EuiccManager.
        // They should return with DUPLICATE_REQUEST error.
        retryResultCode = onsProfileDownloader.downloadProfile(mMockSubInfo.getSubscriptionId());
        assertEquals(DownloadProfileResult.DUPLICATE_REQUEST, retryResultCode);

        retryResultCode = onsProfileDownloader.downloadProfile(mMockSubInfo.getSubscriptionId());
        assertEquals(DownloadProfileResult.DUPLICATE_REQUEST, retryResultCode);

        verify(mMockEUICCManager).downloadSubscription(any(), eq(true), any());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }
}
