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
  out=$(env -u LC_ALL -u MC_MOTD_LANG LANG=zh_CN.UTF-8 fish -c 'source completions/motd.fish; complete -C "motd --b"' 2>/dev/null)
  case "$out" in
    *查询基岩版*) ok "fish: LANG=zh_CN.UTF-8 → 中文描述" ;;
    *) ng "fish: LANG=zh_CN.UTF-8 时没有中文描述" ;;
  esac

  out=$(env -u LC_ALL MC_MOTD_LANG=en LANG=zh_CN.UTF-8 fish -c 'source completions/motd.fish; complete -C "motd --b"' 2>/dev/null)
  case "$out" in
    *"query a Bedrock"*) ok "fish: MC_MOTD_LANG=en 覆盖 LANG" ;;
    *) ng "fish: MC_MOTD_LANG 没覆盖 LANG" ;;
  esac

else
  printf 'skip fish checks (fish not installed)\n'
fi
check "install.sh is executable" test -x "$INSTALL"

# --- 补全描述跟随语言（和 CLI 同一套规则） -------------------------------
if command -v zsh >/dev/null 2>&1; then
  # 把 _arguments 换成打印参数的桩，直接看它收到什么描述
  # 清掉 runner 自带的 LC_ALL / MC_MOTD_LANG，否则会盖掉用例里的 LANG
  zsh_specs() {
    env -u LC_ALL -u MC_MOTD_LANG "$@" zsh -f -c '
      _arguments() { print -rl -- "$@" }
      probe() { source ./completions/_motd }
      probe
    '
  }

  out=$(zsh_specs LANG=zh_CN.UTF-8)
  case "$out" in
    *显示帮助*) ok "zsh: LANG=zh_CN.UTF-8 → 中文描述" ;;
    *) ng "zsh: LANG=zh_CN.UTF-8 时没有中文描述" ;;
  esac

  out=$(zsh_specs LANG=en_US.UTF-8)
  case "$out" in
    *"show help"*) ok "zsh: LANG=en_US.UTF-8 → 英文描述" ;;
    *) ng "zsh: LANG=en_US.UTF-8 时没有英文描述" ;;
  esac

  out=$(zsh_specs MC_MOTD_LANG=en LANG=zh_CN.UTF-8)
  case "$out" in
    *"show help"*) ok "zsh: MC_MOTD_LANG 覆盖 LANG" ;;
    *) ng "zsh: MC_MOTD_LANG 没覆盖 LANG" ;;
  esac

  out=$(zsh_specs LC_ALL=zh_CN.UTF-8 LANG=en_US.UTF-8)
  case "$out" in
    *显示帮助*) ok "zsh: LC_ALL 优先于 LANG" ;;
    *) ng "zsh: LC_ALL 没有优先于 LANG" ;;
  esac

  out=$(env -u LANG -u LC_ALL -u MC_MOTD_LANG zsh -f -c '
    _arguments() { print -rl -- "$@" }
    probe() { source ./completions/_motd }
    probe
  ')
  case "$out" in
    *显示帮助*) ok "zsh: 没有 locale 变量时默认中文" ;;
    *) ng "zsh: 默认语言不是中文" ;;
  esac

  # 16 条带 [描述] 的 spec（-h/--help 和 -v/--version 各展开成两条 + 12 个长选项）
  specs=$(printf '%s\n' "$out" | grep -c '\[')
  if [ "$specs" -eq 16 ]; then
    ok "zsh: 16 条选项 spec 都在"
  else
    ng "zsh: 选项 spec 数量不对（$specs，应为 16）"
  fi
  # 花括号必须被 shell 展开，剩下未展开的 {} 会被 _arguments 判为 invalid argument
  if printf '%s\n' "$out" | grep -q '{'; then
    ng "zsh: spec 里还有未展开的 {…}（_arguments 会报 invalid argument）"
  else
    ok "zsh: 短长选项对已正确展开"
  fi
  case "$out" in
    *服务器地址*) ok "zsh: 地址参数描述也本地化了" ;;
    *) ng "zsh: 地址参数描述没本地化" ;;
  esac
else
  printf 'skip zsh language checks (zsh not installed)\n'
fi

# --- zsh 真实补全（zpty 开交互 shell 按 Tab，_arguments 的 spec 错误只有真跑才暴露） ---
if command -v zsh >/dev/null 2>&1 && zsh -fc 'zmodload zsh/zpty' >/dev/null 2>&1; then
  TO=""
  if command -v timeout >/dev/null 2>&1; then
    TO="timeout 25"
  elif command -v gtimeout >/dev/null 2>&1; then
    TO="gtimeout 25"
  fi

  out=$(env -u LC_ALL -u MC_MOTD_LANG LANG=zh_CN.UTF-8 $TO zsh -f test/zsh-completion-probe.zsh "$ROOT" 2>/dev/null)
  case "$out" in
    *"invalid argument"*) ng "zsh 真实补全: _arguments 报 invalid argument" ;;
    *--bedrock*) ok "zsh 真实补全: 能补出 --bedrock" ;;
    *) ng "zsh 真实补全: 没看到 --bedrock 候选" ;;
  esac
  case "$out" in
    *查询基岩版*) ok "zsh 真实补全: 描述是中文" ;;
    *) ng "zsh 真实补全: 描述不是中文" ;;
  esac

  out=$(env -u LC_ALL -u MC_MOTD_LANG LANG=en_US.UTF-8 $TO zsh -f test/zsh-completion-probe.zsh "$ROOT" 2>/dev/null)
  case "$out" in
    *"query a Bedrock"*) ok "zsh 真实补全: LANG=en 时描述是英文" ;;
    *) ng "zsh 真实补全: 英文描述缺失" ;;
  esac
else
  printf 'skip zsh real completion (zsh/zpty unavailable)\n'
fi

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

# --- half-written block must not eat the rest of the file -----------------
H="$TMP/half"
mkdir -p "$H"
printf '# mc-motd completion (managed by completions/install.sh)\nsetopt keepme\nautoload -Uz compinit && compinit\n' > "$H/.zshrc"
HOME="$H" SHELL=/bin/zsh bash "$INSTALL" --shell zsh --uninstall >/dev/null 2>&1
check_absent "half block: stray marker removed" "$H/.zshrc" 'mc-motd completion (managed'
check_present "half block: config below survived" "$H/.zshrc" 'setopt keepme'
check_present "half block: compinit survived" "$H/.zshrc" 'compinit'

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
