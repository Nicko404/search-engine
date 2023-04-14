package searchengine.repositories;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;
import searchengine.model.Site;

import java.util.List;
import java.util.Set;

public interface LemmaRepositoryInterface extends CrudRepository<Lemma, Integer> {

    @Query(value = "from Lemma l where l.site = :site and l.lemma in (:lemmaList) order by l.frequency")
    List<Lemma> findByLemmaListAndSite(Set<String> lemmaList, Site site);

    @Query(value = "from Lemma l where l.lemma in (:lemmaList) order by l.frequency")
    List<Lemma> findByLemmaList(Set<String> lemmaList);

    @Modifying
    @Transactional
    @Query(value = "delete from Lemma l where l.site = :site")
    void removeAllBySite(Site site);

    @Modifying
    @Transactional
    @Query(value = "update Lemma l set l.frequency = l.frequency - 1 where l in (:lemmaList)")
    void decrementFrequencyById(List<Lemma> lemmaList);

    @Modifying
    @Transactional
    @Query(value = "delete from Lemma l where l.frequency <= 0")
    void removeIfFrequencyIsZero();

    int countBySite(Site site);
}
