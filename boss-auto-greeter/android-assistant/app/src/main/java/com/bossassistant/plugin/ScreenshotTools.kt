package com.bossassistant.plugin

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Bitmap.Config
import android.graphics.Bitmap.CompressFormat
import android.os.Build
import android.view.Display
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object ScreenshotTools {
    private fun screenshotDisplayId(service: AccessibilityService): Int {
        return service.display?.displayId ?: Display.DEFAULT_DISPLAY
    }

    fun captureBitmap(service: AccessibilityService, onResult: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onResult(null)
            return
        }

        service.takeScreenshot(
            screenshotDisplayId(service),
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val wrapped = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                    val copy = wrapped?.copy(Config.ARGB_8888, false)
                    wrapped?.recycle()
                    screenshot.hardwareBuffer.close()
                    onResult(copy)
                }

                override fun onFailure(errorCode: Int) {
                    onResult(null)
                }
            }
        )
    }

    fun captureAndSaveFailure(
        service: AccessibilityService,
        reasonTag: String,
        onResult: (String?) -> Unit
    ) {
        captureBitmap(service) { bitmap ->
            if (bitmap == null) {
                onResult(null)
                return@captureBitmap
            }

            val dir = File(service.filesDir, "failure_screenshots")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val safeTag = reasonTag.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_\\-]"), "_")
            val file = File(dir, "fail_${System.currentTimeMillis()}_${safeTag}.png")
            runCatching {
                FileOutputStream(file).use { out ->
                    bitmap.compress(CompressFormat.PNG, 100, out)
                    out.flush()
                }
            }.onFailure {
                onResult(null)
                return@captureBitmap
            }
            onResult(file.absolutePath)
        }
    }

    fun decode(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    }
}
