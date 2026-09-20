package com.example.api

import com.example.model.GithubRelease
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

interface GithubApi {
    @GET
    suspend fun getLatestRelease(@Url url: String): Response<GithubRelease>

    @GET
    suspend fun getReleases(@Url url: String): Response<List<GithubRelease>>

    @GET
    suspend fun getLatestCommit(@Url url: String): Response<com.example.model.GithubCommit>

    @GET
    suspend fun getRecentCommits(@Url url: String): Response<List<com.example.model.GithubCommit>>

    @GET
    // Returns the raw body deliberately. Parsing into HvacLayoutConfig here and re-serializing
    // before saving silently dropped every key the running build didn't know about, so a panel
    // that applied a newer config on an older APK stored a stripped copy under the new version
    // number and looked up to date while missing whole features.
    suspend fun getLayoutConfig(@Url url: String, @retrofit2.http.Query("t") timestamp: Long): Response<okhttp3.ResponseBody>

    @Streaming
    @GET
    suspend fun downloadFile(@Url fileUrl: String): Response<ResponseBody>
}
