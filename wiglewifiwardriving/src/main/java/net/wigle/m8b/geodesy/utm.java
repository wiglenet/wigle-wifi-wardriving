package net.wigle.m8b.geodesy;

// port from https://github.com/chrisveness/geodesy/blob/master/utm.js 
// stupid helpful javascript -_-

/* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -  */
/*  MGRS / UTM Conversion Functions                                   (c) Chris Veness 2014-2016  */
/*                                                                                   MIT Licence  */
/* www.movable-type.co.uk/scripts/latlong-utm-mgrs.html                                           */
/* www.movable-type.co.uk/scripts/geodesy/docs/module-mgrs.html                                   */
/* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -  */


/**
 * Convert between Universal Transverse Mercator coordinates and WGS 84 latitude/longitude points.
 *
 * Method based on Karney 2011 ‘Transverse Mercator with an accuracy of a few nanometers’,
 * building on Krüger 1912 ‘Konforme Abbildung des Erdellipsoids in der Ebene’.
 *
 * @module   utm
 * @requires latlon-ellipsoidal
 */

/* qv www.fgdc.gov/standards/projects/FGDC-standards-projects/usng/fgdc_std_011_2001_usng.pdf p10 */

public class utm {

    /*
* @param  {number} zone - UTM 6° longitudinal zone (1..60 covering 180°W..180°E).
 * @param  {string} hemisphere - N for northern hemisphere, S for southern hemisphere.
 * @param  {number} easting - Easting in metres from false easting (-500km from central meridian).
 * @param  {number} northing - Northing in metres from equator (N) or from false northing -10,000km (S).
* @param  {number} [convergence] - Meridian convergence (bearing of grid north clockwise from true
 *                  north), in degrees
 * @param  {number} [scale] - Grid scale factor
 */
    int zone;
    char hemisphere;
    double easting;
    double northing;
    double convergence;
    double scale;
    char latBand;

    private static final double falseEasting = 500e3;
    private static final double falseNorthing = 10000e3;

    private static final String mgrsLatBands = "CDEFGHJKLMNPQRSTUVWXX"; // X is repeated for 80-84°N
    private static final double sixRadians = Math.toRadians(6);

    // WGS-84 only
    private static final double a = 6378137.0;
    private static final double b = 6356752.314245;
    private static final double f = 1/298.257223563;

    private static final double k0 = 0.9996; // UTM scale on the central meridian

    // ---- easting, northing: Karney 2011 Eq 7-14, 29, 35:

    private static final double e = Math.sqrt(f*(2-f)); // eccentricity
    private static final double n = f / (2 - f);        // 3rd flattening
    private static final double n2 = n*n;
    private static final double n3 = n*n2;
    private static final double n4 = n*n3;
    private static final double n5 = n*n4;
    private static final double n6 = n*n5; // TODO: compare Horner-form accuracy?

    private static final double A = a/(1+n) * (1 + (1/4.0)*n2 + (1/64.0)*n4 + (1/256.0)*n6); // 2πA is the circumference of a meridian

    private static final double[] α = new double[] { Double.NaN, // note α is one-based array (6th order Krüger expressions)
     (1/2.0)*n - (2/3.0)*n2 + (5/16.0)*n3 +   (41/180.0)*n4 -     (127/288.0)*n5 +      (7891/37800.0)*n6,
               (13/48.0)*n2 -  (3/5.0)*n3 + (557/1440.0)*n4 +     (281/630.0)*n5 - (1983433/1935360.0)*n6,
                            (61/240.0)*n3 -  (103/140.0)*n4 + (15061/26880.0)*n5 +   (167603/181440.0)*n6,
                                        (49561/161280.0)*n4 -     (179/168.0)*n5 + (6601661/7257600.0)*n6,
                                                              (34729/80640.0)*n5 - (3418889/1995840.0)*n6,
                                                                               (212378941/319334400.0)*n6 };
    

