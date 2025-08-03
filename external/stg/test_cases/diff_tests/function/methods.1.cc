struct Func {
  // change
  long change_return_type();

  // add or remove
  int add_parameter(int);
  int remove_parameter();
  int change_parameter_type(long);
  int rename_new();

  // no diff
  int change_parameter_name(int);

  long x;
} var;

long Func::change_return_type() { return 0; }
int Func::add_parameter(int) { return 1; }
int Func::remove_parameter() { return 2; }
int Func::change_parameter_type(long) { return 3; }
int Func::rename_new() { return 4; }
int Func::change_parameter_name(int) { return 5; }
