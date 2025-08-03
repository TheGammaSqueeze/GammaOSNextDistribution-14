struct A {
  union {
    struct {
      int x;
    };
    struct {
      char y;
    };
  };
};

struct A foo() {
  struct A a;
  a.x = 0;
  a.y = 'a';
  return a;
}
