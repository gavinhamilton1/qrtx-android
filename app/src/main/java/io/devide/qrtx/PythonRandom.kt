package io.devide.qrtx

import java.util.*

/**
 * Python-compatible Mersenne Twister random number generator
 * This implementation matches Python's random module behavior
 * 
 * Based on the Mersenne Twister algorithm (MT19937)
 */
class PythonRandom(seed: Int) {
    companion object {
        private const val N = 624
        private const val M = 397
        private const val MATRIX_A = 0x9908b0dfL
        private const val UPPER_MASK = 0x80000000L
        private const val LOWER_MASK = 0x7fffffffL
        
        private val MAG01 = longArrayOf(0x0L, MATRIX_A)
    }
    
    private val mt = LongArray(N)
    private var mti = N + 1
    
    init {
        setSeed(seed)
    }
    
    private fun setSeed(seed: Int) {
        mt[0] = (seed.toLong() and 0xffffffffL)
        mti = 1
        while (mti < N) {
            mt[mti] = (1812433253L * (mt[mti - 1] xor (mt[mti - 1] shr 30)) + mti) and 0xffffffffL
            mti++
        }
    }
    
    private fun nextInt(): Int {
        var y: Long
        
        if (mti >= N) {
            var kk = 0
            for (kk in 0 until N - M) {
                y = (mt[kk] and UPPER_MASK) or (mt[kk + 1] and LOWER_MASK)
                mt[kk] = mt[kk + M] xor (y shr 1) xor MAG01[(y and 0x1L).toInt()]
            }
            kk = N - M
            while (kk < N - 1) {
                y = (mt[kk] and UPPER_MASK) or (mt[kk + 1] and LOWER_MASK)
                mt[kk] = mt[kk + M - N] xor (y shr 1) xor MAG01[(y and 0x1L).toInt()]
                kk++
            }
            y = (mt[N - 1] and UPPER_MASK) or (mt[0] and LOWER_MASK)
            mt[N - 1] = mt[M - 1] xor (y shr 1) xor MAG01[(y and 0x1L).toInt()]
            mti = 0
        }
        
        y = mt[mti++]
        
        // Tempering
        y = y xor (y shr 11)
        y = y xor ((y shl 7) and 0x9d2c5680L)
        y = y xor ((y shl 15) and 0xefc60000L)
        y = y xor (y shr 18)
        
        return (y shr 1).toInt()
    }
    
    /**
     * Python's randint(a, b) - inclusive on both ends
     * Returns a random integer N such that a <= N <= b
     */
    fun randint(a: Int, b: Int): Int {
        if (a > b) {
            throw IllegalArgumentException("randint() a > b")
        }
        return a + nextInt(b - a + 1)
    }
    
    /**
     * Python's random.randint equivalent - returns int in [0, n)
     */
    private fun nextInt(n: Int): Int {
        if (n <= 0) {
            throw IllegalArgumentException("n must be positive")
        }
        if ((n and -n) == n) {
            return (n * (nextInt().toLong() and 0xffffffffL shr 1).toInt()).toInt()
        }
        var bits: Int
        var value: Int
        do {
            bits = nextInt() ushr 1
            value = bits % n
        } while (bits - value + (n - 1) < 0)
        return value
    }
    
    /**
     * Python's random.sample() equivalent
     * Randomly sample k items from population without replacement
     */
    fun sample(population: List<Int>, k: Int): List<Int> {
        if (k > population.size) {
            throw IllegalArgumentException("Sample larger than population")
        }
        if (k < 0) {
            throw IllegalArgumentException("Sample size must be non-negative")
        }
        
        val result = mutableListOf<Int>()
        val available = population.toMutableList()
        
        for (i in 0 until k) {
            val index = nextInt(available.size)
            result.add(available.removeAt(index))
        }
        
        return result
    }
}

