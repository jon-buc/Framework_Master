package parser.modified

import controller.*
import grammar.EarleyGrammar
import model.earley.EarleyChart
import model.earley.EarleyState
import model.earley.EarleyTable
import model.parsedTree.EDIT_DISTANCE
import model.parsedTree.MESSAGE_TYPE
import model.parsedTree.PARSER_TYPE
import model.parsedTree.ParsedTree
import org.apache.uima.cas.CAS
import parser.EarleyParser

class EarleyParserCorrections(
    grammar: EarleyGrammar,
    directoryForXMI: String,
    private val editDistance: Int
) :
    EarleyParser(
        grammar,
        directoryForXMI
    ) {

    private val parsedTokens = mutableSetOf<MutableList<Token>>()

    override fun parseCAS(
        cas: CAS,
        writeToDirectory: Boolean
    ): ArrayList<CAS> {
        var startIndex = 0
        val listCAS = arrayListOf<CAS>()
        val parsedCAS: CAS = cas
        val parsingTreesForAnnotation = mutableSetOf<MutableSet<ParsedTree>>()
        parsedCAS.sentenceAnnotations()?.forEach {

            val tokens = Tokenizer.rfTagger(it.coveredText, startIndex)
            val earleyTable = parse(tokens)
            earleyTable.editDistance = 1

            val parsedTrees = mutableSetOf<ParsedTree>()
            if (!earleyTable.isComplete()) {
                parsedTrees.addAll(correctIncompleteTable(earleyTable, tokens, this.editDistance))
            }

            parsedTrees.forEach { item -> item.logErrorMessage(); println() }

            if (parsingTreesForAnnotation.size == 0) {
                for (t in parsedTrees) {
                    parsingTreesForAnnotation.add(mutableSetOf(t))
                }
            } else {
                val newlyParsedTreesForAnnotation = mutableSetOf<MutableSet<ParsedTree>>()
                for (pt in parsingTreesForAnnotation) {
                    for (t in parsedTrees) {
                        val currentTrees = mutableSetOf<ParsedTree>()
                        currentTrees.addAll(pt)
                        currentTrees.add(t)
                        newlyParsedTreesForAnnotation.add(currentTrees)
                    }
                }
                parsingTreesForAnnotation.clear()
                parsingTreesForAnnotation.addAll(newlyParsedTreesForAnnotation)
            }

            startIndex = tokens[tokens.size - 1].end + 1
        }
        listCAS.addAll(cas.addParsingTreeAnnotationSetsToCAS(parsingTreesForAnnotation))
        if (writeToDirectory) {
            writeToDirectory(listCAS, PARSER_TYPE.EARLEY)
        }

        return listCAS
    }

    private fun correctIncompleteTable(
        earleyTable: EarleyTable,
        tokens: MutableList<Token>,
        editDistance: Int
    ): MutableSet<ParsedTree> {
        var indexEdit = 1
        val parsedTree = mutableSetOf<ParsedTree>()

        while (indexEdit <= editDistance) {
            this.parsedTokens.clear()

            parsedTree.addAll(deleteToken(earleyTable, tokens, indexEdit))
            parsedTree.addAll(switchTokens(earleyTable, tokens, indexEdit))
            parsedTree.addAll(addToken(earleyTable, tokens, indexEdit))

            if (parsedTree.isNotEmpty()) {
                return parsedTree
            }
            indexEdit++
        }

        return parsedTree
    }

    private fun deleteToken(
        earleyTable: EarleyTable,
        tokens: MutableList<Token>,
        editDistance: Int
    ): MutableSet<ParsedTree> {
        val modifiedTable = earleyTable.copy()
        val modifiedTokens = mutableListOf<Token>()
        modifiedTokens.addAll(tokens)
        var index = deleteTokenFromTable(modifiedTable)
        var tagsChanged = false

        if (index == tokens.size - 1 && tokens[index].posTag == "$.") {
            index--
        }

        val parsedTrees = mutableSetOf<ParsedTree>()

        modifiedTokens.removeAt(index)

        val newTaggedTokens = Tokenizer.reTagTokens(modifiedTokens)

        for (i in 0 until index) {
            if (modifiedTokens[i] != newTaggedTokens[i]) {
                tagsChanged = true
                break
            }
        }
        modifiedTable.annotateErrorMessage(
            MESSAGE_TYPE.DELETED,
            EDIT_DISTANCE.ONE,
            tokens,
            index
        )

        if (!this.parsedTokens.contains(newTaggedTokens)) {
            this.parsedTokens.add(newTaggedTokens)
            if (index == modifiedTokens.size) {
                index--
            }
            if (tagsChanged) {
                parseEarleyTable(modifiedTable, newTaggedTokens, 0)
            } else {
                parseEarleyTable(modifiedTable, modifiedTokens, index)
            }

            if (editDistance == 1) {
                parsedTrees.addAll(
                    findTreeRoot(
                        modifiedTable,
                        newTaggedTokens
                    ).toMutableSet()
                )
            } else {
                parsedTrees.addAll(
                    correctIncompleteTable(
                        modifiedTable,
                        modifiedTokens,
                        editDistance - 1
                    )
                )
            }
        }

        parsedTrees.forEach { it.annotateErrorLog(modifiedTable.errorLog) }
        return parsedTrees
    }

    private fun deleteTokenFromTable(earleyTable: EarleyTable): Int {
        var index = -1
        for (i in earleyTable.earleyCharts.size - 2 downTo 0) {
            val chart = earleyTable.earleyCharts[i]
            if (chart.stateList.isNotEmpty()) {
                index = i
                break
            }

        }

        val chartArray = Array(earleyTable.earleyCharts.size - 1) { EarleyChart() }
        for (i in 0..index) {
            chartArray[i] = earleyTable.earleyCharts[i].copy()
        }
        earleyTable.earleyCharts = chartArray
        earleyTable.sizeOfTable = chartArray.size - 1
        return index
    }

    private fun addToken(
        earleyTable: EarleyTable,
        tokens: MutableList<Token>,
        editDistance: Int
    ): MutableSet<ParsedTree> {
        val modifiedTable = earleyTable.copy()
        val sttsTags = mutableSetOf<String>()
        var index = addTokenToTable(modifiedTable)
        val parsedTrees = mutableSetOf<ParsedTree>()

        for (state in modifiedTable.earleyCharts[index].stateList) {
            if (STTS.containsTag(state.earleyRule.getCurrentLiteral())) {
                sttsTags.add(state.earleyRule.getCurrentLiteral())
            }
        }

        for (tag in sttsTags) {
            val newParsedTrees = mutableSetOf<ParsedTree>()
            var modifiedTagTable = modifiedTable.copy()
            val modifiedTokens = mutableListOf<Token>()
            modifiedTokens.addAll(tokens)
            var tagsChanged = false
            if (index == tokens.size) {
                index--
            }
            val end = if (index == tokens.size) {
                tokens[index - 1].end
            } else {
                tokens[index].begin
            }
            val begin = if (index == 0) {
                0
            } else {
                tokens[index - 1].end
            }
            val document = tokens[index].document
            modifiedTokens.add(
                index,
                Token(begin, end, STTS.getWordFromTag(tag), document, tag)
            )


            if (editDistance == 1) {
                val newTaggedTokens = Tokenizer.reTagTokens(modifiedTokens)

                for (i in 0 until index) {
                    if (modifiedTokens[i] != newTaggedTokens[i]) {
                        tagsChanged = true
                        break
                    }
                }

                if (!this.parsedTokens.contains(newTaggedTokens)) {
                    this.parsedTokens.add(newTaggedTokens)
                    if (tagsChanged) {
                        modifiedTagTable = parse(newTaggedTokens)
                        newParsedTrees.addAll(
                            findTreeRoot(
                                modifiedTagTable,
                                newTaggedTokens
                            ).toMutableSet()
                        )
                    } else {
                        parseEarleyTable(modifiedTagTable, modifiedTokens, index)

                        newParsedTrees.addAll(
                            findTreeRoot(
                                modifiedTagTable,
                                modifiedTokens
                            ).toMutableSet()
                        )
                    }
                } else {
                    newParsedTrees.addAll(
                        correctIncompleteTable(
                            modifiedTable,
                            modifiedTokens,
                            editDistance - 1
                        )
                    )
                }
            }
            modifiedTagTable.annotateErrorMessage(
                MESSAGE_TYPE.ADDED,
                EDIT_DISTANCE.ONE,
                modifiedTokens,
                index
            )

            newParsedTrees.forEach { it.annotateErrorLog(modifiedTagTable.errorLog) }
            parsedTrees.addAll(newParsedTrees)
        }

        return parsedTrees
    }

    private fun addTokenToTable(earleyTable: EarleyTable): Int {
        var index = -1
        val modifiedStateList = mutableSetOf<EarleyState>()

        for (i in earleyTable.earleyCharts.size - 2 downTo 0) {
            val chart = earleyTable.earleyCharts[i]
            if (chart.stateList.isNotEmpty()) {

                for (state in chart.stateList) {

                    if (STTS.containsTag(state.earleyRule.getCurrentLiteral())) {
                        val earleyRule = state.earleyRule.copyOf()
                        earleyRule.incrementCurrentLiteralIndex()
                        modifiedStateList.add(
                            EarleyState(
                                earleyRule,
                                state.addedInRotation
                            )
                        )
                    }
                }
                index = i + 1
                break
            }
        }

        val earleyCharts = Array(earleyTable.earleyCharts.size + 1) { EarleyChart() }

        for (i in 0 until index) {
            earleyCharts[i] = earleyTable.earleyCharts[i].copy()
        }
        earleyCharts[index] = EarleyChart(modifiedStateList)

        earleyTable.earleyCharts = earleyCharts
        earleyTable.sizeOfTable = earleyCharts.size - 1

        return index - 1
    }

    private fun switchTokens(
        earleyTable: EarleyTable,
        tokens: MutableList<Token>,
        editDistance: Int
    ): MutableSet<ParsedTree> {
        var modifiedTable = earleyTable.copy()
        val sttsTags = mutableSetOf<String>()
        var index = switchTokensInTable(modifiedTable)
        val parsedTrees = mutableSetOf<ParsedTree>()
        var tagsChanged = false

        if (index == tokens.size - 1 && tokens[index].posTag == "$.") {
            index--
        }

        for (state in modifiedTable.earleyCharts[index].stateList) {
            if (STTS.containsTag(state.earleyRule.getCurrentLiteral())) {
                sttsTags.add(state.earleyRule.getCurrentLiteral())
            }
        }

        for (tag in sttsTags) {
            val tempModifiedTable = modifiedTable.copy()
            var possibleToken = false
            var indexToken = -1
            for (i in index + 1 until tokens.size) {
                if (tokens[i].posTag == tag) {
                    possibleToken = true
                    indexToken = i
                    break
                }
            }

            if (possibleToken) {
                val tempParsedTrees = mutableSetOf<ParsedTree>()
                val modifiedTokens = mutableListOf<Token>()
                modifiedTokens.addAll(tokens)

                val switchToken = modifiedTokens[indexToken]
                modifiedTokens.removeAt(indexToken)
                modifiedTokens.add(index, switchToken)

                val newTaggedTokens = Tokenizer.reTagTokens(modifiedTokens)

                for (i in 0 until index) {
                    if (modifiedTokens[i] != newTaggedTokens[i]) {
                        tagsChanged = true
                        break
                    }
                }
                tempModifiedTable.annotateErrorMessage(
                    MESSAGE_TYPE.SWITCHED,
                    EDIT_DISTANCE.ONE,
                    newTaggedTokens,
                    index
                )

                if (!this.parsedTokens.contains(newTaggedTokens)) {
                    this.parsedTokens.add(newTaggedTokens)
                    if (tagsChanged) {
                        parseEarleyTable(tempModifiedTable, newTaggedTokens, 0)
                    } else {
                        parseEarleyTable(tempModifiedTable, modifiedTokens, index)
                    }

                    if (editDistance == 1) {
                        tempParsedTrees.addAll(
                            findTreeRoot(
                                tempModifiedTable,
                                newTaggedTokens
                            ).toMutableSet()
                        )
                    } else {
                        tempParsedTrees.addAll(
                            correctIncompleteTable(
                                tempModifiedTable,
                                modifiedTokens,
                                editDistance - 1
                            )
                        )
                    }
                }
                tempParsedTrees.forEach { it.annotateErrorLog(tempModifiedTable.errorLog) }
                parsedTrees.addAll(tempParsedTrees)
            }
        }


        for (i in tokens.indices) {

            if (i != index) {
                val tempParsedTrees = mutableSetOf<ParsedTree>()
                modifiedTable = earleyTable.copy()
                val modifiedTokens = mutableListOf<Token>()
                modifiedTokens.addAll(tokens)
                val removedT = modifiedTokens.removeAt(index)
                modifiedTokens.add(i, removedT)

                val newTaggedTokens = Tokenizer.reTagTokens(modifiedTokens)

                modifiedTable.annotateErrorMessage(
                    MESSAGE_TYPE.SWITCHED,
                    EDIT_DISTANCE.ONE,
                    tokens,
                    i
                )

                if (!this.parsedTokens.contains(newTaggedTokens)) {
                    this.parsedTokens.add(newTaggedTokens)

                    val newModifiedTable = parse(newTaggedTokens)


                    newModifiedTable.annotateErrorMessage(
                        MESSAGE_TYPE.SWITCHED,
                        EDIT_DISTANCE.ONE,
                        newTaggedTokens,
                        i
                    )

                    if (editDistance == 1) {
                        tempParsedTrees.addAll(
                            findTreeRoot(
                                newModifiedTable,
                                newTaggedTokens
                            ).toMutableSet()
                        )
                    } else {
                        tempParsedTrees.addAll(
                            correctIncompleteTable(
                                newModifiedTable,
                                modifiedTokens,
                                editDistance - 1
                            )
                        )
                    }
                    tempParsedTrees.forEach { it.annotateErrorLog(newModifiedTable.errorLog) }
                    parsedTrees.addAll(tempParsedTrees)
                }
            }
        }

        return parsedTrees
    }

    private fun switchTokensInTable(earleyTable: EarleyTable): Int {
        for (i in earleyTable.earleyCharts.size - 2 downTo 0) {
            val chart = earleyTable.earleyCharts[i]
            if (chart.stateList.isNotEmpty()) {
                return i
            }

        }
        return -1
    }
}
