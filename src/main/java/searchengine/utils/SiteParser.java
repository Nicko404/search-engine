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
                siteParserData.getDataSaver().isPageSaved(path, site) || !indexPage()) return;
        Elements aElements = document.getElementsByTag("a");
        document = null;
        List<SiteParser> parsers = new ArrayList<>();
        for (Element element : aElements) {
            String path = element.attr("href");
            if (!isCorrectPath(path)) continue;
            if (path.startsWith("http")) path = path.replace(site.getUrl(), "");
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
                    siteParserData.getDataSaver().
                            updateSite(site, Site.Status.FAILED, "Ошибка индексации: требуется авторизация");
                    this.completeExceptionally(null);
                }
                savePage(statusCode, true);
            }
            if (e instanceof UnknownHostException) {
                siteParserData.getDataSaver()
                        .updateSite(site, Site.Status.FAILED, "Ошибка подключиня: указан не верный хост");
                this.completeExceptionally(null);
            }
            if (e instanceof SocketTimeoutException) {
                String errorMessage = uri.equals(site.getUrl())
                        ? "Ошибка индексации: главная страница сайта не доступна"
                        : "Ошибка индексации: страница не доступна";
                siteParserData.getDataSaver()
                                .updateSite(site, Site.Status.FAILED, errorMessage);
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
        site.setStatus(IndexingServiceImpl.isIndexingStarted() ? Site.Status.INDEXING : Site.Status.INDEXED);
        siteParserData.getDataSaver().updateSite(site);
        if (isFailed) siteParserData.getDataSaver().saveIndexingData(new ArrayList<>(), page);
        return page;
    }

    public boolean indexPage() {
        if (!connect()) return false;
        Page page = savePage(document.connection().response().statusCode(), false);
        if (Objects.isNull(page)) return false;
        String text = LemmaFinder.deleteHtmlTags(document);
        Map<String, Integer> lemmas = LemmaFinder.findLemma(text);
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
        siteParserData.getDataSaver().saveIndexingData(lemmaList, page);
        return true;
    }
}
