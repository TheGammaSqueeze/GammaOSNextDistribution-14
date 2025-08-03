typedef short small;
typedef int large;

struct foo {
  small x;
};

long id1(struct foo q) { return q.x; }
long id2(small q) { return q; }
