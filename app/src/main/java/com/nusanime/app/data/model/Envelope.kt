package com.nusanime.app.data.model

import com.google.gson.annotations.SerializedName

/** The API wraps every payload in {"status":..., "message":..., "data":...}. */
data class Envelope<T>(
    @SerializedName("status") val status: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: T? = null,
)
