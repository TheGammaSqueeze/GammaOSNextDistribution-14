struct A {
  int x;
};

struct B {
  int y;
};

struct SameSize : A {
  int z;
};

struct DiffSize : B {
  int z;
};

SameSize same_size;
DiffSize diff_size;
