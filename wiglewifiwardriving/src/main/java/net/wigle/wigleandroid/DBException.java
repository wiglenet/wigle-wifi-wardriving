package net.wigle.wigleandroid;

/**
 * Make db exceptions checked, so the compiler will help make sure we deal with them
 * @author bobzilla
 */
public class DBException extends Exception {
    private static final long serialVersionUID = 2011052800L;

    public DBException(final String message, final Throwable throwable) {
        super( message, throwable );
    }
}
