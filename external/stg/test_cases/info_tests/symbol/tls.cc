// Test has to include at least two TLS symbols to exercise matching.

thread_local int foo = 0;

namespace ns {

thread_local short foo = 0;

}

int bar() {
  thread_local static long local_foo = 0;
  return foo + ns::foo + local_foo;
}
