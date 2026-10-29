package com.koda.dev.core.terminal

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ShellEnvironmentInfo(
    val shellPath: String,
    val isGitAvailable: Boolean,
    val gitVersion: String?,
    val isBusyboxAvailable: Boolean,
    val busyboxVersion: String?,
    val nativeLibraryDir: String?,
    val architecture: String,
    val path: String,
    val isPRootAvailable: Boolean = false,
    val isAlpineInstalled: Boolean = false,
    val alpineVersion: String? = null,
    val isUbuntuInstalled: Boolean = false,
    val ubuntuVersion: String? = null,
    val environmentType: String = "Ubuntu 24.04 LTS (glibc)"
)

class NativeBinaryManager(
    private val context: Context? = null,
    val alpineBootstrapManager: AlpineBootstrapManager = AlpineBootstrapManager(context),
    val ubuntuBootstrapManager: UbuntuBootstrapManager = UbuntuBootstrapManager(context)
) {
    // Secondary constructors
    constructor() : this(null, AlpineBootstrapManager(null), UbuntuBootstrapManager(null))
    constructor(context: Context?, alpineBootstrapManager: AlpineBootstrapManager) : this(
        context,
        alpineBootstrapManager,
        UbuntuBootstrapManager(context)
    )

    var ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO

    val nativeLibraryDir: File?
        get() = try {
            context?.applicationInfo?.nativeLibraryDir?.let { File(it) }
        } catch (_: Exception) {
            null
        }

    val internalBinDir: File?
        get() = try {
            context?.filesDir?.let { File(it, "bin") }
        } catch (_: Exception) {
            null
        }

    fun isNativeGitBundled(): Boolean {
        val dir = nativeLibraryDir ?: return false
        return File(dir, "libgit.so").exists()
    }

    fun isNativeBusyboxBundled(): Boolean {
        val dir = nativeLibraryDir ?: return false
        return File(dir, "libbusybox.so").exists()
    }

    fun isPRootAvailable(): Boolean {
        val dir = nativeLibraryDir ?: return false
        val proot = File(dir, "libproot-xed.so")
        val loader = File(dir, "libproot.so")
        return proot.exists() && loader.exists()
    }

    fun resolvePRootExecutable(): String? {
        val dir = nativeLibraryDir ?: return null
        val proot = File(dir, "libproot-xed.so")
        return if (proot.exists() && proot.canExecute()) proot.absolutePath else null
    }

    fun isUbuntuReady(): Boolean {
        return isPRootAvailable() && ubuntuBootstrapManager.isInstalled()
    }

    fun isAlpineReady(): Boolean {
        return isPRootAvailable() && alpineBootstrapManager.isInstalled()
    }

    fun isLinuxReady(): Boolean {
        return isUbuntuReady() || isAlpineReady()
    }

    fun resolveGitExecutable(): String? {
        // 1. If Ubuntu is installed, git in Ubuntu is accessible inside container
        if (isUbuntuReady()) {
            val ubuntuGit = File(ubuntuBootstrapManager.ubuntuDir, "usr/bin/git")
            if (ubuntuGit.exists()) {
                return "/usr/bin/git"
            }
        }

        // 2. If Alpine is installed, git in Alpine is accessible inside container
        if (isAlpineReady()) {
            val alpineGit = File(alpineBootstrapManager.alpineDir, "usr/bin/git")
            if (alpineGit.exists()) {
                return "/usr/bin/git"
            }
        }

        // 2. Check nativeLibraryDir bundled binary
        val nativeDir = nativeLibraryDir
        if (nativeDir != null) {
            val libGit = File(nativeDir, "libgit.so")
            if (libGit.exists() && libGit.canExecute()) {
                return libGit.absolutePath
            }
        }

        // 3. Check internal files bin
        val binDir = internalBinDir
        if (binDir != null) {
            val binGit = File(binDir, "git")
            if (binGit.exists() && binGit.canExecute()) {
                return binGit.absolutePath
            }
        }

        // 4. Check system PATH
        val pathDirs = (System.getenv("PATH") ?: "/system/bin:/system/xbin:/bin:/usr/bin")
            .split(":")
            .filter { it.isNotBlank() }

        for (p in pathDirs) {
            val candidate = File(p, "git")
            if (candidate.exists() && candidate.canExecute()) {
                return candidate.absolutePath
            }
        }

        return null
    }

    fun resolveAapt2Binary(projectDir: File? = null): File? {
        // 1. Check workspace tools/bin/arm64-v8a/aapt2 if projectDir provided
        if (projectDir != null) {
            val workspaceAapt2 = File(projectDir, "tools/bin/arm64-v8a/aapt2")
            if (workspaceAapt2.exists()) {
                return workspaceAapt2
            }
        }

        // 2. Check internal files bin
        val internalAapt2 = internalBinDir?.let { File(it, "aapt2") }
        if (internalAapt2 != null && internalAapt2.exists()) {
            return internalAapt2
        }

        // 3. Check Ubuntu container /usr/local/bin/aapt2 or /usr/bin/aapt2
        if (isUbuntuReady()) {
            val localBin = File(ubuntuBootstrapManager.ubuntuDir, "usr/local/bin/aapt2")
            if (localBin.exists()) return localBin
            val usrBin = File(ubuntuBootstrapManager.ubuntuDir, "usr/bin/aapt2")
            if (usrBin.exists()) return usrBin
        }

        return null
    }

    fun resolveBusyboxExecutable(): String? {
        val nativeDir = nativeLibraryDir
        if (nativeDir != null) {
            val libBb = File(nativeDir, "libbusybox.so")
            if (libBb.exists() && libBb.canExecute()) {
                return libBb.absolutePath
            }
        }

        val candidates = listOf(
            "/system/xbin/busybox",
            "/system/bin/busybox",
            "/bin/busybox",
            "/usr/bin/busybox"
        )
        for (c in candidates) {
            val f = File(c)
            if (f.exists() && f.canExecute()) {
                return f.absolutePath
            }
        }
        return null
    }

    suspend fun inspectEnvironment(executor: TerminalExecutor): ShellEnvironmentInfo = withContext(ioDispatcher) {
        val ubuntuActive = isUbuntuReady()
        val ubuntuInstalled = ubuntuBootstrapManager.isInstalled()
        val alpineActive = isAlpineReady()
        val alpineInstalled = alpineBootstrapManager.isInstalled()
        val prootAvailable = isPRootAvailable()

        val alpineReleaseFile = File(alpineBootstrapManager.alpineDir, "etc/alpine-release")
        val alpineVersion = if (alpineInstalled && alpineReleaseFile.exists()) {
            try { alpineReleaseFile.readText().trim() } catch (_: Exception) { null }
        } else null

        val ubuntuIssueFile = File(ubuntuBootstrapManager.ubuntuDir, "etc/issue.net")
        val ubuntuVersion = if (ubuntuInstalled && ubuntuIssueFile.exists()) {
            try { ubuntuIssueFile.readText().trim() } catch (_: Exception) { "Ubuntu 24.04 LTS" }
        } else if (ubuntuInstalled) {
            "Ubuntu 24.04 LTS"
        } else null

        val shell = when {
            ubuntuActive -> "/bin/bash (Ubuntu 24.04 LTS)"
            ubuntuInstalled -> "/bin/bash (Ubuntu 24.04 LTS - ready)"
            alpineActive -> "/bin/sh (Alpine Linux)"
            else -> "/bin/bash (Ubuntu 24.04 LTS)"
        }

        val gitExec = resolveGitExecutable()
        val gitVersion = if (gitExec != null) {
            try {
                when (val res = executor.execute("git --version")) {
                    is TerminalResult.Success -> res.output.trim()
                    else -> null
                }
            } catch (_: Exception) { null }
        } else null

        val bbExec = resolveBusyboxExecutable()
        val bbVersion = if (bbExec != null) {
            try {
                when (val res = executor.execute("busybox --help")) {
                    is TerminalResult.Success -> res.output.lineSequence().firstOrNull()?.trim()
                    else -> null
                }
            } catch (_: Exception) { null }
        } else null

        val arch = try {
            Build.SUPPORTED_ABIS?.firstOrNull() ?: System.getProperty("os.arch") ?: "unknown"
        } catch (_: Exception) {
            System.getProperty("os.arch") ?: "unknown"
        }

        val envType = when {
            ubuntuActive -> "Ubuntu 24.04 LTS (PRoot glibc Container)"
            ubuntuInstalled -> "Ubuntu 24.04 LTS (Installed, awaiting PRoot initialization)"
            alpineActive -> "Alpine Linux ${alpineVersion ?: "3.21"} (PRoot Container)"
            alpineInstalled -> "Alpine Linux (Installed, awaiting PRoot initialization)"
            else -> "Ubuntu 24.04 LTS (Pending Setup)"
        }

        ShellEnvironmentInfo(
            shellPath = shell,
            isGitAvailable = gitExec != null || isNativeGitBundled(),
            gitVersion = gitVersion,
            isBusyboxAvailable = bbExec != null || isNativeBusyboxBundled(),
            busyboxVersion = bbVersion,
            nativeLibraryDir = nativeLibraryDir?.absolutePath,
            architecture = arch,
            path = System.getenv("PATH") ?: "",
            isPRootAvailable = prootAvailable,
            isAlpineInstalled = alpineInstalled,
            alpineVersion = alpineVersion,
            isUbuntuInstalled = ubuntuInstalled,
            ubuntuVersion = ubuntuVersion,
            environmentType = envType
        )
    }
}
