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

#include <gtest/gtest.h>

#include <stddef.h>
#include <cstring>

#include "chpp/common/wifi_types.h"
#include "chpp/memory.h"
#include "chre/test/common/macros.h"

namespace {

void validateScanResult(const ChppWifiScanResult &chppAp,
                        const chreWifiScanResult &chreAp, bool decodeMode) {
  EXPECT_EQ(chppAp.ageMs, chreAp.ageMs);
  EXPECT_EQ(chppAp.capabilityInfo, chreAp.capabilityInfo);
  EXPECT_EQ(chppAp.ssidLen, chreAp.ssidLen);
  EXPECT_EQ(std::memcmp(chppAp.ssid, chreAp.ssid, sizeof(chppAp.ssid)), 0);
  EXPECT_EQ(std::memcmp(chppAp.bssid, chreAp.bssid, sizeof(chppAp.bssid)), 0);
  EXPECT_EQ(chppAp.flags, chreAp.flags);
  EXPECT_EQ(chppAp.rssi, chreAp.rssi);
  EXPECT_EQ(chppAp.band, chreAp.band);
  EXPECT_EQ(chppAp.primaryChannel, chreAp.primaryChannel);
  EXPECT_EQ(chppAp.centerFreqPrimary, chreAp.centerFreqPrimary);
  EXPECT_EQ(chppAp.centerFreqSecondary, chreAp.centerFreqSecondary);
  EXPECT_EQ(chppAp.channelWidth, chreAp.channelWidth);
  EXPECT_EQ(chppAp.securityMode, chreAp.securityMode);
  EXPECT_EQ(chppAp.radioChain, chreAp.radioChain);
  EXPECT_EQ(chppAp.rssiChain0, chreAp.rssiChain0);
  EXPECT_EQ(chppAp.rssiChain1, chreAp.rssiChain1);

  for (size_t i = 0;
       i < (decodeMode ? sizeof(chreAp.reserved) : sizeof(chppAp.reserved));
       i++) {
    SCOPED_TRACE(i);
    EXPECT_EQ((decodeMode ? chreAp.reserved[i] : chppAp.reserved[i]), 0);
  }
}

void validateScanEvent(const chreWifiScanEvent &chreEvent) {
  ChppWifiScanEventWithHeader *chppWithHeader = nullptr;
  size_t outputSize = 999;

  // Encode
  bool result =
      chppWifiScanEventFromChre(&chreEvent, &chppWithHeader, &outputSize);
  ASSERT_TRUE(result);
  ASSERT_NE(chppWithHeader, nullptr);

  size_t expectedSize = sizeof(ChppWifiScanEventWithHeader) +
                        chreEvent.scannedFreqListLen * sizeof(uint32_t) +
                        chreEvent.resultCount * sizeof(ChppWifiScanResult);
  EXPECT_EQ(outputSize, expectedSize);

  ChppWifiScanEvent *chppEvent = &chppWithHeader->payload;

  // Decode
  outputSize -= sizeof(struct ChppAppHeader);
  chreWifiScanEvent *backEvent = chppWifiScanEventToChre(chppEvent, outputSize);
  ASSERT_NE(backEvent, nullptr);

  // Compare chreEvent against encoded (chppEvent) and decoded back (backEvent)

  EXPECT_EQ(chppEvent->version, CHRE_WIFI_SCAN_EVENT_VERSION);
  EXPECT_EQ(chppEvent->resultCount, chreEvent.resultCount);
  EXPECT_EQ(chppEvent->resultTotal, chreEvent.resultTotal);
  EXPECT_EQ(chppEvent->resultCount, chreEvent.resultCount);
  EXPECT_EQ(chppEvent->eventIndex, chreEvent.eventIndex);
  EXPECT_EQ(chppEvent->scanType, chreEvent.scanType);
  EXPECT_EQ(chppEvent->ssidSetSize, chreEvent.ssidSetSize);
  EXPECT_EQ(chppEvent->scannedFreqListLen, chreEvent.scannedFreqListLen);
  EXPECT_EQ(chppEvent->referenceTime, chreEvent.referenceTime);
  EXPECT_EQ(chppEvent->radioChainPref, chreEvent.radioChainPref);

  EXPECT_EQ(backEvent->version, CHRE_WIFI_SCAN_EVENT_VERSION);
  EXPECT_EQ(backEvent->resultCount, chreEvent.resultCount);
  EXPECT_EQ(backEvent->resultTotal, chreEvent.resultTotal);
  EXPECT_EQ(backEvent->resultCount, chreEvent.resultCount);
  EXPECT_EQ(backEvent->eventIndex, chreEvent.eventIndex);
  EXPECT_EQ(backEvent->scanType, chreEvent.scanType);
  EXPECT_EQ(backEvent->ssidSetSize, chreEvent.ssidSetSize);
  EXPECT_EQ(backEvent->scannedFreqListLen, chreEvent.scannedFreqListLen);
  EXPECT_EQ(backEvent->referenceTime, chreEvent.referenceTime);
  EXPECT_EQ(backEvent->radioChainPref, chreEvent.radioChainPref);

  uint16_t baseOffset = sizeof(ChppWifiScanEvent);
  if (chreEvent.scannedFreqListLen > 0) {
    EXPECT_EQ(chppEvent->scannedFreqList.offset, baseOffset);
    EXPECT_EQ(chppEvent->scannedFreqList.length,
              chppEvent->scannedFreqListLen * sizeof(uint32_t));
    baseOffset += chppEvent->scannedFreqList.length;

    auto *chppScannedFreqList =
        ((const uint8_t *)chppEvent + chppEvent->scannedFreqList.offset);
    for (size_t i = 0; i < chppEvent->scannedFreqListLen; i++) {
      uint32_t currScannedFreq;
      memcpy(&currScannedFreq, chppScannedFreqList + (i * sizeof(uint32_t)),
             sizeof(uint32_t));
      SCOPED_TRACE(i);
      EXPECT_EQ(currScannedFreq, chreEvent.scannedFreqList[i]);
      EXPECT_EQ(currScannedFreq, backEvent->scannedFreqList[i]);
    }
  } else {
    EXPECT_EQ(chppEvent->scannedFreqList.offset, 0);
    EXPECT_EQ(chppEvent->scannedFreqList.length, 0);
  }

  if (chreEvent.resultCount > 0) {
    EXPECT_EQ(chppEvent->results.offset, baseOffset);
    EXPECT_EQ(chppEvent->results.length,
              chppEvent->resultCount * sizeof(ChppWifiScanResult));
    baseOffset += chppEvent->results.length;

    const ChppWifiScanResult *chppAp =
        (const ChppWifiScanResult *)((const uint8_t *)chppEvent +
                                     chppEvent->results.offset);
    for (size_t i = 0; i < chppEvent->resultCount; i++) {
      SCOPED_TRACE(::testing::Message() << "Scan result index " << i);
      validateScanResult(chppAp[i], chreEvent.results[i], /*decodeMode=*/false);
      validateScanResult(chppAp[i], backEvent->results[i], /*decodeMode=*/true);
    }
  } else {
    EXPECT_EQ(chppEvent->results.offset, 0);
    EXPECT_EQ(chppEvent->results.length, 0);
  }

  // Handling of short input
  chreWifiScanEvent *chreMalformed;
  chreMalformed = chppWifiScanEventToChre(chppEvent, outputSize - 1);
  ASSERT_EQ(chreMalformed, nullptr);

  chppFree(chppWithHeader);
  chppFree(backEvent);
}

void validateScanParams(const chreWifiScanParams &chreParams) {
  ChppWifiScanParamsWithHeader *chppWithHeader = nullptr;
  size_t outputSize = 999;

  // Encode
  bool result =
      chppWifiScanParamsFromChre(&chreParams, &chppWithHeader, &outputSize);
  ASSERT_TRUE(result);
  ASSERT_NE(chppWithHeader, nullptr);

  size_t expectedSize = sizeof(ChppWifiScanParamsWithHeader) +
                        chreParams.frequencyListLen * sizeof(uint32_t) +
                        chreParams.ssidListLen * sizeof(ChppWifiSsidListItem);
  EXPECT_EQ(outputSize, expectedSize);

  ChppWifiScanParams *chppParams = &chppWithHeader->payload;

  // Decode
  outputSize -= sizeof(struct ChppAppHeader);
  chreWifiScanParams *backParams =
      chppWifiScanParamsToChre(chppParams, outputSize);
  ASSERT_NE(backParams, nullptr);

  // Compare chreEvent against encoded (chppEvent) and decoded back (backEvent)

  EXPECT_EQ(chppParams->scanType, chreParams.scanType);
  EXPECT_EQ(chppParams->maxScanAgeMs, chreParams.maxScanAgeMs);
  EXPECT_EQ(chppParams->frequencyListLen, chreParams.frequencyListLen);
  EXPECT_EQ(chppParams->ssidListLen, chreParams.ssidListLen);
  EXPECT_EQ(chppParams->radioChainPref, chreParams.radioChainPref);

  EXPECT_EQ(backParams->scanType, chreParams.scanType);
  EXPECT_EQ(backParams->maxScanAgeMs, chreParams.maxScanAgeMs);
  EXPECT_EQ(backParams->frequencyListLen, chreParams.frequencyListLen);
  EXPECT_EQ(backParams->ssidListLen, chreParams.ssidListLen);
  EXPECT_EQ(backParams->radioChainPref, chreParams.radioChainPref);

  uint16_t baseOffset = sizeof(ChppWifiScanParams);
  if (chreParams.frequencyListLen > 0) {
    EXPECT_EQ(chppParams->frequencyList.offset, baseOffset);
    EXPECT_EQ(chppParams->frequencyList.length,
              chppParams->frequencyListLen * sizeof(uint32_t));
    baseOffset += chppParams->frequencyList.length;

    auto *chppFrequencyList =
        ((const uint8_t *)chppParams + chppParams->frequencyList.offset);
    for (size_t i = 0; i < chppParams->frequencyListLen; i++) {
      uint32_t currScannedFreq;
      memcpy(&currScannedFreq, chppFrequencyList + (i * sizeof(uint32_t)),
             sizeof(uint32_t));
      SCOPED_TRACE(i);
      EXPECT_EQ(currScannedFreq, chreParams.frequencyList[i]);
      EXPECT_EQ(currScannedFreq, backParams->frequencyList[i]);
    }
  } else {
    EXPECT_EQ(chppParams->frequencyList.offset, 0);
    EXPECT_EQ(chppParams->frequencyList.length, 0);
  }

  if (chreParams.ssidListLen > 0) {
    EXPECT_EQ(chppParams->ssidList.offset, baseOffset);
    EXPECT_EQ(chppParams->ssidList.length,
              chppParams->ssidListLen * sizeof(ChppWifiSsidListItem));
    baseOffset += chppParams->ssidList.length;

    const ChppWifiSsidListItem *chppSsidList =
        (const ChppWifiSsidListItem *)((const uint8_t *)chppParams +
                                       chppParams->ssidList.offset);
    for (size_t i = 0; i < chppParams->ssidListLen; i++) {
      SCOPED_TRACE(i);
      EXPECT_EQ(chppSsidList[i].ssidLen, chreParams.ssidList[i].ssidLen);
      EXPECT_EQ(std::memcmp(chppSsidList[i].ssid, chreParams.ssidList[i].ssid,
                            sizeof(chppSsidList[i].ssid)),
                0);
      EXPECT_EQ(chppSsidList[i].ssidLen, backParams->ssidList[i].ssidLen);
      EXPECT_EQ(std::memcmp(chppSsidList[i].ssid, backParams->ssidList[i].ssid,
                            sizeof(chppSsidList[i].ssid)),
                0);
    }
  } else {
    EXPECT_EQ(chppParams->ssidList.offset, 0);
    EXPECT_EQ(chppParams->ssidList.length, 0);
  }

  // Handling of short input
  chreWifiScanParams *chreMalformed;
  chreMalformed = chppWifiScanParamsToChre(chppParams, outputSize - 1);
  ASSERT_EQ(chreMalformed, nullptr);

  chppFree(chppWithHeader);
  chppFree(backParams);
}

void validateRangingParams(const chreWifiRangingParams &chreParams) {
  ChppWifiRangingParamsWithHeader *chppWithHeader = nullptr;
  size_t outputSize = 999;

  // Encode
  bool result =
      chppWifiRangingParamsFromChre(&chreParams, &chppWithHeader, &outputSize);
  ASSERT_TRUE(result);
  ASSERT_NE(chppWithHeader, nullptr);

  size_t expectedSize =
      sizeof(ChppWifiRangingParamsWithHeader) +
      chreParams.targetListLen * sizeof(ChppWifiRangingTarget);
  EXPECT_EQ(outputSize, expectedSize);

  ChppWifiRangingParams *chppParams = &chppWithHeader->payload;

  // Decode
  outputSize -= sizeof(struct ChppAppHeader);
  chreWifiRangingParams *backParams =
      chppWifiRangingParamsToChre(chppParams, outputSize);
  ASSERT_NE(backParams, nullptr);

  // Compare chreEvent against encoded (chppEvent) and decoded back (backEvent)
  EXPECT_EQ(chppParams->targetListLen, chreParams.targetListLen);
  EXPECT_EQ(backParams->targetListLen, chreParams.targetListLen);

  uint16_t baseOffset = sizeof(ChppWifiRangingParams);
  if (chreParams.targetListLen > 0) {
    EXPECT_EQ(chppParams->targetList.offset, baseOffset);
    EXPECT_EQ(chppParams->targetList.length,
              chppParams->targetListLen * sizeof(ChppWifiRangingTarget));
    baseOffset += chppParams->targetList.length;

    auto *chppRangingList =
        ((const uint8_t *)chppParams + chppParams->targetList.offset);
    for (size_t i = 0; i < chppParams->targetListLen; i++) {
      ChppWifiRangingTarget currentRangingTarget;
      memcpy(&currentRangingTarget,
             chppRangingList + (i * sizeof(ChppWifiRangingTarget)),
             sizeof(ChppWifiRangingTarget));
      SCOPED_TRACE(i);
      EXPECT_EQ(currentRangingTarget.macAddress[0],
                chreParams.targetList[i].macAddress[0]);
      EXPECT_EQ(currentRangingTarget.macAddress[1],
                chreParams.targetList[i].macAddress[1]);
      EXPECT_EQ(currentRangingTarget.macAddress[2],
                chreParams.targetList[i].macAddress[2]);
      EXPECT_EQ(currentRangingTarget.macAddress[3],
                chreParams.targetList[i].macAddress[3]);
      EXPECT_EQ(currentRangingTarget.macAddress[4],
                chreParams.targetList[i].macAddress[4]);
      EXPECT_EQ(currentRangingTarget.macAddress[5],
                chreParams.targetList[i].macAddress[5]);
      EXPECT_EQ(currentRangingTarget.primaryChannel,
                chreParams.targetList[i].primaryChannel);
      EXPECT_EQ(currentRangingTarget.centerFreqPrimary,
                chreParams.targetList[i].centerFreqPrimary);
      EXPECT_EQ(currentRangingTarget.centerFreqSecondary,
                chreParams.targetList[i].centerFreqSecondary);
      EXPECT_EQ(currentRangingTarget.channelWidth,
                chreParams.targetList[i].channelWidth);

      EXPECT_EQ(currentRangingTarget.macAddress[0],
                backParams->targetList[i].macAddress[0]);
      EXPECT_EQ(currentRangingTarget.macAddress[1],
                backParams->targetList[i].macAddress[1]);
      EXPECT_EQ(currentRangingTarget.macAddress[2],
                backParams->targetList[i].macAddress[2]);
      EXPECT_EQ(currentRangingTarget.macAddress[3],
                backParams->targetList[i].macAddress[3]);
      EXPECT_EQ(currentRangingTarget.macAddress[4],
                backParams->targetList[i].macAddress[4]);
      EXPECT_EQ(currentRangingTarget.macAddress[5],
                backParams->targetList[i].macAddress[5]);
      EXPECT_EQ(currentRangingTarget.primaryChannel,
                backParams->targetList[i].primaryChannel);
      EXPECT_EQ(currentRangingTarget.centerFreqPrimary,
                backParams->targetList[i].centerFreqPrimary);
      EXPECT_EQ(currentRangingTarget.centerFreqSecondary,
                backParams->targetList[i].centerFreqSecondary);
      EXPECT_EQ(currentRangingTarget.channelWidth,
                backParams->targetList[i].channelWidth);
    }
  } else {
    EXPECT_EQ(chppParams->targetList.offset, 0);
    EXPECT_EQ(chppParams->targetList.length, 0);
  }

  // Handling of short input
  chreWifiRangingParams *chreMalformed;
  chreMalformed = chppWifiRangingParamsToChre(chppParams, outputSize - 1);
  ASSERT_EQ(chreMalformed, nullptr);

  chppFree(chppWithHeader);
  chppFree(backParams);
}

void validateNanSubscribeConfig(const chreWifiNanSubscribeConfig &config) {
  ChppWifiNanSubscribeConfigWithHeader *chppWithHeader = nullptr;
  size_t outputSize = 999;

  // Test Encode
  bool result =
      chppWifiNanSubscribeConfigFromChre(&config, &chppWithHeader, &outputSize);
  ASSERT_TRUE(result);
  ASSERT_NE(chppWithHeader, nullptr);

  size_t expectedSize = sizeof(ChppWifiNanSubscribeConfigWithHeader) +
                        std::strlen(config.service) + 1 +
                        config.matchFilterLength +
                        config.serviceSpecificInfoSize;
  EXPECT_EQ(expectedSize, outputSize);

  // Test Decode
  outputSize -= sizeof(struct ChppAppHeader);
  ChppWifiNanSubscribeConfig *chppConfig = &chppWithHeader->payload;
  chreWifiNanSubscribeConfig *decodedConfig =
      chppWifiNanSubscribeConfigToChre(chppConfig, outputSize);
  ASSERT_NE(decodedConfig, nullptr);

  EXPECT_EQ(config.subscribeType, decodedConfig->subscribeType);
  EXPECT_EQ(std::string(config.service), std::string(decodedConfig->service));

  EXPECT_EQ(config.serviceSpecificInfoSize,
            decodedConfig->serviceSpecificInfoSize);
  EXPECT_TRUE(0 == std::memcmp(config.serviceSpecificInfo,
                               decodedConfig->serviceSpecificInfo,
                               config.serviceSpecificInfoSize));

  EXPECT_EQ(config.matchFilterLength, decodedConfig->matchFilterLength);
  EXPECT_TRUE(0 == std::memcmp(config.matchFilter, decodedConfig->matchFilter,
                               config.matchFilterLength));
}

void validateNanDiscoveryEvent(const struct chreWifiNanDiscoveryEvent &event) {
  ChppWifiNanDiscoveryEventWithHeader *chppWithHeader = nullptr;
  size_t outputSize = 999;

  // Test Encode
  bool result =
      chppWifiNanDiscoveryEventFromChre(&event, &chppWithHeader, &outputSize);
  ASSERT_TRUE(result);
  ASSERT_NE(chppWithHeader, nullptr);

  size_t expectedSize = sizeof(ChppWifiNanDiscoveryEventWithHeader) +
                        event.serviceSpecificInfoSize;
  EXPECT_EQ(expectedSize, outputSize);

  // Test Decode
  outputSize -= sizeof(struct ChppAppHeader);
  ChppWifiNanDiscoveryEvent *chppEvent = &chppWithHeader->payload;
  chreWifiNanDiscoveryEvent *decodedEvent =
      chppWifiNanDiscoveryEventToChre(chppEvent, outputSize);
  ASSERT_NE(decodedEvent, nullptr);

  EXPECT_EQ(event.publishId, decodedEvent->publishId);
  EXPECT_EQ(event.subscribeId, decodedEvent->subscribeId);
  EXPECT_TRUE(0 == std::memcmp(event.serviceSpecificInfo,
                               decodedEvent->serviceSpecificInfo,
                               event.serviceSpecificInfoSize));
  EXPECT_TRUE(0 == std::memcmp(event.publisherAddress,
                               decodedEvent->publisherAddress,
                               CHRE_WIFI_BSSID_LEN));
}

void validateNanRangingParams(const struct chreWifiNanRangingParams &params) {
  ChppWifiNanRangingParamsWithHeader *chppWithHeader = nullptr;
  size_t outputSize = 999;

  // Test Encode
  bool result =
      chppWifiNanRangingParamsFromChre(&params, &chppWithHeader, &outputSize);
  ASSERT_TRUE(result);
  ASSERT_NE(chppWithHeader, nullptr);

  size_t expectedSize = sizeof(ChppWifiNanRangingParamsWithHeader);
  EXPECT_EQ(expectedSize, outputSize);

  // Test Decode
  outputSize -= sizeof(struct ChppAppHeader);
  ChppWifiNanRangingParams *chppParams = &chppWithHeader->payload;
  chreWifiNanRangingParams *decodedParams =
      chppWifiNanRangingParamsToChre(chppParams, outputSize);
  ASSERT_NE(decodedParams, nullptr);
  EXPECT_TRUE(0 == std::memcmp(&params, decodedParams, sizeof(params)));
}

void validateNanSessionLostEvent(
    const struct chreWifiNanSessionLostEvent &event) {
  ChppWifiNanSessionLostEventWithHeader *chppWithHeader = nullptr;
  size_t outputSize = 999;

  // Test Encode
  bool result =
      chppWifiNanSessionLostEventFromChre(&event, &chppWithHeader, &outputSize);
  ASSERT_TRUE(result);
  ASSERT_NE(chppWithHeader, nullptr);

  size_t expectedSize = sizeof(ChppWifiNanSessionLostEventWithHeader);
  EXPECT_EQ(expectedSize, outputSize);

  // Test Decode
  outputSize -= sizeof(struct ChppAppHeader);
  ChppWifiNanSessionLostEvent *chppEvent = &chppWithHeader->payload;
  chreWifiNanSessionLostEvent *decodedEvent =
      chppWifiNanSessionLostEventToChre(chppEvent, outputSize);
  ASSERT_NE(decodedEvent, nullptr);
  EXPECT_TRUE(0 == std::memcmp(&event, decodedEvent, sizeof(event)));
}

void validateNanSessionTerminatedEvent(
    const struct chreWifiNanSessionTerminatedEvent &event) {
  ChppWifiNanSessionTerminatedEventWithHeader *chppWithHeader = nullptr;
  size_t outputSize = 999;

  // Test Encode
  bool result = chppWifiNanSessionTerminatedEventFromChre(
      &event, &chppWithHeader, &outputSize);
  ASSERT_TRUE(result);
  ASSERT_NE(chppWithHeader, nullptr);

  size_t expectedSize = sizeof(ChppWifiNanSessionTerminatedEventWithHeader);
  EXPECT_EQ(expectedSize, outputSize);

  // Test Decode
  outputSize -= sizeof(struct ChppAppHeader);
  ChppWifiNanSessionTerminatedEvent *chppEvent = &chppWithHeader->payload;
  chreWifiNanSessionTerminatedEvent *decodedEvent =
      chppWifiNanSessionTerminatedEventToChre(chppEvent, outputSize);
  ASSERT_NE(decodedEvent, nullptr);
  EXPECT_TRUE(0 == std::memcmp(&event, decodedEvent, sizeof(event)));
}

}  // anonymous namespace

