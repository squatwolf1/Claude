package com.squatwolf.gagyebu.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.squatwolf.gagyebu.AppExit
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * GitHub 릴리스(build-N 태그, gagyebu.apk)에서 새 버전을 찾아 받아 설치 화면을 띄운다.
 * 저장소가 공개여야 로그인 없이 확인·다운로드된다.
 */
object Updater {
    private const val RELEASES = "https://api.github.com/repos/squatwolf1/Claude/releases?per_page=10"
    private const val ASSET = "gagyebu.apk"

    data class Release(val build: Int, val url: String)

    fun currentBuild(context: Context): Int {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
    }

    /** 지금보다 새 빌드가 있으면 돌려준다. 인터넷이 없거나 저장소가 비공개면 null. */
    suspend fun check(context: Context): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(RELEASES).openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(body)
            val found = (0 until arr.length()).mapNotNull { i ->
                val r = arr.getJSONObject(i)
                if (r.optBoolean("draft") || r.optBoolean("prerelease")) return@mapNotNull null
                val build = r.optString("tag_name").removePrefix("build-").toIntOrNull() ?: return@mapNotNull null
                val assets = r.getJSONArray("assets")
                val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                    .firstOrNull { it.optString("name") == ASSET } ?: return@mapNotNull null
                Release(build, apk.getString("browser_download_url"))
            }.maxByOrNull { it.build }
            found?.takeIf { it.build > currentBuild(context) }
        }.getOrNull()
    }

    suspend fun download(context: Context, release: Release, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "kkoppang-${release.build}.apk")
            val conn = URL(release.url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            val length = conn.contentLengthLong
            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (length > 0) onProgress(done.toFloat() / length)
                    }
                }
            }
            file
        }

    /** 설치 화면을 띄운다. 처음이면 '이 앱에서 설치 허용' 설정 화면으로 보내고 false. */
    fun install(context: Context, file: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            AppExit.leavingForExternal = true
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        AppExit.leavingForExternal = true
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
