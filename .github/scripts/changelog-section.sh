#!/usr/bin/env bash
# Print the CHANGELOG entry for one version, for use as GitHub release notes.
#
# Usage: changelog-section.sh <version> [changelog-file]
#
# Entries look like "* 2.111 (08/06/2026)" or "* 2.107 - 2.108 (04/08/2026)", followed by
# one line per change. A version inside a range heading gets that range's entry.
# Exits 1 (printing nothing) when no entry covers the version.
set -euo pipefail

version="$1"
changelog="${2:-CHANGELOG}"

awk -v want="$version" '
  # compare dotted versions numerically: -1, 0, 1
  function vcmp(a, b,    x, y, n, i, xa, yb) {
    n = split(a, x, "."); i = split(b, y, "."); if (i > n) n = i
    for (i = 1; i <= n; i++) {
      xa = (i in x) ? x[i] + 0 : 0; yb = (i in y) ? y[i] + 0 : 0
      if (xa < yb) return -1
      if (xa > yb) return 1
    }
    return 0
  }
  { sub(/\r$/, "") }
  /^\* / {
    if (found) exit
    lo = $2; hi = ($3 == "-") ? $4 : $2
    if (vcmp(want, lo) >= 0 && vcmp(want, hi) <= 0) found = 1
    next
  }
  found { lines[++count] = $0 }
  END {
    while (count > 0 && lines[count] ~ /^[[:space:]]*$/) count--
    for (i = 1; i <= count; i++) print lines[i]
    exit found ? 0 : 1
  }
' "$changelog"
