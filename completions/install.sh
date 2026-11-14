#!/usr/bin/env bash
# mc-motd shell completion installer.
#
#   bash completions/install.sh              # auto-detect zsh / bash
#   bash completions/install.sh --shell bash # force a shell
#   bash completions/install.sh --uninstall  # remove what we installed
#
# Works from a git checkout and from a global npm install, e.g.
#   bash "$(npm root -g)/mc-motd/completions/install.sh"
#
# No sudo, no network, only touches files inside your home directory.

set -euo pipefail

SRC_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
SHELL_NAME=""
MODE="install"
FORCE_BASHRC=0

usage() {
  cat <<'EOF'
Usage: install.sh [options]

Options:
  --shell zsh|bash   target shell (default: detect from $SHELL)
  --bashrc           bash only: always source motd.bash from ~/.bashrc
                     instead of using the bash-completion directory
  --uninstall        remove the completion files again
  -h, --help         show this help
EOF
}

say() { printf '%s\n' "$*"; }
warn() { printf 'warning: %s\n' "$*" >&2; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --shell)
      [ $# -ge 2 ] || die "--shell needs a value (zsh or bash)"
      SHELL_NAME=$2
      shift 2
      ;;
    --shell=*)
      SHELL_NAME=${1#--shell=}
      shift
      ;;
    --uninstall)
      MODE="uninstall"
      shift
      ;;
    --bashrc)
      FORCE_BASHRC=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "unknown option: $1 (try --help)"
      ;;
  esac
done

if [ -z "$SHELL_NAME" ]; then
  SHELL_NAME=$(basename -- "${SHELL:-}")
fi

case "$SHELL_NAME" in
  zsh|bash) ;;
  "") die "cannot detect your shell (empty \$SHELL); pass --shell zsh or --shell bash" ;;
  *) die "unsupported shell: $SHELL_NAME (only zsh and bash are supported)" ;;
esac

BLOCK_START='# mc-motd completion (managed by completions/install.sh)'
BLOCK_END='# mc-motd completion end'

block_text() {
  printf '%s\n%s\n%s' "$BLOCK_START" "$1" "$BLOCK_END"
}

# Make sure the file ends with a newline before appending to it.
ensure_trailing_newline() {
  local file=$1
  [ -s "$file" ] || return 0
  if [ -n "$(tail -c 1 "$file")" ]; then
    printf '\n' >> "$file"
  fi
}

has_block() {
  [ -f "$1" ] && grep -qF -- "$BLOCK_START" "$1" 2>/dev/null
}

# Append the managed block to a file unless it is already there.
append_block() {
  local file=$1 body=$2
  if has_block "$file"; then
    return 1
  fi
  touch "$file"
  ensure_trailing_newline "$file"
  printf '%s\n' "$(block_text "$body")" >> "$file"
  return 0
}

# Insert the managed block before the first `compinit` line so fpath is set in time.
insert_block_before_compinit() {
  local file=$1 body=$2
  if has_block "$file"; then
    return 1
  fi
  touch "$file"
  local block line tmp
  block=$(block_text "$body")
  line=$(grep -n -m1 -E '(^|[^[:alnum:]_])compinit([^[:alnum:]_]|$)' "$file" | cut -d: -f1 || true)
  if [ -n "$line" ]; then
    tmp=$(mktemp)
    awk -v n="$line" -v block="$block" 'NR == n { print block } { print }' "$file" > "$tmp"
    mv "$tmp" "$file"
  else
    ensure_trailing_newline "$file"
    printf '%s\n' "$block" >> "$file"
  fi
  return 0
}

# Delete the managed block, from its start marker through its end marker.
remove_block() {
  local file=$1
  has_block "$file" || return 1
  local tmp
  tmp=$(mktemp)
  awk -v start="$BLOCK_START" -v end="$BLOCK_END" '
    index($0, start) { inblock = 1; next }
    inblock && index($0, end) { inblock = 0; next }
    inblock { next }
    { print }
  ' "$file" > "$tmp"
  mv "$tmp" "$file"
  return 0
}

