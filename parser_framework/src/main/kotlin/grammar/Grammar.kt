package grammar

import grammar.rule.Rule
import java.nio.file.Files
import java.nio.file.Paths

open class Grammar {

    val rules: MutableSet<Rule> = mutableSetOf()
    val ruleMap: HashMap<String, MutableSet<Rule>> = hashMapOf()
    val ruleNameMap = hashMapOf<String, String>()

    private val STAR = "\u002a"
    private val PLUS = "\u002b"
    private val PIPE = "\u007c"
    private val QUESTION = "\u003f"
    private val BRACKET_S = "\u0028"
    private val BRACKET_E = "\u0029"
    private val EQUALS = "\u003d"
    private val SPACE = "\u0020"
    private val BRACKET_REGEX = ".*\\(.*\\|.*\\).*"

    open fun importRules(rawRule: String) {
        var rawRuleSet = rawRule

        if (Files.exists(Paths.get(rawRuleSet))) {
            //In case a path is given, the content is stored in the ruleSet as a string.
            rawRuleSet = Files.readString(Paths.get(rawRuleSet))
        }

        for (rule in rawRuleSet.split("\n")) {
            if (rule.isNotEmpty() || rule.isNotBlank()) {
                for (cnfRule in convertRuleFromEBNFToBNF(rule)) {
                    val resultLiteral = cnfRule.split(EQUALS)[0]
                    var ruleName = getRuleName(cnfRule)
                    val literals =
                        cnfRule.replace(ruleName, "").trim().split(EQUALS)[1].split(SPACE)
                            .toMutableList()

                    ruleName = if (ruleName.contains("[")) {
                        ruleName.removePrefix("[").removeSuffix("]").replace(" ", "-")
                    } else {
                        resultLiteral
                    }
                    ruleNameMap[resultLiteral] = ruleName

                    val possibilityString = getProb(literals.last())
                    var possibility = 1.0

                    if (possibilityString != "") {
                        possibility = possibilityString.toDouble()
                        literals.removeLast()
                    }
                    val newRule =
                        Rule(resultLiteral, literals.toTypedArray(), possibility, ruleName)
                    rules.add(newRule)

                    if (ruleMap.containsKey(resultLiteral)) {
                        val rules = ruleMap[resultLiteral]
                        rules!!.add(newRule)
                        ruleMap[resultLiteral] = rules
                    } else {
                        val rules = mutableSetOf(newRule)
                        ruleMap[resultLiteral] = rules
                    }
                }
            }
        }
    }

    private fun convertRuleFromEBNFToBNF(rawRule: String): MutableList<String> {
        val rawRules = mutableListOf<String>()

        var formattedRule = rawRule.replace("\\s*\\|\\s*".toRegex(), "|")
        formattedRule = formattedRule.replace("\\s*=\\s*".toRegex(), "=")
        formattedRule = formattedRule.replace("\\(\\s*".toRegex(), "(")
        formattedRule = formattedRule.replace("\\s*\\)".toRegex(), ")")
        formattedRule = formattedRule.replace("\\s+".toRegex(), " ")

        if (formattedRule.matches(BRACKET_REGEX.toRegex())) {
            val createdRules = operatorBracket(formattedRule)

            for (rule in createdRules) {
                rawRules.addAll(convertRuleFromEBNFToBNF(rule))
            }
        } else if (formattedRule.contains(PIPE)) {
            rawRules.addAll(operatorPipe(formattedRule))
        } else {
            if (formattedRule.contains(STAR)) {
                val index = findOperator(formattedRule, STAR)
                rawRules.addAll(operatorStar(formattedRule, index))
            } else if (formattedRule.contains(PLUS)) {
                val index = findOperator(formattedRule, PLUS)
                rawRules.addAll(operatorPlus(formattedRule, index))
            } else if (formattedRule.contains(QUESTION)) {
                val index = findOperator(formattedRule, QUESTION)
                rawRules.addAll(operatorQuestion(formattedRule, index))
            } else {
                rawRules.add(formattedRule)
            }

        }
        return rawRules
    }

    private fun operatorPipe(rawRule: String): MutableList<String> {
        val rawRules = mutableListOf<String>()
        var tokenRules = rawRule

        val tokenSequence = rawRule.split(EQUALS)
        val ruleName = getRuleName(rawRule)
        tokenRules = tokenRules.replace(ruleName, "")
        val prob = getProb(tokenRules)
        val separateRules = tokenSequence[1].replace(prob, "").split(PIPE)
        for (rule in separateRules) {
            rawRules.addAll(convertRuleFromEBNFToBNF("${tokenSequence[0]}$EQUALS$rule $prob $ruleName"))
        }
        return rawRules
    }

    private fun operatorStar(rule: String, index: Int): MutableList<String> {
        val rawRules = mutableListOf<String>()

        val tokenSequence = rule.split(EQUALS)
        val tokens = tokenSequence[1].split(SPACE).toList().toMutableList()
        tokens[index] = tokens[index].replace(STAR, PLUS)

        rawRules.addAll(
            convertRuleFromEBNFToBNF(
                tokenSequence[0] + EQUALS + tokens.joinToString(
                    SPACE
                )
            )
        )

        if (tokens.size > 2) {
            tokens.removeAt(index)
            rawRules.addAll(
                convertRuleFromEBNFToBNF(
                    tokenSequence[0] + EQUALS + tokens.joinToString(
                        SPACE
                    )
                )
            )
        }

        return rawRules
    }

