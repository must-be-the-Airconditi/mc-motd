#!/usr/bin/env zsh
# 用 zpty 起一个交互 zsh，在 motd 后面按 Tab，打印终端输出。
# 用来验证补全在真实 zsh 里能被解析（_arguments 的 spec 格式错误只有真跑才看得到）。
#
#   zsh -f test/zsh-completion-probe.zsh <仓库根目录> [要补全的命令行片段]
#
# 不赌固定 sleep：等内部 shell 打出 SETUP_DONE 再按 Tab（标记用 SE''TUP_DONE 拼接，
# 免得命令自身的回显就匹配上）。输出靠非阻塞轮询读到安静为止，补全进菜单也不会挂。

root=${1:-$PWD}
line=${2:-'motd --b'}

zmodload zsh/zpty 2>/dev/null || { print -u2 'zpty unavailable'; exit 3 }

zpty z 'env TERM=xterm-256color zsh -f -i' || exit 3

zpty -w z "fpath=($root/completions \$fpath)"
zpty -w z "zstyle ':completion:*' menu no"
zpty -w z "autoload -Uz compinit && compinit -u -d \${TMPDIR:-/tmp}/zcd-probe-\$\$ && echo SE''TUP_DONE"

out='' chunk=''
integer i=0
while (( i < 300 )); do
  chunk=''
  if zpty -rmt z chunk '*SETUP_DONE*' 2>/dev/null; then
    out+=$chunk
    break
  fi
  out+=$chunk
  sleep 0.05
  (( i++ )) || true
done

zpty -w -n z "$line"$'\t'

# 读补全输出：有数据就继续读，连续 20 次（约 1 秒）没新输出就当渲染完了
integer quiet=0 k=0
while (( k < 120 )); do
  chunk=''
  if zpty -rt z chunk 2>/dev/null; then
    out+=$chunk
    quiet=0
  else
    (( quiet++ )) || true
    (( quiet >= 20 )) && break
  fi
  sleep 0.05
  (( k++ )) || true
done

# 补全可能停在菜单选择里，发个 Ctrl-C 让子 shell 退出
zpty -w -n z $'\x03' 2>/dev/null
zpty -d z 2>/dev/null

print -r -- "$out"
