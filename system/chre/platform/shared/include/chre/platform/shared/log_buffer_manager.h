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

#ifndef CHRE_PLATFORM_LOG_BUFFER_MANAGER_BUFFER_H_
#define CHRE_PLATFORM_LOG_BUFFER_MANAGER_BUFFER_H_

#include "chre/platform/assert.h"
#include "chre/platform/condition_variable.h"
#include "chre/platform/mutex.h"
#include "chre/platform/shared/bt_snoop_log.h"
#include "chre/platform/shared/generated/host_messages_generated.h"
#include "chre/platform/shared/log_buffer.h"
#include "chre/util/singleton.h"
#include "chre_api/chre/re.h"

#ifndef CHRE_LOG_BUFFER_DATA_SIZE
#define CHRE_LOG_BUFFER_DATA_SIZE CHRE_MESSAGE_TO_HOST_MAX_SIZE
#endif

namespace chre {

using LogType = fbs::LogType;

/**
 * A log buffer manager that platform code can use to buffer logs when the host
 * is not available and then send them off when the host becomes available. Uses
 * the LogBuffer API to buffer the logs in memory.
 *
 * The manager uses two LogBuffer objects to handle flushing logs to the host at
 * the same time as handling more incoming logs. Incoming logs are always put
 * into the primary buffer first. The secondary buffer takes all the logs
 * currently in the primary buffer before the logs are sent off to the host
 * because the secondary buffer is the memory location passed to the
 * HostLink::sendLogs API. Logs are also flushed to the secondary buffer from
 * the primary buffer when the primary buffer fills up.
 *
 * When implementing this class in platform code. Use the singleton defined
 * after this class and pass logs to the log or logVa methods. Initialize the
 * singleton before using it. Call the onLogsSentToHost callback immediately
 * after sending logs to the host.
 */
class LogBufferManager : public LogBufferCallbackInterface {
 public:
  LogBufferManager(uint8_t *primaryBufferData, uint8_t *secondaryBufferData,
                   size_t bufferSize)
      : mPrimaryLogBuffer(this, primaryBufferData, bufferSize),
        mSecondaryLogBuffer(nullptr /* callback */, secondaryBufferData,
                            bufferSize) {}

  ~LogBufferManager() = default;

  /**
   * Logs message with printf-style arguments. No trailing newline is required
   * for this method.
   */
  void log(chreLogLevel logLevel, const char *formatStr, ...);

  /**
   * Similar as log() but with a log buffer and a log size argument instead
   * of a printf-style arguments.
   *
   * @param logs Pointer to the buffer containing the encoded log message.
   * @param logSize Size of the encoded logs.
   */
  void logEncoded(chreLogLevel logLevel, const uint8_t *encodedLog,
                  size_t encodedLogSize);

  /**
   * Log via the pigweed tokenized logging format for nanoapps. The log message
   * message is tokenized and will be decoded into printf style strings at the
   * receiver.
   *
   * @param level Logging level.
   * @param instanceId The instance ID of the nanoapp which sends the log.
   * @param msg A byte buffer containing the tokenized log message.
   * @param msgSize Size of the tokenized log message buffer.
   */
  void logNanoappTokenized(chreLogLevel logLevel, uint16_t instanceID,
                           const uint8_t *msg, size_t msgSize);

  /**
   * Logs message with printf-style arguments. No trailing newline is required
   * for this method. Uses va_list parameter instead of ...
   */
  void logVa(chreLogLevel logLevel, const char *formatStr, va_list args);

  /**
   * Logs BT commands and events. These logs will not be displayed on logcat.
   * The BT events will be handled with a bt snoop log parser.
   */
  void logBtSnoop(BtSnoopDirection direction, const uint8_t *buffer,
                  size_t size);

  /**
   * Overrides required method from LogBufferCallbackInterface.
   */
  void onLogsReady() final;

  /**
   * Flush any logs that might be in the default log buffer.
   */
  void flushLogs();

  /**
   * The platform code should call this method after the logs have been sent to
   * the host to signal that more logs can be sent to the host when ready. The
   * caller must indicate whether the platform could successfully deliver the
   * logs as well.
   *
   * @param success true if the logs were sent through to host successfully.
   */
  void onLogsSentToHost(bool success);

  /**
   * Loop that waits on the conditions for sending logs to host to be met and
   * sends the logs to the host if so. This method never exits. Should be called
   * by a platform thread.
   */
  void startSendLogsToHostLoop();

 private:
  /*
   * @return The LogBuffer log level for the given CHRE log level.
   */
  LogBufferLogLevel chreToLogBufferLogLevel(chreLogLevel chreLogLevel);

  /**
   * Perform any setup needed by the plaform before the secondary buffer is
   * used.
   *
   * Implemented by the platform.
   */
  void preSecondaryBufferUse() const;

  /**
   * Same as onLogsSentToHost, but without locking. The calling code should have
   * the flush logs mutex locked before calling this method.
   *
   * @param success true if the logs were successfully delivered to the host.
   */
  void onLogsSentToHostLocked(bool success);

  uint32_t getTimestampMs();

  void bufferOverflowGuard(size_t logSize, LogType type);

  LogBuffer mPrimaryLogBuffer;
  LogBuffer mSecondaryLogBuffer;

  size_t mNumLogsDroppedTotal = 0;

  ConditionVariable mSendLogsToHostCondition;
  bool mLogFlushToHostPending = false;
  bool mLogsBecameReadyWhileFlushPending = false;
  Mutex mFlushLogsMutex;
};

//! Provides an alias to the LogBufferManager singleton.
typedef Singleton<LogBufferManager> LogBufferManagerSingleton;

//! Extern the explicit LogBufferManagerSingleton to force non-inline calls.
//! This reduces codesize considerably.
extern template class Singleton<LogBufferManager>;

}  // namespace chre

#endif  // CHRE_PLATFORM_LOG_BUFFER_MANAGER_H_