    private fun operatorPlus(rule: String, index: Int): MutableList<String> {
        val rawRules = mutableListOf<String>()

        val tokenSequence = rule.split(EQUALS)
        val tokens = tokenSequence[1].split(SPACE).toList().toMutableList()
        val prob = getProb(rule)
        tokens[index] = tokens[index].replace(PLUS, "")

        if (index == 0) {
            val newRule =
                tokenSequence[0] + EQUALS + tokens[index] + SPACE + tokenSequence[0] + prob
            rawRules.add(newRule)
        } else {
            val newRule = tokens[index] + EQUALS + tokens[index] + SPACE + tokens[index] + prob
            rawRules.add(newRule)
        }

        val changedRule = tokenSequence[0] + EQUALS + tokens.joinToString(SPACE)

        rawRules.addAll(convertRuleFromEBNFToBNF(changedRule))

        return rawRules
    }

    private fun operatorQuestion(rule: String, index: Int): MutableList<String> {
        val rawRules = mutableListOf<String>()

        val tokenSequence = rule.split(EQUALS)
        val tokens = tokenSequence[1].split(SPACE).toList().toMutableList()
        tokens[index] = tokens[index].replace(QUESTION, "")
        rawRules.addAll(
            convertRuleFromEBNFToBNF(
                tokenSequence[0] + EQUALS + tokens.joinToString(
                    SPACE
                )
            )
        )

        tokens.removeAt(index)
        rawRules.addAll(
            convertRuleFromEBNFToBNF(
                tokenSequence[0] + EQUALS + tokens.joinToString(
                    SPACE
                )
            )
        )

        return rawRules
    }

    private fun operatorBracket(rule: String): MutableSet<String> {
        val startBracket = rule.indexOf(BRACKET_S)
        val endBracket = findEndOfBracket(rule, startBracket)
        val bracket = rule.substring(startBracket, endBracket + 1)
        val bracketContent = bracket.removePrefix(BRACKET_S).removeSuffix(BRACKET_E)
        val createdRules = mutableSetOf<String>()

        var bracketItems = bracketContent.split(PIPE).toMutableSet()

        if (bracketContent.matches(BRACKET_REGEX.toRegex())) {
            bracketItems = mutableSetOf()
            for (item in findBracketItems(bracketContent)) {
                if (item.matches(BRACKET_REGEX.toRegex())) {
                    val recursiveItems = operatorBracket(item)

                    for (rItem in recursiveItems) {
                        bracketItems.addAll(rItem.split(PIPE))
                    }
                } else {
                    bracketItems.add(item)
                }
            }
        }

        for (item in bracketItems) {
            val convertedRule = rule.replace(bracket, item)
            if (convertedRule.matches(BRACKET_REGEX.toRegex())) {
                createdRules.addAll(operatorBracket(convertedRule))
            } else {
                createdRules.add(convertedRule)
            }
        }

        return createdRules
    }

    private fun findOperator(rule: String, operator: String): Int {
        val tokenSequence = rule.split(EQUALS)
        val tokens = tokenSequence[1].split(SPACE).toList().toMutableList()

        for (i in tokens.indices) {
            if (tokens[i].contains(operator)) {
                return i
            }
        }

        return -1
    }

    private fun getProb(rule: String): String {
        val prob = rule.split(" ").last()

        val doubleRegex = "\\d+.\\d+"
        val intRegex = "\\d+"
        if (prob.matches(doubleRegex.toRegex()) || prob.matches(intRegex.toRegex())) {
            return " $prob"
        }

        return ""
    }

    private fun getRuleName(rule: String): String {
        if (rule.indexOf("[") > -1 && rule.indexOf("]") > -1) {
            val name = rule.substring(rule.indexOf("["), rule.indexOf("]") + 1)

            if (name.contains("[") || name.contains("]")) {
                return name
            }
        }
        return ""
    }

    private fun findEndOfBracket(rule: String, start: Int): Int {
        var bracketCounter = 0
        val charsOfRule = rule.toCharArray()

        for (i in start..charsOfRule.size) {
            val char = charsOfRule[i]
            if (char == '(') {
                bracketCounter++
            } else if (char == ')') {
                bracketCounter--
            }

            if (bracketCounter == 0) {
                return i
            }
        }

        return -1
    }

    private fun findBracketItems(bracket: String): MutableSet<String> {
        var bracketCounter = 0
        var start = 0
        val charsOfRule = bracket.toCharArray()
        val bracketItems = mutableSetOf<String>()

        for (i in charsOfRule.indices) {
            val char = charsOfRule[i]
            if (char == '(') {
                bracketCounter++
            } else if (char == ')') {
                bracketCounter--
            }

            if (bracketCounter == 0 && char == '|') {
                bracketItems.add(bracket.substring(start, i))
                start = i + 1
            } else if (bracketCounter == 0 && i == charsOfRule.size - 1) {
                bracketItems.add(bracket.substring(start, i + 1))
            }
        }
        return bracketItems
    }
}
