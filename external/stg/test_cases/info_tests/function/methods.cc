struct Func {
  // change
  long change_return_type();

  // add or remove
  int add_par(int add);
  int remove_par();
  int change_par_type(long par);
  int rename_new();

  // no diff
  int change_par_name(int add);

  long x;
} var;

long Func::change_return_type() { return 0; }
int Func::add_par(int add) { return add; }
int Func::remove_par() { return 0; }
int Func::change_par_type(long par) { return par; }
int Func::rename_new() { return 0; }
int Func::change_par_name(int add) { return add; }
