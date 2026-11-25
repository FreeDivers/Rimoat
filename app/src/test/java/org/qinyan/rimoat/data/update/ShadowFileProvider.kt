package org.qinyan.rimoat.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.xmlpull.v1.XmlPullParser

/**
 * Mirrors [FileProvider.getUriForFile] in JVM tests on Windows, where AndroidX's
 * `SimplePathStrategy.belongsToRoot` rejects backslash separators and caches per-test roots.
 */
@Implements(FileProvider::class)
class ShadowFileProvider {
    companion object {
        @Implementation
        @JvmStatic
        fun getUriForFile(context: Context, authority: String, file: File): Uri {
            val info = context.packageManager.resolveContentProvider(authority, PackageManager.GET_META_DATA)
                ?: throw IllegalArgumentException("Couldn't find meta-data for provider with authority $authority")
            val parser = info.loadXmlMetaData(context.packageManager, "android.support.FILE_PROVIDER_PATHS")
                ?: throw IllegalArgumentException("Missing android.support.FILE_PROVIDER_PATHS meta-data")
            val roots = mutableListOf<Pair<String, File>>()
            parser.use { xml ->
                var event = xml.next()
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        val base = when (xml.name) {
                            "files-path" -> context.filesDir
                            "cache-path" -> context.cacheDir
                            else -> null
                        }
                        val name = xml.getAttributeValue(null, "name")
                        val sub = xml.getAttributeValue(null, "path").orEmpty()
                        if (base != null && !name.isNullOrEmpty()) {
                            roots += name to File(base, sub).canonicalFile
                        }
                    }
                    event = xml.next()
                }
            }
            val target = file.canonicalPath
            val match = roots
                .filter { (_, root) ->
                    val prefix = root.path.trimEnd(File.separatorChar) + File.separatorChar
                    target.startsWith(prefix)
                }
                .maxByOrNull { (_, root) -> root.path.length }
                ?: throw IllegalArgumentException("Failed to find configured root that contains $target")
            val rootPath = match.second.path.trimEnd(File.separatorChar)
            val relative = target.substring(rootPath.length + 1).replace(File.separatorChar, '/')
            val encodedPath = Uri.encode(match.first) + "/" + Uri.encode(relative, "/")
            return Uri.Builder().scheme("content").authority(authority).encodedPath(encodedPath).build()
        }
    }
}
