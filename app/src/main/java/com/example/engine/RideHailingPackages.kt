package com.example.engine

object RideHailingPackages {

    /**
     * Known ride-hailing target packages worldwide.
     */
    val TARGET_PACKAGES = setOf(
        "com.ubercab.driver",
        "com.ubercab",
        "sinet.startup.indriver",
        "com.mizink.indriver",
        "com.careem.captain",
        "com.careem.acclaim",
        "ee.mtakso.driver", // Bolt
        "com.didiglobal.driver",
        "com.sdu.didi.gui",
        "com.yandex.taximeter", // Yango / Yandex Pro
        "com.grabtaxi.driver2",
        "me.lyft.android.driver"
    )

    /**
     * Checks if a package belongs to supported ride-hailing applications or internal testing.
     */
    fun isTargetPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val lower = packageName.lowercase()
        return TARGET_PACKAGES.contains(lower) ||
                lower.contains("uber") ||
                lower.contains("indriver") ||
                lower.contains("careem") ||
                lower.contains("bolt") ||
                lower.contains("didi") ||
                lower.contains("yango") ||
                lower.contains("taximeter") ||
                lower.contains("lyft") ||
                lower.contains("grabtaxi") ||
                lower.contains("aetosdriver") ||
                lower.contains("example")
    }

    /**
     * Maps package name to human-readable platform label.
     */
    fun getReadableAppName(packageName: String?): String {
        if (packageName.isNullOrBlank()) return "Ride Alert"
        val lower = packageName.lowercase()
        return when {
            lower.contains("uber") -> "Uber Driver"
            lower.contains("indriver") || lower.contains("mizink") || lower.contains("sinet") -> "InDrive"
            lower.contains("careem") -> "Careem Captain"
            lower.contains("bolt") || lower.contains("mtakso") -> "Bolt Driver"
            lower.contains("didi") -> "DiDi Driver"
            lower.contains("yango") || lower.contains("taximeter") -> "Yango Pro"
            lower.contains("lyft") -> "Lyft Driver"
            lower.contains("grab") -> "Grab Driver"
            else -> "Ride Alert"
        }
    }
}
