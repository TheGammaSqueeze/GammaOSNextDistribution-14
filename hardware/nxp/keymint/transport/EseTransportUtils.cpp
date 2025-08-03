/*
 ** Copyright 2020-2021 NXP
 **
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
 */
#include <vector>
#include <iomanip>

#include <EseTransportUtils.h>

namespace keymint::javacard {
 // Helper method to dump vector contents
std::ostream& operator<<(std::ostream& os, const std::vector<uint8_t>& vec) {
  std::ios_base::fmtflags flags(os.flags());
  os << "{ ";
  for (uint8_t c : vec) os <<std::setfill('0')<<std::hex<< std::uppercase << std::setw(2)<<(0xFF & c);
  os.flags(flags);
  os << " }";
  return os;
}
} // namespace keymint::javacard
