struct ChangeType {
  static long st;
  static long print();
};
long ChangeType::print() { return st; }

// No change
struct ChangeOrder {
  static int print();
  static int st;
};
int ChangeOrder::print() { return st; }

struct Rename {
  static int st_new;
  static int print_new();
};
int Rename::print_new() { return st_new; }

ChangeType change_type;
ChangeOrder change_order;  // No change
Rename rename;

long ChangeType::st;
int ChangeOrder::st;  // No change
int Rename::st_new;

