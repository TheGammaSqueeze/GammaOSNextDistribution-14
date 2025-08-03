/*
 * Copyright (C) 2018 The Android Open Source Project
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

#define STATSD_DEBUG false  // STOPSHIP if true
#include "Log.h"

#include "puller_util.h"
#include "stats_log_util.h"

namespace android {
namespace os {
namespace statsd {

using namespace std;

/**
 * Process all data and merge isolated with host if necessary.
 * For example:
 *   NetworkBytesAtom {
 *       int uid = 1;
 *       State process_state = 2;
 *       int byte_send = 3;
 *       int byte_recv = 4;
 *   }
 *   additive fields are {3, 4}
 * If we pulled the following events (uid1_child is an isolated uid which maps to uid1):
 * [uid1, fg, 100, 200]
 * [uid1_child, fg, 100, 200]
 * [uid1, bg, 100, 200]
 *
 * We want to merge them and results should be:
 * [uid1, fg, 200, 400]
 * [uid1, bg, 100, 200]
 *
 * All atoms should be of the same tagId. All fields should be present.
 */
void mapAndMergeIsolatedUidsToHostUid(vector<shared_ptr<LogEvent>>& data, const sp<UidMap>& uidMap,
                                      int tagId, const vector<int>& additiveFieldsVec) {
    // Check the first LogEvent for attribution chain or a uid field as either all atoms with this
    // tagId have them or none of them do.
    std::pair<size_t, size_t> attrIndexRange;
    const bool hasAttributionChain = data[0]->hasAttributionChain(&attrIndexRange);
    const uint8_t numUidFields = data[0]->getNumUidFields();

    if (!hasAttributionChain && numUidFields == 0) {
        VLOG("No uid or attribution chain to merge, atom %d", tagId);
        return;
    }

    // 1. Map all isolated uid in-place to host uid
    for (shared_ptr<LogEvent>& event : data) {
        if (event->GetTagId() != tagId) {
            ALOGE("Wrong atom. Expecting %d, got %d", tagId, event->GetTagId());
            return;
        }
        if (hasAttributionChain) {
            vector<FieldValue>* const fieldValues = event->getMutableValues();
            for (size_t i = attrIndexRange.first; i <= attrIndexRange.second; i++) {
                FieldValue& fieldValue = fieldValues->at(i);
                if (isAttributionUidField(fieldValue)) {
                    const int hostUid = uidMap->getHostUidOrSelf(fieldValue.mValue.int_value);
                    fieldValue.mValue.setInt(hostUid);
                }
            }
        } else {
            mapIsolatedUidsToHostUidInLogEvent(uidMap, *event);
        }
    }

    // 2. sort the data, bit-wise
    sort(data.begin(), data.end(),
         [](const shared_ptr<LogEvent>& lhs, const shared_ptr<LogEvent>& rhs) {
             if (lhs->size() != rhs->size()) {
                 return lhs->size() < rhs->size();
             }
             const std::vector<FieldValue>& lhsValues = lhs->getValues();
             const std::vector<FieldValue>& rhsValues = rhs->getValues();
             for (int i = 0; i < (int)lhs->size(); i++) {
                 if (lhsValues[i] != rhsValues[i]) {
                     return lhsValues[i] < rhsValues[i];
                 }
             }
             return false;
         });

    vector<shared_ptr<LogEvent>> mergedData;
    const set<int> additiveFields(additiveFieldsVec.begin(), additiveFieldsVec.end());
    bool needMerge = true;

    // 3. do the merge.
    // The loop invariant is this: for every event,
    // - check if it has a different length (means different attribution chains or repeated fields)
    // - check if fields are different
    // - check if non-additive field values are different (non-additive is default for repeated
    // fields)
    // If any are true, no need to merge, add itself to the result. Otherwise, merge the
    // value onto the one immediately next to it.
    for (int i = 0; i < (int)data.size() - 1; i++) {
        // Size different, must be different chains or repeated fields.
        if (data[i]->size() != data[i + 1]->size()) {
            mergedData.push_back(data[i]);
            continue;
        }
        vector<FieldValue>* lhsValues = data[i]->getMutableValues();
        vector<FieldValue>* rhsValues = data[i + 1]->getMutableValues();
        needMerge = true;
        for (int p = 0; p < (int)lhsValues->size(); p++) {
            if ((*lhsValues)[p].mField != (*rhsValues)[p].mField) {
                needMerge = false;
                break;
            }
            if ((*lhsValues)[p].mValue != (*rhsValues)[p].mValue) {
                int pos = (*lhsValues)[p].mField.getPosAtDepth(0);
                // Differ on non-additive field, abort.
                // Repeated additive fields are treated as non-additive fields.
                if (isPrimitiveRepeatedField((*lhsValues)[p].mField) ||
                    (additiveFields.find(pos) == additiveFields.end())) {
                    needMerge = false;
                    break;
                }
            }
        }
        if (!needMerge) {
            mergedData.push_back(data[i]);
            continue;
        }
        // This should be infrequent operation.
        for (int p = 0; p < (int)lhsValues->size(); p++) {
            int pos = (*lhsValues)[p].mField.getPosAtDepth(0);
            // Don't merge repeated fields.
            if (!isPrimitiveRepeatedField((*lhsValues)[p].mField) &&
                (additiveFields.find(pos) != additiveFields.end())) {
                (*rhsValues)[p].mValue += (*lhsValues)[p].mValue;
            }
        }
    }
    mergedData.push_back(data.back());

    data.clear();
    data = mergedData;
}

}  // namespace statsd
}  // namespace os
}  // namespace android