TEST(WifiConvert, EmptyScanResult) {
  const chreWifiScanEvent chreEvent = {
      .version = 200,  // ignored
      .resultCount = 0,
      .resultTotal = 0,
      .eventIndex = 0,
      .scanType = CHRE_WIFI_SCAN_TYPE_ACTIVE_PLUS_PASSIVE_DFS,
      .ssidSetSize = 2,
      .scannedFreqListLen = 0,
      .referenceTime = 1234,
      .scannedFreqList = nullptr,
      .results = nullptr,
      .radioChainPref = CHRE_WIFI_RADIO_CHAIN_PREF_HIGH_ACCURACY,
  };

  validateScanEvent(chreEvent);
}

TEST(WifiConvert, SingleResult) {
  // clang-format off
  const chreWifiScanResult chreAp = {
      .ageMs = 11,
      .capabilityInfo = 22,
      .ssidLen = 4,
      .ssid = {'a', 'b', 'c', 'd',},
      .bssid = {0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff},
      .flags = CHRE_WIFI_SCAN_RESULT_FLAGS_IS_FTM_RESPONDER,
      .rssi = -37,
      .band = CHRE_WIFI_BAND_2_4_GHZ,
      .primaryChannel = 2437,
      .centerFreqPrimary = 2442,
      .centerFreqSecondary = 2447,
      .channelWidth = CHRE_WIFI_CHANNEL_WIDTH_80_MHZ,
      .securityMode = CHRE_WIFI_SECURITY_MODE_PSK,
      .radioChain = CHRE_WIFI_RADIO_CHAIN_0 | CHRE_WIFI_RADIO_CHAIN_1,
      .rssiChain0 = -37,
      .rssiChain1 = -42,
  };
  const chreWifiScanEvent chreEvent = {
      .version = 200,  // ignored
      .resultCount = 1,
      .resultTotal = 5,
      .eventIndex = 2,
      .scanType = CHRE_WIFI_SCAN_TYPE_ACTIVE,
      .ssidSetSize = 0,
      .scannedFreqListLen = 0,
      .referenceTime = 12345,
      .scannedFreqList = nullptr,
      .results = &chreAp,
      .radioChainPref = CHRE_WIFI_RADIO_CHAIN_PREF_DEFAULT,
  };
  // clang-format on

  validateScanEvent(chreEvent);
}

