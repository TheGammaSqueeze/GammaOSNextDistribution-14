/*
 * Copyright (C) 2010 The Android Open Source Project
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

#ifndef CHRE_DAEMON_H_
#define CHRE_DAEMON_H_

/**
 * @file daemon_base.h
 * This header defines the CHRE daemon base class, off of which all supported
 * CHRE daemon variants are expected to derive from. The goal is to provide
 * common (abstract or implemented) interfaces that all CHRE daemons must
 * implement.
 */

// TODO(b/298459533): metrics_reporter_in_the_daemon ramp up -> remove old
// code

#include <atomic>
#include <csignal>
#include <cstdint>
#include <map>
#include <mutex>
#include <queue>
#include <string>
#include <thread>

#include "chre_host/host_protocol_host.h"
#include "chre_host/log_message_parser.h"
#include "chre_host/socket_server.h"

#ifdef CHRE_DAEMON_METRIC_ENABLED
#include <aidl/android/frameworks/stats/IStats.h>
#include <android/binder_manager.h>

#include "chre_host/metrics_reporter.h"
#endif  // CHRE_DAEMON_METRIC_ENABLED

namespace android {
namespace chre {

class ChreDaemonBase {
 public:
  ChreDaemonBase();
  virtual ~ChreDaemonBase() {
    if (mSignalHandlerThread.joinable()) {
      std::raise(SIGINT);
      mSignalHandlerThread.join();
    }
  }

  /**
   * Initialize the CHRE daemon. We're expected to fail here and not start
   * the daemon if we don't get all the resources we're hoping to.
   * Any resources claimed by this function should be released in the
   * destructor
   *
   * @return true on successful initialization
   */
  virtual bool init() = 0;

  /**
   * Start the CHRE Daemon. This method must be called after @ref init() has
   * been called.
   */
  virtual void run() = 0;

  /**
   * Send a message to CHRE
   *
   * @param clientId The client ID that this message originates from.
   * @param data The data to pass down.
   * @param length The size of the data to send.
   * @return true if successful, false otherwise.
   */
  virtual bool sendMessageToChre(uint16_t clientId, void *data,
                                 size_t dataLen) = 0;

  /**
   * Function to be invoked on a shutdown request (eg: from a signal handler)
   * to initiate a graceful shutdown of the daemon.
   */
  virtual void onShutdown() {
    setShutdownRequested(true);
    mServer.shutdownServer();
  }

  /**
   * Function to query if a graceful shutdown of CHRE was requested
   *
   * @return true if a graceful shutdown was requested
   */
  bool wasShutdownRequested() const {
    return mChreShutdownRequested;
  }

 protected:
  //! The host ID to use when preloading nanoapps. This is used before the
  //! server is started and is sufficiently high enough so as to not collide
  //! with any clients after the server starts.
  static constexpr uint16_t kHostClientIdDaemon = UINT16_MAX;

  //! Contains the transaction ID and app ID used to preload nanoapps.
  struct Transaction {
    uint32_t transactionId;
    uint64_t nanoappId;
  };

  void setShutdownRequested(bool request) {
    mChreShutdownRequested = request;
  }

  /**
   * Attempts to load all preloaded nanoapps from a config file. The config file
   * is expected to be valid JSON with the following structure:
   *
   * { "nanoapps": [
   *     "/path/to/nanoapp_1",
   *     "/path/to/nanoapp_2"
   * ]}
   *
   * The napp_header and so files will both be loaded. All errors are logged.
   */
  void loadPreloadedNanoapps();

  /**
   * Loads a preloaded nanoapp given a filename to load from. Allows the
   * transaction to complete before the nanoapp starts so the server can start
   * serving requests as soon as possible.
   *
   * @param directory The directory to load the nanoapp from.
   * @param name The filename of the nanoapp to load.
   * @param transactionId The transaction ID to use when loading the app.
   */
  virtual void loadPreloadedNanoapp(const std::string &directory,
                                    const std::string &name,
                                    uint32_t transactionId);

  /**
   * Sends a preloaded nanoapp filename / metadata to CHRE.
   *
   * @param header The nanoapp header binary blob.
   * @param nanoappName The filename of the nanoapp to be loaded.
   * @param transactionId The transaction ID to use when loading the app.
   * @return true if successful, false otherwise.
   */
  bool loadNanoapp(const std::vector<uint8_t> &header,
                   const std::string &nanoappName, uint32_t transactionId);

