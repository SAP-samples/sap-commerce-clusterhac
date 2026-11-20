#!/usr/bin/env bash
# Fails when a tracked file names a real CCv2 or Cloud Logging Service host. These identify
# a subscription, and this repository is public. Use a placeholder such as
# https://dashboards-<subscription>.<region>.cls.services.cloud.sap instead.
# The same patterns are gitleaks rules in .gitleaks.toml, which also covers commit history.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

patterns=(
  # Cloud Logging Service dashboards carry the subscription UUID in the host name.
  '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}[a-z0-9.-]*\.cloud\.sap'
  # CCv2 endpoints carry the subscription code and environment, e.g. <code>-<name>-d1-public.
  '[a-z0-9]+-[a-z0-9]+-[dsp][0-9]+-public\.model-t\.cc\.commerce\.ondemand\.com'
)

args=()
for p in "${patterns[@]}"; do args+=(-e "$p"); done

rc=0
git grep -nIiE "${args[@]}" -- . ':!.github/scripts/check-hostnames.sh' ':!.gitleaks.toml' || rc=$?
case $rc in
  0) echo "Environment host names found above. Replace them with a placeholder." >&2; exit 1 ;;
  1) echo "No environment host names found." ;;
  *) echo "git grep failed (exit $rc)." >&2; exit 2 ;;
esac
