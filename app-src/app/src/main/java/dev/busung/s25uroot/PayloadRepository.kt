package dev.busung.s25uroot

import android.content.Context
import android.system.Os
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

data class VerifiedPayloads(
    val profile: TargetProfile,
    val exploit: File,
    val kernelSu: File,
)

class PayloadRepository(private val context: Context) {
    fun loadTargets(): List<TargetProfile> {
        /* v0.2.24+: 完全离线——manifest 内嵌于 APK assets，不访问任何网络。 */
        val manifestBytes = context.assets.open("targets-v3.json").use { input -> input.readBytes() }
        return SupportManifest.parse(manifestBytes).targets
    }

    fun resolveTarget(snapshot: DeviceSnapshot): TargetProfile = loadTargets()
        .firstOrNull { it.matches(snapshot) }
        ?: error(context.getString(R.string.repo_no_profile))

    fun resolveTarget(profileId: String): TargetProfile = loadTargets()
        .firstOrNull { it.profileId == profileId }
        ?: error(context.getString(R.string.repo_profile_missing, profileId))

    fun download(profile: TargetProfile, onProgress: (String) -> Unit): VerifiedPayloads {
        val directory = File(context.filesDir, "payloads/${profile.profileId}").apply { mkdirs() }
        val exploit = bundledAsset("cve-2026-43499-app.so", directory, onProgress,
            context.getString(R.string.artifact_exploit_bundled))
            ?: error("bundled exploit missing: cve-2026-43499-app.so")
        if (profile.profileId == "q5q-f9460-zcs9gzf1") {
            patchF9460Zcs9Gzf1(exploit)
        }
        val kernelSu = bundledAsset("ksud-f731u-kdp", directory, onProgress,
            context.getString(R.string.artifact_kernelsu_bundled))
            ?: error("bundled KernelSU missing: ksud-f731u-kdp")
        Os.chmod(exploit.absolutePath, 0b100100100)
        Os.chmod(kernelSu.absolutePath, 0b100100100)
        return VerifiedPayloads(profile, exploit, kernelSu)
    }

    /** Convert the bundled F731U 5.15.189 payload to F9460ZCS9GZF1 symbol offsets. */
    private fun patchF9460Zcs9Gzf1(file: File) {
        val bytes = file.readBytes()
        require(bytes.size == F9460_PAYLOAD_SIZE) { "unexpected payload size: ${bytes.size}" }
        val actualMd5 = md5(bytes)
        require(actualMd5 == F731_PAYLOAD_MD5) { "unexpected base payload md5: $actualMd5" }
        val patched = bytes.copyOf()
        val patches = arrayOf(
            0x0067ed to byteArrayOf(0x87.toByte()),
            0x0068b9 to byteArrayOf(0x78.toByte()),
            0x006965 to byteArrayOf(0x78.toByte()),
            0x006b7d to byteArrayOf(0x78.toByte()),
            0x006bdd to byteArrayOf(0x78.toByte()),
            0x007471 to byteArrayOf(0x7c.toByte()),
            0x007479 to byteArrayOf(0x83.toByte()),
            0x00754d to byteArrayOf(0x7c.toByte()),
            0x007555 to byteArrayOf(0x83.toByte()),
            0x00765d to byteArrayOf(0x7c.toByte()),
            0x00766c to byteArrayOf(0xf6.toByte(), 0xd7.toByte(), 0x80.toByte(), 0x92.toByte()),
            0x00789d to byteArrayOf(0x80.toByte()),
            0x0078ad to byteArrayOf(0x7f.toByte()),
            0x007af5 to byteArrayOf(0x7c.toByte()),
            0x007afd to byteArrayOf(0x83.toByte()),
        )
        for ((offset, patch) in patches) System.arraycopy(patch, 0, patched, offset, patch.size)
        FileOutputStream(file).use { it.write(patched); it.fd.sync() }
    }

    private fun md5(bytes: ByteArray): String = java.security.MessageDigest.getInstance("MD5")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun bundledAsset(name: String, directory: File, onProgress: (String) -> Unit, label: String): File? {
        return try {
            val destination = File(directory, name)
            if (destination.exists() && !destination.delete()) {
                val alt = File(directory, "${name}.${System.currentTimeMillis()}.tmp")
                FileOutputStream(alt).use { output ->
                    context.assets.open(name).use { input -> input.copyTo(output) }
                    output.fd.sync()
                }
                onProgress(label)
                return alt
            }
            FileOutputStream(destination).use { output ->
                context.assets.open(name).use { input -> input.copyTo(output) }
                output.fd.sync()
            }
            onProgress(label)
            destination
        } catch (e: Throwable) { null }
    }

    private fun downloadArtifact(artifact: RemoteArtifact, destination: File, label: String, onProgress: (String) -> Unit): File {
        onProgress(context.getString(R.string.repo_downloading, label))
        val temporary = File(destination.parentFile, "${destination.name}.part")
        val connection = open(artifact.url)
        require(connection.contentLengthLong == -1L || connection.contentLengthLong == artifact.size) {
            context.getString(R.string.repo_size_mismatch, label)
        }
        var total = 0L
        connection.inputStream.use { input ->
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= artifact.size) { context.getString(R.string.repo_size_exceeded, label) }
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
        connection.disconnect()
        require(total == artifact.size) { context.getString(R.string.repo_incomplete, label) }
        if (destination.exists()) destination.delete()
        require(temporary.renameTo(destination)) { context.getString(R.string.repo_finalize_failed, label) }
        onProgress(context.getString(R.string.repo_verified, label))
        return destination
    }

    private fun resolveMainCommit(): String {
        val response = downloadBytes(COMMIT_API_URL, MAX_COMMIT_RESPONSE_BYTES)
        val commit = JSONObject(response.toString(Charsets.UTF_8)).getJSONObject("object").getString("sha")
        require(commit.matches(Regex("[0-9a-f]{40}"))) { context.getString(R.string.repo_commit_invalid) }
        return commit
    }

    private fun rawUrl(commit: String, path: String) = "$RAW_REPOSITORY@$commit/$path"

    private fun pinArtifactUrl(url: String, commit: String): String {
        require(url.startsWith(MUTABLE_RAW_PREFIX)) { context.getString(R.string.repo_url_invalid) }
        return "$RAW_REPOSITORY@$commit/${url.removePrefix(MUTABLE_RAW_PREFIX)}"
    }

    private fun downloadBytes(url: String, maximum: Int): ByteArray {
        val connection = open(url)
        val bytes = connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= maximum) { context.getString(R.string.repo_response_too_large) }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        connection.disconnect()
        return bytes
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 60_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "S25URoot/${BuildConfig.VERSION_NAME}")
        connect()
        require(responseCode == HttpURLConnection.HTTP_OK) { "HTTP $responseCode" }
    }

    companion object {
        private const val COMMIT_API_URL = "https://api.github.com/repos/IamNewHands/rmg-f731u/git/ref/heads/main"
        private const val RAW_REPOSITORY = "https://cdn.jsdelivr.net/gh/IamNewHands/rmg-f731u"
        private const val MUTABLE_RAW_PREFIX = "$RAW_REPOSITORY@main/"
        private const val MAX_COMMIT_RESPONSE_BYTES = 16 * 1024
        private const val MAX_MANIFEST_BYTES = 256 * 1024
        private const val F9460_PAYLOAD_SIZE = 131072
        private const val F731_PAYLOAD_MD5 = "3c82d4f678bd58846facf3e4ad356a33"
    }
}
