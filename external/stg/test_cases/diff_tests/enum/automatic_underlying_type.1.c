#define INT_MAX 2147483647
#define LONG_MAX 9223372036854775807L
#define ULONG_MAX 18446744073709551615UL

enum A {
  Ae = 1ull << 48,
};

enum B {
  Be = INT_MAX + 1ull,
};

enum C {
  Ce = ULONG_MAX,
};

enum D {
  De = 1,
};

unsigned int fun(enum A a, enum B b, enum C c, enum D d) {
  return sizeof(a) + sizeof(b) + sizeof(c) + sizeof(d);
}
