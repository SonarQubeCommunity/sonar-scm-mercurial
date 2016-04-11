#!/bin/bash

set -euo pipefail

function configureTravis {
  mkdir ~/.local
  curl -sSL https://github.com/SonarSource/travis-utils/tarball/v27 | tar zx --strip-components 1 -C ~/.local
  source ~/.local/bin/install
}
configureTravis

case "$TEST" in

CI)
  regular_mvn_build_deploy_analyze
  ;;


*)
  echo "Unexpected TEST mode: $TEST"
  exit 1
  ;;

esac
