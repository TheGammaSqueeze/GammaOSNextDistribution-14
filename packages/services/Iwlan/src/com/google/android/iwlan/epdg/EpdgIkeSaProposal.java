/*
 * Copyright 2020 The Android Open Source Project
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

package com.google.android.iwlan.epdg;

import android.net.ipsec.ike.IkeSaProposal;
import android.util.Pair;

import java.util.LinkedHashSet;

public class EpdgIkeSaProposal extends EpdgSaProposal {
    protected final LinkedHashSet<Integer> mProposedPrfAlgos = new LinkedHashSet<>();

    /**
     * Add proposed PRF algorithms by the carrier.
     *
     * @param prfAlgos proposed PRF algorithms
     */
    public void addProposedPrfAlgorithm(int[] prfAlgos) {
        for (int prfAlgo : prfAlgos) {
            if (validateConfig(prfAlgo, VALID_PRF_ALGOS, CONFIG_TYPE_PRF_ALGO)) {
                mProposedPrfAlgos.add(prfAlgo);
            }
        }
    }

    private int[] getPrfAlgos() {
        if (isSaferProposalsPrioritized()) {
            return mProposedPrfAlgos.stream()
                    .sorted(
                            (item1, item2) ->
                                    compareTransformPriority(VALID_PRF_ALGOS, item1, item2))
                    .mapToInt(Integer::intValue)
                    .toArray();
        }

        return mProposedPrfAlgos.stream().mapToInt(Integer::intValue).toArray();
    }

    private int[] getSupportedPrfAlgos() {
        return VALID_PRF_ALGOS.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Builds {@link IkeSaProposal} of carrier proposed encryption algorithms (non-AEAD) cipher
     * suit.
     */
    public IkeSaProposal buildProposedIkeSaProposal() {
        return buildProposal(false, true);
    }

    /** Builds {@link IkeSaProposal} of carrier proposed AEAD algorithms cipher suit. */
    public IkeSaProposal buildProposedIkeSaAeadProposal() {
        return buildProposal(true, true);
    }

    /**
     * Builds {@link IkeSaProposal} of Iwlan supported encryption algorithms (non-AEAD) cipher suit.
     */
    public IkeSaProposal buildSupportedIkeSaProposal() {
        return buildProposal(false, false);
    }

    /** Builds {@link IkeSaProposal} of Iwlan supported AEAD algorithms cipher suit. */
    public IkeSaProposal buildSupportedIkeSaAeadProposal() {
        return buildProposal(true, false);
    }

    private IkeSaProposal buildProposal(boolean isAead, boolean isProposed) {
        IkeSaProposal.Builder saProposalBuilder = new IkeSaProposal.Builder();

        int[] dhGroups = isProposed ? getDhGroups() : getSupportedDhGroups();
        for (int dhGroup : dhGroups) {
            saProposalBuilder.addDhGroup(dhGroup);
        }

        Pair<Integer, Integer>[] encrAlgos;

        if (isAead) {
            encrAlgos = (isProposed) ? getAeadAlgos() : getSupportedAeadAlgos();
        } else {
            encrAlgos = (isProposed) ? getEncryptionAlgos() : getSupportedEncryptionAlgos();
        }

        for (Pair<Integer, Integer> encrAlgo : encrAlgos) {
            saProposalBuilder.addEncryptionAlgorithm(encrAlgo.first, encrAlgo.second);
        }

        if (!isAead) {
            int[] integrityAlgos =
                    (isProposed) ? getIntegrityAlgos() : getSupportedIntegrityAlgos();
            for (int integrityAlgo : integrityAlgos) {
                saProposalBuilder.addIntegrityAlgorithm(integrityAlgo);
            }
        }

        int[] prfAlgos = (isProposed) ? getPrfAlgos() : getSupportedPrfAlgos();
        for (int prfAlgo : prfAlgos) {
            saProposalBuilder.addPseudorandomFunction(prfAlgo);
        }

        return saProposalBuilder.build();
    }
}
