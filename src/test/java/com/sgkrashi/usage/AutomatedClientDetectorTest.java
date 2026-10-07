package com.sgkrashi.usage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real people and the mobile app are counted; link-preview fetchers, search engines, monitors and scripts are not. */
class AutomatedClientDetectorTest {

    private static void automated(String userAgent) {
        assertTrue(AutomatedClientDetector.isAutomated(userAgent), "should be excluded: " + userAgent);
    }

    private static void person(String userAgent) {
        assertFalse(AutomatedClientDetector.isAutomated(userAgent), "should be counted: " + userAgent);
    }

    @Test
    void linkPreviewFetchersAreExcluded() {
        automated("WhatsApp/2.23.20.0 A");
        automated("facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)");
        automated("Facebot");
        automated("Twitterbot/1.0");
        automated("TelegramBot (like TwitterBot)");
        automated("LinkedInBot/1.0 (compatible; Mozilla/5.0; Apache-HttpClient +http://www.linkedin.com)");
        automated("Slackbot-LinkExpanding 1.0 (+https://api.slack.com/robots)");
        automated("Mozilla/5.0 (compatible; Discordbot/2.0; +https://discordapp.com)");
        automated("Mozilla/5.0 (compatible; Embedly/0.2; +http://support.embed.ly/)");
    }

    @Test
    void theWebsitesOwnPreviewFunctionIsExcluded() {
        automated(AutomatedClientDetector.PREVIEW_USER_AGENT);
        automated("node");          // what a Node fetch sends if the explicit name were ever missing
        automated("undici");
        automated("node-fetch/1.0 (+https://github.com/bitinn/node-fetch)");
    }

    @Test
    void searchEngineCrawlersAreExcluded() {
        automated("Mozilla/5.0 (Linux; Android 6.0.1; Nexus 5X Build/MMB29P) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)");
        automated("Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)");
        automated("Mozilla/5.0 (compatible; YandexBot/3.0; +http://yandex.com/bots)");
        automated("Mozilla/5.0 (compatible; AhrefsBot/7.0; +http://ahrefs.com/robot/)");
        automated("Mozilla/5.0 (compatible; Yahoo! Slurp; http://help.yahoo.com/help/us/ysearch/slurp)");
        automated("Mozilla/5.0 (compatible; MJ12bot/v1.4.8; http://mj12bot.com/)");
        automated("Some generic crawler for tests");
        automated("a spider v2");
    }

    @Test
    void monitorsAuditsAndScriptsAreExcluded() {
        automated("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/120.0.0.0 Safari/537.36");
        automated("Mozilla/5.0 (Linux; Android 11; moto g power) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Mobile Safari/537.36 Chrome-Lighthouse");
        automated("Mozilla/5.0 (compatible; UptimeRobot/2.0; http://www.uptimerobot.com/)");
        automated("Pingdom.com_bot_version_1.4_(http://www.pingdom.com/)");
        automated("curl/8.4.0");
        automated("Wget/1.21");
        automated("python-requests/2.31.0");
        automated("Go-http-client/1.1");
        automated("Java/17.0.2");
        automated("PostmanRuntime/7.36.0");
        automated("axios/1.6.0");
    }

    @Test
    void aMissingOrBlankUserAgentIsTreatedAsAutomated() {
        automated(null);
        automated("");
        automated("   ");
    }

    @Test
    void realBrowsersAndTheMobileAppAreCounted() {
        person("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36");
        person("Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1");
        person("Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36");
        person("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15");
        person("Mozilla/5.0 (X11; Linux x86_64; rv:125.0) Gecko/20100101 Firefox/125.0");
        person("okhttp/4.12.0");                       // the React Native Android app
        person("SGKrashi/1.0 CFNetwork/1494.0.7 Darwin/23.4.0");
    }

    @Test
    void aPhoneWhoseModelNameContainsBotIsStillAPerson() {
        person("Mozilla/5.0 (Linux; Android 10; CUBOT_X30) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Mobile Safari/537.36");
        person("Mozilla/5.0 (Linux; Android 12; Abbot Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Mobile Safari/537.36");
    }

    @Test
    void thePreviewConstantIsWhatTheWebsiteSends() {
        assertEquals("SGKrashi-LinkPreview/1.0", AutomatedClientDetector.PREVIEW_USER_AGENT); // keep in sync with the web repo's api/_lib/config.js
    }
}
