#!/usr/bin/env bash
set -euo pipefail
# Xcode does not inherit shell/Android Studio environment configuration.
# Default device and simulator builds to QA; explicitly opt into development.
TCH_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export APP_ENVIRONMENT="${APP_ENVIRONMENT:-homologation}"
exec bash "$TCH_ROOT/scripts/gradlew-local.sh" \
  "-Papp.environment=$APP_ENVIRONMENT" \
  :composeApp:embedAndSignAppleFrameworkForXcode "$@"
