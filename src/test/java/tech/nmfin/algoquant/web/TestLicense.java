package tech.nmfin.algoquant.web;
import java.io.File;
import dev.nm.misc.license.License;
/** The library license is process-wide and can be initialized only once. */
public final class TestLicense {
    private static boolean loaded;
    public static synchronized void load() {
        if(!loaded){License.setLicenseFile(new File(System.getenv().getOrDefault("NM_LICENSE_FILE","license/nm.lic")));loaded=true;}
    }
}
