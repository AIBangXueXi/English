package com.example.english.data.api

import retrofit2.http.GET
import retrofit2.http.Query

interface WordApiService {
    @GET("bxx_en_android/word")
    suspend fun getWords(
        @Query("seq") seq: Int = 1,
        @Query("num") num: Int = 20
    ): ApiResponse

    @GET("bxx_en_android/version")
    suspend fun getVersion(): VersionResponse
}
