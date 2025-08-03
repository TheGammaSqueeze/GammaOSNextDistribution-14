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

package com.android.federatedcompute.services.training.jni;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.examplestore.ExampleIterator;
import com.android.federatedcompute.services.training.util.ListenableSupplier;

import com.google.intelligence.fcp.client.SelectorContext;

import java.io.Closeable;

/** Default implementation for {@link SimpleTaskEnvironment} */
public class SimpleTaskEnvironmentImpl implements SimpleTaskEnvironment, Closeable {
    private static final String TAG = SimpleTaskEnvironmentImpl.class.getSimpleName();
    private final ListenableSupplier<Boolean> mInterruptionFlag;
    private final JavaExampleStore mJavaExampleStore;
    private final Object mLock = new Object();

    public SimpleTaskEnvironmentImpl(
            ListenableSupplier<Boolean> interruptionFlag, ExampleIterator exampleIterator) {
        this.mInterruptionFlag = interruptionFlag;
        this.mJavaExampleStore = new JavaExampleStore(exampleIterator);
    }

    /**
     * Isolated training process should not have file system access, so we throw exception here on
     * purpose. It should be called because we directly return at JNI layer.
     */
    @Override
    public String getBaseDir() {
        throw new UnsupportedOperationException("getBaseDir is not supported yet.");
    }

    /**
     * Isolated training process should not have file system access, so we throw exception here on
     * purpose. It should be called because we directly return at JNI layer.
     */
    @Override
    public String getCacheDir() {
        throw new UnsupportedOperationException("getCacheDir is not supported yet.");
    }

    /**
     * We don't check real training conditions here because the isolated training process can't
     * access system service e.g. PowerManager, battery intent. We only check all the conditions
     * before the training starts.
     */
    @Override
    public boolean trainingConditionsSatisfied() {
        // Check for external termination.
        if (Thread.interrupted()) {
            return false;
        }

        if (mInterruptionFlag.get()) {
            LogUtil.i(
                    TAG, "Interrupting training due to custom interruption flag set to" + " true");
            return false;
        }
        return true;
    }

    @Override
    public JavaExampleIterator createExampleIterator(byte[] exampleSelector) {
        return createExampleIteratorWithContext(
                exampleSelector, SelectorContext.getDefaultInstance().toByteArray());
    }

    @Override
    public JavaExampleIterator createExampleIteratorWithContext(
            byte[] exampleSelector, byte[] selectorContext) {
        synchronized (mLock) {
            return mJavaExampleStore.createExampleIteratorWithContext(
                    exampleSelector, selectorContext);
        }
    }

    @Override
    public void close() {
        mJavaExampleStore.close();
    }
}
