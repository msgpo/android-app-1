// AUTOGENERATED FILE - DO NOT MODIFY!
// This file generated by Djinni from open_pgp.djinni

package ch.protonmail.android.utils.nativelib;

import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;

public abstract class LibVersion {

    /**
     * get lib version
     */
    @NonNull
    public static native String getLibVersion();

    /**
     * get pgp version
     */
    @NonNull
    public static native String getPgpVersion();

    /**
     * get vcard version
     */
    @NonNull
    public static native String getVcardVersion();

    private static final class CppProxy extends LibVersion {

        private final long nativeRef;
        private final AtomicBoolean destroyed = new AtomicBoolean(false);

        private CppProxy(long nativeRef) {
            if (nativeRef == 0) {
                throw new RuntimeException("nativeRef is zero");
            }
            this.nativeRef = nativeRef;
        }

        private native void nativeDestroy(long nativeRef);

        public void destroy() {
            boolean destroyed = this.destroyed.getAndSet(true);
            if (!destroyed) {
                nativeDestroy(this.nativeRef);
            }
        }

        protected void finalize() throws java.lang.Throwable {
            destroy();
            super.finalize();
        }
    }
}