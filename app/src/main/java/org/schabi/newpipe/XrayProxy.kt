package org.schabi.newpipe

import android.content.Context
import android.util.Log
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

object XrayProxy {
    const val PORT = 10909
    private const val TAG = "XrayProxy"
    private var process: Process? = null

    @JvmStatic
    @Synchronized
    fun start(context: Context) {
        try {
            val dir = File(context.filesDir, "xray").apply { mkdirs() }
            val updated = context.packageManager
                .getPackageInfo(context.packageName, 0).lastUpdateTime
            for (name in listOf("config.json", "geoip.dat", "geosite.dat")) {
                val out = File(dir, name)
                if (!out.exists() || out.lastModified() < updated) {
                    context.assets.open("xray/$name").use { i ->
                        out.outputStream().use { o -> i.copyTo(o) }
                    }
                }
            }
            if (!isPortOpen()) {
                val bin = File(context.applicationInfo.nativeLibraryDir, "libxray.so")
                val pb = ProcessBuilder(bin.path, "run", "-c", File(dir, "config.json").path)
                pb.environment()["XRAY_LOCATION_ASSET"] = dir.path
                pb.redirectErrorStream(true)
                pb.redirectOutput(File(dir, "xray.log"))
                process = pb.start()
                var i = 0
                while (i < 60 && !isPortOpen()) { Thread.sleep(50); i++ }
            }
            for (p in listOf("http", "https")) {
                System.setProperty("$p.proxyHost", "127.0.0.1")
                System.setProperty("$p.proxyPort", PORT.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "failed to start xray", e)
        }
    }

    private fun isPortOpen(): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", PORT), 200); true }
    } catch (e: Exception) { false }
}