  /**
   * Loads a nanoapp by sending the nanoapp filename to the CHRE framework. This
   * method will return after sending the request so no guarantee is made that
   * the nanoapp is loaded until after the response is received.
   *
   * @param appId The ID of the nanoapp to load.
   * @param appVersion The version of the nanoapp to load.
   * @param appTargetApiVersion The version of the CHRE API that the app
   * targets.
   * @param appBinaryName The name of the binary as stored in the filesystem.
   * This will be used to load the nanoapp into CHRE.
   * @param transactionId The transaction ID to use when loading.
   * @return true if a request was successfully sent, false otherwise.
   */
  virtual bool sendNanoappLoad(uint64_t appId, uint32_t appVersion,
                               uint32_t appTargetApiVersion,
                               const std::string &appBinaryName,
                               uint32_t transactionId) = 0;

  /**
   * Send a time sync message to CHRE
   *
   * @param logOnError If true, logs an error message on failure.
   *
   * @return true if the time sync message was successfully sent to CHRE.
   */
  virtual bool sendTimeSync(bool logOnError) = 0;

  /**
   * Computes and returns the clock drift between the system clock
   * and the processor timer registers
   *
   * @return offset in nanoseconds
   */
  virtual int64_t getTimeOffset(bool *success) = 0;

  /**
   * Sends a time sync message to CHRE, retrying a specified time until success.
   *
   * @param maxNumRetries The number of times to retry sending the message
   *
   * @return true if the time sync message was successfully sent to CHRE.
   */
  bool sendTimeSyncWithRetry(size_t numRetries, useconds_t retryDelayUs,
                             bool logOnError);

  /**
   * Interface to a callback that is called when the Daemon receives a message.
   *
   * @param message A buffer containing the message
   * @param messageLen size of the message buffer in bytes
   */
  virtual void onMessageReceived(const unsigned char *message,
                                 size_t messageLen) = 0;

  /**
   * Handles a message that is directed towards the daemon.
   *
   * @param message The message sent to the daemon.
   */
  virtual void handleDaemonMessage(const uint8_t *message) = 0;

  /**
   * Enables or disables LPMA (low power microphone access).
   */
  virtual void configureLpma(bool enabled) = 0;

#ifdef CHRE_DAEMON_METRIC_ENABLED
  /**
   * Handles a metric log message sent from CHRE
   *
   */
  virtual void handleMetricLog(const ::chre::fbs::MetricLogT *metric_msg);

#ifdef CHRE_LOG_ATOM_EXTENSION_ENABLED
  /**
   * Handles additional metrics that aren't logged by the common CHRE code.
   */
  virtual void handleVendorMetricLog(
      const ::chre::fbs::MetricLogT *metric_msg) = 0;
#endif  // CHRE_LOG_ATOM_EXTENSION_ENABLED

  /**
   * Create and report CHRE vendor atom and send it to stats_client.
   *
   * @param atom the vendor atom to be reported.
   */
  void reportMetric(const aidl::android::frameworks::stats::VendorAtom &atom);
#endif  // CHRE_DAEMON_METRIC_ENABLED

  /**
   * Handles a NAN configuration request sent from CHRE.
   *
   * @param request NAN configuration request.
   */
  virtual void handleNanConfigurationRequest(
      const ::chre::fbs::NanConfigurationRequestT *request);

  /**
   * Returns the CHRE log message parser instance.
   * @return log message parser instance.
   */
  LogMessageParser &getLogger() {
    return mLogger;
  }

  //! Server used to communicate with daemon clients
  SocketServer mServer;

#ifdef CHRE_DAEMON_METRIC_ENABLED
  android::chre::MetricsReporter mMetricsReporter;
#endif  // CHRE_DAEMON_METRIC_ENABLED

  //! This is used to find the nanoapp name with its app ID.
  std::unordered_map<uint64_t, std::string> mNanoappAppIdToFileNames;

  //! This is used to find the nanoapp app ID with its instance ID.
  std::unordered_map<uint16_t, uint64_t> mNanoappInstanceIdtoAppId;

 private:
  LogMessageParser mLogger;

  std::thread mSignalHandlerThread;

  //! Set to true when we request a graceful shutdown of CHRE
  std::atomic<bool> mChreShutdownRequested;
};

}  // namespace chre
}  // namespace android

#endif  // CHRE_DAEMON_H
