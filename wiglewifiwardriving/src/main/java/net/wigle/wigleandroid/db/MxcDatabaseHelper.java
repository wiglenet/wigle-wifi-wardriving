package net.wigle.wigleandroid.db;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;

import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.model.MccMncRecord;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MxcDatabaseHelper extends SQLiteOpenHelper {
    private static final String MXC_DB_NAME = "mmcmnc.sqlite";
    private static final String DATABASE_PATH = Environment.getExternalStorageDirectory() + "/wiglewifi/";
    private static final int MXC_DATABASE_VERSION = 1;

    private final Context context;
    private SQLiteDatabase db;

    // query when you just need opname
    private static final String OPERATOR_FOR_MCC_MNC = "SELECT operator FROM wigle_mcc_mnc WHERE mcc = ? and mnc = ? LIMIT 1";

    // query for the whole record
    private static final String RECORD_FOR_MCC_MNC = "SELECT * FROM wigle_mcc_mnc WHERE mcc = ? and mnc = ? LIMIT 1";

    public MxcDatabaseHelper(Context context) {
        super(context, MXC_DB_NAME, null, MXC_DATABASE_VERSION);
        this.context = context;
    }

    public boolean isPresent() {
        final String mxcPath = DATABASE_PATH + MXC_DB_NAME;
        final File dbFile = new File(mxcPath);
        if (dbFile.exists()) {
            return true;
        }
        return false;
    }

    public void implantMxcDatabase() throws IOException {
        MainActivity.info("installing mmc/mnc database...");
        InputStream assetInputData = null;

        try {
            assetInputData = context.getAssets().open(MXC_DB_NAME);
            final String outputFilePath = DATABASE_PATH + MXC_DB_NAME;
                    //context.getDatabasePath(MXC_DB_NAME).getAbsolutePath();
            MainActivity.info("/data/data/" + context.getPackageName() + "/databases/" + MXC_DB_NAME +" vs "+outputFilePath);
            final File outputFile = new File(outputFilePath);

            OutputStream mxcOutput = new FileOutputStream(outputFile);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = assetInputData.read(buffer))>0){
                mxcOutput.write(buffer, 0, length);
            }

            mxcOutput.flush();
            mxcOutput.close();
        } catch (IOException ioe) {
            throw ioe;
        } finally {
            if (null != assetInputData) {
                assetInputData.close();
            }
        }
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        //ALIBI: pre-created during build
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        //ALIBI: pre-created during build
    }

    public MccMncRecord networkRecordForMccMnc(final String mcc, final String mnc) {
        Cursor cursor = null;

        if (!isPresent()) {
            //DEBUG: MainActivity.error("No Mxc DB");
            return null;
        }

        try {
            if (openDataBase()) {
                cursor = db.rawQuery(RECORD_FOR_MCC_MNC, new String[]{mcc, mnc});
                if (cursor.getCount() > 0) {
                    cursor.moveToFirst();

                    MccMncRecord operator = new MccMncRecord(
                            cursor.getString(cursor.getColumnIndex("type")),
                            cursor.getString(cursor.getColumnIndex("countryName")),
                            cursor.getString(cursor.getColumnIndex("countryCode")),
                            cursor.getString(cursor.getColumnIndex("mcc")),
                            cursor.getString(cursor.getColumnIndex("mnc")),
                            cursor.getString(cursor.getColumnIndex("brand")),
                            cursor.getString(cursor.getColumnIndex("operator")),
                            cursor.getString(cursor.getColumnIndex("status")),
                            cursor.getString(cursor.getColumnIndex("bands")),
                            cursor.getString(cursor.getColumnIndex("notes")));
                    return operator;
                }
            } else {
                MainActivity.error("unable to open mcc/mnc database for record.");
            }
        } catch (SQLException sqlex) {
            MainActivity.error("Unable to open DB for record: ",sqlex);

        }finally {
            if (null != cursor) {
                cursor.close();
            }
            if ((null != db) && (db.isOpen())) {
                db.close();
            }
        }
        return null;
    }

    public String networkNameForMccMnc(final String mcc, final String mnc) {
        Cursor cursor = null;
        String operator = null;

        if (!isPresent()) {
            //DEBUG: MainActivity.error("No Mxc DB");
            return null;
        }

        try {
            if (openDataBase()) {
                cursor = db.rawQuery(OPERATOR_FOR_MCC_MNC, new String[]{mcc, mnc});
                if (cursor.getCount() > 0) {
                    cursor.moveToFirst();
                    operator = cursor.getString(cursor.getColumnIndex("operator"));
                }
                return operator;
            } else {
                MainActivity.error("unable to open mcc/mnc database for name.");
            }
        } catch (SQLException sqlex) {
            MainActivity.error("Unable to open DB for name: ",sqlex);

        }finally {
            if (null != cursor) {
                cursor.close();
            }
            if ((null != db) && (db.isOpen())) {
                db.close();
            }
        }
        return operator;
    }

    public boolean openDataBase() throws SQLException {
        if (!isPresent()) {
            return false;
        }

        final String mxcPath = DATABASE_PATH + MXC_DB_NAME;

        if (null == db || !db.isOpen()) {
            db = SQLiteDatabase.openDatabase(mxcPath, null,
                    SQLiteDatabase.OPEN_READONLY);
        }
        return db.isOpen();
    }

    /** This method close database connection and released occupied memory **/
    @Override
    public synchronized void close() {
        if (db != null)
            db.close();
        SQLiteDatabase.releaseMemory();
        super.close();
    }
}
