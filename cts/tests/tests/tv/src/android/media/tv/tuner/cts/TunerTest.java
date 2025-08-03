/*
 * Copyright 2019 The Android Open Source Project
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

package android.media.tv.tuner.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.AudioPresentation;
import android.media.tv.tuner.DemuxCapabilities;
import android.media.tv.tuner.DemuxInfo;
import android.media.tv.tuner.Descrambler;
import android.media.tv.tuner.Lnb;
import android.media.tv.tuner.LnbCallback;
import android.media.tv.tuner.Tuner;
import android.media.tv.tuner.TunerVersionChecker;
import android.media.tv.tuner.dvr.DvrPlayback;
import android.media.tv.tuner.dvr.DvrRecorder;
import android.media.tv.tuner.dvr.OnPlaybackStatusChangedListener;
import android.media.tv.tuner.dvr.OnRecordStatusChangedListener;
import android.media.tv.tuner.filter.AlpFilterConfiguration;
import android.media.tv.tuner.filter.AudioDescriptor;
import android.media.tv.tuner.filter.AvSettings;
import android.media.tv.tuner.filter.DownloadEvent;
import android.media.tv.tuner.filter.DownloadSettings;
import android.media.tv.tuner.filter.Filter;
import android.media.tv.tuner.filter.FilterCallback;
import android.media.tv.tuner.filter.FilterConfiguration;
import android.media.tv.tuner.filter.FilterEvent;
import android.media.tv.tuner.filter.IpCidChangeEvent;
import android.media.tv.tuner.filter.IpFilterConfiguration;
import android.media.tv.tuner.filter.IpPayloadEvent;
import android.media.tv.tuner.filter.MediaEvent;
import android.media.tv.tuner.filter.MmtpFilterConfiguration;
import android.media.tv.tuner.filter.MmtpRecordEvent;
import android.media.tv.tuner.filter.PesEvent;
import android.media.tv.tuner.filter.PesSettings;
import android.media.tv.tuner.filter.RecordSettings;
import android.media.tv.tuner.filter.RestartEvent;
import android.media.tv.tuner.filter.ScramblingStatusEvent;
import android.media.tv.tuner.filter.SectionEvent;
import android.media.tv.tuner.filter.SectionSettingsWithSectionBits;
import android.media.tv.tuner.filter.SectionSettingsWithTableInfo;
import android.media.tv.tuner.filter.Settings;
import android.media.tv.tuner.filter.SharedFilter;
import android.media.tv.tuner.filter.SharedFilterCallback;
import android.media.tv.tuner.filter.TemiEvent;
import android.media.tv.tuner.filter.TimeFilter;
import android.media.tv.tuner.filter.TlvFilterConfiguration;
import android.media.tv.tuner.filter.TsFilterConfiguration;
import android.media.tv.tuner.filter.TsRecordEvent;
import android.media.tv.tuner.frontend.AnalogFrontendCapabilities;
import android.media.tv.tuner.frontend.AnalogFrontendSettings;
import android.media.tv.tuner.frontend.Atsc3FrontendCapabilities;
import android.media.tv.tuner.frontend.Atsc3FrontendSettings;
import android.media.tv.tuner.frontend.Atsc3PlpInfo;
import android.media.tv.tuner.frontend.AtscFrontendCapabilities;
import android.media.tv.tuner.frontend.AtscFrontendSettings;
import android.media.tv.tuner.frontend.DtmbFrontendCapabilities;
import android.media.tv.tuner.frontend.DtmbFrontendSettings;
import android.media.tv.tuner.frontend.DvbcFrontendCapabilities;
import android.media.tv.tuner.frontend.DvbcFrontendSettings;
import android.media.tv.tuner.frontend.DvbsFrontendCapabilities;
import android.media.tv.tuner.frontend.DvbsFrontendSettings;
import android.media.tv.tuner.frontend.DvbtFrontendCapabilities;
import android.media.tv.tuner.frontend.DvbtFrontendSettings;
import android.media.tv.tuner.frontend.FrontendCapabilities;
import android.media.tv.tuner.frontend.FrontendInfo;
import android.media.tv.tuner.frontend.FrontendSettings;
import android.media.tv.tuner.frontend.FrontendStatus;
import android.media.tv.tuner.frontend.FrontendStatus.Atsc3PlpTuningInfo;
import android.media.tv.tuner.frontend.FrontendStatusReadiness;
import android.media.tv.tuner.frontend.IptvFrontendCapabilities;
import android.media.tv.tuner.frontend.IptvFrontendSettings;
import android.media.tv.tuner.frontend.Isdbs3FrontendCapabilities;
import android.media.tv.tuner.frontend.Isdbs3FrontendSettings;
import android.media.tv.tuner.frontend.IsdbsFrontendCapabilities;
import android.media.tv.tuner.frontend.IsdbsFrontendSettings;
import android.media.tv.tuner.frontend.IsdbtFrontendCapabilities;
import android.media.tv.tuner.frontend.IsdbtFrontendSettings;
import android.media.tv.tuner.frontend.OnTuneEventListener;
import android.media.tv.tuner.frontend.ScanCallback;
import android.media.tv.tunerresourcemanager.TunerFrontendInfo;
import android.media.tv.tunerresourcemanager.TunerFrontendRequest;
import android.media.tv.tunerresourcemanager.TunerResourceManager;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.SparseIntArray;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class TunerTest {
    private static final String TAG = "MediaTunerTest";

    @Rule
    public RequiredFeatureRule featureRule = new RequiredFeatureRule(
            PackageManager.FEATURE_TUNER);

    private static final int TIMEOUT_MS = 10 * 1000;  // 10 seconds
    private static final int SCAN_TIMEOUT_MS = 2 * 60 * 1000; // 2 minutes
    private static final long TIMEOUT_BINDER_SERVICE_SEC = 2;

    private Context mContext;
    private Tuner mTuner;
    private CountDownLatch mLockLatch = new CountDownLatch(1);
    private TunerResourceManager mTunerResourceManager = null;
    private TestServiceConnection mConnection;
    private ISharedFilterTestServer mSharedFilterTestServer;

    private int mDummyResourceCount = 0;
    private DemuxFilterTypeProcessor mDFMTProcessor = new DemuxFilterTypeProcessor();

    private final Object mResourceLostCountLock = new Object();
    private int mResourceLostCount = 0;

    private int getFilterSubTypeForTest(int filterType) {
        int subType = Filter.TYPE_UNDEFINED;
        switch (filterType) {
            case (Filter.TYPE_TS):
                subType = Filter.SUBTYPE_SECTION;
                break;
            case (Filter.TYPE_MMTP):
                subType = Filter.SUBTYPE_PES;
                break;
            case (Filter.TYPE_IP):
                subType = Filter.SUBTYPE_IP;
                break;
            case (Filter.TYPE_TLV):
                subType = Filter.SUBTYPE_TLV;
                break;
            case (Filter.TYPE_ALP):
                subType = Filter.SUBTYPE_SECTION;
                break;
            default:
                break;
        }
        return subType;
    }

    private class DemuxFilterTypeProcessor {
        // number of the emuxes on the system
        int mNumOfDemuxes;

        // the bitwise OR of all the DemuxFilterTypes on the system
        int mCombinedCaps;

        // the cap (singular) that are supported by the least # of demuxes on the system
        int mLeastFrequentCap;
        int mRunningMinCount;

        // the cap with the least # of bits that are not singular
        // e.g. if there are 4 demuxes with b111, b1011, b1000, b1010 --> b1010
        int mSmallestMultiBitsCap;
        int mRunningSmallestNumOfBits;

        // for storing # of demux resources supporting each cap
        SparseIntArray mCapCounts = new SparseIntArray();

        public int getNumOfDemuxes() {
            return mNumOfDemuxes;
        }

        public int getSmallestMultiBitsCap() {
            return mSmallestMultiBitsCap;
        }

        public int getCapCount(int cap) {
            if (cap >= Integer.SIZE) {
                return 0;
            }
            return mCapCounts.get(cap, -1);
        }

        public int getLeastFrequentCap() {
            return mLeastFrequentCap;
        }

        // e.g. getPartialCap(b101) --> b1
        public int getPartialCap(int baseCap) {
            int cap = 1;
            for (int i = 0; i < Integer.SIZE; i++) {
                if ((cap & baseCap) == cap) {
                    return cap;
                }
                cap = cap << 1;
            }
            return baseCap;
        }

        // e.g. getFirstSupportedCap(b0) -> b1, when mCombinedCaps is b10101
        //      getFirstSupportedCap(b1) -> b100, when mCombinedCaps is b10101
        public int getFirstSupportedCap(int excludeCaps) {
            int cap = 1;
            for (int i = 0; i < Integer.SIZE; i++) {
                if ((excludeCaps & cap) == 0
                        && (mCombinedCaps & cap) == cap) {
                    return cap;
                }
                cap = cap << 1;
            }
            return 0;
        }

        // e.g. getFirstNonSupportedCap() -> b01, when mCombinedCaps is b10101
        public int getFirstNonSupportedCap() {
            int cap = 1;
            for (int i = 0; i < Integer.SIZE; i++) {
                if ((cap & mCombinedCaps) != cap) {
                    return cap;
                }
                cap = cap << 1;
            }
            return 0;
        }

        public void reset() {
            mNumOfDemuxes = 0;
            mSmallestMultiBitsCap = 0;
            mRunningSmallestNumOfBits = Integer.SIZE;
            mCombinedCaps = 0;
            mCapCounts.clear();
            mLeastFrequentCap = 0;
            mRunningMinCount = Integer.MAX_VALUE;
        }

        DemuxFilterTypeProcessor() {
            reset();
        }

        private void updateCapCounts(int caps) {
            int mask = 1;
            for (int i = 0; i < Integer.SIZE - 1; i++) {
                if (mask > caps) {
                    break;
                }
                if ((caps & mask) == mask) {
                    int newCount = mCapCounts.get(mask, 0) + 1;
                    mCapCounts.put(mask, newCount);
                    if (newCount < mRunningMinCount) {
                        mRunningMinCount = newCount;
                        mLeastFrequentCap = mask;
                    }
                }
                mask = mask << 1;
            }
        }

        public void processEntry(DemuxFilterTypeInfo entry) {
            int caps = entry.getCaps();
            int numOfCaps = entry.getNumOfCaps();

            // update mSmallestMultiBitsCap
            if (numOfCaps > 1 && numOfCaps < mRunningSmallestNumOfBits) {
                mRunningSmallestNumOfBits = numOfCaps;
                mSmallestMultiBitsCap = caps;
            }
            mCombinedCaps = mCombinedCaps | caps;
            updateCapCounts(caps);
            mNumOfDemuxes++;
        }
    }

    private class DemuxFilterTypeInfo {
        int mFilterTypes;
        int mNumOfCaps;

        DemuxFilterTypeInfo(int filterTypes) {
            mFilterTypes = filterTypes;
            mNumOfCaps = Integer.bitCount(filterTypes);
        }
        public int getCaps() {
            return mFilterTypes;
        }
        public int getNumOfCaps() {
            return mNumOfCaps;
        }
    }


    private class TestServiceConnection implements ServiceConnection {
        private BlockingQueue<IBinder> mBlockingQueue = new LinkedBlockingQueue<>();

        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBlockingQueue.offer(service);
        }

        public void onServiceDisconnected(ComponentName componentName) {}

        public IBinder getService() throws Exception {
            final IBinder service =
                    mBlockingQueue.poll(TIMEOUT_BINDER_SERVICE_SEC, TimeUnit.SECONDS);
            return service;
        }
    }

    private class TunerResourceTestServiceConnection implements ServiceConnection {
        private BlockingQueue<IBinder> mBlockingQueue = new LinkedBlockingQueue<>();

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBlockingQueue.offer(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName){}

        public ITunerResourceTestServer getService() throws Exception {
            final IBinder service =
                    mBlockingQueue.poll(TIMEOUT_BINDER_SERVICE_SEC, TimeUnit.SECONDS);
            return ITunerResourceTestServer.Stub.asInterface(service);
        }
    }

    private class TunerTestOnTuneEventListener implements OnTuneEventListener {
        public static final int INVALID_TUNE_EVENT = -1;
        private static final int SLEEP_TIME_MS = 3000;
        private static final int TIMEOUT_MS = 3000;
        private final ReentrantLock mLock = new ReentrantLock();
        private final ConditionVariable mCV = new ConditionVariable();
        private int mLastTuneEvent = INVALID_TUNE_EVENT;

        @Override
        public void onTuneEvent(int tuneEvent) {
            synchronized (mLock) {
                mLastTuneEvent = tuneEvent;
                mCV.open();
            }
        }

        public void resetLastTuneEvent() {
            synchronized (mLock) {
                mLastTuneEvent = INVALID_TUNE_EVENT;
            }
        }

        public int getLastTuneEvent() {
            try {
                // yield to let the callback handling execute
                Thread.sleep(SLEEP_TIME_MS);
            } catch (Exception e) {
                // ignore exception
            }
            synchronized (mLock) {
                mCV.block(TIMEOUT_MS);
                mCV.close();
                return mLastTuneEvent;
            }
        }
    }

    private class TunerTestLnbCallback implements LnbCallback {
        public static final int INVALID_LNB_EVENT = -1;
        private static final int SLEEP_TIME_MS = 100;
        private static final int TIMEOUT_MS = 500;
        private final ReentrantLock mDMLock = new ReentrantLock();
        private final ConditionVariable mDMCV = new ConditionVariable();
        private boolean mOnDiseqcMessageCalled = false;

        // will not test this as there is no good way to trigger this
        @Override
        public void onEvent(int lnbEventType) {}

        // will test this instead
        @Override
        public void onDiseqcMessage(byte[] diseqcMessage) {
            synchronized (mDMLock) {
                mOnDiseqcMessageCalled = true;
                mDMCV.open();
            }
        }

        public void resetOnDiseqcMessageCalled() {
            synchronized (mDMLock) {
                mOnDiseqcMessageCalled = false;
            }
        }

        public boolean getOnDiseqcMessageCalled() {
            try {
                // yield to let the callback handling execute
                Thread.sleep(SLEEP_TIME_MS);
            } catch (Exception e) {
                // ignore exception
            }

            synchronized (mDMLock) {
                mDMCV.block(TIMEOUT_MS);
                mDMCV.close();
                return mOnDiseqcMessageCalled;
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        InstrumentationRegistry
                .getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();
        mTuner = new Tuner(mContext, null, 100);
    }

    @After
    public void tearDown() {
        if (mTuner != null) {
          mTuner.close();
          mTuner = null;
        }
    }

    @Test
    public void testTunerConstructor() throws Exception {
        assertNotNull(mTuner);
    }

    @Test
    public void testTunerVersion() {
        assertNotNull(mTuner);
        int version = TunerVersionChecker.getTunerVersion();
        assertTrue(version >= TunerVersionChecker.TUNER_VERSION_1_0);
        assertTrue(version <= TunerVersionChecker.TUNER_VERSION_3_0);
    }

    @Test
    public void testFrontendHardwareInfo() throws Exception {
        String hwInfo = null;
        try {
            hwInfo = mTuner.getCurrentFrontendHardwareInfo();
            if (TunerVersionChecker.isHigherOrEqualVersionTo(
                    TunerVersionChecker.TUNER_VERSION_2_0)) {
                fail("Get Frontend hardware info should throw IllegalStateException.");
            } else {
                assertNull(hwInfo);
            }
        } catch (IllegalStateException e) {
            // pass
        }

        List<Integer> ids = mTuner.getFrontendIds();
        if (ids == null) return;
        assertFalse(ids.isEmpty());

        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        int res = mTuner.tune(createFrontendSettings(info));
        hwInfo = mTuner.getCurrentFrontendHardwareInfo();
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_2_0)) {
            assertNotNull(hwInfo);
            assertFalse(hwInfo.isEmpty());
        } else {
            assertNull(hwInfo);
        }
        res = mTuner.cancelTuning();
        assertEquals(Tuner.RESULT_SUCCESS, res);
    }

    @Test
    public void testTuning() throws Exception {
        List<Integer> ids = mTuner.getFrontendIds();
        if (ids == null) return;
        assertFalse(ids.isEmpty());

        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        int res = mTuner.tune(createFrontendSettings(info));
        assertEquals(Tuner.RESULT_SUCCESS, res);
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_3_0)) {
            if (mTuner.isLnaSupported()) {
                res = mTuner.setLnaEnabled(false);
                assertEquals(Tuner.RESULT_SUCCESS, res);
            } else {
                res = mTuner.setLnaEnabled(false);
                assertEquals(Tuner.RESULT_UNAVAILABLE, res);
            }
        } else {
            res = mTuner.setLnaEnabled(false);
            assertTrue((res == Tuner.RESULT_SUCCESS) || (res == Tuner.RESULT_UNAVAILABLE));
        }
        res = mTuner.cancelTuning();
        assertEquals(Tuner.RESULT_SUCCESS, res);
    }

    @Test
    public void testIsLnaSupported() throws Exception {
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_3_0)) {
            mTuner.isLnaSupported();
            // no exception thrown
        } else {
            try {
                mTuner.isLnaSupported();
                fail("Is LNA Supported should throw UnsupportedOperationException.");
            } catch (UnsupportedOperationException uoe) {
                // pass
            }
        }
    }

    @Test
    public void testMultiTuning() throws Exception {
        List<Integer> ids = mTuner.getFrontendIds();
        if (ids == null) return;
        assertFalse(ids.isEmpty());

        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        int res = mTuner.tune(createFrontendSettings(info));
        assertEquals(Tuner.RESULT_SUCCESS, res);
        res = mTuner.cancelTuning();
        assertEquals(Tuner.RESULT_SUCCESS, res);

        // Tune again with the same frontend.
        mTuner.tune(createFrontendSettings(info));
        assertEquals(Tuner.RESULT_SUCCESS, res);
        res = mTuner.cancelTuning();
        assertEquals(Tuner.RESULT_SUCCESS, res);

        for (int i = 1; i < ids.size(); i++) {
            FrontendInfo info2 = mTuner.getFrontendInfoById(ids.get(i));
            if (info2.getType() != info.getType()) {
                res = mTuner.tune(createFrontendSettings(info2));
                assertEquals(Tuner.RESULT_INVALID_STATE, res);
            }
        }
    }

    @Test
    public void testRequestFrontendThenTune() throws Exception {
        List<FrontendInfo> frontendInfos = mTuner.getAvailableFrontendInfos();
        if (frontendInfos == null) return;
        assertFalse(frontendInfos.isEmpty());

        FrontendInfo frontendInfo = frontendInfos.get(0);
        int result = mTuner.applyFrontend(frontendInfo);
        assertEquals(Tuner.RESULT_SUCCESS, result);

        result = mTuner.tune(createFrontendSettings(frontendInfo));
        assertEquals(Tuner.RESULT_SUCCESS, result);

        for (FrontendInfo info2: frontendInfos) {
            if (info2.getType() != frontendInfo.getType()) {
                result = mTuner.tune(createFrontendSettings(info2));
                assertEquals(Tuner.RESULT_INVALID_STATE, result);
            }
        }

        // After tune(), the frontend assigned by applyFrontend should still be used.
        FrontendInfo currentFrontendInfo = mTuner.getFrontendInfo();
        assertEquals(frontendInfo.getId(), currentFrontendInfo.getId());
    }

    @Test
    public void testScanning() throws Exception {
        // Use the same test approach as testTune since it is not possible to test all frontends on
        // one signal source
        List<Integer> ids = mTuner.getFrontendIds();
        if (ids == null) return;
        assertFalse(ids.isEmpty());

        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        int res = mTuner.scan(
                        createFrontendSettings(info),
                        Tuner.SCAN_TYPE_AUTO,
                        getExecutor(),
                        getScanCallback());
        assertEquals(Tuner.RESULT_SUCCESS, res);
        res = mTuner.cancelScanning();
        assertEquals(Tuner.RESULT_SUCCESS, res);
    }

    @Test
    public void testFrontendStatus() throws Exception {
        List<Integer> ids = mTuner.getFrontendIds();
        if (ids == null) return;
        assertFalse(ids.isEmpty());

        for (int id : ids) {
            Tuner tuner = new Tuner(mContext, null, 100);
            FrontendInfo info = tuner.getFrontendInfoById(id);
            int res = tuner.tune(createFrontendSettings(info));

            int[] statusCapabilities = info.getStatusCapabilities();
            assertNotNull(statusCapabilities);
            FrontendStatus status = tuner.getFrontendStatus(statusCapabilities);
            assertNotNull(status);

            for (int i = 0; i < statusCapabilities.length; i++) {
                switch (statusCapabilities[i]) {
                    case FrontendStatus.FRONTEND_STATUS_TYPE_DEMOD_LOCK:
                        status.isDemodLocked();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_SNR:
                        status.getSnr();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_BER:
                        status.getBer();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_PER:
                        status.getPer();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_PRE_BER:
                        status.getPerBer();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_SIGNAL_QUALITY:
                        status.getSignalQuality();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_SIGNAL_STRENGTH:
                        status.getSignalStrength();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_SYMBOL_RATE:
                        status.getSymbolRate();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_FEC:
                        status.getInnerFec();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_MODULATION:
                        if (info.getType() != FrontendSettings.TYPE_DVBT &&
                            info.getType() !=  FrontendSettings.TYPE_ANALOG)
                            status.getModulation();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_SPECTRAL:
                        status.getSpectralInversion();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_LNB_VOLTAGE:
                        status.getLnbVoltage();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_PLP_ID:
                        status.getPlpId();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_EWBS:
                        status.isEwbs();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_AGC:
                        status.getAgc();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_LNA:
                        status.isLnaOn();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_LAYER_ERROR:
                        boolean[] r = status.getLayerErrors();
                        assertNotNull(r);
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_MER:
                        status.getMer();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_FREQ_OFFSET:
                        status.getFreqOffsetLong();
                        status.getFreqOffset();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_HIERARCHY:
                        status.getHierarchy();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_RF_LOCK:
                        status.isRfLocked();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_ATSC3_PLP_INFO:
                        Atsc3PlpTuningInfo[] tuningInfos = status.getAtsc3PlpTuningInfo();
                        if (tuningInfos != null) {
                            for (Atsc3PlpTuningInfo tuningInfo : tuningInfos) {
                                tuningInfo.getPlpId();
                                tuningInfo.isLocked();
                                tuningInfo.getUec();
                            }
                        }
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_BERS:
                        int[] b = status.getBers();
                        assertNotNull(b);
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_CODERATES:
                        int[] c = status.getCodeRates();
                        assertNotNull(c);
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_BANDWIDTH:
                        status.getBandwidth();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_GUARD_INTERVAL:
                        status.getGuardInterval();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_TRANSMISSION_MODE:
                        status.getTransmissionMode();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_UEC:
                        status.getUec();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_T2_SYSTEM_ID:
                        status.getSystemId();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_INTERLEAVINGS:
                        int[] l = status.getInterleaving();
                        assertNotNull(l);
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_ISDBT_SEGMENTS:
                        int[] segment = status.getIsdbtSegment();
                        assertNotNull(segment);
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_TS_DATA_RATES:
                        int[] rates = status.getTsDataRate();
                        assertNotNull(rates);
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_MODULATIONS_EXT:
                        int[] modulations = status.getExtendedModulations();
                        assertNotNull(modulations);
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_ROLL_OFF:
                        status.getRollOff();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_IS_MISO_ENABLED:
                        status.isMisoEnabled();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_IS_LINEAR:
                        status.isLinear();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_IS_SHORT_FRAMES_ENABLED:
                        status.isShortFramesEnabled();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_ISDBT_MODE:
                        status.getIsdbtMode();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_ISDBT_PARTIAL_RECEPTION_FLAG:
                        status.getIsdbtPartialReceptionFlag();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_STREAM_IDS:
                        int[] streamIds = status.getStreamIds();
                        assertNotNull(streamIds);
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_DVBT_CELL_IDS:
                        int[] cellIds = status.getDvbtCellIds();
                        assertNotNull(cellIds);
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_ATSC3_ALL_PLP_INFO:
                        List<Atsc3PlpInfo> plps = status.getAllAtsc3PlpInfo();
                        assertFalse(plps.isEmpty());
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_IPTV_CONTENT_URL:
                        String iptvContentUrl = status.getIptvContentUrl();
                        assertNotNull(iptvContentUrl);
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_IPTV_PACKETS_LOST:
                        status.getIptvPacketsLost();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_IPTV_PACKETS_RECEIVED:
                        status.getIptvPacketsReceived();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_IPTV_WORST_JITTER_MS:
                        status.getIptvWorstJitterMillis();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_IPTV_AVERAGE_JITTER_MS:
                        status.getIptvAverageJitterMillis();
                        break;
                }
            }
            tuner.close();
            tuner = null;
        }
    }

    @Test
    public void testFrontendStatusReadiness() throws Exception {
        // Test w/o active frontend
        try {
            int[] caps = {0};
            List<FrontendStatusReadiness> readiness = mTuner.getFrontendStatusReadiness(caps);
            if (TunerVersionChecker.isHigherOrEqualVersionTo(
                        TunerVersionChecker.TUNER_VERSION_2_0)) {
                fail("Get Frontend Status Readiness should throw IllegalStateException.");
            } else {
                assertTrue(readiness.isEmpty());
            }
        } catch (IllegalStateException e) {
            // pass
        }

        List<Integer> ids = mTuner.getFrontendIds();
        if (ids == null)
            return;
        assertFalse(ids.isEmpty());

        for (int id : ids) {
            Tuner tuner = new Tuner(mContext, null, 100);
            FrontendInfo info = tuner.getFrontendInfoById(id);
            int res = tuner.tune(createFrontendSettings(info));

            int[] statusCapabilities = info.getStatusCapabilities();
            assertNotNull(statusCapabilities);
            List<FrontendStatusReadiness> readiness =
                    tuner.getFrontendStatusReadiness(statusCapabilities);
            if (TunerVersionChecker.isHigherOrEqualVersionTo(
                        TunerVersionChecker.TUNER_VERSION_2_0)) {
                assertEquals(readiness.size(), statusCapabilities.length);
                for (int i = 0; i < readiness.size(); i++) {
                    assertEquals(readiness.get(i).getStatusType(), statusCapabilities[i]);
                    int r = readiness.get(i).getStatusReadiness();
                    if (r == FrontendStatusReadiness.FRONTEND_STATUS_READINESS_UNAVAILABLE
                            || r == FrontendStatusReadiness.FRONTEND_STATUS_READINESS_UNSTABLE
                            || r == FrontendStatusReadiness.FRONTEND_STATUS_READINESS_STABLE) {
                        // pass
                    } else {
                        fail("Get Frontend Status Readiness returned wrong readiness " + r);
                    }
                }
            } else {
                assertTrue(readiness.isEmpty());
            }
            tuner.cancelTuning();
            tuner.close();
            tuner = null;
        }
    }

    @Test
    public void testConfigureDemux() throws Exception {
        DemuxCapabilities dc = mTuner.getDemuxCapabilities();
        if (dc == null || dc.getFilterTypeCapabilityList().length <= 0) {
            return;
        }

        // Test configureDemux with all the valid capabilities and set up the demux info processor
        mDFMTProcessor.reset();
        DemuxInfo di = new DemuxInfo(Filter.TYPE_UNDEFINED);

        for (int caps : dc.getFilterTypeCapabilityList()) {
            di.setFilterTypes(caps);
            assertEquals(Tuner.RESULT_SUCCESS, mTuner.configureDemux(di));
            assertEquals(caps, mTuner.getDesiredDemuxInfo().getFilterTypes());
            mDFMTProcessor.processEntry(new DemuxFilterTypeInfo(caps));
        }

        // Test configureDemux with TYPE_UNDEFINED
        di.setFilterTypes(Filter.TYPE_UNDEFINED);
        assertEquals(Tuner.RESULT_SUCCESS, mTuner.configureDemux(di));
        assertEquals(Filter.TYPE_UNDEFINED, mTuner.getDesiredDemuxInfo().getFilterTypes());

        // Validate getCurrentDemuxInfo() returns null
        assertNull(mTuner.getCurrentDemuxInfo());

        // Test configureDemux with unsupported cap
        int nonSupportedCap = mDFMTProcessor.getFirstNonSupportedCap();
        di.setFilterTypes(nonSupportedCap);
        assertEquals(Tuner.RESULT_UNAVAILABLE, mTuner.configureDemux(di));

        // Now confirm the demux release related behavior
        // first get any demux resource
        assertNotNull(mTuner.openDescrambler());
        assertNotNull(mTuner.getCurrentDemuxInfo());
        // configureDemux with TYPE_UNDEFINED and expect no release
        di.setFilterTypes(Filter.TYPE_UNDEFINED);
        assertEquals(Tuner.RESULT_SUCCESS, mTuner.configureDemux(di));
        assertEquals(Filter.TYPE_UNDEFINED, mTuner.getDesiredDemuxInfo().getFilterTypes());
        assertNotNull(mTuner.getCurrentDemuxInfo());
        // configureDemux with null to invoke demux release
        assertEquals(Tuner.RESULT_SUCCESS, mTuner.configureDemux(null));
        assertEquals(Filter.TYPE_UNDEFINED, mTuner.getDesiredDemuxInfo().getFilterTypes());
        assertNull(mTuner.getCurrentDemuxInfo());


        // now through openFilter
        int cap = mDFMTProcessor.getFirstSupportedCap(0);
        assertNotEquals(0, cap);
        Filter f = mTuner.openFilter(
                cap, getFilterSubTypeForTest(cap), 1000, getExecutor(), getFilterCallback());
        assertEquals(cap, mTuner.getDesiredDemuxInfo().getFilterTypes());
        assertNotNull(f);
        assertEquals(cap, mTuner.getCurrentDemuxInfo().getFilterTypes() & cap);

        // configureDemux with TYPE_UNDEFINED and expect no release
        assertEquals(Tuner.RESULT_SUCCESS, mTuner.configureDemux(di));
        assertEquals(Filter.TYPE_UNDEFINED, mTuner.getDesiredDemuxInfo().getFilterTypes());
        assertNotNull(mTuner.getCurrentDemuxInfo());

        // now configureDemux with null to invoke demux release
        assertEquals(Tuner.RESULT_SUCCESS, mTuner.configureDemux(null));
        assertEquals(Filter.TYPE_UNDEFINED, mTuner.getDesiredDemuxInfo().getFilterTypes());
        assertNull(mTuner.getCurrentDemuxInfo());

        // do additional test if multi bits caps is available
        int caps = mDFMTProcessor.getSmallestMultiBitsCap();
        if (caps != 0) {
            // configureDemux with multi bits caps and allocate demux
            di.setFilterTypes(caps);
            assertEquals(Tuner.RESULT_SUCCESS, mTuner.configureDemux(di));
            assertEquals(caps, mTuner.getDesiredDemuxInfo().getFilterTypes());
            assertNotNull(mTuner.openDescrambler());
            assertEquals(caps, mTuner.getCurrentDemuxInfo().getFilterTypes());

            // configure with the same caps - everything should stay the same
            assertEquals(Tuner.RESULT_SUCCESS, mTuner.configureDemux(di));
            assertEquals(caps, mTuner.getDesiredDemuxInfo().getFilterTypes());
            assertEquals(caps, mTuner.getCurrentDemuxInfo().getFilterTypes());

            // configure with the subset - everything except for the desired cap should stay the
            // same
            int partialCap = mDFMTProcessor.getPartialCap(caps);
            di.setFilterTypes(partialCap);
            assertEquals(Tuner.RESULT_SUCCESS, mTuner.configureDemux(di));
            assertEquals(partialCap, mTuner.getDesiredDemuxInfo().getFilterTypes());
            assertEquals(caps, mTuner.getCurrentDemuxInfo().getFilterTypes());

            // configure with another subset - everything except for the desired cap should stay the
            // same
            partialCap = partialCap ^ caps;
            di.setFilterTypes(partialCap);
            assertEquals(Tuner.RESULT_SUCCESS, mTuner.configureDemux(di));
            assertEquals(partialCap, mTuner.getDesiredDemuxInfo().getFilterTypes());
            assertEquals(caps, mTuner.getCurrentDemuxInfo().getFilterTypes());

            // configure with different cap - should trigger demux relesase
            int exclusiveCap = mDFMTProcessor.getFirstSupportedCap(caps);
            di.setFilterTypes(exclusiveCap);
            assertEquals(Tuner.RESULT_SUCCESS, mTuner.configureDemux(di));
            assertEquals(exclusiveCap, mTuner.getDesiredDemuxInfo().getFilterTypes());
            assertNull(mTuner.getCurrentDemuxInfo());

            // now confirm that the openFilter() won't change the desired filter types when it's
            // called with a type that is a subset of previously set desired filter types.
            di.setFilterTypes(caps);
            assertEquals(Tuner.RESULT_SUCCESS, mTuner.configureDemux(di));
            assertEquals(caps, mTuner.getDesiredDemuxInfo().getFilterTypes());
            f = mTuner.openFilter(
                    partialCap, getFilterSubTypeForTest(partialCap), 1000, getExecutor(),
                        getFilterCallback());
            assertEquals(caps, mTuner.getDesiredDemuxInfo().getFilterTypes());
            assertEquals(caps, mTuner.getCurrentDemuxInfo().getFilterTypes());
            assertNotNull(f);
        }
    }

    @Test
    public void testDemuxReclaim() throws Exception {
        DemuxCapabilities dc = mTuner.getDemuxCapabilities();
        if (dc == null || dc.getFilterTypeCapabilityList().length <= 0) {
            return;
        }

        // Test configureDemux with all the valid capabilities and set up the demux info processor
        mDFMTProcessor.reset();
        DemuxInfo di = new DemuxInfo(Filter.TYPE_UNDEFINED);
        for (int caps : dc.getFilterTypeCapabilityList()) {
            di.setFilterTypes(caps);
            assertEquals(Tuner.RESULT_SUCCESS, mTuner.configureDemux(di));
            assertEquals(caps, mTuner.getDesiredDemuxInfo().getFilterTypes());
            mDFMTProcessor.processEntry(new DemuxFilterTypeInfo(caps));
        }

        // get the cap that is supported by least number of Demux
        int cap = mDFMTProcessor.getLeastFrequentCap();
        int subType = getFilterSubTypeForTest(cap);
        assertNotEquals(0, cap);

        List<Tuner> lowerPrioTuners = new ArrayList<Tuner>();
        List<Filter> filters = new ArrayList<Filter>();
        int numOfCompatibleDemuxes = mDFMTProcessor.getCapCount(cap);
        mResourceLostCount = 0;
        Filter filter;
        for (int i = 0; i < numOfCompatibleDemuxes; i++) {
            Tuner tuner = new Tuner(mContext, null, 100);
            tuner.setResourceLostListener(getExecutor(), new Tuner.OnResourceLostListener() {
                @Override
                public void onResourceLost(Tuner tuner) {
                    synchronized (mResourceLostCountLock) {
                        mResourceLostCount++;
                    }
                }
            });
            filter = tuner.openFilter(
                    cap, subType, 1000, getExecutor(), getFilterCallback());
            assertNotNull(filter);
            assertEquals(cap, tuner.getCurrentDemuxInfo().getFilterTypes() & cap);

            filters.add(filter);
            lowerPrioTuners.add(tuner);
        }

        // now claim another demux with higher priority
        Tuner highPrioTuner = new Tuner(mContext, null, 200);
        filter = highPrioTuner.openFilter(
                cap, subType, 1000, getExecutor(), getFilterCallback());
        assertNotNull(filter);
        assertEquals(cap, highPrioTuner.getCurrentDemuxInfo().getFilterTypes() & cap);
        Thread.sleep(1);
        assertEquals(1, mResourceLostCount);
        highPrioTuner.close();

        // clean up the resource
        for (Tuner t : lowerPrioTuners) {
            t.close();
        }
    }

    @Test
    public void testLnb() throws Exception {
        Lnb lnb = mTuner.openLnb(getExecutor(), getLnbCallback());
        if (lnb == null) return;
        assertEquals(lnb.setVoltage(Lnb.VOLTAGE_5V), Tuner.RESULT_SUCCESS);
        assertEquals(lnb.setTone(Lnb.TONE_NONE), Tuner.RESULT_SUCCESS);
        assertEquals(
                lnb.setSatellitePosition(Lnb.POSITION_A), Tuner.RESULT_SUCCESS);
        lnb.sendDiseqcMessage(new byte[] {1, 2});
        lnb.close();
    }

    @Test
    public void testLnbAddAndRemoveCallback() throws Exception {
        TunerTestLnbCallback lnbCB1 = new TunerTestLnbCallback();
        Lnb lnb = mTuner.openLnb(getExecutor(), lnbCB1);
        if (lnb == null) {
            return;
        }

        assertEquals(lnb.setVoltage(Lnb.VOLTAGE_5V), Tuner.RESULT_SUCCESS);
        assertEquals(lnb.setTone(Lnb.TONE_NONE), Tuner.RESULT_SUCCESS);
        assertEquals(
                lnb.setSatellitePosition(Lnb.POSITION_A), Tuner.RESULT_SUCCESS);
        lnb.sendDiseqcMessage(new byte[] {1, 2});
        assertTrue(lnbCB1.getOnDiseqcMessageCalled());
        lnbCB1.resetOnDiseqcMessageCalled();

        List<Integer> ids = mTuner.getFrontendIds();
        assertFalse(ids.isEmpty());
        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        FrontendSettings feSettings = createFrontendSettings(info);
        int res = mTuner.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);

        // create sharee
        Tuner sharee = new Tuner(mContext, null, 100);
        sharee.shareFrontendFromTuner(mTuner);
        TunerTestLnbCallback lnbCB2 = new TunerTestLnbCallback();

        // add it as sharee
        lnb.addCallback(getExecutor(), lnbCB2);

        // check callback
        lnb.sendDiseqcMessage(new byte[] {1, 2});
        assertTrue(lnbCB1.getOnDiseqcMessageCalled());
        lnbCB1.resetOnDiseqcMessageCalled();
        assertTrue(lnbCB2.getOnDiseqcMessageCalled());
        lnbCB2.resetOnDiseqcMessageCalled();

        // remove sharee the sharee (should succeed)
        assertTrue(lnb.removeCallback(lnbCB2));

        // check callback (only the original owner gets callback
        lnb.sendDiseqcMessage(new byte[] {1, 2});
        assertTrue(lnbCB1.getOnDiseqcMessageCalled());
        lnbCB1.resetOnDiseqcMessageCalled();
        assertFalse(lnbCB2.getOnDiseqcMessageCalled());
        lnbCB2.resetOnDiseqcMessageCalled();

        sharee.close();
    }

    @Test
    public void testOpenLnbByname() throws Exception {
        Lnb lnb = mTuner.openLnbByName("default", getExecutor(), getLnbCallback());
        if (lnb != null) {
            lnb.close();
        }
    }

    @Test
    public void testCiCam() throws Exception {
    // open filter to get demux resource
        mTuner.openFilter(
                Filter.TYPE_TS, Filter.SUBTYPE_SECTION, 1000, getExecutor(), getFilterCallback());

        mTuner.connectCiCam(1);
        mTuner.disconnectCiCam();
    }

    @Test
    public void testFrontendToCiCam() throws Exception {
        // tune to get frontend resource
        List<Integer> ids = mTuner.getFrontendIds();
        if (ids == null) return;
        assertFalse(ids.isEmpty());
        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        int res = mTuner.tune(createFrontendSettings(info));
        assertEquals(Tuner.RESULT_SUCCESS, res);

        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            // TODO: get real CiCam id from MediaCas
            // only tuner hal1.1 support CiCam
            res = mTuner.connectFrontendToCiCam(0);
            if (res != Tuner.INVALID_LTS_ID)
                assertEquals(mTuner.disconnectFrontendToCiCam(0), Tuner.RESULT_SUCCESS);
        }
    }

    @Test
    public void testRemoveOutputPid() throws Exception {
        // Test w/o active frontend
        try {
            int status = mTuner.removeOutputPid(10);
            if (TunerVersionChecker.isHigherOrEqualVersionTo(
                        TunerVersionChecker.TUNER_VERSION_2_0)) {
                fail("Remove output PID should throw IllegalStateException.");
            } else {
                assertEquals(status, Tuner.RESULT_UNAVAILABLE);
            }
        } catch (IllegalStateException e) {
            // pass
        }

        // tune to get frontend resource
        List<Integer> ids = mTuner.getFrontendIds();
        if (ids == null)
            return;
        assertFalse(ids.isEmpty());
        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        int res = mTuner.tune(createFrontendSettings(info));
        assertEquals(Tuner.RESULT_SUCCESS, res);

        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            // TODO: get real CiCam id from MediaCas
            res = mTuner.connectFrontendToCiCam(0);
        } else {
            res = mTuner.connectFrontendToCiCam(0);
            assertEquals(Tuner.INVALID_LTS_ID, mTuner.connectFrontendToCiCam(0));
        }

        int status = mTuner.removeOutputPid(10);
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_2_0)) {
            if (status != Tuner.RESULT_SUCCESS) {
                assertEquals(status, Tuner.RESULT_UNAVAILABLE);
            }
        } else {
            assertEquals(status, Tuner.RESULT_UNAVAILABLE);
        }

        if (res != Tuner.INVALID_LTS_ID) {
            assertEquals(mTuner.disconnectFrontendToCiCam(0), Tuner.RESULT_SUCCESS);
        } else {
            // Make sure the connectFrontendToCiCam only fails because the current device
            // does not support connecting frontend to cicam
            assertEquals(mTuner.disconnectFrontendToCiCam(0), Tuner.RESULT_UNAVAILABLE);
        }
    }

    @Test
    public void testAvSyncId() throws Exception {
    // open filter to get demux resource
        Filter f = mTuner.openFilter(
                Filter.TYPE_TS, Filter.SUBTYPE_AUDIO, 1000, getExecutor(), getFilterCallback());
        assertNotNull(f);
        assertNotEquals(Tuner.INVALID_FILTER_ID, f.getId());
        Settings settings = AvSettings
                .builder(Filter.TYPE_TS, true)
                .setPassthrough(false)
                .setUseSecureMemory(false)
                .setAudioStreamType(AvSettings.AUDIO_STREAM_TYPE_MPEG1)
                .build();
        FilterConfiguration config = TsFilterConfiguration
                .builder()
                .setTpid(10)
                .setSettings(settings)
                .build();
        f.configure(config);
        int id = mTuner.getAvSyncHwId(f);
        if (id != Tuner.INVALID_AV_SYNC_ID) {
            assertNotEquals(Tuner.INVALID_TIMESTAMP, mTuner.getAvSyncTime(id));
        }
    }

    @Test
    public void testReadFilter() throws Exception {
        Filter f = mTuner.openFilter(
                Filter.TYPE_TS, Filter.SUBTYPE_SECTION, 1000, getExecutor(), getFilterCallback());
        assertNotNull(f);
        assertNotEquals(Tuner.INVALID_FILTER_ID, f.getId());
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertNotEquals(Tuner.INVALID_FILTER_ID_LONG, f.getIdLong());
        } else {
            assertEquals(Tuner.INVALID_FILTER_ID_LONG, f.getIdLong());
        }

        Settings settings = SectionSettingsWithTableInfo
                .builder(Filter.TYPE_TS)
                .setTableId(2)
                .setVersion(1)
                .setCrcEnabled(true)
                .setRaw(false)
                .setRepeat(false)
                .build();
        FilterConfiguration config = TsFilterConfiguration
                .builder()
                .setTpid(10)
                .setSettings(settings)
                .build();
        f.configure(config);
        f.setMonitorEventMask(
                Filter.MONITOR_EVENT_SCRAMBLING_STATUS | Filter.MONITOR_EVENT_IP_CID_CHANGE);

        // Tune a frontend before start the filter
        List<Integer> ids = mTuner.getFrontendIds();
        if (ids == null) return;
        assertFalse(ids.isEmpty());

        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        int res = mTuner.tune(createFrontendSettings(info));
        assertEquals(Tuner.RESULT_SUCCESS, res);

        f.start();
        f.flush();
        f.read(new byte[3], 0, 3);
        f.stop();
        f.close();

        res = mTuner.cancelTuning();
        assertEquals(Tuner.RESULT_SUCCESS, res);
    }

    @Test
    public void testAudioFilterStreamTypeConfig() throws Exception {
        Filter f = mTuner.openFilter(
                Filter.TYPE_TS, Filter.SUBTYPE_AUDIO, 1000, getExecutor(), getFilterCallback());
        assertNotNull(f);
        assertNotEquals(Tuner.INVALID_FILTER_ID, f.getId());

        Settings settings = AvSettings
                .builder(Filter.TYPE_TS, true)
                .setPassthrough(false)
                .setUseSecureMemory(false)
                .setAudioStreamType(AvSettings.AUDIO_STREAM_TYPE_MPEG1)
                .build();
        FilterConfiguration config = TsFilterConfiguration
                .builder()
                .setTpid(10)
                .setSettings(settings)
                .build();
        f.configure(config);

        // Tune a frontend before start the filter
        List<Integer> ids = mTuner.getFrontendIds();
        if (ids == null) return;
        assertFalse(ids.isEmpty());

        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        int res = mTuner.tune(createFrontendSettings(info));
        assertEquals(Tuner.RESULT_SUCCESS, res);

        f.start();
        f.flush();
        f.stop();
        f.close();

        res = mTuner.cancelTuning();
        assertEquals(Tuner.RESULT_SUCCESS, res);
    }

    @Test
    public void testTimeFilter() throws Exception {
        if (!mTuner.getDemuxCapabilities().isTimeFilterSupported()) return;
        TimeFilter f = mTuner.openTimeFilter();
        assertNotNull(f);
        f.setCurrentTimestamp(0);
        assertNotEquals(Tuner.INVALID_TIMESTAMP, f.getTimeStamp());
        assertNotEquals(Tuner.INVALID_TIMESTAMP, f.getSourceTime());
        f.clearTimestamp();
        f.close();
    }

    @Test
    public void testIpFilter() throws Exception {
        Filter f = mTuner.openFilter(
                Filter.TYPE_IP, Filter.SUBTYPE_IP, 1000, getExecutor(), getFilterCallback());
        if (f == null) return;
        assertNotEquals(Tuner.INVALID_FILTER_ID, f.getId());

        FilterConfiguration config = IpFilterConfiguration
                .builder()
                .setSrcIpAddress(new byte[] {(byte) 0xC0, (byte) 0xA8, 0, 1})
                .setDstIpAddress(new byte[] {(byte) 0xC0, (byte) 0xA8, 3, 4})
                .setSrcPort(33)
                .setDstPort(23)
                .setPassthrough(false)
                .setSettings(null)
                .setIpFilterContextId(1)
                .build();
        f.configure(config);

        // Tune a frontend before start the filter
        List<Integer> ids = mTuner.getFrontendIds();
        if (ids == null) return;
        assertFalse(ids.isEmpty());

        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        int res = mTuner.tune(createFrontendSettings(info));
        assertEquals(Tuner.RESULT_SUCCESS, res);

        f.start();
        f.stop();
        f.close();

        res = mTuner.cancelTuning();
        assertEquals(Tuner.RESULT_SUCCESS, res);
    }

    @Test
    public void testAlpSectionFilterConfig() throws Exception {
        Filter f = mTuner.openFilter(
                Filter.TYPE_ALP, Filter.SUBTYPE_SECTION, 1000, getExecutor(), getFilterCallback());
        if (f == null) return;
        assertNotEquals(Tuner.INVALID_FILTER_ID, f.getId());

        SectionSettingsWithSectionBits settings =
                SectionSettingsWithSectionBits
                        .builder(Filter.TYPE_TS)
                        .setCrcEnabled(true)
                        .setRepeat(false)
                        .setRaw(false)
                        .setFilter(new byte[]{2, 3, 4})
                        .setMask(new byte[]{7, 6, 5, 4})
                        .setMode(new byte[]{22, 55, 33})
                        .build();
        AlpFilterConfiguration config =
                AlpFilterConfiguration
                        .builder()
                        .setPacketType(AlpFilterConfiguration.PACKET_TYPE_COMPRESSED)
                        .setLengthType(AlpFilterConfiguration.LENGTH_TYPE_WITH_ADDITIONAL_HEADER)
                        .setSettings(settings)
                        .build();
        f.configure(config);
        f.start();
        f.stop();
        f.close();
    }

    @Test
    public void testMmtpPesFilterConfig() throws Exception {
        Filter f = mTuner.openFilter(
                Filter.TYPE_MMTP, Filter.SUBTYPE_PES, 1000, getExecutor(), getFilterCallback());
        if (f == null) return;
        assertNotEquals(Tuner.INVALID_FILTER_ID, f.getId());

        PesSettings settings =
                PesSettings
                        .builder(Filter.TYPE_TS)
                        .setStreamId(3)
                        .setRaw(false)
                        .build();
        MmtpFilterConfiguration config =
                MmtpFilterConfiguration
                        .builder()
                        .setMmtpPacketId(3)
                        .setSettings(settings)
                        .build();
        f.configure(config);
        f.start();
        f.stop();
        f.close();
    }

    @Test
    public void testMmtpDownloadFilterConfig() throws Exception {
        Filter f = mTuner.openFilter(
                Filter.TYPE_MMTP, Filter.SUBTYPE_DOWNLOAD,
                1000, getExecutor(), getFilterCallback());
        if (f == null) return;
        assertNotEquals(Tuner.INVALID_FILTER_ID, f.getId());

        DownloadSettings.Builder builder = DownloadSettings.builder(Filter.TYPE_MMTP);
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_2_0)) {
            builder.setUseDownloadId(true);
        }
        builder.setDownloadId(2);
        DownloadSettings settings = builder.build();
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_2_0)) {
            assertEquals(settings.useDownloadId(), true);
        } else {
            assertEquals(settings.useDownloadId(), false);
        }
        assertEquals(settings.getDownloadId(), 2);

        MmtpFilterConfiguration config =
                MmtpFilterConfiguration
                        .builder()
                        .setMmtpPacketId(3)
                        .setSettings(settings)
                        .build();
        f.configure(config);
        f.start();
        f.stop();
        f.close();
    }

    @Test
    public void testTsAvFilterConfig() throws Exception {
        Filter f = mTuner.openFilter(
                Filter.TYPE_TS, Filter.SUBTYPE_AUDIO, 1000, getExecutor(), getFilterCallback());
        assertNotNull(f);
        assertNotEquals(Tuner.INVALID_FILTER_ID, f.getId());

        AvSettings settings =
                AvSettings
                        .builder(Filter.TYPE_TS, true) // is Audio
                        .setPassthrough(false)
                        .setUseSecureMemory(false)
                        .setAudioStreamType(AvSettings.AUDIO_STREAM_TYPE_MPEG1)
                        .build();
        TsFilterConfiguration config =
                TsFilterConfiguration
                        .builder()
                        .setTpid(521)
                        .setSettings(settings)
                        .build();
        f.configure(config);
        f.start();
        f.stop();
        f.close();
    }

    @Test
    public void testTsRecordFilterConfig() throws Exception {
        Filter f = mTuner.openFilter(
                Filter.TYPE_TS, Filter.SUBTYPE_RECORD, 1000, getExecutor(), getFilterCallback());
        assertNotNull(f);
        assertNotEquals(Tuner.INVALID_FILTER_ID, f.getId());

        RecordSettings settings =
                RecordSettings
                        .builder(Filter.TYPE_TS)
                        .setTsIndexMask(
                                RecordSettings.TS_INDEX_FIRST_PACKET
                                        | RecordSettings.TS_INDEX_PRIVATE_DATA)
                        .setScIndexType(RecordSettings.INDEX_TYPE_SC)
                        .setScIndexMask(RecordSettings.SC_INDEX_B_SLICE)
                        .build();
        TsFilterConfiguration config =
                TsFilterConfiguration
                        .builder()
                        .setTpid(521)
                        .setSettings(settings)
                        .build();
        f.configure(config);
        f.start();
        f.stop();
        f.close();
    }

    @Test
    public void testTlvTlvFilterConfig() throws Exception {
        Filter f = mTuner.openFilter(
                Filter.TYPE_TLV, Filter.SUBTYPE_TLV, 1000, getExecutor(), getFilterCallback());
        if (f == null) return;
        assertNotEquals(Tuner.INVALID_FILTER_ID, f.getId());

        TlvFilterConfiguration config =
                TlvFilterConfiguration
                        .builder()
                        .setPacketType(TlvFilterConfiguration.PACKET_TYPE_IPV4)
                        .setCompressedIpPacket(true)
                        .setPassthrough(false)
                        .setSettings(null)
                        .build();
        f.configure(config);
        f.start();
        f.stop();
        f.close();
    }

    @Test
    public void testDescrambler() throws Exception {
        Descrambler d = mTuner.openDescrambler();
        byte[] keyToken = new byte[] {1, 3, 2};
        assertNotNull(d);
        Filter f = mTuner.openFilter(
                Filter.TYPE_TS, Filter.SUBTYPE_SECTION, 1000, getExecutor(), getFilterCallback());
        assertTrue(d.isValidKeyToken(keyToken));
        d.setKeyToken(keyToken);
        d.addPid(Descrambler.PID_TYPE_T, 1, f);
        d.removePid(Descrambler.PID_TYPE_T, 1, f);
        f.close();
        d.close();
    }

    @Test
    public void testDescramblerKeyTokenValidator() throws Exception {
        byte[] invalidToken = new byte[17];
        byte[] validToken = new byte[] {1, 3, 2};
        assertTrue(Descrambler.isValidKeyToken(validToken));
        assertTrue(Descrambler.isValidKeyToken(Tuner.VOID_KEYTOKEN));
        assertFalse(Descrambler.isValidKeyToken(invalidToken));
    }

    @Test
    public void testOpenDvrRecorder() throws Exception {
        DvrRecorder d = mTuner.openDvrRecorder(100, getExecutor(), getRecordListener());
        assertNotNull(d);
        d.close();
    }

    @Test
    public void testOpenDvPlayback() throws Exception {
        DvrPlayback d = mTuner.openDvrPlayback(100, getExecutor(), getPlaybackListener());
        assertNotNull(d);
        d.close();
    }

    @Test
    public void testDemuxCapabilities() throws Exception {
        DemuxCapabilities d = mTuner.getDemuxCapabilities();
        assertNotNull(d);

        d.getDemuxCount();
        d.getRecordCount();
        d.getPlaybackCount();
        d.getTsFilterCount();
        d.getSectionFilterCount();
        d.getAudioFilterCount();
        d.getVideoFilterCount();
        d.getPesFilterCount();
        d.getPcrFilterCount();
        d.getSectionFilterLength();
        d.getFilterCapabilities();
        d.getLinkCapabilities();
        d.isTimeFilterSupported();
        d.getFilterTypeCapabilityList();
    }

    @Test
    public void testResourceLostListener() throws Exception {
        mTuner.setResourceLostListener(getExecutor(), new Tuner.OnResourceLostListener() {
            @Override
            public void onResourceLost(Tuner tuner) {
            }
        });
        mTuner.clearResourceLostListener();
    }

    @Test
    public void testOnTuneEventListener() throws Exception {
        mTuner.setOnTuneEventListener(getExecutor(), new OnTuneEventListener() {
            @Override
            public void onTuneEvent(int tuneEvent) {
            }
        });
        mTuner.clearOnTuneEventListener();
    }

    @Test
    public void testUpdateResourcePriority() throws Exception {
        mTuner.updateResourcePriority(100, 20);
    }

    @Test
    public void testResourceReclaimed() throws Exception {
        List<Integer> ids = mTuner.getFrontendIds();
        assertFalse(ids.isEmpty());
        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        FrontendSettings feSettings = createFrontendSettings(info);

        // first tune with mTuner to acquire resource
        int res = mTuner.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);
        assertNotNull(mTuner.getFrontendInfo());

        // now tune with a higher priority tuner to have mTuner's resource reclaimed
        Tuner higherPrioTuner = new Tuner(mContext, null, 200);
        res = higherPrioTuner.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);
        assertNotNull(higherPrioTuner.getFrontendInfo());

        higherPrioTuner.close();
    }

    // TODO: change this to use ITunerResourceTestServer
    @Test
    public void testResourceReclaimedDifferentThread() throws Exception {
        List<Integer> ids = mTuner.getFrontendIds();
        assertFalse(ids.isEmpty());
        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        FrontendSettings feSettings = createFrontendSettings(info);

        // first tune with mTuner to acquire resource
        int res = mTuner.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);
        assertNotNull(mTuner.getFrontendInfo());

        // now tune with a higher priority tuner to have mTuner's resource reclaimed
        TunerHandler tunerHandler = createTunerHandler(null);
        Message msgCreate = new Message();
        msgCreate.what = MSG_TUNER_HANDLER_CREATE;
        msgCreate.arg1 = 200;
        tunerHandler.sendMessage(msgCreate);
        mTunerHandlerTaskComplete.block();
        mTunerHandlerTaskComplete.close();

        Message msgTune = new Message();
        msgTune.what = MSG_TUNER_HANDLER_TUNE;
        msgTune.obj = (Object) feSettings;
        tunerHandler.sendMessage(msgTune);

        // call mTuner.close in parallel
        int sleepMS = 1;
        //int sleepMS = (int) (Math.random() * 3.);
        try {
            Thread.sleep(sleepMS);
        } catch (Exception e) { } // ignore
        mTuner.close();
        mTuner = null;

        mTunerHandlerTaskComplete.block();
        mTunerHandlerTaskComplete.close();
        res = tunerHandler.getResult();
        assertEquals(Tuner.RESULT_SUCCESS, res);

        Tuner higherPrioTuner = tunerHandler.getTuner();
        assertNotNull(higherPrioTuner.getFrontendInfo());

        Message msgClose = new Message();
        msgClose.what = MSG_TUNER_HANDLER_CLOSE;
        tunerHandler.sendMessage(msgClose);

    }

    @Test
    public void testResourceReclaimedDifferentProcess() throws Exception {
        List<Integer> ids = mTuner.getFrontendIds();
        int frontendIndex = 0;
        assertFalse(ids.isEmpty());
        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(frontendIndex));
        FrontendSettings feSettings = createFrontendSettings(info);

        // set up the test server
        TunerResourceTestServiceConnection connection = new TunerResourceTestServiceConnection();
        ITunerResourceTestServer tunerResourceTestServer = null;
        Intent intent = new Intent(mContext, TunerResourceTestService.class);

        // get the TunerResourceTestService
        mContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        tunerResourceTestServer = connection.getService();

        // CASE1 - normal reclaim
        //
        // first tune with mTuner to acquire resource
        int res = mTuner.tune(feSettings);
        boolean tunerReclaimed = false;
        assertEquals(Tuner.RESULT_SUCCESS, res);
        assertNotNull(mTuner.getFrontendInfo());

        // now tune with a higher priority tuner to have mTuner's resource reclaimed

        // create higher priority tuner
        tunerResourceTestServer.createTuner(200);

        // now tune on higher priority tuner to get mTuner reclaimed
        res = tunerResourceTestServer.tune(frontendIndex);
        assertEquals(Tuner.RESULT_SUCCESS, res);

        try {
            int[] statusCapabilities = info.getStatusCapabilities();
            mTuner.getFrontendStatus(statusCapabilities);

        } catch (IllegalStateException e) {
            tunerReclaimed = true;
            mTuner.close();
            mTuner = null;
        }

        // confirm if the mTuner is reclaimed
        assertTrue(tunerReclaimed);

        tunerResourceTestServer.closeTuner();
        assertTrue(tunerResourceTestServer.verifyTunerIsNull());


        // CASE2 - race between Tuner#close() and reclaim
        mTuner = new Tuner(mContext, null, 100);
        res = mTuner.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);
        assertNotNull(mTuner.getFrontendInfo());

        tunerResourceTestServer.createTuner(200);
        tunerResourceTestServer.tuneAsync(frontendIndex);

        // adjust timing to induce race/deadlock
        int sleepMS = 4;
        //int sleepMS = (int) (Math.random() * 5.);
        try {
            Thread.sleep(sleepMS);
        } catch (Exception e) { } // ignore
        mTuner.close();
        mTuner = null;

        tunerResourceTestServer.closeTuner();

        // unbind
        mContext.unbindService(connection);
    }

    @Test
    public void testApplyFrontend() throws Exception {
        List<FrontendInfo> frontendInfos = mTuner.getAvailableFrontendInfos();
        if (frontendInfos == null) return;
        assertFalse(frontendInfos.isEmpty());

        FrontendInfo frontendInfo = frontendInfos.get(0);
        int result = mTuner.applyFrontend(frontendInfo);
        assertEquals(Tuner.RESULT_SUCCESS, result);

        // Request a frontend again cases
        FrontendInfo secondFrontend = null;
        for (FrontendInfo info: frontendInfos) {
            result = mTuner.applyFrontend(info);
            assertEquals(Tuner.RESULT_INVALID_STATE, result);
        }
    }

    @Test
    public void testShareFrontendFromTuner() throws Exception {
        Tuner tuner100 = new Tuner(mContext, null, 100);
        List<Integer> ids = tuner100.getFrontendIds();
        assertFalse(ids.isEmpty());
        FrontendInfo info = tuner100.getFrontendInfoById(ids.get(0));
        FrontendSettings feSettings = createFrontendSettings(info);
        int[] statusTypes = {1};
        boolean exceptionThrown = false;
        int res;

        // CASE1: check resource reclaim while sharee's priority < owner's priority
        // let tuner100 share from tuner200
        Tuner tuner200 = new Tuner(mContext, null, 200);
        res = tuner200.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);

        info = tuner200.getFrontendInfoById(ids.get(0));
        res = tuner200.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);

        tuner100.shareFrontendFromTuner(tuner200);
        // call openFilter to trigger ITunerDemux.setFrontendDataSourceById()
        Filter f = tuner100.openFilter(
                Filter.TYPE_TS, Filter.SUBTYPE_SECTION, 1000, getExecutor(), getFilterCallback());
        assertNotNull(f);

        // setup onTuneCallback
        TunerTestOnTuneEventListener cb100 = new TunerTestOnTuneEventListener();
        TunerTestOnTuneEventListener cb200 = new TunerTestOnTuneEventListener();

        // tune again on the owner
        info = tuner200.getFrontendInfoById(ids.get(1));
        tuner100.setOnTuneEventListener(getExecutor(), cb100);
        tuner200.setOnTuneEventListener(getExecutor(), cb200);
        res = tuner200.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);
        assertEquals(OnTuneEventListener.SIGNAL_LOCKED, cb100.getLastTuneEvent());
        assertEquals(OnTuneEventListener.SIGNAL_LOCKED, cb200.getLastTuneEvent());
        tuner100.clearOnTuneEventListener();
        tuner200.clearOnTuneEventListener();

        // now let the higher priority tuner steal the resource
        Tuner tuner300 = new Tuner(mContext, null, 300);
        res = tuner300.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);

        // confirm owner & sharee's resource gets reclaimed by confirming an exception is thrown
        exceptionThrown = false;
        try {
            tuner200.getFrontendStatus(statusTypes);
        } catch (Exception e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);

        exceptionThrown = false;
        try {
            tuner100.getFrontendStatus(statusTypes);
        } catch (Exception e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);

        tuner100.close();
        tuner200.close();
        tuner300.close();


        // CASE2: check resource reclaim fail when sharee's priority > new requester
        tuner100 = new Tuner(mContext, null, 100);
        res = tuner100.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);

        tuner300 = new Tuner(mContext, null, 300);
        tuner300.shareFrontendFromTuner(tuner100);
        f = tuner100.openFilter(
                Filter.TYPE_TS, Filter.SUBTYPE_SECTION, 1000, getExecutor(), getFilterCallback());
        assertNotNull(f);

        tuner200 = new Tuner(mContext, null, 200);
        res = tuner200.tune(feSettings);
        assertNotEquals(Tuner.RESULT_SUCCESS, res);

        // confirm the original tuner is still intact
        res = tuner100.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);

        tuner100.close();
        tuner200.close();
        tuner300.close();
    }

    private void testTransferFeOwnershipSingleTuner() {
        List<Integer> ids = mTuner.getFrontendIds();
        if (ids == null) {
            return;
        }
        assertFalse(ids.isEmpty());
        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        FrontendSettings feSettings = createFrontendSettings(info);

        // SCENARIO 1 - transfer and close the previous owner

        // First create a tuner and tune() to acquire frontend resource
        Tuner tunerA = new Tuner(mContext, null, 100);
        int res = tunerA.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);

        // Create another tuner and share frontend from tunerA
        Tuner tunerB = new Tuner(mContext, null, 500);
        tunerB.shareFrontendFromTuner(tunerA);
        DvrRecorder d = tunerB.openDvrRecorder(100, getExecutor(), getRecordListener());
        assertNotNull(d);

        // Call transferOwner in the wrong configurations and confirm it fails
        assertEquals(Tuner.RESULT_INVALID_STATE, tunerB.transferOwner(tunerA));
        Tuner nonSharee = new Tuner(mContext, null, 300);
        assertEquals(Tuner.RESULT_INVALID_STATE, tunerA.transferOwner(nonSharee));
        nonSharee.close();

        // Now call it correctly to transfer ownership from tunerA to tunerB
        assertEquals(Tuner.RESULT_SUCCESS, tunerA.transferOwner(tunerB));

        // Close the original owner (tunerA)
        tunerA.close();

        // Confirm the new owner (tunerB) is still functional
        assertNotNull(tunerB.getFrontendInfo());

        // Close the new owner (tunerB)
        d.close();
        tunerB.close();

        // SCENARIO 2 - transfer and closeFrontend and tune on the previous owner

        // First create a tuner and tune() to acquire frontend resource
        tunerA = new Tuner(mContext, null, 200);
        res = tunerA.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);

        // Create another tuner and share frontend from tunerA
        tunerB = new Tuner(mContext, null, 100);
        tunerB.shareFrontendFromTuner(tunerA);
        assertNotNull(tunerB.getFrontendInfo());

        // Transfer ownership from tunerA to tunerB
        assertEquals(Tuner.RESULT_SUCCESS, tunerA.transferOwner(tunerB));

        // Close frontend for the original owner (tunerA)
        tunerA.closeFrontend();

        // Confirm tune works without going through Tuner.close() even after transferOwner()
        // The purpose isn't to get tunerB's frontend revoked, but doing so as singletuner
        // based test has wider coverage
        res = tunerA.tune(feSettings); // this should reclaim tunerB
        assertEquals(Tuner.RESULT_SUCCESS, res);

        // Confirm tuberB is revoked
        assertNull(tunerB.getFrontendInfo());

        // Close tunerA
        tunerA.close();

        // close TunerB just in case
        tunerB.close();
    }

    private void testTransferFeAndCiCamOwnership() {
        List<Integer> ids = mTuner.getFrontendIds();
        assertNotNull(ids);
        assertFalse(ids.isEmpty());
        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        FrontendSettings feSettings = createFrontendSettings(info);

        // Create tuner and tune to get frontend resource
        Tuner tunerA = new Tuner(mContext, null, 100);
        assertEquals(Tuner.RESULT_SUCCESS, tunerA.tune(feSettings));

        int ciCamId = 0;
        boolean linkCiCamToFrontendSupported = false;

        // connect CiCam to Frontend
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            // TODO: get real CiCam id from MediaCas
            int res = tunerA.connectFrontendToCiCam(ciCamId);
            // INVALID_LTS_ID means hal doesn't support CiCam
            if (res == Tuner.INVALID_LTS_ID) {
                // Resources cleanup before return
                tunerA.close();
                return;
            }
            assertEquals(Tuner.RESULT_SUCCESS, tunerA.connectFrontendToCiCam(ciCamId));
            linkCiCamToFrontendSupported = true;
        } else {
            assertEquals(Tuner.INVALID_LTS_ID, tunerA.connectFrontendToCiCam(ciCamId));
        }

        // connect CiCam to Demux
        assertEquals(Tuner.RESULT_SUCCESS, tunerA.connectCiCam(ciCamId));

        // start another tuner and connect the same CiCam to its own demux
        Tuner tunerB = new Tuner(mContext, null, 400);
        tunerB.shareFrontendFromTuner(tunerA);
        assertNotNull(tunerB.getFrontendInfo());
        assertEquals(Tuner.RESULT_SUCCESS, tunerB.connectCiCam(ciCamId));

        // unlink CiCam to Demux in tunerA and transfer ownership
        assertEquals(Tuner.RESULT_SUCCESS, tunerA.disconnectCiCam());
        assertEquals(Tuner.RESULT_SUCCESS, tunerA.transferOwner(tunerB));

        // close the original owner
        tunerA.close();

        // disconnect CiCam from demux
        assertEquals(Tuner.RESULT_SUCCESS, tunerB.disconnectCiCam());

        // let Tuner.close() handle the release of CiCam
        tunerB.close();

        // now that the CiCam is released, disconnectFrontendToCiCam() should fail
        assertEquals(Tuner.RESULT_INVALID_STATE, tunerB.disconnectFrontendToCiCam(ciCamId));

        // see if tune still works just in case
        tunerA = new Tuner(mContext, null, 100);
        assertEquals(Tuner.RESULT_SUCCESS, tunerA.tune(feSettings));
        tunerA.close();
    }

    private void testTransferFeAndLnbOwnership() {
        List<Integer> ids = mTuner.getFrontendIds();
        assertNotNull(ids);
        assertFalse(ids.isEmpty());
        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        FrontendSettings feSettings = createFrontendSettings(info);

        // Create tuner and tune to acquire frontend resource
        Tuner tunerA = new Tuner(mContext, null, 100);
        assertEquals(Tuner.RESULT_SUCCESS, tunerA.tune(feSettings));

        // Open Lnb and check the callback
        TunerTestLnbCallback lnbCB1 = new TunerTestLnbCallback();
        Lnb lnbA = tunerA.openLnb(getExecutor(), lnbCB1);
        assertNotNull(lnbA);
        lnbA.setVoltage(Lnb.VOLTAGE_5V);
        lnbA.setTone(Lnb.TONE_CONTINUOUS);
        lnbA.sendDiseqcMessage(new byte[] {1, 2});
        assertTrue(lnbCB1.getOnDiseqcMessageCalled());
        lnbCB1.resetOnDiseqcMessageCalled();

        // Create another tuner and share from tunerB
        Tuner tunerB = new Tuner(mContext, null, 300);
        tunerB.shareFrontendFromTuner(tunerA);

        // add sharee and check the callback
        TunerTestLnbCallback lnbCB2 = new TunerTestLnbCallback();
        lnbA.addCallback(getExecutor(), lnbCB2);
        lnbA.sendDiseqcMessage(new byte[] {1, 2});
        assertTrue(lnbCB1.getOnDiseqcMessageCalled());
        lnbCB1.resetOnDiseqcMessageCalled();
        assertTrue(lnbCB2.getOnDiseqcMessageCalled());
        lnbCB2.resetOnDiseqcMessageCalled();

        // transfer owner and check callback
        assertEquals(Tuner.RESULT_SUCCESS, tunerA.transferOwner(tunerB));
        lnbA.sendDiseqcMessage(new byte[] {1, 2});
        assertTrue(lnbCB1.getOnDiseqcMessageCalled());
        lnbCB1.resetOnDiseqcMessageCalled();
        assertTrue(lnbCB2.getOnDiseqcMessageCalled());
        lnbCB2.resetOnDiseqcMessageCalled();

        // remove the owner callback (just for testing)
        assertTrue(lnbA.removeCallback(lnbCB2));

        // remove sharee and check callback
        assertTrue(lnbA.removeCallback(lnbCB1));
        lnbA.sendDiseqcMessage(new byte[] {1, 2});
        assertFalse(lnbCB1.getOnDiseqcMessageCalled());
        lnbCB1.resetOnDiseqcMessageCalled();
        assertFalse(lnbCB2.getOnDiseqcMessageCalled());
        lnbCB2.resetOnDiseqcMessageCalled();

        // close the original owner
        tunerA.close();

        // confirm the new owner is still intact
        int[] statusCapabilities = info.getStatusCapabilities();
        assertNotNull(statusCapabilities);
        FrontendStatus status = tunerB.getFrontendStatus(statusCapabilities);
        assertNotNull(status);

        tunerB.close();
    }

    @Test
    public void testTransferOwner() throws Exception {
        testTransferFeOwnershipSingleTuner();
        testTransferFeAndCiCamOwnership();
        testTransferFeAndLnbOwnership();
    }

    @Test
    public void testClose() throws Exception {
        Tuner other = new Tuner(mContext, null, 100);

        List<Integer> ids = other.getFrontendIds();
        if (ids == null) return;
        assertFalse(ids.isEmpty());
        FrontendInfo info = other.getFrontendInfoById(ids.get(0));

        FrontendSettings feSettings = createFrontendSettings(info);
        int res = other.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);
        assertNotNull(other.getFrontendInfo());

        other.close();

        // make sure pre-existing tuner is still functional
        res = mTuner.applyFrontend(info);
        assertEquals(Tuner.RESULT_SUCCESS, res);
        assertNotNull(mTuner.getFrontendInfo());
        mTuner.closeFrontend();
        res = mTuner.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);
        assertNotNull(mTuner.getFrontendInfo());

        // Frontend sharing scenario 1: close owner first
        // create sharee
        Tuner sharee = new Tuner(mContext, null, 100);
        sharee.shareFrontendFromTuner(mTuner);

        // close the owner
        mTuner.close();
        mTuner = null;

        // check the frontend of sharee is also released
        assertNull(sharee.getFrontendInfo());

        sharee.close();

        // Frontend sharing scenario 2: close sharee first
        // create owner first
        mTuner = new Tuner(mContext, null, 100);
        res = mTuner.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);

        // create sharee
        sharee = new Tuner(mContext, null, 100);
        sharee.shareFrontendFromTuner(mTuner);

        // close sharee
        sharee.close();

        // confirm owner is still intact
        int[] statusCapabilities = info.getStatusCapabilities();
        assertNotNull(statusCapabilities);
        FrontendStatus status = mTuner.getFrontendStatus(statusCapabilities);
        assertNotNull(status);
    }

    @Test
    public void testCloseFrontend() throws Exception {
        List<Integer> ids = mTuner.getFrontendIds();
        if (ids == null) {
            return;
        }

        // SCENARIO 1 - without Lnb
        assertFalse(ids.isEmpty());
        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        FrontendSettings feSettings = createFrontendSettings(info);
        int res = mTuner.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);
        assertNotNull(mTuner.getFrontendInfo());

        // now close frontend
        mTuner.closeFrontend();

        // confirm frontend is closed
        int[] statusCapabilities = info.getStatusCapabilities();
        boolean frontendClosed = false;
        try {
            mTuner.getFrontendStatus(statusCapabilities);

        } catch (IllegalStateException e) {
            frontendClosed = true;
        }
        assertTrue(frontendClosed);

        // now tune to a different setting
        info = mTuner.getFrontendInfoById(ids.get(1));
        feSettings = createFrontendSettings(info);
        mTuner.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);
        assertNotNull(mTuner.getFrontendInfo());
        FrontendStatus status = mTuner.getFrontendStatus(statusCapabilities);
        assertNotNull(status);

        // SCENARIO 2 - with Lnb

        TunerTestLnbCallback lnbCB1 = new TunerTestLnbCallback();
        Lnb lnb = mTuner.openLnb(getExecutor(), lnbCB1);
        if (lnb == null) {
            return;
        }

        mTuner.closeFrontend();
        // confirm frontend is closed
        statusCapabilities = info.getStatusCapabilities();
        frontendClosed = false;
        try {
            mTuner.getFrontendStatus(statusCapabilities);

        } catch (IllegalStateException e) {
            frontendClosed = true;
        }
        assertTrue(frontendClosed);

        info = mTuner.getFrontendInfoById(ids.get(0));
        feSettings = createFrontendSettings(info);
        mTuner.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);
        assertNotNull(mTuner.getFrontendInfo());
        status = mTuner.getFrontendStatus(statusCapabilities);
        assertNotNull(status);
    }

    @Test
    public void testHasUnusedFrontend1() throws Exception {
        prepTRMCustomFeResourceMapTest();

        // Use try block to ensure restoring the TunerResourceManager
        // Note: the handles will be changed from the original value, but should be OK
        try {
            TunerFrontendInfo[] infos = new TunerFrontendInfo[6];
            // tunerFrontendInfo(handle, FrontendSettings.TYPE_*, exclusiveGroupId
            infos[0] = tunerFrontendInfo(1, FrontendSettings.TYPE_DVBT, 1);
            infos[1] = tunerFrontendInfo(2, FrontendSettings.TYPE_DVBC, 1);
            infos[2] = tunerFrontendInfo(3, FrontendSettings.TYPE_DVBS, 1);
            infos[3] = tunerFrontendInfo(4, FrontendSettings.TYPE_DVBT, 2);
            infos[4] = tunerFrontendInfo(5, FrontendSettings.TYPE_DVBC, 2);
            infos[5] = tunerFrontendInfo(6, FrontendSettings.TYPE_DVBS, 2);

            mTunerResourceManager.setFrontendInfoList(infos);

            Tuner A = new Tuner(mContext, null, 100);
            Tuner B = new Tuner(mContext, null, 100);
            Tuner C = new Tuner(mContext, null, 100);

            // check before anyone holds resource
            assertFalse(A.hasUnusedFrontend(FrontendSettings.TYPE_UNDEFINED));
            assertFalse(A.hasUnusedFrontend(FrontendSettings.TYPE_ATSC));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBC));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBS));

            // let B hold resource
            assignFeResource(B.getClientId(), FrontendSettings.TYPE_DVBT,
                             true /* expectedResult */, 1 /* expectedHandle */);

            // check when one of the two exclusive groups are held
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBC));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBS));

            assertTrue(B.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));

            // let C hold the resource
            assignFeResource(C.getClientId(), FrontendSettings.TYPE_DVBC,
                             true /* expectedResult */, 5 /* expectedHandle */);

            assertFalse(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));
            assertFalse(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBC));
            assertFalse(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBS));

            assertFalse(B.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));
            assertFalse(C.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));

            // let go of B's resource
            B.close();

            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBC));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBS));

            assertTrue(B.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));
            assertTrue(C.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));

            C.close();
            A.close();
        } catch (Exception e) {
            throw (e);
        } finally {
            cleanupTRMCustomFeResourceMapTest();
        }
    }

    @Test
    public void testHasUnusedFrontend2() throws Exception {
        prepTRMCustomFeResourceMapTest();

        // Use try block to ensure restoring the TunerResourceManager
        // Note: the handles will be changed from the original value, but should be OK
        try {
            TunerFrontendInfo[] infos = new TunerFrontendInfo[5];
            // tunerFrontendInfo(handle, FrontendSettings.TYPE_*, exclusiveGroupId
            infos[0] = tunerFrontendInfo(1, FrontendSettings.TYPE_DVBT, 1);
            infos[1] = tunerFrontendInfo(2, FrontendSettings.TYPE_DVBC, 1);
            infos[2] = tunerFrontendInfo(3, FrontendSettings.TYPE_DVBT, 2);
            infos[3] = tunerFrontendInfo(4, FrontendSettings.TYPE_DVBC, 2);
            infos[4] = tunerFrontendInfo(5, FrontendSettings.TYPE_DVBS, 3);

            mTunerResourceManager.setFrontendInfoList(infos);

            Tuner A = new Tuner(mContext, null, 100);
            Tuner B = new Tuner(mContext, null, 100);
            Tuner C = new Tuner(mContext, null, 100);

            // let B hold resource
            assignFeResource(B.getClientId(), FrontendSettings.TYPE_DVBT,
                             true /* expectedResult */, 1 /* expectedHandle */);

            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBC));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBS));

            // let C hold the resource
            assignFeResource(C.getClientId(), FrontendSettings.TYPE_DVBC,
                             true /* expectedResult */, 4 /* expectedHandle */);

            assertFalse(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));
            assertFalse(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBC));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBS));

            B.close();
            C.close();
        } catch (Exception e) {
            throw (e);
        } finally {
            cleanupTRMCustomFeResourceMapTest();
        }
    }

    @Test
    public void testHasUnusedFrontend3() throws Exception {
        prepTRMCustomFeResourceMapTest();

        // Use try block to ensure restoring the TunerResourceManager
        // Note: the handles will be changed from the original value, but should be OK
        try {
            TunerFrontendInfo[] infos = new TunerFrontendInfo[6];
            // tunerFrontendInfo(handle, FrontendSettings.TYPE_*, exclusiveGroupId
            infos[0] = tunerFrontendInfo(1, FrontendSettings.TYPE_DVBT, 1);
            infos[1] = tunerFrontendInfo(2, FrontendSettings.TYPE_DVBC, 1);
            infos[2] = tunerFrontendInfo(3, FrontendSettings.TYPE_DVBS, 1);
            infos[3] = tunerFrontendInfo(4, FrontendSettings.TYPE_DVBT, 2);
            infos[4] = tunerFrontendInfo(5, FrontendSettings.TYPE_DVBC, 2);
            infos[5] = tunerFrontendInfo(6, FrontendSettings.TYPE_DVBS, 2);

            mTunerResourceManager.setFrontendInfoList(infos);

            Tuner A = new Tuner(mContext, null, 100);
            Tuner B = new Tuner(mContext, null, 100);
            Tuner C = new Tuner(mContext, null, 100);

            // let B hold resource
            assignFeResource(B.getClientId(), FrontendSettings.TYPE_DVBT,
                             true /* expectedResult */, 1 /* expectedHandle */);

            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBC));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBS));

            // let C share from B
            mTunerResourceManager.shareFrontend(C.getClientId(), B.getClientId());

            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBC));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBS));

            A.close();
            C.close();
            B.close();
        } catch (Exception e) {
            throw (e);
        } finally {
            cleanupTRMCustomFeResourceMapTest();
        }
    }

    @Test
    public void testIsLowestPriorityCornerCases() throws Exception {
        prepTRMCustomFeResourceMapTest();

        // Use try block to ensure restoring the TunerResourceManager
        // Note: the handles will be changed from the original value, but should be OK
        try {
            setupSingleTunerSetupForIsLowestPriority();

            // must return true when non existing frontend type is specified
            assertTrue(mTuner.isLowestPriority(FrontendSettings.TYPE_UNDEFINED));
            assertTrue(mTuner.isLowestPriority(FrontendSettings.TYPE_ATSC));

            // must return true when no one is holding the resource
            assertTrue(mTuner.isLowestPriority(FrontendSettings.TYPE_DVBT));
            assertTrue(mTuner.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertTrue(mTuner.isLowestPriority(FrontendSettings.TYPE_DVBT));

            // must return true when the callee is the only one holding the resource
            assignFeResource(mTuner.getClientId(), FrontendSettings.TYPE_DVBT,
                             true /* expectedResult */, 1 /* expectedHandle */);
            assertTrue(mTuner.isLowestPriority(FrontendSettings.TYPE_DVBT));
            assertTrue(mTuner.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertTrue(mTuner.isLowestPriority(FrontendSettings.TYPE_DVBT));

        } catch (Exception e) {
            throw (e);
        } finally {
            cleanupTRMCustomFeResourceMapTest();
        }
    }

    @Test
    public void testIsLowestPriorityTwoClients() throws Exception {
        prepTRMCustomFeResourceMapTest();

        // Use try block to ensure restoring the TunerResourceManager
        // Note: the handles will be changed from the original value, but should be OK
        try {
            setupSingleTunerSetupForIsLowestPriority();
            testTwoClientsForIsLowestPriority(200, 100); // A > B
            testTwoClientsForIsLowestPriority(100, 200); // A < B
            testTwoClientsForIsLowestPriority(100, 100); // A = B

            setupDualTunerSetupForIsLowestPriority();
            testTwoClientsForIsLowestPriority(200, 100); // A > B
            testTwoClientsForIsLowestPriority(100, 200); // A < B
            testTwoClientsForIsLowestPriority(100, 100); // A = B
        } catch (Exception e) {
            throw (e);
        } finally {
            cleanupTRMCustomFeResourceMapTest();
        }
    }

    @Test
    public void testIsLowestPriorityThreeClients() throws Exception {
        prepTRMCustomFeResourceMapTest();

        // Use try block to ensure restoring the TunerResourceManager
        // Note: the handles will be changed from the original value, but should be OK
        try {
            setupDualTunerSetupForIsLowestPriority();
            testThreeClientsForIsLowestPriority(300, 200, 100); // A > B > C
            testThreeClientsForIsLowestPriority(300, 100, 200); // A > C > B
            testThreeClientsForIsLowestPriority(200, 300, 100); // B > A > C
            testThreeClientsForIsLowestPriority(200, 100, 300); // C > A > B
            testThreeClientsForIsLowestPriority(100, 300, 200); // B > C > A
            testThreeClientsForIsLowestPriority(100, 200, 300); // C > B > A
            testThreeClientsForIsLowestPriority(100, 100, 100); // A = B = C
            testThreeClientsForIsLowestPriority(200, 200, 100); // A = B > C
            testThreeClientsForIsLowestPriority(200, 100, 100); // A > B = C
            testThreeClientsForIsLowestPriority(200, 100, 200); // A = C > B
            testThreeClientsForIsLowestPriority(200, 300, 200); // B > A = C
            testThreeClientsForIsLowestPriority(100, 100, 200); // C > A = B
            testThreeClientsForIsLowestPriority(100, 200, 200); // B = C > A
        } catch (Exception e) {
            throw (e);
        } finally {
            cleanupTRMCustomFeResourceMapTest();
        }
    }

    private TunerFrontendInfo tunerFrontendInfo(
            int handle, int frontendType, int exclusiveGroupId) {
        TunerFrontendInfo info = new TunerFrontendInfo();
        info.handle = handle;
        info.type = frontendType;
        info.exclusiveGroupId = exclusiveGroupId;
        return info;
    }

    /**
     * Prep function for TunerTest that requires custom frontend resource map
     */
    private void prepTRMCustomFeResourceMapTest() {
        if (mTunerResourceManager == null) {
            mTunerResourceManager = (TunerResourceManager)
                    mContext.getSystemService(Context.TV_TUNER_RESOURCE_MGR_SERVICE);
        }
        mTunerResourceManager.storeResourceMap(TunerResourceManager.TUNER_RESOURCE_TYPE_FRONTEND);
        mTunerResourceManager.clearResourceMap(TunerResourceManager.TUNER_RESOURCE_TYPE_FRONTEND);
    }

    /**
     * Clean up function for TunerTest that requires custom frontend resource map
     */
    private void cleanupTRMCustomFeResourceMapTest() {
        // first close mTuner in case a frontend resource is opened
        if (mTuner != null) {
            mTuner.close();
            mTuner = null;
        }

        // now restore the original frontend resource map
        if (mTunerResourceManager != null) {
            mTunerResourceManager.restoreResourceMap(
                    TunerResourceManager.TUNER_RESOURCE_TYPE_FRONTEND);
        }
    }

    private void clearFrontendInfoList() {
        if (mTunerResourceManager != null) {
            mTunerResourceManager.clearResourceMap(
                    TunerResourceManager.TUNER_RESOURCE_TYPE_FRONTEND);
        }
    }

    private void assignFeResource(int clientId, int frontendType,
                                  boolean expectedResult, int expectedHandle) {
        int[] feHandle = new int[1];
        TunerFrontendRequest request = new TunerFrontendRequest();
        request.clientId = clientId;
        request.frontendType = frontendType;
        boolean granted = mTunerResourceManager.requestFrontend(request, feHandle);
        assertEquals(granted, expectedResult);
        assertEquals(feHandle[0], expectedHandle);
    }

    private void setupSingleTunerSetupForIsLowestPriority() {
        // first clear the frontend resource to register new set of resources
        clearFrontendInfoList();

        TunerFrontendInfo[] infos = new TunerFrontendInfo[3];
        // tunerFrontendInfo(handle, FrontendSettings.TYPE_*, exclusiveGroupId
        infos[0] = tunerFrontendInfo(1, FrontendSettings.TYPE_DVBT, 1);
        infos[1] = tunerFrontendInfo(2, FrontendSettings.TYPE_DVBC, 1);
        infos[2] = tunerFrontendInfo(3, FrontendSettings.TYPE_DVBS, 1);

        mTunerResourceManager.setFrontendInfoList(infos);
    }

    private void setupDualTunerSetupForIsLowestPriority() {
        // first clear the frontend resource to register new set of resources
        clearFrontendInfoList();

        TunerFrontendInfo[] infos = new TunerFrontendInfo[6];
        // tunerFrontendInfo(handle, FrontendSettings.TYPE_*, exclusiveGroupId
        infos[0] = tunerFrontendInfo(1, FrontendSettings.TYPE_DVBT, 1);
        infos[1] = tunerFrontendInfo(2, FrontendSettings.TYPE_DVBC, 1);
        infos[2] = tunerFrontendInfo(3, FrontendSettings.TYPE_DVBS, 1);
        infos[3] = tunerFrontendInfo(4, FrontendSettings.TYPE_DVBT, 2);
        infos[4] = tunerFrontendInfo(5, FrontendSettings.TYPE_DVBC, 2);
        infos[5] = tunerFrontendInfo(6, FrontendSettings.TYPE_DVBS, 2);

        mTunerResourceManager.setFrontendInfoList(infos);
    }


    private void testTwoClientsForIsLowestPriority(int prioA, int prioB) {

        Tuner A = new Tuner(mContext, null, prioA);
        Tuner B = new Tuner(mContext, null, prioB);

        // all should return true
        assertTrue(A.isLowestPriority(FrontendSettings.TYPE_DVBT));
        assertTrue(B.isLowestPriority(FrontendSettings.TYPE_DVBC));

        // let A hold resource
        assignFeResource(A.getClientId(), FrontendSettings.TYPE_DVBT,
                         true /* expectedResult */, 1 /* expectedHandle */);

        // should return true for A as A is the sole holder
        assertTrue(A.isLowestPriority(FrontendSettings.TYPE_DVBT));
        // should return false for B only if A < B
        if ( prioA < prioB ) {
            assertFalse(B.isLowestPriority(FrontendSettings.TYPE_DVBC));
        } else {
            assertTrue(B.isLowestPriority(FrontendSettings.TYPE_DVBC));
        }

        A.close();
        B.close();
    }

    private void testThreeClientsForIsLowestPriority(int prioA, int prioB, int prioC) {

        Tuner A = new Tuner(mContext, null, prioA);
        Tuner B = new Tuner(mContext, null, prioB);
        Tuner C = new Tuner(mContext, null, prioC);

        // all should return true
        assertTrue(A.isLowestPriority(FrontendSettings.TYPE_DVBT));
        assertTrue(B.isLowestPriority(FrontendSettings.TYPE_DVBC));
        assertTrue(C.isLowestPriority(FrontendSettings.TYPE_DVBS));

        // let A & C hold resource
        assignFeResource(A.getClientId(), FrontendSettings.TYPE_DVBT,
                         true /* expectedResult */, 1 /* expectedHandle */);

        assignFeResource(C.getClientId(), FrontendSettings.TYPE_DVBC,
                         true /* expectedResult */, 5 /* expectedHandle */);

        // should return false for B only if A < B
        if (prioA > prioB && prioB > prioC) {
            assertFalse(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertFalse(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertTrue(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioA > prioC && prioC > prioB) {
            assertFalse(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertTrue(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertTrue(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioA > prioC && prioC > prioB) {
            assertFalse(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertTrue(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertTrue(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioB > prioA && prioA > prioC) {
            assertFalse(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertFalse(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertTrue(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioC > prioA && prioA > prioB) {
            assertTrue(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertTrue(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertFalse(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioB > prioC && prioC > prioA) {
            assertTrue(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertFalse(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertFalse(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioC > prioB && prioB > prioA) {
            assertTrue(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertFalse(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertFalse(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioA == prioB && prioB == prioC) {
            assertTrue(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertTrue(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertTrue(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioA == prioB && prioB > prioC) {
            assertFalse(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertFalse(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertTrue(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioA > prioB && prioB == prioC) {
            assertFalse(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertTrue(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertTrue(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioA == prioC && prioC > prioB) {
            assertTrue(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertTrue(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertTrue(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioB > prioA && prioA == prioC) {
            assertTrue(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertFalse(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertTrue(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioC > prioA && prioA == prioB) {
            assertTrue(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertTrue(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertFalse(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioB == prioC && prioC > prioA) {
            assertTrue(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertFalse(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertFalse(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        }

        A.close();
        B.close();
        C.close();
    }

    @Test
    public void testSharedFilterOneProcess() throws Exception {
        Filter f = createTsSectionFilter(mTuner, getExecutor(), getFilterCallback());
        assertTrue(f != null);

        String token1 = f.acquireSharedFilterToken();
        assertTrue(token1 != null);

        String token2 = f.acquireSharedFilterToken();
        assertTrue(token2 == null);

        // Tune a frontend before start the filter
        List<Integer> ids = mTuner.getFrontendIds();
        assertFalse(ids.isEmpty());

        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        int res = mTuner.tune(createFrontendSettings(info));
        assertEquals(Tuner.RESULT_SUCCESS, res);

        Settings settings = SectionSettingsWithTableInfo
                .builder(Filter.TYPE_TS)
                .setTableId(2)
                .setVersion(1)
                .setCrcEnabled(true)
                .setRaw(false)
                .setRepeat(false)
                .build();
        FilterConfiguration config = TsFilterConfiguration
                .builder()
                .setTpid(10)
                .setSettings(settings)
                .build();

        assertEquals(f.configure(config), Tuner.RESULT_INVALID_STATE);
        assertEquals(f.setMonitorEventMask(Filter.MONITOR_EVENT_SCRAMBLING_STATUS),
                Tuner.RESULT_INVALID_STATE);
        assertEquals(f.setDataSource(null), Tuner.RESULT_INVALID_STATE);
        assertEquals(f.start(), Tuner.RESULT_INVALID_STATE);
        assertEquals(f.flush(), Tuner.RESULT_INVALID_STATE);
        assertEquals(f.read(new byte[3], 0, 3), 0);
        assertEquals(f.stop(), Tuner.RESULT_INVALID_STATE);

        res = mTuner.cancelTuning();
        assertEquals(Tuner.RESULT_SUCCESS, res);

        f.freeSharedFilterToken(token1);
        f.close();
        f = null;
    }

    @Test
    public void testSharedFilterTwoProcessesCloseInSharedFilter() throws Exception {
        mConnection = new TestServiceConnection();
        mContext.bindService(new Intent(mContext, SharedFilterTestService.class), mConnection,
                Context.BIND_AUTO_CREATE);
        mSharedFilterTestServer =
                ISharedFilterTestServer.Stub.asInterface(mConnection.getService());

        String token = mSharedFilterTestServer.acquireSharedFilterToken();
        assertTrue(token != null);
        SharedFilter f =
                Tuner.openSharedFilter(mContext, token, getExecutor(), getSharedFilterCallback());
        assertTrue(f != null);

        assertEquals(f.start(), Tuner.RESULT_SUCCESS);
        assertEquals(f.flush(), Tuner.RESULT_SUCCESS);
        int size = f.read(new byte[3], 0, 3);
        assertTrue(size >= 0 && size <= 3);
        assertEquals(f.stop(), Tuner.RESULT_SUCCESS);

        mLockLatch = new CountDownLatch(1);
        f.close();
        f = null;
        mSharedFilterTestServer.closeFilter();
        Thread.sleep(2000);
        assertEquals(mLockLatch.getCount(), 1);
        mLockLatch = null;

        mContext.unbindService(mConnection);
    }

    @Test
    public void testSharedFilterTwoProcessesCloseInFilter() throws Exception {
        mConnection = new TestServiceConnection();
        mContext.bindService(new Intent(mContext, SharedFilterTestService.class), mConnection,
                Context.BIND_AUTO_CREATE);
        mSharedFilterTestServer =
                ISharedFilterTestServer.Stub.asInterface(mConnection.getService());

        String token = mSharedFilterTestServer.acquireSharedFilterToken();
        assertTrue(token != null);

        SharedFilter f =
                Tuner.openSharedFilter(mContext, token, getExecutor(), getSharedFilterCallback());
        assertTrue(f != null);

        assertEquals(f.start(), Tuner.RESULT_SUCCESS);
        assertEquals(f.flush(), Tuner.RESULT_SUCCESS);
        int size = f.read(new byte[3], 0, 3);
        assertTrue(size >= 0 && size <= 3);
        assertEquals(f.stop(), Tuner.RESULT_SUCCESS);

        mLockLatch = new CountDownLatch(1);
        mSharedFilterTestServer.closeFilter();
        assertTrue(mLockLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        mLockLatch = null;
        f.close();
        f = null;

        mContext.unbindService(mConnection);
    }

    @Test
    public void testSharedFilterTwoProcessesReleaseInFilter() throws Exception {
        mConnection = new TestServiceConnection();
        mContext.bindService(new Intent(mContext, SharedFilterTestService.class), mConnection,
                Context.BIND_AUTO_CREATE);
        mSharedFilterTestServer =
                ISharedFilterTestServer.Stub.asInterface(mConnection.getService());

        String token = mSharedFilterTestServer.acquireSharedFilterToken();
        assertTrue(token != null);

        SharedFilter f =
                Tuner.openSharedFilter(mContext, token, getExecutor(), getSharedFilterCallback());
        assertTrue(f != null);

        assertEquals(f.start(), Tuner.RESULT_SUCCESS);
        assertEquals(f.flush(), Tuner.RESULT_SUCCESS);
        int size = f.read(new byte[3], 0, 3);
        assertTrue(size >= 0 && size <= 3);
        assertEquals(f.stop(), Tuner.RESULT_SUCCESS);

        mLockLatch = new CountDownLatch(1);
        mSharedFilterTestServer.freeSharedFilterToken(token);
        assertTrue(mLockLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        mLockLatch = null;

        mSharedFilterTestServer.closeFilter();
        f.close();
        f = null;

        mContext.unbindService(mConnection);
    }

    @Test
    public void testSharedFilterTwoProcessesVerifySharedFilter() throws Exception {
        mConnection = new TestServiceConnection();
        mContext.bindService(new Intent(mContext, SharedFilterTestService.class), mConnection,
                Context.BIND_AUTO_CREATE);
        mSharedFilterTestServer =
                ISharedFilterTestServer.Stub.asInterface(mConnection.getService());

        Filter f = createTsSectionFilter(mTuner, getExecutor(), getFilterCallback());
        assertTrue(f != null);

        String token = f.acquireSharedFilterToken();
        assertTrue(token != null);

        // Tune a frontend before start the shared filter
        List<Integer> ids = mTuner.getFrontendIds();
        assertFalse(ids.isEmpty());

        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        int res = mTuner.tune(createFrontendSettings(info));
        assertEquals(Tuner.RESULT_SUCCESS, res);
        assertTrue(mSharedFilterTestServer.verifySharedFilter(token));

        res = mTuner.cancelTuning();
        assertEquals(Tuner.RESULT_SUCCESS, res);

        f.freeSharedFilterToken(token);
        f.close();
        f = null;

        mContext.unbindService(mConnection);
    }

    @Test
    public void testFilterTimeDelay() throws Exception {
        Filter f = createTsSectionFilter(mTuner, getExecutor(), getFilterCallback());

        int timeDelayInMs = 5000;
        Instant start = Instant.now();
        int status = f.delayCallbackForDurationMillis(timeDelayInMs);

        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_2_0)) {
            // Configure filter setting for receiving PAT
            Settings settings = SectionSettingsWithTableInfo
                    .builder(Filter.TYPE_TS)
                    .setTableId(0)
                    .setCrcEnabled(true)
                    .setRaw(false)
                    .setRepeat(false)
                    .build();
            FilterConfiguration config = TsFilterConfiguration
                    .builder()
                    .setTpid(0)
                    .setSettings(settings)
                    .build();

            f.configure(config);

            // Tune a frontend before starting the filter
            List<Integer> ids = mTuner.getFrontendIds();
            assertFalse(ids.isEmpty());

            FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
            int res = mTuner.tune(createFrontendSettings(info));
            assertEquals(Tuner.RESULT_SUCCESS, res);

            // start / stop prevents initial race condition after first setting the time delay.
            f.start();
            f.stop();

            mLockLatch = new CountDownLatch(1);
            f.start();
            assertTrue(mLockLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

            Instant finish = Instant.now();
            Duration timeElapsed = Duration.between(start, finish);
            assertTrue(timeElapsed.toMillis() >= timeDelayInMs);

            res = mTuner.cancelTuning();
            assertEquals(Tuner.RESULT_SUCCESS, res);
        } else {
            assertEquals(Tuner.RESULT_UNAVAILABLE, status);
        }
        f.close();
        f = null;
    }

    @Test
    public void testFilterDataSizeDelay() throws Exception {
        Filter f = createTsSectionFilter(mTuner, getExecutor(), getFilterCallback());
        int status = f.delayCallbackUntilBytesAccumulated(5000);
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_2_0)) {
            assertEquals(Tuner.RESULT_SUCCESS, status);
        } else {
            assertEquals(Tuner.RESULT_UNAVAILABLE, status);
        }
        f.close();
    }

    @Test
    public void testMaxNumberOfFrontends() throws Exception {
        List<Integer> ids = mTuner.getFrontendIds();
        assertFalse(ids.isEmpty());
        for (int i = 0; i < ids.size(); i++) {
            int type = mTuner.getFrontendInfoById(ids.get(i)).getType();
            if (TunerVersionChecker.isHigherOrEqualVersionTo(
                        TunerVersionChecker.TUNER_VERSION_2_0)) {
                int defaultMax = -1;
                int status;
                // Check default value
                defaultMax = mTuner.getMaxNumberOfFrontends(type);
                assertTrue(defaultMax > 0);
                // Set to -1
                status = mTuner.setMaxNumberOfFrontends(type, -1);
                assertEquals(Tuner.RESULT_INVALID_ARGUMENT, status);
                // Set to defaultMax + 1
                status = mTuner.setMaxNumberOfFrontends(type, defaultMax + 1);
                assertEquals(Tuner.RESULT_INVALID_ARGUMENT, status);
                // Set to 0
                status = mTuner.setMaxNumberOfFrontends(type, 0);
                assertEquals(Tuner.RESULT_SUCCESS, status);
                // Check after set
                int currentMax = -1;
                currentMax = mTuner.getMaxNumberOfFrontends(type);
                assertEquals(currentMax, 0);
                // Reset to default
                status = mTuner.setMaxNumberOfFrontends(type, defaultMax);
                assertEquals(Tuner.RESULT_SUCCESS, status);
                currentMax = mTuner.getMaxNumberOfFrontends(type);
                assertEquals(defaultMax, currentMax);
            } else {
                int defaultMax = mTuner.getMaxNumberOfFrontends(type);
                assertEquals(defaultMax, -1);
                int status = mTuner.setMaxNumberOfFrontends(type, 0);
                assertEquals(Tuner.RESULT_UNAVAILABLE, status);
            }
        }
        // validate the behavior of tune
        FrontendInfo info1 = mTuner.getFrontendInfoById(ids.get(0));
        FrontendSettings feSettings1 = createFrontendSettings(info1);
        int type1 = info1.getType();
        if (TunerVersionChecker.isHigherOrEqualVersionTo(
                        TunerVersionChecker.TUNER_VERSION_2_0)) {
            if (ids.size() >= 1) {
                int originalMax1 = mTuner.getMaxNumberOfFrontends(type1);
                assertEquals(Tuner.RESULT_SUCCESS, mTuner.tune(feSettings1));
                assertNotNull(mTuner.getFrontendInfo());

                // validate that set max cannot be set to lower value than current usage
                assertEquals(Tuner.RESULT_INVALID_ARGUMENT,
                            mTuner.setMaxNumberOfFrontends(type1, 0));

                // validate max value is reflected in the tune behavior
                mTuner.closeFrontend();
                assertEquals(Tuner.RESULT_SUCCESS,
                            mTuner.setMaxNumberOfFrontends(type1, 0));
                assertEquals(Tuner.RESULT_UNAVAILABLE,
                            mTuner.tune(feSettings1));

                assertEquals(Tuner.RESULT_SUCCESS,
                            mTuner.setMaxNumberOfFrontends(type1, originalMax1));
                assertEquals(Tuner.RESULT_SUCCESS, mTuner.tune(feSettings1));
                assertNotNull(mTuner.getFrontendInfo());
                mTuner.closeFrontend();
            }

            // validate max number on one frontend type has no impact on other
            if (ids.size() >= 2) {
                FrontendInfo info2 = mTuner.getFrontendInfoById(ids.get(1));
                int type2 = info2.getType();
                int originalMax2 = mTuner.getMaxNumberOfFrontends(type2);

                assertEquals(Tuner.RESULT_SUCCESS,
                        mTuner.setMaxNumberOfFrontends(type2, 0));
                assertEquals(Tuner.RESULT_SUCCESS,
                        mTuner.tune(feSettings1));
                assertNotNull(mTuner.getFrontendInfo());

                // set it back to the original max
                assertEquals(Tuner.RESULT_SUCCESS,
                        mTuner.setMaxNumberOfFrontends(type2, originalMax2));
                mTuner.closeFrontend();

            }
        }
    }

    public static Filter createTsSectionFilter(
            Tuner tuner, Executor e, FilterCallback cb) {
        Filter f = tuner.openFilter(Filter.TYPE_TS, Filter.SUBTYPE_SECTION, 1000, e, cb);
        Settings settings = SectionSettingsWithTableInfo
                .builder(Filter.TYPE_TS)
                .setTableId(2)
                .setVersion(1)
                .setCrcEnabled(true)
                .setRaw(false)
                .setRepeat(false)
                .build();
        FilterConfiguration config = TsFilterConfiguration
                .builder()
                .setTpid(10)
                .setSettings(settings)
                .build();
        f.configure(config);
        f.setMonitorEventMask(
                Filter.MONITOR_EVENT_SCRAMBLING_STATUS | Filter.MONITOR_EVENT_IP_CID_CHANGE);

        return f;
    }

    private boolean hasTuner() {
        return mContext.getPackageManager().hasSystemFeature("android.hardware.tv.tuner");
    }

    private Executor getExecutor() {
        return Runnable::run;
    }

    private LnbCallback getLnbCallback() {
        return new LnbCallback() {
            @Override
            public void onEvent(int lnbEventType) {}
            @Override
            public void onDiseqcMessage(byte[] diseqcMessage) {}
        };
    }

    private FilterCallback getFilterCallback() {
        return new FilterCallback() {
            @Override
            public void onFilterEvent(Filter filter, FilterEvent[] events) {
                for (FilterEvent e : events) {
                    if (e instanceof DownloadEvent) {
                        testDownloadEvent(filter, (DownloadEvent) e);
                    } else if (e instanceof IpPayloadEvent) {
                        testIpPayloadEvent(filter, (IpPayloadEvent) e);
                    } else if (e instanceof MediaEvent) {
                        testMediaEvent(filter, (MediaEvent) e);
                    } else if (e instanceof MmtpRecordEvent) {
                        testMmtpRecordEvent(filter, (MmtpRecordEvent) e);
                    } else if (e instanceof PesEvent) {
                        testPesEvent(filter, (PesEvent) e);
                    } else if (e instanceof SectionEvent) {
                        testSectionEvent(filter, (SectionEvent) e);
                    } else if (e instanceof TemiEvent) {
                        testTemiEvent(filter, (TemiEvent) e);
                    } else if (e instanceof TsRecordEvent) {
                        testTsRecordEvent(filter, (TsRecordEvent) e);
                    } else if (e instanceof ScramblingStatusEvent) {
                        testScramblingStatusEvent(filter, (ScramblingStatusEvent) e);
                    } else if (e instanceof IpCidChangeEvent) {
                        testIpCidChangeEvent(filter, (IpCidChangeEvent) e);
                    } else if (e instanceof RestartEvent) {
                        testRestartEvent(filter, (RestartEvent) e);
                    }
                }
                if (mLockLatch != null) {
                    mLockLatch.countDown();
                }
            }
            @Override
            public void onFilterStatusChanged(Filter filter, int status) {}
        };
    }

    private SharedFilterCallback getSharedFilterCallback() {
        return new SharedFilterCallback() {
            @Override
            public void onFilterEvent(SharedFilter filter, FilterEvent[] events) {}
            @Override
            public void onFilterStatusChanged(SharedFilter filter, int status) {
                if (status == SharedFilter.STATUS_INACCESSIBLE) {
                    if (mLockLatch != null) {
                        mLockLatch.countDown();
                    }
                }
            }
        };
    }

    private void testDownloadEvent(Filter filter, DownloadEvent e) {
        e.getItemId();
        e.getDownloadId();
        e.getMpuSequenceNumber();
        e.getItemFragmentIndex();
        e.getLastItemFragmentIndex();
        long length = e.getDataLength();
        if (length > 0) {
            byte[] buffer = new byte[(int) length];
            assertNotEquals(0, filter.read(buffer, 0, length));
        }
    }

    private void testIpPayloadEvent(Filter filter, IpPayloadEvent e) {
        long length = e.getDataLength();
        if (length > 0) {
            byte[] buffer = new byte[(int) length];
            assertNotEquals(0, filter.read(buffer, 0, length));
        }
    }

    private void testMediaEvent(Filter filter, MediaEvent e) {
        e.getStreamId();
        e.isPtsPresent();
        e.getPts();
        e.isDtsPresent();
        e.getDts();
        e.getDataLength();
        e.getOffset();
        e.getLinearBlock();
        e.isSecureMemory();
        e.getAvDataId();
        e.getAudioHandle();
        e.getMpuSequenceNumber();
        e.isPrivateData();
        e.getScIndexMask();
        AudioDescriptor ad = e.getExtraMetaData();
        if (ad != null) {
            ad.getAdFade();
            ad.getAdPan();
            ad.getAdVersionTextTag();
            ad.getAdGainCenter();
            ad.getAdGainFront();
            ad.getAdGainSurround();
        }
        List<AudioPresentation> aps = e.getAudioPresentations();
        for (AudioPresentation ap : aps) {
            ap.getPresentationId();
            ap.getProgramId();
            ap.getLabels();
            ap.getLocale();
            ap.getMasteringIndication();
            ap.hasAudioDescription();
            ap.hasDialogueEnhancement();
            ap.hasSpokenSubtitles();
        }
        e.release();
    }

    private void testMmtpRecordEvent(Filter filter, MmtpRecordEvent e) {
        e.getScHevcIndexMask();
        e.getDataLength();
        int mpuSequenceNumber = e.getMpuSequenceNumber();
        long pts = e.getPts();
        int firstMbInSlice = e.getFirstMacroblockInSlice();
        int tsIndexMask = e.getTsIndexMask();
        if (!TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertEquals(mpuSequenceNumber, Tuner.INVALID_MMTP_RECORD_EVENT_MPT_SEQUENCE_NUM);
            assertEquals(pts, Tuner.INVALID_TIMESTAMP);
            assertEquals(firstMbInSlice, Tuner.INVALID_FIRST_MACROBLOCK_IN_SLICE);
            assertEquals(tsIndexMask, 0);
        }
    }

    private void testPesEvent(Filter filter, PesEvent e) {
        e.getStreamId();
        e.getMpuSequenceNumber();
        long length = e.getDataLength();
        if (length > 0) {
            byte[] buffer = new byte[(int) length];
            assertNotEquals(0, filter.read(buffer, 0, length));
        }
    }

    private void testSectionEvent(Filter filter, SectionEvent e) {
        e.getTableId();
        e.getVersion();
        e.getSectionNumber();
        e.getDataLength();
        long length = e.getDataLengthLong();
        if (length > 0) {
            byte[] buffer = new byte[(int) length];
            assertNotEquals(0, filter.read(buffer, 0, length));
        }
    }

    private void testTemiEvent(Filter filter, TemiEvent e) {
        e.getPts();
        e.getDescriptorTag();
        e.getDescriptorData();
    }

    private void testTsRecordEvent(Filter filter, TsRecordEvent e) {
        e.getPacketId();
        e.getTsIndexMask();
        e.getScIndexMask();
        e.getDataLength();
        long pts = e.getPts();
        int firstMbInSlice = e.getFirstMacroblockInSlice();
        if (!TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertEquals(pts, Tuner.INVALID_TIMESTAMP);
            assertEquals(firstMbInSlice, Tuner.INVALID_FIRST_MACROBLOCK_IN_SLICE);
        }
    }

    private void testScramblingStatusEvent(Filter filter, ScramblingStatusEvent e) {
        e.getScramblingStatus();
    }

    private void testIpCidChangeEvent(Filter filter, IpCidChangeEvent e) {
        e.getIpCid();
    }

    private void testRestartEvent(Filter filter, RestartEvent e) {
        e.getStartId();
    }

    private OnRecordStatusChangedListener getRecordListener() {
        return new OnRecordStatusChangedListener() {
            @Override
            public void onRecordStatusChanged(int status) {}
        };
    }

    private OnPlaybackStatusChangedListener getPlaybackListener() {
        return new OnPlaybackStatusChangedListener() {
            @Override
            public void onPlaybackStatusChanged(int status) {}
        };
    }

    static public FrontendSettings createFrontendSettings(FrontendInfo info) {
            FrontendCapabilities caps = info.getFrontendCapabilities();
            long minFreq = info.getFrequencyRangeLong().getLower();
            long maxFreq = info.getFrequencyRangeLong().getUpper();
            FrontendCapabilities feCaps = info.getFrontendCapabilities();
            switch(info.getType()) {
                case FrontendSettings.TYPE_ANALOG: {
                    AnalogFrontendCapabilities analogCaps = (AnalogFrontendCapabilities) caps;
                    int signalType = getFirstCapable(analogCaps.getSignalTypeCapability());
                    int sif = getFirstCapable(analogCaps.getSifStandardCapability());
                    return AnalogFrontendSettings
                            .builder()
                            .setFrequencyLong(55250000) //2nd freq of VHF
                            .setSignalType(signalType)
                            .setSifStandard(sif)
                            .build();
                }
                case FrontendSettings.TYPE_ATSC3: {
                    Atsc3FrontendCapabilities atsc3Caps = (Atsc3FrontendCapabilities) caps;
                    int bandwidth = getFirstCapable(atsc3Caps.getBandwidthCapability());
                    int demod = getFirstCapable(atsc3Caps.getDemodOutputFormatCapability());
                    Atsc3FrontendSettings settings =
                            Atsc3FrontendSettings
                                    .builder()
                                    .setFrequencyLong(473000000) // 1st freq of UHF
                                    .setBandwidth(bandwidth)
                                    .setDemodOutputFormat(demod)
                                    .build();
                    settings.setEndFrequencyLong(maxFreq);
                    return settings;
                }
                case FrontendSettings.TYPE_ATSC: {
                    AtscFrontendCapabilities atscCaps = (AtscFrontendCapabilities) caps;
                    int modulation = getFirstCapable(atscCaps.getModulationCapability());
                    return AtscFrontendSettings
                            .builder()
                            .setFrequencyLong(479000000) // 2nd freq of UHF
                            .setModulation(modulation)
                            .build();
                }
                case FrontendSettings.TYPE_DVBC: {
                    DvbcFrontendCapabilities dvbcCaps = (DvbcFrontendCapabilities) caps;
                    int modulation = getFirstCapable(dvbcCaps.getModulationCapability());
                    int fec = getFirstCapable(dvbcCaps.getFecCapability());
                    int annex = getFirstCapable(dvbcCaps.getAnnexCapability());
                    DvbcFrontendSettings settings =
                            DvbcFrontendSettings
                                    .builder()
                                    .setFrequencyLong(490000000)
                                    .setModulation(modulation)
                                    .setInnerFec(fec)
                                    .setAnnex(annex)
                                    .build();
                    settings.setEndFrequencyLong(maxFreq);
                    return settings;
                }
                case FrontendSettings.TYPE_DVBS: {
                    DvbsFrontendCapabilities dvbsCaps = (DvbsFrontendCapabilities) caps;
                    int modulation = getFirstCapable(dvbsCaps.getModulationCapability());
                    int standard = getFirstCapable(dvbsCaps.getStandardCapability());
                    DvbsFrontendSettings settings =
                            DvbsFrontendSettings
                                    .builder()
                                    .setFrequencyLong(950000000) //950Mhz
                                    .setModulation(modulation)
                                    .setStandard(standard)
                                    .build();
                    settings.setEndFrequencyLong(maxFreq);
                    return settings;
                }
                case FrontendSettings.TYPE_DVBT: {
                    DvbtFrontendCapabilities dvbtCaps = (DvbtFrontendCapabilities) caps;
                    int transmission = getFirstCapable(dvbtCaps.getTransmissionModeCapability());
                    int bandwidth = getFirstCapable(dvbtCaps.getBandwidthCapability());
                    int constellation = getFirstCapable(dvbtCaps.getConstellationCapability());
                    int codeRate = getFirstCapable(dvbtCaps.getCodeRateCapability());
                    int hierarchy = getFirstCapable(dvbtCaps.getHierarchyCapability());
                    int guardInterval = getFirstCapable(dvbtCaps.getGuardIntervalCapability());
                    DvbtFrontendSettings settings = DvbtFrontendSettings
                            .builder()
                            .setFrequencyLong(498000000)
                            .setTransmissionMode(transmission)
                            .setBandwidth(bandwidth)
                            .setConstellation(constellation)
                            .setHierarchy(hierarchy)
                            .setHighPriorityCodeRate(codeRate)
                            .setLowPriorityCodeRate(codeRate)
                            .setGuardInterval(guardInterval)
                            .setStandard(DvbtFrontendSettings.STANDARD_T)
                            .setMiso(false)
                            .build();
                    settings.setEndFrequencyLong(maxFreq);
                    return settings;
                }
                case FrontendSettings.TYPE_ISDBS3: {
                    Isdbs3FrontendCapabilities isdbs3Caps = (Isdbs3FrontendCapabilities) caps;
                    int modulation = getFirstCapable(isdbs3Caps.getModulationCapability());
                    int codeRate = getFirstCapable(isdbs3Caps.getCodeRateCapability());
                    Isdbs3FrontendSettings settings = Isdbs3FrontendSettings
                            .builder()
                            .setFrequencyLong(1000000000) //1000 Mhz
                            .setModulation(modulation)
                            .setCodeRate(codeRate)
                            .build();
                    settings.setEndFrequencyLong(maxFreq);
                    return settings;
                }
                case FrontendSettings.TYPE_ISDBS: {
                    IsdbsFrontendCapabilities isdbsCaps = (IsdbsFrontendCapabilities) caps;
                    int modulation = getFirstCapable(isdbsCaps.getModulationCapability());
                    int codeRate = getFirstCapable(isdbsCaps.getCodeRateCapability());
                    IsdbsFrontendSettings settings = IsdbsFrontendSettings
                            .builder()
                            .setFrequencyLong(1050000000) //1050 Mhz
                            .setModulation(modulation)
                            .setCodeRate(codeRate)
                            .build();
                    settings.setEndFrequencyLong(maxFreq);
                    return settings;
                }
                case FrontendSettings.TYPE_ISDBT: {
                    IsdbtFrontendCapabilities isdbtCaps = (IsdbtFrontendCapabilities) caps;
                    int mode = getFirstCapable(isdbtCaps.getModeCapability());
                    int bandwidth = getFirstCapable(isdbtCaps.getBandwidthCapability());
                    int modulation = getFirstCapable(isdbtCaps.getModulationCapability());
                    int codeRate = getFirstCapable(isdbtCaps.getCodeRateCapability());
                    int guardInterval = getFirstCapable(isdbtCaps.getGuardIntervalCapability());
                    int timeInterleaveMode =
                            getFirstCapable(isdbtCaps.getTimeInterleaveModeCapability());
                    boolean isSegmentAutoSupported = isdbtCaps.isSegmentAutoSupported();
                    boolean isFullSegmentSupported = isdbtCaps.isFullSegmentSupported();

                    IsdbtFrontendSettings.Builder builder = IsdbtFrontendSettings.builder();
                    builder.setFrequencyLong(527143000); //22 ch    527.143 MHz
                    builder.setBandwidth(bandwidth);
                    builder.setMode(mode);
                    builder.setGuardInterval(guardInterval);

                    if (!TunerVersionChecker.isHigherOrEqualVersionTo(
                                TunerVersionChecker.TUNER_VERSION_2_0)) {
                        builder.setModulation(modulation);
                        builder.setCodeRate(codeRate);
                    } else {
                        IsdbtFrontendSettings.IsdbtLayerSettings.Builder layerBuilder =
                                IsdbtFrontendSettings.IsdbtLayerSettings.builder();
                        layerBuilder.setTimeInterleaveMode(timeInterleaveMode);
                        layerBuilder.setModulation(modulation);
                        layerBuilder.setCodeRate(codeRate);
                        if (isSegmentAutoSupported) {
                            layerBuilder.setNumberOfSegments(0xFF);
                        } else {
                            if (isFullSegmentSupported) {
                                layerBuilder.setNumberOfSegments(13);
                            } else {
                                layerBuilder.setNumberOfSegments(1);
                            }
                        }
                        IsdbtFrontendSettings.IsdbtLayerSettings layer = layerBuilder.build();
                        builder.setLayerSettings(
                                new IsdbtFrontendSettings.IsdbtLayerSettings[] {layer});
                        builder.setPartialReceptionFlag(
                                IsdbtFrontendSettings.PARTIAL_RECEPTION_FLAG_TRUE);
                    }
                    IsdbtFrontendSettings settings = builder.build();
                    settings.setEndFrequencyLong(maxFreq);
                    return settings;
                }
                case FrontendSettings.TYPE_DTMB: {
                    DtmbFrontendCapabilities dtmbCaps = (DtmbFrontendCapabilities) caps;
                    int modulation = getFirstCapable(dtmbCaps.getModulationCapability());
                    int transmissionMode = getFirstCapable(
                            dtmbCaps.getTransmissionModeCapability());
                    int guardInterval = getFirstCapable(dtmbCaps.getGuardIntervalCapability());
                    int timeInterleaveMode = getFirstCapable(
                            dtmbCaps.getTimeInterleaveModeCapability());
                    int codeRate = getFirstCapable(dtmbCaps.getCodeRateCapability());
                    int bandwidth = getFirstCapable(dtmbCaps.getBandwidthCapability());
                    DtmbFrontendSettings settings =
                            DtmbFrontendSettings
                                    .builder()
                                    .setFrequencyLong(506000000)
                                    .setModulation(modulation)
                                    .setTransmissionMode(transmissionMode)
                                    .setBandwidth(bandwidth)
                                    .setCodeRate(codeRate)
                                    .setGuardInterval(guardInterval)
                                    .setTimeInterleaveMode(timeInterleaveMode)
                                    .build();
                    settings.setEndFrequencyLong(maxFreq);
                    return settings;
                }
                case FrontendSettings.TYPE_IPTV: {
                    IptvFrontendCapabilities iptvCaps = (IptvFrontendCapabilities) caps;
                    int protocol = getFirstCapable(iptvCaps.getProtocolCapability());
                    IptvFrontendSettings settings =
                            new IptvFrontendSettings
                                    .Builder()
                                    .setProtocol(protocol)
                                    .build();
                    return settings;
                }
                default:
                    break;
            }
        return null;
    }

    static public int getFirstCapable(int caps) {
        if (caps == 0) return 0;
        int mask = 1;
        while ((mask & caps) == 0) {
            mask = mask << 1;
        }
        return (mask & caps);
    }

    static public long getFirstCapable(long caps) {
        if (caps == 0) return 0;
        long mask = 1;
        while ((mask & caps) == 0) {
            mask = mask << 1;
        }
        return (mask & caps);
    }

    private ScanCallback getScanCallback() {
        return new ScanCallback() {
            @Override
            public void onLocked() {
                if (mLockLatch != null) {
                    mLockLatch.countDown();
                }
            }

            @Override
            public void onUnlocked() {
                ScanCallback.super.onUnlocked();
                if (mLockLatch != null) {
                    mLockLatch.countDown();
                }
            }

            @Override
            public void onScanStopped() {}

            @Override
            public void onProgress(int percent) {}

            @Override
            public void onFrequenciesReported(int[] frequency) {}

            @Override
            public void onFrequenciesLongReported(long[] frequencies) {
                ScanCallback.super.onFrequenciesLongReported(frequencies);
            }

            @Override
            public void onSymbolRatesReported(int[] rate) {}

            @Override
            public void onPlpIdsReported(int[] plpIds) {}

            @Override
            public void onGroupIdsReported(int[] groupIds) {}

            @Override
            public void onInputStreamIdsReported(int[] inputStreamIds) {}

            @Override
            public void onDvbsStandardReported(int dvbsStandard) {}

            @Override
            public void onDvbtStandardReported(int dvbtStandard) {}

            @Override
            public void onAnalogSifStandardReported(int sif) {}

            @Override
            public void onAtsc3PlpInfosReported(Atsc3PlpInfo[] atsc3PlpInfos) {
                for (Atsc3PlpInfo info : atsc3PlpInfos) {
                    if (info != null) {
                        info.getPlpId();
                        info.getLlsFlag();
                    }
                }
            }

            @Override
            public void onHierarchyReported(int hierarchy) {}

            @Override
            public void onSignalTypeReported(int signalType) {}

            @Override
            public void onModulationReported(int modulation) {
                ScanCallback.super.onModulationReported(modulation);
            }

            @Override
            public void onPriorityReported(boolean isHighPriority) {
                ScanCallback.super.onPriorityReported(isHighPriority);
            }

            @Override
            public void onDvbcAnnexReported(int dvbcAnnext) {
                ScanCallback.super.onDvbcAnnexReported(dvbcAnnext);
            }

            @Override
            public void onDvbtCellIdsReported(int[] dvbtCellIds) {
                ScanCallback.super.onDvbtCellIdsReported(dvbtCellIds);
            }
        };
    }

    // TunerHandler utility for testing Tuner api calls in a different thread
    private static final int MSG_TUNER_HANDLER_CREATE = 1;
    private static final int MSG_TUNER_HANDLER_TUNE = 2;
    private static final int MSG_TUNER_HANDLER_CLOSE = 3;

    private ConditionVariable mTunerHandlerTaskComplete = new ConditionVariable();

    private TunerHandler createTunerHandler(Looper looper) {
        if (looper != null) {
            return new TunerHandler(looper);
        } else if ((looper = Looper.myLooper()) != null) {
            return new TunerHandler(looper);
        } else if ((looper = Looper.getMainLooper()) != null) {
            return new TunerHandler(looper);
        }
        return null;
    }

    private class TunerHandler extends Handler {
        Object mLock = new Object();
        Tuner mHandlersTuner;
        int mResult;

        private TunerHandler(Looper looper) {
            super(looper);
        }

        public Tuner getTuner() {
            synchronized (mLock) {
                return mHandlersTuner;
            }
        }

        public int getResult() {
            synchronized (mLock) {
                return mResult;
            }
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_TUNER_HANDLER_CREATE: {
                    synchronized (mLock) {
                        int useCase = msg.arg1;
                        mHandlersTuner = new Tuner(mContext, null, useCase);
                    }
                    break;
                }
                case MSG_TUNER_HANDLER_TUNE: {
                    synchronized (mLock) {
                        FrontendSettings feSettings = (FrontendSettings) msg.obj;
                        mResult = mHandlersTuner.tune(feSettings);
                    }
                    break;
                }
                case MSG_TUNER_HANDLER_CLOSE: {
                    synchronized (mLock) {
                        mHandlersTuner.close();
                    }
                    break;
                }
                default:
                    break;
            }
            mTunerHandlerTaskComplete.open();
        }
    }
}
