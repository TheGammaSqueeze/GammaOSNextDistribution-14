/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tradefed.host;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.util.RunInterruptedException;

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * Host options holder class.
 * This class is used to store host-wide options.
 */
@OptionClass(alias = "host_options", global_namespace = false)
public class HostOptions implements IHostOptions {

    @Option(name = "concurrent-flasher-limit", description =
            "The maximum number of concurrent flashers (may be useful to avoid memory constraints)")
    private Integer mConcurrentFlasherLimit = 1;

    @Option(
        name = "concurrent-download-limit",
        description =
                "The maximum number of concurrent downloads (may be useful to avoid network "
                        + "constraints)"
    )
    private Integer mConcurrentDownloadLimit = null;

    @Option(
            name = "concurrent-virtual-device-startup-limit",
            description =
                    "The maximum number of concurrent virtual device startup to avoid resource"
                            + " contentions depending on factors such as network, CPU, I/O etc.")
    private Integer mConcurrentVirtualDeviceStartupLimit = null;

    @Option(name = "concurrent-limits", description =
            "The maximum number of concurrent actions of a given type.")
    private Map<PermitLimitType, Integer> mConcurrentLimit = new HashMap<>();

    @Option(
        name = "fastboot-tmpdir",
        description = "The location of temporary directory used by fastboot"
    )
    private File mFastbootTmpDir = null;

    @Option(
            name = "enable-fastbootd-mode",
            description = "Feature flag to enable the support for fastbootd.")
    private boolean mEnableFastbootdMode = true;

    @Option(name = "download-cache-dir", description = "the directory for caching downloaded "
            + "flashing files. Should be on the same filesystem as java.io.tmpdir.  Consider "
            + "changing the java.io.tmpdir property if you want to move downloads to a different "
            + "filesystem.")
    private File mDownloadCacheDir = new File(System.getProperty("java.io.tmpdir"), "lc_cache");

    @Option(name = "use-sso-client", description = "Use a SingleSignOn client for HTTP requests.")
    private Boolean mUseSsoClient = true;

    @Option(
        name = "service-account-json-key-file",
        description =
                "Specify a service account json key file, and a String key name to identify it."
    )
    private Map<String, File> mJsonServiceAccountMap = new HashMap<>();

    @Option(name = "label", description = "Labels to describe the host.")
    private List<String> mLabels = new ArrayList<>();

    @Option(
            name = "known-tcp-device-ip-pool",
            description =
                    "known remote device available via ip associated with the "
                            + "tcp-device placeholder.")
    private Set<String> mKnownTcpDeviceIpPool = new HashSet<>();

    @Option(
            name = "known-gce-device-ip-pool",
            description =
                    "known remote device available via ip associated with the "
                            + "gce-device placeholder.")
    private Set<String> mKnownGceDeviceIpPool = new HashSet<>();

    @Option(
            name = "known-remote-device-ip-pool",
            description =
                    "known remote device available via ip associated with the "
                            + "remote-device placeholder.")
    private Set<String> mKnownRemoteDeviceIpPool = new HashSet<>();

    @Option(
            name = "use-zip64-in-partial-download",
            description = "Whether to use zip64 format in partial download.")
    private boolean mUseZip64InPartialDownload = true;

    @Option(
            name = "use-network-interface",
            description = "The network interface used to connect to test devices.")
    private String mNetworkInterface = null;

    @Option(
            name = "preconfigured-virtual-device-pool",
            description = "Preconfigured virtual device pool. (Value format: $hostname:$user.)")
    private List<String> mPreconfiguredVirtualDevicePool = new ArrayList<>();

    @Option(
            name = "flash-with-fuse-zip",
            description = "Use `fastboot flashall` on a folder of fuse mounted device image zip "
                    + "instead of `fastboot update` with zip")
    private boolean mFlashWithFuseZip = false;

    @Option(
            name = "cache-size-limit",
            description =
                    "The maximum allowed size(bytes) of the local file cache. (default: 15GB)")
    private Long mCacheSizeLimit = 15L * 1024L * 1024L * 1024L;

