struct foo {
  long x = 0;
};

int a;
foo b;
foo * c;
foo & d = b;
foo * * e;
foo * & f = c;
const foo g;
const foo * h;
const foo & i = g;
const foo * * j;
const foo * & k = h;
