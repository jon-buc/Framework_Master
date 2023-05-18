package controller

import de.uniwue.aries.uima.types.Types
import model.parsedTree.ParsedTree
import model.parsedTree.TREE_TYPE
import model.parsedTree.TreeItem
import org.apache.uima.cas.CAS
import org.apache.uima.cas.text.AnnotationFS
import org.apache.uima.cas.text.AnnotationIndex
import org.apache.uima.util.CasCopier
import org.apache.uima.util.CasCreationUtils

private const val TYPE_SENTENCE = "de.uniwue.kalimachos.coref.type.Sentence"
private const val GRAMMATIC_UNIT = "de.uniwue.kallimachos.coref.type.GrammaticUnit"

fun CAS.addSentence(begin: Int, end: Int, addToIndicies: Boolean = true): AnnotationFS {
    val annotation =
        this.createAnnotation<AnnotationFS>(Types.getType(this, TYPE_SENTENCE), begin, end)
    if (addToIndicies) {
        this.addFsToIndexes(annotation)
    }
    return annotation
}

fun CAS.addAnnotation(
    treeItem: TreeItem,
    parseFather: AnnotationFS? = null,
    addToIndicies: Boolean = true
): AnnotationFS {
    val annotation = this.createAnnotation<AnnotationFS>(
        Types.getType(this, GRAMMATIC_UNIT),
        treeItem.begin,
        treeItem.end
    )
    annotation.setFeatureValueFromString(
        Types.getType(this, GRAMMATIC_UNIT).getFeatureByBaseName("Phrasetype"),
        treeItem.terminalText
    )

    if (parseFather != null) {
        annotation.setFeatureValue(
            Types.getType(this, GRAMMATIC_UNIT).getFeatureByBaseName("Parsefather"),
            parseFather
        )
    }

    if (addToIndicies) {
        this.addFsToIndexes(annotation)
    }
    return annotation
}

fun CAS.sentenceAnnotations(): AnnotationIndex<AnnotationFS>? {
    return this.getAnnotationIndex(Types.getType(this, TYPE_SENTENCE))
}

fun CAS.addParsingTreeAnnotationSetsToCAS(setForAnnotation: MutableSet<MutableSet<ParsedTree>>): MutableSet<CAS> {
    val setOfCAS = mutableSetOf<CAS>()
    for (treeSet in setForAnnotation) {
        val casObject: CAS = CasCreationUtils.createCas(Types.getTypeSystem(), null, null)
        CasCopier.copyCas(this, casObject, true)

        for (tree in treeSet) {
            casObject.addParsedTreeToCAS(tree)
        }
        setOfCAS.add(casObject)
    }
    return setOfCAS
}

fun CAS.addAnnotationsToCAS(parsingTrees: MutableSet<ParsedTree>): ArrayList<CAS> {
    val listCAS = arrayListOf<CAS>()

    for (tree in parsingTrees) {
        val casObject: CAS = CasCreationUtils.createCas(Types.getTypeSystem(), null, null)
        CasCopier.copyCas(this, casObject, true)

        if (tree.treeType == TREE_TYPE.INCOMPLETED) {
            for (partTagged in tree.treeRoot.children) {
                val annotation = casObject.addAnnotation(
                    partTagged, addToIndicies = true
                )
                for (child in partTagged.children) {
                    annotateParsingTree(casObject, child, annotation)
                }
            }
        } else {
            val annotation = casObject.addAnnotation(
                tree.treeRoot, addToIndicies = true
            )
            for (child in tree.treeRoot.children) {
                annotateParsingTree(casObject, child, annotation)
            }
        }

        listCAS.add(casObject)
    }
    return listCAS
}

fun CAS.addParsedTreeToCAS(parsingTree: ParsedTree) {

    if (parsingTree.treeType == TREE_TYPE.INCOMPLETED) {
        for (partTagged in parsingTree.treeRoot.children) {
            val annotation = this.addAnnotation(
                partTagged, addToIndicies = true
            )
            for (child in partTagged.children) {
                annotateParsingTree(this, child, annotation)
            }
        }
    } else {
        val annotation = this.addAnnotation(
            parsingTree.treeRoot, addToIndicies = true
        )
        for (child in parsingTree.treeRoot.children) {
            annotateParsingTree(this, child, annotation)
        }
    }
}

private fun annotateParsingTree(cas: CAS, treeItem: TreeItem, fatherAnn: AnnotationFS) {

    val annotation = cas.addAnnotation(
        treeItem, fatherAnn, addToIndicies = true
    )

    for (child in treeItem.children) {
        annotateParsingTree(cas, child, annotation)
    }
}
