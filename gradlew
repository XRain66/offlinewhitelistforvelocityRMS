#!/usr/bin/env sh

set -e

# Determine the directory of this script
DIR=$(dirname "$0")

# Use the Gradle wrapper jar file
if [ -z "$GRADLE_HOME" ]; then
    GRADLE_HOME="${DIR}/gradle/wrapper/gradle-wrapper.jar"
fi

exec java -jar "$GRADLE_HOME" "$@"
