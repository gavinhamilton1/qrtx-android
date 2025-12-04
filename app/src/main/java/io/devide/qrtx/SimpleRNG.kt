package io.devide.qrtx

/**
 * Simple, portable Linear Congruential Generator (LCG)
 * Works identically in Python and Kotlin/Java for cross-platform compatibility.
 * 
 * Uses parameters from Numerical Recipes (L'Ecuyer parameters):
 * multiplier = 1664525
 * increment = 1013904223
 * modulus = 2^32
 */
class SimpleRNG(seed: Int) {
    private var state: Long = (seed.toLong() and 0xFFFFFFFFL)
    
    /**
     * Generate random integer in range [0, bound)
     */
    private fun nextInt(bound: Int): Int {
        if (bound <= 0) {
            throw IllegalArgumentException("bound must be positive")
        }
        
        // Generate next random number using LCG
        state = ((state * 1664525L + 1013904223L) and 0xFFFFFFFFL)
        
        // Scale to range [0, bound)
        return (state % bound).toInt()
    }
    
    /**
     * Generate random integer in range [a, b] (inclusive on both ends)
     * Equivalent to Python's randint(a, b)
     */
    fun randint(a: Int, b: Int): Int {
        if (a > b) {
            throw IllegalArgumentException("a must be <= b")
        }
        return a + nextInt(b - a + 1)
    }
    
    /**
     * Randomly sample k items from population without replacement
     * Equivalent to Python's random.sample(population, k)
     */
    fun sample(population: List<Int>, k: Int): List<Int> {
        if (k > population.size) {
            throw IllegalArgumentException("Sample larger than population")
        }
        if (k < 0) {
            throw IllegalArgumentException("Sample size must be non-negative")
        }
        
        val result = mutableListOf<Int>()
        val available = population.toMutableList()  // Copy
        
        for (i in 0 until k) {
            val index = nextInt(available.size)
            result.add(available.removeAt(index))
        }
        
        return result
    }
}

