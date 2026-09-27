package com.codeagent.core.terminal

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AlpineBootstrapState {
    data object NotInstalled : AlpineBootstrapState
    data class InProgress(val stage: String, val progress: Float = -1f, val message: String = "") : AlpineBootstrapState
    data class Ready(val alpineDir: File) : AlpineBootstrapState
    data class Error(val message: String) : AlpineBootstrapState
}

data class AlpineArchInfo(
    val detectedAbi: String,
    val assetDir: String,
    val alpineArch: String,
    val filename: String,
    val hasLibproot32: Boolean
)

class AlpineBootstrapManager(
    private val context: Context? = null
) {
    // Secondary constructor for JVM tests or standalone usage
    private var customBaseDir: File? = null
    constructor(baseDir: File) : this(null) {
        this.customBaseDir = baseDir
    }

    var ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO

    val rootDir: File
        get() = customBaseDir
            ?: context?.filesDir
            ?: File(System.getProperty("user.dir") ?: ".", "terminal_root")

    val alpineDir: File
        get() = File(rootDir, "alpine")

    val tmpDir: File
        get() = File(rootDir, "tmp")

    val publicDir: File
        get() = File(rootDir, "public")

    val configuredMarker: File
        get() = File(rootDir, ".configured")

    val nativeLibraryDir: File?
        get() = try {
            context?.applicationInfo?.nativeLibraryDir?.let { File(it) }
        } catch (_: Exception) {
            null
        }

    private val _bootstrapState = MutableStateFlow<AlpineBootstrapState>(
        if (isInstalled()) AlpineBootstrapState.Ready(alpineDir) else AlpineBootstrapState.NotInstalled
    )
    val bootstrapState: StateFlow<AlpineBootstrapState> = _bootstrapState.asStateFlow()

    fun isInstalled(): Boolean {
        val root = rootDir
        val marker = File(root, ".configured")
        val alpine = File(root, "alpine")
        val sh = File(alpine, "bin/sh")
        return marker.exists() && alpine.isDirectory && (sh.exists() || File(alpine, "bin/busybox").exists())
    }

    fun resolveArchitecture(): AlpineArchInfo {
        val primaryAbi = try {
            Build.SUPPORTED_ABIS?.firstOrNull()
        } catch (_: Throwable) {
            null
        } ?: System.getProperty("os.arch") ?: "aarch64"

        val lower = primaryAbi.lowercase()
        return when {
            lower.contains("arm64") || lower.contains("aarch64") -> AlpineArchInfo(
                detectedAbi = "arm64-v8a",
                assetDir = "arm64",
                alpineArch = "aarch64",
                filename = "alpine-minirootfs-3.21.0-aarch64.tar.gz",
                hasLibproot32 = true
            )
            lower.contains("v7") || lower.contains("arm") -> AlpineArchInfo(
                detectedAbi = "armeabi-v7a",
                assetDir = "arm32",
                alpineArch = "armhf",
                filename = "alpine-minirootfs-3.21.0-armhf.tar.gz",
                hasLibproot32 = false
            )
            lower.contains("x86_64") || lower.contains("amd64") -> AlpineArchInfo(
                detectedAbi = "x86_64",
                assetDir = "x64",
                alpineArch = "x86_64",
                filename = "alpine-minirootfs-3.21.0-x86_64.tar.gz",
                hasLibproot32 = true
            )
            else -> AlpineArchInfo(
                detectedAbi = primaryAbi,
                assetDir = "arm64",
                alpineArch = "aarch64",
                filename = "alpine-minirootfs-3.21.0-aarch64.tar.gz",
                hasLibproot32 = true
            )
        }
    }

    fun isSupported(): Boolean {
        val arch = resolveArchitecture()
        return arch.detectedAbi in listOf("arm64-v8a", "armeabi-v7a", "x86_64")
    }

    suspend fun bootstrap(
        forceReinstall: Boolean = false,
        onProgress: ((String, Float) -> Unit)? = null
    ): Result<File> = withContext(ioDispatcher) {
        if (!forceReinstall && isInstalled()) {
            val ready = AlpineBootstrapState.Ready(alpineDir)
            _bootstrapState.value = ready
            return@withContext Result.success(alpineDir)
        }

        try {
            val arch = resolveArchitecture()
            updateState("Setting up filesystem directories...", 0.05f, onProgress)

            val root = rootDir
            val targetAlpine = alpineDir
            val targetTmp = tmpDir
            val targetPublic = publicDir

            root.mkdirs()
            targetTmp.mkdirs()
            targetPublic.mkdirs()

            if (forceReinstall && targetAlpine.exists()) {
                targetAlpine.deleteRecursively()
                configuredMarker.delete()
            }
            targetAlpine.mkdirs()
            File(targetAlpine, "tmp").mkdirs()

            setupSupportingLibraries(root)

            updateState("Loading Alpine Linux rootfs...", 0.15f, onProgress)
            val inputStream = getRootfsInputStream(arch, onProgress)

            updateState("Extracting Alpine Linux packages...", 0.35f, onProgress)
            extractTarGz(inputStream, targetAlpine) { extractedCount, totalHint ->
                val fraction = if (totalHint > 0) {
                    0.35f + (0.50f * (extractedCount.toFloat() / totalHint)).coerceAtMost(0.50f)
                } else 0.50f
                updateState("Extracting packages ($extractedCount files)...", fraction, onProgress)
            }

            updateState("Configuring network & package repositories...", 0.90f, onProgress)
            configureAlpineEnvironment(targetAlpine, root)

            configuredMarker.writeText("configured\n")
            val readyState = AlpineBootstrapState.Ready(targetAlpine)
            _bootstrapState.value = readyState
            updateState("Alpine Linux environment ready!", 1.0f, onProgress)
            Result.success(targetAlpine)
        } catch (e: Exception) {
            val errMsg = "Failed to bootstrap Alpine Linux: ${e.message}"
            _bootstrapState.value = AlpineBootstrapState.Error(errMsg)
            Result.failure(e)
        }
    }

    private fun setupSupportingLibraries(root: File) {
        val nativeDir = nativeLibraryDir ?: return
        val tallocTarget = File(nativeDir, "libtalloc.so")
        if (tallocTarget.exists()) {
            val tallocLink = File(root, "libtalloc.so.2")
            if (tallocLink.exists() || Files.isSymbolicLink(tallocLink.toPath())) {
                tallocLink.delete()
            }
            try {
                Files.createSymbolicLink(tallocLink.toPath(), tallocTarget.toPath())
            } catch (_: Exception) {
                try {
                    tallocTarget.copyTo(tallocLink, overwrite = true)
                } catch (_: Exception) {}
            }
        }
    }

    private fun getRootfsInputStream(
        arch: AlpineArchInfo,
        onProgress: ((String, Float) -> Unit)?
    ): InputStream {
        // 1. Try bundled asset first
        val assetPath = "alpine_assets/${arch.assetDir}/alpine.rootfs"
        val assetStream = try {
            context?.assets?.open(assetPath)
        } catch (_: Exception) {
            null
        }

        if (assetStream != null) {
            return assetStream
        }

        // 2. Download from official Alpine CDN mirror
        val downloadUrl = "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/${arch.alpineArch}/${arch.filename}"
        updateState("Downloading rootfs from Alpine CDN...", 0.20f, onProgress)
        val tempTarGz = File(rootDir, "alpine.tar.gz")
        val conn = URL(downloadUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true

        val contentLength = conn.contentLengthLong
        conn.inputStream.use { input ->
            FileOutputStream(tempTarGz).use { output ->
                val buffer = ByteArray(16 * 1024)
                var bytesRead: Int
                var totalRead = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (contentLength > 0) {
                        val progress = 0.20f + (0.15f * (totalRead.toFloat() / contentLength))
                        updateState("Downloading rootfs (${totalRead / 1024} KB / ${contentLength / 1024} KB)...", progress, onProgress)
                    }
                }
            }
        }
        return tempTarGz.inputStream()
    }

    fun extractTarGz(
        inputStream: InputStream,
        targetDir: File,
        onFileExtracted: ((Int, Int) -> Unit)? = null
    ) {
        val rootCanonical = targetDir.canonicalPath
        val bufferedIn = BufferedInputStream(inputStream)
        val gzipIn = GZIPInputStream(bufferedIn)
        val tarIn = TarArchiveInputStream(gzipIn)

        var count = 0
        var entry: TarArchiveEntry? = tarIn.nextEntry
        while (entry != null) {
            val destFile = File(targetDir, entry.name)
            val destCanonical = destFile.canonicalPath
            // Zip-slip security guard
            if (!destCanonical.startsWith(rootCanonical)) {
                entry = tarIn.nextEntry
                continue
            }

            when {
                entry.isDirectory -> {
                    destFile.mkdirs()
                }
                entry.isSymbolicLink -> {
                    destFile.parentFile?.mkdirs()
                    if (destFile.exists() || Files.isSymbolicLink(destFile.toPath())) {
                        destFile.delete()
                    }
                    try {
                        Files.createSymbolicLink(destFile.toPath(), Paths.get(entry.linkName))
                    } catch (_: Exception) {
                        // Some non-symlink supporting filesystems fallback
                    }
                }
                entry.isFile -> {
                    destFile.parentFile?.mkdirs()
                    FileOutputStream(destFile).use { fos ->
                        tarIn.copyTo(fos)
                    }
                    // Preserve executable permissions
                    val isExecutable = (entry.mode and 0b001_001_001) != 0 ||
                            entry.name.contains("/bin/") ||
                            entry.name.contains("/sbin/") ||
                            entry.name.startsWith("bin/") ||
                            entry.name.startsWith("sbin/")
                    if (isExecutable) {
                        destFile.setExecutable(true, false)
                    }
                }
            }
            count++
            if (count % 50 == 0) {
                onFileExtracted?.invoke(count, 1200)
            }
            entry = tarIn.nextEntry
        }
        onFileExtracted?.invoke(count, count)
    }

    private fun configureAlpineEnvironment(alpine: File, root: File) {
        // 1. DNS resolv.conf
        val etcDir = File(alpine, "etc")
        etcDir.mkdirs()
        File(etcDir, "resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")

        // 2. APK Repositories
        val apkDir = File(etcDir, "apk")
        apkDir.mkdirs()
        File(apkDir, "repositories").writeText(
            "https://dl-cdn.alpinelinux.org/alpine/v3.21/main\n" +
            "https://dl-cdn.alpinelinux.org/alpine/v3.21/community\n"
        )

        // 3. Linker config
        val linkerDir = File(alpine, "linkerconfig")
        linkerDir.mkdirs()
        File(linkerDir, "ld.config.txt").apply {
            if (!exists()) createNewFile()
        }

        // 4. rm wrapper for --link2symlink
        val rmWrapperContent = try {
            context?.assets?.open("alpine_assets/rm-wrapper.sh")?.bufferedReader()?.use { it.readText() }
        } catch (_: Exception) { null } ?: """
            #!/bin/sh
            unlink_recursive() {
                path="${'$'}1"
                for entry in "${'$'}path"/* "${'$'}path"/.[!.]* "${'$'}path"/..?*; do
                    case "${'$'}entry" in
                        *'*'*|*'?'*) continue ;;
                    esac
                    unlink_recursive "${'$'}entry"
                done 2>/dev/null
                unlink "${'$'}path" 2>/dev/null || :
            }
            for target in "${'$'}@"; do
                unlink_recursive "${'$'}target"
            done
            err="${'$'}(busybox rm "${'$'}@" 2>&1 >/dev/null)"
            printf "%s\n" "${'$'}err" | grep -v "No such file or directory"
        """.trimIndent()

        val binRm = File(alpine, "bin/rm")
        if (binRm.exists() || Files.isSymbolicLink(binRm.toPath())) {
            binRm.delete()
        }
        binRm.writeText(rmWrapperContent)
        binRm.setExecutable(true, false)

        // 5. MOTD
        File(etcDir, "codeagent_motd").writeText(
            """
            Welcome to Alpine Linux inside CodeAgent!
            
            Package Manager:
              • apk update            - Refresh package indexes
              • apk add <pkg>         - Install tools (git, python3, gcc, g++, make, nodejs...)
              • apk search <term>     - Search packages
              • apk del <pkg>         - Remove package
            
            Mounted storage: /sdcard, /storage
            Home directory:  /root (/public)
            """.trimIndent()
        )

        // 6. Profile / bashrc
        val profile = File(etcDir, "profile")
        if (!profile.exists() || !profile.readText().contains("codeagent_motd")) {
            profile.appendText("\n[ -f /etc/codeagent_motd ] && cat /etc/codeagent_motd\n")
        }

        val bashrc = File(root, "public/.bashrc")
        if (!bashrc.exists()) {
            File(root, "public").mkdirs()
            bashrc.writeText(
                """
                export PATH=/usr/local/bin:/usr/bin:/bin:/usr/local/sbin:/usr/sbin:/sbin
                export TERM=xterm-256color
                export HOME=/root
                export PS1='\[\033[01;32m\]codeagent\[\033[00m\]:\[\033[01;34m\]\w\[\033[00m\]\$ '
                """.trimIndent()
            )
        }
    }

    private fun updateState(
        stage: String,
        progress: Float,
        onProgress: ((String, Float) -> Unit)?
    ) {
        _bootstrapState.value = AlpineBootstrapState.InProgress(stage, progress, stage)
        onProgress?.invoke(stage, progress)
    }
}
