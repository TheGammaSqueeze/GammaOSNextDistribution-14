/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static android.net.apf.BaseApfGenerator.APF_VERSION_4;
import static android.net.apf.BaseApfGenerator.DROP_LABEL;
import static android.net.apf.BaseApfGenerator.PASS_LABEL;
import static android.net.apf.BaseApfGenerator.Register.R0;
import static android.net.apf.BaseApfGenerator.Register.R1;
import static android.net.apf.ApfJniUtils.compareBpfApf;
import static android.net.apf.ApfJniUtils.compileToBpf;
import static android.net.apf.ApfJniUtils.dropsAllPackets;
import static android.net.apf.ApfTestUtils.DROP;
import static android.net.apf.ApfTestUtils.MIN_PKT_SIZE;
import static android.net.apf.ApfTestUtils.PASS;
import static android.net.apf.ApfTestUtils.assertProgramEquals;
import static android.os.PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED;
import static android.os.PowerManager.ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED;
import static android.system.OsConstants.ARPHRD_ETHER;
import static android.system.OsConstants.ETH_P_ARP;
import static android.system.OsConstants.ETH_P_IP;
import static android.system.OsConstants.ETH_P_IPV6;
import static android.system.OsConstants.IPPROTO_ICMPV6;
import static android.system.OsConstants.IPPROTO_IPV6;
import static android.system.OsConstants.IPPROTO_TCP;
import static android.system.OsConstants.IPPROTO_UDP;

import static com.android.net.module.util.HexDump.hexStringToByteArray;
import static com.android.net.module.util.HexDump.toHexString;
import static com.android.net.module.util.NetworkStackConstants.ICMPV6_ECHO_REQUEST_TYPE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.InetAddresses;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.MacAddress;
import android.net.NattKeepalivePacketDataParcelable;
import android.net.TcpKeepalivePacketDataParcelable;
import android.net.apf.ApfCounterTracker.Counter;
import android.net.apf.ApfFilter.ApfConfiguration;
import android.net.apf.ApfTestUtils.MockIpClientCallback;
import android.net.apf.ApfTestUtils.TestApfFilter;
import android.net.apf.ApfTestUtils.TestLegacyApfFilter;
import android.net.apf.BaseApfGenerator.IllegalInstructionException;
import android.net.metrics.IpConnectivityLog;
import android.os.Build;
import android.os.PowerManager;
import android.stats.connectivity.NetworkQuirkEvent;
import android.system.ErrnoException;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import com.android.internal.util.HexDump;
import com.android.net.module.util.DnsPacket;
import com.android.net.module.util.Inet4AddressUtils;
import com.android.net.module.util.NetworkStackConstants;
import com.android.net.module.util.PacketBuilder;
import com.android.networkstack.metrics.ApfSessionInfoMetrics;
import com.android.networkstack.metrics.IpClientRaInfoMetrics;
import com.android.networkstack.metrics.NetworkQuirkMetrics;
import com.android.server.networkstack.tests.R;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import libcore.io.Streams;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Tests for APF program generator and interpreter.
 *
 * The test cases will be executed by both APFv4 and APFv6 interpreter.
 */
@RunWith(DevSdkIgnoreRunner.class)
@SmallTest
public class ApfTest {
    private static final int MIN_APF_VERSION = 2;

    @Rule
    public DevSdkIgnoreRule mDevSdkIgnoreRule = new DevSdkIgnoreRule();
    // Indicates which apf interpreter to run.
    @Parameterized.Parameter()
    public int mApfVersion;

    @Parameterized.Parameters
    public static Iterable<? extends Object> data() {
        return Arrays.asList(4, 6);
    }

