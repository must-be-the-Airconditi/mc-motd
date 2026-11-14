#!/usr/bin/env zsh
# 用 zpty 起一个交互 zsh，在 motd 后面按 Tab，打印终端输出。
# 用来验证补全在真实 zsh 里能被解析（_arguments 的 spec 格式错误只有真跑才看得到）。
#
#   zsh -f test/zsh-completion-probe.zsh <仓库根目录> [要补全的命令行片段]
#
# 读输出用 zpty -r -m -t 非阻塞轮询，最多等 10 秒，避免补全进入菜单时卡死 CI。

root=${1:-$PWD}
line=${2:-'motd --b'}

zmodload zsh/zpty 2>/dev/null || { print -u2 'zpty unavailable'; exit 3 }

zpty z 'zsh -f -i' || exit 3

zpty -w z "fpath=($root/completions \$fpath)"
zpty -w z "autoload -Uz compinit && compinit -u -d ${TMPDIR:-/tmp}/zcd-probe-$$"
zpty -w z "zstyle ':completion:*' menu no; setopt no_menu_complete 2>/dev/null"
sleep 1

zpty -w -n z "$line"$'\t'
sleep 1

# 万一补全进了菜单选择或列表提示，发 Ctrl-C 让它回到命令行，否则后面的 echo 不会执行
zpty -w -n z $'\x03'
sleep 0.3
zpty -w z 'echo READY_MARK'

out=''
integer i=0
while (( i < 200 )); do
  chunk=''
  if zpty -rmt z chunk '*READY_MARK*' 2>/dev/null; then
    out+=$chunk
    break
  fi
  out+=$chunk
  sleep 0.05
  (( i++ )) || true
done

zpty -d z 2>/dev/null

print -r -- "$out"
