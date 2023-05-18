package parser

import controller.Token
import controller.Tokenizer
import controller.addParsingTreeAnnotationSetsToCAS
import controller.sentenceAnnotations
import grammar.EarleyGrammar
import grammar.rule.EarleyRule
import model.earley.EarleyChart
import model.earley.EarleyState
import model.earley.EarleyTable
import model.parsedTree.PARSER_TYPE
import model.parsedTree.ParsedTree
import model.parsedTree.TREE_TYPE
import model.parsedTree.TreeItem
import org.apache.uima.cas.CAS

open class EarleyParser(val grammar: EarleyGrammar, override val directoryForXMI: String) : Parser {

    private val START_SYMBOL = "S"

    private var iteration = -1

    override fun parseCAS(
        cas: CAS,
        writeToDirectory: Boolean
    ): ArrayList<CAS> {
        var startIndex = 0
        val listCAS = arrayListOf<CAS>()
        val parsedCAS: CAS = cas
        val parsingTreesForAnnotation = mutableSetOf<MutableSet<ParsedTree>>()
        parsedCAS.sentenceAnnotations()?.forEach {
            println(it.coveredText)
            val tokens = Tokenizer.rfTagger(it.coveredText, startIndex)
            val earleyTable = parse(tokens)
            val parsedTrees = findTreeRoot(earleyTable, tokens).toMutableSet()

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
                if (parsedTrees.size > 0) {
                    parsingTreesForAnnotation.clear()
                    parsingTreesForAnnotation.addAll(newlyParsedTreesForAnnotation)
                }
            }

            startIndex = tokens[tokens.size - 1].end + 1
        }
        for (trees in parsingTreesForAnnotation) {
            for (tree in trees) {
                println(tree.toQTree())
            }
        }
        listCAS.addAll(cas.addParsingTreeAnnotationSetsToCAS(parsingTreesForAnnotation))
        if (writeToDirectory) {
            writeToDirectory(listCAS, PARSER_TYPE.EARLEY)
        }

