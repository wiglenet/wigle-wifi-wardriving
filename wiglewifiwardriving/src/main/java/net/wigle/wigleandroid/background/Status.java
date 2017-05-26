package net.wigle.wigleandroid.background;

import net.wigle.wigleandroid.R;

public enum Status {
    UNKNOWN( R.string.status_unknown, R.string.status_unknown_error ),
    FAIL( R.string.status_fail, R.string.status_fail ),
    SUCCESS( R.string.status_success, R.string.status_upload_success ),
    WRITE_SUCCESS( R.string.status_success, R.string.status_write_success ),
    BAD_USERNAME( R.string.status_fail, R.string.status_no_user ),
    BAD_PASSWORD( R.string.status_fail, R.string.status_no_pass ),
    EXCEPTION( R.string.status_fail, R.string.status_exception ),
    BAD_LOGIN( R.string.status_fail, R.string.status_login_fail ),
    UPLOADING( R.string.status_working, R.string.status_uploading ),
    WRITING( R.string.status_working, R.string.status_writing ),
    EMPTY_FILE( R.string.status_nothing, R.string.status_empty ),
    DOWNLOADING( R.string.status_downloading, R.string.status_downloading ),
    PARSING( R.string.status_parsing, R.string.status_parsing );

    private final int title;
    private final int message;
    Status( final int title, final int message ) {
        this.title = title;
        this.message = message;
    }
    public int getTitle() {
        return title;
    }
    public int getMessage() {
        return message;
    }
}