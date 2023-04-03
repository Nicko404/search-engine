package searchengine.repositories;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Component;
import searchengine.model.Site;
import searchengine.model.SiteStatus;

import javax.persistence.NoResultException;
import javax.persistence.Query;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Component
public class SiteRepository {


    private final SessionFactory sessionFactory;
    private static final Logger logger = LogManager.getLogger(SiteRepository.class);

    public SiteRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public Site save(Site site) {
        Session session = null;
        try {
            session = sessionFactory.openSession();
            session.beginTransaction();
            Serializable savedSite = session.save(site);
            site = session.get(Site.class, savedSite);
            session.getTransaction().commit();
        } catch (Exception e) {
            if (Objects.nonNull(session) && Objects.nonNull(session.getTransaction())) {
                session.getTransaction().rollback();
            }
            logger.warn("Failed to save site: " + site.getName() + " - " + e.getMessage());
        } finally {
            if (Objects.nonNull(session)) {
                session.close();
            }
        }
        return site;
    }

    public void update(Site site) {
        Session session = null;
        try {
            session = sessionFactory.openSession();
            session.beginTransaction();
            session.update(site);
            session.getTransaction().commit();
        } catch (Exception e) {
            if (Objects.nonNull(session) && Objects.nonNull(session.getTransaction())) {
                session.getTransaction().rollback();
            }
            logger.warn("Failed to update site: " + site.getName() + " - " + e.getMessage());
        } finally {
            if (Objects.nonNull(session)) {
                session.close();
            }
        }
    }

    public void update(Site site, SiteStatus status, String lastError) {
        Session session = null;
        site.setStatus(status);
        site.setLastError(lastError);
        site.setStatusTime(LocalDateTime.now());
        try {
            session = sessionFactory.openSession();
            session.beginTransaction();
            session.update(site);
            session.getTransaction().commit();
        } catch (Exception e) {
            if (Objects.nonNull(session) && Objects.nonNull(session.getTransaction())) {
                session.getTransaction().rollback();
            }
            logger.warn("Failed to update site: " + site.getName() + " - "  + e.getMessage());
        } finally {
            if (Objects.nonNull(session)) {
                session.close();
            }
        }
    }

    public Site findByUrl(String url) {
        String sql = "from Site s where s.url = :url";
        Site site = null;
        Session session = null;
        try {
            session = sessionFactory.openSession();
            Query query = session.createQuery(sql);
            query.setParameter("url", url);
            site = (Site) query.getSingleResult();
        } catch (NoResultException ex) {
            return null;
        } catch (Exception e) {
            logger.warn("Failed to find site: " + url + " - " + e.getMessage());
        } finally {
            if (Objects.nonNull(session)) {
                session.close();
            }
        }
        return site;
    }

    public void remove(Site site) {
        String sql = "delete from Site s where s.id = :id";
        Session session = null;
        try {
            session = sessionFactory.openSession();
            session.beginTransaction();
            Query query = session.createQuery(sql);
            query.setParameter("id", site.getId());
            query.executeUpdate();
            session.getTransaction().commit();
        } catch (Exception e) {
            if (Objects.nonNull(session) && Objects.nonNull(session.getTransaction())) {
                session.getTransaction().rollback();
            }
            logger.warn("Failed to remove site: " + site.getName() + " - " + e.getMessage());
        } finally {
            if (Objects.nonNull(session)) {
                session.close();
            }
        }
    }
}
