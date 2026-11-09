package com.worldtv.app.data

import java.io.File

data class CachedText(
    val body: String,
    val savedAtMillis: Long,
)

interface TextCache {
    fun read(path: String): CachedText?

    fun write(path: String, body: String)

    fun clear()
}

class FileTextCache(private val root: File) : TextCache {
    companion object {
        const val DIRECTORY = "catalog-v2"
    }
    override fun read(path: String): CachedText? {
        val file = fileFor(path)
        if (!file.isFile) return null
        return CachedText(file.readText(), file.lastModified())
    }

    override fun write(path: String, body: String) {
        val file = fileFor(path)
        file.parentFile?.mkdirs()
        file.writeText(body)
    }

    override fun clear() {
        root.deleteRecursively()
        root.mkdirs()
    }

    private fun fileFor(path: String): File {
        val safe = path.trim().removePrefix("/")
        require(!safe.contains("..") && safe.isNotEmpty()) { "Unsafe cache path" }
        return File(root, safe)
    }
}
