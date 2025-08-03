/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.net.cts;

import static android.net.DnsResolver.CLASS_IN;
import static android.net.DnsResolver.FLAG_EMPTY;
import static android.net.DnsResolver.FLAG_NO_CACHE_LOOKUP;
import static android.net.DnsResolver.TYPE_A;
import static android.net.DnsResolver.TYPE_AAAA;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.cts.util.CtsNetUtils.TestNetworkCallback;
import static android.provider.DeviceConfig.NAMESPACE_CONNECTIVITY;
import static android.system.OsConstants.ETIMEDOUT;

import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.DnsResolver;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.ParseException;
import android.net.cts.util.CtsNetUtils;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.platform.test.annotations.AppModeFull;
import android.provider.Settings;
import android.system.ErrnoException;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.DnsPacket;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;
import com.android.testutils.DeviceConfigRule;
import com.android.testutils.DnsResolverModuleTest;
import com.android.testutils.SkipPresubmit;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@AppModeFull(reason = "WRITE_SECURE_SETTINGS permission can't be granted to instant apps")
@RunWith(AndroidJUnit4.class)
public class DnsResolverTest {
    @ClassRule
    public static final DeviceConfigRule DEVICE_CONFIG_CLASS_RULE = new DeviceConfigRule();
    @Rule
    public final DevSdkIgnoreRule ignoreRule = new DevSdkIgnoreRule();

    private static final String TAG = "DnsResolverTest";
    private static final char[] HEX_CHARS = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    static final String TEST_DOMAIN = "www.google.com";
    static final String TEST_NX_DOMAIN = "test1-nx.metric.gstatic.com";
    static final String INVALID_PRIVATE_DNS_SERVER = "invalid.google";
    static final String GOOGLE_PRIVATE_DNS_SERVER = "dns.google";
    static final byte[] TEST_BLOB = new byte[]{
            /* Header */
            0x55, 0x66, /* Transaction ID */
            0x01, 0x00, /* Flags */
            0x00, 0x01, /* Questions */
            0x00, 0x00, /* Answer RRs */
            0x00, 0x00, /* Authority RRs */
            0x00, 0x00, /* Additional RRs */
            /* Queries */
            0x03, 0x77, 0x77, 0x77, 0x06, 0x67, 0x6F, 0x6F, 0x67, 0x6c, 0x65,
            0x03, 0x63, 0x6f, 0x6d, 0x00, /* Name */
            0x00, 0x01, /* Type */
            0x00, 0x01  /* Class */
    };
    static final int TIMEOUT_MS = 12_000;
    static final int CANCEL_TIMEOUT_MS = 3_000;
    static final int CANCEL_RETRY_TIMES = 5;
    static final int QUERY_TIMES = 10;
    static final int NXDOMAIN = 3;

    private Context mContext;
    private ContentResolver mCR;
    private ConnectivityManager mCM;
    private PackageManager mPackageManager;
    private CtsNetUtils mCtsNetUtils;
    private Executor mExecutor;
    private Executor mExecutorInline;
    private DnsResolver mDns;

    private TestNetworkCallback mWifiRequestCallback = null;

    /**
     * @see BeforeClass
     */
    @BeforeClass
    public static void beforeClass() throws Exception {
        // Use async private DNS resolution to avoid flakes due to races applying the setting
        DEVICE_CONFIG_CLASS_RULE.setConfig(NAMESPACE_CONNECTIVITY,
                "networkmonitor_async_privdns_resolution", "1");
        // Make sure NetworkMonitor is restarted before and after the test so the flag is applied
        // and cleaned up.
        maybeToggleWifiAndCell();
        DEVICE_CONFIG_CLASS_RULE.runAfterNextCleanup(DnsResolverTest::maybeToggleWifiAndCell);
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mCM = mContext.getSystemService(ConnectivityManager.class);
        mDns = DnsResolver.getInstance();
        mExecutor = new Handler(Looper.getMainLooper())::post;
        mExecutorInline = (Runnable r) -> r.run();
        mCR = mContext.getContentResolver();
        mCtsNetUtils = new CtsNetUtils(mContext);
        mCtsNetUtils.storePrivateDnsSetting();
        mPackageManager = mContext.getPackageManager();
    }

