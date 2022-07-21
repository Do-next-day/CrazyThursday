package org.dnd

import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Pack {

    private val dataDir = File("data")
    private val outDir = File("out").apply { mkdirs() }

    @JvmStatic
    fun main(args: Array<String>) {
        json()
        zip()
    }

    /**
     * 未找到end时手动扫描, end是最后一个
     */
    private fun scanEnd(): Long {
        val dirs = dataDir.listFiles() ?: return 0
        val files = dirs.flatMap { it.listFiles()?.toList() ?: emptyList() }
        return files.maxOf { it.name.removeSuffix(".txt").toLong() }
    }

    private fun import(list: List<String>) {
        var end = dataDir.resolve("end").let { if (it.isFile) it.readText().toLongOrNull() else null } ?: scanEnd()
        val dir = dataDir.resolve(SimpleDateFormat("yyyy.MM.dd").format(Date()))
        val map = list.associateBy { ++end }
        runBlocking {
            map.forEach() { (id, str) ->
                launch { dir.resolve("$id.txt").writeText(str) }
            }
        }
    }

    private fun json() {
        val dirs = dataDir.listFiles() ?: return
        val map = runBlocking {
            dirs.mapNotNull { dir ->
                val files = dir.listFiles()?.filter { it.isFile } ?: return@mapNotNull null
                files.map {
                    async(Dispatchers.IO) { it.name.removeSuffix(".txt") to it.readText() }
                }.awaitAll()
            }.flatten().toMap()
        }
        val json = Json.encodeToString(map)
        outDir.resolve("pack.json").writeText(json)
    }

    private fun zip() {
        val os = outDir.resolve("pack.zip").outputStream()
        val zos = ZipOutputStream(os)
        dataDir.zip(null, zos)
        zos.close()
        os.close()
    }

    private fun File.zip(path: String?, zos: ZipOutputStream) {
        if (isDirectory) {
            listFiles()?.forEach {
                it.zip(if (path == null) it.name else "$path/${it.name}", zos)
            }
            return
        }
        zos.putNextEntry(ZipEntry(path ?: name))
        inputStream().use { it.copyTo(zos) }
    }
}