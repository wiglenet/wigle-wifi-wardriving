package net.wigle.m8b.geodesy;

// port from https://github.com/chrisveness/geodesy/blob/master/mgrs.js 

/* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -  */
/*  MGRS / UTM Conversion Functions                                   (c) Chris Veness 2014-2016  */
/*                                                                                   MIT Licence  */
/* www.movable-type.co.uk/scripts/latlong-utm-mgrs.html                                           */
/* www.movable-type.co.uk/scripts/geodesy/docs/module-mgrs.html                                   */
/* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -  */

/**
 * Convert between Universal Transverse Mercator (UTM) coordinates and Military Grid Reference
 * System (MGRS/NATO) grid references.
 *
 * @module   mgrs
 * @requires utm
 * @requires latlon-ellipsoidal
 */

/* qv www.fgdc.gov/standards/projects/FGDC-standards-projects/usng/fgdc_std_011_2001_usng.pdf p10 */

public final class mgrs {

    
    /*
     * @param  {number} zone - 6° longitudinal zone (1..60 covering 180°W..180°E).
 * @param  {string} band - 8° latitudinal band (C..X covering 80°S..84°N).
 * @param  {string} e100k - First letter (E) of 100km grid square.
 * @param  {string} n100k - Second letter (N) of 100km grid square.
 * @param  {number} easting - Easting in metres within 100km grid square.
 * @param  {number} northing - Northing in metres within 100km grid square.
 */
    int zone;
    char band;
    char e100k;
    char n100k;

    double easting;
    double northing;

    /*
     * 100km grid square column (‘e’) letters repeat every third zone
     */
    private static final String[] e100kLetters = new String[]{ "ABCDEFGH", "JKLMNPQR", "STUVWXYZ" };


    /*
     * 100km grid square row (‘n’) letters repeat every other zone
     */
    private static final String[] n100kLetters = new String[] {"ABCDEFGHJKLMNPQRSTUV", "FGHJKLMNPQRSTUVABCDE" };

    private static final String mgrsLatBands = "CDEFGHJKLMNPQRSTUVWXX"; // X is repeated for 80-84°N
    
    private static final double _100k = 100e3;
    
    public static mgrs fromUtm(utm u){
	mgrs m = new mgrs();

	m.zone = u.zone;
	m.band = u.latBand;

	int col = (int)Math.floor( u.easting/ _100k );
	m.e100k = e100kLetters[(u.zone-1)%3].charAt(col-1); // col-1 since 1*100e3 -> A (index 0), 2*100e3 -> B (index 1), etc.

	// rows in even zones are A-V, in odd zones are F-E
	int row = (int)(Math.floor(u.northing / _100k) % 20);
	m.n100k = n100kLetters[(u.zone-1)%2].charAt(row);

	// truncate easting/northing to within 100km grid square
	m.easting = u.easting % _100k;
	m.northing = u.northing % _100k;	
	
	return m;
    }

    public String toString() {
	return String.format("%02d%c%c%c%s%s",zone,band,e100k,n100k,String.format("%05d",(int)easting).substring(0,2),String.format("%05d",(int)northing).substring(0,2));
    }

    /**
     * b.length == 9
     */   
    public void populateBytes(byte[] b){
	assert b.length == 9;

	b[0] = z1(zone);
	b[1] = z2(zone);
	b[2] = (byte)band;
	b[3] = (byte)e100k;
	b[4] = (byte)n100k;

	// get leftmost two digits in string form
	/*
	  cases:
	  100 000
	  -------
	   00 000
	   00 009
	   00 010
	   00 100
	   01 000
	   10 000
	   99 999
	 */

	{
	    int eint = canon(easting);
	    b[5] = z1(eint);
	    b[6] = z2(eint);
	}

	{
	    int nint = canon(northing);
	    b[7] = z1(nint);
	    b[8] = z2(nint);
	}

    }

    private static byte[] _z = new byte[]{(byte)0x30,(byte)0x31,(byte)0x32,(byte)0x33,(byte)0x34,(byte)0x35,(byte)0x36,(byte)0x37,(byte)0x38,(byte)0x39};
    
    // first digit as a byte
    private static byte z1(int zone) {
	return _z[(int)(zone/10)]; // trunc
    }

    // second digit as a byte
    private static byte z2(int zone){	
	return _z[(int)(zone%10)]; // mod
    }

    // turn a {north,east}ing into their compact left two digit
    private static int canon(double ing){
	int iint = (int)ing;
	iint = iint / 1000;
	return iint;
    }

    
    // the hashcode and equals comparisons are specific to this application, because java is sad
    // per item 7 from effective java
    // obey the general contract when overriding equals
    public boolean equals( Object obj ) {
	if ( this == obj ) {
	    return true;
	}
	
	if ( !(obj instanceof mgrs) ) {
	    return false;
	}
	
        mgrs m = (mgrs) obj;
	
	// delegate
	return equals(m);
    }

    public boolean equals(mgrs m) {
	if ( this == m ) {
	    return true;
	}
	if ( m == null ) {
	    return false;
	}
	if ( zone != m.zone ) {
	    return false;
	}
	if ( band != m.band ) {
	    return false;
	}
	if ( e100k != m.e100k ) {
	    return false;
	}
	if ( n100k != m.n100k ) {
	    return false;
	}
	if ( canon(easting) != canon(m.easting) ) {
	    return false;
	}
	if ( canon(northing) != canon(m.northing) ) {
	    return false;
	}
	
	return true;
    }
    
    
    // per item 8 from effective java
    // always override hashcode when you override equals
    public int hashCode() {
	int result = 17;
	result = 37 * result + zone;
	result = 37 * result + band;
	result = 37 * result + e100k;
	result = 37 * result + n100k;
	result = 37 * result + canon(easting);
	result = 37 * result + canon(northing);

	return result;
    }
    
    
    /*
     * int encoding of 1km square (21 bits)
     * zone | band | e100k | n100k
     * zone: 60 (6 bits)
     * band: 20 (5 bits)
     *
     * e: 24 (5 bits)
     * n: 20 (5 bits)  
     *
     * easting: 7 bits
     * northing: 7 bits
     *
     * 21+14 = 36 -> ceil() = 40 bits. 5 bytes.
     *
     * string version is 9 bytes. 18SVK8924
     */
    
}
