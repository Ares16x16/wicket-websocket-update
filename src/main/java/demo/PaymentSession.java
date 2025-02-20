package demo;

import javax.persistence.*;

@Entity
@Table(name = "payment_sessions")
public class PaymentSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true)
    private String sessionId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "value", nullable = false)
    private int value;

    @Version
    @Column(name = "version")
    private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
