package net.wushilin.kts

import java.io.IOException

fun ZPool_List(host:Host):List<ZPoolState> {
    var result = mutableListOf<ZPoolState>()
    var listResult = host.execute("zpool list -o name,size,cap,health")
    if(listResult.isSuccessful()) {
        var outputs = listResult.outputs()
        var sublist = outputs.filter{
            it.isNotBlank()
        }.toMutableList()
        sublist.removeFirst()
        sublist.forEach {
            var reversed = it.reversed()
            var delimeter = " \t"
            var (healthR, first3) = nextToken(reversed, delimeter)
            var (capR, first2) = nextToken(first3, delimeter)
            var (sizeR, nameR) = nextToken(first2, delimeter)
            var name = nameR.reversed()
            var size = sizeR.reversed()
            var cap = capR.reversed()
            var health = healthR.reversed()
            result.add(ZPoolState(name, size, cap.substring(0, cap.length - 1).toFloat()/100, health))
        }

    } else {
        throw IOException("Failed to run zpool list at target")
    }
    return result
}

fun ZPool_GetState(host:Host, pool:String):ZPoolStatus {
    var execResult = host.execute("zpool status '$pool'")
    if(!execResult.isSuccessful()) {
        throw IOException("zpool status '$pool' didn't succeed, code:${execResult.exitCode}, msg:${execResult.error()}")
    }

    var outputs = execResult.outputs()
    var configStarted = false
    var configEnded = false

    var state = ""
    var errors = ""
    var disks = mutableListOf<ZPoolDiskStatus>()
    outputs.forEach {
        var delimeter = " \t:"
        var line = it.trim()
        if(!configStarted) {
            if(line == "") {
                return@forEach
            }
            var (key, remaining) = nextToken(line, delimeter)
            if (key == "state") {
                state = remaining
            }
            if (key == "config") {
                configStarted = true
            }
            return@forEach
        } else {
            // read configs
            if(!configEnded) {
                if (line == "" && disks.size > 0) {
                    configEnded = true
                    return@forEach
                }
                if(line == "") {
                    return@forEach
                }
                var (matched, matchResult) = matchWhole(line, "^\\s*(.*?)\\s+(\\S+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+).*$")
                if (!matched) {
                    return@forEach
                }
                var diskStatus =
                    ZPoolDiskStatus(matchResult[1], matchResult[2], matchResult[3].toInt(), matchResult[4].toInt(), matchResult[5].toInt())
                disks.add(diskStatus)

            } else {
                if(line == "") {
                    return@forEach
                }
                var delimeter = " \t:"
                var (name, remaining) = nextToken(line, delimeter)
                errors = remaining
            }
        }
    }
    return ZPoolStatus(pool, state, disks, errors)
}

fun ZFS_Snapshot(host:Host, zfs:String, snapshot:String) {
    var createResult = host.execute("zfs snapshot '$zfs@$snapshot'")
    if(!createResult.isSuccessful()) {
        throw IOException("Create snapshot failed: ${createResult.error()}")
    }
}

fun ZFS_Send(srcHost:Host, srcSnapshot:String, parentSnapshot:String, destHost:Host, remoteZFS:String) {
    var runResult: ProcessResult
    if(parentSnapshot != "") {
        if(destHost.isLocal()) {
            runResult = srcHost.execute("zfs send -I '$parentSnapshot' '$srcSnapshot' | zfs recv '$remoteZFS'")
        } else {
            runResult = srcHost.execute("zfs send -I '$parentSnapshot' '$srcSnapshot' | ssh ${destHost.user}@${destHost.host} zfs recv '$remoteZFS'")
        }
    } else {
        if(destHost.isLocal()) {
            runResult = srcHost.execute("zfs send '$srcSnapshot' | zfs recv '$remoteZFS'")
        } else {
            runResult = srcHost.execute("zfs send '$srcSnapshot' | ssh ${destHost.user}@${destHost.host} zfs recv '$remoteZFS'")
        }
    }
    if(!runResult.isSuccessful()) {
        throw IOException("ZFS Send error: ${runResult.error()}")
    }
}
fun ZFS_RollbackSnapshot(host:Host, zfs:String, snapshot:String) {
    var rollbackResult = host.execute("zfs rollback -r '$zfs@$snapshot'")
    if(!rollbackResult.isSuccessful()) {
        throw IOException("Rollback snapshot failed: ${rollbackResult.error()}")
    }
}
fun ZFS_DestroySnapshot(host:Host, zfs:String, snapshot:String) {
    var deleteResult = host.execute("zfs destroy '$zfs@$snapshot'")
    if(!deleteResult.isSuccessful()) {
        throw IOException("Delete snapshot failed: ${deleteResult.error()}")
    }
}

