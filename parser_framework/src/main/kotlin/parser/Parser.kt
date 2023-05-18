package parser

import controller.addSentence
import de.uniwue.aries.uima.types.Types
import model.parsedTree.PARSER_TYPE
import org.apache.uima.cas.CAS
import org.apache.uima.cas.impl.XmiCasSerializer
import org.apache.uima.util.CasCreationUtils
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths

interface Parser {
    val directoryForXMI: String

    fun parseSentence(sentence: String, writeToDirectory: Boolean = false): ArrayList<CAS> {
        val cas: CAS = CasCreationUtils.createCas(Types.getTypeSystem(), null, null)
        cas.documentText = sentence

        //Adding a general sentence annotation in order to be parsed by WebAthen
        cas.addSentence(0, sentence.length, addToIndicies = true)

        return parseCAS(cas, writeToDirectory)
    }

    fun parseSentences(
        sentences: MutableList<String>,
        writeToDirectory: Boolean = false
    ): ArrayList<CAS> {
        val cas: CAS = CasCreationUtils.createCas(Types.getTypeSystem(), null, null)
        cas.documentText = sentences.joinToString(" ")
        var index = 0

        for (s in sentences) {
            val endSentence = index + s.length
            cas.addSentence(index, endSentence, addToIndicies = true)
            index = endSentence + 1
        }

        return parseCAS(cas, writeToDirectory)
    }

    fun parseTxtFile(
        path: String,
        writeToDirectory: Boolean = false
    ): ArrayList<CAS> {
        val file: String

        if (Files.exists(Paths.get(path))) {
            file = Files.readString(Paths.get(path))
        } else {
            throw Exception("The given path cannot be found!")
        }

        val sentences = mutableListOf<String>()

        for (s in file.split("\n")) {
            if (s.isNotEmpty()) {
                sentences.add(s)
            }
        }

        return parseSentences(
            sentences,
            writeToDirectory
        )
    }

    fun parseCAS(
        cas: CAS,
        writeToDirectory: Boolean = false
    ): ArrayList<CAS>

    fun writeToDirectory(listCAS: ArrayList<CAS>, parserType: PARSER_TYPE) {
        if (directoryForXMI.isNotEmpty() || directoryForXMI.isNotBlank()) {
            for (i in listCAS.indices) {
                try {
                    //Filepath based on the parameter "filePath" and the number of the sentence
                    val file =
                        FileOutputStream("$directoryForXMI/$parserType$i.xmi")

                    //XMI File Writer
                    XmiCasSerializer.serialize(listCAS[i], file)
                    file.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
