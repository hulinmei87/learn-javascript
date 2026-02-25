package com.bossassistant.plugin

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

class OcrClickHelper(private val service: AccessibilityService) {
    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    fun clickByKeywords(keywords: List<String>, onResult: (Boolean, String) -> Unit) {
        val terms = keywords.map { it.trim() }.filter { it.isNotBlank() }
        if (terms.isEmpty()) {
            onResult(false, "关键词为空")
            return
        }

        ScreenshotTools.captureBitmap(service) { bitmap ->
            if (bitmap == null) {
                onResult(false, "截图失败")
                return@captureBitmap
            }

            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    val rect = findMatchRect(result.textBlocks.mapNotNull { block ->
                        val blockRect = block.boundingBox
                        val blockText = block.text.orEmpty()
                        if (blockRect == null || blockText.isBlank()) null else blockRect to blockText
                    }, terms) ?: findMatchRect(
                        result.textBlocks.flatMap { block ->
                            block.lines.mapNotNull { line ->
                                val r = line.boundingBox
                                val t = line.text.orEmpty()
                                if (r == null || t.isBlank()) null else r to t
                            }
                        },
                        terms
                    )

                    if (rect == null) {
                        onResult(false, "OCR未匹配到关键词")
                        return@addOnSuccessListener
                    }

                    performClick(rect, onResult)
                }
                .addOnFailureListener { error ->
                    onResult(false, "OCR识别失败: ${error.message}")
                }
        }
    }

    private fun findMatchRect(candidates: List<Pair<Rect, String>>, keywords: List<String>): Rect? {
        return candidates.firstOrNull { (_, text) ->
            keywords.any { keyword -> text.contains(keyword, ignoreCase = true) }
        }?.first
    }

    private fun performClick(rect: Rect, onResult: (Boolean, String) -> Unit) {
        val cx = rect.centerX().toFloat()
        val cy = rect.centerY().toFloat()
        val path = Path().apply { moveTo(cx, cy) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 120L))
            .build()
        val dispatched = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    onResult(true, "OCR点击成功")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    onResult(false, "OCR点击被取消")
                }
            },
            null
        )

        if (!dispatched) {
            onResult(false, "手势下发失败")
        }
    }
}
