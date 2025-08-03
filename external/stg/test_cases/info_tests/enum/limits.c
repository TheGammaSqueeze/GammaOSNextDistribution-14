#define INT_MAX 2147483647
#define INT_MIN (-INT_MAX - 1)
#define UINT_MAX 4294967295U
#define LLONG_MAX 9223372036854775807LL
#define LLONG_MIN (-LLONG_MAX - 1LL)
#define ULLONG_MAX 18446744073709551615ULL

enum A {
  Ae = INT_MIN,
} a;

enum B {
  Be = -1,
} b;

enum C {
  Ce = INT_MAX,
} c;

enum D {
  De = INT_MAX + 1U,
} d;

enum E {
  Ee = LLONG_MIN,
} e;

enum F {
  Fe = LLONG_MAX,
} f;

enum G {
  Ge = 1ULL << 24,  // 16777216
} G;

enum H {
  He = 1ULL << 48,  // 281474976710656
} h;

enum I {
  Ie = UINT_MAX,
} i;

enum J {
  Je = ULLONG_MAX,
} j;