    @Option(
            name = "test-phase-timeout",
            description =
                    "the maximum time to wait for test phase to finish before attempting to force"
                            + "stop it. A value of zero will indicate no timeout.",
            isTimeVal = true)
    private long mTestPhaseTimeout = 0;

    @Option(
            name = "enable-flashstation",
            description = "Feature flag to enable the support for flashstation.")
    private boolean mEnableFlashstation = false;

    @Option(
            name = "cl-flashstation",
            description = "cl_flashstation script stored in remote GCS bucket.")
    private File mClFlashstation = new File("/tradefed/cl_flashstation");

    @Option(
            name = "enable-incremental-flashing",
            description = "Feature flag to enable incremental flashing on one host.")
    private boolean mEnableIncrementalFlashing = false;

    @Option(
            name = "opt-out-incremental-flashing",
            description = "Allows an host to fully opt-out of incremental flashing.")
    private boolean mOptOutFromIncrementalFlashing = false;

    @Option(
            name = "disable-host-metric-reporting",
            description = "Feature flag to disable the support for host metric reporting.")
    private boolean mDisableHostMetricReporting = false;

    private Map<PermitLimitType, Semaphore> mConcurrentLocks = new HashMap<>();
    private Map<PermitLimitType, Integer> mInternalConcurrentLimits = new HashMap<>();

    /** {@inheritDoc} */
    @Override
    public Long getCacheSizeLimit() {
        return mCacheSizeLimit;
    }

    /** {@inheritDoc} */
    @Override
    public Integer getConcurrentFlasherLimit() {
        return mConcurrentFlasherLimit;
    }

    /** {@inheritDoc} */
    @Override
    public Integer getConcurrentDownloadLimit() {
        return mConcurrentDownloadLimit;
    }

    @Override
    public Integer getConcurrentVirtualDeviceStartupLimit() {
        return mConcurrentVirtualDeviceStartupLimit;
    }

