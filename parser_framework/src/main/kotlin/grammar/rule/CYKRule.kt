package grammar.rule

import model.cyk.CYKToken

class CYKRule(
    private val literal_one: String,
    private val literal_two: String,
    resultLiteral: String,
    literals: Array<String>,
    probability: Double = 1.0,
    ruleName: String
) : Rule(resultLiteral, literals, probability, ruleName) {
    override fun copyOf(): Rule {
        return CYKRule(literal_one, literal_two, resultLiteral, literals, probability, ruleName)
    }

    fun returnCYKToken(ruleName: String, leftToken: CYKToken, rightToken: CYKToken): CYKToken {
        return CYKToken(
            ruleName,
            this.resultLiteral,
            this.probability * leftToken.possiblility * rightToken.possiblility,
            leftToken,
            rightToken,
            leftToken.begin,
            rightToken.end
        )
    }
}
