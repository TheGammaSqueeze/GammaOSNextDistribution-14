android_add_library(
  TARGET util-lib
  LICENSE Apache-2.0
  SRC crash_report.cc
      crash_report.h
      filesystem.h
      ini_file.cc
      ini_file.h
      log.cc
      log.h
      os_utils.cc
      os_utils.h
      string_utils.cc
      string_utils.h)
target_include_directories(util-lib PRIVATE ..)
target_compile_definitions(util-lib PUBLIC NETSIM_ANDROID_EMULATOR)
