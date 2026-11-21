package com.campushub.files;
import com.campushub.common.BusinessException;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/** Works with AWS S3, Cloudflare R2 or MinIO (set S3_ENDPOINT). Clients are built lazily so the app boots without storage configured. */
@Service
public class S3StorageService implements StorageService {
  @Value("${app.storage.bucket:}") private String bucket;
  @Value("${app.storage.endpoint:}") private String endpoint;
  @Value("${app.storage.region:auto}") private String region;
  @Value("${app.storage.access-key:}") private String accessKey;
  @Value("${app.storage.secret-key:}") private String secretKey;
  @Value("${app.storage.signed-url-minutes:5}") private long signedMinutes;
  private S3Client client; private S3Presigner presigner;

  private synchronized void init(){
    if (client != null) return;
    if (bucket.isBlank() || accessKey.isBlank()) throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_OFF", "File storage isn't set up yet. Please try again later.");
    var creds = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    var cb = S3Client.builder().region(Region.of(region)).credentialsProvider(creds).serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
    var pb = S3Presigner.builder().region(Region.of(region)).credentialsProvider(creds).serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
    if (!endpoint.isBlank()) { cb.endpointOverride(URI.create(endpoint)); pb.endpointOverride(URI.create(endpoint)); }
    client = cb.build(); presigner = pb.build();
  }
  @Override public void put(String key, byte[] data, String contentType){ init();
    client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(), RequestBody.fromBytes(data)); }
  @Override public void delete(String key){ init(); client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build()); }
  @Override public String presignGet(String key, String downloadName){ init();
    String safe = downloadName.replaceAll("[^A-Za-z0-9._ -]", "_");
    var get = GetObjectRequest.builder().bucket(bucket).key(key).responseContentDisposition("attachment; filename=\"" + safe + "\"").build();
    return presigner.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(Duration.ofMinutes(signedMinutes)).getObjectRequest(get).build()).url().toString(); }
}