TEST(WifiConvert, TwoResultsWithFreqList) {
  // clang-format off
  const chreWifiScanResult chreAps[] = {
  {
      .ageMs = 11,
      .capabilityInfo = 22,
      .ssidLen = 4,
      .ssid = {'a', 'b', 'c', 'd',},
      .bssid = {0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff},
      .flags = CHRE_WIFI_SCAN_RESULT_FLAGS_IS_FTM_RESPONDER,
      .rssi = -37,
      .band = CHRE_WIFI_BAND_2_4_GHZ,
      .primaryChannel = 2437,
      .centerFreqPrimary = 2442,
      .centerFreqSecondary = 2447,
      .channelWidth = CHRE_WIFI_CHANNEL_WIDTH_80_MHZ,
      .securityMode = CHRE_WIFI_SECURITY_MODE_PSK,
      .radioChain = CHRE_WIFI_RADIO_CHAIN_0 | CHRE_WIFI_RADIO_CHAIN_1,
      .rssiChain0 = -37,
      .rssiChain1 = -42,
  },
  {
      .ageMs = 4325,
      .capabilityInfo = 37,
      .ssidLen = 2,
      .ssid = {'h', 'i',},
      .bssid = {0xab, 0xcd, 0xef, 0x01, 0x23, 0x45},
      .flags = CHRE_WIFI_SCAN_RESULT_FLAGS_VHT_OPS_PRESENT,
      .rssi = -52,
      .band = CHRE_WIFI_BAND_5_GHZ,
      .primaryChannel = 9999,
      .centerFreqPrimary = 8888,
      .centerFreqSecondary = 7777,
      .channelWidth = CHRE_WIFI_CHANNEL_WIDTH_160_MHZ,
      .securityMode = CHRE_WIFI_SECURITY_MODE_SAE,
      .radioChain = CHRE_WIFI_RADIO_CHAIN_0,
      .rssiChain0 = -37,
      .rssiChain1 = 0,
  }};
  const uint32_t freqList[] = {0xdeadbeef, 0xc001cafe, 0xc0a1ba11};
  const chreWifiScanEvent chreEvent = {
      .version = 200,  // ignored
      .resultCount = 2,
      .resultTotal = 3,
      .eventIndex = 1,
      .scanType = CHRE_WIFI_SCAN_TYPE_ACTIVE,
      .ssidSetSize = 10,
      .scannedFreqListLen = 3,
      .referenceTime = 56789,
      .scannedFreqList = freqList,
      .results = chreAps,
      .radioChainPref = CHRE_WIFI_RADIO_CHAIN_PREF_LOW_POWER,
  };
  // clang-format on

  validateScanEvent(chreEvent);
}

