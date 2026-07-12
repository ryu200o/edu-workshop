#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# create-module.sh — Scaffold a new module skeleton for this Modular Monolith
# (Spring Modulith + Hexagonal Architecture + DDD Tactical).
#
# Usage:  bash create-module.sh <module-name>
#
# Example: bash create-module.sh testmodule
#   -> creates: src/main/java/<base-package>/testmodule/
# ---------------------------------------------------------------------------

if [ "$#" -ne 1 ]; then
  echo "Usage: bash create-module.sh <module-name>" >&2
  exit 1
fi

MODULE_NAME="$1"

# --- Validate module name (lowercase, digits, underscores only) ------------
if [[ ! "$MODULE_NAME" =~ ^[a-z][a-z0-9_]*$ ]]; then
  echo "Error: module name must start with a lowercase letter and contain only lowercase letters, digits or underscores." >&2
  exit 1
fi

# --- Detect base package from existing source tree -------------------------
# Find the root source package directory by locating the main application class.
SRC_MAIN="src/main/java"
APP_FILE=$(find "$SRC_MAIN" -name "*Application.java" | head -1)

if [ -z "$APP_FILE" ]; then
  echo "Error: could not locate the main Application class to derive the base package." >&2
  exit 1
fi

# Derive base package: strip src/main/java/ prefix and the file name.
REL_PATH="${APP_FILE#$SRC_MAIN/}"
BASE_PKG=$(dirname "$REL_PATH" | tr '/' '.')
echo "Detected base package: $BASE_PKG"

# --- Derive Java-ish names --------------------------------------------------
# Module package name stays as-is (e.g. testmodule).
MOD_PKG="$BASE_PKG.$MODULE_NAME"
# CamelCase module name: testmodule -> Testmodule, order_item -> OrderItem
MODULE_NAME_CAMEL=$(echo "$MODULE_NAME" | sed -E 's/_([a-z])/\U\1/g; s/^([a-z])/\U\1/')
API_IFACE="${MODULE_NAME_CAMEL}ExposeAPI"
API_IMPL="${MODULE_NAME_CAMEL}ExposeAPIImpl"

# --- Directory tree to create ----------------------------------------------
DIRS=(
  "contract/dto"
  "contract/events"
  "internal/domain/service"
  "internal/domain/model/entity"
  "internal/domain/model/state"
  "internal/domain/model/event"
  "internal/domain/model/exception"
  "internal/application/port/in/command"
  "internal/application/port/in/query"
  "internal/application/port/out"
  "internal/application/handler"
  "internal/application/event"
  "internal/application/mapper"
  "internal/adapter/driving/module_api"
  "internal/adapter/driving/http"
  "internal/adapter/driven/persistence"
)

MODULE_ROOT="$SRC_MAIN/${MOD_PKG//./\/}"
for d in "${DIRS[@]}"; do
  mkdir -p "$MODULE_ROOT/$d"
done

# --- Generate ExposeAPI interface (public, module root) --------------------
API_IFACE_FILE="$MODULE_ROOT/$API_IFACE.java"
cat > "$API_IFACE_FILE" <<EOF
package $MOD_PKG;

/**
 * Public inter-module communication interface for the ${MODULE_NAME} module.
 * This is the only surface exposed to other modules.
 */
public interface $API_IFACE {
}
EOF

# --- Generate ExposeAPIImpl (package-private, module_api adapter) -----------
API_IMPL_FILE="$MODULE_ROOT/internal/adapter/driving/module_api/$API_IMPL.java"
cat > "$API_IMPL_FILE" <<EOF
package $MOD_PKG.internal.adapter.driving.module_api;

import $MOD_PKG.$API_IFACE;

/**
 * Package-private implementation of {@link $API_IFACE}.
 * Resides inside the information-hiding boundary (internal/.../module_api).
 */
class $API_IMPL implements $API_IFACE {
}
EOF

echo "Created module '$MODULE_NAME' at $MODULE_ROOT"
echo "  - $API_IFACE.java (public interface, module root)"
echo "  - internal/adapter/driving/module_api/$API_IMPL.java (package-private impl)"
