package grammar

import grammar.rule.EarleyRule

class EarleyGrammar : Grammar() {
    val ruleSet: HashMap<String, MutableList<EarleyRule>> = hashMapOf()

    override fun importRules(rawRule: String) {
        super.importRules(rawRule)

        for (rule in super.rules) {
            val earleyRule = EarleyRule(
                currentLiteralIndex = 0,
                completed = false,
                rule.resultLiteral,
                rule.literals,
                rule.probability,
                rule.ruleName
            )

            if (ruleSet.containsKey(earleyRule.resultLiteral)) {
                val keyLiterals = ruleSet[earleyRule.resultLiteral]
                keyLiterals!!.add(earleyRule)

                ruleSet[earleyRule.resultLiteral] = keyLiterals
            } else {
                ruleSet[earleyRule.resultLiteral] = arrayListOf(earleyRule)
            }
        }
    }
}
