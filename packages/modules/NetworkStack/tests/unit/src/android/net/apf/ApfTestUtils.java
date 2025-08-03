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
package android.net.apf;

import static android.net.apf.ApfJniUtils.apfSimulate;
import static android.system.OsConstants.AF_UNIX;
import static android.system.OsConstants.SOCK_STREAM;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.apf.BaseApfGenerator.IllegalInstructionException;
import android.net.ip.IIpClientCallbacks;
import android.net.ip.IpClient;
import android.net.metrics.IpConnectivityLog;
import android.os.ConditionVariable;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.text.format.DateUtils;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.HexDump;
import com.android.net.module.util.InterfaceParams;
import com.android.net.module.util.SharedLog;
import com.android.networkstack.apishim.NetworkInformationShimImpl;
import com.android.networkstack.metrics.NetworkQuirkMetrics;

import libcore.io.IoUtils;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;

/**
 * The util class for calling the APF interpreter and check the return value
 */
public class ApfTestUtils {
    public static final int TIMEOUT_MS = 500;
    public static final int PASS = 1;
    public static final int DROP = 0;
    // Interpreter will just accept packets without link layer headers, so pad fake packet to at
    // least the minimum packet size.
    public static final int MIN_PKT_SIZE = 15;

    private ApfTestUtils() {
    }

    private static String label(int code) {
        switch (code) {
            case PASS:
                return "PASS";
            case DROP:
                return "DROP";
            default:
                return "UNKNOWN";
        }
    }

    private static void assertReturnCodesEqual(String msg, int expected, int got) {
        assertEquals(msg, label(expected), label(got));
    }

    private static void assertReturnCodesEqual(int expected, int got) {
        assertEquals(label(expected), label(got));
    }

    private static void assertVerdict(int apfVersion, int expected, byte[] program, byte[] packet,
            int filterAge) {
        final String msg = "Unexpected APF verdict. To debug:\n" + "  apf_run --program "
                + HexDump.toHexString(program) + " --packet " + HexDump.toHexString(packet)
                + " --trace | less\n  ";
        assertReturnCodesEqual(msg, expected,
                apfSimulate(apfVersion, program, packet, null, filterAge));
    }

    /**
     * Runs the APF program and checks the return code is equals to expected value. If not, the
     * customized message is printed.
     */
    public static void assertVerdict(int apfVersion, String msg, int expected, byte[] program,
            byte[] packet, int filterAge) {
        assertReturnCodesEqual(msg, expected,
                apfSimulate(apfVersion, program, packet, null, filterAge));
    }

    /**
     * Runs the APF program and checks the return code is equals to expected value.
     */
    public static void assertVerdict(int apfVersion, int expected, byte[] program, byte[] packet) {
        assertVerdict(apfVersion, expected, program, packet, 0);
    }

    /**
     * Runs the APF program and checks the return code is PASS.
     */
    public static void assertPass(int apfVersion, byte[] program, byte[] packet, int filterAge) {
        assertVerdict(apfVersion, PASS, program, packet, filterAge);
    }

    /**
     * Runs the APF program and checks the return code is PASS.
     */
    public static void assertPass(int apfVersion, byte[] program, byte[] packet) {
        assertVerdict(apfVersion, PASS, program, packet);
    }

    /**
     * Runs the APF program and checks the return code is DROP.
     */
    public static void assertDrop(int apfVersion, byte[] program, byte[] packet, int filterAge) {
        assertVerdict(apfVersion, DROP, program, packet, filterAge);
    }

    /**
     * Runs the APF program and checks the return code is DROP.
     */
    public static void assertDrop(int apfVersion, byte[] program, byte[] packet) {
        assertVerdict(apfVersion, DROP, program, packet);
    }

    /**
     * Checks the generated APF program equals to the expected value.
     */
    public static void assertProgramEquals(byte[] expected, byte[] program) throws AssertionError {
        // assertArrayEquals() would only print one byte, making debugging difficult.
        if (!Arrays.equals(expected, program)) {
            throw new AssertionError("\nexpected: " + HexDump.toHexString(expected) + "\nactual:   "
                    + HexDump.toHexString(program));
        }
    }

    /**
     * Runs the APF program and checks the return code and data regions equals to expected value.
     */
    public static void assertDataMemoryContents(int apfVersion, int expected, byte[] program,
            byte[] packet, byte[] data, byte[] expectedData)
            throws ApfV4Generator.IllegalInstructionException, Exception {
        assertReturnCodesEqual(expected,
                apfSimulate(apfVersion, program, packet, data, 0 /* filterAge */));

        // assertArrayEquals() would only print one byte, making debugging difficult.
        if (!Arrays.equals(expectedData, data)) {
            throw new Exception("\nprogram:     " + HexDump.toHexString(program) + "\ndata memory: "
                    + HexDump.toHexString(data) + "\nexpected:    " + HexDump.toHexString(
                    expectedData));
        }
    }

