package net.wigle.wigleandroid.model.api;

import java.util.List;

/**
 * API response for uploads
 */
public class UploadReseponse {
    private Boolean success;
    private String warning;
    private UploadResultsResponse results;
    private String observer;

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }
    public String getWarning() {
        return warning;
    }

    public void setWarning(String warning) {
        this.warning = warning;
    }

    public UploadResultsResponse getResults() {
        return results;
    }

    public void setResults(UploadResultsResponse results) {
        this.results = results;
    }

    public String getObserver() {
        return observer;
    }

    public void setObserver(String observer) {
        this.observer = observer;
    }

    public class UploadResultsResponse {
        private String timeTaken;
        private Long filesize;
        private String filename;
        private List<UploadTransaction> transids;

        public String getTimeTaken() {
            return timeTaken;
        }

        public void setTimeTaken(String timeTaken) {
            this.timeTaken = timeTaken;
        }

        public Long getFilesize() {
            return filesize;
        }

        public void setFilesize(Long filesize) {
            this.filesize = filesize;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public List<UploadTransaction> getTransids() {
            return transids;
        }

        public void setTransids(List<UploadTransaction> transids) {
            this.transids = transids;
        }
    }
    public class UploadTransaction {
        private String file;
        private Long size;
        private String transId;

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }

        public Long getSize() {
            return size;
        }

        public void setSize(Long size) {
            this.size = size;
        }

        public String getTransId() {
            return transId;
        }

        public void setTransId(String transId) {
            this.transId = transId;
        }
    }

}
