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

package android.telephony.ims.cts;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.telephony.ims.ImsService;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.feature.RcsFeature;
import android.telephony.ims.stub.ImsConfigImplBase;
import android.telephony.ims.stub.ImsFeatureConfiguration;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.telephony.ims.stub.SipTransportImplBase;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * A Test ImsService that will verify ImsService functionality.
 */
public class TestImsService extends Service {

    private static final String TAG = "CtsImsTestImsService";
    private static MessageExecutor sMessageExecutor = null;

    private TestImsRegistration mImsRegistrationImplBase;
    private TestRcsFeature mTestRcsFeature;
    private TestMmTelFeature mTestMmTelFeature;
    private TestImsConfig mTestImsConfig;
    private TestSipTransport mTestSipTransport;
    private ImsService mTestImsService;
    private ImsService mTestImsServiceCompat;
    private Executor mExecutor = Runnable::run;
    private boolean mIsEnabled = false;
    private boolean mSetNullRcsBinding = false;
    private boolean mIsSipTransportImplemented = false;
    private boolean mIsTestTypeExecutor = false;
    private boolean mIsImsServiceCompat = false;
    private long mCapabilities = 0;
    private ImsFeatureConfiguration mFeatureConfig;
    protected boolean mIsTelephonyBound = false;
    private HashSet<Integer> mSubIDs = new HashSet<Integer>();
    protected final Object mLock = new Object();

    public static final int LATCH_FEATURES_READY = 0;
    public static final int LATCH_ENABLE_IMS = 1;
    public static final int LATCH_DISABLE_IMS = 2;
    public static final int LATCH_CREATE_MMTEL = 3;
    public static final int LATCH_CREATE_RCS = 4;
    public static final int LATCH_REMOVE_MMTEL = 5;
    public static final int LATCH_REMOVE_RCS = 6;
    public static final int LATCH_MMTEL_READY = 7;
    public static final int LATCH_RCS_READY = 8;
    public static final int LATCH_MMTEL_CAP_SET = 9;
    public static final int LATCH_RCS_CAP_SET = 10;
    public static final int LATCH_UCE_LISTENER_SET = 11;
    public static final int LATCH_UCE_REQUEST_PUBLISH = 12;
    public static final int LATCH_ON_UNBIND = 13;
    public static final int LATCH_LAST_MESSAGE_EXECUTE = 14;
    private static final int LATCH_MAX = 15;
    private static final int WAIT_FOR_EXIT_TEST = 2000;
    protected static final CountDownLatch[] sLatches = new CountDownLatch[LATCH_MAX];
    static {
        for (int i = 0; i < LATCH_MAX; i++) {
            sLatches[i] = new CountDownLatch(1);
        }
    }

    interface RemovedListener {
        void onRemoved();
    }
    interface ReadyListener {
        void onReady();
    }
    interface CapabilitiesSetListener {
        void onSet();
    }
    interface RcsCapabilityExchangeEventListener {
        void onSet();
    }
    interface DeviceCapPublishListener {
        void onPublish();
    }

    // This is defined here instead TestImsService extending ImsService directly because the GTS
    // tests were failing to run on pre-P devices. Not sure why, but TestImsService is loaded
    // even if it isn't used.
    private class ImsServiceUT extends ImsService {

        ImsServiceUT(Context context) {
            // As explained above, ImsServiceUT is created in order to get around classloader
            // restrictions. Attach the base context from the wrapper ImsService.
            if (getBaseContext() == null) {
                attachBaseContext(context);
            }

            if (mIsTestTypeExecutor) {
                mImsRegistrationImplBase = new TestImsRegistration(mExecutor);
                mTestSipTransport = new TestSipTransport(mExecutor);
                mTestImsConfig = new TestImsConfig(mExecutor);
            } else {
                mImsRegistrationImplBase = new TestImsRegistration();
                mTestImsConfig = new TestImsConfig();
                mTestSipTransport = new TestSipTransport();
            }
        }

        @Override
        public ImsFeatureConfiguration querySupportedImsFeatures() {
            return getFeatureConfig();
        }

