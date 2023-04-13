import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import searchengine.Application;
import searchengine.model.*;
import searchengine.repositories.IndexRepositoryInterface;
import searchengine.repositories.LemmaRepositoryInterface;
import searchengine.repositories.PageRepositoryInterface;
import searchengine.repositories.SiteRepositoryInterface;
import searchengine.utils.DataSaver;
import searchengine.utils.Lemmatizer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SpringBootTest(classes = Application.class)
public class SpringRepositoryTest {

    @Autowired
    private SiteRepositoryInterface siteRepository;
    @Autowired
    private PageRepositoryInterface pageRepository;
    @Autowired
    private IndexRepositoryInterface indexRepository;
    @Autowired
    private LemmaRepositoryInterface lemmaRepository;
    @Autowired
    private DataSaver dataSaver;

    @Test
    public void siteRepositoryTest() throws IOException {
        Site site = new Site();
        site.setName("name");
        site.setStatus(SiteStatus.INDEXED);
        site.setUrl("https://lenta.ru");
        site.setStatusTime(LocalDateTime.now());
        site = siteRepository.save(site);
        Document document = Jsoup.connect(site.getUrl()).get();
        Lemmatizer lemmatizer = new Lemmatizer();
        Page page = new Page();
        page.setSite(site);
        page.setCode(document.connection().response().statusCode());
        page.setPath("/");
        page.setContent(document.toString());
        Map<String, Integer> lemmas = lemmatizer.lemmatize(lemmatizer.deleteHtmlTags(document));
        List<Lemma> lemmaList = new ArrayList<>();
        for (String l : lemmas.keySet()) {
            Lemma lemma = new Lemma();
            lemma.setSite(site);
            lemma.setFrequency(1);
            lemma.setLemma(l);
            Index index = new Index();
            index.setRank(lemmas.get(l));
            index.setPage(page);
            lemma.addIndex(index);
            lemmaList.add(lemma);
        }
        pageRepository.save(page);
        lemmaRepository.saveAll(lemmaList);
    }

    @Test
    public void deleteTest() {
        List<String> strings = new ArrayList<>();
        strings.addAll(new ArrayList<>());
    }

    @Test
    public void insertTest() throws IOException {
        Site site = siteRepository.findByUrl("https://lenta.ru").orElseThrow();
        List<Page> pageList = new ArrayList<>();
        List<Lemma> lemmaList = new ArrayList<>();
        Document document = Jsoup.connect(site.getUrl()).get();
        Lemmatizer lemmatizer = new Lemmatizer();
        Map<String, Integer> lemmaMap = lemmatizer.lemmatize(lemmatizer.deleteHtmlTags(document));
        for (int i = 0; i < 2; i++) {
            Page page = new Page();
            page.setSite(site);
            page.setCode(200);
            page.setContent("content");
            page.setPath("/");
            pageList.add(page);
            for (String l : lemmaMap.keySet()) {
                Lemma lemma = new Lemma();
                lemma.setSite(site);
                lemma.setLemma(l);
                lemma.setFrequency(1);
                Index index = new Index();
                index.setPage(page);
                index.setRank(lemmaMap.get(l));
                lemma.addIndex(index);
                lemmaList.add(lemma);
            }
        }
        pageList.forEach(System.err::println);
        Iterable<Page> pages = pageRepository.saveAll(pageList);
        pages.forEach(it -> System.out.println(it.getId()));
//        lemmaRepository.saveAll(lemmaList);
    }
}
