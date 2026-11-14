#!/usr/bin/env zsh
# 用 zpty 起一个交互 zsh，在 motd 后面按 Tab，打印终端输出。
# 用来验证补全在真实 zsh 里能被解析（_arguments 的 spec 格式错误只有真跑才看得到）。
#
#   zsh -f test/zsh-completion-probe.zsh <仓库根目录> [要补全的命令行片段]

root=${1:-$PWD}
line=${2:-'motd --b'}

zmodload zsh/zpty 2>/dev/null || { print -u2 'zpty unavailable'; exit 3 }

zpty z 'zsh -f -i' || exit 3

zpty -w z "fpath=($root/completions \$fpath)"
zpty -w z "autoload -Uz compinit && compinit -u -d ${TMPDIR:-/tmp}/zcd-probe-$$"
zpty -w z "zstyle ':completion:*' menu no"
sleep 1

zpty -w -n z "$line"$'\t'
sleep 1

zpty -w z 'echo READY_MARK'
out=''
zpty -r z out '*READY_MARK*' || exit 4
zpty -d z

print -r -- "$out"
