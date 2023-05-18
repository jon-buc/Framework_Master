package parser.modified

import controller.*
import de.uniwue.aries.uima.types.Types
import grammar.CYKGrammar
import model.cyk.CYKToken
import model.parsedTree.EDIT_DISTANCE
import model.parsedTree.MESSAGE_TYPE
import model.parsedTree.PARSER_TYPE
import model.parsedTree.ParsedTree
import org.apache.uima.cas.CAS
import org.apache.uima.util.CasCopier
import org.apache.uima.util.CasCreationUtils
import parser.CYKParser

class CYKParserBruteForce(override val grammar: CYKGrammar, override val directoryForXMI: String) :
    CYKParser(grammar, directoryForXMI) {

    private val taggedSentences = mutableSetOf<String>()

    override fun parseCAS(
        cas: CAS,
        writeToDirectory: Boolean
    ): ArrayList<CAS> {
        var startIndex = 0
        val listCAS = arrayListOf<CAS>()
        var parsedCAS: CAS = CasCreationUtils.createCas(Types.getTypeSystem(), null, null)
        CasCopier.copyCas(cas, parsedCAS, true)
        parsedCAS.sentenceAnnotations()?.forEach {
            val tokens = Tokenizer.rfTagger(it.coveredText, startIndex)
            val cykMatrix = parseCYKMatrixByTokens(tokens)

            var parsingTrees = mutableSetOf<ParsedTree>()

            if (!containsSentenceAnnotation(cykMatrix)) {
                taggedSentences.clear()
                parsingTrees = bruteForceErrors(tokens, tokens, true, 1)
            }


            if (parsingTrees.isEmpty()) {
                parsingTrees = findParsingTree(cykMatrix)
            }

            for (tree in parsingTrees) {
                tree.logErrorMessage()
                println()
            }

            val annotatedCAS = parsedCAS.addAnnotationsToCAS(parsingTrees)

            if (annotatedCAS.size > 1) {
                listCAS.addAll(annotatedCAS)
            } else {
                parsedCAS = annotatedCAS[0]
            }

            startIndex = tokens[tokens.size - 1].end + 1
        }
        if (listCAS.size == 0) {
            listCAS.add(parsedCAS)
        }
        if (writeToDirectory) {
            writeToDirectory(listCAS, PARSER_TYPE.CYK)
        }
        return listCAS
    }

    private fun bruteForceErrors(
        originalTokens: MutableList<Token>,
        alteredTokens: MutableList<Token>,
        editDistanceReached: Boolean,
        editDistanceValue: Int
    ): MutableSet<ParsedTree> {
        val parsingTrees = deleteTokens(alteredTokens, editDistanceReached, editDistanceValue)

        parsingTrees.addAll(
            switchTokensInEntireList(
                originalTokens,
                alteredTokens,
                editDistanceReached,
                editDistanceValue
            )
        )

        parsingTrees.addAll(
            addTokens(
                originalTokens,
                alteredTokens,
                editDistanceReached,
                editDistanceValue
            )
        )

        if (parsingTrees.isEmpty() && editDistanceValue == 1) {
            taggedSentences.clear()
            parsingTrees.addAll(bruteForceErrors(originalTokens, alteredTokens, false, 2))
        }

        return parsingTrees
    }

    private fun deleteTokens(
        tokens: MutableList<Token>,
        editDistanceReached: Boolean,
        editDistanceValue: Int = 1
    ): MutableSet<ParsedTree> {
        val parsingTrees = mutableSetOf<ParsedTree>()
        for (token in tokens) {
            val deletedTokens = tokens.map { it.copy() }.toMutableList()
            deletedTokens.remove(token)


            if (editDistanceReached) {
                val modifiedTokens = Tokenizer.reTagTokens(deletedTokens)
                if (!taggedSentences.contains(modifiedTokens.joinToString(" ") { it.posTag })) {
                    taggedSentences.add(modifiedTokens.joinToString(" ") { it.posTag })
                    if (modifiedTokens.size > 1) {
                        val cykMatrix = createCYKMatrix(modifiedTokens)
                        parseCYKMatrix(cykMatrix)

                        if (containsSentenceAnnotation(cykMatrix)) {
                            val parsedTrees = findParsingTree(cykMatrix)
                            for (tree in parsedTrees) {
                                EDIT_DISTANCE.fromInt(editDistanceValue)?.let {
                                    tree.annotateErrorMessage(
                                        MESSAGE_TYPE.DELETED,
                                        it,
                                        tokens,
                                        tokens.indexOf(token)
                                    )
                                }
                            }
                            parsingTrees.addAll(parsedTrees)
                        }
                    }
                }
            } else {
                val possibleTrees =
                    bruteForceErrors(deletedTokens, deletedTokens, true, 2)
                for (tree in possibleTrees) {
                    EDIT_DISTANCE.fromInt(editDistanceValue)?.let {
                        tree.annotateErrorMessage(
                            MESSAGE_TYPE.DELETED,
                            it,
                            tokens,
                            tokens.indexOf(token)
                        )
                    }
                }
                parsingTrees.addAll(possibleTrees)
            }
        }
        return parsingTrees
    }

    private fun addTokens(
        originalTokens: MutableList<Token>,
        alteredTokens: MutableList<Token>,
        editDistanceReached: Boolean,
        editDistanceValue: Int = 1
    ): MutableSet<ParsedTree> {
        val parsingTrees = mutableSetOf<ParsedTree>()
        for (index in originalTokens.indices) {
            val document = originalTokens[index].document
            val parsedTrees = when (index) {
                0 -> {
                    insertSTTS(
                        alteredTokens,
                        index,
                        0,
                        0,
                        document,
                        editDistanceReached,
                        editDistanceValue
                    )
                }
                originalTokens.size - 1 -> {
                    insertSTTS(
                        alteredTokens,
                        index,
                        originalTokens[originalTokens.size - 1].end,
                        originalTokens[originalTokens.size - 1].end,
                        document,
                        editDistanceReached,
                        editDistanceValue
                    )
                }
                else -> {
                    insertSTTS(
                        alteredTokens,
                        index,
                        originalTokens[index].end,
                        originalTokens[index].end + 1,
                        document,
                        editDistanceReached,
                        editDistanceValue
                    )
                }
            }
            parsingTrees.addAll(parsedTrees)
        }
        return parsingTrees
    }

    private fun insertSTTS(
        alteredTokens: MutableList<Token>,
        index: Int,
        begin: Int,
        end: Int,
        document: String,
        editDistanceReached: Boolean,
        editDistanceValue: Int
    ): MutableSet<ParsedTree> {
        val parsingTrees = mutableSetOf<ParsedTree>()
        for (tag in STTS.values()) {
            val addedTokens = alteredTokens.map { it.copy() }.toMutableList()
            addedTokens.add(index, Token(begin, end, tag.word, document, tag.tag))



            if (editDistanceReached) {
                val modifiedTokens = Tokenizer.reTagTokens(addedTokens)
                if (!taggedSentences.contains(modifiedTokens.joinToString(" ") { it.posTag })) {
                    taggedSentences.add(modifiedTokens.joinToString(" ") { it.posTag })
                    val cykMatrix = parseCYKMatrixByTokens(modifiedTokens)

                    if (containsSentenceAnnotation(cykMatrix)) {
                        val parsedTrees = findParsingTree(cykMatrix)
                        for (tree in parsedTrees) {
                            EDIT_DISTANCE.fromInt(editDistanceValue)?.let {
                                tree.annotateErrorMessage(
                                    MESSAGE_TYPE.ADDED,
                                    it, modifiedTokens, index
                                )
                            }
                        }
                        parsingTrees.addAll(parsedTrees)
                    }
                }
            } else {
                val possibleTrees = bruteForceErrors(addedTokens, addedTokens, true, 2)
                for (tree in possibleTrees) {
                    EDIT_DISTANCE.fromInt(editDistanceValue)?.let {
                        tree.annotateErrorMessage(
                            MESSAGE_TYPE.ADDED, it, addedTokens, index
                        )
                    }
                }
                parsingTrees.addAll(possibleTrees)
            }
        }
        return parsingTrees
    }

    private fun switchTokensInEntireList(
        originalTokens: MutableList<Token>,
        alteredTokens: MutableList<Token>,
        editDistanceReached: Boolean,
        editDistanceValue: Int = 1
    ): MutableSet<ParsedTree> {
        val parsingTrees = mutableSetOf<ParsedTree>()
        for (index in 0 until alteredTokens.size - 1) {
            val switchedTokens = alteredTokens.map { it.copy() }.toMutableList()
            val token = switchedTokens.removeAt(index)
            for (i in 0 until switchedTokens.size - 1) {
                switchedTokens.add(i, token)

                if (editDistanceReached) {
                    val modifiedTokens = Tokenizer.reTagTokens(switchedTokens)
                    if (!taggedSentences.contains(modifiedTokens.joinToString(" ") { it.posTag })) {
                        taggedSentences.add(modifiedTokens.joinToString(" ") { it.posTag })

                        val cykMatrix =
                            createCYKMatrixForSwitchedTokens(modifiedTokens, originalTokens)
                        parseCYKMatrix(cykMatrix)

                        if (containsSentenceAnnotation(cykMatrix)) {
                            val parsedTrees = findParsingTree(cykMatrix)
                            for (tree in parsedTrees) {
                                EDIT_DISTANCE.fromInt(editDistanceValue)?.let {
                                    tree.annotateErrorMessage(
                                        MESSAGE_TYPE.SWITCHED,
                                        it,
                                        modifiedTokens,
                                        i
                                    )
                                }
                            }
                            parsingTrees.addAll(parsedTrees)
                        }
                    }
                } else {
                    val possibleTrees =
                        bruteForceErrors(originalTokens, switchedTokens, true, 2)
                    for (tree in possibleTrees) {
                        EDIT_DISTANCE.fromInt(editDistanceValue)?.let {
                            tree.annotateErrorMessage(
                                MESSAGE_TYPE.SWITCHED,
                                it,
                                switchedTokens,
                                index
                            )
                        }
                    }
                    parsingTrees.addAll(possibleTrees)

                }
                switchedTokens.removeAt(i)
            }
        }


        return parsingTrees
    }

    private fun createCYKMatrixForSwitchedTokens(
        modifiedTokens: MutableList<Token>,
        tokens: MutableList<Token>
    ): Array<Array<ArrayList<CYKToken>>> {
        val sizeOfMatrix = modifiedTokens.size
        val cykMatrix =
            Array(sizeOfMatrix) { Array(sizeOfMatrix) { arrayListOf<CYKToken>() } }

        for (i in modifiedTokens.indices) {
            val t = modifiedTokens[i]
            val originT = tokens[i]
            cykMatrix[0][i].add(
                CYKToken(
                    t.posTag,
                    t.posTag,
                    begin = t.begin,
                    end = t.end,
                    rightToken = CYKToken(
                        originT.posTag,
                        originT.posTag,
                        begin = originT.begin,
                        end = originT.end
                    )
                )
            )
        }
        return cykMatrix
    }
}
