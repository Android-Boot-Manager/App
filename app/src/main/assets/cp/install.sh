#!/system/bin/sh

sleep 5
echo $1
sha256sum $1
echo id $2
echo name $3
ls
exit 0
