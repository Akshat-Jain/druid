#!/bin/bash

# Log received parameters
echo "run-tests.sh received parameters: $@"

# Parse arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --dtest)
      DTEST_PATTERN="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1"
      exit 1
      ;;
  esac
done

# Execute tests
if [ -n "$DTEST_PATTERN" ]; then
  echo "Using test pattern: $DTEST_PATTERN"
  mvn test -Dtest="$DTEST_PATTERN"
else
  echo "No test pattern provided, running all tests"
  mvn test
fi 