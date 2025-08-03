#include <iostream>
#include <string>

#include "sharedlibstest.h"

// This parameter gets modified by the build_artifacts.sh script.
#define FINGERPRINT "VERSION_XXX"

int main() {
  std::cout << "FOO_" << FINGERPRINT << " "
            << sharedlibstest::getSharedLibsTestFingerprint();
  return 0;
}
