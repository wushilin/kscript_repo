import net.wushilin.kts.*

val host1 = Host("wushilin", "awsec2.wushilin.net", "sh")
val host2 = Host("wushilin", "localhost", "sh")
val bazinga = Host("root", "192.168.44.100", "sh")
val stargazer = Host("root", "192.168.44.9")
val localhost = Host("root", "127.0.0.1")
fun main(args:Array<String>) {
    val root1 = BTRFS_GetRoot(stargazer, "/mnt/btrfs")
    val root2 = BTRFS_GetRoot(stargazer, "/mnt/btrfs2")

    println(BTRFS_SubvolumeIsReadonly(stargazer, "/mnt/btrfs/sv3/"))
    return
    val subv = BTRFS_ListSubvolumes(stargazer, root1)
    println(root1.fullPath())
    subv.forEach {
        println(it.fullPath())
    }

    val snaps = BTRFS_ListSnapshots(stargazer, root1)
    snaps.forEach {
        println(it.fullPath())
    }

    val subvW = mutableListOf<BTRFS>()
    subvW.add(root1)
    subvW.addAll(subv)
    subvW.removeAll(snaps)
    var now = "snap@" + System.currentTimeMillis()
    subvW.forEach {
        println("Creating snapshot for ${it.fullPath()}")
        BTRFS_Snapshot(stargazer, it, now, true)
    }

    now = "thesnap@" + System.currentTimeMillis()
    BTRFS_Snapshot(stargazer, root1, now)
    var snapshots = BTRFS_ListSnapshots(stargazer, root1)
    snapshots = snapshots.toMutableList().filter {
        it.fullPath().endsWith(now)
    }
    snapshots.forEach {
        BTRFS_Send(stargazer, it, null, stargazer, root2.fullPath() + "/.snapshots")
    }

    BTRFS_ListSubvolumes(stargazer, root2).forEach {
        println(it)
    }

}

