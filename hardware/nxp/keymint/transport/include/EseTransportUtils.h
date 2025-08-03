 /*
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 ** http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 **
 ** Copyright 2020-2021 NXP
 **
 */
#ifndef __ESE_TRANSPORT_CONFIG__
#define __ESE_TRANSPORT_CONFIG__
#include <vector>

namespace keymint::javacard {

#define MAX_GET_SERVICE_RETRY 10
#define ONE_SEC  1000*1000*1
#define LOGICAL_CH_NOT_SUPPORTED_SW1 0x68
#define LOGICAL_CH_NOT_SUPPORTED_SW2 0x81
#define APDU_INS_OFFSET 1      // INS offset in command APDU
#define SELECT_P2_VALUE_0 0    // Select command P2 value 0
#define SELECT_P2_VALUE_2 2    // Select command P2 value 2
#define MAX_RETRY_COUNT 3      // Number of retry in case of failure

// Helper method to dump vector contents
static bool debug_omapi = true;
#define LOGD_OMAPI(x) \
  if(debug_omapi) { \
    LOG(INFO) <<"("<<__FUNCTION__ <<")"<<" "<<x; \
  }

#define LOGE_OMAPI(x) \
    LOG(INFO) <<"("<<__FUNCTION__ <<")"<<" "<<x;
std::ostream& operator<<(std::ostream& os, const std::vector<uint8_t>& vec);

} // namespace keymint::javacard
#endif /* __ESE_TRANSPORT_CONFIG__ */
