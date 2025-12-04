package io.devide.qrtx

import org.json.JSONObject

data class Droplet(
    val seed: Long,
    val data: String,
    val numBlocks: Int,
    val fileSize: Long,
    val blockSize: Int
) {
    companion object {
        fun fromString(jsonString: String): Droplet? {
            return try {
                val jsonObj = JSONObject(jsonString)
                val seed = jsonObj.getLong("seed")
                val data = jsonObj.getString("data")
                val numBlocks = jsonObj.getInt("num_blocks")
                val fileSize = jsonObj.getLong("file_size")
                val blockSize = jsonObj.getInt("block_size")
                Droplet(seed, data, numBlocks, fileSize, blockSize)
            } catch (e: Exception) {
                // Return null if JSON parsing fails
                null
            }
        }
    }
}
