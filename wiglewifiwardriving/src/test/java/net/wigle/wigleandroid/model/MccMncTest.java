package net.wigle.wigleandroid.model;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class MccMncTest {

    @Test
    public void intMncUsesTwoDigitsBelowOneHundred() {
        Assert.assertEquals("00", MccMnc.mncFor(0));
        Assert.assertEquals("01", MccMnc.mncFor(1));
        Assert.assertEquals("10", MccMnc.mncFor(10));
        Assert.assertEquals("99", MccMnc.mncFor(99));
    }

    @Test
    public void intMncKeepsThreeDigitsAtOrAboveOneHundred() {
        Assert.assertEquals("100", MccMnc.mncFor(100));
        Assert.assertEquals("260", MccMnc.mncFor(260));
        Assert.assertEquals("999", MccMnc.mncFor(999));
    }

    @Test
    public void unknownIntValuesAreRejected() {
        Assert.assertNull(MccMnc.mncFor(-1));
        Assert.assertNull(MccMnc.mncFor(1000));
        Assert.assertNull(MccMnc.mncFor(Integer.MAX_VALUE));
        Assert.assertNull(MccMnc.mccFor(0));
        Assert.assertNull(MccMnc.mccFor(Integer.MAX_VALUE));
    }

    @Test
    public void mccIsAlwaysThreeDigits() {
        Assert.assertEquals("001", MccMnc.mccFor(1));
        Assert.assertEquals("310", MccMnc.mccFor(310));
        Assert.assertEquals("001", MccMnc.normalizeMcc("1"));
        Assert.assertEquals("001", MccMnc.normalizeMcc("01"));
        Assert.assertEquals("001", MccMnc.normalizeMcc("001"));
    }

    @Test
    public void stringMncPreservesReportedWidth() {
        Assert.assertEquals("00", MccMnc.normalizeMnc("00"));
        Assert.assertEquals("000", MccMnc.normalizeMnc("000"));
        Assert.assertEquals("01", MccMnc.normalizeMnc("01"));
        Assert.assertEquals("001", MccMnc.normalizeMnc("001"));
        Assert.assertEquals("010", MccMnc.normalizeMnc("010"));
        Assert.assertEquals("260", MccMnc.normalizeMnc("260"));
    }

    @Test
    public void singleDigitStringMncPadsToTwo() {
        Assert.assertEquals("00", MccMnc.normalizeMnc("0"));
        Assert.assertEquals("01", MccMnc.normalizeMnc("1"));
        Assert.assertNull(MccMnc.normalizeMnc(null));
        Assert.assertNull(MccMnc.normalizeMnc(""));
        Assert.assertNull(MccMnc.normalizeMnc("0001"));
    }

    @Test
    public void uploadedTokenMatchesParserCandidates() {
        final String zero = MccMnc.mccFor(310) + MccMnc.mncFor(0);
        Assert.assertEquals("31000", zero);
        Assert.assertEquals("31000", MccMnc.operatorToken(zero + "_1_2"));
        Assert.assertEquals(Arrays.asList("00", "000"), MccMnc.mncCandidates("00"));

        final String paddedThree = MccMnc.normalizeMcc("310") + MccMnc.normalizeMnc("000");
        Assert.assertEquals("310000", paddedThree);
        Assert.assertEquals(Arrays.asList("000", "00"), MccMnc.mncCandidates("000"));
        Assert.assertEquals(Arrays.asList("001", "01"), MccMnc.mncCandidates("001"));
        Assert.assertEquals(Arrays.asList("01", "001"), MccMnc.mncCandidates(MccMnc.mncFor(1)));
        Assert.assertEquals(Arrays.asList("10", "010"), MccMnc.mncCandidates("10"));
        Assert.assertEquals(Arrays.asList("010", "10"), MccMnc.mncCandidates("010"));
        Assert.assertEquals(Collections.singletonList("260"), MccMnc.mncCandidates("260"));
    }

    @Test
    public void operatorTokenAcceptsParserExamples() {
        Assert.assertEquals("310260", MccMnc.operatorToken("310260_253_22671"));
        Assert.assertEquals("310260", MccMnc.operatorToken("310260"));
        Assert.assertEquals("23410", MccMnc.operatorToken("23410_100_5"));
        Assert.assertEquals("00101", MccMnc.operatorToken(MccMnc.mccFor(1) + MccMnc.mncFor(1) + "_8_9"));
        Assert.assertEquals("001", MccMnc.operatorToken("00101_8_9").substring(0, 3));
        Assert.assertEquals("01", MccMnc.operatorToken("00101_8_9").substring(3));
        Assert.assertNull(MccMnc.operatorToken("310"));
        Assert.assertNull(MccMnc.operatorToken("3102601"));
        Assert.assertNull(MccMnc.operatorToken(null));
    }
}
