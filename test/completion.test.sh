#!/usr/bin/env bash
# Smoke tests for completions/install.sh. No network, everything inside a temp HOME.
#
#   bash test/completion.test.sh

set -uo pipefail

# Ambient XDG_*/ZSH_* variables would move the install targets (GitHub runners
# set XDG_CONFIG_HOME, for example). Tests that care set them explicitly.
unset XDG_CONFIG_HOME XDG_DATA_HOME ZSH ZSH_CUSTOM ZDOTDIR

cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." || exit 1
ROOT=$(pwd)
INSTALL="$ROOT/completions/install.sh"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

pass=0
fail=0

ok() { pass=$((pass + 1)); printf 'ok   %s\n' "$1"; }
ng() {
  fail=$((fail + 1))
  printf 'FAIL %s\n' "$1"
  # Surface the exact assertion in the GitHub Actions summary.
  if [ -n "${GITHUB_ACTIONS:-}" ]; then
    printf '::error title=completion test::%s\n' "$1"
  fi
}

squash() { # squash <text> -> single line, truncated
  printf '%s' "$1" | tr '\n' '|' | cut -c1-400
}

check() { # check <name> <command...>
  local name=$1
  shift
  if "$@" >/dev/null 2>&1; then ok "$name"; else ng "$name"; fi
}

check_file() { # check_file <name> <path>
  if [ -f "$2" ]; then ok "$1"; else ng "$1 ($2 missing)"; fi
}

check_gone() { # check_gone <name> <path>
  if [ ! -f "$2" ]; then ok "$1"; else ng "$1 ($2 still there)"; fi
}

check_absent() { # check_absent <name> <haystack file> <needle>
  if grep -qF -- "$3" "$2" 2>/dev/null; then ng "$1 (found '$3')"; else ok "$1"; fi
}

check_present() { # check_present <name> <haystack file> <needle>
  if grep -qF -- "$3" "$2" 2>/dev/null; then ok "$1"; else ng "$1 (missing '$3')"; fi
}

check_order() { # check_order <name> <file> <first> <second>
  local a b
  a=$(grep -n -F -m1 -- "$3" "$2" | cut -d: -f1)
  b=$(grep -n -F -m1 -- "$4" "$2" | cut -d: -f1)
  if [ -n "$a" ] && [ -n "$b" ] && [ "$a" -lt "$b" ]; then ok "$1"; else ng "$1 (only fixed below the other)"; fi
}

# --- static checks ---------------------------------------------------------
for f in completions/_motd completions/motd.bash completions/install.sh; do
  check "syntax: $f" bash -n "$f"
done
if command -v fish >/dev/null 2>&1; then
  check "syntax: completions/motd.fish" fish --no-execute completions/motd.fish

  fish_complete() { # fish_complete <line> -> candidates, one per line
    fish -c "source completions/motd.fish; complete -C '$1'" 2>/dev/null
  }

  out=$(fish_complete "motd --b")
  case "$out" in
    *--bedrock*) ok "fish: '--b' completes to --bedrock" ;;
    *) ng "fish: '--b' did not complete to --bedrock" ;;
  esac

  out=$(fish_complete "motd --lang ")
  if printf '%s' "$out" | grep -q 'zh' && printf '%s' "$out" | grep -q 'en'; then
    ok "fish: --lang completes zh / en"
  else
    ng "fish: --lang does not complete zh / en"
  fi

  out=$(fish_complete "motd --")
  missing=""
  for opt in --json --timeout --proxy --lang --bedrock --no-srv --no-legacy \
    --no-color --color --web --port --host --help --version; do
    printf '%s' "$out" | grep -q -- "$opt" || missing="$missing $opt"
  done
  if [ -z "$missing" ]; then ok "fish: every --option is offered"; else ng "fish: options missing:$missing"; fi

  out=$(fish_complete "motd ./")
  if [ -z "$out" ]; then ok "fish: file completion disabled"; else ng "fish: file completion still active"; fi
else
  printf 'skip fish checks (fish not installed)\n'
fi
check "install.sh is executable" test -x "$INSTALL"

# --- plain zsh -------------------------------------------------------------
H="$TMP/zsh-plain"
mkdir -p "$H"
printf 'setopt hist_ignore_all_dups\nautoload -Uz compinit && compinit\n' > "$H/.zshrc"
out=$(HOME="$H" SHELL=/bin/zsh bash "$INSTALL" --shell zsh 2>&1)
check_file "zsh: _motd installed to ~/.zfunc" "$H/.zfunc/_motd"
if grep -qF -- 'fpath=("$HOME/.zfunc" $fpath)' "$H/.zshrc"; then
  ok "zsh: fpath line added to ~/.zshrc"
else
  ng "zsh: fpath line missing (installer: $(squash "$out")) (zshrc: $(squash "$(cat "$H/.zshrc" 2>/dev/null)"))"
fi
check_order "zsh: fpath inserted before compinit" "$H/.zshrc" 'fpath=("$HOME/.zfunc"' 'compinit'

HOME="$H" SHELL=/bin/zsh bash "$INSTALL" --shell zsh >/dev/null 2>&1
if [ "$(grep -c 'mc-motd completion (managed' "$H/.zshrc")" = "1" ]; then
  ok "zsh: re-run is idempotent"
else
  ng "zsh: re-run duplicated the block"
fi

HOME="$H" SHELL=/bin/zsh bash "$INSTALL" --shell zsh --uninstall >/dev/null 2>&1
check_gone "zsh: uninstall removes _motd" "$H/.zfunc/_motd"
check_absent "zsh: uninstall removes the whole block" "$H/.zshrc" 'fpath=("$HOME/.zfunc" $fpath)'
check_present "zsh: uninstall keeps the user's compinit line" "$H/.zshrc" 'compinit'

