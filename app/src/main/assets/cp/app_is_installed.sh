#!/system/bin/sh

/data/data/org.androidbootmanager.app/assets/Scripts/is_installed.sh >/dev/null && {ls /data/abm-part.cfg || {ls /dev/block/by-name/lk && {cat > /data/abm-part.cfg << EOF
mkdir -p /data/bootset/lk2nd
mount -t ext4 /dev/block/by-name/cache /cache
mount --bind /cache /data/bootset
mount --bind /data/bootset/db /data/bootset/lk2nd
EOF
echo > /data/abm-part.2.cfg
} || {cat > /data/abm-part.cfg << EOF
mkdir -p /data/bootset
mount -t ext4 /dev/block/bootdevice/by-name/oem /data/bootset
EOF
cat > /data/abm-part.2.cfg << EOF
umount /data/bootset
EOF
}

}} || rm -f /data/abm-part.cfg /data/abm-part.2.cfg
