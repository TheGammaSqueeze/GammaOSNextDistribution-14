/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "logd/LogEvent.h"

#include <android-base/stringprintf.h>
#include <android/binder_ibinder.h>
#include <private/android_filesystem_config.h>

#include "flags/FlagProvider.h"
#include "stats_annotations.h"
#include "stats_log_util.h"
#include "statslog_statsd.h"

namespace android {
namespace os {
namespace statsd {

// for TrainInfo experiment id serialization
const int FIELD_ID_EXPERIMENT_ID = 1;

using namespace android::util;
using android::base::StringPrintf;
using android::util::ProtoOutputStream;
using std::string;
using std::vector;

namespace {

uint8_t getTypeId(uint8_t typeInfo) {
    return typeInfo & 0x0F;  // type id in lower 4 bytes
}

uint8_t getNumAnnotations(uint8_t typeInfo) {
    return (typeInfo >> 4) & 0x0F;  // num annotations in upper 4 bytes
}

}  // namespace

LogEvent::LogEvent(int32_t uid, int32_t pid)
    : mLogdTimestampNs(getWallClockNs()), mLogUid(uid), mLogPid(pid) {
}

LogEvent::LogEvent(const string& trainName, int64_t trainVersionCode, bool requiresStaging,
                   bool rollbackEnabled, bool requiresLowLatencyMonitor, int32_t state,
                   const std::vector<uint8_t>& experimentIds, int32_t userId) {
    mLogdTimestampNs = getWallClockNs();
    mElapsedTimestampNs = getElapsedRealtimeNs();
    mTagId = util::BINARY_PUSH_STATE_CHANGED;
    mLogUid = AIBinder_getCallingUid();
    mLogPid = AIBinder_getCallingPid();

    mValues.push_back(FieldValue(Field(mTagId, getSimpleField(1)), Value(trainName)));
    mValues.push_back(FieldValue(Field(mTagId, getSimpleField(2)), Value(trainVersionCode)));
    mValues.push_back(FieldValue(Field(mTagId, getSimpleField(3)), Value((int)requiresStaging)));
    mValues.push_back(FieldValue(Field(mTagId, getSimpleField(4)), Value((int)rollbackEnabled)));
    mValues.push_back(
            FieldValue(Field(mTagId, getSimpleField(5)), Value((int)requiresLowLatencyMonitor)));
    mValues.push_back(FieldValue(Field(mTagId, getSimpleField(6)), Value(state)));
    mValues.push_back(FieldValue(Field(mTagId, getSimpleField(7)), Value(experimentIds)));
    mValues.push_back(FieldValue(Field(mTagId, getSimpleField(8)), Value(userId)));
}

LogEvent::LogEvent(int64_t wallClockTimestampNs, int64_t elapsedTimestampNs,
                   const InstallTrainInfo& trainInfo) {
    mLogdTimestampNs = wallClockTimestampNs;
    mElapsedTimestampNs = elapsedTimestampNs;
    mTagId = util::TRAIN_INFO;

    mValues.push_back(
            FieldValue(Field(mTagId, getSimpleField(1)), Value(trainInfo.trainVersionCode)));
    std::vector<uint8_t> experimentIdsProto;
    writeExperimentIdsToProto(trainInfo.experimentIds, &experimentIdsProto);
    mValues.push_back(FieldValue(Field(mTagId, getSimpleField(2)), Value(experimentIdsProto)));
    mValues.push_back(FieldValue(Field(mTagId, getSimpleField(3)), Value(trainInfo.trainName)));
    mValues.push_back(FieldValue(Field(mTagId, getSimpleField(4)), Value(trainInfo.status)));
}

void LogEvent::parseInt32(int32_t* pos, int32_t depth, bool* last, uint8_t numAnnotations) {
    int32_t value = readNextValue<int32_t>();
    addToValues(pos, depth, value, last);
    parseAnnotations(numAnnotations);
}

void LogEvent::parseInt64(int32_t* pos, int32_t depth, bool* last, uint8_t numAnnotations) {
    int64_t value = readNextValue<int64_t>();
    addToValues(pos, depth, value, last);
    parseAnnotations(numAnnotations);
}

void LogEvent::parseString(int32_t* pos, int32_t depth, bool* last, uint8_t numAnnotations) {
    int32_t numBytes = readNextValue<int32_t>();
    if ((uint32_t)numBytes > mRemainingLen) {
        mValid = false;
        return;
    }

    string value = string((char*)mBuf, numBytes);
    mBuf += numBytes;
    mRemainingLen -= numBytes;
    addToValues(pos, depth, value, last);
    parseAnnotations(numAnnotations);
}

void LogEvent::parseFloat(int32_t* pos, int32_t depth, bool* last, uint8_t numAnnotations) {
    float value = readNextValue<float>();
    addToValues(pos, depth, value, last);
    parseAnnotations(numAnnotations);
}

void LogEvent::parseBool(int32_t* pos, int32_t depth, bool* last, uint8_t numAnnotations) {
    // cast to int32_t because FieldValue does not support bools
    int32_t value = (int32_t)readNextValue<uint8_t>();
    addToValues(pos, depth, value, last);
    parseAnnotations(numAnnotations);
}

void LogEvent::parseByteArray(int32_t* pos, int32_t depth, bool* last, uint8_t numAnnotations) {
    int32_t numBytes = readNextValue<int32_t>();
    if ((uint32_t)numBytes > mRemainingLen) {
        mValid = false;
        return;
    }

    vector<uint8_t> value(mBuf, mBuf + numBytes);
    mBuf += numBytes;
    mRemainingLen -= numBytes;
    addToValues(pos, depth, value, last);
    parseAnnotations(numAnnotations);
}

void LogEvent::parseKeyValuePairs(int32_t* pos, int32_t depth, bool* last, uint8_t numAnnotations) {
    int32_t numPairs = readNextValue<uint8_t>();

    for (pos[1] = 1; pos[1] <= numPairs; pos[1]++) {
        last[1] = (pos[1] == numPairs);

        // parse key
        pos[2] = 1;
        parseInt32(pos, /*depth=*/2, last, /*numAnnotations=*/0);

        // parse value
        last[2] = true;

        uint8_t typeInfo = readNextValue<uint8_t>();
        switch (getTypeId(typeInfo)) {
            case INT32_TYPE:
                pos[2] = 2;  // pos[2] determined by index of type in KeyValuePair in atoms.proto
                parseInt32(pos, /*depth=*/2, last, /*numAnnotations=*/0);
                break;
            case INT64_TYPE:
                pos[2] = 3;
                parseInt64(pos, /*depth=*/2, last, /*numAnnotations=*/0);
                break;
            case STRING_TYPE:
                pos[2] = 4;
                parseString(pos, /*depth=*/2, last, /*numAnnotations=*/0);
                break;
            case FLOAT_TYPE:
                pos[2] = 5;
                parseFloat(pos, /*depth=*/2, last, /*numAnnotations=*/0);
                break;
            default:
                mValid = false;
        }
    }

    parseAnnotations(numAnnotations);

    pos[1] = pos[2] = 1;
    last[1] = last[2] = false;
}

void LogEvent::parseAttributionChain(int32_t* pos, int32_t depth, bool* last,
                                     uint8_t numAnnotations) {
    std::optional<size_t> firstUidInChainIndex = mValues.size();
    const uint8_t numNodes = readNextValue<uint8_t>();

    if (numNodes > INT8_MAX) mValid = false;

    for (pos[1] = 1; pos[1] <= numNodes; pos[1]++) {
        last[1] = (pos[1] == numNodes);

        // parse uid
        pos[2] = 1;
        parseInt32(pos, /*depth=*/2, last, /*numAnnotations=*/0);

        // parse tag
        pos[2] = 2;
        last[2] = true;
        parseString(pos, /*depth=*/2, last, /*numAnnotations=*/0);
    }

    if (mValues.size() > (firstUidInChainIndex.value() + 1)) {
        // At least one node was successfully parsed.
        mAttributionChainStartIndex = firstUidInChainIndex;
        mAttributionChainEndIndex = mValues.size() - 1;
    } else {
        firstUidInChainIndex = std::nullopt;
        mValid = false;
    }

    if (mValid) {
        parseAnnotations(numAnnotations, /*numElements*/ std::nullopt, firstUidInChainIndex);
    }

    pos[1] = pos[2] = 1;
    last[1] = last[2] = false;
}

void LogEvent::parseArray(int32_t* pos, int32_t depth, bool* last, uint8_t numAnnotations) {
    const uint8_t numElements = readNextValue<uint8_t>();
    const uint8_t typeInfo = readNextValue<uint8_t>();
    const uint8_t typeId = getTypeId(typeInfo);

    if (numElements > INT8_MAX) mValid = false;

    for (pos[1] = 1; pos[1] <= numElements; pos[1]++) {
        last[1] = (pos[1] == numElements);

        // The top-level array is at depth 0, and all of its elements are at depth 1.
        // Once nested fields are supported, array elements will be at top-level depth + 1.

        switch (typeId) {
            case INT32_TYPE:
                parseInt32(pos, /*depth=*/1, last, /*numAnnotations=*/0);
                break;
            case INT64_TYPE:
                parseInt64(pos, /*depth=*/1, last, /*numAnnotations=*/0);
                break;
            case FLOAT_TYPE:
                parseFloat(pos, /*depth=*/1, last, /*numAnnotations=*/0);
                break;
            case BOOL_TYPE:
                parseBool(pos, /*depth=*/1, last, /*numAnnotations=*/0);
                break;
            case STRING_TYPE:
                parseString(pos, /*depth=*/1, last, /*numAnnotations=*/0);
                break;
            default:
                mValid = false;
                break;
        }
    }

    parseAnnotations(numAnnotations, numElements);

    pos[1] = 1;
    last[1] = false;
}

// Assumes that mValues is not empty
bool LogEvent::checkPreviousValueType(Type expected) {
    return mValues[mValues.size() - 1].mValue.getType() == expected;
}

void LogEvent::parseIsUidAnnotation(uint8_t annotationType, std::optional<uint8_t> numElements) {
    // Need to set numElements if not an array.
    if (!numElements) {
        numElements = 1;
    }

    // If array is empty, skip uid parsing.
    if (numElements == 0 && annotationType == BOOL_TYPE) {
        readNextValue<uint8_t>();
        return;
    }

    // Allowed types: INT, repeated INT
    if (numElements > mValues.size() || !checkPreviousValueType(INT) ||
        annotationType != BOOL_TYPE) {
        VLOG("Atom ID %d error while parseIsUidAnnotation()", mTagId);
        mValid = false;
        return;
    }

    bool isUid = readNextValue<uint8_t>();
    if (isUid) {
        mNumUidFields += numElements.value();
    }

    for (int i = 1; i <= numElements; i++) {
        mValues[mValues.size() - i].mAnnotations.setUidField(isUid);
    }
}

void LogEvent::parseTruncateTimestampAnnotation(uint8_t annotationType) {
    if (!mValues.empty() || annotationType != BOOL_TYPE) {
        VLOG("Atom ID %d error while parseTruncateTimestampAnnotation()", mTagId);
        mValid = false;
        return;
    }

    mTruncateTimestamp = readNextValue<uint8_t>();
}

void LogEvent::parsePrimaryFieldAnnotation(uint8_t annotationType,
                                           std::optional<uint8_t> numElements,
                                           std::optional<size_t> firstUidInChainIndex) {
    // Allowed types: all types except for attribution chains and repeated fields.
    if (mValues.empty() || annotationType != BOOL_TYPE || firstUidInChainIndex || numElements) {
        VLOG("Atom ID %d error while parsePrimaryFieldAnnotation()", mTagId);
        mValid = false;
        return;
    }

    const bool primaryField = readNextValue<uint8_t>();
    mValues[mValues.size() - 1].mAnnotations.setPrimaryField(primaryField);
}

void LogEvent::parsePrimaryFieldFirstUidAnnotation(uint8_t annotationType,
                                                   std::optional<size_t> firstUidInChainIndex) {
    // Allowed types: attribution chains
    if (mValues.empty() || annotationType != BOOL_TYPE || !firstUidInChainIndex) {
        VLOG("Atom ID %d error while parsePrimaryFieldFirstUidAnnotation()", mTagId);
        mValid = false;
        return;
    }

    if (mValues.size() < firstUidInChainIndex.value() + 1) {  // AttributionChain is empty.
        VLOG("Atom ID %d error while parsePrimaryFieldFirstUidAnnotation()", mTagId);
        mValid = false;
        android_errorWriteLog(0x534e4554, "174485572");
        return;
    }

    const bool primaryField = readNextValue<uint8_t>();
    mValues[firstUidInChainIndex.value()].mAnnotations.setPrimaryField(primaryField);
}

void LogEvent::parseExclusiveStateAnnotation(uint8_t annotationType,
                                             std::optional<uint8_t> numElements) {
    // Allowed types: BOOL
    if (mValues.empty() || annotationType != BOOL_TYPE || !checkPreviousValueType(INT) ||
        numElements) {
        VLOG("Atom ID %d error while parseExclusiveStateAnnotation()", mTagId);
        mValid = false;
        return;
    }

    const bool exclusiveState = readNextValue<uint8_t>();
    mExclusiveStateFieldIndex = mValues.size() - 1;
    mValues[getExclusiveStateFieldIndex().value()].mAnnotations.setExclusiveState(exclusiveState);
}

void LogEvent::parseTriggerStateResetAnnotation(uint8_t annotationType,
                                                std::optional<uint8_t> numElements) {
    // Allowed types: INT
    if (mValues.empty() || annotationType != INT32_TYPE || !checkPreviousValueType(INT) ||
        numElements) {
        VLOG("Atom ID %d error while parseTriggerStateResetAnnotation()", mTagId);
        mValid = false;
        return;
    }

    mResetState = readNextValue<int32_t>();
}

void LogEvent::parseStateNestedAnnotation(uint8_t annotationType,
                                          std::optional<uint8_t> numElements) {
    // Allowed types: BOOL
    if (mValues.empty() || annotationType != BOOL_TYPE || !checkPreviousValueType(INT) ||
        numElements) {
        VLOG("Atom ID %d error while parseStateNestedAnnotation()", mTagId);
        mValid = false;
        return;
    }

    bool nested = readNextValue<uint8_t>();
    mValues[mValues.size() - 1].mAnnotations.setNested(nested);
}

void LogEvent::parseRestrictionCategoryAnnotation(uint8_t annotationType) {
    // Allowed types: INT, field value should be empty since this is atom-level annotation.
    if (!mValues.empty() || annotationType != INT32_TYPE) {
        mValid = false;
        return;
    }
    int value = readNextValue<int32_t>();
    // should be one of predefined category in StatsLog.java
    switch (value) {
        case ASTATSLOG_RESTRICTION_CATEGORY_DIAGNOSTIC:
        case ASTATSLOG_RESTRICTION_CATEGORY_SYSTEM_INTELLIGENCE:
        case ASTATSLOG_RESTRICTION_CATEGORY_AUTHENTICATION:
        case ASTATSLOG_RESTRICTION_CATEGORY_FRAUD_AND_ABUSE:
            break;
        default:
            mValid = false;
            return;
    }
    mRestrictionCategory = static_cast<StatsdRestrictionCategory>(value);
    return;
}

void LogEvent::parseFieldRestrictionAnnotation(uint8_t annotationType) {
    // Allowed types: BOOL
    if (mValues.empty() || annotationType != BOOL_TYPE) {
        mValid = false;
        return;
    }
    // Read the value so that the rest of the event is correctly parsed
    // TODO: store the field annotations once the metrics need to parse them.
    readNextValue<uint8_t>();
    return;
}

// firstUidInChainIndex is a default parameter that is only needed when parsing
// annotations for attribution chains.
// numElements is a default param that is only needed when parsing annotations for repeated fields
void LogEvent::parseAnnotations(uint8_t numAnnotations, std::optional<uint8_t> numElements,
                                std::optional<size_t> firstUidInChainIndex) {
    for (uint8_t i = 0; i < numAnnotations; i++) {
        uint8_t annotationId = readNextValue<uint8_t>();
        uint8_t annotationType = readNextValue<uint8_t>();

        switch (annotationId) {
            case ASTATSLOG_ANNOTATION_ID_IS_UID:
                parseIsUidAnnotation(annotationType, numElements);
                break;
            case ASTATSLOG_ANNOTATION_ID_TRUNCATE_TIMESTAMP:
                parseTruncateTimestampAnnotation(annotationType);
                break;
            case ASTATSLOG_ANNOTATION_ID_PRIMARY_FIELD:
                parsePrimaryFieldAnnotation(annotationType, numElements, firstUidInChainIndex);
                break;
            case ASTATSLOG_ANNOTATION_ID_PRIMARY_FIELD_FIRST_UID:
                parsePrimaryFieldFirstUidAnnotation(annotationType, firstUidInChainIndex);
                break;
            case ASTATSLOG_ANNOTATION_ID_EXCLUSIVE_STATE:
                parseExclusiveStateAnnotation(annotationType, numElements);
                break;
            case ASTATSLOG_ANNOTATION_ID_TRIGGER_STATE_RESET:
                parseTriggerStateResetAnnotation(annotationType, numElements);
                break;
            case ASTATSLOG_ANNOTATION_ID_STATE_NESTED:
                parseStateNestedAnnotation(annotationType, numElements);
                break;
            case ASTATSLOG_ANNOTATION_ID_RESTRICTION_CATEGORY:
                if (isAtLeastU()) {
                    parseRestrictionCategoryAnnotation(annotationType);
                } else {
                    mValid = false;
                }
                break;
            // Currently field restrictions are ignored, so we parse but do not store them.
            case ASTATSLOG_ANNOTATION_ID_FIELD_RESTRICTION_PERIPHERAL_DEVICE_INFO:
            case ASTATSLOG_ANNOTATION_ID_FIELD_RESTRICTION_APP_USAGE:
            case ASTATSLOG_ANNOTATION_ID_FIELD_RESTRICTION_APP_ACTIVITY:
            case ASTATSLOG_ANNOTATION_ID_FIELD_RESTRICTION_HEALTH_CONNECT:
            case ASTATSLOG_ANNOTATION_ID_FIELD_RESTRICTION_ACCESSIBILITY:
            case ASTATSLOG_ANNOTATION_ID_FIELD_RESTRICTION_SYSTEM_SEARCH:
            case ASTATSLOG_ANNOTATION_ID_FIELD_RESTRICTION_USER_ENGAGEMENT:
            case ASTATSLOG_ANNOTATION_ID_FIELD_RESTRICTION_AMBIENT_SENSING:
            case ASTATSLOG_ANNOTATION_ID_FIELD_RESTRICTION_DEMOGRAPHIC_CLASSIFICATION:
                if (isAtLeastU()) {
                    parseFieldRestrictionAnnotation(annotationType);
                } else {
                    mValid = false;
                }
                break;
            default:
                VLOG("Atom ID %d error while parseAnnotations() - wrong annotationId(%d)", mTagId,
                     annotationId);
                mValid = false;
                return;
        }
    }
}

LogEvent::BodyBufferInfo LogEvent::parseHeader(const uint8_t* buf, size_t len) {
    BodyBufferInfo bodyInfo;

    mParsedHeaderOnly = true;

    mBuf = buf;
    mRemainingLen = (uint32_t)len;

    // Beginning of buffer is OBJECT_TYPE | NUM_FIELDS | TIMESTAMP | ATOM_ID
    uint8_t typeInfo = readNextValue<uint8_t>();
    if (getTypeId(typeInfo) != OBJECT_TYPE) {
        mValid = false;
        mBuf = nullptr;
        return bodyInfo;
    }

    uint8_t numElements = readNextValue<uint8_t>();
    if (numElements < 2 || numElements > INT8_MAX) {
        mValid = false;
        mBuf = nullptr;
        return bodyInfo;
    }

    typeInfo = readNextValue<uint8_t>();
    if (getTypeId(typeInfo) != INT64_TYPE) {
        mValid = false;
        mBuf = nullptr;
        return bodyInfo;
    }
    mElapsedTimestampNs = readNextValue<int64_t>();
    numElements--;

    typeInfo = readNextValue<uint8_t>();
    if (getTypeId(typeInfo) != INT32_TYPE) {
        mValid = false;
        mBuf = nullptr;
        return bodyInfo;
    }
    mTagId = readNextValue<int32_t>();
    numElements--;

    parseAnnotations(getNumAnnotations(typeInfo));  // atom-level annotations

    bodyInfo.numElements = numElements;
    bodyInfo.buffer = mBuf;
    bodyInfo.bufferSize = mRemainingLen;

    mBuf = nullptr;
    return bodyInfo;
}

bool LogEvent::parseBody(const BodyBufferInfo& bodyInfo) {
    mParsedHeaderOnly = false;

    mBuf = bodyInfo.buffer;
    mRemainingLen = (uint32_t)bodyInfo.bufferSize;

    int32_t pos[] = {1, 1, 1};
    bool last[] = {false, false, false};

    // While this number is not guaranteed to be correct due to repeated fields and
    // attribution chains, it still positively affects performance and reduces the number
    // of vector buffer reallocations.
    mValues.reserve(bodyInfo.numElements);

    for (pos[0] = 1; pos[0] <= bodyInfo.numElements && mValid; pos[0]++) {
        last[0] = (pos[0] == bodyInfo.numElements);

        uint8_t typeInfo = readNextValue<uint8_t>();
        uint8_t typeId = getTypeId(typeInfo);

        switch (typeId) {
            case BOOL_TYPE:
                parseBool(pos, /*depth=*/0, last, getNumAnnotations(typeInfo));
                break;
            case INT32_TYPE:
                parseInt32(pos, /*depth=*/0, last, getNumAnnotations(typeInfo));
                break;
            case INT64_TYPE:
                parseInt64(pos, /*depth=*/0, last, getNumAnnotations(typeInfo));
                break;
            case FLOAT_TYPE:
                parseFloat(pos, /*depth=*/0, last, getNumAnnotations(typeInfo));
                break;
            case BYTE_ARRAY_TYPE:
                parseByteArray(pos, /*depth=*/0, last, getNumAnnotations(typeInfo));
                break;
            case STRING_TYPE:
                parseString(pos, /*depth=*/0, last, getNumAnnotations(typeInfo));
                break;
            case KEY_VALUE_PAIRS_TYPE:
                parseKeyValuePairs(pos, /*depth=*/0, last, getNumAnnotations(typeInfo));
                break;
            case ATTRIBUTION_CHAIN_TYPE:
                parseAttributionChain(pos, /*depth=*/0, last, getNumAnnotations(typeInfo));
                break;
            case LIST_TYPE:
                parseArray(pos, /*depth=*/0, last, getNumAnnotations(typeInfo));
                break;
            case ERROR_TYPE:
                /* mErrorBitmask =*/readNextValue<int32_t>();
                mValid = false;
                break;
            default:
                mValid = false;
                break;
        }
    }

    if (mRemainingLen != 0) mValid = false;
    mBuf = nullptr;
    return mValid;
}

// This parsing logic is tied to the encoding scheme used in StatsEvent.java and
// stats_event.c
bool LogEvent::parseBuffer(const uint8_t* buf, size_t len) {
    BodyBufferInfo bodyInfo = parseHeader(buf, len);

    // emphasize intention to parse the body, however atom data could be incomplete
    // if header/body parsing was failed due to invalid buffer content for example
    mParsedHeaderOnly = false;

    // early termination if header is invalid
    if (!mValid) {
        mBuf = nullptr;
        return false;
    }

    return parseBody(bodyInfo);
}

int64_t LogEvent::GetLong(size_t key, status_t* err) const {
    // TODO(b/110561208): encapsulate the magical operations in Field struct as static functions
    int field = getSimpleField(key);
    for (const auto& value : mValues) {
        if (value.mField.getField() == field) {
            if (value.mValue.getType() == LONG) {
                return value.mValue.long_value;
            } else if (value.mValue.getType() == INT) {
                return value.mValue.int_value;
            } else {
                *err = BAD_TYPE;
                return 0;
            }
        }
        if ((size_t)value.mField.getPosAtDepth(0) > key) {
            break;
        }
    }

    *err = BAD_INDEX;
    return 0;
}

int LogEvent::GetInt(size_t key, status_t* err) const {
    int field = getSimpleField(key);
    for (const auto& value : mValues) {
        if (value.mField.getField() == field) {
            if (value.mValue.getType() == INT) {
                return value.mValue.int_value;
            } else {
                *err = BAD_TYPE;
                return 0;
            }
        }
        if ((size_t)value.mField.getPosAtDepth(0) > key) {
            break;
        }
    }

    *err = BAD_INDEX;
    return 0;
}

const char* LogEvent::GetString(size_t key, status_t* err) const {
    int field = getSimpleField(key);
    for (const auto& value : mValues) {
        if (value.mField.getField() == field) {
            if (value.mValue.getType() == STRING) {
                return value.mValue.str_value.c_str();
            } else {
                *err = BAD_TYPE;
                return 0;
            }
        }
        if ((size_t)value.mField.getPosAtDepth(0) > key) {
            break;
        }
    }

    *err = BAD_INDEX;
    return NULL;
}

bool LogEvent::GetBool(size_t key, status_t* err) const {
    int field = getSimpleField(key);
    for (const auto& value : mValues) {
        if (value.mField.getField() == field) {
            if (value.mValue.getType() == INT) {
                return value.mValue.int_value != 0;
            } else if (value.mValue.getType() == LONG) {
                return value.mValue.long_value != 0;
            } else {
                *err = BAD_TYPE;
                return false;
            }
        }
        if ((size_t)value.mField.getPosAtDepth(0) > key) {
            break;
        }
    }

    *err = BAD_INDEX;
    return false;
}

float LogEvent::GetFloat(size_t key, status_t* err) const {
    int field = getSimpleField(key);
    for (const auto& value : mValues) {
        if (value.mField.getField() == field) {
            if (value.mValue.getType() == FLOAT) {
                return value.mValue.float_value;
            } else {
                *err = BAD_TYPE;
                return 0.0;
            }
        }
        if ((size_t)value.mField.getPosAtDepth(0) > key) {
            break;
        }
    }

    *err = BAD_INDEX;
    return 0.0;
}

std::vector<uint8_t> LogEvent::GetStorage(size_t key, status_t* err) const {
    int field = getSimpleField(key);
    for (const auto& value : mValues) {
        if (value.mField.getField() == field) {
            if (value.mValue.getType() == STORAGE) {
                return value.mValue.storage_value;
            } else {
                *err = BAD_TYPE;
                return vector<uint8_t>();
            }
        }
        if ((size_t)value.mField.getPosAtDepth(0) > key) {
            break;
        }
    }

    *err = BAD_INDEX;
    return vector<uint8_t>();
}

string LogEvent::ToString() const {
    string result;
    result += StringPrintf("{ uid(%d) %lld %lld (%d)", mLogUid, (long long)mLogdTimestampNs,
                           (long long)mElapsedTimestampNs, mTagId);
    string annotations;
    if (mTruncateTimestamp) {
        annotations = "TRUNCATE_TS";
    }
    if (mResetState != -1) {
        annotations += annotations.size() ? ", RESET_STATE" : "RESET_STATE";
    }
    if (annotations.size()) {
        result += " [" + annotations + "] ";
    }

    if (isParsedHeaderOnly()) {
        result += " ParsedHeaderOnly }";
        return result;
    }

    for (const auto& value : mValues) {
        result += StringPrintf("%#x", value.mField.getField()) + "->" + value.mValue.toString();
        result += value.mAnnotations.toString() + " ";
    }
    result += " }";
    return result;
}

void LogEvent::ToProto(ProtoOutputStream& protoOutput) const {
    writeFieldValueTreeToStream(mTagId, getValues(), &protoOutput);
}

bool LogEvent::hasAttributionChain(std::pair<size_t, size_t>* indexRange) const {
    if (!mAttributionChainStartIndex || !mAttributionChainEndIndex) {
        return false;
    }

    if (nullptr != indexRange) {
        indexRange->first = mAttributionChainStartIndex.value();
        indexRange->second = mAttributionChainEndIndex.value();
    }

    return true;
}

void writeExperimentIdsToProto(const std::vector<int64_t>& experimentIds,
                               std::vector<uint8_t>* protoOut) {
    ProtoOutputStream proto;
    for (const auto& expId : experimentIds) {
        proto.write(FIELD_TYPE_INT64 | FIELD_COUNT_REPEATED | FIELD_ID_EXPERIMENT_ID,
                    (long long)expId);
    }

    protoOut->resize(proto.size());
    size_t pos = 0;
    sp<ProtoReader> reader = proto.data();
    while (reader->readBuffer() != NULL) {
        size_t toRead = reader->currentToRead();
        std::memcpy(protoOut->data() + pos, reader->readBuffer(), toRead);
        pos += toRead;
        reader->move(toRead);
    }
}

}  // namespace statsd
}  // namespace os
}  // namespace android
