package wtf.mxl.sfkt.util

import java.io.File

object FileHelper {

    fun createOrUpdate(file: File, content: String) {
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }
        file.writeText(content)
    }

    fun read(file: File): String {
        return if (file.exists()) file.readText() else ""
    }

    fun delete(file: File): Boolean {
        return if (file.exists()) file.delete() else true
    }
}
