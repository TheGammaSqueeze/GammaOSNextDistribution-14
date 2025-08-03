enum E { x = 1 };
enum F;
struct S { int x; };
struct T;
union U { int x; };
union V;

// bodies differ due to https://gcc.gnu.org/bugzilla/show_bug.cgi?id=112372
int f1(enum E* a, enum F* b, struct S* c, struct T* d, union U* e, union V* f) {
  (void)a;
  (void)b;
  (void)c;
  (void)d;
  (void)e;
  (void)f;
  return 1;
};

struct K;
union L;
union M;
enum N;
enum O;
struct P;

int f2(struct K* v0, union L* v1, union M* v2, enum N* v3, enum O* v4, struct P* v5) {
  (void)v0;
  (void)v1;
  (void)v2;
  (void)v3;
  (void)v4;
  (void)v5;
  return 2;
};
