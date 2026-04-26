#!/usr/bin/env bash
# =============================================================================
# passtheo-content-service release helper
# =============================================================================
# Tags HEAD on develop with `passtheo-content-service/v<MAJOR>.<MINOR>.<PATCH>` and pushes.
# Refuses to tag from anything other than a clean-tree develop in sync with
# origin, with the latest develop CI run green.
#
# On tag push the `build` workflow (vendored from passtheo/ci-workflows
# templates/spring-boot-service.yml) builds + pushes the image and dispatches
# the infra image-tag bump.
#
# Usage:  ./scripts/release.sh
# =============================================================================

set -euo pipefail

SERVICE="passtheo-content-service"
REPO="passtheo/${SERVICE}"
WORKFLOW="build"

die() { printf '\n[release.sh] error: %s\n' "$1" >&2; exit 1; }
info() { printf '[release.sh] %s\n' "$1"; }

command -v gh  >/dev/null 2>&1 || die "gh CLI not found in PATH"
command -v git >/dev/null 2>&1 || die "git not found in PATH"

[[ "$(git branch --show-current)" == "develop" ]] \
  || die "must be on 'develop' (currently on '$(git branch --show-current)')"

[[ -z "$(git status --porcelain)" ]] \
  || die "working tree not clean — commit, stash, or revert before tagging"

info "fetching origin..."
git fetch --quiet origin develop

LOCAL_SHA=$(git rev-parse HEAD)
ORIGIN_SHA=$(git rev-parse origin/develop)
[[ "${LOCAL_SHA}" == "${ORIGIN_SHA}" ]] \
  || die "local develop (${LOCAL_SHA:0:7}) differs from origin (${ORIGIN_SHA:0:7}) — pull/push first"

info "checking latest CI run on develop..."
LATEST_CI=$(gh run list --repo "${REPO}" --branch develop --workflow "${WORKFLOW}" --limit 1 \
              --json conclusion,headSha --jq '.[0]')
[[ -n "${LATEST_CI}" && "${LATEST_CI}" != "null" ]] \
  || die "no '${WORKFLOW}' run found for develop yet — push something and wait, or run via workflow_dispatch"
CI_SHA=$(echo "${LATEST_CI}" | python3 -c "import sys,json;print(json.load(sys.stdin)['headSha'])")
CI_CONCLUSION=$(echo "${LATEST_CI}" | python3 -c "import sys,json;print(json.load(sys.stdin)['conclusion'])")
[[ "${CI_SHA}" == "${LOCAL_SHA}" ]] \
  || die "latest CI run was on ${CI_SHA:0:7}, but HEAD is ${LOCAL_SHA:0:7} — push first and let CI finish"
[[ "${CI_CONCLUSION}" == "success" ]] \
  || die "latest CI run on develop concluded '${CI_CONCLUSION}', not 'success'"

LAST_TAG=$(git tag -l "${SERVICE}/v*" --sort=-v:refname | head -1 || true)
if [[ -z "${LAST_TAG}" ]]; then
  CUR_VERSION="0.0.0"
  info "no prior tag — starting from 0.0.0"
else
  CUR_VERSION="${LAST_TAG#"${SERVICE}/v"}"
  info "last tag: ${LAST_TAG}"
fi

IFS='.-' read -r MAJOR MINOR PATCH _ <<<"${CUR_VERSION}"
MAJOR="${MAJOR:-0}"; MINOR="${MINOR:-0}"; PATCH="${PATCH:-0}"

cat <<EOF

Pick a bump:
  1) major  ${MAJOR}.${MINOR}.${PATCH} -> $((MAJOR+1)).0.0
  2) minor  ${MAJOR}.${MINOR}.${PATCH} -> ${MAJOR}.$((MINOR+1)).0
  3) patch  ${MAJOR}.${MINOR}.${PATCH} -> ${MAJOR}.${MINOR}.$((PATCH+1))
EOF
read -r -p "choice [1/2/3]: " CHOICE
case "${CHOICE}" in
  1) NEW_VERSION="$((MAJOR+1)).0.0" ;;
  2) NEW_VERSION="${MAJOR}.$((MINOR+1)).0" ;;
  3) NEW_VERSION="${MAJOR}.${MINOR}.$((PATCH+1))" ;;
  *) die "invalid choice '${CHOICE}'" ;;
esac

NEW_TAG="${SERVICE}/v${NEW_VERSION}"
SHORT_SHA="$(git rev-parse --short=7 HEAD)"

cat <<EOF

About to:
  tag    ${NEW_TAG}
  on     ${SHORT_SHA}  ($(git log -1 --format='%s' HEAD))
  push   to origin

This will trigger:
  - full CI pipeline (test, build JAR, build+push image to registry.passtheo.com)
  - repository_dispatch into passtheo/infra to bump dev values.yaml
  - Argo CD sync of <svc>-dev to the new image

EOF
read -r -p "Proceed? (y/N): " YN
[[ "${YN}" == "y" || "${YN}" == "Y" ]] || die "aborted"

info "tagging ${NEW_TAG}..."
git tag -a "${NEW_TAG}" -m "Release ${SERVICE} ${NEW_VERSION}"
info "pushing tag..."
git push origin "${NEW_TAG}"

cat <<EOF

Pushed ${NEW_TAG}. Watch:
  gh run watch -R ${REPO}
  gh pr list  -R passtheo/infra --search "head:bump/${SERVICE}"
EOF
