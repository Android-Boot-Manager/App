#!/system/bin/sh

mkdir -p /sdcard/abm
cd /data/data/org.androidbootmanager.app/assets
echo app_install.sh starting > /sdcard/abm/install.log
/data/data/org.androidbootmanager.app/assets/install.sh "$1" hjacked "$2" >> /sdcard/abm/install.log 2>&1 && echo OK || echo ERROR
echo /dev/block/bootdevice/by-name/oem > ../files/cfg
echo app_install.sh finished >> /sdcard/abm/install.log

