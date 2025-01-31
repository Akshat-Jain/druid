#!/bin/bash

set -e
set -x

echo "GITHUB_BASE_REF: ${GITHUB_BASE_REF}"

echo "Printing git status:"
git status

echo "Printing git branch:"
git branch

echo "Printing git remote -v:"
git remote -v

echo "Printing all jacoco.exec files"
find . -name '*jacoco*.exec'

echo "Setting up git remote"
git remote set-branches --add origin ${GITHUB_BASE_REF}
git fetch

echo "Printing git branch:"
git branch

echo "Printing git branch -a:"
git branch -a

# Compile the project. jacoco:report needs class files along with jacoco.exec files to generate the report.
#mvn -B clean install -DskipTests -P skip-static-checks -Dweb.console.skip=true -Dmaven.javadoc.skip=true
#
## If there are multiple jacoco.exec files present in any module, merge them into a single jacoco.exec file for that module.
#mvn jacoco:merge
#
#mvn jacoco:report
#
#echo "GITHUB_BASE_REF: ${GITHUB_BASE_REF}"

#changed_files_x="$(git diff --name-only origin/akshat/new-workflow...HEAD | grep "\.java$" || [[ $? == 1 ]])"
#echo "changed_files_x: $changed_files_x"
#
changed_files="$(git diff --name-only remotes/origin/${GITHUB_BASE_REF}...HEAD | grep "\.java$" || [[ $? == 1 ]])"

echo "Changed files:"
for f in ${changed_files}
do
  echo $f
done
#
#npm install @connectis/diff-test-coverage@1.5.3
#
#if [ -n "${changed_files}" ]
#then
#  git diff origin/${GITHUB_BASE_REF}...HEAD -- ${changed_files} |
#  node_modules/.bin/diff-test-coverage \
#  --coverage "**/target/site/jacoco/jacoco.xml" \
#  --type jacoco \
#  --line-coverage 50 \
#  --branch-coverage 50 \
#  --function-coverage 0 \
#  --log-template "coverage-lines-complete" \
#  --log-template "coverage-files-complete" \
#  --log-template "totals-complete" \
#  --log-template "errors" \
#  -- ||
#  { printf "\n\n****FAILED****\nDiff code coverage check failed. To view coverage report, run 'mvn clean test jacoco:report' and open 'target/site/jacoco/index.html'\nFor more details on how to run code coverage locally, follow instructions here - https://github.com/apache/druid/blob/master/dev/code-review/code-coverage.md#running-code-coverage-locally\n\n" && echo "coverage_failure=true" >> "$GITHUB_ENV" && false; }
#fi
