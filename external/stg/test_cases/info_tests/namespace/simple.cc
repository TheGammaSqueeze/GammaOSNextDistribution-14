namespace foo {
int x1;

int x2[5];

extern const int x3 = 5;

typedef int type_definition;
type_definition x4;

int x5() {
  return 0;
}

struct S {
  int x;
} x6;

union U {
  int x;
} x7;

enum class E { X, Y } x8;

}  // namespace foo
