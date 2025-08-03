#include <string.h>
#include <stdlib.h>

void BuggyCode1(const char *data) {
  if (strcmp(data, "Hi!") == 0) {
    abort();  // Boom!
  }
}

void BuggyCode2(const char *data) {
  if (strcmp(data, "Hey") == 0) {
    abort();  // Boom!
  }
}