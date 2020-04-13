#!/system/bin/sh

is_user_root () { [ ${EUID:-$(id -u)} -eq 0 ]; }
is_user_root && echo "I am root, fine! :)" || echo "I am not root! Please contact our Telegram group @andbootmgr for help."
