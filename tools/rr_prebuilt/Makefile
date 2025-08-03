SRC_PATHS := rrprebuiltupdater tests

check: format lint test
.DEFAULT_GOAL: check

format:
	poetry run black $(SRC_PATHS)
	poetry run isort $(SRC_PATHS)

lint:
	poetry run mypy $(SRC_PATHS)
	poetry run pylint $(SRC_PATHS)

test:
	poetry run pytest \
		--cov-report=term \
		--cov-report=xml \
		--cov rrprebuiltupdater \
		$(SRC_PATHS)

.PHONY: format lint test check
