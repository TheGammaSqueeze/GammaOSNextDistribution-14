/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.bedstead.dpmwrapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.util.Log;

import com.android.bedstead.dpmwrapper.TestAppSystemServiceFactory.ServiceManagerWrapper;

import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.util.HashMap;

final class GenericManagerWrapper extends ServiceManagerWrapper<GenericManager> {

    private static final String TAG = GenericManagerWrapper.class.getSimpleName();

    private static final HashMap<Context, GenericManager> sMocks = new HashMap<>();

    @Override
    GenericManager getWrapper(Context context, GenericManager manager, Answer<?> answer) {
        int userId = context.getUserId();
        GenericManager mock = sMocks.get(context);
        if (mock != null) {
            Log.d(TAG, "get(): returning cached mock for user " + userId);
            return mock;
        }

        mock = Mockito.mock(GenericManager.class);
        String mockString = "GenericManagerWrapper#" + System.identityHashCode(mock);
        Log.d(TAG, "get(): creatied mock for user " + context.getUserId() + ": " + mockString);

        // TODO(b/176993670): given that GenericManager is an interface, we could dynamilly mock all
        // methods (for example, using Java's DynamicProxy), but given that DpmWrapper will
        // eventually go away, it's not worth the effort
        try {
            when(mock.toString()).thenReturn(mockString);
            when(mock.getSecureIntSettings(any())).thenAnswer(answer);
        } catch (Exception e) {
            // Should never happen, but needs to be catch as some methods declare checked exceptions
            Log.wtf("Exception setting mocks", e);
        }

        sMocks.put(context, mock);
        Log.d(TAG, "get(): returning new mock for context " + context + " and user " + userId);

        return mock;
    }
}
