#define MAX 1

struct foo {
  int bar[MAX];
};

void baz(const struct foo* z) { (void) z; }
