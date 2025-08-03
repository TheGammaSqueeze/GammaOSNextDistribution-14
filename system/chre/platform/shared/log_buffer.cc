/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "chre/platform/shared/log_buffer.h"
#include "chre/platform/assert.h"
#include "chre/platform/shared/generated/host_messages_generated.h"
#include "chre/util/lock_guard.h"

#include <cstdarg>
#include <cstdio>

namespace chre {

using LogType = fbs::LogType;

LogBuffer::LogBuffer(LogBufferCallbackInterface *callback, void *buffer,
                     size_t bufferSize)
    : mBufferData(static_cast<uint8_t *>(buffer)),
      mBufferMaxSize(bufferSize),
      mCallback(callback) {
  CHRE_ASSERT(bufferSize >= kBufferMinSize);
}

void LogBuffer::handleLog(LogBufferLogLevel logLevel, uint32_t timestampMs,
                          const char *logFormat, ...) {
  va_list args;
  va_start(args, logFormat);
  handleLogVa(logLevel, timestampMs, logFormat, args);
  va_end(args);
}

void LogBuffer::handleLogVa(LogBufferLogLevel logLevel, uint32_t timestampMs,
                            const char *logFormat, va_list args) {
  char tempBuffer[kLogMaxSize];
  int logLenSigned = vsnprintf(tempBuffer, kLogMaxSize, logFormat, args);
  processLog(logLevel, timestampMs, tempBuffer, logLenSigned, LogType::STRING);
}

#ifdef CHRE_BLE_SUPPORT_ENABLED
void LogBuffer::handleBtLog(BtSnoopDirection direction, uint32_t timestampMs,
                            const uint8_t *buffer, size_t size) {
  if (size == 0) {
    return;
  }
  auto logLen = static_cast<uint8_t>(size);

  if (size < kLogMaxSize) {
    LockGuard<Mutex> lockGuard(mLock);

    static_assert(sizeof(LogType) == sizeof(uint8_t),
                  "LogType size is not equal to size of uint8_t");
    static_assert(sizeof(direction) == sizeof(uint8_t),
                  "BtSnoopDirection size is not equal to the size of uint8_t");
    uint8_t snoopLogDirection = static_cast<uint8_t>(direction);

    discardExcessOldLogsLocked(logLen + kBtSnoopLogOffset);

    // Set all BT logs to the CHRE_LOG_LEVEL_INFO.
    uint8_t metadata =
        setLogMetadata(LogType::BLUETOOTH, LogBufferLogLevel::INFO);

    copyVarToBuffer(&metadata);
    copyVarToBuffer(&timestampMs);
    copyVarToBuffer(&snoopLogDirection);
    copyVarToBuffer(&logLen);

    copyToBuffer(logLen, buffer);
  } else {
    // Cannot truncate a BT event. Log a failure message instead.
    constexpr char kBtSnoopLogGenericErrorMsg[] =
        "Bt Snoop log message too large";
    static_assert(
        sizeof(kBtSnoopLogGenericErrorMsg) <= kLogMaxSize,
        "Error meessage size needs to be smaller than max log length");
    logLen = static_cast<uint8_t>(sizeof(kBtSnoopLogGenericErrorMsg));
    copyLogToBuffer(LogBufferLogLevel::INFO, timestampMs,
                    kBtSnoopLogGenericErrorMsg, logLen, LogType::BLUETOOTH);
  }
  dispatch();
}
#endif  // CHRE_BLE_SUPPORT_ENABLED

void LogBuffer::handleEncodedLog(LogBufferLogLevel logLevel,
                                 uint32_t timestampMs, const uint8_t *log,
                                 size_t logSize) {
  processLog(logLevel, timestampMs, log, logSize, LogType::TOKENIZED);
}

void LogBuffer::handleNanoappTokenizedLog(LogBufferLogLevel logLevel,
                                          uint32_t timestampMs,
                                          uint16_t instanceId,
                                          const uint8_t *log, size_t logSize) {
  processLog(logLevel, timestampMs, log, logSize, LogType::NANOAPP_TOKENIZED,
             instanceId);
}

size_t LogBuffer::copyLogs(void *destination, size_t size,
                           size_t *numLogsDropped) {
  LockGuard<Mutex> lock(mLock);
  return copyLogsLocked(destination, size, numLogsDropped);
}

bool LogBuffer::logWouldCauseOverflow(size_t logSize) {
  LockGuard<Mutex> lock(mLock);
  return (mBufferDataSize + logSize + kLogDataOffset > mBufferMaxSize);
}

void LogBuffer::transferTo(LogBuffer &buffer) {
  LockGuard<Mutex> lockGuardOther(buffer.mLock);
  size_t numLogsDropped;
  size_t bytesCopied;
  {
    LockGuard<Mutex> lockGuardThis(mLock);
    // The buffer being transferred to should be as big or bigger.
    CHRE_ASSERT(buffer.mBufferMaxSize >= mBufferMaxSize);

    buffer.resetLocked();

    bytesCopied = copyLogsLocked(buffer.mBufferData, buffer.mBufferMaxSize,
                                 &numLogsDropped);

    resetLocked();
  }
  buffer.mBufferDataTailIndex = bytesCopied % buffer.mBufferMaxSize;
  buffer.mBufferDataSize = bytesCopied;
  buffer.mNumLogsDropped = numLogsDropped;
}

void LogBuffer::updateNotificationSetting(LogBufferNotificationSetting setting,
                                          size_t thresholdBytes) {
  LockGuard<Mutex> lock(mLock);

  mNotificationSetting = setting;
  mNotificationThresholdBytes = thresholdBytes;
}

void LogBuffer::reset() {
  LockGuard<Mutex> lock(mLock);
  resetLocked();
}

const uint8_t *LogBuffer::getBufferData() {
  return mBufferData;
}

size_t LogBuffer::getBufferSize() {
  LockGuard<Mutex> lockGuard(mLock);
  return mBufferDataSize;
}

size_t LogBuffer::getNumLogsDropped() {
  LockGuard<Mutex> lockGuard(mLock);
  return mNumLogsDropped;
}

size_t LogBuffer::incrementAndModByBufferMaxSize(size_t originalVal,
                                                 size_t incrementBy) const {
  return (originalVal + incrementBy) % mBufferMaxSize;
}

void LogBuffer::copyToBuffer(size_t size, const void *source) {
  const uint8_t *sourceBytes = static_cast<const uint8_t *>(source);
  if (mBufferDataTailIndex + size > mBufferMaxSize) {
    size_t firstSize = mBufferMaxSize - mBufferDataTailIndex;
    size_t secondSize = size - firstSize;
    memcpy(&mBufferData[mBufferDataTailIndex], sourceBytes, firstSize);
    memcpy(mBufferData, &sourceBytes[firstSize], secondSize);
  } else {
    memcpy(&mBufferData[mBufferDataTailIndex], sourceBytes, size);
  }
  mBufferDataSize += size;
  mBufferDataTailIndex =
      incrementAndModByBufferMaxSize(mBufferDataTailIndex, size);
}

void LogBuffer::copyFromBuffer(size_t size, void *destination) {
  uint8_t *destinationBytes = static_cast<uint8_t *>(destination);
  if (mBufferDataHeadIndex + size > mBufferMaxSize) {
    size_t firstSize = mBufferMaxSize - mBufferDataHeadIndex;
    size_t secondSize = size - firstSize;
    memcpy(destinationBytes, &mBufferData[mBufferDataHeadIndex], firstSize);
    memcpy(&destinationBytes[firstSize], mBufferData, secondSize);
  } else {
    memcpy(destinationBytes, &mBufferData[mBufferDataHeadIndex], size);
  }
  mBufferDataSize -= size;
  mBufferDataHeadIndex =
      incrementAndModByBufferMaxSize(mBufferDataHeadIndex, size);
}

size_t LogBuffer::copyLogsLocked(void *destination, size_t size,
                                 size_t *numLogsDropped) {
  size_t copySize = 0;

  if (size != 0 && destination != nullptr && mBufferDataSize != 0) {
    if (size >= mBufferDataSize) {
      copySize = mBufferDataSize;
    } else {
      size_t logSize;
      size_t logStartIndex = getNextLogIndex(mBufferDataHeadIndex, &logSize);
      while (copySize + logSize <= size &&
             copySize + logSize <= mBufferDataSize) {
        copySize += logSize;
        logStartIndex = getNextLogIndex(logStartIndex, &logSize);
      }
    }
    copyFromBuffer(copySize, destination);
  }

  *numLogsDropped = mNumLogsDropped;

  return copySize;
}

void LogBuffer::resetLocked() {
  mBufferDataHeadIndex = 0;
  mBufferDataTailIndex = 0;
  mBufferDataSize = 0;
  mNumLogsDropped = 0;
}

size_t LogBuffer::getNextLogIndex(size_t startingIndex, size_t *logSize) {
  size_t logDataStartIndex =
      incrementAndModByBufferMaxSize(startingIndex, kLogDataOffset);
  LogType type = getLogTypeFromMetadata(mBufferData[startingIndex]);
  size_t logDataSize = getLogDataLength(logDataStartIndex, type);
  *logSize = kLogDataOffset + logDataSize;
  return incrementAndModByBufferMaxSize(startingIndex, *logSize);
}

size_t LogBuffer::getLogDataLength(size_t startingIndex, LogType type) {
  size_t currentIndex = startingIndex;
  size_t numBytes = kLogMaxSize;

  switch (type) {
    case LogType::STRING:
      for (size_t i = 0; i < kLogMaxSize; i++) {
        if (mBufferData[currentIndex] == '\0') {
          // +1 to include the null terminator
          numBytes = i + 1;
          break;
        }
        currentIndex = incrementAndModByBufferMaxSize(currentIndex, 1);
      }
      break;

    case LogType::TOKENIZED:
      numBytes = mBufferData[startingIndex] + kTokenizedLogOffset;
      break;

    case LogType::BLUETOOTH:
      // +1 to account for the bt snoop direction.
      currentIndex = incrementAndModByBufferMaxSize(startingIndex, 1);
      numBytes = mBufferData[currentIndex] + kBtSnoopLogOffset;
      break;

    case LogType::NANOAPP_TOKENIZED:
      // +2 to account for the uint16_t instance ID.
      currentIndex = incrementAndModByBufferMaxSize(startingIndex, 2);
      numBytes = mBufferData[currentIndex] + kNanoappTokenizedLogOffset;
      break;

    default:
      CHRE_ASSERT_LOG(false, "Received unexpected log message type");
      break;
  }

  return numBytes;
}

void LogBuffer::processLog(LogBufferLogLevel logLevel, uint32_t timestampMs,
                           const void *logBuffer, size_t logSize, LogType type,
                           uint16_t instanceId) {
  if (logSize == 0) {
    return;
  }
  auto logLen = static_cast<uint8_t>(logSize);

  constexpr char kTokenizedLogGenericErrorMsg[] =
      "Tokenized log message too large";

  // For tokenized logs, need to leave space for the message size offset. For
  // string logs, need to leave 1 byte for the null terminator at the end.
  if (type == LogType::STRING && logSize >= kLogMaxSize - 1) {
    // String logs longer than kLogMaxSize - 1 will be truncated.
    logLen = static_cast<uint8_t>(kLogMaxSize - 1);
  } else if (tokenizedLogExceedsMaxSize(type, logSize)) {
    // There is no way of decoding an encoded message if we truncate it, so
    // we do the next best thing and try to log a generic failure message
    // reusing the logbuffer for as much as we can. Note that we also need
    // flip the encoding flag for proper decoding by the host log message
    // parser.
    static_assert(
        sizeof(kTokenizedLogGenericErrorMsg) <= kLogMaxSize - 1,
        "Error meessage size needs to be smaller than max log length");
    logBuffer = kTokenizedLogGenericErrorMsg;
    logLen = static_cast<uint8_t>(sizeof(kTokenizedLogGenericErrorMsg));
    type = LogType::STRING;
  }

  copyLogToBuffer(logLevel, timestampMs, logBuffer, logLen, type, instanceId);
  dispatch();
}

void LogBuffer::copyLogToBuffer(LogBufferLogLevel level, uint32_t timestampMs,
                                const void *logBuffer, uint8_t logLen,
                                LogType type, uint16_t instanceId) {
  LockGuard<Mutex> lockGuard(mLock);
  if (type == LogType::NANOAPP_TOKENIZED) {
    discardExcessOldLogsLocked(logLen + kNanoappTokenizedLogOffset);
  } else {
    // For STRING logs, add 1 byte for null terminator. For TOKENIZED logs, add
    // 1 byte for the size metadata added to the message.
    discardExcessOldLogsLocked(logLen + 1);
  }
  encodeAndCopyLogLocked(level, timestampMs, logBuffer, logLen, type,
                         instanceId);
}

void LogBuffer::discardExcessOldLogsLocked(uint8_t currentLogLen) {
  size_t totalLogSize = kLogDataOffset + currentLogLen;
  while (mBufferDataSize + totalLogSize > mBufferMaxSize) {
    mNumLogsDropped++;
    size_t logSize;
    mBufferDataHeadIndex = getNextLogIndex(mBufferDataHeadIndex, &logSize);
    mBufferDataSize -= logSize;
  }
}

void LogBuffer::encodeAndCopyLogLocked(LogBufferLogLevel level,
                                       uint32_t timestampMs,
                                       const void *logBuffer, uint8_t logLen,
                                       LogType type, uint16_t instanceId) {
  uint8_t metadata = setLogMetadata(type, level);

  copyVarToBuffer(&metadata);
  copyVarToBuffer(&timestampMs);

  if (type == LogType::NANOAPP_TOKENIZED) {
    copyVarToBuffer(&instanceId);
    copyVarToBuffer(&logLen);
  } else if (type == LogType::TOKENIZED) {
    copyVarToBuffer(&logLen);
  }
  copyToBuffer(logLen, logBuffer);
  if (type == LogType::STRING) {
    copyToBuffer(1, reinterpret_cast<const void *>("\0"));
  }
}

void LogBuffer::dispatch() {
  if (mCallback != nullptr) {
    switch (mNotificationSetting) {
      case LogBufferNotificationSetting::ALWAYS: {
        mCallback->onLogsReady();
        break;
      }
      case LogBufferNotificationSetting::NEVER: {
        break;
      }
      case LogBufferNotificationSetting::THRESHOLD: {
        if (mBufferDataSize > mNotificationThresholdBytes) {
          mCallback->onLogsReady();
        }
        break;
      }
    }
  }
}

LogType LogBuffer::getLogTypeFromMetadata(uint8_t metadata) {
  LogType type;
  if (((metadata & 0x20) != 0) && ((metadata & 0x10) != 0)) {
    type = LogType::NANOAPP_TOKENIZED;
  } else if ((metadata & 0x20) != 0) {
    type = LogType::BLUETOOTH;
  } else if ((metadata & 0x10) != 0) {
    type = LogType::TOKENIZED;
  } else {
    type = LogType::STRING;
  }
  return type;
}

uint8_t LogBuffer::setLogMetadata(LogType type, LogBufferLogLevel logLevel) {
  return static_cast<uint8_t>(type) << 4 | static_cast<uint8_t>(logLevel);
}

bool LogBuffer::tokenizedLogExceedsMaxSize(LogType type, size_t size) {
  return (type == LogType::TOKENIZED &&
          size >= kLogMaxSize - kTokenizedLogOffset) ||
         (type == LogType::NANOAPP_TOKENIZED &&
          size >= kLogMaxSize - kNanoappTokenizedLogOffset);
}

}  // namespace chre
