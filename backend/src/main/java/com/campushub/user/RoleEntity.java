package com.campushub.user;
import jakarta.persistence.*;
import lombok.Getter;
@Entity @Table(name="roles") @Getter
public class RoleEntity {
  @Id private Long id;
  @Enumerated(EnumType.STRING) @Column(nullable=false, unique=true) private RoleName name;
}
