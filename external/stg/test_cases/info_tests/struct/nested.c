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
void register_ops6(struct containing y) { (void) y; tweak(6); }
void register_ops7(struct containing* y) { (void) y; tweak(7); }
void register_ops8(struct referring y) { (void) y; tweak(8); }
void register_ops9(struct referring* y) { (void) y; tweak(9); }
