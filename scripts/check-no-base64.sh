#!/bin/sh
# CVE-2026-114938 ("Java4Shell") policy gate.
#
# The HotSpot intrinsics of the JDK Base64 decoder are the attack surface of
# CVE-2026-114938. This project never encodes or decodes Base64: all
# binary-to-text output (ciphertexts, keys, hashes) must use
# com.devhub.batch.core.crypto.HexCodec instead.
#
# CI fails if the banned decoder is referenced anywhere in source or config.
set -e

HITS=$(grep -rn "java\.util\.Base64" --include='*.java' --include='*.xml' --include='*.properties' --include='*.sql' src config 2>/dev/null || true)

if [ -n "$HITS" ]; then
  echo "ERROR: banned Base64 decoder referenced (CVE-2026-114938 policy):"
  echo "$HITS"
  echo "Use com.devhub.batch.core.crypto.HexCodec for binary-to-text encoding."
  exit 1
fi

echo "OK: no banned Base64 decoder usage found."
