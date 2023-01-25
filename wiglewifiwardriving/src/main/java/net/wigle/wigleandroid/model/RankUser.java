package net.wigle.wigleandroid.model;

import net.wigle.wigleandroid.model.api.RankResponse;

/**
 * rank view user. not thread-safe.
 * This class only exists (vs. {@link RankResponse.RankResponseRow}) to supply rankDiff. which is presentation mode-specific.
 */
public final class RankUser {
    private final long rank;
    private final long rankDiff;
    private final String username;
    private final long monthWifiGps;
    private final long totalWifiGps;
    private final long totalBtGps;
    private final long totalCellGps;

    public RankUser(final long rank, final long rankDiff, final String username, final long monthWifiGps,
                    final long totalWifiGps, final long totalBtGps, final long totalCellGps) {
        this.rank = rank;
        this.rankDiff = rankDiff;
        this.username = username;
        this.monthWifiGps = monthWifiGps;
        this.totalWifiGps = totalWifiGps;
        this.totalBtGps = totalBtGps;
        this.totalCellGps = totalCellGps;
    }

    public RankUser(final RankResponse.RankResponseRow row, final boolean monthRankingMode) {
        this.rank = row.getRank();
        this.rankDiff = monthRankingMode?(row.getPrevMonthRank()-row.getRank()):(row.getPrevRank()-row.getRank());
        this.username = row.getUserName();
        this.monthWifiGps = row.getDiscoveredWiFiGPS();
        this.totalWifiGps = row.getTotalWiFiLocations();
        this.totalBtGps = row.getDiscoveredBtGPS();
        this.totalCellGps = row.getDiscoveredCellGPS();
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

    public long getTotalBtGps() {
        return totalBtGps;
    }

    public long getTotalCellGps() {
        return totalCellGps;
    }
}
