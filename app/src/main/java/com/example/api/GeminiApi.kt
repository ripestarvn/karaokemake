package com.example.api

import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiPart(val text: String)

@JsonClass(generateAdapter = true)
data class GeminiContent(val parts: List<GeminiPart>)

@JsonClass(generateAdapter = true)
data class GeminiRequest(val contents: List<GeminiContent>)

@JsonClass(generateAdapter = true)
data class GeminiPartResponse(val text: String?)

@JsonClass(generateAdapter = true)
data class GeminiContentResponse(val parts: List<GeminiPartResponse>)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(val content: GeminiContentResponse)

@JsonClass(generateAdapter = true)
data class GeminiResponse(val candidates: List<GeminiCandidate>?)

interface GeminiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateLyrics(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val service: GeminiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiService::class.java)
    }

    suspend fun generateLyrics(prompt: String): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return ""
        }
        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(
                            text = "Bạn là nhạc sĩ sáng tác lời bài hát karaoke bằng tiếng Việt hoặc tiếng Anh. " +
                                   "Hãy viết lời bài hát karaoke ngắn và ý nghĩa dựa trên yêu cầu này: '$prompt'. " +
                                   "Hãy viết đúng 4 dòng thơ/lời hát ngắn, súc tích, phân tách bằng dấu xuống dòng, không thêm lời dẫn, không thêm số thứ tự hay ký tự đặc biệt khác."
                        )
                    )
                )
            )
        )
        return try {
            val response = service.generateLyrics(apiKey, request)
            val generated = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            generated.trim().replace(Regex("(?m)^Line\\s+\\d+:\\s*"), "")
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
