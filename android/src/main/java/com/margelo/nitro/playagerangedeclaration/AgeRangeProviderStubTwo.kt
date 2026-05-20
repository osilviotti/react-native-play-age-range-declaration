package com.margelo.nitro.playagerangedeclaration

import android.content.Context

class AgeRangeProviderStubTwo(private val context: Context) : AgeRangeDeclarationProvider {

    override suspend fun getPlayAgeRangeDeclaration(): PlayAgeRangeDeclarationResult {
        return PlayAgeRangeDeclarationResult(
            isEligible = false,
            installId = null,
            userStatus = null,
            error = "Not implemented",
            ageLower = null,
            ageUpper = null,
            mostRecentApprovalDate = null,
        )
    }

    companion object {
        fun isAvailable(context: Context): Boolean = false
    }
}
