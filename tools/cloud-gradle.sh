#!/usr/bin/env bash
# Build helper for this managed cloud's proxy and retained Java 21 toolchain.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${JAVA_HOME:-}" && -x /workspace/tools/jdk-21.0.8+9/bin/javac ]]; then
    export JAVA_HOME=/workspace/tools/jdk-21.0.8+9
fi
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/workspace/gradle-cache}"
if [[ -f /etc/codex/network-policy.json ]]; then
    export GRADLE_OPTS="${GRADLE_OPTS:-} -Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi
exec ./gradlew "$@"
