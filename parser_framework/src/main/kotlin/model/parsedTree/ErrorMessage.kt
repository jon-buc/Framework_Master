package model.parsedTree

import controller.Token

class ErrorMessage(
    val type: MESSAGE_TYPE,
    val editDistance: EDIT_DISTANCE,
    tokens: MutableList<Token>,
    tokenIndex: Int
) {
    private var message = ""

    init {
        if (type == MESSAGE_TYPE.DELETED) {
            val errorToken = tokens[tokenIndex]
            message = "[ERROR]: ${errorToken.document}\n" +
                    "Please delete ${tokenIndex + 1}. word \'${errorToken.text}\' from the sentence!"
        } else {
            val errorToken = tokens[tokenIndex]
            val errorText = if (type == MESSAGE_TYPE.SWITCHED) {
                "the word '${errorToken.text}'"
            } else {
                "a word of the type '${errorToken.posTag}'"
            }
            message = when (tokenIndex) {
                0 -> {
                    "[ERROR]: ${errorToken.document}\n" +
                            "Please insert $errorText before '${tokens[tokenIndex + 1].text}'!"
                }
                tokens.size - 1 -> {
                    "[ERROR]: ${errorToken.document}\n" +
                            "Please insert $errorText after '${tokens[tokenIndex - 1].text}'!"
                }
                else -> {
                    "[ERROR]: ${errorToken.document}\n" +
                            "Please insert $errorText between '${tokens[tokenIndex - 1].text}' and '${tokens[tokenIndex + 1].text}'!"
                }
            }
        }
    }


    fun logMessage(): String {
        return message
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ErrorMessage

        if (type != other.type) return false
        if (editDistance != other.editDistance) return false
        if (message != other.message) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + editDistance.hashCode()
        result = 31 * result + message.hashCode()
        return result
    }
}

enum class MESSAGE_TYPE {
    DELETED, SWITCHED, ADDED;
}

enum class EDIT_DISTANCE(val value: Int) {
    ONE(1), TWO(2);

    companion object {
        private val map = values().associateBy(EDIT_DISTANCE::value)
        fun fromInt(type: Int) = map[type]
    }
}
