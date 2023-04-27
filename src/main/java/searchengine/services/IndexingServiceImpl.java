package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.config.SiteParserData;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.model.*;
import searchengine.utils.DataSaver;
import searchengine.utils.LemmaFinder;
import searchengine.utils.SiteParser;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    @Getter
    private static volatile boolean indexingStarted = false;
    private final SitesList sites;
    private final DataSaver dataSaver;
    private final SiteParserData siteParserData;
    private final Map<ForkJoinPool, SiteParser> poolParserMap = new HashMap<>();
    private ScheduledExecutorService service;
    private static final Logger logger = LogManager.getLogger(IndexingServiceImpl.class);


    @Override
    public IndexingResponse startIndexing() {
        IndexingResponse response = new IndexingResponse();
        if (indexingStarted) {
            response.setResult(false);
            response.setError("Индексация уже запущена.");
            return response;
        }
        indexingStarted = true;
        new Thread(() -> {
            for (searchengine.config.Site siteConf : sites.getSites()) {
                deleteAllSiteData(siteUrlToBaseForm(siteConf.getUrl()));
                ForkJoinPool pool = new ForkJoinPool();
                SiteParser parser = new SiteParser("/", saveSite(siteConf), siteParserData);
                poolParserMap.put(pool, parser);
                pool.execute(parser);
            }
            startCheckForEndingIndexing();
        }).start();
        response.setResult(true);
        return response;
    }

    @Override
    public IndexingResponse stopIndexing() {
        IndexingResponse response = new IndexingResponse();
        if (!indexingStarted) {
            response.setResult(false);
            response.setError("Индексация ещё не запущена.");
            return response;
        }
        indexingStarted = false;
        service.shutdownNow();
        new Thread(() -> {
            for (ForkJoinPool pool : new HashSet<>(poolParserMap.keySet())) {
                pool.shutdown();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    logger.warn("Interrupted Exception in StopIndexingMethod");
                }
                Site site = poolParserMap.get(pool).getSite();
                dataSaver.updateSite(site, Site.Status.FAILED, "Индексация остановлена пользователем");
                poolParserMap.remove(pool);
            }
        }).start();
        response.setResult(true);
        return response;
    }

    @Override
    public IndexingResponse indexPage(String uri) {
        IndexingResponse response = new IndexingResponse();
        uri = siteUrlToBaseForm(uri);
        searchengine.config.Site siteConf = configContainsUrl(uri);
        if (Objects.nonNull(siteConf)) {
            String url = siteUrlToBaseForm(siteConf.getUrl());
            String path = uri.substring(url.length());
            Optional<Site> siteOptional = dataSaver.findSiteByUrl(url);
            Site site = siteOptional.orElseGet(() -> saveSite(siteConf));
            Optional<Page> pageOptional = dataSaver.findPageByPathAndSite(path, site);
            pageOptional.ifPresent(dataSaver::removeAllByPage);
            SiteParser parser = new SiteParser(path, site, siteParserData);
            if (parser.indexPage()) {
                response.setResult(true);
                return response;
            }
            response.setError("Не удалось проиндексировать страницу");
        } else {
            response.setError("Данная страница находится за пределами сайтов, " +
                    "указанных в конфигурационном файле");
        }
        response.setResult(false);
        return response;
    }

    private List<Page> selectPageWithLemma(List<Lemma> lemmaList, List<Page> pageList) {
        if (lemmaList.isEmpty()) return pageList;
        List<Page> result = new ArrayList<>();
        for (Page page : pageList) {
            for (Index index : page.getIndexes())
                if (index.getLemma().getLemma().equals(lemmaList.get(0).getLemma())) result.add(index.getPage());
        }
        lemmaList.remove(0);
        return selectPageWithLemma(lemmaList, result);
    }

    @Override
    public SearchResponse search(String site, String query) {
        SearchResponse response = new SearchResponse();
        if (query.isBlank()) {
            response.setResult(false);
            response.setError("Задан пустой поисковый запрос");
            return response;
        }
        Set<String> queryLemmas = LemmaFinder.findLemma(query).keySet();
        Site siteObj = dataSaver.findSiteByUrl(site).orElse(null);
        List<Lemma> lemmaList = dataSaver.findLemmaByLemmaListAndSite(queryLemmas, siteObj);
        if (lemmaList.isEmpty()) {
            response.setResult(true);
            return response;
        }
        String lemma = lemmaList.remove(0).getLemma();
        Optional<List<Page>> pageList = dataSaver.findPagesByLemmaAndSite(lemma, siteObj);
        List<Page> result = selectPageWithLemma(lemmaList, pageList.orElse(new ArrayList<>()));
        response.setResult(true);
        response.setCount(result.size());
        response.setData(new ArrayList<>());
        for (Page page : result) {
            SearchData data = new SearchData();
            data.setUri(page.getPath());
            data.setSite(page.getSite().getUrl());
            data.setSiteName(page.getSite().getName());
            data.setTitle(selectTitle(page.getContent()));
            data.setSnippet(selectSnippet(page.getContent(), query.split("\\s")));
            data.setRelevance(calculateRelevance(page, lemmaList));
            response.getData().add(data);
        }
        response.getData().sort((d1, d2) -> (int) (d2.getRelevance() - d1.getRelevance()));
        return response;
    }

    private String selectTitle(String content) {
        String regex = "<title>[\\s\\S]*</title>";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            int start = matcher.start() + 7;
            int end = matcher.end() - 8;
            return content.substring(start, end);
        }
        return "Default Title";
    }

    private String selectSnippet(String content, String[] lemmaList) {
        StringBuilder result = new StringBuilder();
        content = Jsoup.parse(content).text();
        String contentLowerCase = content.toLowerCase();
        StringBuilder regex = new StringBuilder("\\S*\\s\\S*\\s");
        for (String lemma : lemmaList) {
            regex.append(lemma.length() > 4 ? lemma.substring(0, lemma.length() / 2 + 1).toLowerCase() : lemma.toLowerCase())
                    .append("\\S*\\s");
        }
        regex.append("\\S*");
        Pattern pattern = Pattern.compile(regex.toString());
        Matcher matcher = pattern.matcher(contentLowerCase);
        while (matcher.find()) {
            if (result.length() > 200) break;
            int start = matcher.start();
            int end = matcher.end();
            result.append("<b>")
                    .append(content, start, end)
                    .append("...</b> ");
        }
        if (result.isEmpty()) {
            for (String lemma : lemmaList) {
                if (lemma.length() == 1) continue;
                if (result.length() > 200) break;
                regex.delete(0, regex.length());
                regex.append("\\S*\\s\\S*")
                        .append(lemma)
                        .append("\\S*\\s\\S*");
                pattern = Pattern.compile(regex.toString());
                matcher = pattern.matcher(contentLowerCase);
                if (matcher.find()) {
                    int start = matcher.start();
                    int end = matcher.end();
                    result.append("<b>")
                            .append(content, start, end)
                            .append("...</b> ");
                }
            }
        }
        return result.toString();
    }

    private float calculateRelevance(Page page, List<Lemma> lemmaList) {
        List<Index> indexList = dataSaver.findIndexByPageAndLemmaList(page, lemmaList);
        float result = 0f;
        for (Index index : indexList) {
            result += index.getRank();
        }
        return result;
    }

    private searchengine.config.Site configContainsUrl(String url) {
        for (searchengine.config.Site site : sites.getSites()) {
            if (url.startsWith(siteUrlToBaseForm(site.getUrl())))
                return site;
        }
        return null;
    }

    private Site saveSite(searchengine.config.Site siteConf) {
        Site site = new Site();
        site.setUrl(siteUrlToBaseForm(siteConf.getUrl()));
        site.setName(siteConf.getName());
        site.setStatus(indexingStarted ? Site.Status.INDEXING : Site.Status.INDEXED);
        site.setStatusTime(LocalDateTime.now());
        return dataSaver.saveSite(site);
    }

    public static String siteUrlToBaseForm(String url) {
        return url.replace("www.", "");
    }

    private void deleteAllSiteData(String url) {
        Optional<Site> siteOptional = dataSaver.findSiteByUrl(url);
        siteOptional.ifPresent(dataSaver::removeAllBySite);
    }

    private void startCheckForEndingIndexing() {
        service = Executors.newScheduledThreadPool(1);
        service.scheduleAtFixedRate(() -> {
            for (ForkJoinPool pool : new HashSet<>(poolParserMap.keySet())) {
                SiteParser parser = poolParserMap.get(pool);
                if (parser.isCompletedNormally()) {
                    Site site = parser.getSite();
                    dataSaver.updateSite(site, Site.Status.INDEXED, site.getLastError());
                    poolParserMap.remove(pool);
                    continue;
                }
                if (parser.isCompletedAbnormally()) {
                    Site site = parser.getSite();
                    dataSaver.updateSite(site, Site.Status.FAILED, site.getLastError());
                    poolParserMap.remove(pool);
                }
            }
            if (poolParserMap.size() == 0) {
                indexingStarted = false;
                service.shutdown();
            }
        }, 10000, 10000, TimeUnit.MILLISECONDS);
    }
}