package net.wigle.wigleandroid.model;

/**
 * news. not thread-safe.
 */
public final class NewsItem {
    private final String subject;
    private final String post;
    private final String poster;
    private final String dateTime;
    private final String link;

    public NewsItem(final String subject, final String post, final String poster, final String dateTime,
                    final String link) {

        this.subject = subject;
        this.post = post;
        this.poster = poster;
        this.dateTime = dateTime;
        this.link = link;
    }

    public String getSubject() {
        return subject;
    }

    public String getPost() {
        return post;
    }

    public String getPoster() {
        return poster;
    }

    public String getDateTime() {
        return dateTime;
    }

    public String getLink() {
        return link;
    }
}
