#!/system/bin/sh
# Unlock CE storage during Nano boot using the credential saved by
# LockSettingsService.saveNanoBootCredential().

CRED_FILE="/data/system_de/0/nano_credential"
TAG="nano-ce-unlock"

log_i() { log -t "$TAG" -p i "$1"; }
log_e() { log -t "$TAG" -p e "$1"; }

if [ "$(getprop sys.user.0.ce_available)" = "true" ]; then
    log_i "CE already unlocked, nothing to do"
    exit 0
fi

if [ ! -f "$CRED_FILE" ]; then
    log_i "No saved credential found, CE will stay locked"
    exit 0
fi

CRED=$(cat "$CRED_FILE")
if [ -z "$CRED" ]; then
    log_i "Saved credential is empty, skipping"
    exit 0
fi

log_i "Attempting CE unlock with saved credential"

# Wait for lock_settings service to be available (up to 30s)
i=0
while [ $i -lt 60 ]; do
    if service check lock_settings 2>/dev/null | grep -q "not found"; then
        sleep 0.5
        i=$((i + 1))
    else
        break
    fi
done

locksettings verify --old "$CRED" >/dev/null 2>&1

# ce_available may take a moment to propagate after unlock
i=0
while [ $i -lt 20 ]; do
    if [ "$(getprop sys.user.0.ce_available)" = "true" ]; then
        log_i "CE storage unlocked successfully"
        exit 0
    fi
    sleep 0.5
    i=$((i + 1))
done

log_e "CE unlock timed out, credential may be stale"
