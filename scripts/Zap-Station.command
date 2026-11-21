#!/bin/zsh
set -eu
workshop_scripts=${0:A:h}
if ! command -v node >/dev/null; then
  for node_bin in "$HOME"/.nvm/versions/node/*/bin/node(Nom) /opt/homebrew/bin/node /usr/local/bin/node; do
    if [[ -x "$node_bin" ]]; then export PATH="${node_bin:h}:$PATH"; break; fi
  done
fi
exec node "$workshop_scripts/zap-workshop.mjs" "$workshop_scripts/../../.justnoise-zap-issuer-v1"
