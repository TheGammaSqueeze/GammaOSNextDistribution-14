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

package android.telephony.cts.util;

import static com.android.internal.util.FunctionalUtils.ThrowingConsumer;
import static com.android.internal.util.FunctionalUtils.ThrowingFunction;

import android.content.Context;
import android.os.ParcelUuid;
import android.telephony.SubscriptionManager;

import java.util.Collections;

/**
 * Utility to execute a code block with an ephemeral subscription group.
 *
 * <p>These methods will remove the subscription(s) from the ephemeral subscription group once the
 * specified task is completed.
 *
 * <p>Example:
 *
 * <pre>
 * SubscriptionGroupUtils.withEphemeralSubscriptionGroup(
 *         ctx, subId, (subGrp) -> telephonyManager.doFoo(subGrp, bar));
 * </pre>
 */
public class SubscriptionGroupUtils {
    public static void withEphemeralSubscriptionGroup(
            Context c, int subId, ThrowingConsumer<ParcelUuid> action) throws Exception {
        final SubscriptionManager subMgr = c.getSystemService(SubscriptionManager.class);
        ParcelUuid subGrp = null;
        try {
            subGrp = subMgr.createSubscriptionGroup(Collections.singletonList(subId));
            action.acceptOrThrow(subGrp);
        } finally {
            if (subGrp != null) {
                subMgr.removeSubscriptionsFromGroup(Collections.singletonList(subId), subGrp);
            }
        }
    }

    public static <R> R withEphemeralSubscriptionGroup(
            Context c, int subId, ThrowingFunction<ParcelUuid, R> action) throws Exception {
        final SubscriptionManager subMgr = c.getSystemService(SubscriptionManager.class);
        ParcelUuid subGrp = null;
        try {
            subGrp = subMgr.createSubscriptionGroup(Collections.singletonList(subId));
            return action.applyOrThrow(subGrp);
        } finally {
            if (subGrp != null) {
                subMgr.removeSubscriptionsFromGroup(Collections.singletonList(subId), subGrp);
            }
        }
    }
}
