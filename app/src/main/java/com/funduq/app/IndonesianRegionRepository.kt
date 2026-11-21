package com.funduq.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class RegionOption(
    val id: String,
    val name: String
)

object IndonesianRegionRepository {
    private const val BASE_URL = "https://www.emsifa.com/api-wilayah-indonesia/api"

    suspend fun getProvinces(): List<RegionOption> = withContext(Dispatchers.IO) {
        fetchRegions("$BASE_URL/provinces.json")
    }

    suspend fun getCities(provinceId: String): List<RegionOption> = withContext(Dispatchers.IO) {
        fetchRegions("$BASE_URL/regencies/$provinceId.json")
    }

    private fun fetchRegions(url: String): List<RegionOption> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.requestMethod = "GET"

        return try {
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                throw IOException("Layanan wilayah mengembalikan HTTP $statusCode")
            }

            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val regions = try {
                val json = JSONArray(response)
                (0 until json.length()).mapNotNull { index ->
                    val region = json.optJSONObject(index) ?: return@mapNotNull null
                    val id = region.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val name = region.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    RegionOption(id = id, name = name)
                }
            } catch (error: JSONException) {
                throw IOException("Format data wilayah tidak valid", error)
            }

            if (regions.isEmpty()) {
                throw IOException("Data wilayah tidak tersedia")
            }
            regions
        } finally {
            connection.disconnect()
        }
    }
}
