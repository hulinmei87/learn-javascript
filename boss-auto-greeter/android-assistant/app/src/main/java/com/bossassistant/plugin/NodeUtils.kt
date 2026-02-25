package com.bossassistant.plugin

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

object NodeUtils {
    private fun nodeText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val direct = node.text?.toString()?.trim().orEmpty()
        if (direct.isNotEmpty()) return direct
        return node.contentDescription?.toString()?.trim().orEmpty()
    }

    fun findNodeByTexts(
        root: AccessibilityNodeInfo?,
        textCandidates: List<String>,
        requireVisible: Boolean = true
    ): AccessibilityNodeInfo? {
        if (root == null || textCandidates.isEmpty()) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = nodeText(node)
            val visible = !requireVisible || node.isVisibleToUser
            if (visible && textCandidates.any { t -> t.isNotBlank() && text.contains(t, ignoreCase = true) }) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    fun findEditableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val className = node.className?.toString().orEmpty()
            if (node.isVisibleToUser && node.isEditable && className.contains("EditText")) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    fun clickNodeOrClickableParent(node: AccessibilityNodeInfo?): Boolean {
        var cur = node
        while (cur != null) {
            if (cur.isClickable) {
                return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            cur = cur.parent
        }
        return false
    }

    fun setText(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }
}
