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
package com.android.tradefed.device;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.tradefed.device.IDeviceManager.IFastbootListener;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunInterruptedException;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.TimeUtil;

import com.google.common.base.Strings;
import com.google.common.primitives.Longs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for monitoring the state of a {@link IDevice} with no framework support.
 */
public class NativeDeviceStateMonitor implements IDeviceStateMonitor {

    static final String BOOTCOMPLETE_PROP = "dev.bootcomplete";

    private IDevice mDevice;
    private TestDeviceState mDeviceState;

    /** the time in ms to wait between 'poll for responsiveness' attempts */
    private static final long CHECK_POLL_TIME = 1 * 1000;

    protected static final long MAX_CHECK_POLL_TIME = 10 * 1000;
    /** the maximum operation time in ms for a 'poll for responsiveness' command */
    protected static final int MAX_OP_TIME = 10 * 1000;
    /** Reference for TMPFS from 'man statfs' */
    private static final Set<String> TMPFS_MAGIC =
            new HashSet<>(Arrays.asList("1021994", "01021994"));

    /** The  time in ms to wait for a device to be online. */
    private long mDefaultOnlineTimeout = 1 * 60 * 1000;

    /** The  time in ms to wait for a device to available. */
    private long mDefaultAvailableTimeout = 6 * 60 * 1000;

    /** The fastboot mode serial number */
    private String mFastbootSerialNumber = null;

    private List<DeviceStateListener> mStateListeners;
    private IDeviceManager mMgr;
    private final boolean mFastbootEnabled;
    private boolean mMountFileSystemCheckEnabled = false;

    protected static final String PERM_DENIED_ERROR_PATTERN = "Permission denied";

    public NativeDeviceStateMonitor(IDeviceManager mgr, IDevice device,
            boolean fastbootEnabled) {
        mMgr = mgr;
        mDevice = device;
        mStateListeners = new ArrayList<DeviceStateListener>();
        mDeviceState = TestDeviceState.getStateByDdms(device.getState());
        mFastbootEnabled = fastbootEnabled;
        mMountFileSystemCheckEnabled = mMgr.isFileSystemMountCheckEnabled();
    }

    /**
     * Get the {@link RunUtil} instance to use.
     * <p/>
     * Exposed for unit testing.
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * Set the time in ms to wait for a device to be online in {@link #waitForDeviceOnline()}.
     */
    @Override
    public void setDefaultOnlineTimeout(long timeoutMs) {
        mDefaultOnlineTimeout = timeoutMs;
    }

    /**
     * Set the time in ms to wait for a device to be available in {@link #waitForDeviceAvailable()}.
     */
    @Override
    public void setDefaultAvailableTimeout(long timeoutMs) {
        mDefaultAvailableTimeout = timeoutMs;
    }

