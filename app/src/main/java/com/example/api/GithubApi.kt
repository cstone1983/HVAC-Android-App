package com.example.api

import com.example.model.GithubRelease
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

interface GithubApi {
    @GET("repos/cstone1983/HVAC-Android-App/releases/latest")
    suspend fun getLatestRelease(): Response<GithubRelease>

    @GET("repos/cstone1983/HVAC-Android-App/releases")
    suspend fun getReleases(): Response<List<GithubRelease>>

    @Streaming
    @GET
    suspend fun downloadFile(@Url fileUrl: String): Response<ResponseBody>
}