    @Mock private Context mContext;
    @Mock
    private ApfFilter.Dependencies mDependencies;
    @Mock private PowerManager mPowerManager;
    @Mock private IpConnectivityLog mIpConnectivityLog;
    @Mock private NetworkQuirkMetrics mNetworkQuirkMetrics;
    @Mock private ApfSessionInfoMetrics mApfSessionInfoMetrics;
    @Mock private IpClientRaInfoMetrics mIpClientRaInfoMetrics;
    @Mock private ApfFilter.Clock mClock;
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(mPowerManager).when(mContext).getSystemService(PowerManager.class);
        doReturn(mApfSessionInfoMetrics).when(mDependencies).getApfSessionInfoMetrics();
        doReturn(mIpClientRaInfoMetrics).when(mDependencies).getIpClientRaInfoMetrics();
    }

    private static final String TAG = "ApfTest";
    // Expected return codes from APF interpreter.
    private static final ApfCapabilities MOCK_APF_CAPABILITIES =
            new ApfCapabilities(2, 4096, ARPHRD_ETHER);

    private static final boolean DROP_MULTICAST = true;
    private static final boolean ALLOW_MULTICAST = false;

    private static final boolean DROP_802_3_FRAMES = true;
    private static final boolean ALLOW_802_3_FRAMES = false;

    private static final int MIN_RDNSS_LIFETIME_SEC = 0;
    private static final int MIN_METRICS_SESSION_DURATIONS_MS = 300_000;

    // Constants for opcode encoding
    private static final byte LI_OP   = (byte)(13 << 3);
    private static final byte LDDW_OP = (byte)(22 << 3);
    private static final byte STDW_OP = (byte)(23 << 3);
    private static final byte SIZE0   = (byte)(0 << 1);
    private static final byte SIZE8   = (byte)(1 << 1);
    private static final byte SIZE16  = (byte)(2 << 1);
    private static final byte SIZE32  = (byte)(3 << 1);
    private static final byte R1_REG = 1;

    private static ApfConfiguration getDefaultConfig() {
        ApfFilter.ApfConfiguration config = new ApfConfiguration();
        config.apfCapabilities = MOCK_APF_CAPABILITIES;
        config.multicastFilter = ALLOW_MULTICAST;
        config.ieee802_3Filter = ALLOW_802_3_FRAMES;
        config.ethTypeBlackList = new int[0];
        config.minRdnssLifetimeSec = MIN_RDNSS_LIFETIME_SEC;
        config.minRdnssLifetimeSec = 67;
        config.minMetricsSessionDurationMs = MIN_METRICS_SESSION_DURATIONS_MS;
        return config;
    }

    private void assertPass(ApfV4Generator gen) throws ApfV4Generator.IllegalInstructionException {
        ApfTestUtils.assertPass(mApfVersion, gen);
    }

    private void assertDrop(ApfV4Generator gen) throws ApfV4Generator.IllegalInstructionException {
        ApfTestUtils.assertDrop(mApfVersion, gen);
    }

    private void assertPass(byte[] program, byte[] packet) {
        ApfTestUtils.assertPass(mApfVersion, program, packet);
    }

    private void assertDrop(byte[] program, byte[] packet) {
        ApfTestUtils.assertDrop(mApfVersion, program, packet);
    }

    private void assertPass(byte[] program, byte[] packet, int filterAge) {
        ApfTestUtils.assertPass(mApfVersion, program, packet, filterAge);
    }

    private void assertDrop(byte[] program, byte[] packet, int filterAge) {
        ApfTestUtils.assertDrop(mApfVersion, program, packet, filterAge);
    }

    private void assertPass(ApfV4Generator gen, byte[] packet, int filterAge)
            throws ApfV4Generator.IllegalInstructionException {
        ApfTestUtils.assertPass(mApfVersion, gen, packet, filterAge);
    }

    private void assertDrop(ApfV4Generator gen, byte[] packet, int filterAge)
            throws ApfV4Generator.IllegalInstructionException {
        ApfTestUtils.assertDrop(mApfVersion, gen, packet, filterAge);
    }

    private void assertDataMemoryContents(int expected, byte[] program, byte[] packet,
            byte[] data, byte[] expectedData) throws Exception {
        ApfTestUtils.assertDataMemoryContents(mApfVersion, expected, program, packet, data,
                expectedData);
    }

    private void assertVerdict(String msg, int expected, byte[] program,
            byte[] packet, int filterAge) {
        ApfTestUtils.assertVerdict(mApfVersion, msg, expected, program, packet, filterAge);
    }

    private void assertVerdict(int expected, byte[] program, byte[] packet) {
        ApfTestUtils.assertVerdict(mApfVersion, expected, program, packet);
    }

    /**
     * Test each instruction by generating a program containing the instruction,
     * generating bytecode for that program and running it through the
     * interpreter to verify it functions correctly.
     */
    @Test
    public void testApfInstructions() throws IllegalInstructionException {
        // Empty program should pass because having the program counter reach the
        // location immediately after the program indicates the packet should be
        // passed to the AP.
        ApfV4Generator gen = new ApfV4Generator(MIN_APF_VERSION);
        assertPass(gen);

        // Test pass opcode
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addPass();
        gen.addJump(DROP_LABEL);
        assertPass(gen);

        // Test jumping to pass label.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addJump(PASS_LABEL);
        byte[] program = gen.generate();
        assertEquals(1, program.length);
        assertEquals((14 << 3) | (0 << 1) | 0, program[0]);
        assertPass(program, new byte[MIN_PKT_SIZE], 0);

        // Test jumping to drop label.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addJump(DROP_LABEL);
        program = gen.generate();
        assertEquals(2, program.length);
        assertEquals((14 << 3) | (1 << 1) | 0, program[0]);
        assertEquals(1, program[1]);
        assertDrop(program, new byte[15], 15);

        // Test jumping if equal to 0.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addJumpIfR0Equals(0, DROP_LABEL);
        assertDrop(gen);

        // Test jumping if not equal to 0.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addJumpIfR0NotEquals(0, DROP_LABEL);
        assertPass(gen);
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R0, 1);
        gen.addJumpIfR0NotEquals(0, DROP_LABEL);
        assertDrop(gen);

        // Test jumping if registers equal.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addJumpIfR0EqualsR1(DROP_LABEL);
        assertDrop(gen);

        // Test jumping if registers not equal.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addJumpIfR0NotEqualsR1(DROP_LABEL);
        assertPass(gen);
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R0, 1);
        gen.addJumpIfR0NotEqualsR1(DROP_LABEL);
        assertDrop(gen);

        // Test load immediate.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R0, 1234567890);
        gen.addJumpIfR0Equals(1234567890, DROP_LABEL);
        assertDrop(gen);

        // Test add.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addAdd(1234567890);
        gen.addJumpIfR0Equals(1234567890, DROP_LABEL);
        assertDrop(gen);

        // Test add with a small signed negative value.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addAdd(-1);
        gen.addJumpIfR0Equals(-1, DROP_LABEL);
        assertDrop(gen);

        // Test subtract.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addAdd(-1234567890);
        gen.addJumpIfR0Equals(-1234567890, DROP_LABEL);
        assertDrop(gen);

        // Test or.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addOr(1234567890);
        gen.addJumpIfR0Equals(1234567890, DROP_LABEL);
        assertDrop(gen);

        // Test and.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R0, 1234567890);
        gen.addAnd(123456789);
        gen.addJumpIfR0Equals(1234567890 & 123456789, DROP_LABEL);
        assertDrop(gen);

        // Test left shift.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R0, 1234567890);
        gen.addLeftShift(1);
        gen.addJumpIfR0Equals(1234567890 << 1, DROP_LABEL);
        assertDrop(gen);

        // Test right shift.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R0, 1234567890);
        gen.addRightShift(1);
        gen.addJumpIfR0Equals(1234567890 >> 1, DROP_LABEL);
        assertDrop(gen);

        // Test multiply.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R0, 123456789);
        gen.addMul(2);
        gen.addJumpIfR0Equals(123456789 * 2, DROP_LABEL);
        assertDrop(gen);

        // Test divide.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R0, 1234567890);
        gen.addDiv(2);
        gen.addJumpIfR0Equals(1234567890 / 2, DROP_LABEL);
        assertDrop(gen);

        // Test divide by zero.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addDiv(0);
        gen.addJump(DROP_LABEL);
        assertPass(gen);

        // Test add.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R1, 1234567890);
        gen.addAddR1();
        gen.addJumpIfR0Equals(1234567890, DROP_LABEL);
        assertDrop(gen);

        // Test subtract.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R1, -1234567890);
        gen.addAddR1();
        gen.addJumpIfR0Equals(-1234567890, DROP_LABEL);
        assertDrop(gen);

        // Test or.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R1, 1234567890);
        gen.addOrR1();
        gen.addJumpIfR0Equals(1234567890, DROP_LABEL);
        assertDrop(gen);

        // Test and.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R0, 1234567890);
        gen.addLoadImmediate(R1, 123456789);
        gen.addAndR1();
        gen.addJumpIfR0Equals(1234567890 & 123456789, DROP_LABEL);
        assertDrop(gen);

        // Test left shift.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R0, 1234567890);
        gen.addLoadImmediate(R1, 1);
        gen.addLeftShiftR1();
        gen.addJumpIfR0Equals(1234567890 << 1, DROP_LABEL);
        assertDrop(gen);

        // Test right shift.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R0, 1234567890);
        gen.addLoadImmediate(R1, -1);
        gen.addLeftShiftR1();
        gen.addJumpIfR0Equals(1234567890 >> 1, DROP_LABEL);
        assertDrop(gen);

        // Test multiply.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R0, 123456789);
        gen.addLoadImmediate(R1, 2);
        gen.addMulR1();
        gen.addJumpIfR0Equals(123456789 * 2, DROP_LABEL);
        assertDrop(gen);

        // Test divide.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R0, 1234567890);
        gen.addLoadImmediate(R1, 2);
        gen.addDivR1();
        gen.addJumpIfR0Equals(1234567890 / 2, DROP_LABEL);
        assertDrop(gen);

        // Test divide by zero.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addDivR1();
        gen.addJump(DROP_LABEL);
        assertPass(gen);

        // Test byte load.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoad8(R0, 1);
        gen.addJumpIfR0Equals(45, DROP_LABEL);
        assertDrop(gen, new byte[]{123,45,0,0,0,0,0,0,0,0,0,0,0,0,0}, 0);

        // Test out of bounds load.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoad8(R0, 16);
        gen.addJumpIfR0Equals(0, DROP_LABEL);
        assertPass(gen, new byte[]{123,45,0,0,0,0,0,0,0,0,0,0,0,0,0}, 0);

        // Test half-word load.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoad16(R0, 1);
        gen.addJumpIfR0Equals((45 << 8) | 67, DROP_LABEL);
        assertDrop(gen, new byte[]{123,45,67,0,0,0,0,0,0,0,0,0,0,0,0}, 0);

        // Test word load.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoad32(R0, 1);
        gen.addJumpIfR0Equals((45 << 24) | (67 << 16) | (89 << 8) | 12, DROP_LABEL);
        assertDrop(gen, new byte[]{123,45,67,89,12,0,0,0,0,0,0,0,0,0,0}, 0);

        // Test byte indexed load.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R1, 1);
        gen.addLoad8Indexed(R0, 0);
        gen.addJumpIfR0Equals(45, DROP_LABEL);
        assertDrop(gen, new byte[]{123,45,0,0,0,0,0,0,0,0,0,0,0,0,0}, 0);

        // Test out of bounds indexed load.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R1, 8);
        gen.addLoad8Indexed(R0, 8);
        gen.addJumpIfR0Equals(0, DROP_LABEL);
        assertPass(gen, new byte[]{123,45,0,0,0,0,0,0,0,0,0,0,0,0,0}, 0);

        // Test half-word indexed load.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R1, 1);
        gen.addLoad16Indexed(R0, 0);
        gen.addJumpIfR0Equals((45 << 8) | 67, DROP_LABEL);
        assertDrop(gen, new byte[]{123,45,67,0,0,0,0,0,0,0,0,0,0,0,0}, 0);

        // Test word indexed load.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R1, 1);
        gen.addLoad32Indexed(R0, 0);
        gen.addJumpIfR0Equals((45 << 24) | (67 << 16) | (89 << 8) | 12, DROP_LABEL);
        assertDrop(gen, new byte[]{123,45,67,89,12,0,0,0,0,0,0,0,0,0,0}, 0);

        // Test jumping if greater than.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addJumpIfR0GreaterThan(0, DROP_LABEL);
        assertPass(gen);
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R0, 1);
        gen.addJumpIfR0GreaterThan(0, DROP_LABEL);
        assertDrop(gen);

        // Test jumping if less than.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addJumpIfR0LessThan(0, DROP_LABEL);
        assertPass(gen);
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addJumpIfR0LessThan(1, DROP_LABEL);
        assertDrop(gen);

        // Test jumping if any bits set.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addJumpIfR0AnyBitsSet(3, DROP_LABEL);
        assertPass(gen);
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R0, 1);
        gen.addJumpIfR0AnyBitsSet(3, DROP_LABEL);
        assertDrop(gen);
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R0, 3);
        gen.addJumpIfR0AnyBitsSet(3, DROP_LABEL);
        assertDrop(gen);

        // Test jumping if register greater than.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addJumpIfR0GreaterThanR1(DROP_LABEL);
        assertPass(gen);
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R0, 2);
        gen.addLoadImmediate(R1, 1);
        gen.addJumpIfR0GreaterThanR1(DROP_LABEL);
        assertDrop(gen);

        // Test jumping if register less than.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addJumpIfR0LessThanR1(DROP_LABEL);
        assertPass(gen);
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R1, 1);
        gen.addJumpIfR0LessThanR1(DROP_LABEL);
        assertDrop(gen);

        // Test jumping if any bits set in register.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R1, 3);
        gen.addJumpIfR0AnyBitsSetR1(DROP_LABEL);
        assertPass(gen);
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R1, 3);
        gen.addLoadImmediate(R0, 1);
        gen.addJumpIfR0AnyBitsSetR1(DROP_LABEL);
        assertDrop(gen);
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R1, 3);
        gen.addLoadImmediate(R0, 3);
        gen.addJumpIfR0AnyBitsSetR1(DROP_LABEL);
        assertDrop(gen);

        // Test load from memory.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadFromMemory(R0, 0);
        gen.addJumpIfR0Equals(0, DROP_LABEL);
        assertDrop(gen);

        // Test store to memory.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R1, 1234567890);
        gen.addStoreToMemory(R1, 12);
        gen.addLoadFromMemory(R0, 12);
        gen.addJumpIfR0Equals(1234567890, DROP_LABEL);
        assertDrop(gen);

        // Test filter age pre-filled memory.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadFromMemory(R0, gen.FILTER_AGE_MEMORY_SLOT);
        gen.addJumpIfR0Equals(123, DROP_LABEL);
        assertDrop(gen, new byte[MIN_PKT_SIZE], 123);

        // Test packet size pre-filled memory.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadFromMemory(R0, gen.PACKET_SIZE_MEMORY_SLOT);
        gen.addJumpIfR0Equals(MIN_PKT_SIZE, DROP_LABEL);
        assertDrop(gen);

        // Test IPv4 header size pre-filled memory.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadFromMemory(R0, gen.IPV4_HEADER_SIZE_MEMORY_SLOT);
        gen.addJumpIfR0Equals(20, DROP_LABEL);
        assertDrop(gen, new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0x45}, 0);

        // Test not.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R0, 1234567890);
        gen.addNot(R0);
        gen.addJumpIfR0Equals(~1234567890, DROP_LABEL);
        assertDrop(gen);

        // Test negate.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R0, 1234567890);
        gen.addNeg(R0);
        gen.addJumpIfR0Equals(-1234567890, DROP_LABEL);
        assertDrop(gen);

        // Test move.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R1, 1234567890);
        gen.addMove(R0);
        gen.addJumpIfR0Equals(1234567890, DROP_LABEL);
        assertDrop(gen);
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R0, 1234567890);
        gen.addMove(R1);
        gen.addJumpIfR0Equals(1234567890, DROP_LABEL);
        assertDrop(gen);

        // Test swap.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R1, 1234567890);
        gen.addSwap();
        gen.addJumpIfR0Equals(1234567890, DROP_LABEL);
        assertDrop(gen);
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R0, 1234567890);
        gen.addSwap();
        gen.addJumpIfR0Equals(0, DROP_LABEL);
        assertDrop(gen);

        // Test jump if bytes not equal.
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R0, 1);
        gen.addJumpIfBytesAtR0NotEqual(new byte[]{123}, DROP_LABEL);
        program = gen.generate();
        assertEquals(6, program.length);
        assertEquals((13 << 3) | (1 << 1) | 0, program[0]);
        assertEquals(1, program[1]);
        assertEquals(((20 << 3) | (1 << 1) | 0) - 256, program[2]);
        assertEquals(1, program[3]);
        assertEquals(1, program[4]);
        assertEquals(123, program[5]);
        assertDrop(program, new byte[MIN_PKT_SIZE], 0);
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R0, 1);
        gen.addJumpIfBytesAtR0NotEqual(new byte[]{123}, DROP_LABEL);
        byte[] packet123 = {0,123,0,0,0,0,0,0,0,0,0,0,0,0,0};
        assertPass(gen, packet123, 0);
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addJumpIfBytesAtR0NotEqual(new byte[]{123}, DROP_LABEL);
        assertDrop(gen, packet123, 0);
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R0, 1);
        gen.addJumpIfBytesAtR0NotEqual(new byte[]{1, 2, 30, 4, 5}, DROP_LABEL);
        byte[] packet12345 = {0,1,2,3,4,5,0,0,0,0,0,0,0,0,0};
        assertDrop(gen, packet12345, 0);
        gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R0, 1);
        gen.addJumpIfBytesAtR0NotEqual(new byte[]{1, 2, 3, 4, 5}, DROP_LABEL);
        assertPass(gen, packet12345, 0);
    }

    @Test(expected = ApfV4Generator.IllegalInstructionException.class)
    public void testApfGeneratorWantsV2OrGreater() throws Exception {
        // The minimum supported APF version is 2.
        new ApfV4Generator(1);
    }

    @Test
    public void testApfDataOpcodesWantApfV3() throws IllegalInstructionException, Exception {
        ApfV4Generator gen = new ApfV4Generator(MIN_APF_VERSION);
        try {
            gen.addStoreData(R0, 0);
            fail();
        } catch (IllegalInstructionException expected) {
            /* pass */
        }
        try {
            gen.addLoadData(R0, 0);
            fail();
        } catch (IllegalInstructionException expected) {
            /* pass */
        }
    }

    /**
     * Test that the generator emits immediates using the shortest possible encoding.
     */
    @Test
    public void testImmediateEncoding() throws IllegalInstructionException {
        ApfV4Generator gen;

        // 0-byte immediate: li R0, 0
        gen = new ApfV4Generator(4);
        gen.addLoadImmediate(R0, 0);
        assertProgramEquals(new byte[]{LI_OP | SIZE0}, gen.generate());

        // 1-byte immediate: li R0, 42
        gen = new ApfV4Generator(4);
        gen.addLoadImmediate(R0, 42);
        assertProgramEquals(new byte[]{LI_OP | SIZE8, 42}, gen.generate());

        // 2-byte immediate: li R1, 0x1234
        gen = new ApfV4Generator(4);
        gen.addLoadImmediate(R1, 0x1234);
        assertProgramEquals(new byte[]{LI_OP | SIZE16 | R1_REG, 0x12, 0x34}, gen.generate());

        // 4-byte immediate: li R0, 0x12345678
        gen = new ApfV4Generator(3);
        gen.addLoadImmediate(R0, 0x12345678);
        assertProgramEquals(
                new byte[]{LI_OP | SIZE32, 0x12, 0x34, 0x56, 0x78},
                gen.generate());
    }

    /**
     * Test that the generator emits negative immediates using the shortest possible encoding.
     */
    @Test
    public void testNegativeImmediateEncoding() throws IllegalInstructionException {
        ApfV4Generator gen;

        // 1-byte negative immediate: li R0, -42
        gen = new ApfV4Generator(3);
        gen.addLoadImmediate(R0, -42);
        assertProgramEquals(new byte[]{LI_OP | SIZE8, -42}, gen.generate());

        // 2-byte negative immediate: li R1, -0x1122
        gen = new ApfV4Generator(3);
        gen.addLoadImmediate(R1, -0x1122);
        assertProgramEquals(new byte[]{LI_OP | SIZE16 | R1_REG, (byte)0xEE, (byte)0xDE},
                gen.generate());

        // 4-byte negative immediate: li R0, -0x11223344
        gen = new ApfV4Generator(3);
        gen.addLoadImmediate(R0, -0x11223344);
        assertProgramEquals(
                new byte[]{LI_OP | SIZE32, (byte)0xEE, (byte)0xDD, (byte)0xCC, (byte)0xBC},
                gen.generate());
    }

    /**
     * Test that the generator correctly emits positive and negative immediates for LDDW/STDW.
     */
    @Test
    public void testLoadStoreDataEncoding() throws IllegalInstructionException {
        ApfV4Generator gen;

        // Load data with no offset: lddw R0, [0 + r1]
        gen = new ApfV4Generator(APF_VERSION_4);
        gen.addLoadData(R0, 0);
        assertProgramEquals(new byte[]{LDDW_OP | SIZE0}, gen.generate());

        // Store data with 8bit negative offset: lddw r0, [-42 + r1]
        gen = new ApfV4Generator(APF_VERSION_4);
        gen.addStoreData(R0, -42);
        assertProgramEquals(new byte[]{STDW_OP | SIZE8, -42}, gen.generate());

        // Store data to R1 with 16bit negative offset: stdw r1, [-0x1122 + r0]
        gen = new ApfV4Generator(APF_VERSION_4);
        gen.addStoreData(R1, -0x1122);
        assertProgramEquals(new byte[]{STDW_OP | SIZE16 | R1_REG, (byte)0xEE, (byte)0xDE},
                gen.generate());

        // Load data to R1 with 32bit negative offset: lddw r1, [0xDEADBEEF + r0]
        gen = new ApfV4Generator(APF_VERSION_4);
        gen.addLoadData(R1, 0xDEADBEEF);
        assertProgramEquals(
                new byte[]{LDDW_OP | SIZE32 | R1_REG,
                        (byte)0xDE, (byte)0xAD, (byte)0xBE, (byte)0xEF},
                gen.generate());
    }

    /**
     * Test that the interpreter correctly executes STDW with a negative 8bit offset
     */
    @Test
    public void testApfDataWrite() throws IllegalInstructionException, Exception {
        byte[] packet = new byte[MIN_PKT_SIZE];
        byte[] data = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15};
        byte[] expected_data = data.clone();

        // No memory access instructions: should leave the data segment untouched.
        ApfV4Generator gen = new ApfV4Generator(APF_VERSION_4);
        assertDataMemoryContents(PASS, gen.generate(), packet, data, expected_data);

        // Expect value 0x87654321 to be stored starting from address -11 from the end of the
        // data buffer, in big-endian order.
        gen = new ApfV4Generator(APF_VERSION_4);
        gen.addLoadImmediate(R0, 0x87654321);
        gen.addLoadImmediate(R1, -5);
        gen.addStoreData(R0, -6);  // -5 + -6 = -11 (offset +5 with data_len=16)
        expected_data[5] = (byte)0x87;
        expected_data[6] = (byte)0x65;
        expected_data[7] = (byte)0x43;
        expected_data[8] = (byte)0x21;
        assertDataMemoryContents(PASS, gen.generate(), packet, data, expected_data);
    }

    /**
     * Test that the interpreter correctly executes LDDW with a negative 16bit offset
     */
    @Test
    public void testApfDataRead() throws IllegalInstructionException, Exception {
        // Program that DROPs if address 10 (-6) contains 0x87654321.
        ApfV4Generator gen = new ApfV4Generator(APF_VERSION_4);
        gen.addLoadImmediate(R1, 1000);
        gen.addLoadData(R0, -1006);  // 1000 + -1006 = -6 (offset +10 with data_len=16)
        gen.addJumpIfR0Equals(0x87654321, DROP_LABEL);
        byte[] program = gen.generate();
        byte[] packet = new byte[MIN_PKT_SIZE];

        // Content is incorrect (last byte does not match) -> PASS
        byte[] data = new byte[16];
        data[10] = (byte)0x87;
        data[11] = (byte)0x65;
        data[12] = (byte)0x43;
        data[13] = (byte)0x00;  // != 0x21
        byte[] expected_data = data.clone();
        assertDataMemoryContents(PASS, program, packet, data, expected_data);

        // Fix the last byte -> conditional jump taken -> DROP
        data[13] = (byte)0x21;
        expected_data = data;
        assertDataMemoryContents(DROP, program, packet, data, expected_data);
    }

    /**
     * Test that the interpreter correctly executes LDDW followed by a STDW.
     * To cover a few more edge cases, LDDW has a 0bit offset, while STDW has a positive 8bit
     * offset.
     */
    @Test
    public void testApfDataReadModifyWrite() throws IllegalInstructionException, Exception {
        ApfV4Generator gen = new ApfV4Generator(APF_VERSION_4);
        gen.addLoadImmediate(R1, -22);
        gen.addLoadData(R0, 0);  // Load from address 32 -22 + 0 = 10
        gen.addAdd(0x78453412);  // 87654321 + 78453412 = FFAA7733
        gen.addStoreData(R0, 4);  // Write back to address 32 -22 + 4 = 14

        byte[] packet = new byte[MIN_PKT_SIZE];
        byte[] data = new byte[32];
        data[10] = (byte)0x87;
        data[11] = (byte)0x65;
        data[12] = (byte)0x43;
        data[13] = (byte)0x21;
        byte[] expected_data = data.clone();
        expected_data[14] = (byte)0xFF;
        expected_data[15] = (byte)0xAA;
        expected_data[16] = (byte)0x77;
        expected_data[17] = (byte)0x33;
        assertDataMemoryContents(PASS, gen.generate(), packet, data, expected_data);
    }

    @Test
    public void testApfDataBoundChecking() throws IllegalInstructionException, Exception {
        byte[] packet = new byte[MIN_PKT_SIZE];
        byte[] data = new byte[32];
        byte[] expected_data = data;

        // Program that DROPs unconditionally. This is our the baseline.
        ApfV4Generator gen = new ApfV4Generator(APF_VERSION_4);
        gen.addLoadImmediate(R0, 3);
        gen.addLoadData(R1, 7);
        gen.addJump(DROP_LABEL);
        assertDataMemoryContents(DROP, gen.generate(), packet, data, expected_data);

        // Same program as before, but this time we're trying to load past the end of the data.
        // 3 instructions, all normal opcodes (LI, LDDW, JMP) with 1 byte immediate = 6 byte program
        // 32 byte data length, for a total of 38 byte ram len.
        // APFv6 needs to round this up to be a multiple of 4, so 40.
        gen = new ApfV4Generator(APF_VERSION_4);
        gen.addLoadImmediate(R0, 20);
        if (mApfVersion == 4) {
            gen.addLoadData(R1, 15);  // R0(20)+15+U32[0..3] >= 6 prog + 32 data, so invalid
        } else {
            gen.addLoadData(R1, 17);  // R0(20)+17+U32[0..3] >= 6 prog + 2 pad + 32 data, so invalid
        }
        gen.addJump(DROP_LABEL);  // Not reached.
        assertDataMemoryContents(PASS, gen.generate(), packet, data, expected_data);

        // Subtracting an immediate should work...
        gen = new ApfV4Generator(APF_VERSION_4);
        gen.addLoadImmediate(R0, 20);
        gen.addLoadData(R1, -4);
        gen.addJump(DROP_LABEL);
        assertDataMemoryContents(DROP, gen.generate(), packet, data, expected_data);

        // ...and underflowing simply wraps around to the end of the buffer...
        gen = new ApfV4Generator(APF_VERSION_4);
        gen.addLoadImmediate(R0, 20);
        gen.addLoadData(R1, -30);
        gen.addJump(DROP_LABEL);
        assertDataMemoryContents(DROP, gen.generate(), packet, data, expected_data);

        // ...but doesn't allow accesses before the start of the buffer
        gen = new ApfV4Generator(APF_VERSION_4);
        gen.addLoadImmediate(R0, 20);
        gen.addLoadData(R1, -1000);
        gen.addJump(DROP_LABEL);  // Not reached.
        assertDataMemoryContents(PASS, gen.generate(), packet, data, expected_data);
    }

    /**
     * Generate some BPF programs, translate them to APF, then run APF and BPF programs
     * over packet traces and verify both programs filter out the same packets.
     */
    @Test
    public void testApfAgainstBpf() throws Exception {
        String[] tcpdump_filters = new String[]{ "udp", "tcp", "icmp", "icmp6", "udp port 53",
                "arp", "dst 239.255.255.250", "arp or tcp or udp port 53", "net 192.168.1.0/24",
                "arp or icmp6 or portrange 53-54", "portrange 53-54 or portrange 100-50000",
                "tcp[tcpflags] & (tcp-ack|tcp-fin) != 0 and (ip[2:2] > 57 or icmp)" };
        String pcap_filename = stageFile(R.raw.apf);
        for (String tcpdump_filter : tcpdump_filters) {
            byte[] apf_program = Bpf2Apf.convert(compileToBpf(tcpdump_filter));
            assertTrue("Failed to match for filter: " + tcpdump_filter,
                    compareBpfApf(mApfVersion, tcpdump_filter, pcap_filename, apf_program));
        }
    }

    /**
     * Generate APF program, run pcap file though APF filter, then check all the packets in the file
     * should be dropped.
     */
    @Test
    public void testApfFilterPcapFile() throws Exception {
        final byte[] MOCK_PCAP_IPV4_ADDR = {(byte) 172, 16, 7, (byte) 151};
        String pcapFilename = stageFile(R.raw.apfPcap);
        MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        LinkAddress link = new LinkAddress(InetAddress.getByAddress(MOCK_PCAP_IPV4_ADDR), 16);
        LinkProperties lp = new LinkProperties();
        lp.addLinkAddress(link);

        ApfConfiguration config = getDefaultConfig();
        ApfCapabilities MOCK_APF_PCAP_CAPABILITIES = new ApfCapabilities(4, 1700, ARPHRD_ETHER);
        config.apfCapabilities = MOCK_APF_PCAP_CAPABILITIES;
        config.multicastFilter = DROP_MULTICAST;
        config.ieee802_3Filter = DROP_802_3_FRAMES;
        TestApfFilter apfFilter = new TestApfFilter(mContext, config, ipClientCallback,
                mNetworkQuirkMetrics);
        apfFilter.setLinkProperties(lp);
        byte[] program = ipClientCallback.assertProgramUpdateAndGet();
        byte[] data = new byte[Counter.totalSize()];
        final boolean result;

        result = dropsAllPackets(mApfVersion, program, data, pcapFilename);
        Log.i(TAG, "testApfFilterPcapFile(): Data counters: " + HexDump.toHexString(data, false));

        assertTrue("Failed to drop all packets by filter. \nAPF counters:" +
            HexDump.toHexString(data, false), result);
        apfFilter.shutdown();
    }

    private static final int ETH_HEADER_LEN               = 14;
    private static final int ETH_DEST_ADDR_OFFSET         = 0;
    private static final int ETH_ETHERTYPE_OFFSET         = 12;
    private static final byte[] ETH_BROADCAST_MAC_ADDRESS =
            {(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff };
    private static final byte[] ETH_MULTICAST_MDNS_v4_MAC_ADDRESS =
            {(byte) 0x01, (byte) 0x00, (byte) 0x5e, (byte) 0x00, (byte) 0x00, (byte) 0xfb};
    private static final byte[] ETH_MULTICAST_MDNS_V6_MAC_ADDRESS =
            {(byte) 0x33, (byte) 0x33, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xfb};

    private static final int IP_HEADER_OFFSET = ETH_HEADER_LEN;

    private static final int IPV4_HEADER_LEN          = 20;
    private static final int IPV4_TOTAL_LENGTH_OFFSET = IP_HEADER_OFFSET + 2;
    private static final int IPV4_PROTOCOL_OFFSET     = IP_HEADER_OFFSET + 9;
    private static final int IPV4_SRC_ADDR_OFFSET     = IP_HEADER_OFFSET + 12;
    private static final int IPV4_DEST_ADDR_OFFSET    = IP_HEADER_OFFSET + 16;

    private static final int IPV4_TCP_HEADER_LEN           = 20;
    private static final int IPV4_TCP_HEADER_OFFSET        = IP_HEADER_OFFSET + IPV4_HEADER_LEN;
    private static final int IPV4_TCP_SRC_PORT_OFFSET      = IPV4_TCP_HEADER_OFFSET + 0;
    private static final int IPV4_TCP_DEST_PORT_OFFSET     = IPV4_TCP_HEADER_OFFSET + 2;
    private static final int IPV4_TCP_SEQ_NUM_OFFSET       = IPV4_TCP_HEADER_OFFSET + 4;
    private static final int IPV4_TCP_ACK_NUM_OFFSET       = IPV4_TCP_HEADER_OFFSET + 8;
    private static final int IPV4_TCP_HEADER_LENGTH_OFFSET = IPV4_TCP_HEADER_OFFSET + 12;
    private static final int IPV4_TCP_HEADER_FLAG_OFFSET   = IPV4_TCP_HEADER_OFFSET + 13;

    private static final int IPV4_UDP_HEADER_OFFSET    = IP_HEADER_OFFSET + IPV4_HEADER_LEN;
    private static final int IPV4_UDP_SRC_PORT_OFFSET  = IPV4_UDP_HEADER_OFFSET + 0;
    private static final int IPV4_UDP_DEST_PORT_OFFSET = IPV4_UDP_HEADER_OFFSET + 2;
    private static final int IPV4_UDP_LENGTH_OFFSET    = IPV4_UDP_HEADER_OFFSET + 4;
    private static final int IPV4_UDP_PAYLOAD_OFFSET   = IPV4_UDP_HEADER_OFFSET + 8;
    private static final byte[] IPV4_BROADCAST_ADDRESS =
            {(byte) 255, (byte) 255, (byte) 255, (byte) 255};

    private static final int IPV6_HEADER_LEN             = 40;
    private static final int IPV6_PAYLOAD_LENGTH_OFFSET  = IP_HEADER_OFFSET + 4;
    private static final int IPV6_NEXT_HEADER_OFFSET     = IP_HEADER_OFFSET + 6;
    private static final int IPV6_SRC_ADDR_OFFSET        = IP_HEADER_OFFSET + 8;
    private static final int IPV6_DEST_ADDR_OFFSET       = IP_HEADER_OFFSET + 24;
    private static final int IPV6_PAYLOAD_OFFSET = IP_HEADER_OFFSET + IPV6_HEADER_LEN;
    private static final int IPV6_TCP_SRC_PORT_OFFSET    = IPV6_PAYLOAD_OFFSET + 0;
    private static final int IPV6_TCP_DEST_PORT_OFFSET   = IPV6_PAYLOAD_OFFSET + 2;
    private static final int IPV6_TCP_SEQ_NUM_OFFSET     = IPV6_PAYLOAD_OFFSET + 4;
    private static final int IPV6_TCP_ACK_NUM_OFFSET     = IPV6_PAYLOAD_OFFSET + 8;
    // The IPv6 all nodes address ff02::1
    private static final byte[] IPV6_ALL_NODES_ADDRESS   =
            { (byte) 0xff, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 };
    private static final byte[] IPV6_ALL_ROUTERS_ADDRESS =
            { (byte) 0xff, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2 };
    private static final byte[] IPV6_SOLICITED_NODE_MULTICAST_ADDRESS = {
            (byte) 0xff, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1,
            (byte) 0xff, (byte) 0xab, (byte) 0xcd, (byte) 0xef,
    };

    private static final int ICMP6_TYPE_OFFSET           = IP_HEADER_OFFSET + IPV6_HEADER_LEN;
    private static final int ICMP6_ROUTER_SOLICITATION   = 133;
    private static final int ICMP6_ROUTER_ADVERTISEMENT  = 134;
    private static final int ICMP6_NEIGHBOR_SOLICITATION = 135;
    private static final int ICMP6_NEIGHBOR_ANNOUNCEMENT = 136;

    private static final int ICMP6_RA_HEADER_LEN = 16;
    private static final int ICMP6_RA_CHECKSUM_OFFSET =
            IP_HEADER_OFFSET + IPV6_HEADER_LEN + 2;
    private static final int ICMP6_RA_ROUTER_LIFETIME_OFFSET =
            IP_HEADER_OFFSET + IPV6_HEADER_LEN + 6;
    private static final int ICMP6_RA_REACHABLE_TIME_OFFSET =
            IP_HEADER_OFFSET + IPV6_HEADER_LEN + 8;
    private static final int ICMP6_RA_RETRANSMISSION_TIMER_OFFSET =
            IP_HEADER_OFFSET + IPV6_HEADER_LEN + 12;
    private static final int ICMP6_RA_OPTION_OFFSET =
            IP_HEADER_OFFSET + IPV6_HEADER_LEN + ICMP6_RA_HEADER_LEN;

    private static final int ICMP6_PREFIX_OPTION_TYPE                      = 3;
    private static final int ICMP6_PREFIX_OPTION_LEN                       = 32;
    private static final int ICMP6_PREFIX_OPTION_VALID_LIFETIME_OFFSET     = 4;
    private static final int ICMP6_PREFIX_OPTION_PREFERRED_LIFETIME_OFFSET = 8;

    // From RFC6106: Recursive DNS Server option
    private static final int ICMP6_RDNSS_OPTION_TYPE = 25;
    // From RFC6106: DNS Search List option
    private static final int ICMP6_DNSSL_OPTION_TYPE = 31;

    // From RFC4191: Route Information option
    private static final int ICMP6_ROUTE_INFO_OPTION_TYPE = 24;
    // Above three options all have the same format:
    private static final int ICMP6_4_BYTE_OPTION_LEN      = 8;
    private static final int ICMP6_4_BYTE_LIFETIME_OFFSET = 4;
    private static final int ICMP6_4_BYTE_LIFETIME_LEN    = 4;

    private static final int UDP_HEADER_LEN              = 8;
    private static final int UDP_DESTINATION_PORT_OFFSET = ETH_HEADER_LEN + 22;

    private static final int DHCP_CLIENT_PORT       = 68;
    private static final int DHCP_CLIENT_MAC_OFFSET = ETH_HEADER_LEN + UDP_HEADER_LEN + 48;

    private static final int ARP_HEADER_OFFSET          = ETH_HEADER_LEN;
    private static final byte[] ARP_IPV4_REQUEST_HEADER = {
            0, 1, // Hardware type: Ethernet (1)
            8, 0, // Protocol type: IP (0x0800)
            6,    // Hardware size: 6
            4,    // Protocol size: 4
            0, 1  // Opcode: request (1)
    };
    private static final byte[] ARP_IPV4_REPLY_HEADER = {
            0, 1, // Hardware type: Ethernet (1)
            8, 0, // Protocol type: IP (0x0800)
            6,    // Hardware size: 6
            4,    // Protocol size: 4
            0, 2  // Opcode: reply (2)
    };
    private static final int ARP_SOURCE_IP_ADDRESS_OFFSET = ARP_HEADER_OFFSET + 14;
    private static final int ARP_TARGET_IP_ADDRESS_OFFSET = ARP_HEADER_OFFSET + 24;

    private static final byte[] MOCK_IPV4_ADDR           = {10, 0, 0, 1};
    private static final byte[] MOCK_BROADCAST_IPV4_ADDR = {10, 0, 31, (byte) 255}; // prefix = 19
    private static final byte[] MOCK_MULTICAST_IPV4_ADDR = {(byte) 224, 0, 0, 1};
    private static final byte[] ANOTHER_IPV4_ADDR        = {10, 0, 0, 2};
    private static final byte[] IPV4_SOURCE_ADDR         = {10, 0, 0, 3};
    private static final byte[] ANOTHER_IPV4_SOURCE_ADDR = {(byte) 192, 0, 2, 1};
    private static final byte[] BUG_PROBE_SOURCE_ADDR1   = {0, 0, 1, 2};
    private static final byte[] BUG_PROBE_SOURCE_ADDR2   = {3, 4, 0, 0};
    private static final byte[] IPV4_ANY_HOST_ADDR       = {0, 0, 0, 0};
    private static final byte[] IPV4_MDNS_MULTICAST_ADDR = {(byte) 224, 0, 0, (byte) 251};
    private static final byte[] IPV6_MDNS_MULTICAST_ADDR =
            {(byte) 0xff, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xfb};
    private static final int IPV6_UDP_DEST_PORT_OFFSET = IPV6_PAYLOAD_OFFSET + 2;
    private static final int MDNS_UDP_PORT = 5353;

    private static void setIpv4VersionFields(ByteBuffer packet) {
        packet.putShort(ETH_ETHERTYPE_OFFSET, (short) ETH_P_IP);
        packet.put(IP_HEADER_OFFSET, (byte) 0x45);
    }

    private static void setIpv6VersionFields(ByteBuffer packet) {
        packet.putShort(ETH_ETHERTYPE_OFFSET, (short) ETH_P_IPV6);
        packet.put(IP_HEADER_OFFSET, (byte) 0x60);
    }

    private static ByteBuffer makeIpv4Packet(int proto) {
        ByteBuffer packet = ByteBuffer.wrap(new byte[100]);
        setIpv4VersionFields(packet);
        packet.put(IPV4_PROTOCOL_OFFSET, (byte) proto);
        return packet;
    }

    private static ByteBuffer makeIpv6Packet(int nextHeader) {
        ByteBuffer packet = ByteBuffer.wrap(new byte[100]);
        setIpv6VersionFields(packet);
        packet.put(IPV6_NEXT_HEADER_OFFSET, (byte) nextHeader);
        return packet;
    }

    @Test
    public void testApfFilterIPv4() throws Exception {
        MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        LinkAddress link = new LinkAddress(InetAddress.getByAddress(MOCK_IPV4_ADDR), 19);
        LinkProperties lp = new LinkProperties();
        lp.addLinkAddress(link);

        ApfConfiguration config = getDefaultConfig();
        config.multicastFilter = DROP_MULTICAST;
        TestApfFilter apfFilter = new TestApfFilter(mContext, config, ipClientCallback,
                mNetworkQuirkMetrics);
        apfFilter.setLinkProperties(lp);

        byte[] program = ipClientCallback.assertProgramUpdateAndGet();

        // Verify empty packet of 100 zero bytes is passed
        ByteBuffer packet = ByteBuffer.wrap(new byte[100]);
        assertPass(program, packet.array());

        // Verify unicast IPv4 packet is passed
        put(packet, ETH_DEST_ADDR_OFFSET, TestApfFilter.MOCK_MAC_ADDR);
        packet.putShort(ETH_ETHERTYPE_OFFSET, (short)ETH_P_IP);
        put(packet, IPV4_DEST_ADDR_OFFSET, MOCK_IPV4_ADDR);
        assertPass(program, packet.array());

        // Verify L2 unicast to IPv4 broadcast addresses is dropped (b/30231088)
        put(packet, IPV4_DEST_ADDR_OFFSET, IPV4_BROADCAST_ADDRESS);
        assertDrop(program, packet.array());
        put(packet, IPV4_DEST_ADDR_OFFSET, MOCK_BROADCAST_IPV4_ADDR);
        assertDrop(program, packet.array());

        // Verify multicast/broadcast IPv4, not DHCP to us, is dropped
        put(packet, ETH_DEST_ADDR_OFFSET, ETH_BROADCAST_MAC_ADDRESS);
        assertDrop(program, packet.array());
        packet.put(IP_HEADER_OFFSET, (byte) 0x45);
        assertDrop(program, packet.array());
        packet.put(IPV4_PROTOCOL_OFFSET, (byte)IPPROTO_UDP);
        assertDrop(program, packet.array());
        packet.putShort(UDP_DESTINATION_PORT_OFFSET, (short)DHCP_CLIENT_PORT);
        assertDrop(program, packet.array());
        put(packet, IPV4_DEST_ADDR_OFFSET, MOCK_MULTICAST_IPV4_ADDR);
        assertDrop(program, packet.array());
        put(packet, IPV4_DEST_ADDR_OFFSET, MOCK_BROADCAST_IPV4_ADDR);
        assertDrop(program, packet.array());
        put(packet, IPV4_DEST_ADDR_OFFSET, IPV4_BROADCAST_ADDRESS);
        assertDrop(program, packet.array());

        // Verify broadcast IPv4 DHCP to us is passed
        put(packet, DHCP_CLIENT_MAC_OFFSET, TestApfFilter.MOCK_MAC_ADDR);
        assertPass(program, packet.array());

        // Verify unicast IPv4 DHCP to us is passed
        put(packet, ETH_DEST_ADDR_OFFSET, TestApfFilter.MOCK_MAC_ADDR);
        assertPass(program, packet.array());

        apfFilter.shutdown();
    }

    @Test
    public void testApfFilterIPv6() throws Exception {
        MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        ApfConfiguration config = getDefaultConfig();
        TestApfFilter apfFilter = new TestApfFilter(mContext, config, ipClientCallback,
                mNetworkQuirkMetrics);
        byte[] program = ipClientCallback.assertProgramUpdateAndGet();

        // Verify empty IPv6 packet is passed
        ByteBuffer packet = makeIpv6Packet(IPPROTO_UDP);
        assertPass(program, packet.array());

        // Verify empty ICMPv6 packet is passed
        packet.put(IPV6_NEXT_HEADER_OFFSET, (byte)IPPROTO_ICMPV6);
        assertPass(program, packet.array());

        // Verify empty ICMPv6 NA packet is passed
        packet.put(ICMP6_TYPE_OFFSET, (byte)ICMP6_NEIGHBOR_ANNOUNCEMENT);
        assertPass(program, packet.array());

        // Verify ICMPv6 NA to ff02::1 is dropped
        put(packet, IPV6_DEST_ADDR_OFFSET, IPV6_ALL_NODES_ADDRESS);
        assertDrop(program, packet.array());

        // Verify ICMPv6 NA to ff02::2 is dropped
        put(packet, IPV6_DEST_ADDR_OFFSET, IPV6_ALL_ROUTERS_ADDRESS);
        assertDrop(program, packet.array());

        // Verify ICMPv6 NA to Solicited-Node Multicast is passed
        put(packet, IPV6_DEST_ADDR_OFFSET, IPV6_SOLICITED_NODE_MULTICAST_ADDRESS);
        assertPass(program, packet.array());

        // Verify ICMPv6 RS to any is dropped
        packet.put(ICMP6_TYPE_OFFSET, (byte)ICMP6_ROUTER_SOLICITATION);
        assertDrop(program, packet.array());
        put(packet, IPV6_DEST_ADDR_OFFSET, IPV6_ALL_ROUTERS_ADDRESS);
        assertDrop(program, packet.array());

        apfFilter.shutdown();
    }

    private static void fillQuestionSection(ByteBuffer buf, String... qnames) throws IOException {
        buf.put(new DnsPacket.DnsHeader(0 /* id */, 0 /* flags */, qnames.length, 0 /* ancount */)
                .getBytes());
        for (String qname : qnames) {
            buf.put(DnsPacket.DnsRecord.makeQuestion(qname, 0 /* nsType */, 0 /* nsClass */)
                    .getBytes());
        }
    }

    private static byte[] makeMdnsV4Packet(String... qnames) throws IOException {
        final ByteBuffer buf = ByteBuffer.wrap(new byte[256]);
        final PacketBuilder builder = new PacketBuilder(buf);
        builder.writeL2Header(MacAddress.fromString("11:22:33:44:55:66"),
                MacAddress.fromBytes(ETH_MULTICAST_MDNS_v4_MAC_ADDRESS),
                (short) ETH_P_IP);
        builder.writeIpv4Header((byte) 0 /* tos */, (short) 0 /* id */,
                (short) 0 /* flagsAndFragmentOffset */, (byte) 0 /* ttl */, (byte) IPPROTO_UDP,
                (Inet4Address) Inet4Address.getByAddress(IPV4_SOURCE_ADDR),
                (Inet4Address) Inet4Address.getByAddress(IPV4_MDNS_MULTICAST_ADDR));
        builder.writeUdpHeader((short) MDNS_UDP_PORT, (short) MDNS_UDP_PORT);
        fillQuestionSection(buf, qnames);
        return builder.finalizePacket().array();
    }

    private static byte[] makeMdnsV6Packet(String... qnames) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(new byte[256]);
        final PacketBuilder builder = new PacketBuilder(buf);
        builder.writeL2Header(MacAddress.fromString("11:22:33:44:55:66"),
                MacAddress.fromBytes(ETH_MULTICAST_MDNS_V6_MAC_ADDRESS),
                (short) ETH_P_IPV6);
        builder.writeIpv6Header(0x680515ca /* vtf */, (byte) IPPROTO_UDP, (short) 0 /* hopLimit */,
                (Inet6Address) InetAddress.getByAddress(IPV6_ANOTHER_ADDR),
                (Inet6Address) Inet6Address.getByAddress(IPV6_MDNS_MULTICAST_ADDR));
        builder.writeUdpHeader((short) MDNS_UDP_PORT, (short) MDNS_UDP_PORT);
        fillQuestionSection(buf, qnames);
        return builder.finalizePacket().array();
    }

    private static void putLabel(ByteBuffer buf, String label) {
        final byte[] bytes = label.getBytes(StandardCharsets.UTF_8);
        buf.put((byte) bytes.length);
        buf.put(bytes);
    }

    private static void putPointer(ByteBuffer buf, int offset) {
        short pointer = (short) (offset | 0xc000);
        buf.putShort(pointer);
    }


    // Simplistic DNS compression code that intentionally does not depend on production code.
    private static List<Pair<Integer, String>> getDnsLabels(int startOffset, String... names) {
        // Maps all possible name suffixes to packet offsets.
        final HashMap<String, Integer> mPointerOffsets = new HashMap<>();
        final List<Pair<Integer, String>> out = new ArrayList<>();
        int offset = startOffset;
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            while (true) {
                if (name.length() == 0) {
                    out.add(label(""));
                    offset += 1 + 4;  // 1-byte label, DNS query
                    break;
                }

                final int pointerOffset = mPointerOffsets.getOrDefault(name, -1);
                if (pointerOffset != -1) {
                    out.add(pointer(pointerOffset));
                    offset += 2 + 4; // 2-byte pointer, DNS query
                    break;
                }

                mPointerOffsets.put(name, offset);

                final int indexOfDot = name.indexOf(".");
                final String label;
                if (indexOfDot == -1) {
                    label = name;
                    name = "";
                } else {
                    label = name.substring(0, indexOfDot);
                    name = name.substring(indexOfDot + 1);
                }
                out.add(label(label));
                offset += 1 + label.length();
            }
        }
        return out;
    }

    static Pair<Integer, String> label(String label) {
        return Pair.create(label.length(), label);
    }

    static Pair<Integer, String> pointer(int offset) {
        return Pair.create(0xc000 | offset, null);
    }

    @Test
    public void testGetDnsLabels() throws Exception {
        int startOffset = 12;
        List<Pair<Integer, String>> actual = getDnsLabels(startOffset, "myservice.tcp.local");
        assertEquals(4, actual.size());
        assertEquals(label("myservice"), actual.get(0));
        assertEquals(label("tcp"), actual.get(1));
        assertEquals(label("local"), actual.get(2));
        assertEquals(label(""), actual.get(3));

        startOffset = 30;
        actual = getDnsLabels(startOffset,
                "myservice.tcp.local", "foo.tcp.local", "myhostname.local", "bar.udp.local",
                "foo.myhostname.local");
        final int tcpLocalOffset = startOffset + 1 + "myservice".length();
        final int localOffset = startOffset + 1 + "myservice".length() + 1 + "tcp".length();
        final int myhostnameLocalOffset = 30
                + 1 + "myservice".length() + 1 + "tcp".length() + 1 + "local".length() + 1 + 4
                + 1 + "foo".length() + 2 + 4;

        assertEquals(13, actual.size());
        assertEquals(label("myservice"), actual.get(0));
        assertEquals(label("tcp"), actual.get(1));
        assertEquals(label("local"), actual.get(2));
        assertEquals(label(""), actual.get(3));
        assertEquals(label("foo"), actual.get(4));
        assertEquals(pointer(tcpLocalOffset), actual.get(5));
        assertEquals(label("myhostname"), actual.get(6));
        assertEquals(pointer(localOffset), actual.get(7));
        assertEquals(label("bar"), actual.get(8));
        assertEquals(label("udp"), actual.get(9));
        assertEquals(pointer(localOffset), actual.get(10));
        assertEquals(label("foo"), actual.get(11));
        assertEquals(pointer(myhostnameLocalOffset), actual.get(12));

    }

    private static byte[] makeMdnsCompressedV6Packet(String... names) throws IOException {
        ByteBuffer questions = ByteBuffer.allocate(1500);
        questions.put(new DnsPacket.DnsHeader(123, 0, names.length, 0).getBytes());
        final List<Pair<Integer, String>> labels = getDnsLabels(questions.position(), names);
        for (Pair<Integer, String> label : labels) {
            final String name = label.second;
            if (name == null) {
                putPointer(questions, label.first);
            } else {
                putLabel(questions, name);
            }
            if (TextUtils.isEmpty(name)) {
                questions.put(new byte[4]);
            }
        }
        questions.flip();

        ByteBuffer buf = PacketBuilder.allocate(/*hasEther=*/ true, IPPROTO_IPV6, IPPROTO_UDP,
                questions.limit());
        final PacketBuilder builder = new PacketBuilder(buf);
        builder.writeL2Header(MacAddress.fromString("11:22:33:44:55:66"),
                MacAddress.fromBytes(ETH_MULTICAST_MDNS_V6_MAC_ADDRESS),
                (short) ETH_P_IPV6);
        builder.writeIpv6Header(0x680515ca /* vtf */, (byte) IPPROTO_UDP, (short) 0 /* hopLimit */,
                (Inet6Address) InetAddress.getByAddress(IPV6_ANOTHER_ADDR),
                (Inet6Address) Inet6Address.getByAddress(IPV6_MDNS_MULTICAST_ADDR));
        builder.writeUdpHeader((short) MDNS_UDP_PORT, (short) MDNS_UDP_PORT);

        buf.put(questions);

        return builder.finalizePacket().array();
    }

    private static byte[] makeMdnsCompressedV6Packet() throws IOException {
        return makeMdnsCompressedV6Packet("myservice.tcp.local", "googlecast.tcp.local",
                "matter.tcp.local", "myhostname.local");
    }

    private static byte[] makeMdnsCompressedV6PacketWithManyNames() throws IOException {
        return makeMdnsCompressedV6Packet("myservice.tcp.local", "googlecast.tcp.local",
                "matter.tcp.local", "myhostname.local", "myhostname2.local", "myhostname3.local",
                "myhostname4.local", "myhostname5.local", "myhostname6.local", "myhostname7.local");

    }

    @Test
    public void testAddNopAddsOneByte() throws Exception {
        ApfV4Generator gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addNop();
        assertEquals(1, gen.generate().length);

        final int count = 42;
        gen = new ApfV4Generator(MIN_APF_VERSION);
        for (int i = 0; i < count; i++) {
            gen.addNop();
        }
        assertEquals(count, gen.generate().length);
    }

    @Test
    public void testQnameEncoding() {
        String[] qname = new String[]{"abcd", "ef", "日本"};
        byte[] encodedQname = ApfFilter.encodeQname(qname);
        Assert.assertArrayEquals(
                new byte[]{0x04, 0x61, 0x62, 0x63, 0x64, 0x02, 0x65, 0x66, 0x06, (byte) 0xe6,
                        (byte) 0x97, (byte) 0xa5, (byte) 0xe6, (byte) 0x9c, (byte) 0xac, 0x00},
                encodedQname);
    }

    @Test
    public void testApfFilterMdns() throws Exception {
        final byte[] unicastIpv4Addr = {(byte) 192, 0, 2, 63};

        MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        LinkAddress link = new LinkAddress(InetAddress.getByAddress(unicastIpv4Addr), 24);
        LinkProperties lp = new LinkProperties();
        lp.addLinkAddress(link);

        ApfConfiguration config = getDefaultConfig();
        TestApfFilter apfFilter = new TestApfFilter(mContext, config, ipClientCallback,
                mNetworkQuirkMetrics);
        apfFilter.setLinkProperties(lp);

        // Construct IPv4 mDNS packet
        byte[] mdnsv4packet = makeMdnsV4Packet("test.local");
        byte[] mdnsv6packet = makeMdnsV6Packet("test.local");
        byte[] program = ipClientCallback.assertProgramUpdateAndGet();
        // mDNSv4 packet is passed if no mDns filter is turned on
        assertPass(program, mdnsv4packet);
        // mDNSv6 packet is passed if no mDNS filter is turned on
        assertPass(program, mdnsv6packet);

        // mDNSv4 packet with qname in the allowlist is passed
        apfFilter.addToMdnsAllowList(new String[]{"test", "local"});
        apfFilter.addToMdnsAllowList(new String[]{"abcd", "local"});
        apfFilter.setMulticastFilter(true);
        program = ipClientCallback.assertProgramUpdateAndGet();
        assertPass(program, mdnsv4packet);
        assertPass(program, mdnsv6packet);
        // If packet contains more than one qname, pass the packet
        mdnsv4packet = makeMdnsV4Packet("cccc.local", "dddd.local");
        mdnsv6packet = makeMdnsV6Packet("cccc.local", "dddd.local");
        assertPass(program, mdnsv4packet);
        assertPass(program, mdnsv6packet);
        // If packet doesn't contain any qname, pass the packet
        mdnsv4packet = makeMdnsV4Packet();
        mdnsv6packet = makeMdnsV6Packet();
        assertPass(program, mdnsv4packet);
        assertPass(program, mdnsv6packet);

        mdnsv4packet = makeMdnsV4Packet("abcd.local");
        mdnsv6packet = makeMdnsV6Packet("abcd.local");
        assertPass(program, mdnsv4packet);
        assertPass(program, mdnsv6packet);

        // mDNSv4 packet with qname not in the allowlist is dropped
        mdnsv4packet = makeMdnsV4Packet("ffff.local");
        mdnsv6packet = makeMdnsV6Packet("ffff.local");
        assertDrop(program, mdnsv4packet);
        assertDrop(program, mdnsv6packet);

        apfFilter.removeFromAllowList(new String[]{"abcd", "local"});
        program = ipClientCallback.assertProgramUpdateAndGet();
        mdnsv4packet = makeMdnsV4Packet("abcd.local");
        mdnsv6packet = makeMdnsV6Packet("abcd.local");
        assertDrop(program, mdnsv4packet);
        assertDrop(program, mdnsv6packet);

        apfFilter.shutdown();
    }

    private ApfV4Generator generateDnsFilter(boolean ipv6, String... labels) throws Exception {
        ApfV4Generator gen = new ApfV4Generator(MIN_APF_VERSION);
        gen.addLoadImmediate(R1, ipv6 ? IPV6_HEADER_LEN : IPV4_HEADER_LEN);
        DnsUtils.generateFilter(gen, labels);
        return gen;
    }

    private void doTestDnsParsing(boolean expectPass, boolean ipv6, String filterName,
            byte[] pkt) throws Exception {
        final String[] labels = filterName.split(/*regex=*/ "[.]");
        ApfV4Generator gen = generateDnsFilter(ipv6, labels);

        // Hack to prevent the APF instruction limit triggering.
        for (int i = 0; i < 500; i++) {
            gen.addNop();
        }

        byte[] program = gen.generate();
        Log.d(TAG, "prog_len=" + program.length);
        if (expectPass) {
            assertPass(program, pkt, 0);
        } else {
            assertDrop(program, pkt, 0);
        }
    }

    private void doTestDnsParsing(boolean expectPass, boolean ipv6, String filterName,
            String... packetNames) throws Exception {
        final byte[] pkt = ipv6 ? makeMdnsV6Packet(packetNames) : makeMdnsV4Packet(packetNames);
        doTestDnsParsing(expectPass, ipv6, filterName, pkt);
    }

    @Test
    public void testDnsParsing() throws Exception {
        final boolean ipv4 = false, ipv6 = true;

        // Packets with one question.
        // Names don't start with _ because DnsPacket thinks such names are invalid.
        doTestDnsParsing(true, ipv6, "googlecast.tcp.local", "googlecast.tcp.local");
        doTestDnsParsing(true, ipv4, "googlecast.tcp.local", "googlecast.tcp.local");
        doTestDnsParsing(false, ipv6, "googlecast.tcp.lozal", "googlecast.tcp.local");
        doTestDnsParsing(false, ipv4, "googlecast.tcp.lozal", "googlecast.tcp.local");
        doTestDnsParsing(false, ipv6, "googlecast.udp.local", "googlecast.tcp.local");
        doTestDnsParsing(false, ipv4, "googlecast.udp.local", "googlecast.tcp.local");

        // Packets with multiple questions that can't be compressed. Not realistic for MDNS since
        // everything ends in .local, but useful to ensure only the non-compression code is tested.
        doTestDnsParsing(true, ipv6, "googlecast.tcp.local",
                "googlecast.tcp.local", "developer.android.com");
        doTestDnsParsing(true, ipv4, "googlecast.tcp.local",
                "developer.android.com", "googlecast.tcp.local");
        doTestDnsParsing(false, ipv4, "googlecast.tcp.local",
                "developer.android.com", "googlecast.tcp.invalid");
        doTestDnsParsing(true, ipv6, "googlecast.tcp.local",
                "developer.android.com", "www.google.co.jp", "googlecast.tcp.local");
        doTestDnsParsing(false, ipv4, "veryverylongservicename.tcp.local",
                "www.google.co.jp", "veryverylongservicename.tcp.invalid");
        doTestDnsParsing(true, ipv6, "googlecast.tcp.local",
                "www.google.co.jp", "googlecast.tcp.local", "developer.android.com");

        // Name with duplicate labels.
        doTestDnsParsing(true, ipv6, "local.tcp.local", "local.tcp.local");

        final byte[] pkt = makeMdnsCompressedV6Packet();
        doTestDnsParsing(true, ipv6, "googlecast.tcp.local", pkt);
        doTestDnsParsing(true, ipv6, "matter.tcp.local", pkt);
        doTestDnsParsing(true, ipv6, "myservice.tcp.local", pkt);
        doTestDnsParsing(false, ipv6, "otherservice.tcp.local", pkt);
    }

    private void doTestDnsParsingProgramLength(int expectedLength,
            String filterName) throws Exception {
        final String[] labels = filterName.split(/*regex=*/ "[.]");

        ApfV4Generator gen = generateDnsFilter(/*ipv6=*/ true, labels);
        assertEquals("Program for " + filterName + " had unexpected length:",
                expectedLength, gen.generate().length);
    }

    /**
     * Rough metric of code size. Checks how large the generated filter is in various scenarios.
     * Helps ensure any changes to the code do not substantially increase APF code size.
     */
    @Test
    public void testDnsParsingProgramLength() throws Exception {
        doTestDnsParsingProgramLength(237, "MyDevice.local");
        doTestDnsParsingProgramLength(285, "_googlecast.tcp.local");
        doTestDnsParsingProgramLength(291, "_googlecast12345.tcp.local");
        doTestDnsParsingProgramLength(244, "_googlecastZtcp.local");
        doTestDnsParsingProgramLength(249, "_googlecastZtcp12345.local");
    }

    private void doTestDnsParsingNecessaryOverhead(int expectedNecessaryOverhead,
            String filterName, byte[] pkt, String description) throws Exception {
        final String[] labels = filterName.split(/*regex=*/ "[.]");

        // Check that the generated code, when the program contains the specified number of extra
        // bytes, is capable of dropping the packet.
        ApfV4Generator gen = generateDnsFilter(/*ipv6=*/ true, labels);
        for (int i = 0; i < expectedNecessaryOverhead; i++) {
            gen.addNop();
        }
        final byte[] programWithJustEnoughOverhead = gen.generate();
        assertVerdict(
                "Overhead too low: filter for " + filterName + " with " + expectedNecessaryOverhead
                        + " extra instructions unexpectedly passed " + description,
                DROP, programWithJustEnoughOverhead, pkt, 0);

        if (expectedNecessaryOverhead == 0) return;

        // Check that the generated code, without the specified number of extra program bytes,
        // cannot correctly drop the packet because it hits the interpreter instruction limit.
        gen = generateDnsFilter(/*ipv6=*/ true, labels);
        for (int i = 0; i < expectedNecessaryOverhead - 1; i++) {
            gen.addNop();
        }
        final byte[] programWithNotEnoughOverhead = gen.generate();

        assertVerdict(
                "Overhead too high: filter for " + filterName + " with " + expectedNecessaryOverhead
                        + " extra instructions unexpectedly dropped " + description,
                PASS, programWithNotEnoughOverhead, pkt, 0);
    }

    private void doTestDnsParsingNecessaryOverhead(int expectedNecessaryOverhead,
            String filterName, String... packetNames) throws Exception {
        doTestDnsParsingNecessaryOverhead(expectedNecessaryOverhead, filterName,
                makeMdnsV6Packet(packetNames),
                "IPv6 MDNS packet containing: " + Arrays.toString(packetNames));
    }

    /**
     * Rough metric of filter efficiency. Because the filter uses backwards jumps, on complex
     * packets it will not finish running before the interpreter hits the maximum number of allowed
     * instructions (== number of bytes in the program) and unconditionally accepts the packet.
     * This test checks much extra code the program must contain in order for the generated filter
     * to successfully drop the packet. It helps ensure any changes to the code do not reduce the
     * complexity of packets that the APF code can drop.
     */
    @Test
    public void testDnsParsingNecessaryOverhead() throws Exception {
        // Simple packets can be parsed with zero extra code.
        doTestDnsParsingNecessaryOverhead(0, "googlecast.tcp.local",
                "matter.tcp.local", "developer.android.com");

        doTestDnsParsingNecessaryOverhead(0, "googlecast.tcp.local",
                "developer.android.com", "matter.tcp.local");

        doTestDnsParsingNecessaryOverhead(0, "googlecast.tcp.local",
                "developer.android.com", "matter.tcp.local", "www.google.co.jp");

        doTestDnsParsingNecessaryOverhead(0, "googlecast.tcp.local",
                "developer.android.com", "matter.tcp.local", "www.google.co.jp",
                "example.org");

        // More complicated packets cause more instructions to be run and can only be dropped if
        // the program contains lots of extra code.
        doTestDnsParsingNecessaryOverhead(57, "googlecast.tcp.local",
                "developer.android.com", "matter.tcp.local", "www.google.co.jp",
                "example.org", "otherexample.net");

        doTestDnsParsingNecessaryOverhead(115, "googlecast.tcp.local",
                "developer.android.com", "matter.tcp.local", "www.google.co.jp",
                "example.org", "otherexample.net", "docs.new");

        doTestDnsParsingNecessaryOverhead(0, "foo.tcp.local",
                makeMdnsCompressedV6Packet(), "compressed packet");

        doTestDnsParsingNecessaryOverhead(235, "foo.tcp.local",
                makeMdnsCompressedV6PacketWithManyNames(), "compressed packet with many names");
    }

    @Test
    public void testApfFilterMulticast() throws Exception {
        final byte[] unicastIpv4Addr   = {(byte)192,0,2,63};
        final byte[] broadcastIpv4Addr = {(byte)192,0,2,(byte)255};
        final byte[] multicastIpv4Addr = {(byte)224,0,0,1};
        final byte[] multicastIpv6Addr = {(byte)0xff,2,0,0,0,0,0,0,0,0,0,0,0,0,0,(byte)0xfb};

        MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        LinkAddress link = new LinkAddress(InetAddress.getByAddress(unicastIpv4Addr), 24);
        LinkProperties lp = new LinkProperties();
        lp.addLinkAddress(link);

        ApfConfiguration config = getDefaultConfig();
        config.ieee802_3Filter = DROP_802_3_FRAMES;
        TestApfFilter apfFilter = new TestApfFilter(mContext, config, ipClientCallback,
                mNetworkQuirkMetrics);
        apfFilter.setLinkProperties(lp);

        byte[] program = ipClientCallback.assertProgramUpdateAndGet();

        // Construct IPv4 and IPv6 multicast packets.
        ByteBuffer mcastv4packet = makeIpv4Packet(IPPROTO_UDP);
        put(mcastv4packet, IPV4_DEST_ADDR_OFFSET, multicastIpv4Addr);

        ByteBuffer mcastv6packet = makeIpv6Packet(IPPROTO_UDP);
        put(mcastv6packet, IPV6_DEST_ADDR_OFFSET, multicastIpv6Addr);

        // Construct IPv4 broadcast packet.
        ByteBuffer bcastv4packet1 = makeIpv4Packet(IPPROTO_UDP);
        bcastv4packet1.put(ETH_BROADCAST_MAC_ADDRESS);
        bcastv4packet1.putShort(ETH_ETHERTYPE_OFFSET, (short)ETH_P_IP);
        put(bcastv4packet1, IPV4_DEST_ADDR_OFFSET, multicastIpv4Addr);

        ByteBuffer bcastv4packet2 = makeIpv4Packet(IPPROTO_UDP);
        bcastv4packet2.put(ETH_BROADCAST_MAC_ADDRESS);
        bcastv4packet2.putShort(ETH_ETHERTYPE_OFFSET, (short)ETH_P_IP);
        put(bcastv4packet2, IPV4_DEST_ADDR_OFFSET, IPV4_BROADCAST_ADDRESS);

        // Construct IPv4 broadcast with L2 unicast address packet (b/30231088).
        ByteBuffer bcastv4unicastl2packet = makeIpv4Packet(IPPROTO_UDP);
        bcastv4unicastl2packet.put(TestApfFilter.MOCK_MAC_ADDR);
        bcastv4unicastl2packet.putShort(ETH_ETHERTYPE_OFFSET, (short)ETH_P_IP);
        put(bcastv4unicastl2packet, IPV4_DEST_ADDR_OFFSET, broadcastIpv4Addr);

        // Verify initially disabled multicast filter is off
        assertPass(program, mcastv4packet.array());
        assertPass(program, mcastv6packet.array());
        assertPass(program, bcastv4packet1.array());
        assertPass(program, bcastv4packet2.array());
        assertPass(program, bcastv4unicastl2packet.array());

        // Turn on multicast filter and verify it works
        ipClientCallback.resetApfProgramWait();
        apfFilter.setMulticastFilter(true);
        program = ipClientCallback.assertProgramUpdateAndGet();
        assertDrop(program, mcastv4packet.array());
        assertDrop(program, mcastv6packet.array());
        assertDrop(program, bcastv4packet1.array());
        assertDrop(program, bcastv4packet2.array());
        assertDrop(program, bcastv4unicastl2packet.array());

        // Turn off multicast filter and verify it's off
        ipClientCallback.resetApfProgramWait();
        apfFilter.setMulticastFilter(false);
        program = ipClientCallback.assertProgramUpdateAndGet();
        assertPass(program, mcastv4packet.array());
        assertPass(program, mcastv6packet.array());
        assertPass(program, bcastv4packet1.array());
        assertPass(program, bcastv4packet2.array());
        assertPass(program, bcastv4unicastl2packet.array());

        // Verify it can be initialized to on
        ipClientCallback.resetApfProgramWait();
        apfFilter.shutdown();
        config.multicastFilter = DROP_MULTICAST;
        config.ieee802_3Filter = DROP_802_3_FRAMES;
        apfFilter = new TestApfFilter(mContext, config, ipClientCallback, mNetworkQuirkMetrics);
        apfFilter.setLinkProperties(lp);
        program = ipClientCallback.assertProgramUpdateAndGet();
        assertDrop(program, mcastv4packet.array());
        assertDrop(program, mcastv6packet.array());
        assertDrop(program, bcastv4packet1.array());
        assertDrop(program, bcastv4unicastl2packet.array());

        // Verify that ICMPv6 multicast is not dropped.
        mcastv6packet.put(IPV6_NEXT_HEADER_OFFSET, (byte)IPPROTO_ICMPV6);
        assertPass(program, mcastv6packet.array());

        apfFilter.shutdown();
    }

    @Test
    public void testApfFilterMulticastPingWhileDozing() throws Exception {
        doTestApfFilterMulticastPingWhileDozing(false /* isLightDozing */);
    }

    @Test
    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testApfFilterMulticastPingWhileLightDozing() throws Exception {
        doTestApfFilterMulticastPingWhileDozing(true /* isLightDozing */);
    }

    @Test
    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testShouldHandleLightDozeKillSwitch() throws Exception {
        final MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        final ApfConfiguration configuration = getDefaultConfig();
        configuration.shouldHandleLightDoze = false;
        final ApfFilter apfFilter = TestApfFilter.createTestApfFilter(mContext, ipClientCallback,
                configuration, mNetworkQuirkMetrics, mDependencies);
        final ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mDependencies).addDeviceIdleReceiver(receiverCaptor.capture(), anyBoolean());
        final BroadcastReceiver receiver = receiverCaptor.getValue();
        doReturn(true).when(mPowerManager).isDeviceLightIdleMode();
        receiver.onReceive(mContext, new Intent(ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED));
        assertFalse(apfFilter.isInDozeMode());
    }

    private void doTestApfFilterMulticastPingWhileDozing(boolean isLightDozing) throws Exception {
        final MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        final ApfConfiguration configuration = getDefaultConfig();
        configuration.shouldHandleLightDoze = true;
        final ApfFilter apfFilter = TestApfFilter.createTestApfFilter(mContext, ipClientCallback,
                configuration, mNetworkQuirkMetrics, mDependencies);
        final ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mDependencies).addDeviceIdleReceiver(receiverCaptor.capture(), anyBoolean());
        final BroadcastReceiver receiver = receiverCaptor.getValue();

        // Construct a multicast ICMPv6 ECHO request.
        final byte[] multicastIpv6Addr = {(byte)0xff,2,0,0,0,0,0,0,0,0,0,0,0,0,0,(byte)0xfb};
        final ByteBuffer packet = makeIpv6Packet(IPPROTO_ICMPV6);
        packet.put(ICMP6_TYPE_OFFSET, (byte)ICMPV6_ECHO_REQUEST_TYPE);
        put(packet, IPV6_DEST_ADDR_OFFSET, multicastIpv6Addr);

        // Normally, we let multicast pings alone...
        assertPass(ipClientCallback.assertProgramUpdateAndGet(), packet.array());

        if (isLightDozing) {
            doReturn(true).when(mPowerManager).isDeviceLightIdleMode();
            receiver.onReceive(mContext, new Intent(ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED));
        } else {
            doReturn(true).when(mPowerManager).isDeviceIdleMode();
            receiver.onReceive(mContext, new Intent(ACTION_DEVICE_IDLE_MODE_CHANGED));
        }
        // ...and even while dozing...
        assertPass(ipClientCallback.assertProgramUpdateAndGet(), packet.array());

        // ...but when the multicast filter is also enabled, drop the multicast pings to save power.
        apfFilter.setMulticastFilter(true);
        assertDrop(ipClientCallback.assertProgramUpdateAndGet(), packet.array());

        // However, we should still let through all other ICMPv6 types.
        ByteBuffer raPacket = ByteBuffer.wrap(packet.array().clone());
        setIpv6VersionFields(packet);
        packet.put(IPV6_NEXT_HEADER_OFFSET, (byte) IPPROTO_ICMPV6);
        raPacket.put(ICMP6_TYPE_OFFSET, (byte) NetworkStackConstants.ICMPV6_ROUTER_ADVERTISEMENT);
        assertPass(ipClientCallback.assertProgramUpdateAndGet(), raPacket.array());

        // Now wake up from doze mode to ensure that we no longer drop the packets.
        // (The multicast filter is still enabled at this point).
        if (isLightDozing) {
            doReturn(false).when(mPowerManager).isDeviceLightIdleMode();
            receiver.onReceive(mContext, new Intent(ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED));
        } else {
            doReturn(false).when(mPowerManager).isDeviceIdleMode();
            receiver.onReceive(mContext, new Intent(ACTION_DEVICE_IDLE_MODE_CHANGED));
        }
        assertPass(ipClientCallback.assertProgramUpdateAndGet(), packet.array());

        apfFilter.shutdown();
    }

    @Test
    public void testApfFilter802_3() throws Exception {
        MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        ApfConfiguration config = getDefaultConfig();
        ApfFilter apfFilter = TestApfFilter.createTestApfFilter(mContext, ipClientCallback, config,
                mNetworkQuirkMetrics, mDependencies);
        byte[] program = ipClientCallback.assertProgramUpdateAndGet();

        // Verify empty packet of 100 zero bytes is passed
        // Note that eth-type = 0 makes it an IEEE802.3 frame
        ByteBuffer packet = ByteBuffer.wrap(new byte[100]);
        assertPass(program, packet.array());

        // Verify empty packet with IPv4 is passed
        setIpv4VersionFields(packet);
        assertPass(program, packet.array());

        // Verify empty IPv6 packet is passed
        setIpv6VersionFields(packet);
        assertPass(program, packet.array());

        // Now turn on the filter
        ipClientCallback.resetApfProgramWait();
        apfFilter.shutdown();
        config.ieee802_3Filter = DROP_802_3_FRAMES;
        apfFilter = TestApfFilter.createTestApfFilter(mContext, ipClientCallback, config,
                mNetworkQuirkMetrics, mDependencies);
        program = ipClientCallback.assertProgramUpdateAndGet();

        // Verify that IEEE802.3 frame is dropped
        // In this case ethtype is used for payload length
        packet.putShort(ETH_ETHERTYPE_OFFSET, (short)(100 - 14));
        assertDrop(program, packet.array());

        // Verify that IPv4 (as example of Ethernet II) frame will pass
        setIpv4VersionFields(packet);
        assertPass(program, packet.array());

        // Verify that IPv6 (as example of Ethernet II) frame will pass
        setIpv6VersionFields(packet);
        assertPass(program, packet.array());

        apfFilter.shutdown();
    }

    @Test
    public void testApfFilterEthTypeBL() throws Exception {
        final int[] emptyBlackList = {};
        final int[] ipv4BlackList = {ETH_P_IP};
        final int[] ipv4Ipv6BlackList = {ETH_P_IP, ETH_P_IPV6};

        MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        ApfConfiguration config = getDefaultConfig();
        ApfFilter apfFilter = TestApfFilter.createTestApfFilter(mContext, ipClientCallback, config,
                mNetworkQuirkMetrics, mDependencies);
        byte[] program = ipClientCallback.assertProgramUpdateAndGet();

        // Verify empty packet of 100 zero bytes is passed
        // Note that eth-type = 0 makes it an IEEE802.3 frame
        ByteBuffer packet = ByteBuffer.wrap(new byte[100]);
        assertPass(program, packet.array());

        // Verify empty packet with IPv4 is passed
        setIpv4VersionFields(packet);
        assertPass(program, packet.array());

        // Verify empty IPv6 packet is passed
        setIpv6VersionFields(packet);
        assertPass(program, packet.array());

        // Now add IPv4 to the black list
        ipClientCallback.resetApfProgramWait();
        apfFilter.shutdown();
        config.ethTypeBlackList = ipv4BlackList;
        apfFilter = TestApfFilter.createTestApfFilter(mContext, ipClientCallback, config,
                mNetworkQuirkMetrics, mDependencies);
        program = ipClientCallback.assertProgramUpdateAndGet();

        // Verify that IPv4 frame will be dropped
        setIpv4VersionFields(packet);
        assertDrop(program, packet.array());

        // Verify that IPv6 frame will pass
        setIpv6VersionFields(packet);
        assertPass(program, packet.array());

        // Now let us have both IPv4 and IPv6 in the black list
        ipClientCallback.resetApfProgramWait();
        apfFilter.shutdown();
        config.ethTypeBlackList = ipv4Ipv6BlackList;
        apfFilter = TestApfFilter.createTestApfFilter(mContext, ipClientCallback, config,
                mNetworkQuirkMetrics, mDependencies);
        program = ipClientCallback.assertProgramUpdateAndGet();

        // Verify that IPv4 frame will be dropped
        setIpv4VersionFields(packet);
        assertDrop(program, packet.array());

        // Verify that IPv6 frame will be dropped
        setIpv6VersionFields(packet);
        assertDrop(program, packet.array());

        apfFilter.shutdown();
    }

    private byte[] getProgram(MockIpClientCallback cb, ApfFilter filter, LinkProperties lp) {
        cb.resetApfProgramWait();
        filter.setLinkProperties(lp);
        return cb.assertProgramUpdateAndGet();
    }

    private void verifyArpFilter(byte[] program, int filterResult) {
        // Verify ARP request packet
        assertPass(program, arpRequestBroadcast(MOCK_IPV4_ADDR));
        assertVerdict(filterResult, program, arpRequestBroadcast(ANOTHER_IPV4_ADDR));
        assertDrop(program, arpRequestBroadcast(IPV4_ANY_HOST_ADDR));

        // Verify ARP reply packets from different source ip
        assertDrop(program, arpReply(IPV4_ANY_HOST_ADDR, IPV4_ANY_HOST_ADDR));
        assertPass(program, arpReply(ANOTHER_IPV4_SOURCE_ADDR, IPV4_ANY_HOST_ADDR));
        assertPass(program, arpReply(BUG_PROBE_SOURCE_ADDR1, IPV4_ANY_HOST_ADDR));
        assertPass(program, arpReply(BUG_PROBE_SOURCE_ADDR2, IPV4_ANY_HOST_ADDR));

        // Verify unicast ARP reply packet is always accepted.
        assertPass(program, arpReply(IPV4_SOURCE_ADDR, MOCK_IPV4_ADDR));
        assertPass(program, arpReply(IPV4_SOURCE_ADDR, ANOTHER_IPV4_ADDR));
        assertPass(program, arpReply(IPV4_SOURCE_ADDR, IPV4_ANY_HOST_ADDR));

        // Verify GARP reply packets are always filtered
        assertDrop(program, garpReply());
    }

    @Test
    public void testApfFilterArp() throws Exception {
        MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        ApfConfiguration config = getDefaultConfig();
        config.multicastFilter = DROP_MULTICAST;
        config.ieee802_3Filter = DROP_802_3_FRAMES;
        TestApfFilter apfFilter = new TestApfFilter(mContext, config, ipClientCallback,
                mNetworkQuirkMetrics);

        // Verify initially ARP request filter is off, and GARP filter is on.
        verifyArpFilter(ipClientCallback.assertProgramUpdateAndGet(), PASS);

        // Inform ApfFilter of our address and verify ARP filtering is on
        LinkAddress linkAddress = new LinkAddress(InetAddress.getByAddress(MOCK_IPV4_ADDR), 24);
        LinkProperties lp = new LinkProperties();
        assertTrue(lp.addLinkAddress(linkAddress));
        verifyArpFilter(getProgram(ipClientCallback, apfFilter, lp), DROP);

        // Inform ApfFilter of loss of IP and verify ARP filtering is off
        verifyArpFilter(getProgram(ipClientCallback, apfFilter, new LinkProperties()), PASS);

        apfFilter.shutdown();
    }

    private static byte[] arpReply(byte[] sip, byte[] tip) {
        ByteBuffer packet = ByteBuffer.wrap(new byte[100]);
        packet.putShort(ETH_ETHERTYPE_OFFSET, (short)ETH_P_ARP);
        put(packet, ARP_HEADER_OFFSET, ARP_IPV4_REPLY_HEADER);
        put(packet, ARP_SOURCE_IP_ADDRESS_OFFSET, sip);
        put(packet, ARP_TARGET_IP_ADDRESS_OFFSET, tip);
        return packet.array();
    }

    private static byte[] arpRequestBroadcast(byte[] tip) {
        ByteBuffer packet = ByteBuffer.wrap(new byte[100]);
        packet.putShort(ETH_ETHERTYPE_OFFSET, (short)ETH_P_ARP);
        put(packet, ETH_DEST_ADDR_OFFSET, ETH_BROADCAST_MAC_ADDRESS);
        put(packet, ARP_HEADER_OFFSET, ARP_IPV4_REQUEST_HEADER);
        put(packet, ARP_TARGET_IP_ADDRESS_OFFSET, tip);
        return packet.array();
    }

    private static byte[] garpReply() {
        ByteBuffer packet = ByteBuffer.wrap(new byte[100]);
        packet.putShort(ETH_ETHERTYPE_OFFSET, (short)ETH_P_ARP);
        put(packet, ETH_DEST_ADDR_OFFSET, ETH_BROADCAST_MAC_ADDRESS);
        put(packet, ARP_HEADER_OFFSET, ARP_IPV4_REPLY_HEADER);
        put(packet, ARP_TARGET_IP_ADDRESS_OFFSET, IPV4_ANY_HOST_ADDR);
        return packet.array();
    }

    private static final byte[] IPV4_KEEPALIVE_SRC_ADDR = {10, 0, 0, 5};
    private static final byte[] IPV4_KEEPALIVE_DST_ADDR = {10, 0, 0, 6};
    private static final byte[] IPV4_ANOTHER_ADDR = {10, 0 , 0, 7};
    private static final byte[] IPV6_KEEPALIVE_SRC_ADDR =
            {(byte) 0x24, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xfa, (byte) 0xf1};
    private static final byte[] IPV6_KEEPALIVE_DST_ADDR =
            {(byte) 0x24, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xfa, (byte) 0xf2};
    private static final byte[] IPV6_ANOTHER_ADDR =
            {(byte) 0x24, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xfa, (byte) 0xf5};

    @Test
    public void testApfFilterKeepaliveAck() throws Exception {
        final MockIpClientCallback cb = new MockIpClientCallback();
        final ApfConfiguration config = getDefaultConfig();
        config.multicastFilter = DROP_MULTICAST;
        config.ieee802_3Filter = DROP_802_3_FRAMES;
        final TestApfFilter apfFilter = new TestApfFilter(mContext, config, cb,
                mNetworkQuirkMetrics);
        byte[] program;
        final int srcPort = 12345;
        final int dstPort = 54321;
        final int seqNum = 2123456789;
        final int ackNum = 1234567890;
        final int anotherSrcPort = 23456;
        final int anotherDstPort = 65432;
        final int anotherSeqNum = 2123456780;
        final int anotherAckNum = 1123456789;
        final int slot1 = 1;
        final int slot2 = 2;
        final int window = 14480;
        final int windowScale = 4;

        // src: 10.0.0.5, port: 12345
        // dst: 10.0.0.6, port: 54321
        InetAddress srcAddr = InetAddress.getByAddress(IPV4_KEEPALIVE_SRC_ADDR);
        InetAddress dstAddr = InetAddress.getByAddress(IPV4_KEEPALIVE_DST_ADDR);

        final TcpKeepalivePacketDataParcelable parcel = new TcpKeepalivePacketDataParcelable();
        parcel.srcAddress = srcAddr.getAddress();
        parcel.srcPort = srcPort;
        parcel.dstAddress = dstAddr.getAddress();
        parcel.dstPort = dstPort;
        parcel.seq = seqNum;
        parcel.ack = ackNum;

        apfFilter.addTcpKeepalivePacketFilter(slot1, parcel);
        program = cb.assertProgramUpdateAndGet();

        // Verify IPv4 keepalive ack packet is dropped
        // src: 10.0.0.6, port: 54321
        // dst: 10.0.0.5, port: 12345
        assertDrop(program,
                ipv4TcpPacket(IPV4_KEEPALIVE_DST_ADDR, IPV4_KEEPALIVE_SRC_ADDR,
                        dstPort, srcPort, ackNum, seqNum + 1, 0 /* dataLength */));
        // Verify IPv4 non-keepalive ack packet from the same source address is passed
        assertPass(program,
                ipv4TcpPacket(IPV4_KEEPALIVE_DST_ADDR, IPV4_KEEPALIVE_SRC_ADDR,
                        dstPort, srcPort, ackNum + 100, seqNum, 0 /* dataLength */));
        assertPass(program,
                ipv4TcpPacket(IPV4_KEEPALIVE_DST_ADDR, IPV4_KEEPALIVE_SRC_ADDR,
                        dstPort, srcPort, ackNum, seqNum + 1, 10 /* dataLength */));
        // Verify IPv4 packet from another address is passed
        assertPass(program,
                ipv4TcpPacket(IPV4_ANOTHER_ADDR, IPV4_KEEPALIVE_SRC_ADDR, anotherSrcPort,
                        anotherDstPort, anotherSeqNum, anotherAckNum, 0 /* dataLength */));

        // Remove IPv4 keepalive filter
        apfFilter.removeKeepalivePacketFilter(slot1);

        try {
            // src: 2404:0:0:0:0:0:faf1, port: 12345
            // dst: 2404:0:0:0:0:0:faf2, port: 54321
            srcAddr = InetAddress.getByAddress(IPV6_KEEPALIVE_SRC_ADDR);
            dstAddr = InetAddress.getByAddress(IPV6_KEEPALIVE_DST_ADDR);

            final TcpKeepalivePacketDataParcelable ipv6Parcel =
                    new TcpKeepalivePacketDataParcelable();
            ipv6Parcel.srcAddress = srcAddr.getAddress();
            ipv6Parcel.srcPort = srcPort;
            ipv6Parcel.dstAddress = dstAddr.getAddress();
            ipv6Parcel.dstPort = dstPort;
            ipv6Parcel.seq = seqNum;
            ipv6Parcel.ack = ackNum;

            apfFilter.addTcpKeepalivePacketFilter(slot1, ipv6Parcel);
            program = cb.assertProgramUpdateAndGet();

            // Verify IPv6 keepalive ack packet is dropped
            // src: 2404:0:0:0:0:0:faf2, port: 54321
            // dst: 2404:0:0:0:0:0:faf1, port: 12345
            assertDrop(program,
                    ipv6TcpPacket(IPV6_KEEPALIVE_DST_ADDR, IPV6_KEEPALIVE_SRC_ADDR,
                            dstPort, srcPort, ackNum, seqNum + 1));
            // Verify IPv6 non-keepalive ack packet from the same source address is passed
            assertPass(program,
                    ipv6TcpPacket(IPV6_KEEPALIVE_DST_ADDR, IPV6_KEEPALIVE_SRC_ADDR,
                            dstPort, srcPort, ackNum + 100, seqNum));
            // Verify IPv6 packet from another address is passed
            assertPass(program,
                    ipv6TcpPacket(IPV6_ANOTHER_ADDR, IPV6_KEEPALIVE_SRC_ADDR, anotherSrcPort,
                            anotherDstPort, anotherSeqNum, anotherAckNum));

            // Remove IPv6 keepalive filter
            apfFilter.removeKeepalivePacketFilter(slot1);

            // Verify multiple filters
            apfFilter.addTcpKeepalivePacketFilter(slot1, parcel);
            apfFilter.addTcpKeepalivePacketFilter(slot2, ipv6Parcel);
            program = cb.assertProgramUpdateAndGet();

            // Verify IPv4 keepalive ack packet is dropped
            // src: 10.0.0.6, port: 54321
            // dst: 10.0.0.5, port: 12345
            assertDrop(program,
                    ipv4TcpPacket(IPV4_KEEPALIVE_DST_ADDR, IPV4_KEEPALIVE_SRC_ADDR,
                            dstPort, srcPort, ackNum, seqNum + 1, 0 /* dataLength */));
            // Verify IPv4 non-keepalive ack packet from the same source address is passed
            assertPass(program,
                    ipv4TcpPacket(IPV4_KEEPALIVE_DST_ADDR, IPV4_KEEPALIVE_SRC_ADDR,
                            dstPort, srcPort, ackNum + 100, seqNum, 0 /* dataLength */));
            // Verify IPv4 packet from another address is passed
            assertPass(program,
                    ipv4TcpPacket(IPV4_ANOTHER_ADDR, IPV4_KEEPALIVE_SRC_ADDR, anotherSrcPort,
                            anotherDstPort, anotherSeqNum, anotherAckNum, 0 /* dataLength */));

            // Verify IPv6 keepalive ack packet is dropped
            // src: 2404:0:0:0:0:0:faf2, port: 54321
            // dst: 2404:0:0:0:0:0:faf1, port: 12345
            assertDrop(program,
                    ipv6TcpPacket(IPV6_KEEPALIVE_DST_ADDR, IPV6_KEEPALIVE_SRC_ADDR,
                            dstPort, srcPort, ackNum, seqNum + 1));
            // Verify IPv6 non-keepalive ack packet from the same source address is passed
            assertPass(program,
                    ipv6TcpPacket(IPV6_KEEPALIVE_DST_ADDR, IPV6_KEEPALIVE_SRC_ADDR,
                            dstPort, srcPort, ackNum + 100, seqNum));
            // Verify IPv6 packet from another address is passed
            assertPass(program,
                    ipv6TcpPacket(IPV6_ANOTHER_ADDR, IPV6_KEEPALIVE_SRC_ADDR, anotherSrcPort,
                            anotherDstPort, anotherSeqNum, anotherAckNum));

            // Remove keepalive filters
            apfFilter.removeKeepalivePacketFilter(slot1);
            apfFilter.removeKeepalivePacketFilter(slot2);
        } catch (UnsupportedOperationException e) {
            // TODO: support V6 packets
        }

        program = cb.assertProgramUpdateAndGet();

        // Verify IPv4, IPv6 packets are passed
        assertPass(program,
                ipv4TcpPacket(IPV4_KEEPALIVE_DST_ADDR, IPV4_KEEPALIVE_SRC_ADDR,
                        dstPort, srcPort, ackNum, seqNum + 1, 0 /* dataLength */));
        assertPass(program,
                ipv6TcpPacket(IPV6_KEEPALIVE_DST_ADDR, IPV6_KEEPALIVE_SRC_ADDR,
                        dstPort, srcPort, ackNum, seqNum + 1));
        assertPass(program,
                ipv4TcpPacket(IPV4_ANOTHER_ADDR, IPV4_KEEPALIVE_SRC_ADDR, srcPort,
                        dstPort, anotherSeqNum, anotherAckNum, 0 /* dataLength */));
        assertPass(program,
                ipv6TcpPacket(IPV6_ANOTHER_ADDR, IPV6_KEEPALIVE_SRC_ADDR, srcPort,
                        dstPort, anotherSeqNum, anotherAckNum));

        apfFilter.shutdown();
    }

    private static byte[] ipv4TcpPacket(byte[] sip, byte[] dip, int sport,
            int dport, int seq, int ack, int dataLength) {
        final int totalLength = dataLength + IPV4_HEADER_LEN + IPV4_TCP_HEADER_LEN;

        ByteBuffer packet = ByteBuffer.wrap(new byte[totalLength + ETH_HEADER_LEN]);

        // Ethertype and IPv4 header
        setIpv4VersionFields(packet);
        packet.putShort(IPV4_TOTAL_LENGTH_OFFSET, (short) totalLength);
        packet.put(IPV4_PROTOCOL_OFFSET, (byte) IPPROTO_TCP);
        put(packet, IPV4_SRC_ADDR_OFFSET, sip);
        put(packet, IPV4_DEST_ADDR_OFFSET, dip);
        packet.putShort(IPV4_TCP_SRC_PORT_OFFSET, (short) sport);
        packet.putShort(IPV4_TCP_DEST_PORT_OFFSET, (short) dport);
        packet.putInt(IPV4_TCP_SEQ_NUM_OFFSET, seq);
        packet.putInt(IPV4_TCP_ACK_NUM_OFFSET, ack);

        // TCP header length 5(20 bytes), reserved 3 bits, NS=0
        packet.put(IPV4_TCP_HEADER_LENGTH_OFFSET, (byte) 0x50);
        // TCP flags: ACK set
        packet.put(IPV4_TCP_HEADER_FLAG_OFFSET, (byte) 0x10);
        return packet.array();
    }

    private static byte[] ipv6TcpPacket(byte[] sip, byte[] tip, int sport,
            int dport, int seq, int ack) {
        ByteBuffer packet = ByteBuffer.wrap(new byte[100]);
        setIpv6VersionFields(packet);
        packet.put(IPV6_NEXT_HEADER_OFFSET, (byte) IPPROTO_TCP);
        put(packet, IPV6_SRC_ADDR_OFFSET, sip);
        put(packet, IPV6_DEST_ADDR_OFFSET, tip);
        packet.putShort(IPV6_TCP_SRC_PORT_OFFSET, (short) sport);
        packet.putShort(IPV6_TCP_DEST_PORT_OFFSET, (short) dport);
        packet.putInt(IPV6_TCP_SEQ_NUM_OFFSET, seq);
        packet.putInt(IPV6_TCP_ACK_NUM_OFFSET, ack);
        return packet.array();
    }

    @Test
    public void testApfFilterNattKeepalivePacket() throws Exception {
        final MockIpClientCallback cb = new MockIpClientCallback();
        final ApfConfiguration config = getDefaultConfig();
        config.multicastFilter = DROP_MULTICAST;
        config.ieee802_3Filter = DROP_802_3_FRAMES;
        final TestApfFilter apfFilter = new TestApfFilter(mContext, config, cb,
                mNetworkQuirkMetrics);
        byte[] program;
        final int srcPort = 1024;
        final int dstPort = 4500;
        final int slot1 = 1;
        // NAT-T keepalive
        final byte[] kaPayload = {(byte) 0xff};
        final byte[] nonKaPayload = {(byte) 0xfe};

        // src: 10.0.0.5, port: 1024
        // dst: 10.0.0.6, port: 4500
        InetAddress srcAddr = InetAddress.getByAddress(IPV4_KEEPALIVE_SRC_ADDR);
        InetAddress dstAddr = InetAddress.getByAddress(IPV4_KEEPALIVE_DST_ADDR);

        final NattKeepalivePacketDataParcelable parcel = new NattKeepalivePacketDataParcelable();
        parcel.srcAddress = srcAddr.getAddress();
        parcel.srcPort = srcPort;
        parcel.dstAddress = dstAddr.getAddress();
        parcel.dstPort = dstPort;

        apfFilter.addNattKeepalivePacketFilter(slot1, parcel);
        program = cb.assertProgramUpdateAndGet();

        // Verify IPv4 keepalive packet is dropped
        // src: 10.0.0.6, port: 4500
        // dst: 10.0.0.5, port: 1024
        byte[] pkt = ipv4UdpPacket(IPV4_KEEPALIVE_DST_ADDR,
                    IPV4_KEEPALIVE_SRC_ADDR, dstPort, srcPort, 1 /* dataLength */);
        System.arraycopy(kaPayload, 0, pkt, IPV4_UDP_PAYLOAD_OFFSET, kaPayload.length);
        assertDrop(program, pkt);

        // Verify a packet with payload length 1 byte but it is not 0xff will pass the filter.
        System.arraycopy(nonKaPayload, 0, pkt, IPV4_UDP_PAYLOAD_OFFSET, nonKaPayload.length);
        assertPass(program, pkt);

        // Verify IPv4 non-keepalive response packet from the same source address is passed
        assertPass(program,
                ipv4UdpPacket(IPV4_KEEPALIVE_DST_ADDR, IPV4_KEEPALIVE_SRC_ADDR,
                        dstPort, srcPort, 10 /* dataLength */));

        // Verify IPv4 non-keepalive response packet from other source address is passed
        assertPass(program,
                ipv4UdpPacket(IPV4_ANOTHER_ADDR, IPV4_KEEPALIVE_SRC_ADDR,
                        dstPort, srcPort, 10 /* dataLength */));

        apfFilter.removeKeepalivePacketFilter(slot1);
        apfFilter.shutdown();
    }

    private static byte[] ipv4UdpPacket(byte[] sip, byte[] dip, int sport,
            int dport, int dataLength) {
        final int totalLength = dataLength + IPV4_HEADER_LEN + UDP_HEADER_LEN;
        final int udpLength = UDP_HEADER_LEN + dataLength;
        ByteBuffer packet = ByteBuffer.wrap(new byte[totalLength + ETH_HEADER_LEN]);

        // Ethertype and IPv4 header
        setIpv4VersionFields(packet);
        packet.putShort(IPV4_TOTAL_LENGTH_OFFSET, (short) totalLength);
        packet.put(IPV4_PROTOCOL_OFFSET, (byte) IPPROTO_UDP);
        put(packet, IPV4_SRC_ADDR_OFFSET, sip);
        put(packet, IPV4_DEST_ADDR_OFFSET, dip);
        packet.putShort(IPV4_UDP_SRC_PORT_OFFSET, (short) sport);
        packet.putShort(IPV4_UDP_DEST_PORT_OFFSET, (short) dport);
        packet.putShort(IPV4_UDP_LENGTH_OFFSET, (short) udpLength);

        return packet.array();
    }

    private static class RaPacketBuilder {
        final ByteArrayOutputStream mPacket = new ByteArrayOutputStream();
        int mFlowLabel = 0x12345;
        int mReachableTime = 30_000;
        int mRetransmissionTimer = 1000;

        public RaPacketBuilder(int routerLft) throws Exception {
            InetAddress src = InetAddress.getByName("fe80::1234:abcd");
            ByteBuffer buffer = ByteBuffer.allocate(ICMP6_RA_OPTION_OFFSET);

            buffer.putShort(ETH_ETHERTYPE_OFFSET, (short) ETH_P_IPV6);
            buffer.position(ETH_HEADER_LEN);

            // skip version, tclass, flowlabel; set in build()
            buffer.position(buffer.position() + 4);

            buffer.putShort((short) 0);                     // Payload length; updated later
            buffer.put((byte) IPPROTO_ICMPV6);              // Next header
            buffer.put((byte) 0xff);                        // Hop limit
            buffer.put(src.getAddress());                   // Source address
            buffer.put(IPV6_ALL_NODES_ADDRESS);             // Destination address

            buffer.put((byte) ICMP6_ROUTER_ADVERTISEMENT);  // Type
            buffer.put((byte) 0);                           // Code (0)
            buffer.putShort((short) 0);                     // Checksum (ignored)
            buffer.put((byte) 64);                          // Hop limit
            buffer.put((byte) 0);                           // M/O, reserved
            buffer.putShort((short) routerLft);             // Router lifetime
            // skip reachable time; set in build()
            // skip retransmission timer; set in build();

            mPacket.write(buffer.array(), 0, buffer.capacity());
        }

        public RaPacketBuilder setFlowLabel(int flowLabel) {
            mFlowLabel = flowLabel;
            return this;
        }

        public RaPacketBuilder setReachableTime(int reachable) {
            mReachableTime = reachable;
            return this;
        }

        public RaPacketBuilder setRetransmissionTimer(int retrans) {
            mRetransmissionTimer = retrans;
            return this;
        }

        public RaPacketBuilder addPioOption(int valid, int preferred, String prefixString)
                throws Exception {
            ByteBuffer buffer = ByteBuffer.allocate(ICMP6_PREFIX_OPTION_LEN);

            IpPrefix prefix = new IpPrefix(prefixString);
            buffer.put((byte) ICMP6_PREFIX_OPTION_TYPE);  // Type
            buffer.put((byte) 4);                         // Length in 8-byte units
            buffer.put((byte) prefix.getPrefixLength());  // Prefix length
            buffer.put((byte) 0b11000000);                // L = 1, A = 1
            buffer.putInt(valid);
            buffer.putInt(preferred);
            buffer.putInt(0);                             // Reserved
            buffer.put(prefix.getRawAddress());

            mPacket.write(buffer.array(), 0, buffer.capacity());
            return this;
        }

        public RaPacketBuilder addRioOption(int lifetime, String prefixString) throws Exception {
            IpPrefix prefix = new IpPrefix(prefixString);

            int optionLength;
            if (prefix.getPrefixLength() == 0) {
                optionLength = 1;
            } else if (prefix.getPrefixLength() <= 64) {
                optionLength = 2;
            } else {
                optionLength = 3;
            }

            ByteBuffer buffer = ByteBuffer.allocate(optionLength * 8);

            buffer.put((byte) ICMP6_ROUTE_INFO_OPTION_TYPE);  // Type
            buffer.put((byte) optionLength);                  // Length in 8-byte units
            buffer.put((byte) prefix.getPrefixLength());      // Prefix length
            buffer.put((byte) 0b00011000);                    // Pref = high
            buffer.putInt(lifetime);                          // Lifetime

            byte[] prefixBytes = prefix.getRawAddress();
            buffer.put(prefixBytes, 0, (optionLength - 1) * 8);

            mPacket.write(buffer.array(), 0, buffer.capacity());
            return this;
        }

        public RaPacketBuilder addDnsslOption(int lifetime, String... domains) {
            ByteArrayOutputStream dnssl = new ByteArrayOutputStream();
            for (String domain : domains) {
                for (String label : domain.split(".")) {
                    final byte[] bytes = label.getBytes(StandardCharsets.UTF_8);
                    dnssl.write((byte) bytes.length);
                    dnssl.write(bytes, 0, bytes.length);
                }
                dnssl.write((byte) 0);
            }

            // Extend with 0s to make it 8-byte aligned.
            while (dnssl.size() % 8 != 0) {
                dnssl.write((byte) 0);
            }

            final int length = ICMP6_4_BYTE_OPTION_LEN + dnssl.size();
            ByteBuffer buffer = ByteBuffer.allocate(length);

            buffer.put((byte) ICMP6_DNSSL_OPTION_TYPE);  // Type
            buffer.put((byte) (length / 8));             // Length
            // skip past reserved bytes
            buffer.position(buffer.position() + 2);
            buffer.putInt(lifetime);                     // Lifetime
            buffer.put(dnssl.toByteArray());             // Domain names

            mPacket.write(buffer.array(), 0, buffer.capacity());
            return this;
        }

        public RaPacketBuilder addRdnssOption(int lifetime, String... servers) throws Exception {
            int optionLength = 1 + 2 * servers.length;   // In 8-byte units
            ByteBuffer buffer = ByteBuffer.allocate(optionLength * 8);

            buffer.put((byte) ICMP6_RDNSS_OPTION_TYPE);  // Type
            buffer.put((byte) optionLength);             // Length
            buffer.putShort((short) 0);                  // Reserved
            buffer.putInt(lifetime);                     // Lifetime
            for (String server : servers) {
                buffer.put(InetAddress.getByName(server).getAddress());
            }

            mPacket.write(buffer.array(), 0, buffer.capacity());
            return this;
        }

        public RaPacketBuilder addZeroLengthOption() throws Exception {
            ByteBuffer buffer = ByteBuffer.allocate(ICMP6_4_BYTE_OPTION_LEN);
            buffer.put((byte) ICMP6_PREFIX_OPTION_TYPE);
            buffer.put((byte) 0);

            mPacket.write(buffer.array(), 0, buffer.capacity());
            return this;
        }

        public byte[] build() {
            ByteBuffer buffer = ByteBuffer.wrap(mPacket.toByteArray());
            // IPv6, traffic class = 0, flow label = mFlowLabel
            buffer.putInt(IP_HEADER_OFFSET, 0x60000000 | (0xFFFFF & mFlowLabel));
            buffer.putShort(IPV6_PAYLOAD_LENGTH_OFFSET, (short) buffer.capacity());

            buffer.position(ICMP6_RA_REACHABLE_TIME_OFFSET);
            buffer.putInt(mReachableTime);
            buffer.putInt(mRetransmissionTimer);

            return buffer.array();
        }
    }

    private byte[] buildLargeRa() throws Exception {
        RaPacketBuilder builder = new RaPacketBuilder(1800 /* router lft */);

        builder.addRioOption(1200, "64:ff9b::/96");
        builder.addRdnssOption(7200, "2001:db8:1::1", "2001:db8:1::2");
        builder.addRioOption(2100, "2000::/3");
        builder.addRioOption(2400, "::/0");
        builder.addPioOption(600, 300, "2001:db8:a::/64");
        builder.addRioOption(1500, "2001:db8:c:d::/64");
        builder.addPioOption(86400, 43200, "fd95:d1e:12::/64");

        return builder.build();
    }

    @Test
    public void testRaToString() throws Exception {
        MockIpClientCallback cb = new MockIpClientCallback();
        ApfConfiguration config = getDefaultConfig();
        TestApfFilter apfFilter = new TestApfFilter(mContext, config, cb, mNetworkQuirkMetrics);

        byte[] packet = buildLargeRa();
        ApfFilter.Ra ra = apfFilter.new Ra(packet, packet.length);
        String expected = "RA fe80::1234:abcd -> ff02::1 1800s "
                + "2001:db8:a::/64 600s/300s fd95:d1e:12::/64 86400s/43200s "
                + "DNS 7200s 2001:db8:1::1 2001:db8:1::2 "
                + "RIO 1200s 64:ff9b::/96 RIO 2100s 2000::/3 "
                + "RIO 2400s ::/0 RIO 1500s 2001:db8:c:d::/64 ";
        assertEquals(expected, ra.toString());
    }

    // Verify that the last program pushed to the IpClient.Callback properly filters the
    // given packet for the given lifetime.
    private void verifyRaLifetime(byte[] program, ByteBuffer packet, int lifetime) {
        verifyRaLifetime(program, packet, lifetime, 0);
    }

    // Verify that the last program pushed to the IpClient.Callback properly filters the
    // given packet for the given lifetime and programInstallTime. programInstallTime is
    // the time difference between when RA is last seen and the program is installed.
    private void verifyRaLifetime(byte[] program, ByteBuffer packet, int lifetime,
            int programInstallTime) {
        final int FRACTION_OF_LIFETIME = 6;
        final int ageLimit = lifetime / FRACTION_OF_LIFETIME - programInstallTime;

        // Verify new program should drop RA for 1/6th its lifetime and pass afterwards.
        assertDrop(program, packet.array());
        assertDrop(program, packet.array(), ageLimit);
        assertPass(program, packet.array(), ageLimit + 1);
        assertPass(program, packet.array(), lifetime);
        // Verify RA checksum is ignored
        final short originalChecksum = packet.getShort(ICMP6_RA_CHECKSUM_OFFSET);
        packet.putShort(ICMP6_RA_CHECKSUM_OFFSET, (short)12345);
        assertDrop(program, packet.array());
        packet.putShort(ICMP6_RA_CHECKSUM_OFFSET, (short)-12345);
        assertDrop(program, packet.array());
        packet.putShort(ICMP6_RA_CHECKSUM_OFFSET, originalChecksum);

        // Verify other changes to RA (e.g., a change in the source address) make it not match.
        final int offset = IPV6_SRC_ADDR_OFFSET + 5;
        final byte originalByte = packet.get(offset);
        packet.put(offset, (byte) (~originalByte));
        assertPass(program, packet.array());
        packet.put(offset, originalByte);
        assertDrop(program, packet.array());
    }

    // Test that when ApfFilter is shown the given packet, it generates a program to filter it
    // for the given lifetime.
    private void verifyRaLifetime(TestApfFilter apfFilter, MockIpClientCallback ipClientCallback,
            ByteBuffer packet, int lifetime) throws IOException, ErrnoException {
        // Verify new program generated if ApfFilter witnesses RA
        apfFilter.pretendPacketReceived(packet.array());
        byte[] program = ipClientCallback.assertProgramUpdateAndGet();
        verifyRaLifetime(program, packet, lifetime);
    }

    private void assertInvalidRa(TestApfFilter apfFilter, MockIpClientCallback ipClientCallback,
            ByteBuffer packet) throws IOException, ErrnoException {
        apfFilter.pretendPacketReceived(packet.array());
        ipClientCallback.assertNoProgramUpdate();
    }

    @Test
    public void testApfFilterRa() throws Exception {
        MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        ApfConfiguration config = getDefaultConfig();
        config.multicastFilter = DROP_MULTICAST;
        config.ieee802_3Filter = DROP_802_3_FRAMES;
        TestApfFilter apfFilter = new TestApfFilter(mContext, config, ipClientCallback,
                mNetworkQuirkMetrics);
        byte[] program = ipClientCallback.assertProgramUpdateAndGet();

        final int ROUTER_LIFETIME = 1000;
        final int PREFIX_VALID_LIFETIME = 200;
        final int PREFIX_PREFERRED_LIFETIME = 100;
        final int RDNSS_LIFETIME  = 300;
        final int ROUTE_LIFETIME  = 400;
        // Note that lifetime of 2000 will be ignored in favor of shorter route lifetime of 1000.
        final int DNSSL_LIFETIME  = 2000;

        // Verify RA is passed the first time
        RaPacketBuilder ra = new RaPacketBuilder(ROUTER_LIFETIME);
        ByteBuffer basePacket = ByteBuffer.wrap(ra.build());
        assertPass(program, basePacket.array());

        verifyRaLifetime(apfFilter, ipClientCallback, basePacket, ROUTER_LIFETIME);

        ra = new RaPacketBuilder(ROUTER_LIFETIME);
        // Check that changes are ignored in every byte of the flow label.
        ra.setFlowLabel(0x56789);
        ByteBuffer newFlowLabelPacket = ByteBuffer.wrap(ra.build());

        // Ensure zero-length options cause the packet to be silently skipped.
        // Do this before we test other packets. http://b/29586253
        ra = new RaPacketBuilder(ROUTER_LIFETIME);
        ra.addZeroLengthOption();
        ByteBuffer zeroLengthOptionPacket = ByteBuffer.wrap(ra.build());
        assertInvalidRa(apfFilter, ipClientCallback, zeroLengthOptionPacket);

        // Generate several RAs with different options and lifetimes, and verify when
        // ApfFilter is shown these packets, it generates programs to filter them for the
        // appropriate lifetime.
        ra = new RaPacketBuilder(ROUTER_LIFETIME);
        ra.addPioOption(PREFIX_VALID_LIFETIME, PREFIX_PREFERRED_LIFETIME, "2001:db8::/64");
        ByteBuffer prefixOptionPacket = ByteBuffer.wrap(ra.build());
        verifyRaLifetime(
                apfFilter, ipClientCallback, prefixOptionPacket, PREFIX_PREFERRED_LIFETIME);

        ra = new RaPacketBuilder(ROUTER_LIFETIME);
        ra.addRdnssOption(RDNSS_LIFETIME, "2001:4860:4860::8888", "2001:4860:4860::8844");
        ByteBuffer rdnssOptionPacket = ByteBuffer.wrap(ra.build());
        verifyRaLifetime(apfFilter, ipClientCallback, rdnssOptionPacket, RDNSS_LIFETIME);

        final int lowLifetime = 60;
        ra = new RaPacketBuilder(ROUTER_LIFETIME);
        ra.addRdnssOption(lowLifetime, "2620:fe::9");
        ByteBuffer lowLifetimeRdnssOptionPacket = ByteBuffer.wrap(ra.build());
        verifyRaLifetime(apfFilter, ipClientCallback, lowLifetimeRdnssOptionPacket,
                ROUTER_LIFETIME);

        ra = new RaPacketBuilder(ROUTER_LIFETIME);
        ra.addRioOption(ROUTE_LIFETIME, "64:ff9b::/96");
        ByteBuffer routeInfoOptionPacket = ByteBuffer.wrap(ra.build());
        verifyRaLifetime(apfFilter, ipClientCallback, routeInfoOptionPacket, ROUTE_LIFETIME);

        // Check that RIOs differing only in the first 4 bytes are different.
        ra = new RaPacketBuilder(ROUTER_LIFETIME);
        ra.addRioOption(ROUTE_LIFETIME, "64:ff9b::/64");
        // Packet should be passed because it is different.
        program = ipClientCallback.assertProgramUpdateAndGet();
        assertPass(program, ra.build());

        ra = new RaPacketBuilder(ROUTER_LIFETIME);
        ra.addDnsslOption(DNSSL_LIFETIME, "test.example.com", "one.more.example.com");
        ByteBuffer dnsslOptionPacket = ByteBuffer.wrap(ra.build());
        verifyRaLifetime(apfFilter, ipClientCallback, dnsslOptionPacket, ROUTER_LIFETIME);

        ByteBuffer largeRaPacket = ByteBuffer.wrap(buildLargeRa());
        verifyRaLifetime(apfFilter, ipClientCallback, largeRaPacket, 300);

        // Verify that current program filters all the RAs (note: ApfFilter.MAX_RAS == 10).
        program = ipClientCallback.assertProgramUpdateAndGet();
        verifyRaLifetime(program, basePacket, ROUTER_LIFETIME);
        verifyRaLifetime(program, newFlowLabelPacket, ROUTER_LIFETIME);
        verifyRaLifetime(program, prefixOptionPacket, PREFIX_PREFERRED_LIFETIME);
        verifyRaLifetime(program, rdnssOptionPacket, RDNSS_LIFETIME);
        verifyRaLifetime(program, lowLifetimeRdnssOptionPacket, ROUTER_LIFETIME);
        verifyRaLifetime(program, routeInfoOptionPacket, ROUTE_LIFETIME);
        verifyRaLifetime(program, dnsslOptionPacket, ROUTER_LIFETIME);
        verifyRaLifetime(program, largeRaPacket, 300);

        apfFilter.shutdown();
    }

    @Test
    public void testRaWithDifferentReachableTimeAndRetransTimer() throws Exception {
        final MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        final ApfConfiguration config = getDefaultConfig();
        config.multicastFilter = DROP_MULTICAST;
        config.ieee802_3Filter = DROP_802_3_FRAMES;
        final TestApfFilter apfFilter = new TestApfFilter(mContext, config, ipClientCallback,
                mNetworkQuirkMetrics);
        byte[] program = ipClientCallback.assertProgramUpdateAndGet();
        final int RA_REACHABLE_TIME = 1800;
        final int RA_RETRANSMISSION_TIMER = 1234;

        // Create an Ra packet without options
        // Reachable time = 1800, retransmission timer = 1234
        RaPacketBuilder ra = new RaPacketBuilder(1800 /* router lft */);
        ra.setReachableTime(RA_REACHABLE_TIME);
        ra.setRetransmissionTimer(RA_RETRANSMISSION_TIMER);
        byte[] raPacket = ra.build();
        // First RA passes filter
        assertPass(program, raPacket);

        // Assume apf is shown the given RA, it generates program to filter it.
        apfFilter.pretendPacketReceived(raPacket);
        program = ipClientCallback.assertProgramUpdateAndGet();
        assertDrop(program, raPacket);

        // A packet with different reachable time should be passed.
        // Reachable time = 2300, retransmission timer = 1234
        ra.setReachableTime(RA_REACHABLE_TIME + 500);
        raPacket = ra.build();
        assertPass(program, raPacket);

        // A packet with different retransmission timer should be passed.
        // Reachable time = 1800, retransmission timer = 2234
        ra.setReachableTime(RA_REACHABLE_TIME);
        ra.setRetransmissionTimer(RA_RETRANSMISSION_TIMER + 1000);
        raPacket = ra.build();
        assertPass(program, raPacket);
    }

    // The ByteBuffer is always created by ByteBuffer#wrap in the helper functions
    @SuppressWarnings("ByteBufferBackingArray")
    @Test
    public void testRaWithProgramInstalledSomeTimeAfterLastSeen() throws Exception {
        final MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        final ApfConfiguration config = getDefaultConfig();
        config.multicastFilter = DROP_MULTICAST;
        config.ieee802_3Filter = DROP_802_3_FRAMES;
        final TestApfFilter apfFilter = new TestApfFilter(mContext, config, ipClientCallback,
                mNetworkQuirkMetrics);
        byte[] program = ipClientCallback.assertProgramUpdateAndGet();

        final int routerLifetime = 1000;
        final int timePassedSeconds = 12;

        // Verify that when the program is generated and installed some time after RA is last seen
        // it should be installed with the correct remaining lifetime.
        ByteBuffer basePacket = ByteBuffer.wrap(new RaPacketBuilder(routerLifetime).build());
        verifyRaLifetime(apfFilter, ipClientCallback, basePacket, routerLifetime);
        apfFilter.increaseCurrentTimeSeconds(timePassedSeconds);
        synchronized (apfFilter) {
            apfFilter.installNewProgramLocked();
        }
        program = ipClientCallback.assertProgramUpdateAndGet();
        verifyRaLifetime(program, basePacket, routerLifetime, timePassedSeconds);

        // Packet should be passed if the program is installed after 1/6 * lifetime from last seen
        apfFilter.increaseCurrentTimeSeconds((int) (routerLifetime / 6) - timePassedSeconds - 1);
        synchronized (apfFilter) {
            apfFilter.installNewProgramLocked();
        }
        program = ipClientCallback.assertProgramUpdateAndGet();
        assertDrop(program, basePacket.array());
        apfFilter.increaseCurrentTimeSeconds(1);
        synchronized (apfFilter) {
            apfFilter.installNewProgramLocked();
        }
        program = ipClientCallback.assertProgramUpdateAndGet();
        assertPass(program, basePacket.array());

        apfFilter.shutdown();
    }

    /**
     * Stage a file for testing, i.e. make it native accessible. Given a resource ID,
     * copy that resource into the app's data directory and return the path to it.
     */
    private String stageFile(int rawId) throws Exception {
        File file = new File(InstrumentationRegistry.getContext().getFilesDir(), "staged_file");
        new File(file.getParent()).mkdirs();
        InputStream in = null;
        OutputStream out = null;
        try {
            in = InstrumentationRegistry.getContext().getResources().openRawResource(rawId);
            out = new FileOutputStream(file);
            Streams.copy(in, out);
        } finally {
            if (in != null) in.close();
            if (out != null) out.close();
        }
        return file.getAbsolutePath();
    }

    private static void put(ByteBuffer buffer, int position, byte[] bytes) {
        final int original = buffer.position();
        buffer.position(position);
        buffer.put(bytes);
        buffer.position(original);
    }

    @Test
    public void testRaParsing() throws Exception {
        final int maxRandomPacketSize = 512;
        final Random r = new Random();
        MockIpClientCallback cb = new MockIpClientCallback();
        ApfConfiguration config = getDefaultConfig();
        config.multicastFilter = DROP_MULTICAST;
        config.ieee802_3Filter = DROP_802_3_FRAMES;
        TestApfFilter apfFilter = new TestApfFilter(mContext, config, cb, mNetworkQuirkMetrics);
        for (int i = 0; i < 1000; i++) {
            byte[] packet = new byte[r.nextInt(maxRandomPacketSize + 1)];
            r.nextBytes(packet);
            try {
                apfFilter.new Ra(packet, packet.length);
            } catch (ApfFilter.InvalidRaException e) {
            } catch (Exception e) {
                throw new Exception("bad packet: " + HexDump.toHexString(packet), e);
            }
        }
        apfFilter.shutdown();
    }

    @Test
    public void testRaProcessing() throws Exception {
        final int maxRandomPacketSize = 512;
        final Random r = new Random();
        MockIpClientCallback cb = new MockIpClientCallback();
        ApfConfiguration config = getDefaultConfig();
        config.multicastFilter = DROP_MULTICAST;
        config.ieee802_3Filter = DROP_802_3_FRAMES;
        TestApfFilter apfFilter = new TestApfFilter(mContext, config, cb, mNetworkQuirkMetrics);
        for (int i = 0; i < 1000; i++) {
            byte[] packet = new byte[r.nextInt(maxRandomPacketSize + 1)];
            r.nextBytes(packet);
            try {
                apfFilter.processRa(packet, packet.length);
            } catch (Exception e) {
                throw new Exception("bad packet: " + HexDump.toHexString(packet), e);
            }
        }
        apfFilter.shutdown();
    }

    @Test
    public void testMatchedRaUpdatesLifetime() throws Exception {
        final MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        final TestApfFilter apfFilter = new TestApfFilter(mContext, getDefaultConfig(),
                ipClientCallback, mNetworkQuirkMetrics);

        // Create an RA and build an APF program
        byte[] ra = new RaPacketBuilder(1800 /* router lifetime */).build();
        apfFilter.pretendPacketReceived(ra);
        byte[] program = ipClientCallback.assertProgramUpdateAndGet();

        // lifetime dropped significantly, assert pass
        ra = new RaPacketBuilder(200 /* router lifetime */).build();
        assertPass(program, ra);

        // update program with the new RA
        apfFilter.pretendPacketReceived(ra);
        program = ipClientCallback.assertProgramUpdateAndGet();

        // assert program was updated and new lifetimes were taken into account.
        assertDrop(program, ra);
        apfFilter.shutdown();
    }

    @Test
    public void testProcessRaWithInfiniteLifeTimeWithoutCrash() throws Exception {
        final MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        // configure accept_ra_min_lft
        final ApfConfiguration config = getDefaultConfig();
        config.acceptRaMinLft = 180;
        TestApfFilter apfFilter;
        // Template packet:
        // Frame 1: 150 bytes on wire (1200 bits), 150 bytes captured (1200 bits)
        // Ethernet II, Src: Netgear_23:67:2c (28:c6:8e:23:67:2c), Dst: IPv6mcast_01 (33:33:00:00:00:01)
        // Internet Protocol Version 6, Src: fe80::2ac6:8eff:fe23:672c, Dst: ff02::1
        // Internet Control Message Protocol v6
        //   Type: Router Advertisement (134)
        //   Code: 0
        //   Checksum: 0x0acd [correct]
        //   Checksum Status: Good
        //   Cur hop limit: 64
        //   Flags: 0xc0, Managed address configuration, Other configuration, Prf (Default Router Preference): Medium
        //   Router lifetime (s): 7000
        //   Reachable time (ms): 0
        //   Retrans timer (ms): 0
        //   ICMPv6 Option (Source link-layer address : 28:c6:8e:23:67:2c)
        //     Type: Source link-layer address (1)
        //     Length: 1 (8 bytes)
        //     Link-layer address: Netgear_23:67:2c (28:c6:8e:23:67:2c)
        //     Source Link-layer address: Netgear_23:67:2c (28:c6:8e:23:67:2c)
        //   ICMPv6 Option (MTU : 1500)
        //     Type: MTU (5)
        //     Length: 1 (8 bytes)
        //     Reserved
        //     MTU: 1500
        //   ICMPv6 Option (Prefix information : 2401:fa00:480:f000::/64)
        //     Type: Prefix information (3)
        //     Length: 4 (32 bytes)
        //     Prefix Length: 64
        //     Flag: 0xc0, On-link flag(L), Autonomous address-configuration flag(A)
        //     Valid Lifetime: Infinity (4294967295)
        //     Preferred Lifetime: Infinity (4294967295)
        //     Reserved
        //     Prefix: 2401:fa00:480:f000::
        //   ICMPv6 Option (Recursive DNS Server 2401:fa00:480:f000::1)
        //     Type: Recursive DNS Server (25)
        //     Length: 3 (24 bytes)
        //     Reserved
        //     Lifetime: 7000
        //     Recursive DNS Servers: 2401:fa00:480:f000::1
        //   ICMPv6 Option (Advertisement Interval : 600000)
        //     Type: Advertisement Interval (7)
        //     Length: 1 (8 bytes)
        //     Reserved
        //     Advertisement Interval: 600000
        final String packetStringFmt = "33330000000128C68E23672C86DD60054C6B00603AFFFE800000000000002AC68EFFFE23672CFF02000000000000000000000000000186000ACD40C01B580000000000000000010128C68E23672C05010000000005DC030440C0%s000000002401FA000480F00000000000000000001903000000001B582401FA000480F000000000000000000107010000000927C0";
        final List<String> lifetimes = List.of("FFFFFFFF", "00000000", "00000001", "00001B58");
        for (String lifetime : lifetimes) {
            apfFilter = new TestApfFilter(mContext, config, ipClientCallback, mNetworkQuirkMetrics);
            final byte[] ra = hexStringToByteArray(
                    String.format(packetStringFmt, lifetime + lifetime));
            // feed the RA into APF and generate the filter, the filter shouldn't crash.
            apfFilter.pretendPacketReceived(ra);
            ipClientCallback.assertProgramUpdateAndGet();
            apfFilter.shutdown();
        }
    }

    // Test for go/apf-ra-filter Case 1a.
    // Old lifetime is 0
    @Test
    public void testAcceptRaMinLftCase1a() throws Exception {
        final MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        // configure accept_ra_min_lft
        final ApfConfiguration config = getDefaultConfig();
        config.acceptRaMinLft = 180;
        final TestApfFilter apfFilter = new TestApfFilter(mContext, config, ipClientCallback,
                mNetworkQuirkMetrics);

        // Create an initial RA and build an APF program
        byte[] ra = new RaPacketBuilder(1800 /* router lifetime */)
                .addPioOption(1800 /*valid*/, 0 /*preferred*/, "2001:db8::/64")
                .build();

        apfFilter.pretendPacketReceived(ra);
        byte[] program = ipClientCallback.assertProgramUpdateAndGet();

        // repeated RA is dropped
        assertDrop(program, ra);

        // PIO preferred lifetime increases
        ra = new RaPacketBuilder(1800 /* router lifetime */)
                .addPioOption(1800 /*valid*/, 1 /*preferred*/, "2001:db8::/64")
                .build();
        assertPass(program, ra);
        apfFilter.shutdown();
    }

    // Test for go/apf-ra-filter Case 2a.
    // Old lifetime is > 0
    @Test
    public void testAcceptRaMinLftCase2a() throws Exception {
        final MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        // configure accept_ra_min_lft
        final ApfConfiguration config = getDefaultConfig();
        config.acceptRaMinLft = 180;
        final TestApfFilter apfFilter = new TestApfFilter(mContext, config, ipClientCallback,
                mNetworkQuirkMetrics);

        // Create an initial RA and build an APF program
        byte[] ra = new RaPacketBuilder(1800 /* router lifetime */)
                .addPioOption(1800 /*valid*/, 100 /*preferred*/, "2001:db8::/64")
                .build();

        apfFilter.pretendPacketReceived(ra);
        byte[] program = ipClientCallback.assertProgramUpdateAndGet();

        // repeated RA is dropped
        assertDrop(program, ra);

        // PIO preferred lifetime increases
        ra = new RaPacketBuilder(1800 /* router lifetime */)
                .addPioOption(1800 /*valid*/, 101 /*preferred*/, "2001:db8::/64")
                .build();
        assertPass(program, ra);

        // PIO preferred lifetime decreases significantly
        ra = new RaPacketBuilder(1800 /* router lifetime */)
                .addPioOption(1800 /*valid*/, 33 /*preferred*/, "2001:db8::/64")
                .build();
        assertPass(program, ra);
        apfFilter.shutdown();
    }


    // Test for go/apf-ra-filter Case 1b.
    // Old lifetime is 0
    @Test
    public void testAcceptRaMinLftCase1b() throws Exception {
        final MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        // configure accept_ra_min_lft
        final ApfConfiguration config = getDefaultConfig();
        config.acceptRaMinLft = 180;
        final TestApfFilter apfFilter = new TestApfFilter(mContext, config, ipClientCallback,
                mNetworkQuirkMetrics);

        // Create an initial RA and build an APF program
        byte[] ra = new RaPacketBuilder(0 /* router lifetime */).build();

        apfFilter.pretendPacketReceived(ra);
        byte[] program = ipClientCallback.assertProgramUpdateAndGet();

        // repeated RA is dropped
        assertDrop(program, ra);

        // lifetime increases below accept_ra_min_lft
        ra = new RaPacketBuilder(179 /* router lifetime */).build();
        assertDrop(program, ra);

        // lifetime increases to accept_ra_min_lft
        ra = new RaPacketBuilder(180 /* router lifetime */).build();
        assertPass(program, ra);
        apfFilter.shutdown();
    }


    // Test for go/apf-ra-filter Case 2b.
    // Old lifetime is < accept_ra_min_lft (but not 0).
    @Test
    public void testAcceptRaMinLftCase2b() throws Exception {
        final MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        // configure accept_ra_min_lft
        final ApfConfiguration config = getDefaultConfig();
        config.acceptRaMinLft = 180;
        final TestApfFilter apfFilter = new TestApfFilter(mContext, config, ipClientCallback,
                mNetworkQuirkMetrics);

        // Create an initial RA and build an APF program
        byte[] ra = new RaPacketBuilder(100 /* router lifetime */).build();

        apfFilter.pretendPacketReceived(ra);
        byte[] program = ipClientCallback.assertProgramUpdateAndGet();

        // repeated RA is dropped
        assertDrop(program, ra);

        // lifetime increases
        ra = new RaPacketBuilder(101 /* router lifetime */).build();
        assertDrop(program, ra);

        // lifetime decreases significantly
        ra = new RaPacketBuilder(1 /* router lifetime */).build();
        assertDrop(program, ra);

        // equals accept_ra_min_lft
        ra = new RaPacketBuilder(180 /* router lifetime */).build();
        assertPass(program, ra);

        // lifetime is 0
        ra = new RaPacketBuilder(0 /* router lifetime */).build();
        assertPass(program, ra);
        apfFilter.shutdown();
    }

    // Test for go/apf-ra-filter Case 3b.
    // Old lifetime is >= accept_ra_min_lft and <= 3 * accept_ra_min_lft
    @Test
    public void testAcceptRaMinLftCase3b() throws Exception {
        final MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        // configure accept_ra_min_lft
        final ApfConfiguration config = getDefaultConfig();
        config.acceptRaMinLft = 180;
        final TestApfFilter apfFilter = new TestApfFilter(mContext, config, ipClientCallback,
                mNetworkQuirkMetrics);

        // Create an initial RA and build an APF program
        byte[] ra = new RaPacketBuilder(200 /* router lifetime */).build();

        apfFilter.pretendPacketReceived(ra);
        byte[] program = ipClientCallback.assertProgramUpdateAndGet();

        // repeated RA is dropped
        assertDrop(program, ra);

        // lifetime increases
        ra = new RaPacketBuilder(201 /* router lifetime */).build();
        assertPass(program, ra);

        // lifetime is below accept_ra_min_lft (but not 0)
        ra = new RaPacketBuilder(1 /* router lifetime */).build();
        assertDrop(program, ra);

        // lifetime is 0
        ra = new RaPacketBuilder(0 /* router lifetime */).build();
        assertPass(program, ra);
        apfFilter.shutdown();
    }

    // Test for go/apf-ra-filter Case 4b.
    // Old lifetime is > 3 * accept_ra_min_lft
    @Test
    public void testAcceptRaMinLftCase4b() throws Exception {
        final MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        // configure accept_ra_min_lft
        final ApfConfiguration config = getDefaultConfig();
        config.acceptRaMinLft = 180;
        final TestApfFilter apfFilter = new TestApfFilter(mContext, config, ipClientCallback,
                mNetworkQuirkMetrics);

        // Create an initial RA and build an APF program
        byte[] ra = new RaPacketBuilder(1800 /* router lifetime */).build();

        apfFilter.pretendPacketReceived(ra);
        byte[] program = ipClientCallback.assertProgramUpdateAndGet();

        // repeated RA is dropped
        assertDrop(program, ra);

        // lifetime increases
        ra = new RaPacketBuilder(1801 /* router lifetime */).build();
        assertPass(program, ra);

        // lifetime is 1/3 of old lft
        ra = new RaPacketBuilder(600 /* router lifetime */).build();
        assertDrop(program, ra);

        // lifetime is below 1/3 of old lft
        ra = new RaPacketBuilder(599 /* router lifetime */).build();
        assertPass(program, ra);

        // lifetime is below accept_ra_min_lft (but not 0)
        ra = new RaPacketBuilder(1 /* router lifetime */).build();
        assertDrop(program, ra);

        // lifetime is 0
        ra = new RaPacketBuilder(0 /* router lifetime */).build();
        assertPass(program, ra);
        apfFilter.shutdown();
    }

    @Test
    public void testRaFilterIsUpdated() throws Exception {
        final MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        // configure accept_ra_min_lft
        final ApfConfiguration config = getDefaultConfig();
        config.acceptRaMinLft = 180;
        final TestApfFilter apfFilter = new TestApfFilter(mContext, config, ipClientCallback,
                mNetworkQuirkMetrics);

        // Create an initial RA and build an APF program
        byte[] ra = new RaPacketBuilder(1800 /* router lifetime */).build();
        apfFilter.pretendPacketReceived(ra);
        byte[] program = ipClientCallback.assertProgramUpdateAndGet();

        // repeated RA is dropped.
        assertDrop(program, ra);

        // updated RA is passed, repeated RA is dropped after program update.
        ra = new RaPacketBuilder(599 /* router lifetime */).build();
        assertPass(program, ra);
        apfFilter.pretendPacketReceived(ra);
        program = ipClientCallback.assertProgramUpdateAndGet();
        assertDrop(program, ra);

        ra = new RaPacketBuilder(180 /* router lifetime */).build();
        assertPass(program, ra);
        apfFilter.pretendPacketReceived(ra);
        program = ipClientCallback.assertProgramUpdateAndGet();
        assertDrop(program, ra);

        ra = new RaPacketBuilder(0 /* router lifetime */).build();
        assertPass(program, ra);
        apfFilter.pretendPacketReceived(ra);
        program = ipClientCallback.assertProgramUpdateAndGet();
        assertDrop(program, ra);

        ra = new RaPacketBuilder(180 /* router lifetime */).build();
        assertPass(program, ra);
        apfFilter.pretendPacketReceived(ra);
        program = ipClientCallback.assertProgramUpdateAndGet();
        assertDrop(program, ra);

        ra = new RaPacketBuilder(599 /* router lifetime */).build();
        assertPass(program, ra);
        apfFilter.pretendPacketReceived(ra);
        program = ipClientCallback.assertProgramUpdateAndGet();
        assertDrop(program, ra);

        ra = new RaPacketBuilder(1800 /* router lifetime */).build();
        assertPass(program, ra);
        apfFilter.pretendPacketReceived(ra);
        program = ipClientCallback.assertProgramUpdateAndGet();
        assertDrop(program, ra);
        apfFilter.shutdown();
    }

    @Test
    public void testBroadcastAddress() throws Exception {
        assertEqualsIp("255.255.255.255", ApfFilter.ipv4BroadcastAddress(IPV4_ANY_HOST_ADDR, 0));
        assertEqualsIp("0.0.0.0", ApfFilter.ipv4BroadcastAddress(IPV4_ANY_HOST_ADDR, 32));
        assertEqualsIp("0.0.3.255", ApfFilter.ipv4BroadcastAddress(IPV4_ANY_HOST_ADDR, 22));
        assertEqualsIp("0.255.255.255", ApfFilter.ipv4BroadcastAddress(IPV4_ANY_HOST_ADDR, 8));

        assertEqualsIp("255.255.255.255", ApfFilter.ipv4BroadcastAddress(MOCK_IPV4_ADDR, 0));
        assertEqualsIp("10.0.0.1", ApfFilter.ipv4BroadcastAddress(MOCK_IPV4_ADDR, 32));
        assertEqualsIp("10.0.0.255", ApfFilter.ipv4BroadcastAddress(MOCK_IPV4_ADDR, 24));
        assertEqualsIp("10.0.255.255", ApfFilter.ipv4BroadcastAddress(MOCK_IPV4_ADDR, 16));
    }

    public void assertEqualsIp(String expected, int got) throws Exception {
        int want = Inet4AddressUtils.inet4AddressToIntHTH(
                (Inet4Address) InetAddresses.parseNumericAddress(expected));
        assertEquals(want, got);
    }

    private TestAndroidPacketFilter makeTestApfFilter(ApfConfiguration config,
            MockIpClientCallback ipClientCallback, boolean isLegacy) throws Exception {
        final TestAndroidPacketFilter apfFilter;
        if (isLegacy) {
            apfFilter = new TestLegacyApfFilter(mContext, config, ipClientCallback,
                    mIpConnectivityLog, mNetworkQuirkMetrics, mDependencies, mClock);
        } else {
            apfFilter = new TestApfFilter(mContext, config, ipClientCallback, mNetworkQuirkMetrics,
                    mDependencies, mClock);
        }
        return apfFilter;
    }

    // LegacyApfFilter ignores zero lifetime RAs (doesn't update program) but ApfFilter won't.
    private void verifyUpdateProgramForZeroLifetimeRa(MockIpClientCallback ipClientCallback,
            boolean isLegacy) {
        if (isLegacy) {
            ipClientCallback.assertNoProgramUpdate();
        } else {
            ipClientCallback.assertProgramUpdateAndGet();
        }
    }

    private void verifyInstallPacketFilterFailure(boolean isLegacy) throws Exception {
        final MockIpClientCallback ipClientCallback = new MockIpClientCallback(false);
        final ApfConfiguration config = getDefaultConfig();
        final TestAndroidPacketFilter apfFilter =
                makeTestApfFilter(config, ipClientCallback, isLegacy);
        verify(mNetworkQuirkMetrics).setEvent(NetworkQuirkEvent.QE_APF_INSTALL_FAILURE);
        verify(mNetworkQuirkMetrics).statsWrite();
        reset(mNetworkQuirkMetrics);
        synchronized (apfFilter) {
            apfFilter.installNewProgramLocked();
        }
        verify(mNetworkQuirkMetrics).setEvent(NetworkQuirkEvent.QE_APF_INSTALL_FAILURE);
        verify(mNetworkQuirkMetrics).statsWrite();
        apfFilter.shutdown();
    }

    @Test
    public void testInstallPacketFilterFailure() throws Exception {
        verifyInstallPacketFilterFailure(false /* isLegacy */);
    }

    @Test
    public void testInstallPacketFilterFailure_LegacyApfFilter() throws Exception {
        verifyInstallPacketFilterFailure(true /* isLegacy */);
    }

    private void verifyApfProgramOverSize(boolean isLegacy) throws Exception {
        final MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        final ApfConfiguration config = getDefaultConfig();
        final ApfCapabilities capabilities = new ApfCapabilities(2, 512, ARPHRD_ETHER);
        config.apfCapabilities = capabilities;
        final TestAndroidPacketFilter apfFilter =
                makeTestApfFilter(config, ipClientCallback, isLegacy);
        byte[] program = ipClientCallback.assertProgramUpdateAndGet();
        final byte[] ra = buildLargeRa();
        apfFilter.pretendPacketReceived(ra);
        // The generated program size will be 529, which is larger than 512
        program = ipClientCallback.assertProgramUpdateAndGet();
        verify(mNetworkQuirkMetrics).setEvent(NetworkQuirkEvent.QE_APF_OVER_SIZE_FAILURE);
        verify(mNetworkQuirkMetrics).statsWrite();
        apfFilter.shutdown();
    }

    @Test
    public void testApfProgramOverSize() throws Exception {
        verifyApfProgramOverSize(false /* isLegacy */);
    }

    @Test
    public void testApfProgramOverSize_LegacyApfFilter() throws Exception {
        verifyApfProgramOverSize(true /* isLegacy */);
    }

    private void verifyGenerateApfProgramException(boolean isLegacy) throws Exception {
        final MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        final ApfConfiguration config = getDefaultConfig();
        final TestAndroidPacketFilter apfFilter;
        if (isLegacy) {
            apfFilter = new TestLegacyApfFilter(mContext, config, ipClientCallback,
                    mIpConnectivityLog, mNetworkQuirkMetrics, mDependencies,
                    true /* throwsExceptionWhenGeneratesProgram */);
        } else {
            apfFilter = new TestApfFilter(mContext, config, ipClientCallback, mNetworkQuirkMetrics,
                    mDependencies, true /* throwsExceptionWhenGeneratesProgram */);
        }
        synchronized (apfFilter) {
            apfFilter.installNewProgramLocked();
        }
        verify(mNetworkQuirkMetrics).setEvent(NetworkQuirkEvent.QE_APF_GENERATE_FILTER_EXCEPTION);
        verify(mNetworkQuirkMetrics).statsWrite();
        apfFilter.shutdown();
    }

    @Test
    public void testGenerateApfProgramException() throws Exception {
        verifyGenerateApfProgramException(false /* isLegacy */);
    }

    @Test
    public void testGenerateApfProgramException_LegacyApfFilter() throws Exception {
        verifyGenerateApfProgramException(true /* isLegacy */);
    }

    private void verifyApfSessionInfoMetrics(boolean isLegacy) throws Exception {
        final MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        final ApfConfiguration config = getDefaultConfig();
        final ApfCapabilities capabilities = new ApfCapabilities(4, 4096, ARPHRD_ETHER);
        config.apfCapabilities = capabilities;
        final long startTimeMs = 12345;
        final long durationTimeMs = config.minMetricsSessionDurationMs;
        doReturn(startTimeMs).when(mClock).elapsedRealtime();
        final TestAndroidPacketFilter apfFilter =
                makeTestApfFilter(config, ipClientCallback, isLegacy);
        int maxProgramSize = 0;
        int numProgramUpdated = 0;
        byte[] program = ipClientCallback.assertProgramUpdateAndGet();
        maxProgramSize = Math.max(maxProgramSize, program.length);
        numProgramUpdated++;

        final byte[] data = new byte[Counter.totalSize()];
        final byte[] expectedData = data.clone();
        final int totalPacketsCounterIdx = Counter.totalSize() + Counter.TOTAL_PACKETS.offset();
        final int passedIpv6IcmpCounterIdx =
                Counter.totalSize() + Counter.PASSED_IPV6_ICMP.offset();
        final int droppedIpv4MulticastIdx =
                Counter.totalSize() + Counter.DROPPED_IPV4_MULTICAST.offset();

        // Receive an RA packet (passed).
        final byte[] ra = buildLargeRa();
        expectedData[totalPacketsCounterIdx + 3] += 1;
        expectedData[passedIpv6IcmpCounterIdx + 3] += 1;
        assertDataMemoryContents(PASS, program, ra, data, expectedData);
        apfFilter.pretendPacketReceived(ra);
        program = ipClientCallback.assertProgramUpdateAndGet();
        maxProgramSize = Math.max(maxProgramSize, program.length);
        numProgramUpdated++;

        apfFilter.setMulticastFilter(true);
        // setMulticastFilter will trigger program installation.
        program = ipClientCallback.assertProgramUpdateAndGet();
        maxProgramSize = Math.max(maxProgramSize, program.length);
        numProgramUpdated++;

        // Receive IPv4 multicast packet (dropped).
        final byte[] multicastIpv4Addr = {(byte) 224, 0, 0, 1};
        ByteBuffer mcastv4packet = makeIpv4Packet(IPPROTO_UDP);
        put(mcastv4packet, IPV4_DEST_ADDR_OFFSET, multicastIpv4Addr);
        expectedData[totalPacketsCounterIdx + 3] += 1;
        expectedData[droppedIpv4MulticastIdx + 3] += 1;
        assertDataMemoryContents(DROP, program, mcastv4packet.array(), data, expectedData);

        // Set data snapshot and update counters.
        apfFilter.setDataSnapshot(data);

        // Write metrics data to statsd pipeline when shutdown.
        doReturn(startTimeMs + durationTimeMs).when(mClock).elapsedRealtime();
        apfFilter.shutdown();
        verify(mApfSessionInfoMetrics).setVersion(4);
        verify(mApfSessionInfoMetrics).setMemorySize(4096);

        // Verify Counters
        final Map<Counter, Long> expectedCounters = Map.of(Counter.TOTAL_PACKETS, 2L,
                Counter.PASSED_IPV6_ICMP, 1L, Counter.DROPPED_IPV4_MULTICAST, 1L);
        final ArgumentCaptor<Counter> counterCaptor = ArgumentCaptor.forClass(Counter.class);
        final ArgumentCaptor<Long> valueCaptor = ArgumentCaptor.forClass(Long.class);
        verify(mApfSessionInfoMetrics, times(expectedCounters.size())).addApfCounter(
                counterCaptor.capture(), valueCaptor.capture());
        final List<Counter> counters = counterCaptor.getAllValues();
        final List<Long> values = valueCaptor.getAllValues();
        final ArrayMap<Counter, Long> capturedCounters = new ArrayMap<>();
        for (int i = 0; i < counters.size(); i++) {
            capturedCounters.put(counters.get(i), values.get(i));
        }
        assertEquals(expectedCounters, capturedCounters);

        verify(mApfSessionInfoMetrics).setApfSessionDurationSeconds(
                (int) (durationTimeMs / DateUtils.SECOND_IN_MILLIS));
        verify(mApfSessionInfoMetrics).setNumOfTimesApfProgramUpdated(numProgramUpdated);
        verify(mApfSessionInfoMetrics).setMaxProgramSize(maxProgramSize);
        verify(mApfSessionInfoMetrics).statsWrite();
    }

    @Test
    public void testApfSessionInfoMetrics() throws Exception {
        verifyApfSessionInfoMetrics(false /* isLegacy */);
    }

    @Test
    public void testApfSessionInfoMetrics_LegacyApfFilter() throws Exception {
        verifyApfSessionInfoMetrics(true /* isLegacy */);
    }

    private void verifyIpClientRaInfoMetrics(boolean isLegacy) throws Exception {
        final MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        final ApfConfiguration config = getDefaultConfig();
        final long startTimeMs = 12345;
        final long durationTimeMs = config.minMetricsSessionDurationMs;
        doReturn(startTimeMs).when(mClock).elapsedRealtime();
        final TestAndroidPacketFilter apfFilter =
                makeTestApfFilter(config, ipClientCallback, isLegacy);
        byte[] program = ipClientCallback.assertProgramUpdateAndGet();

        final int routerLifetime = 1000;
        final int prefixValidLifetime = 200;
        final int prefixPreferredLifetime = 100;
        final int rdnssLifetime  = 300;
        final int routeLifetime  = 400;

        // Construct 2 RAs with partial lifetimes larger than predefined constants
        final RaPacketBuilder ra1 = new RaPacketBuilder(routerLifetime);
        ra1.addPioOption(prefixValidLifetime + 123, prefixPreferredLifetime, "2001:db8::/64");
        ra1.addRdnssOption(rdnssLifetime, "2001:4860:4860::8888", "2001:4860:4860::8844");
        ra1.addRioOption(routeLifetime + 456, "64:ff9b::/96");
        final RaPacketBuilder ra2 = new RaPacketBuilder(routerLifetime + 123);
        ra2.addPioOption(prefixValidLifetime, prefixPreferredLifetime, "2001:db9::/64");
        ra2.addRdnssOption(rdnssLifetime + 456, "2001:4860:4860::8888", "2001:4860:4860::8844");
        ra2.addRioOption(routeLifetime, "64:ff9b::/96");

        // Construct an invalid RA packet
        final RaPacketBuilder raInvalid = new RaPacketBuilder(routerLifetime);
        raInvalid.addZeroLengthOption();

        // Construct 4 different kinds of zero lifetime RAs
        final RaPacketBuilder raZeroRouterLifetime = new RaPacketBuilder(0 /* routerLft */);
        final RaPacketBuilder raZeroPioValidLifetime = new RaPacketBuilder(routerLifetime);
        raZeroPioValidLifetime.addPioOption(0, prefixPreferredLifetime, "2001:db10::/64");
        final RaPacketBuilder raZeroRdnssLifetime = new RaPacketBuilder(routerLifetime);
        raZeroRdnssLifetime.addPioOption(
                prefixValidLifetime, prefixPreferredLifetime, "2001:db11::/64");
        raZeroRdnssLifetime.addRdnssOption(0, "2001:4860:4860::8888", "2001:4860:4860::8844");
        final RaPacketBuilder raZeroRioRouteLifetime = new RaPacketBuilder(routerLifetime);
        raZeroRioRouteLifetime.addPioOption(
                prefixValidLifetime, prefixPreferredLifetime, "2001:db12::/64");
        raZeroRioRouteLifetime.addRioOption(0, "64:ff9b::/96");

        // Inject RA packets. Calling assertProgramUpdateAndGet()/assertNoProgramUpdate() is to make
        // sure that the RA packet has been processed.
        apfFilter.pretendPacketReceived(ra1.build());
        program = ipClientCallback.assertProgramUpdateAndGet();
        apfFilter.pretendPacketReceived(ra2.build());
        program = ipClientCallback.assertProgramUpdateAndGet();
        apfFilter.pretendPacketReceived(raInvalid.build());
        ipClientCallback.assertNoProgramUpdate();
        apfFilter.pretendPacketReceived(raZeroRouterLifetime.build());
        verifyUpdateProgramForZeroLifetimeRa(ipClientCallback, isLegacy);
        apfFilter.pretendPacketReceived(raZeroPioValidLifetime.build());
        verifyUpdateProgramForZeroLifetimeRa(ipClientCallback, isLegacy);
        apfFilter.pretendPacketReceived(raZeroRdnssLifetime.build());
        verifyUpdateProgramForZeroLifetimeRa(ipClientCallback, isLegacy);
        apfFilter.pretendPacketReceived(raZeroRioRouteLifetime.build());
        verifyUpdateProgramForZeroLifetimeRa(ipClientCallback, isLegacy);

        // Write metrics data to statsd pipeline when shutdown.
        doReturn(startTimeMs + durationTimeMs).when(mClock).elapsedRealtime();
        apfFilter.shutdown();

        // Verify each metric fields in IpClientRaInfoMetrics.
        if (isLegacy) {
            // LegacyApfFilter will purge expired RAs before adding new RA. Every time a new zero
            // lifetime RA is received, zero lifetime RAs except the newly added one will be
            // cleared, so the number of distinct RAs is 3 (ra1, ra2 and the newly added RA).
            verify(mIpClientRaInfoMetrics).setMaxNumberOfDistinctRas(3);
        } else {
            verify(mIpClientRaInfoMetrics).setMaxNumberOfDistinctRas(6);
        }
        verify(mIpClientRaInfoMetrics).setNumberOfZeroLifetimeRas(4);
        verify(mIpClientRaInfoMetrics).setNumberOfParsingErrorRas(1);
        verify(mIpClientRaInfoMetrics).setLowestRouterLifetimeSeconds(routerLifetime);
        verify(mIpClientRaInfoMetrics).setLowestPioValidLifetimeSeconds(prefixValidLifetime);
        verify(mIpClientRaInfoMetrics).setLowestRioRouteLifetimeSeconds(routeLifetime);
        verify(mIpClientRaInfoMetrics).setLowestRdnssLifetimeSeconds(rdnssLifetime);
        verify(mIpClientRaInfoMetrics).statsWrite();
    }

    @Test
    public void testIpClientRaInfoMetrics() throws Exception {
        verifyIpClientRaInfoMetrics(false /* isLegacy */);
    }

    @Test
    public void testIpClientRaInfoMetrics_LegacyApfFilter() throws Exception {
        verifyIpClientRaInfoMetrics(true /* isLegacy */);
    }

    private void verifyNoMetricsWrittenForShortDuration(boolean isLegacy) throws Exception {
        final MockIpClientCallback ipClientCallback = new MockIpClientCallback();
        final ApfConfiguration config = getDefaultConfig();
        final long startTimeMs = 12345;
        final long durationTimeMs = config.minMetricsSessionDurationMs;

        // Verify no metrics data written to statsd for duration less than durationTimeMs.
        doReturn(startTimeMs).when(mClock).elapsedRealtime();
        final TestAndroidPacketFilter apfFilter =
                makeTestApfFilter(config, ipClientCallback, isLegacy);
        doReturn(startTimeMs + durationTimeMs - 1).when(mClock).elapsedRealtime();
        apfFilter.shutdown();
        verify(mApfSessionInfoMetrics, never()).statsWrite();
        verify(mIpClientRaInfoMetrics, never()).statsWrite();

        // Verify metrics data written to statsd for duration greater than or equal to
        // durationTimeMs.
        ApfFilter.Clock clock = mock(ApfFilter.Clock.class);
        doReturn(startTimeMs).when(clock).elapsedRealtime();
        final TestAndroidPacketFilter apfFilter2 = new TestApfFilter(mContext, config,
                ipClientCallback, mNetworkQuirkMetrics, mDependencies, clock);
        doReturn(startTimeMs + durationTimeMs).when(clock).elapsedRealtime();
        apfFilter2.shutdown();
        verify(mApfSessionInfoMetrics).statsWrite();
        verify(mIpClientRaInfoMetrics).statsWrite();
    }

    @Test
    public void testNoMetricsWrittenForShortDuration() throws Exception {
        verifyNoMetricsWrittenForShortDuration(false /* isLegacy */);
    }

    @Test
    public void testNoMetricsWrittenForShortDuration_LegacyApfFilter() throws Exception {
        verifyNoMetricsWrittenForShortDuration(true /* isLegacy */);
    }

    private int deriveApfGeneratorVersion(ApfV4GeneratorBase<?> gen) {
        if (gen instanceof ApfV4Generator) {
            return 4;
        } else if (gen instanceof ApfV6Generator) {
            return 6;
        }
        return -1;
    }

    @Test
    public void testApfGeneratorPropagation() throws IllegalInstructionException {
        ApfV4Generator v4Gen = new ApfV4Generator(APF_VERSION_4);
        ApfV6Generator v6Gen = new ApfV6Generator();
        assertEquals(4, deriveApfGeneratorVersion(v4Gen));
        assertEquals(6, deriveApfGeneratorVersion(v6Gen));
    }

    @Test
    public void testFullApfV4ProgramGenerationIPV6() throws IllegalInstructionException {
        ApfV4Generator gen = new ApfV4Generator(APF_VERSION_4);
        gen.addLoadImmediate(R1, -4);
        gen.addLoadData(R0, 0);
        gen.addAdd(1);
        gen.addStoreData(R0, 0);
        gen.addLoad16(R0, 12);
        gen.addLoadImmediate(R1, -108);
        gen.addJumpIfR0LessThan(0x600, "LABEL_504");
        gen.addLoadImmediate(R1, -112);
        gen.addJumpIfR0Equals(0x88a2, "LABEL_504");
        gen.addJumpIfR0Equals(0x88a4, "LABEL_504");
        gen.addJumpIfR0Equals(0x88b8, "LABEL_504");
        gen.addJumpIfR0Equals(0x88cd, "LABEL_504");
        gen.addJumpIfR0Equals(0x88e1, "LABEL_504");
        gen.addJumpIfR0Equals(0x88e3, "LABEL_504");
        gen.addJumpIfR0NotEquals(0x806, "LABEL_116");
        gen.addLoadImmediate(R0, 14);
        gen.addLoadImmediate(R1, -36);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("000108000604"), "LABEL_498");
        gen.addLoad16(R0, 20);
        gen.addJumpIfR0Equals(0x1, "LABEL_102");
        gen.addLoadImmediate(R1, -40);
        gen.addJumpIfR0NotEquals(0x2, "LABEL_498");
        gen.addLoad32(R0, 28);
        gen.addLoadImmediate(R1, -116);
        gen.addJumpIfR0Equals(0x0, "LABEL_504");
        gen.addLoadImmediate(R0, 0);
        gen.addLoadImmediate(R1, -44);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("ffffffffffff"), "LABEL_498");

        gen.defineLabel("LABEL_102");
        gen.addLoad32(R0, 38);
        gen.addLoadImmediate(R1, -64);
        gen.addJumpIfR0Equals(0x0, "LABEL_504");
        gen.addLoadImmediate(R1, -8);
        gen.addJump("LABEL_498");

        gen.defineLabel("LABEL_116");
        gen.addLoad16(R0, 12);
        gen.addJumpIfR0NotEquals(0x800, "LABEL_207");
        gen.addLoad8(R0, 23);
        gen.addJumpIfR0NotEquals(0x11, "LABEL_159");
        gen.addLoad16(R0, 20);
        gen.addJumpIfR0AnyBitsSet(0x1fff, "LABEL_159");
        gen.addLoadFromMemory(R1, 13);
        gen.addLoad16Indexed(R0, 16);
        gen.addJumpIfR0NotEquals(0x44, "LABEL_159");
        gen.addLoadImmediate(R0, 50);
        gen.addAddR1();
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("e212507c6345"), "LABEL_159");
        gen.addLoadImmediate(R1, -12);
        gen.addJump("LABEL_498");

        gen.defineLabel("LABEL_159");
        gen.addLoad8(R0, 30);
        gen.addAnd(240);
        gen.addLoadImmediate(R1, -84);
        gen.addJumpIfR0Equals(0xe0, "LABEL_504");
        gen.addLoadImmediate(R1, -76);
        gen.addLoad32(R0, 30);
        gen.addJumpIfR0Equals(0xffffffff, "LABEL_504");
        gen.addLoadImmediate(R1, -24);
        gen.addLoadImmediate(R0, 0);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("ffffffffffff"), "LABEL_498");
        gen.addLoadImmediate(R1, -72);
        gen.addJump("LABEL_504");
        gen.addLoadImmediate(R1, -16);
        gen.addJump("LABEL_498");

        gen.defineLabel("LABEL_207");
        gen.addJumpIfR0Equals(0x86dd, "LABEL_231");
        gen.addLoadImmediate(R0, 0);
        gen.addLoadImmediate(R1, -48);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("ffffffffffff"), "LABEL_498");
        gen.addLoadImmediate(R1, -56);
        gen.addJump("LABEL_504");

        gen.defineLabel("LABEL_231");
        gen.addLoad8(R0, 20);
        gen.addJumpIfR0Equals(0x3a, "LABEL_249");
        gen.addLoadImmediate(R1, -104);
        gen.addLoad8(R0, 38);
        gen.addJumpIfR0Equals(0xff, "LABEL_504");
        gen.addLoadImmediate(R1, -32);
        gen.addJump("LABEL_498");

        gen.defineLabel("LABEL_249");
        gen.addLoad8(R0, 54);
        gen.addLoadImmediate(R1, -88);
        gen.addJumpIfR0Equals(0x85, "LABEL_504");
        gen.addJumpIfR0NotEquals(0x88, "LABEL_283");
        gen.addLoadImmediate(R0, 38);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("ff0200000000000000000000000000"), "LABEL_283");
        gen.addLoadImmediate(R1, -92);
        gen.addJump("LABEL_504");

        gen.defineLabel("LABEL_283");
        gen.addLoadFromMemory(R0, 14);
        gen.addJumpIfR0NotEquals(0xa6, "LABEL_496");
        gen.addLoadFromMemory(R0, 15);
        gen.addJumpIfR0GreaterThan(0x254, "LABEL_496");
        gen.addLoadImmediate(R0, 0);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("e212507c6345648788fd6df086dd68"), "LABEL_496");
        gen.addLoadImmediate(R0, 18);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("00703afffe800000000000002a0079e10abc1539fe80000000000000e01250fffe7c63458600"), "LABEL_496");
        gen.addLoadImmediate(R0, 58);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("4000"), "LABEL_496");
        gen.addLoad16(R0, 60);
        gen.addJumpIfR0LessThan(0x254, "LABEL_496");
        gen.addLoadImmediate(R0, 62);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("0000000000000000"), "LABEL_496");
        gen.addLoadImmediate(R0, 78);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("19050000"), "LABEL_496");
        gen.addLoad32(R0, 82);
        gen.addJumpIfR0LessThan(0x254, "LABEL_496");
        gen.addLoadImmediate(R0, 86);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("2001486048600000000000000000646420014860486000000000000000000064"), "LABEL_496");
        gen.addLoadImmediate(R0, 118);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("030440c0"), "LABEL_496");
        gen.addLoad32(R0, 122);
        gen.addJumpIfR0LessThan(0x254, "LABEL_496");
        gen.addLoad32(R0, 126);
        gen.addJumpIfR0LessThan(0x254, "LABEL_496");
        gen.addLoadImmediate(R0, 130);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("00000000"), "LABEL_496");
        gen.addLoadImmediate(R0, 134);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("2a0079e10abc15390000000000000000"), "LABEL_496");
        gen.addLoadImmediate(R1, -60);
        gen.addJump("LABEL_504");

        gen.defineLabel("LABEL_496");
        gen.addLoadImmediate(R1, -28);

        gen.defineLabel("LABEL_498");
        gen.addLoadData(R0, 0);
        gen.addAdd(1);
        gen.addStoreData(R0, 0);
        gen.addJump(PASS_LABEL);

        gen.defineLabel("LABEL_504");
        gen.addLoadData(R0, 0);
        gen.addAdd(1);
        gen.addStoreData(R0, 0);
        gen.addJump(DROP_LABEL);

        byte[] program = gen.generate();
        final String programString = toHexString(program).toLowerCase();
        final String referenceProgramHexString = "6bfcb03a01b8120c6b949401e906006b907c01e288a27c01dd88a47c01d888b87c01d388cd7c01ce88e17c01c988e384004008066a0e6bdca401af000600010800060412147a1e016bd88401a300021a1c6b8c7c01a00000686bd4a4018c0006ffffffffffff1a266bc07c018900006bf874017e120c84005408000a17821f1112149c00181fffab0d2a108211446a3239a20506e212507c63456bf47401530a1e52f06bac7c014e00e06bb41a1e7e00000141ffffffff6be868a4012d0006ffffffffffff6bb874012e6bf07401237c001386dd686bd0a401100006ffffffffffff6bc87401110a147a0d3a6b980a267c010300ff6be072f90a366ba87af8858218886a26a2040fff02000000000000000000000000006ba472ddaa0e82d0a6aa0f8c00c9025468a2b60fe212507c6345648788fd6df086dd686a12a28b2600703afffe800000000000002a0079e10abc1539fe80000000000000e01250fffe7c634586006a3aa284024000123c94007d02546a3ea2700800000000000000006a4ea26704190500001a5294006002546a56a23b2020014860486000000000000000006464200148604860000000000000000000646a76a23204030440c01a7a94002b02541a7e94002402546c0082a21a04000000006c0086a204102a0079e10abc153900000000000000006bc472086be4b03a01b87206b03a01b87201";
        assertEquals(referenceProgramHexString, programString);
    }

    @Test
    public void testFullApfV4ProgramGenerationIPV4() throws IllegalInstructionException {
        ApfV4Generator gen = new ApfV4Generator(APF_VERSION_4);
        gen.addLoadImmediate(R1, -4);
        gen.addLoadData(R0, 0);
        gen.addAdd(1);
        gen.addStoreData(R0, 0);
        gen.addLoad16(R0, 12);
        gen.addLoadImmediate(R1, -108);
        gen.addJumpIfR0LessThan(0x600, "LABEL_283");
        gen.addLoadImmediate(R1, -112);
        gen.addJumpIfR0Equals(0x88a2, "LABEL_283");
        gen.addJumpIfR0Equals(0x88a4, "LABEL_283");
        gen.addJumpIfR0Equals(0x88b8, "LABEL_283");
        gen.addJumpIfR0Equals(0x88cd, "LABEL_283");
        gen.addJumpIfR0Equals(0x88e1, "LABEL_283");
        gen.addJumpIfR0Equals(0x88e3, "LABEL_283");
        gen.addJumpIfR0NotEquals(0x806, "LABEL_109");
        gen.addLoadImmediate(R0, 14);
        gen.addLoadImmediate(R1, -36);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("000108000604"), "LABEL_277");
        gen.addLoad16(R0, 20);
        gen.addJumpIfR0Equals(0x1, "LABEL_94");
        gen.addLoadImmediate(R1, -40);
        gen.addJumpIfR0NotEquals(0x2, "LABEL_277");
        gen.addLoad32(R0, 28);
        gen.addLoadImmediate(R1, -116);
        gen.addJumpIfR0Equals(0x0, "LABEL_283");
        gen.addLoadImmediate(R0, 0);
        gen.addLoadImmediate(R1, -44);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("ffffffffffff"), "LABEL_277");

        gen.defineLabel("LABEL_94");
        gen.addLoadImmediate(R0, 38);
        gen.addLoadImmediate(R1, -68);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("c0a801b3"), "LABEL_283");
        gen.addLoadImmediate(R1, -8);
        gen.addJump("LABEL_277");

        gen.defineLabel("LABEL_109");
        gen.addLoad16(R0, 12);
        gen.addJumpIfR0NotEquals(0x800, "LABEL_204");
        gen.addLoad8(R0, 23);
        gen.addJumpIfR0NotEquals(0x11, "LABEL_151");
        gen.addLoad16(R0, 20);
        gen.addJumpIfR0AnyBitsSet(0x1fff, "LABEL_151");
        gen.addLoadFromMemory(R1, 13);
        gen.addLoad16Indexed(R0, 16);
        gen.addJumpIfR0NotEquals(0x44, "LABEL_151");
        gen.addLoadImmediate(R0, 50);
        gen.addAddR1();
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("f683d58f832b"), "LABEL_151");
        gen.addLoadImmediate(R1, -12);
        gen.addJump("LABEL_277");

        gen.defineLabel("LABEL_151");
        gen.addLoad8(R0, 30);
        gen.addAnd(240);
        gen.addLoadImmediate(R1, -84);
        gen.addJumpIfR0Equals(0xe0, "LABEL_283");
        gen.addLoadImmediate(R1, -76);
        gen.addLoad32(R0, 30);
        gen.addJumpIfR0Equals(0xffffffff, "LABEL_283");
        gen.addLoadImmediate(R1, -80);
        gen.addJumpIfR0Equals(0xc0a801ff, "LABEL_283");
        gen.addLoadImmediate(R1, -24);
        gen.addLoadImmediate(R0, 0);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("ffffffffffff"), "LABEL_277");
        gen.addLoadImmediate(R1, -72);
        gen.addJump("LABEL_283");
        gen.addLoadImmediate(R1, -16);
        gen.addJump("LABEL_277");

        gen.defineLabel("LABEL_204");
        gen.addJumpIfR0Equals(0x86dd, "LABEL_225");
        gen.addLoadImmediate(R0, 0);
        gen.addLoadImmediate(R1, -48);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("ffffffffffff"), "LABEL_277");
        gen.addLoadImmediate(R1, -56);
        gen.addJump("LABEL_283");

        gen.defineLabel("LABEL_225");
        gen.addLoad8(R0, 20);
        gen.addJumpIfR0Equals(0x3a, "LABEL_241");
        gen.addLoadImmediate(R1, -104);
        gen.addLoad8(R0, 38);
        gen.addJumpIfR0Equals(0xff, "LABEL_283");
        gen.addLoadImmediate(R1, -32);
        gen.addJump("LABEL_277");

        gen.defineLabel("LABEL_241");
        gen.addLoad8(R0, 54);
        gen.addLoadImmediate(R1, -88);
        gen.addJumpIfR0Equals(0x85, "LABEL_283");
        gen.addJumpIfR0NotEquals(0x88, "LABEL_275");
        gen.addLoadImmediate(R0, 38);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("ff0200000000000000000000000000"), "LABEL_275");
        gen.addLoadImmediate(R1, -92);
        gen.addJump("LABEL_283");

        gen.defineLabel("LABEL_275");
        gen.addLoadImmediate(R1, -28);

        gen.defineLabel("LABEL_277");
        gen.addLoadData(R0, 0);
        gen.addAdd(1);
        gen.addStoreData(R0, 0);
        gen.addJump(PASS_LABEL);

        gen.defineLabel("LABEL_283");
        gen.addLoadData(R0, 0);
        gen.addAdd(1);
        gen.addStoreData(R0, 0);
        gen.addJump(DROP_LABEL);

        byte[] program = gen.generate();
        final String programString = toHexString(program).toLowerCase();
        final String referenceProgramHexString = "6bfcb03a01b8120c6b9494010c06006b907c010588a27c010088a47c00fb88b87c00f688cd7c00f188e17c00ec88e384003908066a0e6bdca2d40600010800060412147a18016bd882ca021a1c6b8c7ac900686bd4a2b706ffffffffffff6a266bbca2b204c0a801b36bf872a8120c84005808000a17821e1112149c00171fffab0d2a108210446a3239a20406f683d58f832b6bf4727e0a1e52f06bac7a7be06bb41a1e7e0000006effffffff6bb07e00000063c0a801ff6be868a25106ffffffffffff6bb872536bf072497c001086dd686bd0a23806ffffffffffff6bc8723a0a147a0b3a6b980a267a2eff6be072240a366ba87a23858218886a26a2040fff02000000000000000000000000006ba472086be4b03a01b87206b03a01b87201";
        assertEquals(referenceProgramHexString, programString);
    }

    @Test
    public void testFullApfV4ProgramGenerationNatTKeepAliveV4() throws IllegalInstructionException {
        ApfV4Generator gen = new ApfV4Generator(APF_VERSION_4);
        gen.addLoadImmediate(R1, -4);
        gen.addLoadData(R0, 0);
        gen.addAdd(1);
        gen.addStoreData(R0, 0);
        gen.addLoad16(R0, 12);
        gen.addLoadImmediate(R1, -108);
        gen.addJumpIfR0LessThan(0x600, "LABEL_345");
        gen.addLoadImmediate(R1, -112);
        gen.addJumpIfR0Equals(0x88a2, "LABEL_345");
        gen.addJumpIfR0Equals(0x88a4, "LABEL_345");
        gen.addJumpIfR0Equals(0x88b8, "LABEL_345");
        gen.addJumpIfR0Equals(0x88cd, "LABEL_345");
        gen.addJumpIfR0Equals(0x88e1, "LABEL_345");
        gen.addJumpIfR0Equals(0x88e3, "LABEL_345");
        gen.addJumpIfR0NotEquals(0x806, "LABEL_115");
        gen.addLoadImmediate(R0, 14);
        gen.addLoadImmediate(R1, -36);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("000108000604"), "LABEL_339");
        gen.addLoad16(R0, 20);
        gen.addJumpIfR0Equals(0x1, "LABEL_100");
        gen.addLoadImmediate(R1, -40);
        gen.addJumpIfR0NotEquals(0x2, "LABEL_339");
        gen.addLoad32(R0, 28);
        gen.addLoadImmediate(R1, -116);
        gen.addJumpIfR0Equals(0x0, "LABEL_345");
        gen.addLoadImmediate(R0, 0);
        gen.addLoadImmediate(R1, -44);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("ffffffffffff"), "LABEL_339");

        gen.defineLabel("LABEL_100");
        gen.addLoadImmediate(R0, 38);
        gen.addLoadImmediate(R1, -68);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("c0a801be"), "LABEL_345");
        gen.addLoadImmediate(R1, -8);
        gen.addJump("LABEL_339");

        gen.defineLabel("LABEL_115");
        gen.addLoad16(R0, 12);
        gen.addJumpIfR0NotEquals(0x800, "LABEL_263");
        gen.addLoad8(R0, 23);
        gen.addJumpIfR0NotEquals(0x11, "LABEL_157");
        gen.addLoad16(R0, 20);
        gen.addJumpIfR0AnyBitsSet(0x1fff, "LABEL_157");
        gen.addLoadFromMemory(R1, 13);
        gen.addLoad16Indexed(R0, 16);
        gen.addJumpIfR0NotEquals(0x44, "LABEL_157");
        gen.addLoadImmediate(R0, 50);
        gen.addAddR1();
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("ea42226789c0"), "LABEL_157");
        gen.addLoadImmediate(R1, -12);
        gen.addJump("LABEL_339");

        gen.defineLabel("LABEL_157");
        gen.addLoad8(R0, 30);
        gen.addAnd(240);
        gen.addLoadImmediate(R1, -84);
        gen.addJumpIfR0Equals(0xe0, "LABEL_345");
        gen.addLoadImmediate(R1, -76);
        gen.addLoad32(R0, 30);
        gen.addJumpIfR0Equals(0xffffffff, "LABEL_345");
        gen.addLoadImmediate(R1, -80);
        gen.addJumpIfR0Equals(0xc0a801ff, "LABEL_345");
        gen.addLoad8(R0, 23);
        gen.addJumpIfR0NotEquals(0x11, "LABEL_243");
        gen.addLoadImmediate(R0, 26);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("6b7a1f1fc0a801be"), "LABEL_243");
        gen.addLoadFromMemory(R0, 13);
        gen.addAdd(8);
        gen.addSwap();
        gen.addLoad16(R0, 16);
        gen.addNeg(R1);
        gen.addAddR1();
        gen.addJumpIfR0NotEquals(0x1, "LABEL_243");
        gen.addLoadFromMemory(R0, 13);
        gen.addAdd(14);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("1194ceca"), "LABEL_243");
        gen.addAdd(8);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("ff"), "LABEL_243");
        gen.addLoadImmediate(R1, -128);
        gen.addJump("LABEL_345");

        gen.defineLabel("LABEL_243");
        gen.addLoadImmediate(R1, -24);
        gen.addLoadImmediate(R0, 0);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("ffffffffffff"), "LABEL_339");
        gen.addLoadImmediate(R1, -72);
        gen.addJump("LABEL_345");
        gen.addLoadImmediate(R1, -16);
        gen.addJump("LABEL_339");

        gen.defineLabel("LABEL_263");
        gen.addJumpIfR0Equals(0x86dd, "LABEL_284");
        gen.addLoadImmediate(R0, 0);
        gen.addLoadImmediate(R1, -48);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("ffffffffffff"), "LABEL_339");
        gen.addLoadImmediate(R1, -56);
        gen.addJump("LABEL_345");

        gen.defineLabel("LABEL_284");
        gen.addLoad8(R0, 20);
        gen.addJumpIfR0Equals(0x0, "LABEL_339");
        gen.addJumpIfR0Equals(0x3a, "LABEL_303");
        gen.addLoadImmediate(R1, -104);
        gen.addLoad8(R0, 38);
        gen.addJumpIfR0Equals(0xff, "LABEL_345");
        gen.addLoadImmediate(R1, -32);
        gen.addJump("LABEL_339");

        gen.defineLabel("LABEL_303");
        gen.addLoad8(R0, 54);
        gen.addLoadImmediate(R1, -88);
        gen.addJumpIfR0Equals(0x85, "LABEL_345");
        gen.addJumpIfR0NotEquals(0x88, "LABEL_337");
        gen.addLoadImmediate(R0, 38);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("ff0200000000000000000000000000"), "LABEL_337");
        gen.addLoadImmediate(R1, -92);
        gen.addJump("LABEL_345");

        gen.defineLabel("LABEL_337");
        gen.addLoadImmediate(R1, -28);

        gen.defineLabel("LABEL_339");
        gen.addLoadData(R0, 0);
        gen.addAdd(1);
        gen.addStoreData(R0, 0);
        gen.addJump(PASS_LABEL);

        gen.defineLabel("LABEL_345");
        gen.addLoadData(R0, 0);
        gen.addAdd(1);
        gen.addStoreData(R0, 0);
        gen.addJump(DROP_LABEL);

        byte[] program = gen.generate();
        final String programString = toHexString(program).toLowerCase();
        final String referenceProgramHexString = "6bfcb03a01b8120c6b9494014a06006b907c014388a27c013e88a47c013988b87c013488cd7c012f88e17c012a88e384003f08066a0e6bdca40110000600010800060412147a1c016bd884010400021a1c6b8c7c01010000686bd4a2ef06ffffffffffff6a266bbca2ea04c0a801be6bf872e0120c84008d08000a17821e1112149c00171fffab0d2a108210446a3239a20406ea42226789c06bf472b60a1e52f06bac7ab3e06bb41a1e7e000000a6ffffffff6bb07e0000009bc0a801ff0a178230116a1aa223086b7a1f1fc0a801beaa0d3a08aa221210ab2139821501aa0d3a0ea20a041194ceca3a08a20401ff6b8072666be868a25406ffffffffffff6bb872566bf0724c7c001086dd686bd0a23b06ffffffffffff6bc8723d0a147a32007a0b3a6b980a267a2eff6be072240a366ba87a23858218886a26a2040fff02000000000000000000000000006ba472086be4b03a01b87206b03a01b87201";
        assertEquals(referenceProgramHexString, programString);
    }

    @Test
    public void testInfiniteLifetimeFullApfV4ProgramGeneration() throws IllegalInstructionException {
        ApfV4Generator gen = new ApfV4Generator(APF_VERSION_4);
        gen.addLoadImmediate(R1, -8);
        gen.addLoadData(R0, 0);
        gen.addAdd(1);
        gen.addStoreData(R0, 0);
        gen.addLoad16(R0, 12);
        gen.addLoadImmediate(R1, -120);
        gen.addJumpIfR0LessThan(0x600, "LABEL_582");
        gen.addLoadImmediate(R1, -124);
        gen.addJumpIfR0Equals(0x88a2, "LABEL_582");
        gen.addJumpIfR0Equals(0x88a4, "LABEL_582");
        gen.addJumpIfR0Equals(0x88b8, "LABEL_582");
        gen.addJumpIfR0Equals(0x88cd, "LABEL_582");
        gen.addJumpIfR0Equals(0x88e1, "LABEL_582");
        gen.addJumpIfR0Equals(0x88e3, "LABEL_582");
        gen.addJumpIfR0NotEquals(0x806, "LABEL_122");
        gen.addLoadImmediate(R0, 14);
        gen.addLoadImmediate(R1, -152);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("000108000604"), "LABEL_582");
        gen.addLoad16(R0, 20);
        gen.addJumpIfR0Equals(0x1, "LABEL_104");
        gen.addLoadImmediate(R1, -156);
        gen.addJumpIfR0NotEquals(0x2, "LABEL_582");
        gen.addLoad32(R0, 28);
        gen.addLoadImmediate(R1, -128);
        gen.addJumpIfR0Equals(0x0, "LABEL_582");
        gen.addLoadImmediate(R0, 0);
        gen.addLoadImmediate(R1, -56);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("ffffffffffff"), "LABEL_576");

        gen.defineLabel("LABEL_104");
        gen.addLoadImmediate(R0, 38);
        gen.addLoadImmediate(R1, -80);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("c0a801ec"), "LABEL_582");
        gen.addLoadImmediate(R1, -20);
        gen.addJump("LABEL_576");

        gen.defineLabel("LABEL_122");
        gen.addLoad16(R0, 12);
        gen.addJumpIfR0NotEquals(0x800, "LABEL_249");
        gen.addLoad8(R0, 23);
        gen.addJumpIfR0NotEquals(0x11, "LABEL_165");
        gen.addLoad16(R0, 20);
        gen.addJumpIfR0AnyBitsSet(0x1fff, "LABEL_165");
        gen.addLoadFromMemory(R1, 13);
        gen.addLoad16Indexed(R0, 16);
        gen.addJumpIfR0NotEquals(0x44, "LABEL_165");
        gen.addLoadImmediate(R0, 50);
        gen.addAddR1();
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("7e9046bc7008"), "LABEL_165");
        gen.addLoadImmediate(R1, -24);
        gen.addJump("LABEL_576");

        gen.defineLabel("LABEL_165");
        gen.addLoad8(R0, 30);
        gen.addAnd(240);
        gen.addLoadImmediate(R1, -96);
        gen.addJumpIfR0Equals(0xe0, "LABEL_582");
        gen.addLoadImmediate(R1, -88);
        gen.addLoad32(R0, 30);
        gen.addJumpIfR0Equals(0xffffffff, "LABEL_582");
        gen.addLoadImmediate(R1, -92);
        gen.addJumpIfR0Equals(0xc0a801ff, "LABEL_582");
        gen.addLoad8(R0, 23);
        gen.addJumpIfR0NotEquals(0x6, "LABEL_225");
        gen.addLoad16(R0, 20);
        gen.addJumpIfR0AnyBitsSet(0x1fff, "LABEL_225");
        gen.addLoadFromMemory(R1, 13);
        gen.addLoad16Indexed(R0, 16);
        gen.addJumpIfR0NotEquals(0x7, "LABEL_225");
        gen.addLoadImmediate(R1, -148);
        gen.addJump("LABEL_582");

        gen.defineLabel("LABEL_225");
        gen.addLoadImmediate(R1, -36);
        gen.addLoadImmediate(R0, 0);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("ffffffffffff"), "LABEL_576");
        gen.addLoadImmediate(R1, -84);
        gen.addJump("LABEL_582");
        gen.addLoadImmediate(R1, -28);
        gen.addJump("LABEL_576");

        gen.defineLabel("LABEL_249");
        gen.addJumpIfR0Equals(0x86dd, "LABEL_273");
        gen.addLoadImmediate(R0, 0);
        gen.addLoadImmediate(R1, -60);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("ffffffffffff"), "LABEL_576");
        gen.addLoadImmediate(R1, -68);
        gen.addJump("LABEL_582");

        gen.defineLabel("LABEL_273");
        gen.addLoad8(R0, 20);
        gen.addJumpIfR0Equals(0x0, "LABEL_576");
        gen.addJumpIfR0Equals(0x3a, "LABEL_297");
        gen.addLoadImmediate(R1, -116);
        gen.addLoad8(R0, 38);
        gen.addJumpIfR0Equals(0xff, "LABEL_582");
        gen.addLoadImmediate(R1, -44);
        gen.addJump("LABEL_576");

        gen.defineLabel("LABEL_297");
        gen.addLoad8(R0, 54);
        gen.addLoadImmediate(R1, -100);
        gen.addJumpIfR0Equals(0x85, "LABEL_582");
        gen.addJumpIfR0NotEquals(0x88, "LABEL_333");
        gen.addLoadImmediate(R0, 38);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("ff0200000000000000000000000000"), "LABEL_333");
        gen.addLoadImmediate(R1, -104);
        gen.addJump("LABEL_582");

        gen.defineLabel("LABEL_333");
        gen.addLoadFromMemory(R0, 14);
        gen.addJumpIfR0NotEquals(0x96, "LABEL_574");
        gen.addLoadFromMemory(R0, 15);
        gen.addJumpIfR0GreaterThan(0x48e, "LABEL_574");
        gen.addLoadImmediate(R0, 0);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("7e9046bc700828c68e23672c86dd60"), "LABEL_574");
        gen.addLoadImmediate(R0, 18);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("00603afffe800000000000002ac68efffe23672c"), "LABEL_574");
        gen.addLoadImmediate(R0, 54);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("8600"), "LABEL_574");
        gen.addLoadImmediate(R0, 58);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("40c0"), "LABEL_574");
        gen.addLoad16(R0, 60);
        gen.addJumpIfR0Equals(0x0, "LABEL_574");
        gen.addJumpIfR0LessThan(0xb4, "LABEL_421");
        gen.addJumpIfR0LessThan(0x91e, "LABEL_574");
        gen.addJumpIfR0GreaterThan(0x1b58, "LABEL_574");

        gen.defineLabel("LABEL_421");
        gen.addLoadImmediate(R0, 62);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("0000000000000000010128c68e23672c05010000000005dc030440c0"), "LABEL_574");
        gen.addLoad32(R0, 90);
        gen.addJumpIfR0Equals(0x0, "LABEL_574");
        gen.addJumpIfR0LessThan(0xb4, "LABEL_480");
        gen.addJumpIfR0LessThan(0x55555555, "LABEL_574");
        gen.addJumpIfR0GreaterThan(0xffffffffL, "LABEL_574");

        gen.defineLabel("LABEL_480");
        gen.addLoad32(R0, 94);
        gen.addJumpIfR0LessThan(0x55555555, "LABEL_574");
        gen.addJumpIfR0GreaterThan(0xffffffffL, "LABEL_574");
        gen.addLoadImmediate(R0, 98);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("000000002401fa000480f000000000000000000019030000"), "LABEL_574");
        gen.addLoad32(R0, 122);
        gen.addJumpIfR0Equals(0x0, "LABEL_574");
        gen.addJumpIfR0LessThan(0x78, "LABEL_547");
        gen.addJumpIfR0LessThan(0x91e, "LABEL_574");
        gen.addJumpIfR0GreaterThan(0x1b58, "LABEL_574");

        gen.defineLabel("LABEL_547");
        gen.addLoadImmediate(R0, 126);
        gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("2401fa000480f00000000000000000010701"), "LABEL_574");
        gen.addLoadImmediate(R1, -72);
        gen.addJump("LABEL_582");

        gen.defineLabel("LABEL_574");
        gen.addLoadImmediate(R1, -40);

        gen.defineLabel("LABEL_576");
        gen.addLoadData(R0, 0);
        gen.addAdd(1);
        gen.addStoreData(R0, 0);
        gen.addJump(PASS_LABEL);

        gen.defineLabel("LABEL_582");
        gen.addLoadData(R0, 0);
        gen.addAdd(1);
        gen.addStoreData(R0, 0);
        gen.addJump(DROP_LABEL);

        byte[] program = gen.generate();
        final String programString = toHexString(program).toLowerCase();
        final String referenceProgramHexString = "6bf8b03a01b8120c6b8894023706006b847c023088a27c022b88a47c022688b87c022188cd7c021c88e17c021788e384004608066a0e6dff68a40202000600010800060412147a1f016dff648401f500021a1c6b807c01ec0000686bc8a401d80006ffffffffffff6a266bb0a401d10004c0a801ec6bec7401c6120c84007808000a17821f1112149c00181fffab0d2a108211446a3239a205067e9046bc70086be874019b0a1e52f06ba07c019600e06ba81a1e7e00000189ffffffff6ba47e0000017ec0a801ff0a1782140612149c000d1fffab0d2a108206076dff6c7401656bdc68a401510006ffffffffffff6bac7401526be47401477c001386dd686bc4a401340006ffffffffffff6bbc7401350a147c012800007a0e3a6b8c0a267c012200ff6bd47401170a366b9c7c011400858218886a26a2040fff02000000000000000000000000006b9872f9aa0e82ec96aa0f8c00e5048e68a2d20f7e9046bc700828c68e23672c86dd606a12a2b91400603afffe800000000000002ac68efffe23672c6a36a2b20286006a3aa2ab0240c0123c7aa600920ab494009e091e8c00991b586a3ea2781c0000000000000000010128c68e23672c05010000000005dc030440c01a5a7a73009212b49600000067555555558e0000005effffffff1a5e9600000053555555558e0000004affffffff6a62a22d18000000002401fa000480f0000000000000000000190300001a7a7a2800920a78940020091e8c001b1b586a7ea204122401fa000480f000000000000000000107016bb872086bd8b03a01b87206b03a01b87201";
        assertEquals(referenceProgramHexString, programString);
    }
}
