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

#include "chre/platform/shared/log_buffer_manager.h"

#include "chre/core/event_loop_manager.h"
#include "chre/platform/assert.h"
#include "chre/platform/shared/bt_snoop_log.h"
#include "chre/platform/shared/generated/host_messages_generated.h"
#include "chre/util/lock_guard.h"

void chrePlatformLogToBuffer(chreLogLevel chreLogLevel, const char *format,
                             ...) {
  va_list args;
  va_start(args, format);
  if (chre::LogBufferManagerSingleton::isInitialized()) {
    chre::LogBufferManagerSingleton::get()->logVa(chreLogLevel, format, args);
  }
  va_end(args);
}

void chrePlatformEncodedLogToBuffer(chreLogLevel level, const uint8_t *msg,
                                    size_t msgSize) {
  if (chre::LogBufferManagerSingleton::isInitialized()) {
    chre::LogBufferManagerSingleton::get()->logEncoded(level, msg, msgSize);
  }
}

void chrePlatformBtSnoopLog(BtSnoopDirection direction, const uint8_t *buffer,
                            size_t size) {
  chre::LogBufferManagerSingleton::get()->logBtSnoop(direction, buffer, size);
}

namespace chre {

using LogType = fbs::LogType;

void LogBufferManager::onLogsReady() {
  LockGuard<Mutex> lockGuard(mFlushLogsMutex);
  if (!mLogFlushToHostPending) {
    if (EventLoopManagerSingleton::isInitialized() &&
        EventLoopManagerSingleton::get()
            ->getEventLoop()
            .getPowerControlManager()
            .hostIsAwake()) {
      mLogFlushToHostPending = true;
      mSendLogsToHostCondition.notify_one();
    }
  } else {
    mLogsBecameReadyWhileFlushPending = true;
  }
}

void LogBufferManager::flushLogs() {
  onLogsReady();
}

void LogBufferManager::onLogsSentToHost(bool success) {
  LockGuard<Mutex> lockGuard(mFlushLogsMutex);
  onLogsSentToHostLocked(success);
}

void LogBufferManager::startSendLogsToHostLoop() {
  LockGuard<Mutex> lockGuard(mFlushLogsMutex);
  // TODO(b/181871430): Allow this loop to exit for certain platforms
  while (true) {
    while (!mLogFlushToHostPending) {
      mSendLogsToHostCondition.wait(mFlushLogsMutex);
    }
    bool logWasSent = false;
    if (EventLoopManagerSingleton::get()
            ->getEventLoop()
            .getPowerControlManager()
            .hostIsAwake()) {
      auto &hostCommsMgr =
          EventLoopManagerSingleton::get()->getHostCommsManager();
      preSecondaryBufferUse();
      if (mSecondaryLogBuffer.getBufferSize() == 0) {
        // TODO (b/184178045): Transfer logs into the secondary buffer from
        // primary if there is room.
        mPrimaryLogBuffer.transferTo(mSecondaryLogBuffer);
      }
      // If the primary buffer was not flushed to the secondary buffer then set
      // the flag that will cause sendLogsToHost to be run again after
      // onLogsSentToHost has been called and the secondary buffer has been
      // cleared out.
      if (mPrimaryLogBuffer.getBufferSize() > 0) {
        mLogsBecameReadyWhileFlushPending = true;
      }
      if (mSecondaryLogBuffer.getBufferSize() > 0) {
        mNumLogsDroppedTotal += mSecondaryLogBuffer.getNumLogsDropped();
        mFlushLogsMutex.unlock();
        hostCommsMgr.sendLogMessageV2(mSecondaryLogBuffer.getBufferData(),
                                      mSecondaryLogBuffer.getBufferSize(),
                                      mNumLogsDroppedTotal);
        logWasSent = true;
        mFlushLogsMutex.lock();
      }
    }
    if (!logWasSent) {
      onLogsSentToHostLocked(false);
    }
  }
}

void LogBufferManager::log(chreLogLevel logLevel, const char *formatStr, ...) {
  va_list args;
  va_start(args, formatStr);
  logVa(logLevel, formatStr, args);
  va_end(args);
}

uint32_t LogBufferManager::getTimestampMs() {
  uint64_t timeNs = SystemTime::getMonotonicTime().toRawNanoseconds();
  return static_cast<uint32_t>(timeNs / kOneMillisecondInNanoseconds);
}

void LogBufferManager::bufferOverflowGuard(size_t logSize, LogType type) {
  switch (type) {
    case LogType::STRING:
      logSize += LogBuffer::kStringLogOverhead;
      break;
    case LogType::TOKENIZED:
      logSize += LogBuffer::kTokenizedLogOffset;
      break;
    case LogType::BLUETOOTH:
      logSize += LogBuffer::kBtSnoopLogOffset;
      break;
    case LogType::NANOAPP_TOKENIZED:
      logSize += LogBuffer::kNanoappTokenizedLogOffset;
      break;
    default:
      CHRE_ASSERT_LOG(false, "Received unexpected log message type");
      break;
  }
  if (mPrimaryLogBuffer.logWouldCauseOverflow(logSize)) {
    LockGuard<Mutex> lockGuard(mFlushLogsMutex);
    if (!mLogFlushToHostPending) {
      preSecondaryBufferUse();
      mPrimaryLogBuffer.transferTo(mSecondaryLogBuffer);
    }
  }
}

void LogBufferManager::logVa(chreLogLevel logLevel, const char *formatStr,
                             va_list args) {
  // Copy the va_list before getting size from vsnprintf so that the next
  // argument that will be accessed in buffer.handleLogVa is the starting one.
  va_list getSizeArgs;
  va_copy(getSizeArgs, args);
  size_t logSize = vsnprintf(nullptr, 0, formatStr, getSizeArgs);
  va_end(getSizeArgs);
  bufferOverflowGuard(logSize, LogType::STRING);
  mPrimaryLogBuffer.handleLogVa(chreToLogBufferLogLevel(logLevel),
                                getTimestampMs(), formatStr, args);
}

void LogBufferManager::logBtSnoop(BtSnoopDirection direction,
                                  const uint8_t *buffer, size_t size) {
#ifdef CHRE_BLE_SUPPORT_ENABLED
  bufferOverflowGuard(size, LogType::BLUETOOTH);
  mPrimaryLogBuffer.handleBtLog(direction, getTimestampMs(), buffer, size);
#else
  UNUSED_VAR(direction);
  UNUSED_VAR(buffer);
  UNUSED_VAR(size);
#endif  // CHRE_BLE_SUPPORT_ENABLED
}

void LogBufferManager::logEncoded(chreLogLevel logLevel,
                                  const uint8_t *encodedLog,
                                  size_t encodedLogSize) {
  bufferOverflowGuard(encodedLogSize, LogType::TOKENIZED);
  mPrimaryLogBuffer.handleEncodedLog(chreToLogBufferLogLevel(logLevel),
                                     getTimestampMs(), encodedLog,
                                     encodedLogSize);
}

void LogBufferManager::logNanoappTokenized(chreLogLevel logLevel,
                                           uint16_t instanceId,
                                           const uint8_t *msg, size_t msgSize) {
  bufferOverflowGuard(msgSize, LogType::NANOAPP_TOKENIZED);
  mPrimaryLogBuffer.handleNanoappTokenizedLog(chreToLogBufferLogLevel(logLevel),
                                              getTimestampMs(), instanceId, msg,
                                              msgSize);
}

LogBufferLogLevel LogBufferManager::chreToLogBufferLogLevel(
    chreLogLevel chreLogLevel) {
  switch (chreLogLevel) {
    case CHRE_LOG_ERROR:
      return LogBufferLogLevel::ERROR;
    case CHRE_LOG_WARN:
      return LogBufferLogLevel::WARN;
    case CHRE_LOG_INFO:
      return LogBufferLogLevel::INFO;
    default:  // CHRE_LOG_DEBUG
      return LogBufferLogLevel::DEBUG;
  }
}

void LogBufferManager::onLogsSentToHostLocked(bool success) {
  if (success) {
    mSecondaryLogBuffer.reset();
  }
  // If there is a failure to send a log through do not try to send another
  // one to avoid an infinite loop occurring
  mLogFlushToHostPending = mLogsBecameReadyWhileFlushPending && success;
  mLogsBecameReadyWhileFlushPending = false;
  if (mLogFlushToHostPending) {
    mSendLogsToHostCondition.notify_one();
  }
}

//! Explicitly instantiate the EventLoopManagerSingleton to reduce codesize.
template class Singleton<LogBufferManager>;

}  // namespace chre
