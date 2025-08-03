
struct S {
  int (*f01)();
  const int* (*f02)();
  int* const (*f03)();
  int* restrict (*f04)();
  const int* restrict (*f05)();
  int* restrict const (*f06)();
  int* const restrict (*f07)();
};

struct S s;
