#!/bin/sh
# Gradle wrapper stub for DALUR film. Prefers local D:\tools\gradle-8.7 if present.
APP_BASE_NAME=${0##*/}
APP_HOME=$(cd "${APP_BASE_NAME%/*}" >/dev/null; pwd)
JAVA_HOME="${JAVA_HOME:-C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot}"
if [ -x "/d/tools/gradle-8.7/bin/gradle" ]; then
  exec "/d/tools/gradle-8.7/bin/gradle" "$@"
elif [ -x "D:\tools\gradle-8.7\bin\gradle.bat" ]; then
  exec "D:\tools\gradle-8.7\bin\gradle.bat" "$@"
else
  echo "Gradle 8.7 not found. Install from https://services.gradle.org/distributions/gradle-8.7-bin.zip" >&2
  exit 1
fi
