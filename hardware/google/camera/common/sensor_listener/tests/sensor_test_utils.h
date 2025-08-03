#include <atomic>
#include <sstream>

#include <log/log.h>
#include <utils/SystemClock.h>

namespace android {
namespace camera_sensor_listener {
namespace {

// Based on input filename, generate output filename including
// full path and timestamp.
// Input:
//   input_filename: partial filename, usually log type.
// Output:
//   output_full_filename: final output filename with full path.
// Example:
//   input_filename: gyro_direct_log
//   output_full_filename:
//     /vendor/bin/lib_sensor_listener_test/gyro_direct_log_%h%m%s.txt
bool GenerateLogFilename(const std::string& input_filename,
                         std::string* output_full_filename) {
  const char* path = "/vendor/bin/lib_sensor_listener_test/";
  struct stat directory_stat;
  memset(&directory_stat, 0, sizeof(directory_stat));
  if (stat(path, &directory_stat) != 0) {
    // Create the directory if it doesn't exist.
    if (errno == ENOENT) {
      if (mkdir(path, S_IRWXU | S_IRWXG | S_IRWXO) != 0) {
        ALOGE("%s: %d: createing %s failed. (errno: %d)", __func__, __LINE__,
              path, errno);
        return false;
      }
    } else {
      ALOGE("%s: %d: stat(%s) failed: (errno: %d).", __func__, __LINE__, path,
            errno);
      return false;
    }
  }

  char timebuf[256] = {0};
  struct timeval now_time;
  gettimeofday(&now_time, nullptr);
  strftime(timebuf, 256, "%H%M%S", localtime(&now_time.tv_sec));

  std::stringstream ss;
  ss << path << input_filename << "_" << timebuf << "_" << now_time.tv_usec
     << ".txt";
  ss >> *output_full_filename;
  return true;
}

}  // namespace
}  // namespace camera_sensor_listener
}  // namespace android
