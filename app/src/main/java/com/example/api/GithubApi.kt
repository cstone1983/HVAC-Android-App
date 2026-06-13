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
    suspend fun getLayoutConfig(@Url url: String, @retrofit2.http.Query("t") timestamp: Long): Response<com.example.model.HvacLayoutConfig>

    @Streaming
    @GET
    suspend fun downloadFile(@Url fileUrl: String): Response<ResponseBody>
}
