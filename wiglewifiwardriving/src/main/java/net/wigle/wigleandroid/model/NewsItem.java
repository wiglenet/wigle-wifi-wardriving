package net.wigle.wigleandroid.model;

import android.os.Build;
import android.text.Html;
import android.text.Spanned;

import net.wigle.wigleandroid.MainActivity;

/**
 * news. not thread-safe.
 */
public final class NewsItem {
    private final String subject;
    private final Spanned post;
    private final String poster;
    private final String dateTime;
    private final String link;

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

    public NewsItem(final String subject, final String post, final String poster, final String dateTime,
                    final String link) {

        this.subject = subject;
        this.post = NewsItem.bbCodeToText(post);
        this.poster = poster;
        this.dateTime = dateTime;
        this.link = link;
    }

    public String getSubject() {
        return subject;
    }

    public Spanned getPost() {
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

    private static Spanned bbCodeToText(String sourcePost) {
        String[] chunked = sourcePost.split("(\r\n|\n)");
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
