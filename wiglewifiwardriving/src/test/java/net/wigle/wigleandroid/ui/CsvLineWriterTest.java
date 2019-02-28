package net.wigle.wigleandroid.ui;

import android.database.Cursor;

import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.util.CsvUtil;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

@RunWith(MockitoJUnitRunner.class)
public class CsvLineWriterTest {


    //74:ea:3a:af:b9:3a,N00:03:47:b4:a7:d9,333,[WEP][ESS],2018-10-17 15:07:04,6,-79,47.78304,19.9209166666667,151.7,2.40000009536743,WIFI

    final String badString20181017161505 = "N00:03:47:b4:a7:d9,333";
    final String badStringQuotes = "\"N00:03:47:b4:a7:d9,333";
    final String bssid = "74:ea:3a:af:b9:3a";
    final String capabilities = "[WEP][ESS]";

    static Date date = new Date();
    static CharBuffer charBuffer = CharBuffer.allocate( 1024 );
    static ByteBuffer byteBuffer = ByteBuffer.allocate( 1024 );
    static StringBuffer stringBuffer = new StringBuffer();
    static FieldPosition fp = new FieldPosition(NumberFormat.INTEGER_FIELD);
    static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    static NumberFormat numberFormat;
    static final CharsetEncoder encoder = Charset.forName( MainActivity.ENCODING ).newEncoder();

    @Mock
    private Cursor cursor;

    @BeforeClass
    public static void setupTestData() {
        encoder.onUnmappableCharacter( CodingErrorAction.REPLACE );
        numberFormat = NumberFormat.getNumberInstance( Locale.US );
    }

    @Test
    public void testCommaSsid() {
        Network network = new Network( bssid , badString20181017161505, 0, capabilities, -79, NetworkType.WIFI );
        Assert.assertTrue(CsvUtil.writeLine(cursor, network, date, charBuffer, byteBuffer, stringBuffer,
                fp, dateFormat, numberFormat));
        charBuffer.flip();
        encoder.reset();
        encoder.encode( charBuffer, byteBuffer, true );
        encoder.flush( byteBuffer );

        System.out.println("\t"+ new String(byteBuffer.array(), StandardCharsets.UTF_8));
    }

    @Test
    public void testCommaDoubleQuoteSsid() {
        Network network = new Network( bssid, badStringQuotes, 0, capabilities, -79, NetworkType.WIFI );
        Assert.assertTrue(CsvUtil.writeLine(cursor, network, date, charBuffer, byteBuffer, stringBuffer,
                fp, dateFormat, numberFormat));
        charBuffer.flip();
        encoder.reset();
        encoder.encode( charBuffer, byteBuffer, true );
        encoder.flush( byteBuffer );
        System.out.println("\t"+ new String(byteBuffer.array(), StandardCharsets.UTF_8));
    }
}
