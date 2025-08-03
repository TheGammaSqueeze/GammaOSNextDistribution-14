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

import android.net.ipsec.ike.ChildSaProposal;
import android.util.Pair;

public class EpdgChildSaProposal extends EpdgSaProposal {
    private boolean mAddChildSessionRekeyKePayload = false;

    /**
     * Add DH Groups to child session during rekey with KE payload. IKE library doesn't add KE
     * payload if dh groups are not set in child session params. Use the same groups as that of IKE
     * session.
     */
    public void enableAddChildSessionRekeyKePayload() {
        mAddChildSessionRekeyKePayload = true;
    }

    /**
     * Builds {@link ChildSaProposal} of carrier proposed encryption algorithms (non-AEAD) cipher
     * suit.
     */
    public ChildSaProposal buildProposedChildSaProposal() {
        return buildProposal(false, true);
    }

    /** Builds {@link ChildSaProposal} of carrier proposed AEAD algorithms cipher suit. */
    public ChildSaProposal buildProposedChildSaAeadProposal() {
        return buildProposal(true, true);
    }

    /**
     * Builds {@link ChildSaProposal} of Iwlan supported encryption algorithms (non-AEAD) cipher
     * suit.
     */
    public ChildSaProposal buildSupportedChildSaProposal() {
        return buildProposal(false, false);
    }

    /** Builds {@link ChildSaProposal} of Iwlan supported AEAD algorithms cipher suit. */
    public ChildSaProposal buildSupportedChildSaAeadProposal() {
        return buildProposal(true, false);
    }

    private ChildSaProposal buildProposal(boolean isAead, boolean isProposed) {
        ChildSaProposal.Builder saProposalBuilder = new ChildSaProposal.Builder();

        if (mAddChildSessionRekeyKePayload) {
            int[] dhGroups = getDhGroups();
            for (int dhGroup : dhGroups) {
                saProposalBuilder.addDhGroup(dhGroup);
            }
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

        return saProposalBuilder.build();
    }
}
