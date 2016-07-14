package net.wigle.wigleandroid.model;

/**
 * news. not thread-safe.
 */
public final class NewsItem {
    private final String subject;
    private final String post;

    public NewsItem(final String subject, final String post) {

        this.subject = subject;
        this.post = post;
    }

    public String getSubject() {
        return subject;
    }

    public String getPost() {
        return post;
    }
}
