package io.devide.qrtx

import android.util.Base64
import android.util.Log

/**
 * Fountain Code (Luby Transform Code) Decoder for Android
 * Reconstructs files from fountain code droplets
 *
 * Uses SimpleRNG (Linear Congruential Generator) to match the Python encoder's
 * SimpleRNG implementation exactly for cross-platform compatibility.
 */
class FountainCodeDecoder(
    private val numBlocks: Int,
    private val blockSize: Int = 256
) {
    private val solvedBlocks: MutableMap<Int, ByteArray> = HashMap()
    private var numSolved: Int = 0
    private var fileSize: Int? = null

    // Track which droplets (seeds) have been processed to avoid duplicates
    private val processedSeeds: MutableSet<Int> = HashSet()

    // Pending droplets that couldn't be solved yet
    private data class PendingDroplet(
        val seed: Int,
        val data: ByteArray,
        val blockIndices: List<Int>
    )

    private val pendingDroplets: MutableList<PendingDroplet> = ArrayList()

    /**
     * Reconstruct block indices from seed using simple portable RNG
     * This matches Python's SimpleRNG implementation exactly
     */
    private fun getBlockIndicesFromSeed(seed: Int): List<Int> {
        val rng = SimpleRNG(seed)
        val maxSelect = minOf(numBlocks, 10)
        
        // Generate number of blocks to select: [1, maxSelect] inclusive
        val numToSelect = rng.randint(1, maxSelect)

        // Sample block indices without replacement
        val population = (0 until numBlocks).toList()
        return rng.sample(population, numToSelect)
    }

    /**
     * Add a received droplet and attempt to solve blocks
     *
     * @param droplet Map containing: seed (Int), data (String base64), num_blocks (Int), file_size (Int), block_size (Int)
     * @return true if all blocks are solved, false otherwise
     * @throws IllegalArgumentException if droplet data is invalid or block size mismatch
     */
    fun addDroplet(droplet: Map<String, Any>): Boolean {
        // Extract droplet data
        val seed = (droplet["seed"] as? Number)?.toInt()
            ?: throw IllegalArgumentException("Missing or invalid 'seed' in droplet")
        
        // Skip if we've already processed this droplet
        if (processedSeeds.contains(seed)) {
            return isComplete()
        }
        
        val dataBase64 = droplet["data"] as? String
            ?: throw IllegalArgumentException("Missing or invalid 'data' in droplet")
        val dropletData = Base64.decode(dataBase64, Base64.DEFAULT)

        // Validate droplet data size matches block size
        if (dropletData.size != blockSize) {
            throw IllegalArgumentException(
                "Droplet data size (${dropletData.size}) does not match block size ($blockSize). " +
                "Seed: $seed"
            )
        }

        // Store file_size if available
        if (fileSize == null && droplet.containsKey("file_size")) {
            fileSize = (droplet["file_size"] as? Number)?.toInt()
        }

        // Reconstruct which blocks were XORed using the seed
        val blockIndices = getBlockIndicesFromSeed(seed)
        
        // Debug: Log block indices to verify they match Python (check logcat)
        if (processedSeeds.size < 10) {
            Log.d("FountainCodeDecoder", "Seed $seed -> block indices: $blockIndices, unknown: ${blockIndices.filter { !solvedBlocks.containsKey(it) }.size}")
        }

        // Try to solve blocks
        val unknownIndices = blockIndices.filter { !solvedBlocks.containsKey(it) }

        if (unknownIndices.size == 1) {
            // We can solve this block!
            val targetIdx = unknownIndices[0]
            val result = dropletData.copyOf()

            // XOR out all known blocks
            for (idx in blockIndices) {
                if (idx != targetIdx && solvedBlocks.containsKey(idx)) {
                    val knownBlock = solvedBlocks[idx]!!
                    // Ensure both arrays are the same size
                    if (knownBlock.size != result.size) {
                        throw IllegalArgumentException(
                            "Block size mismatch when solving block $targetIdx: " +
                            "known block size ${knownBlock.size} != result size ${result.size}"
                        )
                    }
                    for (i in result.indices) {
                        result[i] = (result[i].toInt() xor knownBlock[i].toInt()).toByte()
                    }
                }
            }

            // Store the solved block
            solvedBlocks[targetIdx] = result
            numSolved++

            // Try to solve pending droplets with this new information
            processPendingDroplets()
        } else {
            // Store for later processing
            pendingDroplets.add(PendingDroplet(seed, dropletData, blockIndices))
        }
        
        // Mark this seed as processed
        processedSeeds.add(seed)

        return isComplete()
    }

    /**
     * Try to solve pending droplets with newly solved blocks
     */
    private fun processPendingDroplets() {
        val remaining = mutableListOf<PendingDroplet>()

        for (droplet in pendingDroplets) {
            val unknownIndices = droplet.blockIndices.filter { !solvedBlocks.containsKey(it) }

            if (unknownIndices.size == 1) {
                // Can solve now!
                val targetIdx = unknownIndices[0]
                val result = droplet.data.copyOf()
                var canSolve = true

                // XOR out all known blocks
                for (idx in droplet.blockIndices) {
                    if (idx != targetIdx && solvedBlocks.containsKey(idx)) {
                        val knownBlock = solvedBlocks[idx]!!
                        // Ensure both arrays are the same size
                        if (knownBlock.size != result.size) {
                            // Cannot solve this droplet yet due to size mismatch
                            canSolve = false
                            break
                        }
                        for (i in result.indices) {
                            result[i] = (result[i].toInt() xor knownBlock[i].toInt()).toByte()
                        }
                    }
                }

                if (canSolve) {
                    solvedBlocks[targetIdx] = result
                    numSolved++
                } else {
                    remaining.add(droplet)
                }
            } else {
                remaining.add(droplet)
            }
        }

        pendingDroplets.clear()
        pendingDroplets.addAll(remaining)
    }
    
    /**
     * Try to process pending droplets multiple times to make progress
     * This helps when solving one block unlocks multiple other blocks
     */
    fun processPendingDropletsAggressively() {
        var previousSolved = numSolved
        var iterations = 0
        val maxIterations = 10
        
        // Keep processing until no more progress is made or we reach max iterations
        while (iterations < maxIterations) {
            processPendingDroplets()
            
            // If we made progress, continue; otherwise break
            if (numSolved > previousSolved) {
                previousSolved = numSolved
                iterations = 0 // Reset counter when progress is made
            } else {
                iterations++
            }
            
            // If we're complete, stop
            if (isComplete()) {
                break
            }
        }
    }
    
    /**
     * Get the number of pending droplets
     */
    fun getNumPendingDroplets(): Int = pendingDroplets.size

    /**
     * Check if all blocks have been solved
     * Verifies that all blocks from 0 to numBlocks-1 are actually present
     */
    fun isComplete(): Boolean {
        if (numSolved < numBlocks) {
            return false
        }
        // Verify all blocks are actually present
        for (i in 0 until numBlocks) {
            if (!solvedBlocks.containsKey(i)) {
                return false
            }
        }
        return true
    }

    /**
     * Get the current progress as a percentage (0.0 to 100.0)
     */
    fun getProgress(): Double {
        return if (numBlocks > 0) {
            (numSolved.toDouble() / numBlocks.toDouble()) * 100.0
        } else {
            0.0
        }
    }

    /**
     * Get the number of solved blocks
     */
    fun getNumSolved(): Int = numSolved

    /**
     * Get the total number of blocks
     */
    fun getNumBlocks(): Int = numBlocks
    
    /**
     * Check if a specific block has been solved
     */
    fun hasBlock(index: Int): Boolean {
        return solvedBlocks.containsKey(index)
    }

    /**
     * Reconstruct the original file from solved blocks
     *
     * @return Reconstructed file bytes
     * @throws IllegalStateException if not all blocks are solved
     */
    fun getResult(): ByteArray {
        if (!isComplete()) {
            throw IllegalStateException("Not all blocks are solved yet. Progress: ${getProgress()}%")
        }

        // Reconstruct file in order
        val resultParts = mutableListOf<ByteArray>()
        for (i in 0 until numBlocks) {
            val block = solvedBlocks[i]
                ?: throw IllegalStateException("Block $i is missing")

            // Remove padding from last block if file_size is known
            if (i == numBlocks - 1 && fileSize != null) {
                val actualSize = fileSize!! % blockSize
                if (actualSize > 0) {
                    resultParts.add(block.sliceArray(0 until actualSize))
                } else {
                    resultParts.add(block.sliceArray(0 until blockSize))
                }
            } else {
                resultParts.add(block)
            }
        }

        // Combine all blocks
        val totalSize = resultParts.sumOf { it.size }
        val result = ByteArray(totalSize)
        var offset = 0
        for (part in resultParts) {
            System.arraycopy(part, 0, result, offset, part.size)
            offset += part.size
        }

        return result
    }

    /**
     * Reset the decoder (clear all solved blocks and pending droplets)
     */
    fun reset() {
        solvedBlocks.clear()
        pendingDroplets.clear()
        numSolved = 0
        fileSize = null
    }
}
