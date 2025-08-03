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

package com.android.server.sdksandbox.verifier;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.os.Handler;
import android.os.Looper;

import com.android.server.sdksandbox.verifier.SerialDexLoader.DexLoadResult;
import com.android.server.sdksandbox.verifier.SerialDexLoader.VerificationHandler;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SerialDexLoaderUnitTest {
    private static final String TAG = "SdkSandboxVerifierTest";
    private SerialDexLoader mSerialDexLoader;
    private FakeDexParser mFakeParser;

    @Before
    public void setup() {
        mFakeParser = new FakeDexParser();
        mSerialDexLoader = new SerialDexLoader(mFakeParser, new Handler(Looper.getMainLooper()));
    }

    @Test
    public void loadSingleDex_succeeds() throws Exception {
        TestVerificationHandler handler = new TestVerificationHandler(/*shouldPass=*/ true);
        mFakeParser.setDexList(Arrays.asList("classes.dex"));

        mSerialDexLoader.queueApkToLoad("apk_path", "com.test.packagename", handler);

        assertThat(handler.getPassedVerification()).isTrue();
        assertThat(handler.getLoadedDexCount()).isEqualTo(1);
    }

    @Test
    public void loadMultiple_succeeds() throws Exception {
        // Load and verify first dex
        mFakeParser.setDexList(Arrays.asList("classes.dex"));
        TestVerificationHandler handler1 = new TestVerificationHandler(/*shouldPass=*/ true);
        mSerialDexLoader.queueApkToLoad("apk_path", "com.test.packagename1", handler1);
        assertThat(handler1.getPassedVerification()).isTrue();
        assertThat(handler1.getLoadedDexCount()).isEqualTo(1);

        // Load and verify two dexes more
        TestVerificationHandler handler2 = new TestVerificationHandler(/*shouldPass=*/ true);
        mFakeParser.setDexList(Arrays.asList("classes1.dex", "classes2.dex"));
        mSerialDexLoader.queueApkToLoad("apk_path", "com.test.packagename2", handler2);
        assertThat(handler2.getPassedVerification()).isTrue();
        assertThat(handler2.getLoadedDexCount()).isEqualTo(2);
    }

    @Test
    public void failedVerification_nextSucceeds() throws Exception {
        // Verification of classes1.dex fails
        mFakeParser.setDexList(Arrays.asList("classes1.dex", "classes2.dex"));
        TestVerificationHandler handler1 = new TestVerificationHandler(/*shouldPass=*/ false);
        mSerialDexLoader.queueApkToLoad("apk_path", "com.test.packagename1", handler1);
        assertThat(handler1.getPassedVerification()).isFalse();
        assertThat(handler1.getLoadedDexCount()).isEqualTo(1);

        // Verification of next dex files succeed
        mFakeParser.setDexList(Arrays.asList("classes1.dex", "classes2.dex"));
        TestVerificationHandler handler2 = new TestVerificationHandler(/*shouldPass=*/ true);
        mSerialDexLoader.queueApkToLoad("apk_path", "com.test.packagename2", handler2);
        assertThat(handler2.getPassedVerification()).isTrue();
        assertThat(handler2.getLoadedDexCount()).isEqualTo(2);
    }

    @Test
    public void failedSingleVerification() throws Exception {
        TestVerificationHandler handler = new TestVerificationHandler(/*shouldPass=*/ false);
        mFakeParser.setDexList(Arrays.asList("classes.dex"));
        mSerialDexLoader.queueApkToLoad("apk_path", "com.test.packagename", handler);

        assertThat(handler.getPassedVerification()).isFalse();
        assertThat(handler.getLoadedDexCount()).isEqualTo(1);
    }

    private static class FakeDexParser implements DexParser {
        List<String> mFakeDexList;

        public void setDexList(List<String> fakeDexList) {
            mFakeDexList = fakeDexList;
        }

        @Override
        public List<String> getDexList(String apkPath) {
            return mFakeDexList;
        }

        @Override
        public void loadDexSymbols(String dexFile, DexLoadResult dexLoadResult) {}
    }

    private static class TestVerificationHandler implements VerificationHandler {
        private CountDownLatch mLatch = new CountDownLatch(1);
        private boolean mShouldPass;
        private int mLoadedDexCount = 0;
        private boolean mPassed = false;

        TestVerificationHandler(boolean shouldPass) {
            mShouldPass = shouldPass;
        }

        public int getLoadedDexCount() throws Exception {
            assertWithMessage("Latch timed out").that(mLatch.await(5, TimeUnit.SECONDS)).isTrue();
            return mLoadedDexCount;
        }

        public boolean getPassedVerification() throws Exception {
            assertWithMessage("Latch timed out").that(mLatch.await(5, TimeUnit.SECONDS)).isTrue();
            return mPassed;
        }

        @Override
        public boolean verify(DexLoadResult result) {
            mLoadedDexCount++;
            return mShouldPass;
        }

        @Override
        public void verificationFinishedForPackage(boolean passed) {
            mPassed = passed;
            mLatch.countDown();
        }
    }
}
