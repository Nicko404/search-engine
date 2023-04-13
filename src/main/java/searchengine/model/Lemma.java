package searchengine.model;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLInsert;

import javax.persistence.*;
import javax.persistence.Index;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
@Entity
@Table(name = "lemma", indexes = @Index(name = "site_lemma_index", columnList = "lemma, site_id", unique = true))
@SQLInsert(sql = "insert into lemma (frequency, lemma, site_id) values (?, ?, ?) on duplicate key update " +
        "frequency = lemma.frequency + 1")
public class Lemma implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(nullable = false)
    private String lemma;

    @Column(nullable = false)
    private int frequency;

    @OneToMany(mappedBy = "lemma", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<searchengine.model.Index> indexes = new ArrayList<>();

    public void addIndex(searchengine.model.Index index) {
        indexes.add(index);
        index.setLemma(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Lemma lemma1 = (Lemma) o;
        return frequency == lemma1.frequency && Objects.equals(site, lemma1.site) && Objects.equals(lemma, lemma1.lemma);
    }

    @Override
    public int hashCode() {
        return Objects.hash(site, lemma, frequency);
    }
}