    /**
     * Runs the APF program with customized data region and checks the return code.
     */
    public static void assertVerdict(int apfVersion, int expected, byte[] program, byte[] packet,
            byte[] data) {
        assertReturnCodesEqual(expected,
                apfSimulate(apfVersion, program, packet, data, 0 /* filterAge */));
    }

    private static void assertVerdict(int apfVersion, int expected, ApfV4Generator gen,
            byte[] packet, int filterAge) throws ApfV4Generator.IllegalInstructionException {
        assertReturnCodesEqual(expected,
                apfSimulate(apfVersion, gen.generate(), packet, null, filterAge));
    }

    /**
     * Runs the APF program and checks the return code is PASS.
     */
    public static void assertPass(int apfVersion, ApfV4Generator gen, byte[] packet, int filterAge)
            throws ApfV4Generator.IllegalInstructionException {
        assertVerdict(apfVersion, PASS, gen, packet, filterAge);
    }

    /**
     * Runs the APF program and checks the return code is DROP.
     */
    public static void assertDrop(int apfVersion, ApfV4Generator gen, byte[] packet, int filterAge)
            throws ApfV4Generator.IllegalInstructionException {
        assertVerdict(apfVersion, DROP, gen, packet, filterAge);
    }

    /**
     * Runs the APF program and checks the return code is PASS.
     */
    public static void assertPass(int apfVersion, ApfV4Generator gen)
            throws ApfV4Generator.IllegalInstructionException {
        assertVerdict(apfVersion, PASS, gen, new byte[MIN_PKT_SIZE], 0);
    }

    /**
     * Runs the APF program and checks the return code is DROP.
     */
    public static void assertDrop(int apfVersion, ApfV4Generator gen)
            throws ApfV4Generator.IllegalInstructionException {
        assertVerdict(apfVersion, DROP, gen, new byte[MIN_PKT_SIZE], 0);
    }

    /**
     * The Mock ip client callback class.
     */
    public static class MockIpClientCallback extends IpClient.IpClientCallbacksWrapper {
        private final ConditionVariable mGotApfProgram = new ConditionVariable();
        private byte[] mLastApfProgram;
        private boolean mInstallPacketFilterReturn = true;

        MockIpClientCallback() {
            super(mock(IIpClientCallbacks.class), mock(SharedLog.class),
                    NetworkInformationShimImpl.newInstance());
        }

        MockIpClientCallback(boolean installPacketFilterReturn) {
            super(mock(IIpClientCallbacks.class), mock(SharedLog.class),
                    NetworkInformationShimImpl.newInstance());
            mInstallPacketFilterReturn = installPacketFilterReturn;
        }

        @Override
        public boolean installPacketFilter(byte[] filter) {
            mLastApfProgram = filter;
            mGotApfProgram.open();
            return mInstallPacketFilterReturn;
        }

        /**
         * Reset the apf program and wait for the next update.
         */
        public void resetApfProgramWait() {
            mGotApfProgram.close();
        }

        /**
         * Assert the program is update within TIMEOUT_MS and return the program.
         */
        public byte[] assertProgramUpdateAndGet() {
            assertTrue(mGotApfProgram.block(TIMEOUT_MS));
            return mLastApfProgram;
        }

        /**
         * Assert the program is not update within TIMEOUT_MS.
         */
        public void assertNoProgramUpdate() {
            assertFalse(mGotApfProgram.block(TIMEOUT_MS));
        }
    }

    /**
     * The test apf filter class.
     */
    public static class TestApfFilter extends ApfFilter implements TestAndroidPacketFilter {
        public static final byte[] MOCK_MAC_ADDR = {1, 2, 3, 4, 5, 6};
        private static final byte[] MOCK_IPV4_ADDR = {10, 0, 0, 1};

        private FileDescriptor mWriteSocket;
        private long mCurrentTimeMs = SystemClock.elapsedRealtime();
        private final MockIpClientCallback mMockIpClientCb;
        private final boolean mThrowsExceptionWhenGeneratesProgram;

        public TestApfFilter(Context context, ApfConfiguration config,
                MockIpClientCallback ipClientCallback, NetworkQuirkMetrics networkQuirkMetrics)
                throws Exception {
            this(context, config, ipClientCallback, networkQuirkMetrics, new Dependencies(context),
                    false /* throwsExceptionWhenGeneratesProgram */, new ApfFilter.Clock());
        }

