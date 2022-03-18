package net.wushilin.kts

import java.io.IOException
import java.util.*

fun BTRFS_GetRoot(host:Host, path:String):BTRFSRoot {
    val runResult = host.execute("btrfs fi show '$path'")
    if(!runResult.isSuccessful()) {
        throw IOException("BTRFS fi list failed: ${runResult.error()}")
    }
    runResult.outputs().forEach {
        var (matchResult, groups) = matchWhole(it, "^\\s*Label:\\s+(.*?)\\s+uuid: (\\S+).*$")
        if(matchResult) {
            return BTRFSRoot(path, groups[2], groups[1])
        }
    }
    throw IOException("No result matching that pattern found: ${runResult.stdout()}")
}

fun BTRFS_SubvolumeIsReadonly(host:Host, path:String):Boolean {
    val runResult = host.execute("btrfs subvolume show '$path' | grep 'Flags:' | grep 'readonly'")
    if(!runResult.isSuccessful()) {
        return false
    }
    return true
}

fun BTRFS_IsRoot(host:Host, path:String):Boolean {
    try {
        val fInfo = BTRFS_GetRoot(host, path)
        return fInfo.uuid != ""
    } catch(ex:Exception) {
        return false
    }
}

fun BTRFS_ListSnapshots(host:Host, root:BTRFSRoot):List<BTRFSSubvolume> {
    return BTRFS_ListSubvolumes(host, root, true)
}

fun BTRFS_DeleteSubvolume(host:Host, btrfs:BTRFS) {
    var command = "btrfs subvolume delete '${btrfs.fullPath()}'"
    val runResult = host.execute(command)
    if(!runResult.isSuccessful()) {
        throw IOException("Failed to delete subvolume ${btrfs.fullPath()}: ${runResult.error()}")
    }
}

fun BTRFS_Send(srcHost:Host, srcSnapshot:BTRFSSubvolume, parentSnapshot:BTRFSSubvolume? = null, destHost:Host, destFolder:String) {
    var command = ""
    var sshTag = if(destHost.host == srcHost.host) "" else "ssh ${destHost.user}@${destHost.host}"
    destHost.execute("mkdir -p '${destFolder}'")
    if(parentSnapshot == null) {
        command = "btrfs send '${srcSnapshot.fullPath()}' | $sshTag btrfs receive '${destFolder}'"
    } else {
        command = "btrfs send '${srcSnapshot.fullPath()}' -p '${parentSnapshot.fullPath()}' | $sshTag btrfs receive '${destFolder}'"
    }
    var runResult = srcHost.execute(command)
    if(!runResult.isSuccessful()) {
        throw IOException("Failed to send: ${runResult.error()}")
    }
}

fun BTRFS_Snapshot(host:Host, btrfs:BTRFS, name:String, readOnly:Boolean = true) {
    var command = "btrfs subvolume snapshot -r '${btrfs.fullPath()}' '${btrfs.fullPath()}/.snapshots/$name'"
    if(!readOnly) {
        command = "btrfs subvolume snapshot -r '${btrfs.fullPath()}' '${btrfs.fullPath()}/.snapshots/$name'"
    }
    host.execute("mkdir -p '${btrfs.fullPath()}'/.snapshots")
    val runResult = host.execute(command)
    if(!runResult.isSuccessful()) {
        throw IOException("Failed to create snapshot: ${runResult.error()}")
    }
}

fun BTRFS_ListSubvolumes(host:Host, root:BTRFSRoot, snapshotOnly:Boolean=false):List<BTRFSSubvolume> {
    // btrfs subvolume list -suR /mnt/volume3
    var flag = "-uR"
    if(snapshotOnly) {
        flag = "-suR"
    }
    val runResult = host.execute("btrfs subvolume list $flag '${root.path}'")
    if(!runResult.isSuccessful()) {
        throw IOException("Failed to show sub volume for ${root.path}: ${runResult.error()}")
    }

    val result = mutableListOf<BTRFSSubvolume>()
    runResult.outputs().forEach {
            line->
        var (matched, groups) = matchWhole(line, "ID\\s+(\\d+)\\s+gen\\s+(\\d+).*" +
                "top level\\s+(\\d+).*\\s+received_uuid\\s+(\\S+)\\s+uuid\\s+(\\S+)\\s+path\\s+(.*?)\\s*")
        if(matched) {
            var receivedUuid = if(groups[4] == "-") "" else groups[4].trim()
            var uuid = if(groups[5] == "-") "" else groups[5].trim()
            var path = groups[6].trim()
            result.add(BTRFSSubvolume(root, id=groups[1].toLong(), generation = groups[2].toLong(), topLevel = groups[3].toLong(),
                receivedUuid= receivedUuid, uuid=uuid, path))
        }
    }
    return result.sortedBy {
        it.fullPath()
    }
}
interface BTRFS {
    fun fullPath():String
}
data class BTRFSRoot(val path:String, val uuid:String, val label:String):BTRFS {
    override fun fullPath():String {
        return path
    }
}

data class BTRFSSubvolume(val root:BTRFSRoot, val id:Long, val generation:Long, val topLevel:Long, val receivedUuid:String, val uuid:String, val path:String):BTRFS {
    override fun fullPath():String {
        return root.path + "/" + path
    }
}