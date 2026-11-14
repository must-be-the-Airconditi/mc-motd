# mc-motd 的 fish 补全
# 安装：bash completions/install.sh --shell fish
# 或手动复制到 ~/.config/fish/completions/motd.fish

# 参数都是选项和服务器地址，没有文件名，关掉文件补全
complete -c motd -f

complete -c motd -s h -l help -d 'show help'
complete -c motd -s v -l version -d 'show version'
complete -c motd -l json -d 'output JSON'
complete -c motd -l timeout -x -d 'request timeout in milliseconds'
complete -c motd -l proxy -x -d 'proxy URL'
complete -c motd -l lang -x -a 'zh en' -d 'output language'
complete -c motd -l bedrock -d 'query a Bedrock Edition server (UDP 19132)'
complete -c motd -l no-srv -d 'skip SRV lookup'
complete -c motd -l no-legacy -d 'do not fall back to the legacy ping'
complete -c motd -l no-color -d 'disable colors'
complete -c motd -l color -d 'force colors'
complete -c motd -l web -d 'start the local web UI'
complete -c motd -l port -x -d 'web UI port'
complete -c motd -l host -x -d 'bind address'
