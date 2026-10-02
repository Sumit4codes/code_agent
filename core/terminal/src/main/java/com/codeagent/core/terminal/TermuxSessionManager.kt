package com.codeagent.core.terminal

import android.content.Context
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TermuxSessionManager @Inject constructor(
    @ApplicationContext private val context: Context? = null,
    val nativeBinaryManager: NativeBinaryManager? = null,
    val alpineBootstrapManager: AlpineBootstrapManager? = null
) {
    // Secondary constructor for JVM tests without Android context
    constructor() : this(null, null, null)

    fun createSession(
        workingDir: File?,
        client: TerminalSessionClient
    ): TerminalSession {
        val rootDir = alpineBootstrapManager?.rootDir
            ?: context?.filesDir
            ?: File(".")
        val effectiveDir = workingDir ?: rootDir
        val isAlpine = nativeBinaryManager?.isAlpineReady() == true

        val executable: String
        val args: Array<String>
        val env: Array<String>

        if (isAlpine) {
            val prootExec = nativeBinaryManager?.resolvePRootExecutable() ?: "/system/bin/sh"
            val nativeDir = try { context?.applicationInfo?.nativeLibraryDir?.let { File(it) } } catch (_: Exception) { null }
            val alpineDir = alpineBootstrapManager?.alpineDir ?: File(rootDir, "alpine")
            val tmpDir = alpineBootstrapManager?.tmpDir ?: File(rootDir, "tmp")
            val publicDir = alpineBootstrapManager?.publicDir ?: File(rootDir, "public")

            alpineBootstrapManager?.setupSupportingLibraries()
            alpineBootstrapManager?.ensureGitConfigured()

            val argList = mutableListOf<String>()
            argList.add("--kill-on-exit")
            argList.add("-r")
            argList.add(alpineDir.absolutePath)
            argList.add("-0")
            argList.add("--link2symlink")
            argList.add("--sysvipc")
            argList.add("-L")

            val boundSources = mutableListOf<String>()
            fun addBind(src: String, dst: String? = null) {
                val f = File(src)
                if (f.exists()) {
                    argList.add("-b")
                    if (dst != null) argList.add("$src:$dst") else argList.add(src)
                    boundSources.add(f.absolutePath)
                    try { boundSources.add(f.canonicalPath) } catch (_: Exception) {}
                }
            }

            for (m in listOf("/dev", "/proc", "/sys")) addBind(m)
            if (File("/dev/urandom").exists()) {
                argList.add("-b")
                argList.add("/dev/urandom:/dev/random")
            }
            for (st in listOf("/sdcard", "/storage", "/mnt/sdcard")) addBind(st)
            if (nativeDir != null && nativeDir.exists()) addBind(nativeDir.absolutePath)
            addBind(rootDir.absolutePath)
            argList.add("-b")
            argList.add("${publicDir.absolutePath}:/root")
            argList.add("-b")
            argList.add("${publicDir.absolutePath}:/home")
            boundSources.add(publicDir.absolutePath)

            val shm = File(alpineDir, "tmp")
            shm.mkdirs()
            argList.add("-b")
            argList.add("${shm.absolutePath}:/dev/shm")

            val hostWorkDir = effectiveDir.absolutePath
            val workCanon = try { effectiveDir.canonicalPath } catch (_: Exception) { hostWorkDir }
            val isAlreadyBound = boundSources.any { boundPath ->
                hostWorkDir == boundPath || hostWorkDir.startsWith("$boundPath/") ||
                    workCanon == boundPath || workCanon.startsWith("$boundPath/")
            }

            if (effectiveDir.exists()) {
                if (!isAlreadyBound) {
                    argList.add("-b")
                    argList.add(hostWorkDir)
                }
                argList.add("-w")
                argList.add(hostWorkDir)
            } else {
                argList.add("-w")
                argList.add("/root")
            }

            // Interactive login shell inside Alpine Linux
            argList.add("/bin/sh")
            argList.add("-l")

            executable = prootExec
            args = argList.toTypedArray()

            val envList = mutableListOf(
                "TERM=xterm-256color",
                "COLORTERM=truecolor",
                "PATH=/usr/local/bin:/usr/bin:/bin:/usr/local/sbin:/usr/sbin:/sbin",
                "HOME=/root",
                "USER=root",
                "SHELL=/bin/sh",
                "LANG=C.UTF-8",
                "LC_ALL=C.UTF-8",
                "PROOT_TMP_DIR=${tmpDir.absolutePath}",
                "GIT_CONFIG_PARAMETERS='core.createObject=rename' 'core.filemode=false' 'core.symlinks=false' 'safe.directory=*'",
                "GIT_OPTIONAL_LOCKS=0"
            )

            if (nativeDir != null) {
                envList.add("PROOT_LOADER=${File(nativeDir, "libproot.so").absolutePath}")
                val loader32 = File(nativeDir, "libproot32.so")
                if (loader32.exists()) {
                    envList.add("PROOT_LOADER32=${loader32.absolutePath}")
                }
                envList.add("LD_LIBRARY_PATH=${rootDir.absolutePath}:${nativeDir.absolutePath}")
            } else {
                envList.add("LD_LIBRARY_PATH=${rootDir.absolutePath}")
            }

            env = envList.toTypedArray()
        } else {
            val shell = when {
                File("/system/bin/sh").exists() -> "/system/bin/sh"
                File("/bin/sh").exists() -> "/bin/sh"
                else -> "sh"
            }
            executable = shell
            args = arrayOf("-i")

            val nativeDir = try { context?.applicationInfo?.nativeLibraryDir } catch (_: Exception) { null }
            val pathEntries = mutableListOf<String>()
            if (!nativeDir.isNullOrBlank()) pathEntries.add(nativeDir)
            val filesBin = try { context?.filesDir?.let { File(it, "bin").absolutePath } } catch (_: Exception) { null }
            if (!filesBin.isNullOrBlank()) pathEntries.add(filesBin)
            val systemPath = System.getenv("PATH") ?: "/system/bin:/system/xbin:/bin:/usr/bin"
            pathEntries.add(systemPath)
            for (stdPath in listOf("/system/bin", "/system/xbin", "/vendor/bin", "/apex/com.android.runtime/bin")) {
                if (!pathEntries.any { it.contains(stdPath) }) pathEntries.add(stdPath)
            }

            env = arrayOf(
                "TERM=xterm-256color",
                "COLORTERM=truecolor",
                "PATH=${pathEntries.joinToString(":")}",
                "HOME=${effectiveDir.absolutePath}",
                "PWD=${effectiveDir.absolutePath}",
                "SHELL=$shell",
                "LANG=C.UTF-8",
                "LC_ALL=C.UTF-8"
            )
        }

        return TerminalSession(
            executable,
            effectiveDir.absolutePath,
            args,
            env,
            2000,
            client
        )
    }
}