    /** {@inheritDoc} */
    @Override
    public File getFastbootTmpDir() {
        if (mFastbootTmpDir != null) {
            if (!mFastbootTmpDir.exists() || !mFastbootTmpDir.isDirectory()) {
                throw new HarnessRuntimeException(
                        String.format(
                                "Fastboot tmp dir '%s' is missing and was expected.",
                                mFastbootTmpDir),
                        InfraErrorIdentifier.LAB_HOST_FILESYSTEM_ERROR);
            }
            return mFastbootTmpDir;
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isFastbootdEnable() {
        return mEnableFastbootdMode;
    }

    /** {@inheritDoc} */
    @Override
    public File getDownloadCacheDir() {
        return mDownloadCacheDir;
    }

    /** {@inheritDoc} */
    @Override
    public Boolean shouldUseSsoClient() {
        return mUseSsoClient;
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, File> getServiceAccountJsonKeyFiles() {
        return new HashMap<>(mJsonServiceAccountMap);
    }

    /** {@inheritDoc} */
    @Override
    public void validateOptions() throws ConfigurationException {
        // Validation of host options
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getLabels() {
        return new ArrayList<>(mLabels);
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> getKnownTcpDeviceIpPool() {
        return new HashSet<>(mKnownTcpDeviceIpPool);
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> getKnownGceDeviceIpPool() {
        return new HashSet<>(mKnownGceDeviceIpPool);
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> getKnownRemoteDeviceIpPool() {
        return new HashSet<>(mKnownRemoteDeviceIpPool);
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getKnownPreconfigureVirtualDevicePool() {
        return new ArrayList<>(mPreconfiguredVirtualDevicePool);
    }

    /** {@inheritDoc} */
    @Override
    public boolean getUseZip64InPartialDownload() {
        return mUseZip64InPartialDownload;
    }

    /** {@inheritDoc} */
    @Override
    public String getNetworkInterface() {
        if (mNetworkInterface != null) {
            return mNetworkInterface;
        }

        try {
            for (Enumeration<NetworkInterface> enNetI = NetworkInterface.getNetworkInterfaces();
                    enNetI.hasMoreElements(); ) {
                NetworkInterface netI = enNetI.nextElement();
                if (!netI.isUp()) {
                    continue;
                }
                for (Enumeration<InetAddress> enumIpAddr = netI.getInetAddresses();
                        enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isAnyLocalAddress()
                            && !inetAddress.isLinkLocalAddress()
                            && !inetAddress.isLoopbackAddress()
                            && !inetAddress.isMulticastAddress()) {
                        mNetworkInterface = netI.getName();
                        return mNetworkInterface;
                    }
                }
            }
        } catch (SocketException e) {
            CLog.w("Failed to get host's active interface");
            CLog.w(e);
        }
        return null;
    }

    @Override
    public long getTestPhaseTimeout() {
        return mTestPhaseTimeout;
    }

    @Override
    public void initConcurrentLocks() {
        // Do not reinit if it has been called before
        if (!mConcurrentLocks.isEmpty()) {
            return;
        }
        mInternalConcurrentLimits.putAll(mConcurrentLimit);
        // Backfill flasher & download limit from their dedicated option
        if (!mInternalConcurrentLimits.containsKey(PermitLimitType.CONCURRENT_FLASHER)) {
            mInternalConcurrentLimits.put(
                    PermitLimitType.CONCURRENT_FLASHER, mConcurrentFlasherLimit);
        }
        if (!mInternalConcurrentLimits.containsKey(PermitLimitType.CONCURRENT_DOWNLOAD)) {
            mInternalConcurrentLimits.put(
                    PermitLimitType.CONCURRENT_DOWNLOAD, mConcurrentDownloadLimit);
        }

        if (!mInternalConcurrentLimits.containsKey(
                PermitLimitType.CONCURRENT_VIRTUAL_DEVICE_STARTUP)) {
            mInternalConcurrentLimits.put(
                    PermitLimitType.CONCURRENT_VIRTUAL_DEVICE_STARTUP,
                    mConcurrentVirtualDeviceStartupLimit);
        }

        for (Entry<PermitLimitType, Integer> limits : mInternalConcurrentLimits.entrySet()) {
            if (limits.getValue() == null) {
                continue;
            }
            mConcurrentLocks.put(limits.getKey(),
                    new Semaphore(limits.getValue(), true /* fair */));
        }
    }

    @Override
    public void takePermit(PermitLimitType type) {
        if (!mConcurrentLocks.containsKey(type)) {
            return;
        }
        CLog.i(
                "Requesting a '%s' permit out of the max limit of %s. Current queue "
                        + "length: %s",
                type,
                mInternalConcurrentLimits.get(type),
                mConcurrentLocks.get(type).getQueueLength());
        try {
            mConcurrentLocks.get(type).acquire();
        } catch (InterruptedException e) {
            throw new RunInterruptedException(e.getMessage(), e, InfraErrorIdentifier.UNDETERMINED);
        }
    }

    @Override
    public void returnPermit(PermitLimitType type) {
        if (!mConcurrentLocks.containsKey(type)) {
            return;
        }
        mConcurrentLocks.get(type).release();
    }

    @Override
    public Integer getAvailablePermits(PermitLimitType type) {
        if (!mConcurrentLocks.containsKey(type)) {
            return Integer.MAX_VALUE;
        }
        return mConcurrentLocks.get(type).availablePermits();
    }

    @Override
    public int getInUsePermits(PermitLimitType type) {
        if (!mConcurrentLocks.containsKey(type)) {
            return 0;
        }
        return mInternalConcurrentLimits.get(type) - mConcurrentLocks.get(type).availablePermits();
    }

    @Override
    public boolean shouldFlashWithFuseZip() {
        return mFlashWithFuseZip;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isFlashstationEnabled() {
        return mEnableFlashstation;
    }

    /** {@inheritDoc} */
    @Override
    public File getClFlashstation() {
        return mClFlashstation;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isIncrementalFlashingEnabled() {
        return mEnableIncrementalFlashing;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isOptOutOfIncrementalFlashing() {
        return mOptOutFromIncrementalFlashing;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isHostMetricReportingDisabled() {
        return mDisableHostMetricReporting;
    }
}
