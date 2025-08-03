/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tradefed.invoker.shard;

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.invoker.shard.token.ITokenProvider;
import com.android.tradefed.invoker.shard.token.ITokenRequest;
import com.android.tradefed.invoker.shard.token.TokenProperty;
import com.android.tradefed.invoker.shard.token.TokenProviderHelper;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.IRemoteTest;

import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/** Implementation of a pool of local tests */
public class LocalPool implements ITestsPool {

    private final Collection<IRemoteTest> mGenericPool;
    private final Collection<ITokenRequest> mTokenPool;
    private final Set<ITokenRequest> mRejectedToken;

    public LocalPool(Collection<IRemoteTest> genericPool, Collection<ITokenRequest> tokenPool) {
        mGenericPool = genericPool;
        mTokenPool = tokenPool;
        mRejectedToken = Sets.newConcurrentHashSet();
    }

    @Override
    public IRemoteTest poll(TestInformation info, boolean reportNotExecuted) {
        if (mTokenPool != null) {
            synchronized (mTokenPool) {
                if (!mTokenPool.isEmpty()) {
                    Iterator<ITokenRequest> itr = mTokenPool.iterator();
                    while (itr.hasNext()) {
                        ITokenRequest test = itr.next();
                        if (reportNotExecuted) {
                            // Return to report not executed tests, regardless of if they can
                            // actually execute or not.
                            mRejectedToken.remove(test);
                            mTokenPool.remove(test);
                            return test;
                        }
                        if (mRejectedToken.contains(test)) {
                            // If the poller already rejected the tests once, do not re-evaluate.
                            continue;
                        }
                        Set<TokenProperty> tokens = test.getRequiredTokens(info);
                        if (tokens == null || tokens.isEmpty() || isSupported(info, tokens)) {
                            // No Token can run anywhere, or supported can run
                            mTokenPool.remove(test);
                            mRejectedToken.remove(test);
                            return test;
                        }

                        // Track as rejected
                        mRejectedToken.add(test);
                    }
                }
            }
        }
        synchronized (mGenericPool) {
            if (mGenericPool.isEmpty()) {
                return null;
            }
            IRemoteTest test = mGenericPool.iterator().next();
            mGenericPool.remove(test);
            return test;
        }
    }

    @Override
    public ITokenRequest pollRejectedTokenModule() {
        if (mTokenPool == null) {
            return null;
        }
        synchronized (mTokenPool) {
            if (mRejectedToken.isEmpty()) {
                return null;
            }
            ITokenRequest test = mRejectedToken.iterator().next();
            mRejectedToken.remove(test);
            mTokenPool.remove(test);
            return test;
        }
    }

    @VisibleForTesting
    int peekTokenSize() {
        if (mTokenPool != null) {
            return mTokenPool.size();
        }
        return 0;
    }

    private boolean isSupported(TestInformation info, Set<TokenProperty> requiredTokens) {
        for (TokenProperty prop : requiredTokens) {
            ITokenProvider provider = TokenProviderHelper.getTokenProvider(prop);
            if (provider == null) {
                CLog.e("No provider for token %s", prop);
                return false;
            }
            if (!provider.hasToken(info.getDevice(), prop)) {
                return false;
            }
        }
        return true;
    }
}
