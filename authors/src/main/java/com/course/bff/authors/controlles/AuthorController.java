package com.course.bff.authors.controlles;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import com.course.bff.authors.models.Author;
import com.course.bff.authors.requests.CreateAuthorCommand;
import com.course.bff.authors.responses.AuthorResponse;
import com.course.bff.authors.services.AuthorService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("api/v1/authors")
public class AuthorController {

    private final static Logger logger = LoggerFactory.getLogger(AuthorController.class);
    private final AuthorService authorService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Tracer tracer;
    private final MeterRegistry meterRegistry;
    @Value("${redis.topic}")
    private String redisTopic;
    private Counter requestCounter;
    private Counter errorCounter;
    private Timer executionTimer;

    public AuthorController(AuthorService authorService, RedisTemplate<String, Object> redisTemplate, Tracer tracer, MeterRegistry meterRegistry) {
        this.authorService = authorService;
        this.redisTemplate = redisTemplate;
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        initMetrics();
    }

    @GetMapping()
    public Collection<AuthorResponse> getAuthors() throws Exception {
        return executionTimer.recordCallable(() -> {
            logger.info("Get authors");
            List<AuthorResponse> authorResponses = new ArrayList<>();
            this.authorService.getAuthors().forEach(author -> {
                AuthorResponse authorResponse = createAuthorResponse(author);
                authorResponses.add(authorResponse);
            });

            requestCounter.increment();
            return authorResponses;
        });
    }

    @GetMapping("/{id}")
    public AuthorResponse getById(@PathVariable UUID id) {
        logger.info(String.format("Find authors by %s", id));
        Optional<Author> authorSearch = this.authorService.findById(id);
        if (authorSearch.isEmpty()) {
            errorCounter.increment();
            throw new RuntimeException("Author isn't found");
        }

        requestCounter.increment();
        return createAuthorResponse(authorSearch.get());
    }

    @PostMapping()
    public AuthorResponse createAuthors(@RequestBody CreateAuthorCommand createAuthorCommand) {
        logger.info("Create authors");
        Author author = this.authorService.create(createAuthorCommand);
        AuthorResponse authorResponse = createAuthorResponse(author);
        this.sendPushNotification(authorResponse);
        requestCounter.increment();
        return authorResponse;
    }


    private void sendPushNotification(AuthorResponse authorResponse) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Span redisSpan = tracer.nextSpan().name("redisSpan").start();
        try (SpanInScope ws = tracer.withSpanInScope(redisSpan.start())) {
            redisTemplate.convertAndSend(redisTopic, gson.toJson(authorResponse));
        } catch (Exception e) {
            logger.error("Push Notification Error", e);
        } finally {
            redisSpan.finish();
        }
    }

    private AuthorResponse createAuthorResponse(Author author) {
        AuthorResponse authorResponse = new AuthorResponse();
        authorResponse.setId(author.getId());
        authorResponse.setFirstName(author.getFirstName());
        authorResponse.setLastName(author.getLastName());
        authorResponse.setAddress(author.getAddress());
        authorResponse.setLanguage(author.getLanguage());
        return authorResponse;
    }

    private void initMetrics() {
        requestCounter = meterRegistry.counter("request_count",
                "ControllerName", this.getClass().getSimpleName(),
                "ServiceName", authorService.getClass().getSimpleName());
        errorCounter = meterRegistry.counter("error_count",
                "ControllerName", this.getClass().getSimpleName(),
                "ServiceName", authorService.getClass().getSimpleName());
        executionTimer = meterRegistry.timer("execution_duration",
                "ControllerName", this.getClass().getSimpleName(),
                "ServiceName", authorService.getClass().getSimpleName());
    }
}
