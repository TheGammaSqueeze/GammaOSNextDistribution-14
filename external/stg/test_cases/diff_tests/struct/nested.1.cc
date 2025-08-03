struct nested {
  long x;
};

struct containing {
  struct nested inner;
};

struct referring {
  struct nested * inner;
};

void tweak(int);
void register_ops6(containing) { tweak(6); }
void register_ops7(containing*) { tweak(7); }
void register_ops8(referring) { tweak(8); }
void register_ops9(referring*) { tweak(9); }
