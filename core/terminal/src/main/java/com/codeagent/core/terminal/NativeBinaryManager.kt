package com.codeagent.core.terminal

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
    val path: String
)

@Singleton
class NativeBinaryManager @Inject constructor(
    @ApplicationContext private val context: Context? = null
) {
    // Secondary constructor for JVM tests
    constructor() : this(null)

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

    fun resolveGitExecutable(): String? {
        // 1. Check nativeLibraryDir bundled binary
        val nativeDir = nativeLibraryDir
        if (nativeDir != null) {
            val libGit = File(nativeDir, "libgit.so")
            if (libGit.exists() && libGit.canExecute()) {
                return libGit.absolutePath
            }
        }

        // 2. Check internal files bin
        val binDir = internalBinDir
        if (binDir != null) {
            val binGit = File(binDir, "git")
            if (binGit.exists() && binGit.canExecute()) {
                return binGit.absolutePath
            }
        }

        // 3. Check system PATH
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
            "/system/bin/toybox",
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
        val shell = when {
            File("/system/bin/sh").exists() -> "/system/bin/sh"
            File("/bin/sh").exists() -> "/bin/sh"
            else -> "sh"
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

        ShellEnvironmentInfo(
            shellPath = shell,
            isGitAvailable = gitExec != null || isNativeGitBundled(),
            gitVersion = gitVersion,
            isBusyboxAvailable = bbExec != null || isNativeBusyboxBundled(),
            busyboxVersion = bbVersion,
            nativeLibraryDir = nativeLibraryDir?.absolutePath,
            architecture = arch,
            path = System.getenv("PATH") ?: ""
        )
    }
}
