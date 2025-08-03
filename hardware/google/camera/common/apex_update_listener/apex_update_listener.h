#pragma once

#include <set>
#include <sstream>
#include <string>
#include <string_view>
#include <thread>

class ApexUpdateListener {
 private:
  struct Sigil {};

 public:
  // Information extracted from updated /apex/apex-info-list.xml
  struct Info {
    std::string module_name;
    std::string module_path;
    std::string preinstalled_module_path;
    int64_t version_code;
    std::string version_name;
    bool is_factory;
    bool is_active;

    auto AsTuple() const {
      return std::make_tuple(is_active, is_factory, version_code, version_name,
                             module_path, preinstalled_module_path, module_name);
    }

    bool operator<(const Info& other) const {
      return AsTuple() < other.AsTuple();
    }

    bool operator==(const ApexUpdateListener::Info& other) const {
      return !(other < *this) && !(*this < other);
    }
    bool operator!=(const ApexUpdateListener::Info& other) const {
      return !(*this == other);
    }
    template <typename T>
    friend auto& operator<<(T& stream, const ApexUpdateListener::Info& i) {
      return stream << "{ moduleName: " << i.module_name
                    << ", modulePath: " << i.module_path
                    << ", preinstalledModulePath: " << i.preinstalled_module_path
                    << ", versionCode: " << i.version_code
                    << ", versionName: " << i.version_name
                    << ", i.isFactory: " << i.is_factory
                    << ", i.isActive: " << i.is_active << " }";
    }
    template <typename T>
    friend auto& operator<<(T& stream,
                            const std::set<ApexUpdateListener::Info>& s) {
      stream << "{";
      std::string sep = "";
      for (auto& i : s) {
        stream << sep << i;
        sep = ", ";
      }
      return stream << "}";
    }
  };

  using CallbackFunction =
      std::function<void(const std::set<Info>& last_versions,
                         const std::set<Info>& current_versions)>;

  ApexUpdateListener(Sigil, const std::string& apex_name,
                     const std::string& apex_info_list_file_name,
                     CallbackFunction callback, int fd, int wd,
                     const std::set<Info>& last_info);

  static std::unique_ptr<ApexUpdateListener> Make(
      const std::string& apex_name, CallbackFunction callback,
      bool invoke_with_initial_version = false,
      const std::string& apex_info_list_file_name = "/apex/apex-info-list.xml");

  // We need some cleanup handling
  ~ApexUpdateListener();

 private:
  const std::string apex_name_;
  const std::string apex_info_list_file_name_;
  const CallbackFunction callback_function_;
  std::atomic<bool> running_ = true;
  int file_descriptor_ = -1;
  int watch_descriptor_ = -1;
  std::set<Info> last_info_;
  std::thread thread_;

  void ThreadFunction();
  static std::optional<std::set<Info>> TrySlurpInfo(
      const std::string& apex_name, const std::string& apex_info_list_file_name);
};
