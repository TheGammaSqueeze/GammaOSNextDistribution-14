typedef int thing;

struct s {
  int a;
  int * b;
  int c[7];
  int (*d)(void);
  thing e;
};

void f(struct s t) {
  (void) t;
}
