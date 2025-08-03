struct A {
  unsigned int x;
};

struct B {
  long y;
};

struct SameSize : A {
  int z;
};

struct DiffSize : B {
  int z;
};

SameSize same_size;
DiffSize diff_size;
