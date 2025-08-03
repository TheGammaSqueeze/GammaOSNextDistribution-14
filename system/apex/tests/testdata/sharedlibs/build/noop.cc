#include <iostream>

#include "sharedlibstest.h"

int main() {
  std::cout << "This binary should never be executed";
  std::cout << sharedlibstest::getSharedLibsTestFingerprint();
  return 1;
}