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
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.SiteStatus;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.IndexingServiceImpl;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.RecursiveAction;

@Getter
public class SiteParser extends RecursiveAction {

    private final String path;
    private final Site site;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final SiteParserData siteParserData;
    private final String uri;
    private Document document;
    private static final Logger logger = LogManager.getLogger(SiteParser.class);

    public SiteParser(String path, Site site, SiteParserData siteParserData) {
        this.path = path;
        this.site = site;
        this.siteParserData = siteParserData;
        this.pageRepository = siteParserData.getPageRepository();
        this.siteRepository = siteParserData.getSiteRepository();
        uri = site.getUrl() + path;
    }


    @Override
    protected void compute() {
        if (!IndexingServiceImpl.isIndexingStarted() || pageRepository.isPageSaved(path, site) || !connect()) return;
        savePage(document.connection().response().statusCode());
        Elements aElements = document.getElementsByTag("a");
        document = null;
        List<SiteParser> parsers = new ArrayList<>();
        for (Element element : aElements) {
            String path = element.attr("href");
            if (!isCorrectPath(path)) continue;
            if (path.startsWith("http")) {
                path = path.replace(site.getUrl(), "");
            }
            if (pageRepository.isPageSaved(path, site)) continue;
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
                    siteRepository.update(site, SiteStatus.FAILED, "Ошибка индексации: требуется авторизация");
                    this.completeExceptionally(null);
                }
                savePage(statusCode);
            }
            if (e instanceof UnknownHostException) {
                siteRepository.update(site, SiteStatus.FAILED, "Ошибка подключиня: указан не верный хост");
                this.completeExceptionally(null);
            }
            if (e instanceof SocketTimeoutException) {
                String errorMessage = uri.equals(site.getUrl())
                        ? "Ошибка индексации: главная страница сайта не доступна"
                        : "Ошибка индексации: страница не доступна";
                siteRepository.update(site, SiteStatus.FAILED, errorMessage);
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

    private void savePage(int statusCode) {
        Page page = new Page();
        page.setSite(site);
        page.setPath(path);
        page.setCode(statusCode);
        page.setContent(Objects.isNull(document) ? "<Default Content>" : document.text());
        if (pageRepository.savePage(page)) siteRepository.update(
                site,
                IndexingServiceImpl.isIndexingStarted() ? SiteStatus.INDEXING : site.getStatus(),
                site.getLastError());
    }
}
