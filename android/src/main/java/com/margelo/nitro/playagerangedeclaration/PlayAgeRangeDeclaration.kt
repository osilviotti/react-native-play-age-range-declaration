package com.margelo.nitro.playagerangedeclaration

import android.content.Context
import com.facebook.proguard.annotations.DoNotStrip
import com.margelo.nitro.core.Promise
import com.margelo.nitro.NitroModules

@DoNotStrip
class PlayAgeRangeDeclaration : HybridPlayAgeRangeDeclarationSpec() {

  private val appContext: Context
    get() = NitroModules.applicationContext
        ?: throw IllegalStateException("Application context not available")

  private fun selectProvider(context: Context): AgeRangeDeclarationProvider = when {
    AmazonGetUserAgeDataProvider.isAvailable(context) -> AmazonGetUserAgeDataProvider(context)
    SamsungGetAgeSignalsProvider.isAvailable(context) -> SamsungGetAgeSignalsProvider(context)
    GooglePlayAgeSignalsProvider.isAvailable(context) -> GooglePlayAgeSignalsProvider(context)
    else -> GooglePlayAgeSignalsProvider(context)
  }

  override fun getPlayAgeRangeDeclaration(): Promise<PlayAgeRangeDeclarationResult> {
    return Promise.async {
      selectProvider(appContext).getPlayAgeRangeDeclaration()
    }
  }

  override fun requestDeclaredAgeRange(firstThresholdAge: Double, secondThresholdAge: Double?, thirdThresholdAge: Double?): Promise<DeclaredAgeRangeResult> {
    return Promise.async {
      DeclaredAgeRangeResult(
        isEligible = false,
        status = null,
        lowerBound = null,
        upperBound = null,
        parentControls = null
      )
    }
  }
}
