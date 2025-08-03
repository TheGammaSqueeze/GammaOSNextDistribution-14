typedef int INT_1;
typedef INT_1 INT_2;
typedef INT_2 INT_3;

struct foo {
  INT_3 x;
};

int func(struct foo f) { return f.x; }
