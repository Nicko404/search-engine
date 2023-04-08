package searchengine.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLInsert;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "page", indexes = @javax.persistence.Index(name = "path_index", columnList = "path, site_id", unique = true))
@SQLInsert(sql = "insert ignore into page (code, content, path, site_id, id) values (?, ?, ?, ?, ?)")
@EqualsAndHashCode(exclude = {"content"})
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqGen")
    @SequenceGenerator(name = "seqGen", sequenceName = "seq", initialValue = 1)
    private int id;

    @ManyToOne
    @JoinColumn(nullable = false, name = "site_id")
    private Site site;

    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String path;

    @Column(nullable = false)
    private int code;

    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;
}
