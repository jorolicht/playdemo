# ==============================================================================
# 1. GRUNDEINSTELLUNGEN & ENVIRONMENT
# ==============================================================================
export EDITOR="nano"
export VISUAL="code --wait"
export LANG="en_US.UTF-8"
export LC_ALL="en_US.UTF-8"

# History: Großzügig dimensionieren & Duplikate ignorieren
export HISTSIZE=50000
export HISTFILESIZE=50000
export HISTCONTROL=ignoreboth:erasedups
shopt -s histappend

# Farbige Terminal-Ausgabe erzwingen
export CLICOLOR=1
export TERM=xterm-256color

# Pfaderweiterungen (Homebrew, lokale Skripte)
export PATH="/usr/local/bin:/usr/local/sbin:$HOME/.local/bin:$HOME/bin:$PATH"

# ==============================================================================
# 2. PROMPT (Schlank, zweizeilig mit Git-Branch & Exit-Code)
# ==============================================================================
parse_git_branch() {
    git branch 2> /dev/null | sed -e '/^[^*]/d' -e 's/* \(.*\)/ (\1)/'
}

# Farben
C_RESET="\[\033[0m\]"
C_USER="\[\033[01;32m\]"
C_DIR="\[\033[01;34m\]"
C_GIT="\[\033[01;33m\]"
C_ARROW="\[\033[01;35m\]"

# Zweizeiliger Prompt: [user@host:dir (git_branch)] \n ➜
export PS1="┌[\[\033[0;32m\]\h \[\033[0;30m\]@ \[\033[0;34m\]\w \[\033[0;30m\](\[\033[1;30m\]\u\[\033[0;30m\])] \[\033[0;37m\]\t\[\033[0;30m\] \n└-> "

#PS1="${C_USER}\u@\h${C_RESET}:${C_DIR}\w${C_GIT}\$(parse_git_branch)${C_RESET}\n${C_ARROW}➜ ${C_RESET}"

# ==============================================================================
# 3. NAVIGATION & VERZEICHNISSE
# ==============================================================================
alias ..='cd ..'
alias ...='cd ../..'
alias ....='cd ../../..'
alias -- -='cd -'

# Verzeichnisse anlegen und direkt hineinspringen
mkcd() {
    mkdir -p "$1" && cd "$1"
}

# ==============================================================================
# 4. ALLGEMEINE ALIASES (Bessere Defaults)
# ==============================================================================
alias ls='ls -G --color=auto' 2>/dev/null || alias ls='ls -G'
alias ll='ls -lahG'
alias la='ls -lah'
alias l='ls -CF'

# Sicherheitsnetz gegen versehentliches Überschreiben/Löschen
alias cp='cp -i'
alias mv='mv -i'
alias rm='rm -i'

# System-Infos & Festplattenbelegung
alias df='df -h'
alias du='du -h -d 1'
alias free='free -m' 2>/dev/null || true
alias ports='netstat -tulanp 2>/dev/null || lsof -i -P -n | grep LISTEN'

# ==============================================================================
# 5. DOCKER & SERVER SHORTCUTS
# ==============================================================================
alias d='docker'
alias dps='docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"'
alias dpsa='docker ps -a'
alias dlogs='docker logs -f --tail=100'
alias dstopall='docker stop $(docker ps -q)'
alias dprune='docker system prune -af --volumes'

# Docker Compose (V2 Syntax)
alias dc='docker compose'
alias dcup='docker compose up -d'
alias dcdown='docker compose down'
alias dcrestart='docker compose restart'
alias dclogs='docker compose logs -f --tail=100'

# ==============================================================================
# 6. GIT SHORTCUTS
# ==============================================================================
alias gs='git status -sb'
alias ga='git add'
alias gc='git commit -m'
alias gco='git checkout'
alias gp='git push'
alias gl='git pull'
alias gd='git diff'
alias glog='git log --graph --pretty=format:"%Cred%h%Creset -%C(yellow)%d%Creset %s %Cgreen(%cr) %C(bold blue)<%an>%Creset" --abbrev-commit'

# ==============================================================================
# 7. NÜTZLICHE HELFERFUNKTIONEN
# ==============================================================================

# Lokale & Öffentliche IP ermitteln
myip() {
    echo "--- Lokale IPs ---"
    ip -br addr 2>/dev/null || ifconfig | grep "inet " | grep -v 127.0.0.1
    echo "--- Externe IP ---"
    curl -s https://api.ipify.org && echo ""
}

# Schnelles Extrahieren aller Archivtypen
extract() {
    if [ -f "$1" ]; then
        case "$1" in
            *.tar.bz2)   tar xjf "$1"     ;;
            *.tar.gz)    tar xzf "$1"     ;;
            *.bz2)       bunzip2 "$1"     ;;
            *.rar)       unrar x "$1"     ;;
            *.gz)        gunzip "$1"      ;;
            *.tar)       tar xf "$1"      ;;
            *.tbz2)      tar xjf "$1"     ;;
            *.tgz)       tar xzf "$1"     ;;
            *.zip)       unzip "$1"       ;;
            *.Z)         uncompress "$1"  ;;
            *.7z)        7z x "$1"        ;;
            *)           echo "'$1' kann nicht entpackt werden" ;;
        esac
    else
        echo "'$1' ist keine gültige Datei"
    fi
}

# Profil schnell neu laden / editieren
alias reload='source ~/.bash_profile 2>/dev/null || source ~/.bashrc'
alias profile='$EDITOR ~/.bash_profile 2>/dev/null || $EDITOR ~/.bashrc'