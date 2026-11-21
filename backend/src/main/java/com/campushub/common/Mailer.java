package com.campushub.common;
import org.slf4j.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
@Service
public class Mailer {
  private static final Logger log = LoggerFactory.getLogger(Mailer.class);
  private final ObjectProvider<JavaMailSender> sender;
  @Value("${app.mail-from}") private String from;
  public Mailer(ObjectProvider<JavaMailSender> s){ sender = s; }
  public void send(String to, String subject, String body){
    JavaMailSender s = sender.getIfAvailable();
    if (s == null) { log.info("MAIL not sent (SMTP not configured). to={} subject={} body={}", to, subject, body); return; }  // dev only
    try { SimpleMailMessage m = new SimpleMailMessage(); m.setFrom(from); m.setTo(to); m.setSubject(subject); m.setText(body); s.send(m); }
    catch (Exception e){ log.warn("Email to {} failed", to, e); }
  }
}
