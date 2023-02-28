/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2022.

*/

package com.eterocell.nekoegram.update

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.eterocell.nekoegram.nekoegramVersion
import com.eterocell.nekoegram.store.NekoeStore
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.telegram.messenger.*
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.Components.TypefaceSpan
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat

object UpdateUtils {

    private const val uri = "https://api.github.com/repos/Eterocell/Nekoegram/releases/latest"
    private var id = 0L
    private var updateCheckInterval = 3600000L // 1 hour
    private var updateDownloaded = false

    private var version = ""
    private var changelog = ""
    private var size = ""
    private var uploadDate = ""
    private var downloadUrl = ""

    private var otaPath: File? = null
    private var versionPath: File? = null
    private var apkFile: File? = null

    private val userAgents = arrayListOf(
        "Mozilla/5.0 (iPhone; CPU iPhone OS 10_0 like Mac OS X) AppleWebKit/602.1.38 (KHTML, like Gecko) Version/10.0 Mobile/14A5297c Safari/602.1",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/105.0.0.0 Safari/537.36,gzip(gfe)",
        "Mozilla/5.0 (X11; Linux x86_64; rv:10.0) Gecko/20150101 Firefox/47.0 (Chrome)",
        "Mozilla/5.0 (Linux; Android 6.0; Nexus 7 Build/MRA51D) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/44.0.2403.133 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_5) AppleWebKit/600.8.9 (KHTML, like Gecko) Version/8.0.8 Safari/600.8.9",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Ubuntu Chromium/44.0.2403.89 Chrome/44.0.2403.89 Safari/537.36",
        "Mozilla/5.0 (Linux; Android 5.0.2; SAMSUNG SM-G920F Build/LRX22G) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/3.0 Chrome/38.0.2125.102 Mobile Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; rv:40.0) Gecko/20100101 Firefox/40.0"
    )

    private fun getRandomUserAgent(): String = userAgents[Utilities.random.nextInt(userAgents.size)]

    private fun checkDirs() {
        val externalDir = ApplicationLoader.applicationContext.getExternalFilesDir(null)
        otaPath = File(externalDir, "ota")
        versionPath = File(otaPath, version)
        apkFile = File(versionPath, "update.apk")

        if (!versionPath!!.exists()) {
            versionPath!!.mkdirs()
        }
        updateDownloaded = apkFile!!.exists()
    }

    fun checkUpdates(context: Context, manual: Boolean) {
        checkUpdates(context, manual, object : OnUpdate {
            override fun foundThen() {}
            override fun notFoundThen() {}
        })
    }

