package com.nerd.erp.domain.tenancy;

import com.nerd.erp.domain.shared.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "tenant")
@Getter
@Setter
public class Tenant extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(nullable = false)
    private boolean active = true;

    // --- Identité visuelle de l'écran de connexion (exposée publiquement par slug) ---

    /** Couleur d'accent au format #RRGGBB. */
    @Column(name = "brand_color", length = 7)
    private String brandColor;

    @Column(name = "logo_url", length = 2048)
    private String logoUrl;

    @Column(name = "login_tagline", length = 255)
    private String loginTagline;
}
