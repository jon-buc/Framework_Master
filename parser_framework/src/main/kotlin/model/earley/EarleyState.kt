package model.earley

import grammar.rule.EarleyRule

class EarleyState(
    val earleyRule: EarleyRule,
    val addedInRotation: Int
) {
    override fun toString(): String {
        return "$earleyRule ($addedInRotation)"
    }

    override fun hashCode(): Int {
        return earleyRule.hashCode() + addedInRotation
    }

    override fun equals(other: Any?): Boolean {
        return this.hashCode() == other.hashCode()
    }

    fun copy(): EarleyState {
        return EarleyState(earleyRule.copyOf(), addedInRotation)
    }
}