install_zsh() {
  local dest_dir need_fpath=0
  if [ -n "${ZSH_CUSTOM:-}" ] || [ -d "$HOME/.oh-my-zsh" ]; then
    # The custom/completions dir is already on $fpath for oh-my-zsh users.
    dest_dir="${ZSH_CUSTOM:-$HOME/.oh-my-zsh/custom}/completions"
  else
    dest_dir="$HOME/.zfunc"
    need_fpath=1
  fi
  [ -f "$SRC_DIR/_motd" ] || die "missing $SRC_DIR/_motd"
  mkdir -p "$dest_dir"
  cp "$SRC_DIR/_motd" "$dest_dir/_motd"
  chmod 644 "$dest_dir/_motd"
  say "installed  $dest_dir/_motd"
  if [ "$need_fpath" -eq 1 ]; then
    if insert_block_before_compinit "$HOME/.zshrc" 'fpath=("$HOME/.zfunc" $fpath)'; then
      say "updated    ~/.zshrc (added \$HOME/.zfunc to fpath)"
    else
      say "kept       ~/.zshrc (fpath entry already present)"
    fi
    if ! grep -qE 'autoload -Uz compinit' "$HOME/.zshrc" 2>/dev/null; then
      warn "your ~/.zshrc has no 'compinit' call; add 'autoload -Uz compinit && compinit' for zsh completion to work"
    fi
  fi
}

install_bash() {
  local data_dir dest_dir rc="$HOME/.bashrc"
  data_dir="${XDG_DATA_HOME:-$HOME/.local/share}"
  dest_dir="$data_dir/bash-completion/completions"
  [ -f "$SRC_DIR/motd.bash" ] || die "missing $SRC_DIR/motd.bash"

  if [ "$FORCE_BASHRC" -eq 0 ] &&
    { [ -f /usr/share/bash-completion/bash_completion ] || [ -f "$data_dir/bash-completion/bash_completion" ]; }; then
    # bash-completion 2.x loads ~/.local/share/bash-completion/completions/* on demand.
    mkdir -p "$dest_dir"
    cp "$SRC_DIR/motd.bash" "$dest_dir/motd"
    chmod 644 "$dest_dir/motd"
    say "installed  $dest_dir/motd"
    if [ -f /etc/bash_completion.d/motd ]; then
      warn "/etc/bash_completion.d/motd also exists and may take precedence"
    fi
  else
    if append_block "$rc" "source \"$SRC_DIR/motd.bash\""; then
      say "updated    ~/.bashrc (sourced $SRC_DIR/motd.bash)"
    else
      say "kept       ~/.bashrc (source line already present)"
    fi
  fi
}

uninstall_zsh() {
  local dest_dir removed=0
  for dest_dir in "${ZSH_CUSTOM:-$HOME/.oh-my-zsh/custom}/completions" "$HOME/.zfunc"; do
    if [ -f "$dest_dir/_motd" ]; then
      rm -f "$dest_dir/_motd"
      say "removed    $dest_dir/_motd"
      removed=1
    fi
  done
  if remove_block "$HOME/.zshrc"; then
    say "updated    ~/.zshrc (removed fpath entry)"
    removed=1
  fi
  [ "$removed" -eq 1 ] || say "nothing to remove"
}

uninstall_bash() {
  local data_dir dest_dir rc="$HOME/.bashrc" removed=0
  data_dir="${XDG_DATA_HOME:-$HOME/.local/share}"
  dest_dir="$data_dir/bash-completion/completions"
  if [ -f "$dest_dir/motd" ]; then
    rm -f "$dest_dir/motd"
    say "removed    $dest_dir/motd"
    removed=1
  fi
  if remove_block "$rc"; then
    say "updated    ~/.bashrc (removed source line)"
    removed=1
  fi
  [ "$removed" -eq 1 ] || say "nothing to remove"
}

if [ "$MODE" = "uninstall" ]; then
  case "$SHELL_NAME" in
    zsh) uninstall_zsh ;;
    bash) uninstall_bash ;;
  esac
  say ""
  say "restart your shell to apply the change."
  exit 0
fi

case "$SHELL_NAME" in
  zsh) install_zsh ;;
  bash) install_bash ;;
esac

say ""
say "restart your shell (or run 'exec $SHELL_NAME') and try: motd -<Tab>"
