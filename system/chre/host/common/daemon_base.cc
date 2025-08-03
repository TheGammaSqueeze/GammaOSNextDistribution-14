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

// TODO(b/298459533): metrics_reporter_in_the_daemon ramp up -> remove old
// code

#include <signal.h>
#include <cstdlib>
#include <fstream>

#include "chre_host/config_util.h"
#include "chre_host/daemon_base.h"
#include "chre_host/file_stream.h"
#include "chre_host/log.h"
#include "chre_host/napp_header.h"

#ifdef CHRE_DAEMON_METRIC_ENABLED
#include <android_chre_flags.h>
#include <chre_atoms_log.h>
#include <system/chre/core/chre_metrics.pb.h>
#endif  // CHRE_DAEMON_METRIC_ENABLED

// Aliased for consistency with the way these symbols are referenced in
// CHRE-side code
namespace fbs = ::chre::fbs;

namespace android {
namespace chre {

#ifdef CHRE_DAEMON_METRIC_ENABLED
using ::aidl::android::frameworks::stats::IStats;
using ::aidl::android::frameworks::stats::VendorAtom;
using ::aidl::android::frameworks::stats::VendorAtomValue;

using ::android::chre::Atoms::CHRE_EVENT_QUEUE_SNAPSHOT_REPORTED;
using ::android::chre::Atoms::CHRE_PAL_OPEN_FAILED;
using ::android::chre::Atoms::ChrePalOpenFailed;
using ::android::chre::flags::metrics_reporter_in_the_daemon;
#endif  // CHRE_DAEMON_METRIC_ENABLED

namespace {

void signalHandler(void *ctx) {
  auto *daemon = static_cast<ChreDaemonBase *>(ctx);
  int rc = -1;
  sigset_t signalMask;
  sigfillset(&signalMask);
  sigdelset(&signalMask, SIGINT);
  sigdelset(&signalMask, SIGTERM);
  if (sigprocmask(SIG_SETMASK, &signalMask, NULL) != 0) {
    LOG_ERROR("Couldn't mask all signals except INT/TERM", errno);
  }

  while (true) {
    int signum = 0;
    if ((rc = sigwait(&signalMask, &signum)) != 0) {
      LOGE("Sigwait failed: %d", rc);
    }
    LOGI("Received signal %d", signum);
    if (signum == SIGINT || signum == SIGTERM) {
      daemon->onShutdown();
      break;
    }
  }
}

}  // anonymous namespace

ChreDaemonBase::ChreDaemonBase() : mChreShutdownRequested(false) {
  mLogger.init();
  // TODO(b/297388964): Replace thread with handler installed via std::signal()
  mSignalHandlerThread = std::thread(signalHandler, this);
}

void ChreDaemonBase::loadPreloadedNanoapps() {
  const std::string kPreloadedNanoappsConfigPath =
      "/vendor/etc/chre/preloaded_nanoapps.json";
  std::string directory;
  std::vector<std::string> nanoapps;
  bool success = getPreloadedNanoappsFromConfigFile(
      kPreloadedNanoappsConfigPath, directory, nanoapps);
  if (!success) {
    LOGE("Failed to parse preloaded nanoapps config file");
    return;
  }

  for (uint32_t i = 0; i < nanoapps.size(); ++i) {
    loadPreloadedNanoapp(directory, nanoapps[i], i);
  }
}

void ChreDaemonBase::loadPreloadedNanoapp(const std::string &directory,
                                          const std::string &name,
                                          uint32_t transactionId) {
  std::vector<uint8_t> headerBuffer;

  std::string headerFile = directory + "/" + name + ".napp_header";

  // Only create the nanoapp filename as the CHRE framework will load from
  // within the directory its own binary resides in.
  std::string nanoappFilename = name + ".so";

  if (!readFileContents(headerFile.c_str(), headerBuffer) ||
      !loadNanoapp(headerBuffer, nanoappFilename, transactionId)) {
    LOGE("Failed to load nanoapp: '%s'", name.c_str());
  }
}

bool ChreDaemonBase::loadNanoapp(const std::vector<uint8_t> &header,
                                 const std::string &nanoappName,
                                 uint32_t transactionId) {
  bool success = false;
  if (header.size() != sizeof(NanoAppBinaryHeader)) {
    LOGE("Header size mismatch");
  } else {
    // The header blob contains the struct above.
    const auto *appHeader =
        reinterpret_cast<const NanoAppBinaryHeader *>(header.data());

    // Build the target API version from major and minor.
    uint32_t targetApiVersion = (appHeader->targetChreApiMajorVersion << 24) |
                                (appHeader->targetChreApiMinorVersion << 16);

    success = sendNanoappLoad(appHeader->appId, appHeader->appVersion,
                              targetApiVersion, nanoappName, transactionId);
  }

  return success;
}

bool ChreDaemonBase::sendTimeSyncWithRetry(size_t numRetries,
                                           useconds_t retryDelayUs,
                                           bool logOnError) {
  bool success = false;
  while (!success && (numRetries-- != 0)) {
    success = sendTimeSync(logOnError);
    if (!success) {
      usleep(retryDelayUs);
    }
  }
  return success;
}

void ChreDaemonBase::handleNanConfigurationRequest(
    const ::chre::fbs::NanConfigurationRequestT * /*request*/) {
  LOGE("NAN is unsupported on this platform");
}

#ifdef CHRE_DAEMON_METRIC_ENABLED
void ChreDaemonBase::handleMetricLog(const ::chre::fbs::MetricLogT *metricMsg) {
  const std::vector<int8_t> &encodedMetric = metricMsg->encoded_metric;

  switch (metricMsg->id) {
    case CHRE_PAL_OPEN_FAILED: {
      metrics::ChrePalOpenFailed metric;
      if (!metric.ParseFromArray(encodedMetric.data(), encodedMetric.size())) {
        LOGE("Failed to parse metric data");
      } else {
        if (metrics_reporter_in_the_daemon()) {
          ChrePalOpenFailed::ChrePalType pal =
              static_cast<ChrePalOpenFailed::ChrePalType>(metric.pal());
          ChrePalOpenFailed::Type type =
              static_cast<ChrePalOpenFailed::Type>(metric.type());
          if (!mMetricsReporter.logPalOpenFailed(pal, type)) {
            LOGE("Could not log the PAL open failed metric");
          }
        } else {
          std::vector<VendorAtomValue> values(2);
          values[0].set<VendorAtomValue::intValue>(metric.pal());
          values[1].set<VendorAtomValue::intValue>(metric.type());
          const VendorAtom atom{
              .atomId = Atoms::CHRE_PAL_OPEN_FAILED,
              .values{std::move(values)},
          };
          reportMetric(atom);
        }
      }
      break;
    }
    case CHRE_EVENT_QUEUE_SNAPSHOT_REPORTED: {
      metrics::ChreEventQueueSnapshotReported metric;
      if (!metric.ParseFromArray(encodedMetric.data(), encodedMetric.size())) {
        LOGE("Failed to parse metric data");
      } else {
        if (metrics_reporter_in_the_daemon()) {
          if (!mMetricsReporter.logEventQueueSnapshotReported(
                  metric.snapshot_chre_get_time_ms(),
                  metric.max_event_queue_size(), metric.mean_event_queue_size(),
                  metric.num_dropped_events())) {
            LOGE("Could not log the event queue snapshot metric");
          }
        } else {
          std::vector<VendorAtomValue> values(6);
          values[0].set<VendorAtomValue::intValue>(
              metric.snapshot_chre_get_time_ms());
          values[1].set<VendorAtomValue::intValue>(
              metric.max_event_queue_size());
          values[2].set<VendorAtomValue::intValue>(
              metric.mean_event_queue_size());
          values[3].set<VendorAtomValue::intValue>(metric.num_dropped_events());
          // Last two values are not currently populated and will be implemented
          // later. To avoid confusion of the interpretation, we use UINT32_MAX
          // as a placeholder value.
          values[4].set<VendorAtomValue::intValue>(
              UINT32_MAX);  // max_queue_delay_us
          values[5].set<VendorAtomValue::intValue>(
              UINT32_MAX);  // mean_queue_delay_us
          const VendorAtom atom{
              .atomId = Atoms::CHRE_EVENT_QUEUE_SNAPSHOT_REPORTED,
              .values{std::move(values)},
          };
          reportMetric(atom);
        }
      }
      break;
    }
    default: {
#ifdef CHRE_LOG_ATOM_EXTENSION_ENABLED
      handleVendorMetricLog(metricMsg);
#else
      LOGW("Unknown metric ID %" PRIu32, metricMsg->id);
#endif  // CHRE_LOG_ATOM_EXTENSION_ENABLED
    }
  }
}

void ChreDaemonBase::reportMetric(const VendorAtom &atom) {
  const std::string statsServiceName =
      std::string(IStats::descriptor).append("/default");
  if (!AServiceManager_isDeclared(statsServiceName.c_str())) {
    LOGE("Stats service is not declared.");
    return;
  }

  std::shared_ptr<IStats> stats_client = IStats::fromBinder(ndk::SpAIBinder(
      AServiceManager_waitForService(statsServiceName.c_str())));
  if (stats_client == nullptr) {
    LOGE("Failed to get IStats service");
    return;
  }

  const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(atom);
  if (!ret.isOk()) {
    LOGE("Failed to report vendor atom");
  }
}
#endif  // CHRE_DAEMON_METRIC_ENABLED

}  // namespace chre
}  // namespace android
