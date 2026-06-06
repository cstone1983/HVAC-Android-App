package com.example.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GithubRelease(
    val id: Long? = null,
    @Json(name = "tag_name") val tagName: String,
    val name: String?,
    val body: String?,
    @Json(name = "published_at") val publishedAt: String? = null,
    val assets: List<GithubAsset>
)

@JsonClass(generateAdapter = true)
data class GithubAsset(
    val name: String,
    @Json(name = "browser_download_url") val browserDownloadUrl: String,
    val size: Long
)
