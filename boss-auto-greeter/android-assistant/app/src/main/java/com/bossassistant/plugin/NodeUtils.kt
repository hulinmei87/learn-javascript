package com.bossassistant.plugin

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

data class NodeLocatorRule(
    val textCandidates: List<String> = emptyList(),
    val viewIdCandidates: List<String> = emptyList(),
    val classCandidates: List<String> = emptyList()
)

object NodeUtils {
    fun nodeText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val direct = node.text?.toString()?.trim().orEmpty()
        if (direct.isNotEmpty()) return direct
        val contentDesc = node.contentDescription?.toString()?.trim().orEmpty()
        if (contentDesc.isNotEmpty()) return contentDesc
        return node.hintText?.toString()?.trim().orEmpty()
    }

    private fun containsAnyIgnoreCase(source: String, candidates: List<String>): Boolean {
        if (source.isBlank()) return false
        return candidates.any { token ->
            token.isNotBlank() && source.contains(token, ignoreCase = true)
        }
    }

    private fun sanitizeRule(rule: NodeLocatorRule): NodeLocatorRule {
        fun sanitize(values: List<String>): List<String> = values.map { it.trim() }.filter { it.isNotBlank() }
        return NodeLocatorRule(
            textCandidates = sanitize(rule.textCandidates),
            viewIdCandidates = sanitize(rule.viewIdCandidates),
            classCandidates = sanitize(rule.classCandidates)
        )
    }

    private fun firstVisibleNode(nodes: List<AccessibilityNodeInfo>, requireVisible: Boolean): AccessibilityNodeInfo? {
        return nodes.firstOrNull { !requireVisible || it.isVisibleToUser }
    }

    private fun findByViewId(
        root: AccessibilityNodeInfo,
        viewIds: List<String>,
        requireVisible: Boolean
    ): AccessibilityNodeInfo? {
        for (viewId in viewIds) {
            val found = runCatching { root.findAccessibilityNodeInfosByViewId(viewId) }.getOrElse { emptyList() }
            firstVisibleNode(found, requireVisible)?.let { return it }
        }
        return null
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

    fun findNodeByRule(
        root: AccessibilityNodeInfo?,
        rule: NodeLocatorRule,
        requireVisible: Boolean = true
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        val normalized = sanitizeRule(rule)
        if (normalized.textCandidates.isEmpty() &&
            normalized.viewIdCandidates.isEmpty() &&
            normalized.classCandidates.isEmpty()
        ) {
            return null
        }

        findByViewId(root, normalized.viewIdCandidates, requireVisible)?.let { return it }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (requireVisible && !node.isVisibleToUser) {
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
                continue
            }

            val text = nodeText(node)
            val className = node.className?.toString().orEmpty()
            val textOk = normalized.textCandidates.isEmpty() || containsAnyIgnoreCase(text, normalized.textCandidates)
            val classOk = normalized.classCandidates.isEmpty() || containsAnyIgnoreCase(className, normalized.classCandidates)
            val hasFilter = normalized.textCandidates.isNotEmpty() || normalized.classCandidates.isNotEmpty()
            if (hasFilter && textOk && classOk) {
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

    fun findEditableNodeByRule(
        root: AccessibilityNodeInfo?,
        rule: NodeLocatorRule,
        requireVisible: Boolean = true
    ): AccessibilityNodeInfo? {
        val byRule = findNodeByRule(root, rule, requireVisible)
        if (byRule != null && byRule.isEditable) {
            return byRule
        }

        val editable = findEditableNode(root)
        if (editable != null) {
            return editable
        }
        return byRule
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