    @After
    public void tearDown() throws Exception {
        mCtsNetUtils.restorePrivateDnsSetting();
        if (mWifiRequestCallback != null) {
            mCM.unregisterNetworkCallback(mWifiRequestCallback);
        }
    }

    private static void maybeToggleWifiAndCell() throws Exception {
        final CtsNetUtils utils = new CtsNetUtils(InstrumentationRegistry.getContext());
        utils.reconnectWifiIfSupported();
        utils.reconnectCellIfSupported();
    }

    private static String byteArrayToHexString(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; ++i) {
            int b = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_CHARS[b >>> 4];
            hexChars[i * 2 + 1] = HEX_CHARS[b & 0x0F];
        }
        return new String(hexChars);
    }

    private Network[] getTestableNetworks() {
        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            // File a NetworkRequest for Wi-Fi, so it connects even if a higher-scoring
            // network, such as Ethernet, is already connected.
            final NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            mWifiRequestCallback = new TestNetworkCallback();
            mCM.requestNetwork(request, mWifiRequestCallback);
            mCtsNetUtils.ensureWifiConnected();
        }
        final ArrayList<Network> testableNetworks = new ArrayList<Network>();
        for (Network network : mCM.getAllNetworks()) {
            final NetworkCapabilities nc = mCM.getNetworkCapabilities(network);
            if (nc != null
                    && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                    && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                testableNetworks.add(network);
            }
        }

        assertTrue(
                "This test requires that at least one network be connected. " +
                        "Please ensure that the device is connected to a network.",
                testableNetworks.size() >= 1);
        // In order to test query with null network, add null as an element.
        // Test cases which query with null network will go on default network.
        testableNetworks.add(null);
        return testableNetworks.toArray(new Network[0]);
    }

    static private void assertGreaterThan(String msg, int first, int second) {
        assertTrue(msg + " Excepted " + first + " to be greater than " + second, first > second);
    }

    private static class DnsParseException extends Exception {
        public DnsParseException(String msg) {
            super(msg);
        }
    }

    private static class DnsAnswer extends DnsPacket {
        DnsAnswer(@NonNull byte[] data) throws DnsParseException {
            super(data);

            // Check QR field.(query (0), or a response (1)).
            if ((mHeader.getFlags() & (1 << 15)) == 0) {
                throw new DnsParseException("Not an answer packet");
            }
        }

        int getRcode() {
            return mHeader.getFlags() & 0x0F;
        }

        int getANCount() {
            return mHeader.getRecordCount(ANSECTION);
        }

        int getQDCount() {
            return mHeader.getRecordCount(QDSECTION);
        }
    }

    /**
     * A query callback that ensures that the query is cancelled and that onAnswer is never
     * called. If the query succeeds before it is cancelled, needRetry will return true so the
     * test can retry.
     */
    class VerifyCancelCallback implements DnsResolver.Callback<byte[]> {
        private final CountDownLatch mLatch = new CountDownLatch(1);
        private final String mMsg;
        private final CancellationSignal mCancelSignal;
        private int mRcode;
        private DnsAnswer mDnsAnswer;
        private String mErrorMsg = null;

        VerifyCancelCallback(@NonNull String msg, @Nullable CancellationSignal cancel) {
            mMsg = msg;
            mCancelSignal = cancel;
        }

        VerifyCancelCallback(@NonNull String msg) {
            this(msg, null);
        }

        public boolean waitForAnswer(int timeout) throws InterruptedException {
            return mLatch.await(timeout, TimeUnit.MILLISECONDS);
        }

        public boolean waitForAnswer() throws InterruptedException {
            return waitForAnswer(TIMEOUT_MS);
        }

        public boolean needRetry() throws InterruptedException {
            return mLatch.await(CANCEL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        @Override
        public void onAnswer(@NonNull byte[] answer, int rcode) {
            if (mCancelSignal != null && mCancelSignal.isCanceled()) {
                mErrorMsg = mMsg + " should not have returned any answers";
                mLatch.countDown();
                return;
            }

            mRcode = rcode;
            try {
                mDnsAnswer = new DnsAnswer(answer);
            } catch (ParseException | DnsParseException e) {
                mErrorMsg = mMsg + e.getMessage();
                mLatch.countDown();
                return;
            }
            Log.d(TAG, "Reported blob: " + byteArrayToHexString(answer));
            mLatch.countDown();
        }

        @Override
        public void onError(@NonNull DnsResolver.DnsException error) {
            mErrorMsg = mMsg + error.getMessage();
            mLatch.countDown();
        }

        private void assertValidAnswer() {
            assertNull(mErrorMsg);
            assertNotNull(mMsg + " No valid answer", mDnsAnswer);
            assertEquals(mMsg + " Unexpected error: reported rcode" + mRcode +
                    " blob's rcode " + mDnsAnswer.getRcode(), mRcode, mDnsAnswer.getRcode());
        }

        public void assertHasAnswer() {
            assertValidAnswer();
            // Check rcode field.(0, No error condition).
            assertEquals(mMsg + " Response error, rcode: " + mRcode, mRcode, 0);
            // Check answer counts.
            assertGreaterThan(mMsg + " No answer found", mDnsAnswer.getANCount(), 0);
            // Check question counts.
            assertGreaterThan(mMsg + " No question found", mDnsAnswer.getQDCount(), 0);
        }

        public void assertNXDomain() {
            assertValidAnswer();
            // Check rcode field.(3, NXDomain).
            assertEquals(mMsg + " Unexpected rcode: " + mRcode, mRcode, NXDOMAIN);
            // Check answer counts. Expect 0 answer.
            assertEquals(mMsg + " Not an empty answer", mDnsAnswer.getANCount(), 0);
            // Check question counts.
            assertGreaterThan(mMsg + " No question found", mDnsAnswer.getQDCount(), 0);
        }

        public void assertEmptyAnswer() {
            assertValidAnswer();
            // Check rcode field.(0, No error condition).
            assertEquals(mMsg + " Response error, rcode: " + mRcode, mRcode, 0);
            // Check answer counts. Expect 0 answer.
            assertEquals(mMsg + " Not an empty answer", mDnsAnswer.getANCount(), 0);
            // Check question counts.
            assertGreaterThan(mMsg + " No question found", mDnsAnswer.getQDCount(), 0);
        }
    }

    @Test
    @DnsResolverModuleTest
    public void testRawQuery() throws Exception {
        doTestRawQuery(mExecutor);
    }

    @Test
    @DnsResolverModuleTest
    public void testRawQueryInline() throws Exception {
        doTestRawQuery(mExecutorInline);
    }

    @Test
    @DnsResolverModuleTest
    public void testRawQueryBlob() throws Exception {
        doTestRawQueryBlob(mExecutor);
    }

    @Test
    @DnsResolverModuleTest
    public void testRawQueryBlobInline() throws Exception {
        doTestRawQueryBlob(mExecutorInline);
    }

    @Test
    @DnsResolverModuleTest
    public void testRawQueryRoot() throws Exception {
        doTestRawQueryRoot(mExecutor);
    }

    @Test
    @DnsResolverModuleTest
    public void testRawQueryRootInline() throws Exception {
        doTestRawQueryRoot(mExecutorInline);
    }

    @Test
    @DnsResolverModuleTest
    public void testRawQueryNXDomain() throws Exception {
        doTestRawQueryNXDomain(mExecutor);
    }

    @Test
    @DnsResolverModuleTest
    public void testRawQueryNXDomainInline() throws Exception {
        doTestRawQueryNXDomain(mExecutorInline);
    }

    @Test
    @DnsResolverModuleTest
    public void testRawQueryNXDomainWithPrivateDns() throws Exception {
        doTestRawQueryNXDomainWithPrivateDns(mExecutor);
    }

    @Test
    @DnsResolverModuleTest
    public void testRawQueryNXDomainInlineWithPrivateDns() throws Exception {
        doTestRawQueryNXDomainWithPrivateDns(mExecutorInline);
    }

    public void doTestRawQuery(Executor executor) throws InterruptedException {
        final String msg = "RawQuery " + TEST_DOMAIN;
        for (Network network : getTestableNetworks()) {
            final VerifyCancelCallback callback = new VerifyCancelCallback(msg);
            mDns.rawQuery(network, TEST_DOMAIN, CLASS_IN, TYPE_AAAA, FLAG_NO_CACHE_LOOKUP,
                    executor, null, callback);

            assertTrue(msg + " but no answer after " + TIMEOUT_MS + "ms.",
                    callback.waitForAnswer());
            callback.assertHasAnswer();
        }
    }

    public void doTestRawQueryBlob(Executor executor) throws InterruptedException {
        final byte[] blob = new byte[]{
                /* Header */
                0x55, 0x66, /* Transaction ID */
                0x01, 0x00, /* Flags */
                0x00, 0x01, /* Questions */
                0x00, 0x00, /* Answer RRs */
                0x00, 0x00, /* Authority RRs */
                0x00, 0x00, /* Additional RRs */
                /* Queries */
                0x03, 0x77, 0x77, 0x77, 0x06, 0x67, 0x6F, 0x6F, 0x67, 0x6c, 0x65,
                0x03, 0x63, 0x6f, 0x6d, 0x00, /* Name */
                0x00, 0x01, /* Type */
                0x00, 0x01  /* Class */
        };
        final String msg = "RawQuery blob " + byteArrayToHexString(blob);
        for (Network network : getTestableNetworks()) {
            final VerifyCancelCallback callback = new VerifyCancelCallback(msg);
            mDns.rawQuery(network, blob, FLAG_NO_CACHE_LOOKUP, executor, null, callback);

            assertTrue(msg + " but no answer after " + TIMEOUT_MS + "ms.",
                    callback.waitForAnswer());
            callback.assertHasAnswer();
        }
    }

    public void doTestRawQueryRoot(Executor executor) throws InterruptedException {
        final String dname = "";
        final String msg = "RawQuery empty dname(ROOT) ";
        for (Network network : getTestableNetworks()) {
            final VerifyCancelCallback callback = new VerifyCancelCallback(msg);
            mDns.rawQuery(network, dname, CLASS_IN, TYPE_AAAA, FLAG_NO_CACHE_LOOKUP,
                    executor, null, callback);

            assertTrue(msg + " but no answer after " + TIMEOUT_MS + "ms.",
                    callback.waitForAnswer());
            // Except no answer record because the root does not have AAAA records.
            callback.assertEmptyAnswer();
        }
    }

    public void doTestRawQueryNXDomain(Executor executor) throws InterruptedException {
        final String msg = "RawQuery " + TEST_NX_DOMAIN;

        for (Network network : getTestableNetworks()) {
            final NetworkCapabilities nc = (network != null)
                    ? mCM.getNetworkCapabilities(network)
                    : mCM.getNetworkCapabilities(mCM.getActiveNetwork());
            assertNotNull("Couldn't determine NetworkCapabilities for " + network, nc);
            // Some cellular networks configure their DNS servers never to return NXDOMAIN, so don't
            // test NXDOMAIN on these DNS servers.
            // b/144521720
            if (nc.hasTransport(TRANSPORT_CELLULAR)) continue;
            final VerifyCancelCallback callback = new VerifyCancelCallback(msg);
            mDns.rawQuery(network, TEST_NX_DOMAIN, CLASS_IN, TYPE_AAAA, FLAG_NO_CACHE_LOOKUP,
                    executor, null, callback);

            assertTrue(msg + " but no answer after " + TIMEOUT_MS + "ms.",
                    callback.waitForAnswer());
            callback.assertNXDomain();
        }
    }

    public void doTestRawQueryNXDomainWithPrivateDns(Executor executor)
            throws InterruptedException {
        final String msg = "RawQuery " + TEST_NX_DOMAIN + " with private DNS";
        // Enable private DNS strict mode and set server to dns.google before doing NxDomain test.
        // b/144521720
        mCtsNetUtils.setPrivateDnsStrictMode(GOOGLE_PRIVATE_DNS_SERVER);
        for (Network network :  getTestableNetworks()) {
            final Network networkForPrivateDns =
                    (network != null) ? network : mCM.getActiveNetwork();
            assertNotNull("Can't find network to await private DNS on", networkForPrivateDns);
            mCtsNetUtils.awaitPrivateDnsSetting(msg + " wait private DNS setting timeout",
                    networkForPrivateDns, GOOGLE_PRIVATE_DNS_SERVER, true);
            final VerifyCancelCallback callback = new VerifyCancelCallback(msg);
            mDns.rawQuery(network, TEST_NX_DOMAIN, CLASS_IN, TYPE_AAAA, FLAG_NO_CACHE_LOOKUP,
                    executor, null, callback);

            assertTrue(msg + " but no answer after " + TIMEOUT_MS + "ms.",
                    callback.waitForAnswer());
            callback.assertNXDomain();
        }
    }

    @Test
    public void testRawQueryCancel() throws InterruptedException {
        final String msg = "Test cancel RawQuery " + TEST_DOMAIN;
        // Start a DNS query and the cancel it immediately. Use VerifyCancelCallback to expect
        // that the query is cancelled before it succeeds. If it is not cancelled before it
        // succeeds, retry the test until it is.
        for (Network network : getTestableNetworks()) {
            boolean retry = false;
            int round = 0;
            do {
                if (++round > CANCEL_RETRY_TIMES) {
                    fail(msg + " cancel failed " + CANCEL_RETRY_TIMES + " times");
                }
                final CountDownLatch latch = new CountDownLatch(1);
                final CancellationSignal cancelSignal = new CancellationSignal();
                final VerifyCancelCallback callback = new VerifyCancelCallback(msg, cancelSignal);
                mDns.rawQuery(network, TEST_DOMAIN, CLASS_IN, TYPE_AAAA, FLAG_EMPTY,
                        mExecutor, cancelSignal, callback);
                mExecutor.execute(() -> {
                    cancelSignal.cancel();
                    latch.countDown();
                });

                retry = callback.needRetry();
                assertTrue(msg + " query was not cancelled",
                        latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            } while (retry);
        }
    }

    @Test
    public void testRawQueryBlobCancel() throws InterruptedException {
        final String msg = "Test cancel RawQuery blob " + byteArrayToHexString(TEST_BLOB);
        // Start a DNS query and the cancel it immediately. Use VerifyCancelCallback to expect
        // that the query is cancelled before it succeeds. If it is not cancelled before it
        // succeeds, retry the test until it is.
        for (Network network : getTestableNetworks()) {
            boolean retry = false;
            int round = 0;
            do {
                if (++round > CANCEL_RETRY_TIMES) {
                    fail(msg + " cancel failed " + CANCEL_RETRY_TIMES + " times");
                }
                final CountDownLatch latch = new CountDownLatch(1);
                final CancellationSignal cancelSignal = new CancellationSignal();
                final VerifyCancelCallback callback = new VerifyCancelCallback(msg, cancelSignal);
                mDns.rawQuery(network, TEST_BLOB, FLAG_EMPTY, mExecutor, cancelSignal, callback);
                mExecutor.execute(() -> {
                    cancelSignal.cancel();
                    latch.countDown();
                });

                retry = callback.needRetry();
                assertTrue(msg + " cancel is not done",
                        latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            } while (retry);
        }
    }

    @Test
    public void testCancelBeforeQuery() throws InterruptedException {
        final String msg = "Test cancelled RawQuery " + TEST_DOMAIN;
        for (Network network : getTestableNetworks()) {
            final VerifyCancelCallback callback = new VerifyCancelCallback(msg);
            final CancellationSignal cancelSignal = new CancellationSignal();
            cancelSignal.cancel();
            mDns.rawQuery(network, TEST_DOMAIN, CLASS_IN, TYPE_AAAA, FLAG_EMPTY,
                    mExecutor, cancelSignal, callback);

            assertTrue(msg + " should not return any answers",
                    !callback.waitForAnswer(CANCEL_TIMEOUT_MS));
        }
    }

    /**
     * A query callback for InetAddress that ensures that the query is
     * cancelled and that onAnswer is never called. If the query succeeds
     * before it is cancelled, needRetry will return true so the
     * test can retry.
     */
    class VerifyCancelInetAddressCallback implements DnsResolver.Callback<List<InetAddress>> {
        private final CountDownLatch mLatch = new CountDownLatch(1);
        private final String mMsg;
        private final List<InetAddress> mAnswers;
        private final CancellationSignal mCancelSignal;
        private String mErrorMsg = null;

        VerifyCancelInetAddressCallback(@NonNull String msg, @Nullable CancellationSignal cancel) {
            this.mMsg = msg;
            this.mCancelSignal = cancel;
            mAnswers = new ArrayList<>();
        }

        public boolean waitForAnswer() throws InterruptedException {
            return mLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        public boolean needRetry() throws InterruptedException {
            return mLatch.await(CANCEL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        public boolean isAnswerEmpty() {
            return mAnswers.isEmpty();
        }

        public boolean hasIpv6Answer() {
            for (InetAddress answer : mAnswers) {
                if (answer instanceof Inet6Address) return true;
            }
            return false;
        }

        public boolean hasIpv4Answer() {
            for (InetAddress answer : mAnswers) {
                if (answer instanceof Inet4Address) return true;
            }
            return false;
        }

        public void assertNoError() {
            assertNull(mErrorMsg);
        }

        @Override
        public void onAnswer(@NonNull List<InetAddress> answerList, int rcode) {
            if (mCancelSignal != null && mCancelSignal.isCanceled()) {
                mErrorMsg = mMsg + " should not have returned any answers";
                mLatch.countDown();
                return;
            }
            for (InetAddress addr : answerList) {
                Log.d(TAG, "Reported addr: " + addr.toString());
            }
            mAnswers.clear();
            mAnswers.addAll(answerList);
            mLatch.countDown();
        }

        @Override
        public void onError(@NonNull DnsResolver.DnsException error) {
            mErrorMsg = mMsg + error.getMessage();
            mLatch.countDown();
        }
    }

    @Test
    @DnsResolverModuleTest
    public void testQueryForInetAddress() throws Exception {
        doTestQueryForInetAddress(mExecutor);
    }

    @Test
    @DnsResolverModuleTest
    public void testQueryForInetAddressInline() throws Exception {
        doTestQueryForInetAddress(mExecutorInline);
    }

    @Test
    @DnsResolverModuleTest
    public void testQueryForInetAddressIpv4() throws Exception {
        doTestQueryForInetAddressIpv4(mExecutor);
    }

    @Test
    @DnsResolverModuleTest
    public void testQueryForInetAddressIpv4Inline() throws Exception {
        doTestQueryForInetAddressIpv4(mExecutorInline);
    }

    @Test
    @DnsResolverModuleTest
    public void testQueryForInetAddressIpv6() throws Exception {
        doTestQueryForInetAddressIpv6(mExecutor);
    }

    @Test
    @DnsResolverModuleTest
    public void testQueryForInetAddressIpv6Inline() throws Exception {
        doTestQueryForInetAddressIpv6(mExecutorInline);
    }

    @Test
    @DnsResolverModuleTest
    public void testContinuousQueries() throws Exception {
        doTestContinuousQueries(mExecutor);
    }

    @Test
    @DnsResolverModuleTest
    @SkipPresubmit(reason = "Flaky: b/159762682; add to presubmit after fixing")
    public void testContinuousQueriesInline() throws Exception {
        doTestContinuousQueries(mExecutorInline);
    }

    public void doTestQueryForInetAddress(Executor executor) throws InterruptedException {
        final String msg = "Test query for InetAddress " + TEST_DOMAIN;
        for (Network network : getTestableNetworks()) {
            final VerifyCancelInetAddressCallback callback =
                    new VerifyCancelInetAddressCallback(msg, null);
            mDns.query(network, TEST_DOMAIN, FLAG_NO_CACHE_LOOKUP, executor, null, callback);

            assertTrue(msg + " but no answer after " + TIMEOUT_MS + "ms.",
                    callback.waitForAnswer());
            callback.assertNoError();
            assertTrue(msg + " returned 0 results", !callback.isAnswerEmpty());
        }
    }

    @Test
    public void testQueryCancelForInetAddress() throws InterruptedException {
        final String msg = "Test cancel query for InetAddress " + TEST_DOMAIN;
        // Start a DNS query and the cancel it immediately. Use VerifyCancelInetAddressCallback to
        // expect that the query is cancelled before it succeeds. If it is not cancelled before it
        // succeeds, retry the test until it is.
        for (Network network : getTestableNetworks()) {
            boolean retry = false;
            int round = 0;
            do {
                if (++round > CANCEL_RETRY_TIMES) {
                    fail(msg + " cancel failed " + CANCEL_RETRY_TIMES + " times");
                }
                final CountDownLatch latch = new CountDownLatch(1);
                final CancellationSignal cancelSignal = new CancellationSignal();
                final VerifyCancelInetAddressCallback callback =
                        new VerifyCancelInetAddressCallback(msg, cancelSignal);
                mDns.query(network, TEST_DOMAIN, FLAG_EMPTY, mExecutor, cancelSignal, callback);
                mExecutor.execute(() -> {
                    cancelSignal.cancel();
                    latch.countDown();
                });

                retry = callback.needRetry();
                assertTrue(msg + " query was not cancelled",
                        latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            } while (retry);
        }
    }

    public void doTestQueryForInetAddressIpv4(Executor executor) throws InterruptedException {
        final String msg = "Test query for IPv4 InetAddress " + TEST_DOMAIN;
        for (Network network : getTestableNetworks()) {
            final VerifyCancelInetAddressCallback callback =
                    new VerifyCancelInetAddressCallback(msg, null);
            mDns.query(network, TEST_DOMAIN, TYPE_A, FLAG_NO_CACHE_LOOKUP,
                    executor, null, callback);

            assertTrue(msg + " but no answer after " + TIMEOUT_MS + "ms.",
                    callback.waitForAnswer());
            callback.assertNoError();
            assertTrue(msg + " returned 0 results", !callback.isAnswerEmpty());
            assertTrue(msg + " returned Ipv6 results", !callback.hasIpv6Answer());
        }
    }

    public void doTestQueryForInetAddressIpv6(Executor executor) throws InterruptedException {
        final String msg = "Test query for IPv6 InetAddress " + TEST_DOMAIN;
        for (Network network : getTestableNetworks()) {
            final VerifyCancelInetAddressCallback callback =
                    new VerifyCancelInetAddressCallback(msg, null);
            mDns.query(network, TEST_DOMAIN, TYPE_AAAA, FLAG_NO_CACHE_LOOKUP,
                    executor, null, callback);

            assertTrue(msg + " but no answer after " + TIMEOUT_MS + "ms.",
                    callback.waitForAnswer());
            callback.assertNoError();
            assertTrue(msg + " returned 0 results", !callback.isAnswerEmpty());
            assertTrue(msg + " returned Ipv4 results", !callback.hasIpv4Answer());
        }
    }

    @Test
    public void testPrivateDnsBypass() throws InterruptedException {
        final String dataStallSetting = Settings.Global.getString(mCR,
                Settings.Global.DATA_STALL_RECOVERY_ON_BAD_NETWORK);
        Settings.Global.putInt(mCR, Settings.Global.DATA_STALL_RECOVERY_ON_BAD_NETWORK, 0);
        try {
            doTestPrivateDnsBypass();
        } finally {
            Settings.Global.putString(mCR, Settings.Global.DATA_STALL_RECOVERY_ON_BAD_NETWORK,
                    dataStallSetting);
        }
    }

    private void doTestPrivateDnsBypass() throws InterruptedException {
        final Network[] testNetworks = getTestableNetworks();

        // Set an invalid private DNS server
        mCtsNetUtils.setPrivateDnsStrictMode(INVALID_PRIVATE_DNS_SERVER);
        final String msg = "Test PrivateDnsBypass " + TEST_DOMAIN;
        for (Network network : testNetworks) {
            // This test cannot be ran with null network because we need to explicitly pass a
            // private DNS bypassable network or bind one.
            if (network == null) continue;

            // wait for private DNS setting propagating
            mCtsNetUtils.awaitPrivateDnsSetting(msg + " wait private DNS setting timeout",
                    network, INVALID_PRIVATE_DNS_SERVER, false);

            final CountDownLatch latch = new CountDownLatch(1);
            final DnsResolver.Callback<List<InetAddress>> errorCallback =
                    new DnsResolver.Callback<List<InetAddress>>() {
                        @Override
                        public void onAnswer(@NonNull List<InetAddress> answerList, int rcode) {
                            fail(msg + " should not get valid answer");
                        }

                        @Override
                        public void onError(@NonNull DnsResolver.DnsException error) {
                            assertEquals(DnsResolver.ERROR_SYSTEM, error.code);
                            assertEquals(ETIMEDOUT, ((ErrnoException) error.getCause()).errno);
                            latch.countDown();
                        }
                    };
            // Private DNS strict mode with invalid DNS server is set
            // Expect no valid answer returned but ErrnoException with ETIMEDOUT
            mDns.query(network, TEST_DOMAIN, FLAG_NO_CACHE_LOOKUP, mExecutor, null, errorCallback);

            assertTrue(msg + " invalid server round. No response after " + TIMEOUT_MS + "ms.",
                    latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

            final VerifyCancelInetAddressCallback callback =
                    new VerifyCancelInetAddressCallback(msg, null);
            // Bypass privateDns, expect query works fine
            mDns.query(network.getPrivateDnsBypassingCopy(),
                    TEST_DOMAIN, FLAG_NO_CACHE_LOOKUP, mExecutor, null, callback);

            assertTrue(msg + " bypass private DNS round. No answer after " + TIMEOUT_MS + "ms.",
                    callback.waitForAnswer());
            callback.assertNoError();
            assertTrue(msg + " returned 0 results", !callback.isAnswerEmpty());

            // To ensure private DNS bypass still work even if passing null network.
            // Bind process network with a private DNS bypassable network.
            mCM.bindProcessToNetwork(network.getPrivateDnsBypassingCopy());
            final VerifyCancelInetAddressCallback callbackWithNullNetwork =
                    new VerifyCancelInetAddressCallback(msg + " with null network ", null);
            mDns.query(null,
                    TEST_DOMAIN, FLAG_NO_CACHE_LOOKUP, mExecutor, null, callbackWithNullNetwork);

            assertTrue(msg + " with null network bypass private DNS round. No answer after " +
                    TIMEOUT_MS + "ms.", callbackWithNullNetwork.waitForAnswer());
            callbackWithNullNetwork.assertNoError();
            assertTrue(msg + " with null network returned 0 results",
                    !callbackWithNullNetwork.isAnswerEmpty());

            // Reset process network to default.
            mCM.bindProcessToNetwork(null);
        }
    }

    public void doTestContinuousQueries(Executor executor) throws InterruptedException {
        final String msg = "Test continuous " + QUERY_TIMES + " queries " + TEST_DOMAIN;
        for (Network network : getTestableNetworks()) {
            for (int i = 0; i < QUERY_TIMES ; ++i) {
                final VerifyCancelInetAddressCallback callback =
                        new VerifyCancelInetAddressCallback(msg, null);
                // query v6/v4 in turn
                boolean queryV6 = (i % 2 == 0);
                mDns.query(network, TEST_DOMAIN, queryV6 ? TYPE_AAAA : TYPE_A,
                        FLAG_NO_CACHE_LOOKUP, executor, null, callback);

                assertTrue(msg + " but no answer after " + TIMEOUT_MS + "ms.",
                        callback.waitForAnswer());
                callback.assertNoError();
                assertTrue(msg + " returned 0 results", !callback.isAnswerEmpty());
                assertTrue(msg + " returned " + (queryV6 ? "Ipv4" : "Ipv6") + " results",
                        queryV6 ? !callback.hasIpv4Answer() : !callback.hasIpv6Answer());
            }
        }
    }

    /** Verifies that DnsResolver.DnsException can be subclassed and its constructor re-used. */
    @Test @IgnoreUpTo(SC_V2) // TODO: Use to Build.VERSION_CODES.SC_V2 when available
    public void testDnsExceptionConstructor() throws InterruptedException {
        class TestDnsException extends DnsResolver.DnsException {
            TestDnsException(int code, @Nullable Throwable cause) {
                super(code, cause);
            }
        }
        try {
            throw new TestDnsException(DnsResolver.ERROR_SYSTEM, null);
        } catch (DnsResolver.DnsException e) {
            assertEquals(DnsResolver.ERROR_SYSTEM, e.code);
        }
    }

    @Test
    public void testNoRawBinderAccess() {
        assertNull(mContext.getSystemService("dnsresolver"));
    }
}
