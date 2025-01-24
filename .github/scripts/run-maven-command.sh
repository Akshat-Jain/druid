#!/bin/bash

set -e
set -x

eval mvn -B "$@"