    fun checkUpdates(context: Context, manual: Boolean, onUpdate: OnUpdate) {
        Utilities.globalQueue.postRunnable {
            NekoeStore.getLastUpdateCheckTime(context)
            NekoeStore.setLastUpdateCheckTime(System.currentTimeMillis(), context)

            if (id != 0L || (System.currentTimeMillis() - NekoeStore.getUpdateScheduleTimestamp(
                    context
                ) < updateCheckInterval && !manual)
            ) {
                return@postRunnable
            }

            try {
                val connection = URI(uri).toURL().openConnection() as HttpURLConnection
                with(connection) {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", getRandomUserAgent())
                    setRequestProperty("Content-Type", "application/json")
                }

                val textBuilder = StringBuilder()
                val reader = BufferedReader(
                    InputStreamReader(
                        connection.inputStream, StandardCharsets.UTF_8
                    )
                )
                reader.useLines {
                    textBuilder.append(it)
                }

                val obj = JSONObject(textBuilder.toString())
                val arr = obj.getJSONArray("assets")

                if (arr.length() == 0) {
                    return@postRunnable
                }

                var link: String
                var cpu = ""
                try {
                    val pInfo = ApplicationLoader.applicationContext.packageManager.getPackageInfo(
                        ApplicationLoader.applicationContext.packageName, 0
                    )
                    when (PackageInfoCompat.getLongVersionCode(pInfo) % 10) {
                        1L, 3L -> {
                            cpu = "arm-v7a"
                        }
                        2L, 4L -> {
                            cpu = "x86"
                        }
                        5L, 7L -> {
                            cpu = "arm64-v8a"
                        }
                        6L, 8L -> {
                            cpu = "x86_64"
                        }
                        0L, 9L -> {
                            cpu = "universal"
                        }
                    }
                } catch (e: Exception) {
                    cpu = Build.SUPPORTED_ABIS[0]
                }

                for (i in 0 until arr.length()) {
                    link = arr.getJSONObject(i).getString("browser_download_url")
                    downloadUrl = link
                    size = AndroidUtilities.formatFileSize(arr.getJSONObject(i).getLong("size"))
                    if (link.contains("arm64") && cpu == "arm64-v8a" || link.contains("armv7") && cpu == "armeabi-v7a" || link.contains(
                            "x86"
                        ) && cpu == "x86" || link.contains("x64") && cpu == "x86_64" || link.contains(
                            "universal"
                        ) && cpu == "universal"
                    ) {
                        break
                    }
                }

                version = obj.getString("name")
                changelog = obj.getString("body")
                uploadDate = obj.getString("published_at").replace("[TZ]".toRegex(), " ")
                uploadDate = LocaleController.formatDateTime(getMillisFromDate(uploadDate) / 1000)

                if (isNewVersion(nekoegramVersion, version)) {
                    checkDirs()
                    AndroidUtilities.runOnUIThread {
                        UpdaterBottomSheet(
                            context,
                            true,
                            UpdateInfo(version, changelog, size, downloadUrl, uploadDate)
                        ).show()
                        onUpdate.foundThen()
                    }
                } else {
                    AndroidUtilities.runOnUIThread {
                        onUpdate.notFoundThen()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun downloadApk(context: Context, link: String, title: String) {
        if (!updateDownloaded) {
            val request = DownloadManager.Request(Uri.parse(link))
            with(request) {
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE or DownloadManager.Request.NETWORK_WIFI)
                setTitle(title)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(
                    context, "ota/$version", "update.apk"
                )
            }

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            id = manager.enqueue(request)

            val downloadReceiver = DownloadReceiver()
            val intentFilter = IntentFilter()
            with(intentFilter) {
                addAction("android.intent.action.DOWNLOAD_COMPLETE")
                addAction("android.intent.action.DOWNLOAD_NOTIFICATION_CLICKED")
            }
            context.registerReceiver(downloadReceiver, intentFilter)
        } else {
            installApk(context, apkFile!!.absolutePath)
        }
    }

    fun installApk(context: Context, path: String) {
        val file = File(path)
        if (!file.exists()) {
            return
        }
        val install = Intent(Intent.ACTION_VIEW)
        val fileUri: Uri? = if (Build.VERSION.SDK_INT >= 24) {
            FileProvider.getUriForFile(
                context, ApplicationLoader.getApplicationId() + ".provider", file
            )
        } else {
            Uri.fromFile(file)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ApplicationLoader.applicationContext.packageManager.canRequestPackageInstalls()) {
            AlertsCreator.createApkRestrictedDialog(context, null).show()
            return
        }
        if (fileUri != null) {
            install.setDataAndType(fileUri, "application/vnd.android.package-archive")
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (install.resolveActivity(context.packageManager) != null) {
                context.startActivity(install)
            }
        }
    }

    private fun isNewVersion(orig: String, new: String): Boolean {
        var replacedOrig = orig.replace("\\D+".toRegex(), "")
        var replacedNew = new.replace("\\D+".toRegex(), "")

        if (replacedOrig.toInt() <= 999) {
            replacedOrig += "0"
        }
        if (replacedNew.toInt() <= 999) {
            replacedNew += "0"
        }

        return replacedOrig.toInt() < replacedNew.toInt()
    }

    fun getOtaDirSize(): String? {
        checkDirs()
        return AndroidUtilities.formatFileSize(
            Utilities.getDirSize(
                otaPath?.absolutePath, 5, true
            ), true
        )
    }

    fun cleanOtaDir() {
        checkDirs()
        otaPath?.let { cleanFolder(it) }
    }

    private fun cleanFolder(file: File) {
        val files = file.listFiles()
        if (files != null) {
            for (f in files) {
                if (f.isDirectory) {
                    cleanFolder(f)
                }
                f.delete()
            }
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun getMillisFromDate(d: String): Long {
        val sdf = SimpleDateFormat("yyyy-M-dd hh:mm:ss")
        val date = sdf.parse(d)
        return date?.time ?: 1L
    }

    fun replaceTags(str: String): SpannableStringBuilder {
        try {
            var start: Int
            var end: Int
            val stringBuilder = StringBuilder(str)
            val spannableStringBuilder = SpannableStringBuilder(str)
            var symbol = ""
            var font = "fonts/rregular.ttf"
            for (i in 0..2) {
                when (i) {
                    0 -> {
                        symbol = "**"
                        font = "fonts/rmedium.ttf"
                    }
                    1 -> {
                        symbol = "_"
                        font = "fonts/ritalic.ttf"
                    }
                    2 -> {
                        symbol = "`"
                        font = "fonts/rmono.ttf"
                    }
                }
                while (stringBuilder.indexOf(symbol).also { start = it } != -1) {
                    stringBuilder.replace(start, start + symbol.length, "")
                    spannableStringBuilder.replace(start, start + symbol.length, "")
                    end = stringBuilder.indexOf(symbol)
                    if (end >= 0) {
                        stringBuilder.replace(end, end + symbol.length, "")
                        spannableStringBuilder.replace(end, end + symbol.length, "")
                        spannableStringBuilder.setSpan(
                            TypefaceSpan(
                                AndroidUtilities.getTypeface(
                                    font
                                )
                            ), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
            }
            return spannableStringBuilder
        } catch (e: Exception) {
            FileLog.e(e)
        }
        return SpannableStringBuilder(str)
    }

    fun translate(
        text: CharSequence, onTranslation: OnTranslation
    ) {
        Utilities.globalQueue.postRunnable {
            var uri: String?
            val connection: HttpURLConnection
            try {
                uri = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl="
                uri += Uri.encode(
                    LocaleController.getInstance().currentLocale.language
                )
                uri += "&dt=t&ie=UTF-8&oe=UTF-8&otf=1&ssel=0&tsel=0&kc=7&dt=at&dt=bd&dt=ex&dt=ld&dt=md&dt=qca&dt=rw&dt=rm&dt=ss&q="
                uri += Uri.encode(text.toString())
                connection = URI(uri).toURL().openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty(
                    "User-Agent", getRandomUserAgent()
                )
                connection.setRequestProperty("Content-Type", "application/json")
                val textBuilder = StringBuilder()
                BufferedReader(
                    InputStreamReader(
                        connection.inputStream, StandardCharsets.UTF_8
                    )
                ).use { reader ->
                    var c: Int
                    while (reader.read().also { c = it } != -1) textBuilder.append(c.toChar())
                }
                val tokenizer = JSONTokener(textBuilder.toString())
                val array = JSONArray(tokenizer)
                val array1 = array.getJSONArray(0)
                val result = StringBuilder()
                for (i in 0 until array1.length()) {
                    val blockText = array1.getJSONArray(i).getString(0)
                    if (blockText != null && blockText != "null") result.append(blockText)
                }
                if (text.isNotEmpty() && text[0] == '\n') result.insert(0, "\n")
                AndroidUtilities.runOnUIThread {
                    onTranslation.successThen(
                        result.toString()
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onTranslation.failThen()
            }
        }
    }

    private class DownloadReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent!!.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                if (id == intent.getLongExtra(
                        DownloadManager.EXTRA_DOWNLOAD_ID, -1
                    )
                ) {
                    installApk(
                        context!!, apkFile!!.absolutePath
                    )
                    id = 0L
                    updateDownloaded = false
                }
            } else if (intent.action == DownloadManager.ACTION_NOTIFICATION_CLICKED) {
                val viewDownloadIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
                viewDownloadIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context!!.startActivity(viewDownloadIntent)
            }
        }
    }
}

interface OnUpdate {
    fun foundThen()
    fun notFoundThen()
}

interface OnTranslation {
    fun successThen(translated: String)
    fun failThen()
}
