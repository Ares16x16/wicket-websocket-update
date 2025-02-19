package demo;

import javax.persistence.*;

@Entity
@Table(name = "value_entries") 
public class ValueEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    
    @Column(name = "value", nullable = false)
    private int value;
    
    @Version
    @Column(name = "version")
    private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
