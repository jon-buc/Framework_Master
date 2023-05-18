package grammar.rule


class EarleyRule(
    var currentLiteralIndex: Int = 0,
    var completed: Boolean = false,
    resultLiteral: String,
    literals: Array<String>,
    probability: Double = 1.0,
    ruleName: String
) : Rule(resultLiteral, literals, probability, ruleName) {
    override fun copyOf(): EarleyRule {
        return EarleyRule(
            currentLiteralIndex,
            completed,
            resultLiteral,
            literals.copyOf(),
            probability,
            ruleName
        )
    }

    override fun hashCode(): Int {
        return currentLiteralIndex + completed.hashCode() + resultLiteral.hashCode() + literals.contentHashCode() + probability.hashCode() + ruleName.hashCode()
    }

    override fun equals(rule: Any?): Boolean {
        return this.hashCode() == rule.hashCode()
    }

    fun getCurrentLiteral(): String {
        return literals[currentLiteralIndex]
    }

    fun incrementCurrentLiteralIndex() {
        currentLiteralIndex++

        if (literals.size == currentLiteralIndex) {
            completed = true
            currentLiteralIndex--
        }
    }

    fun decreaseCurrentLiteralIndex() {
        if (currentLiteralIndex > 0) {
            currentLiteralIndex--
        }
    }

    override fun toString(): String {
        val literalArrayWithStar = arrayListOf<String>()
        literalArrayWithStar.addAll(literals)
        if (completed) {
            literalArrayWithStar.add(currentLiteralIndex + 1, "*")
        } else {
            literalArrayWithStar.add(currentLiteralIndex, "*")
        }

        return "${this.resultLiteral} = ${literalArrayWithStar.joinToString(" ")} (${this.probability})"
    }
}


