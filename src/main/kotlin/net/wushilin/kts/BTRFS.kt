package net.wushilin.kts

fun BTRFS_GetFileSystemInfo(host:Host, path:String) {
    host.execute("btrfs fi show '$path'")
}

data class BTRFSRoot(val path:String, val uuid:String, val label:String)

