namespace unchanged_n {
int var;
}  // namespace unchanged_n

namespace added_n {
int var;
}  // namespace removed_n

// Difference: every int below changed to long
namespace foo {
long x1;

long x2[5];

extern const long x3 = 5;

typedef long type_definition;
type_definition x4;

long x5() {
  return 0;
}

struct S {
  long x;
} x6;

union U {
  long x;
} x7;

enum class E { X, Y, Z } x8;

}  // namespace foo
