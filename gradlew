#!/bin/sh
# Simplified Gradle wrapper launcher for A3 core.
APP_HOME=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
JAVACMD="$JAVA_HOME/bin/java"
if [ ! -x "$JAVACMD" ]; then
  JAVACMD=$(command -v java || true)
fi
if [ -z "$JAVACMD" ] || [ ! -x "$JAVACMD" ]; then
  echo "ERROR: JAVA_HOME is not set and no 'java' command could be found." >&2
  exit 1
fi
DEFAULT_JVM_OPTS='-Xmx64m -Xms64m'
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
exec "$JAVACMD" $DEFAULT_JVM_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