        public TestApfFilter(Context context, ApfConfiguration config,
                MockIpClientCallback ipClientCallback, NetworkQuirkMetrics networkQuirkMetrics,
                Dependencies dependencies) throws Exception {
            this(context, config, ipClientCallback, networkQuirkMetrics, dependencies,
                    false /* throwsExceptionWhenGeneratesProgram */, new ApfFilter.Clock());
        }

        public TestApfFilter(Context context, ApfConfiguration config,
                MockIpClientCallback ipClientCallback, NetworkQuirkMetrics networkQuirkMetrics,
                Dependencies dependencies, boolean throwsExceptionWhenGeneratesProgram)
                throws Exception {
            this(context, config, ipClientCallback, networkQuirkMetrics, dependencies,
                    throwsExceptionWhenGeneratesProgram, new ApfFilter.Clock());
        }

        public TestApfFilter(Context context, ApfConfiguration config,
                MockIpClientCallback ipClientCallback, NetworkQuirkMetrics networkQuirkMetrics,
                Dependencies dependencies, ApfFilter.Clock clock) throws Exception {
            this(context, config, ipClientCallback, networkQuirkMetrics, dependencies,
                    false /* throwsExceptionWhenGeneratesProgram */, clock);
        }

        public TestApfFilter(Context context, ApfConfiguration config,
                MockIpClientCallback ipClientCallback, NetworkQuirkMetrics networkQuirkMetrics,
                Dependencies dependencies, boolean throwsExceptionWhenGeneratesProgram,
                ApfFilter.Clock clock) throws Exception {
            super(context, config, InterfaceParams.getByName("lo"), ipClientCallback,
                    networkQuirkMetrics, dependencies, clock);
            mMockIpClientCb = ipClientCallback;
            mThrowsExceptionWhenGeneratesProgram = throwsExceptionWhenGeneratesProgram;
        }

        /**
         * Create a new test ApfFiler.
         */
        public static ApfFilter createTestApfFilter(Context context,
                MockIpClientCallback ipClientCallback, ApfConfiguration config,
                NetworkQuirkMetrics networkQuirkMetrics, ApfFilter.Dependencies dependencies)
                throws Exception {
            LinkAddress link = new LinkAddress(InetAddress.getByAddress(MOCK_IPV4_ADDR), 19);
            LinkProperties lp = new LinkProperties();
            lp.addLinkAddress(link);
            TestApfFilter apfFilter = new TestApfFilter(context, config, ipClientCallback,
                    networkQuirkMetrics, dependencies);
            apfFilter.setLinkProperties(lp);
            return apfFilter;
        }

        /**
         * Pretend an RA packet has been received and show it to ApfFilter.
         */
        public void pretendPacketReceived(byte[] packet) throws IOException, ErrnoException {
            mMockIpClientCb.resetApfProgramWait();
            // ApfFilter's ReceiveThread will be waiting to read this.
            Os.write(mWriteSocket, packet, 0, packet.length);
        }

        /**
         * Simulate current time changes.
         */
        public void increaseCurrentTimeSeconds(int delta) {
            mCurrentTimeMs += delta * DateUtils.SECOND_IN_MILLIS;
        }

        @Override
        protected int secondsSinceBoot() {
            return (int) (mCurrentTimeMs / DateUtils.SECOND_IN_MILLIS);
        }

        @Override
        public synchronized void maybeStartFilter() {
            mHardwareAddress = MOCK_MAC_ADDR;
            installNewProgramLocked();

            // Create two sockets, "readSocket" and "mWriteSocket" and connect them together.
            FileDescriptor readSocket = new FileDescriptor();
            mWriteSocket = new FileDescriptor();
            try {
                Os.socketpair(AF_UNIX, SOCK_STREAM, 0, mWriteSocket, readSocket);
            } catch (ErrnoException e) {
                fail();
                return;
            }
            // Now pass readSocket to ReceiveThread as if it was setup to read raw RAs.
            // This allows us to pretend RA packets have been received via pretendPacketReceived().
            mReceiveThread = new ReceiveThread(readSocket);
            mReceiveThread.start();
        }

        @Override
        public synchronized void shutdown() {
            super.shutdown();
            if (mReceiveThread != null) {
                mReceiveThread.halt();
                mReceiveThread = null;
            }
            IoUtils.closeQuietly(mWriteSocket);
        }

        @Override
        @GuardedBy("this")
        protected ApfV4Generator emitPrologueLocked() throws IllegalInstructionException {
            if (mThrowsExceptionWhenGeneratesProgram) {
                throw new IllegalStateException();
            }
            return super.emitPrologueLocked();
        }
    }

