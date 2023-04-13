package searchengine.repositories;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;
import searchengine.model.Site;

public interface LemmaRepositoryInterface extends CrudRepository<Lemma, Integer> {

    @Modifying
    @Transactional
    @Query(value = "delete from Lemma l where l.site = :site")
    void removeAllBySite(Site site);

    @Modifying
    @Transactional
    @Query(value = "update Lemma l set l.frequency = l.frequency - 1 where l.id = :id")
    void decrementFrequencyById(int id);

    @Modifying
    @Transactional
    @Query(value = "delete from Lemma l where l.frequency <= 0")
    void removeIfFrequencyIsZero();
}
