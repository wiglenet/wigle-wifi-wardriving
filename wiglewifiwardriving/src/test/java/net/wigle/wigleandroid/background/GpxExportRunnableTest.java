package net.wigle.wigleandroid.background;
import static org.mockito.Mockito.*;

import android.database.Cursor;
import android.os.Environment;
import android.util.Log;

import androidx.fragment.app.FragmentActivity;

import net.wigle.wigleandroid.db.DBException;
import net.wigle.wigleandroid.util.FileUtility;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static org.junit.Assert.*;

public class GpxExportRunnableTest {

    @Mock
    private FragmentActivity mockActivity;

    @Mock
    private Cursor mockCursor;

    private MockedStatic<Log> mockedLog;
    private MockedStatic<Environment> mockedEnvironment;
    private MockedStatic<FileUtility> mockedFileUtility;

    private GpxExportRunnable gpxExportRunnable;

    @Before
    public void setUp() throws DBException {
        MockitoAnnotations.openMocks(this);
        mockedLog = Mockito.mockStatic(Log.class);

        // Define default behavior for Log methods to prevent RuntimeException
        mockedLog.when(() -> Log.v(anyString(), anyString())).thenReturn(0);
        mockedLog.when(() -> Log.d(anyString(), anyString())).thenReturn(0);
        mockedLog.when(() -> Log.i(anyString(), anyString())).thenReturn(0);
        mockedLog.when(() -> Log.w(anyString(), anyString())).thenReturn(0);
        mockedLog.when(() -> Log.w(anyString(), any(Throwable.class))).thenReturn(0);
        mockedLog.when(() -> Log.w(anyString(), anyString(), any(Throwable.class))).thenReturn(0);
        mockedLog.when(() -> Log.e(anyString(), anyString())).thenReturn(0);
        mockedLog.when(() -> Log.e(anyString(), anyString(), any(Throwable.class))).thenReturn(0);

        mockedEnvironment = Mockito.mockStatic(Environment.class);
        String filePath = "mock/external/storage";
        File mockExternalStorageDir = new File(filePath);
        mockedEnvironment.when(Environment::getExternalStorageDirectory).thenReturn(mockExternalStorageDir);

        mockedFileUtility = Mockito.mockStatic(FileUtility.class);
        mockedFileUtility.when(() -> FileUtility.getGpxPath(any())).thenReturn(filePath);

        gpxExportRunnable = new GpxExportRunnable(mockActivity, true, 100, -1);
    }

    @After
    public void tearDown() {
        if (mockedLog != null) {
            mockedLog.close();
        }
        if (mockedEnvironment != null) {
            mockedEnvironment.close();
        }
        if (mockedFileUtility != null) {
            mockedFileUtility.close();
        }
    }

    @Test
    public void testWriteSegmentsWithCursor() throws Exception {
        when(mockCursor.moveToFirst()).thenReturn(true);
        when(mockCursor.isAfterLast()).thenReturn(false, true);
        when(mockCursor.moveToNext()).thenReturn(true);

        double lat = 52.2297;
        double lon = 21.0122;
        long time = new Date().getTime();
        when(mockCursor.getDouble(0)).thenReturn(lat);
        when(mockCursor.getDouble(1)).thenReturn(lon);
        when(mockCursor.getLong(2)).thenReturn(time);

        File tempFile = File.createTempFile("test", ".gpx");
        FileWriter writer = new FileWriter(tempFile);

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
        long segmentCount = gpxExportRunnable.writeSegmentsWithCursor(writer, mockCursor, df, 1L);
        writer.close();

        assertEquals(1, segmentCount);

        String str = new String(Files.readAllBytes(tempFile.toPath()), StandardCharsets.UTF_8);

        String expected = "<trkpt lat=\""+lat+"\" lon=\""+lon+"\"><time>" + df.format(time) + "</time></trkpt>\n";

        assertEquals(expected, str);

        tempFile.delete();
    }
}