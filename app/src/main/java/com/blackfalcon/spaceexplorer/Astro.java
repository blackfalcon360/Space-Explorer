package com.blackfalcon.spaceexplorer;

/** Low-precision (about 1 degree) sky positions of the Sun, Moon and planets. Works fully offline. */
public final class Astro {

    // sky body indexes
    public static final int SUN = 0, MOON = 1;
    public static final String[] NAMES = {"Sun", "Moon", "Mercury", "Venus", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune"};

    public static final class Result {
        public final double[] az = new double[9];   // degrees from true North, clockwise
        public final double[] alt = new double[9];  // degrees above the horizon
        public double moonElong;                    // Moon-Sun angle along the ecliptic, 0..360
    }

    // JPL approximate Keplerian elements (valid 1800-2050): a, da, e, de, I, dI, L, dL, long.peri, d, long.node, d  (per century)
    // rows: Mercury, Venus, Earth, Mars, Jupiter, Saturn, Uranus, Neptune
    private static final double[][] EL = {
        {0.38709927, 0.00000037, 0.20563593, 0.00001906, 7.00497902, -0.00594749, 252.25032350, 149472.67411175, 77.45779628, 0.16047689, 48.33076593, -0.12534081},
        {0.72333566, 0.00000390, 0.00677672, -0.00004107, 3.39467605, -0.00078890, 181.97909950, 58517.81538729, 131.60246718, 0.00268329, 76.67984255, -0.27769418},
        {1.00000261, 0.00000562, 0.01671123, -0.00004392, -0.00001531, -0.01294668, 100.46457166, 35999.37244981, 102.93768193, 0.32327364, 0.0, 0.0},
        {1.52371034, 0.00001847, 0.09339410, 0.00007882, 1.84969142, -0.00813131, -4.55343205, 19140.30268499, -23.94362959, 0.44441088, 49.55953891, -0.29257343},
        {5.20288700, -0.00011607, 0.04838624, -0.00013253, 1.30439695, -0.00183714, 34.39644051, 3034.74612775, 14.72847983, 0.21252668, 100.47390909, 0.20469106},
        {9.53667594, -0.00125060, 0.05386179, -0.00050991, 2.48599187, 0.00193609, 49.95424423, 1222.49362201, 92.59887831, -0.41897216, 113.66242448, -0.28867794},
        {19.18916464, -0.00196176, 0.04725744, -0.00004397, 0.77263783, -0.00242939, 313.23810451, 428.48202785, 170.95427630, 0.40805281, 74.01692503, 0.04240589},
        {30.06992276, 0.00026291, 0.00859048, 0.00005105, 1.77004347, 0.00035372, -55.12002969, 218.45945325, 44.96476227, -0.32241464, 131.78422574, -0.00508664}
    };

    private static double norm360(double d) { d %= 360.0; return d < 0 ? d + 360.0 : d; }

    /** Heliocentric ecliptic (J2000) position in AU for element row `row`. */
    private static double[] helio(int row, double T) {
        double[] k = EL[row];
        double a = k[0] + k[1] * T;
        double e = k[2] + k[3] * T;
        double I = Math.toRadians(k[4] + k[5] * T);
        double L = k[6] + k[7] * T;
        double lp = k[8] + k[9] * T;
        double node = k[10] + k[11] * T;

        double M = norm360(L - lp);
        if (M > 180) M -= 360;
        double Mr = Math.toRadians(M);
        double E = Mr + e * Math.sin(Mr);
        for (int i = 0; i < 12; i++) {
            E -= (E - e * Math.sin(E) - Mr) / (1 - e * Math.cos(E));
        }
        double xp = a * (Math.cos(E) - e);
        double yp = a * Math.sqrt(1 - e * e) * Math.sin(E);

        double w = Math.toRadians(lp - node);
        double N = Math.toRadians(node);
        double cw = Math.cos(w), sw = Math.sin(w), cN = Math.cos(N), sN = Math.sin(N), cI = Math.cos(I), sI = Math.sin(I);
        double x = (cw * cN - sw * sN * cI) * xp + (-sw * cN - cw * sN * cI) * yp;
        double y = (cw * sN + sw * cN * cI) * xp + (-sw * sN + cw * cN * cI) * yp;
        double z = (sw * sI) * xp + (cw * sI) * yp;
        return new double[]{x, y, z};
    }

    /** ecliptic lon/lat (degrees, of date) -> {ra, dec} in radians. */
    private static double[] eclToEq(double lonDeg, double latDeg, double eps) {
        double l = Math.toRadians(lonDeg), b = Math.toRadians(latDeg);
        double ra = Math.atan2(Math.sin(l) * Math.cos(eps) - Math.tan(b) * Math.sin(eps), Math.cos(l));
        double dec = Math.asin(Math.sin(b) * Math.cos(eps) + Math.cos(b) * Math.sin(eps) * Math.sin(l));
        return new double[]{ra, dec};
    }

    private static void horizon(double[] eq, double lstDeg, double latDeg, Result r, int idx) {
        double ra = eq[0], dec = eq[1];
        double H = Math.toRadians(lstDeg) - ra;
        double phi = Math.toRadians(latDeg);
        double sinAlt = Math.sin(phi) * Math.sin(dec) + Math.cos(phi) * Math.cos(dec) * Math.cos(H);
        r.alt[idx] = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, sinAlt))));
        double az = Math.atan2(Math.sin(H), Math.cos(H) * Math.sin(phi) - Math.tan(dec) * Math.cos(phi)) + Math.PI;
        r.az[idx] = norm360(Math.toDegrees(az));
    }

    public static Result compute(long millis, double latDeg, double lonDeg) {
        double jd = millis / 86400000.0 + 2440587.5;
        double T = (jd - 2451545.0) / 36525.0;
        double eps = Math.toRadians(23.439291 - 0.0130042 * T);
        double prec = 1.396971 * T; // precession in ecliptic longitude, degrees
        double gmst = norm360(280.46061837 + 360.98564736629 * (jd - 2451545.0));
        double lst = norm360(gmst + lonDeg);

        Result r = new Result();
        double[] earth = helio(2, T);

        // Sun (opposite of Earth as seen from the Sun)
        double sx = -earth[0], sy = -earth[1], sz = -earth[2];
        double sunLon = norm360(Math.toDegrees(Math.atan2(sy, sx)) + prec);
        double sunLat = Math.toDegrees(Math.atan2(sz, Math.hypot(sx, sy)));
        horizon(eclToEq(sunLon, sunLat, eps), lst, latDeg, r, SUN);

        // Moon (low-precision series, ecliptic of date)
        double moonLon = 218.32 + 481267.881 * T
                + 6.29 * Math.sin(Math.toRadians(135.0 + 477198.87 * T))
                - 1.27 * Math.sin(Math.toRadians(259.3 - 413335.36 * T))
                + 0.66 * Math.sin(Math.toRadians(235.7 + 890534.22 * T))
                + 0.21 * Math.sin(Math.toRadians(269.9 + 954397.74 * T))
                - 0.19 * Math.sin(Math.toRadians(357.5 + 35999.05 * T))
                - 0.11 * Math.sin(Math.toRadians(186.5 + 966404.03 * T));
        double moonLat = 5.13 * Math.sin(Math.toRadians(93.3 + 483202.02 * T))
                + 0.28 * Math.sin(Math.toRadians(228.2 + 960400.89 * T))
                - 0.28 * Math.sin(Math.toRadians(318.3 + 6003.15 * T))
                - 0.17 * Math.sin(Math.toRadians(217.6 - 407332.21 * T));
        moonLon = norm360(moonLon);
        horizon(eclToEq(moonLon, moonLat, eps), lst, latDeg, r, MOON);
        r.moonElong = norm360(moonLon - sunLon);

        // planets
        for (int i = 0; i < 7; i++) {
            double[] p = helio(i < 2 ? i : i + 1, T); // skip Earth row (index 2)
            double gx = p[0] - earth[0], gy = p[1] - earth[1], gz = p[2] - earth[2];
            double lon = norm360(Math.toDegrees(Math.atan2(gy, gx)) + prec);
            double lat = Math.toDegrees(Math.atan2(gz, Math.hypot(gx, gy)));
            horizon(eclToEq(lon, lat, eps), lst, latDeg, r, 2 + i);
        }
        return r;
    }

    // test harness (not used by the app)
    public static void main(String[] a) {
        // J2000: 2000-01-01 12:00 UTC, observer at lat 0, lon 0
        Result r = compute(946728000000L, 0, 0);
        for (int i = 0; i < 9; i++) System.out.printf("%s az=%.1f alt=%.1f%n", NAMES[i], r.az[i], r.alt[i]);
        System.out.printf("elong=%.1f%n", r.moonElong);
    }
}
