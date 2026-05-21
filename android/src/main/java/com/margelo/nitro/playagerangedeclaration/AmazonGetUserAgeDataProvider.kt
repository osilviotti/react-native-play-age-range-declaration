package com.margelo.nitro.playagerangedeclaration

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

// https://developer.amazon.com/docs/app-submission/test-getuseragedata-api.html

class AmazonGetUserAgeDataProvider(private val context: Context) : AgeRangeDeclarationProvider {

    override suspend fun getPlayAgeRangeDeclaration(): PlayAgeRangeDeclarationResult {
        return try {
            withTimeout(TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    queryAgeData()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching age data", e)
            PlayAgeRangeDeclarationResult(
                isEligible = false,
                installId = null,
                userStatus = null,
                error = "AMAZON_AGE_DATA_ERROR: ${e.message}",
                ageLower = null,
                ageUpper = null,
                mostRecentApprovalDate = null,
            )
        }
    }

    private fun queryAgeData(): PlayAgeRangeDeclarationResult {
        val uri = Uri.parse("content://$AUTHORITY$PATH")
        val cursor = context.contentResolver.query(uri, null, null, null, null)
            ?: return PlayAgeRangeDeclarationResult(
                isEligible = false,
                installId = null,
                userStatus = null,
                error = "AMAZON_AGE_DATA_NULL_CURSOR",
                ageLower = null,
                ageUpper = null,
                mostRecentApprovalDate = null,
            )

        return cursor.use {
            if (!it.moveToFirst()) {
                return@use PlayAgeRangeDeclarationResult(
                    isEligible = false,
                    installId = null,
                    userStatus = null,
                    error = "AMAZON_AGE_DATA_EMPTY_CURSOR",
                    ageLower = null,
                    ageUpper = null,
                    mostRecentApprovalDate = null,
                )
            }

            val responseStatus = it.getString(it.getColumnIndexOrThrow(COLUMN_RESPONSE_STATUS))
            if (responseStatus != RESPONSE_STATUS_SUCCESS) {
                return@use PlayAgeRangeDeclarationResult(
                    isEligible = false,
                    installId = null,
                    userStatus = null,
                    error = "AMAZON_AGE_DATA_RESPONSE_ERROR: $responseStatus",
                    ageLower = null,
                    ageUpper = null,
                    mostRecentApprovalDate = null,
                )
            }

            val userStatusStr = it.getString(it.getColumnIndexOrThrow(COLUMN_USER_STATUS))

            val ageLowerCol = it.getColumnIndex(COLUMN_AGE_LOWER)
            val ageUpperCol = it.getColumnIndex(COLUMN_AGE_UPPER)
            val userIdCol = it.getColumnIndex(COLUMN_USER_ID)
            val approvalDateCol = it.getColumnIndex(COLUMN_MOST_RECENT_APPROVAL_DATE)

            val ageLower = if (ageLowerCol >= 0 && !it.isNull(ageLowerCol)) it.getInt(ageLowerCol).toDouble() else null
            val ageUpper = if (ageUpperCol >= 0 && !it.isNull(ageUpperCol)) it.getInt(ageUpperCol).toDouble() else null
            val userId = if (userIdCol >= 0 && !it.isNull(userIdCol)) it.getString(userIdCol) else null
            val approvalDate = if (approvalDateCol >= 0 && !it.isNull(approvalDateCol)) it.getString(approvalDateCol) else null

            // userStatus will be empty if the user is not in a location where they are
            // legally required to see the age verification prompt — different from UNKNOWN
            // https://developer.amazon.com/docs/app-submission/test-getuseragedata-api.html
            val userStatus = when (userStatusStr) {
                USER_STATUS_VERIFIED -> PlayAgeRangeDeclarationUserStatusValues._0
                USER_STATUS_SUPERVISED -> PlayAgeRangeDeclarationUserStatusValues._1
                USER_STATUS_CONSENT_NOT_GRANTED -> PlayAgeRangeDeclarationUserStatusValues._3
                USER_STATUS_UNKNOWN -> PlayAgeRangeDeclarationUserStatusValues._4
                else -> null
            }
            val isEligible = userStatus != null

            PlayAgeRangeDeclarationResult(
                isEligible = isEligible,
                installId = userId,
                userStatus = userStatus,
                ageLower = ageLower,
                ageUpper = ageUpper,
                mostRecentApprovalDate = approvalDate,
                error = null,
            )
        }
    }

    companion object {
        private const val TAG = "AmazonGetUserAgeDataProvider"

        // Production authority; use "amzn_test_appstore" for testing
        const val AUTHORITY = "amzn_appstore"

        // Production path; use "/getUserAgeData?testOption=k" (k = 1–11) for testing:
        // testOption=1  → VERIFIED (18+)
        // testOption=2  → UNKNOWN
        // testOption=3  → SUPERVISED, age 0–12
        // testOption=4  → SUPERVISED, age 13–15
        // testOption=5  → SUPERVISED, age 16–17
        // testOption=6  → CONSENT_NOT_GRANTED, age 0–12
        // testOption=7  → empty userStatus (law not applicable in region)
        // testOption=8  → APP_NOT_OWNED
        // testOption=9  → INTERNAL_TRANSIENT_ERROR
        // testOption=10 → INTERNAL_ERROR
        // testOption=11 → FEATURE_NOT_SUPPORTED
        const val PATH = "/getUserAgeData"

        const val COLUMN_RESPONSE_STATUS = "responseStatus"
        const val COLUMN_USER_STATUS = "userStatus"
        const val COLUMN_AGE_LOWER = "ageLower"
        const val COLUMN_AGE_UPPER = "ageUpper"
        const val COLUMN_USER_ID = "userId"
        const val COLUMN_MOST_RECENT_APPROVAL_DATE = "mostRecentApprovalDate"

        // responseStatus values:
        // "SUCCESS"                  - request processed successfully
        // "APP_NOT_OWNED"            - app not owned by this account
        // "INTERNAL_TRANSIENT_ERROR" - temporary service error; may succeed on retry
        // "INTERNAL_ERROR"           - persistent service error
        // "FEATURE_NOT_SUPPORTED"    - feature unavailable in user's region
        const val RESPONSE_STATUS_SUCCESS = "SUCCESS"

        // userStatus values:
        // "VERIFIED"            - user is 18+ and age-verified
        // "SUPERVISED"          - user is under 18 with parental supervision
        // "CONSENT_NOT_GRANTED" - user is under 18 without parental consent/supervision
        // "UNKNOWN"             - age verification/consent status cannot be determined
        // "" (empty)            - age verification law not applicable in user's region
        const val USER_STATUS_VERIFIED = "VERIFIED"
        const val USER_STATUS_SUPERVISED = "SUPERVISED"
        const val USER_STATUS_CONSENT_NOT_GRANTED = "CONSENT_NOT_GRANTED"
        const val USER_STATUS_UNKNOWN = "UNKNOWN"

        private const val TIMEOUT_MS = 5000L

        fun isAvailable(context: Context): Boolean {
            return try {
                val uri = Uri.parse("content://$AUTHORITY")
                val client = context.contentResolver.acquireContentProviderClient(uri)
                @Suppress("DEPRECATION")
                client?.release()
                client != null
            } catch (e: Exception) {
                false
            }
        }
    }
}
