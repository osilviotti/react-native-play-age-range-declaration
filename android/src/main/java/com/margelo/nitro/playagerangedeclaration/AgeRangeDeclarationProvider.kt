package com.margelo.nitro.playagerangedeclaration

import android.content.Context

/**
 * Contract for a platform-specific age range declaration API implementation.
 *
 * Each implementing class must also provide a companion object with:
 *   fun isAvailable(context: Context): Boolean
 *
 * This method is used by [PlayAgeRangeDeclaration] to select the appropriate
 * provider at runtime. Return true if this provider's underlying API is
 * available and should be used on the current device.
 */
interface AgeRangeDeclarationProvider {
    suspend fun getPlayAgeRangeDeclaration(): PlayAgeRangeDeclarationResult
}
