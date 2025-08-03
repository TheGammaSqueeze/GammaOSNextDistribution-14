typedef short small;
typedef int large;

struct foo {
  large x;
};

long id1(struct foo q) { return q.x; }
long id2(large q) { return q; }
