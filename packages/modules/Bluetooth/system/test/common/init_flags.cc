

#include "common/init_flags.h"

#include <map>
#include <string>

namespace bluetooth {
namespace common {

bool InitFlags::logging_debug_enabled_for_all = false;
bool InitFlags::leaudio_targeted_announcement_reconnection_mode = true;
std::unordered_map<std::string, bool>
    InitFlags::logging_debug_explicit_tag_settings = {};
void InitFlags::Load(const char** flags) {}
void InitFlags::SetAll(bool value) {
  InitFlags::logging_debug_enabled_for_all = value;
}
void InitFlags::SetAllForTesting() {
  InitFlags::logging_debug_enabled_for_all = true;
}

}  // namespace common
}  // namespace bluetooth
