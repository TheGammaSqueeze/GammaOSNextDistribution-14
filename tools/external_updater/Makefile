# TODO: Move sources into a package.
SRC_PATHS := *.py tests
# This is distinct from SRC_PATHS because not all the tests can be run with
# pytest. Any test that touches the METADATA file (or even imports a module that
# does) must be run via soong.
PYTEST_PATHS := tests

.PHONY: check
check: lint test
.DEFAULT_GOAL: check

.PHONY: lint
lint:
	mypy $(SRC_PATHS)
	pylint $(SRC_PATHS)

.PHONY: test
test:
	pytest $(PYTEST_PATHS)
	atest --host-unit-test-only
