### Why is gabeldorsche plural?

Please see this [informative video we've prepared](https://www.youtube.com/watch?v=vLRyJ0dawjM).

### Architecture

Guidelines for developing the Gabeldorsche (GD) stack

*   [Architecture](./docs/architecture/architecture.md)
*   [Style Guide](./docs/architecture/style_guide.md)

### Testing

Gabeldorsche (GD) was built with test driven development in mind. Three types of
tests are used in ensuring Gabeldorsche stack's stability, correctness and free
from regression.

If you are verifying something is glued or hooked up correctly inside the stack,
use a unit test.

*   [GTest Unit Test](./docs/testing/gtest.md)

If you are verifying correct behavior (especially interop problems) **DO NOT**
write a unit test as this not a good use of your time. Write a [cert test](./cert_test.md) instead
so it applies to any stack.

*   [GD Certification Tests](./docs/testing/cert_test.md)
