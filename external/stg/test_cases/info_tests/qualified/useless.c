void tweak(int);

struct foo {
};

void bar_2(struct foo* y) {
  (void) y;
  tweak(0);
}

void bar(const volatile struct foo* y) {
  (void) y;
  tweak(1);
}

void baz(void(*const volatile y)(const volatile struct foo*)) {
  (void) y;
  tweak(2);
}

void(*const volatile quux)(const volatile struct foo*) = &bar;
