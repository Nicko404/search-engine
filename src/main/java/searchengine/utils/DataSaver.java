package searchengine.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepositoryInterface;
import searchengine.repositories.LemmaRepositoryInterface;
import searchengine.repositories.PageRepositoryInterface;
import searchengine.repositories.SiteRepositoryInterface;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class DataSaver {

    private final SiteRepositoryInterface siteRepository;
    private final PageRepositoryInterface pageRepository;
    private final IndexRepositoryInterface indexRepository;
    private final LemmaRepositoryInterface lemmaRepository;
    private final Map<Site, Set<String>> sitePaths = new ConcurrentHashMap<>();
    private final Set<Page> pageList = Collections.synchronizedSet(new HashSet<>(110, 1));
    private final Set<Lemma> lemmaList = Collections.synchronizedSet(new HashSet<>());
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

    public List<Lemma> findLemmaByLemmaListAndSite(Set<String> lemmaString, Site site) {
        return Objects.isNull(site) ?
                lemmaRepository.findByLemmaList(lemmaString) :
                lemmaRepository.findByLemmaListAndSite(lemmaString, site);
    }

    public List<Index> findIndexByLemmaString(String lemma) {
        return indexRepository.findByLemmaString(lemma);
    }

    public List<Index> findIndexByLemmaAndSite(Lemma lemma, Site site) {
        return indexRepository.findByLemmaAndSite(lemma, site);
    }

    public boolean indexExistsByLemmaAndPage(Lemma lemma, Page page) {
        return indexRepository.countByLemmaAndPage(lemma, page) > 0;
    }

    public List<Index> findIndexByPageAndLemmaList(Page page, List<Lemma> lemmaList) {
        return indexRepository.findByPageAndLemmaList(page, lemmaList);
    }

    public boolean indexExistsByLemmaStringAndPage(String lemma, Page page) {
        return indexRepository.countByLemmaStringAndPage(lemma, page) > 0;
    }

    public boolean isPageSaved(String path, Site site) {
        Set<String> paths = sitePaths.get(site);
        return !(Objects.isNull(paths) || !paths.contains(path));
    }

    public void saveIndexingData(Page page, List<Lemma> lemmaList) {
        Set<String> paths;
        if (sitePaths.containsKey(page.getSite())) {
            paths = sitePaths.get(page.getSite());
        } else {
            paths = Collections.synchronizedSet(new HashSet<>());
            sitePaths.put(page.getSite(), paths);
        }
        synchronized (o) {
            if (paths.add(page.getPath()) && pageList.add(page)) {
                addLemmas(lemmaList);
                if (pageList.size() >= 100) flush();
            }
        }
    }

    private void addLemmas(List<Lemma> lemmaList) {
        for (Lemma lemma : lemmaList) {
            if (!this.lemmaList.add(lemma)) {
                for (Lemma target : this.lemmaList) {
                    if (target.equals(lemma)) {
                        target.setFrequency(target.getFrequency() + 1);
                        lemma.getIndexes().forEach(target::addIndex);
                    }
                }
            }
        }
    }

    public void flush() {
        synchronized (o) {
            try {
                pageRepository.saveAll(pageList);
                lemmaRepository.saveAll(lemmaList);
                lemmaList.clear();
                pageList.clear();
            } catch (Exception e) {
                e.printStackTrace();
            }
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

    public void clear(Site site) {
        Set<String> paths = sitePaths.get(site);
        if (Objects.nonNull(paths)) {
            paths.clear();
        }
        sitePaths.remove(site);
    }

    public void removePathFromMap(Page page) {
        Set<String> paths = sitePaths.get(page.getSite());
        if (Objects.nonNull(paths)) {
            paths.remove(page.getPath());
        }
    }
}