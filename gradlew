#!/bin/sh
# Gradle wrapper launcher — standard Android project boilerplate
# https://docs.gradle.org/current/userguide/gradle_wrapper.html

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")
APP_HOME=$(CDPATH="" cd "$(dirname "$0")" && pwd)

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

DEFAULT_JVM_OPTS='-Xmx64m -Xms64m'

die () {
    echo
    echo "ERROR: $*"
    echo
    exit 1
}

if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
    if [ ! -x "$JAVACMD" ]; then
        die "JAVA_HOME is set to an invalid directory: $JAVA_HOME"
    fi
else
    JAVACMD="java"
    which java > /dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command found."
fi

exec "$JAVACMD" \
    $DEFAULT_JVM_OPTS \
    $JAVA_OPTS \
    $GRADLE_OPTS \
    "-Dorg.gradle.appname=$APP_BASE_NAME" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
