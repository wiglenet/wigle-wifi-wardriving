package net.wigle.wigleandroid.model;

/**
 * rank user. not thread-safe.
 */
public final class RankUser {
    private final long rank;
    private final String username;
    private final long monthWifiGps;
    private final long totalWifiGps;
    private final long totalCellGps;

    public RankUser(final long rank, final String username, final long monthWifiGps, final long totalWifiGps,
                    final long totalCellGps) {
        this.rank = rank;
        this.username = username;
        this.monthWifiGps = monthWifiGps;
        this.totalWifiGps = totalWifiGps;
        this.totalCellGps = totalCellGps;
    }

    public long getRank() {
        return rank;
    }

    public String getUsername() {
        return username;
    }

    public long getMonthWifiGps() {
        return monthWifiGps;
    }

    public long getTotalWifiGps() {
        return totalWifiGps;
    }

    public long getTotalCellGps() {
        return totalCellGps;
    }
}