    /**
     * The test legacy apf filter class.
     */
    public static class TestLegacyApfFilter extends LegacyApfFilter
            implements TestAndroidPacketFilter {
        public static final byte[] MOCK_MAC_ADDR = {1, 2, 3, 4, 5, 6};
        private static final byte[] MOCK_IPV4_ADDR = {10, 0, 0, 1};

        private FileDescriptor mWriteSocket;
        private final MockIpClientCallback mMockIpClientCb;
        private final boolean mThrowsExceptionWhenGeneratesProgram;

        public TestLegacyApfFilter(Context context, ApfFilter.ApfConfiguration config,
                MockIpClientCallback ipClientCallback, IpConnectivityLog ipConnectivityLog,
                NetworkQuirkMetrics networkQuirkMetrics) throws Exception {
            this(context, config, ipClientCallback, ipConnectivityLog, networkQuirkMetrics,
                    new ApfFilter.Dependencies(context),
                    false /* throwsExceptionWhenGeneratesProgram */, new ApfFilter.Clock());
        }

        public TestLegacyApfFilter(Context context, ApfFilter.ApfConfiguration config,
                MockIpClientCallback ipClientCallback, IpConnectivityLog ipConnectivityLog,
                NetworkQuirkMetrics networkQuirkMetrics, ApfFilter.Dependencies dependencies,
                boolean throwsExceptionWhenGeneratesProgram) throws Exception {
            this(context, config, ipClientCallback, ipConnectivityLog, networkQuirkMetrics,
                    dependencies, throwsExceptionWhenGeneratesProgram, new ApfFilter.Clock());
        }

        public TestLegacyApfFilter(Context context, ApfFilter.ApfConfiguration config,
                MockIpClientCallback ipClientCallback, IpConnectivityLog ipConnectivityLog,
                NetworkQuirkMetrics networkQuirkMetrics, ApfFilter.Dependencies dependencies,
                ApfFilter.Clock clock) throws Exception {
            this(context, config, ipClientCallback, ipConnectivityLog, networkQuirkMetrics,
                    dependencies, false /* throwsExceptionWhenGeneratesProgram */, clock);
        }

        public TestLegacyApfFilter(Context context, ApfFilter.ApfConfiguration config,
                MockIpClientCallback ipClientCallback, IpConnectivityLog ipConnectivityLog,
                NetworkQuirkMetrics networkQuirkMetrics, ApfFilter.Dependencies dependencies,
                boolean throwsExceptionWhenGeneratesProgram, ApfFilter.Clock clock)
                throws Exception {
            super(context, config, InterfaceParams.getByName("lo"), ipClientCallback,
                    ipConnectivityLog, networkQuirkMetrics, dependencies, clock);
            mMockIpClientCb = ipClientCallback;
            mThrowsExceptionWhenGeneratesProgram = throwsExceptionWhenGeneratesProgram;
        }

        /**
         * Pretend an RA packet has been received and show it to LegacyApfFilter.
         */
        public void pretendPacketReceived(byte[] packet) throws IOException, ErrnoException {
            mMockIpClientCb.resetApfProgramWait();
            // ApfFilter's ReceiveThread will be waiting to read this.
            Os.write(mWriteSocket, packet, 0, packet.length);
        }

        @Override
        public synchronized void maybeStartFilter() {
            mHardwareAddress = MOCK_MAC_ADDR;
            installNewProgramLocked();

            // Create two sockets, "readSocket" and "mWriteSocket" and connect them together.
            FileDescriptor readSocket = new FileDescriptor();
            mWriteSocket = new FileDescriptor();
            try {
                Os.socketpair(AF_UNIX, SOCK_STREAM, 0, mWriteSocket, readSocket);
            } catch (ErrnoException e) {
                fail();
                return;
            }
            // Now pass readSocket to ReceiveThread as if it was setup to read raw RAs.
            // This allows us to pretend RA packets have been received via pretendPacketReceived().
            mReceiveThread = new ReceiveThread(readSocket);
            mReceiveThread.start();
        }

        @Override
        public synchronized void shutdown() {
            super.shutdown();
            if (mReceiveThread != null) {
                mReceiveThread.halt();
                mReceiveThread = null;
            }
            IoUtils.closeQuietly(mWriteSocket);
        }

        @Override
        @GuardedBy("this")
        protected ApfV4Generator emitPrologueLocked() throws IllegalInstructionException {
            if (mThrowsExceptionWhenGeneratesProgram) {
                throw new IllegalStateException();
            }
            return super.emitPrologueLocked();
        }
    }
}