fun ZFS_List(host: Host):List<ZFS> {
    var result = mutableListOf<ZFS>()
    var listResult = host.execute("zfs list")
    if(!listResult.isSuccessful()) {
        throw IOException("zfs list failed, ${listResult.error()}")
    }

    var outputs = listResult.outputs().toMutableList()
    outputs.removeFirst()

    outputs.forEach {
        var (matched, groups) = matchWhole(it, "^\\s*(.*?)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(.*?)\\s*$")
        if(matched) {
            var newZfs = ZFS(name=groups[1], used=groups[2], available=groups[3], refer=groups[4], mountPoint=groups[5])
            result.add(newZfs)
        }
    }
    return result
}

fun ZFS_GetSnapShot(host: Host, name:String):List<ZFSSnapShot>{
    var execResult = host.execute("zfs list -t snapshot '$name'")
    if(!execResult.isSuccessful()) {
        throw IOException("zfs list -t snapshot failed: ${execResult.error()}")
    }
    var result = mutableListOf<ZFSSnapShot>()
    var outputs = execResult.outputs().toMutableList()
    outputs.removeFirst()
    outputs.filter {
        it.isNotBlank()
    }.forEach {
        var line = it
        var (matched, groups) = matchWhole(line, "^\\s*(.*?)\\s+(\\d\\S+)\\s+(\\S+)\\s+(\\d\\S+)\\s+(\\S+)\\s*$")
        if(matched) {
            var snapshotName = groups[1]
            var used = groups[2]
            var available = groups[3]
            var refer = groups[4]
            var mountPoint = groups[5]
            var index = snapshotName.indexOf('@')
            var zfs = snapshotName.substring(0, index)
            var snapshot = snapshotName.substring(index + 1)
            result.add(ZFSSnapShot(zfs, snapshot, used, available, refer, mountPoint))
        }
    }
    return result
}

fun ZFS_GetProperty(host:Host, name:String):Map<String, ZFSProperty> {
    val result = mutableMapOf<String, ZFSProperty>()
    val runResult = host.execute("zfs get all '$name'")
    if(!runResult.isSuccessful()) {
        throw IOException("Failed get ZFS property: ${runResult.error()}")
    }

    val outputs = runResult.outputs().toMutableList()
    val firstLine = outputs.removeFirst()
    val startFirst = firstLine.indexOf("NAME")
    val startSecond = firstLine.indexOf("PROPERTY")
    val startThird = firstLine.indexOf("VALUE")
    val startLast = firstLine.indexOf("SOURCE")
    outputs.forEach {
        if(startLast >= it.length) {
            return@forEach
        }
        val name = it.substring(startFirst, startSecond).trim()
        val property = it.substring(startSecond, startThird).trim()
        var value = it.substring(startThird, startLast).trim()
        var source = it.substring(startLast).trim()
        if(source == "-") {
            source = ""
        }
        if(value == "-") {
            value = ""
        }
        var newStat = ZFSProperty(name, property, value, source)
        result[property] = newStat
    }
    return result
}

data class ZFSSnapShot(var zfs:String, var snapshot:String, val used:String, val available:String, val refer:String, val mountPoint:String)
data class ZFS(val name:String, val used:String, val available:String, val refer:String, val mountPoint:String)
data class ZPoolState(val name:String, val size:String, val cap:Float, val health:String)
data class ZPoolStatus(val name:String, val state:String, val diskStatus:List<ZPoolDiskStatus>, val errors:String)
data class ZPoolDiskStatus(val name:String, val state:String, val readError:Int, val writeError:Int, val cksumError:Int)
data class ZFSProperty(val zfs:String, val propertyName:String, val propertyValue:String, val source:String)