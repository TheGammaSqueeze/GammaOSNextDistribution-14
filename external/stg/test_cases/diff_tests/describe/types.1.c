struct amusement {
  // declare A as array 7 of int
  int A[7];
  // declare B as pointer to int
  int *B;
  // declare C as pointer to function (void) returning int
  int (*C)(void );
  // declare D as array 7 of array 7 of int
  int D[7][7];
  // declare E as array 7 of pointer to int
  int *E[7];
  // declare F as array 7 of pointer to function (void) returning int
  int (*F[7])(void );
  // declare G as pointer to array 7 of int
  int (*G)[7];
  // declare H as pointer to pointer to int
  int **H;
  // declare I as pointer to function (void) returning int
  int (*I)(void );
  // declare J as pointer to function (void) returning pointer to array 7 of int
  int (*(*J)(void ))[7];
  // declare K as pointer to function (void) returning pointer to int
  int *(*K)(void );
  // declare L as pointer to function (void) returning pointer to function (void) returning int
  int (*(*L)(void ))(void );

  // declare a as array 7 of volatile int
  volatile int a[7];
  // declare b as const pointer to volatile int
  volatile int * const b;
  // declare c as const pointer to function (void) returning int
  int (* const c)(void );
  // declare d as array 7 of array 7 of volatile int
  volatile int d[7][7];
  // declare e as array 7 of const pointer to volatile int
  volatile int * const e[7];
  // declare f as array 7 of const pointer to function (void) returning int
  int (* const f[7])(void );
  // declare g as const pointer to array 7 of volatile int
  volatile int (* const g)[7];
  // declare h as const pointer to const pointer to volatile int
  volatile int * const * const h;
  // declare i as const pointer to function (void) returning int
  int (* const i)(void );
  // declare j as const pointer to function (void) returning pointer to array 7 of volatile int
  volatile int (*(* const j)(void ))[7];
  // declare k as const pointer to function (void) returning pointer to volatile int
  volatile int *(* const k)(void );
  // declare l as const pointer to function (void) returning pointer to function (void) returning int
  int (*(* const l)(void ))(void );
};

struct amusement * fun(void) { return 0; }

void tweak(int);

// declare M as function (void) returning int
int M(void ) { tweak(0); return 0; }
// declare N as function (void) returning pointer to array 7 of int
int (*N(void ))[7] { tweak(1); return 0; }
// declare O as function (void) returning pointer to int
int *O(void ) { tweak(2); return 0; }
// declare P as function (void) returning pointer to function (void) returning int
int (*P(void ))(void ) { tweak(3); return 0; }

// declare m as function (void) returning int
int m(void ) { tweak(4); return 0; }
// declare n as function (void) returning pointer to array 7 of volatile int
volatile int (*n(void ))[7] { tweak(5); return 0; }
// declare o as function (void) returning pointer to volatile int
volatile int *o(void ) { tweak(6); return 0; }
// declare p as function (void) returning pointer to function (void) returning int
int (*p(void ))(void ) { tweak(7); return 0; }
