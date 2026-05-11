package org.egov.filestore.repository.impl.minio;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(value = "isS3Enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class MinioClientFacade {

	@Autowired
	private MinioConfig minioConfig;

	@Bean
	private MinioClient getMinioClient() {
		log.info("Initializing the minio ");

		// 5s keep-alive is safely below NAT gateway / S3 idle timeouts (which can be < 20s)
		// preventing stale connection reuse and the resulting EOFException on PUT
		OkHttpClient httpClient = new OkHttpClient.Builder()
				.connectionPool(new ConnectionPool(5, 5, TimeUnit.SECONDS))
				.connectTimeout(10, TimeUnit.SECONDS)
				.readTimeout(60, TimeUnit.SECONDS)
				.writeTimeout(60, TimeUnit.SECONDS)
				.retryOnConnectionFailure(true)
				.build();

		MinioClient minioClient = MinioClient.builder()
				.endpoint(minioConfig.getEndPoint())
				.credentials(minioConfig.getAccessKey(), minioConfig.getSecretKey())
				.region(minioConfig.getRegion())
				.httpClient(httpClient)
				.build();

        return minioClient;
	}
}