        @Override
        public long getImsServiceCapabilities() {
            return mCapabilities;
        }

        @Override
        public void readyForFeatureCreation() {
            synchronized (mLock) {
                countDownLatch(LATCH_FEATURES_READY);
            }
        }

        @Override
        public void enableImsForSubscription(int slotId, int subId) {
            synchronized (mLock) {
                countDownLatch(LATCH_ENABLE_IMS);
                mSubIDs.add(subId);
                setIsEnabled(true);
            }
        }

        @Override
        public void disableImsForSubscription(int slotId, int subId) {
            synchronized (mLock) {
                countDownLatch(LATCH_DISABLE_IMS);
                mSubIDs.add(subId);
                setIsEnabled(false);
            }
        }

        @Override
        public RcsFeature createRcsFeatureForSubscription(int slotId, int subId) {
            TestImsService.ReadyListener readyListener = () -> {
                synchronized (mLock) {
                    countDownLatch(LATCH_RCS_READY);
                }
            };

            TestImsService.RemovedListener removedListener = () -> {
                synchronized (mLock) {
                    countDownLatch(LATCH_REMOVE_RCS);
                    mTestRcsFeature = null;
                }
            };

            TestImsService.CapabilitiesSetListener setListener = () -> {
                synchronized (mLock) {
                    countDownLatch(LATCH_RCS_CAP_SET);
                }
            };

            TestImsService.RcsCapabilityExchangeEventListener capExchangeEventListener = () -> {
                synchronized (mLock) {
                    countDownLatch(LATCH_UCE_LISTENER_SET);
                }
            };

            synchronized (mLock) {
                countDownLatch(LATCH_CREATE_RCS);
                mSubIDs.add(subId);

                if (mIsTestTypeExecutor) {
                    mTestRcsFeature = new TestRcsFeature(readyListener, removedListener,
                            setListener, capExchangeEventListener, mExecutor);
                } else {
                    mTestRcsFeature = new TestRcsFeature(readyListener, removedListener,
                            setListener, capExchangeEventListener);
                }

                // Setup UCE request listener
                mTestRcsFeature.setDeviceCapPublishListener(() -> {
                    synchronized (mLock) {
                        countDownLatch(LATCH_UCE_REQUEST_PUBLISH);
                    }
                });

                if (mSetNullRcsBinding) {
                    return null;
                }
                return mTestRcsFeature;
            }
        }

        @Override
        public ImsConfigImplBase getConfigForSubscription(int slotId, int subId) {
            mSubIDs.add(subId);
            return mTestImsConfig;
        }

        @Override
        public MmTelFeature createMmTelFeatureForSubscription(int slotId, int subId) {
            TestImsService.ReadyListener readyListener = () -> {
                synchronized (mLock) {
                    countDownLatch(LATCH_MMTEL_READY);
                }
            };

            TestImsService.RemovedListener removedListener = () -> {
                synchronized (mLock) {
                    countDownLatch(LATCH_REMOVE_MMTEL);
                    mTestMmTelFeature = null;
                }
            };

            TestImsService.CapabilitiesSetListener capSetListener = () -> {
                synchronized (mLock) {
                    countDownLatch(LATCH_MMTEL_CAP_SET);
                }
            };

            synchronized (mLock) {
                countDownLatch(LATCH_CREATE_MMTEL);
                mSubIDs.add(subId);
                if (mIsTestTypeExecutor) {
                    mTestMmTelFeature = new TestMmTelFeature(readyListener, removedListener,
                            capSetListener, mExecutor);
                } else {
                    mTestMmTelFeature = new TestMmTelFeature(readyListener, removedListener,
                            capSetListener);
                }

                return mTestMmTelFeature;
            }
        }

        @Override
        public ImsRegistrationImplBase getRegistrationForSubscription(int slotId, int subId) {
            mSubIDs.add(subId);
            return mImsRegistrationImplBase;
        }

        @Nullable
        @Override
        public SipTransportImplBase getSipTransport(int slotId) {
            if (mIsSipTransportImplemented) {
                return mTestSipTransport;
            } else {
                return null;
            }
        }

