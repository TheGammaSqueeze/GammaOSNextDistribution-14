enum A : unsigned char {
  Ae = 0,
};

enum B : unsigned long {
  Be = 0,
};

unsigned int fun(A a, B b) {
  return sizeof(a) + sizeof(b);
}
