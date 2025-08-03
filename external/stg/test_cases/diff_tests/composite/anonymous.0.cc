struct Foo {
  class {
    int x;
  } anon_class;
  struct {
    int x;
  } anon_struct;
  union {
    int x;
  } anon_union;
  enum { X = 1 } anon_enum;
};

Foo var;
