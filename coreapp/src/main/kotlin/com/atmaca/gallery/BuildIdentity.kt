package com.atmaca.gallery

const val ATMACA_TEST_VERSION_CODE: Int = 140906
private const val ATMACA_BUILD_BADGE: String = "BUILD 140906"

fun visibleBuildBadge(): String = ATMACA_BUILD_BADGE

fun appVersionCodeForTest(): Int = ATMACA_TEST_VERSION_CODE
