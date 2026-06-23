package com.superencrypter.data.remote

import com.superencrypter.model.VirusTotalStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ScanResult(
    val status: VirusTotalStatus,
    val malicious: Int = 0,
    val suspicious: Int = 0,
    val harmless: Int = 0,
    val undetected: Int = 0
)

data class ScanHistoryItem(
    val id: Long,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val checkedAt: String,
    val result: ScanResult
)

data class ScanJobStatus(
    val id: String,
    val status: String,
    val statusText: String,
    val queuePosition: Int? = null,
    val result: ScanResult? = null,
    val error: String? = null
)

class VirusTotalClient(
    private val baseUrl: String,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()
) {
    suspend fun scanSha256(sha256: String): ScanResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("sha256", sha256).toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/scan")
            .post(body)
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (_: IOException) {
            error(backendConnectionMessage())
        }
        response.use {
            val responseBody = it.body?.string().orEmpty()
            if (!it.isSuccessful) error(scanFileErrorMessage(it.code, responseBody))
            parseScanResult(JSONObject(responseBody))
        }
    }

    suspend fun scanFile(
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): ScanResult = withContext(Dispatchers.IO) {
        val mediaType = mimeType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, bytes.toRequestBody(mediaType))
            .build()
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/scan-file")
            .post(body)
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (_: IOException) {
            error(backendConnectionMessage())
        }
        response.use {
            val responseBody = it.body?.string().orEmpty()
            if (!it.isSuccessful) error(scanFileErrorMessage(it.code, responseBody))
            parseScanResult(JSONObject(responseBody))
        }
    }

    suspend fun startScanFileJob(
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): ScanJobStatus = withContext(Dispatchers.IO) {
        val mediaType = mimeType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, bytes.toRequestBody(mediaType))
            .build()
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/scan-file-jobs")
            .post(body)
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (_: IOException) {
            error(backendConnectionMessage())
        }
        response.use {
            val responseBody = it.body?.string().orEmpty()
            if (!it.isSuccessful) error(scanFileErrorMessage(it.code, responseBody))
            parseScanJobStatus(JSONObject(responseBody))
        }
    }

    suspend fun scanFileJob(jobId: String): ScanJobStatus = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/scan-file-jobs/$jobId")
            .get()
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (_: IOException) {
            error(backendConnectionMessage())
        }
        response.use {
            val responseBody = it.body?.string().orEmpty()
            if (!it.isSuccessful) error(scanFileErrorMessage(it.code, responseBody))
            parseScanJobStatus(JSONObject(responseBody))
        }
    }

    suspend fun scanHistory(limit: Int? = null): List<ScanHistoryItem> = withContext(Dispatchers.IO) {
        val url = buildString {
            append("${baseUrl.trimEnd('/')}/scan-history")
            if (limit != null) append("?limit=$limit")
        }
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) return@withContext emptyList()
            parseScanHistory(JSONArray(it.body?.string().orEmpty()))
        }
    }

    suspend fun clearScanHistory(): Unit = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/scan-history")
            .delete()
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (_: IOException) {
            error(backendConnectionMessage())
        }
        response.use {
            val responseBody = it.body?.string().orEmpty()
            if (!it.isSuccessful) error(scanFileErrorMessage(it.code, responseBody))
        }
    }

    private fun parseScanResult(json: JSONObject): ScanResult =
        ScanResult(
            status = VirusTotalStatus.fromBackend(json.optString("status")),
            malicious = json.optInt("malicious", 0),
            suspicious = json.optInt("suspicious", 0),
            harmless = json.optInt("harmless", 0),
            undetected = json.optInt("undetected", 0)
        )

    private fun parseScanJobStatus(json: JSONObject): ScanJobStatus {
        val resultJson = json.optJSONObject("result")
        return ScanJobStatus(
            id = json.optString("id"),
            status = json.optString("status"),
            statusText = json.optString("statusText", "Análise em andamento"),
            queuePosition = json.optIntOrNull("queuePosition"),
            result = resultJson?.let(::parseScanResult),
            error = json.optString("error").takeIf { it.isNotBlank() }
        )
    }

    private fun scanFileErrorMessage(code: Int, responseBody: String): String {
        val backendMessage = runCatching {
            JSONObject(responseBody).optString("error")
        }.getOrNull().orEmpty()

        return if (backendMessage.isNotBlank()) {
            backendMessage
        } else {
            "Backend recusou o scan manual (HTTP $code)."
        }
    }

    private fun backendConnectionMessage(): String =
        "Não foi possível conectar ao backend em $baseUrl. Inicie o backend com npm start na pasta backend. " +
            "Se estiver usando celular físico, troque a URL do backend para o IP do computador na mesma rede."

    private fun parseScanHistory(json: JSONArray): List<ScanHistoryItem> =
        List(json.length()) { index ->
            val item = json.getJSONObject(index)
            ScanHistoryItem(
                id = item.optLong("id", index.toLong()),
                fileName = item.optString("fileName", "arquivo"),
                mimeType = item.optString("mimeType", "application/octet-stream"),
                sizeBytes = item.optLong("sizeBytes", 0L),
                checkedAt = item.optString("checkedAt"),
                result = parseScanResult(item)
            )
        }

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (isNull(name)) null else optInt(name)
}
