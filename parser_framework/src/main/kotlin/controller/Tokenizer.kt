package controller

import de.sfb833.a4.RFTagger.tagsetconv.ConverterFactory
import de.sfb833.a4.RFTagger.tagsetconv.TagsetConverter
import de.uniwue.aries.rft.ARIESRFTagger
import java.util.*

data class Token(
    var begin: Int,
    var end: Int,
    val text: String,
    var document: String,
    var posTag: String = ""
) {
    override fun toString(): String {
        return "${document.substring(begin, end)} [$begin $end] ($posTag)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Token

        if (begin != other.begin) return false
        if (end != other.end) return false
        if (text != other.text) return false
        if (document != other.document) return false
        if (posTag != other.posTag) return false

        return true
    }

    override fun hashCode(): Int {
        var result = begin
        result = 31 * result + end
        result = 31 * result + text.hashCode()
        result = 31 * result + document.hashCode()
        result = 31 * result + posTag.hashCode()
        return result
    }

}

object Tokenizer {
    private val converter: TagsetConverter = ConverterFactory.getConverter("stts")
    private val tagger: ARIESRFTagger = ARIESRFTagger.getInstance()

    fun tokenize(sentence: String): MutableList<Token> {
        val tokenizer = StringTokenizer(sentence, " ,.?!;-\"", true)
        val tokenList = mutableListOf<Token>()
        var begin = 0
        for (token in tokenizer) {
            if (token.toString().isNotBlank()) {
                tokenList.add(
                    Token(
                        begin,
                        begin + token.toString().length,
                        token.toString(),
                        sentence
                    )
                )
            }
            begin += token.toString().length
        }
        return tokenList
    }

    //https://www.cis.uni-muenchen.de/~schmid/tools/RFTagger/
    fun rfTagger(sentence: String, startSentence: Int): MutableList<Token> {


        val tokens = tokenize(sentence)
        val pos = tagger.tag(tokens.map { it.text }).map { converter.rftag2tag(it) }
        for (i in pos.indices) {
            tokens[i].posTag = pos[i]
            tokens[i].begin += startSentence
            tokens[i].end += startSentence
        }
        return tokens
    }

    fun reTagTokens(tokens: MutableList<Token>): MutableList<Token> {
        val newTaggedTokens = rfTagger(tokens.joinToString(" ") { it.text }, 0)

        val modifiedTokens = tokens.map { it.copy() }.toMutableList()

        for (index in modifiedTokens.indices) {
            modifiedTokens[index].posTag = newTaggedTokens[index].posTag
        }
        return modifiedTokens
    }
}
