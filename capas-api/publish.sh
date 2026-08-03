#!/usr/bin/env bash
# Publica as capas do vercapas a partir de um IP residencial (a Mac do dono),
# contornando o bloqueio da Cloudflare que apanha os IPs de datacenter do CI.
# Corre via launchd: ~/Library/LaunchAgents/com.joaobzao.capas-publish.plist
set -euo pipefail

# launchd arranca com um ambiente mínimo — fixar PATH e falhar rápido no SSH.
export PATH="/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin:${PATH:-}"
export GIT_SSH_COMMAND="ssh -o BatchMode=yes"

REPO="/Users/sofia/Documents/projects/capas"
API="$REPO/capas-api"
CARGO="/opt/homebrew/bin/cargo"

echo "===== $(date -u +'%Y-%m-%dT%H:%M:%SZ') a iniciar publish ====="

# 1. Compilar e correr o scraper. Se o vercapas bloquear, o guardrail sai != 0
#    e o `set -e` aborta ANTES de publicar (mantém o último JSON válido).
cd "$API"
"$CARGO" build --release
./target/release/capas-api

# 2. Publicar public/ para o branch gh-pages via worktree efémero.
WT="$(mktemp -d /tmp/capas-ghpages.XXXXXX)"
cleanup() {
  git -C "$REPO" worktree remove --force "$WT" 2>/dev/null || true
  git -C "$REPO" worktree prune 2>/dev/null || true
}
trap cleanup EXIT

git -C "$REPO" fetch --quiet origin gh-pages
git -C "$REPO" worktree add --quiet --force "$WT" origin/gh-pages
cp "$API/public/capas.json" "$API/public/index.html" "$WT/"
git -C "$WT" add -A

if git -C "$WT" diff --cached --quiet; then
  echo "Sem alterações — nada a publicar."
else
  git -C "$WT" commit --quiet \
    -m "🔄 Update capas ($(date -u +'%Y-%m-%d %H:%M:%S UTC')) — publish local (Mac)"
  git -C "$WT" push --quiet origin HEAD:gh-pages
  echo "✅ Publicado em gh-pages."
fi

echo "===== $(date -u +'%Y-%m-%dT%H:%M:%SZ') concluído ====="
