package net.wigle.wigleandroid.model;

/**
 * rank user. not thread-safe.
 */
public final class RankUser {
    private final long rank;
    private final long rankDiff;
    private final String username;
    private final long monthWifiGps;
    private final long totalWifiGps;
    private final long totalCellGps;

    public RankUser(final long rank, final long rankDiff, final String username, final long monthWifiGps,
                    final long totalWifiGps, final long totalCellGps) {
        this.rank = rank;
        this.rankDiff = rankDiff;
        this.username = username;
        this.monthWifiGps = monthWifiGps;
        this.totalWifiGps = totalWifiGps;
        this.totalCellGps = totalCellGps;
    }

    public long getRank() {
        return rank;
    }

    public long getRankDiff() {
        return rankDiff;
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
