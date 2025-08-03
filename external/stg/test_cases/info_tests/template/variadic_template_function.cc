template <class... Args> void func(Args&&...) {}
void f() {
    func(1, true, 'q');
}
