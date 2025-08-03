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

package android.app.cts.broadcasts;

import static com.android.app.cts.broadcasts.Common.ORDERED_BROADCAST_INITIAL_DATA;
import static com.android.app.cts.broadcasts.Common.ORDERED_BROADCAST_RESULT_DATA;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;

import com.android.app.cts.broadcasts.Common;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BroadcastsTestRunner.class)
public class BroadcastsTest extends BaseBroadcastTest {

    @Test
    public void testExplicitManifestBroadcast() throws Exception {
        final Intent explicitIntent = new Intent(Common.ORDERED_BROADCAST_ACTION)
                .setPackage(HELPER_PKG1);
        // Helper app should receive the broadcast even if it isn't already running.
        {
            final ResultReceiver resultReceiver = new ResultReceiver();
            mContext.sendOrderedBroadcast(explicitIntent, null /* receiverPermission */,
                    resultReceiver, null /* scheduler */, 0 /* initialCode */,
                    ORDERED_BROADCAST_INITIAL_DATA, null /* initialExtras */);
            waitForBroadcastBarrier();
            assertThat(resultReceiver.getResult()).isEqualTo(ORDERED_BROADCAST_RESULT_DATA);
        }

        // Helper app should receive the broadcast if it is already running.
        {
            final TestServiceConnection connection = bindToHelperService(HELPER_PKG1);
            try {
                // Wait for service to be connected.
                connection.getCommandReceiver();

                final ResultReceiver resultReceiver = new ResultReceiver();
                mContext.sendOrderedBroadcast(explicitIntent, null /* receiverPermission */,
                        resultReceiver, null /* scheduler */, 0 /* initialCode */,
                        ORDERED_BROADCAST_INITIAL_DATA, null /* initialExtras */);
                waitForBroadcastBarrier();
                assertThat(resultReceiver.getResult()).isEqualTo(ORDERED_BROADCAST_RESULT_DATA);
            } finally {
                connection.unbind();
            }
        }
    }

    @Test
    public void testImplicitManifestBroadcast() throws Exception {
        final Intent implicitIntent = new Intent(Common.ORDERED_BROADCAST_ACTION);
        // Helper app should not receive the broadcast if it isn't already running.
        {
            final ResultReceiver resultReceiver = new ResultReceiver();
            mContext.sendOrderedBroadcast(implicitIntent, null /* receiverPermission */,
                    resultReceiver, null /* scheduler */, 0 /* initialCode */,
                    ORDERED_BROADCAST_INITIAL_DATA, null /* initialExtras */);
            waitForBroadcastBarrier();
            assertThat(resultReceiver.getResult()).isEqualTo(ORDERED_BROADCAST_INITIAL_DATA);
        }

        // Helper app should not receive the broadcast even if it is already running.
        {
            final TestServiceConnection connection = bindToHelperService(HELPER_PKG1);
            try {
                // Wait for service to be connected.
                connection.getCommandReceiver();

                final ResultReceiver resultReceiver = new ResultReceiver();
                mContext.sendOrderedBroadcast(implicitIntent, null /* receiverPermission */,
                        resultReceiver, null /* scheduler */, 0 /* initialCode */,
                        ORDERED_BROADCAST_INITIAL_DATA, null /* initialExtras */);
                waitForBroadcastBarrier();
                assertThat(resultReceiver.getResult()).isEqualTo(ORDERED_BROADCAST_INITIAL_DATA);
            } finally {
                connection.unbind();
            }
        }
    }

}
