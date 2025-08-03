struct Foo {
  class {
    long x;
  } anon_class;
  struct {
    long x;
  } anon_struct;
  union {
    long x;
  } anon_union;
  enum { X = 2 } anon_enum;
};

Foo var;
