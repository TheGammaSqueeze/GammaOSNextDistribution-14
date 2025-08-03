#define INT_MAX 2147483647
#define LONG_MAX 9223372036854775807L
#define LONG_MIN (-LONG_MAX - 1L)

enum A {
  Ae = 1ull << 24,
};

enum B {
  Be = INT_MAX,
};

enum C {
  Ce = LONG_MIN,
};

enum D {
  De = -1,
};

unsigned int fun(enum A a, enum B b, enum C c, enum D d) {
  return sizeof(a) + sizeof(b) + sizeof(c) + sizeof(d);
}
