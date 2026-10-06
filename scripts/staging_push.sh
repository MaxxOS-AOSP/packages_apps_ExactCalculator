#!/usr/bin/env bash
set -euo pipefail

BRANCH="staging"
COMMIT_MESSAGE="MaxxOS: modernize calculator and add About page"

if [[ -n "$(git status --porcelain)" ]]; then
    echo "Creating/switching to ${BRANCH}..."
else
    echo "Working tree is clean; creating/switching to ${BRANCH}..."
fi

git fetch origin
git checkout -B "${BRANCH}"
git add .
git status --short

git commit -m "${COMMIT_MESSAGE}" || {
    echo "No new changes to commit."
}

git push -u origin "${BRANCH}"

echo
echo "Done: ${BRANCH} pushed to origin."
