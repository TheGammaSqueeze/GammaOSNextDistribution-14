/**
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

package com.android.car.internal.property;

import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.util.ArraySet;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SubscriptionManagerUnitTest extends AbstractExtendedMockitoTestCase {

    private final SubscriptionManager<Object> mSubscriptionManager = new SubscriptionManager<>();
    private final Object mClient1 = new Object();
    private final Object mClient2 = new Object();
    private static final int PROPERTY1 = 1;
    private static final int PROPERTY2 = 2;
    private static final int AREA1 = 1;
    private static final int AREA2 = 2;
    private static final float ON_CHANGE_RATE = 0f;

    private CarSubscription getCarSubscription(int propertyId, int[] areaIds) {
        return getCarSubscription(propertyId, areaIds, ON_CHANGE_RATE,
                /*enableVariableUpdateRate*/ false, /*resolution*/ 0.0f);
    }

    private CarSubscription getCarSubscription(int propertyId, int[] areaIds,
            float updateRateHz) {
        return getCarSubscription(propertyId, areaIds, updateRateHz,
                /*enableVariableUpdateRate*/ false, /*resolution*/ 0.0f);
    }

    private CarSubscription getCarSubscription(int propertyId, int[] areaIds,
                                               float updateRateHz, boolean enableVariableUpdateRate,
                                               float resolution) {
        CarSubscription option = new CarSubscription();
        option.propertyId = propertyId;
        option.areaIds = areaIds;
        option.updateRateHz = updateRateHz;
        option.enableVariableUpdateRate = enableVariableUpdateRate;
        option.resolution = resolution;
        return option;
    }

    @Test
    public void testStageNewOptions_Commit_GetClients() {
        mSubscriptionManager.stageNewOptions(mClient1, List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2})
        ));

        mSubscriptionManager.commit();

        expectWithMessage("Get expected clients for PROPERTY1, AREA1").that(
                mSubscriptionManager.getClients(PROPERTY1, AREA1)).isEqualTo(Set.of(mClient1));
        expectWithMessage("Get expected clients for PROPERTY1, AREA2").that(
                mSubscriptionManager.getClients(PROPERTY1, AREA2)).isEqualTo(Set.of(mClient1));
        expectWithMessage("Get expected clients for PROPERTY2, AREA1").that(
                mSubscriptionManager.getClients(PROPERTY2, AREA1)).isNull();
    }

    @Test
    public void testStageNewOptions_Commit_GetCurrentSubscribedPropIds() {
        mSubscriptionManager.stageNewOptions(mClient1, List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}),
                getCarSubscription(PROPERTY2, new int[]{AREA1, AREA2})
        ));

        mSubscriptionManager.commit();

        expectWithMessage("Get expected current subscribed property IDs").that(
                mSubscriptionManager.getCurrentSubscribedPropIds())
                .isEqualTo(Set.of(PROPERTY1, PROPERTY2));
    }

    @Test
    public void testStageNewOptions_UpdateRateChangedToLowerRate() {
        mSubscriptionManager.stageNewOptions(mClient1, List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}, 1.0f)
        ));

        mSubscriptionManager.commit();

        List<CarSubscription> newOptions = List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}, 0.5f)
        );
        mSubscriptionManager.stageNewOptions(mClient1, newOptions);

        List<CarSubscription> outDiffSubscribeOptions = new ArrayList<>();
        List<Integer> outPropertyIdsToUnsubscribe = new ArrayList<>();
        mSubscriptionManager.diffBetweenCurrentAndStage(outDiffSubscribeOptions,
                outPropertyIdsToUnsubscribe);

        expectThat(outPropertyIdsToUnsubscribe).isEmpty();
        expectThat(outDiffSubscribeOptions).containsExactlyElementsIn(newOptions);
    }

    @Test
    public void testStageNewOptions_resolutionChangedToDifferentValue() {
        mSubscriptionManager.stageNewOptions(mClient1, List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}, 1.0f,
                        /*enableVariableUpdateRate*/ false, 1.0f)
        ));

        mSubscriptionManager.commit();

        mSubscriptionManager.stageNewOptions(mClient2, List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}, 1.0f,
                        /*enableVariableUpdateRate*/ false, 10.0f)
        ));

        List<CarSubscription> outDiffSubscribeOptions = new ArrayList<>();
        List<Integer> outPropertyIdsToUnsubscribe = new ArrayList<>();
        mSubscriptionManager.diffBetweenCurrentAndStage(outDiffSubscribeOptions,
                outPropertyIdsToUnsubscribe);

        expectThat(outPropertyIdsToUnsubscribe).isEmpty();
        expectThat(outDiffSubscribeOptions).isEmpty();

        List<CarSubscription> newOptions = List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}, 1.0f,
                        /*enableVariableUpdateRate*/ false, 0.1f)
        );
        mSubscriptionManager.stageNewOptions(mClient2, newOptions);

        mSubscriptionManager.diffBetweenCurrentAndStage(outDiffSubscribeOptions,
                outPropertyIdsToUnsubscribe);

        expectThat(outPropertyIdsToUnsubscribe).isEmpty();
        expectThat(outDiffSubscribeOptions).containsExactlyElementsIn(newOptions);
    }

    @Test
    public void testStageNewOptions_DropCommit_GetClients() {
        mSubscriptionManager.stageNewOptions(mClient1, List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1})
        ));
        mSubscriptionManager.commit();
        mSubscriptionManager.stageNewOptions(mClient1, List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA2})
        ));
        mSubscriptionManager.dropCommit();

        expectWithMessage("Committed change must be preserved").that(
                mSubscriptionManager.getClients(PROPERTY1, AREA1)).isEqualTo(Set.of(mClient1));
        expectWithMessage("Dropped change must be discarded").that(
                mSubscriptionManager.getClients(PROPERTY1, AREA2)).isNull();
    }

    @Test
    public void testStageUnregister_Commit_GetClients() {
        mSubscriptionManager.stageNewOptions(mClient1, List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}),
                getCarSubscription(PROPERTY2, new int[]{AREA1, AREA2})
        ));
        mSubscriptionManager.commit();
        mSubscriptionManager.stageUnregister(mClient1, new ArraySet<Integer>(Set.of(
                PROPERTY1)));
        mSubscriptionManager.commit();

        expectWithMessage("Committed unregistered property must not be returned").that(
                mSubscriptionManager.getClients(PROPERTY1, AREA1)).isNull();
        expectWithMessage("Committed unregistered property must not be returned").that(
                mSubscriptionManager.getClients(PROPERTY1, AREA2)).isNull();
        expectWithMessage("Still registered client must be returned").that(
                mSubscriptionManager.getClients(PROPERTY2, AREA1)).isEqualTo(Set.of(mClient1));
        expectWithMessage("Still registered client must be returned").that(
                mSubscriptionManager.getClients(PROPERTY2, AREA2)).isEqualTo(Set.of(mClient1));
    }

    @Test
    public void testStageUnregister_DropCommit_GetClients() {
        mSubscriptionManager.stageNewOptions(mClient1, List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2})
        ));
        mSubscriptionManager.commit();
        mSubscriptionManager.stageUnregister(mClient1, new ArraySet<Integer>(Set.of(
                PROPERTY1)));
        mSubscriptionManager.dropCommit();

        expectWithMessage("Dropped unregistration must be discarded").that(
                mSubscriptionManager.getClients(PROPERTY1, AREA1)).isEqualTo(Set.of(mClient1));
        expectWithMessage("Dropped unregistration must be discarded").that(
                mSubscriptionManager.getClients(PROPERTY1, AREA2)).isEqualTo(Set.of(mClient1));
    }

    @Test
    public void testDiffBetweenCurrentAndStage_simpleAdd() {
        List<CarSubscription> newOptions = List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}),
                getCarSubscription(PROPERTY2, new int[]{AREA1, AREA2})
        );
        mSubscriptionManager.stageNewOptions(mClient1, newOptions);
        List<CarSubscription> outDiffSubscribeOptions = new ArrayList<>();
        List<Integer> outPropertyIdsToUnsubscribe = new ArrayList<>();

        mSubscriptionManager.diffBetweenCurrentAndStage(outDiffSubscribeOptions,
                outPropertyIdsToUnsubscribe);

        expectThat(outPropertyIdsToUnsubscribe).isEmpty();
        expectThat(outDiffSubscribeOptions).containsExactlyElementsIn(newOptions);
    }

    @Test
    public void testDiffBetweenCurrentAndStage_simpleRemove() {
        List<CarSubscription> newOptions = List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}),
                getCarSubscription(PROPERTY2, new int[]{AREA1, AREA2})
        );
        mSubscriptionManager.stageNewOptions(mClient1, newOptions);
        mSubscriptionManager.commit();
        mSubscriptionManager.stageUnregister(mClient1, new ArraySet<Integer>(Set.of(
                PROPERTY1)));
        List<CarSubscription> outDiffSubscribeOptions = new ArrayList<>();
        List<Integer> outPropertyIdsToUnsubscribe = new ArrayList<>();

        mSubscriptionManager.diffBetweenCurrentAndStage(outDiffSubscribeOptions,
                outPropertyIdsToUnsubscribe);

        expectThat(outPropertyIdsToUnsubscribe).isEqualTo(List.of(PROPERTY1));
        expectThat(outDiffSubscribeOptions).isEmpty();
    }

    @Test
    public void testDiffBetweenCurrentAndStage_updatedRate() {
        List<CarSubscription> client1Options = List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}),
                getCarSubscription(PROPERTY2, new int[]{AREA1, AREA2})
        );
        mSubscriptionManager.stageNewOptions(mClient1, client1Options);
        mSubscriptionManager.commit();

        List<CarSubscription> client2Options = List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}, 1.23f),
                getCarSubscription(PROPERTY2, new int[]{AREA1, AREA2}, 2.34f)
        );
        mSubscriptionManager.stageNewOptions(mClient2, client2Options);
        List<CarSubscription> outDiffSubscribeOptions = new ArrayList<>();
        List<Integer> outPropertyIdsToUnsubscribe = new ArrayList<>();

        mSubscriptionManager.diffBetweenCurrentAndStage(outDiffSubscribeOptions,
                outPropertyIdsToUnsubscribe);

        expectThat(outPropertyIdsToUnsubscribe).isEmpty();
        expectThat(outDiffSubscribeOptions).containsExactlyElementsIn(client2Options);
    }

    @Test
    public void testDiffBetweenCurrentAndStage_updatedResolution() {
        List<CarSubscription> client1Options = List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}, ON_CHANGE_RATE,
                        /*enableVariableUpdateRate*/ false, 1.0f),
                getCarSubscription(PROPERTY2, new int[]{AREA1, AREA2}, ON_CHANGE_RATE,
                        /*enableVariableUpdateRate*/ false, 1.0f)
        );
        mSubscriptionManager.stageNewOptions(mClient1, client1Options);
        mSubscriptionManager.commit();

        List<CarSubscription> client2Options = List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}, ON_CHANGE_RATE,
                        /*enableVariableUpdateRate*/ false, 0.1f),
                getCarSubscription(PROPERTY2, new int[]{AREA1, AREA2}, ON_CHANGE_RATE,
                        /*enableVariableUpdateRate*/ false, 0.1f)
        );
        mSubscriptionManager.stageNewOptions(mClient2, client2Options);
        List<CarSubscription> outDiffSubscribeOptions = new ArrayList<>();
        List<Integer> outPropertyIdsToUnsubscribe = new ArrayList<>();

        mSubscriptionManager.diffBetweenCurrentAndStage(outDiffSubscribeOptions,
                outPropertyIdsToUnsubscribe);

        expectThat(outPropertyIdsToUnsubscribe).isEmpty();
        expectThat(outDiffSubscribeOptions).containsExactlyElementsIn(client2Options);
    }

    @Test
    public void testDiffBetweenCurrentAndStage_updateRateChangedToLowerRate() {
        mSubscriptionManager.stageNewOptions(mClient1, List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}, 1.0f)
        ));

        mSubscriptionManager.commit();

        List<CarSubscription> newOptions = List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}, 0.5f)
        );
        mSubscriptionManager.stageNewOptions(mClient1, newOptions);

        List<CarSubscription> outDiffSubscribeOptions = new ArrayList<>();
        List<Integer> outPropertyIdsToUnsubscribe = new ArrayList<>();
        mSubscriptionManager.diffBetweenCurrentAndStage(outDiffSubscribeOptions,
                outPropertyIdsToUnsubscribe);

        expectThat(outPropertyIdsToUnsubscribe).isEmpty();
        expectThat(outDiffSubscribeOptions).containsExactlyElementsIn(newOptions);
    }

    @Test
    public void testDiffBetweenCurrentAndStage_resolutionChangedToDifferentValue() {
        mSubscriptionManager.stageNewOptions(mClient1, List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}, 1.0f,
                        /*enableVariableUpdateRate*/ false, 1.0f)
        ));

        mSubscriptionManager.commit();

        mSubscriptionManager.stageNewOptions(mClient2, List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}, 1.0f,
                        /*enableVariableUpdateRate*/ false, 10.0f)
        ));

        List<CarSubscription> outDiffSubscribeOptions = new ArrayList<>();
        List<Integer> outPropertyIdsToUnsubscribe = new ArrayList<>();
        mSubscriptionManager.diffBetweenCurrentAndStage(outDiffSubscribeOptions,
                outPropertyIdsToUnsubscribe);

        expectThat(outPropertyIdsToUnsubscribe).isEmpty();
        expectThat(outDiffSubscribeOptions).isEmpty();

        List<CarSubscription> newOptions = List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}, 1.0f,
                        /*enableVariableUpdateRate*/ false, 0.1f)
        );
        mSubscriptionManager.stageNewOptions(mClient2, newOptions);

        mSubscriptionManager.diffBetweenCurrentAndStage(outDiffSubscribeOptions,
                outPropertyIdsToUnsubscribe);

        expectThat(outPropertyIdsToUnsubscribe).isEmpty();
        expectThat(outDiffSubscribeOptions).containsExactlyElementsIn(newOptions);
    }

    @Test
    public void testDiffBetweenCurrentAndStage_updateEnableVariableUpdateRate() {
        CarSubscription option1 = getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2});
        CarSubscription option2 = getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2});
        option1.enableVariableUpdateRate = false;
        option2.enableVariableUpdateRate = true;
        // client1 disables Vur.
        mSubscriptionManager.stageNewOptions(mClient1, List.of(option1));

        List<CarSubscription> outDiffSubscribeOptions = new ArrayList<>();
        List<Integer> outPropertyIdsToUnsubscribe = new ArrayList<>();

        mSubscriptionManager.diffBetweenCurrentAndStage(outDiffSubscribeOptions,
                outPropertyIdsToUnsubscribe);

        expectThat(outPropertyIdsToUnsubscribe).isEmpty();
        expectThat(outDiffSubscribeOptions).containsExactly(option1);
        outDiffSubscribeOptions.clear();

        mSubscriptionManager.commit();
        // Only client2 enables Vur, so combined Vur is still disabled.
        mSubscriptionManager.stageNewOptions(mClient2, List.of(option2));

        mSubscriptionManager.diffBetweenCurrentAndStage(outDiffSubscribeOptions,
                outPropertyIdsToUnsubscribe);

        expectThat(outPropertyIdsToUnsubscribe).isEmpty();
        expectWithMessage("Only one client enables Vur must cause Vur disabled as combined")
                .that(outDiffSubscribeOptions).isEmpty();

        mSubscriptionManager.commit();
        // Unregisters client1, so only client2 is registered with Vur enabled.
        mSubscriptionManager.stageUnregister(mClient1, new ArraySet<Integer>(Set.of(
                PROPERTY1)));

        mSubscriptionManager.diffBetweenCurrentAndStage(outDiffSubscribeOptions,
                outPropertyIdsToUnsubscribe);

        expectThat(outPropertyIdsToUnsubscribe).isEmpty();
        expectWithMessage("Vur must be enabled when only one client registers with Vur enabled")
                .that(outDiffSubscribeOptions).containsExactly(option2);
    }

    @Test
    public void testDiffBetweenCurrentAndStage_removeClientCauseRateChange() {
        mSubscriptionManager.stageNewOptions(mClient1, List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}, 1.0f)
        ));
        mSubscriptionManager.commit();
        mSubscriptionManager.stageNewOptions(mClient2, List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}, 0.5f)
        ));
        mSubscriptionManager.commit();
        mSubscriptionManager.stageUnregister(mClient1, new ArraySet<Integer>(Set.of(
                PROPERTY1)));

        List<CarSubscription> outDiffSubscribeOptions = new ArrayList<>();
        List<Integer> outPropertyIdsToUnsubscribe = new ArrayList<>();
        mSubscriptionManager.diffBetweenCurrentAndStage(outDiffSubscribeOptions,
                outPropertyIdsToUnsubscribe);

        expectThat(outPropertyIdsToUnsubscribe).isEmpty();
        expectThat(outDiffSubscribeOptions).containsExactlyElementsIn(List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}, 0.5f)
        ));
    }

    @Test
    public void testDiffBetweenCurrentAndStage_removeClientCauseResolutionChange() {
        mSubscriptionManager.stageNewOptions(mClient1, List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}, 1.0f,
                        /*enableVariableUpdateRate*/ false, 1.0f)
        ));
        mSubscriptionManager.commit();
        mSubscriptionManager.stageNewOptions(mClient2, List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}, 1.0f,
                        /*enableVariableUpdateRate*/ false, 10.0f)
        ));
        mSubscriptionManager.commit();
        mSubscriptionManager.stageUnregister(mClient1, new ArraySet<Integer>(Set.of(
                PROPERTY1)));

        List<CarSubscription> outDiffSubscribeOptions = new ArrayList<>();
        List<Integer> outPropertyIdsToUnsubscribe = new ArrayList<>();
        mSubscriptionManager.diffBetweenCurrentAndStage(outDiffSubscribeOptions,
                outPropertyIdsToUnsubscribe);

        expectThat(outPropertyIdsToUnsubscribe).isEmpty();
        expectThat(outDiffSubscribeOptions).containsExactlyElementsIn(List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}, 1.0f,
                        /*enableVariableUpdateRate*/ false, 10.0f)
        ));
    }

    @Test
    public void testDiffBetweenCurrentAndStage_complicatedCases() {
        // Client1 subscribes for PROPERTY1, AREA1.
        List<CarSubscription> client1Options = List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1}, 1.23f)
        );
        mSubscriptionManager.stageNewOptions(mClient1, client1Options);
        mSubscriptionManager.commit();

        // Client2 subscribes at all areas for property 1 at a lower rate, and all areas for
        // property 2 at a higher rate.
        List<CarSubscription> client2Options = List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}, 0.12f),
                getCarSubscription(PROPERTY2, new int[]{AREA1, AREA2}, 3.45f)
        );
        mSubscriptionManager.stageNewOptions(mClient2, client2Options);
        List<CarSubscription> outDiffSubscribeOptions = new ArrayList<>();
        List<Integer> outPropertyIdsToUnsubscribe = new ArrayList<>();

        mSubscriptionManager.diffBetweenCurrentAndStage(outDiffSubscribeOptions,
                outPropertyIdsToUnsubscribe);

        // [PROPERTY1, AREA1]: No change since new rate is lower.
        // [PROPERTY1, AREA2]: New rate 0.12f
        // [PROPERTY2, AREA1]: New rate 3.45f
        // [PROPERTY2, AREA2]: New rate 3.45f
        expectThat(outPropertyIdsToUnsubscribe).isEmpty();
        expectWithMessage(
                "Only the newly subscribed areas or areas with a higher rate should be changed")
                .that(outDiffSubscribeOptions).containsExactlyElementsIn(List.of(
                        getCarSubscription(PROPERTY1, new int[]{AREA2}, 0.12f),
                        getCarSubscription(PROPERTY2, new int[]{AREA1, AREA2}, 3.45f)
        ));

        mSubscriptionManager.commit();
        // Client 2 subscribes to PROPERTY2, AREA1 for a higher rate.
        mSubscriptionManager.stageNewOptions(mClient2, List.of(
                getCarSubscription(PROPERTY2, new int[]{AREA1}, 4.56f)
        ));

        outDiffSubscribeOptions.clear();
        outPropertyIdsToUnsubscribe.clear();
        mSubscriptionManager.diffBetweenCurrentAndStage(outDiffSubscribeOptions,
                outPropertyIdsToUnsubscribe);

        // [PROPERTY2, AREA1]: New rate 4.56f
        expectThat(outPropertyIdsToUnsubscribe).isEmpty();
        expectWithMessage(
                "Only areas with a higher rate should be changed")
                .that(outDiffSubscribeOptions).containsExactlyElementsIn(List.of(
                        getCarSubscription(PROPERTY2, new int[]{AREA1}, 4.56f)
        ));

        mSubscriptionManager.commit();
        // Client2 unsubscribes PROPERTY1, PROPERTY2.
        // Client1 still subscribes to PROPERTY1, AREA1 at 1.23f.
        mSubscriptionManager.stageUnregister(mClient2, new ArraySet<Integer>(Set.of(
                PROPERTY1, PROPERTY2)));

        outDiffSubscribeOptions.clear();
        outPropertyIdsToUnsubscribe.clear();
        mSubscriptionManager.diffBetweenCurrentAndStage(outDiffSubscribeOptions,
                outPropertyIdsToUnsubscribe);

        // Technically Property1, Area2 is also unsubscribed, but we do not support unsubscribing
        // a single area yet, so this is a no-op.
        expectWithMessage("Property2 is no longer subscribed after client 2 unregister")
                .that(outPropertyIdsToUnsubscribe).containsExactly(PROPERTY2);
        expectWithMessage("Property1, Area1 is already subscribed at 1.23f, "
                + "client2 unsubscribe at a lower rate should not cause change")
                .that(outDiffSubscribeOptions).isEmpty();

        mSubscriptionManager.commit();
        mSubscriptionManager.stageUnregister(mClient1, new ArraySet<Integer>(Set.of(
                PROPERTY1)));

        outDiffSubscribeOptions.clear();
        outPropertyIdsToUnsubscribe.clear();
        mSubscriptionManager.diffBetweenCurrentAndStage(outDiffSubscribeOptions,
                outPropertyIdsToUnsubscribe);

        expectWithMessage("Property1 is no longer subscribed").that(outPropertyIdsToUnsubscribe)
                .containsExactly(PROPERTY1);
        expectThat(outDiffSubscribeOptions).isEmpty();

        // Finally let's make sure that dropping the commit should cause no diff.
        mSubscriptionManager.dropCommit();

        outDiffSubscribeOptions.clear();
        outPropertyIdsToUnsubscribe.clear();
        mSubscriptionManager.diffBetweenCurrentAndStage(outDiffSubscribeOptions,
                outPropertyIdsToUnsubscribe);

        expectWithMessage("Dropping commit must cause not change")
                .that(outDiffSubscribeOptions).isEmpty();
        expectWithMessage("Dropping commit must cause not change")
                .that(outPropertyIdsToUnsubscribe).isEmpty();
    }

    @Test
    public void testStageNewOptions_calledTwice() {
        mSubscriptionManager.stageNewOptions(mClient1, List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2})
        ));
        mSubscriptionManager.stageNewOptions(mClient2, List.of(
                getCarSubscription(PROPERTY2, new int[]{AREA1, AREA2})
        ));

        List<CarSubscription> outDiffSubscribeOptions = new ArrayList<>();
        List<Integer> outPropertyIdsToUnsubscribe = new ArrayList<>();
        mSubscriptionManager.diffBetweenCurrentAndStage(outDiffSubscribeOptions,
                outPropertyIdsToUnsubscribe);

        expectThat(outPropertyIdsToUnsubscribe).isEmpty();
        expectThat(outDiffSubscribeOptions).containsExactly(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}),
                getCarSubscription(PROPERTY2, new int[]{AREA1, AREA2})
        );
    }

    @Test
    public void testStageUnregister_calledTwice() {
        mSubscriptionManager.stageNewOptions(mClient1, List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}),
                getCarSubscription(PROPERTY2, new int[]{AREA1, AREA2})
        ));
        mSubscriptionManager.commit();

        mSubscriptionManager.stageUnregister(mClient1, new ArraySet<Integer>(Set.of(
                PROPERTY1)));
        mSubscriptionManager.stageUnregister(mClient1, new ArraySet<Integer>(Set.of(
                PROPERTY2)));

        List<CarSubscription> outDiffSubscribeOptions = new ArrayList<>();
        List<Integer> outPropertyIdsToUnsubscribe = new ArrayList<>();
        mSubscriptionManager.diffBetweenCurrentAndStage(outDiffSubscribeOptions,
                outPropertyIdsToUnsubscribe);

        expectThat(outPropertyIdsToUnsubscribe).containsExactly(PROPERTY1, PROPERTY2);
        expectThat(outDiffSubscribeOptions).isEmpty();
    }

    @Test
    public void testDiffBetweenCurrentAndStage_noStagedChanges() {
        List<CarSubscription> client1Options = List.of(
                getCarSubscription(PROPERTY1, new int[]{AREA1, AREA2}),
                getCarSubscription(PROPERTY2, new int[]{AREA1, AREA2})
        );
        mSubscriptionManager.stageNewOptions(mClient1, client1Options);
        mSubscriptionManager.commit();

        // No staged changes after commit.
        List<CarSubscription> outDiffSubscribeOptions = new ArrayList<>();
        List<Integer> outPropertyIdsToUnsubscribe = new ArrayList<>();
        mSubscriptionManager.diffBetweenCurrentAndStage(outDiffSubscribeOptions,
                outPropertyIdsToUnsubscribe);

        expectThat(outPropertyIdsToUnsubscribe).isEmpty();
        expectThat(outDiffSubscribeOptions).isEmpty();
    }
}
