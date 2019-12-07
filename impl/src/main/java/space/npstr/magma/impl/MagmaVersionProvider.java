package space.npstr.magma.impl;

import com.jcabi.manifests.Manifests;

public class MagmaVersionProvider {

    // See gradle build file where this is written into the manifest
    private static final String MAGMA_VERSION_KEY = "Magma-Version";

    private static final String FALLBACK_VERSION = "unknown version";

    public String getVersion() {

        if (!Manifests.exists(MAGMA_VERSION_KEY)) {
            return FALLBACK_VERSION;
        }

        return Manifests.read(MAGMA_VERSION_KEY);
    }
}
