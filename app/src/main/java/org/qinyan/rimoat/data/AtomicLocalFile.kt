package org.qinyan.rimoat.data

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Writes alongside the target, then atomically replaces it. Failed writes leave the old file intact. */
internal object AtomicLocalFile {
    fun write(target: File, text: String) {
        val parent = target.parentFile ?: throw IOException(AppStrings.get(R.string.msg_directory_missing, "文件目录不存在"))
        if (!parent.exists() && !parent.mkdirs()) throw IOException(AppStrings.get(R.string.msg_directory_create_failed, "无法创建存储目录"))
        val temporary = File.createTempFile(".${target.name}.", ".tmp", parent)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            if (android.os.Build.VERSION.SDK_INT in 1..25) {
                if (!temporary.renameTo(target)) throw IOException(AppStrings.get(R.string.msg_remote_file_replace_failed, "无法替换遥控器文件，原文件已保留"))
            } else {
                // File.renameTo cannot replace an existing file on Windows (JVM tests).
                // Require atomic replacement; never delete the original before moving.
                java.nio.file.Files.move(temporary.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }
}
