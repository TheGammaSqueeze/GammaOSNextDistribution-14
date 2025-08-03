#include <other_package/IBaz.h>

// Enum type should be available here since IBaz.h includes Enum.h
void acceptEnum(other_package::Enum n) {
  (void)n;
}