        return listCAS
    }

    protected open fun parse(tokens: MutableList<Token>): EarleyTable {
        val earleyTable = EarleyTable(tokens.size)

        earleyTable.earleyCharts = findStartingRules(earleyTable.earleyCharts)

        return parseEarleyTable(earleyTable, tokens, 0)
    }

    protected open fun parseEarleyTable(
        earleyTable: EarleyTable,
        tokens: MutableList<Token>,
        index: Int
    ): EarleyTable {
        for (i in index..tokens.size) {
            var sizeOfStates = earleyTable.earleyCharts[i].stateList.size - 1
            for (j in 0..sizeOfStates) {
                val state = earleyTable.earleyCharts[i].stateList.elementAt(j)
                if (!state.earleyRule.completed) {
                    if (i < tokens.size && tokens[i].posTag != state.earleyRule.getCurrentLiteral()) {
                        predictor(earleyTable.earleyCharts, state.earleyRule, i)
                        sizeOfStates = earleyTable.earleyCharts[i].stateList.size - 1
                    } else {
                        scanner(earleyTable.earleyCharts, state, tokens, i)
                    }
                } else {
                    earleyTable.earleyCharts = completer(earleyTable.earleyCharts, state, i)
                    sizeOfStates = earleyTable.earleyCharts[i].stateList.size - 1
                }
            }
        }
        return earleyTable
    }

    protected open fun predictor(
        earleyCharts: Array<EarleyChart>,
        rule: EarleyRule,
        chartIndex: Int
    ): Array<EarleyChart> {
        val current = rule.getCurrentLiteral()
        return addAllToChart(earleyCharts, grammar.ruleSet[current], chartIndex)
    }

    protected open fun scanner(
        earleyCharts: Array<EarleyChart>,
        earleyState: EarleyState,
        tokens: MutableList<Token>,
        index: Int
    ): Boolean {
        val currentLiteral = earleyState.earleyRule.getCurrentLiteral()

        if (index < tokens.size && tokens[index].posTag == currentLiteral) {
            val earleyRule = earleyState.earleyRule.copyOf()
            val newEarleyState = EarleyState(earleyRule, earleyState.addedInRotation)
            newEarleyState.earleyRule.incrementCurrentLiteralIndex()
            earleyCharts[index + 1].stateList.add(newEarleyState)
            return true
        }

        return false
    }

    protected open fun completer(
        earleyCharts: Array<EarleyChart>,
        earleyState: EarleyState,
        rotation: Int
    ): Array<EarleyChart> {
        val finishedLiteral = earleyState.earleyRule.resultLiteral
        for (state in earleyCharts[earleyState.addedInRotation].stateList) {
            if (!state.earleyRule.completed && state.earleyRule.getCurrentLiteral() == finishedLiteral
            ) {
                val earleyRule = state.earleyRule.copyOf()
                earleyRule.incrementCurrentLiteralIndex()
                earleyCharts[rotation].stateList.add(EarleyState(earleyRule, state.addedInRotation))
            }
        }

        return earleyCharts
    }

    protected open fun addAllToChart(
        earleyCharts: Array<EarleyChart>,
        earleyRules: MutableList<EarleyRule>?,
        chartIndex: Int
    ): Array<EarleyChart> {
        if (earleyRules != null) {
            for (earleyRule in earleyRules) {
                val rule = EarleyRule(
                    resultLiteral = earleyRule.resultLiteral,
                    literals = earleyRule.literals,
                    ruleName = earleyRule.ruleName
                )
                earleyCharts[chartIndex].stateList.add(
                    EarleyState(
                        rule,
                        chartIndex
                    )
                )
            }
        }
        return earleyCharts
    }

    protected open fun findStartingRules(earleyCharts: Array<EarleyChart>): Array<EarleyChart> {
        for (earleyRule in grammar.ruleSet[START_SYMBOL]!!) {
            earleyCharts[0].stateList.add(
                EarleyState(
                    earleyRule,
                    0
                )
            )
        }

        return earleyCharts
    }

    protected open fun findStates(
        earleyTable: EarleyTable,
        terminal: String,
        index: Int
    ): ArrayList<EarleyState> {
        val startingRules = arrayListOf<EarleyState>()

        for (i in earleyTable.earleyCharts[index].stateList.size - 1 downTo 0) {
            val state = earleyTable.earleyCharts[index].stateList.elementAt(i)
            if (state.earleyRule.resultLiteral == terminal && state.earleyRule.completed) {
                startingRules.add(state)
            }
        }

        return startingRules
    }

    protected open fun findTreeRoot(
        earleyTable: EarleyTable,
        tokens: MutableList<Token>
    ): MutableList<ParsedTree> {
        iteration = earleyTable.sizeOfTable
        val children = createTreeItem(earleyTable, START_SYMBOL, tokens)
        val childrenParsedTrees = mutableListOf<ArrayList<TreeItem>>()
        for (child in children) {
            var coverage = 0
            for (item in child) {
                coverage += item.getTagCount()
            }
            if (tokens.size == coverage) {
                childrenParsedTrees.add(child)
            }
        }
        val parsedTrees = mutableListOf<ParsedTree>()

        val startSymbol = if (grammar.ruleNameMap.contains(START_SYMBOL)) {
            grammar.ruleNameMap[START_SYMBOL]!!
        } else {
            START_SYMBOL
        }
        for (child in childrenParsedTrees) {
            parsedTrees.add(
                ParsedTree(
                    PARSER_TYPE.EARLEY,
                    TREE_TYPE.COMPLETED,
                    TreeItem(
                        tokens[0].begin, tokens[tokens.size - 1].end, startSymbol, START_SYMBOL,
                        child
                    )
                )
            )
        }
        return parsedTrees

    }

    protected open fun createTreeItem(
        earleyTable: EarleyTable,
        terminal: String,
        tokens: MutableList<Token>
    ): MutableList<ArrayList<TreeItem>> {
        val currentIteration = iteration
        val states = findStates(earleyTable, terminal, iteration)
        val children = mutableListOf<ArrayList<TreeItem>>()

        for (state in states) {
            val stateChildren = mutableListOf<ArrayList<TreeItem>>()
            for (i in state.earleyRule.literals.size - 1 downTo 0) {
                if (stateChildren.isEmpty()) {
                    iteration = currentIteration
                    val treeChildren = matchLiteralWithStates(earleyTable, state, tokens, i)
                    for (child in treeChildren) {
                        stateChildren.add(arrayListOf(child))
                    }
                } else {
                    val tempStateChildren = mutableListOf<ArrayList<TreeItem>>()
                    for (child in stateChildren) {
                        var coverage = 0
                        for (item in child) {
                            coverage += item.getTagCount()
                        }
                        iteration = currentIteration - coverage

                        val treeChildren = matchLiteralWithStates(earleyTable, state, tokens, i)

                        val newChildren = mutableListOf<ArrayList<TreeItem>>()
                        for (tchild in treeChildren) {
                            val tempChildren = arrayListOf<TreeItem>()
                            tempChildren.addAll(child)
                            tempChildren.add(0, tchild)
                            newChildren.add(tempChildren)
                        }
                        tempStateChildren.addAll(newChildren)
                    }
                    stateChildren.clear()
                    stateChildren.addAll(tempStateChildren)
                }
            }
            children.addAll(stateChildren)
        }

        return children
    }

    protected open fun matchLiteralWithStates(
        earleyTable: EarleyTable,
        state: EarleyState,
        tokens: MutableList<Token>,
        i: Int
    ): MutableList<TreeItem> {
        val literal = state.earleyRule.literals[i]
        if (literal == tokens[iteration - 1].posTag) {
            iteration--
            return mutableListOf(
                TreeItem(
                    tokens[iteration].begin,
                    tokens[iteration].end,
                    tokens[iteration].posTag,
                    tokens[iteration].posTag
                )
            )

        } else {
            earleyTable.earleyCharts[iteration].stateList.remove(state)
            val children = createTreeItem(
                earleyTable,
                state.earleyRule.literals[i],
                tokens
            )
            val parentNodes = mutableListOf<TreeItem>()

            for (child in children) {
                parentNodes.add(
                    TreeItem(
                        child[0].begin,
                        child[child.size - 1].end,
                        state.earleyRule.ruleName,
                        literal,
                        child
                    )
                )
            }
            return parentNodes
        }
    }
}
