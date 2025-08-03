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

#ifndef CHRE_PLATFORM_SHARED_LOG_BUFFER_H_
#define CHRE_PLATFORM_SHARED_LOG_BUFFER_H_

#include <cinttypes>
#include <cstdarg>
#include <cstring>

#include "chre/core/event.h"
#include "chre/platform/mutex.h"
#include "chre/platform/shared/bt_snoop_log.h"
#include "chre/platform/shared/generated/host_messages_generated.h"

namespace chre {

using LogType = fbs::LogType;

/**
 * Values that represent a preferred setting for when the LogBuffer should
 * notify the platform that logs are ready to be copied.
 *
 * ALWAYS - The LogBuffer should immediately notify the platform when a new log
 *          is received.
 * NEVER -  The LogBuffer should never alert the platform that logs have been
 *          received. It is up to the platform to decide when to copy logs out.
 * THRESHOLD - The LogBuffer should notify the platform when a certain thresold
 *             of memory has been allocated for logs in the buffer.
 */
enum class LogBufferNotificationSetting : uint8_t { ALWAYS, NEVER, THRESHOLD };

/**
 * The log level options for logs stored in a log buffer.
 */
enum class LogBufferLogLevel : uint8_t {
  UNKNOWN,
  ERROR,
  WARN,
  INFO,
  DEBUG,
  VERBOSE
};

// Forward declaration for LogBufferCallbackInterface.
class LogBuffer;

/**
 * Callback objects that are implemented by the platform code and passed to the
 * log buffer instances are notified of changes in the state of the buffer
 * through this callback interface.
 */
class LogBufferCallbackInterface {
 public:
  virtual ~LogBufferCallbackInterface() {}

  /**
   * Notify the platform code that is using the buffer manager that it should
   * call copyLogs because the buffer internal state has changed to suit the
   * requirements for alerting the platform that logs are ready to be copied
   * out of buffer.
   */
  virtual void onLogsReady() = 0;
};

/**
 * The class responsible for batching logs in memory until the notification
 * callback is triggered and the platform copies log data out of the buffer.
 */
class LogBuffer {
 public:
  //! The max size of a single log entry which must fit in a single byte.
  static constexpr size_t kLogMaxSize = UINT8_MAX;

  //! The number of bytes in a log entry of the buffer before the log data is
  //! encountered. This is determined by the size of the 'header' in the log
  //! message.
  static constexpr size_t kLogDataOffset = 5;

  //! The number of overhead bytes in a printf style string entry. This value
  //! indicates the size of the null terminator appended to the end of each log.
  static constexpr size_t kStringLogOverhead = 1;

  //! The number of bytes in a tokenized log entry of the buffer after the
  //! 'header' and before the tokenized log data is encountered. The value
  //! indicate the size of the uint8_t logSize field.
  static constexpr size_t kTokenizedLogOffset = 1;

  //! The number of bytes in a bt snoop log entry of the buffer after the
  //! 'header' and before the bt snoop log data is encountered. The value
  //! indicate the size of the uint8_t size field and the BtSnoopDirection
  //! field.
  static constexpr size_t kBtSnoopLogOffset = 2;

  //! The number of bytes in a nanoapp tokenized log entry of the buffer after
  //! the 'header' and before the tokenized log data is encountered. The value
  //! accounts for the size of the uint8_t logSize field and the uint16_t
  //! instanceId field.
  static constexpr size_t kNanoappTokenizedLogOffset = 3;

  /**
   * @param callback The callback object that will receive notifications about
   *                 the state of the log buffer or nullptr if it is not needed.
   * @param buffer The buffer location that will store log data.
   *                    message.
   * @param bufferSize The number of bytes in the buffer. This value must be >
   *                   kBufferMinSize
   */
  LogBuffer(LogBufferCallbackInterface *callback, void *buffer,
            size_t bufferSize);

  /**
   * Buffer this log and possibly call on logs ready callback depending on the
   * notification setting in place.  The method is thread-safe and will ensure
   * that logs are buffered in a FIFO ordering. If the buffer is full then drop
   * the oldest log.
   *
   * @param logLevel The log level.
   * @param timestampMs The timestamp that the log was collected as in
   *                    milliseconds. Monotonically increasing and in
   *                    milliseconds since boot.
   * @param logFormat The ASCII log format that is buffered.
   * @param ... The variable length set of parameters to print into the
   *            logFormat string.
   */
  void handleLog(LogBufferLogLevel logLevel, uint32_t timestampMs,
                 const char *logFormat, ...);

  /**
   * Adds a string log to the buffer with a va_list argument and determines
   * whether to send log buffer to host.
   *
   * @param args The arguments in a va_list type.
   */
  void handleLogVa(LogBufferLogLevel logLevel, uint32_t timestampMs,
                   const char *logFormat, va_list args);

  /**
   * Adds a tokenized log to the buffer and determines whether to send log
   * buffer to host.
   *
   * @param logs Pointer to the buffer containing the encoded log message.
   * @param logSize Size of the encoded logs.
   */
  void handleEncodedLog(LogBufferLogLevel logLevel, uint32_t timestampMs,
                        const uint8_t *log, size_t logSize);

