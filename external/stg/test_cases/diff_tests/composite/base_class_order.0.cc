struct A {
  int x;
};

struct B {
  int y;
};

struct C {
  int z;
};

struct AddRemove : A, C {
  int m;
};

struct DiffOrder : A, B, C {
  int m;
};

AddRemove add_remove;
DiffOrder diff_order;
