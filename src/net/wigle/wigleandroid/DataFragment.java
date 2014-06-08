package net.wigle.wigleandroid;

import java.util.List;

import net.wigle.wigleandroid.MainActivity.Doer;
import net.wigle.wigleandroid.background.FileUploaderListener;
import net.wigle.wigleandroid.background.FileUploaderTask;
import net.wigle.wigleandroid.background.HttpDownloader;
import net.wigle.wigleandroid.background.KmlWriter;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * configure settings
 */
public final class DataFragment extends Fragment implements FileUploaderListener {

  private static final int MENU_EXIT = 11;
  private static final int MENU_SETTINGS = 12;
  private static final int MENU_ERROR_REPORT = 13;

  /** Called when the activity is first created. */
  @Override
  public void onCreate( final Bundle savedInstanceState) {
      super.onCreate( savedInstanceState );
      setHasOptionsMenu(true);
      // set language
      MainActivity.setLocale( getActivity() );

      // force media volume controls
      getActivity().setVolumeControlStream( AudioManager.STREAM_MUSIC );
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    final View view = inflater.inflate(R.layout.data, container, false);

    setupQueryButtons( view );
    setupCsvButtons( view );
    setupKmlButtons( view );
    setupBackupDbButton( view );
    setupImportObservedButton( view );

    return view;
  }

