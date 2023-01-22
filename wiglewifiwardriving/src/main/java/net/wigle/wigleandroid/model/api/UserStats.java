package net.wigle.wigleandroid.model.api;

/**
 * Response from the WiGLE API v2 user stats call
 * @author arkasha
 */
public class UserStats {
    private String imageBadgeUrl;
    private long monthRank;
    private long rank;
    private boolean success;
    private String user;
    private Statistics statistics;

    public class Statistics {
        private long discoveredCell;
        private long discoveredCellGPS;
        private long discoveredWiFi;
        private long discoveredWiFiGPS;
        private float discoveredWiFiGPSPercent;

        public long getDiscoveredBtGPS() {
            return discoveredBtGPS;
        }

        public void setDiscoveredBtGPS(long discoveredBtGPS) {
            this.discoveredBtGPS = discoveredBtGPS;
        }

        public long getDiscoveredBt() {
            return discoveredBt;
        }

        public void setDiscoveredBt(long discoveredBt) {
            this.discoveredBt = discoveredBt;
        }

        private long discoveredBtGPS;
        private long discoveredBt;
        private long eventMonthCount;
        private long eventPrevMonthCount;
        private String first;
        private String last;
        private long monthRank;
        private long prevMonthRank;
        private long prevRank;
        private long rank;
        private boolean self;
        private long totalWiFiLocations;
        private String userName;

        public long getDiscoveredCell() {
            return discoveredCell;
        }

        public void setDiscoveredCell(long discoveredCell) {
            this.discoveredCell = discoveredCell;
        }

        public long getDiscoveredCellGPS() {
            return discoveredCellGPS;
        }

        public void setDiscoveredCellGPS(long discoveredCellGPS) {
            this.discoveredCellGPS = discoveredCellGPS;
        }

        public long getDiscoveredWiFi() {
            return discoveredWiFi;
        }

        public void setDiscoveredWiFi(long discoveredWiFi) {
            this.discoveredWiFi = discoveredWiFi;
        }

        public long getDiscoveredWiFiGPS() {
            return discoveredWiFiGPS;
        }

        public void setDiscoveredWiFiGPS(long discoveredWiFiGPS) {
            this.discoveredWiFiGPS = discoveredWiFiGPS;
        }

        public float getDiscoveredWiFiGPSPercent() {
            return discoveredWiFiGPSPercent;
        }

        public void setDiscoveredWiFiGPSPercent(float discoveredWiFiGPSPercent) {
            this.discoveredWiFiGPSPercent = discoveredWiFiGPSPercent;
        }

        public long getEventMonthCount() {
            return eventMonthCount;
        }

        public void setEventMonthCount(long eventMonthCount) {
            this.eventMonthCount = eventMonthCount;
        }

        public long getEventPrevMonthCount() {
            return eventPrevMonthCount;
        }

        public void setEventPrevMonthCount(long eventPrevMonthCount) {
            this.eventPrevMonthCount = eventPrevMonthCount;
        }

        public String getFirst() {
            return first;
        }

        public void setFirst(String first) {
            this.first = first;
        }

        public String getLast() {
            return last;
        }

        public void setLast(String last) {
            this.last = last;
        }

        public long getMonthRank() {
            return monthRank;
        }

        public void setMonthRank(long monthRank) {
            this.monthRank = monthRank;
        }

        public long getPrevMonthRank() {
            return prevMonthRank;
        }

        public void setPrevMonthRank(long prevMonthRank) {
            this.prevMonthRank = prevMonthRank;
        }

        public long getPrevRank() {
            return prevRank;
        }

        public void setPrevRank(long prevRank) {
            this.prevRank = prevRank;
        }

        public long getRank() {
            return rank;
        }

        public void setRank(long rank) {
            this.rank = rank;
        }

        public boolean isSelf() {
            return self;
        }

        public void setSelf(boolean self) {
            this.self = self;
        }

        public long getTotalWiFiLocations() {
            return totalWiFiLocations;
        }

        public void setTotalWiFiLocations(long totalWiFiLocations) {
            this.totalWiFiLocations = totalWiFiLocations;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }
    }

    public String getImageBadgeUrl() {
        return imageBadgeUrl;
    }

    public void setImageBadgeUrl(String imageBadgeUrl) {
        this.imageBadgeUrl = imageBadgeUrl;
    }

    public long getMonthRank() {
        return monthRank;
    }

    public void setMonthRank(long monthRank) {
        this.monthRank = monthRank;
    }

    public long getRank() {
        return rank;
    }

    public void setRank(long rank) {
        this.rank = rank;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public Statistics getStatistics() {
        return statistics;
    }

    public void setStatistics(Statistics statistics) {
        this.statistics = statistics;
    }
}
