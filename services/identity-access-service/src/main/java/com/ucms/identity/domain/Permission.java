package com.ucms.identity.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "permissions")
public class Permission extends AuditableNoSoftDelete {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String module;

    @Column(name = "action_key", nullable = false)
    private String actionKey;

    public UUID getId() { return id; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getActionKey() { return actionKey; }
    public void setActionKey(String actionKey) { this.actionKey = actionKey; }
}
