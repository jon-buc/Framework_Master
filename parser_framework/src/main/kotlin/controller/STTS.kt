package controller


enum class STTS(val word: String, val tag: String) {
    NN("Tisch", "NN"), NE("Hans", "NE"),
    ADJA("große", "ADJA"), ADJD("schnell", "ADJD"),
    CARD("zwei", "CARD"),
    VMFIN("dürfen", "VMFIN"), VAFIN("bist", "VAFIN"), VVFIN("gehst", "VVFIN"),
    VAIMP("sei", "VAIMP"), VVIMP("komm", "VVIMP"),
    VVINF("gehen", "VVINF"), VAINF("werden", "VAINF"), VMINF("wollen", "VMINF"), VVIZU(
        "anzukommen",
        "VVIZU"
    ),
    VVPP("gegangen", "VVPP"), VMPP("gekonnt", "VMPP"), VAPP("gewesen", "VAPP"),
    ART("der", "ART"),
    PPER("ich", "PPER"), PRF("sich", "PRF"),
    PPOSAT("mein", "PPOSAT"), PPOSS("meins", "PPOSS"),
    PDAT("jener", "PDAT"), PDS("dieser", "PDS"),
    PIAT("kein", "PIAT"), PIDAT("wenig", "PIDAT"), PIS("keiner", "PIS"),
    PRELAT("dessen", "PRELAT"), PRELS("der", "PRELS"),
    PWAT("welche", "PWAT"), PWS("wer", "PWS"), PWAV("warum", "PWAV"),
    PAV("dafür", "PAV"),
    ADV("schon", "ADV"),
    KOUI("um", "KOUI"), KOUS("weil", "KOUS"), KON("und", "KON"), KOKOM("als", "KOKOM"),
    APPR("in", "APPR"), APPRART("im", "APPRART"), APPO("zufolge", "APPO"), APZR("an", "APZR"),
    PTKZU("zu", "PTKZU"), PTKNEG("nicht", "PTKNEG"), PTKVZ("an", "PTKVZ"), PTKA(
        "am",
        "PTKA"
    ),
    PTKANT("ja", "PTKANT"),
    ITJ("mhm", "ITJ"), TRUNC("An-", "TRUNC"), XY("H2O", "XY"), FM("Fish", "FM"),
    KOMMA(",", "$,"), POINT(".", "$.");

    companion object {
        private val map = values().associateBy(STTS::tag)
        fun containsTag(tag: String) = map.containsKey(tag)
        fun getWordFromTag(tag: String) = if (map.containsKey(tag)) {
            map[tag]!!.word
        } else {
            ""
        }
    }
}
