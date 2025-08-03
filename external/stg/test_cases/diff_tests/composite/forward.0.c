enum E;
enum F { x = 1 };
struct S;
struct T { int x; };
union U;
union V { int x; };

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

enum K;
enum L;
struct M;
struct N;
union O;
union P;

int f2(enum K* v0, enum L* v1, struct M* v2, struct N* v3, union O* v4, union P* v5) {
  (void)v0;
  (void)v1;
  (void)v2;
  (void)v3;
  (void)v4;
  (void)v5;
  return 2;
};
