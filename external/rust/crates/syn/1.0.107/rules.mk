LOCAL_DIR := $(GET_LOCAL_DIR)
MODULE := $(LOCAL_DIR)
MODULE_CRATE_NAME := syn
MODULE_SRCS := \
	$(LOCAL_DIR)/src/lib.rs \

# The crate name is still syn but the file name
# should be libsyn_deprecated.rlib
MODULE_RUST_STEM := syn_1.0.107

MODULE_RUST_EDITION := 2018
MODULE_RUSTFLAGS += \
	--cfg 'feature="clone-impls"' \
	--cfg 'feature="default"' \
	--cfg 'feature="derive"' \
	--cfg 'feature="extra-traits"' \
	--cfg 'feature="full"' \
	--cfg 'feature="parsing"' \
	--cfg 'feature="printing"' \
	--cfg 'feature="proc-macro"' \
	--cfg 'feature="quote"' \
	--cfg 'feature="visit"' \
	--cfg 'feature="visit-mut"' \
	--cfg 'syn_disable_nightly_tests' \

MODULE_LIBRARY_DEPS := \
	external/rust/crates/proc-macro2 \
	external/rust/crates/quote \
	external/rust/crates/unicode-ident \

include make/library.mk
