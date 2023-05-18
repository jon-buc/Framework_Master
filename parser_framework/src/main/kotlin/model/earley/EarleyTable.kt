package model.earley

import controller.Token
import model.parsedTree.EDIT_DISTANCE
import model.parsedTree.ErrorMessage
import model.parsedTree.MESSAGE_TYPE

class EarleyTable(var sizeOfTable: Int, var editDistance: Int = 0) {
    var earleyCharts = Array(sizeOfTable + 1) { EarleyChart() }
    var errorLog: MutableSet<ErrorMessage> = mutableSetOf()

    fun annotateErrorMessage(
        type: MESSAGE_TYPE,
        editDistance: EDIT_DISTANCE,
        tokens: MutableList<Token>,
        tokenIndex: Int,
        switchedTokenIndex: Int = -1
    ) {
        errorLog.add(
            ErrorMessage(type, editDistance, tokens, tokenIndex)
        )
    }

    fun isComplete(): Boolean {
        for (state in earleyCharts[earleyCharts.size - 1].stateList) {
            if (state.earleyRule.completed && state.earleyRule.resultLiteral == "S") {
                return true
            }
        }
        return false
    }

    fun copy(): EarleyTable {
        val table = EarleyTable(this.earleyCharts.size - 1)
        for (i in this.earleyCharts.indices) {
            table.earleyCharts[i] = this.earleyCharts[i].copy()
        }

        return table
    }

    override fun toString(): String {
        var table = ""
        for (i in earleyCharts.indices) {
            table += "$i:\n${earleyCharts[i]}"
        }
        return table
    }
}
