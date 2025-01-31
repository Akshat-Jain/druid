#!/bin/bash

set -e
set -x

SAFE_DTEST="$(echo "$1" | sed 's/[*]//g' | sed 's/,/-/g')"
.github/scripts/run-maven-command.sh test -pl '!integration-tests,!:druid-it-tools,!:druid-it-image,!:druid-it-cases' -Dtest="$1" -Dsurefire.failIfNoSpecifiedTests=false -P skip-static-checks -Dweb.console.skip=true -Dmaven.javadoc.skip=true -Dmaven.test.failure.ignore=true -Djacoco.destFile="target/jacoco-${SAFE_DTEST}.exec"
