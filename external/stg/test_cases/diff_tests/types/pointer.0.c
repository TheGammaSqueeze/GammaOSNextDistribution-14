struct foo {
  int x;
};

int a;
struct foo b = { 0 };
struct foo * c;
struct foo * * e = &c;
const struct foo g = { 0 };
const struct foo * h = &g;
const struct foo * * j = &h;
