package net.wigle.wigleandroid.model.api;

import java.util.List;

public class RankResponse {
    private boolean eventView;
    private String myUsername;
    private long pageEnd;
    private long pageStart;
    private List<RankResponseRow> results;
    private long selected;

    public boolean isEventView() {
        return eventView;
    }

    public void setEventView(boolean eventView) {
        this.eventView = eventView;
    }

    public String getMyUsername() {
        return myUsername;
    }

    public void setMyUsername(String myUsername) {
        this.myUsername = myUsername;
    }

    public long getPageEnd() {
        return pageEnd;
    }

    public void setPageEnd(long pageEnd) {
        this.pageEnd = pageEnd;
    }

    public long getPageStart() {
        return pageStart;
    }

    public void setPageStart(long pageStart) {
        this.pageStart = pageStart;
    }

    public List<RankResponseRow> getResults() {
        return results;
    }

    public void setResults(List<RankResponseRow> results) {
        this.results = results;
    }

    public long getSelected() {
        return selected;
    }

    public void setSelected(long selected) {
        this.selected = selected;
    }

    public class RankResponseRow {
        private long discoveredCell;
        private long discoveredCellGPS;
        private long discoveredWiFi;
        private long discoveredWiFiGPS;
        private long discoveredBt;
        private long discoveredBtGPS;
        private float discoveredWiFiGPSPercent;
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

        public long getDiscoveredBt() {
            return discoveredBt;
        }

        public void setDiscoveredBt(long discoveredBt) {
            this.discoveredBt = discoveredBt;
        }

        public long getDiscoveredBtGPS() {
            return discoveredBtGPS;
        }

        public void setDiscoveredBtGPS(long discoveredBtGPS) {
            this.discoveredBtGPS = discoveredBtGPS;
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
}
