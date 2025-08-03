#include "apex_update_listener.h"
#include <log/log.h>
#include <sys/inotify.h>
#include <sys/types.h>
#include <tinyxml2.h>
#include <fstream>
#include <sstream>
#include <streambuf>
#include <string>
#include <vector>

#undef LOG_TAG
#define LOG_TAG "apex_update_listener"

namespace {

template <typename T>
std::string ToString(const T& value) {
  std::stringstream stream;
  stream << value;
  return stream.str();
}

}  // namespace

ApexUpdateListener::ApexUpdateListener(
    ApexUpdateListener::Sigil, const std::string& apex_name,
    const std::string& apex_info_list_file_name, CallbackFunction callback,
    int fd, int wd, const std::set<Info>& last_info)
    : apex_name_(apex_name),
      apex_info_list_file_name_(apex_info_list_file_name),
      callback_function_(callback),
      file_descriptor_(fd),
      watch_descriptor_(wd),
      last_info_(last_info),
      thread_(&ApexUpdateListener::ThreadFunction, this) {
}

ApexUpdateListener::~ApexUpdateListener() {
  running_ = false;
  if (watch_descriptor_ >= 0) {
    inotify_rm_watch(file_descriptor_, watch_descriptor_);
  }
  if (file_descriptor_ >= 0) {
    close(file_descriptor_);
  }
  if (thread_.joinable()) {
    thread_.join();
  }
}
std::unique_ptr<ApexUpdateListener> ApexUpdateListener::Make(
    const std::string& apex_name, CallbackFunction callback,
    bool invoke_with_initial_version,
    const std::string& apex_info_list_file_name) {
  int file_descriptor = inotify_init();
  if (file_descriptor < 0) {
    ALOGE("Failed to inotify_init(): %s (%d)", strerror(errno), errno);
    return nullptr;
  }
  int watch_descriptor = inotify_add_watch(
      file_descriptor, std::string(apex_info_list_file_name).c_str(),
      IN_MODIFY | IN_CREATE | IN_DELETE);
  if (watch_descriptor < 0) {
    ALOGE("Failed to inotify_add_watch(%s): %s (%d)",
          std::string(apex_info_list_file_name).c_str(), strerror(errno), errno);
    close(file_descriptor);
    return nullptr;
  }
  if (!callback) {
    ALOGE("Called with null callback");
    return nullptr;
  }
  auto last_info = TrySlurpInfo(apex_name, apex_info_list_file_name);
  if (!last_info.has_value()) {
    ALOGE("Could not slurp info from %s for package %s",
          std::string(apex_info_list_file_name).c_str(),
          std::string(apex_name).c_str());
    return nullptr;
  }
  if (invoke_with_initial_version) {
    callback(std::set<Info>(), *last_info);
  }

  return std::make_unique<ApexUpdateListener>(
      Sigil{}, apex_name, apex_info_list_file_name, callback, file_descriptor,
      watch_descriptor, *last_info);
}

std::optional<std::set<ApexUpdateListener::Info>>
ApexUpdateListener::TrySlurpInfo(const std::string& apex_name,
                                 const std::string& apex_info_list_file_name) {
  tinyxml2::XMLDocument xml_doc;
  auto status = xml_doc.LoadFile(apex_info_list_file_name.c_str());
  if (status != tinyxml2::XML_SUCCESS) {
    ALOGE("XML parsing failed: %d", status);
    return std::nullopt;
  }
  tinyxml2::XMLElement* apex_info_list =
      xml_doc.FirstChildElement("apex-info-list");
  if (!apex_info_list) {
    ALOGE("XML did not contain apex-info-list");
    return std::nullopt;
  }
  std::set<ApexUpdateListener::Info> ret;
  for (tinyxml2::XMLElement* apex_info =
           apex_info_list->FirstChildElement("apex-info");
       apex_info != nullptr;
       apex_info = apex_info->NextSiblingElement("apex-info")) {
    if (apex_info->Attribute("moduleName", apex_name.c_str())) {
      Info info{.module_name = apex_name.c_str()};
      auto module_path = apex_info->Attribute("modulePath");
      auto preinstalled_module_path =
          apex_info->Attribute("preinstalledModulePath");
      auto version_code = apex_info->Attribute("versionCode");
      auto version_name = apex_info->Attribute("versionName");
      auto is_active = apex_info->Attribute("isActive");
      auto is_factory = apex_info->Attribute("isFactory");
      if (module_path) {
        info.module_path = module_path;
      }
      if (preinstalled_module_path) {
        info.preinstalled_module_path = preinstalled_module_path;
      }
      if (version_code) {
        info.version_code = std::strtol(version_code, nullptr, 10);
      }
      if (version_name) {
        info.version_name = version_name;
      }
      if (is_active) {
        info.is_active = !strcmp(is_active, "true");
      }
      if (is_factory) {
        info.is_factory = !strcmp(is_factory, "true");
      }
      ret.insert(info);
    }
  }
  if (ret.size()) {
    return ret;
  }
  ALOGE("XML did not contain any apex-info about %s", apex_name.c_str());
  return std::nullopt;
}

void ApexUpdateListener::ThreadFunction() {
  // Maximum number of events to read at a time
  constexpr int event_number = 16;
  std::vector<struct inotify_event> events(event_number);
  do {
    auto length = read(file_descriptor_, events.data(),
                       event_number * sizeof(inotify_event));
    if (length == -EINTR) {
      continue;  // Interrupted by signal, try again
    }
    if (length < 0 || !running_) {
      if (running_) {
        ALOGE("Error reading inotify event from '%s': %s (%d)",
              apex_info_list_file_name_.c_str(), strerror(errno), errno);
      }
      return;
    }

    for (size_t i = 0; i < length / sizeof(inotify_event); i++) {
      struct inotify_event& event = events[i];

      if (event.mask & IN_CREATE) {
        ALOGI("%s created as %s", apex_info_list_file_name_.c_str(),
              (event.mask & IN_ISDIR) ? "directory" : "file");
      } else if (event.mask & IN_DELETE) {
        ALOGI("%s deleted as %s", apex_info_list_file_name_.c_str(),
              (event.mask & IN_ISDIR) ? "directory" : "file");
      } else if (event.mask & IN_MODIFY) {
        ALOGI("%s modified as %s", apex_info_list_file_name_.c_str(),
              (event.mask & IN_ISDIR) ? "directory" : "file");
      }
      // We don't really care how it was updated as long as it wasn't deleted
      if (event.mask & IN_DELETE) {
        continue;
      }
      auto info = TrySlurpInfo(apex_name_, apex_info_list_file_name_);
      if (info.has_value()) {
        if (*info != last_info_ && callback_function_) {
          callback_function_(last_info_, *info);
          last_info_ = *info;
        }
      }
    }
  } while (running_);
}
