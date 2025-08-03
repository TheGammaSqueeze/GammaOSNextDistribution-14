#define MAX 2

struct foo {
  int bar[MAX];
};

void baz(const struct foo* z) { (void) z; }