TEST(WifiConvert, DefaultScanParams) {
  // From chreWifiRequestScanAsyncDefault
  struct chreWifiScanParams params = {};
  params.scanType = CHRE_WIFI_SCAN_TYPE_ACTIVE;
  params.maxScanAgeMs = 5000;  // 5 seconds
  params.frequencyListLen = 0;
  params.ssidListLen = 0;
  params.radioChainPref = CHRE_WIFI_RADIO_CHAIN_PREF_DEFAULT;

  validateScanParams(params);
}

TEST(WifiConvert, ScanParamsWithFreqList) {
  uint32_t freqList[] = {1234, 3456};
  struct chreWifiScanParams chreParams = {
      .scanType = CHRE_WIFI_SCAN_TYPE_ACTIVE_PLUS_PASSIVE_DFS,
      .maxScanAgeMs = 9999,
      .frequencyListLen = 2,
      .frequencyList = freqList,
      .ssidListLen = 0,
      .ssidList = nullptr,
      .radioChainPref = CHRE_WIFI_RADIO_CHAIN_PREF_LOW_POWER,
  };

  validateScanParams(chreParams);
}

TEST(WifiConvert, ScanParamsWithSsidList) {
  // clang-format off
  chreWifiSsidListItem ssidList[] = {
      {.ssidLen = 4, .ssid = {0xde, 0xad, 0xbe, 0xef}},
      {.ssidLen = 2, .ssid = {':', ')'}}
  };
  // clang-format on
  struct chreWifiScanParams chreParams = {
      .scanType = CHRE_WIFI_SCAN_TYPE_ACTIVE_PLUS_PASSIVE_DFS,
      .maxScanAgeMs = 9999,
      .frequencyListLen = 0,
      .frequencyList = nullptr,
      .ssidListLen = 2,
      .ssidList = ssidList,
      .radioChainPref = CHRE_WIFI_RADIO_CHAIN_PREF_LOW_POWER,
  };

  validateScanParams(chreParams);
}

