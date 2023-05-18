package parser

import controller.Token
import controller.Tokenizer
import controller.addParsingTreeAnnotationSetsToCAS
import controller.sentenceAnnotations
import de.uniwue.aries.uima.types.Types
import grammar.CYKGrammar
import model.cyk.CYKToken
import model.parsedTree.PARSER_TYPE
import model.parsedTree.ParsedTree
import model.parsedTree.TREE_TYPE
import model.parsedTree.TreeItem
import org.apache.uima.cas.CAS
import org.apache.uima.util.CasCopier
import org.apache.uima.util.CasCreationUtils

open class CYKParser(open val grammar: CYKGrammar, override val directoryForXMI: String) : Parser {

    override fun parseCAS(
        cas: CAS,
        writeToDirectory: Boolean
    ): ArrayList<CAS> {
        var incorrectCounter = 0
        var sentenceIndex = 0
        var startIndex = 0
        val listCAS = arrayListOf<CAS>()
        val parsedCAS: CAS = CasCreationUtils.createCas(Types.getTypeSystem(), null, null)
        CasCopier.copyCas(cas, parsedCAS, true)
        val parsingTreesForAnnotation = mutableSetOf<MutableSet<ParsedTree>>()
        parsedCAS.sentenceAnnotations()?.forEach {
            sentenceIndex++
            println(sentenceIndex)
            var incorrect = false
            val tokens = Tokenizer.rfTagger(it.coveredText, startIndex)

            if (tokens.size > 2) {
                val cykMatrix = parseCYKMatrixByTokens(tokens)

                val parsedTrees = findParsingTree(cykMatrix)

                for (tree in parsedTrees) {
                    if (tree.treeType == TREE_TYPE.INCOMPLETED) {
                        incorrect = true
                    }
                }

                if (incorrect) {
                    incorrectCounter++
                }

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
        }

        listCAS.addAll(cas.addParsingTreeAnnotationSetsToCAS(parsingTreesForAnnotation))

        if (writeToDirectory) {
            writeToDirectory(listCAS, PARSER_TYPE.CYK)
        }
        return listCAS
    }

    protected open fun parseCYKMatrixByTokens(tokens: MutableList<Token>): Array<Array<ArrayList<CYKToken>>> {
        return parseCYKMatrix(createCYKMatrix(tokens))
    }

    protected open fun parseCYKMatrix(cykMatrix: Array<Array<ArrayList<CYKToken>>>): Array<Array<ArrayList<CYKToken>>> {
        val sizeOfMatrix = cykMatrix.size

        for (i in 0 until cykMatrix[1].size - 1) {
            val left = cykMatrix[0][i][0]
            val right = cykMatrix[0][i + 1][0]

            cykMatrix[1][i].addAll(this.grammar.findMatchingRule(left, right))
        }

        for (i in 2 until sizeOfMatrix) {
            for (j in 0 until sizeOfMatrix - i) {
                searchForMatch(cykMatrix, i, j)
            }
        }

        return cykMatrix
    }

    protected open fun createCYKMatrix(tokens: MutableList<Token>): Array<Array<ArrayList<CYKToken>>> {
        val sizeOfMatrix = tokens.size
        val cykMatrix =
            Array(sizeOfMatrix) { Array(sizeOfMatrix) { arrayListOf<CYKToken>() } }

        for (i in tokens.indices) {
            val t = tokens[i]
            cykMatrix[0][i].add(CYKToken(t.posTag, t.posTag, begin = t.begin, end = t.end))
        }
        return cykMatrix
    }

    protected open fun searchForMatch(
        cykMatrix: Array<Array<ArrayList<CYKToken>>>,
        row: Int,
        col: Int,
        findBestMatch: Boolean = false
    ): Array<Array<ArrayList<CYKToken>>> {
        var bestMatchedTokens = arrayListOf<CYKToken>()
        var bestMatchedPossibility = 0.0

        for ((rowIndex, i) in (row - 1 downTo 0).withIndex()) {
            val left = cykMatrix[i][col]
            val right = cykMatrix[rowIndex][col + i + 1]

            if (left.size > 0 && right.size > 0) {
                if (findBestMatch) {
                    val matches = matchLiterals(
                        left,
                        right,
                        findBestMatch
                    )

                    for (t in matches) {
                        if (t.possiblility > bestMatchedPossibility) {
                            bestMatchedPossibility = t.possiblility
                            bestMatchedTokens = arrayListOf(t)
                        } else if (t.possiblility == bestMatchedPossibility) {
                            bestMatchedTokens.add(t)
                        }
                    }

                } else {
                    cykMatrix[row][col].addAll(
                        matchLiterals(
                            left,
                            right,
                            findBestMatch
                        )
                    )
                }
            }
        }
        if (findBestMatch) {
            cykMatrix[row][col].addAll(bestMatchedTokens)
        }
        return cykMatrix
    }

    protected open fun matchLiterals(
        leftTokens: ArrayList<CYKToken>,
        rightTokens: ArrayList<CYKToken>,
        findBestMatch: Boolean
    ): ArrayList<CYKToken> {
        val tokenList = arrayListOf<CYKToken>()
        for (l in leftTokens) {
            for (r in rightTokens) {
                tokenList.addAll(
                    this.grammar.findMatchingRule(
                        l,
                        r,
                        findBestMatch
                    )
                )
            }
        }
        return tokenList
    }

    protected open fun findParsingTree(cykMatrix: Array<Array<ArrayList<CYKToken>>>): MutableSet<ParsedTree> {
        val parsedTree = mutableSetOf<ParsedTree>()

        for (token in cykMatrix[cykMatrix.size - 1][0]) {
            if (token.literal == "S") {
                parsedTree.add(
                    ParsedTree(
                        PARSER_TYPE.CYK,
                        TREE_TYPE.COMPLETED,
                        createParsingTree(token)
                    )
                )
            }
        }

        if (parsedTree.size > 0) {
            return parsedTree
        }

        val listOfChildren =
            createIncompleteParsingTree(cykMatrix, cykMatrix.size - 1, 0, cykMatrix.size)
        for (children in listOfChildren) {
            parsedTree.add(
                ParsedTree(
                    PARSER_TYPE.CYK,
                    TREE_TYPE.INCOMPLETED,
                    TreeItem(
                        0,
                        cykMatrix[0][cykMatrix.size - 1][0].end,
                        "Incomplete Sentence",
                        "Incomplete",
                        children
                    )
                )
            )
        }

        return parsedTree
    }

    protected open fun createParsingTree(cykToken: CYKToken): TreeItem {
        val children = arrayListOf<TreeItem>()
        var itemName = cykToken.tokenName
        if (cykToken.leftToken != null && cykToken.rightToken != null) {
            children.add(createParsingTree(cykToken.leftToken))
            children.add(createParsingTree(cykToken.rightToken))
        } else if (cykToken.leftToken == null && cykToken.rightToken != null) {
            children.add(createParsingTree(cykToken.rightToken))
        }
        if (cykToken.literal.matches("\\d+".toRegex())) {
            itemName = grammar.splitRules[cykToken.literal.toInt()].toString()
        }
        return TreeItem(cykToken.begin, cykToken.end, itemName, cykToken.literal, children)

    }

    protected open fun containsSentenceAnnotation(cykMatrix: Array<Array<ArrayList<CYKToken>>>): Boolean {
        for (token in cykMatrix[cykMatrix.size - 1][0]) {
            if (token.literal == "S") {
                return true
            }
        }
        return false
    }

    protected open fun createIncompleteParsingTree(
        cykMatrix: Array<Array<ArrayList<CYKToken>>>,
        height: Int,
        startWidth: Int,
        endWidth: Int
    ): MutableList<ArrayList<TreeItem>> {
        val listOfChildren = mutableListOf<ArrayList<TreeItem>>()
        for (i in height downTo 1) {
            var j = startWidth
            var end = endWidth
            while (j < end) {
                if (cykMatrix[i][j].isNotEmpty() && j + i + 1 <= endWidth) {
                    end = j + i + 1
                    for (token in cykMatrix[i][j]) {
                        val rightSiblings = createIncompleteParsingTree(
                            cykMatrix,
                            i - 1,
                            startWidth,
                            j
                        )

                        val leftSiblings = createIncompleteParsingTree(
                            cykMatrix,
                            i,
                            j + i + 1,
                            endWidth
                        )

                        val treeItem = createParsingTree(token)

                        val tempSiblings = mutableListOf<ArrayList<TreeItem>>()
                        for (rSibling in rightSiblings) {
                            for (lSibling in leftSiblings) {
                                val list = arrayListOf<TreeItem>()
                                list.add(treeItem)
                                list.addAll(rSibling)
                                list.addAll(lSibling)
                                tempSiblings.add(list)
                            }
                        }
                        listOfChildren.addAll(tempSiblings)
                    }

                }
                j++
            }
            if (listOfChildren.size > 0) {
                return listOfChildren
            }
        }
        if (height <= 0 || listOfChildren.size == 0) {
            val children = arrayListOf<TreeItem>()
            for (j in startWidth until endWidth) {
                val token = cykMatrix[0][j][0]
                children.add(TreeItem(token.begin, token.end, token.literal, token.literal))
            }
            listOfChildren.add(children)
        }
        return listOfChildren

    }
}
