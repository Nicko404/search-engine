package searchengine.repositories;

import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;

import javax.persistence.Query;
import java.util.List;
import java.util.Objects;

@Repository
@RequiredArgsConstructor
public class IndexRepository {

    private final SessionFactory sessionFactory;

    public void save(Index index) {
        Session session = null;
        try {
            session = sessionFactory.openSession();
            session.beginTransaction();
            session.save(index);
            session.getTransaction().commit();
        } catch (Exception e) {
            if (Objects.nonNull(session) && Objects.nonNull(session.getTransaction())) {
                session.getTransaction().rollback();
            }
//            e.printStackTrace();
        } finally {
            if (Objects.nonNull(session)) {
                session.close();
            }
        }
    }

    public List<Index> findByPageAndLemmaList(Page page, List<Lemma> lemmas) {
        List<Index> indexes = null;
        String sql = "from Index i where i.page = :page and i.lemma in (:lemmas)";
        Session session = null;
        try {
            session = sessionFactory.openSession();
            Query query = session.createQuery(sql);
            query.setParameter("page", page);
            query.setParameter("lemmas", lemmas);
            indexes = query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (Objects.nonNull(session)) {
                session.close();
            }
        }
        return indexes;
    }

    public List<Index> findByLemmaAndSite(Lemma lemma, Site site) {
        List<Index> indexes = null;
        String sql = Objects.isNull(site) ? "from Index i where i.lemma.lemma = :lemma" :
                "from Index i where i.lemma = :lemma and i.lemma.site = :site";
        Session session = null;
        try {
            session = sessionFactory.openSession();
            Query query = session.createQuery(sql);
            if (Objects.nonNull(site)) {
                query.setParameter("lemma", lemma);
                query.setParameter("site", site);
            } else {
                query.setParameter("lemma", lemma.getLemma());
            }
            indexes = query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (Objects.nonNull(session)) {
                session.close();
            }
        }
        return indexes;
    }

    public List<Index> findByLemmaStr(Lemma lemma) {
        String sql = "from Index i where i.lemma.lemma = :lemma";
        Session session = null;
        List<Index> result = null;
        try {
            session = sessionFactory.openSession();
            Query query = session.createQuery(sql);
            query.setParameter("lemma", lemma.getLemma());
            result = query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (Objects.nonNull(session)) {
                session.close();
            }
        }
        return result;
    }

    public boolean existsByLemmaStrAndPage(String lemma, Page page) {
        String sql = "select count(*) from Index i where i.lemma.lemma = :lemma and i.page = :page";
        Session session = null;
        int result = 0;
        try {
            session = sessionFactory.openSession();
            Query query = session.createQuery(sql);
            query.setParameter("lemma", lemma);
            query.setParameter("page", page);
            result = ((Long) query.getSingleResult()).intValue();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (Objects.nonNull(session)) {
                session.close();
            }
        }
        return result > 0;
    }

    public boolean existsByLemmaIdAndPage(Lemma lemma, Page page) {
        String sql = "select count(*) from Index i where i.lemma = :lemma and i.page = :page";
        Session session = null;
        int result = 0;
        try {
            session = sessionFactory.openSession();
            Query query = session.createQuery(sql);
            query.setParameter("lemma", lemma);
            query.setParameter("page", page);
            result = ((Long) query.getSingleResult()).intValue();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (Objects.nonNull(session)) {
                session.close();
            }
        }
        return result > 0;
    }

    public List<Lemma> removeByPage(Page page) {
        List<Lemma> result = null;
        String findSql = "select i.lemma from Index i where i.page = :page";
        String sql = "delete from Index i where i.page = :page";
        Session session = null;
        try {
            session = sessionFactory.openSession();
            session.beginTransaction();
            Query query = session.createQuery(sql);
            Query findQuery = session.createQuery(findSql);
            findQuery.setParameter("page", page);
            result = findQuery.getResultList();
            query.setParameter("page", page);
            query.executeUpdate();
            session.getTransaction().commit();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (Objects.nonNull(session)) {
                session.close();
            }
        }
        return result;
    }

    public void removeBySite(Site site) {
        String sql = "delete  from Index i where i.page in (from Page p where p.site = :site)";
        Session session = null;
        try {
            session = sessionFactory.openSession();
            session.beginTransaction();
            Query query = session.createQuery(sql);
            query.setParameter("site", site);
            query.executeUpdate();
            session.getTransaction().commit();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (Objects.nonNull(session)) {
                session.close();
            }
        }
    }
}