  /**
   * Adds a nanoapp tokenized log to the buffer and determines whether to send
   * log buffer to host.
   *
   * @param instanceId The instance ID of the nanoapp which sends the log
   * message.
   * @param logs Pointer to the buffer containing the encoded log message.
   * @param logSize Size of the encoded logs.
   */
  void handleNanoappTokenizedLog(LogBufferLogLevel logLevel,
                                 uint32_t timestampMs, uint16_t instanceId,
                                 const uint8_t *log, size_t logSize);

#ifdef CHRE_BLE_SUPPORT_ENABLED
  /**
   * Similar to handleLog but buffer a BT snoop log.
   *
   * @param direction Direction of the BT snoop log.
   * @param timestampMs The timestamp that the log was collected as in
   *                    milliseconds. Monotonically increasing and in
   *                    milliseconds since boot.
   * @param buffer Pointer to the buffer location containing the BT snoop log.
   * @param size Size of the BT snoop log.
   */
  void handleBtLog(BtSnoopDirection direction, uint32_t timestampMs,
                   const uint8_t *buffer, size_t size);
#endif  // CHRE_BLE_SUPPORT_ENABLED

  // TODO(b/179786399): Remove the copyLogs method when the LogBufferManager is
  // refactored to no longer use it.
  /**
   * Copy out as many logs as will fit into destination buffer as they are
   * formatted internally. The memory where the logs were stored will be freed.
   * This method is thread-safe and will ensure that copyLogs will only copy
   * out the logs in a FIFO ordering.
   *
   * @param destination Pointer to the destination memory address.
   * @param size The max number of bytes to copy.
   * @param numLogsDropped Non-null pointer which will be set to the number of
   * logs dropped since CHRE started.
   *
   * @return The number of bytes copied from buffer to destination which may be
   *         less than size because partial logs are not copied into
   *         destination or the number of bytes left in the buffer is less than
   *         size.
   */
  size_t copyLogs(void *destination, size_t size, size_t *numLogsDropped);

  /**
   *
   * @param logSize The size of the log payload, including overhead like
   * metadata, null terminator, etc.
   * @return true if log would cause an overflow of the buffer and would
   * overwrite a log if it was pushed onto the buffer.
   */
  bool logWouldCauseOverflow(size_t logSize);

  /**
   * Transfer all data from one log buffer to another. The destination log
   * buffer must have equal or greater capacity than this buffer. The
   * otherBuffer will be reset prior to this buffer's data being transferred to
   * it and after the transfer this buffer will be reset. This method is
   * thread-safe and will ensure that logs are kept in FIFO ordering during a
   * transfer operation.
   *
   * @param otherBuffer The log buffer that is transferred to.
   */
  void transferTo(LogBuffer &otherBuffer);

  /**
   * Update the current log buffer notification setting which will determine
   * when the platform is notified to copy logs out of the buffer. Thread-safe.
   *
   * @param setting The new notification setting value.
   * @param thresholdBytes If the nofification setting is THRESHOLD, then if
   *                       the buffer allocates this many bytes the notification
   *                       callback will be triggerd, otherwise this parameter
   *                       is ignored.
   */
  void updateNotificationSetting(LogBufferNotificationSetting setting,
                                 size_t thresholdBytes = 0);

  /**
   * Thread safe.
   *
   * Empty out the log entries currently in the buffer and reset the number of
   * logs dropped.
   */
  void reset();

  /**
   * The data inside the buffer that is returned may be altered by
   * another thread so it is up to the calling code to ensure that race
   * conditions do not occur on writes to the data.
   *
   * @return The pointer to the underlying data buffer.
   */
  const uint8_t *getBufferData();

  /**
   * Thread safe.
   *
   * @return The current buffer size.
   */
  size_t getBufferSize();

  /**
   *
   * Thread safe.
   *
   * @return The number of logs dropped since the object was last reset or
   * instantiated.
   */
  size_t getNumLogsDropped();

  /**
   * @param startingIndex The index to start from.
   * @param type. The type of the log. See host_message.fbs.
   * @return The length of the data portion of a log. If the end of log was not
   * found at most kLogMaxSize bytes away from the startingIndex then
   * kLogMaxSize is returned.
   */
  size_t getLogDataLength(size_t startingIndex, LogType type);

 private:
  /**
   * Increment the value and take the modulus of the max size of the buffer.
   *
   * @param originalVal The original value to increment and mod.
   * @param incrementBy The amount to increment by.
   * @return The final value after incrementing and modulus.
   */
  size_t incrementAndModByBufferMaxSize(size_t originalVal,
                                        size_t incrementBy) const;

  /**
   * Copy from the source memory location to the buffer data ensuring that
   * the copy wraps around the buffer data if needed.
   *
   * @param size The number of bytes to copy into the buffer.
   * @param source The memory location to copy from.
   */
  void copyToBuffer(size_t size, const void *source);

  template <typename Type>
  void copyVarToBuffer(const Type *var) {
    copyToBuffer(sizeof(Type), var);
  }

