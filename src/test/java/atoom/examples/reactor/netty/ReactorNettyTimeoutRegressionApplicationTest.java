package atoom.examples.reactor.netty;


import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import java.nio.charset.Charset;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.reactive.server.ReactiveWebServerFactory;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.StreamUtils;

import com.github.tomakehurst.wiremock.core.Options.ChunkedEncodingPolicy;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import atoom.examples.reactor.netty.ReactorNettyTimeoutRegressionApplicationTest.ITConfig;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = { "wiremock.port=${test.port}" })
@Import(ITConfig.class)
public class ReactorNettyTimeoutRegressionApplicationTest {

	@ClassRule
	public static WireMockRule wireMockRule = new WireMockRule(wireMockConfig()
			.dynamicPort()
			.gzipDisabled(true)
			.useChunkedTransferEncoding(ChunkedEncodingPolicy.NEVER));

	@LocalServerPort
	private int port = 0;

	private WebTestClient webClient;

	@BeforeClass
	public static void beforeClass() {
	    System.setProperty("test.port", Integer.toString(wireMockRule.port()));
	}

	@AfterClass
	public static void afterClass() {
		System.clearProperty("test.port");
	}

	@Before
	public void setUp() {
		String baseUri = "http://localhost:" + port;
		webClient = WebTestClient.bindToServer()
				.baseUrl(baseUri)
				.build();
	}

	@Test
	public void testRequestResponse() {
		String authRequestBody = getJson("/json/request.json");
		String authResponseBody = getJson("/json/response.json");

		stubFor(post("/rp/v5/auth").willReturn(ok(authResponseBody)));

		webClient
			.post()
				.uri("/rp/v5/auth")
				.bodyValue(authRequestBody)
			.exchange()
				.expectStatus()
					.isOk()
				.expectBody()
					.json(authResponseBody);

		verify(postRequestedFor(urlEqualTo("/rp/v5/auth")));
	}

	protected String getJson(String path) {
		try {
			return StreamUtils.copyToString(getClass().getResourceAsStream(path), Charset.defaultCharset());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@EnableAutoConfiguration
	public static class ITConfig {

		/**
		 * Declare an explicit ReactiveWebServerFactory and set it to Netty so that
		 * Spring Boot does not choose Jetty because it is on the classpath via WireMock
		 *
		 * @return
		 */
		@Bean
		public ReactiveWebServerFactory reactiveWebServerFactory() {
			return new NettyReactiveWebServerFactory();
		}
	}
}
