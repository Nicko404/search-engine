package searchengine.repositories;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Site;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface SiteRepository extends CrudRepository<Site, Integer> {

    @Modifying
    @Transactional
    @Query("update Site s set s.status = :status, s.statusTime = :statusTime, s.lastError = :lastError where s.id = :id")
    void update(Site.Status status, LocalDateTime statusTime, String lastError, int id);

    Optional<Site> findByUrl(String url);

    @Modifying
    @Transactional
    @Query(value = "delete from Site s where s.id = :id")
    void remove(int id);
}
