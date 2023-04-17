package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import searchengine.config.SiteParserData;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.model.*;
import searchengine.utils.DataSaver;
import searchengine.utils.Lemmatizer;
import searchengine.utils.SiteParser;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
        new Thread(() -> {
            try {
                service.shutdown();
                for (ForkJoinPool pool : new HashSet<>(poolParserMap.keySet())) {
                    pool.shutdown();
                    Site site = poolParserMap.get(pool).getSite();
                    Thread.sleep(3_000);
                    site.setLastError("Индексация остановлена пользователем");
                    site.setStatus(SiteStatus.FAILED);
                    site.setStatusTime(LocalDateTime.now());
                    dataSaver.updateSite(site);
                    dataSaver.clear(site);
                    poolParserMap.remove(pool);
                }
                dataSaver.flush();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
        response.setResult(true);
        return response;
    }

    @Override
    public IndexingResponse indexPage(String url) {
        IndexingResponse response = new IndexingResponse();
        searchengine.config.Site siteConf = configContainsUrl(siteUrlToBaseForm(url));
        if (Objects.nonNull(siteConf)) {
            url = siteUrlToBaseForm(url);
            String path = url.substring(siteUrlToBaseForm(siteConf.getUrl()).length());
            Optional<Site> siteOptional = dataSaver.findSiteByUrl(url.substring(0, url.length() - path.length()));
            Site site = siteOptional.orElseGet(() -> saveSite(siteConf));
            Optional<Page> pageOptional = dataSaver.findPageByPathAndSite(path, site);
            if (pageOptional.isPresent()) {
                Page page = pageOptional.get();
                List<Lemma> lemmaList = dataSaver.removeIndexByPage(page);
                dataSaver.decrementLemmaFrequencyByLemmaId(lemmaList);
                dataSaver.removeLemmaIfFrequencyIsZero();
                dataSaver.removePageByPathAndSite(path, site);
                dataSaver.removePathFromMap(page);
            }
            SiteParser parser = new SiteParser(path, site, siteParserData);
            if (parser.indexPage()) {
                if (!indexingStarted) dataSaver.flush();
                response.setResult(true);
                return response;
            }
            response.setResult(false);
            response.setError("Не удалось проиндексировать страницу");
        } else {
            response.setResult(false);
            response.setError("Данная страница находится за пределами сайтов, " +
                    "указанных в конфигурационном файле\n");
        }
        return response;
    }

    @Override
    public SearchResponse search(String site, String query) {
        Lemmatizer lemmatizer = new Lemmatizer();
        Set<String> queryLemmas = lemmatizer.lemmatize(query).keySet();
        Site siteObj = dataSaver.findSiteByUrl(site).orElse(null);
        List<Lemma> lemmaList = dataSaver.findLemmaByLemmaListAndSite(queryLemmas, siteObj);
        List<Page> pageList;
        Set<Page> result = new HashSet<>();
        if (Objects.isNull(siteObj)) {
            pageList = dataSaver.findIndexByLemmaString(lemmaList.get(0).getLemma()).stream()
                    .map(Index::getPage).toList();
            for (Lemma lemma : lemmaList) {
                result.clear();
                for (Page page : pageList) {
                    if (dataSaver.indexExistsByLemmaStringAndPage(lemma.getLemma(), page)) {
                        result.add(page);
                    }
                }
                pageList = new ArrayList<>(result);
            }
        } else {
            pageList = dataSaver.findIndexByLemmaAndSite(lemmaList.get(0), siteObj).stream()
                    .map(Index::getPage).toList();
            for (Lemma lemma : lemmaList) {
                result.clear();
                for (Page page : pageList) {
                    if (dataSaver.indexExistsByLemmaAndPage(lemma, page)) {
                        result.add(page);
                    }
                }
                pageList = new ArrayList<>(result);
            }
        }
        SearchResponse response = new SearchResponse();
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
        Document document = Jsoup.parse(content);
        return document.title();
    }

    private String selectSnippet(String content, String[] lemmaList) {
        Document document = Jsoup.parse(content);
        Elements elements = document.getAllElements();
        for (String lemma : lemmaList) {
            String core = lemma.substring(0, lemma.length() / 2 + 1);
            for (Element element : elements) {
                String text = element.text();
                if (text.contains(core)) {
                    int word = text.indexOf(core);
                    int end = text.indexOf(".", word) + 1;
                    String begin = text.substring(0, word);
                    int start = begin.lastIndexOf(".") + 1;
                    return "<b>" + text.substring(start, end) + "</b>";
                }
            }
        }
        return null;
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
        site.setStatus(SiteStatus.INDEXING);
        site.setStatusTime(LocalDateTime.now());
        return dataSaver.saveSite(site);
    }

    private String siteUrlToBaseForm(String url) {
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
                    site.setLastError(site.getLastError());
                    site.setStatus(SiteStatus.INDEXED);
                    site.setStatusTime(LocalDateTime.now());
                    dataSaver.updateSite(site);
                    dataSaver.clear(site);
                    poolParserMap.remove(pool);
                    continue;
                }
                if (parser.isCompletedAbnormally()) {
                    Site site = parser.getSite();
                    site.setLastError(site.getLastError());
                    site.setStatus(SiteStatus.FAILED);
                    site.setStatusTime(LocalDateTime.now());
                    dataSaver.updateSite(site);
                    dataSaver.clear(site);
                    poolParserMap.remove(pool);
                }
            }
            if (poolParserMap.size() == 0) {
                indexingStarted = false;
                dataSaver.flush();
                service.shutdown();
            }
        }, 10000, 10000, TimeUnit.MILLISECONDS);
    }
}