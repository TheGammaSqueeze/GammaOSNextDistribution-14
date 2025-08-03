
#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include <iostream>

#include <string>

void CrashFunc(const char *data) {

  if (strcmp(data, "c") == 0) {
    volatile int* arr = new int[10];
    for (int i = 0; i < 15; i++){
        arr[i] = 54;
    }
  }
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
  std::string null_terminated_string(reinterpret_cast<const char *>(data),
                                     size);

  CrashFunc(null_terminated_string.c_str());
  return 0;
}
