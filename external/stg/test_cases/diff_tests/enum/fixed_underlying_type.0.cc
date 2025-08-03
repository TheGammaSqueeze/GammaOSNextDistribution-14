enum A : signed char {
  Ae = 0,
};

enum B : signed char {
  Be = 0,
};

unsigned int fun(A a, B b) {
  return sizeof(a) + sizeof(b);
}
