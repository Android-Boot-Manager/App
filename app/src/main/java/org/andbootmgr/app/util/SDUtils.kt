package org.andbootmgr.app.util

import android.util.Log
import com.topjohnwu.superuser.Shell
import org.andbootmgr.app.DeviceInfo
import java.util.*
import java.util.stream.Collectors
import kotlin.jvm.optionals.getOrElse

object SDUtils {

	// Unmount drive
	fun umsd(meta: SDPartitionMeta): String {
		val s = StringBuilder()
		for (p in meta.p) s.append(p.unmount()).append(" && ")
		val e = s.toString()
		return if (e.isEmpty()) "true" else e.substring(0, e.length - 4)
	}

	fun generateMeta(deviceInfo: DeviceInfo): SDPartitionMeta? {
		val meta: SDPartitionMeta
		val r =
			Shell.cmd("printf \"mm:%d:%d\\n\" `stat -c '0x%t 0x%T' ${deviceInfo.bdev}` && sgdisk ${deviceInfo.bdev} --print")
				.exec()
		meta = if (r.isSuccess) SDPartitionMeta() else return null
		try {
			meta.path = deviceInfo.bdev
			meta.ppath = deviceInfo.pbdev
			meta.nid = 1
			var temp: Long = -1
			var o: String
			for (oo in r.out) {
				o = oo
				if (o.startsWith("Disk ") && !o.contains("GUID")) {
					val t = o.split(": ").toTypedArray()[1].split(", ").toTypedArray()
					meta.sectors = t[0].replace(" sectors", "").toLong()
					meta.friendlySize = t[1]
				} else if (o.startsWith("Logical sector size: ") && o.endsWith(" bytes")) {
					meta.logicalSectorSizeBytes =
						o.replace("Logical sector size: ", "").replace(" bytes", "").toInt()
				} else if (o.startsWith("Sector size (logical/physical): ") && o.endsWith(" bytes")) {
					meta.logicalSectorSizeBytes =
						o.replace("Sector size (logical/physical): ", "").replace(" bytes", "")
							.split("/").toTypedArray()[0].toInt()
				} else if (o.startsWith("Disk identifier (GUID): ")) {
					meta.guid = o.replace("Disk identifier (GUID): ", "")
				} else if (o.startsWith("Partition table holds up to ")) {
					meta.maxEntries =
						o.replace("Partition table holds up to ", "").replace(" entries", "").toInt()
				} else if (o.startsWith("First usable sector is ")) {
					meta.firstUsableSector = o.replace("First usable sector is ", "")
						.replaceFirst(", last usable sector is .*$".toRegex(), "").toLong()
					meta.lastUsableSector = o.replace(
						"First usable sector is " + meta.firstUsableSector + ", last usable sector is ",
						""
					).toLong()
					meta.usableSectors = meta.lastUsableSector - meta.firstUsableSector
					temp = meta.firstUsableSector
				} else if (o.startsWith("Partitions will be aligned on ") && o.endsWith("-sector boundaries")) {
					meta.alignSector = o.replace("Partitions will be aligned on ", "")
						.replace("-sector boundaries", "").toLong()
				} else if (o.startsWith("Total free space is ") && o.contains("sectors")) {
					val t = o.replace(")", "").split("(").toTypedArray()
					meta.totalFreeSectors =
						t[0].replace("Total free space is ", "").replace(" sectors ", "").toLong()
					meta.totalFreeFancy = t[1]
				} else if (o.startsWith("mm:")) {
					val t = o.trim().split(":").toTypedArray()
					meta.major = t[1].toInt()
					meta.minor = t[2].toInt()
				} else if (o == "" || o.startsWith("Number  Start (sector)    End (sector)  Size       Code  Name") || o.startsWith(
						"Main partition table begins at"
					)
				) {
					assert(true) //avoid empty statement warning but do nothing
				} else if (o.startsWith("  ") && o.contains("iB")) {
					while (o.contains("  ")) o = o.trim { it <= ' ' }.replace("  ", " ")
					val ocut = o.split(" ").toTypedArray()
					val id = ocut[0].toInt()
					val code = ocut[5]
					val type = when (code) {
						"8301" ->
							if (id == 1) PartitionType.RESERVED else PartitionType.UNKNOWN
						"0700" -> PartitionType.PORTABLE
						"FFFF" ->
							if (id == 1) PartitionType.RESERVED else PartitionType.ADOPTED
						"8302" -> PartitionType.DATA
						"8305" -> PartitionType.SYSTEM
						"8300" -> PartitionType.UNKNOWN
						else -> PartitionType.UNKNOWN
					}
					val p = Partition(
						meta,
						type,
						id,
						meta.ppath + id,
						ocut[1].toLong() /* startSector */,
						ocut[2].toLong()/* endSector */,
						meta.logicalSectorSizeBytes,
						code,
						ocut.copyOfRange(6, ocut.size).toList().joinToString(" ") /* label/name */,
						meta.major,
						meta.minor + id
					)
					if (meta.nid == p.id) meta.nid++
					meta.p.add(p)
					meta.u.add(p)
				} else if (o=="Found invalid GPT and valid MBR; converting MBR to GPT format"){
					meta.ismbr=true
				} else if(o=="***************************************************************" || o=="in memory. ") {
					assert(true)
				} else {
					Log.e("ABM SDUtils", "can't handle $o")
					return null
				}
			}
			val l = meta.u.stream()
				.collect(Collectors.toList()) // this actually copies, therefore, we need this.
			l.sortWith { o1: Partition, o2: Partition ->
				// this is like this because startSector is long and I don't want overflows due to casting
				if (o1.startSector - o2.startSector < -1) return@sortWith -1 else if (o1.startSector == o2.startSector) return@sortWith 0 else return@sortWith 1
			}
			for (p in l) {
				if (p.startSector > temp + meta.alignSector) meta.u.add(
					Partition.FreeSpace(
						meta,
						temp,
						p.startSector - 1,
						meta.logicalSectorSizeBytes
					)
				)
				temp = p.endSector
			}
			if (meta.lastUsableSector > temp + meta.alignSector) meta.u.add(
				Partition.FreeSpace(
					meta,
					temp,
					meta.lastUsableSector - 1,
					meta.logicalSectorSizeBytes
				)
			)
			meta.s = meta.u.subList(0, meta.u.size)
			meta.s.sortWith { o1: Partition, o2: Partition ->
				// this is like this because startSector is long and I don't want overflows due to casting
				if (o1.startSector - o2.startSector < -1) return@sortWith -1 else if (o1.startSector == o2.startSector) return@sortWith 0 else return@sortWith 1
			}
			Log.i("ABM", meta.toString())
			return meta
		} catch (e: Exception) {
			Log.e("ABM", Log.getStackTraceString(e))
			return null
		}
	}

