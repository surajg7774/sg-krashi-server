package com.sgkrashi.usage;

import java.util.regex.Pattern;

/**
 * Decides whether a request comes from a program rather than a person, so link-preview fetchers, search-engine
 * crawlers, uptime monitors and scripts never inflate the usage counters.
 *
 * <p>Only the User-Agent header is looked at, only for this yes/no, and it is never stored. A missing or blank
 * User-Agent counts as automated: browsers and the mobile app always send one, scripts often do not.
 *
 * <p>The patterns are deliberately specific ({@code bot/}, {@code bot;}, {@code bot-}...) rather than a bare
 * "bot", which would also exclude real people on phones whose model name contains it (for example Cubot or
 * Abbot). Crawlers that write "bot" followed by a space are listed by name instead.
 * The website's own link-preview function identifies itself with {@link #PREVIEW_USER_AGENT}.
 */
public final class AutomatedClientDetector {

    /** Sent by the website's Vercel link-preview function (api/preview.js) when it reads a public detail page. */
    public static final String PREVIEW_USER_AGENT = "SGKrashi-LinkPreview/1.0";

    private static final Pattern AUTOMATED = Pattern.compile(
            "(?i)("
                    // Googlebot/2.1, bingbot/2.0, AhrefsBot/7.0, Slackbot-LinkExpanding, Discordbot/2.0 ... (and a UA that simply ends in "bot")
                    + "bot[/;)-]|bot$"
                    + "|telegrambot|applebot|petalbot"
                    + "|\\b(crawler|spider|scraper|slurp)\\b"
                    + "|facebookexternalhit|facebot|whatsapp|linkedinbot|twitterbot|embedly|skypeuripreview|vkshare"
                    + "|headlesschrome|lighthouse|gtmetrix|pagespeed|pingdom|uptimerobot|statuscake|site24x7|healthchecks|betteruptime"
                    + "|curl/|wget/|python-requests|python-urllib|aiohttp|go-http-client|libwww-perl|java/|apache-httpclient"
                    + "|^node$|^node[/ ]|undici|node-fetch|axios/|postmanruntime|insomnia"
                    + "|sgkrashi-linkpreview"
                    + ")");

    private AutomatedClientDetector() {
    }

    public static boolean isAutomated(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return true;
        return AUTOMATED.matcher(userAgent.trim()).find();
    }
}
