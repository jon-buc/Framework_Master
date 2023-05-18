package model.earley

class EarleyChart(
    var stateList: MutableSet<EarleyState> = mutableSetOf()
) {
    override fun toString(): String {
        var states = ""
        for (state in stateList) {
            states += "$state\n"
        }
        return states
    }

    fun copy(): EarleyChart {
        val stateList = mutableSetOf<EarleyState>()
        for (state in this.stateList) {
            stateList.add(state.copy())
        }
        return EarleyChart(stateList)
    }
}
