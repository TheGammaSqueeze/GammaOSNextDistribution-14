/******************************************************************************
 *
 * Copyright (c) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

#include "content_control_id_keeper.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "le_audio_types.h"

namespace le_audio {

TEST(ContentControlIdKeeperTest, testStart) {
  auto ccid_keeper = ContentControlIdKeeper::GetInstance();
  ASSERT_NE(nullptr, ccid_keeper);
  ccid_keeper->Start();
  ccid_keeper->Stop();
}

TEST(ContentControlIdKeeperTest, testMultipleSetGetOneCcid) {
  auto ccid_keeper = ContentControlIdKeeper::GetInstance();
  ASSERT_NE(nullptr, ccid_keeper);
  ccid_keeper->Start();

  int ccid_one = 1;

  ccid_keeper->SetCcid(
      types::LeAudioContextType::MEDIA | types::LeAudioContextType::ALERTS,
      ccid_one);
  ASSERT_EQ(ccid_one, ccid_keeper->GetCcid(types::LeAudioContextType::MEDIA));
  ASSERT_EQ(ccid_one, ccid_keeper->GetCcid(types::LeAudioContextType::ALERTS));

  auto media_ccids = ccid_keeper->GetAllCcids(
      types::AudioContexts(types::LeAudioContextType::MEDIA));
  ASSERT_EQ(1LU, media_ccids.size());
  ASSERT_NE(media_ccids.end(),
            std::find(media_ccids.begin(), media_ccids.end(), ccid_one));

  auto allerts_ccids = ccid_keeper->GetAllCcids(
      types::AudioContexts(types::LeAudioContextType::ALERTS));
  ASSERT_EQ(1LU, allerts_ccids.size());
  ASSERT_NE(allerts_ccids.end(),
            std::find(allerts_ccids.begin(), allerts_ccids.end(), ccid_one));

  auto all_ccids = ccid_keeper->GetAllCcids(types::LeAudioContextType::MEDIA |
                                            types::LeAudioContextType::ALERTS);
  ASSERT_EQ(1LU, all_ccids.size());
  ASSERT_NE(all_ccids.end(),
            std::find(all_ccids.begin(), all_ccids.end(), ccid_one));

  ccid_keeper->Stop();
}

TEST(ContentControlIdKeeperTest, testMultipleSetGetMultipleCcids) {
  auto ccid_keeper = ContentControlIdKeeper::GetInstance();
  ASSERT_NE(nullptr, ccid_keeper);
  ccid_keeper->Start();

  int ccid_two = 2;
  int ccid_three = 3;

  ccid_keeper->SetCcid(types::LeAudioContextType::MEDIA, ccid_two);
  ASSERT_EQ(ccid_two, ccid_keeper->GetCcid(types::LeAudioContextType::MEDIA));
  ccid_keeper->SetCcid(types::LeAudioContextType::ALERTS, ccid_three);
  ASSERT_EQ(ccid_three,
            ccid_keeper->GetCcid(types::LeAudioContextType::ALERTS));

  auto media_ccids = ccid_keeper->GetAllCcids(
      types::AudioContexts(types::LeAudioContextType::MEDIA));
  ASSERT_EQ(1LU, media_ccids.size());
  ASSERT_NE(media_ccids.end(),
            std::find(media_ccids.begin(), media_ccids.end(), ccid_two));

  auto allerts_ccids = ccid_keeper->GetAllCcids(
      types::AudioContexts(types::LeAudioContextType::ALERTS));
  ASSERT_EQ(1LU, allerts_ccids.size());
  ASSERT_NE(allerts_ccids.end(),
            std::find(allerts_ccids.begin(), allerts_ccids.end(), ccid_three));

  auto all_ccids = ccid_keeper->GetAllCcids(types::LeAudioContextType::MEDIA |
                                            types::LeAudioContextType::ALERTS);
  ASSERT_EQ(2LU, all_ccids.size());
  ASSERT_NE(all_ccids.end(),
            std::find(all_ccids.begin(), all_ccids.end(), ccid_two));
  ASSERT_NE(all_ccids.end(),
            std::find(all_ccids.begin(), all_ccids.end(), ccid_three));

  ccid_keeper->Stop();
}

TEST(ContentControlIdKeeperTest, testStop) {
  auto ccid_keeper = ContentControlIdKeeper::GetInstance();
  ASSERT_NE(nullptr, ccid_keeper);
  ccid_keeper->Start();

  int ccid_two = 2;
  int ccid_three = 3;
  ccid_keeper->SetCcid(types::LeAudioContextType::MEDIA, ccid_two);
  ccid_keeper->SetCcid(types::LeAudioContextType::ALERTS, ccid_three);

  ccid_keeper->Stop();

  // Check if it all got erased on Stop
  ASSERT_EQ(-1, ccid_keeper->GetCcid(types::LeAudioContextType::MEDIA));
  ASSERT_EQ(-1, ccid_keeper->GetCcid(types::LeAudioContextType::ALERTS));
  auto all_ccids = ccid_keeper->GetAllCcids(types::LeAudioContextType::MEDIA |
                                            types::LeAudioContextType::ALERTS);
  ASSERT_EQ(0LU, all_ccids.size());
}

}  // namespace le_audio