# --- oh-my-zsh -------------------------------------------------------------
H="$TMP/zsh-omz"
mkdir -p "$H/.oh-my-zsh/custom"
HOME="$H" SHELL=/bin/zsh bash "$INSTALL" --shell zsh >/dev/null 2>&1
check_file "omz: _motd installed to custom/completions" "$H/.oh-my-zsh/custom/completions/_motd"
check_absent "omz: ~/.zshrc left untouched" "$H/.zshrc" 'mc-motd completion'

# --- bash with bash-completion --------------------------------------------
H="$TMP/bash-full"
mkdir -p "$H/.local/share/bash-completion"
: > "$H/.local/share/bash-completion/bash_completion"
HOME="$H" XDG_DATA_HOME="$H/.local/share" SHELL=/bin/bash bash "$INSTALL" --shell bash >/dev/null 2>&1
check_file "bash: completion installed to bash-completion dir" \
  "$H/.local/share/bash-completion/completions/motd"
check_absent "bash: ~/.bashrc untouched with bash-completion" "$H/.bashrc" 'mc-motd completion'
HOME="$H" XDG_DATA_HOME="$H/.local/share" SHELL=/bin/bash bash "$INSTALL" --shell bash --uninstall >/dev/null 2>&1
check_gone "bash: uninstall removes the completion file" \
  "$H/.local/share/bash-completion/completions/motd"

# --- bash without bash-completion ----------------------------------------
H="$TMP/bash-plain"
mkdir -p "$H"
printf 'alias ll="ls -l"' > "$H/.bashrc" # deliberately no trailing newline
HOME="$H" SHELL=/bin/bash bash "$INSTALL" --shell bash --bashrc >/dev/null 2>&1
check_present "bash: source line added to ~/.bashrc" "$H/.bashrc" 'completions/motd.bash'
check "bash: ~/.bashrc still parses" bash -n "$H/.bashrc"
HOME="$H" SHELL=/bin/bash bash "$INSTALL" --shell bash --uninstall >/dev/null 2>&1
check_absent "bash: uninstall removes the source line" "$H/.bashrc" 'completions/motd.bash'
check_present "bash: uninstall keeps the user's alias" "$H/.bashrc" 'alias ll='

# --- --bashrc wins even when bash-completion is installed (CI has it) ----
H="$TMP/bash-forced"
mkdir -p "$H/.local/share/bash-completion"
: > "$H/.local/share/bash-completion/bash_completion"
HOME="$H" XDG_DATA_HOME="$H/.local/share" SHELL=/bin/bash bash "$INSTALL" --shell bash --bashrc >/dev/null 2>&1
check_present "--bashrc: source line added despite bash-completion" "$H/.bashrc" 'completions/motd.bash'
check_gone "--bashrc: bash-completion dir untouched" \
  "$H/.local/share/bash-completion/completions/motd"

# --- fish -----------------------------------------------------------------
H="$TMP/fish"
mkdir -p "$H"
HOME="$H" SHELL=/usr/bin/fish bash "$INSTALL" --shell fish >/dev/null 2>&1
check_file "fish: completion installed to ~/.config/fish/completions" \
  "$H/.config/fish/completions/motd.fish"
check_absent "fish: config.fish left alone" "$H/.config/fish/config.fish" 'mc-motd'
# ls -l 的权限位在两个平台格式一致；BSD 的 stat 没有 -c
if [ "$(ls -ld "$H/.config/fish/completions/motd.fish" 2>/dev/null | cut -c1-10)" = "-rw-r--r--" ]; then
  ok "fish: completion file is world readable"
else
  ng "fish: completion file mode is not 644"
fi
HOME="$H" SHELL=/usr/bin/fish bash "$INSTALL" --shell fish --uninstall >/dev/null 2>&1
check_gone "fish: uninstall removes the completion file" \
  "$H/.config/fish/completions/motd.fish"

# fish honours XDG_CONFIG_HOME
H="$TMP/fish-xdg"
mkdir -p "$H/cfg"
HOME="$H" XDG_CONFIG_HOME="$H/cfg" SHELL=/usr/bin/fish bash "$INSTALL" --shell fish >/dev/null 2>&1
check_file "fish: honours XDG_CONFIG_HOME" "$H/cfg/fish/completions/motd.fish"
check_gone "fish: nothing written to ~/.config with XDG_CONFIG_HOME" \
  "$H/.config/fish/completions/motd.fish"

# fish is auto-detected from $SHELL
H="$TMP/fish-detect"
mkdir -p "$H"
HOME="$H" SHELL=/usr/local/bin/fish bash "$INSTALL" >/dev/null 2>&1
check_file "fish: auto-detected from \$SHELL" "$H/.config/fish/completions/motd.fish"

# --- bad input ------------------------------------------------------------
H="$TMP/bad"
mkdir -p "$H"
if HOME="$H" SHELL=/bin/tcsh bash "$INSTALL" >/dev/null 2>&1; then
  ng "unsupported shell should fail"
else
  ok "unsupported shell fails"
fi
if HOME="$H" SHELL= bash "$INSTALL" >/dev/null 2>&1; then
  ng "unknown shell should fail"
else
  ok "unknown shell fails"
fi
if HOME="$H" SHELL=/bin/zsh bash "$INSTALL" --nope >/dev/null 2>&1; then
  ng "unknown option should fail"
else
  ok "unknown option fails"
fi

printf '\n%d passed, %d failed\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
