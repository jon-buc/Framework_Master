package model.parsedTree

import controller.Token

class ParsedTree(
    val parserType: PARSER_TYPE,
    val treeType: TREE_TYPE,
    val treeRoot: TreeItem,
    private var errorLog: MutableSet<ErrorMessage> = mutableSetOf()
) {
    fun annotateErrorMessage(
        type: MESSAGE_TYPE,
        editDistance: EDIT_DISTANCE,
        tokens: MutableList<Token>,
        tokenIndex: Int
    ) {
        errorLog.add(ErrorMessage(type, editDistance, tokens, tokenIndex))

    }

    fun annotateErrorLog(errorLog: MutableSet<ErrorMessage>) {
        this.errorLog.addAll(errorLog)
    }

    fun logErrorMessage() {
        if (errorLog.isNotEmpty()) {
            for (error in errorLog) {
                println(error.logMessage())
            }
        } else {
            println("There has been no error correction!")
        }
        println()
    }

    fun returnEditDistance(): Int {
        var editDistance = 1
        for (log in errorLog) {
            if (log.editDistance == EDIT_DISTANCE.TWO) {
                editDistance = 2
            }
        }
        return editDistance
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ParsedTree

        if (parserType != other.parserType) return false
        if (treeType != other.treeType) return false
        if (treeRoot != other.treeRoot) return false
        if (errorLog != other.errorLog) return false

        return true
    }

    override fun hashCode(): Int {
        var result = parserType.hashCode()
        result = 31 * result + treeType.hashCode()
        result = 31 * result + treeRoot.hashCode()
        result = 31 * result + errorLog.hashCode()
        return result
    }

    fun toQTree(): String {
        return "\\Tree${treeRoot.toQTree()}"
    }

}

enum class PARSER_TYPE {
    CYK, EARLEY
}

enum class TREE_TYPE {
    COMPLETED, INCOMPLETED, CHANGED
}
