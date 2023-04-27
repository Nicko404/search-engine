package searchengine.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
public class DataSaver {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;
    private final Object o = new Object();


    public Optional<Site> findSiteByUrl(String url) {
        return siteRepository.findByUrl(url);
    }

    public Optional<Page> findPageByPathAndSite(String path, Site site) {
        return pageRepository.findByPathAndSite(path, site);
    }

    public Site saveSite(Site site) {
        return siteRepository.save(site);
    }

    public void updateSite(Site site) {
        siteRepository.update(site.getStatus(), site.getStatusTime(), site.getLastError(), site.getId());
    }

    public void updateSite(Site site, Site.Status status, String lastError) {
        siteRepository.update(status, LocalDateTime.now(), lastError, site.getId());
    }

    public List<Lemma> findLemmaByLemmaListAndSite(Set<String> lemmaString, Site site) {
        return Objects.isNull(site) ?
                lemmaRepository.findByLemmaList(lemmaString) :
                lemmaRepository.findByLemmaListAndSite(lemmaString, site);
    }

    public Optional<List<Page>> findPagesByLemmaAndSite(String lemma, Site site) {
        return Objects.isNull(site) ? pageRepository.findByLemma(lemma)
                : pageRepository.findByLemmaAndSite(lemma, site);
    }

    public List<Index> findIndexByPageAndLemmaList(Page page, List<Lemma> lemmaList) {
        return indexRepository.findByPageAndLemmaList(page, lemmaList);
    }

    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    public boolean isPageSaved(String path, Site site) {
        return pageRepository.existsByPathAndSite(path, site);
    }

    public void saveIndexingData(List<Lemma> lemmaList, Page page) {
        synchronized (o) {
            if (pageRepository.existsByPathAndSite(page.getPath(), page.getSite())) return;
            pageRepository.save(page);
            lemmaRepository.saveAll(lemmaList);
        }
    }

    public void removeAllBySite(Site site) {
        indexRepository.removeAllBySite(site);
        lemmaRepository.removeAllBySite(site);
        pageRepository.removeAllBySite(site);
        siteRepository.remove(site.getId());
    }

    public List<Lemma> removeIndexByPage(Page page) {
        List<Lemma> result = indexRepository.findLemmaByPage(page);
        indexRepository.removeByPage(page);
        return result;
    }

    public void removeAllByPage(Page page) {
        List<Lemma> lemmaList = removeIndexByPage(page);
        decrementLemmaFrequencyByLemmaId(lemmaList);
        removeLemmaIfFrequencyIsZero();
        removePageByPathAndSite(page.getPath(), page.getSite());
    }

    public void removePageByPathAndSite(String path, Site site) {
        pageRepository.removeByPathAndSite(path, site);
    }

    public void removeLemmaIfFrequencyIsZero() {
        lemmaRepository.removeIfFrequencyIsZero();
    }

    public void decrementLemmaFrequencyByLemmaId(List<Lemma> lemmaList) {
        lemmaRepository.decrementFrequencyById(lemmaList);
    }

    public int countLemmaBySite(Site site) {
        return lemmaRepository.countBySite(site);
    }

    public int countPageBySite(Site site) {
        return pageRepository.countBySite(site);
    }

}