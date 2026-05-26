package com.mrjack.dressflow.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.mrjack.dressflow.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

private const val GITHUB_API_URL =
    "https://api.github.com/repos/luanmtb200/DressFlow/releases/latest"

data class AppVersion(val version: String, val apkUrl: String, val notes: String)

suspend fun checkForUpdate(context: Context): AppVersion? = withContext(Dispatchers.IO) {
    try {
        val conn = URL(GITHUB_API_URL).openConnection()
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        val json = JSONObject(conn.getInputStream().bufferedReader().readText())

        val tagName = json.getString("tag_name")           // ex: "android-v1.3"
        val version = tagName.removePrefix("android-v")   // ex: "1.3"

        if (!isNewerVersion(version, BuildConfig.VERSION_NAME)) return@withContext null

        val assets = json.getJSONArray("assets")
        var apkUrl = ""
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.getString("name").endsWith(".apk")) {
                apkUrl = asset.getString("browser_download_url")
                break
            }
        }
        if (apkUrl.isBlank()) return@withContext null

        AppVersion(version, apkUrl, json.optString("body", ""))
    } catch (_: Exception) { null }
}

fun isNewerVersion(remote: String, current: String): Boolean {
    val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
    val c = current.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(r.size, c.size)) {
        val rv = r.getOrElse(i) { 0 }
        val cv = c.getOrElse(i) { 0 }
        if (rv > cv) return true
        if (rv < cv) return false
    }
    return false
}

suspend fun downloadAndInstallSync(context: Context, apkUrl: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "dressflow-update.apk")
        if (apkFile.exists()) apkFile.delete()

        val conn = URL(apkUrl).openConnection()
        conn.connectTimeout = 30_000
        conn.readTimeout = 120_000
        conn.connect()
        conn.getInputStream().use { input ->
            apkFile.outputStream().use { output -> input.copyTo(output) }
        }

        installApk(context, apkFile)
        true
    } catch (_: Exception) { false }
}

private fun installApk(context: Context, apkFile: File) {
    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
    } else {
        Uri.fromFile(apkFile)
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
