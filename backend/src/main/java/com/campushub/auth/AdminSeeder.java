package com.campushub.auth;
import com.campushub.common.Db;
import com.campushub.user.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
/** Creates the first SUPER_ADMIN from ADMIN_EMAIL / ADMIN_PASSWORD if missing. There is no public way to create admins. */
@Component
public class AdminSeeder implements CommandLineRunner {
  private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);
  private final UserRepository users; private final RoleRepository roles; private final PasswordEncoder enc; private final Db db;
  @Value("${app.admin.email:}") String email; @Value("${app.admin.password:}") String password;
  public AdminSeeder(UserRepository u, RoleRepository r, PasswordEncoder e, Db db){ users=u; roles=r; enc=e; this.db=db; }
  @Override @Transactional public void run(String... args){
    if (email.isBlank() || password.length() < 12 || users.existsByEmailIgnoreCase(email)) return;
    User u = new User(); u.setEmail(email.toLowerCase()); u.setFullName("Platform Admin"); u.setPasswordHash(enc.encode(password));
    u.setEmailVerified(true); u.setPhoneVerified(true); u.getRoles().add(roles.findByName(RoleName.SUPER_ADMIN).orElseThrow()); users.save(u);
    db.update("INSERT IGNORE INTO notification_preferences(user_id) VALUES (?)", u.getId());
    log.info("Seeded SUPER_ADMIN {}", email);
  }
}
