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
    private final long discoveredWiFiGps;
    private final long totalWifiGps;
    private final long totalBtGps;
    private final long discoveredBtGps;
    private final long totalCellGps;
    private final long discoveredCellGps;

    public RankUser(final RankResponse.RankResponseRow row, final boolean monthRankingMode) {
        this.rank = row.getRank();
        this.rankDiff = monthRankingMode?(row.getPrevMonthRank()-row.getRank()):(row.getPrevRank()-row.getRank());
        this.username = row.getUserName();
        this.monthWifiGps = row.getEventMonthCount();
        this.discoveredWiFiGps = row.getDiscoveredWiFiGPS();
        this.totalWifiGps = row.getTotalWiFiLocations();
        this.discoveredBtGps = row.getDiscoveredBtGPS();
        this.totalBtGps = row.getDiscoveredBt();
        this.discoveredCellGps = row.getDiscoveredCellGPS();
        this.totalCellGps = row.getDiscoveredCell();
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

    public long getDiscoveredWiFiGps() {
        return discoveredWiFiGps;
    }

    public long getDiscoveredBtGps() {
        return discoveredBtGps;
    }

    public long getDiscoveredCellGps() {
        return discoveredCellGps;
    }
}
