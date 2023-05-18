package grammar

import grammar.rule.CYKRule
import grammar.rule.Rule
import model.cyk.CYKToken

class CYKGrammar : Grammar() {
    val ruleSet: HashMap<String, CYKRule> = hashMapOf()
    var splitRules = hashMapOf<Int, String>()
    var ruleSplitter = 0

    override fun importRules(rawRule: String) {
        super.importRules(rawRule)

        for (rule in super.rules.sortedBy { it.literals.size }) {
            addToMap(rule, rule.resultLiteral, rule.literals.toMutableList())
        }
    }

    fun findMatchingRule(
        token_one: CYKToken,
        token_two: CYKToken,
        findBestMatch: Boolean = false
    ): ArrayList<CYKToken> {
        val literal_one = token_one.literal
        val literal_two = token_two.literal
        var cykTokens = arrayListOf<CYKToken>()

        if (findBestMatch) {
            var possibility = 0.0
            val key = "$literal_one;$literal_two"
            if (ruleSet.containsKey(key)) {
                val rule = ruleSet.get(key)
                val token = rule!!.returnCYKToken(rule.ruleName, token_one, token_two)

                if (token.possiblility > possibility) {
                    cykTokens = arrayListOf(token)
                    possibility = token.possiblility
                } else if (token.possiblility == possibility) {
                    cykTokens.add(token)
                }
            }
        } else {
            val key = "$literal_one;$literal_two"
            if (ruleSet.containsKey(key)) {
                val rule = ruleSet.get(key)
                cykTokens.add(rule!!.returnCYKToken(rule.ruleName, token_one, token_two))
            }

        }
        return cykTokens
    }

    private fun addToMap(rule: Rule, resultingLiteral: String, literals: MutableList<String>) {
        if (literals.size > 2) {
            val right = literals.removeLast()
            val left = literals.removeLast()
            val key = "$left;$right"
            var newResulting = ruleSplitter.toString()

            if (ruleSet.containsKey(key)) {
                newResulting = ruleSet[key]!!.resultLiteral
            } else {
                var splitLeft = left
                var splitRight = right
                if (splitLeft.matches("\\d+".toRegex())) {
                    splitLeft = this.splitRules[splitLeft.toInt()].toString()
                }
                if (splitRight.matches("\\d+".toRegex())) {
                    splitRight = this.splitRules[splitRight.toInt()].toString()
                }
                if (super.ruleNameMap.containsKey(splitRight)) {
                    splitRight = super.ruleNameMap[splitRight].toString()
                }
                if (super.ruleNameMap.containsKey(splitLeft)) {
                    splitLeft = super.ruleNameMap[splitLeft].toString()
                }
                splitRules[ruleSplitter] = "$splitLeft $splitRight"
                ruleSplitter++
            }

            val cykRule =
                CYKRule(left, right, newResulting, rule.literals, rule.probability, rule.ruleName)
            ruleSet[key] = cykRule

            literals.add(newResulting)

            addToMap(cykRule, resultingLiteral, literals)
        } else {
            val right = literals.removeLast()
            val left = literals.removeLast()

            val cykRule = CYKRule(
                left,
                right,
                resultingLiteral,
                rule.literals,
                rule.probability,
                rule.ruleName
            )
            ruleSet["$left;$right"] = cykRule
        }
    }
}