  /**
   * Copy from the buffer data to a destination memory location ensuring that
   * the copy wraps around the buffer data if needed.
   *
   * @param size The number of bytes to copy into the buffer.
   * @param destination The memory location to copy to.
   */
  void copyFromBuffer(size_t size, void *destination);

  /**
   * Same as copyLogs method but requires that a lock already be held.
   */
  size_t copyLogsLocked(void *destination, size_t size, size_t *numLogsDropped);

  /**
   * Same as reset method but requires that a lock already be held.
   */
  void resetLocked();

  /**
   * Get next index indicating the start of a log entry from the starting
   * index of a previous log entry.
   *
   * @param startingIndex The starting index given.
   * @param logSize Non-null pointer that will be set to the size of the current
   *        log message.
   * @return The next starting log index.
   */
  size_t getNextLogIndex(size_t startingIndex, size_t *logSize);

  /**
   * Encode the received log message (if tokenization or similar encoding
   * is used) and dispatch it.
   *
   * @param instanceId The instance ID of the nanoapp which sends the log
   * message. Defaulted to kSystemInstanceId if the log is sent from the CHRE
   * system or if the nanoapp which sends the message is not tokenized enabled.
   */
  void processLog(LogBufferLogLevel logLevel, uint32_t timestampMs,
                  const void *log, size_t logSize, LogType type,
                  uint16_t instanceId = kSystemInstanceId);

  /**
   * First ensure that there's enough space for the log by discarding older
   * logs, then encode and copy this log into the internal log buffer.
   */
  void copyLogToBuffer(LogBufferLogLevel level, uint32_t timestampMs,
                       const void *logBuffer, uint8_t logLen, LogType type,
                       uint16_t instanceId = kSystemInstanceId);

  /**
   * Invalidate memory allocated for log at head while the buffer is greater
   * than max size. This function must only be called with the log buffer mutex
   * locked.
   */
  void discardExcessOldLogsLocked(uint8_t currentLogLen);

  /**
   * Add an encoding header to the log message if the encoding param is true.
   * This function must only be called with the log buffer mutex locked.
   */
  void encodeAndCopyLogLocked(LogBufferLogLevel level, uint32_t timestampMs,
                              const void *logBuffer, uint8_t logLen,
                              LogType type, uint16_t instanceId);

  /**
   * Send ready to dispatch logs over, based on the current log notification
   * setting
   */
  void dispatch();

  /**
   * @param metadata The metadata of the log message.
   * @return The log type of the log message.
   */
  LogType getLogTypeFromMetadata(uint8_t metadata);

  /**
   * Set the upper nibble of the log metadata based on log type and log level.
   *
   * @param type The log type of the log message.
   * @param logLevel The log level of the log message.
   * @return The metadata of the log message.
   */
  uint8_t setLogMetadata(LogType type, LogBufferLogLevel logLevel);

  /**
   * @param type The log type of the log message.
   * @param size The size of the current log message.
   * @return True if the log exceeds max size allowed.
   */
  bool tokenizedLogExceedsMaxSize(LogType type, size_t size);

  /**
   * The buffer data is stored in the format
   *
   * [ metadata (1B) , timestamp (4B), data (dataLenB) ]
   *
   * The upper nibble of the metadata indicates if an encoding scheme was used,
   * while the lower nibble indicates the severity level of this log.
   *
   * The data buffer is encoded as follows:
   *  - In the case of encoded logs, the first byte indicates the number of
   *    actual log data bytes that follow. These are typically used as
   *    information for the decoder, which decodes the log data from a 1 byte
   *    offset.
   *  - When logs are unencoded, the data buffer can be interpreted as a
   *    NULL terminated C-style string (pass to string manipulation functions,
   *    get size from strlen(), etc.).
   *
   * This pattern is repeated as many times as there is log entries in the
   * buffer.
   *
   * Since dataLength cannot be greater than uint8_t the max size of the data
   * portion can be max 255.
   */
  uint8_t *const mBufferData;

  // TODO(b/170870354): Create a cirular buffer class to reuse this concept in
  // other parts of CHRE
  //! The buffer data head index
  size_t mBufferDataHeadIndex = 0;
  //! The buffer data tail index
  size_t mBufferDataTailIndex = 0;
  //! The current size of the data buffer
  size_t mBufferDataSize = 0;
  //! The buffer max size
  size_t mBufferMaxSize;
  //! The number of logs that have been dropped
  size_t mNumLogsDropped = 0;
  //! The buffer min size
  // TODO(b/170870354): Setup a more appropriate min size
  static constexpr size_t kBufferMinSize = 1024;  // 1KB

  //! The callback object
  LogBufferCallbackInterface *mCallback;
  //! The notification setting object
  LogBufferNotificationSetting mNotificationSetting =
      LogBufferNotificationSetting::ALWAYS;
  //! The number of bytes that will trigger the threshold notification
  size_t mNotificationThresholdBytes = 0;

  // TODO(srok): Optimize the locking scheme
  //! The mutex guarding all thread safe operations.
  Mutex mLock;
};

}  // namespace chre

#endif  // CHRE_PLATFORM_SHARED_LOG_BUFFER_H_
