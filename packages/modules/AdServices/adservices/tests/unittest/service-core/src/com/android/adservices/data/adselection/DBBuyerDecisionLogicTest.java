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

package com.android.adservices.data.adselection;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;

import android.adservices.common.CommonFixture;
import android.net.Uri;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;


public class DBBuyerDecisionLogicTest {

    private static final String BUYER_DECISION_LOGIC_JS =
            "function test() { return \"hello world\"; }";
    private static final Uri BIDDING_LOGIC_URI = Uri.parse("http://www.domain.com/logic/1");

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildDBBuyerDecisionLogic() {
        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(BUYER_DECISION_LOGIC_JS)
                        .build();

        assertEquals(BIDDING_LOGIC_URI, dbBuyerDecisionLogic.getBiddingLogicUri());
        assertEquals(BUYER_DECISION_LOGIC_JS, dbBuyerDecisionLogic.getBuyerDecisionLogicJs());
    }

    @Test
    public void testBuyerDecisionLogicsWithSameValuesAreEqual() {
        DBBuyerDecisionLogic obj1 =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(BUYER_DECISION_LOGIC_JS)
                        .build();

        DBBuyerDecisionLogic obj2 =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(BUYER_DECISION_LOGIC_JS)
                        .build();

        assertThat(obj1).isEqualTo(obj2);
    }

    @Test
    public void testBuyerDecisionLogicsWithDifferentValuesAreNotEqual() {
        DBBuyerDecisionLogic obj1 =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(BUYER_DECISION_LOGIC_JS)
                        .build();

        DBBuyerDecisionLogic obj2 =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(Uri.parse("http://www.differenturl.com/logic/1"))
                        .setBuyerDecisionLogicJs(BUYER_DECISION_LOGIC_JS)
                        .build();

        assertThat(obj1).isNotEqualTo(obj2);
    }

    @Test
    public void testEqualBuyerDecisionLogicObjectsHaveSameHashCode() {
        DBBuyerDecisionLogic obj1 =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(BUYER_DECISION_LOGIC_JS)
                        .build();
        DBBuyerDecisionLogic obj2 =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(BUYER_DECISION_LOGIC_JS)
                        .build();

        CommonFixture.assertHaveSameHashCode(obj1, obj2);
    }

    @Test
    public void testNotEqualBuyerDecisionLogicObjectsHaveDifferentHashCode() {
        DBBuyerDecisionLogic obj1 =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(BUYER_DECISION_LOGIC_JS)
                        .build();
        DBBuyerDecisionLogic obj2 =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(Uri.parse("http://www.differenturl.com/logic/1"))
                        .setBuyerDecisionLogicJs(BUYER_DECISION_LOGIC_JS)
                        .build();
        DBBuyerDecisionLogic obj3 =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs("function test() { return \"different world\"; }")
                        .build();

        CommonFixture.assertDifferentHashCode(obj1, obj2, obj3);
    }
}
