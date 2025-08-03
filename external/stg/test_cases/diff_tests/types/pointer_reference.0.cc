struct foo {
  int x = 0;
};

int a;
foo b;
foo * c;
foo & d = b;
foo * * e = &c;
foo * & f = c;
const foo g;
const foo * h = &g;
const foo & i = g;
const foo * * j = &h;
const foo * & k = h;