        @Override
        public @NonNull Executor getExecutor() {
            if (mIsTestTypeExecutor) {
                return mExecutor;
            } else {
                mExecutor = Runnable::run;
                return mExecutor;
            }
        }
    }

    private class ImsServiceUT_compat extends ImsService {

        ImsServiceUT_compat(Context context) {
            // As explained above, ImsServiceUT is created in order to get around classloader
            // restrictions. Attach the base context from the wrapper ImsService.
            if (getBaseContext() == null) {
                attachBaseContext(context);
            }

            if (mIsTestTypeExecutor) {
                mImsRegistrationImplBase = new TestImsRegistration(mExecutor);
                mTestSipTransport = new TestSipTransport(mExecutor);
                mTestImsConfig = new TestImsConfig(mExecutor);
            } else {
                mImsRegistrationImplBase = new TestImsRegistration();
                mTestImsConfig = new TestImsConfig();
                mTestSipTransport = new TestSipTransport();
            }
        }

        @Override
        public ImsFeatureConfiguration querySupportedImsFeatures() {
            return getFeatureConfig();
        }

        @Override
        public long getImsServiceCapabilities() {
            return mCapabilities;
        }

        @Override
        public void readyForFeatureCreation() {
            synchronized (mLock) {
                countDownLatch(LATCH_FEATURES_READY);
            }
        }

        @Override
        public void enableIms(int slotId) {
            synchronized (mLock) {
                countDownLatch(LATCH_ENABLE_IMS);
                setIsEnabled(true);
            }
        }

        @Override
        public void disableIms(int slotId) {
            synchronized (mLock) {
                countDownLatch(LATCH_DISABLE_IMS);
                setIsEnabled(false);
            }
        }

        @Override
        public RcsFeature createRcsFeature(int slotId) {

            TestImsService.ReadyListener readyListener = () -> {
                synchronized (mLock) {
                    countDownLatch(LATCH_RCS_READY);
                }
            };

            TestImsService.RemovedListener removedListener = () -> {
                synchronized (mLock) {
                    countDownLatch(LATCH_REMOVE_RCS);
                    mTestRcsFeature = null;
                }
            };

            TestImsService.CapabilitiesSetListener setListener = () -> {
                synchronized (mLock) {
                    countDownLatch(LATCH_RCS_CAP_SET);
                }
            };

            TestImsService.RcsCapabilityExchangeEventListener capExchangeEventListener = () -> {
                synchronized (mLock) {
                    countDownLatch(LATCH_UCE_LISTENER_SET);
                }
            };

            synchronized (mLock) {
                countDownLatch(LATCH_CREATE_RCS);

                if (mIsTestTypeExecutor) {
                    mTestRcsFeature = new TestRcsFeature(readyListener, removedListener,
                            setListener, capExchangeEventListener, mExecutor);
                } else {
                    mTestRcsFeature = new TestRcsFeature(readyListener, removedListener,
                            setListener, capExchangeEventListener);
                }

                // Setup UCE request listener
                mTestRcsFeature.setDeviceCapPublishListener(() -> {
                    synchronized (mLock) {
                        countDownLatch(LATCH_UCE_REQUEST_PUBLISH);
                    }
                });

                if (mSetNullRcsBinding) {
                    return null;
                }
                return mTestRcsFeature;
            }
        }

        @Override
        public ImsConfigImplBase getConfig(int slotId) {
            return mTestImsConfig;
        }

        @Override
        public MmTelFeature createMmTelFeature(int slotId) {
            TestImsService.ReadyListener readyListener = () -> {
                synchronized (mLock) {
                    countDownLatch(LATCH_MMTEL_READY);
                }
            };

            TestImsService.RemovedListener removedListener = () -> {
                synchronized (mLock) {
                    countDownLatch(LATCH_REMOVE_MMTEL);
                    mTestMmTelFeature = null;
                }
            };

            TestImsService.CapabilitiesSetListener capSetListener = () -> {
                synchronized (mLock) {
                    countDownLatch(LATCH_MMTEL_CAP_SET);
                }
            };

            synchronized (mLock) {
                countDownLatch(LATCH_CREATE_MMTEL);
                if (mIsTestTypeExecutor) {
                    mTestMmTelFeature = new TestMmTelFeature(readyListener, removedListener,
                            capSetListener, mExecutor);
                } else {
                    mTestMmTelFeature = new TestMmTelFeature(readyListener, removedListener,
                            capSetListener);
                }

                return mTestMmTelFeature;
            }
        }

