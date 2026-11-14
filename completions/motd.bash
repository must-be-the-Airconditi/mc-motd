# mc-motd 的 bash 补全
# 安装：在 ~/.bashrc 里 source 这个文件，或复制到 /etc/bash_completion.d/

_motd() {
  local cur prev opts
  COMPREPLY=()
  cur="${COMP_WORDS[COMP_CWORD]}"
  prev="${COMP_WORDS[COMP_CWORD-1]}"

  case "$prev" in
    --lang)
      COMPREPLY=( $(compgen -W "zh en" -- "$cur") )
      return 0
      ;;
    --timeout|--port|--proxy|--host)
      return 0
      ;;
  esac

  opts="--json --timeout --proxy --lang --bedrock --no-srv --no-legacy --no-color --color --web --port --host -h --help -v --version"

  if [[ "$cur" == -* ]]; then
    COMPREPLY=( $(compgen -W "$opts" -- "$cur") )
  fi
  return 0
}

complete -F _motd motd
