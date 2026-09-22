package dev.vcgms.aapp.vaterialfiles.provider.ftp.client

interface Authenticator {
    fun getPassword(authority: Authority): String?
}