    /** Set the fastboot mode serial number. */
    @Override
    public void setFastbootSerialNumber(String serial) {
        mFastbootSerialNumber = serial;

        if (mFastbootSerialNumber != null && !mFastbootSerialNumber.equals(getSerialNumber())) {
            // Add to IDeviceManager to monitor it
            mMgr.addMonitoringTcpFastbootDevice(getSerialNumber(), mFastbootSerialNumber);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IDevice waitForDeviceOnline(long waitTime) {
        if (waitForDeviceState(TestDeviceState.ONLINE, waitTime)) {
            return getIDevice();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IDevice waitForDeviceOnline() {
        return waitForDeviceOnline(mDefaultOnlineTimeout);
    }

    @Override
    public IDevice waitForDeviceInRecovery() {
        if (waitForDeviceState(TestDeviceState.RECOVERY, mDefaultOnlineTimeout)) {
            return getIDevice();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean waitForDeviceInRecovery(long waitTime) {
        return waitForDeviceState(TestDeviceState.RECOVERY, waitTime);
    }

    /**
     * @return {@link IDevice} associate with the state monitor
     */
    protected IDevice getIDevice() {
        synchronized (mDevice) {
            return mDevice;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSerialNumber() {
        return getIDevice().getSerialNumber();
    }

    /** {@inheritDoc} */
    @Override
    public String getFastbootSerialNumber() {
        if (mFastbootSerialNumber == null) {
            mFastbootSerialNumber = getSerialNumber();
        }
        return mFastbootSerialNumber;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean waitForDeviceNotAvailable(long waitTime) {
        IFastbootListener listener = new StubFastbootListener();
        if (mFastbootEnabled) {
            mMgr.addFastbootListener(listener);
        }
        boolean result = waitForDeviceState(TestDeviceState.NOT_AVAILABLE, waitTime);
        if (mFastbootEnabled) {
            mMgr.removeFastbootListener(listener);
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean waitForDeviceInSideload(long waitTime) {
        return waitForDeviceState(TestDeviceState.SIDELOAD, waitTime);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean waitForDeviceShell(final long waitTime) {
        CLog.i("Waiting %d ms for device %s shell to be responsive", waitTime,
                getSerialNumber());
        Callable<BUSY_WAIT_STATUS> bootComplete =
                () -> {
                    final CollectingOutputReceiver receiver = createOutputReceiver();
                    final String cmd = "id";
                    try {
                        getIDevice()
                                .executeShellCommand(
                                        cmd, receiver, MAX_OP_TIME, TimeUnit.MILLISECONDS);
                        String output = receiver.getOutput();
                        if (output.contains("uid=")) {
                            CLog.i("shell ready. id output: %s", output);
                            return BUSY_WAIT_STATUS.SUCCESS;
                        }
                    } catch (IOException
                            | AdbCommandRejectedException
                            | ShellCommandUnresponsiveException e) {
                        CLog.e("%s failed on: %s", cmd, getSerialNumber());
                        CLog.e(e);
                    } catch (TimeoutException e) {
                        CLog.e("%s failed on %s: timeout", cmd, getSerialNumber());
                        CLog.e(e);
                    }
                    return BUSY_WAIT_STATUS.CONTINUE_WAITING;
                };
        boolean result = busyWaitFunction(bootComplete, waitTime);
        if (!result) {
            CLog.w("Device %s shell is unresponsive", getSerialNumber());
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IDevice waitForDeviceAvailable(final long waitTime) {
        try {
            return internalWaitForDeviceAvailable(waitTime);
        } catch (DeviceNotAvailableException e) {
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public IDevice waitForDeviceAvailable() {
        return waitForDeviceAvailable(mDefaultAvailableTimeout);
    }

    /** {@inheritDoc} */
    @Override
    public IDevice waitForDeviceAvailableInRecoverPath(final long waitTime)
            throws DeviceNotAvailableException {
        return internalWaitForDeviceAvailable(waitTime);
    }

    private IDevice internalWaitForDeviceAvailable(final long waitTime)
            throws DeviceNotAvailableException {
        // A device is currently considered "available" if and only if four events are true:
        // 1. Device is online aka visible via DDMS/adb
        // 2. Device has dev.bootcomplete flag set
        // 3. Device's package manager is responsive (may be inop)
        // 4. Device's external storage is mounted
        //
        // The current implementation waits for each event to occur in sequence.
        //
        // it will track the currently elapsed time and fail if it is
        // greater than waitTime

        long startTime = System.currentTimeMillis();
        IDevice device = waitForDeviceOnline(waitTime);
        if (device == null) {
            return null;
        }
        long elapsedTime = System.currentTimeMillis() - startTime;
        if (!waitForBootComplete(waitTime - elapsedTime)) {
            return null;
        }
        elapsedTime = System.currentTimeMillis() - startTime;
        if (!postOnlineCheck(waitTime - elapsedTime)) {
            return null;
        }
        return device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean waitForBootComplete(final long waitTime) {
        CLog.i("Waiting %d ms for device %s boot complete", waitTime, getSerialNumber());
        Callable<BUSY_WAIT_STATUS> bootComplete =
                () -> {
                    final String cmd = "getprop " + BOOTCOMPLETE_PROP;
                    try {
                        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
                        getIDevice()
                                .executeShellCommand(
                                        "getprop " + BOOTCOMPLETE_PROP,
                                        receiver,
                                        60000L,
                                        TimeUnit.MILLISECONDS);
                        String bootFlag = receiver.getOutput();
                        if (bootFlag != null) {
                            // Workaround for microdroid: `adb shell` prints permission warnings
                            bootFlag = bootFlag.lines().reduce((a, b) -> b).orElse(null);
                        }
                        if (bootFlag != null && "1".equals(bootFlag.trim())) {
                            return BUSY_WAIT_STATUS.SUCCESS;
                        }
                    } catch (IOException | ShellCommandUnresponsiveException e) {
                        CLog.e("%s failed on: %s", cmd, getSerialNumber());
                        CLog.e(e);
                    } catch (TimeoutException e) {
                        CLog.e("%s failed on %s: timeout", cmd, getSerialNumber());
                        CLog.e(e);
                    } catch (AdbCommandRejectedException e) {
                        CLog.e("%s failed on: %s", cmd, getSerialNumber());
                        CLog.e(e);
                        if (e.isDeviceOffline() || e.wasErrorDuringDeviceSelection()) {
                            return BUSY_WAIT_STATUS.ABORT;
                        }
                    }
                    return BUSY_WAIT_STATUS.CONTINUE_WAITING;
                };
        boolean result = busyWaitFunction(bootComplete, waitTime);
        if (!result) {
            CLog.w("Device %s did not boot after %d ms", getSerialNumber(), waitTime);
        }
        return result;
    }

    /**
     * Additional checks to be done on an Online device
     *
     * @param waitTime time in ms to wait before giving up
     * @return <code>true</code> if checks are successful before waitTime expires. <code>false
     *     </code> otherwise
     * @throws DeviceNotAvailableException
     */
    protected boolean postOnlineCheck(final long waitTime) throws DeviceNotAvailableException {
        // Until we have clarity on storage requirements, move the check to
        // full device only.
        // return waitForStoreMount(waitTime);
        return true;
    }

    /**
     * Waits for the device's external store to be mounted.
     *
     * @param waitTime time in ms to wait before giving up
     * @return <code>true</code> if external store is mounted before waitTime expires. <code>false
     *     </code> otherwise
     */
    protected boolean waitForStoreMount(final long waitTime) throws DeviceNotAvailableException {
        CLog.i("Waiting %d ms for device %s external store", waitTime, getSerialNumber());
        long startTime = System.currentTimeMillis();
        int counter = 0;
        // TODO(b/151119210): Remove this 'retryOnPermissionDenied' workaround when we figure out
        // what causes "Permission denied" to be returned incorrectly.
        int retryOnPermissionDenied = 1;
        while (System.currentTimeMillis() - startTime < waitTime) {
            if (counter > 0) {
                getRunUtil().sleep(Math.min(getCheckPollTime() * counter, MAX_CHECK_POLL_TIME));
            }
            counter++;
            final CollectingOutputReceiver receiver = createOutputReceiver();
            final CollectingOutputReceiver bitBucket = new CollectingOutputReceiver();
            final long number = getCurrentTime();
            final String externalStore = getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
            if (externalStore == null) {
                CLog.w("Failed to get external store mount point for %s", getSerialNumber());
                continue;
            }

            if (mMountFileSystemCheckEnabled) {
                String fileSystem = getFileSystem(externalStore);
                if (Strings.isNullOrEmpty(fileSystem)) {
                    CLog.w("Failed to get the fileSystem of '%s'", externalStore);
                    continue;
                }
                if (TMPFS_MAGIC.contains(fileSystem)) {
                    CLog.w(
                            "External storage fileSystem is '%s', waiting for it to be mounted.",
                            fileSystem);
                    continue;
                }
            }
            final String testFile = String.format("'%s/%d'", externalStore, number);
            final String testString = String.format("number %d one", number);
            final String writeCmd = String.format("echo '%s' > %s", testString, testFile);
            final String checkCmd = String.format("cat %s", testFile);
            final String cleanupCmd = String.format("rm %s", testFile);
            String cmd = null;

            try {
                cmd = writeCmd;
                getIDevice()
                        .executeShellCommand(
                                writeCmd, bitBucket, MAX_OP_TIME, TimeUnit.MILLISECONDS);
                cmd = checkCmd;
                getIDevice()
                        .executeShellCommand(
                                checkCmd, receiver, MAX_OP_TIME, TimeUnit.MILLISECONDS);
                cmd = cleanupCmd;
                getIDevice()
                        .executeShellCommand(
                                cleanupCmd, bitBucket, MAX_OP_TIME, TimeUnit.MILLISECONDS);

                String output = receiver.getOutput();
                CLog.v("%s returned %s", checkCmd, output);
                if (output.contains(testString)) {
                    return true;
                } else if (output.contains(PERM_DENIED_ERROR_PATTERN)
                        && --retryOnPermissionDenied < 0) {
                    CLog.w(
                            "Device %s mount check returned Permission Denied, "
                                    + "issue with mounting.",
                            getSerialNumber());
                    return false;
                }
            } catch (IOException
                    | ShellCommandUnresponsiveException e) {
                CLog.i("%s on device %s failed:", cmd, getSerialNumber());
                CLog.e(e);
            } catch (TimeoutException e) {
                CLog.i("%s on device %s failed: timeout", cmd, getSerialNumber());
                CLog.e(e);
            } catch (AdbCommandRejectedException e) {
                String message =
                        String.format("%s on device %s was rejected:", cmd, getSerialNumber());
                CLog.i(message);
                CLog.e(e);
                rejectToUnavailable(message, e);
            }
        }
        CLog.w("Device %s external storage is not mounted after %d ms",
                getSerialNumber(), waitTime);
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public String getMountPoint(String mountName) throws DeviceNotAvailableException {
        String mountPoint = getIDevice().getMountPoint(mountName);
        if (mountPoint != null) {
            return mountPoint;
        }
        // cached mount point is null - try querying directly
        CollectingOutputReceiver receiver = createOutputReceiver();
        String command = "echo $" + mountName;
        try {
            getIDevice().executeShellCommand(command, receiver);
            return receiver.getOutput().trim();
        } catch (IOException e) {
            return null;
        } catch (TimeoutException e) {
            return null;
        } catch (AdbCommandRejectedException e) {
            rejectToUnavailable(command, e);
            return null;
        } catch (ShellCommandUnresponsiveException e) {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestDeviceState getDeviceState() {
        return mDeviceState;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean waitForDeviceBootloader(long time) {
        return waitForDeviceBootloaderOrFastbootd(time, false);
    }

    /** {@inheritDoc} */
    @Override
    public boolean waitForDeviceFastbootd(String fastbootPath, long time) {
        return waitForDeviceBootloaderOrFastbootd(time, true);
    }

    private boolean waitForDeviceBootloaderOrFastbootd(long time, boolean fastbootd) {
        if (!mFastbootEnabled) {
            return false;
        }
        long startTime = System.currentTimeMillis();
        // ensure fastboot state is updated at least once
        waitForDeviceBootloaderStateUpdate();
        long elapsedTime = System.currentTimeMillis() - startTime;
        IFastbootListener listener = new StubFastbootListener();
        mMgr.addFastbootListener(listener);
        long waitTime = time - elapsedTime;
        if (waitTime < 0) {
            // wait at least 200ms
            waitTime = 200;
        }
        TestDeviceState mode = TestDeviceState.FASTBOOT;
        if (fastbootd) {
            mode = TestDeviceState.FASTBOOTD;
        }
        boolean result = waitForDeviceState(mode, waitTime);
        mMgr.removeFastbootListener(listener);
        return result;
    }

    @Override
    public void waitForDeviceBootloaderStateUpdate() {
        if (!mFastbootEnabled) {
            return;
        }
        IFastbootListener listener = new NotifyFastbootListener();
        synchronized (listener) {
            mMgr.addFastbootListener(listener);
            try {
                listener.wait();
            } catch (InterruptedException e) {
                CLog.w("wait for device bootloader state update interrupted");
                CLog.w(e);
                throw new RunInterruptedException(
                        e.getMessage(), e, InfraErrorIdentifier.UNDETERMINED);
            } finally {
                mMgr.removeFastbootListener(listener);
            }
        }
    }

    private boolean waitForDeviceState(TestDeviceState state, long time) {
        String deviceSerial = getSerialNumber();
        TestDeviceState currentStatus = getDeviceState();
        if (currentStatus.equals(state)) {
            CLog.i("Device %s is already %s", deviceSerial, state);
            return true;
        }
        CLog.i(
                "Waiting for device %s to be in %s mode for '%s'; it is currently in %s mode...",
                deviceSerial, state, TimeUtil.formatElapsedTime(time), currentStatus);
        DeviceStateListener listener = new DeviceStateListener(state);
        addDeviceStateListener(listener);
        synchronized (listener) {
            try {
                listener.wait(time);
            } catch (InterruptedException e) {
                CLog.w("wait for device state interrupted");
                CLog.w(e);
                throw new RunInterruptedException(
                        e.getMessage(), e, InfraErrorIdentifier.UNDETERMINED);
            } finally {
                removeDeviceStateListener(listener);
            }
        }
        return getDeviceState().equals(state);
    }

    /**
     * @param listener
     */
    private void removeDeviceStateListener(DeviceStateListener listener) {
        synchronized (mStateListeners) {
            mStateListeners.remove(listener);
        }
    }

    /**
     * @param listener
     */
    private void addDeviceStateListener(DeviceStateListener listener) {
        synchronized (mStateListeners) {
            mStateListeners.add(listener);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setState(TestDeviceState deviceState) {
        mDeviceState = deviceState;
        // create a copy of listeners to prevent holding mStateListeners lock when notifying
        // and to protect from list modification when iterating
        Collection<DeviceStateListener> listenerCopy = new ArrayList<DeviceStateListener>(
                mStateListeners.size());
        synchronized (mStateListeners) {
            listenerCopy.addAll(mStateListeners);
        }
        for (DeviceStateListener listener: listenerCopy) {
            listener.stateChanged(deviceState);
        }
    }

    @Override
    public void setIDevice(IDevice newDevice) {
        IDevice currentDevice = mDevice;
        if (!getIDevice().equals(newDevice)) {
            synchronized (currentDevice) {
                mDevice = newDevice;
            }
        }
    }

    private static class DeviceStateListener {
        private final TestDeviceState mExpectedState;

        public DeviceStateListener(TestDeviceState expectedState) {
            mExpectedState = expectedState;
        }

        public void stateChanged(TestDeviceState newState) {
            if (mExpectedState.equals(newState)) {
                synchronized (this) {
                    notify();
                }
            }
        }
    }

    /**
     * An empty implementation of {@link IFastbootListener}
     */
    private static class StubFastbootListener implements IFastbootListener {
        @Override
        public void stateUpdated() {
            // ignore
        }
    }

    /**
     * A {@link IFastbootListener} that notifies when a status update has been received.
     */
    private static class NotifyFastbootListener implements IFastbootListener {
        @Override
        public void stateUpdated() {
            synchronized (this) {
                notify();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAdbTcp() {
        return mDevice.getSerialNumber().contains(":");
    }

    /**
     * Exposed for testing
     * @return {@link CollectingOutputReceiver}
     */
    protected CollectingOutputReceiver createOutputReceiver() {
        return new CollectingOutputReceiver();
    }

    /**
     * Exposed for testing
     */
    protected long getCheckPollTime() {
        return CHECK_POLL_TIME;
    }

    /**
     * Exposed for testing
     */
    protected long getCurrentTime() {
        return System.currentTimeMillis();
    }

    private String getFileSystem(String externalStorePath) throws DeviceNotAvailableException {
        final CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        String statCommand = "stat -f -c \"%t\" " + externalStorePath;
        try {
            getIDevice().executeShellCommand(statCommand, receiver, 10000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException
                | ShellCommandUnresponsiveException
                | IOException e) {
            CLog.e("Exception while attempting to read filesystem of '%s'", externalStorePath);
            CLog.e(e);
            return null;
        } catch (AdbCommandRejectedException e) {
            rejectToUnavailable(
                    String.format(
                            "Exception while attempting to read filesystem of '%s'",
                            externalStorePath),
                    e);
            return null;
        }
        String output = receiver.getOutput().trim();
        CLog.v("'%s' returned %s", statCommand, output);
        if (Longs.tryParse(output) == null && Longs.tryParse(output, 16) == null) {
            CLog.w("stat command return value should be a number. output: %s", output);
            return null;
        }
        return output;
    }

    private boolean busyWaitFunction(Callable<BUSY_WAIT_STATUS> callable, long maxWaitTime) {
        int counter = 0;
        long startTime = System.currentTimeMillis();
        long currentTotalWaitTime = 0L;
        while ((System.currentTimeMillis() - startTime) < maxWaitTime) {
            if (counter > 0) {
                long nextWaitTime = Math.min(getCheckPollTime() * counter, MAX_CHECK_POLL_TIME);
                if (currentTotalWaitTime + nextWaitTime > maxWaitTime) {
                    nextWaitTime = maxWaitTime - currentTotalWaitTime;
                }
                getRunUtil().sleep(nextWaitTime);
                currentTotalWaitTime += nextWaitTime;
            }
            counter++;
            try {
                BUSY_WAIT_STATUS res = callable.call();
                if (BUSY_WAIT_STATUS.SUCCESS.equals(res)) {
                    return true;
                }
                if (BUSY_WAIT_STATUS.ABORT.equals(res)) {
                    return false;
                }
            } catch (Exception e) {
                CLog.e(e);
            }
        }
        return false;
    }

    private enum BUSY_WAIT_STATUS {
        CONTINUE_WAITING,
        ABORT,
        SUCCESS,
    }

    /** Translate a reject adb command exception into DeviceNotAvailableException */
    private void rejectToUnavailable(String command, AdbCommandRejectedException e)
            throws DeviceNotAvailableException {
        if (e.isDeviceOffline() || e.wasErrorDuringDeviceSelection()) {
            throw new DeviceNotAvailableException(
                    String.format("%s: %s", command, e.getMessage()),
                    e,
                    getSerialNumber(),
                    DeviceErrorIdentifier.DEVICE_UNAVAILABLE);
        }
    }
}