	enum class PartitionType {
		RESERVED, ADOPTED, PORTABLE, UNKNOWN, FREE, SYSTEM, DATA
	}

	open class Partition(
		var meta: SDPartitionMeta,
		val type: PartitionType,
		val id: Int,
		open val path: String,
		val startSector: Long,
		val endSector: Long,
		bytes: Int,
		val code: String,
		val name: String,
		val major: Int,
		val minor: Int
	) {
		val size: Long = endSector - startSector
		val sizeFancy: String = SOUtils.humanReadableByteCountBin(size * bytes)
		override fun toString(): String {
			return "Partition{" +
					"type=" + type +
					", id=" + id +
					", startSector=" + startSector +
					", endSector=" + endSector +
					", size=" + size +
					", sizeFancy='" + sizeFancy + '\'' +
					", code='" + code + '\'' +
					", name='" + name + '\'' +
					", minor=" + minor +
					'}'
		}

		fun mount(): String {
			return when (this.type) {
				PartitionType.FREE -> "echo 'Why are you trying to mount free space?!'"
				PartitionType.PORTABLE -> "sm mount public:${this.major},${this.minor}"
				PartitionType.ADOPTED -> "sm mount private:${this.major},${this.minor}"
				else -> "echo 'Warning: Unsure on how to mount this partition.'"
			}
		}
		fun unmount(): String {
			return when (type) {
				PartitionType.FREE -> "true"
				PartitionType.PORTABLE -> "sm unmount public:${this.major},${this.minor}"
				PartitionType.ADOPTED -> "sm unmount private:${this.major},${this.minor}"
				PartitionType.RESERVED -> {
					if (name == "abm_settings") {
						// TODO unmount bootset
						"true"
					} else {
						"echo 'Warning: Unsure on how to unmount this partition.'"
					}
				}
				PartitionType.SYSTEM, PartitionType.DATA -> {
					// TODO rework this when dual android is supported by looking at current os' xpart
					"true"
				}
				else -> "echo 'Warning: Unsure on how to unmount this partition.'"
			}
		}

		fun delete(): String {
			if (type == PartitionType.FREE)
				return "echo 'Tried to delete free space'"
			return "sgdisk " + meta.path + " --delete " + id
		}
		fun rename(newName: String): String {
			if (type == PartitionType.FREE)
				return "echo 'Tried to rename free space'; exit 1"
			return "sgdisk " + meta.path + " --change-name " + id + ":'" + newName.replace("'", "") + "'"
		}

		class FreeSpace(meta: SDPartitionMeta, start: Long, end: Long, bytes: Int) :
			Partition(meta,
				PartitionType.FREE, 0, "", (start / 2048 + 1) * 2048, end, bytes, "", "", 0, 0) {

			override val path
				get() = throw IllegalArgumentException()

			override fun toString(): String {
				return "FreeSpace{" +
						"startSector=" + startSector +
						", endSector=" + endSector +
						", size=" + size +
						", sizeFancy='" + sizeFancy + '\'' +
						'}'
			}

			// start & end are RELATIVE to startSector of this instance
			fun create(start: Long, end: Long, typecode: String, name: String): String {
				val rstart = startSector + start
				val rend = startSector + end
				val a = rstart > endSector
				val b = start < 0
				val c = rend > endSector
				val d = end < start
				if (a || b || c || d) {
					return "echo 'Invalid values ($start:$end - $rstart>$startSector:$endSector>$rend - $a $b $c $d). Aborting...'; exit 1"
				}
				return "sgdisk ${meta.path} --new ${meta.nid}:$rstart:$rend --typecode ${meta.nid}:$typecode --change-name ${meta.nid}:'${name.replace("'", "")}' && sleep 1 && ls ${meta.ppath}${meta.nid}" + when(typecode) {
					 "0700" -> " && sm format public:${meta.major},${meta.minor+meta.nid}"
					 "8301" -> " && mkfs.ext4 ${meta.ppath}${meta.nid}"
					 else -> ""
				}
			}
		}
	}

	class SDPartitionMeta {
		// List of partitions sorted by partition number
		var p: MutableList<Partition> = ArrayList()
		// List of partitions with free space entries, unsorted
		var u: MutableList<Partition> = ArrayList()
		// List of partitions with free space entries, sorted by physical order
		var s: MutableList<Partition> = ArrayList()
		var friendlySize: String? = null
		var guid: String? = null
		var sectors: Long = 0
		var logicalSectorSizeBytes = 0
		var maxEntries = 0
		var firstUsableSector: Long = 0
		var lastUsableSector: Long = 0
		var ismbr : Boolean = false
		var alignSector: Long = 0
		var totalFreeSectors: Long = 0
		var totalFreeFancy: String? = null
		var usableSectors: Long = 0
		// First free partition number
		var nid = 0
		var major = 0
		var minor = 0
		var path: String? = null
		var ppath: String? = null
		override fun toString(): String {
			return "SDPartitionMeta{" +
					"p=" + p +
					", s=" + s +
					", friendlySize='" + friendlySize + '\'' +
					", guid='" + guid + '\'' +
					", sectors=" + sectors +
					", logicalSectorSizeBytes=" + logicalSectorSizeBytes +
					", maxEntries=" + maxEntries +
					", firstUsableSector=" + firstUsableSector +
					", lastUsableSector=" + lastUsableSector +
					", alignSector=" + alignSector +
					", totalFreeSectors=" + totalFreeSectors +
					", totalFreeFancy='" + totalFreeFancy + '\'' +
					", usableSectors=" + usableSectors +
					'}'
		}

		// Get partition by kernel id
		fun dumpKernelPartition(id: Int): Partition {
			return p.stream().filter { it.id == id }.findFirst().getOrElse {
				throw IllegalArgumentException("no such partition with id $id, have: ${this.p}") }
		}
	}
}
