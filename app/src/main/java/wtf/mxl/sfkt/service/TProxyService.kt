package wtf.mxl.sfkt.service

/**
 * JNI wrapper for hev-socks5-tunnel native library.
 * This class provides the native method declarations that the JNI library expects.
 * The class name and method names must match exactly what's defined in hev-jni.c
 */
object TProxyService {

    private var loaded = false

    /**
     * Load the native library
     * @return true if loaded successfully
     */
    @Synchronized
    fun loadLibrary(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary("hev-socks5-tunnel")
            loaded = true
            true
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e("TProxyService", "Failed to load native library", e)
            false
        }
    }

    /**
     * Start the tproxy service
     * @param configPath path to the YAML config file
     * @param fd TUN file descriptor
     */
    @JvmStatic
    external fun TProxyStartService(configPath: String, fd: Int)

    /**
     * Stop the tproxy service
     */
    @JvmStatic
    external fun TProxyStopService()

    /**
     * Get traffic statistics
     * @return array of [txPackets, txBytes, rxPackets, rxBytes]
     */
    @JvmStatic
    external fun TProxyGetStats(): LongArray
}
