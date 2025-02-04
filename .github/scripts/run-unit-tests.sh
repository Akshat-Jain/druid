#!/bin/bash

set -e
set -x

JACOCO_REPORT_HASH="$(echo "$*" | md5sum | cut -d ' ' -f1)"
OPTS+="-pl !integration-tests,!:druid-it-tools,!:druid-it-image,!:druid-it-cases"
OPTS+=" -Dsurefire.failIfNoSpecifiedTests=false -P skip-static-checks -Dweb.console.skip=true -Dmaven.javadoc.skip=true"
OPTS+=" -Djacoco.destFile='target/jacoco-${JACOCO_REPORT_HASH}.exec'"

mvn -B $OPTS test "$@"
