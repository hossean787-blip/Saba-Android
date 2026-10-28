package ir.saba.moadian

/** Structural checks only. Does not verify Verhoeff or official tax registration. */
data class TaxIdParts(val memoryId: String, val dayHex: String, val serialHex: String, val checkDigit: Char)

object TaxIdStructure {
    private val hex = Regex("[0-9A-Fa-f]+")
    fun parse(value: String): TaxIdParts {
        require(value.length == 22) { "Tax ID must have exactly 22 characters" }
        val memory = value.substring(0, 6)
        val day = value.substring(6, 11)
        val serial = value.substring(11, 21)
        val check = value[21]
        require(memory.all { it.isLetterOrDigit() && it.code < 128 })
        require(day.matches(hex) && serial.matches(hex))
        require(check.isDigit() && check.code in 48..57)
        return TaxIdParts(memory, day.uppercase(), serial.uppercase(), check)
    }
}
