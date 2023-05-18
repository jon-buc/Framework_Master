package grammar.rule

open class Rule(
    val resultLiteral: String,
    val literals: Array<String>,
    val probability: Double = 1.0,
    val ruleName: String
) {
    open fun copyOf(): Rule {
        return Rule(this.resultLiteral, this.literals.copyOf(), this.probability, this.ruleName)
    }

    override fun equals(other: Any?): Boolean {
        if (other is Rule) {
            if (this.resultLiteral != other.resultLiteral) {
                return false
            }
            if (!this.literals.contentDeepEquals(other.literals)) {
                return false
            }
            if (this.probability != other.probability) {
                return false
            }
            return true
        }
        return false
    }

    override fun hashCode(): Int {
        return this.resultLiteral.hashCode() + this.literals.contentHashCode() + this.probability.hashCode()
    }

    override fun toString(): String {
        return "${this.resultLiteral} = ${this.literals.joinToString(" ")} (${this.probability})"
    }
}
