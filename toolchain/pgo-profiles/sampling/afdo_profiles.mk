# AFDO_PROFILES maps a cc module name to a fully qualified fdo_profile module name
# AFDO_PROFILES is used to construct AfdoProfiles product var in Soong
# AFDO_PROFILES is applied to all products while PRODUCT_AFDO_PROFILES, which is set
# in a product config, has the ability to override it with custom profile
AFDO_PROFILES += keystore2://toolchain/pgo-profiles/sampling:keystore2 \
	libbinder://toolchain/pgo-profiles/sampling:libbinder \
	libbinder_ndk://toolchain/pgo-profiles/sampling:libbinder_ndk \
	libc++://toolchain/pgo-profiles/sampling:libc++ \
	libgui://toolchain/pgo-profiles/sampling:libgui \
	libhidlbase://toolchain/pgo-profiles/sampling:libhidlbase \
	libhwui://toolchain/pgo-profiles/sampling:libhwui \
	liblog://toolchain/pgo-profiles/sampling:liblog \
	libsensorservice://toolchain/pgo-profiles/sampling:libsensorservice \
	libui://toolchain/pgo-profiles/sampling:libui \
	libutils://toolchain/pgo-profiles/sampling:libutils \
	lmkd://toolchain/pgo-profiles/sampling:lmkd \
	surfaceflinger://toolchain/pgo-profiles/sampling:surfaceflinger \
	libart://toolchain/pgo-profiles/sampling:libart \
	libartbase://toolchain/pgo-profiles/sampling:libartbase \
	linker://toolchain/pgo-profiles/sampling:linker \
	libsqlite://toolchain/pgo-profiles/sampling:libsqlite \
	libcrypto://toolchain/pgo-profiles/sampling:libcrypto \
	server_configurable_flags://toolchain/pgo-profiles/sampling:server_configurable_flags
