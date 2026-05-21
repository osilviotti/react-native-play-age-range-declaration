package com.margelo.nitro.playagerangedeclaration

import android.content.Context
import android.util.Log
import com.google.android.play.agesignals.AgeSignalsManager
import com.google.android.play.agesignals.AgeSignalsManagerFactory
import com.google.android.play.agesignals.AgeSignalsRequest
import com.google.android.play.agesignals.AgeSignalsResult
import com.google.android.play.agesignals.testing.FakeAgeSignalsManager
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume

// https://developer.android.com/google/play/age-signals/test-age-signals-api

class GooglePlayAgeSignalsProvider(private val context: Context) : AgeRangeDeclarationProvider {

    override suspend fun getPlayAgeRangeDeclaration(): PlayAgeRangeDeclarationResult {
        return try {
            val manager = getManager(context)
            val request = AgeSignalsRequest.builder().build()

            suspendCancellableCoroutine { cont ->
                manager.checkAgeSignals(request)
                    .addOnSuccessListener { r ->
                        val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        val approvalDate = r.mostRecentApprovalDate()?.let { isoDateFormat.format(it) }
                        // userStatus will be empty if the user is not in a location where they are
                        // legally required to see the age verification prompt — different from UNKNOWN
                        // https://developer.android.com/google/play/age-signals/use-age-signals-api#age-signals-responses
                        val userStatus = when (r.userStatus()) {
                            AgeSignalsVerificationStatus.VERIFIED -> PlayAgeRangeDeclarationUserStatusValues._0
                            AgeSignalsVerificationStatus.SUPERVISED -> PlayAgeRangeDeclarationUserStatusValues._1
                            AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_PENDING -> PlayAgeRangeDeclarationUserStatusValues._2
                            AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_DENIED -> PlayAgeRangeDeclarationUserStatusValues._3
                            AgeSignalsVerificationStatus.UNKNOWN -> PlayAgeRangeDeclarationUserStatusValues._4
                            else -> null
                        }
                        val isEligible = userStatus != null

                        cont.resume(
                            PlayAgeRangeDeclarationResult(
                                isEligible = isEligible,
                                installId = r.installId(),
                                ageLower = r.ageLower()?.toDouble(),
                                ageUpper = r.ageUpper()?.toDouble(),
                                mostRecentApprovalDate = approvalDate,
                                userStatus = userStatus,
                                error = null
                            )
                        )
                    }
                    .addOnFailureListener { e ->
                        cont.resume(
                            PlayAgeRangeDeclarationResult(
                                isEligible = false,
                                installId = null,
                                userStatus = null,
                                error = e.message ?: "Unknown error",
                                ageLower = null,
                                ageUpper = null,
                                mostRecentApprovalDate = null,
                            )
                        )
                    }
            }
        } catch (e: Exception) {
            Log.e("GooglePlayAgeSignalsProvider", "Initialization error", e)
            PlayAgeRangeDeclarationResult(
                isEligible = false,
                installId = null,
                userStatus = null,
                error = "AGE_SIGNALS_INIT_ERROR: ${e.message}",
                ageLower = null,
                ageUpper = null,
                mostRecentApprovalDate = null,
            )
        }
    }

    // MOCK: Use setMockUser for testing
    // https://developer.android.com/google/play/age-signals/test-age-signals-api
    //
    // To test different scenarios, you can override the userStatus with one of the following values:
    // - AgeSignalsVerificationStatus.VERIFIED - User is 18+ (verified adult)
    // - AgeSignalsVerificationStatus.SUPERVISED - User is supervised (13-17 with parental controls)
    // - AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_PENDING - Pending approval
    // - AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_DENIED - Approval denied
    // - AgeSignalsVerificationStatus.UNKNOWN - User status unknown
    //
    // Configure it in any of your native app files (e.g. MainActivity.kt):
    //
    // import com.margelo.nitro.playagerangedeclaration.GooglePlayAgeSignalsProvider
    // import com.margelo.nitro.playagerangedeclaration.PlayAgeRangeMockConfig
    //
    // override fun onCreate() {
    //   GooglePlayAgeSignalsProvider.setMockUser(
    //     PlayAgeRangeMockConfig(
    //       userStatus = AgeSignalsVerificationStatus.SUPERVISED,
    //       ageLower = 13,
    //       ageUpper = 17,
    //       mostRecentApprovalDate = Date.from(LocalDate.of(2025, 2, 1).atStartOfDay(ZoneOffset.UTC).toInstant()),
    //       installId = "fake_install_id_12345"
    //     )
    //   )
    // }

    companion object {
        var mockUser: AgeSignalsResult? = null

        fun isAvailable(context: Context): Boolean = true

        fun getManager(context: Context): AgeSignalsManager {
            return mockUser?.let {
                FakeAgeSignalsManager().apply { setNextAgeSignalsResult(it) }
            } ?: AgeSignalsManagerFactory.create(context)
        }

        fun setMockUser(config: PlayAgeRangeMockConfig?) {
            if (config == null) {
                mockUser = null
                return
            }

            val user = AgeSignalsResult.builder().setInstallId("fake_install_id_12345")

            config.userStatus.let { user.setUserStatus(it) }
            config.ageLower?.let { user.setAgeLower(it) }
            config.ageUpper?.let { user.setAgeUpper(it) }
            config.installId?.let { user.setInstallId(it) }
            config.mostRecentApprovalDate?.let { user.setMostRecentApprovalDate(it) }

            mockUser = user.build()
        }
    }
}
