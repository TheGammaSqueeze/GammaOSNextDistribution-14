/*
 * Copyright 2021 The Android Open Source Project
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
#pragma once

#include <cstdint>
#include <memory>

#include "hci/hci_packets.h"
#include "module.h"
#include "neighbor/inquiry.h"
#include "neighbor/scan_parameters.h"
#include "os/handler.h"

#include <gmock/gmock.h>

// Unit test interfaces
namespace bluetooth {
namespace neighbor {
namespace testing {

class MockInquiryModule : public InquiryModule {
 public:
  MOCK_METHOD(void, RegisterCallbacks, (InquiryCallbacks inquiry_callbacks));
  MOCK_METHOD(void, UnregisterCallbacks, ());
  MOCK_METHOD(void, StartGeneralInquiry, (InquiryLength inquiry_length, NumResponses num_responses));
  MOCK_METHOD(void, StartLimitedInquiry, (InquiryLength inquiry_length, NumResponses num_responses));
  MOCK_METHOD(void, StopInquiry, ());
  MOCK_METHOD(
      void,
      StartGeneralPeriodicInquiry,
      (InquiryLength inquiry_length, NumResponses num_responses, PeriodLength max_delay, PeriodLength min_delay));
  MOCK_METHOD(
      void,
      StartLimitedPeriodicInquiry,
      (InquiryLength inquiry_length, NumResponses num_responses, PeriodLength max_delay, PeriodLength min_delay));
  MOCK_METHOD(void, StopPeriodicInquiry, ());
  MOCK_METHOD(void, SetScanActivity, (ScanParameters parms));
  MOCK_METHOD(void, SetInterlacedScan, ());
  MOCK_METHOD(void, SetStandardScan, ());
  MOCK_METHOD(void, SetStandardInquiryResultMode, ());
  MOCK_METHOD(void, SetInquiryWithRssiResultMode, ());
  MOCK_METHOD(void, SetExtendedInquiryResultMode, ());
};

}  // namespace testing
}  // namespace neighbor
}  // namespace bluetooth
