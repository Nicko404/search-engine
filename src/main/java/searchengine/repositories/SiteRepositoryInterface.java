package searchengine.repositories;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Site;
import searchengine.model.SiteStatus;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface SiteRepositoryInterface extends CrudRepository<Site, Integer> {

    @Modifying
    @Transactional
    @Query("update Site s set s.status = ?1, s.statusTime = ?2, s.lastError = ?3 where s.id = ?4")
    void update(SiteStatus status, LocalDateTime statusTime, String lastError, int id);

    Optional<Site> findByUrl(String url);

    @Modifying
    @Transactional
    @Query(value = "delete from Site s where s.id = :id")
    void remove(int id);
}
