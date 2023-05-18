package model.cyk

class CYKToken(
    val tokenName: String,
    var literal: String,
    val possiblility: Double = 1.0,
    val leftToken: CYKToken? = null,
    val rightToken: CYKToken? = null,
    val begin: Int,
    val end: Int
)