  private void setupQueryButtons( final View view ) {
    Button button = (Button) view.findViewById( R.id.search_button );
    button.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick( final View buttonView ) {
        final QueryArgs queryArgs = new QueryArgs();
        String fail = null;
        String field = null;
        boolean okValue = false;

        for ( final int id : new int[]{ R.id.query_address, R.id.query_ssid, R.id.query_bssid } ) {
          if ( fail != null ) {
            break;
          }

          final EditText editText = (EditText) view.findViewById( id );
          final String text = editText.getText().toString().trim();
          if ( "".equals(text) ) {
            continue;
          }

          try {
            switch( id ) {
              case R.id.query_address:
                field = getString(R.string.address);
                Geocoder gc = new Geocoder(getActivity());
                List<Address> addresses = gc.getFromLocationName(text, 1);
                if ( addresses.size() < 1 ) {
                  fail = getString(R.string.no_address_found);
                  break;
                }
                queryArgs.setAddress(addresses.get(0));
                okValue = true;
                break;
              case R.id.query_ssid:
                field = getString(R.string.ssid);
                queryArgs.setSSID(text);
                okValue = true;
                break;
              case R.id.query_bssid:
                field = getString(R.string.bssid);
                queryArgs.setBSSID(text);
                okValue = true;
                break;
              default:
                MainActivity.error("setupButtons: bad id: " + id);
            }
          }
          catch( Exception ex ) {
            fail = getString(R.string.problem_with_field) + " '" + field + "': " + ex.getMessage();
            break;
          }
        }

        if ( fail == null && ! okValue ) {
          fail = "No query fields specified";
        }

        if ( fail != null ) {
          // toast!
          Toast.makeText( getActivity(), fail, Toast.LENGTH_SHORT ).show();
        }
        else {
          ListFragment.lameStatic.queryArgs = queryArgs;
          // start db result activity
          final Intent settingsIntent = new Intent( getActivity(), DBResultActivity.class );
          startActivity( settingsIntent );
        }
      }
    });

    button = (Button) view.findViewById( R.id.reset_button );
    button.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick( final View buttonView ) {
        for ( final int id : new int[]{ R.id.query_address, R.id.query_ssid } ) {
          final EditText editText = (EditText) view.findViewById( id );
          editText.setText("");
        }
      }
    });

  }

  /**
   * FileUploaderListener interface
   */
  @Override
  public void transferComplete() {
    // nothing
  }

  private void setupCsvButtons( final View view ) {
    // actually need this Activity context, for dialogs

    final Button csvRunExportButton = (Button) view.findViewById( R.id.csv_run_export_button );
    csvRunExportButton.setOnClickListener( new OnClickListener() {
      @Override
      public void onClick( final View buttonView ) {
        MainActivity.createConfirmation( getActivity(),
            DataFragment.this.getString(R.string.data_export_csv), new Doer() {
          @Override
          public void execute() {
            // actually need this Activity context, for dialogs
            FileUploaderTask fileUploaderTask = new FileUploaderTask( getActivity(),
                ListFragment.lameStatic.dbHelper, DataFragment.this, true );
            fileUploaderTask.setWriteRunOnly();
            fileUploaderTask.start();
          }
        } );
      }
    });

    final Button csvExportButton = (Button) view.findViewById( R.id.csv_export_button );
    csvExportButton.setOnClickListener( new OnClickListener() {
      @Override
      public void onClick( final View buttonView ) {
        MainActivity.createConfirmation( getActivity(),
            DataFragment.this.getString(R.string.data_export_csv_db), new Doer() {
          @Override
          public void execute() {
            // actually need this Activity context, for dialogs
            FileUploaderTask fileUploaderTask = new FileUploaderTask( getActivity(),
                ListFragment.lameStatic.dbHelper, DataFragment.this, true );
            fileUploaderTask.setWriteWholeDb();
            fileUploaderTask.start();
          }
        } );
      }
    });
  }

  private void setupKmlButtons( final View view ) {
    final Button kmlRunExportButton = (Button) view.findViewById( R.id.kml_run_export_button );
    kmlRunExportButton.setOnClickListener( new OnClickListener() {
      @Override
      public void onClick( final View buttonView ) {
        MainActivity.createConfirmation( getActivity(),
            DataFragment.this.getString(R.string.data_export_kml_run), new Doer() {
          @Override
          public void execute() {
            // actually need this Activity context, for dialogs
            KmlWriter kmlWriter = new KmlWriter( getActivity(), ListFragment.lameStatic.dbHelper,
                ListFragment.lameStatic.runNetworks );
            kmlWriter.start();
          }
        } );
      }
    });

    final Button kmlExportButton = (Button) view.findViewById( R.id.kml_export_button );
    kmlExportButton.setOnClickListener( new OnClickListener() {
      @Override
      public void onClick( final View buttonView ) {
        MainActivity.createConfirmation( getActivity(),
            DataFragment.this.getString(R.string.data_export_kml_db), new Doer() {
          @Override
          public void execute() {
            // actually need this Activity context, for dialogs
            KmlWriter kmlWriter = new KmlWriter( getActivity(), ListFragment.lameStatic.dbHelper );
            kmlWriter.start();
          }
        } );
      }
    });
  }

  private void setupBackupDbButton( final View view ) {
    final Button kmlExportButton = (Button) view.findViewById( R.id.backup_db_button );
    if ( ! MainActivity.hasSD() ) {
      kmlExportButton.setEnabled(false);
    }

    kmlExportButton.setOnClickListener( new OnClickListener() {
      @Override
      public void onClick( final View buttonView ) {
        MainActivity.createConfirmation( getActivity(),
            DataFragment.this.getString(R.string.data_backup_db), new Doer() {
          @Override
          public void execute() {
            // actually need this Activity context, for dialogs
            BackupTask task = new BackupTask(DataFragment.this, MainActivity.getMainActivity(DataFragment.this));
            task.execute();
          }
        } );
      }
    });
  }

  private void setupImportObservedButton( final View view ) {
    final Button importObservedButton = (Button) view.findViewById( R.id.import_observed_button );

    importObservedButton.setOnClickListener( new OnClickListener() {
      @Override
      public void onClick( final View buttonView ) {
        MainActivity.createConfirmation( getActivity(),
            DataFragment.this.getString(R.string.data_import_observed), new Doer() {
          @Override
          public void execute() {
            final MainActivity mainActivity = MainActivity.getMainActivity( DataFragment.this );
            if ( mainActivity != null ) {
              mainActivity.setTransferring();
            }
            // actually need this Activity context, for dialogs
            HttpDownloader task = new HttpDownloader(getActivity(), ListFragment.lameStatic.dbHelper,
                new FileUploaderListener() {
              @Override
              public void transferComplete() {
                if ( mainActivity != null ) {
                  mainActivity.transferComplete();
                }
              }
            });
            task.start();
          }
        } );
      }
    });
  }

  /**
   * way to background load the data and show progress on the gui thread
   */
  public static class BackupTask extends AsyncTask<Object, Integer, Integer> {
    private final Fragment fragment;
    private final MainActivity mainActivity;
    private Pair<Boolean,String> dbResult;

    public BackupTask ( final Fragment fragment, final MainActivity mainActivity ) {
      this.fragment = fragment;
      this.mainActivity = mainActivity;
      mainActivity.setTransferring();
    }

    @Override
    protected Integer doInBackground( Object... obj ) {
      dbResult = ListFragment.lameStatic.dbHelper.copyDatabase(this);
      // dbResult = new Pair<Boolean,String>(Boolean.TRUE, "meh");
      return 0;
    }

    @Override
    protected void onProgressUpdate( Integer... progress ) {
      final View view = fragment.getView();
      final TextView tv = (TextView) view.findViewById( R.id.backup_db_text );
      tv.setText( mainActivity.getString(R.string.backup_db_text) + "\n" + progress[0] + "%" );
    }

    @Override
    protected void onPostExecute( Integer result ) {
      mainActivity.transferComplete();

      final View view = fragment.getView();
      final TextView tv = (TextView) view.findViewById( R.id.backup_db_text );
      tv.setText( mainActivity.getString(R.string.backup_db_text) );

      final AlertDialog.Builder builder = new AlertDialog.Builder( mainActivity );
      builder.setCancelable( true );
      builder.setTitle( mainActivity.getString( dbResult.getFirst() ? R.string.status_success : R.string.status_fail ));
      builder.setMessage( dbResult.getSecond() );
      final AlertDialog ad = builder.create();
      // ok
      ad.setButton( DialogInterface.BUTTON_POSITIVE, mainActivity.getString(R.string.ok), new DialogInterface.OnClickListener() {
        @Override
        public void onClick( final DialogInterface dialog, final int which ) {
          try {
            dialog.dismiss();
          }
          catch ( Exception ex ) {
            // guess it wasn't there anyways
            MainActivity.info( "exception dismissing alert dialog: " + ex );
          }
          return;
        } });
      ad.show();
    }

    public void progress( int progress ) {
      publishProgress(progress);
    }
  }

  @Override
  public void onResume() {
    MainActivity.info( "resume data." );
    super.onResume();
    getActivity().setTitle(R.string.data_activity_name);
  }

  /* Creates the menu items */
  @Override
  public void onCreateOptionsMenu (final Menu menu, final MenuInflater inflater) {
	MenuItem item = menu.add( 0, MENU_SETTINGS, 0, getString(R.string.menu_settings) );
	item.setIcon( android.R.drawable.ic_menu_preferences );

    item = menu.add( 0, MENU_ERROR_REPORT, 0, getString(R.string.menu_error_report) );
    item.setIcon( android.R.drawable.ic_menu_report_image );

    item = menu.add( 0, MENU_EXIT, 0, getString(R.string.menu_exit) );
    item.setIcon( android.R.drawable.ic_menu_close_clear_cancel );

    super.onCreateOptionsMenu(menu, inflater);
  }

  /* Handles item selections */
  @Override
  public boolean onOptionsItemSelected( final MenuItem item ) {
      switch ( item.getItemId() ) {
        case MENU_EXIT:
          final MainActivity main = MainActivity.getMainActivity();
          main.finish();
          return true;
        case MENU_SETTINGS:
          final Intent settingsIntent = new Intent( getActivity(), SettingsActivity.class );
          startActivity( settingsIntent );
          break;
        case MENU_ERROR_REPORT:
          final Intent errorReportIntent = new Intent( getActivity(), ErrorReportActivity.class );
          startActivity( errorReportIntent );
          break;
      }
      return false;
  }

}
