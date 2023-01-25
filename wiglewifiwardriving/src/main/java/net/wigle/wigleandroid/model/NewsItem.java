package net.wigle.wigleandroid.model;

import android.os.Build;
import android.text.Html;
import android.text.Spanned;

/**
 * news. not thread-safe.
 */
public final class NewsItem {
    private String subject;
    private String postDate;
    private String link;
    private String story;
    private String storyId;
    private Boolean more;
    private String userName;

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getPostDate() {
        return postDate;
    }

    public void setPostDate(String postDate) {
        this.postDate = postDate;
    }

    public String getStory() {
        return story;
    }

    public void setStory(String story) {
        this.story = story;
    }

    public String getStoryId() {
        return storyId;
    }

    public void setStoryId(String storyId) {
        this.storyId = storyId;
    }

    public Boolean getMore() {
        return more;
    }

    public void setMore(Boolean more) {
        this.more = more;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    final static String[] format_search = {
            "\\&\\#58;",
            "\\&\\#46;",
            "\\[b\\](.*?)\\[\\/b\\]",
            "\\[b.*\\](.*?)\\[\\/b.*\\]",
            "\\[i\\](.*?)\\[\\/i\\]",
            "\\[i.*\\](.*?)\\[\\/i.*\\]",
            "\\[url=(.*?)(:[^:]*?)?\\](.*?)\\[\\/url(:[^:]*?)?\\]",
            "\\[u\\](.*?)\\[\\/u\\]",
            "\\[u.*\\](.*?)\\[\\/u.*\\]",
            "\\{SMILIES_PATH\\}"
    };

    final static String[] format_replace = {
            ":",
            ".",
            "<strong>$1</strong>",
            "<strong>$1</strong>",
            "<em>$1</em>",
            "<em>$1</em>",
            "<a href=\"$1$2\">$3</a>",
            "<span style=\"text-decoration: underline;\">$1</span>",
            "<span style=\"text-decoration: underline;\">$1</span>",
            "/phpbb/images/smilies",
    };

    public NewsItem(final String subject, final String story, final String userName, final String postDate,
                    final String link) {

        this.subject = subject;
        this.story = story;
        this.userName = userName;
        this.postDate = postDate;
        this.link = link;
    }

    public Spanned getSpannedStory() {
        return bbCodeToText(story);
    }
    private static Spanned bbCodeToText(String sourcePost) {
        String filtered = sourcePost.replace("\\n", "\n")
                .replace("&quot;", "\"")
                .replace("&amp;", "&").replaceAll("<.*?>", "");
        String[] chunked = filtered.split("(\r\n|\n)");
        String htmlBreaks = "";
        for (String s: chunked) {
            htmlBreaks += "<p>"+s+"</p>";
        }
        for (int i = 0; i < format_search.length; i++) {
            htmlBreaks = htmlBreaks.replaceAll(format_search[i], format_replace[i]);
        }
        if (Build.VERSION.SDK_INT >= 24) {
            return Html.fromHtml(htmlBreaks,
                    Html.FROM_HTML_MODE_LEGACY);
        } else {
            return Html.fromHtml(htmlBreaks);
        }
    }
}
