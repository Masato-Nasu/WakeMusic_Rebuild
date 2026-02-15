#!/usr/bin/env sh
# Simplified Gradle wrapper script. If this fails on your machine, use the one Android Studio generates.
DIR="$(cd "$(dirname "$0")" && pwd)"
exec java -jar "$DIR/gradle/wrapper/gradle-wrapper.jar" "$@"
