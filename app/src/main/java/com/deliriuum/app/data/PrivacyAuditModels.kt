package com.deliriuum.app.data

enum class PrivacyCheckStatus {
    PROTECTED,
    PARTIAL,
    EXPOSED,
    NOT_TESTED
}


enum class PrivacyProtectionLevel {
    LOW,
    REINFORCED,
    HIGH
}


data class PrivacyCheck(
    val id: String,
    val title: String,
    val status: PrivacyCheckStatus,
    val detail: String
)


data class PrivacyAuditState(
    val level: PrivacyProtectionLevel,
    val protectedCount: Int,
    val partialCount: Int,
    val exposedCount: Int,
    val notTestedCount: Int,
    val checks: List<PrivacyCheck>
) {

    companion object {

        fun empty(): PrivacyAuditState {
            return PrivacyAuditState(
                level = PrivacyProtectionLevel.LOW,
                protectedCount = 0,
                partialCount = 0,
                exposedCount = 0,
                notTestedCount = 0,
                checks = emptyList()
            )
        }
    }
}