    public static utm fromLatLon(double lat, double lon) {
        if (Double.isNaN(lat) || Double.isNaN(lon)){ throw new ArithmeticException("Invalid point");}
        if (!(-80<=lat && lat<=84)){ throw new ArithmeticException("Outside UTM limits");}
	
        int zone = (int)(Math.floor((lon+180)/6) + 1); // longitudinal zone
        double λ0 = Math.toRadians((zone-1)*6 - 180 + 3); // longitude of central meridian

	// ---- handle Norway/Svalbard exceptions
	// grid zones are 8° tall; 0°N is offset 10 into latitude bands array
	
	char latBand = mgrsLatBands.charAt((int)Math.floor((lat/8)+10));
	// adjust zone & central meridian for Norway
	if (zone==31 && latBand=='V' && lon>= 3) { zone++; λ0 += sixRadians; }
	// adjust zone & central meridian for Svalbard
	if (zone==32 && latBand=='X' && lon<  9) { zone--; λ0 -= sixRadians; }
	if (zone==32 && latBand=='X' && lon>= 9) { zone++; λ0 += sixRadians; }
	if (zone==34 && latBand=='X' && lon< 21) { zone--; λ0 -= sixRadians; }
	if (zone==34 && latBand=='X' && lon>=21) { zone++; λ0 += sixRadians; }
	if (zone==36 && latBand=='X' && lon< 33) { zone--; λ0 -= sixRadians; }
	if (zone==36 && latBand=='X' && lon>=33) { zone++; λ0 += sixRadians; }
	
	double φ = Math.toRadians(lat);      // latitude ± from equator
	double λ = Math.toRadians(lon) - λ0; // longitude ± from central meridian

	// WGS84 only

	double cosλ = Math.cos(λ);
	double sinλ = Math.sin(λ);
	double tanλ = Math.tan(λ);

	double τ = Math.tan(φ); // τ ≡ tanφ, τʹ ≡ tanφʹ; prime (ʹ) indicates angles on the conformal sphere. (ʹ is actually \u0374 keraia which is why it can be a name.)
	double σ = Math.sinh(e*atanh(e*τ/Math.sqrt(1+τ*τ)));

	double τʹ = τ*Math.sqrt(1+σ*σ) - σ*Math.sqrt(1+τ*τ);
	
	double ξʹ = Math.atan2(τʹ, cosλ);
	double ηʹ = asinh(sinλ / Math.sqrt(τʹ*τʹ + cosλ*cosλ));
	

	double ξ = ξʹ;
	for (int j=1; j<=6; j++){ ξ += α[j] * Math.sin(2*j*ξʹ) * Math.cosh(2*j*ηʹ);}
	
	double η = ηʹ;
	for (int j=1; j<=6; j++){ η += α[j] * Math.cos(2*j*ξʹ) * Math.sinh(2*j*ηʹ);}
	
	double x = k0 * A * η;
	double y = k0 * A * ξ;

	// ---- convergence: Karney 2011 Eq 23, 24

	double pʹ = 1;
	for (int j=1; j<=6; j++){ pʹ += 2*j*α[j] * Math.cos(2*j*ξʹ) * Math.cosh(2*j*ηʹ);}
	double qʹ = 0;
	for (int j=1; j<=6; j++){ qʹ += 2*j*α[j] * Math.sin(2*j*ξʹ) * Math.sinh(2*j*ηʹ);}
	
	double γʹ = Math.atan(τʹ / Math.sqrt(1+τʹ*τʹ)*tanλ);
	double γʺ = Math.atan2(qʹ, pʹ);

	double γ = γʹ + γʺ;

	// ---- scale: Karney 2011 Eq 25

	double sinφ = Math.sin(φ);
	double kʹ = Math.sqrt(1 - e*e*sinφ*sinφ) * Math.sqrt(1 + τ*τ) / Math.sqrt(τʹ*τʹ + cosλ*cosλ);
	double kʺ = A / a * Math.sqrt(pʹ*pʹ + qʹ*qʹ);

	double k = k0 * kʹ * kʺ;

	// shift x/y to false origins
	x = x + falseEasting;      // make x relative to false easting
	if (y < 0) {
	    y = y + falseNorthing; // make y in southern hemisphere relative to false northing
	}

	// round to reasonable precision...which java can't do.
	//x = Math.round(x,6); // nm precision
	//y = Math.round(y,6); // nm precision
	double convergence = Math.toDegrees(γ);//Math.round(Math.toDegrees(γ),9);
	double scale = k;//Math.round(k,12);

	char h = lat>=0 ? 'N' : 'S'; // hemisphere
    
	utm u = new utm();
	u.zone = zone;
	u.hemisphere = h;
	u.easting = x;
	u.northing = y;
	u.convergence = convergence;
	u.scale = scale;
	u.latBand = latBand;
        return u;
    }


    public static double atanh(double x){
	return 0.5 * Math.log((1+x)/(1-x));
    }

    public static double asinh(double x){
	return Math.log(x+Math.sqrt((x*x)+1));
    }
    

    public String toString() {
	return String.format("%1$02d %2$s %3$.6f %4$.6f",zone,hemisphere,easting,northing);
    }

    public static void main(String[] argv) throws Exception {
	utm u = fromLatLon(Double.valueOf(argv[0]),Double.valueOf(argv[1]));
	System.out.println(u);
	mgrs m = mgrs.fromUtm(u);
	System.out.println(m);
    }
    
}
