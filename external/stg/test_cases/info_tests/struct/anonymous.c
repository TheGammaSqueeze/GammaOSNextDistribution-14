typedef struct {
  union {
    struct {
      unsigned int x;
      unsigned int y;
    };
    unsigned long long z;
  };
} Foo;

Foo var;
