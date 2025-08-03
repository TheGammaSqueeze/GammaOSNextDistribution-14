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

package android.provider.cts.simphonebook;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.UiccCardInfo;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.RequiredFeatureRule;
import com.android.compatibility.common.util.SystemUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Provides the SubscriptionInfo and slot count for removable SIM cards. */
class RemovableSims {
    private final Context mContext;
    private final TelephonyManager mTelephonyManager;

    private List<SubscriptionInfo> mRemovableSubscriptionInfos;
    private int mRemovableSimSlotCount;

    public RemovableSims(Context context) {
        mContext = context;
        mTelephonyManager = Objects.requireNonNull(
                context.getSystemService(TelephonyManager.class));
    }

    private synchronized void initialize() {
        SubscriptionManager subscriptionManager = Objects.requireNonNull(
                mContext.getSystemService(SubscriptionManager.class));
        mRemovableSubscriptionInfos = new ArrayList<>();
        mRemovableSimSlotCount = 0;

        if (RequiredFeatureRule.hasFeature(PackageManager.FEATURE_TELEPHONY_EUICC)) {
            // Wait for the eSIM state to be loaded before continuing. Otherwise, the card info
            // we load later may indicate that they are not eSIM when they actually are.
            PollingCheck.waitFor(30_000, () ->
                    mTelephonyManager.getCardIdForDefaultEuicc() !=
                            TelephonyManager.UNINITIALIZED_CARD_ID
            );
        }

        List<UiccCardInfo> uiccCards = SystemUtil.runWithShellPermissionIdentity(
                mTelephonyManager::getUiccCardsInfo,
                Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        List<SubscriptionInfo> allSubscriptions = SystemUtil.runWithShellPermissionIdentity(() ->
                        subscriptionManager.getActiveSubscriptionInfoList(),
                Manifest.permission.READ_PHONE_STATE);
        if (allSubscriptions == null) {
            allSubscriptions = Collections.emptyList();
        }
        Map<Integer, List<SubscriptionInfo>> subscriptionsByCardId = allSubscriptions.stream()
                .collect(Collectors.groupingBy(SubscriptionInfo::getCardId));
        for (UiccCardInfo cardInfo : uiccCards) {
            // On GSI builds the eUICC won't be loaded but its card info will still be returned
            // and so it will have UNINITIALIZED_CARD_ID permanently.
            if (!cardInfo.isRemovable() || cardInfo.isEuicc() ||
                    cardInfo.getCardId() == TelephonyManager.UNINITIALIZED_CARD_ID) {
                continue;
            }
            mRemovableSimSlotCount++;

            List<SubscriptionInfo> listWithSubscription = subscriptionsByCardId
                    .getOrDefault(cardInfo.getCardId(), Collections.emptyList());
            // There should only be 1 in the list but using addAll simplifies things because we
            // don't have to check for the empty case.
            mRemovableSubscriptionInfos.addAll(listWithSubscription);
        }
    }

    public List<SubscriptionInfo> getSubscriptionInfoForRemovableSims() {
        if (mRemovableSubscriptionInfos == null ||
                mRemovableSubscriptionInfos.size() < mRemovableSimSlotCount) {
            initialize();
        }
        return mRemovableSubscriptionInfos;
    }

    public int getRemovableSimSlotCount() {
        if (mRemovableSubscriptionInfos == null) {
            initialize();
        }
        return mRemovableSimSlotCount;
    }

    public int getDefaultSubscriptionId() {
        List<SubscriptionInfo> removableSubscriptionInfos = getSubscriptionInfoForRemovableSims();
        int subscriptionId = SubscriptionManager.getDefaultSubscriptionId();
        if (removableSubscriptionInfos.stream().anyMatch(
                info -> info.getSubscriptionId() == subscriptionId)) {
            return subscriptionId;
        } else if (!removableSubscriptionInfos.isEmpty()) {
            return removableSubscriptionInfos.get(0).getSubscriptionId();
        }
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }
}
