# mc-motd 的 fish 补全
# 安装：bash completions/install.sh --shell fish
# 描述语言跟随 MC_MOTD_LANG / LC_ALL / LANG，规则和 CLI 一致：en* 用英文，其余用中文

set -l locale "$MC_MOTD_LANG"
if test -z "$locale"
    set locale "$LC_ALL$LANG"
end
set locale (string lower -- "$locale")

set -l d
if string match -q 'en*' -- "$locale"
    set d 'show help' 'show version' 'output JSON' \
        'request timeout in milliseconds' 'proxy URL' 'output language' \
        'query a Bedrock Edition server (UDP 19132)' 'skip SRV lookup' \
        'do not fall back to the legacy ping' 'disable colors' 'force colors' \
        'start the local web UI' 'web UI port' 'bind address'
else
    set d '显示帮助' '显示版本' '输出 JSON' \
        '请求超时（毫秒）' '代理地址' '输出语言' \
        '查询基岩版服务器（UDP 19132）' '跳过 SRV 查询' \
        '不回退到旧版 Ping' '禁用颜色' '强制启用颜色' \
        '启动本地网页版' '网页版端口' '监听地址'
end

# 参数都是选项和服务器地址，没有文件名，关掉文件补全
complete -c motd -f

complete -c motd -s h -l help -d "$d[1]"
complete -c motd -s v -l version -d "$d[2]"
complete -c motd -l json -d "$d[3]"
complete -c motd -l timeout -x -d "$d[4]"
complete -c motd -l proxy -x -d "$d[5]"
complete -c motd -l lang -x -a 'zh en' -d "$d[6]"
complete -c motd -l bedrock -d "$d[7]"
complete -c motd -l no-srv -d "$d[8]"
complete -c motd -l no-legacy -d "$d[9]"
complete -c motd -l no-color -d "$d[10]"
complete -c motd -l color -d "$d[11]"
complete -c motd -l web -d "$d[12]"
complete -c motd -l port -x -d "$d[13]"
complete -c motd -l host -x -d "$d[14]"
