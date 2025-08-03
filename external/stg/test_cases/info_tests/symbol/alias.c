// TODO: Fix name demangling of aliases.
int x = 0;
extern int y __attribute__ ((alias ("x")));
extern int z __attribute__ ((weak, alias ("y")));

int a () { return 0; }
extern int b () __attribute__ ((alias ("a")));
extern int c () __attribute__ ((weak, alias ("b")));
