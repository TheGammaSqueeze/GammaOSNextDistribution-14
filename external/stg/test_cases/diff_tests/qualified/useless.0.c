void tweak(int);

struct foo {
};

void bar(struct foo y) {
  (void) y;
  tweak(0);
}

void bar_1(const volatile struct foo* y) {
  (void) y;
  tweak(1);
}

void baz(void(*y)(struct foo)) {
  (void) y;
  tweak(2);
}

void(*quux)(struct foo) = &bar;
