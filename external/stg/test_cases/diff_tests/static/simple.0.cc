struct ChangeType {
  static int st;
  static int print();
};
int ChangeType::print() { return st; };

// No change
struct ChangeOrder {
  static int st;
  static int print();
};
int ChangeOrder::print() { return st; }

struct Rename {
  static int st;
  static int print();
};
int Rename::print() { return st; }

ChangeType change_type;
ChangeOrder change_order;  // No change
Rename rename;

int ChangeType::st;
int ChangeOrder::st;  // No change
int Rename::st;

