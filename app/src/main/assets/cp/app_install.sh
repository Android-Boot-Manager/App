#!/system/bin/sh

mkdir -p /sdcard/abm
cd /data/data/org.androidbootmanager.app/assets/Toolkit
echo app_install.sh starting > /sdcard/abm/install.log
/data/data/org.androidbootmanager.app/assets/Scripts/install/$3.sh "$1" hjacked "$2" >> /sdcard/abm/install.log 2>&1 && echo OK || echo ERROR
echo app_install.sh finished >> /sdcard/abm/install.log