        @Override
        public ImsRegistrationImplBase getRegistration(int slotId) {
            return mImsRegistrationImplBase;
        }

        @Nullable
        @Override
        public SipTransportImplBase getSipTransport(int slotId) {
            if (mIsSipTransportImplemented) {
                return mTestSipTransport;
            } else {
                return null;
            }
        }

        @Override
        public @NonNull Executor getExecutor() {
            if (mIsTestTypeExecutor) {
                return mExecutor;
            } else {
                mExecutor = Runnable::run;
                return mExecutor;
            }
        }
    }

    private static Looper createLooper(String name) {
        HandlerThread thread = new HandlerThread(name);
        thread.start();

        Looper looper = thread.getLooper();

        if (looper == null) {
            return Looper.getMainLooper();
        }
        return looper;
    }

    /**
     * Executes the tasks in the other thread rather than the calling thread.
     */
    public class MessageExecutor extends Handler implements Executor {
        public MessageExecutor(String name) {
            super(createLooper(name));
        }

        @Override
        public void execute(Runnable r) {
            Message m = Message.obtain(this, 0, r);
            m.sendToTarget();
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.obj instanceof Runnable) {
                executeInternal((Runnable) msg.obj);
            } else {
                Log.d(TAG, "[MessageExecutor] handleMessage :: "
                        + "Not runnable object; ignore the msg=" + msg);
            }
        }

