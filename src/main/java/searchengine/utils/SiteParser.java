package searchengine.utils;

import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.SiteParserData;
import searchengine.model.*;
import searchengine.services.IndexingServiceImpl;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.RecursiveAction;

@Getter
public class SiteParser extends RecursiveAction {

    private final String path;
    private final Site site;
    private final SiteParserData siteParserData;
    private final String uri;
    private Document document;
    private static final Logger logger = LogManager.getLogger(SiteParser.class);

    public SiteParser(String path, Site site, SiteParserData siteParserData) {
        this.path = path;
        this.site = site;
        this.siteParserData = siteParserData;
        uri = site.getUrl() + path;
    }


    @Override
    protected void compute() {
        if (!IndexingServiceImpl.isIndexingStarted() ||
                siteParserData.getDataSaver().isPageSaved(path, site) ||
                !indexPage()) return;
        Elements aElements = document.getElementsByTag("a");
        document = null;
        List<SiteParser> parsers = new ArrayList<>();
        for (Element element : aElements) {
            String path = element.attr("href");
            if (!isCorrectPath(path)) continue;
            if (path.startsWith("http")) {
                path = path.replace(site.getUrl(), "");
            }
            if (siteParserData.getDataSaver().isPageSaved(path, site)) continue;
            SiteParser parser = new SiteParser(path, site, siteParserData);
            parsers.add(parser);

        }
        invokeAll(parsers);
    }

    private boolean connect() {
        try {
            Thread.sleep(400);
            document = Jsoup.connect(uri)
                    .userAgent(siteParserData.getConnectionData().getUserAgent())
                    .referrer(siteParserData.getConnectionData().getReferrer())
                    .maxBodySize(0)
                    .timeout(20_000)
                    .ignoreContentType(true)
                    .followRedirects(true)
                    .get();
        } catch (IOException | InterruptedException e) {
            if (e instanceof HttpStatusException) {
                int statusCode = ((HttpStatusException) e).getStatusCode();
                if (statusCode == 403 && site.getUrl().equals(uri)) {
                    site.setStatus(SiteStatus.FAILED);
                    site.setStatusTime(LocalDateTime.now());
                    site.setLastError("Ошибка индексации: требуется авторизация");
                    siteParserData.getDataSaver().updateSite(site);
                    this.completeExceptionally(null);
                }
                savePage(statusCode, true);
            }
            if (e instanceof UnknownHostException) {
                site.setStatus(SiteStatus.FAILED);
                site.setStatusTime(LocalDateTime.now());
                site.setLastError("Ошибка подключиня: указан не верный хост");
                siteParserData.getDataSaver().updateSite(site);
                this.completeExceptionally(null);
            }
            if (e instanceof SocketTimeoutException) {
                String errorMessage = uri.equals(site.getUrl())
                        ? "Ошибка индексации: главная страница сайта не доступна"
                        : "Ошибка индексации: страница не доступна";
                site.setStatus(SiteStatus.FAILED);
                site.setStatusTime(LocalDateTime.now());
                site.setLastError(errorMessage);
                siteParserData.getDataSaver().updateSite(site);
            }
            logger.warn("Failed to connect to page: " + uri + " - " + e.getMessage());
            return false;
        }
        return Objects.nonNull(document) && Objects.requireNonNull(document.connection().response().contentType())
                .toLowerCase(Locale.ROOT).contains("text");
    }

    private boolean isCorrectPath(String path) {
        return path.startsWith("/") || path.startsWith(site.getUrl());
    }

    private Page savePage(int statusCode, boolean isFailed) {
        Page page = new Page();
        page.setCode(statusCode);
        page.setSite(site);
        page.setPath(path.length() > 0 ? path : "/");
        page.setContent(Objects.isNull(document) ? "<Default Content>" : document.toString());
        if (siteParserData.getDataSaver().isPageSaved(path, site)) return null;
        site.setStatusTime(LocalDateTime.now());
        site.setStatus(SiteStatus.INDEXING);
        siteParserData.getDataSaver().updateSite(site);
        if (isFailed) siteParserData.getDataSaver().saveIndexingData(page, new ArrayList<>());
        return page;
    }

    public boolean indexPage() {
        if (!connect()) return false;
        Page page = savePage(document.connection().response().statusCode(), false);
        if (Objects.isNull(page)) return false;
        Lemmatizer lemmatizer = new Lemmatizer();
        String text = lemmatizer.deleteHtmlTags(document);
        Map<String, Integer> lemmas = lemmatizer.lemmatize(text);
        List<Lemma> lemmaList = new LinkedList<>();
        for (String l : lemmas.keySet()) {
            Lemma lemma = new Lemma();
            lemma.setSite(site);
            lemma.setLemma(l);
            lemma.setFrequency(1);
            Index index = new Index();
            index.setPage(page);
            index.setRank(lemmas.get(l));
            lemma.addIndex(index);
            lemmaList.add(lemma);
        }
        siteParserData.getDataSaver().saveIndexingData(page, lemmaList);
        return true;
    }
}
