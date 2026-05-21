package com.margelo.nitro.playagerangedeclaration

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

// https://developer.samsung.com/galaxy-store/galaxy-store-content-provider-api/get-age-signals.html

class SamsungGetAgeSignalsProvider(private val context: Context) : AgeRangeDeclarationProvider {

    override suspend fun getPlayAgeRangeDeclaration(): PlayAgeRangeDeclarationResult {
        return try {
            withTimeout(TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    queryAgeSignals()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching age signals", e)
            PlayAgeRangeDeclarationResult(
                isEligible = false,
                installId = null,
                userStatus = null,
                error = "SAMSUNG_AGE_SIGNALS_ERROR: ${e.message}",
                ageLower = null,
                ageUpper = null,
                mostRecentApprovalDate = null,
            )
        }
    }

    private fun queryAgeSignals(): PlayAgeRangeDeclarationResult {
        val uri = Uri.parse(URI_ASAA_SETTINGS)
        val resultBundle = context.contentResolver.call(uri, METHOD_GET_AGE_SIGNAL_RESULT, null, null)
            ?: return PlayAgeRangeDeclarationResult(
                isEligible = false,
                installId = null,
                userStatus = null,
                error = "SAMSUNG_AGE_SIGNALS_NULL_RESULT",
                ageLower = null,
                ageUpper = null,
                mostRecentApprovalDate = null,
            )

        val resultCode = resultBundle.getInt(KEY_RESULT_CODE, RESULT_CODE_FAILURE)
        if (resultCode != RESULT_CODE_SUCCESS) {
            val message = resultBundle.getString(KEY_RESULT_MESSAGE, "")
            return PlayAgeRangeDeclarationResult(
                isEligible = false,
                installId = null,
                userStatus = null,
                error = "SAMSUNG_AGE_SIGNALS_FAILURE: $message",
                ageLower = null,
                ageUpper = null,
                mostRecentApprovalDate = null,
            )
        }

        val userStatusStr = resultBundle.getString(KEY_RESULT_USER_STATUS, "")
        val ageLower = resultBundle.getString(KEY_RESULT_AGE_LOWER)?.toDoubleOrNull()
        val ageUpper = resultBundle.getString(KEY_RESULT_AGE_UPPER)?.toDoubleOrNull()
        val approvalDate = resultBundle.getString(KEY_RESULT_APPROVAL_DATE)
        val installId = resultBundle.getString(KEY_RESULT_INSTALL_ID)

        // userStatus will be UNKNOWN if the user has not agreed to Galaxy Store T&Cs,
        // the installer is not Galaxy Store, the user is a minor outside a Samsung family
        // group, or Samsung parental controls are disabled — different from a supervised user
        // https://developer.samsung.com/galaxy-store/galaxy-store-content-provider-api/get-age-signals.html
        val userStatus = when (userStatusStr) {
            USER_STATUS_VERIFIED -> PlayAgeRangeDeclarationUserStatusValues._0
            USER_STATUS_SUPERVISED -> PlayAgeRangeDeclarationUserStatusValues._1
            USER_STATUS_SUPERVISED_APPROVAL_PENDING -> PlayAgeRangeDeclarationUserStatusValues._2
            USER_STATUS_SUPERVISED_APPROVAL_DENIED -> PlayAgeRangeDeclarationUserStatusValues._3
            USER_STATUS_UNKNOWN -> PlayAgeRangeDeclarationUserStatusValues._4
            else -> null
        }
        val isEligible = userStatus != null

        return PlayAgeRangeDeclarationResult(
            isEligible = isEligible,
            installId = installId,
            userStatus = userStatus,
            ageLower = ageLower,
            ageUpper = ageUpper,
            mostRecentApprovalDate = approvalDate,
            error = null,
        )
    }

    companion object {
        private const val TAG = "SamsungGetAgeSignalsProvider"

        private const val GALAXYSTORE = "com.sec.android.app.samsungapps"

        // Minimum Galaxy Store version 4.6.03.1 is required — maps to provider metadata version >= 1.0
        private const val ASAA_META = "$GALAXYSTORE.AccountabilityActProvider.version"
        private const val MIN_PROVIDER_VERSION = 1.0f

        // Full URI: "content://com.sec.android.app.samsungapps.provider.ASAA/settings"
        private const val ASAA_AUTHORITY = "com.sec.android.app.samsungapps.provider.ASAA"
        private const val QUERY_SETTINGS = "settings"
        private const val URI_ASAA_SETTINGS = "content://$ASAA_AUTHORITY/$QUERY_SETTINGS"

        private const val METHOD_GET_AGE_SIGNAL_RESULT = "getAgeSignalResult"

        // result_code values:
        // 0 - success
        // 1 - failure (see result_message for reason)
        private const val KEY_RESULT_CODE = "result_code"
        private const val RESULT_CODE_SUCCESS = 0
        private const val RESULT_CODE_FAILURE = 1

        // result_message values (only present on failure):
        // "It is not a supported method"      - method name is wrong, or function is disabled by server
        //                                       (also returned when user is not subject to current laws)
        // "The device is not registered SA"   - device has no registered Samsung account
        private const val KEY_RESULT_MESSAGE = "result_message"

        private const val KEY_RESULT_USER_STATUS = "userStatus"
        private const val KEY_RESULT_AGE_LOWER = "ageLower"
        private const val KEY_RESULT_AGE_UPPER = "ageUpper"
        private const val KEY_RESULT_APPROVAL_DATE = "mostRecentApprovalDate"
        private const val KEY_RESULT_INSTALL_ID = "installID"

        // userStatus values:
        // "VERIFIED"                    - user is 18+
        // "SUPERVISED"                  - supervised Samsung account managed by a parent who sets their age
        // "SUPERVISED_APPROVAL_PENDING" - supervised; parent has not yet approved one or more pending changes
        // "SUPERVISED_APPROVAL_DENIED"  - supervised; parent has denied one or more pending changes
        // "UNKNOWN"                     - user has not agreed to Galaxy Store T&Cs, installer is not
        //                                 Galaxy Store, minor is not in a Samsung family group, or
        //                                 Samsung parental controls are disabled
        private const val USER_STATUS_VERIFIED = "VERIFIED"
        private const val USER_STATUS_SUPERVISED = "SUPERVISED"
        private const val USER_STATUS_SUPERVISED_APPROVAL_PENDING = "SUPERVISED_APPROVAL_PENDING"
        private const val USER_STATUS_SUPERVISED_APPROVAL_DENIED = "SUPERVISED_APPROVAL_DENIED"
        private const val USER_STATUS_UNKNOWN = "UNKNOWN"

        private const val TIMEOUT_MS = 5000L

        fun isAvailable(context: Context): Boolean {
            return try {
                val installedByStore = PlayAgeRangeDeclaration.getInstallerPackage(context)
                val appInfo = context.packageManager.getApplicationInfo(
                    GALAXYSTORE,
                    PackageManager.GET_META_DATA
                )
                val version = appInfo.metaData?.getFloat(ASAA_META, 0f) ?: 0f
                installedByStore == GALAXYSTORE && version >= MIN_PROVIDER_VERSION
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }
}
