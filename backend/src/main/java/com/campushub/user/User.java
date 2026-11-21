package com.campushub.user;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;
import org.hibernate.annotations.*;

@Entity @Table(name="users") @Getter @Setter @NoArgsConstructor
public class User {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(nullable=false, unique=true) private String email;
  @Column(name="password_hash", nullable=false) private String passwordHash;
  @Column(name="full_name", nullable=false) private String fullName;
  private String phone;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private UserStatus status = UserStatus.ACTIVE;
  @Column(name="email_verified") private boolean emailVerified;
  @Column(name="phone_verified") private boolean phoneVerified;
  @Column(name="failed_logins") private int failedLogins;
  @ManyToMany(fetch=FetchType.EAGER)
  @JoinTable(name="user_roles", joinColumns=@JoinColumn(name="user_id"), inverseJoinColumns=@JoinColumn(name="role_id"))
  private Set<RoleEntity> roles = new HashSet<>();
  @CreationTimestamp @Column(name="created_at", updatable=false) private Instant createdAt;
  @UpdateTimestamp @Column(name="updated_at") private Instant updatedAt;
  public Set<RoleName> roleNames(){ Set<RoleName> s = new HashSet<>(); roles.forEach(r -> s.add(r.getName())); return s; }
}
