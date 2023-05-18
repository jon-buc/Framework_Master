package parser.modified

import controller.STTS
import controller.Tokenizer
import controller.addAnnotationsToCAS
import controller.sentenceAnnotations
import de.uniwue.aries.uima.types.Types
import grammar.CYKGrammar
import grammar.rule.Rule
import model.parsedTree.PARSER_TYPE
import model.parsedTree.TREE_TYPE
import model.parsedTree.TreeItem
import org.apache.uima.cas.CAS
import org.apache.uima.util.CasCopier
import org.apache.uima.util.CasCreationUtils
import parser.CYKParser
import java.io.File

class CYKGrammarSuggestions(
    override val grammar: CYKGrammar,
    override val directoryForXMI: String,
    private var restrictedLiterals: MutableSet<String> = mutableSetOf(),
    private val pathForLogFile: String = ""
) :
    CYKParser(grammar, directoryForXMI) {

    private val parsedRules: MutableSet<String> = mutableSetOf()

    fun resetRestrictedLiterals(literals: MutableSet<String>) {
        restrictedLiterals.clear()
        restrictedLiterals.addAll(literals)
    }

    override fun parseCAS(
        cas: CAS,
        writeToDirectory: Boolean
    ): ArrayList<CAS> {
        var incorrectCounter = 0
        var startIndex = 0
        val listCAS = arrayListOf<CAS>()
        var parsedCAS: CAS = CasCreationUtils.createCas(Types.getTypeSystem(), null, null)
        CasCopier.copyCas(cas, parsedCAS, true)
        val ruleSuggestions = mutableMapOf<String, Int>()
        parsedCAS.sentenceAnnotations()?.forEach {
            var incorrect = false
            val tokens = Tokenizer.rfTagger(it.coveredText, startIndex)

            val cykMatrix = parseCYKMatrixByTokens(tokens)

            val parsingTrees = findParsingTree(cykMatrix)

            for (tree in parsingTrees) {
                if (tree.treeType == TREE_TYPE.INCOMPLETED) {
                    parsedRules.clear()
                    incorrect = true
                    addToMap(ruleSuggestions, findMissingRules("S", tree.treeRoot.children))
                }
            }

            if (incorrect) {
                incorrectCounter++
            }

            val annotatedCAS = parsedCAS.addAnnotationsToCAS(parsingTrees)

            if (annotatedCAS.size > 1) {
                listCAS.addAll(annotatedCAS)
            } else {
                parsedCAS = annotatedCAS[0]
            }

            startIndex = tokens[tokens.size - 1].end + 1
        }
        val result = ruleSuggestions.toList().sortedByDescending { (_, v) -> v }.toMap()

        if (pathForLogFile.isNotEmpty()) {
            File(pathForLogFile).outputStream()
                .use { out ->
                    result.keys.forEach {
                        out.write("$it [${result[it]!!}]".toByteArray()); out.write("\n".toByteArray())
                    }
                }
        } else {
            result.keys.forEach {
                println("$it [${result[it]!!}]")
            }
        }

        if (listCAS.size == 0) {
            listCAS.add(parsedCAS)
        }
        if (writeToDirectory) {
            writeToDirectory(listCAS, PARSER_TYPE.CYK)
        }
        return listCAS
    }

    private fun findMissingRules(
        literal: String,
        parsedLiterals: MutableList<TreeItem>
    ): MutableMap<String, Int> {
        val ruleSuggestions = mutableMapOf<String, Int>()
        val rules = mutableListOf<Rule>()
        super.grammar.ruleMap[literal]?.let { rules.addAll(it) }
        val parsedLiteralsSorted = mutableListOf<TreeItem>()
        parsedLiteralsSorted.addAll(parsedLiterals)

        var index = 0
        while (index < parsedLiteralsSorted.size) {
            if (restrictedLiterals.contains(parsedLiteralsSorted[index].literal) || parsedLiteralsSorted[index].terminalText.contains(
                    " "
                )
            ) {
                val newLiteral = parsedLiteralsSorted.removeAt(index)
                parsedLiteralsSorted.addAll(index, newLiteral.children)
            } else {
                index++
            }

        }

        parsedLiteralsSorted.sortBy { it.begin }
        for (rule in rules) {
            if (rule.literals.size < parsedLiteralsSorted.size) {
                var possibleRule = true
                var ruleIndex = 0
                val ruleLiterals = rule.literals.toMutableList()
                val literalList = mutableListOf<TreeItem>()
                literalList.addAll(parsedLiteralsSorted)
                val rsl = mutableListOf<MutableList<String>>()
                val tsl = mutableListOf<MutableList<TreeItem>>()

                var deletedLiteralIndex = -1
                var deletedRuleIndex = -1
                while (ruleIndex < ruleLiterals.size) {
                    var parsedLiteralsIndex = if (deletedLiteralIndex == -1) {
                        0
                    } else {
                        deletedLiteralIndex
                    }
                    while (parsedLiteralsIndex < literalList.size) {
                        if (ruleLiterals[ruleIndex] == literalList[parsedLiteralsIndex].literal) {

                            if (((parsedLiteralsIndex == deletedLiteralIndex && ruleIndex != deletedRuleIndex) || (parsedLiteralsIndex < deletedLiteralIndex + 2 && ruleIndex != deletedRuleIndex)) && deletedLiteralIndex != -1 && deletedRuleIndex != -1) {
                                possibleRule = false
                                ruleIndex = ruleLiterals.size
                                parsedLiteralsIndex = literalList.size
                            } else {
                                if (deletedLiteralIndex == -1) {
                                    deletedLiteralIndex = 0
                                }
                                val leftParsedTokens = literalList.slice(
                                    IntRange(
                                        deletedLiteralIndex,
                                        parsedLiteralsIndex - 1
                                    )
                                ).toMutableList()
                                if (deletedRuleIndex == -1) {
                                    deletedRuleIndex = 0
                                }
                                val leftRuleTokens =
                                    ruleLiterals.slice(IntRange(deletedRuleIndex, ruleIndex - 1))
                                        .toMutableList()

                                if (leftParsedTokens.isNotEmpty() && leftRuleTokens.isNotEmpty() && leftRuleTokens.size * 2 > leftParsedTokens.size) {
                                    possibleRule = false
                                    ruleIndex = ruleLiterals.size
                                    parsedLiteralsIndex = literalList.size
                                } else {
                                    if (leftParsedTokens.isNotEmpty()) {
                                        rsl.add(
                                            leftRuleTokens
                                        )
                                        tsl.add(
                                            leftParsedTokens
                                        )
                                    }
                                    ruleLiterals.removeAt(ruleIndex)
                                    literalList.removeAt(parsedLiteralsIndex)
                                    deletedLiteralIndex = parsedLiteralsIndex
                                    deletedRuleIndex = ruleIndex
                                    parsedLiteralsIndex = literalList.size
                                    ruleIndex--
                                }

                            }
                        }
                        parsedLiteralsIndex++
                    }
                    ruleIndex++
                }
                if (deletedRuleIndex != rule.literals.size - 1 && deletedLiteralIndex == parsedLiteralsSorted.size - 1) {
                    possibleRule = false
                }
                if (deletedLiteralIndex != parsedLiteralsSorted.size - 1) {
                    if (deletedLiteralIndex == -1) {
                        deletedLiteralIndex = 0
                    }
                    val leftParsedTokens = literalList.slice(
                        IntRange(
                            deletedLiteralIndex,
                            literalList.size - 1
                        )
                    ).toMutableList()

                    if (deletedRuleIndex == -1) {
                        deletedRuleIndex = 0
                    }
                    val leftRuleTokens =
                        ruleLiterals.slice(IntRange(deletedRuleIndex, ruleLiterals.size - 1))
                            .toMutableList()

                    if (leftParsedTokens.isNotEmpty() && leftRuleTokens.isNotEmpty() && leftRuleTokens.size * 2 > leftParsedTokens.size) {
                        possibleRule = false
                    } else {
                        if (leftParsedTokens.isNotEmpty()) {
                            rsl.add(
                                leftRuleTokens
                            )
                            tsl.add(
                                leftParsedTokens
                            )
                        }
                    }
                }

                if (possibleRule) {
                    if (rsl.isEmpty() && ruleLiterals.isNotEmpty()) {
                        rsl.add(ruleLiterals)
                        tsl.add(literalList)
                    }
                    for (i in rsl.indices) {
                        if (rsl[i].isEmpty() && tsl[i].isNotEmpty()) {
                            addToMap(
                                ruleSuggestions, onlyTokensLeftOfParsing(
                                    rule,
                                    parsedLiteralsSorted,
                                    tsl[i]
                                )
                            )
                        } else if (rsl[i].size == 1 && tsl[i].isNotEmpty()) {
                            addToMap(
                                ruleSuggestions, oneNonTerminalsLeft(
                                    rsl[i][0],
                                    tsl[i]
                                )
                            )
                        } else if (rsl[i].size > 1 && tsl[i].isNotEmpty()) {
                            addToMap(
                                ruleSuggestions, moreNonTerminalsLeft(
                                    rsl[i],
                                    tsl[i],
                                    rule,
                                    parsedLiteralsSorted,
                                )
                            )
                        }
                    }
                }
            } else if (rule.literals.contentDeepEquals(parsedLiteralsSorted.map { it.literal }
                    .toTypedArray())) {
                return ruleSuggestions
            }
        }
        if (rules.isEmpty() || ruleSuggestions.isEmpty()) {
            val shortTokens = shortTokenSequence(parsedLiteralsSorted)

            val newRule =
                "$literal = ${parsedLiteralsSorted.joinToString(" ") { it.literal }}"
            val replacedSequence = parsedLiteralsSorted.joinToString(" ") { it.literal }
            if (!parsedRules.contains(newRule)) {
                parsedRules.add(newRule)
                addToMapOne(
                    ruleSuggestions, newRule.replace(
                        replacedSequence,
                        shortTokens.joinToString(" ")
                    )
                )
            }
        }
        return ruleSuggestions
    }

    private fun onlyTokensLeftOfParsing(
        rule: Rule,
        parsedLiterals: MutableList<TreeItem>,
        leftTokens: MutableList<TreeItem>
    ): MutableMap<String, Int> {
        val ruleSuggestions = mutableMapOf<String, Int>()
        val shortTokens = shortTokenSequence(leftTokens)

        var newRule =
            "${rule.resultLiteral} = ${parsedLiterals.joinToString(" ") { it.literal }}"
        parsedRules.add(newRule)
        val replacedSequence = leftTokens.joinToString(" ") { it.literal }

        var rightLiteral = parsedLiterals.indexOf(leftTokens[leftTokens.size - 1])
        rightLiteral = if (rightLiteral != parsedLiterals.size - 1) {
            rightLiteral + 1
        } else {
            -1
        }

        var leftLiteral = parsedLiterals.indexOf(leftTokens[0])
        leftLiteral = if (leftLiteral != 0) {
            leftLiteral - 1
        } else {
            -1
        }

        if (rightLiteral != -1 && !STTS.containsTag(parsedLiterals[rightLiteral].literal)) {
            leftTokens.addAll(parsedLiterals[rightLiteral].children)
            val recreatedRule =
                "${parsedLiterals[rightLiteral].literal} = ${leftTokens.joinToString(" ") { it.literal }}"
            if (!parsedRules.contains(recreatedRule)) {
                addToMap(
                    ruleSuggestions, findMissingRules(
                        parsedLiterals[rightLiteral].literal,
                        leftTokens
                    )
                )
            }
        }

        if (leftLiteral != -1 && !STTS.containsTag(parsedLiterals[leftLiteral].literal)) {
            leftTokens.addAll(parsedLiterals[leftLiteral].children)
            val recreatedRule =
                "${parsedLiterals[leftLiteral].literal} = ${leftTokens.joinToString(" ") { it.literal }}"
            if (!parsedRules.contains(recreatedRule)) {
                addToMap(
                    ruleSuggestions, findMissingRules(
                        parsedLiterals[leftLiteral].literal,
                        leftTokens
                    )
                )
            }
        }

        if (ruleSuggestions.isEmpty()) {
            newRule = if (shortTokens.size == 1) {
                if (shortTokens[0].contains("+")) {
                    shortTokens[0].replace("+", "*")
                    newRule.replace(replacedSequence, "${shortTokens.joinToString(" ")}")
                } else {
                    newRule.replace(replacedSequence, "${shortTokens.joinToString(" ")}?")
                }

            } else {
                newRule.replace(replacedSequence, "(${shortTokens.joinToString(" ")})?")
            }
            addToMapOne(ruleSuggestions, newRule)
        }
        return ruleSuggestions
    }

    private fun oneNonTerminalsLeft(
        nonTerminal: String,
        leftTokens: MutableList<TreeItem>
    ): MutableMap<String, Int> {
        val ruleSuggestions = mutableMapOf<String, Int>()
        if (!STTS.containsTag(nonTerminal)) {

            val newRule =
                "$nonTerminal = ${leftTokens.joinToString(" ") { it.literal }}"

            if (!parsedRules.contains(newRule)) {
                addToMap(ruleSuggestions, findMissingRules(nonTerminal, leftTokens))
            }
        }
        return ruleSuggestions
    }

    private fun moreNonTerminalsLeft(
        nonTerminals: MutableList<String>,
        leftTokens: MutableList<TreeItem>,
        rule: Rule,
        parsedLiterals: MutableList<TreeItem>,
    ): MutableMap<String, Int> {
        val ruleSuggestions = mutableMapOf<String, Int>()
        val modifiedNonTerminals = mutableListOf<String>()
        for (nonT in nonTerminals) {
            if (!STTS.containsTag(nonT)) {
                modifiedNonTerminals.add(nonT)
            }
        }
        if (modifiedNonTerminals.size == 0) {
            onlyTokensLeftOfParsing(
                rule,
                parsedLiterals,
                leftTokens
            )
        } else if (modifiedNonTerminals.size == 1) {
            oneNonTerminalsLeft(
                modifiedNonTerminals[0],
                leftTokens
            )
        } else if (modifiedNonTerminals.size * 2 <= leftTokens.size) {
            var endSequence = leftTokens.size - (2 * (modifiedNonTerminals.size - 1)) - 1
            var startSequence = 0
            for (nonT in modifiedNonTerminals) {
                val sublist = leftTokens.slice(IntRange(startSequence, endSequence)).toMutableList()

                if (!STTS.containsTag(nonT)) {
                    addToMap(ruleSuggestions, createTokensFromSublist(nonT, sublist))
                    startSequence += 2
                    endSequence += 2
                }
            }
        }
        return ruleSuggestions
    }

    private fun createTokensFromSublist(
        nonTerminal: String,
        sublist: MutableList<TreeItem>
    ): MutableMap<String, Int> {
        val ruleSuggestions = mutableMapOf<String, Int>()
        val tokenList = mutableListOf(sublist[0], sublist[1])

        var recreatedRule =
            "$nonTerminal = ${tokenList.joinToString(" ") { it.literal }}"

        if (!parsedRules.contains(recreatedRule)) {
            val suggestions = findMissingRules(nonTerminal, tokenList)

            if (suggestions.isNotEmpty()) {
                addToMap(ruleSuggestions, suggestions)
            }
        }

        for (i in 2 until sublist.size) {
            tokenList.add(sublist[i])
            recreatedRule =
                "$nonTerminal = ${tokenList.joinToString(" ") { it.literal }}"

            if (!parsedRules.contains(recreatedRule)) {
                val suggestions = findMissingRules(nonTerminal, tokenList)

                if (suggestions.isNotEmpty()) {
                    addToMap(ruleSuggestions, suggestions)
                }
            }
        }
        for (i in 0 until sublist.size - 2) {
            tokenList.removeAt(0)

            recreatedRule =
                "$nonTerminal = ${tokenList.joinToString(" ") { it.literal }}"

            if (!parsedRules.contains(recreatedRule)) {
                val suggestions = findMissingRules(nonTerminal, tokenList)

                if (suggestions.isNotEmpty()) {
                    addToMap(ruleSuggestions, suggestions)
                }
            }
        }
        return ruleSuggestions
    }

    private fun shortTokenSequence(tokens: MutableList<TreeItem>): MutableList<String> {
        val shortTokens = mutableListOf<String>()

        var i = 0
        var repeatedT = ""
        while (i < tokens.size) {
            val currT = tokens[i]

            if (i < tokens.size - 1 && currT.literal == tokens[i + 1].literal) {
                shortTokens.add("${currT.literal}+")
                repeatedT = currT.literal
            } else if (currT.literal != repeatedT) {
                shortTokens.add(currT.literal)
                repeatedT = ""
            }
            i++
        }
        return shortTokens
    }

    private fun addToMap(
        ruleSuggestions: MutableMap<String, Int>,
        newSuggestions: MutableMap<String, Int>
    ) {
        for (sugg in newSuggestions.keys) {
            if (ruleSuggestions.containsKey(sugg)) {
                val count = ruleSuggestions[sugg]
                if (count != null) {
                    ruleSuggestions[sugg] = count + 1
                }
            } else {
                ruleSuggestions[sugg] = newSuggestions[sugg]!!
            }
        }
    }

    private fun addToMapOne(ruleSuggestions: MutableMap<String, Int>, rule: String) {
        if (ruleSuggestions.containsKey(rule)) {
            val count = ruleSuggestions[rule]
            if (count != null) {
                ruleSuggestions[rule] = count + 1
            }
        } else {
            ruleSuggestions[rule] = 1
        }
    }
}
