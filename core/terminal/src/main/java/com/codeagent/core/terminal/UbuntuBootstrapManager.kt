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

sealed interface UbuntuBootstrapState {
    data object NotInstalled : UbuntuBootstrapState
    data class InProgress(val stage: String, val progress: Float = -1f, val message: String = "") : UbuntuBootstrapState
    data class Ready(val ubuntuDir: File) : UbuntuBootstrapState
    data class Error(val message: String) : UbuntuBootstrapState
}

data class UbuntuArchInfo(
    val detectedAbi: String,
    val ubuntuArch: String,
    val filename: String,
    val repoUrl: String
)

class UbuntuBootstrapManager(
    private val context: Context? = null
) {
    // Secondary constructor for JVM unit tests or standalone usage
    private var customBaseDir: File? = null
    constructor(baseDir: File) : this(null) {
        this.customBaseDir = baseDir
    }

    var ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO

    val rootDir: File
        get() = customBaseDir
            ?: context?.filesDir
            ?: File(System.getProperty("user.dir") ?: ".", "terminal_root")

    val ubuntuDir: File
        get() = File(rootDir, "ubuntu")

    val tmpDir: File
        get() = File(rootDir, "tmp")

    val publicDir: File
        get() = File(rootDir, "public")

    val configuredMarker: File
        get() = File(rootDir, ".ubuntu_configured")

    var customNativeLibraryDir: File? = null

    val nativeLibraryDir: File?
        get() = customNativeLibraryDir ?: try {
            context?.applicationInfo?.nativeLibraryDir?.let { File(it) }
        } catch (_: Exception) {
            null
        }

    private val _bootstrapState = MutableStateFlow<UbuntuBootstrapState>(
        if (isInstalled()) UbuntuBootstrapState.Ready(ubuntuDir) else UbuntuBootstrapState.NotInstalled
    )
    val bootstrapState: StateFlow<UbuntuBootstrapState> = _bootstrapState.asStateFlow()

    fun isInstalled(): Boolean {
        val root = rootDir
        val marker = File(root, ".ubuntu_configured")
        val ubuntu = File(root, "ubuntu")
        val bash = File(ubuntu, "bin/bash")
        val usrBash = File(ubuntu, "usr/bin/bash")
        val sh = File(ubuntu, "bin/sh")
        return marker.exists() && ubuntu.isDirectory && (bash.exists() || usrBash.exists() || sh.exists())
    }

    fun resolveArchitecture(): UbuntuArchInfo {
        val primaryAbi = try {
            Build.SUPPORTED_ABIS?.firstOrNull()
        } catch (_: Throwable) {
            null
        } ?: System.getProperty("os.arch") ?: "aarch64"

        val lower = primaryAbi.lowercase()
        return when {
            lower.contains("arm64") || lower.contains("aarch64") -> UbuntuArchInfo(
                detectedAbi = "arm64-v8a",
                ubuntuArch = "arm64",
                filename = "ubuntu-base-24.04-base-arm64.tar.gz",
                repoUrl = "http://ports.ubuntu.com/ubuntu-ports/"
            )
            lower.contains("v7") || lower.contains("arm") -> UbuntuArchInfo(
                detectedAbi = "armeabi-v7a",
                ubuntuArch = "armhf",
                filename = "ubuntu-base-24.04-base-armhf.tar.gz",
                repoUrl = "http://ports.ubuntu.com/ubuntu-ports/"
            )
            lower.contains("x86_64") || lower.contains("amd64") -> UbuntuArchInfo(
                detectedAbi = "x86_64",
                ubuntuArch = "amd64",
                filename = "ubuntu-base-24.04-base-amd64.tar.gz",
                repoUrl = "http://archive.ubuntu.com/ubuntu/"
            )
            else -> UbuntuArchInfo(
                detectedAbi = primaryAbi,
                ubuntuArch = "arm64",
                filename = "ubuntu-base-24.04-base-arm64.tar.gz",
                repoUrl = "http://ports.ubuntu.com/ubuntu-ports/"
            )
        }
    }

    suspend fun bootstrap(
        forceReinstall: Boolean = false,
        onProgress: ((String, Float) -> Unit)? = null
    ): Result<File> = withContext(ioDispatcher) {
        if (!forceReinstall && isInstalled()) {
            setupSupportingLibraries(rootDir)
            ensureGitConfigured()
            val ready = UbuntuBootstrapState.Ready(ubuntuDir)
            _bootstrapState.value = ready
            return@withContext Result.success(ubuntuDir)
        }

        try {
            val arch = resolveArchitecture()
            updateState("Setting up filesystem directories...", 0.05f, onProgress)

            val root = rootDir
            val targetUbuntu = ubuntuDir
            val targetTmp = tmpDir
            val targetPublic = publicDir

            root.mkdirs()
            targetTmp.mkdirs()
            targetPublic.mkdirs()

            if (forceReinstall && targetUbuntu.exists()) {
                targetUbuntu.deleteRecursively()
                configuredMarker.delete()
            }
            targetUbuntu.mkdirs()
            File(targetUbuntu, "tmp").mkdirs()
            File(targetUbuntu, "root").mkdirs()
            File(targetUbuntu, "home").mkdirs()

            setupSupportingLibraries(root)

            updateState("Loading Ubuntu Linux rootfs...", 0.15f, onProgress)
            val inputStream = getRootfsInputStream(arch, onProgress)

            updateState("Extracting Ubuntu Linux packages...", 0.35f, onProgress)
            extractTarGz(inputStream, targetUbuntu) { extractedCount, totalHint ->
                val fraction = if (totalHint > 0) {
                    0.35f + (0.50f * (extractedCount.toFloat() / totalHint)).coerceAtMost(0.50f)
                } else 0.50f
                updateState("Extracting packages ($extractedCount files)...", fraction, onProgress)
            }

            updateState("Configuring Ubuntu environment & APT...", 0.90f, onProgress)
            configureUbuntuEnvironment(targetUbuntu, arch)

            configuredMarker.writeText("configured\n")
            val readyState = UbuntuBootstrapState.Ready(targetUbuntu)
            _bootstrapState.value = readyState
            updateState("Ubuntu 24.04 LTS environment ready!", 1.0f, onProgress)
            Result.success(targetUbuntu)
        } catch (e: Exception) {
            val errMsg = "Failed to bootstrap Ubuntu Linux: ${e.message}"
            _bootstrapState.value = UbuntuBootstrapState.Error(errMsg)
            Result.failure(e)
        }
    }

    fun setupSupportingLibraries(root: File = rootDir) {
        val nativeDir = nativeLibraryDir ?: return
        val tallocTarget = File(nativeDir, "libtalloc.so")
        if (tallocTarget.exists()) {
            for (linkName in listOf("libtalloc.so.2", "libtalloc.so")) {
                val destFile = File(root, linkName)
                if (destFile.exists() || Files.isSymbolicLink(destFile.toPath())) {
                    destFile.delete()
                }
                try {
                    Files.createSymbolicLink(destFile.toPath(), tallocTarget.toPath())
                } catch (_: Exception) {
                    try {
                        tallocTarget.copyTo(destFile, overwrite = true)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun getRootfsInputStream(
        arch: UbuntuArchInfo,
        onProgress: ((String, Float) -> Unit)?
    ): InputStream {
        // 1. Try local archive in rootDir if already present (e.g. pre-cached or in test environments)
        val localArchive = File(rootDir, "ubuntu.tar.gz")
        if (localArchive.exists()) {
            return localArchive.inputStream()
        }

        // 2. Try bundled asset
        val assetPath = "ubuntu_assets/ubuntu.rootfs"
        val assetStream = try {
            context?.assets?.open(assetPath)
        } catch (_: Exception) {
            null
        }

        if (assetStream != null) {
            return assetStream
        }

        // 3. Download from official Canonical Ubuntu CDImage releases
        val downloadCandidates = listOf(
            "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/${arch.filename}",
            "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04.1/release/${arch.filename.replace("24.04-base", "24.04.1-base")}",
            "https://cdimage.ubuntu.com/ubuntu-base/releases/22.04/release/ubuntu-base-22.04-base-${arch.ubuntuArch}.tar.gz"
        )

        updateState("Downloading Ubuntu 24.04 LTS rootfs from Canonical...", 0.20f, onProgress)
        val tempTarGz = File(rootDir, "ubuntu.tar.gz")

        var lastException: Exception? = null
        for (urlStr in downloadCandidates) {
            try {
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.connectTimeout = 8_000
                conn.readTimeout = 15_000
                conn.instanceFollowRedirects = true

                if (conn.responseCode in 200..299) {
                    val contentLength = conn.contentLengthLong
                    conn.inputStream.use { input ->
                        FileOutputStream(tempTarGz).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var bytesRead: Int
                            var totalRead = 0L

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                if (contentLength > 0) {
                                    val progress = 0.20f + (0.15f * (totalRead.toFloat() / contentLength)).coerceAtMost(0.15f)
                                    val mb = totalRead / (1024 * 1024)
                                    val totalMb = contentLength / (1024 * 1024)
                                    updateState("Downloading Ubuntu rootfs ($mb/$totalMb MB)...", progress, onProgress)
                                }
                            }
                        }
                    }
                    return tempTarGz.inputStream()
                } else {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        throw lastException ?: IllegalStateException("Could not download Ubuntu rootfs from Canonical mirrors.")
    }

    fun extractTarGz(
        inputStream: InputStream,
        targetDir: File,
        onFileExtracted: ((Int, Int) -> Unit)? = null
    ) {
        targetDir.mkdirs()
        val gzipIn = GZIPInputStream(BufferedInputStream(inputStream))
        val tarIn = TarArchiveInputStream(gzipIn)

        val rootCanonical = targetDir.canonicalPath
        var entry: TarArchiveEntry? = tarIn.nextEntry
        var count = 0

        while (entry != null) {
            val destFile = File(targetDir, entry.name)
            val destCanonical = try { destFile.canonicalPath } catch (_: Exception) { destFile.absolutePath }

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
                        // Some filesystems without symlink support fallback
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
            if (count % 100 == 0) {
                onFileExtracted?.invoke(count, 3500)
            }
            entry = tarIn.nextEntry
        }
        onFileExtracted?.invoke(count, count)
    }

    fun configureUbuntuEnvironment(ubuntu: File, arch: UbuntuArchInfo) {
        val etcDir = File(ubuntu, "etc")
        etcDir.mkdirs()

        // 1. DNS resolv.conf
        File(etcDir, "resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")

        // 2. /etc/hosts
        File(etcDir, "hosts").writeText("127.0.0.1 localhost\n::1 localhost\n")

        // 3. Headless policy-rc.d (blocks background daemon startup failures during apt install)
        val sbinDir = File(ubuntu, "usr/sbin")
        sbinDir.mkdirs()
        val policyRc = File(sbinDir, "policy-rc.d")
        policyRc.writeText("#!/bin/sh\nexit 101\n")
        policyRc.setExecutable(true, false)

        // 4. APT configuration for non-interactive headless mobile container
        val aptConfDir = File(etcDir, "apt/apt.conf.d")
        aptConfDir.mkdirs()
        File(aptConfDir, "99codeagent").writeText(
            """
            APT::Install-Recommends "false";
            APT::Install-Suggests "false";
            APT::Clean-Installed "true";
            Acquire::Languages "none";
            DPkg::Options {
               "--force-confdef";
               "--force-confold";
            };
            """.trimIndent() + "\n"
        )

        // 5. APT Sources List for Ubuntu 24.04 (Noble)
        val sourcesList = File(etcDir, "apt/sources.list")
        val repo = arch.repoUrl.trimEnd('/')
        sourcesList.writeText(
            """
            deb $repo noble main restricted universe multiverse
            deb $repo noble-updates main restricted universe multiverse
            deb $repo noble-security main restricted universe multiverse
            """.trimIndent() + "\n"
        )

        // 6. Root .bashrc profile
        val rootHome = File(ubuntu, "root")
        rootHome.mkdirs()
        val bashrc = File(rootHome, ".bashrc")
        bashrc.writeText(
            """
            # CodeAgent Ubuntu 24.04 LTS (Noble) Environment
            export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            export DEBIAN_FRONTEND="noninteractive"
            export LANG="C.UTF-8"
            export LC_ALL="C.UTF-8"
            export HOME="/root"
            export TERM="xterm-256color"
            export PS1='\[\033[01;32m\]codeagent@ubuntu\[\033[00m\]:\[\033[01;34m\]\w\[\033[00m\]\$ '
            
            alias ll='ls -alF'
            alias la='ls -A'
            alias l='ls -CF'
            alias apt-update='apt-get update'
            alias apt-install='DEBIAN_FRONTEND=noninteractive apt-get install -y'
            """.trimIndent() + "\n"
        )

        // Ensure /tmp has wide permissions
        val tmpDir = File(ubuntu, "tmp")
        tmpDir.mkdirs()
        tmpDir.setReadable(true, false)
        tmpDir.setWritable(true, false)
        tmpDir.setExecutable(true, false)
    }

    fun ensureGitConfigured() {
        val rootHome = File(ubuntuDir, "root")
        if (!rootHome.exists()) rootHome.mkdirs()
        val gitConfig = File(rootHome, ".gitconfig")
        if (!gitConfig.exists()) {
            gitConfig.writeText(
                """
                [safe]
                	directory = *
                [core]
                	filemode = false
                	symlinks = false
                """.trimIndent() + "\n"
            )
        }
    }

    private fun updateState(
        stage: String,
        progress: Float = -1f,
        onProgress: ((String, Float) -> Unit)? = null
    ) {
        _bootstrapState.value = UbuntuBootstrapState.InProgress(stage, progress)
        onProgress?.invoke(stage, progress)
    }
}
