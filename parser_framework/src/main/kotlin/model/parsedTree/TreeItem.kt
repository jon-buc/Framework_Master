package model.parsedTree

class TreeItem(
    val begin: Int,
    val end: Int,
    val terminalText: String,
    val literal: String,
    val children: ArrayList<TreeItem> = arrayListOf(),
) {
    fun getTagCount(): Int {
        var count = 0
        if (children.isEmpty()) {
            count++
        }

        for (child in children) {
            count += child.getTagCount()
        }

        return count
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TreeItem

        if (begin != other.begin) return false
        if (end != other.end) return false
        if (terminalText != other.terminalText) return false
        if (children != other.children) return false

        return true
    }

    override fun hashCode(): Int {
        var result = begin
        result = 31 * result + end
        result = 31 * result + terminalText.hashCode()
        result = 31 * result + children.hashCode()
        return result
    }

    fun toQTree(): String {
        val text = if (terminalText.contains("$")) {
            terminalText.replace("$", "")
        } else {
            terminalText
        }
        var qtree = if (text.contains(" ")) {
            ""
        } else {
            "[.$text"
        }
        for (child in children.sortedBy { it.begin }) {
            qtree += " ${child.toQTree()}"
        }
        return if (text.contains(" ")) {
            qtree
        } else {
            "$qtree ]"
        }
    }

    override fun toString(): String {
        return "TreeItem(terminalText='$terminalText')"
    }
}