TEST(WifiConvert, ScanParamsWithBothLists) {
  uint32_t freqList[] = {1234, 3456, 5678};
  // clang-format off
  chreWifiSsidListItem ssidList[] = {
      {.ssidLen = 4, .ssid = {0xde, 0xad, 0xbe, 0xef}},
      {.ssidLen = 3, .ssid = {':', '-', ')'}}
  };
  // clang-format on
  struct chreWifiScanParams chreParams = {
      .scanType = CHRE_WIFI_SCAN_TYPE_ACTIVE_PLUS_PASSIVE_DFS,
      .maxScanAgeMs = 9999,
      .frequencyListLen = 3,
      .frequencyList = freqList,
      .ssidListLen = 2,
      .ssidList = ssidList,
      .radioChainPref = CHRE_WIFI_RADIO_CHAIN_PREF_LOW_POWER,
  };

  validateScanParams(chreParams);
}

TEST(WifiConvert, RangingParamsEmpty) {
  struct chreWifiRangingParams chreParams = {
      .targetListLen = 0,
      .targetList = NULL,
  };

  validateRangingParams(chreParams);
}

TEST(WifiConvert, RangingParamsWithTarget) {
  struct chreWifiRangingTarget target = {
      .macAddress = {0x12, 0x34, 0x56, 0x78, 0x9a, 0xbc},
      .primaryChannel = 0xdef02468,
      .centerFreqPrimary = 0xace13579,
      .centerFreqSecondary = 0xbdf369cf,
      .channelWidth = 0x48,
  };

  struct chreWifiRangingParams chreParams = {
      .targetListLen = 1,
      .targetList = &target,
  };

  validateRangingParams(chreParams);
}

