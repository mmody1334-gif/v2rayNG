package com.v2ray.ang.shroudly

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.security.SecureRandom

/*
 * From Shroudly (https://github.com/umutxyp/Shroudly) - MIT License.
 *
 * Random credentials written to a private file with 0600 mode for SOCKS5 auth.
 */
class SocksAuth(val user: String, val pass: String) {
    companion object {
        const val OPTION = "--auth-file"
        private val TOKEN = Regex("^[0-9a-f]{8,64}$")

        fun isToken(s: String) = TOKEN.matches(s)

        fun random(rng: SecureRandom = SecureRandom()): SocksAuth {
            fun hex(n: Int) = ByteArray(n).also { rng.nextBytes(it) }.joinToString("") { "%02x".format(it) }
            return SocksAuth(hex(8), hex(16))
        }
    }

    init {
        require(isToken(user) && isToken(pass)) { "bad credential" }
    }

    fun line() = "$user:$pass\n"

    fun writeTo(f: File) {
        if (f.exists()) {
            f.delete()
        }
        val fd: FileDescriptor = Os.open(
            f.path,
            OsConstants.O_CREAT or OsConstants.O_EXCL or OsConstants.O_WRONLY,
            384 /* 0600 */
        )
        FileOutputStream(fd).use { it.write(line().toByteArray(Charsets.US_ASCII)) }
    }
}
