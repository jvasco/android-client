package edu.rutgers.css.Rutgers;

import edu.rutgers.css.Rutgers.utils.LinkUtils;

/**
 * App configuration
 */
public final class Config {

    private Config() {}

    // Build info
    public static final String APPTAG = "Rutgers-" + BuildConfig.FLAVOR;
    public static final String PACKAGE_NAME = BuildConfig.APPLICATION_ID;
    public static final String VERSION = BuildConfig.VERSION_NAME;
    public static final String OSNAME = "android";
    public static final String BETAMODE = "beta";
    public static final Boolean BETA = true;
    public static final Boolean FORCE_DEBUG_LOGGING = false;

    // Server and API level
    public static final String API_LEVEL = "1";
    public static final String API_MACHINE = "doxa";
    public static final String API_BASE = "https://"+API_MACHINE+".rutgers.edu/mobile/"+API_LEVEL+"/";

    // Location-based services config
    public static final float NEARBY_RANGE = 300.0f; // Within 300 meters is considered "nearby"

    // Deep link schema info
    public static final LinkUtils.Schema SCHEMA = LinkUtils.Schema.HTTP;
}
