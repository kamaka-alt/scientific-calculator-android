#!/bin/sh
# Gradle wrapper stub — GitHub Actions will replace this with the real wrapper
# if it runs `gradle wrapper` first. For local builds use: gradle assembleDebug

GRADLE_OPTS="${GRADLE_OPTS:-"-Xmx1536m"}"
exec gradle "$@"
