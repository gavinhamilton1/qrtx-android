package io.devide.qrtx

data class FountainCodePart(val index: Int, val total: Int, val data: String) {
    companion object {
        fun fromString(input: String): FountainCodePart? {
            val parts = input.trim().split('/')
            if (parts.size < 3) {
                return null
            }
            return try {
                val index = parts[0].trim().toInt()
                val total = parts[1].trim().toInt()
                val data = parts.drop(2).joinToString("/")
                FountainCodePart(index, total, data)
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
}