        private void executeInternal(Runnable r) {
            try {
                r.run();
            } catch (Throwable t) {
                Log.d(TAG, "[MessageExecutor] executeInternal :: run task=" + r);
                t.printStackTrace();
            }
        }
    }

    private final LocalBinder mBinder = new LocalBinder();
    // For local access of this Service.
    class LocalBinder extends Binder {
        TestImsService getService() {
            return TestImsService.this;
        }
    }

    /**
     * Returns IMS service for CTS test purpose.
     * @return test Ims service.
     */
    public ImsService getImsService() {
        synchronized (mLock) {
            if (mTestImsService != null) {
                return mTestImsService;
            }
            mTestImsService = new ImsServiceUT(this);
            return mTestImsService;
        }
    }

    protected ImsService getImsServiceCompat() {
        synchronized (mLock) {
            if (mTestImsServiceCompat != null) {
                return mTestImsServiceCompat;
            }
            mTestImsServiceCompat = new ImsServiceUT_compat(this);
            return mTestImsServiceCompat;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        synchronized (mLock) {
            if ("android.telephony.ims.ImsService".equals(intent.getAction())) {
                mIsTelephonyBound = true;
                if (mIsImsServiceCompat) {
                    if (ImsUtils.VDBG) {
                        Log.d(TAG, "onBind-Remote-Compat");
                    }
                    return getImsServiceCompat().onBind(intent);
                } else {
                    if (ImsUtils.VDBG) {
                        Log.d(TAG, "onBind-Remote");
                    }
                    return getImsService().onBind(intent);
                }
            }
            if (ImsUtils.VDBG) {
                Log.i(TAG, "onBind-Local");
            }
            return mBinder;
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        synchronized (mLock) {
            if ("android.telephony.ims.ImsService".equals(intent.getAction())) {
                if (ImsUtils.VDBG)  Log.i(TAG, "onUnbind-Remote");
                mIsTelephonyBound = false;
                countDownLatch(LATCH_ON_UNBIND);
            } else {
                if (ImsUtils.VDBG)  Log.i(TAG, "onUnbind-Local");
            }
            // return false so that onBind is called next time.
            return false;
        }
    }

    public void resetState() {
        synchronized (mLock) {
            mTestMmTelFeature = null;
            mTestRcsFeature = null;
            mIsEnabled = false;
            mSetNullRcsBinding = false;
            mIsSipTransportImplemented = false;
            mIsTestTypeExecutor = false;
            mIsImsServiceCompat = false;
            mCapabilities = 0;
            for (int i = 0; i < LATCH_MAX; i++) {
                sLatches[i] = new CountDownLatch(1);
            }

            if (sMessageExecutor != null) {
                sMessageExecutor.getLooper().quitSafely();
                sMessageExecutor = null;
            }
            mSubIDs.clear();
        }
    }

    public boolean isTelephonyBound() {
        return mIsTelephonyBound;
    }

    public void setExecutorTestType(boolean type) {
        mIsTestTypeExecutor = type;
        if (mIsTestTypeExecutor) {
            if (sMessageExecutor == null) {
                sMessageExecutor = new MessageExecutor("TestImsService");
            }
            mExecutor = sMessageExecutor;
        }
    }

    public void waitForExecutorFinish() {
        if (mIsTestTypeExecutor && sMessageExecutor != null) {
            sMessageExecutor.postDelayed(() -> countDownLatch(LATCH_LAST_MESSAGE_EXECUTE), null ,
                    WAIT_FOR_EXIT_TEST);
            waitForLatchCountdown(LATCH_LAST_MESSAGE_EXECUTE);
        }
    }

    public void setImsServiceCompat() {
        synchronized (mLock) {
            mIsImsServiceCompat = true;
        }
    }

    // Sets the feature configuration. Make sure to call this before initiating Bind to this
    // ImsService.
    public void setFeatureConfig(ImsFeatureConfiguration f) {
        synchronized (mLock) {
            mFeatureConfig = f;
        }
    }

    public ImsFeatureConfiguration getFeatureConfig() {
        synchronized (mLock) {
            return mFeatureConfig;
        }
    }

    public boolean isEnabled() {
        synchronized (mLock) {
            return mIsEnabled;
        }
    }

    public void setNullRcsBinding() {
        synchronized (mLock) {
            mSetNullRcsBinding = true;
        }
    }

    public void setIsEnabled(boolean isEnabled) {
        synchronized (mLock) {
            mIsEnabled = isEnabled;
        }
    }

    public void addCapabilities(long capabilities) {
        synchronized (mLock) {
            mCapabilities |= capabilities;
        }
    }

    public void setSipTransportImplemented() {
        synchronized (mLock) {
            mIsSipTransportImplemented = true;
        }
    }

    public boolean waitForLatchCountdown(int latchIndex) {
        return waitForLatchCountdown(latchIndex, ImsUtils.TEST_TIMEOUT_MS);
    }

    public boolean waitForLatchCountdown(int latchIndex, long waitMs) {
        boolean complete = false;
        try {
            CountDownLatch latch;
            synchronized (mLock) {
                latch = sLatches[latchIndex];
            }
            long startTime = System.currentTimeMillis();
            complete = latch.await(waitMs, TimeUnit.MILLISECONDS);
            if (ImsUtils.VDBG) {
                Log.i(TAG, "Latch " + latchIndex + " took "
                        + (System.currentTimeMillis() - startTime) + " ms to count down.");
            }
        } catch (InterruptedException e) {
            // complete == false
        }
        synchronized (mLock) {
            sLatches[latchIndex] = new CountDownLatch(1);
        }
        return complete;
    }

    public void countDownLatch(int latchIndex) {
        synchronized (mLock) {
            sLatches[latchIndex].countDown();
        }
    }

    public TestMmTelFeature getMmTelFeature() {
        synchronized (mLock) {
            return mTestMmTelFeature;
        }
    }

    public TestRcsFeature getRcsFeature() {
        synchronized (mLock) {
            return mTestRcsFeature;
        }
    }

    public TestSipTransport getSipTransport() {
        synchronized (mLock) {
            return mTestSipTransport;
        }
    }

    public TestImsRegistration getImsRegistration() {
        synchronized (mLock) {
            return mImsRegistrationImplBase;
        }
    }

    public ImsConfigImplBase getConfig() {
        return mTestImsConfig;
    }

    public HashSet<Integer> getSubIDs() {
        return mSubIDs;
    }
}
