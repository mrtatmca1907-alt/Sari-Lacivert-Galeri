package com.atmaca.reeldroppro.model

enum class Platform { INSTAGRAM_PROFILE, INSTAGRAM_HASHTAG, FACEBOOK }
enum class JobState { QUEUED, RESOLVING, DOWNLOADING, POST_PROCESSING, COMPLETED, RETRY_WAIT, FAILED, CANCELLED }
data class ParsedInput(val platform: Platform, val value: String, val sourceKey: String)
