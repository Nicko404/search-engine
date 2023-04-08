package searchengine.model;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLInsert;

import javax.persistence.*;
import javax.persistence.Index;

@Getter
@Setter
@Entity
@Table(name = "lemma", indexes = @Index(name = "site_lemma_index", columnList = "lemma, site_id", unique = true))
@SQLInsert(sql = "insert into lemma (frequency, lemma, site_id) values (?, ?, ?) on duplicate key update " +
        "frequency = lemma.frequency + 1")
public class Lemma {

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
}
