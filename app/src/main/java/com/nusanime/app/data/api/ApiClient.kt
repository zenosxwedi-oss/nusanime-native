package com.nusanime.app.data.api

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    /** Direct upstream. If unreachable (some Indonesian ISPs/Cloudflare block app TLS),
     *  requests are retried via our own reverse proxy on the VPS tunnel. */
    private const val DIRECT_HOST = "www.sankavollerei.web.id"
    private const val PROXY_HOST = "vpn.nusanime.web.id"

    val api: NusanimeApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
                )
            }
            .addInterceptor { chain ->
                val req = chain.request()
                var resp = chain.proceed(req)
                // Only retry via proxy when direct path used and the answer is unusable
                val bodyPeek = try { resp.peekBody(64).string().trim() } catch (_: Exception) { "" }
                val unusable = !resp.isSuccessful || bodyPeek.startsWith("<") || bodyPeek.isEmpty()
                if (unusable && req.url.host == DIRECT_HOST) {
                    resp.close()
                    val viaProxy = req.newBuilder()
                        .url(req.url.newBuilder().host(PROXY_HOST).build())
                        .build()
                    resp = chain.proceed(viaProxy)
                }
                resp
            }
            .build()

        val gson = GsonBuilder().setLenient().create()

        Retrofit.Builder()
            .baseUrl(NusanimeApi.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(NusanimeApi::class.java)
    }
}
