typedef long thing;

struct s {
  long a;
  long * b;
  long c[7];
  long (*d)(void);
  thing e;
};

void f(struct s t) {
  (void) t;
}
