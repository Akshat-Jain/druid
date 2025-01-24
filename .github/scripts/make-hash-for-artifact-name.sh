#!/bin/bash

set -e
set -x

HASH=$(echo -n "$1" | sha256sum | cut -d' ' -f1 | cut -c1-8)
echo "HASH=$HASH" >> $GITHUB_ENV