TEST(WifiConvert, NanSubscribeConfig) {
  constexpr size_t kServiceSpecificInfoSize = 4;
  constexpr size_t kMatchFilterLength = 6;
  uint8_t serviceSpecificInfo[kServiceSpecificInfoSize] = {0x1, 0x2, 0x3, 0x4};
  uint8_t matchFilter[kMatchFilterLength] = {0x1, 0x2, 0x3, 0x4, 0x5, 0x6};
  struct chreWifiNanSubscribeConfig config = {
      .subscribeType = CHRE_WIFI_NAN_SUBSCRIBE_TYPE_PASSIVE,
      .service = "Hello NAN!",
      .serviceSpecificInfo = serviceSpecificInfo,
      .serviceSpecificInfoSize = kServiceSpecificInfoSize,
      .matchFilter = matchFilter,
      .matchFilterLength = kMatchFilterLength};

  validateNanSubscribeConfig(config);
}

TEST(WifiConvert, NanDiscoveryEvent) {
  constexpr size_t kServiceSpecificInfoSize = 4;
  uint8_t serviceSpecificInfo[kServiceSpecificInfoSize] = {0x1, 0x2, 0x3, 0x4};
  struct chreWifiNanDiscoveryEvent event = {
      .subscribeId = 0xc0ffee,
      .publishId = 0xcafe,
      .publisherAddress = {0x1, 0x2, 0x3, 0x4, 0x5, 0x6},
      .serviceSpecificInfo = serviceSpecificInfo,
      .serviceSpecificInfoSize = kServiceSpecificInfoSize};

  validateNanDiscoveryEvent(event);
}

TEST(WifiConvert, NanSessionLostEvent) {
  struct chreWifiNanSessionLostEvent event = {.id = 0xdead, .peerId = 0xbeef};
  validateNanSessionLostEvent(event);
}

TEST(WifiConvert, NanSessionTerminatedEvent) {
  struct chreWifiNanSessionTerminatedEvent event = {.id = 0xc001,
                                                    .reason = CHRE_ERROR};
  validateNanSessionTerminatedEvent(event);
}

TEST(WifiConvert, NanRangingParams) {
  struct chreWifiNanRangingParams params = {
      .macAddress = {0x1, 0x2, 0x3, 0x4, 0x5, 0x6}};
  validateNanRangingParams(params);
}
