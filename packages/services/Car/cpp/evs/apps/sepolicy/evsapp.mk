# Selinux policies for the sample EVS application
PRODUCT_PRIVATE_SEPOLICY_DIRS += packages/services/Car/cpp/evs/apps/sepolicy/private

ifeq ($(ENABLE_CARTELEMETRY_SERVICE), true)
PRODUCT_PRIVATE_SEPOLICY_DIRS += packages/services/Car/cpp/evs/apps/sepolicy/cartelemetry
endif
