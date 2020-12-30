#!/system/bin/sh

mkdir -p /sdcard/abm /data/abm
cd /data/data/org.androidbootmanager.app/assets/Toolkit || exit 2
echo app_install.sh starting
"/data/data/org.androidbootmanager.app/assets/Scripts/install/${2}.sh" /data/data/org.androidbootmanager.app/files/lk2nd.img hjacked "$1" 2>&1
echo install.sh finished: $?
echo app_install.sh finished