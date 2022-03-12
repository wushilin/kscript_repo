package net.wushilin.kts

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread


fun KTS_Version():String {
    return "v1.0.0"
}

fun flock(path:String):Flock {
    return Flock(path)
}

data class Flock (val path:String): Closeable {
    var fos = FileOutputStream(path)
    var channel = fos.getChannel()
    var lock = channel.lock()

    override fun close() {
        lock.close()
        channel.close()
        fos.close()
    }
}
fun <E> nextBatch(what:MutableCollection<E>, limit:Int):Collection<E> {
    val iter = what.iterator()
    val result = mutableListOf<E>()
    while(iter.hasNext() && result.size < limit) {
        val elem = iter.next()
        result.add(elem)
        iter.remove()
    }
    return result
}

fun formatDate(time: Date =Date(), what:String="yyyy-MM-dd'T'HH:mm:ss"):String {
    return SimpleDateFormat(what).format(time)
}

fun parseDate(what:String, format:String="yyyy-MM-dd'T'HH:mm:ss"):Date{
    return SimpleDateFormat(format).parse(what)
}

fun matchPartial(what:String, regex:String):Sequence<MatchResult>{
    var regexp = Regex(regex)
    var results = regexp.findAll(what)
    return results
}
fun matchWhole(what:String, regex:String):Pair<Boolean, List<String>> {
    var result = mutableListOf<String>()
    var regexp = Regex(regex)
    var matcher = regexp.matchEntire(what)
    if(matcher != null) {
        result.addAll(matcher.groupValues)
        return true to result
    } else {
        return false to result
    }
}

fun nextToken(what:String, delimeter:String): Pair<String, String> {
    var foundYet = false
    var startIndex = 0
    what.toCharArray().forEachIndexed {
        idx, char ->
        if(delimeter.indexOf(char) >= 0) {
            if(foundYet) {
                return what.substring(startIndex, idx).trim() to what.substring(idx + 1).trim()
            }
        } else {
            if(!foundYet) {
                startIndex = idx
                foundYet = true
            }
        }
    }
    return what.substring(startIndex).trim() to ""
}

data class Host(val user:String, val host:String, val shell:String = "sh") {
    fun ping():Boolean {
        val result = execute("echo HELLO")
        return result.isSuccessful() && result.outputs().isNotEmpty() && result.outputs()[0] == "HELLO"
    }
    fun execute(cmd:String):ProcessResult {
        return if(isLocal()) {
            Run.ExecWait(File("/"), -1, cmd.toByteArray().inputStream(), listOf(shell))
        } else {
            Run.ExecWait(File("/"), -1, cmd.toByteArray().inputStream(), listOf("ssh", "$user@$host", shell))
        }
    }
    fun isLocal():Boolean {
        return "localhost".equals(host, ignoreCase = true) || "127.0.0.1" == host || "::1" == host
    }

    fun isTheSame(other:Host):Boolean {
        return host == other.host && user == other.user
    }
}
class Run {
    companion object {
        val MAX_TIMEOUT = 999999999999999
        fun ExecWait(workingDirectory: File, timeoutMs:Long, stdin: InputStream?=null, args: List<String>): ProcessResult {
            val pb = ProcessBuilder(args)
            pb.directory(workingDirectory)
            val startMs = System.currentTimeMillis()
            val process = pb.start()
            lateinit var stdout:ByteArray
            lateinit var stderr:ByteArray
            val stdoutThread = thread(name="stdout-reader") {
                process.inputStream.use {
                    stdout = it.readAllBytes()
                }
            }
            val stderrThread = thread(name="stderr-reader") {
                process.errorStream.use {
                    stderr = it.readAllBytes()
                }
            }

            var pipeThread: Thread? = null
            if(stdin != null) {
                pipeThread = thread(name = "input-writer") {
                    process.outputStream.use {
                        stdin.use { input ->
                            input.copyTo(process.outputStream)
                        }
                    }
                }
            }

            var realTimeoutMs = timeoutMs
            if(realTimeoutMs >= MAX_TIMEOUT || realTimeoutMs <= 0) {
                realTimeoutMs = MAX_TIMEOUT
            }


            val waitResult = process.waitFor(realTimeoutMs, TimeUnit.MILLISECONDS)
            if(waitResult) {
                stdoutThread.join()
                stderrThread.join()
                pipeThread?.join()
            } else {
                process.destroyForcibly()
                stdoutThread.join()
                stderrThread.join()
                pipeThread?.join()
            }
            val exitCode = process.exitValue()
            val pid = process.pid()
            return ProcessResult(pid, startMs, System.currentTimeMillis(), exitCode, stdout, stderr)
        }
    }
}


data class ProcessResult(val pid:Long, val startMs:Long, val endMs:Long, val exitCode:Int, val stdout:ByteArray, val stderr:ByteArray) {
    private val stdoutList = mutableListOf<String>()
    private val stderrList = mutableListOf<String>()
    private val stdoutString = String(stdout)
    private val stderrString = String(stderr)
    init {
        var split = stdoutString.split("\n")
        split.forEach {
            stdoutList.add(it)
        }

        split = stderrString.split("\n")
        split.forEach {
            stderrList.add(it)
        }
    }
    override fun toString():String {
        return "ProcessResult[#pid=$pid, code=$exitCode, stdout=>>>>>${stdoutString}<<<<<, stderr=>>>>>${stderrString}<<<<<, duration=${endMs-startMs}ms#]"
    }

    fun isSuccessful():Boolean {
        return exitCode == 0
    }

    fun error():String = stderrString

    fun stdout():String = stdoutString

    fun errors():List<String> {
        return stderrList
    }

    fun outputs():List<String> {
        return stdoutList
    }
}

