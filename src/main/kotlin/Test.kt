import net.wushilin.kts.*

val host1 = Host("wushilin", "awsec2.wushilin.net", "sh")
val host2 = Host("wushilin", "localhost", "sh")
val bazinga = Host("root", "192.168.44.100", "sh")
val stargazer = Host("root", "192.168.44.9")
fun main(args:Array<String>) {
    //var listResult = ZPool_List(stargazer)
    //println(listResult)
    //println(ZPool_GetState(stargazer, "tank"))
    //println(ZPool_GetState(stargazer, "default"))
    //println(ZFS_List(stargazer))
    //println(ZFS_GetSnapShot(stargazer, "default/containers/wildfly-2"))
    try {
        ZFS_Snapshot(stargazer, "default", "test1")
    } catch(ex:Exception) {
        ex.printStackTrace()
    }
    ZFS_RollbackSnapshot(stargazer, "default", "test1")
    ZFS_DestroySnapshot(stargazer, "default", "test1")
}

