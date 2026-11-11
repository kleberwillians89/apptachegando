#!/usr/bin/env bash
set -euo pipefail
TCH_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -z "${JAVA_HOME:-}" ]] && [[ -x "$TCH_ROOT/KotlinProject/jdk-21.0.10+7/Contents/Home/bin/java" ]]; then
  export JAVA_HOME="$TCH_ROOT/KotlinProject/jdk-21.0.10+7/Contents/Home"
fi
if [[ -z "${JAVA_HOME:-}" ]] && [[ "$(uname)" == Darwin ]]; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
fi
cd "$TCH_ROOT/KotlinProject"
exec ./gradlew "$@"
