/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
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

package com.android.bips;

import android.net.Uri;
import android.os.Bundle;
import android.print.PrintJobId;
import android.printservice.PrintJob;
import android.util.Log;

import com.android.bips.discovery.ConnectionListener;
import com.android.bips.discovery.DiscoveredPrinter;
import com.android.bips.discovery.MdnsDiscovery;
import com.android.bips.ipp.Backend;
import com.android.bips.ipp.CapabilitiesCache;
import com.android.bips.ipp.CertificateStore;
import com.android.bips.ipp.JobStatus;
import com.android.bips.jni.BackendConstants;
import com.android.bips.jni.LocalPrinterCapabilities;
import com.android.bips.p2p.P2pPrinterConnection;
import com.android.bips.p2p.P2pUtils;

import java.util.ArrayList;
import java.util.StringJoiner;
import java.util.function.Consumer;

/**
 * Manage the process of delivering a print job
 */
class LocalPrintJob implements MdnsDiscovery.Listener, ConnectionListener,
        CapabilitiesCache.OnLocalPrinterCapabilities {
    private static final String TAG = LocalPrintJob.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static final String IPP_SCHEME = "ipp";
    private static final String IPPS_SCHEME = "ipps";

    /** Maximum time to wait to find a printer before failing the job */
    private static final int DISCOVERY_TIMEOUT = 2 * 60 * 1000;

    // Internal job states
    private static final int STATE_INIT = 0;
    private static final int STATE_DISCOVERY = 1;
    private static final int STATE_CAPABILITIES = 2;
    private static final int STATE_DELIVERING = 3;
    private static final int STATE_SECURITY = 4;
    private static final int STATE_CANCEL = 5;
    private static final int STATE_DONE = 6;

    private final BuiltInPrintService mPrintService;
    private final PrintJob mPrintJob;
    private final Backend mBackend;

    private int mState;
    private Consumer<LocalPrintJob> mCompleteConsumer;
    private Uri mPath;
    private DelayedAction mDiscoveryTimeout;
    private P2pPrinterConnection mConnection;
    private LocalPrinterCapabilities mCapabilities;
    private CertificateStore mCertificateStore;
    private long mStartTime;
    private ArrayList<String> mBlockedReasons = new ArrayList<>();

    /**
     * Construct the object; use {@link #start(Consumer)} to begin job processing.
     */
    LocalPrintJob(BuiltInPrintService printService, Backend backend, PrintJob printJob) {
        mPrintService = printService;
        mBackend = backend;
        mPrintJob = printJob;
        mCertificateStore = mPrintService.getCertificateStore();
        mState = STATE_INIT;

        // Tell the job it is blocked (until start())
        mPrintJob.start();
        mPrintJob.block(printService.getString(R.string.waiting_to_send));
    }

    /**
     * Begin the process of delivering the job. Internally, discovers the target printer,
     * obtains its capabilities, delivers the job to the printer, and waits for job completion.
     *
     * @param callback Callback to be issued when job processing is complete
     */
    void start(Consumer<LocalPrintJob> callback) {
        mStartTime = System.currentTimeMillis();
        // TODO: Log job attempted event here using getJobAttemptedBundle()
        if (DEBUG) Log.d(TAG, "start() " + mPrintJob);
        if (mState != STATE_INIT) {
            Log.w(TAG, "Invalid start state " + mState);
            return;
        }
        mPrintJob.start();

        // Acquire a lock so that WiFi isn't put to sleep while we send the job
        mPrintService.lockWifi();

        mState = STATE_DISCOVERY;
        mCompleteConsumer = callback;
        mDiscoveryTimeout = mPrintService.delay(DISCOVERY_TIMEOUT, () -> {
            if (DEBUG) Log.d(TAG, "Discovery timeout");
            if (mState == STATE_DISCOVERY) {
                finish(false, mPrintService.getString(R.string.printer_offline));
            }
        });

        mPrintService.getDiscovery().start(this);
    }

    /**
     * Restart the job if possible.
     */
    void restart() {
        if (DEBUG) Log.d(TAG, "restart() " + mPrintJob + " in state " + mState);
        if (mState == STATE_SECURITY) {
            mCapabilities.certificate = mCertificateStore.get(mCapabilities.uuid);
            deliver();
        }
    }

    void cancel() {
        if (DEBUG) Log.d(TAG, "cancel() " + mPrintJob + " in state " + mState);

        switch (mState) {
            case STATE_DISCOVERY:
            case STATE_CAPABILITIES:
            case STATE_SECURITY:
                // Cancel immediately
                mState = STATE_CANCEL;
                finish(false, null);
                break;

            case STATE_DELIVERING:
                // Request cancel and wait for completion
                mState = STATE_CANCEL;
                mBackend.cancel();
                break;
        }
        Bundle bundle = getJobCompletedAnalyticsBundle(BackendConstants.JOB_DONE_CANCELLED);
        bundle.putString(BackendConstants.PARAM_ERROR_MESSAGES, getStringifiedBlockedReasons());
        // TODO: Log job completed event here with the above bundle
    }

    PrintJobId getPrintJobId() {
        return mPrintJob.getId();
    }

    @Override
    public void onPrinterFound(DiscoveredPrinter printer) {
        if (mState != STATE_DISCOVERY) {
            return;
        }
        if (!printer.getId(mPrintService).equals(mPrintJob.getInfo().getPrinterId())) {
            return;
        }

        if (DEBUG) Log.d(TAG, "onPrinterFound() " + printer.name + " state=" + mState);

        if (P2pUtils.isP2p(printer)) {
            // Launch a P2P connection attempt
            mConnection = new P2pPrinterConnection(mPrintService, printer, this);
            return;
        }

        if (P2pUtils.isOnConnectedInterface(mPrintService, printer) && mConnection == null) {
            // Hold the P2P connection up during printing
            mConnection = new P2pPrinterConnection(mPrintService, printer, this);
        }

        // We have a good path so stop discovering and get capabilities
        mPrintService.getDiscovery().stop(this);
        mState = STATE_CAPABILITIES;
        mPath = printer.path;
        // Upgrade to IPPS path if present
        for (Uri path : printer.paths) {
            if (IPPS_SCHEME.equals(path.getScheme())) {
                mPath = path;
                break;
            }
        }

        mPrintService.getCapabilitiesCache().request(printer, true, this);
    }

    @Override
    public void onPrinterLost(DiscoveredPrinter printer) {
        // Ignore (the capability request, if any, will fail)
    }

    @Override
    public void onConnectionComplete(DiscoveredPrinter printer) {
        // Ignore late connection events
        if (mState != STATE_DISCOVERY) {
            return;
        }

        if (printer == null) {
            finish(false, mPrintService.getString(R.string.failed_printer_connection));
        } else if (mPrintJob.isBlocked()) {
            mPrintJob.start();
        }
    }

    @Override
    public void onConnectionDelayed(boolean delayed) {
        if (DEBUG) Log.d(TAG, "onConnectionDelayed " + delayed);

        // Ignore late events
        if (mState != STATE_DISCOVERY) {
            return;
        }

        if (delayed) {
            mPrintJob.block(mPrintService.getString(R.string.connect_hint_text));
        } else {
            // Remove block message
            mPrintJob.start();
        }
    }

    PrintJob getPrintJob() {
        return mPrintJob;
    }

    @Override
    public void onCapabilities(LocalPrinterCapabilities capabilities) {
        if (DEBUG) Log.d(TAG, "Capabilities for " + mPath + " are " + capabilities);
        if (mState != STATE_CAPABILITIES) {
            return;
        }

        if (capabilities == null) {
            finish(false, mPrintService.getString(R.string.printer_offline));
        } else {
            if (DEBUG) Log.d(TAG, "Starting backend print of " + mPrintJob);
            if (mDiscoveryTimeout != null) {
                mDiscoveryTimeout.cancel();
            }
            mCapabilities = capabilities;
            deliver();
        }
    }

    private void deliver() {
        // Upgrade to IPPS if necessary
        Uri newUri = Uri.parse(mCapabilities.path);
        if (IPPS_SCHEME.equals(newUri.getScheme()) && newUri.getPort() > 0 &&
            IPP_SCHEME.equals(mPath.getScheme())) {
            mPath = mPath.buildUpon().scheme(IPPS_SCHEME).encodedAuthority(mPath.getHost() +
                ":" + newUri.getPort()).build();
        }

        if (DEBUG) Log.d(TAG, "deliver() to " + mPath);
        if (mCapabilities.certificate != null && !IPPS_SCHEME.equals(mPath.getScheme())) {
            mState = STATE_SECURITY;
            mPrintJob.block(mPrintService.getString(R.string.printer_not_encrypted));
            mPrintService.notifyCertificateChange(mCapabilities.name,
                    mPrintJob.getInfo().getPrinterId(), mCapabilities.uuid, null);
        } else {
            mState = STATE_DELIVERING;
            mPrintJob.start();
            mBackend.print(mPath, mPrintJob, mCapabilities, this::handleJobStatus);
        }
    }

    private void handleJobStatus(JobStatus jobStatus) {
        if (DEBUG) Log.d(TAG, "onJobStatus() " + jobStatus);

        byte[] certificate = jobStatus.getCertificate();
        if (certificate != null && mCapabilities != null) {
            // If there is no certificate, record this one
            if (mCertificateStore.get(mCapabilities.uuid) == null) {
                if (DEBUG) Log.d(TAG, "Recording new certificate");
                mCertificateStore.put(mCapabilities.uuid, certificate);
            }
        }

        mBlockedReasons.addAll(jobStatus.getBlockedReasons());

        switch (jobStatus.getJobState()) {
            case BackendConstants.JOB_STATE_DONE:
                Bundle bundle = getJobCompletedAnalyticsBundle(jobStatus.getJobResult());

                switch (jobStatus.getJobResult()) {
                    case BackendConstants.JOB_DONE_OK:
                        finish(true, null);
                        break;
                    case BackendConstants.JOB_DONE_CANCELLED:
                        mState = STATE_CANCEL;
                        finish(false, null);
                        bundle.putString(
                                BackendConstants.PARAM_ERROR_MESSAGES,
                                getStringifiedBlockedReasons());
                        break;
                    case BackendConstants.JOB_DONE_CORRUPT:
                        finish(false, mPrintService.getString(R.string.unreadable_input));
                        bundle.putString(
                                BackendConstants.PARAM_ERROR_MESSAGES,
                                getStringifiedBlockedReasons());
                        break;
                    case BackendConstants.JOB_DONE_BAD_CERTIFICATE:
                        handleBadCertificate(jobStatus);
                        break;
                    default:
                        // Job failed
                        finish(false, null);
                        bundle.putString(
                                BackendConstants.PARAM_ERROR_MESSAGES,
                                getStringifiedBlockedReasons());
                        break;
                }
                // TODO: Log JobCompleted analytic with the bundle here
                break;

            case BackendConstants.JOB_STATE_BLOCKED:
                if (mState == STATE_CANCEL) {
                    return;
                }
                int blockedId = jobStatus.getBlockedReasonId();
                blockedId = (blockedId == 0) ? R.string.printer_check : blockedId;
                String blockedReason = mPrintService.getString(blockedId);
                mPrintJob.block(blockedReason);
                break;

            case BackendConstants.JOB_STATE_RUNNING:
                if (mState == STATE_CANCEL) {
                    return;
                }
                mPrintJob.start();
                break;
        }
    }

    private void handleBadCertificate(JobStatus jobStatus) {
        byte[] certificate = jobStatus.getCertificate();

        if (certificate == null) {
            mPrintJob.fail(mPrintService.getString(R.string.printer_bad_certificate));
        } else {
            if (DEBUG) Log.d(TAG, "Certificate change detected.");
            mState = STATE_SECURITY;
            mPrintJob.block(mPrintService.getString(R.string.printer_bad_certificate));
            mPrintService.notifyCertificateChange(mCapabilities.name,
                    mPrintJob.getInfo().getPrinterId(), mCapabilities.uuid, certificate);
        }
    }

    /**
     * Terminate the job, issuing appropriate notifications.
     *
     * @param success true if the printer reported successful job completion
     * @param error   reason for job failure if known
     */
    private void finish(boolean success, String error) {
        if (DEBUG) Log.d(TAG, "finish() success=" + success + ", error=" + error);
        mPrintService.getDiscovery().stop(this);
        if (mDiscoveryTimeout != null) {
            mDiscoveryTimeout.cancel();
        }
        if (mConnection != null) {
            mConnection.close();
        }
        mPrintService.unlockWifi();
        mBackend.closeDocument();
        if (success) {
            // Job must not be blocked before completion
            mPrintJob.start();
            mPrintJob.complete();
        } else if (mState == STATE_CANCEL) {
            mPrintJob.cancel();
        } else {
            mPrintJob.fail(error);
        }
        mState = STATE_DONE;
        mCompleteConsumer.accept(LocalPrintJob.this);
    }

    /**
     * Get stringified blocked reasons delimited by '|'
     * @return delimited string of blocked reasons
     */
    private String getStringifiedBlockedReasons() {
        StringJoiner reasons = new StringJoiner("|");
        for (String reason: mBlockedReasons) {
            reasons.add(reason);
        }
        return reasons.toString();
    }

    /**
     * Get the job completed analytics bundle
     *
     * @param result result of the job
     * @return analytics bundle
     */
    private Bundle getJobCompletedAnalyticsBundle(String result) {
        Bundle bundle = new Bundle();
        bundle.putString(BackendConstants.PARAM_JOB_ID, mPrintJob.getId().toString());
        bundle.putLong(BackendConstants.PARAM_DATE_TIME, System.currentTimeMillis());
        // TODO: Add real location
        bundle.putString(BackendConstants.PARAM_LOCATION, "United States");
        // TODO: Add real user id
        bundle.putString(BackendConstants.PARAM_USER_ID, "userid");
        bundle.putString(BackendConstants.PARAM_RESULT, result);
        bundle.putLong(
                BackendConstants.PARAM_ELAPSED_TIME_ALL, System.currentTimeMillis() - mStartTime);
        return bundle;
    }

    /**
     * Get the job started analytics bundle
     *
     * @return analytics bundle
     */
    private Bundle getJobAttemptedAnalyticsBundle() {
        Bundle bundle = new Bundle();
        bundle.putString(BackendConstants.PARAM_JOB_ID, mPrintJob.getId().toString());
        bundle.putLong(BackendConstants.PARAM_DATE_TIME, System.currentTimeMillis());
        // TODO: Add real location
        bundle.putString(BackendConstants.PARAM_LOCATION, "United States");
        bundle.putInt(
                BackendConstants.PARAM_JOB_PAGES,
                mPrintJob.getInfo().getCopies() * mPrintJob.getDocument().getInfo().getPageCount());
        // TODO: Add real user id
        bundle.putString(BackendConstants.PARAM_USER_ID, "userid");
        // TODO: Determine whether the print job came from share to BIPS or from print system
        bundle.putString(BackendConstants.PARAM_SOURCE_PATH, "ShareToBips || PrintSystem");
        return bundle;
    }
}
