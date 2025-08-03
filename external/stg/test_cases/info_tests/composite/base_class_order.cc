struct A {
  int x;
};

struct B {
  int y;
};

struct C {
  int z;
};

struct AddRemove : C, B {
  int m;
};

struct DiffOrder : B, A, C {
  int m;
};

AddRemove add_remove;
DiffOrder diff_order;
